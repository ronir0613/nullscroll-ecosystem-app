package com.nullscroll.morninglock.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFFFF1F1F), onPrimary = Color.White,
    background = Color(0xFFF7F6F3), onBackground = Color(0xFF111111),
    surface = Color(0xFFF7F6F3), onSurface = Color(0xFF111111)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFF1F1F), onPrimary = Color.White,
    background = Color(0xFF101010), onBackground = Color(0xFFF7F6F3),
    surface = Color(0xFF101010), onSurface = Color(0xFFF7F6F3)
)

@Composable
fun MorningLockTheme(darkTheme: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (darkTheme) DarkColors else LightColors, content = content)
}
