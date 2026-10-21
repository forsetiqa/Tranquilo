package com.tranquilo.alarm.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.tranquilo.alarm.data.AlarmSettings
import com.tranquilo.alarm.data.SettingsStore
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

object AlarmScheduler {

    const val ACTION_START_MONITOR = "com.tranquilo.alarm.START_MONITOR"
    const val ACTION_RING = "com.tranquilo.alarm.RING"

    private const val RC_MONITOR = 1001
    private const val RC_RING = 1002

    /** Ставит два будильника: включение датчика и запасной звонок в конце окна. */
    fun schedule(context: Context, settings: AlarmSettings) {
        cancelAll(context)
        if (!settings.enabled) return

        val now = System.currentTimeMillis()
        var endMs = nextOccurrence(settings.windowEnd, now)

        // На этот срок будильник уже прозвонил (например, разбудил по движению в 08:10
        // при сроке 08:30) — не запускаем мониторинг повторно, ставим на следующие сутки.
        if (endMs == SettingsStore(context).loadHandledDeadline()) {
            endMs = nextOccurrence(settings.windowEnd, endMs)
        }

        val monitorMs = endMs - (settings.windowMinutes + AlarmSettings.BASELINE_MINUTES) * 60_000L

        // Если мы уже внутри периода мониторинга — стартуем прямо сейчас.
        val startAt = if (monitorMs <= now) now + 1_000L else monitorMs

        setExact(context, startAt, pendingIntent(context, ACTION_START_MONITOR, RC_MONITOR))
        setExact(context, endMs, pendingIntent(context, ACTION_RING, RC_RING))
    }

    fun cancelBackupRing(context: Context) {
        alarmManager(context).cancel(pendingIntent(context, ACTION_RING, RC_RING))
    }

    fun cancelAll(context: Context) {
        val am = alarmManager(context)
        am.cancel(pendingIntent(context, ACTION_START_MONITOR, RC_MONITOR))
        am.cancel(pendingIntent(context, ACTION_RING, RC_RING))
    }

    /**
     * Ближайшее наступление указанного времени суток строго после момента [afterMs].
     * Корректно работает через полночь.
     */
    private fun nextOccurrence(time: LocalTime, afterMs: Long): Long {
        val after = Instant.ofEpochMilli(afterMs).atZone(ZoneId.systemDefault())
        var target = after.withHour(time.hour).withMinute(time.minute).withSecond(0).withNano(0)
        if (!target.isAfter(after)) target = target.plusDays(1)
        return target.toInstant().toEpochMilli()
    }

    private fun setExact(context: Context, atMillis: Long, pi: PendingIntent) {
        val am = alarmManager(context)
        // На Android 12+ точные будильники требуют разрешения. Если его нет —
        // ставим неточный, он сработает с опозданием в несколько минут, но не упадёт.
        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
        if (canExact) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pi)
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pi)
        }
    }

    private fun alarmManager(context: Context) =
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    private fun pendingIntent(context: Context, action: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).setAction(action)
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
