package com.voyagecam.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

@Composable
fun VoyageCamTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Color(0xFF1F6F78),
            secondary = Color(0xFFF2C14E),
            background = Color(0xFFF7FAF9),
            surface = Color.White,
            onPrimary = Color.White,
            onSurface = Color(0xFF163036),
        ),
        content = {
            Surface(content = content)
        },
    )
}
