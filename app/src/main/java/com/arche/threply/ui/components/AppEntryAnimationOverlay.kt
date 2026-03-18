package com.arche.threply.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.arche.threply.ui.theme.ThreplyPalette
import com.arche.threply.ui.theme.threplyPalette
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

private val EntryOvershootEasing = CubicBezierEasing(0.18f, 0.9f, 0.2f, 1f)

@Composable
fun AppEntryAnimationOverlay(
    playbackToken: Int,
    modifier: Modifier = Modifier,
) {
    if (playbackToken <= 0) return

    val palette = threplyPalette()
    var visible by remember(playbackToken) { mutableStateOf(true) }
    val screenAlpha = remember(playbackToken) { Animatable(1f) }
    val iconScale = remember(playbackToken) { Animatable(0.84f) }
    val iconSquash = remember(playbackToken) { Animatable(0.14f) }
    val iconLift = remember(playbackToken) { Animatable(24f) }
    val iconAlpha = remember(playbackToken) { Animatable(0f) }
    val sheenProgress = remember(playbackToken) { Animatable(-0.45f) }
    val glowStrength = remember(playbackToken) { Animatable(0f) }

    LaunchedEffect(playbackToken) {
        visible = true
        screenAlpha.snapTo(1f)
        iconScale.snapTo(0.84f)
        iconSquash.snapTo(0.14f)
        iconLift.snapTo(24f)
        iconAlpha.snapTo(0f)
        sheenProgress.snapTo(-0.45f)
        glowStrength.snapTo(0f)

        coroutineScope {
            launch {
                iconAlpha.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 180, easing = LinearOutSlowInEasing),
                )
            }
            launch {
                iconScale.animateTo(
                    targetValue = 1.05f,
                    animationSpec = tween(durationMillis = 380, easing = EntryOvershootEasing),
                )
                iconScale.animateTo(
                    targetValue = 1f,
                    animationSpec = spring(dampingRatio = 0.78f, stiffness = 420f),
                )
            }
            launch {
                iconSquash.animateTo(
                    targetValue = -0.05f,
                    animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing),
                )
                iconSquash.animateTo(
                    targetValue = 0.02f,
                    animationSpec = tween(durationMillis = 170, easing = LinearOutSlowInEasing),
                )
                iconSquash.animateTo(
                    targetValue = 0f,
                    animationSpec = spring(dampingRatio = 0.84f, stiffness = 520f),
                )
            }
            launch {
                iconLift.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(durationMillis = 520, easing = FastOutSlowInEasing),
                )
            }
            launch {
                sheenProgress.animateTo(
                    targetValue = 1.28f,
                    animationSpec = tween(
                        durationMillis = 620,
                        delayMillis = 80,
                        easing = LinearEasing,
                    ),
                )
            }
            launch {
                glowStrength.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(
                        durationMillis = 420,
                        delayMillis = 90,
                        easing = FastOutSlowInEasing,
                    ),
                )
            }
            launch {
                screenAlpha.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(
                        durationMillis = 280,
                        delayMillis = 560,
                        easing = FastOutSlowInEasing,
                    ),
                )
            }
        }

        visible = false
    }

    if (!visible) return

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(playbackToken) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        event.changes.forEach { it.consume() }
                    }
                }
            },
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(
                brush = Brush.linearGradient(
                    colors = listOf(
                        palette.backdropStart.copy(alpha = 0.98f),
                        palette.backdropMiddle.copy(alpha = 0.95f),
                        palette.backdropEnd.copy(alpha = 0.92f),
                    ),
                    start = Offset.Zero,
                    end = Offset(size.width, size.height),
                ),
                alpha = screenAlpha.value,
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.16f * screenAlpha.value),
                        Color.Transparent,
                    ),
                    center = Offset(size.width * 0.24f, size.height * 0.18f),
                    radius = size.minDimension * 0.55f,
                ),
                radius = size.minDimension * 0.55f,
                center = Offset(size.width * 0.24f, size.height * 0.18f),
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        palette.gradientOrange.copy(alpha = 0.42f * glowStrength.value * screenAlpha.value),
                        Color.Transparent,
                    ),
                    center = Offset(size.width * 0.58f, size.height * 0.50f),
                    radius = size.minDimension * 0.32f,
                ),
                radius = size.minDimension * 0.32f,
                center = Offset(size.width * 0.58f, size.height * 0.50f),
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        palette.gradientCyan.copy(alpha = 0.34f * glowStrength.value * screenAlpha.value),
                        Color.Transparent,
                    ),
                    center = Offset(size.width * 0.42f, size.height * 0.46f),
                    radius = size.minDimension * 0.28f,
                ),
                radius = size.minDimension * 0.28f,
                center = Offset(size.width * 0.42f, size.height * 0.46f),
            )
        }

        Canvas(
            modifier = Modifier
                .align(Alignment.Center)
                .size(196.dp)
                .graphicsLayer {
                    alpha = iconAlpha.value * screenAlpha.value
                    scaleX = iconScale.value + iconSquash.value
                    scaleY = iconScale.value - iconSquash.value * 0.72f
                    translationY = iconLift.value
                },
        ) {
            drawMetallicEntryMark(
                palette = palette,
                sheenProgress = sheenProgress.value,
                glowStrength = glowStrength.value,
            )
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawMetallicEntryMark(
    palette: ThreplyPalette,
    sheenProgress: Float,
    glowStrength: Float,
) {
    val minDim = size.minDimension
    val outerInset = minDim * 0.10f
    val outerCorner = CornerRadius(minDim * 0.22f, minDim * 0.22f)
    val outerTopLeft = Offset(outerInset, outerInset)
    val outerSize = Size(size.width - outerInset * 2f, size.height - outerInset * 2f)
    val outerRect = RoundRect(
        left = outerTopLeft.x,
        top = outerTopLeft.y,
        right = outerTopLeft.x + outerSize.width,
        bottom = outerTopLeft.y + outerSize.height,
        cornerRadius = outerCorner,
    )

    val cavitySize = Size(size.width * 0.34f, size.height * 0.34f)
    val cavityTopLeft = Offset(
        x = (size.width - cavitySize.width) / 2f,
        y = (size.height - cavitySize.height) / 2f,
    )
    val cavityCorner = CornerRadius(minDim * 0.11f, minDim * 0.11f)
    val cavityRect = RoundRect(
        left = cavityTopLeft.x,
        top = cavityTopLeft.y,
        right = cavityTopLeft.x + cavitySize.width,
        bottom = cavityTopLeft.y + cavitySize.height,
        cornerRadius = cavityCorner,
    )

    val shellPath = Path().apply {
        fillType = PathFillType.EvenOdd
        addRoundRect(outerRect)
        addRoundRect(cavityRect)
    }

    val shellLight = if (palette.isDark) Color(0xFFF3EDFB) else Color(0xFFF7F4FB)
    val shellMid = Color(0xFFBBB4C5)
    val shellDeep = Color(0xFF5D5967)
    val shellDark = Color(0xFF26242D)

    drawRoundRect(
        color = Color.Black.copy(alpha = 0.16f),
        topLeft = outerTopLeft + Offset(0f, minDim * 0.05f),
        size = outerSize,
        cornerRadius = outerCorner,
    )

    drawPath(
        path = shellPath,
        brush = Brush.linearGradient(
            colors = listOf(shellLight, shellMid, shellDeep, shellDark),
            start = Offset(size.width * 0.14f, size.height * 0.06f),
            end = Offset(size.width * 0.90f, size.height * 0.94f),
        ),
    )

    clipPath(shellPath) {
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.28f + glowStrength * 0.10f),
                    Color.Transparent,
                ),
                center = Offset(size.width * 0.26f, size.height * 0.22f),
                radius = minDim * 0.56f,
            ),
            blendMode = BlendMode.Screen,
        )
        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.Transparent,
                    Color.White.copy(alpha = 0.26f + glowStrength * 0.16f),
                    Color.Transparent,
                ),
                start = Offset(size.width * (sheenProgress - 0.34f), size.height * 0.04f),
                end = Offset(size.width * (sheenProgress + 0.08f), size.height * 0.96f),
            ),
            blendMode = BlendMode.Screen,
        )
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.Transparent,
                    Color.Black.copy(alpha = 0.28f),
                ),
                center = Offset(size.width * 0.84f, size.height * 0.86f),
                radius = minDim * 0.68f,
            ),
            blendMode = BlendMode.Multiply,
        )
    }

    drawRoundRect(
        brush = Brush.linearGradient(
            colors = listOf(
                Color(0xFF2B2832),
                Color(0xFF5D5864),
                Color(0xFFA8A2AE),
            ),
            start = cavityTopLeft,
            end = cavityTopLeft + Offset(cavitySize.width, cavitySize.height),
        ),
        topLeft = cavityTopLeft,
        size = cavitySize,
        cornerRadius = cavityCorner,
    )
    drawRoundRect(
        brush = Brush.radialGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.14f + glowStrength * 0.10f),
                Color.Transparent,
            ),
            center = cavityTopLeft + Offset(cavitySize.width * 0.38f, cavitySize.height * 0.30f),
            radius = cavitySize.minDimension * 0.90f,
        ),
        topLeft = cavityTopLeft,
        size = cavitySize,
        cornerRadius = cavityCorner,
        blendMode = BlendMode.Screen,
    )
    drawRoundRect(
        color = Color.White.copy(alpha = 0.14f),
        topLeft = cavityTopLeft,
        size = cavitySize,
        cornerRadius = cavityCorner,
        style = Stroke(width = minDim * 0.010f),
    )
    drawRoundRect(
        color = Color.Black.copy(alpha = 0.24f),
        topLeft = cavityTopLeft + Offset(0f, minDim * 0.008f),
        size = cavitySize,
        cornerRadius = cavityCorner,
        style = Stroke(width = minDim * 0.014f),
    )
    drawRoundRect(
        color = Color.White.copy(alpha = 0.22f),
        topLeft = outerTopLeft,
        size = outerSize,
        cornerRadius = outerCorner,
        style = Stroke(width = minDim * 0.010f),
    )
}
