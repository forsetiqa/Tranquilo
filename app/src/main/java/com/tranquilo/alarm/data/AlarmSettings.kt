package com.tranquilo.alarm.data

import java.time.LocalTime

data class AlarmSettings(
    val enabled: Boolean = false,
    val windowEndHour: Int = 8,
    val windowEndMinute: Int = 30,
    val windowMinutes: Int = 30,
    val sensitivity: Int = 3,
    val soundEnabled: Boolean = false
) {
    /** Крайний срок: в это время будильник звонит в любом случае. */
    val windowEnd: LocalTime get() = LocalTime.of(windowEndHour, windowEndMinute)

    /** Начало окна: раньше этого времени будильник не звонит. */
    val windowStart: LocalTime get() = windowEnd.minusMinutes(windowMinutes.toLong())

    /** Когда включается датчик, чтобы успеть измерить фон ночи. */
    val monitorStart: LocalTime get() = windowStart.minusMinutes(BASELINE_MINUTES.toLong())

    companion object {
        const val BASELINE_MINUTES = 60
    }
}

/** Что случилось прошлой ночью — чтобы утром понять, как отработал алгоритм. */
data class NightReport(
    val ringedAtMillis: Long,
    val smart: Boolean,
    val baseline: Double,
    val threshold: Double,
    val peak: Double
)
