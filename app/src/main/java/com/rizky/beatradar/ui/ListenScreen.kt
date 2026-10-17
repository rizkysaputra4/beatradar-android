package com.rizky.beatradar.ui

import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
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
import com.rizky.beatradar.R
import com.rizky.beatradar.capture.LiveSignal
import com.rizky.beatradar.capture.MonitorState
import com.rizky.beatradar.capture.MonitorStatus
import com.rizky.beatradar.data.Settings
import com.rizky.beatradar.ui.theme.Brand
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.pow
import kotlin.math.roundToInt

@Composable
fun ListenScreen(
    state: MonitorState,
    settings: Settings,
    actions: AppActions,
    onSettingsChange: ((Settings) -> Settings) -> Unit,
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
        BeatCircle(state, beat.pulse, showProgress = settings.rangeEnabled && state.active)
        Spacer(Modifier.height(12.dp))
        if (state.active) {
            LiveGraph(beat, listening = state.status == MonitorStatus.LISTENING, matched = state.matched)
            Spacer(Modifier.height(10.dp))
            if (settings.rangeEnabled) {
                VerdictLine(state, settings)
                Spacer(Modifier.height(4.dp))
            }
        }

        Text(
            when {
                state.title != null -> listOfNotNull(state.title, state.artist).joinToString(" · ")
                state.status == MonitorStatus.LISTENING && state.bpm == null -> "Listening for a beat…"
                state.status == MonitorStatus.PAUSED -> "Paused"
                state.active -> " "
                else -> "Hold the phone near the music, then tap Start"
            },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(16.dp))


        Controls(state, actions)
        Spacer(Modifier.height(16.dp))
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
 * The live BPM inside a circle that pulses on each beat. While the tempo holds inside the target range,
 * the circle's own rim fills clockwise from the top; on a match the whole disc turns coral.
 */
@Composable
private fun BeatCircle(state: MonitorState, pulse: Float, showProgress: Boolean) {
    val listening = state.status == MonitorStatus.LISTENING
    val accent by animateColorAsState(
        if (state.matched) Brand.Coral else MaterialTheme.colorScheme.primary,
        label = "accent",
    )
    val onAccent = if (state.matched) Brand.OnCoral else MaterialTheme.colorScheme.onPrimary
    val progress by animateFloatAsState(if (showProgress) state.matchProgress else 0f, tween(450), label = "progress")
    val beating = listening && state.bpm != null

    Box(Modifier.size(260.dp), contentAlignment = Alignment.Center) {
        if (beating) {
            Canvas(Modifier.fillMaxSize()) {
                // A soft wave that leaves the disc on every beat.
                val outer = size.minDimension / 2
                val inner = 100.dp.toPx()
                drawCircle(accent.copy(alpha = 0.28f * pulse), radius = inner + (outer - inner) * (1f - pulse))
            }
        }
        // Disc and rim pulse together, so the progress reads as part of the BPM display.
        Box(
            Modifier
                .size(200.dp)
                .scale(1f + 0.05f * pulse),
            contentAlignment = Alignment.Center,
        ) {
            if (showProgress) {
                Canvas(Modifier.fillMaxSize()) {
                    val width = 10.dp.toPx()
                    val radius = size.minDimension / 2 - width / 2
                    drawCircle(accent.copy(alpha = 0.22f), radius = radius, style = Stroke(width))
                    if (progress > 0f) {
                        drawArc(
                            color = accent,
                            startAngle = -90f,
                            sweepAngle = 360f * progress,
                            useCenter = false,
                            topLeft = Offset(center.x - radius, center.y - radius),
                            size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
                            style = Stroke(width, cap = StrokeCap.Round),
                        )
                    }
                }
            }
            Surface(
                shape = CircleShape,
                color = if (state.active) accent else MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = if (state.active) onAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                // Fills up to the rim, so disc and progress read as one piece.
                modifier = Modifier.size(if (showProgress) 182.dp else 172.dp),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    when {
                        state.bpm != null && listening -> {
                            Text(state.bpm.roundToInt().toString(), style = MaterialTheme.typography.displayLarge, fontWeight = FontWeight.Bold)
                            Text("BPM", style = MaterialTheme.typography.titleMedium)
                        }
                        else -> Icon(painterResource(R.drawable.ic_mic), contentDescription = null, modifier = Modifier.size(56.dp))
                    }
                }
            }
        }
    }
}

/**
 * Live beat wave: every detected beat sends a smooth wave packet out from the centre to both sides,
 * sized by how hard it hit; minor hits between beats send smaller ones. Packets fade as they travel.
 * Driven by the beat grid, so no beat is ever skipped. Redrawn every frame only while visible.
 */
