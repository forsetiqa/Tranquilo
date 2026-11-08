package com.tranquilo.alarm.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.material.Vignette
import androidx.wear.compose.material.VignettePosition
import com.tranquilo.alarm.data.JournalEvent
import com.tranquilo.alarm.data.Morning
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Журнал последних утр: что делал будильник и где был сбой. */
@Composable
fun JournalScreen(mornings: List<Morning>, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val listState = rememberScalingLazyListState()

    Scaffold(
        timeText = { TimeText() },
        vignette = { Vignette(vignettePosition = VignettePosition.TopAndBottom) },
        positionIndicator = { PositionIndicator(scalingLazyListState = listState) }
    ) {
        ScalingLazyColumn(
            modifier = Modifier.fillMaxWidth(),
            state = listState
        ) {
            item {
                Text(
                    text = "Журнал",
                    style = MaterialTheme.typography.title3,
                    color = MaterialTheme.colors.primary,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }

            if (mornings.isEmpty()) {
                item {
                    Note("Пока пусто. Записи появятся после первого утра с включённым будильником.")
                }
            } else {
                item { Note("Последние утра, хранятся только на часах. ⚠ — сбой: будильник мог не разбудить.") }
                items(mornings) { MorningCard(it) }
            }

            item { WideButton(text = "Назад", onClick = onBack) }
        }
    }
}

@Composable
private fun Note(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.caption3,
        color = MaterialTheme.colors.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
    )
}

@Composable
private fun MorningCard(m: Morning) {
    val colors = MaterialTheme.colors
    val windowStart = m.deadlineMs - m.windowMinutes * 60_000L

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(colors.surface)
            .padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 10.dp)
    ) {
        Text(
            text = dayLabel(m.deadlineMs),
            style = MaterialTheme.typography.button,
            color = if (m.hasProblem) PROBLEM_COLOR else colors.onSurface
        )
        Text(
            text = "окно ${clock(windowStart)}–${clock(m.deadlineMs)}",
            style = MaterialTheme.typography.caption2,
            color = colors.onSurface.copy(alpha = 0.6f)
        )
        m.entries.sortedBy { it.atMs }.forEach { e ->
            // Сбои, найденные задним числом, без времени: точного момента у них нет.
            val text = when {
                e.event in DETECTED -> "⚠ ${e.event.label}"
                e.event.problem -> "${clock(e.atMs)}  ⚠ ${e.event.label}"
                else -> "${clock(e.atMs)}  ${e.event.label}"
            }
            Text(
                text = text,
                style = MaterialTheme.typography.caption2,
                color = if (e.event.problem) PROBLEM_COLOR else colors.onSurface.copy(alpha = 0.85f)
            )
        }
    }
}

/** Коротко, как прошло утро: сбой, звонок или что сейчас происходит. */
internal fun morningSummary(m: Morning): String {
    m.entries.firstOrNull { it.event.problem }?.let { return "⚠ ${it.event.label}" }
    m.entries.firstOrNull { it.event == JournalEvent.RANG_SMART || it.event == JournalEvent.RANG_END }?.let {
        val how = if (it.event == JournalEvent.RANG_SMART) "по движению" else "в конце окна"
        return "звонок ${clock(it.atMs)}, $how"
    }
    if (m.has(JournalEvent.DISABLED)) return "будильник выключили"
    if (m.has(JournalEvent.MONITOR_STARTED)) return "датчик работает"
    return "ждёт утра"
}

/** «Вс, 8 ноября». */
internal fun dayLabel(ms: Long): String =
    DAY_FORMAT.format(Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()))
        .replaceFirstChar { it.uppercase() }

/** «из 1 утра», «из 7 утр». */
internal fun morningsWord(n: Int): String =
    if (n % 10 == 1 && n % 100 != 11) "утра" else "утр"

private fun clock(ms: Long): String {
    val t = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalTime()
    return "%02d:%02d".format(t.hour, t.minute)
}

private val DAY_FORMAT = DateTimeFormatter.ofPattern("EE, d MMMM", Locale.forLanguageTag("ru"))

/** Сбои, которые журнал находит сам, задним числом. */
private val DETECTED = setOf(
    JournalEvent.NOT_STARTED,
    JournalEvent.MONITOR_INTERRUPTED,
    JournalEvent.RING_INTERRUPTED
)

private val PROBLEM_COLOR = Color(0xFFE08A8A)
