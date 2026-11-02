package com.shiftcalendar.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.shiftcalendar.ShiftApp
import com.shiftcalendar.data.Shift
import com.shiftcalendar.data.ShiftKind
import com.shiftcalendar.model.*
import com.shiftcalendar.notify.ShiftAlarmScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

/**
 * Поле времени принимает только цифры и двоеточие; длиннее 5 символов не пускаем,
 * чтобы «HH:mm» набиралось без лишних символов.
 */
fun sanitizeTimeInput(raw: String): String =
    raw.filter { it.isDigit() || it == ':' }.take(5)

/** Оба конца интервала пустые — значит пользователь ещё не задавал своё время. */
fun isEmptyInterval(start: String, end: String): Boolean =
    parseTimeOrNull(start) == null && parseTimeOrNull(end) == null

/** Тип смены по интервалу: переход через полночь — ночная. */
fun kindForInterval(kind: ShiftKind, start: String, end: String): ShiftKind {
    val s = parseTimeOrNull(start) ?: return kind
    val e = parseTimeOrNull(end) ?: return kind
    return if (!e.isAfter(s)) ShiftKind.NIGHT else kind
}

/** Строка для поля времени: «HH:mm» или пусто. */
fun timeFieldText(value: String?): String = normalizeTime(value) ?: (value ?: "")

/** Один месяц со своими сменами — для ленты месяцев. */
data class MonthSummary(
    val ym: YearMonth,
    val shifts: List<Shift>
) {
    val worked: List<Shift> get() = shifts.filter { !it.isDayOff }
    val shiftCount: Int get() = worked.size
    val totalHours: Double get() = worked.sumOf { it.hours }
    val totalEarned: Double get() = worked.sumOf { it.hours * it.rate }
    val hasAny: Boolean get() = shifts.isNotEmpty()
}

data class UiState(
    val year: Int = LocalDate.now().year,
    val selected: YearMonth = YearMonth.from(LocalDate.now()),
    val today: LocalDate = LocalDate.now(),
    val months: List<MonthSummary> = emptyList(),
    val editingDate: LocalDate? = null,
    val editingShift: Shift? = null,
    val editingKind: ShiftKind = ShiftKind.DAY,
    val editingHours: Double = DEFAULT_SHIFT_HOURS,
    val editingRate: Double = 0.0,
    val editingNote: String = "",
    val editingDayOff: Boolean = false,
    val editingStartTime: String = DAY_START,
    val editingEndTime: String = DAY_END,
    /** Перерыв редактируемой смены, минуты — вычитается из времени смены. */
    val editingBreakMinutes: Int = 0,
    // ---- надбавка: окно и процент ----
    /** С этого времени часы смены считаются с надбавкой. */
    val nightThreshold: String = "22:00",
    /** До этого времени действует надбавка (окно может переходить через полночь). */
    val nightEnd: String = "06:00",
    val nightPremiumPercent: Double = 20.0,
    val darkTheme: Boolean = false,
    /** «Сейчас» с точностью до минуты: обновляется тиком, чтобы прогресс/заработок жили. */
    val now: java.time.LocalDateTime = java.time.LocalDateTime.now(),
    val collection: List<CanReward> = emptyList(),
    val rewardsByShift: Map<Long, String> = emptyMap(),
    val savedFlash: String? = null,
    // ---- паттерн смен: черновик цикла ----
    val patternOpen: Boolean = false,
    val patternSteps: List<PatternStep> = emptyList(),
    val patternStart: LocalDate = LocalDate.now(),
    val patternUntil: LocalDate = LocalDate.now().withDayOfYear(1).plusYears(1).withMonth(12).withDayOfMonth(31),
    val patternAlignToMonth: Boolean = false,
    val patternPreview: PatternApplyResult? = null,
    // ---- профили: сохранённые графики ----
    val profiles: List<PatternProfile> = emptyList(),
    /** Выбранный профиль (-1 — просто паттерн без сохранения). */
    val selectedProfileId: Long = -1L,
    val profileName: String = "",
    /** Цвет рабочих дней профиля, ARGB. */
    val workColor: Long = PatternProfile.DEFAULT_WORK_COLOR,
    /** Цвет выходных профиля, ARGB. */
    val dayOffColor: Long = PatternProfile.DEFAULT_DAYOFF_COLOR,
    /** Диалог подтверждения «удалить все смены». */
    val clearShiftsConfirm: Boolean = false
) {
    val current: MonthSummary?
        get() = months.firstOrNull { it.ym == selected }

    /** Профиль, чьи смены сейчас редактируются (null — не выбран). */
    val selectedProfile: PatternProfile?
        get() = profiles.firstOrNull { it.id == selectedProfileId }
}

