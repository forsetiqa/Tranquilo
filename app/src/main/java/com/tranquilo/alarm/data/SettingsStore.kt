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
            .putInt(KEY_R_WINDOW, r.windowMinutes)
            .putString(KEY_R_EPOCHS, r.epochs.joinToString(",") { "${it.secondsToEnd}:${it.value}:${it.hr}" })
            .apply()
    }

    /** Формат: "секунды:движение:пульс" через запятую. Старые записи — без пульса. */
    private fun parseEpochs(raw: String?): List<Epoch> =
        raw.orEmpty().split(",").mapNotNull { item ->
            val parts = item.split(":")
            if (parts.size !in 2..3) return@mapNotNull null
            val seconds = parts[0].toIntOrNull() ?: return@mapNotNull null
            val value = parts[1].toFloatOrNull() ?: return@mapNotNull null
            val hr = parts.getOrNull(2)?.toFloatOrNull() ?: 0f
            Epoch(seconds, value, hr)
        }

    fun loadReport(): NightReport? {
        val time = prefs.getLong(KEY_R_TIME, 0L)
        if (time == 0L) return null
        return NightReport(
            ringedAtMillis = time,
            smart = prefs.getBoolean(KEY_R_SMART, false),
            baseline = prefs.getFloat(KEY_R_BASE, 0f).toDouble(),
            threshold = prefs.getFloat(KEY_R_THRESH, 0f).toDouble(),
            peak = prefs.getFloat(KEY_R_PEAK, 0f).toDouble(),
            windowMinutes = prefs.getInt(KEY_R_WINDOW, 30),
            epochs = parseEpochs(prefs.getString(KEY_R_EPOCHS, null))
        )
    }

    /** Крайний срок (момент времени в мс), на который будильник уже прозвонил. */
    fun saveHandledDeadline(deadlineMs: Long) {
        prefs.edit().putLong(KEY_HANDLED_DEADLINE, deadlineMs).apply()
    }

    fun loadHandledDeadline(): Long = prefs.getLong(KEY_HANDLED_DEADLINE, 0L)

    // --- «Лёг спать» -------------------------------------------------------

    fun startBedtime(nowMs: Long) {
        prefs.edit().putLong(KEY_BED_ACTIVE, nowMs).apply()
    }

    /** Когда нажали «Лёг спать», если поиск засыпания ещё идёт; иначе 0. */
    fun bedtimeActiveSince(): Long = prefs.getLong(KEY_BED_ACTIVE, 0L)

    fun cancelBedtime() {
        prefs.edit().putLong(KEY_BED_ACTIVE, 0L).apply()
    }

    /** latencyMin = null — засыпание за отведённое время не нашлось. */
    fun finishBedtime(bedtimeMs: Long, latencyMin: Int?) {
        val history = if (latencyMin == null) latencyHistory()
        else (latencyHistory() + latencyMin).takeLast(HISTORY_SIZE)
        prefs.edit()
            .putLong(KEY_BED_ACTIVE, 0L)
            .putLong(KEY_BED_LAST_START, bedtimeMs)
            .putInt(KEY_BED_LAST_LATENCY, latencyMin ?: -1)
            .putString(KEY_BED_HISTORY, history.joinToString(","))
            .apply()
    }

    fun lastBedtime(): BedtimeResult? {
        val start = prefs.getLong(KEY_BED_LAST_START, 0L)
        if (start == 0L) return null
        val latency = prefs.getInt(KEY_BED_LAST_LATENCY, -1)
        return BedtimeResult(start, latency.takeIf { it >= 0 })
    }

    /** Время засыпания за последние ночи, в минутах. Пригодится для «По норме сна». */
    fun latencyHistory(): List<Int> =
        prefs.getString(KEY_BED_HISTORY, null).orEmpty()
            .split(",").mapNotNull { it.toIntOrNull() }

    private companion object {
        const val HISTORY_SIZE = 14
        const val KEY_BED_ACTIVE = "bedtime_active"
        const val KEY_BED_LAST_START = "bedtime_last_start"
        const val KEY_BED_LAST_LATENCY = "bedtime_last_latency"
        const val KEY_BED_HISTORY = "bedtime_history"

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
        const val KEY_R_WINDOW = "report_window"
        const val KEY_R_EPOCHS = "report_epochs"

        const val KEY_HANDLED_DEADLINE = "handled_deadline"
    }
}
