package com.northloom.apeiron.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val CosmicColors = darkColorScheme(
    background = Color.Black,
    surface = Color.Black,
    onBackground = Color.White,
    onSurface = Color.White,
    primary = Color(0xFFEDEDED)
)

@Composable
fun HereTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = CosmicColors,
        typography = MaterialTheme.typography,
        content = content
    )
}
