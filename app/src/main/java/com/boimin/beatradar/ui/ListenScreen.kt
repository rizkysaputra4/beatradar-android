package com.boimin.beatradar.ui

import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RangeSlider
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.material3.OutlinedTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import com.boimin.beatradar.R
import kotlinx.coroutines.launch
import com.boimin.beatradar.capture.MicHit
import com.boimin.beatradar.capture.MicHits
import com.boimin.beatradar.capture.MonitorState
import com.boimin.beatradar.capture.MonitorStatus
import com.boimin.beatradar.data.NameSuggestion
import com.boimin.beatradar.data.Settings
import com.boimin.beatradar.sync.UnnamedRules
import com.boimin.beatradar.ui.theme.Brand
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
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(4.dp))
        SourceChip(state)
        Spacer(Modifier.height(8.dp))

        val beat = rememberBeatClock(
            bpm = state.bpm.takeIf { state.status == MonitorStatus.LISTENING },
            lastBeatAtMs = state.lastBeatAtMs,
        )
        val listening = state.status == MonitorStatus.LISTENING
        val ripples = rememberHitRipples(listening, beat)
        BeatCircle(
            state,
            ripples,
            // Until a beat is found the disc kicks on every hit the mic hears; after that it keeps the beat,
            // and the ripples leave on the same beats.
            pulse = if (listening && state.bpm != null && state.lastBeatAtMs != null) beat.pulse else ripples.kick,
            showProgress = settings.rangeEnabled && state.active,
            onToggle = if (state.active) actions.stop else actions.start,
        )
        Spacer(Modifier.height(4.dp))
        if (state.active && settings.rangeEnabled) {
            VerdictLine(state, settings)
            Spacer(Modifier.height(4.dp))
        }

        // A song logged without a name: the best moment to identify it is while it still plays.
        val unnamed = state.title == null && state.placeholderNo != null && state.active
        Text(
            when {
                state.title != null -> listOfNotNull(state.title, state.artist).joinToString(" · ")
                unnamed -> "Song #${state.placeholderNo} · Identify"
                state.status == MonitorStatus.LISTENING && state.bpm == null -> "Listening for a beat…"
                state.status == MonitorStatus.PAUSED -> "Paused"
                state.active -> " "
                else -> "Hold the phone near the music, then tap the circle"
            },
            style = MaterialTheme.typography.bodyLarge,
            color = if (unnamed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = if (unnamed) Modifier.clickable(onClick = actions.identify) else Modifier,
        )
        if (unnamed && suggestion != null) {
            // Same line of the layout, just below: answer while the song still plays.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    UnnamedRules.question(suggestion),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                TextButton(onClick = onAcceptSuggestion) { Text("Yes") }
                TextButton(onClick = onRejectSuggestion) { Text("No") }
            }
        }
        Spacer(Modifier.height(16.dp))


        if (state.active) {
            Controls(state, actions)
            Spacer(Modifier.height(16.dp))
        }
        RangeCard(settings, onSettingsChange)
        Spacer(Modifier.height(24.dp))
    }
}

