package com.boimin.beatradar.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.boimin.beatradar.R
import com.boimin.beatradar.net.SongDetail
import com.boimin.beatradar.ui.theme.Brand

@Composable
internal fun SongDetailScreen(state: DetailState, vm: LibraryViewModel, actions: LibraryActions, modifier: Modifier) {
    var reporting by remember { mutableStateOf(false) }
    val song = state.song
    Column(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = vm::close) { Icon(painterResource(R.drawable.ic_back), contentDescription = "Back") }
            Spacer(Modifier.weight(1f))
            if (song != null) {
                IconButton(onClick = { actions.share(song.summary()) }) { Icon(painterResource(R.drawable.ic_share), contentDescription = "Share") }
                IconButton(onClick = { reporting = true }) { Icon(painterResource(R.drawable.ic_flag), contentDescription = "Report") }
            }
        }
        when {
            song != null -> SongPage(song, vm, actions)
            state.loading -> Centered { CircularProgressIndicator() }
            else -> Message("Couldn't load this song", state.error ?: "", retry = vm::refreshDetail)
        }
    }
    if (reporting && song != null) {
        ReportDialog(
            onDismiss = { reporting = false },
            onSend = { reason, note -> reporting = false; vm.report(song.slug, reason, note) },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SongPage(song: SongDetail, vm: LibraryViewModel, actions: LibraryActions) {
    var explain by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (song.artworkUrl != null) {
            AsyncImage(
                model = song.artworkUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(180.dp).clip(RoundedCornerShape(20.dp)),
            )
            Spacer(Modifier.height(16.dp))
        }
        Text(song.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        Text(song.artist, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(Modifier.height(20.dp))
        BigBpm(song.bpm, song.bpmAlt)
        Spacer(Modifier.height(16.dp))
        SongCounters(
            song.listeners, song.confirmations, loves = null, labelled = true,
            modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable { explain = !explain }.padding(8.dp),
        )
        if (explain) {
            Text(
                confirmationExplainer(song.listeners, song.confirmations),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(16.dp))
        LoveButton(song.lovedByMe, song.loveCount) {
            val target = !song.lovedByMe
            if (actions.signedIn) vm.setLoved(song.slug, target) else actions.requireSignIn { vm.setLoved(song.slug, target) }
        }
        if (song.links.isNotEmpty()) {
            Spacer(Modifier.height(24.dp))
            Text("Listen on", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (link in song.links.sortedBy { SERVICES.keys.indexOf(it.service) }) {
                    OutlinedButton(onClick = { actions.openUrl(link.url) }) {
                        Text(SERVICES[link.service] ?: link.service, maxLines = 1)
                        if (!link.exact) {
                            Spacer(Modifier.width(4.dp))
                            Icon(painterResource(R.drawable.ic_search), contentDescription = "search", modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }
        }
        song.broughtInBy?.let { nick ->
            Spacer(Modifier.height(20.dp))
            Text(
                "Brought in by $nick",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { vm.openProfile(nick) }.padding(6.dp),
            )
        }
        Spacer(Modifier.height(32.dp))
    }
}

private val SERVICES = linkedMapOf("spotify" to "Spotify", "youtube" to "YouTube", "apple_music" to "Apple Music")

@Composable
private fun LoveButton(loved: Boolean, count: Int, onClick: () -> Unit) {
    FilledTonalButton(onClick = onClick) {
        Icon(
            painterResource(if (loved) R.drawable.ic_favorite else R.drawable.ic_favorite_border),
            contentDescription = if (loved) "Unlove" else "Love",
            tint = if (loved) Brand.Coral else MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(compactCount(count))
    }
}

@Composable
private fun ReportDialog(onDismiss: () -> Unit, onSend: (String, String?) -> Unit) {
    val reasons = listOf("wrong_bpm" to "Wrong BPM", "wrong_title" to "Wrong title or artist", "other" to "Something else")
    var reason by remember { mutableStateOf(reasons.first().first) }
    var note by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Report this song") },
        text = {
            Column {
                reasons.forEach { (code, label) ->
                    Row(
                        Modifier.fillMaxWidth().selectable(selected = reason == code, onClick = { reason = code }).padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = reason == code, onClick = null)
                        Spacer(Modifier.width(8.dp))
                        Text(label)
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = note, onValueChange = { note = it.take(500) }, label = { Text("Details (optional)") })
            }
        },
        confirmButton = { TextButton(onClick = { onSend(reason, note.trim().ifEmpty { null }) }) { Text("Send") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Small "badge" pill used on profiles. */
@Composable
internal fun Pill(text: String) {
    Box(
        Modifier.clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.secondaryContainer).padding(horizontal = 10.dp, vertical = 4.dp),
    ) { Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSecondaryContainer) }
}
