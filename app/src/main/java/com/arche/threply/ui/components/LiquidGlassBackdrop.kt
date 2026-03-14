package com.arche.threply.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.arche.threply.ui.theme.ThreplyColors

/**
 * Full-screen gradient backdrop with liquid glass effect.
 * Equivalent to iOS LiquidGlassBackdrop.
 */
@Composable
fun LiquidGlassBackdrop(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize()) {
        // Base dark gradient
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Background gradient
            drawRect(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.Black,
                        Color(0xFF0A0D14),
                        Color(0xFF050508)
                    ),
                    start = Offset.Zero,
                    end = Offset(size.width, size.height)
                )
            )

            // Cyan glow (top-left)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        ThreplyColors.gradientCyan,
                        Color.Transparent
                    ),
                    center = Offset(0f, 0f),
                    radius = size.maxDimension * 0.55f
                ),
                radius = size.maxDimension * 0.55f,
                center = Offset(0f, 0f)
            )

            // Orange glow (top-right)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        ThreplyColors.gradientOrange,
                        Color.Transparent
                    ),
                    center = Offset(size.width, 0f),
                    radius = size.maxDimension * 0.65f
                ),
                radius = size.maxDimension * 0.65f,
                center = Offset(size.width, 0f)
            )

            // Purple glow (bottom-left)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        ThreplyColors.gradientPurple,
                        Color.Transparent
                    ),
                    center = Offset(0f, size.height),
                    radius = size.maxDimension * 0.70f
                ),
                radius = size.maxDimension * 0.70f,
                center = Offset(0f, size.height)
            )

            // Dark overlay to tone down
            drawRect(Color.Black.copy(alpha = 0.35f))
        }
    }
}

/**
 * Simple radial gradient background for home screen.
 * Equivalent to iOS ContentView background.
 */
@Composable
fun RadialGradientBackground(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.fillMaxSize()) {
        drawRect(Color.Black)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.Black,
                    Color(0xFF141420)
                ),
                center = Offset.Zero,
                radius = size.maxDimension * 0.6f
            ),
            radius = size.maxDimension,
            center = Offset.Zero
        )
    }
}
