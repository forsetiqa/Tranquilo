package com.tranquilo.alarm.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.Text

/** Вытянутая кнопка: текст в одну строку, без переносов, как у круглой кнопки. */
@Composable
fun WideButton(text: String, onClick: () -> Unit) {
    Chip(
        onClick = onClick,
        colors = ChipDefaults.primaryChipColors(),
        modifier = Modifier.fillMaxWidth(0.8f),
        label = {
            Text(
                text = text,
                maxLines = 1,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    )
}
