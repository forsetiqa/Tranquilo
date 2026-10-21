package com.tranquilo.alarm.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.tranquilo.alarm.data.SettingsStore

/** После перезагрузки часов системные будильники стираются — ставим заново. */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        AlarmScheduler.schedule(context, SettingsStore(context).load())
    }
}
