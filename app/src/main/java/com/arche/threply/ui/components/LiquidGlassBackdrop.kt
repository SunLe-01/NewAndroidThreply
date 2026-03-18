package com.arche.threply.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import com.arche.threply.ui.theme.ThreplyPalette
import com.arche.threply.ui.theme.threplyPalette
import kotlin.math.max
import kotlin.math.sqrt

@Immutable
data class GlassAmbientSample(
    val topTint: Color = Color.Transparent,
    val bottomTint: Color = Color.Transparent,
    val edgeTint: Color = Color.Transparent,
    val auraTint: Color = Color.Transparent,
)

@Immutable
data class HomeBackdropField(
    val coolX: Float,
    val coolY: Float,
    val warmX: Float,
    val warmY: Float,
    val irisX: Float,
    val irisY: Float,
    val mistX: Float,
)

@Composable
fun rememberHomeBackdropField(): HomeBackdropField {
    val palette = threplyPalette()
    val transition = rememberInfiniteTransition(label = "homeBackdrop")
    val coolX = if (palette.isDark) 0.18f else transition.animateFloat(
        initialValue = 0.16f,
        targetValue = 0.22f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 26000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "homeCoolX",
    ).value
    val coolY = if (palette.isDark) 0.14f else transition.animateFloat(
        initialValue = 0.12f,
        targetValue = 0.18f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 22000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "homeCoolY",
    ).value
    val warmX = if (palette.isDark) 0.78f else transition.animateFloat(
        initialValue = 0.80f,
        targetValue = 0.72f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 30000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "homeWarmX",
    ).value
    val warmY = if (palette.isDark) 0.42f else transition.animateFloat(
        initialValue = 0.48f,
        targetValue = 0.40f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 24000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "homeWarmY",
    ).value
    val irisX = if (palette.isDark) 0.14f else transition.animateFloat(
        initialValue = 0.18f,
        targetValue = 0.26f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 32000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "homeIrisX",
    ).value
    val irisY = if (palette.isDark) 0.84f else transition.animateFloat(
        initialValue = 0.86f,
        targetValue = 0.76f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 28000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "homeIrisY",
    ).value
    val mistX = if (palette.isDark) 0.62f else transition.animateFloat(
        initialValue = 0.58f,
        targetValue = 0.50f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 36000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "homeMistX",
    ).value

    return HomeBackdropField(
        coolX = coolX,
        coolY = coolY,
        warmX = warmX,
        warmY = warmY,
        irisX = irisX,
        irisY = irisY,
        mistX = mistX,
    )
}

/**
 * Full-screen gradient backdrop with liquid glass effect.
 * Equivalent to iOS LiquidGlassBackdrop.
 */
@Composable
fun LiquidGlassBackdrop(modifier: Modifier = Modifier) {
    val palette = threplyPalette()
    val transition = rememberInfiniteTransition(label = "liquidBackdrop")
    val cyanDriftX = if (palette.isDark) 0f else transition.animateFloat(
        initialValue = -0.02f,
        targetValue = 0.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 24000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "liquidCyanX",
    ).value
    val orangeDriftX = if (palette.isDark) 0f else transition.animateFloat(
        initialValue = 0.04f,
        targetValue = -0.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 28000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "liquidOrangeX",
    ).value
    val purpleDriftY = if (palette.isDark) 0f else transition.animateFloat(
        initialValue = 0.04f,
        targetValue = -0.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 30000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "liquidPurpleY",
    ).value

    Box(modifier = modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(
                brush = Brush.linearGradient(
                    colors = listOf(
                        palette.backdropStart,
                        palette.backdropMiddle,
                        palette.backdropEnd,
                    ),
                    start = Offset.Zero,
                    end = Offset(size.width, size.height)
                )
            )

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        palette.gradientCyan,
                        Color.Transparent,
                    ),
                    center = Offset(size.width * cyanDriftX, size.height * 0.02f),
                    radius = size.maxDimension * 0.55f
                ),
                radius = size.maxDimension * 0.55f,
                center = Offset(size.width * cyanDriftX, size.height * 0.02f)
            )

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        palette.gradientOrange,
                        Color.Transparent,
                    ),
                    center = Offset(size.width * (1f + orangeDriftX), size.height * 0.04f),
                    radius = size.maxDimension * 0.65f
                ),
                radius = size.maxDimension * 0.65f,
                center = Offset(size.width * (1f + orangeDriftX), size.height * 0.04f)
            )

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        palette.gradientPurple,
                        Color.Transparent,
                    ),
                    center = Offset(size.width * 0.02f, size.height * (1f + purpleDriftY)),
                    radius = size.maxDimension * 0.70f
                ),
                radius = size.maxDimension * 0.70f,
                center = Offset(size.width * 0.02f, size.height * (1f + purpleDriftY))
            )

            drawRect(palette.backdropOverlay)
        }
    }
}

