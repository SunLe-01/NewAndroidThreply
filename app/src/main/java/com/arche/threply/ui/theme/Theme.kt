package com.arche.threply.ui.theme

import android.app.Activity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

object ThreplyColors {
    val accent = Color(0xFFC7921A)
    val green = Color(0xFF2FA36B)
    val orange = Color(0xFFE49A3D)
    val blue = Color(0xFF4B8DDE)
    val purple = Color(0xFF8A72D8)
}

@Immutable
data class ThreplyPalette(
    val isDark: Boolean,
    val backgroundPrimary: Color,
    val backgroundSecondary: Color,
    val bottomSheetSurface: Color,
    val radialBackgroundBase: Color,
    val radialBackgroundGlow: Color,
    val backdropStart: Color,
    val backdropMiddle: Color,
    val backdropEnd: Color,
    val backdropOverlay: Color,
    val gradientCyan: Color,
    val gradientOrange: Color,
    val gradientPurple: Color,
    val glassSurface: Color,
    val glassSurfaceElevated: Color,
    val cardGradientTop: Color,
    val cardGradientMiddle: Color,
    val cardGradientBottom: Color,
    val glassHighlightTop: Color,
    val glassHighlightEdge: Color,
    val glassMist: Color,
    val panelGradientTop: Color,
    val panelGradientMiddle: Color,
    val panelGradientBottom: Color,
    val glassBorderStrong: Color,
    val glassBorderMedium: Color,
    val glassBorderSoft: Color,
    val glassBorderGlow: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val textDim: Color,
    val avatarSurface: Color,
    val avatarBorder: Color,
    val shadowColor: Color,
    val primaryButtonContainer: Color,
    val primaryButtonContent: Color,
    val secondaryButtonContainer: Color,
    val secondaryButtonContent: Color,
    val secondaryButtonBorder: Color,
    val destructiveContainer: Color,
    val destructiveContent: Color,
    val chipSurface: Color,
    val chipBorder: Color,
    val positiveSurface: Color,
    val positiveContent: Color,
    val cautionSurface: Color,
    val cautionContent: Color,
    val inputSurface: Color,
    val inputSurfaceFocused: Color,
    val inputBorderStrong: Color,
)

private val DarkPalette = ThreplyPalette(
    isDark = true,
    backgroundPrimary = Color(0xFF000000),
    backgroundSecondary = Color(0xFF0F1116),
    bottomSheetSurface = Color(0xFF0D0F14),
    radialBackgroundBase = Color(0xFF000000),
    radialBackgroundGlow = Color(0xFF10131C),
    backdropStart = Color.Black,
    backdropMiddle = Color(0xFF080B12),
    backdropEnd = Color(0xFF030407),
    backdropOverlay = Color.Black.copy(alpha = 0.42f),
    gradientCyan = Color(0xFF33CCDD).copy(alpha = 0.22f),
    gradientOrange = Color(0xFFF28D40).copy(alpha = 0.20f),
    gradientPurple = Color(0xFF8C59F2).copy(alpha = 0.18f),
    glassSurface = Color(0xFF14161B).copy(alpha = 0.48f),
    glassSurfaceElevated = Color(0xFF181B22).copy(alpha = 0.64f),
    cardGradientTop = Color(0xFF1A1B20).copy(alpha = 0.82f),
    cardGradientMiddle = Color(0xFF111218).copy(alpha = 0.74f),
    cardGradientBottom = Color(0xFF090A0E).copy(alpha = 0.68f),
    glassHighlightTop = Color.White.copy(alpha = 0.10f),
    glassHighlightEdge = Color(0xFFDDE5FF).copy(alpha = 0.14f),
    glassMist = Color.White.copy(alpha = 0.02f),
    panelGradientTop = Color(0xFF1B1C22).copy(alpha = 0.84f),
    panelGradientMiddle = Color(0xFF11131A).copy(alpha = 0.74f),
    panelGradientBottom = Color(0xFF090A10).copy(alpha = 0.68f),
    glassBorderStrong = Color.White.copy(alpha = 0.18f),
    glassBorderMedium = Color.White.copy(alpha = 0.08f),
    glassBorderSoft = Color.White.copy(alpha = 0.04f),
    glassBorderGlow = Color(0xFF97B6FF).copy(alpha = 0.12f),
    textPrimary = Color.White,
    textSecondary = Color.White.copy(alpha = 0.75f),
    textTertiary = Color.White.copy(alpha = 0.55f),
    textDim = Color.White.copy(alpha = 0.35f),
    avatarSurface = Color.White.copy(alpha = 0.06f),
    avatarBorder = Color.White.copy(alpha = 0.12f),
    shadowColor = Color.Black.copy(alpha = 0.42f),
    primaryButtonContainer = Color.White,
    primaryButtonContent = Color.Black,
    secondaryButtonContainer = Color(0xFF171920).copy(alpha = 0.54f),
    secondaryButtonContent = Color.White,
    secondaryButtonBorder = Color.White.copy(alpha = 0.14f),
    destructiveContainer = Color.Red.copy(alpha = 0.15f),
    destructiveContent = Color.Red,
    chipSurface = Color.White.copy(alpha = 0.07f),
    chipBorder = Color.White.copy(alpha = 0.10f),
    positiveSurface = ThreplyColors.green.copy(alpha = 0.18f),
    positiveContent = ThreplyColors.green,
    cautionSurface = ThreplyColors.accent.copy(alpha = 0.18f),
    cautionContent = ThreplyColors.accent,
    inputSurface = Color(0xFF111319).copy(alpha = 0.74f),
    inputSurfaceFocused = Color(0xFF171B22).copy(alpha = 0.84f),
    inputBorderStrong = Color.White.copy(alpha = 0.14f),
)

