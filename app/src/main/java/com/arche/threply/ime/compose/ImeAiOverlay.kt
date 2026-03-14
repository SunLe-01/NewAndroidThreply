package com.arche.threply.ime.compose

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arche.threply.ime.model.ImeAiMode
import com.arche.threply.ime.model.SuggestionState

/**
 * Root Compose overlay for the AI panel inside the keyboard.
 * Switches between B mode (3 reply cards) and C mode (2D style pad).
 */
@Composable
fun ImeAiOverlay(
    state: SuggestionState,
    onModeChange: (ImeAiMode) -> Unit,
    onRefresh: () -> Unit,
    onExit: () -> Unit,
    onSelectReply: (String) -> Unit,
    onStyleConfirm: (Float, Float) -> Unit,
    onScan: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFF0F1F3))
            .padding(vertical = 4.dp)
    ) {
        // Action bar: B / C mode chips + refresh + scan + exit
        ActionBar(
            currentMode = state.mode,
            onModeChange = onModeChange,
            onRefresh = onRefresh,
            onScan = onScan,
            onExit = onExit
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Content: B mode or C mode
        AnimatedContent(
            targetState = state.mode,
            transitionSpec = {
                fadeIn() + slideInHorizontally { if (targetState == ImeAiMode.C) it / 4 else -it / 4 } togetherWith
                    fadeOut() + slideOutHorizontally { if (targetState == ImeAiMode.C) -it / 4 else it / 4 }
            },
            label = "modeSwitch"
        ) { mode ->
            when (mode) {
                ImeAiMode.B -> {
                    val texts = state.suggestions.map { it.text }
                    BModePanel(
                        suggestions = texts,
                        isLoading = state.isLoading,
                        streamingText = state.streamingPreview,
                        onSelect = onSelectReply
                    )
                }
                ImeAiMode.C -> {
                    CModePanel(onConfirm = onStyleConfirm)
                }
            }
        }

        // Error message
        if (state.errorMessage != null) {
            Text(
                text = state.errorMessage,
                fontSize = 11.sp,
                color = Color(0xFFCC4444),
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp)
            )
        }
    }
}

@Composable
private fun ActionBar(
    currentMode: ImeAiMode,
    onModeChange: (ImeAiMode) -> Unit,
    onRefresh: () -> Unit,
    onScan: () -> Unit,
    onExit: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ModeChip("B", currentMode == ImeAiMode.B) { onModeChange(ImeAiMode.B) }
        ModeChip("C", currentMode == ImeAiMode.C) { onModeChange(ImeAiMode.C) }

        Spacer(modifier = Modifier.weight(1f))

        ActionChip("刷新") { onRefresh() }
        ActionChip("扫描") { onScan() }
        ActionChip("退出") { onExit() }
    }
}

@Composable
private fun ModeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) Color(0xFFE3EBF8) else Color(0xFFF2F3F5)
    val border = if (selected) Color(0xFF7BA4D9) else Color(0xFFD0D3D8)
    val textColor = if (selected) Color(0xFF3B6DAA) else Color(0xFF555555)

    Text(
        text = label,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        color = textColor,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 6.dp)
    )
}

@Composable
private fun ActionChip(label: String, onClick: () -> Unit) {
    Text(
        text = label,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        color = Color.White,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xCC4A5568))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp)
    )
}