fun HomeBackdropField.sampleGlassAmbient(
    cardCenter: Offset,
    cardWidth: Float,
    cardHeight: Float,
    rootWidth: Float,
    rootHeight: Float,
    scrollOffset: Int,
    palette: ThreplyPalette,
    biasColor: Color = Color.Transparent,
): GlassAmbientSample {
    if (palette.isDark || rootWidth <= 0f || rootHeight <= 0f) {
        val fallback = biasColor.copy(alpha = biasColor.alpha * 0.42f)
        return GlassAmbientSample(
            topTint = fallback,
            bottomTint = fallback.copy(alpha = fallback.alpha * 0.82f),
            edgeTint = fallback.copy(alpha = fallback.alpha * 1.12f),
            auraTint = fallback.copy(alpha = fallback.alpha * 0.58f),
        )
    }

    val topPoint = Offset(cardCenter.x, cardCenter.y - cardHeight * 0.24f)
    val bottomPoint = Offset(cardCenter.x + cardWidth * 0.06f, cardCenter.y + cardHeight * 0.28f)

    val topTint = samplePointTint(
        point = topPoint,
        rootWidth = rootWidth,
        rootHeight = rootHeight,
        scrollOffset = scrollOffset,
        palette = palette,
        biasColor = biasColor,
        biasWeight = 0.22f,
        maxAlpha = 0.34f,
        alphaScale = 0.20f,
    )
    val bottomTint = samplePointTint(
        point = bottomPoint,
        rootWidth = rootWidth,
        rootHeight = rootHeight,
        scrollOffset = scrollOffset,
        palette = palette,
        biasColor = biasColor,
        biasWeight = 0.14f,
        maxAlpha = 0.26f,
        alphaScale = 0.20f,
    )
    val edgeTint = blendAmbientColors(
        listOf(
            topTint.copy(alpha = 1f) to (topTint.alpha * 1.20f),
            bottomTint.copy(alpha = 1f) to (bottomTint.alpha * 0.95f),
            biasColor.copy(alpha = 1f) to (biasColor.alpha * 0.24f),
        ),
        maxAlpha = 0.30f,
        alphaScale = 0.16f,
    )
    val auraTint = blendAmbientColors(
        listOf(
            topTint.copy(alpha = 1f) to (topTint.alpha * 0.85f),
            bottomTint.copy(alpha = 1f) to (bottomTint.alpha * 0.70f),
            Color.White to 0.06f,
        ),
        maxAlpha = 0.18f,
        alphaScale = 0.14f,
    )

    return GlassAmbientSample(
        topTint = topTint,
        bottomTint = bottomTint,
        edgeTint = edgeTint,
        auraTint = auraTint,
    )
}

private fun HomeBackdropField.samplePointTint(
    point: Offset,
    rootWidth: Float,
    rootHeight: Float,
    scrollOffset: Int,
    palette: ThreplyPalette,
    biasColor: Color,
    biasWeight: Float,
    maxAlpha: Float,
    alphaScale: Float,
): Color {
    val maxDim = max(rootWidth, rootHeight)
    val scrollParallax = scrollOffset * 0.06f
    val coolCenter = Offset(
        x = rootWidth * coolX - scrollParallax * 0.18f,
        y = rootHeight * coolY - scrollParallax * 0.10f,
    )
    val warmCenter = Offset(
        x = rootWidth * warmX + scrollParallax * 0.14f,
        y = rootHeight * warmY - scrollParallax * 0.05f,
    )
    val irisCenter = Offset(
        x = rootWidth * irisX - scrollParallax * 0.10f,
        y = rootHeight * irisY + scrollParallax * 0.12f,
    )
    val mistCenter = Offset(
        x = rootWidth * mistX,
        y = rootHeight * 0.24f,
    )

    return blendAmbientColors(
        listOf(
            palette.gradientCyan.copy(alpha = 1f) to (radialWeight(point, coolCenter, maxDim * 0.44f) * 0.96f),
            palette.gradientOrange.copy(alpha = 1f) to (radialWeight(point, warmCenter, maxDim * 0.42f) * 0.82f),
            palette.gradientPurple.copy(alpha = 1f) to (radialWeight(point, irisCenter, maxDim * 0.38f) * 0.72f),
            Color.White to (radialWeight(point, mistCenter, maxDim * 0.54f) * 0.42f),
            biasColor.copy(alpha = 1f) to (biasColor.alpha * biasWeight),
        ),
        maxAlpha = maxAlpha,
        alphaScale = alphaScale,
    )
}

