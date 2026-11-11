package com.tranquilo.alarm.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import androidx.core.content.ContextCompat
import com.tranquilo.alarm.service.SleepMonitorService

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val service = Intent(context, SleepMonitorService::class.java)

        when (intent.action) {
            AlarmScheduler.ACTION_START_MONITOR ->
                service.action = SleepMonitorService.ACTION_MONITOR

            AlarmScheduler.ACTION_RING ->
                service.action = SleepMonitorService.ACTION_RING

            else -> return
        }

        // Система держит часы бодрствующими, только пока идёт onReceive, а сервис стартует
        // чуть позже. Без этой страховки часы могут уснуть в промежутке, и звонок задержится.
        // Сервис сразу берёт свою блокировку, эта отпустится сама через минуту.
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "tranquilo:alarm-start").apply {
            setReferenceCounted(false)
            acquire(START_TIMEOUT_MS)
        }

        ContextCompat.startForegroundService(context, service)
    }

    private companion object {
        const val START_TIMEOUT_MS = 60_000L
    }
}
