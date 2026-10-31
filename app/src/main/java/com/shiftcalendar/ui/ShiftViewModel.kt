package com.shiftcalendar.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.shiftcalendar.ShiftApp
import com.shiftcalendar.data.Shift
import com.shiftcalendar.data.ShiftKind
import com.shiftcalendar.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

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
    val savedFlash: String? = null
) {
    val current: MonthSummary?
        get() = months.firstOrNull { it.ym == selected }
}

class ShiftViewModel(app: Application) : AndroidViewModel(app) {

    private val dao = (app as ShiftApp).db.shifts()
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        refresh()
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
            _state.value = _state.value.copy(
                editingDate = date,
                editingShift = existing,
                editingKind = if (existing == null) ShiftKind.DAY else kind,
                editingHours = existing?.hours ?: DEFAULT_SHIFT_HOURS,
                editingRate = existing?.rate ?: defaultRate(),
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
        val hours = when (kind) {
            ShiftKind.DAY -> 12.0
            ShiftKind.NIGHT -> 12.0
            ShiftKind.CUSTOM -> _state.value.editingHours
        }
        _state.value = _state.value.copy(
            editingKind = kind,
            editingHours = if (_state.value.editingShift == null) hours else _state.value.editingHours
        )
    }

    fun setDayOff(value: Boolean) {
        _state.value = _state.value.copy(editingDayOff = value)
    }

    fun stepHours(delta: Double) {
        val next = (_state.value.editingHours + delta).coerceIn(0.5, 24.0)
        _state.value = _state.value.copy(editingHours = next)
    }

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
                s.editingKind == ShiftKind.DAY -> DAY_START to DAY_END
                s.editingKind == ShiftKind.NIGHT -> NIGHT_START to NIGHT_END
                else -> null to null
            }
            val entity = Shift(
                id = s.editingShift?.id ?: 0,
                date = date.iso(),
                kind = s.editingKind.name,
                hours = if (s.editingDayOff) 0.0 else s.editingHours,
                startTime = start,
                rate = if (s.editingDayOff) 0.0 else s.editingRate,
                isDayOff = s.editingDayOff,
                note = s.editingNote.trim()
            )
            if (entity.id == 0L) dao.insert(entity) else dao.update(entity)
            _state.value = _state.value.copy(
                editingDate = null,
                editingShift = null,
                savedFlash = "Сохранено"
            )
            refresh()
        }
    }

    fun deleteCurrent() {
        val date = _state.value.editingDate ?: return
        viewModelScope.launch {
            dao.deleteByDate(date.iso())
            _state.value = _state.value.copy(
                editingDate = null,
                editingShift = null,
                savedFlash = "Удалено"
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
}