class ShiftViewModel(app: Application) : AndroidViewModel(app) {

    private val app = getApplication<Application>()
    private val dao = (app as ShiftApp).db.shifts()
    private val prefs = app.getSharedPreferences("monsterwork", Application.MODE_PRIVATE)
    private val _state = MutableStateFlow(
        UiState(
            nightThreshold = prefs.getString("night_threshold", "22:00") ?: "22:00",
            nightEnd = prefs.getString("night_end", "06:00") ?: "06:00",
            nightPremiumPercent = prefs.getString("night_percent", "20")?.toDoubleOrNull()?.coerceIn(0.0, 100.0) ?: 20.0,
            darkTheme = prefs.getBoolean("dark_theme", false),
            collection = prefs.getStringSet("collection", emptySet())!!.mapNotNull(::decodeReward),
            rewardsByShift = prefs.all.filterKeys { it.startsWith("reward_") }
                .mapNotNull { (key, value) -> key.removePrefix("reward_").toLongOrNull()?.let { it to (value as? String ?: return@mapNotNull null) } }.toMap(),
            profiles = profilesFromJson(prefs.getString("profiles_json", "") ?: "")
        )
    )
    val state: StateFlow<UiState> = _state.asStateFlow()

    /** Напоминание ставится системно, поэтому переживает закрытие приложения. */
    private val appContext = app.applicationContext

    init {
        ShiftAlarmScheduler.ensureChannel(appContext)
        refresh()
        startMinuteTicker()
    }

    /**
     * Живые значения (прогресс смены, заработок, банки) должны обновляться
     * каждую минуту. Спим до начала следующей минуты, а не фиксированные 60 с —
     * иначе тик «плывёт» и цифры меняются не в :00. Заодно ловим смену суток,
     * чтобы «сегодня» и выбранный год не отставали.
     */
    private fun startMinuteTicker() {
        viewModelScope.launch {
            while (true) {
                val now = java.time.LocalDateTime.now()
                delay(minuteTickDelayMs(now).coerceAtLeast(250L))
                val fresh = java.time.LocalDateTime.now().withSecond(0).withNano(0)
                val midnightPassed = fresh.toLocalDate() != _state.value.today
                if (midnightPassed) {
                    // Новый день: «сегодня» и, если сменился год, лента месяцев.
                    val yearRolled = fresh.year != _state.value.year
                    _state.value = _state.value.copy(
                        now = fresh,
                        today = fresh.toLocalDate(),
                        year = if (yearRolled) fresh.year else _state.value.year,
                        selected = if (yearRolled) YearMonth.from(fresh) else _state.value.selected
                    )
                    refresh()
                } else {
                    _state.value = _state.value.copy(now = fresh)
                }
            }
        }
    }

    /** Перечитать все 12 месяцев выбранного года. */
    private fun refresh() {
        viewModelScope.launch {
            val year = _state.value.year
            val today = LocalDate.now()
            val result = ArrayList<MonthSummary>(12)
            for (m in 1..12) {
                val ym = YearMonth.of(year, m)
                val prefix = "%04d-%02d".format(year, m)
                result.add(MonthSummary(ym, dao.byMonthOnce(prefix)))
            }
            _state.value = _state.value.copy(
                today = today,
                months = result,
                editingDate = _state.value.editingDate
            )
        }
    }

    fun selectMonth(ym: YearMonth) {
        _state.value = _state.value.copy(selected = ym)
    }

