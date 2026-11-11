package com.tranquilo.alarm.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Что может случиться с будильником за утро. problem = показать как сбой. */
enum class JournalEvent(val label: String, val problem: Boolean = false) {
    MONITOR_STARTED("датчик включился"),
    RANG_SMART("звонок по движению"),
    RANG_END("звонок в конце окна"),
    VIBRATION_STARTED("вибрация включилась"),
    DISMISSED_SCREEN("выключен кнопкой"),
    DISMISSED_NOTIFICATION("выключен из уведомления"),
    DISABLED("будильник выключен в настройках"),
    REBOOT("часы перезагружались", problem = true),
    NOT_STARTED("не запускался", problem = true),
    MONITOR_INTERRUPTED("датчик остановился до звонка", problem = true),
    RING_INTERRUPTED("звонок прервался без выключения", problem = true)
}

data class JournalEntry(val atMs: Long, val event: JournalEvent)

/** Одно утро: крайний срок, длина окна и что происходило. */
data class Morning(val deadlineMs: Long, val windowMinutes: Int, val entries: List<JournalEntry>) {
    val hasProblem: Boolean get() = entries.any { it.event.problem }
    fun has(event: JournalEvent) = entries.any { it.event == event }
    val rang: Boolean get() = has(JournalEvent.RANG_SMART) || has(JournalEvent.RANG_END)
    val dismissed: Boolean
        get() = has(JournalEvent.DISMISSED_SCREEN) || has(JournalEvent.DISMISSED_NOTIFICATION)
}

/**
 * Журнал последних утр. Хранится только на часах.
 *
 * Помимо событий, которые записывают сервис и планировщик, сам находит сбои:
 *  - срок прошёл, а записей нет — «не запускался»;
 *  - датчик включился, а звонка не было — «датчик остановился до звонка»;
 *  - звонок был, а выключения нет — «звонок прервался без выключения».
 */
class Journal(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("tranquilo_journal", Context.MODE_PRIVATE)

    /** Утра, новые сверху. */
    fun mornings(): List<Morning> = load().sortedByDescending { it.deadlineMs }

    /** Записывает событие к утру с этим сроком. Одно и то же событие за утро — один раз. */
    fun log(deadlineMs: Long, windowMinutes: Int, event: JournalEvent, atMs: Long = System.currentTimeMillis()) =
        synchronized(LOCK) {
            val list = load().toMutableList()
            val index = list.indexOfFirst { it.deadlineMs == deadlineMs }
            val morning = if (index >= 0) list[index] else Morning(deadlineMs, windowMinutes, emptyList())
            if (morning.has(event)) return@synchronized
            val updated = morning.copy(entries = morning.entries + JournalEntry(atMs, event))
            if (index >= 0) list[index] = updated else list += updated
            save(list)
        }

    // --- ожидаемое утро: на него сейчас поставлен будильник --------------------

    fun setExpected(deadlineMs: Long, windowMinutes: Int) {
        prefs.edit().putLong(KEY_EXPECTED, deadlineMs).putInt(KEY_EXPECTED_WINDOW, windowMinutes).apply()
    }

    fun clearExpected() {
        prefs.edit().putLong(KEY_EXPECTED, 0L).apply()
    }

    fun expectedDeadline(): Long = prefs.getLong(KEY_EXPECTED, 0L)

    fun expectedWindow(): Int = prefs.getInt(KEY_EXPECTED_WINDOW, 30)

    /** Ищет сбои в прошедших утрах и дописывает их. Вызывать до того, как поменяется ожидаемое утро. */
    fun checkProblems(nowMs: Long = System.currentTimeMillis()) = synchronized(LOCK) {
        val list = load().toMutableList()
        var changed = false

        val expected = expectedDeadline()
        if (expected > 0 && expected + GRACE_MS < nowMs && list.none { it.deadlineMs == expected }) {
            list += Morning(expected, expectedWindow(), listOf(JournalEntry(expected, JournalEvent.NOT_STARTED)))
            changed = true
        }

        for (i in list.indices) {
            val m = list[i]
            val extra = ArrayList<JournalEntry>()
            if (m.deadlineMs + GRACE_MS < nowMs &&
                m.has(JournalEvent.MONITOR_STARTED) && !m.rang &&
                !m.has(JournalEvent.DISABLED) && !m.has(JournalEvent.MONITOR_INTERRUPTED)
            ) {
                extra += JournalEntry(m.deadlineMs, JournalEvent.MONITOR_INTERRUPTED)
            }
            if (m.deadlineMs + RING_GRACE_MS < nowMs &&
                m.rang && !m.dismissed &&
                !m.has(JournalEvent.DISABLED) && !m.has(JournalEvent.RING_INTERRUPTED)
            ) {
                extra += JournalEntry(m.deadlineMs, JournalEvent.RING_INTERRUPTED)
            }
            if (extra.isNotEmpty()) {
                list[i] = m.copy(entries = m.entries + extra)
                changed = true
            }
        }

        if (changed) save(list)
    }

    // --- хранение ------------------------------------------------------------

    private fun load(): List<Morning> {
        val raw = prefs.getString(KEY_MORNINGS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).map { i ->
                val o = array.getJSONObject(i)
                val events = o.getJSONArray("e")
                Morning(
                    deadlineMs = o.getLong("d"),
                    windowMinutes = o.getInt("w"),
                    entries = (0 until events.length()).mapNotNull { j ->
                        val e = events.getJSONArray(j)
                        val type = runCatching { JournalEvent.valueOf(e.getString(1)) }.getOrNull()
                        type?.let { JournalEntry(e.getLong(0), it) }
                    }
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun save(list: List<Morning>) {
        val array = JSONArray()
        list.sortedByDescending { it.deadlineMs }.take(MAX_MORNINGS).forEach { m ->
            val events = JSONArray()
            m.entries.sortedBy { it.atMs }.forEach { events.put(JSONArray().put(it.atMs).put(it.event.name)) }
            array.put(JSONObject().put("d", m.deadlineMs).put("w", m.windowMinutes).put("e", events))
        }
        prefs.edit().putString(KEY_MORNINGS, array.toString()).apply()
    }

    private companion object {
        val LOCK = Any()
        const val MAX_MORNINGS = 14
        /** Через сколько после срока считать, что звонка уже не будет. */
        const val GRACE_MS = 10 * 60_000L
        /** Через сколько после срока считать, что звонок так и не выключили. */
        const val RING_GRACE_MS = 60 * 60_000L

        const val KEY_MORNINGS = "mornings"
        const val KEY_EXPECTED = "expected_deadline"
        const val KEY_EXPECTED_WINDOW = "expected_window"
    }
}
