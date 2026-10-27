package com.example.vnscreenreader.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val HighContrastYellow = Color(0xFFFFEB3B)
val HighContrastGreen = Color(0xFF00E676)

private val HighContrastColors = darkColorScheme(
    primary = HighContrastYellow,
    onPrimary = Color.Black,
    secondary = HighContrastGreen,
    onSecondary = Color.Black,
    background = Color.Black,
    onBackground = Color.White,
    surface = Color.Black,
    onSurface = Color.White,
)

@Composable
fun HighContrastTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = HighContrastColors, content = content)
}

@Composable
fun highContrastSwitchColors() = SwitchDefaults.colors(
    checkedThumbColor = Color.Black,
    checkedTrackColor = HighContrastYellow,
    uncheckedThumbColor = Color.White,
    uncheckedTrackColor = Color.DarkGray,
    uncheckedBorderColor = Color.White,
)
