package com.tranquilo.alarm.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.material.Vignette
import androidx.wear.compose.material.VignettePosition
import com.tranquilo.alarm.BuildConfig
import com.tranquilo.alarm.data.AlarmSettings
import com.tranquilo.alarm.data.BedtimeResult
import com.tranquilo.alarm.data.NightReport
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.max
import kotlin.math.sqrt

@Composable
fun MainScreen(
    viewModel: MainViewModel,
    heartRateAccess: Boolean,
    onRequestHeartRate: () -> Unit,
    onEditTime: () -> Unit
) {
    val settings by viewModel.settings.collectAsState()
    val report by viewModel.report.collectAsState()
    val bedtime by viewModel.bedtime.collectAsState()
    val listState = rememberScalingLazyListState()

    Scaffold(
        timeText = { TimeText() },
        vignette = { Vignette(vignettePosition = VignettePosition.TopAndBottom) }
    ) {
        ScalingLazyColumn(
            modifier = Modifier.fillMaxWidth(),
            state = listState
        ) {
            item {
                Text(
                    text = "Tranquilo",
                    style = MaterialTheme.typography.title3,
                    color = MaterialTheme.colors.primary,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }

            item { StatusLine(settings) }

            item { BedtimeChip(bedtime, onClick = { viewModel.toggleBedtime() }) }

            item {
                OptionChip(
                    label = "Будильник",
                    options = listOf("Выкл", "Вкл"),
                    selected = if (settings.enabled) 1 else 0,
                    onClick = { viewModel.toggleEnabled() }
                )
            }

            item {
                Chip(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onEditTime,
                    colors = ChipDefaults.secondaryChipColors(),
                    label = { Text("Разбудить не позже") },
                    secondaryLabel = { Text(fmt(settings.windowEnd)) }
                )
            }

            item {
                OptionChip(
                    label = "Длина окна (минут)",
                    options = WINDOW_OPTIONS.map { it.toString() },
                    selected = WINDOW_OPTIONS.indexOf(settings.windowMinutes).coerceAtLeast(0),
                    onClick = { viewModel.cycleWindowLength() }
                )
            }

            item {
                OptionChip(
                    label = "Чувствительность",
                    options = (1..5).map { it.toString() },
                    selected = settings.sensitivity - 1,
                    onClick = {
                        val s = settings.sensitivity
                        viewModel.setSensitivity(if (s >= 5) 1 else s + 1)
                    }
                )
            }

            item {
                OptionChip(
                    label = "Сигнал",
                    options = listOf("Вибрация", "Со звуком"),
                    selected = if (settings.soundEnabled) 1 else 0,
                    onClick = { viewModel.toggleSound() }
                )
            }

            item {
                Chip(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { if (!heartRateAccess) onRequestHeartRate() },
                    colors = ChipDefaults.secondaryChipColors(),
                    label = { Text("Пульс") },
                    secondaryLabel = {
                        Text(if (heartRateAccess) "Записывается ночью" else "Нет доступа, нажми")
                    }
                )
            }

            item { EveningReport(bedtime.last) }

            item { LastNight(report) }

            item {
                Text(
                    text = "Версия ${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.caption3,
                    color = MaterialTheme.colors.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                )
            }
        }
    }
}

/** Варианты длины окна — в том же порядке, что и в MainViewModel.cycleWindowLength(). */
private val WINDOW_OPTIONS = listOf(15, 30, 45)

/** «Лёг спать»: нажал — часы ищут момент засыпания; нажал ещё раз — отмена. */
@Composable
private fun BedtimeChip(state: BedtimeUi, onClick: () -> Unit) {
    val since = state.activeSinceMs
    if (since != null) {
        InfoButton(
            label = "Жду, когда уснёшь",
            lines = listOf("Лёг в ${fmt(timeOf(since))}", "Нажми, чтобы отменить"),
            highlighted = true,
            onClick = onClick
        )
        return
    }

    val last = state.last
    val latency = last?.latencyMin
    val onset = last?.onsetMs
    val lines = buildList {
        when {
            last == null -> add("Засеку, за сколько уснёшь")
            latency == null || onset == null -> {
                add("Прошлый раз: не определилось")
                add("Лёг в ${fmt(timeOf(last.bedtimeMs))}")
            }
            else -> {
                add("Прошлый раз: $latency мин")
                add("Лёг ${fmt(timeOf(last.bedtimeMs))}, уснул ${fmt(timeOf(onset))}")
            }
        }
        state.typicalMin?.let { add("Обычно: $it мин (${state.nights} ${nightsWord(state.nights)})") }
    }

    InfoButton(label = "Лёг спать", lines = lines, highlighted = false, onClick = onClick)
}

private fun nightsWord(n: Int): String {
    val mod100 = n % 100
    val mod10 = n % 10
    return when {
        mod100 in 11..14 -> "ночей"
        mod10 == 1 -> "ночь"
        mod10 in 2..4 -> "ночи"
        else -> "ночей"
    }
}

private fun timeOf(ms: Long): LocalTime =
    Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalTime()

@Composable
private fun StatusLine(settings: AlarmSettings) {
    val text = if (settings.enabled) {
        "Разбудит между ${fmt(settings.windowStart)} и ${fmt(settings.windowEnd)}. " +
            "Датчик включится в ${fmt(settings.monitorStart)}."
    } else {
        "Включите будильник, чтобы Tranquilo следил за движениями под утро."
    }
    Text(
        text = text,
        style = MaterialTheme.typography.caption2,
        color = MaterialTheme.colors.onSurface,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
    )
}

/** Вечер: что было от «Лёг спать» до засыпания — движения, пульс и найденный момент. */
@Composable
private fun EveningReport(last: BedtimeResult?) {
    if (last == null || last.epochs.isEmpty()) return

    val onset = last.onsetMs
    val title = if (onset != null) {
        "Вечер: лёг ${fmt(timeOf(last.bedtimeMs))}, уснул ${fmt(timeOf(onset))}."
    } else {
        "Вечер: лёг ${fmt(timeOf(last.bedtimeMs))}, засыпание не определилось."
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = title,
            style = MaterialTheme.typography.caption3,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
        EveningChart(last)
        Text(
            text = "Столбики — движения, красная линия — пульс, синяя черта — засыпание.",
            style = MaterialTheme.typography.caption3,
            color = MaterialTheme.colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun EveningChart(last: BedtimeResult) {
    val barColor = MaterialTheme.colors.onSurface.copy(alpha = 0.45f)
    val onsetColor = MaterialTheme.colors.primary
    val pulseColor = Color(0xFFE08A8A)

    val epochs = last.epochs
    val total = max(epochs.maxOf { it.secondsFromBedtime }, 600).toFloat()
    val top = max(epochs.maxOf { it.value }, 0.5f)
    fun scale(v: Float) = sqrt((v / top).coerceIn(0f, 1f))
    val onsetSeconds = last.onsetMs?.let { ((it - last.bedtimeMs) / 1000).toFloat() }

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(44.dp)
    ) {
        val w = size.width
        val h = size.height
        fun x(seconds: Float) = w * (seconds / total)

        val barWidth = max(1.5f, w / (total / 30f) * 0.7f)
        epochs.forEach { e ->
            val ex = x(e.secondsFromBedtime.toFloat())
            drawLine(barColor, Offset(ex, h), Offset(ex, h - h * scale(e.value)), strokeWidth = barWidth)
        }

        val withHr = epochs.filter { it.hr > 0f }
        if (withHr.size >= 2) {
            val lo = withHr.minOf { it.hr }
            val hi = max(withHr.maxOf { it.hr }, lo + 10f)
            fun hy(hr: Float) = h * (0.9f - 0.8f * (hr - lo) / (hi - lo))
            withHr.zipWithNext { a, b ->
                if (b.secondsFromBedtime - a.secondsFromBedtime <= 120) {
                    drawLine(
                        pulseColor,
                        Offset(x(a.secondsFromBedtime.toFloat()), hy(a.hr)),
                        Offset(x(b.secondsFromBedtime.toFloat()), hy(b.hr)),
                        strokeWidth = 2f
                    )
                }
            }
        }

        onsetSeconds?.let {
            val ox = x(it)
            drawLine(onsetColor, Offset(ox, 0f), Offset(ox, h), strokeWidth = 3f)
        }
    }
}

@Composable
private fun LastNight(report: NightReport?) {
    if (report == null) {
        Text(
            text = "Здесь появится отчёт после первой ночи.",
            style = MaterialTheme.typography.caption3,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(8.dp)
        )
        return
    }

    val time = Instant.ofEpochMilli(report.ringedAtMillis)
        .atZone(ZoneId.systemDefault()).toLocalTime()

    val how = if (report.smart) "по движению" else "в конце окна"
    val pulses = report.epochs.map { it.hr }.filter { it > 0f }
    val pulseLine = if (pulses.isEmpty()) "" else
        "\nПульс %.0f–%.0f".format(pulses.min(), pulses.max())

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "Прошлый раз: ${fmt(time)}, $how.\n" +
                "Фон %.2f · порог %.2f · пик %.2f".format(report.baseline, report.threshold, report.peak) +
                pulseLine,
            style = MaterialTheme.typography.caption3,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
        if (report.epochs.isNotEmpty()) {
            NightChart(report)
            Text(
                text = "Столбики — движения, красная линия — пульс, золотистая — порог. Подсвечено окно.",
                style = MaterialTheme.typography.caption3,
                color = MaterialTheme.colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }
    }
}

/**
 * Движения за ночь: от начала калибровки (слева) до крайнего срока (справа).
 * Высота столбика — корень из силы движения, чтобы мелкие тоже было видно.
 */
@Composable
private fun NightChart(report: NightReport) {
    val barColor = MaterialTheme.colors.onSurface.copy(alpha = 0.45f)
    val hitColor = MaterialTheme.colors.primary
    val lineColor = MaterialTheme.colors.secondary
    val windowColor = MaterialTheme.colors.primary.copy(alpha = 0.14f)
    val pulseColor = Color(0xFFE08A8A)

    val total = (report.windowMinutes + AlarmSettings.BASELINE_MINUTES) * 60f
    val windowSeconds = report.windowMinutes * 60
    val top = maxOf(report.threshold * 2, report.peak, 0.5).toFloat()
    fun scale(v: Float) = sqrt((v / top).coerceIn(0f, 1f))

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(44.dp)
    ) {
        val w = size.width
        val h = size.height
        fun x(secondsToEnd: Int) = w * (1f - secondsToEnd / total)

        val windowX = x(windowSeconds)
        drawRect(windowColor, topLeft = Offset(windowX, 0f), size = Size(w - windowX, h))

        val barWidth = max(1.5f, w / (total / 30f) * 0.7f)
        report.epochs.forEach { e ->
            val ex = x(e.secondsToEnd)
            if (ex < 0f || ex > w) return@forEach
            val inWindow = e.secondsToEnd <= windowSeconds
            val color = if (inWindow && e.value > report.threshold) hitColor else barColor
            drawLine(color, Offset(ex, h), Offset(ex, h - h * scale(e.value)), strokeWidth = barWidth)
        }

        if (report.threshold > 0) {
            val ty = h * (1f - scale(report.threshold.toFloat()))
            drawLine(lineColor, Offset(windowX, ty), Offset(w, ty), strokeWidth = 2f)
        }

        // Пульс — своя шкала: от минимума ночи (низ) до максимума (верх).
        val withHr = report.epochs.filter { it.hr > 0f }
        if (withHr.size >= 2) {
            val lo = withHr.minOf { it.hr }
            val hi = max(withHr.maxOf { it.hr }, lo + 10f)
            fun hy(hr: Float) = h * (0.9f - 0.8f * (hr - lo) / (hi - lo))
            withHr.zipWithNext { a, b ->
                // Разрыв больше двух минут — датчик молчал, линию не соединяем.
                if (a.secondsToEnd - b.secondsToEnd <= 120) {
                    drawLine(
                        pulseColor,
                        Offset(x(a.secondsToEnd), hy(a.hr)),
                        Offset(x(b.secondsToEnd), hy(b.hr)),
                        strokeWidth = 2f
                    )
                }
            }
        }
    }
}

private fun fmt(t: LocalTime) = "%02d:%02d".format(t.hour, t.minute)