private fun radialWeight(point: Offset, center: Offset, radius: Float): Float {
    if (radius <= 0f) return 0f
    val dx = point.x - center.x
    val dy = point.y - center.y
    val distance = sqrt(dx * dx + dy * dy)
    val normalized = (1f - distance / radius).coerceIn(0f, 1f)
    return normalized * normalized
}

private fun blendAmbientColors(
    contributions: List<Pair<Color, Float>>,
    maxAlpha: Float,
    alphaScale: Float,
): Color {
    var totalWeight = 0f
    var red = 0f
    var green = 0f
    var blue = 0f

    contributions.forEach { (color, weight) ->
        val effectiveWeight = (weight.coerceAtLeast(0f)) * color.alpha.coerceIn(0f, 1f)
        if (effectiveWeight > 0f) {
            totalWeight += effectiveWeight
            red += color.red * effectiveWeight
            green += color.green * effectiveWeight
            blue += color.blue * effectiveWeight
        }
    }

    if (totalWeight <= 0f) return Color.Transparent

    val normalizedAlpha = (totalWeight / alphaScale).coerceIn(0f, 1f) * maxAlpha
    return Color(
        red = (red / totalWeight).coerceIn(0f, 1f),
        green = (green / totalWeight).coerceIn(0f, 1f),
        blue = (blue / totalWeight).coerceIn(0f, 1f),
        alpha = normalizedAlpha,
    )
}

/**
 * Simple radial gradient background for home screen.
 * Equivalent to iOS ContentView background.
 */
@Composable
fun RadialGradientBackground(
    modifier: Modifier = Modifier,
    scrollOffset: Int = 0,
    backdropField: HomeBackdropField? = null,
) {
    val palette = threplyPalette()
    val field = backdropField ?: rememberHomeBackdropField()

    Canvas(modifier = modifier.fillMaxSize()) {
        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(
                    palette.backdropStart,
                    palette.radialBackgroundBase,
                    palette.backdropEnd,
                ),
                start = Offset.Zero,
                end = Offset(size.width, size.height),
            )
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    palette.radialBackgroundBase,
                    palette.radialBackgroundGlow,
                ),
                center = Offset(size.width * 0.10f, size.height * 0.06f),
                radius = size.maxDimension * 0.6f
            ),
            radius = size.maxDimension,
            center = Offset(size.width * 0.10f, size.height * 0.06f)
        )

        if (!palette.isDark) {
            val scrollParallax = scrollOffset * 0.06f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        palette.gradientCyan.copy(alpha = 0.68f),
                        Color.Transparent,
                    ),
                    center = Offset(
                        size.width * field.coolX - scrollParallax * 0.18f,
                        size.height * field.coolY - scrollParallax * 0.10f,
                    ),
                    radius = size.maxDimension * 0.44f,
                ),
                radius = size.maxDimension * 0.44f,
                center = Offset(
                    size.width * field.coolX - scrollParallax * 0.18f,
                    size.height * field.coolY - scrollParallax * 0.10f,
                ),
            )

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        palette.gradientOrange.copy(alpha = 0.62f),
                        Color.Transparent,
                    ),
                    center = Offset(
                        size.width * field.warmX + scrollParallax * 0.14f,
                        size.height * field.warmY - scrollParallax * 0.05f,
                    ),
                    radius = size.maxDimension * 0.42f,
                ),
                radius = size.maxDimension * 0.42f,
                center = Offset(
                    size.width * field.warmX + scrollParallax * 0.14f,
                    size.height * field.warmY - scrollParallax * 0.05f,
                ),
            )

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        palette.gradientPurple.copy(alpha = 0.42f),
                        Color.Transparent,
                    ),
                    center = Offset(
                        size.width * field.irisX - scrollParallax * 0.10f,
                        size.height * field.irisY + scrollParallax * 0.12f,
                    ),
                    radius = size.maxDimension * 0.38f,
                ),
                radius = size.maxDimension * 0.38f,
                center = Offset(
                    size.width * field.irisX - scrollParallax * 0.10f,
                    size.height * field.irisY + scrollParallax * 0.12f,
                ),
            )

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.34f),
                        Color.Transparent,
                    ),
                    center = Offset(size.width * field.mistX, size.height * 0.24f),
                    radius = size.maxDimension * 0.54f,
                ),
                radius = size.maxDimension * 0.54f,
                center = Offset(size.width * field.mistX, size.height * 0.24f),
            )

            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        palette.backdropOverlay.copy(alpha = 0.40f),
                    ),
                    startY = size.height * 0.35f,
                    endY = size.height,
                )
            )
        }
    }
}
