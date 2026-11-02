package com.shiftcalendar

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.shiftcalendar.model.ISO
import com.shiftcalendar.ui.*
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

class MainActivity : ComponentActivity() {

    /** Разрешение на уведомления нужно, чтобы напоминание «за час до смены» дошло. */
    private val requestNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            // Одна вьюмодель на всё приложение: тема и календарь берут одно состояние.
            val vm: ShiftViewModel = viewModel()
            val ui by vm.state.collectAsState()
            ShiftCalendarTheme(darkTheme = ui.darkTheme) {
                AppRoot(vm)
            }
        }
    }
}

@Composable
fun AppRoot(vm: ShiftViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(state.savedFlash) {
        state.savedFlash?.let {
            snackbar.showSnackbar(it)
            vm.clearFlash()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = Palette.Paper,
        contentColor = Palette.Ink
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Spacer(Modifier.height(8.dp))
            Header(onOpenPattern = vm::openPattern, darkTheme = state.darkTheme, onToggleTheme = vm::toggleTheme)

            // Основной экран месяца: сетка, легенда, статистика и кнопка удаления —
            // всё в одном месте (CalendarScreen.MonthScreen).
            MonthScreen(
                state = state,
                onSelectMonth = vm::selectMonth,
                onOpenDay = vm::openDay,
                onPrevMonth = vm::previousMonth,
                onNextMonth = vm::nextMonth,
                onPrevYear = vm::previousYear,
                onNextYear = vm::nextYear,
                onClearShifts = vm::requestClearShifts,
                onClearShiftsConfirm = vm::confirmClearShifts,
                onClearShiftsDismiss = vm::dismissClearShifts
            )
        }
    }

    if (state.patternOpen) {
        PatternScreen(
            state = state,
            onClose = vm::closePattern,
            onPreset = vm::loadPreset,
            onAdd = vm::addStep,
            onRemove = vm::removeStep,
            onUpdate = vm::updateStep,
            onStepStart = vm::setStepStart,
            onStepEnd = vm::setStepEnd,
            onNudgeStepStart = vm::nudgeStepStart,
            onNudgeStepEnd = vm::nudgeStepEnd,
            onStepBreak = vm::setStepBreak,
            onAllRates = vm::setAllRates,
            onStart = vm::setPatternStart,
            onThreshold = vm::setNightThreshold,
            onNightEnd = vm::setNightEnd,
            onPercent = vm::setNightPercent,
            onUntil = vm::setPatternUntil,
            onAlign = vm::setAlignToMonth,
            onPreview = vm::previewPattern,
            onApply = vm::applyPattern,
            onSelectProfile = vm::loadProfile,
            onNewProfile = vm::newProfile,
            onSaveProfile = vm::saveProfile,
            onDeleteProfile = vm::deleteProfile,
            onProfileName = vm::setProfileName,
            onWorkColor = vm::setWorkColor,
            onDayOffColor = vm::setDayOffColor
        )
    }

    if (state.editingDate != null) {
        DayEditor(
            state = state,
            onDayOff = vm::setDayOff,
            onRate = vm::setRate,
            onNote = vm::setNote,
            onKind = vm::setKind,
            onStartText = { s -> vm.setInterval(s, state.editingEndTime) },
            onEndText = { e -> vm.setInterval(state.editingStartTime, e) },
            onPickStart = vm::pickStart,
            onPickEnd = vm::pickEnd,
            onNudgeStart = vm::nudgeStart,
            onNudgeEnd = vm::nudgeEnd,
            onBreak = vm::setBreakMinutes,
            onSave = vm::save,
            onDelete = vm::deleteCurrent,
            onClose = vm::closeEditor
        )
    }
}

@Composable
private fun Header(onOpenPattern: () -> Unit, darkTheme: Boolean, onToggleTheme: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                "Календарь смен",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Palette.Ink
            )
        }
        TextButton(
            onClick = onToggleTheme,
            colors = ButtonDefaults.textButtonColors(contentColor = Palette.Ink),
            modifier = Modifier.semantics {
                contentDescription = if (darkTheme) "Включить светлую тему" else "Включить тёмную тему"
            }
        ) {
            Text(if (darkTheme) "☀" else "☾", color = Palette.Ink, fontSize = 20.sp)
        }
        Box(
            Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(Palette.Ink)
                .clickable(onClick = onOpenPattern)
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Text("Паттерн", color = Palette.OnInk, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}