    fun openDay(date: LocalDate) {
        viewModelScope.launch {
            val existing = dao.byDate(date.iso())
            val kind = existing?.kind?.let { runCatching { ShiftKind.valueOf(it) }.getOrNull() } ?: ShiftKind.DAY
            val defaults = defaultInterval(kind)
            val startIn = if (existing == null) defaults.first
            else existing.startTime ?: (if (existing.isDayOff) "" else defaults.first)
            val endIn = if (existing == null) defaults.second
            else existing.endTime ?: (if (existing.isDayOff) "" else defaults.second)
            _state.value = _state.value.copy(
                editingDate = date,
                editingShift = existing,
                editingKind = if (existing == null) ShiftKind.DAY else kind,
                editingHours = existing?.hours
                    ?: (intervalHours(startIn, endIn) ?: DEFAULT_SHIFT_HOURS),
                editingRate = existing?.rate ?: defaultRate(),
                editingStartTime = startIn,
                editingEndTime = endIn,
                editingBreakMinutes = existing?.breakMinutes ?: 0,
                editingNote = existing?.note ?: "",
                editingDayOff = existing?.isDayOff ?: false
            )
        }
    }

    /** Ставка последней сохранённой смены — чтобы не вводить её каждый раз. */
    private fun defaultRate(): Double {
        val all = _state.value.months.flatMap { it.shifts }
        return all.lastOrNull { it.rate > 0 }?.rate ?: 0.0
    }

    fun closeEditor() {
        _state.value = _state.value.copy(editingDate = null, editingShift = null)
    }

    fun setKind(kind: ShiftKind) {
        val s = _state.value
        // Смена типа — если пользователь ещё не правил интервал руками, ставим
        // типовой (день 8–20, ночь 20–8); часы считаем по интервалу.
        val (start, end) = defaultInterval(kind)
        val untouched = s.editingShift == null || isEmptyInterval(s.editingStartTime, s.editingEndTime)
        val nextStart = if (untouched) start else s.editingStartTime
        val nextEnd = if (untouched) end else s.editingEndTime
        _state.value = s.copy(
            editingKind = kind,
            editingDayOff = false,
            editingStartTime = nextStart,
            editingEndTime = nextEnd,
            editingHours = intervalHours(nextStart, nextEnd) ?: s.editingHours
        )
    }

    fun setDayOff(value: Boolean) {
        val s = _state.value
        _state.value = s.copy(
            editingDayOff = value,
            editingHours = if (value) 0.0 else s.editingHours
        )
    }

    /** Перерыв смены: вычитается из длительности, шаг 5 минут. */
    fun setBreakMinutes(value: Int) {
        _state.value = _state.value.copy(editingBreakMinutes = value.coerceIn(0, 12 * 60))
    }

    fun setNightThreshold(value: String) {
        val valid = runCatching { java.time.LocalTime.parse(value) }.isSuccess
        if (!valid) return
        prefs.edit().putString("night_threshold", value).apply()
        _state.value = _state.value.copy(nightThreshold = value)
        refresh()
    }

    /**
     * Конец окна надбавки. Если конец совпал с началом — окно пустое, надбавки нет:
     * в этом случае сдвигаем конец на минуту, чтобы окно никогда не «схлопнулось» молча.
     */
    fun setNightEnd(value: String) {
        val valid = runCatching { java.time.LocalTime.parse(value) }.isSuccess
        if (!valid) return
        prefs.edit().putString("night_end", value).apply()
        _state.value = _state.value.copy(nightEnd = value)
        refresh()
    }

    /**
     * Процент надбавки: 0–100. Значение хранится как Double — точность «20,5»
     * сохраняется; формат отображения (нули после запятой) решает formatPercent.
     */
    fun setNightPercent(value: Double) {
        val percent = value.coerceIn(0.0, 100.0)
        prefs.edit().putString("night_percent", percent.toString()).apply()
        _state.value = _state.value.copy(nightPremiumPercent = percent)
        refresh()
    }

    fun toggleTheme() {
        val enabled = !_state.value.darkTheme
        prefs.edit().putBoolean("dark_theme", enabled).apply()
        _state.value = _state.value.copy(darkTheme = enabled)
    }

