package com.networkpeer.mobile.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape

// Light Blue / Sky Design Tokens (Matching NetworkPeers Web)
val BrandSkyPrimary = Color(0xFF0284C7)    // Sky 600 - Main Brand Light Blue
val BrandSkyLight = Color(0xFF38BDF8)      // Sky 400 - Gradient Accent
val BrandSkyVibrant = Color(0xFF0EA5E9)    // Sky 500
val BrandSkySoft = Color(0xFFF0F9FF)       // Sky 50 - Soft surface tint
val BrandSkyContainer = Color(0xFFE0F2FE)  // Sky 100 - Card accent / Selected chips
val BrandSkyText = Color(0xFF0369A1)       // Sky 700 - High contrast text
val BrandTeal = Color(0xFF0D9488)          // Teal 600
val BrandTealSoft = Color(0xFFCCFBF1)

val Slate950 = Color(0xFF0B1220)
val Slate900 = Color(0xFF0F172A)
val Slate700 = Color(0xFF334155)
val Slate500 = Color(0xFF64748B)
val Slate400 = Color(0xFF94A3B8)
val Slate200 = Color(0xFFE2E8F0)
val SurfaceMist = Color(0xFFF8FAFC)        // Clean subtle background

val Success = Color(0xFF16A34A)
val Warning = Color(0xFFD97706)
val Danger = Color(0xFFDC2626)

private val LightColors = lightColorScheme(
    primary = BrandSkyPrimary,
    onPrimary = Color.White,
    primaryContainer = BrandSkyContainer,
    onPrimaryContainer = BrandSkyText,
    secondary = BrandTeal,
    onSecondary = Color.White,
    secondaryContainer = BrandTealSoft,
    onSecondaryContainer = Color(0xFF134E4A),
    background = SurfaceMist,
    onBackground = Slate900,
    surface = Color.White,
    onSurface = Slate900,
    surfaceVariant = Color(0xFFF1F5F9),
    onSurfaceVariant = Slate500,
    outline = Slate200,
    outlineVariant = Color(0xFFCBD5E1),
    error = Danger,
)

private val DarkColors = darkColorScheme(
    primary = BrandSkyLight,
    onPrimary = Slate950,
    primaryContainer = Color(0xFF0369A1),
    onPrimaryContainer = Color(0xFFE0F2FE),
    secondary = Color(0xFF2DD4BF),
    onSecondary = Color(0xFF042F2E),
    secondaryContainer = Color(0xFF115E59),
    background = Color(0xFF0B1220),
    onBackground = Color(0xFFF8FAFC),
    surface = Color(0xFF131D31),
    onSurface = Color(0xFFF8FAFC),
    surfaceVariant = Color(0xFF1E293B),
    onSurfaceVariant = Color(0xFF94A3B8),
    outline = Color(0xFF334155),
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
