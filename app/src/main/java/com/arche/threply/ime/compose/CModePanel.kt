package com.arche.threply.ime.compose

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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arche.threply.data.PrefsManager
import kotlin.math.*

/**
 * Compact C Mode 2D drag panel for inside the keyboard.
 * Reuses the same logic as CModeCard but with a smaller height (210dp, matching iOS).
 */
@Composable
fun CModePanel(
    onConfirm: (length: Float, temperature: Float) -> Unit
) {
    val context = LocalContext.current
    val gridCount = 11
    var normalizedPoint by remember {
        val l = PrefsManager.getImeStyleLength(context)
        val t = PrefsManager.getImeStyleTemperature(context)
        mutableStateOf(Offset(l, t))
    }

    val accent = blendedAccent(normalizedPoint.x, normalizedPoint.y)
    val lengthLabel = styleLabel(normalizedPoint.x, "更长", "更短", "适中")
    val tempLabel = styleLabel(normalizedPoint.y, "更温暖", "更克制", "自然")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Header: style description
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("回复", fontSize = 13.sp, color = Color(0xFF666666))
            Text(lengthLabel, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = accent)
            Text("，语气", fontSize = 13.sp, color = Color(0xFF666666))
            Text(tempLabel, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = accent)
        }

        // Drag pad — 210dp height matching iOS
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
        ) {
            val padW = constraints.maxWidth.toFloat()
            val padH = constraints.maxHeight.toFloat()
            val inset = min(padW, padH) * 0.045f
            val cL = inset; val cT = inset
            val cW = padW - 2 * inset; val cH = padH - 2 * inset
            val bwW = maxWidth; val bwH = maxHeight
            val shape = RoundedCornerShape(16.dp)

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(shape)
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFFF5F5F7), accent.copy(alpha = 0.12f))
                        )
                    )
                    .border(1.dp, Color(0xFFDDDDDD), shape)
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { pos ->
                                normalizedPoint = toNorm(pos, cL, cT, cW, cH)
                            },
                            onDrag = { change, _ ->
                                normalizedPoint = toNorm(change.position, cL, cT, cW, cH)
                            },
                            onDragEnd = {
                                PrefsManager.setImeStyle(context, normalizedPoint.x, normalizedPoint.y)
                                onConfirm(normalizedPoint.x, normalizedPoint.y)
                            }
                        )
                    }
            ) {
                // Dot field
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val mid = (gridCount - 1) / 2
                    for (row in 0 until gridCount) {
                        for (col in 0 until gridCount) {
                            val nx = col.toFloat() / (gridCount - 1) * 2f - 1f
                            val ny = ((gridCount - 1 - row).toFloat() / (gridCount - 1)) * 2f - 1f
                            val dist = sqrt((nx - normalizedPoint.x).pow(2) + (ny - normalizedPoint.y).pow(2))
                            val hl = exp(-(dist / 0.62f).pow(2))
                            val base = 2.5f
                            val axisBoost = if (row == mid || col == mid) 1.5f else 0f
                            val dotSize = base + axisBoost + hl * 14f
                            val alpha = (0.2f + hl * 0.6f).coerceIn(0f, 1f)
                            val px = cL + col.toFloat() / (gridCount - 1) * cW
                            val py = cT + row.toFloat() / (gridCount - 1) * cH
                            if (row == mid && col == mid) {
                                drawCircle(accent.copy(alpha = 0.9f), (dotSize + 3f) / 2f, Offset(px, py), style = Stroke(1f))
                            } else {
                                drawCircle(accent.copy(alpha = alpha), dotSize / 2f, Offset(px, py))
                            }
                        }
                    }
                }

                // Knob
                val anim by animateOffsetAsState(
                    normalizedPoint, spring(0.82f, 600f), label = "knob"
                )
                val kx = cL + (anim.x + 1f) / 2f * cW
                val ky = cT + (1f - (anim.y + 1f) / 2f) * cH

                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .offset(
                            x = (kx / (padW / bwW.value) - 18).dp,
                            y = (ky / (padH / bwH.value) - 18).dp
                        )
                        .shadow(12.dp, CircleShape, ambientColor = accent.copy(alpha = 0.4f))
                        .clip(CircleShape)
                        .background(Brush.radialGradient(listOf(accent.copy(0.9f), accent.copy(0.35f))))
                        .border(1.2.dp, Color.White.copy(0.9f), CircleShape)
                )
            }
        }
    }
}

// ─── Helpers ───

private fun toNorm(pos: Offset, cL: Float, cT: Float, cW: Float, cH: Float): Offset {
    val cx = pos.x.coerceIn(cL, cL + cW)
    val cy = pos.y.coerceIn(cT, cT + cH)
    return Offset(
        x = (cx - cL) / cW * 2f - 1f,
        y = (1f - (cy - cT) / cH) * 2f - 1f
    )
}

private fun styleLabel(value: Float, pos: String, neg: String, neutral: String): String {
    val m = abs(value)
    return when {
        m < 0.12f -> neutral
        m < 0.4f -> if (value >= 0) "稍$pos" else "稍$neg"
        else -> if (value >= 0) pos else neg
    }
}

private fun blendedAccent(x: Float, y: Float): Color {
    val ww = (y + 1f) / 2f; val cw = 1f - ww
    val lw = (x + 1f) / 2f; val sw = 1f - lw
    val o = floatArrayOf(250f/255f, 160f/255f, 72f/255f)
    val g = floatArrayOf(94f/255f, 198f/255f, 121f/255f)
    val b = floatArrayOf(84f/255f, 153f/255f, 222f/255f)
    val p = floatArrayOf(166f/255f, 124f/255f, 233f/255f)
    val ws = listOf(o to (ww*lw), g to (ww*sw), b to (cw*sw), p to (cw*lw))
    val total = ws.sumOf { it.second.toDouble() }.toFloat()
    if (total <= 0f) return Color.White
    var r = 0f; var gr = 0f; var bl = 0f
    for ((c, w) in ws) { r += c[0]*w; gr += c[1]*w; bl += c[2]*w }
    return Color(r/total, gr/total, bl/total)
}