    /** Заработок смены: окно надбавки + перерыв. */
    fun shiftEarnings(shift: Shift): Double {
        val threshold = runCatching { java.time.LocalTime.parse(_state.value.nightThreshold) }
            .getOrDefault(java.time.LocalTime.of(22, 0))
        val end = runCatching { java.time.LocalTime.parse(_state.value.nightEnd) }
            .getOrDefault(java.time.LocalTime.of(6, 0))
        return shiftEarnings(shift, threshold, end, _state.value.nightPremiumPercent, shift.breakMinutes)
    }

    /**
     * Ввод времени с клавиатуры: принимаем «HH:mm», «H:mm», «HHmm», «HH mm».
     * Храним как ввели (чтобы поле не мешало печатать), а часы пересчитываем,
     * только когда оба конца читаются.
     */
    fun setInterval(start: String, end: String) {
        val s = _state.value
        val hours = intervalHours(start, end)
        _state.value = s.copy(
            editingStartTime = sanitizeTimeInput(start),
            editingEndTime = sanitizeTimeInput(end),
            editingHours = when {
                s.editingDayOff -> 0.0
                hours != null -> hours
                else -> s.editingHours
            }
        )
    }

    /** Сдвиг начала/конца на ±степень минут (кнопки «−»/«+»). */
    fun nudgeStart(minutes: Int) = setInterval(
        shiftTime(_state.value.editingStartTime, minutes, DAY_START),
        _state.value.editingEndTime
    )

    fun nudgeEnd(minutes: Int) = setInterval(
        _state.value.editingStartTime,
        shiftTime(_state.value.editingEndTime, minutes, DAY_END)
    )

    /** Выбор времени из диалога — значение уже корректное «HH:mm». */
    fun pickStart(time: String) = setInterval(time, _state.value.editingEndTime)

    fun pickEnd(time: String) = setInterval(_state.value.editingStartTime, time)

    fun stepHours(delta: Double) {
        val next = (_state.value.editingHours + delta).coerceIn(0.5, 24.0)
        _state.value = _state.value.copy(editingHours = next)
    }

    /** Хелпер для редактора: часы по введённому интервалу (null — пока невалидно). */
    fun previewHours(): Double? = intervalHours(_state.value.editingStartTime, _state.value.editingEndTime)

    fun setRate(rate: Double) {
        _state.value = _state.value.copy(editingRate = rate.coerceAtLeast(0.0))
    }

    fun setNote(note: String) {
        _state.value = _state.value.copy(editingNote = note)
    }

    /** Сохранить смену или выходной на выбранный день. */
    fun save() {
        val s = _state.value
        val date = s.editingDate ?: return
        viewModelScope.launch {
            val (start, end) = when {
                s.editingDayOff -> null to null
                else -> {
                    val st = normalizeTime(s.editingStartTime)
                    val en = normalizeTime(s.editingEndTime)
                    if (st != null && en != null) st to en else defaultInterval(s.editingKind)
                }
            }
            // Свой интервал = своя длительность (ночь считается через полночь);
            // перерыв вычитается, оплачиваемые часы не уходят в минус.
            val hours = when {
                s.editingDayOff -> 0.0
                start != null && end != null -> (intervalHours(start, end) ?: s.editingHours) - s.editingBreakMinutes / 60.0
                else -> s.editingHours
            }.coerceAtLeast(0.0)
            val kind = when {
                s.editingDayOff -> s.editingKind
                start != null && end != null -> kindForInterval(s.editingKind, start, end)
                else -> s.editingKind
            }
            val entity = Shift(
                id = s.editingShift?.id ?: 0,
                date = date.iso(),
                kind = kind.name,
                hours = hours,
                startTime = start,
                rate = if (s.editingDayOff) 0.0 else s.editingRate,
                isDayOff = s.editingDayOff,
                note = s.editingNote.trim(),
                endTime = end,
                breakMinutes = if (s.editingDayOff) 0 else s.editingBreakMinutes,
                profileId = s.editingShift?.profileId ?: 0
            )
            val shiftId = if (entity.id == 0L) dao.insert(entity) else { dao.update(entity); entity.id }
            // Напоминание за час до смены: снимаем старое, ставим новое.
            ShiftAlarmScheduler.cancel(appContext, s.editingShift ?: entity.copy(id = shiftId))
            val withId = entity.copy(id = shiftId)
            if (!withId.isDayOff) ShiftAlarmScheduler.schedule(appContext, withId)
            _state.value = _state.value.copy(
                editingDate = null,
                editingShift = null,
                savedFlash = if (withId.isDayOff) "Выходной отмечен"
                else if (withId.startTime != null) "Сохранено · напомню за час до смены"
                else "Сохранено"
            )
            refresh()
        }
    }

