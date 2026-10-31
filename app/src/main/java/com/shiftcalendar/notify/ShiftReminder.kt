package com.shiftcalendar.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.shiftcalendar.MainActivity
import com.shiftcalendar.R
import com.shiftcalendar.data.Shift
import com.shiftcalendar.model.*
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Напоминание «за час до смены». Ставится системным планировщиком
 * при сохранении смены, поэтому работает без запущенного приложения.
 */
object ShiftAlarmScheduler {

    const val CHANNEL_ID = "shift_reminder"
    const val EXTRA_TITLE = "title"
    const val EXTRA_TEXT = "text"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Напоминание о смене",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Предупреждает за час до начала смены"
        }
        nm.createNotificationChannel(channel)
    }

    /** Запланировать напоминание за час до начала смены. */
    fun schedule(context: Context, shift: Shift) {
        if (shift.isDayOff) return
        val startTime = shift.startTime?.let { LocalTime.parse(it) } ?: return
        val date = LocalDate.parse(shift.date, ISO)
        val start = LocalDateTime.of(date, startTime)
        val trigger = start.minusHours(1)

        // только на будущее
        if (!trigger.isAfter(LocalDateTime.now())) return

        val intent = Intent(context, ShiftReminderReceiver::class.java).apply {
            putExtra(EXTRA_TITLE, titleFor(shift))
            putExtra(EXTRA_TEXT, textFor(shift, startTime))
            // requestCode по id смены, чтобы напоминания не перекрывали друг друга
            putExtra("id", shift.id)
        }
        ShiftAlarmBridge.setExact(context, shift.id, trigger, intent)
    }

    fun cancel(context: Context, shift: Shift) {
        ShiftAlarmBridge.cancel(context, shift.id)
    }

    private fun titleFor(shift: Shift): String = when (shift.kind) {
        "NIGHT" -> "Через час — ночная смена"
        "DAY" -> "Через час — дневная смена"
        else -> "Через час — смена"
    }

    private fun textFor(shift: Shift, start: LocalTime): String {
        val end = when (shift.kind) {
            "NIGHT" -> NIGHT_END
            "DAY" -> DAY_END
            else -> null
        }
        val interval = if (end != null) " $start – $end" else ""
        val money = if (shift.rate > 0) " · ${formatMoney(shift.hours * shift.rate)}" else ""
        return "${formatHours(shift.hours)}$interval$money"
    }
}

/**
 * Приёмник напоминания: показывает уведомление.
 */
class ShiftReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra(ShiftAlarmScheduler.EXTRA_TITLE) ?: "Напоминание о смене"
        val text = intent.getStringExtra(ShiftAlarmScheduler.EXTRA_TEXT) ?: ""

        // Начиная с Android 13 уведомления требуют разрешения.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        ShiftAlarmScheduler.ensureChannel(context)

        val openApp = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            context,
            intent.getIntExtra("id", 0).toInt(),
            openApp,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, ShiftAlarmScheduler.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notify)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()

        NotificationManagerCompat.from(context)
            .notify(intent.getIntExtra("id", 0).toInt(), notification)
    }
}
