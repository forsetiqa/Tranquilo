package com.tranquilo.alarm.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

@Composable
fun MainScreen(
    viewModel: MainViewModel,
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

    Text(
        text = "Прошлый раз: ${fmt(time)}, $how.\n" +
            "Фон %.2f · порог %.2f · пик %.2f".format(report.baseline, report.threshold, report.peak),
        style = MaterialTheme.typography.caption3,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(8.dp)
    )
}

private fun fmt(t: LocalTime) = "%02d:%02d".format(t.hour, t.minute)
