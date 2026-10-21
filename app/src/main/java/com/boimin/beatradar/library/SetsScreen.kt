package com.boimin.beatradar.library

import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.CircleShape
import com.boimin.beatradar.ui.BpmValue
import com.boimin.beatradar.ui.theme.Tone
import com.boimin.beatradar.ui.theme.inTargetColor
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
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
fun SetsScreen(vm: LibraryViewModel, settings: Settings, actions: LibraryActions, modifier: Modifier = Modifier) {
    val detail by vm.detail.collectAsStateWithLifecycle()
    detail?.let { d ->
        BackHandler { vm.close() }
        SongDetailScreen(d, vm, actions, settings, modifier)
        return
    }
    val sets by vm.sets.collectAsStateWithLifecycle()
    val playlist by vm.playlist.collectAsStateWithLifecycle()
    val shared by vm.sharedSet.collectAsStateWithLifecycle()
    val build by vm.build.collectAsStateWithLifecycle()
    val openSet by vm.openSetId.collectAsStateWithLifecycle()
    var creating by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val open = openSet?.let { id -> sets.firstOrNull { it.id == id } }
    if (open != null) {
        // Closing the set: the next time it plays in another app, ask again.
        val close = { vm.openSetId.value = null }
        BackHandler(onBack = close)
        SetDetail(open, vm, settings, actions, onBack = close, modifier = modifier)
        return
    }
    playlist?.let { p ->
        BackHandler { vm.closePlaylist() }
        PlaylistScreen(p, vm, settings, onSaved = { id -> vm.closePlaylist(); vm.openSetId.value = id }, modifier = modifier)
        return
    }
    shared?.let { s ->
        BackHandler { vm.closeSharedSet() }
        SharedSetScreen(s, vm, settings, onSaved = { id -> vm.closeSharedSet(); vm.openSetId.value = id }, modifier = modifier)
        return
    }
    build?.let { b ->
        BackHandler { vm.closeBuild() }
        BuildSetScreen(b, vm, settings, onSaved = { id -> vm.closeBuild(); vm.openSetId.value = id }, modifier = modifier)
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
        items(sets, key = { it.id }) { set -> SetCard(set, onClick = { vm.openSetId.value = set.id }) }
    }

    if (creating) {
        NameDialog(
            title = "New set",
            initial = "",
            confirm = "Create",
            onDismiss = { creating = false },
            onSave = { name ->
                creating = false
                scope.launch { vm.createSet(name)?.let { vm.openSetId.value = it } }
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
    LaunchedEffect(songs.map { it.slug to it.durationMs }) { vm.fillDurations(songs) }
    val scope = rememberCoroutineScope()
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var choosingApp by remember { mutableStateOf(false) }
    // Selecting songs (long-press) to remove several at once; swiping one left removes just that one. Both can be undone.
    var selected by rememberSaveable(set.id) { mutableStateOf(setOf<String>()) }
    val selecting = selected.isNotEmpty()
    BackHandler(enabled = selecting) { selected = emptySet() }
    // The app sets play in: a remembered choice, changed with "Change".
    val service = if (settings.playApp == "spotify") PlayLinks.Service.SPOTIFY else PlayLinks.Service.YOUTUBE_MUSIC
    val appName = if (service == PlayLinks.Service.SPOTIFY) "Spotify" else "YouTube"

    fun play(from: Int) {
        val song = songs.getOrNull(from) ?: return
        scope.launch {
            when (service) {
                PlayLinks.Service.SPOTIFY -> actions.openUrl(vm.playUrl(song.slug, song.title, song.artist, service))
                // YouTube plays from this song to the end of the set as one queue.
                PlayLinks.Service.YOUTUBE_MUSIC -> {
                    val q = vm.youtubeQueue(songs.drop(from).map { it.slug })
                    actions.openUrl(
                        q?.url ?: ("https://www.youtube.com/results?search_query=" +
                            java.net.URLEncoder.encode("${song.artist} ${song.title}", Charsets.UTF_8)),
                    )
                }
            }
        }
    }
    fun toggle(slug: String) {
        selected = if (slug in selected) selected - slug else selected + slug
    }

    val totalMs = songs.sumOf { (it.durationMs ?: 0).toLong() }
    val allTimed = songs.isNotEmpty() && songs.all { (it.durationMs ?: 0) > 0 }
    val inTarget = songs.count { settings.inTarget(it.bpm) }

    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            if (selecting) {
                // Same 52 dp as the top bar it replaces, so the page does not move.
                Box(Modifier.fillMaxWidth().padding(horizontal = 14.dp).height(52.dp).padding(vertical = 5.dp)) {
                    SelectionBar(selected.size, onCancel = { selected = emptySet() }, modifier = Modifier.fillMaxSize()) {
                        BarIcon(R.drawable.ic_delete, "Remove selected songs", 22, MaterialTheme.colorScheme.error) {
                            vm.removeSongs(set.id, songs.filter { it.slug in selected })
                            selected = emptySet()
                        }
                    }
                }
            } else {
                // Design: 44 dp touch targets, back at 24 dp, share and ⋮ at 22 dp in the softer text colour.
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp).height(52.dp), verticalAlignment = Alignment.CenterVertically) {
                    BarIcon(R.drawable.ic_arrow_back, "My sets", 24, MaterialTheme.colorScheme.onSurface, onBack)
                    Spacer(Modifier.weight(1f))
                    BarIcon(R.drawable.ic_share, "Share", 22, Tone.text2) { scope.launch { vm.shareSet(set.id)?.let(actions.shareText) } }
                    Spacer(Modifier.width(4.dp))
                    Box {
                        BarIcon(R.drawable.ic_more, "More", 22, Tone.text2) { menu = true }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("Rename") }, onClick = { menu = false; renaming = true })
                            DropdownMenuItem(text = { Text("Delete set", color = MaterialTheme.colorScheme.error) }, onClick = { menu = false; deleting = true })
                        }
                    }
                }
            }
        }
        item {
            Column(Modifier.padding(start = 22.dp, end = 22.dp, top = 8.dp)) {
                Text(set.name, fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text(
                    listOfNotNull(
                        "${songs.size} song${if (songs.size == 1) "" else "s"}",
                        (totalMs / 60_000).takeIf { it > 0 }?.let { (if (allTimed) "" else "about ") + "$it min" },
                        SetRules.span(set.minBpm, set.maxBpm).takeIf { songs.isNotEmpty() },
                    ).joinToString(" · "),
                    fontSize = 15.sp,
                    color = Tone.text2,
                )
                if (songs.isNotEmpty() && settings.rangeEnabled) {
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (inTarget == songs.size) {
                            Icon(painterResource(R.drawable.ic_check_circle), contentDescription = null, tint = inTargetColor(), modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                        }
                        Text(
                            if (inTarget == songs.size) "All in your ${settings.minBpm}–${settings.maxBpm} target"
                            else "$inTarget of ${songs.size} in your ${settings.minBpm}–${settings.maxBpm} target",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (inTarget > 0) inTargetColor() else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (songs.isNotEmpty()) {
                    Spacer(Modifier.height(20.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Row(
                            Modifier
                                .height(52.dp)
                                .clip(RoundedCornerShape(26.dp))
                                .background(MaterialTheme.colorScheme.primary)
                                .clickable { play(0) }
                                .padding(horizontal = 24.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(painterResource(R.drawable.ic_play), contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Play set in ", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = Color.White)
                            Text(appName, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = Color.White)
                        }
                        Box {
                            Row(
                                Modifier
                                    .height(54.dp)
                                    .clip(RoundedCornerShape(26.dp))
                                    .border(1.dp, Tone.line, RoundedCornerShape(26.dp))
                                    .clickable { choosingApp = true }
                                    .padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("Change", fontSize = 14.sp, color = Tone.text2)
                                Spacer(Modifier.width(4.dp))
                                Icon(painterResource(R.drawable.ic_expand_more), contentDescription = null, tint = Tone.text2, modifier = Modifier.size(18.dp))
                            }
                            DropdownMenu(expanded = choosingApp, onDismissRequest = { choosingApp = false }) {
                                for ((value, label) in listOf("youtube" to "YouTube", "spotify" to "Spotify")) {
                                    DropdownMenuItem(
                                        text = { Text(if (settings.playApp == value) "✓ $label" else label) },
                                        onClick = { choosingApp = false; actions.updateSettings { it.copy(playApp = value) } },
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
        if (songs.isEmpty()) {
            item {
                Text(
                    "Open any song in Library and tap Add to set to put it here. Songs are sorted by tempo.",
                    fontSize = 13.sp,
                    color = Tone.text3,
                    modifier = Modifier.padding(start = 22.dp, end = 22.dp, top = 16.dp),
                )
            }
        }
        itemsIndexed(songs, key = { _, s -> s.slug }) { index, song ->
            Box(Modifier.padding(horizontal = 18.dp, vertical = 4.dp)) {
                SwipeToDelete(onDelete = { vm.removeSongs(set.id, listOf(song)) }, enabled = !selecting) {
                    SetSongRow(
                        song,
                        inTarget = settings.inTarget(song.bpm),
                        selected = if (selecting) song.slug in selected else null,
                        onClick = { if (selecting) toggle(song.slug) else vm.open(song.slug) },
                        onLongClick = { toggle(song.slug) },
                        onPlay = { play(index) },
                    )
                }
            }
        }
        if (songs.isNotEmpty()) {
            item {
                Text(
                    "Swipe a song left to remove it. Long-press to select several.",
                    fontSize = 13.sp,
                    color = Tone.text3,
                    modifier = Modifier.padding(start = 22.dp, end = 22.dp, top = 14.dp),
                )
            }
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

/** A 44 dp top-bar button. */
@Composable
internal fun BarIcon(icon: Int, description: String, sizeDp: Int, tint: Color, onClick: () -> Unit) {
    Box(Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(painterResource(icon), contentDescription = description, tint = tint, modifier = Modifier.size(sizeDp.dp))
    }
}

/** A song in a set, laid out like the Library rows ("artist · 3:42"), with a round ▶ on the right. */
@Composable
private fun SetSongRow(song: SetSong, inTarget: Boolean, selected: Boolean?, onClick: () -> Unit, onLongClick: () -> Unit, onPlay: () -> Unit) {
    LibrarySongRow(
        title = song.title,
        artist = listOfNotNull(song.artist, song.durationMs?.takeIf { it > 0 }?.let { "%d:%02d".format(it / 60_000, it / 1000 % 60) }).joinToString(" · "),
        artworkUrl = song.artworkUrl,
        bpm = song.bpm,
        inTarget = inTarget,
        onClick = onClick,
        onLongClick = onLongClick,
        selected = selected,
        trailing = if (selected == null) {
            {
                Spacer(Modifier.width(10.dp))
                PlayButton("Play ${song.title}", onPlay)
            }
        } else {
            null
        },
    )
}

/** A 40 dp violet-tinted circle with a rounded ▶. */
@Composable
private fun PlayButton(description: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f))
            .clickable(onClickLabel = description, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(R.drawable.ic_play), contentDescription = description, tint = Tone.primarySoft, modifier = Modifier.size(22.dp))
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
