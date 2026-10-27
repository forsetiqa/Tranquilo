package com.tranquilo.alarm.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text

private const val SLIDE_MS = 280

/**
 * Переключатель режима: подпись и все варианты в ряд. Текущий вариант — на светлой
 * плашке, остальные приглушены. Нажатие — следующий вариант, плашка плавно переезжает.
 */
@Composable
fun OptionChip(
    label: String,
    options: List<String>,
    selected: Int,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colors
    val current = selected.coerceIn(0, options.lastIndex)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(colors.surface)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { stateDescription = options[current] }
            .padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 10.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.button,
            color = colors.onSurface,
            maxLines = 1
        )
        Spacer(Modifier.height(6.dp))
        Segments(options, current)
    }
}

@Composable
private fun Segments(options: List<String>, selected: Int) {
    val colors = MaterialTheme.colors

    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .height(22.dp)
    ) {
        val segment = maxWidth / options.size
        val pillOffset by animateDpAsState(
            targetValue = segment * selected,
            animationSpec = tween(SLIDE_MS, easing = FastOutSlowInEasing),
            label = "option-pill"
        )

        Box(
            Modifier
                .offset(x = pillOffset)
                .width(segment)
                .fillMaxHeight()
                .clip(RoundedCornerShape(50))
                .background(colors.primary)
        )

        Row(Modifier.fillMaxSize()) {
            options.forEachIndexed { index, text ->
                val textColor by animateColorAsState(
                    targetValue = if (index == selected) colors.onPrimary
                    else colors.onSurface.copy(alpha = 0.45f),
                    animationSpec = tween(SLIDE_MS),
                    label = "option-text"
                )
                Box(
                    modifier = Modifier
                        .width(segment)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = text,
                        color = textColor,
                        style = MaterialTheme.typography.caption2,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

/**
 * Кнопка с подписью и несколькими строками пояснения — в том же стиле, что и OptionChip.
 * highlighted — светлый фон, когда что-то идёт прямо сейчас.
 */
@Composable
fun InfoButton(
    label: String,
    lines: List<String>,
    highlighted: Boolean,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colors
    val background = if (highlighted) colors.primary else colors.surface
    val labelColor = if (highlighted) colors.onPrimary else colors.onSurface
    val linesColor = if (highlighted) colors.onPrimary.copy(alpha = 0.8f)
    else colors.onSurface.copy(alpha = 0.7f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(background)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 10.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.button,
            color = labelColor,
            maxLines = 1
        )
        lines.forEach { line ->
            Text(
                text = line,
                style = MaterialTheme.typography.caption2,
                color = linesColor
            )
        }
    }
}
