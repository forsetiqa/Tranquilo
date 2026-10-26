package com.tranquilo.alarm.core

/**
 * Ищет момент засыпания по движениям руки.
 *
 * Засыпание — начало первого 10-минутного отрезка, в котором было не больше одного
 * заметного движения. Один допуск нужен, потому что при засыпании человек ещё
 * поправляет руку или вздрагивает. Так засыпание определяют в актиграфии.
 *
 * Ограничение: если долго лежать совсем неподвижно без сна, это тоже посчитается сном.
 */
class SleepOnsetDetector {

    /** Последние эпохи: момент начала эпохи и была ли она спокойной. */
    private val recent = ArrayDeque<Pair<Long, Boolean>>()

    /**
     * Принимает эпоху из [MovementAnalyzer]. Возвращает момент засыпания (мс),
     * как только он найден, иначе null.
     */
    fun addEpoch(epochEndMs: Long, value: Double): Long? {
        recent.addLast((epochEndMs - MovementAnalyzer.EPOCH_MS) to (value < QUIET_LEVEL))
        if (recent.size > WINDOW_EPOCHS) recent.removeFirst()
        if (recent.size < WINDOW_EPOCHS) return null

        val active = recent.count { !it.second }
        if (active > MAX_ACTIVE_EPOCHS) return null

        return recent.first { it.second }.first
    }

    companion object {
        /** Эпоха спокойная, если самое сильное движение в ней слабее этого (м/с²). */
        const val QUIET_LEVEL = 0.15

        /** 20 эпох по 30 секунд — 10 минут. */
        const val WINDOW_EPOCHS = 20

        /** Сколько заметных движений допускается внутри этих 10 минут. */
        const val MAX_ACTIVE_EPOCHS = 1

        /** Дольше трёх часов не ищем: значит, что-то пошло не так. */
        const val MAX_SEARCH_MS = 3 * 60 * 60_000L
    }
}
