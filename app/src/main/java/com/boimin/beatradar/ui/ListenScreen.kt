package com.boimin.beatradar.ui

import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.text.font.FontFamily
import com.boimin.beatradar.ui.theme.Tone
import androidx.compose.material3.ExperimentalMaterial3Api
import com.boimin.beatradar.ui.theme.inTargetColor
import com.boimin.beatradar.data.BeatLogEntry
import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.boimin.beatradar.R
import com.boimin.beatradar.capture.MicHit
import com.boimin.beatradar.capture.MicHits
import com.boimin.beatradar.capture.MonitorState
import com.boimin.beatradar.capture.MonitorStatus
import com.boimin.beatradar.data.NameSuggestion
import com.boimin.beatradar.data.Settings
import com.boimin.beatradar.i18n.str
import com.boimin.beatradar.i18n.strPlural
import androidx.annotation.StringRes
import com.boimin.beatradar.ui.theme.Brand
import com.boimin.beatradar.ui.theme.brandSwitchColors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
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
    /** The last few songs in the log, for "Heard recently". */
    recent: List<BeatLogEntry> = emptyList(),
    onOpenSong: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val listening = state.status == MonitorStatus.LISTENING
    val inTarget = state.matched || (listening && state.bpm != null && settings.rangeEnabled && settings.matches(state.bpm))
    var editingTarget by remember { mutableStateOf(false) }

    BoxWithConstraints(modifier.fillMaxSize()) {
        // On a short screen (a 320 × 569 dp phone, or a large font / display size) the dial shrinks so the target
        // chip and the now-playing card (Name song, Pause) still show without scrolling.
        val compact = maxHeight < COMPACT_HEIGHT
        val dialHeight = (maxHeight - if (compact) BELOW_DIAL_COMPACT else BELOW_DIAL).coerceIn(MIN_DIAL, FULL_DIAL)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Dial(state, settings, inTarget, height = dialHeight, onToggle = if (state.active) actions.stop else actions.start)
            if (listening && state.title != null) {
                Spacer(Modifier.height(if (compact) 6.dp else 12.dp))
                Text(
                    listOfNotNull(state.title, state.artist).joinToString(" · "),
                    fontSize = 14.sp,
                    color = Tone.text2,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(if (compact) 8.dp else 18.dp))
            TargetChip(settings, open = editingTarget, onClick = { editingTarget = true })
            Spacer(Modifier.height(if (compact) 12.dp else 22.dp))
            NowPlayingCard(
                state = state,
                suggestion = suggestion,
                onAcceptSuggestion = onAcceptSuggestion,
                onRejectSuggestion = onRejectSuggestion,
                actions = actions,
            )
            if (recent.isNotEmpty()) {
                Spacer(Modifier.height(22.dp))
                HeardRecently(recent, settings, onOpenSong)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
    if (editingTarget) TargetSheet(settings, onSettingsChange, onDismiss = { editingTarget = false })
}

/** The dial's full height; the ring is [RING_SHARE] of it. */
private val FULL_DIAL = 300.dp
private val MIN_DIAL = 130.dp
private const val RING_SHARE = 236f / 300f
/** Below this the screen counts as short: tighter spacing, and the dial gives up room. */
private val COMPACT_HEIGHT = 520.dp
/** What goes under the dial and should still fit: spacing, the 42 dp target chip and the ~76 dp now-playing card. */
private val BELOW_DIAL = 170.dp
private val BELOW_DIAL_COMPACT = 140.dp

/**
 * The dial: a ring (236 dp at full [height], 6 dp thick) with the tempo inside and, under it, how far off it is in
 * plain words. While listening, ripples go out from it on every beat and it kicks slightly. Holding the target fills
 * the ring in mint over the 10 s it takes to lock a match; matched, the whole ring is mint with a faint mint fill.
 */
@Composable
private fun Dial(state: MonitorState, settings: Settings, inTarget: Boolean, height: Dp, onToggle: () -> Unit) {
    val listening = state.status == MonitorStatus.LISTENING
    val mint = inTargetColor()
    val accent by animateColorAsState(if (inTarget) mint else MaterialTheme.colorScheme.primary, label = "accent")
    val fill by animateColorAsState(if (state.matched) mint.copy(alpha = 0.08f) else Color.Transparent, label = "fill")
    val lock by animateFloatAsState(
        when {
            !settings.rangeEnabled || !listening -> 0f
            state.matched -> 1f
            else -> state.matchProgress
        },
        tween(400),
        label = "lock",
    )
    val numberAlpha by animateFloatAsState(if (state.bpmSettled) 1f else 0.6f, tween(350), label = "settled")
    val beat = rememberBeatClock(bpm = state.bpm.takeIf { listening }, lastBeatAtMs = state.lastBeatAtMs)
    val ripples = rememberHitRipples(listening, beat)
    val pulse = if (listening && state.bpm != null && state.lastBeatAtMs != null) beat.pulse else ripples.kick
    val ring = Tone.ring
    // 0..1: how sure the reading is. Sure beats send stronger ripples further out and kick the dial harder.
    val sure by animateFloatAsState(confidencePercent(state.confidence.takeIf { listening }) / 100f, tween(600), label = "sure")

    val k = height / FULL_DIAL
    val ringRadius = height * RING_SHARE / 2

    Box(Modifier.fillMaxWidth().height(height), contentAlignment = Alignment.Center) {
        if (listening && !ripples.quiet) SonarRings(accent, ringRadius)
        if (listening) SmoothHitRipples(ripples, accent, sure, ringRadius, k)
        Box(
            Modifier
                .size(ringRadius * 2)
                .scale(1f + (0.01f + 0.05f * sure) * pulse)
                .clip(CircleShape)
                .background(fill)
                .drawBehind {
                    // The track, then the lock arc filling clockwise from the top.
                    val w = 6.dp.toPx()
                    val corner = Offset(w / 2, w / 2)
                    val arcSize = Size(size.width - w, size.height - w)
                    drawArc(ring, 0f, 360f, false, corner, arcSize, style = Stroke(w))
                    if (lock > 0f) {
                        drawArc(mint, -90f, 360f * lock.coerceIn(0f, 1f), false, corner, arcSize, style = Stroke(w, cap = StrokeCap.Round))
                    }
                }
                .clickable(onClickLabel = if (state.active) str(R.string.listen_stop_listening) else str(R.string.listen_start_listening), onClick = onToggle),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (listening && state.bpm != null) {
                    Text(str(R.string.listen_bpm_label), fontSize = 13.sp, letterSpacing = 1.56.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        state.bpm.roundToInt().toString(),
                        fontSize = 76.sp * k,
                        lineHeight = 76.sp * k,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.alpha(numberAlpha),
                    )
                    Spacer(Modifier.height(6.dp))
                    DialStatusBadge(state, settings, inTarget)
                    // A small dial (a very short screen) has no room for the confidence line.
                    if (k >= 0.6f) {
                        Spacer(Modifier.height(4.dp))
                        Text(str(R.string.listen_confidence_percent, (sure * 100).roundToInt()), fontSize = 13.sp, color = Tone.text3)
                    }
                } else {
                    Icon(
                        painterResource(R.drawable.ic_mic),
                        contentDescription = null,
                        tint = if (state.active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(34.dp),
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        when (state.status) {
                            MonitorStatus.LISTENING -> str(R.string.listen_listening)
                            MonitorStatus.PAUSED -> str(R.string.listen_paused)
                            else -> str(R.string.listen_tap_to_listen)
                        },
                        fontSize = 15.sp,
                        color = Tone.text2,
                    )
                }
            }
        }
    }
}

/** The reading's confidence as 0..100: the share of the tempo vote on that reading, so 80% means 80% sure. */
internal fun confidencePercent(confidence: Float?): Int =
    ((confidence ?: 0f) / FULL_CONFIDENCE * 100).roundToInt().coerceIn(0, 100)

private const val FULL_CONFIDENCE = 1f

/** Under the number, in plain words: "56 below target", "✓ In target · 8 s", or how sure the reading is. */
@Composable
private fun DialStatusBadge(state: MonitorState, settings: Settings, inTarget: Boolean) {
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(state.inTargetSinceMs) {
        while (state.inTargetSinceMs != null) {
            now = SystemClock.elapsedRealtime()
            kotlinx.coroutines.delay(500)
        }
    }
    val bpm = state.bpm?.roundToInt()
    if (inTarget) {
        val held = state.inTargetSinceMs?.let { ((now - it) / 1000).coerceAtLeast(0) } ?: 0
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(R.drawable.ic_check_circle), contentDescription = null, tint = inTargetColor(), modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text(str(R.string.listen_in_target_held, held), fontSize = 15.sp, fontWeight = FontWeight.Medium, color = inTargetColor())
        }
        return
    }
    val label = when {
        settings.rangeEnabled && bpm != null && settings.targetLow?.let { bpm < it } == true -> (settings.minBpm - bpm).let { strPlural(R.plurals.listen_below_target, it, it) }
        settings.rangeEnabled && bpm != null && settings.targetHigh?.let { bpm > it } == true -> (bpm - settings.maxBpm).let { strPlural(R.plurals.listen_above_target, it, it) }
        state.bpmSettled -> str(R.string.listen_locked)
        else -> str(R.string.listen_measuring)
    }
    Text(label, fontSize = 15.sp, color = Tone.text2, maxLines = 1)
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

private class HitRipples {
    val startMs = LongArray(MAX_RIPPLES) { Long.MIN_VALUE / 2 }
    val strength = FloatArray(MAX_RIPPLES)
    private var next = 0
    private var loudness = 0.7f
    var nowMs by mutableLongStateOf(SystemClock.elapsedRealtime())
    /** When the mic last heard a hit (or listening started). */
    var lastSoundMs = SystemClock.elapsedRealtime()
    /** Nothing heard for a while: the animation rests (no frames drawn) until the next hit. */
    var quiet by mutableStateOf(false)
    /** Counts hits, so a resting animation can wait for the next one. */
    val hitCount = MutableStateFlow(0)

    fun onHit(hit: MicHit, beatFound: Boolean) {
        loudness = 0.8f * loudness + 0.2f * hit.strength
        lastSoundMs = SystemClock.elapsedRealtime()
        hitCount.value++
        if (!beatFound) send(hit.atMs, hit.strength)
    }

    /** True while any ripple is still on its way out. */
    fun anyAlive(now: Long): Boolean = startMs.any { now - it < RIPPLE_MS * 1.6f }

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
        ripples.lastSoundMs = SystemClock.elapsedRealtime()
        ripples.quiet = false
        while (true) {
            // A silent room (no beat, no ripple left, nothing heard for a while): stop drawing frames, which on a
            // low-end phone otherwise costs a steady share of a core, until the mic hears something again.
            val idleNow = SystemClock.elapsedRealtime()
            if (beat.anchorMs == null && !ripples.anyAlive(idleNow) && idleNow - ripples.lastSoundMs > QUIET_AFTER_MS) {
                ripples.quiet = true
                val seen = ripples.hitCount.value
                // Also wakes now and then in case a beat was found without a fresh hit.
                withTimeoutOrNull(1_000) { ripples.hitCount.first { it != seen } }
                if (beat.anchorMs == null && ripples.hitCount.value == seen) continue
                ripples.quiet = false
            }
            withFrameMillis {
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
    }
    return ripples
}

/**
 * Beat shockwaves going out from the dial. [sure] (0..1, the reading's confidence) sets how far they travel (from just
 * past the ring to ~190 dp from the centre), how long they last, how bright and how thick they are.
 */
@Composable
private fun SmoothHitRipples(ripples: HitRipples, accent: Color, sure: Float, ringRadius: Dp, scale: Float) {
    Canvas(Modifier.fillMaxSize()) {
        val now = ripples.nowMs
        val inner = ringRadius.toPx()
        val outer = inner + ((10 + 64 * sure) * scale).dp.toPx()
        val glow = (14 + 42 * sure).dp.toPx()
        val lifeMs = RIPPLE_MS * (0.7f + 0.9f * sure)
        val bright = 0.3f + 1.3f * sure
        val stroke = (2.5f + 6.5f * sure).dp.toPx()
        for (i in 0 until MAX_RIPPLES) {
            val t = (now - ripples.startMs[i]) / lifeMs
            if (t !in 0f..1f) continue
            val s = ripples.strength[i]
            val fade = 1f - t
            val travel = 1f - fade * fade
            val r = inner + (outer - inner) * travel
            drawCircle(
                Brush.radialGradient(
                    0f to Color.Transparent,
                    ((r - glow) / r).coerceAtLeast(0f) to Color.Transparent,
                    1f to accent.copy(alpha = (0.25f * s * fade * bright).coerceAtMost(1f)),
                    center = center,
                    radius = r,
                ),
                radius = r,
            )
            drawCircle(
                color = accent.copy(alpha = (0.55f * s * fade * bright).coerceAtMost(1f)),
                radius = r,
                style = Stroke(width = stroke * (0.75f + 0.25f * fade)),
            )
        }
    }
}

private const val MAX_RIPPLES = 8
private const val RIPPLE_MS = 1_100f
private const val KICK_MS = 220f
/** Below this the now-playing card shows Name song as an icon only. */
private val NARROW_CARD = 270.dp
/** How long the room must stay silent before the dial's animation rests. */
private const val QUIET_AFTER_MS = 4_000L

@Composable
private fun SonarRings(accent: Color, ringRadius: Dp) {
    val travel by rememberInfiniteTransition(label = "sonar").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(SONAR_RIPPLE_MS, easing = LinearEasing)),
        label = "sonarTravel",
    )
    Canvas(Modifier.fillMaxSize()) {
        val inner = (ringRadius + 2.dp).toPx()
        val outer = size.minDimension / 2
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

/** "● Target 171–185 BPM ⌄" under the dial (42 dp); tapping it opens the target sheet. */
@Composable
private fun TargetChip(settings: Settings, open: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        Modifier
            .height(42.dp)
            .clip(shape)
            .background(if (open) Tone.selected else Tone.chip)
            .border(1.dp, if (open) MaterialTheme.colorScheme.primary else Tone.chipLine, shape)
            .clickable(onClickLabel = str(R.string.listen_edit_target_range), onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(if (settings.rangeEnabled) inTargetColor() else Tone.text3),
        )
        Spacer(Modifier.width(6.dp))
        Text(str(R.string.listen_target), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(6.dp))
        Text(
            if (settings.rangeEnabled) tempoRangeLabel(settings.targetLow, settings.targetHigh) else str(R.string.listen_target_off),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.width(4.dp))
        Icon(
            painterResource(if (open) R.drawable.ic_expand_less else R.drawable.ic_expand_more),
            contentDescription = null,
            tint = if (open) Tone.text2 else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
    }
}

/** The target range: Between / At least / Up to with steppers, presets (or "Custom"), and whether to vibrate. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TargetSheet(settings: Settings, onSettingsChange: ((Settings) -> Settings) -> Unit, onDismiss: () -> Unit) {
    // An open end keeps the number it had, so switching back to Between brings it back.
    fun setRange(lo: Int?, hi: Int?) = onSettingsChange {
        it.copy(minBpm = lo ?: it.minBpm, maxBpm = hi ?: it.maxBpm, minOpen = lo == null, maxOpen = hi == null)
    }
    CalmBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberCalmSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = Tone.sheet,
        // Tone.sheet isn't a theme colour, so the sheet can't work out its text colour: without this, untinted text
        // takes whatever the caller had (black outside a Surface) and disappears in dark mode.
        contentColor = MaterialTheme.colorScheme.onSurface,
        dragHandle = {
            Box(Modifier.padding(top = 10.dp, bottom = 16.dp).size(36.dp, 4.dp).clip(RoundedCornerShape(2.dp)).background(Color(0xFF3A3946)))
        },
    ) {
        // A short screen or an open keyboard leaves less room than the sheet needs: it scrolls rather than squeezing
        // fixed-height parts (Done, the steppers, the name field) out of shape.
        Column(Modifier.imePadding().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 28.dp)) {
            // Title over the hint: side by side they broke "Target range" over two lines on a 320 dp phone.
            Text(str(R.string.listen_target_range_title), fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(str(R.string.listen_vibrates_after), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))
            TempoRangeEditor(
                low = settings.targetLow,
                high = settings.targetHigh,
                fallbackLow = settings.minBpm,
                fallbackHigh = settings.maxBpm,
                allowAny = false,
                onChange = ::setRange,
            )
            Spacer(Modifier.height(16.dp))
            val preset = TEMPO_PRESETS.firstOrNull { it.min == settings.targetLow && it.max == settings.targetHigh }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TEMPO_PRESETS.forEach { p ->
                    PresetTile(str(p.label), "${p.min}–${p.max}", selected = p == preset, Modifier.weight(1f)) { setRange(p.min, p.max) }
                }
                // Shows what the editor above holds when no preset matches; there is nothing more to open.
                PresetTile(
                    str(R.string.listen_preset_custom),
                    tempoRangeShort(settings.targetLow, settings.targetHigh),
                    selected = preset == null,
                    Modifier.weight(1f),
                    onClick = null,
                )
            }
            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(str(R.string.listen_range_alerts), fontSize = 15.sp, modifier = Modifier.weight(1f))
                Switch(
                    checked = settings.rangeEnabled,
                    onCheckedChange = { on -> onSettingsChange { it.copy(rangeEnabled = on) } },
                    colors = brandSwitchColors(),
                )
            }
        }
    }
}

/** A preset: 52 dp tall, name over range; the selected one filled violet-dark with a violet outline. */
@Composable
private fun PresetTile(name: String, range: String, selected: Boolean, modifier: Modifier, onClick: (() -> Unit)?) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier
            .height(52.dp)
            .clip(shape)
            .background(if (selected) Tone.selected else Tone.track)
            .border(1.dp, if (selected) MaterialTheme.colorScheme.primary else Color.Transparent, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(name, fontSize = 13.sp, lineHeight = 15.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
        Text(range, fontSize = 11.sp, lineHeight = 13.sp, color = if (selected) Tone.text2 else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** The last few songs heard: 40 dp art, title and "artist · 4 min ago", and the BPM (mint with a dot in target). */
@Composable
private fun HeardRecently(recent: List<BeatLogEntry>, settings: Settings, onOpenSong: (String) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Text(str(R.string.listen_heard_recently), fontSize = 13.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 6.dp))
        recent.forEach { e ->
            val inTarget = settings.inTarget(e.bpm)
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .then(if (e.songSlug != null) Modifier.clickable { onOpenSong(e.songSlug) } else Modifier),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).background(Tone.pill))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(e.title ?: str(R.string.listen_unnamed_song), fontSize = 15.sp, lineHeight = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOfNotNull(e.artist ?: if (e.clipPath != null) str(R.string.listen_clip_saved) else null, ago(e.timestamp)).joinToString(" · "),
                        fontSize = 13.sp,
                        lineHeight = 15.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                BpmValue(e.bpm, inTarget, fontSize = 16)
            }
        }
    }
}

/** A tempo as text: mint and bold with a 6 dp dot when in target, otherwise the softer text colour. */
@Composable
internal fun BpmValue(bpm: Float, inTarget: Boolean, fontSize: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (inTarget) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(inTargetColor()))
            Spacer(Modifier.width(6.dp))
        }
        Text(
            "${bpm.roundToInt()}",
            fontSize = fontSize.sp,
            fontWeight = if (inTarget) FontWeight.Bold else FontWeight.Medium,
            color = if (inTarget) inTargetColor() else Tone.text2,
        )
    }
}

