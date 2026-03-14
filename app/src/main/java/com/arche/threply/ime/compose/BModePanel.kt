package com.arche.threply.ime.compose

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * B Mode panel: 3 reply suggestion cards with streaming typing animation.
 * Matches iOS ReplyOptionsLayer / ReplyOptionButton.
 */
@Composable
fun BModePanel(
    suggestions: List<String>,
    isLoading: Boolean,
    streamingText: String,
    onSelect: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Show 3 cards: fill with suggestions or empty placeholders
        for (i in 0 until 3) {
            val text = suggestions.getOrNull(i) ?: ""
            val isThisLoading = isLoading && text.isEmpty()
            ReplyCard(
                text = text,
                isLoading = isThisLoading,
                streamingText = if (i == 0 && isLoading) streamingText else null,
                onClick = { if (text.isNotBlank()) onSelect(text) }
            )
        }
    }
}

@Composable
private fun ReplyCard(
    text: String,
    isLoading: Boolean,
    streamingText: String?,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(12.dp)

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
            .background(Color.White)
            .clickable(enabled = text.isNotBlank()) { onClick() },
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
