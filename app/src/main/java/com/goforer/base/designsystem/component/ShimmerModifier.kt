package com.goforer.base.designsystem.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntSize

@Composable
fun Modifier.shimmer(
    baseColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    highlightColor: Color = MaterialTheme.colorScheme.surface,
    durationMillis: Int = 1_200,
): Modifier = composed {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = durationMillis),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "shimmer-progress",
    )

    // Compose the base fill first, then overlay the moving gradient using a
    // drawWithContent pattern. A linear gradient whose start/end points travel
    // across the bounds produces the sheen.
    this
        .background(baseColor)
        .graphicsLayer { }                // establishes a draw layer so the
                                          // brush below composites cleanly
        .background(
            brush = Brush.linearGradient(
                colors = listOf(
                    baseColor,
                    highlightColor,
                    baseColor,
                ),
                // The gradient's end-point slides with `progress`, sweeping the
                // highlight across the element. Using a pair of float coords
                // (x, y) of equal magnitude keeps the sweep diagonal.
                start = Offset(
                    x = progress * 1_000f - 500f,
                    y = progress * 1_000f - 500f,
                ),
                end = Offset(
                    x = progress * 1_000f - 500f + 400f,
                    y = progress * 1_000f - 500f + 400f,
                ),
            )
        )
}

fun Modifier.shimmer(
    durationMillis: Int = 1200,
    shimmerColor: Color = Color.White.copy(alpha = 0.3f) // 하이라이트 줄 컬러
): Modifier = composed {
    var size by remember { mutableStateOf(IntSize.Zero) }
    val transition = rememberInfiniteTransition(label = "shimmer_transition")
    val translateAnim by transition.animateFloat(
        initialValue = -2f * size.width.toFloat(),
        targetValue = 2f * size.width.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_translate"
    )

    val brush = if (size.width > 0 && size.height > 0) {
        Brush.linearGradient(
            colors = listOf(
                Color.Transparent,
                shimmerColor,
                Color.Transparent,
            ),
            start = Offset(translateAnim, 0f),
            end = Offset(translateAnim + size.width.toFloat(), size.height.toFloat())
        )
    } else {
        Brush.linearGradient(listOf(Color.Transparent, Color.Transparent))
    }

    this
        .onGloballyPositioned { size = it.size }
        .background(brush)
}

/**
 * A convenience overload that hard-codes `MaterialTheme.colorScheme.surface`
 * as the highlight colour — this matches what every previous call site passed.
 *
 * Keep [shimmer] if you need a custom highlight; prefer this when the defaults
 * are fine (which is almost always).
 */
@Composable
@Suppress("unused")
fun Modifier.shimmerWithDefaults(): Modifier =
    shimmer(
        baseColor = MaterialTheme.colorScheme.surfaceVariant,
        highlightColor = MaterialTheme.colorScheme.surface,
    )
