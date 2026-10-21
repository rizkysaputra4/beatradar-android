package com.boimin.beatradar.library

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.boimin.beatradar.R
import com.boimin.beatradar.data.Settings
import com.boimin.beatradar.i18n.str
import com.boimin.beatradar.i18n.strPlural
import com.boimin.beatradar.net.PlaylistTrack
import com.boimin.beatradar.ui.BpmBadge
import kotlinx.coroutines.launch
import com.boimin.beatradar.ui.SkeletonSongList

/**
 * "BPM of my playlist": paste (or share from Spotify / YouTube Music) a playlist link and see the tempo of every
 * song the Library knows, sorted, ready to save as a Set. Songs nobody measured yet are listed as an invitation.
 */
@Composable
internal fun PlaylistScreen(state: PlaylistState, vm: LibraryViewModel, settings: Settings, onSaved: (Long) -> Unit, modifier: Modifier) {
    var link by rememberSaveable(state.link) { mutableStateOf(state.link) }
    var byBpm by rememberSaveable { mutableStateOf(true) }
    val scope = rememberCoroutineScope()
    val result = state.result
    val measured = result?.tracks.orEmpty().filter { it.song != null }.let { t -> if (byBpm) t.sortedBy { it.song!!.bpm } else t }
    val unmeasured = result?.tracks.orEmpty().filter { it.song == null }

    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            TextButton(onClick = vm::closePlaylist) { Text(str(R.string.sets_back)) }
        }
        item {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(str(R.string.playlist_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    str(R.string.playlist_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = link,
                    onValueChange = { link = it.take(2000) },
                    placeholder = { Text("https://open.spotify.com/playlist/…") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { if (link.isNotBlank()) vm.lookupPlaylist(link) }),
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(onClick = { vm.lookupPlaylist(link) }, enabled = link.isNotBlank() && !state.loading) {
                    Text(if (state.loading) str(R.string.playlist_reading) else str(R.string.playlist_get))
                }
                state.error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            }
        }
        if (state.loading) {
            item { SkeletonSongList(6, Modifier.padding(top = 6.dp)) }
        }
        if (result != null) {
            item {
                Column(Modifier.padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    val name = result.name ?: str(R.string.playlist_default_name)
                    Text(name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        listOfNotNull(
                            strPlural(R.plurals.playlist_matched, result.total, result.matched, result.total),
                            if (measured.isNotEmpty()) str(R.string.playlist_in_target, measured.count { settings.inTarget(it.song!!.bpm) }) else null,
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (measured.isNotEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = {
                                scope.launch {
                                    vm.createSetWith(name, measured.mapNotNull { it.song })?.let(onSaved)
                                }
                            }) { Text(str(R.string.sets_save_as_set)) }
                            Spacer(Modifier.weight(1f))
                            TextButton(onClick = { byBpm = !byBpm }) { Text(if (byBpm) str(R.string.playlist_sorted_bpm) else str(R.string.playlist_order)) }
                        }
                    }
                }
            }
            items(measured, key = { "m${it.position}" }) { t ->
                TrackRow(t, inTarget = settings.inTarget(t.song!!.bpm), onClick = { vm.open(t.song.slug) })
            }
            if (unmeasured.isNotEmpty()) {
                item {
                    Column(Modifier.padding(top = 10.dp)) {
                        Text(str(R.string.playlist_not_measured), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            str(R.string.playlist_not_measured_body),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(unmeasured, key = { "u${it.position}" }) { t -> TrackRow(t, inTarget = false, onClick = null) }
            }
        }
    }
}

@Composable
private fun TrackRow(track: PlaylistTrack, inTarget: Boolean, onClick: (() -> Unit)?) {
    val song = track.song
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier.alpha(0.6f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (song != null) {
            SongArtwork(song.artworkUrl, sizeDp = 42, cornerDp = 10)
        } else {
            Box(Modifier.size(42.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(song?.title ?: track.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(song?.artist ?: track.artist, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (song != null) {
            BpmBadge(song.bpm, inTarget)
        } else {
            Text(str(R.string.playlist_unknown_bpm), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