    fun deleteCurrent() {
        val date = _state.value.editingDate ?: return
        val existing = _state.value.editingShift
        viewModelScope.launch {
            dao.deleteByDate(date.iso())
            existing?.let { ShiftAlarmScheduler.cancel(appContext, it) }
            _state.value = _state.value.copy(
                editingDate = null,
                editingShift = null,
                savedFlash = "Удалено"
            )
            refresh()
        }
    }

    // ----------Удаление всех смен (кнопка под статистикой) ----------

    fun requestClearShifts() {
        _state.value = _state.value.copy(clearShiftsConfirm = true)
    }

    fun dismissClearShifts() {
        _state.value = _state.value.copy(clearShiftsConfirm = false)
    }

    /** Убрать все смены: дни становятся «пустыми», как до проставления. */
    fun confirmClearShifts() {
        viewModelScope.launch {
            // Снять будущие напоминания, пока смены ещё в базе.
            val all = dao.shiftsBetween("0000-01-01", "9999-12-31")
            val today = LocalDate.now()
            all.filter { !it.isDayOff && (it.date > today.iso() || it.date == today.iso()) }
                .forEach { ShiftAlarmScheduler.cancel(appContext, it) }
            dao.deleteBetween("0000-01-01", "9999-12-31")
            _state.value = _state.value.copy(
                clearShiftsConfirm = false,
                savedFlash = "Все смены удалены"
            )
            refresh()
        }
    }

    fun clearFlash() {
        _state.value = _state.value.copy(savedFlash = null)
    }

    fun previousYear() {
        _state.value = _state.value.copy(year = _state.value.year - 1)
        refresh()
    }

    fun nextYear() {
        _state.value = _state.value.copy(year = _state.value.year + 1)
        refresh()
    }

    /** Стрелки ‹ › у месяца: листаем месяцы внутри ленты, на границе года — год тоже. */
    fun previousMonth() {
        val s = _state.value
        val prev = s.selected.minusMonths(1)
        if (prev.year != s.year) {
            _state.value = s.copy(year = prev.year, selected = prev)
            refresh()
        } else {
            _state.value = s.copy(selected = prev)
        }
    }

    fun nextMonth() {
        val s = _state.value
        val next = s.selected.plusMonths(1)
        if (next.year != s.year) {
            _state.value = s.copy(year = next.year, selected = next)
            refresh()
        } else {
            _state.value = s.copy(selected = next)
        }
    }

    // ---------- Паттерн смен и профили ----------

    /**
     * Открыть конструктор: черновик восстанавливается таким, каким его оставил
     * пользователь (времена, ставки, перерывы, даты) — без стандартных значений.
     */
    fun openPattern() {
        val saved = prefs.getString("pattern_draft", null)
        val steps = saved?.let(::stepsFromJson).orEmpty()
        val today = LocalDate.now()
        val start = prefs.getString("pattern_start", null)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?: LocalDate.of(today.year, 11, 1).let {
                if (it.isBefore(today)) LocalDate.of(today.year + 1, 11, 1) else it
            }
        val until = prefs.getString("pattern_until", null)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?: LocalDate.of(today.year + 1, 12, 31)
        val selectedId = prefs.getLong("pattern_sel_profile", -1L)
        val sel = _state.value.profiles.firstOrNull { it.id == selectedId }
        _state.value = _state.value.copy(
            patternOpen = true,
            // Совсем первый запуск: дефолтный пресет, дальше — только сохранённый черновик.
            patternSteps = if (steps.isNotEmpty()) steps else PRESET_PATTERNS.first().second,
            patternStart = start,
            patternUntil = until,
            patternAlignToMonth = prefs.getBoolean("pattern_align", false),
            patternPreview = null,
            selectedProfileId = if (sel != null) selectedId else -1L,
            profileName = sel?.name ?: "",
            workColor = sel?.workColor ?: PatternProfile.DEFAULT_WORK_COLOR,
            dayOffColor = sel?.dayOffColor ?: PatternProfile.DEFAULT_DAYOFF_COLOR
        )
        if (saved == null) persistPatternDraft()
    }

