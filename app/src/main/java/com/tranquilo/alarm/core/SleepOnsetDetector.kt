package com.tranquilo.alarm.core

/**
 * Ищет момент засыпания по движениям руки и пульсу.
 *
 * Засыпание — начало первого 10-минутного отрезка, в котором:
 *  - было не больше одного заметного движения (при засыпании человек ещё вздрагивает
 *    и поправляет руку — один допуск на это);
 *  - пульс заметно ниже, чем в первые минуты после «Лёг спать». При засыпании пульс
 *    падает, а у человека, который лежит тихо, но не спит, почти не меняется.
 *
 * Нет пульса — решаем только по движению. Пульс так и не упал, а человек лежит
 * неподвижно полчаса — тоже засчитываем: настолько долгая неподвижность почти всегда сон.
 */
class SleepOnsetDetector {

    private class Item(val startMs: Long, val quiet: Boolean, val hr: Float)

    private val recent = ArrayDeque<Item>()
    private val referenceHr = ArrayList<Float>()
    private var epochsSeen = 0

    /** С какого момента движения непрерывно удовлетворяют условию «тихо». */
    private var stillSinceMs: Long? = null

    /**
     * Принимает эпоху из [MovementAnalyzer] и средний пульс за неё (0 — пульса нет).
     * Возвращает момент засыпания (мс), как только он найден, иначе null.
     */
    fun addEpoch(epochEndMs: Long, value: Double, hr: Float = 0f): Long? {
        epochsSeen++
        if (epochsSeen <= REFERENCE_EPOCHS && hr > 0f) referenceHr.add(hr)

        recent.addLast(Item(epochEndMs - MovementAnalyzer.EPOCH_MS, value < QUIET_LEVEL, hr))
        if (recent.size > WINDOW_EPOCHS) recent.removeFirst()
        if (recent.size < WINDOW_EPOCHS) return null

        val still = recent.count { !it.quiet } <= MAX_ACTIVE_EPOCHS
        if (!still) {
            stillSinceMs = null
            return null
        }

        val onset = recent.first { it.quiet }.startMs
        val since = stillSinceMs ?: onset.also { stillSinceMs = it }

        if (heartRateConfirms()) return onset
        if (epochEndMs - since >= FALLBACK_STILL_MS) return since
        return null
    }

    /** Пульс подтверждает сон — или пульса недостаточно, и решаем по движению. */
    private fun heartRateConfirms(): Boolean {
        if (referenceHr.size < MIN_HR_EPOCHS) return true
        val window = recent.map { it.hr }.filter { it > 0f }
        if (window.size < WINDOW_EPOCHS / 2) return true
        return window.average() <= referenceHr.average() - HR_DROP_BPM
    }

    companion object {
        /** Эпоха спокойная, если самое сильное движение в ней слабее этого (м/с²). */
        const val QUIET_LEVEL = 0.15

        /** 20 эпох по 30 секунд — 10 минут. */
        const val WINDOW_EPOCHS = 20

        /** Сколько заметных движений допускается внутри этих 10 минут. */
        const val MAX_ACTIVE_EPOCHS = 1

        /** Ориентир пульса — первые 5 минут после нажатия (10 эпох). */
        const val REFERENCE_EPOCHS = 10

        /** Меньше стольких эпох с пульсом в ориентире — ориентиру не доверяем. */
        const val MIN_HR_EPOCHS = 4

        /** Насколько пульс во сне должен быть ниже ориентира (уд/мин). */
        const val HR_DROP_BPM = 4.0

        /** Неподвижность дольше этого засчитываем как сон, даже если пульс не упал. */
        const val FALLBACK_STILL_MS = 30 * 60_000L

        /** Дольше трёх часов не ищем: значит, что-то пошло не так. */
        const val MAX_SEARCH_MS = 3 * 60 * 60_000L
    }
}
