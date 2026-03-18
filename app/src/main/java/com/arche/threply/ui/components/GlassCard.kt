package com.arche.threply.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.Canvas
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arche.threply.ui.theme.threplyPalette

/**
 * Glass morphism card component.
 * Equivalent to iOS GlassCard.
 */
@Composable
fun GlassCard(
    title: String,
    modifier: Modifier = Modifier,
    tintColor: Color = Color.Transparent,
    bottomTintColor: Color = tintColor,
    edgeTintColor: Color = tintColor,
    content: @Composable ColumnScope.() -> Unit
) {
    val palette = threplyPalette()
    val shape = RoundedCornerShape(22.dp)
    val topTintMix = tintColor.alpha.coerceIn(0f, 1f)
    val bottomTintMix = bottomTintColor.alpha.coerceIn(0f, 1f)
    val normalizedTopTint = if (topTintMix > 0f) tintColor.copy(alpha = 1f) else tintColor
    val normalizedBottomTint = if (bottomTintMix > 0f) bottomTintColor.copy(alpha = 1f) else bottomTintColor
    val middleTint = when {
        topTintMix > 0f && bottomTintMix > 0f -> lerp(normalizedTopTint, normalizedBottomTint, 0.55f)
        topTintMix > 0f -> normalizedTopTint
        else -> normalizedBottomTint
    }
    val middleTintMix = maxOf(topTintMix, bottomTintMix)
    val topColor = lerp(palette.cardGradientTop, normalizedTopTint, topTintMix * if (palette.isDark) 0.03f else 0.22f)
    val middleColor = lerp(palette.cardGradientMiddle, middleTint, middleTintMix * if (palette.isDark) 0.02f else 0.18f)
    val bottomColor = lerp(palette.cardGradientBottom, normalizedBottomTint, bottomTintMix * if (palette.isDark) 0.015f else 0.15f)
    val borderBrush = if (palette.isDark) {
        Brush.linearGradient(
            colors = listOf(
                palette.glassBorderStrong,
                palette.glassBorderMedium,
                palette.glassBorderSoft,
                palette.glassBorderGlow,
            )
        )
    } else {
        Brush.linearGradient(
            colors = listOf(
                palette.glassBorderStrong,
                palette.glassBorderSoft,
                palette.glassBorderSoft,
                palette.glassBorderGlow,
            )
        )
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = if (palette.isDark) 14.dp else 22.dp,
                shape = shape,
                ambientColor = palette.shadowColor,
                spotColor = palette.shadowColor,
            )
            .clip(shape)
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        topColor,
                        middleColor,
                        bottomColor,
                    )
                )
            )
            .border(
                width = 1.dp,
                brush = borderBrush,
                shape = shape
            )
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val topHighlightRadius = if (palette.isDark) size.maxDimension * 0.58f else size.maxDimension * 0.92f
            val topHighlightCenter = androidx.compose.ui.geometry.Offset(
                x = size.width * if (palette.isDark) 0.24f else 0.22f,
                y = size.height * if (palette.isDark) 0.01f else 0.04f,
            )
            drawRoundRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        palette.glassHighlightTop,
                        Color.Transparent,
                    ),
                    center = topHighlightCenter,
                    radius = topHighlightRadius,
                ),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(22.dp.toPx(), 22.dp.toPx()),
            )

            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        palette.glassHighlightEdge,
                        Color.Transparent,
                    ),
                    startY = 0f,
                    endY = size.height * if (palette.isDark) 0.18f else 0.30f,
                ),
                size = androidx.compose.ui.geometry.Size(
                    size.width,
                    size.height * if (palette.isDark) 0.24f else 0.38f,
                ),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(22.dp.toPx(), 22.dp.toPx()),
            )

            if (!palette.isDark && topTintMix > 0f) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            tintColor.copy(alpha = 0.10f),
                            Color.Transparent,
                        ),
                        center = androidx.compose.ui.geometry.Offset(size.width * 0.18f, size.height * 0.12f),
                        radius = size.maxDimension * 0.55f,
                    ),
                    radius = size.maxDimension * 0.55f,
                    center = androidx.compose.ui.geometry.Offset(size.width * 0.18f, size.height * 0.12f),
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            bottomTintColor.copy(alpha = 0.07f),
                            Color.Transparent,
                        ),
                        center = androidx.compose.ui.geometry.Offset(size.width * 0.82f, size.height * 0.88f),
                        radius = size.maxDimension * 0.48f,
                    ),
                    radius = size.maxDimension * 0.48f,
                    center = androidx.compose.ui.geometry.Offset(size.width * 0.82f, size.height * 0.88f),
                )
            }

            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        palette.glassMist.copy(alpha = if (palette.isDark) 0.06f else 0.32f),
                    ),
                    startY = size.height * 0.58f,
                    endY = size.height,
                ),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(22.dp.toPx(), 22.dp.toPx()),
            )

            if (topTintMix > 0f || bottomTintMix > 0f) {
                val spectralBase = if (palette.isDark) {
                    val normalizedEdgeTint = edgeTintColor.copy(alpha = 1f)
                    lerp(Color(0xFF8FB6FF), normalizedEdgeTint, 0.16f)
                } else {
                    edgeTintColor
                }
                val spectral = spectralBase.copy(alpha = if (palette.isDark) 0.08f else 0.16f)
                drawRoundRect(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            spectral,
                            Color.Transparent,
                            spectralBase.copy(alpha = if (palette.isDark) 0.025f else 0.08f),
                        ),
                        start = androidx.compose.ui.geometry.Offset(0f, size.height * 0.12f),
                        end = androidx.compose.ui.geometry.Offset(size.width, size.height * 0.88f),
                    ),
                    style = Stroke(width = 1.15.dp.toPx()),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(22.dp.toPx(), 22.dp.toPx()),
                )
            }
        }

        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = title,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = palette.textPrimary,
            )
            content()
        }
    }
}

/**
 * Glass morphism panel for onboarding.
 * Equivalent to iOS OnboardingGlassPanel.
 */
@Composable
fun GlassPanel(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val palette = threplyPalette()
    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        palette.panelGradientTop,
                        palette.panelGradientMiddle,
                        palette.panelGradientBottom,
                    )
                )
            )
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        palette.glassBorderStrong,
                        palette.glassBorderSoft,
                        palette.glassBorderSoft,
                        palette.glassBorderGlow,
                    )
                ),
                shape = shape
            )
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = title,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = palette.textPrimary,
            )
            Text(
                text = subtitle,
                fontSize = 14.sp,
                color = palette.textSecondary,
            )
        }
        content()
    }
}
