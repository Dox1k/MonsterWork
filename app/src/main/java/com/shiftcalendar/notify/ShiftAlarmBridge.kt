package com.shiftcalendar.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Мост к системному AlarmManager. Вынесено отдельно, чтобы логика
 * планирования была в одном месте и её было видно на экране разрешений.
 */
object ShiftAlarmBridge {

    private const val REQUEST_OFFSET = 100_000

    private fun pendingIntent(context: Context, shiftId: Long, intent: Intent): PendingIntent {
        return PendingIntent.getBroadcast(
            context,
            (REQUEST_OFFSET + shiftId).toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun setExact(context: Context, shiftId: Long, trigger: LocalDateTime, intent: Intent) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pi = pendingIntent(context, shiftId, intent)
        val millis = trigger.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
            } else {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
            }
        } catch (_: SecurityException) {
            am.set(AlarmManager.RTC_WAKEUP, millis, pi)
        }
    }

    fun cancel(context: Context, shiftId: Long) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, ShiftReminderReceiver::class.java)
        am.cancel(pendingIntent(context, shiftId, intent))
    }
}
