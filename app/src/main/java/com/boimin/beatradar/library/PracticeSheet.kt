package com.boimin.beatradar.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.boimin.beatradar.notify.ClickPlayer

/**
 * "Practise at 126": a click at the song's tempo, nudgeable by a few BPM. Listening stops while it plays (the
 * microphone would measure the click), and the click stops when the sheet closes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PracticeSheet(startBpm: Int, listening: Boolean, onStopListening: () -> Unit, onDismiss: () -> Unit) {
    var bpm by remember { mutableIntStateOf(startBpm.coerceIn(ClickPlayer.MIN_BPM, ClickPlayer.MAX_BPM)) }
    val playing by ClickPlayer.playingBpm.collectAsStateWithLifecycle()
    DisposableEffect(Unit) { onDispose { ClickPlayer.stop() } }
    fun set(value: Int) {
        bpm = value.coerceIn(ClickPlayer.MIN_BPM, ClickPlayer.MAX_BPM)
        if (playing != null) ClickPlayer.play(bpm)
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.padding(start = 24.dp, end = 24.dp, bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Practice click", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { set(bpm - 1) }) { Text("−") }
                Text("$bpm", fontSize = 44.sp, fontWeight = FontWeight.ExtraBold)
                OutlinedButton(onClick = { set(bpm + 1) }) { Text("+") }
            }
            Text("BPM · accent every 4 beats", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { set(bpm / 2) }) { Text("½×") }
                Button(onClick = {
                    if (playing != null) {
                        ClickPlayer.stop()
                    } else {
                        if (listening) onStopListening()
                        ClickPlayer.play(bpm)
                    }
                }) { Text(if (playing != null) "Stop" else "Play") }
                OutlinedButton(onClick = { set(bpm * 2) }) { Text("2×") }
            }
            if (listening) {
                Text(
                    "Listening stops while the click plays: the microphone would measure the click.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