    fun closePattern() {
        _state.value = _state.value.copy(patternOpen = false, patternPreview = null)
    }

    /** Черновик цикла persists после каждой правки — «запоминай, что выставлял». */
    private fun persistPatternDraft() {
        val s = _state.value
        prefs.edit()
            .putString("pattern_draft", stepsToJson(s.patternSteps))
            .putString("pattern_start", s.patternStart.iso())
            .putString("pattern_until", s.patternUntil.iso())
            .putBoolean("pattern_align", s.patternAlignToMonth)
            .putLong("pattern_sel_profile", s.selectedProfileId)
            .apply()
    }

    fun loadPreset(steps: List<PatternStep>) {
        _state.value = _state.value.copy(patternSteps = steps, patternPreview = null)
        persistPatternDraft()
    }

    fun addStep(isDayOff: Boolean) {
        val base = _state.value.patternSteps.lastOrNull()
        val kind = base?.kind ?: ShiftKind.DAY
        val step = if (isDayOff) {
            PatternStep(isDayOff = true, kind = kind)
        } else {
            // Новый шаг наследует явный интервал предыдущего (а не вычисленный конец).
            val start = base?.startTime ?: defaultInterval(kind).first
            val end = base?.endTime ?: defaultInterval(kind).second
            PatternStep(
                isDayOff = false,
                kind = kind,
                hours = base?.effectiveHours ?: DEFAULT_SHIFT_HOURS,
                rate = base?.rate ?: 0.0,
                startTime = start,
                endTime = end,
                breakMinutes = base?.breakMinutes ?: 0
            )
        }
        _state.value = _state.value.copy(patternSteps = _state.value.patternSteps + step, patternPreview = null)
        persistPatternDraft()
    }

    fun removeStep(index: Int) {
        val list = _state.value.patternSteps.toMutableList()
        if (index in list.indices) list.removeAt(index)
        _state.value = _state.value.copy(patternSteps = list, patternPreview = null)
        persistPatternDraft()
    }

    fun updateStep(index: Int, step: PatternStep) {
        val list = _state.value.patternSteps.toMutableList()
        if (index in list.indices) {
            list[index] = step
            _state.value = _state.value.copy(patternSteps = list, patternPreview = null)
            persistPatternDraft()
        }
    }

    /** Время начала конкретного шага паттерна: ввод с клавиатуры. */
    fun setStepStart(index: Int, text: String) = editStepInterval(index, startIn = sanitizeTimeInput(text))

    /** Время конца конкретного шага паттерна: ввод с клавиатуры. */
    fun setStepEnd(index: Int, text: String) = editStepInterval(index, endIn = sanitizeTimeInput(text))

    /** Кнопки «−»/«+» у начала шага: шаг 30 минут, ночь перекатывается через полночь. */
    fun nudgeStepStart(index: Int, minutes: Int) =
        editStepInterval(index, startIn = shiftTime(stepAt(index)?.start, minutes, DAY_START))

    fun nudgeStepEnd(index: Int, minutes: Int) =
        editStepInterval(index, endIn = shiftTime(stepAt(index)?.end, minutes, DAY_END))

    /** Выбор времени из диалога для шага паттерна. */
    fun pickStepStart(index: Int, time: String) = editStepInterval(index, startIn = time)
    fun pickStepEnd(index: Int, time: String) = editStepInterval(index, endIn = time)

