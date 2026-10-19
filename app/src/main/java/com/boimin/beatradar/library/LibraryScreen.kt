package com.boimin.beatradar.library

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.boimin.beatradar.R
import com.boimin.beatradar.data.Settings
import com.boimin.beatradar.net.SongSummary
import com.boimin.beatradar.ui.BpmBadge
import kotlin.math.roundToInt

/** What the Library needs from the rest of the app. */
class LibraryActions(
    val signedIn: Boolean,
    /** Opens the sign-in sheet; [then] runs once signed in (e.g. to finish a love). */
    val requireSignIn: (then: () -> Unit) -> Unit,
    val share: (SongSummary) -> Unit,
    val openUrl: (String) -> Unit,
)

@Composable
fun LibraryScreen(vm: LibraryViewModel, settings: Settings, actions: LibraryActions, modifier: Modifier = Modifier) {
    val detail by vm.detail.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        // Starts at the user's own target range, like the Listen screen.
        if (!vm.list.value.loadedOnce && settings.rangeEnabled) {
            vm.query.value = vm.query.value.copy(bpmMin = settings.minBpm, bpmMax = settings.maxBpm, halfDouble = settings.halfDoubleMatch)
        }
        vm.ensureLoaded()
    }
    val d = detail
    if (d != null) {
        BackHandler { vm.close() }
        SongDetailScreen(d, vm, actions, modifier)
    } else {
        SongList(vm, actions, modifier)
    }
}

@Composable
private fun SongList(vm: LibraryViewModel, actions: LibraryActions, modifier: Modifier) {
    val list by vm.list.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val nearEnd by remember {
        derivedStateOf { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index?.let { it >= listState.layoutInfo.totalItemsCount - 5 } ?: false }
    }
    LaunchedEffect(nearEnd, list.nextCursor) { if (nearEnd && list.nextCursor != null) vm.loadMore() }

    Column(modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query.text,
            onValueChange = { vm.query.value = query.copy(text = it) },
            placeholder = { Text("Search title or artist") },
            leadingIcon = { Icon(painterResource(R.drawable.ic_search), null) },
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 8.dp),
        )
        Filters(query, actions.signedIn, onChange = { vm.query.value = it })
        if (!actions.signedIn) {
            Text(
                "Sign in to love songs and back up your log",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { actions.requireSignIn {} }
                    .padding(vertical = 6.dp),
            )
        }
        when {
            list.items.isEmpty() && list.loading -> Centered { CircularProgressIndicator() }
            list.items.isEmpty() && list.error != null -> Message(
                if (list.error == "You're offline") "You're offline" else "Couldn't load the Library",
                list.error.takeIf { it != "You're offline" } ?: "BeatRadar keeps counting offline. The Library needs a connection.",
                retry = vm::reload,
            )
            list.items.isEmpty() && list.loadedOnce -> Message(
                "No songs here yet",
                if (query.text.isNotBlank() || query.bpmMin != null || query.lovedOnly) "Try another search or tempo." else "Songs appear as people measure them.",
            )
            else -> LazyColumn(
                state = listState,
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(list.items, key = { it.slug }) { song -> SongRow(song) { vm.open(song.slug) } }
                if (list.loading) item { Box(Modifier.fillMaxWidth().padding(12.dp), Alignment.Center) { CircularProgressIndicator(Modifier.size(24.dp)) } }
                if (list.error != null) item { TextButton(onClick = vm::loadMore, modifier = Modifier.fillMaxWidth()) { Text("${list.error} · Retry") } }
            }
        }
    }
}

/** Tempo range (the same 60–200 scale as Listen), half/double, sort and Loved. */
@Composable
private fun Filters(query: SongQuery, signedIn: Boolean, onChange: (SongQuery) -> Unit) {
    var sortOpen by remember { mutableStateOf(false) }
    Row(
        Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val ranged = query.bpmMin != null
        FilterChip(
            selected = ranged,
            onClick = { onChange(if (ranged) query.copy(bpmMin = null, bpmMax = null) else query.copy(bpmMin = 120, bpmMax = 130)) },
            label = { Text(if (ranged) "${query.bpmMin}–${query.bpmMax} BPM" else "Any tempo") },
            shape = RoundedCornerShape(50),
        )
        if (ranged) {
            FilterChip(
                selected = query.halfDouble,
                onClick = { onChange(query.copy(halfDouble = !query.halfDouble)) },
                label = { Text("½× / 2×") },
                shape = RoundedCornerShape(50),
            )
        }
        Box {
            FilterChip(selected = false, onClick = { sortOpen = true }, label = { Text(query.sort.label) }, shape = RoundedCornerShape(50))
            DropdownMenu(expanded = sortOpen, onDismissRequest = { sortOpen = false }) {
                SongSort.entries.forEach { s ->
                    DropdownMenuItem(text = { Text(s.label) }, onClick = { sortOpen = false; onChange(query.copy(sort = s)) })
                }
            }
        }
        if (signedIn) {
            FilterChip(
                selected = query.lovedOnly,
                onClick = { onChange(query.copy(lovedOnly = !query.lovedOnly)) },
                label = { Text("Loved") },
                leadingIcon = { Icon(painterResource(R.drawable.ic_favorite), null, Modifier.size(16.dp)) },
                shape = RoundedCornerShape(50),
            )
        }
    }
    if (query.bpmMin != null && query.bpmMax != null) {
        RangeSlider(
            value = query.bpmMin.toFloat()..query.bpmMax.toFloat(),
            onValueChange = { r ->
                val lo = r.start.roundToInt()
                val hi = r.endInclusive.roundToInt().coerceAtLeast(lo)
                if (lo != query.bpmMin || hi != query.bpmMax) onChange(query.copy(bpmMin = lo, bpmMax = hi))
            },
            valueRange = Settings.BPM_FLOOR.toFloat()..Settings.BPM_CEILING.toFloat(),
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
}

@Composable
private fun SongRow(song: SongSummary, onOpen: () -> Unit) {
    Card(
        onClick = onOpen,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            BpmBadge(song.bpm, inRange = false)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(song.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    song.artist,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                SongCounters(song.listeners, song.confirmations, song.loveCount)
            }
            if (song.artworkUrl != null) {
                Spacer(Modifier.width(10.dp))
                AsyncImage(
                    model = song.artworkUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)),
                )
            }
        }
    }
}

@Composable
internal fun Centered(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) { content() }
}

@Composable
internal fun Message(title: String, body: String, retry: (() -> Unit)? = null) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(painterResource(R.drawable.ic_library), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(56.dp))
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        if (retry != null) {
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = retry) { Text("Try again") }
        }
    }
}

/** A plain BPM disc in the style of the Listen circle, for the song page. */
@Composable
internal fun BigBpm(bpm: Float, alt: Float?) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(150.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    bpm.roundToInt().toString(),
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text("BPM", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
        if (alt != null) {
            Spacer(Modifier.height(6.dp))
            Text("also heard as ${alt.roundToInt()}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
