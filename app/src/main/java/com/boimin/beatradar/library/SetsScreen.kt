package com.boimin.beatradar.library

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.boimin.beatradar.R
import com.boimin.beatradar.data.SetRules
import com.boimin.beatradar.data.SetSong
import com.boimin.beatradar.data.SetSummary
import com.boimin.beatradar.data.Settings
import com.boimin.beatradar.net.SongSummary
import com.boimin.beatradar.ui.BpmBadge
import com.boimin.beatradar.ui.theme.Brand
import kotlinx.coroutines.launch

/** "My sets" in the Library tab: the user's song lists by tempo. */
@Composable
internal fun SetsScreen(vm: LibraryViewModel, settings: Settings, modifier: Modifier = Modifier) {
    val sets by vm.sets.collectAsStateWithLifecycle()
    var openSet by rememberSaveable { mutableStateOf<Long?>(null) }
    var creating by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val open = openSet?.let { id -> sets.firstOrNull { it.id == id } }
    if (open != null) {
        BackHandler { openSet = null }
        SetDetail(open, vm, settings, onBack = { openSet = null }, modifier = modifier)
        return
    }

    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Group songs by tempo for a run, a workout or a DJ set. Add songs from their page.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(10.dp))
                Button(onClick = { creating = true }, contentPadding = PaddingValues(horizontal = 14.dp)) { Text("New set") }
            }
        }
        if (sets.isEmpty()) {
            item {
                Column(
                    Modifier.fillMaxWidth().padding(top = 48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(painterResource(R.drawable.ic_set_add), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp))
                    Text("No sets yet", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Create one, then open any song in Discover and tap Add to set.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        items(sets, key = { it.id }) { set -> SetCard(set, onClick = { openSet = set.id }) }
    }

    if (creating) {
        NameDialog(
            title = "New set",
            initial = "",
            confirm = "Create",
            onDismiss = { creating = false },
            onSave = { name ->
                creating = false
                scope.launch { vm.createSet(name)?.let { openSet = it } }
            },
        )
    }
}

@Composable
private fun SetCard(set: SetSummary, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip16()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(44.dp).clip12().background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(R.drawable.ic_list), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(set.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${set.songs} song${if (set.songs == 1) "" else "s"} · ${SetRules.span(set.minBpm, set.maxBpm)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SetDetail(set: SetSummary, vm: LibraryViewModel, settings: Settings, onBack: () -> Unit, modifier: Modifier) {
    val songs by remember(set.id) { vm.setSongs(set.id) }.collectAsStateWithLifecycle(emptyList())
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    val inTarget = songs.count { settings.inTarget(it.bpm) }

    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onBack) { Text("‹ My sets") }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { renaming = true }) { Text("Rename") }
                TextButton(onClick = { deleting = true }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            }
        }
        item {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip16()
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(set.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    "${songs.size} song${if (songs.size == 1) "" else "s"} · ${SetRules.span(set.minBpm, set.maxBpm)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (songs.isNotEmpty()) {
                    Text(
                        "$inTarget in your target ${settings.minBpm}–${settings.maxBpm} BPM",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (inTarget > 0) Brand.Coral else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (songs.isEmpty()) {
            item {
                Text(
                    "Open any song in Discover and tap Add to set to put it here. Songs are sorted by tempo.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
        }
        items(songs, key = { it.slug }) { song ->
            SetSongRow(
                song,
                inTarget = settings.inTarget(song.bpm),
                onOpen = { vm.open(song.slug) },
                onRemove = { vm.removeFromSet(set.id, song.slug) },
            )
        }
    }

    if (renaming) {
        NameDialog("Rename set", set.name, "Save", onDismiss = { renaming = false }, onSave = { renaming = false; vm.renameSet(set.id, it) })
    }
    if (deleting) {
        AlertDialog(
            onDismissRequest = { deleting = false },
            title = { Text("Delete “${set.name}”?") },
            text = { Text("The songs stay in the Library; only this list goes.") },
            confirmButton = { TextButton(onClick = { deleting = false; vm.deleteSet(set.id); onBack() }) { Text("Delete", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { deleting = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SetSongRow(song: SetSong, inTarget: Boolean, onOpen: () -> Unit, onRemove: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip16()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
            .clickable(onClick = onOpen)
            .padding(start = 12.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SongArtwork(song.artworkUrl, sizeDp = 42, cornerDp = 10)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(song.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(song.artist, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        BpmBadge(song.bpm, inTarget)
        TextButton(onClick = onRemove) { Text("Remove", style = MaterialTheme.typography.labelSmall) }
    }
}

/** From a song's page: tick the sets it belongs in, or start a new one with it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AddToSetSheet(song: SongSummary, vm: LibraryViewModel, onDismiss: () -> Unit) {
    val sets by vm.sets.collectAsStateWithLifecycle()
    val inSets by remember(song.slug) { vm.setsWith(song.slug) }.collectAsStateWithLifecycle(emptyList())
    var newName by rememberSaveable { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(start = 24.dp, end = 24.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Add to set", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(
                "${song.title} · ${Math.round(song.bpm)} BPM",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            sets.forEach { set ->
                val checked = set.id in inSets
                Row(
                    Modifier.fillMaxWidth().clip12().clickable { vm.toggleInSet(set.id, song, checked) }.padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = checked, onCheckedChange = { vm.toggleInSet(set.id, song, checked) })
                    Column(Modifier.weight(1f)) {
                        Text(set.name, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "${set.songs} song${if (set.songs == 1) "" else "s"} · ${SetRules.span(set.minBpm, set.maxBpm)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it.take(SetRules.MAX_NAME) },
                    label = { Text(if (sets.isEmpty()) "Name your first set" else "New set") },
                    placeholder = { Text("e.g. Morning run") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = { scope.launch { vm.createSet(newName, song) }; newName = "" },
                    enabled = SetRules.cleanName(newName) != null,
                ) { Text("Create") }
            }
        }
    }
}

@Composable
private fun NameDialog(title: String, initial: String, confirm: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(SetRules.MAX_NAME) },
                placeholder = { Text("e.g. Morning run 160–170") },
                singleLine = true,
            )
        },
        confirmButton = { TextButton(onClick = { onSave(name) }, enabled = SetRules.cleanName(name) != null) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun Modifier.clip16() = this.then(Modifier.clip(RoundedCornerShape(16.dp)))

private fun Modifier.clip12() = this.then(Modifier.clip(RoundedCornerShape(12.dp)))
