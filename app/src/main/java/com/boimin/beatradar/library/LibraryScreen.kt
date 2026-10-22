package com.boimin.beatradar.library

import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.em
import androidx.compose.foundation.text.TextAutoSize
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.boimin.beatradar.R
import com.boimin.beatradar.data.Settings
import com.boimin.beatradar.i18n.str
import com.boimin.beatradar.i18n.strPlural
import com.boimin.beatradar.net.Facets
import com.boimin.beatradar.net.SongSummary
import com.boimin.beatradar.ui.BpmBadge
import com.boimin.beatradar.ui.LogFilterPill
import com.boimin.beatradar.ui.CalmBottomSheet
import com.boimin.beatradar.ui.TEMPO_PRESETS
import com.boimin.beatradar.ui.TempoRangeEditor
import com.boimin.beatradar.ui.tempoRangeLabel
import com.boimin.beatradar.ui.tempoRangeShort
import com.boimin.beatradar.ui.rememberCalmSheetState
import com.boimin.beatradar.ui.theme.Brand
import com.boimin.beatradar.ui.theme.Tone
import com.boimin.beatradar.ui.theme.brandSwitchColors
import kotlin.math.roundToInt
import com.boimin.beatradar.ui.SkeletonSongList

/** What the Library needs from the rest of the app. */
class LibraryActions(
    val signedIn: Boolean,
    /** Opens the sign-in sheet; [then] runs once signed in (e.g. to finish a love). */
    val requireSignIn: (then: () -> Unit) -> Unit,
    val share: (SongSummary) -> Unit,
    val openUrl: (String) -> Unit,
    /** The share sheet with plain text (e.g. a Set's song list). */
    val shareText: (String) -> Unit,
    /** Changes a setting (e.g. the app sets play in). */
    val updateSettings: ((Settings) -> Settings) -> Unit = {},
)

@Composable
fun LibraryScreen(vm: LibraryViewModel, settings: Settings, actions: LibraryActions, modifier: Modifier = Modifier) {
    val detail by vm.detail.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        // Starts at the user's own target range, like the Listen screen.
        if (!vm.list.value.loadedOnce && settings.rangeEnabled) {
            vm.query.value = vm.query.value.copy(bpmMin = settings.targetLow, bpmMax = settings.targetHigh, halfDouble = settings.halfDoubleMatch)
        }
        // Discover shows live counts and loves: fresh data every time the tab is opened.
        vm.refresh()
    }
    val d = detail
    if (d != null) {
        BackHandler { vm.close() }
        SongDetailScreen(d, vm, actions, settings, modifier)
    } else {
        SongList(vm, settings, actions, modifier)
    }
}