private val LightPalette = ThreplyPalette(
    isDark = false,
    backgroundPrimary = Color(0xFFF6F7F8),
    backgroundSecondary = Color(0xFFFDFCF9),
    bottomSheetSurface = Color(0xFFFDFCF9),
    radialBackgroundBase = Color(0xFFF6F7F8),
    radialBackgroundGlow = Color(0xFFE8EDF1),
    backdropStart = Color(0xFFFAFBFC),
    backdropMiddle = Color(0xFFF4F6F8),
    backdropEnd = Color(0xFFEEF1F4),
    backdropOverlay = Color.White.copy(alpha = 0.22f),
    gradientCyan = Color(0xFFDCECF5).copy(alpha = 0.22f),
    gradientOrange = Color(0xFFF3E7D7).copy(alpha = 0.20f),
    gradientPurple = Color(0xFFE7EEF3).copy(alpha = 0.10f),
    glassSurface = Color.White.copy(alpha = 0.78f),
    glassSurfaceElevated = Color.White.copy(alpha = 0.92f),
    cardGradientTop = Color.White.copy(alpha = 0.97f),
    cardGradientMiddle = Color(0xFFFCFDFF).copy(alpha = 0.90f),
    cardGradientBottom = Color(0xFFF1F5FA).copy(alpha = 0.82f),
    glassHighlightTop = Color.White.copy(alpha = 0.88f),
    glassHighlightEdge = Color.White.copy(alpha = 0.58f),
    glassMist = Color(0xFFE7EDF7).copy(alpha = 0.70f),
    panelGradientTop = Color.White.copy(alpha = 0.95f),
    panelGradientMiddle = Color(0xFFFBFCFE).copy(alpha = 0.88f),
    panelGradientBottom = Color(0xFFF4F7FB).copy(alpha = 0.82f),
    glassBorderStrong = Color(0xFF111827).copy(alpha = 0.14f),
    glassBorderMedium = Color(0xFF111827).copy(alpha = 0.09f),
    glassBorderSoft = Color(0xFF111827).copy(alpha = 0.05f),
    glassBorderGlow = Color.White.copy(alpha = 0.60f),
    textPrimary = Color(0xFF12161F),
    textSecondary = Color(0xFF5B6574),
    textTertiary = Color(0xFF7C8796),
    textDim = Color(0xFF9AA4B2),
    avatarSurface = Color.White.copy(alpha = 0.82f),
    avatarBorder = Color(0xFF111827).copy(alpha = 0.08f),
    shadowColor = Color(0xFF0D1B2A).copy(alpha = 0.14f),
    primaryButtonContainer = Color(0xFF2C3440),
    primaryButtonContent = Color(0xFFFBFCFE),
    secondaryButtonContainer = Color(0xFFF6F8FB).copy(alpha = 0.82f),
    secondaryButtonContent = Color(0xFF2E3845),
    secondaryButtonBorder = Color(0xFFAAB4C1).copy(alpha = 0.28f),
    destructiveContainer = Color(0xFFD9475E).copy(alpha = 0.14f),
    destructiveContent = Color(0xFFC5374D),
    chipSurface = Color(0xFF111827).copy(alpha = 0.05f),
    chipBorder = Color(0xFF111827).copy(alpha = 0.08f),
    positiveSurface = ThreplyColors.green.copy(alpha = 0.12f),
    positiveContent = Color(0xFF1E8A58),
    cautionSurface = ThreplyColors.accent.copy(alpha = 0.12f),
    cautionContent = Color(0xFFA26E10),
    inputSurface = Color.White.copy(alpha = 0.54f),
    inputSurfaceFocused = Color.White.copy(alpha = 0.70f),
    inputBorderStrong = Color(0xFF111827).copy(alpha = 0.14f),
)

