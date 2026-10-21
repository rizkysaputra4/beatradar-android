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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.boimin.beatradar.ui.theme.Brand
import kotlinx.coroutines.delay
import kotlin.math.min

/**
 * Pixel-perfect BeatRadar Splash Screen.
 * 
 * Critical layout design:
 * The Logo Mark is locked strictly at Alignment.Center of the root full-bleed Box,
 * matching Android's system splash screen window coordinates 1:1 with ZERO position shift.
 * The Wordmark is positioned strictly below the center mark as a sibling with an offset,
 * completely preventing any vertical shift or jumping.
 */
@Composable
fun SplashView(modifier: Modifier = Modifier) {
    // Wordmark entrance: smooth fade-in directly below the centered logo
    val wordmarkAlpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        delay(150)
        wordmarkAlpha.animateTo(1f, tween(400, easing = FastOutSlowInEasing))
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0C0B10)),
        contentAlignment = Alignment.Center
    ) {
        // 1. Logo Mark: Anchored at the EXACT screen center (matches Android OS splash 1:1)
        AnimatedBeatRadarLogo(
            modifier = Modifier
                .align(Alignment.Center)
                .size(96.dp)
        )

        // 2. Wordmark: Placed cleanly below the center logo without shifting the logo's position
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = 78.dp)
                .alpha(wordmarkAlpha.value)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Beat",
                    color = Color.White,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.5).sp
                )
                Text(
                    text = "Radar",
                    color = Brand.Violet,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.5).sp
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

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

/**
 * Animated S1 logo mark:
 * Retains full visibility on frame 0 to match the system splash screen seamlessly (zero flicker/blanking),
 * then pulses with rhythmic audio energy and expands sonar waves outward.
 */
@Composable
fun AnimatedBeatRadarLogo(
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "beatPulse")

    // Rhythmic tempo heartbeat pulse
    val heartbeatScale by infiniteTransition.animateFloat(
        initialValue = 0.98f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "heartbeatScale"
    )

    // Sonar radar glow ripple radiating outward from the radar focal center
    val sonarRipple by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sonarRipple"
    )

    // Subtle breathing glow on the outer radar arc
    val arc3Pulse by infiniteTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 0.70f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "arc3Pulse"
    )

    Box(
        modifier = modifier.scale(heartbeatScale),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val scale = min(size.width, size.height) / 108f
            val strokeWidth = 6.5f * scale

            // Center of radar arcs (matches S1 spec)
            val cx = 49f * scale
            val cy = 54f * scale

            // 1. Ambient Sonar Pulse Glow (radiating behind the radar arcs)
            val rippleRadius = (12f + sonarRipple * 30f) * scale
            val rippleAlpha = (1f - sonarRipple) * 0.22f
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

            // 2. Pulse Line (Coral #FF5A5F - Tempo Energy)
            val pulsePath = Path().apply {
                moveTo(18f * scale, 54f * scale)
                lineTo(24f * scale, 54f * scale)
                lineTo(28f * scale, 61f * scale)
                lineTo(34f * scale, 26f * scale)
                lineTo(40f * scale, 78f * scale)
                lineTo(45f * scale, 54f * scale)
                lineTo(49f * scale, 54f * scale)
            }
            drawPath(
                path = pulsePath,
                color = Brand.Coral,
                style = Stroke(
                    width = strokeWidth,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )

            // 3. Radar Arcs (Electric Violet #7C6CF6)
            // Arc 1 (Inner, R=11)
            val r1 = 11f * scale
            drawArc(
                color = Brand.Violet,
                startAngle = -90f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = Offset(cx - r1, cy - r1),
                size = Size(r1 * 2f, r1 * 2f),
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            // Arc 2 (Middle, R=21)
            val r2 = 21f * scale
            drawArc(
                color = Brand.Violet.copy(alpha = 0.8f),
                startAngle = -90f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = Offset(cx - r2, cy - r2),
                size = Size(r2 * 2f, r2 * 2f),
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            // Arc 3 (Outer, R=31 with breathing wave energy)
            val r3 = 31f * scale
            drawArc(
                color = Brand.Violet.copy(alpha = arc3Pulse),
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
