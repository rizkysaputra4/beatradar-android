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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.boimin.beatradar.data.Settings
import com.boimin.beatradar.net.SongSummary
import com.boimin.beatradar.ui.BpmBadge
import com.boimin.beatradar.ui.theme.brandSwitchColors
import kotlinx.coroutines.launch

/** A set someone shared as a link: its songs, and "Copy this set" into My sets. */
@Composable
internal fun SharedSetScreen(state: SharedSetState, vm: LibraryViewModel, settings: Settings, onSaved: (Long) -> Unit, modifier: Modifier) {
    val scope = rememberCoroutineScope()
    val set = state.set
    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { TextButton(onClick = vm::closeSharedSet) { Text("‹ My sets") } }
        when {
            set != null -> {
                item {
                    Card {
                        Text(set.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        val sorted = set.songs.sortedBy { it.bpm }
                        Text(
                            "${set.songs.size} songs · ${com.boimin.beatradar.data.SetRules.span(sorted.firstOrNull()?.bpm, sorted.lastOrNull()?.bpm)}" +
                                (set.sharedBy?.let { " · shared by $it" } ?: ""),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Button(onClick = { scope.launch { vm.createSetWith(set.name, set.songs)?.let(onSaved) } }) { Text("Copy this set") }
                    }
                }
                items(set.songs.sortedBy { it.bpm }, key = { it.slug }) { s ->
                    SongRow(s, settings.inTarget(s.bpm), onClick = { vm.open(s.slug) })
                }
            }
            state.error != null -> item { Text(state.error, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp)) }
            else -> item { Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
        }
    }
}

/** "Build me a set": N minutes of well-confirmed songs in the target range, ready to save. */
@Composable
internal fun BuildSetScreen(state: BuildState, vm: LibraryViewModel, settings: Settings, onSaved: (Long) -> Unit, modifier: Modifier) {
    var minutes by rememberSaveable { mutableIntStateOf(state.minutes) }
    var onlyMine by rememberSaveable { mutableStateOf(state.onlyMine) }
    val range = settings.minBpm..settings.maxBpm
    val scope = rememberCoroutineScope()
    val result = state.result
    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { TextButton(onClick = vm::closeBuild) { Text("‹ My sets") } }
        item {
            Card {
                Text("Build me a set", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    "Well-confirmed songs at your target ${range.first}–${range.last} BPM. Change the target on the Listen tab.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (m in listOf(15, 30, 45, 60)) {
                        Chip("$m min", selected = minutes == m, modifier = Modifier.weight(1f)) { minutes = m }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Only songs I've heard", style = MaterialTheme.typography.bodyMedium)
                        Text("Songs this phone measured", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = onlyMine, onCheckedChange = { onlyMine = it }, colors = brandSwitchColors())
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { vm.runBuild(range, settings.halfDoubleMatch, minutes, onlyMine) }, enabled = !state.loading) {
                        Text(if (state.loading) "Building…" else "Build")
                    }
                    if (result != null && result.songs.isNotEmpty()) {
                        TextButton(onClick = { vm.runBuild(range, settings.halfDoubleMatch, minutes, onlyMine, again = true) }) { Text("Try another mix") }
                    }
                }
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        }
        if (result != null) {
            item {
                val got = result.totalMs / 60_000
                val want = result.targetMs / 60_000
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        when {
                            result.songs.isEmpty() -> "No songs in the Library at this tempo yet. Measure some to grow it."
                            result.totalMs < result.targetMs -> "Found $got of $want minutes: the Library has no more at this tempo yet."
                            else -> "${result.songs.size} songs · about $got minutes"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (result.songs.any { it.estimated }) {
                        Text("Some lengths are guesses (3:30).", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (result.songs.isNotEmpty()) {
                        Button(onClick = {
                            scope.launch { vm.createSetWith("$minutes min at ${range.first}–${range.last}", result.songs.map { it.summary() })?.let(onSaved) }
                        }) { Text("Save as set") }
                    }
                }
            }
            items(result.songs, key = { it.slug }) { s ->
                SongRow(s.summary(), settings.inTarget(s.bpm), onClick = { vm.open(s.slug) }, trailing = "%d:%02d".format(s.durationMs / 60_000, s.durationMs / 1000 % 60))
            }
        }
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) { content() }
}

@Composable
internal fun Chip(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceContainerHigh)
            .border(1.dp, if (selected) MaterialTheme.colorScheme.primary else Color.Transparent, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

@Composable
private fun SongRow(song: SongSummary, inTarget: Boolean, onClick: () -> Unit, trailing: String? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SongArtwork(song.artworkUrl, sizeDp = 42, cornerDp = 10)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(song.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(song.artist, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        trailing?.let {
            Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(8.dp))
        }
        BpmBadge(song.bpm, inTarget)
    }
}
