package com.boimin.beatradar.library

import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import com.boimin.beatradar.ui.BpmValue
import com.boimin.beatradar.ui.CalmBottomSheet
import com.boimin.beatradar.ui.rememberCalmSheetState
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.unit.em
import androidx.compose.foundation.text.TextAutoSize
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.boimin.beatradar.R
import com.boimin.beatradar.data.SetRules
import com.boimin.beatradar.data.SetSong
import com.boimin.beatradar.data.SetSummary
import com.boimin.beatradar.data.Settings
import com.boimin.beatradar.i18n.str
import com.boimin.beatradar.i18n.strPlural
import com.boimin.beatradar.net.SongSummary
import com.boimin.beatradar.ui.BpmBadge
import com.boimin.beatradar.ui.theme.Brand
import kotlinx.coroutines.launch
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import com.boimin.beatradar.ui.tempoRangeShort
import com.boimin.beatradar.ui.tempoRangeLabel

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
    val artwork by vm.setArtwork.collectAsStateWithLifecycle()
    val fromLog by remember(settings) { vm.logSuggestion(settings) }.collectAsStateWithLifecycle(emptyList())
    // "Not now" hides the log card until the app is opened again.
    var logLater by rememberSaveable { mutableStateOf(false) }

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
                    str(R.string.sets_intro),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SetsAction(R.drawable.ic_set_add, str(R.string.sets_new_set), primary = true, Modifier.weight(1f)) { creating = true }
                    SetsAction(R.drawable.ic_queue, str(R.string.sets_build_me_a_set), primary = false, Modifier.weight(1f)) { vm.openBuild() }
                    SetsAction(R.drawable.ic_library_music, str(R.string.playlist_title), primary = false, Modifier.weight(1f)) { vm.openPlaylist() }
                }
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
                    Text(str(R.string.sets_empty_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        str(R.string.sets_empty_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (fromLog.size >= LOG_CARD_MIN && !logLater) {
            item(key = "from-log") {
                val name = str(R.string.sets_from_log_name, tempoRangeShort(settings.targetLow, settings.targetHigh))
                FromLogCard(
                    songs = fromLog,
                    range = tempoRangeLabel(settings.targetLow, settings.targetHigh),
                    onMake = { scope.launch { vm.createSetWith(name, fromLog)?.let { vm.openSetId.value = it } } },
                    onLater = { logLater = true },
                )
            }
        }
        fun playFromCard(set: SetSummary) {
            scope.launch { playSet(vm, actions, settings.playApp, vm.setSongsNow(set.id), from = 0) }
        }
        sets.firstOrNull()?.let { latest ->
            item(key = "latest-label") { SectionLabel(str(R.string.sets_latest)) }
            item(key = latest.id) {
                LatestSetCard(latest, artwork[latest.id].orEmpty(), playAppName(settings.playApp), onClick = { vm.openSetId.value = latest.id }, onPlay = { playFromCard(latest) })
            }
        }
        if (sets.size > 1) {
            item(key = "more-label") { SectionLabel(str(R.string.sets_more_sets)) }
            items(sets.drop(1), key = { it.id }) { set ->
                SetCard(set, artwork[set.id].orEmpty(), onClick = { vm.openSetId.value = set.id }, onPlay = { playFromCard(set) })
            }
        }
    }

    if (creating) {
        NameSheet(
            title = str(R.string.sets_new_set),
            body = str(R.string.sets_new_set_body),
            initial = "",
            confirm = str(R.string.sets_create),
            suggestions = setNameSuggestions(settings),
            onDismiss = { creating = false },
            onSave = { name ->
                creating = false
                scope.launch { vm.createSet(name)?.let { vm.openSetId.value = it } }
            },
        )
    }
}

/** Sets other than the latest: the cover, name, songs · tempo span · length, and Play. */
@Composable
private fun SetCard(set: SetSummary, covers: List<String>, onClick: () -> Unit, onPlay: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip16()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SetCover(covers, sizeDp = 52, cornerDp = 12)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(set.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(setFacts(set), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (set.songs > 0) {
            Spacer(Modifier.width(8.dp))
            PlayButton(str(R.string.sets_play_song, set.name), onPlay)
        }
    }
}

/** The set changed most recently, large: a bigger cover, its facts, and a full-width Play. */
@Composable
private fun LatestSetCard(set: SetSummary, covers: List<String>, appName: String, onClick: () -> Unit, onPlay: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape)
            .clickable(onClick = onClick)
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SetCover(covers, sizeDp = 88, cornerDp = 14)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(set.name, fontSize = 19.sp, lineHeight = 23.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                Text(setFacts(set), fontSize = 13.sp, lineHeight = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (set.songs > 0) {
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(MaterialTheme.colorScheme.primary)
                    .clickable(onClick = onPlay),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(painterResource(R.drawable.ic_play), contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(6.dp))
                Text(str(R.string.sets_play_in, appName), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
            }
        }
    }
}

/**
 * "From your log": songs heard this past week in the target range that are in no set yet. One tap makes them a set;
 * "Not now" hides the card until the app opens again.
 */
@Composable
private fun FromLogCard(songs: List<SongSummary>, range: String, onMake: () -> Unit, onLater: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f).compositeOver(MaterialTheme.colorScheme.surfaceContainer))
            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f), shape)
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(R.drawable.ic_history), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(5.dp))
            Text(str(R.string.sets_from_log_label), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp, color = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(8.dp))
        Text(strPlural(R.plurals.sets_from_log_title, songs.size, songs.size, range), fontSize = 15.sp, lineHeight = 19.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text(
            songs.take(3).joinToString(" · ") { it.title },
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = onMake, modifier = Modifier.height(40.dp), contentPadding = PaddingValues(horizontal = 18.dp)) {
                Text(str(R.string.sets_from_log_make))
            }
            Spacer(Modifier.width(4.dp))
            TextButton(onClick = onLater) { Text(str(R.string.sets_from_log_later)) }
        }
    }
}

