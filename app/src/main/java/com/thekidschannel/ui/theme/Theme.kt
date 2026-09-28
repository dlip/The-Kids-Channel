package com.thekidschannel.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFFCA28),
    secondary = Color(0xFF81D4FA),
    background = Color.Black,
    surface = Color(0xFF151515),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF283593),
    secondary = Color(0xFF0277BD),
)

@Composable
fun KidsChannelTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