private val DarkColorScheme = darkColorScheme(
    primary = DarkPalette.primaryButtonContainer,
    onPrimary = DarkPalette.primaryButtonContent,
    secondary = DarkPalette.secondaryButtonContainer,
    onSecondary = DarkPalette.secondaryButtonContent,
    tertiary = ThreplyColors.accent,
    background = DarkPalette.backgroundPrimary,
    onBackground = DarkPalette.textPrimary,
    surface = DarkPalette.backgroundSecondary,
    onSurface = DarkPalette.textPrimary,
    surfaceVariant = DarkPalette.glassSurface,
    onSurfaceVariant = DarkPalette.textSecondary,
    outline = DarkPalette.glassBorderSoft,
    outlineVariant = DarkPalette.glassBorderMedium,
    error = Color(0xFFFF5252),
    onError = Color.White,
)

private val LightColorScheme = lightColorScheme(
    primary = LightPalette.primaryButtonContainer,
    onPrimary = LightPalette.primaryButtonContent,
    secondary = LightPalette.secondaryButtonContainer,
    onSecondary = LightPalette.secondaryButtonContent,
    tertiary = ThreplyColors.accent,
    background = LightPalette.backgroundPrimary,
    onBackground = LightPalette.textPrimary,
    surface = LightPalette.backgroundSecondary,
    onSurface = LightPalette.textPrimary,
    surfaceVariant = LightPalette.glassSurface,
    onSurfaceVariant = LightPalette.textSecondary,
    outline = LightPalette.glassBorderSoft,
    outlineVariant = LightPalette.glassBorderMedium,
    error = Color(0xFFD9475E),
    onError = Color.White,
)

private val ThreplyTypography = Typography(
    headlineLarge = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = 0.sp,
    ),
    headlineMedium = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 26.sp,
        lineHeight = 32.sp,
        letterSpacing = 0.sp,
    ),
    titleLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
    ),
    bodyLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodySmall = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
    labelLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    labelMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
    labelSmall = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 10.sp,
        lineHeight = 14.sp,
    ),
)

private val LocalThreplyPalette = staticCompositionLocalOf { DarkPalette }

@Composable
fun threplyPalette(): ThreplyPalette = LocalThreplyPalette.current

@Composable
fun threplyCardColors(): CardColors = CardDefaults.cardColors(
    containerColor = threplyPalette().glassSurface
)

