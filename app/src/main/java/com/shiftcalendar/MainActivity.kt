package com.shiftcalendar

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
            MaterialTheme(
                colorScheme = lightColorScheme(
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
            Header(state)
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
            Legend()
            Spacer(Modifier.height(6.dp))
            MonthTotals(state.current, onScrollToToday = {})
            Spacer(Modifier.height(12.dp))
            ShiftList(state.current, onClick = vm::openDay)
            Spacer(Modifier.height(32.dp))
        }
    }

    if (state.editingDate != null) {
        DayEditor(
            state = state,
            onKind = vm::setKind,
            onDayOff = vm::setDayOff,
            onStepHours = vm::stepHours,
            onRate = vm::setRate,
            onNote = vm::setNote,
            onSave = vm::save,
            onDelete = vm::deleteCurrent,
            onDismiss = vm::closeEditor
        )
    }
}

@Composable
private fun Header(state: UiState) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Text(
            "Календарь смен",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Palette.Ink
        )
    }
}
