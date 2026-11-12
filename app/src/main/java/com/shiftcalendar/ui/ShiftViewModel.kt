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
    /** Смена, которую правят; null — в редакторе новая смена этого дня. */
    val editingShift: Shift? = null,
    /** ВСЕ смены открытого дня, в порядке отображения (может быть несколько). */
    val editingDayShifts: List<Shift> = emptyList(),
    /**
     * Смена сменилась (сохранена/выбрана другая) — редактор монтируется заново,
     * чтобы поля не показывали ввод предыдущей смены. Поле времени компоузится
     * с автосохранением состояния, поэтому без явного ключа остался бы старый текст.
     */
    val editorGeneration: Int = 0,
    val editingKind: ShiftKind = ShiftKind.DAY,
    val editingHours: Double = DEFAULT_SHIFT_HOURS,
    val editingRate: Double = 0.0,
    val editingNote: String = "",
    val editingDayOff: Boolean = false,
    val editingStartTime: String = DAY_START,
    val editingEndTime: String = DAY_END,
    /** Перерыв редактируемой смены, минуты — вычитается из времени смены. */
    val editingBreakMinutes: Int = 0,
    /** Причина, по которой смену нельзя сохранить (показывается в редакторе). */
    val editorError: String? = null,
    // ---- надбавка: как считается и с какого времени (решения 13-А, 13-Б) ----
    /** Название надбавки, задаётся пользователем: «Ночная», «Вечерняя» и т.п. */
    val premiumTitle: String = "Ночная надбавка",
    /** Способ расчёта: процентом от ставки или суммой за час. */
    val premiumMode: PremiumMode = PremiumMode.PERCENT,
    /** С этого времени часы смены считаются с надбавкой (до конца смены). */
    val nightThreshold: String = "22:00",
    /** Процент надбавки — когда выбран способ «процентом». */
    val nightPremiumPercent: Double = 20.0,
    /** Сумма надбавки, ₽/час — когда выбран способ «суммой». */
    val premiumAmount: Double = 0.0,
    /**
     * Учитывать ли перерыв, попавший после начала надбавки (решение 13-Б):
     * при true перерыв съедает сначала надбавочные часы, при false — базовые.
     */
    val premiumBreakAfterStart: Boolean = true,
    /** Старое «надбавка до» — оставлено только для чтения прежних настроек. */
    val nightEnd: String = "06:00",
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
    /** Диалог подтверждения удаления. scope: "month" — месяц, "all" — весь календарь. */
    val clearShiftsConfirm: Boolean = false,
    val clearScope: String = "month"
) {
    val current: MonthSummary?
        get() = months.firstOrNull { it.ym == selected }

    /** Профиль, чьи смены сейчас редактируются (null — не выбран). */
    val selectedProfile: PatternProfile?
        get() = profiles.firstOrNull { it.id == selectedProfileId }

    /** Итог дня в редакторе: все смены, часы, признак выходного. */
    val editingDay: DaySummary get() = summarizeDay(editingDayShifts)

    /** Выходной в редакторе: выбран режим «Выходной» или он уже стоит у дня. */
    val editingHasDayOff: Boolean get() = editingDayOff || editingDay.dayOff

    /** Настройки надбавки в виде, который понимает расчёт заработка. */
    val premium: PremiumSettings
        get() = PremiumSettings(
            title = premiumTitle,
            mode = premiumMode,
            percent = nightPremiumPercent,
            amount = premiumAmount,
            threshold = nightThreshold,
            breakCountsInPremium = premiumBreakAfterStart
        )

    /** Раскладка смены редактора на «до надбавки» и «с надбавкой», для подписи. */
    fun premiumSplitFor(start: String, end: String, breakMinutes: Int): PremiumSplit {
        val s = parseTimeOrNull(start) ?: return PremiumSplit(0L, 0L)
        val e = parseTimeOrNull(end) ?: return PremiumSplit(0L, 0L)
        val t = parseTimeOrNull(premium.threshold) ?: java.time.LocalTime.of(22, 0)
        if (premium.isOff) return PremiumSplit(0L, intervalMinutes(s, e) - breakMinutes.coerceAtLeast(0))
        return premiumSplitByThreshold(s, e, t, breakMinutes, premiumBreakAfterStart)
    }
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
            premiumTitle = prefs.getString("premium_title", "Ночная надбавка") ?: "Ночная надбавка",
            premiumMode = runCatching {
                PremiumMode.valueOf(prefs.getString("premium_mode", "PERCENT") ?: "PERCENT")
            }.getOrDefault(PremiumMode.PERCENT),
            premiumAmount = prefs.getString("premium_amount", "0")?.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0,
            premiumBreakAfterStart = prefs.getBoolean("premium_break_after", true),
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

    /**
     * Открыть день. В редактор попадают ВСЕ смены дня: первая открывается на
     * правку, остальные видны списком и выбираются тапом. Пустой день сразу
     * получает поля новой смены с интервалом по умолчанию.
     */
    fun openDay(date: LocalDate) {
        viewModelScope.launch {
            val all = dao.allOnDate(date.iso()).orEmpty()
            val current = all.firstOrNull { !it.isDayOff } ?: all.firstOrNull()
            _state.value = _state.value.copy(editingDate = date, editingDayShifts = all)
            loadIntoEditor(current)
        }
    }

    /** Открыть на правку конкретную смену открытого дня. */
    fun selectEditingShift(id: Long) {
        val target = _state.value.editingDayShifts.firstOrNull { it.id == id } ?: return
        loadIntoEditor(target)
    }

    /** Перечитать смены открытого дня после записи в базу. */
    private suspend fun reloadEditingDay(date: LocalDate): List<Shift> =
        dao.allOnDate(date.iso()).orEmpty().also { list ->
            _state.value = _state.value.copy(editingDayShifts = list)
        }

    /**
     * Заполнить поля редактора по смене (null — новая смена).
     * Ставка берётся у предыдущей смены дня, чтобы её не вводить заново.
     */
    private fun loadIntoEditor(shift: Shift?) {
        val s = _state.value
        val kind = shift?.kind?.let { runCatching { ShiftKind.valueOf(it) }.getOrNull() } ?: ShiftKind.DAY
        val defaults = defaultInterval(kind)
        val startIn = when {
            shift == null -> defaults.first
            shift.startTime != null -> shift.startTime
            shift.isDayOff -> ""
            else -> defaults.first
        }
        val endIn = when {
            shift == null -> defaults.second
            shift.endTime != null -> shift.endTime
            shift.isDayOff -> ""
            else -> defaults.second
        }
        val rate = shift?.rate?.takeIf { it > 0.0 } ?: defaultRate()
        _state.value = s.copy(
            editingShift = shift,
            editorGeneration = s.editorGeneration + 1,
            editingKind = if (shift == null) ShiftKind.DAY else kind,
            editingHours = shift?.hours ?: (intervalHours(startIn, endIn) ?: DEFAULT_SHIFT_HOURS),
            editingRate = rate,
            editingStartTime = startIn,
            editingEndTime = endIn,
            editingBreakMinutes = shift?.breakMinutes ?: 0,
            editingNote = shift?.note ?: "",
            editingDayOff = shift?.isDayOff ?: false,
            editorError = null
        )
    }

    /** «+ Смена» в редакторе: поля пустые под новую смену, выбор — снять. */
    fun startNewShift() = loadIntoEditor(null)

    /** Ставка последней сохранённой смены — чтобы не вводить её каждый раз. */
    private fun defaultRate(): Double {
        val all = _state.value.months.flatMap { it.shifts }
        return all.lastOrNull { it.rate > 0 }?.rate ?: 0.0
    }

    fun closeEditor() {
        _state.value = _state.value.copy(
            editingDate = null,
            editingShift = null,
            editingDayShifts = emptyList(),
            editorError = null
        )
    }

    /** Снять подсказку об ошибке (тап по плашке в редакторе). */
    fun clearEditorError() {
        if (_state.value.editorError != null) {
            _state.value = _state.value.copy(editorError = null)
        }
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

    /** Название надбавки — свободный текст, показывается в редакторе и итогах. */
    fun setPremiumTitle(value: String) {
        val title = value.take(40)
        prefs.edit().putString("premium_title", title).apply()
        _state.value = _state.value.copy(premiumTitle = title)
    }

    /** Способ расчёта надбавки: процентом от ставки или фиксированной суммой за час. */
    fun setPremiumMode(mode: PremiumMode) {
        prefs.edit().putString("premium_mode", mode.name).apply()
        _state.value = _state.value.copy(premiumMode = mode)
        refresh()
    }

    /** Сумма надбавки, ₽/час: 0–100000. */
    fun setPremiumAmount(value: Double) {
        val amount = value.coerceIn(0.0, 100_000.0)
        prefs.edit().putString("premium_amount", amount.toString()).apply()
        _state.value = _state.value.copy(premiumAmount = amount)
        refresh()
    }

    /**
     * Считать ли перерыв, попавший после начала надбавки, вычетом из надбавочных
     * часов (решение 13-Б). true — съедает надбавку, false — базовые часы.
     */
    fun setPremiumBreakAfterStart(value: Boolean) {
        prefs.edit().putBoolean("premium_break_after", value).apply()
        _state.value = _state.value.copy(premiumBreakAfterStart = value)
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

    /** Заработок смены: надбавка от порога до конца смены + перерыв (решения 13-А/13-Б). */
    fun shiftEarnings(shift: Shift): Double {
        if (shift.isDayOff || shift.rate <= 0.0) return 0.0
        val premium = _state.value.premium
        if (premium.isOff) return roundMoney(shift.hours * shift.rate)
        val start = parseTimeOrNull(shift.startTime ?: "") ?: return roundMoney(shift.hours * shift.rate)
        val end = parseTimeOrNull(shift.endTime ?: "") ?: return roundMoney(shift.hours * shift.rate)
        return roundMoney(shiftEarningsWithPremium(shift.rate, start, end, premium, shift.breakMinutes))
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
            },
            editorError = null
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
        _state.value = _state.value.copy(editingHours = roundHours(next))
    }

    /** Хелпер для редактора: часы по введённому интервалу (null — пока невалидно). */
    fun previewHours(): Double? = intervalHours(_state.value.editingStartTime, _state.value.editingEndTime)

    fun setRate(rate: Double) {
        _state.value = _state.value.copy(editingRate = roundMoney(rate.coerceAtLeast(0.0)))
    }

    fun setNote(note: String) {
        _state.value = _state.value.copy(editingNote = note)
    }

    /**
     * Сохранить смену или выходной. В день можно добавить сколько угодно смен:
     * сохранение пишет ровно одну строку (новую или правленую), остальные смены
     * дня не трогаются. Дубликат по времени и вырожденный интервал не пишутся —
     * вместо тихой второй одинаковой строки в редакторе показывается причина.
     */
    fun save() {
        val s = _state.value
        val date = s.editingDate ?: return
        viewModelScope.launch {
            val plan = planShift(draftFrom(s, date), s.editingDayShifts)
            if (plan is ShiftPlan.Invalid) {
                _state.value = _state.value.copy(editorError = plan.reason)
                return@launch
            }
            val entity = (plan as ShiftPlan.Ready).shift
            val existing = s.editingShift
            val shiftId = if (existing == null || existing.id == 0L) {
                dao.insert(entity)
            } else {
                dao.update(entity)
                entity.id
            }
            // Напоминание за час до смены: id смены — requestCode системного
            // будильника, поэтому у каждой смены дня своё напоминание.
            if (existing != null) ShiftAlarmScheduler.cancel(appContext, existing)
            val withId = entity.copy(id = shiftId)
            if (!withId.isDayOff) ShiftAlarmScheduler.schedule(appContext, withId)
            _state.value = _state.value.copy(
                savedFlash = when {
                    withId.isDayOff -> "Выходной отмечен"
                    withId.startTime != null -> "Смена сохранена · напомню за час"
                    else -> "Смена сохранена"
                }
            )
            // День остаётся открытым: можно сразу добавить вторую смену.
            reloadEditingDay(date)
            // Выходной и рабочие смены в одном дне несовместимы — оставляем
            // одну сторону, чтобы день не был одновременно выходным и рабочим.
            val conflicts = reconcileDayConflicts(_state.value.editingDayShifts, fresh = shiftId)
            conflicts.forEach { doomed ->
                ShiftAlarmScheduler.cancel(appContext, doomed)
                dao.delete(doomed)
            }
            val reloaded = if (conflicts.isEmpty()) _state.value.editingDayShifts else reloadEditingDay(date)
            val stillThere = reloaded.firstOrNull { it.id == shiftId }
            if (stillThere != null) {
                // Правленая смена могла сменить время — перечитываем поля из базы.
                loadIntoEditor(stillThere)
            } else {
                loadIntoEditor(reloaded.firstOrNull { !it.isDayOff } ?: reloaded.firstOrNull())
            }
            refresh()
        }
    }

    /** Черновик смены из полей редактора — вход для [planShift]. */
    private fun draftFrom(s: UiState, date: LocalDate) = ShiftDraft(
        id = s.editingShift?.id ?: 0L,
        date = date.iso(),
        isDayOff = s.editingDayOff,
        kind = s.editingKind,
        startTime = s.editingStartTime,
        endTime = s.editingEndTime,
        breakMinutes = s.editingBreakMinutes,
        rate = s.editingRate,
        note = s.editingNote,
        profileId = s.editingShift?.profileId ?: 0L,
        fallbackHours = s.editingHours
    )

    /**
     * Удалить правленую смену — только её: другие смены дня остаются.
     * Если правленая не выбрана (в редакторе новая смена), удаляется смена дня,
     * которую пользователь открыл первым делом… — но такой смены нет, поэтому
     * в этом случае удалять нечего и кнопка в редакторе не показывается.
     */
    fun deleteCurrent() {
        val date = _state.value.editingDate ?: return
        val target = _state.value.editingShift ?: return
        viewModelScope.launch {
            ShiftAlarmScheduler.cancel(appContext, target)
            dao.delete(target)
            val reloaded = reloadEditingDay(date)
            loadIntoEditor(reloaded.firstOrNull { !it.isDayOff } ?: reloaded.firstOrNull())
            _state.value = _state.value.copy(savedFlash = "Смена удалена")
            refresh()
        }
    }

    /** Убрать все смены открытого дня (кнопка «Очистить день»). */
    fun clearEditingDay() {
        val date = _state.value.editingDate ?: return
        viewModelScope.launch {
            _state.value.editingDayShifts.filter { !it.isDayOff }
                .forEach { ShiftAlarmScheduler.cancel(appContext, it) }
            dao.deleteByDate(date.iso())
            reloadEditingDay(date)
            loadIntoEditor(null)
            _state.value = _state.value.copy(savedFlash = "День очищен")
            refresh()
        }
    }

    // ----------Удаление смен: месяц или весь календарь (решение 10-А/11-А) ----------

    /** scope: "month" — смены и выходные выбранного месяца, "all" — весь календарь. */
    fun requestClearShifts(scope: String = "all") {
        _state.value = _state.value.copy(clearShiftsConfirm = true, clearScope = scope)
    }

    fun dismissClearShifts() {
        _state.value = _state.value.copy(clearShiftsConfirm = false)
    }

    /** Удалить по подтверждению: месяц — границы месяца, «все» — весь диапазон дат. */
    fun confirmClearShifts() {
        viewModelScope.launch {
            val scope = _state.value.clearScope
            val from: String
            val to: String
            if (scope == "month") {
                val ym = _state.value.selected
                from = ym.atDay(1).iso()
                to = ym.atEndOfMonth().iso()
            } else {
                from = "0000-01-01"
                to = "9999-12-31"
            }
            // Снять будущие напоминания, пока смены ещё в базе.
            val today = LocalDate.now()
            dao.shiftsBetween(from, to)
                .filter { !it.isDayOff && it.date >= today.iso() }
                .forEach { ShiftAlarmScheduler.cancel(appContext, it) }
            dao.deleteBetween(from, to)
            _state.value = _state.value.copy(
                clearShiftsConfirm = false,
                savedFlash = if (scope == "month") "Смены месяца удалены" else "Все смены удалены"
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
        val steps = _state.value.patternSteps
        val base = steps.lastOrNull()
        val kind = base?.kind ?: ShiftKind.DAY
        val step = if (isDayOff) {
            PatternStep(isDayOff = true, kind = kind)
        } else {
            // Решение клиента (В14): после выходных новый рабочий шаг должен получить
            // график последней рабочей смены ДО них («после выходных — как до выходных»).
            val template = steps.lastOrNull { !it.isDayOff }
            val start = template?.startTime ?: defaultInterval(kind).first
            val end = template?.endTime ?: defaultInterval(kind).second
            PatternStep(
                isDayOff = false,
                kind = kind,
                hours = template?.effectiveHours ?: DEFAULT_SHIFT_HOURS,
                rate = template?.rate ?: 0.0,
                startTime = start,
                endTime = end,
                breakMinutes = template?.breakMinutes ?: 0
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

    /** Сколько смен в работе прямо сейчас — для подписи «сейчас идут 2». */
    fun activeShiftsNowCount(): Int =
        activeShiftsAt(_state.value.months.flatMap { it.shifts }, _state.value.now)

    /** Одна смена из идущих прямо сейчас (для живого заработка на карточке). */
    fun activeShiftNow(): Shift? =
        activeShiftNow(_state.value.months.map { it.shifts }, _state.value.now)
}
