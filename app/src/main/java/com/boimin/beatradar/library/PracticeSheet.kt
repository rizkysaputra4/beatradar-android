package com.boimin.beatradar.library

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.boimin.beatradar.R
import com.boimin.beatradar.i18n.str
import com.boimin.beatradar.notify.ClickPlayer
import com.boimin.beatradar.ui.theme.Brand
import com.boimin.beatradar.ui.theme.Tone

/**
 * "Practise at 126": a click at the song's tempo, in the app's sheet style (as the Listen tab's Target range sheet).
 * The tempo steps by 1 BPM, or jumps to half, the song's own or double time; four lights show the bar as it plays, the
 * first (the accent) in coral. Listening stops while it plays (the microphone would measure the click), and the click
 * stops when the sheet closes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PracticeSheet(songTitle: String, startBpm: Int, listening: Boolean, onStopListening: () -> Unit, onDismiss: () -> Unit) {
    val songBpm = startBpm.coerceIn(ClickPlayer.MIN_BPM, ClickPlayer.MAX_BPM)
    var bpm by remember { mutableIntStateOf(songBpm) }
    val playing by ClickPlayer.playingBpm.collectAsStateWithLifecycle()
    DisposableEffect(Unit) { onDispose { ClickPlayer.stop() } }
    fun set(value: Int) {
        bpm = value.coerceIn(ClickPlayer.MIN_BPM, ClickPlayer.MAX_BPM)
        if (playing != null) ClickPlayer.play(bpm)
    }

    // The beat sounding now, read every frame while the click plays.
    var beat by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(playing) {
        if (playing == null) { beat = null; return@LaunchedEffect }
        while (true) withFrameMillis { beat = ClickPlayer.beatInBar() }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetGesturesEnabled = false,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = Tone.sheet,
        // Tone.sheet isn't a theme colour, so the sheet can't work out its text colour: without this, untinted text
        // takes whatever the caller had (black outside a Surface) and disappears in dark mode.
        contentColor = MaterialTheme.colorScheme.onSurface,
        dragHandle = {
            Box(Modifier.padding(top = 10.dp, bottom = 16.dp).size(36.dp, 4.dp).clip(RoundedCornerShape(2.dp)).background(Color(0xFF3A3946)))
        },
    ) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 28.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(str(R.string.practice_title), fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(str(R.string.practice_accent), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                songTitle,
                fontSize = 13.sp,
                color = Tone.text3,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(Modifier.height(24.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                StepButton(R.drawable.ic_remove, str(R.string.practice_slower), enabled = bpm > ClickPlayer.MIN_BPM) { set(bpm - 1) }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    val bpmDescription = str(R.string.library_bpm_value, bpm)
                    Text(
                        "$bpm",
                        fontSize = 56.sp,
                        lineHeight = 60.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (playing != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.semantics { contentDescription = bpmDescription },
                    )
                    Text(str(R.string.library_bpm), fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                StepButton(R.drawable.ic_add, str(R.string.practice_faster), enabled = bpm < ClickPlayer.MAX_BPM) { set(bpm + 1) }
            }

            Spacer(Modifier.height(20.dp))
            BeatLights(beat)

            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TempoTile(str(R.string.practice_half_time), songBpm / 2, current = bpm, Modifier.weight(1f), onPick = ::set)
                TempoTile(str(R.string.practice_song), songBpm, current = bpm, Modifier.weight(1f), onPick = ::set)
                TempoTile(str(R.string.practice_double_time), songBpm * 2, current = bpm, Modifier.weight(1f), onPick = ::set)
            }

            if (listening && playing == null) {
                Spacer(Modifier.height(16.dp))
                Text(
                    str(R.string.practice_listening_note),
                    fontSize = 12.sp,
                    color = Tone.text3,
                )
            }

            Spacer(Modifier.height(20.dp))
            PlayButton(playing != null) {
                if (playing != null) {
                    ClickPlayer.stop()
                } else {
                    if (listening) onStopListening()
                    ClickPlayer.play(bpm)
                }
            }
        }
    }
}

/** Four lights, one a beat: the sounding one lit and swollen, the accent coral, the rest violet; all dim when quiet. */
@Composable
private fun BeatLights(beat: Int?) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        repeat(4) { i ->
            val on = beat == i
            val color by animateColorAsState(
                when {
                    !on -> Tone.ring
                    i == 0 -> Brand.Coral
                    else -> MaterialTheme.colorScheme.primary
                },
                animationSpec = tween(if (on) 0 else 180),
                label = "beat light",
            )
            val scale by animateFloatAsState(if (on) 1.35f else 1f, animationSpec = tween(if (on) 0 else 180), label = "beat size")
            Box(
                Modifier
                    .padding(horizontal = 9.dp)
                    .size(12.dp)
                    .scale(scale)
                    .clip(CircleShape)
                    .background(color),
            )
        }
    }
}

/** A round 52 dp − / + button on the track colour. */
@Composable
private fun StepButton(icon: Int, description: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(52.dp)
            .clip(CircleShape)
            .background(Tone.track)
            .clickable(enabled = enabled, onClickLabel = description, onClick = onClick)
            .alpha(if (enabled) 1f else 0.4f),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), contentDescription = description, tint = Tone.text2, modifier = Modifier.size(24.dp))
    }
}

/**
 * "Song / 126": a 52 dp tile that sets the tempo, like the Target range presets; selected (violet-dark, violet outline)
 * while the tempo is its own. A tempo the click can't play is shown dimmed and does nothing.
 */
@Composable
private fun TempoTile(name: String, tileBpm: Int, current: Int, modifier: Modifier, onPick: (Int) -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    val selected = tileBpm == current
    val playable = tileBpm in ClickPlayer.MIN_BPM..ClickPlayer.MAX_BPM
    Column(
        modifier
            .height(52.dp)
            .clip(shape)
            .background(if (selected) Tone.selected else Tone.track)
            .border(1.dp, if (selected) MaterialTheme.colorScheme.primary else Color.Transparent, shape)
            .clickable(enabled = playable) { onPick(tileBpm) }
            .alpha(if (playable) 1f else 0.4f),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(name, fontSize = 13.sp, lineHeight = 15.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
        Text("$tileBpm", fontSize = 11.sp, lineHeight = 13.sp, color = if (selected) Tone.text2 else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Full-width 56 dp Start / Stop: violet to start, coral while the click plays. */
@Composable
private fun PlayButton(playing: Boolean, onClick: () -> Unit) {
    val bg by animateColorAsState(if (playing) Brand.Coral else MaterialTheme.colorScheme.primary, label = "play button")
    Row(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painterResource(if (playing) R.drawable.ic_stop else R.drawable.ic_play),
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(if (playing) str(R.string.practice_stop) else str(R.string.practice_start), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
    }
}
