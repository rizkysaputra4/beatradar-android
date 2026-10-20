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
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.unit.sp
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
internal fun SetsScreen(vm: LibraryViewModel, settings: Settings, actions: LibraryActions, modifier: Modifier = Modifier) {
    val sets by vm.sets.collectAsStateWithLifecycle()
    val playlist by vm.playlist.collectAsStateWithLifecycle()
    val shared by vm.sharedSet.collectAsStateWithLifecycle()
    val build by vm.build.collectAsStateWithLifecycle()
    var openSet by rememberSaveable { mutableStateOf<Long?>(null) }
    var creating by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val open = openSet?.let { id -> sets.firstOrNull { it.id == id } }
    if (open != null) {
        // Closing the set: the next time it plays in another app, ask again.
        val close = { openSet = null; vm.playChoice = null }
        BackHandler(onBack = close)
        SetDetail(open, vm, settings, actions, onBack = close, modifier = modifier)
        return
    }
    playlist?.let { p ->
        BackHandler { vm.closePlaylist() }
        PlaylistScreen(p, vm, settings, onSaved = { id -> vm.closePlaylist(); openSet = id }, modifier = modifier)
        return
    }
    shared?.let { s ->
        BackHandler { vm.closeSharedSet() }
        SharedSetScreen(s, vm, settings, onSaved = { id -> vm.closeSharedSet(); openSet = id }, modifier = modifier)
        return
    }
    build?.let { b ->
        BackHandler { vm.closeBuild() }
        BuildSetScreen(b, vm, settings, onSaved = { id -> vm.closeBuild(); openSet = id }, modifier = modifier)
        return
    }

    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Group songs by tempo for a run, a workout or a DJ set. Add songs from their page, or start from a playlist.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { creating = true }, contentPadding = PaddingValues(horizontal = 14.dp)) { Text("New set") }
                    OutlinedButton(onClick = { vm.openBuild() }, contentPadding = PaddingValues(horizontal = 14.dp)) { Text("Build me a set") }
                }
                OutlinedButton(onClick = { vm.openPlaylist() }, contentPadding = PaddingValues(horizontal = 14.dp)) { Text("BPM of a playlist") }
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
private fun SetDetail(set: SetSummary, vm: LibraryViewModel, settings: Settings, actions: LibraryActions, onBack: () -> Unit, modifier: Modifier) {
    val songs by remember(set.id) { vm.setSongs(set.id) }.collectAsStateWithLifecycle(emptyList())
    val scope = rememberCoroutineScope()
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    // The first ▶ in an open set asks which app; later ones go straight there (until the set is closed).
    var choosingFor by remember { mutableStateOf<SetSong?>(null) }
    // Selecting songs (long-press) to remove several at once; swiping one left removes just that one.
    var selected by rememberSaveable(set.id) { mutableStateOf(setOf<String>()) }
    var removing by remember { mutableStateOf<Set<String>?>(null) }
    val selecting = selected.isNotEmpty()
    // Back while selecting only ends the selection.
    BackHandler(enabled = selecting) { selected = emptySet() }
    val inTarget = songs.count { settings.inTarget(it.bpm) }

    fun play(song: SetSong, service: PlayLinks.Service) {
        scope.launch {
            when (service) {
                PlayLinks.Service.SPOTIFY -> actions.openUrl(vm.playUrl(song.slug, song.title, song.artist, service))
                // YouTube plays from this song to the end of the set as one queue.
                PlayLinks.Service.YOUTUBE_MUSIC -> {
                    val from = songs.indexOf(song).coerceAtLeast(0)
                    val q = vm.youtubeQueue(songs.drop(from).map { it.slug })
                    actions.openUrl(
                        q?.url ?: ("https://www.youtube.com/results?search_query=" +
                            java.net.URLEncoder.encode("${song.artist} ${song.title}", Charsets.UTF_8)),
                    )
                }
            }
        }
    }
    fun onPlay(song: SetSong) {
        val chosen = vm.playChoice?.takeIf { it.first == set.id }?.second
        if (chosen != null) play(song, chosen) else choosingFor = song
    }
    fun toggle(slug: String) {
        selected = if (slug in selected) selected - slug else selected + slug
    }

    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            if (selecting) {
                SelectionBar(selected.size, onCancel = { selected = emptySet() }) {
                    IconButton(onClick = { removing = selected }) {
                        Icon(painterResource(R.drawable.ic_delete), contentDescription = "Remove selected songs", tint = MaterialTheme.colorScheme.error)
                    }
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onBack) { Text("‹ My sets") }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { scope.launch { vm.shareSet(set.id)?.let(actions.shareText) } }) { Text("Share") }
                    TextButton(onClick = { renaming = true }) { Text("Rename") }
                    TextButton(onClick = { deleting = true }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                }
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
                    Spacer(Modifier.height(6.dp))
                    // The whole set, in order, as one YouTube playlist.
                    Button(onClick = { play(songs.first(), PlayLinks.Service.YOUTUBE_MUSIC) }, contentPadding = PaddingValues(horizontal = 14.dp)) {
                        Icon(painterResource(R.drawable.ic_play), contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Play all in YouTube")
                    }
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
            SwipeToDelete(onDelete = { removing = setOf(song.slug) }, enabled = !selecting) {
                LibrarySongRow(
                    title = song.title,
                    artist = song.artist,
                    artworkUrl = song.artworkUrl,
                    bpm = song.bpm,
                    inTarget = settings.inTarget(song.bpm),
                    selected = if (selecting) song.slug in selected else null,
                    onClick = { if (selecting) toggle(song.slug) else vm.open(song.slug) },
                    onLongClick = { toggle(song.slug) },
                    trailing = if (selecting) null else ({
                        IconButton(onClick = { onPlay(song) }) {
                            Icon(painterResource(R.drawable.ic_play), contentDescription = "Play ${song.title}", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                        }
                    }),
                )
            }
        }
    }

    choosingFor?.let { song ->
        AlertDialog(
            onDismissRequest = { choosingFor = null },
            title = { Text("Play in") },
            text = { Text("Which app should this set's songs open in?") },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for ((service, label) in listOf(PlayLinks.Service.YOUTUBE_MUSIC to "YouTube", PlayLinks.Service.SPOTIFY to "Spotify")) {
                        TextButton(onClick = {
                            choosingFor = null
                            vm.playChoice = set.id to service
                            play(song, service)
                        }) { Text(label) }
                    }
                }
            },
            dismissButton = { TextButton(onClick = { choosingFor = null }) { Text("Cancel") } },
        )
    }
    removing?.let { slugs ->
        val one = slugs.singleOrNull()?.let { s -> songs.firstOrNull { it.slug == s } }
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text(if (one != null) "Remove “${one.title}”?" else "Remove ${slugs.size} songs?") },
            text = { Text("They come out of “${set.name}”. The songs stay in the Library.") },
            confirmButton = {
                TextButton(onClick = {
                    removing = null
                    vm.removeSongs(set.id, slugs)
                    selected = selected - slugs
                }) { Text("Remove", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { removing = null }) { Text("Cancel") } },
        )
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
