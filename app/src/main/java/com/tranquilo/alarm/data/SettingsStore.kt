package com.tranquilo.alarm.data

import android.content.Context

class SettingsStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("tranquilo", Context.MODE_PRIVATE)

    fun load(): AlarmSettings {
        val d = AlarmSettings()
        return AlarmSettings(
            enabled = prefs.getBoolean(KEY_ENABLED, d.enabled),
            windowEndHour = prefs.getInt(KEY_END_HOUR, d.windowEndHour),
            windowEndMinute = prefs.getInt(KEY_END_MINUTE, d.windowEndMinute),
            windowMinutes = prefs.getInt(KEY_WINDOW, d.windowMinutes),
            sensitivity = prefs.getInt(KEY_SENSITIVITY, d.sensitivity),
            soundEnabled = prefs.getBoolean(KEY_SOUND, d.soundEnabled)
        )
    }

    fun save(s: AlarmSettings) {
        prefs.edit()
            .putBoolean(KEY_ENABLED, s.enabled)
            .putInt(KEY_END_HOUR, s.windowEndHour)
            .putInt(KEY_END_MINUTE, s.windowEndMinute)
            .putInt(KEY_WINDOW, s.windowMinutes)
            .putInt(KEY_SENSITIVITY, s.sensitivity)
            .putBoolean(KEY_SOUND, s.soundEnabled)
            .apply()
    }

    fun saveReport(r: NightReport) {
        prefs.edit()
            .putLong(KEY_R_TIME, r.ringedAtMillis)
            .putBoolean(KEY_R_SMART, r.smart)
            .putFloat(KEY_R_BASE, r.baseline.toFloat())
            .putFloat(KEY_R_THRESH, r.threshold.toFloat())
            .putFloat(KEY_R_PEAK, r.peak.toFloat())
            .apply()
    }

    fun loadReport(): NightReport? {
        val time = prefs.getLong(KEY_R_TIME, 0L)
        if (time == 0L) return null
        return NightReport(
            ringedAtMillis = time,
            smart = prefs.getBoolean(KEY_R_SMART, false),
            baseline = prefs.getFloat(KEY_R_BASE, 0f).toDouble(),
            threshold = prefs.getFloat(KEY_R_THRESH, 0f).toDouble(),
            peak = prefs.getFloat(KEY_R_PEAK, 0f).toDouble()
        )
    }

    /** Крайний срок (момент времени в мс), на который будильник уже прозвонил. */
    fun saveHandledDeadline(deadlineMs: Long) {
        prefs.edit().putLong(KEY_HANDLED_DEADLINE, deadlineMs).apply()
    }

    fun loadHandledDeadline(): Long = prefs.getLong(KEY_HANDLED_DEADLINE, 0L)

    private companion object {
        const val KEY_ENABLED = "enabled"
        const val KEY_END_HOUR = "end_hour"
        const val KEY_END_MINUTE = "end_minute"
        const val KEY_WINDOW = "window_minutes"
        const val KEY_SENSITIVITY = "sensitivity"
        const val KEY_SOUND = "sound"

        const val KEY_R_TIME = "report_time"
        const val KEY_R_SMART = "report_smart"
        const val KEY_R_BASE = "report_baseline"
        const val KEY_R_THRESH = "report_threshold"
        const val KEY_R_PEAK = "report_peak"

        const val KEY_HANDLED_DEADLINE = "handled_deadline"
    }
}