/** Where the sound comes from: always the mic; mention the app if this phone is the one playing. */
@Composable
private fun SourceChip(state: MonitorState) {
    val detail = when {
        !state.active -> null
        state.phoneApp != null -> "hearing ${state.phoneApp} on this phone"
        else -> "hearing music around you"
    }
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(R.drawable.ic_mic), contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                if (detail == null) "Microphone" else "Microphone · $detail",
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
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
    // Plain holder (not Compose state): updated while composing, read by the effect below.
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
 * The live BPM inside a circle that pulses on each beat; tapping it turns the microphone on and off.
 * While the tempo holds inside the target range, the circle's own rim fills clockwise from the top;
 * on a match the whole disc turns coral.
 */
@Composable
private fun BeatCircle(state: MonitorState, ripples: HitRipples, pulse: Float, showProgress: Boolean, onToggle: () -> Unit) {
    val listening = state.status == MonitorStatus.LISTENING
    val accent by animateColorAsState(
        if (state.matched) Brand.Coral else MaterialTheme.colorScheme.primary,
        label = "accent",
    )
    val onAccent = if (state.matched) Brand.OnCoral else MaterialTheme.colorScheme.onPrimary
    val progress by animateFloatAsState(if (showProgress) state.matchProgress else 0f, tween(450), label = "progress")
    // A quick first guess shows dimmed until the reading settles.
    val numberAlpha by animateFloatAsState(if (state.bpmSettled) 1f else 0.55f, tween(400), label = "settled")

    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(1f),
        contentAlignment = Alignment.Center,
    ) {
        if (listening) MicRipple(accent)
        if (listening) HitRippleCanvas(ripples, accent)
        // Disc and rim pulse together, so the progress reads as part of the BPM display.
        Box(
            Modifier
                .size(212.dp)
                .scale(1f + 0.05f * pulse),
            contentAlignment = Alignment.Center,
        ) {
            if (showProgress) ProgressZigzag(progress, pulse, accent)
            Surface(
                onClick = onToggle,
                shape = CircleShape,
                color = if (state.active) accent else MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = if (state.active) onAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(172.dp),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    when {
                        state.bpm != null && listening -> {
                            Text(
                                state.bpm.roundToInt().toString(),
                                style = MaterialTheme.typography.displayLarge,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.alpha(numberAlpha),
                            )
                            Text("BPM", style = MaterialTheme.typography.titleMedium)
                        }
                        else -> Icon(
                            painterResource(R.drawable.ic_mic),
                            contentDescription = if (state.active) "Stop listening" else "Start listening",
                            modifier = Modifier.size(56.dp),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Ripples on their way out. Until a beat is found, every hit the mic hears sends one; once the beat clock
 * runs, one leaves on each beat, together with the disc's pulse, sized by how hard the song has been hitting.
 * [kick] is a quick bump on the latest hit, for the disc while there is no beat yet.
 */
private class HitRipples {
    val startMs = LongArray(MAX_RIPPLES) { Long.MIN_VALUE / 2 }
    val strength = FloatArray(MAX_RIPPLES)
    private var next = 0
    /** How hard recent hits were, for sizing the ripples that follow the beat. */
    private var loudness = 0.7f
    /** The frame clock, so drawing reads it and redraws every frame while listening. */
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
                // A ripple on each beat of the same grid the disc pulses on. When the grid re-locks,
                // skip a beat that would land right after the last ripple.
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

/**
 * A shockwave for every ripple in [ripples], sized by its strength: a glow that brightens toward a jagged
 * front, rolling out past the screen edges and smoothing as it spreads. Overlapping hits overlap their waves.
 */
@Composable
private fun HitRippleCanvas(ripples: HitRipples, accent: Color) {
    val front = remember { Path() }
    Canvas(Modifier.fillMaxSize()) {
        val now = ripples.nowMs
        val inner = 106.dp.toPx()
        val outer = size.minDimension / 2 + 24.dp.toPx()
        val glow = 44.dp.toPx()
        for (i in 0 until MAX_RIPPLES) {
            val t = (now - ripples.startMs[i]) / RIPPLE_MS
            if (t !in 0f..1f) continue
            val s = ripples.strength[i]
            val fade = 1f - t
            // Leaves the disc fast and eases off as it spreads.
            val travel = 1f - fade * fade
            val r = inner + (outer - inner) * travel
            drawCircle(
                Brush.radialGradient(
                    0f to Color.Transparent,
                    ((r - glow) / r).coerceAtLeast(0f) to Color.Transparent,
                    1f to accent.copy(alpha = 0.4f * s * fade),
                    center = center,
                    radius = r,
                ),
                radius = r,
            )
            zigzag(front, center, r, 0f, 360f, toothDeg = 360f / 24, amp = 9.dp.toPx() * s * fade, phase = travel * 2)
            drawPath(front, accent.copy(alpha = 0.85f * s * fade), style = Stroke(3.dp.toPx(), join = StrokeJoin.Round))
        }
    }
}

private const val MAX_RIPPLES = 8
private const val RIPPLE_MS = 1_100f
private const val KICK_MS = 220f

/**
 * The 10 s progress as a zigzag drawn clockwise from the top, with a flat track for the rest. The teeth
 * kick taller on every beat and crawl slowly around, so the ring keeps time with the music.
 */
@Composable
private fun ProgressZigzag(progress: Float, pulse: Float, accent: Color) {
    val crawl by rememberInfiniteTransition(label = "zigzag").animateFloat(
        initialValue = 0f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(tween(1_200, easing = LinearEasing)),
        label = "zigzagCrawl",
    )
    val path = remember { Path() }
    Canvas(Modifier.fillMaxSize()) {
        val width = 4.dp.toPx()
        val radius = 97.dp.toPx()
        val sweep = 360f * progress
        val gap = if (progress > 0f) 8f else 0f
        val track = 360f - sweep - 2 * gap
        if (track > 0f) {
            drawArc(
                color = accent.copy(alpha = 0.25f),
                startAngle = -90f + sweep + gap,
                sweepAngle = track,
                useCenter = false,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
                style = Stroke(width, cap = StrokeCap.Round),
            )
        }
        if (sweep > 0f) {
            val amp = 2.5.dp.toPx() + 3.5.dp.toPx() * pulse
            zigzag(path, center, radius, -90f, sweep, toothDeg = 9f, amp = amp, phase = crawl, taperDeg = 10f)
            drawPath(path, accent, style = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

/**
 * Fills [path] with a zigzag following a circle arc: sharp teeth of [amp] either side of [radius], one
 * every [toothDeg]. [phase] slides the teeth clockwise (2 = one whole tooth); [taperDeg] flattens the ends.
 */
private fun zigzag(
    path: Path,
    center: Offset,
    radius: Float,
    startDeg: Float,
    sweepDeg: Float,
    toothDeg: Float,
    amp: Float,
    phase: Float,
    taperDeg: Float = 0f,
) {
    path.reset()
    val half = toothDeg / 2
    // Position along the zigzag in half-teeth: whole numbers are the tips, alternating out and in.
    fun valueAt(u: Float): Float {
        val k = floor(u)
        val out = (k.toInt() % 2 + 2) % 2 == 0
        return (if (out) 1f else -1f) * (1 - 2 * (u - k))
    }
    fun point(deg: Float, value: Float, first: Boolean) {
        val along = deg - startDeg
        val taper = if (taperDeg > 0f) minOf(1f, along / taperDeg, (sweepDeg - along) / taperDeg).coerceAtLeast(0f) else 1f
        val r = radius + amp * taper * value
        val a = Math.toRadians(deg.toDouble())
        val x = center.x + r * kotlin.math.cos(a).toFloat()
        val y = center.y + r * kotlin.math.sin(a).toFloat()
        if (first) path.moveTo(x, y) else path.lineTo(x, y)
    }
    val endDeg = startDeg + sweepDeg
    val u0 = startDeg / half - phase
    val u1 = endDeg / half - phase
    point(startDeg, valueAt(u0), first = true)
    var k = floor(u0) + 1
    while (k < u1) {
        point((k + phase) * half, if ((k.toInt() % 2 + 2) % 2 == 0) 1f else -1f, first = false)
        k += 1
    }
    point(endDeg, valueAt(u1), first = false)
}

/**
 * A calm, even ripple that rolls out of the disc for as long as the mic is on, whether or not a beat
 * is heard yet: three thin rings, evenly spaced, fading as they spread.
 */
@Composable
private fun MicRipple(accent: Color) {
    val travel by rememberInfiniteTransition(label = "mic").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(MIC_RIPPLE_MS, easing = LinearEasing)),
        label = "micTravel",
    )
    Canvas(Modifier.fillMaxSize()) {
        val inner = 110.dp.toPx()
        val outer = size.minDimension / 2 + 24.dp.toPx()
        for (i in 0 until MIC_RINGS) {
            val t = (travel + i.toFloat() / MIC_RINGS) % 1f
            drawCircle(
                accent.copy(alpha = 0.6f * (1f - t)),
                radius = inner + (outer - inner) * t,
                style = Stroke(2.dp.toPx()),
            )
        }
    }
}

private const val MIC_RIPPLE_MS = 3_600
private const val MIC_RINGS = 3

/** One line explaining the ring: counting, out of range, or the verdict. */
@Composable
private fun VerdictLine(state: MonitorState, settings: Settings) {
    val bpm = state.bpm
    val target = "${settings.minBpm}–${settings.maxBpm} BPM"
    val seconds = (state.matchProgress * 10).toInt()
    val text = when {
        state.matched && state.alreadyListed -> "Already in your list · $target"
        state.matched -> "Match! Stayed in $target for 10 s"
        state.status != MonitorStatus.LISTENING || bpm == null -> "Target $target"
        settings.matches(bpm) -> "In range · hold it, $seconds of 10 s"
        else -> "Out of range · target $target"
    }
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = if (state.matched) FontWeight.SemiBold else FontWeight.Medium,
        color = if (state.matched || (bpm != null && settings.matches(bpm))) Brand.Coral else MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

/** Pause and Identify while listening; starting and stopping is the circle itself. */
@Composable
private fun Controls(state: MonitorState, actions: AppActions) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        val paused = state.status == MonitorStatus.PAUSED
        OutlinedButton(onClick = if (paused) actions.resume else actions.pause, modifier = Modifier.weight(1f)) {
            Icon(painterResource(if (paused) R.drawable.ic_play else R.drawable.ic_pause), null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(if (paused) "Resume" else "Pause")
        }
        OutlinedButton(onClick = actions.identify, modifier = Modifier.weight(1f)) {
            Icon(painterResource(R.drawable.ic_search), null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Identify")
        }
    }
}

@Composable
private fun RangeCard(settings: Settings, onSettingsChange: ((Settings) -> Settings) -> Unit) {
    // Local state while dragging; persisted once the finger lifts.
    var range by remember(settings.minBpm, settings.maxBpm) {
        mutableStateOf(settings.minBpm.toFloat()..settings.maxBpm.toFloat())
    }
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Find a BPM range", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (settings.rangeEnabled) "Vibrates after 10 s inside the range" else "Off — every song's BPM is just logged",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = settings.rangeEnabled,
                    onCheckedChange = { on -> onSettingsChange { it.copy(rangeEnabled = on) } },
                )
            }
            AnimatedVisibility(settings.rangeEnabled) {
                Column {
                    Spacer(Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        BpmField(
                            value = range.start.roundToInt(),
                            label = "Min BPM",
                            onCommit = { v ->
                                val lo = v.coerceIn(Settings.BPM_FLOOR, settings.maxBpm)
                                onSettingsChange { it.copy(minBpm = lo) }
                            },
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            "–",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 14.dp),
                        )
                        BpmField(
                            value = range.endInclusive.roundToInt(),
                            label = "Max BPM",
                            onCommit = { v ->
                                val hi = v.coerceIn(settings.minBpm, Settings.BPM_CEILING)
                                onSettingsChange { it.copy(maxBpm = hi) }
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Spacer(Modifier.height(4.dp))
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
                    )
                    Row {
                        Text("${Settings.BPM_FLOOR}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.weight(1f))
                        Text("${Settings.BPM_CEILING}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

/** A small number box. Applies the typed value on Done or when focus leaves; shows the slider's value otherwise. */
@Composable
private fun BpmField(value: Int, label: String, onCommit: (Int) -> Unit, modifier: Modifier = Modifier) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    var focused by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    fun commit() {
        val typed = text.toIntOrNull()
        if (typed != null && typed != value) onCommit(typed) else text = value.toString()
    }
    OutlinedTextField(
        value = text,
        onValueChange = { new -> if (new.length <= 3 && new.all(Char::isDigit)) text = new },
        label = { Text(label) },
        singleLine = true,
        textStyle = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
            .onFocusChanged { f ->
                if (focused && !f.isFocused) commit()
                focused = f.isFocused
            },
    )
}
