package com.tranquilo.alarm.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import com.tranquilo.alarm.data.AlarmSettings
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

            item {
                Chip(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { viewModel.toggleEnabled() },
                    colors = if (settings.enabled) ChipDefaults.primaryChipColors()
                    else ChipDefaults.secondaryChipColors(),
                    label = { Text(if (settings.enabled) "Будильник включён" else "Будильник выключен") }
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
                Chip(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { viewModel.cycleWindowLength() },
                    colors = ChipDefaults.secondaryChipColors(),
                    label = { Text("Длина окна") },
                    secondaryLabel = { Text("${settings.windowMinutes} мин") }
                )
            }

            item { SensitivityRow(settings.sensitivity, viewModel::setSensitivity) }

            item {
                Chip(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { viewModel.toggleSound() },
                    colors = ChipDefaults.secondaryChipColors(),
                    label = { Text("Звук") },
                    secondaryLabel = { Text(if (settings.soundEnabled) "Вибрация и звук" else "Только вибрация") }
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

            item { LastNight(report) }
        }
    }
}

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

@Composable
private fun SensitivityRow(value: Int, onChange: (Int) -> Unit) {
    Chip(
        modifier = Modifier.fillMaxWidth(),
        onClick = { onChange(if (value >= 5) 1 else value + 1) },
        colors = ChipDefaults.secondaryChipColors(),
        label = { Text("Чувствительность") },
        secondaryLabel = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("$value из 5 · ${sensitivityHint(value)}")
            }
        }
    )
}

private fun sensitivityHint(value: Int) = when (value) {
    1 -> "будит позже"
    2 -> "сдержанно"
    3 -> "обычно"
    4 -> "чутко"
    else -> "будит раньше"
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