@Composable
private fun LiveGraph(beat: BeatClock, listening: Boolean, matched: Boolean) {
    val accent = if (matched) Brand.Coral else MaterialTheme.colorScheme.primary
    val times = remember { LongArray(LiveSignal.SNAPSHOT_SIZE) }
    val levels = remember { FloatArray(LiveSignal.SNAPSHOT_SIZE) }
    val firstIndex = remember { LongArray(1) }
    val wave = remember { Path() }
    val pulseAge = remember { FloatArray(MAX_PULSES) }
    val pulseAmp = remember { FloatArray(MAX_PULSES) }
    var frameTime by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(listening) {
        while (listening) withFrameMillis { frameTime = SystemClock.elapsedRealtime() }
    }
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(120.dp),
    ) {
        val now = frameTime
        val mid = size.height / 2
        val cx = size.width / 2
        val reach = cx - 4.dp.toPx()
        val ampPx = mid - 6.dp.toPx()
        val dim = if (listening) 1f else 0.4f
        val n = LiveSignal.snapshot(times, levels, firstIndex)

        fun hitNear(t: Long, windowMs: Long): Float {
            var best = 0f
            for (i in 0 until n) if (kotlin.math.abs(times[i] - t) <= windowMs && levels[i] > best) best = levels[i]
            return best
        }

        // Main beats from the beat grid: always a wave, taller when the hit was stronger.
        var count = 0
        val anchor = beat.anchorMs
        val period = beat.periodMs
        if (listening && anchor != null && period > 0) {
            var k = floor((now - anchor) / period)
            while (count < MAX_PULSES) {
                val t = (anchor + k * period).toLong()
                val age = (now - t).toFloat()
                if (age > GRAPH_SPAN_MS) break
                if (age >= 0) {
                    pulseAge[count] = age
                    pulseAmp[count] = hitNear(t, 90).coerceAtLeast(0.45f)
                    count++
                }
                k -= 1
            }
        }
        // Minor hits: clear peaks that are not on a beat get a smaller wave.
        val mainCount = count
        for (i in 1 until n - 1) {
            if (count >= MAX_PULSES) break
            val v = levels[i]
            if (v < MIN_SPIKE || v < levels[i - 1] || v <= levels[i + 1]) continue
            val age = (now - times[i]).toFloat()
            if (age < 0 || age > GRAPH_SPAN_MS) continue
            var onBeat = false
            for (m in 0 until mainCount) if (kotlin.math.abs(pulseAge[m] - age) < 120) onBeat = true
            if (onBeat && mainCount > 0) continue
            pulseAge[count] = age
            pulseAmp[count] = if (mainCount > 0) v * 0.35f else v
            count++
        }

        // One smooth wave across the width, the same on both sides of the centre.
        wave.reset()
        val step = 2.dp.toPx()
        var x = cx - reach
        var first = true
        while (x <= cx + reach) {
            val age = kotlin.math.abs(x - cx) / reach * GRAPH_SPAN_MS
            var y = 0f
            for (p in 0 until count) {
                val u = age - pulseAge[p]
                if (u < -3 * PACKET_WIDTH_MS || u > 3 * PACKET_WIDTH_MS) continue
                val envelope = exp(-(u / PACKET_WIDTH_MS) * (u / PACKET_WIDTH_MS))
                y += pulseAmp[p] * envelope * sin(2 * PI.toFloat() * u / WAVELENGTH_MS)
            }
            val py = mid - y.coerceIn(-1f, 1f) * ampPx
            if (first) wave.moveTo(x, py) else wave.lineTo(x, py)
            first = false
            x += step
        }
        drawPath(
            wave,
            Brush.horizontalGradient(
                0f to accent.copy(alpha = 0f),
                0.5f to accent.copy(alpha = dim),
                1f to accent.copy(alpha = 0f),
            ),
            style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round),
        )

        // The source: a dot at the centre that pulses on each beat.
        val r = 5.dp.toPx() + 4.dp.toPx() * beat.pulse
        drawCircle(accent.copy(alpha = 0.18f * dim), radius = r * 2.6f, center = Offset(cx, mid))
        drawCircle(accent.copy(alpha = dim), radius = r, center = Offset(cx, mid))
    }
}

/** Minor hits weaker than this (relative to the loudest recent hit) make no wave. */
private const val MIN_SPIKE = 0.45f

/** How much history each side of the graph shows; short enough that beats stay well apart. */
private const val GRAPH_SPAN_MS = 2_500f
private const val MAX_PULSES = 40
/** Shape of one wave packet, in milliseconds of travel. */
private const val PACKET_WIDTH_MS = 85f
private const val WAVELENGTH_MS = 130f

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

@Composable
private fun Controls(state: MonitorState, actions: AppActions) {
    Button(
        onClick = if (state.active) actions.stop else actions.start,
        colors = if (state.active) ButtonDefaults.filledTonalButtonColors() else ButtonDefaults.buttonColors(),
        shape = RoundedCornerShape(50),
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
    ) {
        Icon(painterResource(if (state.active) R.drawable.ic_stop else R.drawable.ic_play), null, Modifier.size(22.dp))
        Spacer(Modifier.width(10.dp))
        Text(if (state.active) "Stop listening" else "Start listening", style = MaterialTheme.typography.titleMedium)
    }
    if (state.active) {
        Spacer(Modifier.height(10.dp))
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