@Composable
fun threplyPrimaryButtonColors(): ButtonColors = ButtonDefaults.buttonColors(
    containerColor = threplyPalette().primaryButtonContainer,
    contentColor = threplyPalette().primaryButtonContent,
    disabledContainerColor = threplyPalette().primaryButtonContainer.copy(alpha = if (threplyPalette().isDark) 0.28f else 0.38f),
    disabledContentColor = threplyPalette().primaryButtonContent.copy(alpha = 0.56f),
)

@Composable
fun threplySecondaryButtonColors(): ButtonColors = ButtonDefaults.buttonColors(
    containerColor = threplyPalette().secondaryButtonContainer,
    contentColor = threplyPalette().secondaryButtonContent,
    disabledContainerColor = threplyPalette().secondaryButtonContainer.copy(alpha = if (threplyPalette().isDark) 0.26f else 0.58f),
    disabledContentColor = threplyPalette().secondaryButtonContent.copy(alpha = 0.52f),
)

@Composable
fun threplyDestructiveButtonColors(): ButtonColors = ButtonDefaults.buttonColors(
    containerColor = threplyPalette().destructiveContainer,
    contentColor = threplyPalette().destructiveContent,
)

@Composable
fun threplyOutlinedButtonColors(): ButtonColors = ButtonDefaults.outlinedButtonColors(
    containerColor = if (threplyPalette().isDark) Color.Transparent else threplyPalette().secondaryButtonContainer,
    contentColor = threplyPalette().secondaryButtonContent,
    disabledContainerColor = if (threplyPalette().isDark) Color.Transparent else threplyPalette().secondaryButtonContainer.copy(alpha = 0.52f),
    disabledContentColor = threplyPalette().secondaryButtonContent.copy(alpha = 0.48f),
)

@Composable
fun threplyOutlinedBorder(): BorderStroke = BorderStroke(
    width = 1.dp,
    color = threplyPalette().secondaryButtonBorder,
)

@Composable
fun threplyTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = MaterialTheme.colorScheme.onSurface,
    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
    focusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
    focusedContainerColor = threplyPalette().inputSurfaceFocused,
    unfocusedContainerColor = threplyPalette().inputSurface,
    disabledContainerColor = threplyPalette().inputSurface,
    errorContainerColor = threplyPalette().inputSurfaceFocused,
    cursorColor = MaterialTheme.colorScheme.primary,
    focusedLabelColor = threplyPalette().textSecondary,
    unfocusedLabelColor = threplyPalette().textTertiary,
    focusedPlaceholderColor = threplyPalette().textDim,
    unfocusedPlaceholderColor = threplyPalette().textDim,
    focusedTrailingIconColor = threplyPalette().textSecondary,
    unfocusedTrailingIconColor = threplyPalette().textTertiary,
    focusedLeadingIconColor = threplyPalette().textSecondary,
    unfocusedLeadingIconColor = threplyPalette().textTertiary,
)

@Composable
fun threplySwitchColors() = SwitchDefaults.colors(
    checkedThumbColor = Color.White,
    checkedTrackColor = ThreplyColors.green.copy(alpha = if (threplyPalette().isDark) 0.9f else 0.72f),
    uncheckedThumbColor = if (threplyPalette().isDark) {
        Color.White.copy(alpha = 0.78f)
    } else {
        MaterialTheme.colorScheme.surface
    },
    uncheckedTrackColor = if (threplyPalette().isDark) {
        Color.White.copy(alpha = 0.18f)
    } else {
        threplyPalette().glassBorderMedium
    },
)

@Composable
fun threplySliderColors() = SliderDefaults.colors(
    thumbColor = MaterialTheme.colorScheme.primary,
    activeTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.82f),
    inactiveTrackColor = threplyPalette().glassBorderMedium,
)

@Composable
fun ThreplyTheme(content: @Composable () -> Unit) {
    val isDark = isSystemInDarkTheme()
    val palette = if (isDark) DarkPalette else LightPalette
    val colorScheme = if (isDark) DarkColorScheme else LightColorScheme
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !isDark
            controller.isAppearanceLightNavigationBars = !isDark
        }
    }

    CompositionLocalProvider(LocalThreplyPalette provides palette) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = ThreplyTypography,
            content = content,
        )
    }
}
