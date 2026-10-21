package com.tranquilo.alarm.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material.Colors
import androidx.wear.compose.material.MaterialTheme

private val TranquiloColors = Colors(
    primary = Color(0xFF8AB4C8),
    primaryVariant = Color(0xFF4E7386),
    secondary = Color(0xFFC8B18A),
    background = Color.Black,
    surface = Color(0xFF14181B),
    onPrimary = Color(0xFF06171F),
    onSecondary = Color(0xFF1F1806),
    onBackground = Color(0xFFE6ECEF),
    onSurface = Color(0xFFE6ECEF)
)

@Composable
fun TranquiloTheme(content: @Composable () -> Unit) {
    MaterialTheme(colors = TranquiloColors, content = content)
}