@Composable
private fun SongList(vm: LibraryViewModel, settings: Settings, actions: LibraryActions, modifier: Modifier) {
    val list by vm.list.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val extras by vm.extras.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val context = LocalContext.current
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
    // A new search or filter starts a new list: from its top. (Not when the tab opens: the list keeps its place.)
    var shownQuery by remember { mutableStateOf(query) }
    LaunchedEffect(query) {
        if (query != shownQuery) {
            shownQuery = query
            listState.scrollToItem(0)
        }
    }
    val trending = extras.trending?.takeIf { query.text.isBlank() && !query.lovedOnly && it.items.isNotEmpty() }
    // Trending now lands with the songs. On a refresh (the tab opened again) it can appear above songs already on screen,
    // and the list keeps its place on the first song: while that list was at its very top, show the row.
    LaunchedEffect(trending != null) {
        if (trending != null && listState.firstVisibleItemIndex == 1 && listState.firstVisibleItemScrollOffset == 0) listState.scrollToItem(0)
    }

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
                        str(R.string.library_search_hint),
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

        // The selection bar covers the filter pills in their own space, so the list below does not move.
        Filters(
            query,
            settings,
            actions.signedIn,
            extras.facets,
            onChange = { vm.query.value = it },
            selectionBar = if (!selecting) null else { barModifier ->
                SelectionBar(selected.size, onCancel = { selected = emptyMap() }, modifier = barModifier) {
                    SelectionAction(str(R.string.sets_add_to_set)) { adding = true }
                }
            },
        )
        if (adding) {
            AddManyToSetSheet(
                songs = selected.values.toList(),
                vm = vm,
                onDone = { name ->
                    vm.say(context.strPlural(R.plurals.library_added_to_set, selected.size, selected.size, name))
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
                    str(R.string.library_sign_in_prompt),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        when {
            list.items.isEmpty() && list.loading -> SkeletonSongList(8, Modifier.padding(start = 18.dp, end = 18.dp, top = 6.dp))
            list.items.isEmpty() && list.error != null -> Message(
                if (list.error == str(R.string.library_offline)) str(R.string.library_offline) else str(R.string.library_load_failed),
                list.error.takeIf { it != str(R.string.library_offline) } ?: str(R.string.library_offline_body),
                retry = vm::reload,
            )
            list.items.isEmpty() && list.loadedOnce -> Message(
                str(R.string.library_empty_title),
                if (query.text.isNotBlank() || query.bpmMin != null || query.bpmMax != null || query.lovedOnly) str(R.string.library_empty_filtered) else str(R.string.library_empty_body),
            )
            else -> LazyColumn(
                state = listState,
                contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 6.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (trending != null) {
                    item(key = "trending") {
                        TrendingRow(
                            title = if (trending.fallback) str(R.string.library_most_confirmed_here) else str(R.string.library_trending_here),
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
                if (list.loading) item { SkeletonSongList(2) }
                if (list.error != null) item { TextButton(onClick = vm::loadMore, modifier = Modifier.fillMaxWidth()) { Text(str(R.string.library_error_retry, list.error)) } }
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
                    Text(str(R.string.library_bpm_artist, s.bpm.roundToInt(), s.artist), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        Spacer(Modifier.height(2.dp))
    }
}

/**
 * One row of pills: tempo (opens [TempoSheet]), sort, genre, decade and Loved.
 * [selectionBar], when set, is drawn over the row (sized to it by the modifier it is given).
 */
@Composable
private fun Filters(
    query: SongQuery,
    settings: Settings,
    signedIn: Boolean,
    facets: Facets,
    onChange: (SongQuery) -> Unit,
    selectionBar: (@Composable (Modifier) -> Unit)? = null,
) {
    var sortOpen by remember { mutableStateOf(false) }
    var genreOpen by remember { mutableStateOf(false) }
    var decadeOpen by remember { mutableStateOf(false) }
    var tempoOpen by remember { mutableStateOf(false) }
    val ranged = query.bpmMin != null || query.bpmMax != null

    Box(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .alpha(if (selectionBar != null) 0f else 1f)
                .horizontalScroll(rememberScrollState())
                .padding(start = 18.dp, end = 18.dp, bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            LogFilterPill(
                label = if (ranged && query.halfDouble) "${tempoRangeLabel(query.bpmMin, query.bpmMax)} · ½×/2× ▾" else "${tempoRangeLabel(query.bpmMin, query.bpmMax)} ▾",
                selected = ranged,
                activeColor = MaterialTheme.colorScheme.primary,
                onClick = { tempoOpen = true },
            )
            Box {
                LogFilterPill(
                    label = "${str(query.sort.label)} ▾",
                    selected = false,
                    activeColor = MaterialTheme.colorScheme.primary,
                    onClick = { sortOpen = true },
                )
                DropdownMenu(expanded = sortOpen, onDismissRequest = { sortOpen = false }) {
                    SongSort.entries.forEach { s ->
                        DropdownMenuItem(text = { Text(str(s.label)) }, onClick = { sortOpen = false; onChange(query.copy(sort = s)) })
                    }
                }
            }
            if (facets.genres.isNotEmpty() || query.genre != null) {
                Box {
                    LogFilterPill(
                        label = "${query.genre ?: str(R.string.library_genre)} ▾",
                        selected = query.genre != null,
                        activeColor = MaterialTheme.colorScheme.primary,
                        onClick = { genreOpen = true },
                    )
                    DropdownMenu(expanded = genreOpen, onDismissRequest = { genreOpen = false }) {
                        DropdownMenuItem(text = { Text(str(R.string.library_any_genre)) }, onClick = { genreOpen = false; onChange(query.copy(genre = null)) })
                        facets.genres.forEach { g ->
                            DropdownMenuItem(text = { Text("${g.genre} · ${g.songs}") }, onClick = { genreOpen = false; onChange(query.copy(genre = g.genre)) })
                        }
                    }
                }
            }
            if (facets.decades.isNotEmpty() || query.decade != null) {
                Box {
                    LogFilterPill(
                        label = "${query.decade?.let { str(R.string.library_decade_value, it) } ?: str(R.string.library_decade)} ▾",
                        selected = query.decade != null,
                        activeColor = MaterialTheme.colorScheme.primary,
                        onClick = { decadeOpen = true },
                    )
                    DropdownMenu(expanded = decadeOpen, onDismissRequest = { decadeOpen = false }) {
                        DropdownMenuItem(text = { Text(str(R.string.library_any_decade)) }, onClick = { decadeOpen = false; onChange(query.copy(decade = null)) })
                        facets.decades.forEach { d ->
                            DropdownMenuItem(text = { Text("${str(R.string.library_decade_value, d.decade)} · ${d.songs}") }, onClick = { decadeOpen = false; onChange(query.copy(decade = d.decade)) })
                        }
                    }
                }
            }
            if (signedIn) {
                LogFilterPill(
                    label = str(R.string.library_loved),
                    selected = query.lovedOnly,
                    activeColor = Brand.Coral,
                    onClick = { onChange(query.copy(lovedOnly = !query.lovedOnly)) },
                )
            }
        }
        selectionBar?.invoke(Modifier.matchParentSize().padding(start = 18.dp, end = 18.dp, bottom = 6.dp))
    }

    if (tempoOpen) TempoSheet(query, settings, onChange, onDismiss = { tempoOpen = false })
}

/**
 * The tempo filter: Any / Between / At least / Up to with steppers, presets (the user's own target first) and
 * half/double. Changes apply as they are made (the list reloads after a short pause); swipe down, tap outside or Done
 * closes it.
 */
@Composable
private fun TempoSheet(query: SongQuery, settings: Settings, onChange: (SongQuery) -> Unit, onDismiss: () -> Unit) {
    val lo = query.bpmMin
    val hi = query.bpmMax
    fun set(a: Int?, b: Int?) = onChange(query.copy(bpmMin = a, bpmMax = b))
    CalmBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberCalmSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = Tone.sheet,
        // Tone.sheet isn't a theme colour, so untinted text would not follow dark mode without this.
        contentColor = MaterialTheme.colorScheme.onSurface,
        dragHandle = {
            Box(Modifier.padding(top = 10.dp, bottom = 16.dp).size(36.dp, 4.dp).clip(RoundedCornerShape(2.dp)).background(Tone.sheetLine))
        },
    ) {
        // A short screen or an open keyboard leaves less room than the sheet needs: it scrolls rather than squeezing
        // fixed-height parts (Done, the steppers, the name field) out of shape.
        Column(Modifier.imePadding().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 28.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(str(R.string.library_tempo_title), fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(
                    tempoRangeLabel(lo, hi),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (lo != null || hi != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(16.dp))
            TempoRangeEditor(
                low = lo,
                high = hi,
                fallbackLow = settings.minBpm,
                fallbackHigh = settings.maxBpm,
                allowAny = true,
                onChange = ::set,
            )
            Spacer(Modifier.height(16.dp))
            val mine = Triple(str(R.string.library_tempo_my_range), settings.targetLow, settings.targetHigh)
            val presets = listOf(mine) + TEMPO_PRESETS
                .filter { it.min != settings.targetLow || it.max != settings.targetHigh }
                .map { Triple(str(it.label), it.min, it.max) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                presets.forEach { (name, a, b) ->
                    TempoPresetTile(name, tempoRangeShort(a, b), selected = lo == a && hi == b, Modifier.weight(1f)) { set(a, b) }
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onChange(query.copy(halfDouble = !query.halfDouble)) }
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(str(R.string.settings_half_double_title), fontSize = 15.sp)
                    Text(str(R.string.settings_half_double_desc), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked = query.halfDouble,
                    onCheckedChange = { on -> onChange(query.copy(halfDouble = on)) },
                    colors = brandSwitchColors(),
                )
            }
            Spacer(Modifier.height(16.dp))
            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(str(R.string.library_done)) }
        }
    }
}

/** A preset in [TempoSheet]: name over range, like the Listen screen's target presets. */
@Composable
private fun TempoPresetTile(name: String, range: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier
            .height(52.dp)
            .clip(shape)
            .background(if (selected) Tone.selected else Tone.track)
            .border(1.dp, if (selected) MaterialTheme.colorScheme.primary else Color.Transparent, shape)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            name,
            lineHeight = 1.15.em,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            autoSize = TextAutoSize.StepBased(minFontSize = 9.sp, maxFontSize = 13.sp),
        )
        Text(range, fontSize = 11.sp, lineHeight = 13.sp, color = if (selected) Tone.text2 else MaterialTheme.colorScheme.onSurfaceVariant)
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
            TextButton(onClick = retry) { Text(str(R.string.library_try_again)) }
        }
    }
}
