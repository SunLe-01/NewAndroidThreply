package com.arche.threply.ui.home

import androidx.compose.animation.core.animateOffsetAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arche.threply.data.PrefsManager
import com.arche.threply.ui.theme.ThreplyColors
import kotlin.math.*

/**
 * Interactive 2D drag pad for CMode style control.
 * Equivalent to iOS CModeCard.
 */
@Composable
fun CModeCard(modifier: Modifier = Modifier) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val gridCount = 11
    var normalizedPoint by remember {
        val savedLength = PrefsManager.getImeStyleLength(context)
        val savedTemp = PrefsManager.getImeStyleTemperature(context)
        mutableStateOf(Offset(savedLength, savedTemp))
    }

    val accentColor = blendedColor(normalizedPoint.x, normalizedPoint.y)

    val lengthText = descriptor(normalizedPoint.x, "long", "short", "balanced length")
    val temperatureText = descriptor(normalizedPoint.y, "warm", "cold", "balanced tone")

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Label
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = "I want the reply",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White.copy(alpha = 0.8f)
            )
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = lengthText,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    color = accentColor
                )
                Text(
                    text = " and ",
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = temperatureText,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    color = accentColor
                )
            }
        }

        // Drag pad
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f / 0.62f)
        ) {
            val padWidth = constraints.maxWidth.toFloat()
            val padHeight = constraints.maxHeight.toFloat()
            val inset = min(padWidth, padHeight) * 0.045f
            val contentLeft = inset
            val contentTop = inset
            val contentWidth = padWidth - 2 * inset
            val contentHeight = padHeight - 2 * inset
            val bwcMaxWidth = maxWidth
            val bwcMaxHeight = maxHeight

            val shape = RoundedCornerShape(24.dp)

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(shape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.08f),
                                accentColor.copy(alpha = 0.16f)
                            )
                        )
                    )
                    .border(1.dp, Color.White.copy(alpha = 0.2f), shape)
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                normalizedPoint = toNormalized(
                                    offset, contentLeft, contentTop, contentWidth, contentHeight
                                )
                            },
                            onDrag = { change, _ ->
                                normalizedPoint = toNormalized(
                                    change.position, contentLeft, contentTop, contentWidth, contentHeight
                                )
                            },
                            onDragEnd = {
                                PrefsManager.setImeStyle(
                                    context,
                                    normalizedPoint.x,
                                    normalizedPoint.y
                                )
                            }
                        )
                    }
            ) {
                // Dot field
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawDotField(
                        gridCount = gridCount,
                        normalizedPoint = normalizedPoint,
                        accent = accentColor,
                        contentLeft = contentLeft,
                        contentTop = contentTop,
                        contentWidth = contentWidth,
                        contentHeight = contentHeight
                    )
                }

                // Knob
                val animatedPoint by animateOffsetAsState(
                    targetValue = normalizedPoint,
                    animationSpec = spring(dampingRatio = 0.82f, stiffness = 600f),
                    label = "knobPosition"
                )

                val knobX = contentLeft + (animatedPoint.x + 1f) / 2f * contentWidth
                val knobY = contentTop + (1f - (animatedPoint.y + 1f) / 2f) * contentHeight

                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .offset(
                            x = (knobX / (padWidth / bwcMaxWidth.value) - 20).dp,
                            y = (knobY / (padHeight / bwcMaxHeight.value) - 20).dp
                        )
                        .shadow(16.dp, CircleShape, ambientColor = accentColor.copy(alpha = 0.45f))
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    accentColor.copy(alpha = 0.9f),
                                    accentColor.copy(alpha = 0.35f)
                                )
                            )
                        )
                        .border(1.4.dp, Color.White.copy(alpha = 0.9f), CircleShape)
                )
            }
        }
    }
}

// ─── Helpers ───

private fun toNormalized(
    position: Offset,
    contentLeft: Float, contentTop: Float,
    contentWidth: Float, contentHeight: Float
): Offset {
    val clampedX = position.x.coerceIn(contentLeft, contentLeft + contentWidth)
    val clampedY = position.y.coerceIn(contentTop, contentTop + contentHeight)
    val relX = (clampedX - contentLeft) / contentWidth
    val relY = (clampedY - contentTop) / contentHeight
    return Offset(
        x = relX * 2f - 1f,
        y = (1f - relY) * 2f - 1f
    )
}

private fun descriptor(value: Float, positive: String, negative: String, neutral: String): String {
    val magnitude = abs(value)
    return when {
        magnitude < 0.12f -> neutral
        magnitude < 0.35f -> "slightly ${if (value >= 0) positive else negative}"
        magnitude < 0.7f -> if (value >= 0) positive else negative
        else -> "very ${if (value >= 0) positive else negative}"
    }
}

private fun DrawScope.drawDotField(
    gridCount: Int,
    normalizedPoint: Offset,
    accent: Color,
    contentLeft: Float, contentTop: Float,
    contentWidth: Float, contentHeight: Float
) {
    val midIndex = (gridCount - 1) / 2

    for (row in 0 until gridCount) {
        for (col in 0 until gridCount) {
            val normX = col.toFloat() / (gridCount - 1) * 2f - 1f
            val normY = ((gridCount - 1 - row).toFloat() / (gridCount - 1)) * 2f - 1f
            val distance = sqrt((normX - normalizedPoint.x).pow(2) + (normY - normalizedPoint.y).pow(2))
            val highlight = exp(-(distance / 0.62f).pow(2))

            val baseSize = 3f
            val axisBoost = if (row == midIndex || col == midIndex) 2f else 0f
            val dotSize = baseSize + axisBoost + highlight * 18f
            val alpha = (0.2f + highlight * 0.6f).coerceIn(0f, 1f)

            val posX = contentLeft + col.toFloat() / (gridCount - 1) * contentWidth
            val posY = contentTop + row.toFloat() / (gridCount - 1) * contentHeight

            if (row == midIndex && col == midIndex) {
                // Center dot - ring
                drawCircle(
                    color = accent.copy(alpha = 0.9f),
                    radius = (dotSize + 4f) / 2f,
                    center = Offset(posX, posY),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.2f)
                )
            } else {
                drawCircle(
                    color = accent.copy(alpha = alpha),
                    radius = dotSize / 2f,
                    center = Offset(posX, posY)
                )
            }
        }
    }
}

private fun blendedColor(x: Float, y: Float): Color {
    val warmWeight = (y + 1f) / 2f
    val coldWeight = 1f - warmWeight
    val longWeight = (x + 1f) / 2f
    val shortWeight = 1f - longWeight

    val orange = floatArrayOf(250f / 255f, 160f / 255f, 72f / 255f)
    val green = floatArrayOf(94f / 255f, 198f / 255f, 121f / 255f)
    val blue = floatArrayOf(84f / 255f, 153f / 255f, 222f / 255f)
    val purple = floatArrayOf(166f / 255f, 124f / 255f, 233f / 255f)

    val weights = listOf(
        orange to (warmWeight * longWeight),
        green to (warmWeight * shortWeight),
        blue to (coldWeight * shortWeight),
        purple to (coldWeight * longWeight)
    )

    val total = weights.sumOf { it.second.toDouble() }.toFloat()
    if (total <= 0f) return Color.White

    var r = 0f; var g = 0f; var b = 0f
    for ((color, weight) in weights) {
        r += color[0] * weight
        g += color[1] * weight
        b += color[2] * weight
    }

    return Color(r / total, g / total, b / total)
}
