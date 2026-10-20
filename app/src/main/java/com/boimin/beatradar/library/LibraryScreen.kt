package com.boimin.beatradar.library

import androidx.compose.material3.Button
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.boimin.beatradar.R
import com.boimin.beatradar.data.Settings
import com.boimin.beatradar.net.Facets
import com.boimin.beatradar.net.SongSummary
import com.boimin.beatradar.ui.BpmBadge
import com.boimin.beatradar.ui.LogFilterPill
import com.boimin.beatradar.ui.theme.Brand
import kotlin.math.roundToInt

/** What the Library needs from the rest of the app. */
class LibraryActions(
    val signedIn: Boolean,
    /** Opens the sign-in sheet; [then] runs once signed in (e.g. to finish a love). */
    val requireSignIn: (then: () -> Unit) -> Unit,
    val share: (SongSummary) -> Unit,
    val openUrl: (String) -> Unit,
    /** The share sheet with plain text (e.g. a Set's song list). */
    val shareText: (String) -> Unit,
)

@Composable
fun LibraryScreen(vm: LibraryViewModel, settings: Settings, actions: LibraryActions, modifier: Modifier = Modifier) {
    val detail by vm.detail.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        // Starts at the user's own target range, like the Listen screen.
        if (!vm.list.value.loadedOnce && settings.rangeEnabled) {
            vm.query.value = vm.query.value.copy(bpmMin = settings.minBpm, bpmMax = settings.maxBpm, halfDouble = settings.halfDoubleMatch)
        }
        // Discover shows live counts and loves: fresh data every time the tab is opened.
        vm.refresh()
    }
    var showSets by rememberSaveable { mutableStateOf(false) }
    // A playlist link shared from Spotify or YouTube Music opens "BPM of a playlist" under My sets.
    val sharedPlaylist by vm.sharedPlaylistLink.collectAsStateWithLifecycle()
    val sharedSetLink by vm.sharedSetLink.collectAsStateWithLifecycle()
    LaunchedEffect(sharedSetLink) {
        sharedSetLink?.let { slug ->
            vm.close()
            showSets = true
            vm.openSharedSet(slug)
            vm.sharedSetLink.value = null
        }
    }
    LaunchedEffect(sharedPlaylist) {
        sharedPlaylist?.let { link ->
            vm.close()
            showSets = true
            vm.openPlaylist(link)
            vm.sharedPlaylistLink.value = null
        }
    }
    val d = detail
    if (d != null) {
        BackHandler { vm.close() }
        SongDetailScreen(d, vm, actions, modifier)
    } else {
        Column(modifier.fillMaxSize()) {
            LibrarySwitch(showSets, onSelect = { showSets = it })
            if (showSets) SetsScreen(vm, settings, actions) else SongList(vm, actions, Modifier)
        }
    }
}