/** "4 min ago". */
private fun ago(ms: Long): String =
    android.text.format.DateUtils.getRelativeTimeSpanString(ms, System.currentTimeMillis(), android.text.format.DateUtils.MINUTE_IN_MILLIS).toString()

/**
 * What's playing: a 68 dp card with the song (or "Listening…" / "Song not named yet"), a 40 dp "Name song" pill and a
 * 40 dp pause button; a crowd-suggested name underneath when there is one.
 */
@Composable
private fun NowPlayingCard(
    state: MonitorState,
    suggestion: NameSuggestion?,
    onAcceptSuggestion: () -> Unit,
    onRejectSuggestion: () -> Unit,
    actions: AppActions,
) {
    val unnamed = state.title == null && state.placeholderNo != null && state.active
    val titleText = when {
        state.title != null -> state.title
        state.status == MonitorStatus.LISTENING -> str(R.string.listen_listening)
        state.status == MonitorStatus.PAUSED -> str(R.string.listen_paused)
        else -> str(R.string.listen_ready_to_detect)
    }
    val subText = when {
        state.title != null -> listOfNotNull(state.artist, state.phoneApp).joinToString(" · ").ifEmpty { str(R.string.listen_heard_around_you) }
        state.active -> str(R.string.listen_song_not_named)
        else -> str(R.string.listen_hold_phone_hint)
    }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            // A narrow card (a 320 dp phone, or a large font) keeps only the search icon, so the status text isn't cut.
            val iconOnly = maxWidth < NARROW_CARD || (maxWidth < NARROW_CARD + 40.dp && LocalDensity.current.fontScale > 1.15f)
            Row(Modifier.fillMaxWidth().heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(titleText, fontSize = 16.sp, lineHeight = 19.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(subText, fontSize = 13.sp, lineHeight = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (state.active) {
                    if (state.title == null) {
                        Spacer(Modifier.width(8.dp))
                        Row(
                            Modifier
                                .height(40.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(Tone.pill)
                                .clickable(onClickLabel = str(R.string.listen_name_with_shazam), onClick = actions.identify)
                                .padding(horizontal = if (iconOnly) 11.dp else 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                painterResource(R.drawable.ic_search),
                                contentDescription = if (iconOnly) str(R.string.listen_name_song) else null,
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(18.dp),
                            )
                            if (!iconOnly) {
                                Spacer(Modifier.width(6.dp))
                                Text(str(R.string.listen_name_song), fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                            }
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    val paused = state.status == MonitorStatus.PAUSED
                    Box(
                        Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Tone.pill)
                            .clickable(onClick = if (paused) actions.resume else actions.pause),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painterResource(if (paused) R.drawable.ic_play else R.drawable.ic_pause),
                            contentDescription = if (paused) str(R.string.listen_resume) else str(R.string.listen_pause),
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }
        if (unnamed && suggestion != null) {
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    str(R.string.listen_suggestion_question, "${suggestion.title}${if (suggestion.artist.isNotBlank()) " · ${suggestion.artist}" else ""}"),
                    fontSize = 13.sp,
                    color = Tone.text2,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onAcceptSuggestion) { Text(str(R.string.listen_suggestion_yes)) }
                TextButton(onClick = onRejectSuggestion) { Text(str(R.string.listen_suggestion_no), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
}

internal data class TempoPreset(@StringRes val label: Int, val min: Int, val max: Int)

internal val TEMPO_PRESETS = listOf(
    TempoPreset(R.string.listen_preset_groove, 95, 110),
    TempoPreset(R.string.listen_preset_house, 120, 132),
    TempoPreset(R.string.listen_preset_run, 155, 175),
)