    /** Перерыв конкретного шага паттерна, минуты. */
    fun setStepBreak(index: Int, minutes: Int) {
        val cur = stepAt(index) ?: return
        updateStep(index, cur.copy(breakMinutes = minutes.coerceIn(0, 12 * 60)))
    }

    /**
     * Общий путь для правки интервала шага: сохраняем обе стороны явно,
     * пересчитываем часы по интервалу и уточняем тип (через полночь — ночная).
     */
    private fun editStepInterval(index: Int, startIn: String? = null, endIn: String? = null) {
        val cur = stepAt(index) ?: return
        val nextStart = startIn ?: cur.startTime ?: cur.start
        val nextEnd = endIn ?: cur.endTime ?: cur.end
        val startNormalized = normalizeTime(nextStart)
        val endNormalized = normalizeTime(nextEnd)
        val hours = intervalHours(startNormalized, endNormalized)
        val kind = if (startNormalized != null && endNormalized != null) {
            kindForInterval(cur.kind, startNormalized, endNormalized)
        } else cur.kind
        updateStep(
            index,
            cur.copy(
                startTime = nextStart,
                endTime = nextEnd,
                hours = hours ?: cur.hours,
                kind = if (cur.isDayOff) cur.kind else kind
            )
        )
    }

    private fun stepAt(index: Int): PatternStep? =
        _state.value.patternSteps.getOrNull(index)

    /** Одна ставка на весь цикл — самый частый случай. */
    fun setAllRates(rate: Double) {
        val list = _state.value.patternSteps.map { if (it.isDayOff) it else it.copy(rate = rate) }
        _state.value = _state.value.copy(patternSteps = list, patternPreview = null)
        persistPatternDraft()
    }

    fun setPatternStart(date: LocalDate) {
        _state.value = _state.value.copy(patternStart = date, patternPreview = null)
        persistPatternDraft()
    }

    fun setPatternUntil(date: LocalDate) {
        _state.value = _state.value.copy(patternUntil = date, patternPreview = null)
        persistPatternDraft()
    }

    fun setAlignToMonth(value: Boolean) {
        _state.value = _state.value.copy(patternAlignToMonth = value, patternPreview = null)
        persistPatternDraft()
    }

    /** Посчитать, что получится, не записывая в базу. */
    fun previewPattern() {
        val s = _state.value
        viewModelScope.launch {
            val preview = buildPreview(s)
            _state.value = _state.value.copy(patternPreview = preview)
        }
    }

    private suspend fun buildPreview(s: UiState): PatternApplyResult {
        val existing = dao.shiftsBetween(s.patternStart.iso(), s.patternUntil.iso())
        return buildPatternShifts(
            pattern = s.patternSteps,
            start = s.patternStart,
            from = s.patternStart,
            to = s.patternUntil,
            alignToMonth = s.patternAlignToMonth,
            profileId = s.selectedProfileId,
            existingDates = existing.map { it.date }.toSet()
        )
    }

    /**
     * Записать паттерн в календарь. Паттерн ЗАМЕНЯЕТ смены, а не дублирует:
     *  - профиль выбран  — перезаписываются только смены ЭТОГО профиля
     *    (сам на себя дублировать нельзя), чужие и ручные остаются;
     *  - профиль не выбран — заменяются все смены в диапазоне (паттерн
     *    «вступает во владение» датами, старые записи уходят).
     */
    fun applyPattern() {
        val s = _state.value
        if (s.patternSteps.isEmpty()) return
        viewModelScope.launch {
            val range = dao.shiftsBetween(s.patternStart.iso(), s.patternUntil.iso())
            val selfOnly = s.selectedProfileId >= 0L
            val toRemove = if (selfOnly) range.filter { it.profileId == s.selectedProfileId } else range
            val preview = buildPreview(s)
            if (preview.shifts.isNotEmpty()) {
                // Сначала снимаем напоминания удаляемых смен, пока они с интервалами.
                val today = LocalDate.now()
                toRemove.filter { !it.isDayOff }.forEach { ShiftAlarmScheduler.cancel(appContext, it) }
                if (selfOnly) {
                    dao.deleteBetweenOfProfile(s.patternStart.iso(), s.patternUntil.iso(), s.selectedProfileId)
                } else {
                    dao.deleteBetween(s.patternStart.iso(), s.patternUntil.iso())
                }
                dao.insertAll(preview.shifts)
                preview.shifts
                    .filter { !it.isDayOff && LocalDate.parse(it.date, ISO) >= today }
                    .forEach { ShiftAlarmScheduler.schedule(appContext, it) }
            }
            _state.value = _state.value.copy(
                patternOpen = false,
                patternPreview = null,
                savedFlash = "Паттерн проставлен: ${preview.workCount} смен, ${preview.dayOffCount} выходных" +
                    if (preview.replacedCount > 0) " · заменено ${preview.replacedCount}" else ""
            )
            refresh()
        }
    }