/** Discover (the public library) or My sets (the user's own song lists by tempo), inside the one Library tab. */
@Composable
private fun LibrarySwitch(showSets: Boolean, onSelect: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 18.dp, end = 18.dp, top = 2.dp, bottom = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
            .padding(3.dp),
    ) {
        listOf(false to "Discover", true to "My sets").forEach { (value, label) ->
            val selected = showSets == value
            Box(
                Modifier
                    .weight(1f)
                    .height(32.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
                    .clickable { onSelect(value) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SongList(vm: LibraryViewModel, actions: LibraryActions, modifier: Modifier) {
    val list by vm.list.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val extras by vm.extras.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    // Long-press starts selecting songs to add to a set together.
    var selected by remember { mutableStateOf(mapOf<String, SongSummary>()) }
    var adding by remember { mutableStateOf(false) }
    val selecting = selected.isNotEmpty()
    // Back while selecting only ends the selection.
    BackHandler(enabled = selecting) { selected = emptyMap() }
    fun toggle(song: SongSummary) {
        selected = if (song.slug in selected) selected - song.slug else selected + (song.slug to song)
    }
    val nearEnd by remember {
        derivedStateOf { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index?.let { it >= listState.layoutInfo.totalItemsCount - 5 } ?: false }
    }
    LaunchedEffect(nearEnd, list.nextCursor) { if (nearEnd && list.nextCursor != null) vm.loadMore() }

    Column(modifier.fillMaxSize()) {
        // Low-profile Search Bar
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 18.dp, end = 18.dp, top = 4.dp, bottom = 8.dp)
                .height(38.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painterResource(R.drawable.ic_search),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(15.dp),
            )
            Spacer(Modifier.width(8.dp))
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (query.text.isEmpty()) {
                    Text(
                        "Search public tempo library…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                BasicTextField(
                    value = query.text,
                    onValueChange = { vm.query.value = query.copy(text = it) },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        if (selecting) {
            Box(Modifier.padding(horizontal = 18.dp, vertical = 4.dp)) {
                SelectionBar(selected.size, onCancel = { selected = emptyMap() }) {
                    Button(onClick = { adding = true }, contentPadding = PaddingValues(horizontal = 14.dp)) { Text("Add to set") }
                }
            }
        } else {
            Filters(query, actions.signedIn, extras.facets, onChange = { vm.query.value = it })
        }
        if (adding) {
            AddManyToSetSheet(
                songs = selected.values.toList(),
                vm = vm,
                onDone = { name ->
                    vm.say("Added ${selected.size} song${if (selected.size == 1) "" else "s"} to “$name”")
                    adding = false
                    selected = emptyMap()
                },
                onDismiss = { adding = false },
            )
        }

        if (!actions.signedIn) {
            Row(
                Modifier
                    .padding(horizontal = 18.dp, vertical = 2.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { actions.requireSignIn {} }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Sign in to love songs and back up your log →",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
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
                contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 6.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val trending = extras.trending?.takeIf { query.text.isBlank() && !query.lovedOnly && it.items.isNotEmpty() && !selecting }
                if (trending != null) {
                    item(key = "trending") {
                        TrendingRow(
                            title = if (trending.fallback) "MOST CONFIRMED AT THIS TEMPO" else "TRENDING AT THIS TEMPO",
                            songs = trending.items,
                            onOpen = { vm.open(it) },
                        )
                    }
                }
                items(list.items, key = { it.slug }) { song ->
                    LibrarySongRow(
                        title = song.title,
                        artist = song.artist,
                        artworkUrl = song.artworkUrl,
                        bpm = song.bpm,
                        inTarget = false,
                        selected = if (selecting) song.slug in selected else null,
                        onClick = { if (selecting) toggle(song) else vm.open(song.slug) },
                        onLongClick = { toggle(song) },
                        counters = { SongCounters(song.listeners, song.confirmations, song.loveCount) },
                    )
                }
                if (list.loading) item { Box(Modifier.fillMaxWidth().padding(12.dp), Alignment.Center) { CircularProgressIndicator(Modifier.size(24.dp)) } }
                if (list.error != null) item { TextButton(onClick = vm::loadMore, modifier = Modifier.fillMaxWidth()) { Text("${list.error} · Retry") } }
            }
        }
    }
}

/** "Trending at your tempo": this week's most measured songs in the range, swiping sideways above the list. */
@Composable
private fun TrendingRow(title: String, songs: List<SongSummary>, onOpen: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            songs.forEach { s ->
                Column(
                    Modifier
                        .width(124.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
                        .clickable { onOpen(s.slug) }
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    SongArtwork(s.artworkUrl, sizeDp = 104, cornerDp = 10)
                    Text(s.title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${s.bpm.roundToInt()} BPM · ${s.artist}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        Spacer(Modifier.height(2.dp))
    }
}

/** Tempo range (collapsible so more tracks fit above the fold), half/double, sort, Loved, genre and decade. */
@Composable
private fun Filters(query: SongQuery, signedIn: Boolean, facets: Facets, onChange: (SongQuery) -> Unit) {
    var sortOpen by remember { mutableStateOf(false) }
    var genreOpen by remember { mutableStateOf(false) }
    var decadeOpen by remember { mutableStateOf(false) }
    var sliderExpanded by remember { mutableStateOf(false) }
    val ranged = query.bpmMin != null && query.bpmMax != null

    Row(
        Modifier
            .horizontalScroll(rememberScrollState())
            .padding(start = 18.dp, end = 18.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        LogFilterPill(
            label = if (ranged) "${query.bpmMin}–${query.bpmMax} BPM ▾" else "Any tempo ▾",
            selected = ranged,
            activeColor = MaterialTheme.colorScheme.primary,
            onClick = {
                if (!ranged) {
                    onChange(query.copy(bpmMin = 120, bpmMax = 132))
                    sliderExpanded = true
                } else {
                    sliderExpanded = !sliderExpanded
                }
            },
        )
        if (ranged) {
            LogFilterPill(
                label = "½× / 2×",
                selected = query.halfDouble,
                activeColor = MaterialTheme.colorScheme.primary,
                onClick = { onChange(query.copy(halfDouble = !query.halfDouble)) },
            )
        }
        Box {
            LogFilterPill(
                label = "${query.sort.label} ▾",
                selected = false,
                activeColor = MaterialTheme.colorScheme.primary,
                onClick = { sortOpen = true },
            )
            DropdownMenu(expanded = sortOpen, onDismissRequest = { sortOpen = false }) {
                SongSort.entries.forEach { s ->
                    DropdownMenuItem(text = { Text(s.label) }, onClick = { sortOpen = false; onChange(query.copy(sort = s)) })
                }
            }
        }
        if (facets.genres.isNotEmpty() || query.genre != null) {
            Box {
                LogFilterPill(
                    label = "${query.genre ?: "Genre"} ▾",
                    selected = query.genre != null,
                    activeColor = MaterialTheme.colorScheme.primary,
                    onClick = { genreOpen = true },
                )
                DropdownMenu(expanded = genreOpen, onDismissRequest = { genreOpen = false }) {
                    DropdownMenuItem(text = { Text("Any genre") }, onClick = { genreOpen = false; onChange(query.copy(genre = null)) })
                    facets.genres.forEach { g ->
                        DropdownMenuItem(text = { Text("${g.genre} · ${g.songs}") }, onClick = { genreOpen = false; onChange(query.copy(genre = g.genre)) })
                    }
                }
            }
        }
        if (facets.decades.isNotEmpty() || query.decade != null) {
            Box {
                LogFilterPill(
                    label = "${query.decade?.let { "${it}s" } ?: "Decade"} ▾",
                    selected = query.decade != null,
                    activeColor = MaterialTheme.colorScheme.primary,
                    onClick = { decadeOpen = true },
                )
                DropdownMenu(expanded = decadeOpen, onDismissRequest = { decadeOpen = false }) {
                    DropdownMenuItem(text = { Text("Any decade") }, onClick = { decadeOpen = false; onChange(query.copy(decade = null)) })
                    facets.decades.forEach { d ->
                        DropdownMenuItem(text = { Text("${d.decade}s · ${d.songs}") }, onClick = { decadeOpen = false; onChange(query.copy(decade = d.decade)) })
                    }
                }
            }
        }
        if (signedIn) {
            LogFilterPill(
                label = "♥ Loved",
                selected = query.lovedOnly,
                activeColor = Brand.Coral,
                onClick = { onChange(query.copy(lovedOnly = !query.lovedOnly)) },
            )
        }
    }

    AnimatedVisibility(ranged && sliderExpanded) {
        val loVal = (query.bpmMin ?: 120).toFloat()
        val hiVal = (query.bpmMax ?: 132).toFloat()
        Column(
            Modifier
                .fillMaxWidth()
                .padding(start = 18.dp, end = 18.dp, bottom = 8.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Filter Tempo: ${query.bpmMin}–${query.bpmMax} BPM",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Box(
                    Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .clickable {
                            sliderExpanded = false
                            onChange(query.copy(bpmMin = null, bpmMax = null))
                        }
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                ) {
                    Text("Any tempo", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            RangeSlider(
                value = loVal..hiVal,
                onValueChange = { r ->
                    val lo = r.start.roundToInt()
                    val hi = r.endInclusive.roundToInt().coerceAtLeast(lo)
                    if (lo != query.bpmMin || hi != query.bpmMax) onChange(query.copy(bpmMin = lo, bpmMax = hi))
                },
                valueRange = Settings.BPM_FLOOR.toFloat()..Settings.BPM_CEILING.toFloat(),
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
            )
        }
    }
}

/** Artwork-first on the left, track metadata in the middle, crisp squircle BPM badge on the right. */
@Composable
internal fun SongArtwork(artworkUrl: String?, sizeDp: Int, cornerDp: Int) {
    if (artworkUrl != null) {
        AsyncImage(
            model = artworkUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(sizeDp.dp)
                .clip(RoundedCornerShape(cornerDp.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        )
    } else {
        Box(
            Modifier
                .size(sizeDp.dp)
                .clip(RoundedCornerShape(cornerDp.dp))
                .background(
                    Brush.linearGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                            Brand.Coral.copy(alpha = 0.20f),
                        ),
                    ),
                )
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(cornerDp.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(R.drawable.ic_library),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size((sizeDp * 0.42f).dp),
            )
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
        Icon(painterResource(R.drawable.ic_library), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(14.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        if (retry != null) {
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = retry) { Text("Try again") }
        }
    }
}
