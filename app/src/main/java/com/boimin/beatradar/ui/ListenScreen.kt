package com.boimin.beatradar.ui

import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core. FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.boimin.beatradar.R
import com.boimin.beatradar.capture.MicHit
import com.boimin.beatradar.capture.MicHits
import com.boimin.beatradar.capture.MonitorState
import com.boimin.beatradar.capture.MonitorStatus
import com.boimin.beatradar.data.NameSuggestion
import com.boimin.beatradar.data.Settings
import com.boimin.beatradar.ui.theme.Brand
import kotlinx.coroutines.launch
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.roundToInt

@Composable
fun ListenScreen(
    state: MonitorState,
    settings: Settings,
    actions: AppActions,
    onSettingsChange: ((Settings) -> Settings) -> Unit,
    /** A name the server suggests for the unnamed song playing (PLAN §5c). */
    suggestion: NameSuggestion? = null,
    onAcceptSuggestion: () -> Unit = {},
    onRejectSuggestion: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val inTarget = state.matched || (state.status == MonitorStatus.LISTENING && state.bpm != null && settings.rangeEnabled && settings.matches(state.bpm))

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(4.dp))
        SourceChip(state, settings, inTarget)
        Spacer(Modifier.height(8.dp))

        val beat = rememberBeatClock(
            bpm = state.bpm.takeIf { state.status == MonitorStatus.LISTENING },
            lastBeatAtMs = state.lastBeatAtMs,
        )
        val listening = state.status == MonitorStatus.LISTENING
        val ripples = rememberHitRipples(listening, beat)
        BeatCircle(
            state = state,
            settings = settings,
            inTarget = inTarget,
            ripples = ripples,
            pulse = if (listening && state.bpm != null && state.lastBeatAtMs != null) beat.pulse else ripples.kick,
            showProgress = settings.rangeEnabled && state.active,
            onToggle = if (state.active) actions.stop else actions.start,
        )

        Spacer(Modifier.height(8.dp))
        NowPlayingCard(
            state = state,
            inTarget = inTarget,
            suggestion = suggestion,
            onAcceptSuggestion = onAcceptSuggestion,
            onRejectSuggestion = onRejectSuggestion,
            actions = actions,
        )
        Spacer(Modifier.height(10.dp))
        RangeCard(settings, onSettingsChange)
        Spacer(Modifier.height(16.dp))
    }
}

