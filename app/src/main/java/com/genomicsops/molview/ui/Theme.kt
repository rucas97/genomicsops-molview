package com.genomicsops.molview.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColors = darkColorScheme(
    primary = GenomicsCyan,
    secondary = GenomicsTeal,
    background = GenomicsDark,
    surface = GenomicsSurface,
    onPrimary = GenomicsDark,
    onBackground = GenomicsText,
    onSurface = GenomicsText
)

@Composable
fun GenomicsTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkColors, content = content)
}
