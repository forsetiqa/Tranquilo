package com.tranquilo.alarm.core

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Считает, насколько активно человек шевелится.
 *
 * Работает так:
 *  1. Каждые 30 секунд сводит поток данных акселерометра в одно число — "эпоху".
 *  2. До начала окна пробуждения копит эпохи как фон этой ночи (калибровка).
 *  3. В окне сравнивает текущие эпохи с фоном: заметно выше — значит человек
 *     шевелится, это поверхностный сон, можно будить.
 */
class MovementAnalyzer(sensitivity: Int) {

    /** Во сколько раз движение должно превысить фон, чтобы считаться пробуждением. */
    private val multiplier = when (sensitivity.coerceIn(1, 5)) {
        1 -> 3.5   // будит редко, чаще досидит до конца окна
        2 -> 2.8
        3 -> 2.2
        4 -> 1.8
        else -> 1.5 // будит при малейшем шевелении
    }

    // Плавная оценка гравитации. Вычитаем её из показаний — остаётся только движение.
    // Так не важно, как именно откалиброван датчик конкретных часов.
    private var gravity = -1.0

    private var epochStartMs = 0L
    private var sum = 0.0
    private var samples = 0

    private val baselineEpochs = ArrayList<Double>()
    private val recent = ArrayDeque<Boolean>()

    /** Фоновый уровень движений этой ночи. Известен после finishBaseline(). */
    var baseline: Double? = null
        private set

    /** Порог срабатывания. Известен после finishBaseline(). */
    var threshold: Double = Double.MAX_VALUE
        private set

    /**
     * Принимает одно измерение датчика.
     * Возвращает значение эпохи, если 30 секунд накопились, иначе null.
     */
    fun addSample(nowMs: Long, x: Float, y: Float, z: Float): Double? {
        if (epochStartMs == 0L) epochStartMs = nowMs

        val magnitude = sqrt((x * x + y * y + z * z).toDouble())
        gravity = if (gravity < 0) magnitude else gravity + GRAVITY_SMOOTHING * (magnitude - gravity)

        val delta = abs(magnitude - gravity)
        sum += if (delta < NOISE_FLOOR) 0.0 else delta
        samples++

        if (nowMs - epochStartMs < EPOCH_MS) return null

        val value = if (samples > 0) sum / samples else 0.0
        sum = 0.0
        samples = 0
        epochStartMs = nowMs
        return value
    }

    fun addBaselineEpoch(value: Double) {
        baselineEpochs.add(value)
    }

    /**
     * Фиксирует фон. Берём среднее по спокойной половине эпох — так случайный
     * переворот во время калибровки не задирает порог.
     */
    fun finishBaseline() {
        val quiet = baselineEpochs.sorted().let { it.take(max(1, it.size / 2)) }
        val b = if (quiet.isEmpty()) DEFAULT_BASELINE else quiet.average()
        baseline = b
        threshold = max(b * multiplier, ABSOLUTE_FLOOR)
    }

    /**
     * Момент для пробуждения: две активные эпохи из последних трёх.
     * Одиночный рывок не разбудит, минута возни — разбудит.
     */
    fun isWakeMoment(epochValue: Double): Boolean {
        recent.addLast(epochValue > threshold)
        if (recent.size > 3) recent.removeFirst()
        return recent.count { it } >= 2
    }

    companion object {
        const val EPOCH_MS = 30_000L

        // Все подбираемые числа — здесь. Их, скорее всего, придётся подкрутить
        // после первых ночей, глядя на строчку «Прошлый раз» в приложении.
        private const val GRAVITY_SMOOTHING = 0.02
        private const val NOISE_FLOOR = 0.04
        private const val ABSOLUTE_FLOOR = 0.06
        private const val DEFAULT_BASELINE = 0.03
    }
}
