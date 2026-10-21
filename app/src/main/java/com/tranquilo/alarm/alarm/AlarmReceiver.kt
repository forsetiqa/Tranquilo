package com.tranquilo.alarm.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
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

        ContextCompat.startForegroundService(context, service)
    }
}
