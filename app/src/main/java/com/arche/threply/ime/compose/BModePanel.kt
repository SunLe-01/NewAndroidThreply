package com.arche.threply.ime.compose

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arche.threply.ime.model.ImeSuggestion
import com.arche.threply.util.HapticsUtil
import kotlinx.coroutines.delay

/**
 * B Mode panel: 3 reply suggestion cards with streaming typing animation.
 * Supports long-press to expand child replies (reply tree).
 * Matches iOS ReplyOptionsLayer / ReplyOptionButton.
 */
@Composable
fun BModePanel(
    suggestions: List<String>,
    isLoading: Boolean,
    streamingText: String,
    onSelect: (String) -> Unit,
    onLongPress: (Int, String) -> Unit = { _, _ -> },
    onBack: () -> Unit = {},
    showBackButton: Boolean = false
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Back button (shown when in expanded tree view)
        if (showBackButton) {
            Text(
                text = "← 返回",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF3B6DAA),
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onBack() }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
        }

        // Show 3 cards: fill with suggestions or empty placeholders
        for (i in 0 until 3) {
            val text = suggestions.getOrNull(i) ?: ""
            val isThisLoading = isLoading && text.isEmpty()
            ReplyCard(
                text = text,
                isLoading = isThisLoading,
                streamingText = if (i == 0 && isLoading) streamingText else null,
                onClick = { if (text.isNotBlank()) onSelect(text) },
                onLongPress = { if (text.isNotBlank()) onLongPress(i, text) }
            )
        }
    }
}

@Composable
private fun ReplyCard(
    text: String,
    isLoading: Boolean,
    streamingText: String?,
    onClick: () -> Unit,
    onLongPress: () -> Unit = {}
) {
    val shape = RoundedCornerShape(12.dp)
    var isPressed by remember { mutableStateOf(false) }
    val context = LocalContext.current

    // Typing animation: reveal characters one by one
    val displayText = if (streamingText != null && streamingText.isNotBlank()) {
        var revealed by remember { mutableStateOf("") }
        LaunchedEffect(streamingText) {
            // Only animate new characters
            val start = revealed.length
            for (i in start..streamingText.lastIndex) {
                revealed = streamingText.substring(0, i + 1)
                delay(18)
            }
        }
        revealed
    } else {
        text
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .shadow(6.dp, shape, ambientColor = Color.Black.copy(alpha = 0.18f))
            .clip(shape)
            .background(if (isPressed) Color(0xFFF0F0F0) else Color.White)
            .pointerInput(text, onClick, onLongPress) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        tryAwaitRelease()
                        isPressed = false
                    },
                    onLongPress = {
                        if (text.isNotBlank()) {
                            HapticsUtil.impactMedium(context)
                            onLongPress()
                        }
                    },
                    onTap = {
                        if (text.isNotBlank()) {
                            HapticsUtil.tap(context)
                            onClick()
                        }
                    }
                )
            },
        contentAlignment = Alignment.CenterStart
    ) {
        if (isLoading && displayText.isBlank()) {
            ShimmerOverlay()
        }

        Text(
            text = displayText,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFF2F3136),
            maxLines = 2,
            modifier = Modifier.padding(horizontal = 14.dp)
        )

        // Long-press indicator (small dot in corner)
        if (text.isNotBlank() && !isLoading) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(4.dp)
                    .background(Color(0xFFAAAAAA), RoundedCornerShape(2.dp))
            )
        }
    }
}

@Composable
private fun ShimmerOverlay() {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val offsetX by transition.animateFloat(
        initialValue = -1f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerOffset"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(12.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0f),
                        Color(0xFFE8EDF5).copy(alpha = 0.6f),
                        Color.White.copy(alpha = 0f)
                    ),
                    start = Offset(offsetX * 400f, 0f),
                    end = Offset(offsetX * 400f + 240f, 0f)
                )
            )
    )
}
