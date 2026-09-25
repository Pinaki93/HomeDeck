package dev.pinaki.homedeck.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val TerminalColors = darkColorScheme(
    primary = TerminalGreen, primaryContainer = TerminalGreen.copy(alpha = .25f),
    background = TerminalBlack, surface = TerminalSurface, surfaceVariant = TerminalSurface,
    onBackground = TerminalText, onSurface = TerminalText, onSurfaceVariant = TerminalMuted,
)

@Composable
fun HomeDeckTheme(content: @Composable () -> Unit) =
    MaterialTheme(colorScheme = TerminalColors, typography = Typography, content = content)