/** Minimalist status pill with animated 3-bar equalizer and live target verdict. */
@Composable
private fun SourceChip(state: MonitorState, settings: Settings, inTarget: Boolean) {
    val listening = state.status == MonitorStatus.LISTENING
    val accentColor = if (inTarget) Brand.Coral else MaterialTheme.colorScheme.primary
    val sourceText = when {
        !state.active -> "Ambient Mic Ready"
        state.phoneApp != null -> "Phone · ${state.phoneApp}"
        else -> "Ambient Mic"
    }
    val verdictText = when {
        !state.active || !settings.rangeEnabled || state.bpm == null -> null
        state.matched || settings.matches(state.bpm) -> "IN TARGET"
        else -> "OUT OF RANGE"
    }

    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (listening) {
            EqualizerBars(color = accentColor)
        } else {
            Icon(
                painterResource(R.drawable.ic_mic),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(14.dp),
            )
        }
        Spacer(Modifier.width(7.dp))
        Text(
            buildAnnotatedString {
                append(sourceText)
                if (verdictText != null) {
                    append(" · ")
                    withStyle(
                        SpanStyle(
                            color = if (inTarget) Brand.Coral else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Bold,
                        ),
                    ) {
                        append(verdictText)
                    }
                }
            },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun EqualizerBars(color: Color) {
    val transition = rememberInfiniteTransition(label = "eq")
    val h1 by transition.animateFloat(
        initialValue = 4f,
        targetValue = 11f,
        animationSpec = infiniteRepeatable(tween(420, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "eq1",
    )
    val h2 by transition.animateFloat(
        initialValue = 11f,
        targetValue = 4f,
        animationSpec = infiniteRepeatable(tween(360, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "eq2",
    )
    val h3 by transition.animateFloat(
        initialValue = 6f,
        targetValue = 12f,
        animationSpec = infiniteRepeatable(tween(480, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "eq3",
    )
    Row(
        Modifier.height(12.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (h in listOf(h1, h2, h3)) {
            Box(
                Modifier
                    .width(2.5.dp)
                    .height(h.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(color),
            )
        }
    }
}

private class BeatClock {
    /** 1 right on a beat, decaying to 0 before the next. */
    var pulse by mutableFloatStateOf(0f)
    /** A time a beat landed and the beat length; null while no beat is heard. */
    var anchorMs: Long? = null
    var periodMs = 0.0
}

/**
 * Runs a beat grid in sync with the detector. The grid keeps counting smoothly; each new reading
 * only nudges its phase, unless it drifted too far, in which case it re-locks.
 */
@Composable
private fun rememberBeatClock(bpm: Float?, lastBeatAtMs: Long?): BeatClock {
    val clock = remember { BeatClock() }
    val grid = remember { LongArray(1) { NO_ANCHOR } }

    if (bpm != null && lastBeatAtMs != null) {
        val period = 60_000.0 / bpm
        val current = grid[0]
        grid[0] = if (current == NO_ANCHOR) {
            lastBeatAtMs
        } else {
            val beats = (lastBeatAtMs - current) / period
            val whole = beats.roundToInt()
            if (kotlin.math.abs(beats - whole) > 0.3) lastBeatAtMs else (lastBeatAtMs - whole * period).toLong()
        }
    } else {
        grid[0] = NO_ANCHOR
    }
    val anchor = grid[0].takeIf { it != NO_ANCHOR }

    LaunchedEffect(bpm, anchor) {
        val a = anchor
        if (bpm == null || a == null) {
            clock.pulse = 0f
            clock.anchorMs = null
            return@LaunchedEffect
        }
        val period = 60_000.0 / bpm
        clock.anchorMs = a
        clock.periodMs = period
        while (true) {
            withFrameMillis {
                val since = SystemClock.elapsedRealtime() - a
                val beats = since / period
                val phase = beats - floor(beats)
                clock.pulse = (1 - phase).pow(3).toFloat()
            }
        }
    }
    return clock
}

private const val NO_ANCHOR = Long.MIN_VALUE

/**
 * Precision Sonar Dial: smooth concentric rings, high-contrast circular progress arc, and tactile center dial.
 */
@Composable
private fun BeatCircle(
    state: MonitorState,
    settings: Settings,
    inTarget: Boolean,
    ripples: HitRipples,
    pulse: Float,
    showProgress: Boolean,
    onToggle: () -> Unit,
) {
    val listening = state.status == MonitorStatus.LISTENING
    val accent by animateColorAsState(
        if (inTarget) Brand.Coral else MaterialTheme.colorScheme.primary,
        label = "accent",
    )
    val progress by animateFloatAsState(
        targetValue = when {
            !showProgress -> 0f
            state.matched -> 1f
            else -> state.matchProgress
        },
        animationSpec = tween(400),
        label = "progress",
    )
    val numberAlpha by animateFloatAsState(if (state.bpmSettled) 1f else 0.6f, tween(350), label = "settled")

    Box(
        Modifier
            .fillMaxWidth()
            .height(226.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (listening) SonarRings(accent)
        if (listening) SmoothHitRipples(ripples, accent)

        Box(
            Modifier
                .size(196.dp)
                .scale(1f + 0.04f * pulse),
            contentAlignment = Alignment.Center,
        ) {
            ProgressArc(
                progress = if (showProgress) progress else if (listening && state.bpm != null) 0.72f else 0f,
                accent = accent,
                trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.55f),
            )
            val borderColor = when {
                inTarget -> Brand.Coral
                state.active -> MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                else -> MaterialTheme.colorScheme.outline
            }
            Surface(
                onClick = onToggle,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainer,
                contentColor = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .size(164.dp)
                    .border(2.dp, borderColor, CircleShape),
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    if (state.active) accent.copy(alpha = 0.14f) else Color.Transparent,
                                    Color.Transparent,
                                ),
                            ),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        when {
                            state.bpm != null && listening -> {
                                Text(
                                    "BPM",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.5.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    state.bpm.roundToInt().toString(),
                                    fontSize = 50.sp,
                                    lineHeight = 52.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = (-2).sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.alpha(numberAlpha),
                                )
                                Spacer(Modifier.height(4.dp))
                                DialStatusBadge(state, settings, inTarget)
                            }
                            else -> {
                                Icon(
                                    painterResource(R.drawable.ic_mic),
                                    contentDescription = if (state.active) "Stop listening" else "Start listening",
                                    tint = if (state.active) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(38.dp),
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    when (state.status) {
                                        MonitorStatus.LISTENING -> "LISTENING…"
                                        MonitorStatus.PAUSED -> "PAUSED"
                                        else -> "TAP TO LISTEN"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp,
                                    color = if (state.active) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DialStatusBadge(state: MonitorState, settings: Settings, inTarget: Boolean) {
    val seconds = (state.matchProgress * 10).toInt()
    val (label, bg, fg) = when {
        state.matched && state.alreadyListed -> Triple(
            "✓ In Log · 10s",
            Brand.Coral.copy(alpha = 0.16f),
            Brand.Coral,
        )
        state.matched -> Triple(
            "✓ Saved · 10s",
            Brand.Coral.copy(alpha = 0.16f),
            Brand.Coral,
        )
        inTarget -> Triple(
            "Hold ${seconds}s / 10s",
            Brand.Coral.copy(alpha = 0.16f),
            Brand.Coral,
        )
        settings.rangeEnabled -> Triple(
            "Target ${settings.minBpm}–${settings.maxBpm}",
            MaterialTheme.colorScheme.surfaceContainerHigh,
            MaterialTheme.colorScheme.onSurfaceVariant,
        )
        state.bpmSettled -> Triple(
            "Locked",
            MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
            MaterialTheme.colorScheme.primary,
        )
        else -> Triple(
            "Measuring…",
            MaterialTheme.colorScheme.surfaceContainerHigh,
            MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .padding(horizontal = 9.dp, vertical = 3.dp),
    ) {
        Text(
            label,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = fg,
            maxLines = 1,
        )
    }
}

@Composable
private fun ProgressArc(progress: Float, accent: Color, trackColor: Color) {
    Canvas(Modifier.fillMaxSize()) {
        val strokeWidth = 4.dp.toPx()
        val radius = 89.dp.toPx()
        val arcTopLeft = Offset(center.x - radius, center.y - radius)
        val arcSize = Size(radius * 2, radius * 2)
        drawArc(
            color = trackColor,
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = arcTopLeft,
            size = arcSize,
            style = Stroke(width = strokeWidth),
        )
        if (progress > 0f) {
            drawArc(
                color = accent,
                startAngle = -90f,
                sweepAngle = 360f * progress.coerceIn(0f, 1f),
                useCenter = false,
                topLeft = arcTopLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            )
        }
    }
}

private class HitRipples {
    val startMs = LongArray(MAX_RIPPLES) { Long.MIN_VALUE / 2 }
    val strength = FloatArray(MAX_RIPPLES)
    private var next = 0
    private var loudness = 0.7f
    var nowMs by mutableLongStateOf(SystemClock.elapsedRealtime())

    fun onHit(hit: MicHit, beatFound: Boolean) {
        loudness = 0.8f * loudness + 0.2f * hit.strength
        if (!beatFound) send(hit.atMs, hit.strength)
    }

    fun onBeat(atMs: Long) = send(atMs, loudness.coerceIn(0.6f, 1f))

    private fun send(atMs: Long, s: Float) {
        startMs[next] = atMs
        strength[next] = s
        next = (next + 1) % MAX_RIPPLES
    }

    val kick: Float
        get() {
            var best = 0f
            for (i in 0 until MAX_RIPPLES) {
                val t = (nowMs - startMs[i]) / KICK_MS
                if (t in 0f..1f) best = maxOf(best, strength[i] * (1 - t) * (1 - t) * (1 - t))
            }
            return best
        }
}

@Composable
private fun rememberHitRipples(listening: Boolean, beat: BeatClock): HitRipples {
    val ripples = remember { HitRipples() }
    LaunchedEffect(listening) {
        if (!listening) return@LaunchedEffect
        launch { MicHits.hits.collect { ripples.onHit(it, beatFound = beat.anchorMs != null) } }
        var lastBeat = Long.MIN_VALUE
        var lastSentMs = Long.MIN_VALUE / 2
        while (true) withFrameMillis {
            val now = SystemClock.elapsedRealtime()
            ripples.nowMs = now
            val anchor = beat.anchorMs
            val period = beat.periodMs
            if (anchor != null && period > 0) {
                val k = floor((now - anchor) / period).toLong()
                if (k != lastBeat) {
                    val at = anchor + (k * period).toLong()
                    if (at - lastSentMs > period * 0.6) {
                        ripples.onBeat(at)
                        lastSentMs = at
                    }
                    lastBeat = k
                }
            } else {
                lastBeat = Long.MIN_VALUE
            }
        }
    }
    return ripples
}

/** Smooth concentric beat shockwaves expanding cleanly from the sonar dial. */
@Composable
private fun SmoothHitRipples(ripples: HitRipples, accent: Color) {
    Canvas(Modifier.fillMaxSize()) {
        val now = ripples.nowMs
        val inner = 84.dp.toPx()
        val outer = size.minDimension / 2 + 16.dp.toPx()
        val glow = 32.dp.toPx()
        for (i in 0 until MAX_RIPPLES) {
            val t = (now - ripples.startMs[i]) / RIPPLE_MS
            if (t !in 0f..1f) continue
            val s = ripples.strength[i]
            val fade = 1f - t
            val travel = 1f - fade * fade
            val r = inner + (outer - inner) * travel
            drawCircle(
                Brush.radialGradient(
                    0f to Color.Transparent,
                    ((r - glow) / r).coerceAtLeast(0f) to Color.Transparent,
                    1f to accent.copy(alpha = 0.25f * s * fade),
                    center = center,
                    radius = r,
                ),
                radius = r,
            )
            drawCircle(
                color = accent.copy(alpha = 0.55f * s * fade),
                radius = r,
                style = Stroke(width = 1.5.dp.toPx()),
            )
        }
    }
}

private const val MAX_RIPPLES = 8
private const val RIPPLE_MS = 1_100f
private const val KICK_MS = 220f

@Composable
private fun SonarRings(accent: Color) {
    val travel by rememberInfiniteTransition(label = "sonar").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(SONAR_RIPPLE_MS, easing = LinearEasing)),
        label = "sonarTravel",
    )
    Canvas(Modifier.fillMaxSize()) {
        val inner = 86.dp.toPx()
        val outer = size.minDimension / 2 + 12.dp.toPx()
        for (i in 0 until SONAR_RINGS) {
            val t = (travel + i.toFloat() / SONAR_RINGS) % 1f
            drawCircle(
                accent.copy(alpha = 0.35f * (1f - t)),
                radius = inner + (outer - inner) * t,
                style = Stroke(1.2.dp.toPx()),
            )
        }
    }
}

private const val SONAR_RIPPLE_MS = 3_400
private const val SONAR_RINGS = 2

/**
 * Unified Now Playing & Crowd Suggestion card below the dial.
 */
@Composable
private fun NowPlayingCard(
    state: MonitorState,
    inTarget: Boolean,
    suggestion: NameSuggestion?,
    onAcceptSuggestion: () -> Unit,
    onRejectSuggestion: () -> Unit,
    actions: AppActions,
) {
    val unnamed = state.title == null && state.placeholderNo != null && state.active
    val titleText = when {
        state.title != null -> state.title
        unnamed -> "Unnamed Track #${state.placeholderNo}"
        state.status == MonitorStatus.LISTENING -> "Listening for a beat…"
        state.status == MonitorStatus.PAUSED -> "Monitoring paused"
        else -> "Ready to detect tempo"
    }
    val subText = when {
        state.title != null -> listOfNotNull(state.artist, state.phoneApp ?: "Ambient").joinToString(" · ")
        unnamed -> "Tap ID to Shazam or accept crowd match"
        state.active && state.phoneApp != null -> "Hearing ${state.phoneApp} on this phone"
        state.active -> "Hearing music around you"
        else -> "Hold phone near the music and tap the dial"
    }
    val iconBg = when {
        inTarget -> Brand.Coral.copy(alpha = 0.15f)
        state.active -> MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
        else -> MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val iconTint = when {
        inTarget -> Brand.Coral
        state.active -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
            .padding(12.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(iconBg),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(if (state.title != null) R.drawable.ic_speaker else R.drawable.ic_radar),
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(
                Modifier
                    .weight(1f)
                    .then(if (unnamed) Modifier.clickable(onClick = actions.identify) else Modifier),
            ) {
                Text(
                    titleText,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    subText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (state.active) {
                Spacer(Modifier.width(8.dp))
                val paused = state.status == MonitorStatus.PAUSED
                Row(
                    Modifier
                        .height(32.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(9.dp))
                        .clickable(onClick = if (paused) actions.resume else actions.pause)
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        painterResource(if (paused) R.drawable.ic_play else R.drawable.ic_pause),
                        contentDescription = if (paused) "Resume" else "Pause",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(13.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        if (paused) "Resume" else "Pause",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Spacer(Modifier.width(6.dp))
                Row(
                    Modifier
                        .height(32.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
                        .clickable(onClick = actions.identify)
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        painterResource(R.drawable.ic_search),
                        contentDescription = "Identify with Shazam",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(13.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "ID",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }

        if (unnamed && suggestion != null) {
            Spacer(Modifier.height(9.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val label = buildString {
                    append(suggestion.title)
                    if (suggestion.artist.isNotBlank()) append(" · ${suggestion.artist}")
                    append("?")
                }
                Text(
                    buildAnnotatedString {
                        append("Crowd match: ")
                        withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)) {
                            append(label)
                        }
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Brand.Success.copy(alpha = 0.15f))
                        .clickable(onClick = onAcceptSuggestion)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Text("Yes", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Brand.Success)
                }
                Spacer(Modifier.width(4.dp))
                Box(
                    Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .clickable(onClick = onRejectSuggestion)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Text("No", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

private data class TempoPreset(val label: String, val min: Int, val max: Int)

private val TEMPO_PRESETS = listOf(
    TempoPreset("Groove 95–110", 95, 110),
    TempoPreset("House 120–132", 120, 132),
    TempoPreset("Run 155–175", 155, 175),
)

@Composable
private fun RangeCard(settings: Settings, onSettingsChange: ((Settings) -> Settings) -> Unit) {
    var range by remember(settings.minBpm, settings.maxBpm) {
        mutableStateOf(settings.minBpm.toFloat()..settings.maxBpm.toFloat())
    }
    var showCustomDialog by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
            .padding(14.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "Target Range",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    if (settings.rangeEnabled) "Vibrates after 10s in target" else "Off — logging all tempos",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (settings.rangeEnabled) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Brand.Coral.copy(alpha = 0.14f))
                        .clickable { showCustomDialog = true }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Text(
                        "${range.start.roundToInt()} – ${range.endInclusive.roundToInt()} BPM",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Brand.Coral,
                    )
                }
                Spacer(Modifier.width(8.dp))
            }
            Switch(
                checked = settings.rangeEnabled,
                onCheckedChange = { on -> onSettingsChange { it.copy(rangeEnabled = on) } },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                ),
            )
        }
        AnimatedVisibility(settings.rangeEnabled) {
            Column {
                Spacer(Modifier.height(6.dp))
                RangeSlider(
                    value = range,
                    onValueChange = { range = it },
                    valueRange = Settings.BPM_FLOOR.toFloat()..Settings.BPM_CEILING.toFloat(),
                    steps = Settings.BPM_CEILING - Settings.BPM_FLOOR - 1,
                    onValueChangeFinished = {
                        val lo = range.start.roundToInt()
                        val hi = range.endInclusive.roundToInt()
                        onSettingsChange { it.copy(minBpm = lo, maxBpm = hi) }
                    },
                    colors = SliderDefaults.colors(
                        thumbColor = Brand.Coral,
                        activeTrackColor = Brand.Coral,
                        inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ),
                )
                Spacer(Modifier.height(4.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    val curMin = range.start.roundToInt()
                    val curMax = range.endInclusive.roundToInt()
                    TEMPO_PRESETS.forEach { preset ->
                        val active = curMin == preset.min && curMax == preset.max
                        Box(
                            Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                                    else MaterialTheme.colorScheme.surfaceContainerHigh,
                                )
                                .border(
                                    1.dp,
                                    if (active) MaterialTheme.colorScheme.primary else Color.Transparent,
                                    RoundedCornerShape(8.dp),
                                )
                                .clickable {
                                    range = preset.min.toFloat()..preset.max.toFloat()
                                    onSettingsChange { it.copy(minBpm = preset.min, maxBpm = preset.max) }
                                }
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                preset.label,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }

    if (showCustomDialog) {
        CustomRangeDialog(
            minBpm = settings.minBpm,
            maxBpm = settings.maxBpm,
            onDismiss = { showCustomDialog = false },
            onSave = { lo, hi ->
                showCustomDialog = false
                range = lo.toFloat()..hi.toFloat()
                onSettingsChange { it.copy(minBpm = lo, maxBpm = hi) }
            },
        )
    }
}

@Composable
private fun CustomRangeDialog(minBpm: Int, maxBpm: Int, onDismiss: () -> Unit, onSave: (Int, Int) -> Unit) {
    var minText by remember { mutableStateOf(minBpm.toString()) }
    var maxText by remember { mutableStateOf(maxBpm.toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Custom BPM Range") },
        text = {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = minText,
                    onValueChange = { if (it.length <= 3 && it.all(Char::isDigit)) minText = it },
                    label = { Text("Min BPM") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = maxText,
                    onValueChange = { if (it.length <= 3 && it.all(Char::isDigit)) maxText = it },
                    label = { Text("Max BPM") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val a = (minText.toIntOrNull() ?: minBpm).coerceIn(Settings.BPM_FLOOR, Settings.BPM_CEILING)
                    val b = (maxText.toIntOrNull() ?: maxBpm).coerceIn(Settings.BPM_FLOOR, Settings.BPM_CEILING)
                    onSave(minOf(a, b), maxOf(a, b))
                },
            ) { Text("Apply") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
