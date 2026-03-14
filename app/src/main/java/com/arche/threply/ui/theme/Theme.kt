package com.arche.threply.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// ─── Brand Colors (matching iOS glass aesthetic) ───

object ThreplyColors {
    val background = Color(0xFF000000)
    val backgroundSecondary = Color(0xFF141418)
    val glassSurface = Color.White.copy(alpha = 0.06f)
    val glassSurfaceElevated = Color.White.copy(alpha = 0.10f)
    val glassStroke = Color.White.copy(alpha = 0.18f)
    val glassStrokeStrong = Color.White.copy(alpha = 0.50f)
    val textPrimary = Color.White
    val textSecondary = Color.White.copy(alpha = 0.75f)
    val textTertiary = Color.White.copy(alpha = 0.55f)
    val textDim = Color.White.copy(alpha = 0.35f)
    val accent = Color(0xFFFFD700) // Yellow for selected states
    val green = Color(0xFF5EC679)
    val orange = Color(0xFFFAA048)
    val blue = Color(0xFF5499DE)
    val purple = Color(0xFFA67CE9)

    // Gradient colors for LiquidGlassBackdrop
    val gradientCyan = Color(0xFF33CCDD).copy(alpha = 0.22f)
    val gradientOrange = Color(0xFFF28D40).copy(alpha = 0.20f)
    val gradientPurple = Color(0xFF8C59F2).copy(alpha = 0.18f)
}

// ─── Dark Color Scheme ───

private val DarkColorScheme = darkColorScheme(
    primary = Color.White,
    onPrimary = Color.Black,
    secondary = Color.White.copy(alpha = 0.8f),
    onSecondary = Color.Black,
    tertiary = ThreplyColors.accent,
    background = ThreplyColors.background,
    onBackground = Color.White,
    surface = ThreplyColors.backgroundSecondary,
    onSurface = Color.White,
    surfaceVariant = ThreplyColors.glassSurface,
    onSurfaceVariant = Color.White.copy(alpha = 0.75f),
    outline = ThreplyColors.glassStroke,
    outlineVariant = ThreplyColors.glassStrokeStrong,
    error = Color(0xFFFF5252),
    onError = Color.White,
)

// ─── Typography ───

private val ThreplyTypography = Typography(
    headlineLarge = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = 0.sp,
        color = Color.White
    ),
    headlineMedium = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 26.sp,
        lineHeight = 32.sp,
        letterSpacing = 0.sp,
        color = Color.White
    ),
    titleLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        color = Color.White
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        color = Color.White
    ),
    bodyLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        color = Color.White.copy(alpha = 0.92f)
    ),
    bodyMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        color = Color.White.copy(alpha = 0.85f)
    ),
    bodySmall = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        color = Color.White.copy(alpha = 0.6f)
    ),
    labelLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        color = Color.White
    ),
    labelMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        color = Color.White.copy(alpha = 0.92f)
    ),
    labelSmall = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 10.sp,
        lineHeight = 14.sp,
        color = Color.White.copy(alpha = 0.55f)
    ),
)

// ─── Theme Composable ───

@Composable
fun ThreplyTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = ThreplyTypography,
        content = content
    )
}
