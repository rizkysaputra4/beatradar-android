package com.boimin.beatradar.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.boimin.beatradar.ui.theme.Brand
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Animated BeatRadar Splash Screen:
 * 1. Oscilloscope draw-in of the Coral beat pulse.
 * 2. Sequential blooming of the Electric Violet radar arcs (Arc 1 -> Arc 2 -> Arc 3).
 * 3. Soft sonar ripple glow radiating from the radar center.
 * 4. Ambient rhythm heartbeat pulse.
 * 5. Coordinated wordmark entrance ("BeatRadar").
 */
@Composable
fun SplashView(modifier: Modifier = Modifier) {
    // Wordmark fade & slide entrance
    val wordmarkAlpha = remember { Animatable(0f) }
    val wordmarkOffset = remember { Animatable(16f) }

    LaunchedEffect(Unit) {
        delay(400) // Trigger wordmark entrance as the pulse reaches the radar
        launch {
            wordmarkAlpha.animateTo(1f, tween(450, easing = FastOutSlowInEasing))
        }
        launch {
            wordmarkOffset.animateTo(0f, tween(450, easing = FastOutSlowInEasing))
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0C0B10))
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Animated S1 Logo (Beat Pulse + Radar Arcs + Sonar Ripple)
            AnimatedBeatRadarLogo(
                modifier = Modifier.size(92.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Typographic Wordmark with coordinated entrance
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .alpha(wordmarkAlpha.value)
                    .offset { IntOffset(0, wordmarkOffset.value.roundToInt()) }
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Beat",
                        color = Color.White,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-0.5).sp
                    )
                    Text(
                        text = "Radar",
                        color = Brand.Violet,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-0.5).sp
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "BPM & Rhythm Radar",
                    color = Color(0xFF8E8EA0),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.5.sp
                )
            }
        }
    }
}

/**
 * High-performance vector canvas animation of the S1 logo.
 */
@Composable
fun AnimatedBeatRadarLogo(
    modifier: Modifier = Modifier,
    isPulsing: Boolean = true
) {
    // Phase 1: Beat line sweep progress (0f to 1f)
    val pulseProgress = remember { Animatable(0f) }

    // Phase 2: Sequential radar arcs alpha
    val arc1Alpha = remember { Animatable(0f) }
    val arc2Alpha = remember { Animatable(0f) }
    val arc3Alpha = remember { Animatable(0f) }

    // Phase 3: Continuous rhythm heartbeat pulse
    val infiniteTransition = rememberInfiniteTransition(label = "heartbeat")
    val heartbeatScale by infiniteTransition.animateFloat(
        initialValue = 0.98f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "heartbeatScale"
    )

    // Sonar radar glow ripple
    val sonarRipple by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sonarRipple"
    )

    LaunchedEffect(Unit) {
        // 1. Sweep Coral beat pulse from left to right
        pulseProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing)
        )

        // 2. Ripple radar arcs sequentially outward
        launch {
            arc1Alpha.animateTo(1f, tween(220, easing = FastOutSlowInEasing))
        }
        delay(90)
        launch {
            arc2Alpha.animateTo(0.85f, tween(220, easing = FastOutSlowInEasing))
        }
        delay(90)
        launch {
            arc3Alpha.animateTo(0.55f, tween(220, easing = FastOutSlowInEasing))
        }
    }

    val currentScale = if (isPulsing && pulseProgress.value >= 1f) heartbeatScale else 1f

    val pathMeasure = remember { PathMeasure() }

    Box(
        modifier = modifier.scale(currentScale),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val scale = min(size.width, size.height) / 108f
            val strokeWidth = 6.5f * scale

            // Center of radar arcs
            val cx = 49f * scale
            val cy = 54f * scale

            // 1. Ambient Sonar Pulse Glow (radiating behind the radar)
            if (arc1Alpha.value > 0f) {
                val rippleRadius = (15f + sonarRipple * 28f) * scale
                val rippleAlpha = ((1f - sonarRipple) * 0.22f) * arc1Alpha.value
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Brand.Violet.copy(alpha = rippleAlpha),
                            Brand.Violet.copy(alpha = rippleAlpha * 0.4f),
                            Color.Transparent
                        ),
                        center = Offset(cx, cy),
                        radius = rippleRadius
                    ),
                    radius = rippleRadius,
                    center = Offset(cx, cy)
                )
            }

            // 2. Draw animated Coral beat line
            if (pulseProgress.value > 0f) {
                val scaledPath = Path().apply {
                    moveTo(18f * scale, 54f * scale)
                    lineTo(24f * scale, 54f * scale)
                    lineTo(28f * scale, 61f * scale)
                    lineTo(34f * scale, 26f * scale)
                    lineTo(40f * scale, 78f * scale)
                    lineTo(45f * scale, 54f * scale)
                    lineTo(49f * scale, 54f * scale)
                }

                val trimmedPath = Path()
                pathMeasure.setPath(scaledPath, false)
                val totalLength = pathMeasure.length
                pathMeasure.getSegment(0f, totalLength * pulseProgress.value, trimmedPath, true)

                drawPath(
                    path = trimmedPath,
                    color = Brand.Coral,
                    style = Stroke(
                        width = strokeWidth,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
            }

            // 3. Draw Radar Arcs sequentially
            // Arc 1 (Inner, R=11)
            if (arc1Alpha.value > 0f) {
                val r1 = 11f * scale
                drawArc(
                    color = Brand.Violet.copy(alpha = arc1Alpha.value),
                    startAngle = -90f,
                    sweepAngle = 180f,
                    useCenter = false,
                    topLeft = Offset(cx - r1, cy - r1),
                    size = Size(r1 * 2f, r1 * 2f),
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            }

            // Arc 2 (Middle, R=21)
            if (arc2Alpha.value > 0f) {
                val r2 = 21f * scale
                drawArc(
                    color = Brand.Violet.copy(alpha = arc2Alpha.value),
                    startAngle = -90f,
                    sweepAngle = 180f,
                    useCenter = false,
                    topLeft = Offset(cx - r2, cy - r2),
                    size = Size(r2 * 2f, r2 * 2f),
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            }

            // Arc 3 (Outer, R=31)
            if (arc3Alpha.value > 0f) {
                val r3 = 31f * scale
                drawArc(
                    color = Brand.Violet.copy(alpha = arc3Alpha.value),
                    startAngle = -90f,
                    sweepAngle = 180f,
                    useCenter = false,
                    topLeft = Offset(cx - r3, cy - r3),
                    size = Size(r3 * 2f, r3 * 2f),
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            }
        }
    }
}