    // ---------- Профили (сохранённые графики) ----------

    private fun nextProfileId(): Long {
        val next = prefs.getLong("profile_next_id", 1L)
        prefs.edit().putLong("profile_next_id", next + 1).apply()
        return next
    }

    private fun saveProfiles(list: List<PatternProfile>) {
        prefs.edit().putString("profiles_json", profilesToJson(list)).apply()
    }

    /** Новый профиль: пустой цикл из одного шага, имя пустое — пользователь задаёт сам. */
    fun newProfile() {
        _state.value = _state.value.copy(
            patternSteps = listOf(PatternStep(kind = ShiftKind.DAY, startTime = DAY_START, endTime = DAY_END)),
            selectedProfileId = -1L,
            profileName = "",
            workColor = PatternProfile.DEFAULT_WORK_COLOR,
            dayOffColor = PatternProfile.DEFAULT_DAYOFF_COLOR,
            patternPreview = null
        )
        persistPatternDraft()
    }

    /** Загрузить профиль в конструктор: все его значения, ничего по умолчанию. */
    fun loadProfile(id: Long) {
        val p = _state.value.profiles.firstOrNull { it.id == id } ?: return
        _state.value = _state.value.copy(
            patternSteps = p.steps,
            selectedProfileId = id,
            profileName = p.name,
            workColor = p.workColor,
            dayOffColor = p.dayOffColor,
            patternPreview = null
        )
        persistPatternDraft()
    }

    /** Сохранить цикл как профиль: существующий (выбранный) или новый. */
    fun saveProfile() {
        val s = _state.value
        if (s.patternSteps.isEmpty()) return
        val name = s.profileName.trim().ifEmpty { "Профиль ${s.profiles.size + 1}" }
        val id = if (s.selectedProfileId >= 0L) s.selectedProfileId else nextProfileId()
        val profile = PatternProfile(
            id = id,
            name = name,
            steps = s.patternSteps,
            workColor = s.workColor,
            dayOffColor = s.dayOffColor
        )
        val list = s.profiles.filterNot { it.id == id } + profile
        saveProfiles(list)
        _state.value = _state.value.copy(
            profiles = list,
            selectedProfileId = id,
            profileName = name,
            savedFlash = "Профиль «$name» сохранён"
        )
        persistPatternDraft()
    }

    fun deleteProfile(id: Long) {
        val s = _state.value
        val list = s.profiles.filterNot { it.id == id }
        saveProfiles(list)
        _state.value = s.copy(
            profiles = list,
            selectedProfileId = if (s.selectedProfileId == id) -1L else s.selectedProfileId,
            profileName = if (s.selectedProfileId == id) "" else s.profileName
        )
        persistPatternDraft()
    }

    fun setProfileName(name: String) {
        _state.value = _state.value.copy(profileName = name)
    }

    /** Цвет рабочих дней профиля (из выбранного набора). */
    fun setWorkColor(argb: Long) {
        _state.value = _state.value.copy(workColor = argb)
    }

    /** Цвет выходных профиля. */
    fun setDayOffColor(argb: Long) {
        _state.value = _state.value.copy(dayOffColor = argb)
    }

    /** Цвет заливки дня календаря по профилю смены (null — вне дня). */
    fun colorForShiftProfile(): Map<Long, Pair<Long, Long>> =
        _state.value.profiles.associate { it.id to (it.workColor to it.dayOffColor) }
}
