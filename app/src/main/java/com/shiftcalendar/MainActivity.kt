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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.shiftcalendar.model.monthTitle
import com.shiftcalendar.ui.*
import java.time.LocalDate
import java.time.LocalDateTime
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
            val themeVm: ShiftViewModel = viewModel()
            val themeState by themeVm.state.collectAsState()
            MaterialTheme(
                colorScheme = if (themeState.darkTheme) darkColorScheme() else lightColorScheme(
                    background = Palette.Paper,
                    surface = Palette.Paper,
                    onBackground = Palette.Ink,
                    onSurface = Palette.Ink
                )
            ) {
                AppRoot()
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
        containerColor = Palette.Paper
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(Modifier.height(12.dp))
            Header(onOpenPattern = vm::openPattern, darkTheme = state.darkTheme, onToggleTheme = vm::toggleTheme)
            Spacer(Modifier.height(4.dp))
            MonthStrip(
                months = state.months,
                selected = state.selected,
                year = state.year,
                onSelect = vm::selectMonth,
                onPrevYear = vm::previousYear,
                onNextYear = vm::nextYear
            )
            Spacer(Modifier.height(16.dp))

            val byDate = remember(state.current) {
                state.current?.shifts?.associateBy { it.date } ?: emptyMap()
            }
            // Банка-прогресс: показывается, если смена идёт прямо сейчас.
            val now = LocalDateTime.now()
            val running = remember(state.months, now) { currentShift(state.months, now) }
            if (running != null) {
                BankProgressCard(
                    shift = running,
                    now = now,
                    onOpen = { vm.openDay(LocalDate.parse(running.date, com.shiftcalendar.model.ISO)) }
                )
            }
            MonthGrid(
                ym = state.selected,
                byDate = byDate,
                today = state.today,
                onDayClick = vm::openDay
            )
            Spacer(Modifier.height(10.dp))
            MonsterBanks(shift = running, now = now)
            Spacer(Modifier.height(10.dp))
            Legend()
            Spacer(Modifier.height(6.dp))
            ShiftList(state.current, onClick = vm::openDay)
            MonthTotals(state.current, onScrollToToday = {}, threshold = java.time.LocalTime.parse(state.nightThreshold), percent = state.nightPremiumPercent)
            Spacer(Modifier.height(32.dp))
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
            onAllRates = vm::setAllRates,
            onStart = vm::setPatternStart,
            onThreshold = vm::setNightThreshold,
            onPercent = vm::setNightPercent,
            onUntil = vm::setPatternUntil,
            onAlign = vm::setAlignToMonth,
            onKeep = vm::setKeepExisting,
            onPreview = vm::previewPattern,
            onApply = vm::applyPattern
        )
    }

    if (state.editingDate != null) {
        DayEditor(
            state = state,
            onKind = vm::setKind,
            onDayOff = vm::setDayOff,
            onStepHours = vm::stepHours,
            onRate = vm::setRate,
            onNote = vm::setNote,
            onInterval = vm::setInterval,
            onComplete = { state.editingShift?.let(vm::completeShift) },
            onSave = vm::save,
            onDelete = vm::deleteCurrent,
            onDismiss = vm::closeEditor
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
        TextButton(onClick = onToggleTheme) { Text(if (darkTheme) "☀" else "☾", color = Palette.Ink) }
        Box(
            Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(Palette.Ink)
                .clickable(onClick = onOpenPattern)
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Text("Паттерн", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}