/** A set's cover: four song covers in a square, one cover when it has fewer, or the set icon before any. */
@Composable
private fun SetCover(covers: List<String>, sizeDp: Int, cornerDp: Int) {
    when {
        covers.size >= 4 -> Column(Modifier.size(sizeDp.dp).clip(RoundedCornerShape(cornerDp.dp))) {
            covers.take(4).chunked(2).forEach { row ->
                Row { row.forEach { SongArtwork(it, sizeDp = sizeDp / 2, cornerDp = 0) } }
            }
        }
        covers.isNotEmpty() -> SongArtwork(covers.first(), sizeDp = sizeDp, cornerDp = cornerDp)
        else -> Box(
            Modifier.size(sizeDp.dp).clip(RoundedCornerShape(cornerDp.dp)).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(R.drawable.ic_list), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size((sizeDp * 0.42f).dp))
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.8.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp),
    )
}

/** "12 songs · 120–132 BPM · 48 min" (the length once known; "about" while some songs' lengths aren't). */
@Composable
private fun setFacts(set: SetSummary): String = listOfNotNull(
    strPlural(R.plurals.sets_song_count, set.songs, set.songs),
    bpmSpan(set.minBpm, set.maxBpm).takeIf { set.songs > 0 },
    (set.knownMs / 60_000).takeIf { it > 0 }?.let { str(if (set.allTimed) R.string.sets_minutes else R.string.sets_about_minutes, it.toInt()) },
).joinToString(" · ")

private fun playAppName(playApp: String) = if (playApp == "spotify") "Spotify" else "YouTube"

/**
 * Plays [songs] from [from] in the app sets play in: YouTube as one queue to the end of the set, Spotify a song at a
 * time (its own page when the Library knows it, else a search).
 */
private suspend fun playSet(vm: LibraryViewModel, actions: LibraryActions, playApp: String, songs: List<SetSong>, from: Int) {
    val song = songs.getOrNull(from) ?: return
    if (playApp == "spotify") {
        actions.openUrl(vm.playUrl(song.slug, song.title, song.artist, PlayLinks.Service.SPOTIFY))
    } else {
        val q = vm.youtubeQueue(songs.drop(from).map { it.slug })
        actions.openUrl(
            q?.url ?: ("https://www.youtube.com/results?search_query=" + java.net.URLEncoder.encode("${song.artist} ${song.title}", "UTF-8")),
        )
    }
}

