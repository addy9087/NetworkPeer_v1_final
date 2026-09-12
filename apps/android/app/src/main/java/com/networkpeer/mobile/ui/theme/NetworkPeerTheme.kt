package com.networkpeer.mobile.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape

// Rapido-Inspired High-Contrast Design Tokens (Clean, Flat, Minimal Chrome)
val RapidoYellowPrimary = Color(0xFFF9C933)   // Dominant Rapido Canary Yellow
val RapidoYellowLight = Color(0xFFFFDE59)     // Soft yellow highlight
val RapidoYellowVibrant = Color(0xFFFFC72C)   // Action primary yellow
val RapidoYellowSoft = Color(0xFFFFFBEB)      // Warm cream background tint
val RapidoYellowContainer = Color(0xFFFEF08A) // Active chip container
val RapidoDark = Color(0xFF111827)            // Deep Obsidian Black
val RapidoDarkSecondary = Color(0xFF1F2937)   // Secondary dark

// Aliases for seamless component compatibility
val BrandSkyPrimary = RapidoYellowPrimary
val BrandSkyLight = RapidoYellowLight
val BrandSkyVibrant = RapidoYellowVibrant
val BrandSkySoft = RapidoYellowSoft
val BrandSkyContainer = RapidoYellowContainer
val BrandSkyText = RapidoDark
val BrandTeal = Color(0xFF111827)
val BrandTealSoft = Color(0xFFF3F4F6)

val Slate950 = Color(0xFF0B1220)
val Slate900 = Color(0xFF111827)
val Slate700 = Color(0xFF374151)
val Slate500 = Color(0xFF6B7280)
val Slate400 = Color(0xFF9CA3AF)
val Slate200 = Color(0xFFE5E7EB)
val SurfaceMist = Color(0xFFF9FAFB)        // Clean flat Rapido surface

val Success = Color(0xFF10B981)
val Warning = Color(0xFFF59E0B)
val Danger = Color(0xFFEF4444)

private val LightColors = lightColorScheme(
    primary = RapidoYellowVibrant,
    onPrimary = RapidoDark,
    primaryContainer = RapidoYellowContainer,
    onPrimaryContainer = RapidoDark,
    secondary = RapidoDarkSecondary,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF3F4F6),
    onSecondaryContainer = RapidoDark,
    background = Color(0xFFFFFFFF),
    onBackground = RapidoDark,
    surface = Color.White,
    onSurface = RapidoDark,
    surfaceVariant = Color(0xFFF9FAFB),
    onSurfaceVariant = Slate500,
    outline = Color(0xFFE5E7EB),
    outlineVariant = Color(0xFFD1D5DB),
    error = Danger,
)

private val DarkColors = darkColorScheme(
    primary = RapidoYellowVibrant,
    onPrimary = RapidoDark,
    primaryContainer = Color(0xFF854D0E),
    onPrimaryContainer = Color(0xFFFEF08A),
    secondary = Color(0xFF9CA3AF),
    onSecondary = Slate950,
    secondaryContainer = Color(0xFF374151),
    background = Color(0xFF111827),
    onBackground = Color(0xFFF9FAFB),
    surface = Color(0xFF1F2937),
    onSurface = Color(0xFFF9FAFB),
    surfaceVariant = Color(0xFF374151),
    onSurfaceVariant = Color(0xFF9CA3AF),
    outline = Color(0xFF4B5563),
    error = Color(0xFFF87171),
)

@Composable
fun NetworkPeerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        shapes = MaterialTheme.shapes.copy(
            extraSmall = RoundedCornerShape(8.dp),
            small = RoundedCornerShape(12.dp),
            medium = RoundedCornerShape(16.dp),
            large = RoundedCornerShape(20.dp),
            extraLarge = RoundedCornerShape(24.dp),
        ),
        content = content,
    )
}
