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
    val peak: Double,
    val windowMinutes: Int = 30,
    /** Все эпохи ночи: от начала калибровки до звонка. */
    val epochs: List<Epoch> = emptyList()
)

/**
 * Итог «Лёг спать»: когда нажал кнопку, за сколько минут уснул и в какой момент.
 * latencyMin и onsetMs = null — засыпание не определилось.
 */
data class BedtimeResult(val bedtimeMs: Long, val latencyMin: Int?, val onsetMs: Long?)

/**
 * Одна 30-секундная эпоха: сколько секунд оставалось до крайнего срока,
 * сила движения и средний пульс (0 — пульса нет).
 */
data class Epoch(val secondsToEnd: Int, val value: Float, val hr: Float = 0f)
