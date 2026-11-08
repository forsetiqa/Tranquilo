package com.tranquilo.alarm.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.tranquilo.alarm.data.Journal
import com.tranquilo.alarm.data.JournalEvent
import com.tranquilo.alarm.data.SettingsStore

/** После перезагрузки часов системные будильники стираются — ставим заново. */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        // Перезагрузка в ночь перед будильником — отмечаем в журнале этого утра.
        val journal = Journal(context)
        val expected = journal.expectedDeadline()
        val now = System.currentTimeMillis()
        if (expected > now && expected - now < NIGHT_MS) {
            journal.log(expected, journal.expectedWindow(), JournalEvent.REBOOT, now)
        }

        AlarmScheduler.schedule(context, SettingsStore(context).load())
    }

    private companion object {
        const val NIGHT_MS = 16 * 60 * 60_000L
    }
}
