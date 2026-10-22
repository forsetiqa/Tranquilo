package com.tranquilo.alarm.core

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Считает, насколько активно человек шевелится.
 *
 * Работает так:
 *  1. Каждые 30 секунд сводит поток данных акселерометра в одно число — "эпоху".
 *     Это самое сильное движение за эти 30 секунд (как в классической актиграфии).
 *  2. До начала окна пробуждения копит эпохи как фон этой ночи (калибровка).
 *  3. В окне сравнивает эпохи с фоном: движение заметно выше фона — значит человек
 *     ворочается, это поверхностный сон, можно будить.
 */
class MovementAnalyzer(sensitivity: Int) {

    /** На сколько движение должно превысить фон, чтобы разбудить (м/с²). */
    private val step = when (sensitivity.coerceIn(1, 5)) {
        1 -> 1.2   // будит только на явном перевороте
        2 -> 0.8
        3 -> 0.5
        4 -> 0.3
        else -> 0.2 // будит при малейшем шевелении
    }

    // Плавная оценка гравитации. Вычитаем её из показаний — остаётся только движение.
    // Так не важно, как именно откалиброван датчик конкретных часов.
    private var gravity = -1.0

    private var epochStartMs = 0L
    private var epochMax = 0.0

    private val baselineEpochs = ArrayList<Double>()

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
        epochMax = max(epochMax, abs(magnitude - gravity))

        if (nowMs - epochStartMs < EPOCH_MS) return null

        val value = epochMax
        epochMax = 0.0
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
        threshold = b + step
    }

    /**
     * Момент для пробуждения — одно заметное движение. В глубоком сне тело почти
     * неподвижно, поэтому даже короткий переворот означает поверхностный сон.
     */
    fun isWakeMoment(epochValue: Double): Boolean = epochValue > threshold

    companion object {
        const val EPOCH_MS = 30_000L

        // Подбираемые числа — здесь и в `step` выше. Подкручивать по графику
        // прошлой ночи на главном экране.
        private const val GRAVITY_SMOOTHING = 0.02
        private const val DEFAULT_BASELINE = 0.05
    }
}
