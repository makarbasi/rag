package com.qualcomm.rag.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val RagColorScheme = darkColorScheme(
    primary          = Indigo400,
    onPrimary        = Slate950,
    primaryContainer = Indigo900,
    onPrimaryContainer = Purple300,

    secondary        = Teal400,
    onSecondary      = Slate950,
    secondaryContainer = Color(0xFF134E4A),
    onSecondaryContainer = Teal400,

    tertiary         = Purple500,
    onTertiary       = Slate950,

    background       = Background,
    onBackground     = OnSurface,

    surface          = Surface,
    onSurface        = OnSurface,
    surfaceVariant   = SurfaceCard,
    onSurfaceVariant = OnSurfaceMuted,

    outline          = Slate700,
    outlineVariant   = Slate800,

    error            = Rose500,
    onError          = Slate950,
)

@Composable
fun RAGTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = RagColorScheme,
        typography  = RagTypography,
        content     = content
    )
}