/** "From your log" shows once this many songs are waiting. */
private const val LOG_CARD_MIN = 3
/** Below this width a set page's Change button is just its arrow, so "Play set in YouTube" keeps its words. */
private val NARROW_PLAY_ROW = 290.dp

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
        scope.launch { playSet(vm, actions, settings.playApp, songs, from) }
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
                // The bar is as tall as on Discover; it sits in the 52 dp of the top bar it replaces, so the page does not move.
                Box(Modifier.fillMaxWidth().padding(horizontal = 18.dp).height(52.dp), contentAlignment = Alignment.Center) {
                    SelectionBar(selected.size, onCancel = { selected = emptySet() }, modifier = Modifier.height(SelectionBarHeight)) {
                        BarIcon(R.drawable.ic_delete, str(R.string.sets_remove_selected), 22, MaterialTheme.colorScheme.error) {
                            vm.removeSongs(set.id, songs.filter { it.slug in selected })
                            selected = emptySet()
                        }
                    }
                }
            } else {
                // Design: 44 dp touch targets, back at 24 dp, share and ⋮ at 22 dp in the softer text colour.
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp).height(52.dp), verticalAlignment = Alignment.CenterVertically) {
                    BarIcon(R.drawable.ic_arrow_back, str(R.string.sets_my_sets), 24, MaterialTheme.colorScheme.onSurface, onBack)
                    Spacer(Modifier.weight(1f))
                    BarIcon(R.drawable.ic_share, str(R.string.library_share), 22, Tone.text2) { scope.launch { vm.shareSet(set.id)?.let(actions.shareText) } }
                    Spacer(Modifier.width(4.dp))
                    Box {
                        BarIcon(R.drawable.ic_more, str(R.string.library_more), 22, Tone.text2) { menu = true }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text(str(R.string.sets_rename)) }, onClick = { menu = false; renaming = true })
                            if (actions.signedIn) {
                                DropdownMenuItem(text = { Text(str(R.string.sets_show_on_profile)) }, onClick = { menu = false; vm.showSetOnProfile(set.id) })
                            }
                            DropdownMenuItem(text = { Text(str(R.string.sets_delete_set), color = MaterialTheme.colorScheme.error) }, onClick = { menu = false; deleting = true })
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
                        strPlural(R.plurals.sets_song_count, songs.size, songs.size),
                        (totalMs / 60_000).takeIf { it > 0 }?.let { str(if (allTimed) R.string.sets_minutes else R.string.sets_about_minutes, it.toInt()) },
                        bpmSpan(set.minBpm, set.maxBpm).takeIf { songs.isNotEmpty() },
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
                            if (inTarget == songs.size) str(R.string.sets_all_in_target, tempoRangeShort(settings.targetLow, settings.targetHigh))
                            else str(R.string.sets_some_in_target, inTarget, songs.size, tempoRangeShort(settings.targetLow, settings.targetHigh)),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (inTarget > 0) inTargetColor() else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (songs.isNotEmpty()) {
                    Spacer(Modifier.height(20.dp))
                    // Play takes what Change leaves: on a 320 dp phone its label shrinks instead of squeezing Change (the
                    // only way to pick Spotify) down to an empty pill.
                    BoxWithConstraints {
                        val narrow = maxWidth < NARROW_PLAY_ROW
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Row(
                                Modifier
                                    .weight(1f)
                                    .height(52.dp)
                                    .clip(RoundedCornerShape(26.dp))
                                    .background(MaterialTheme.colorScheme.primary)
                                    .clickable { play(0) }
                                    .padding(horizontal = 18.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(painterResource(R.drawable.ic_play), contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    str(R.string.sets_play_in, appName),
                                    fontWeight = FontWeight.Medium,
                                    color = Color.White,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis,
                                    autoSize = TextAutoSize.StepBased(minFontSize = 11.sp, maxFontSize = 16.sp),
                                )
                            }
                            Box {
                                Row(
                                    Modifier
                                        .height(54.dp)
                                        .clip(RoundedCornerShape(26.dp))
                                        .border(1.dp, Tone.line, RoundedCornerShape(26.dp))
                                        .clickable { choosingApp = true }
                                        .padding(horizontal = if (narrow) 15.dp else 16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    if (!narrow) {
                                        Text(str(R.string.sets_change), fontSize = 14.sp, color = Tone.text2, maxLines = 1, softWrap = false)
                                        Spacer(Modifier.width(4.dp))
                                    }
                                    Icon(
                                        painterResource(R.drawable.ic_expand_more),
                                        contentDescription = if (narrow) str(R.string.sets_change) else null,
                                        tint = Tone.text2,
                                        modifier = Modifier.size(if (narrow) 22.dp else 18.dp),
                                    )
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
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
        if (songs.isEmpty()) {
            item {
                Text(
                    str(R.string.sets_empty_set),
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
                    str(R.string.sets_swipe_hint),
                    fontSize = 13.sp,
                    color = Tone.text3,
                    modifier = Modifier.padding(start = 22.dp, end = 22.dp, top = 14.dp),
                )
            }
        }
    }

    if (renaming) {
        NameSheet(
            title = str(R.string.sets_rename_title),
            body = null,
            initial = set.name,
            confirm = str(R.string.sets_save),
            suggestions = emptyList(),
            onDismiss = { renaming = false },
            onSave = { renaming = false; vm.renameSet(set.id, it) },
        )
    }
    if (deleting) {
        AlertDialog(
            onDismissRequest = { deleting = false },
            title = { Text(str(R.string.sets_delete_title, set.name)) },
            text = { Text(str(R.string.sets_delete_body)) },
            confirmButton = { TextButton(onClick = { deleting = false; vm.deleteSet(set.id); onBack() }) { Text(str(R.string.sets_delete), color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { deleting = false }) { Text(str(R.string.library_cancel)) } },
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
                PlayButton(str(R.string.sets_play_song, song.title), onPlay)
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

    CalmBottomSheet(onDismissRequest = onDismiss, sheetState = rememberCalmSheetState()) {
        Column(Modifier.padding(start = 24.dp, end = 24.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(str(R.string.sets_add_to_set), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(
                "${song.title} · ${str(R.string.library_bpm_value, Math.round(song.bpm))}",
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
                            "${strPlural(R.plurals.sets_song_count, set.songs, set.songs)} · ${bpmSpan(set.minBpm, set.maxBpm)}",
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
                    label = { Text(if (sets.isEmpty()) str(R.string.sets_name_first_set) else str(R.string.sets_new_set)) },
                    placeholder = { Text(str(R.string.sets_name_example)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = { scope.launch { vm.createSet(newName, song) }; newName = "" },
                    enabled = SetRules.cleanName(newName) != null,
                ) { Text(str(R.string.sets_create)) }
            }
        }
    }
}

/** Names to start from for a new set: the target range ("160–170 BPM"), then a few common uses. */
@Composable
private fun setNameSuggestions(settings: Settings): List<String> = listOf(
    "${tempoRangeShort(settings.targetLow, settings.targetHigh)} BPM",
    str(R.string.sets_suggest_morning_run),
    str(R.string.sets_suggest_workout),
    str(R.string.sets_suggest_warm_up),
    str(R.string.sets_suggest_dj_set),
)

/**
 * Naming a set (new or rename) in a bottom sheet: the field has the keyboard up from the start, names to tap in, a
 * character count, and Create / Save as the big button. The keyboard's Done saves too.
 */
@Composable
private fun NameSheet(
    title: String,
    body: String?,
    initial: String,
    confirm: String,
    suggestions: List<String>,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var name by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue(initial, selection = TextRange(0, initial.length))) }
    val valid = SetRules.cleanName(name.text) != null
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
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
        Column(Modifier.imePadding().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(Tone.selected),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(painterResource(R.drawable.ic_set_add), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(12.dp))
                Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            if (body != null) {
                Spacer(Modifier.height(10.dp))
                Text(body, fontSize = 13.sp, lineHeight = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = name,
                onValueChange = { name = if (it.text.length <= SetRules.MAX_NAME) it else it.copy(text = it.text.take(SetRules.MAX_NAME)) },
                placeholder = { Text(str(R.string.sets_name_example_range)) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                supportingText = {
                    Text("${name.text.length} / ${SetRules.MAX_NAME}", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.End)
                },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (valid) onSave(name.text) }),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
            if (suggestions.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    suggestions.forEach { suggestion ->
                        val picked = name.text == suggestion
                        Box(
                            Modifier
                                .height(32.dp)
                                .clip(RoundedCornerShape(50))
                                .background(if (picked) Tone.selected else Tone.track)
                                .border(1.dp, if (picked) MaterialTheme.colorScheme.primary else Color.Transparent, RoundedCornerShape(50))
                                .clickable { name = TextFieldValue(suggestion, selection = TextRange(suggestion.length)) }
                                .padding(horizontal = 12.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                suggestion,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (picked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f).height(48.dp)) { Text(str(R.string.library_cancel)) }
                Button(onClick = { onSave(name.text) }, enabled = valid, modifier = Modifier.weight(1f).height(48.dp)) { Text(confirm) }
            }
        }
    }
}

private fun Modifier.clip16() = this.then(Modifier.clip(RoundedCornerShape(16.dp)))

private fun Modifier.clip12() = this.then(Modifier.clip(RoundedCornerShape(12.dp)))

/** One of the three ways to start a set on My sets: equal tiles, icon over a label of up to two lines. */
@Composable
private fun SetsAction(icon: Int, label: String, primary: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    val fg = if (primary) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary
    Column(
        modifier
            .height(76.dp)
            .clip(shape)
            .background(if (primary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainer)
            .border(1.dp, if (primary) Color.Transparent else MaterialTheme.colorScheme.outline, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = fg, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(4.dp))
        // Three tiles across a 320 dp screen with a large font: the label shrinks to keep both lines.
        Text(
            label,
            lineHeight = 1.17.em,
            fontWeight = FontWeight.SemiBold,
            color = if (primary) fg else MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            autoSize = TextAutoSize.StepBased(minFontSize = 9.sp, maxFontSize = 12.sp),
        )
    }
}
