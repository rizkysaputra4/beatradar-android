package com.boimin.beatradar.ui

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import com.boimin.beatradar.library.SongArtwork
import com.boimin.beatradar.ui.theme.Tone
import com.boimin.beatradar.ui.theme.inTargetColor
import android.text.format.DateUtils
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.boimin.beatradar.data.Settings
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.boimin.beatradar.R
import com.boimin.beatradar.i18n.str
import com.boimin.beatradar.i18n.strPlural
import com.boimin.beatradar.data.BeatLogEntry
import com.boimin.beatradar.ui.theme.Brand
import kotlin.math.roundToInt

@Composable
fun LogScreen(
    entries: List<BeatLogEntry>,
    query: String,
    onQueryChange: (String) -> Unit,
    /** The BPM filter is on. */
    onlyInRange: Boolean,
    /** The BPM filter's range: the log's own, or the Listen tab's target until it's changed here. */
    bpmRange: IntRange,
    /** The log has its own range (not following the Listen tab). */
    bpmCustom: Boolean,
    onBpmRange: (IntRange) -> Unit,
    onResetBpm: () -> Unit,
    /** Whether a tempo is in the current target range (the coral badge). */
    inTarget: (Float) -> Boolean,
    onlyIdentified: Boolean,
    onToggleInRange: () -> Unit,
    onToggleIdentified: () -> Unit,
    unnamedWithClip: Int,
    /** Unnamed songs with a suggested name (PLAN §5c). */
    suggested: Int,
    onAcceptSuggestion: (BeatLogEntry) -> Unit,
    onRejectSuggestion: (BeatLogEntry) -> Unit,
    onlyUnnamed: Boolean,
    onToggleUnnamed: () -> Unit,
    onIdentify: (BeatLogEntry) -> Unit,
    onPlayClip: (BeatLogEntry) -> Unit,
    /** The clip playing now (its ▶ shows ■ to stop it). */
    playingClip: String? = null,
    onRename: (BeatLogEntry, String, String) -> Unit,
    /** Opens a song's Library page (entries the server knows). */
    onOpenSong: (String) -> Unit,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    onDelete: (BeatLogEntry) -> Unit,
    onClear: () -> Unit,
    /** Opens the weekly tempo recap. */
    onShowRecap: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var confirmClear by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<BeatLogEntry?>(null) }

    Column(modifier.fillMaxSize()) {
        // Low-profile Search Bar + Clear action
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 18.dp, end = 18.dp, top = 4.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                Modifier
                    .weight(1f)
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
                    if (query.isEmpty()) {
                        Text(
                            str(R.string.log_search_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = onQueryChange,
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurface),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            Box(
                Modifier
                    .height(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                    .clickable(onClickLabel = str(R.string.log_recap_action), onClick = onShowRecap)
                    .padding(horizontal = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(str(R.string.log_my_week), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
            }
            Box(
                Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                    .alpha(if (entries.isNotEmpty()) 1f else 0.45f)
                    .clickable(enabled = entries.isNotEmpty()) { confirmClear = true },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(R.drawable.ic_delete),
                    contentDescription = str(R.string.log_clear_action),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
            }
        }

        // Sleek Filter Pills
        Row(
            Modifier
                .horizontalScroll(rememberScrollState())
                .padding(start = 18.dp, end = 18.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            LogFilterPill(
                label = str(R.string.log_filter_bpm_range, bpmRange.first, bpmRange.last),
                selected = onlyInRange,
                activeColor = inTargetColor(),
                onClick = onToggleInRange,
            )
            LogFilterPill(
                label = str(R.string.log_filter_named),
                selected = onlyIdentified,
                activeColor = MaterialTheme.colorScheme.primary,
                onClick = onToggleIdentified,
            )
            if (unnamedWithClip > 0 || onlyUnnamed) {
                val badgeText = if (suggested > 0) {
                    str(R.string.log_filter_unnamed_match, unnamedWithClip, suggested)
                } else {
                    str(R.string.log_filter_unnamed, unnamedWithClip)
                }
                LogFilterPill(
                    label = badgeText,
                    selected = onlyUnnamed,
                    activeColor = MaterialTheme.colorScheme.primary,
                    onClick = onToggleUnnamed,
                )
            }
        }

        if (onlyInRange) BpmRangeRow(bpmRange, bpmCustom, onBpmRange, onResetBpm)

        // Pull down to sync: uploads, and brings in entries from the user's other phones when signed in.
        PullToRefreshBox(isRefreshing = refreshing, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) {
            if (entries.isEmpty()) {
                EmptyState(searching = query.isNotBlank() || onlyInRange || onlyIdentified || onlyUnnamed)
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(entries, key = { it.id }) { entry ->
                        SwipeToDelete(onDelete = { onDelete(entry) }) {
                            LogRow(
                                entry = entry,
                                inTarget = inTarget(entry.bpm),
                                onIdentify = { onIdentify(entry) },
                                clipPlaying = entry.clipPath != null && entry.clipPath == playingClip,
                                onPlayClip = { onPlayClip(entry) },
                                onRename = { renaming = entry },
                                onOpenSong = entry.songSlug?.let { slug -> { onOpenSong(slug) } },
                                onAccept = { onAcceptSuggestion(entry) },
                                onReject = { onRejectSuggestion(entry) },
                            )
                        }
                    }
                }
            }
        }
    }

    renaming?.let { picked ->
        // The live row, so the clip's ▶ / ■ follows playback while the sheet is open.
        val entry = entries.firstOrNull { it.id == picked.id } ?: picked
        RenameSheet(
            entry = entry,
            inTarget = inTarget(entry.bpm),
            clipPlaying = entry.clipPath != null && entry.clipPath == playingClip,
            onPlayClip = { onPlayClip(entry) },
            onIdentify = { renaming = null; onIdentify(entry) },
            onDismiss = { renaming = null },
            onSave = { title, artist -> renaming = null; onRename(entry, title, artist) },
        )
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(str(R.string.log_clear_title)) },
            text = { Text(strPlural(R.plurals.log_clear_text, entries.size, entries.size)) },
            confirmButton = {
                TextButton(onClick = { confirmClear = false; onClear() }) { Text(str(R.string.log_clear_confirm)) }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text(str(R.string.log_cancel)) } },
        )
    }
}

/** Min – Max boxes for the log's BPM filter. A range is applied once both numbers make sense. */
@Composable
private fun BpmRangeRow(range: IntRange, custom: Boolean, onChange: (IntRange) -> Unit, onReset: () -> Unit) {
    var minText by remember(range) { mutableStateOf(range.first.toString()) }
    var maxText by remember(range) { mutableStateOf(range.last.toString()) }
    fun apply(minT: String, maxT: String) {
        val lo = minT.toIntOrNull() ?: return
        val hi = maxT.toIntOrNull() ?: return
        if (lo in Settings.BPM_FLOOR..Settings.BPM_CEILING && hi in Settings.BPM_FLOOR..Settings.BPM_CEILING && lo <= hi && (lo..hi) != range) {
            onChange(lo..hi)
        }
    }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 18.dp, end = 18.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        BpmBox(minText, str(R.string.log_min_bpm)) { minText = it; apply(it, maxText) }
        Text("–", color = MaterialTheme.colorScheme.onSurfaceVariant)
        BpmBox(maxText, str(R.string.log_max_bpm)) { maxText = it; apply(minText, it) }
        Spacer(Modifier.weight(1f))
        if (custom) {
            Text(
                str(R.string.log_use_target),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onReset).padding(horizontal = 8.dp, vertical = 6.dp),
            )
        } else {
            Text(str(R.string.log_listen_target), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun BpmBox(text: String, label: String, onChange: (String) -> Unit) {
    Box(
        Modifier
            .width(76.dp)
            .height(38.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        BasicTextField(
            value = text,
            onValueChange = { v -> onChange(v.filter(Char::isDigit).take(3)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = label },
        )
    }
}

@Composable
internal fun LogFilterPill(
    label: String,
    selected: Boolean,
    activeColor: Color,
    onClick: () -> Unit,
) {
    val bg = if (selected) activeColor.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceContainer
    val border = if (selected) activeColor.copy(alpha = 0.4f) else MaterialTheme.colorScheme.outline
    val fg = if (selected) activeColor else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 5.dp),
    ) {
        Text(
            label,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = fg,
            maxLines = 1,
        )
    }
}

@Composable
private fun SwipeToDelete(onDelete: () -> Unit, content: @Composable () -> Unit) {
    val state = rememberSwipeToDismissBoxState()
    SwipeToDismissBox(
        state = state,
        enableDismissFromStartToEnd = false,
        onDismiss = { if (it == SwipeToDismissBoxValue.EndToStart) onDelete() },
        backgroundContent = {
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(painterResource(R.drawable.ic_delete), str(R.string.log_delete), tint = MaterialTheme.colorScheme.onErrorContainer)
            }
        },
    ) { content() }
}

/**
 * A logged song, laid out like the Library rows: artwork, title and "artist · source · when", the BPM badge on the right.
 * Unnamed songs with a clip get ▶ Play clip (■ Stop while it plays) and Shazam under the title. Tap opens the song in
 * the Library when it is known there, otherwise renames it; long-press always renames.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LogRow(
    entry: BeatLogEntry,
    inTarget: Boolean,
    clipPlaying: Boolean,
    onIdentify: () -> Unit,
    onPlayClip: () -> Unit,
    onRename: () -> Unit,
    onOpenSong: (() -> Unit)?,
    onAccept: () -> Unit,
    onReject: () -> Unit,
) {
    val suggestion = entry.suggestion?.takeIf { entry.title == null }
    val unnamed = entry.title == null

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
            .combinedClickable(onClick = onOpenSong ?: onRename, onLongClick = onRename)
            .padding(11.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            SongArtwork(null, sizeDp = 46, cornerDp = 11)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (unnamed && entry.placeholderNo != null) str(R.string.log_unnamed_song, entry.placeholderNo) else entry.label(LocalContext.current),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (entry.playCount > 1) {
                        Spacer(Modifier.width(6.dp))
                        Text("${entry.playCount}×", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Text(
                    compactMetaLine(entry),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (unnamed && entry.clipPath != null) {
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        RowAction(
                            if (clipPlaying) R.drawable.ic_stop else R.drawable.ic_play,
                            str(if (clipPlaying) R.string.log_stop_clip else R.string.log_play_clip),
                            highlighted = clipPlaying,
                            onClick = onPlayClip,
                        )
                        RowAction(null, str(R.string.log_shazam), highlighted = false, onClick = onIdentify)
                    }
                }
            }
            Spacer(Modifier.width(10.dp))
            BpmBadge(entry.bpm, inRange = inTarget)
        }

        if (suggestion != null) {
            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                val sugLabel = buildString {
                    append(suggestion.title)
                    if (suggestion.artist.isNotBlank()) append(" · ${suggestion.artist}")
                }
                // The suggested name is inserted by position, then styled where it landed.
                val sugText = str(R.string.log_suggestion, sugLabel)
                val sugAt = sugText.indexOf(sugLabel)
                Text(
                    buildAnnotatedString {
                        append(sugText)
                        if (sugAt >= 0) {
                            addStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold), sugAt, sugAt + sugLabel.length)
                        }
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                RowAction(null, str(R.string.log_accept), highlighted = true, onClick = onAccept)
                Spacer(Modifier.width(4.dp))
                Box(
                    Modifier
                        .clip(RoundedCornerShape(15.dp))
                        .clickable(onClick = onReject)
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                ) {
                    Text(str(R.string.log_dismiss), fontSize = 12.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** A small 30 dp pill in a log row: Play clip / Stop, Shazam, Accept. */
@Composable
private fun RowAction(icon: Int?, label: String, highlighted: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .height(30.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(if (highlighted) MaterialTheme.colorScheme.primary else Tone.pill)
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val tint = if (highlighted) Color.White else MaterialTheme.colorScheme.onSurface
        if (icon != null) {
            Icon(painterResource(icon), contentDescription = null, tint = tint, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(5.dp))
        }
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = tint, maxLines = 1)
    }
}

@Composable
private fun compactMetaLine(entry: BeatLogEntry): String = listOfNotNull(
    entry.artist?.takeIf { it.isNotBlank() },
    when {
        entry.sourceApp != null -> entry.sourceApp
        entry.remoteInstallId != null -> str(R.string.log_source_other_phone)
        entry.phoneApp != null -> entry.phoneApp
        entry.clipPath != null && entry.title == null -> str(R.string.log_source_clip_saved)
        else -> str(R.string.log_source_ambient)
    },
    relativeTime(entry.timestamp),
).joinToString(" · ")

/**
 * Type a title and artist by hand, in the app's sheet style (as the Listen tab's Target range sheet). The top shows
 * which play this is (when, where, its BPM); an unnamed song with a clip can play it while typing or go to Shazam, and a
 * server suggestion fills both fields in one tap. Saving always applies, and merges with an entry of the same song.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RenameSheet(
    entry: BeatLogEntry,
    inTarget: Boolean,
    clipPlaying: Boolean,
    onPlayClip: () -> Unit,
    onIdentify: () -> Unit,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
) {
    val unnamed = entry.title == null
    var title by remember { mutableStateOf(entry.title.orEmpty()) }
    var artist by remember { mutableStateOf(entry.artist.orEmpty()) }
    val titleFocus = remember { FocusRequester() }
    val artistFocus = remember { FocusRequester() }
    val save = { if (title.isNotBlank()) onSave(title.trim(), artist.trim()) }
    // An unnamed song opens ready to type; a rename leaves the keyboard down until a field is tapped.
    LaunchedEffect(Unit) { if (unnamed) titleFocus.requestFocus() }

    CalmBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberCalmSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = Tone.sheet,
        // Tone.sheet isn't a theme colour, so the sheet can't work out its text colour: without this, untinted text
        // takes whatever the caller had (black outside a Surface) and disappears in dark mode.
        contentColor = MaterialTheme.colorScheme.onSurface,
        dragHandle = {
            Box(Modifier.padding(top = 10.dp, bottom = 16.dp).size(36.dp, 4.dp).clip(RoundedCornerShape(2.dp)).background(Color(0xFF3A3946)))
        },
    ) {
        Column(Modifier.imePadding().padding(start = 20.dp, end = 20.dp, bottom = 24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(str(if (unnamed) R.string.log_rename_title_unnamed else R.string.log_rename_title), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        if (unnamed && entry.placeholderNo != null) str(R.string.log_unnamed_song, entry.placeholderNo) else entry.label(LocalContext.current),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        compactMetaLine(entry),
                        fontSize = 12.sp,
                        color = Tone.text3,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(12.dp))
                BpmBadge(entry.bpm, inRange = inTarget)
            }

            if (unnamed && entry.clipPath != null) {
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    RowAction(
                        if (clipPlaying) R.drawable.ic_stop else R.drawable.ic_play,
                        str(if (clipPlaying) R.string.log_stop_clip else R.string.log_play_clip),
                        highlighted = clipPlaying,
                        onClick = onPlayClip,
                    )
                    RowAction(null, str(R.string.log_shazam), highlighted = false, onClick = onIdentify)
                }
            }

            entry.suggestion?.takeIf { unnamed }?.let { s ->
                Spacer(Modifier.height(14.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Tone.selected)
                        .border(1.dp, Tone.chipLine, RoundedCornerShape(14.dp))
                        .clickable(onClickLabel = str(R.string.log_use_this_name)) { title = s.title; artist = s.artist }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(str(R.string.log_suggested), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Tone.primarySoft)
                        Text(
                            listOf(s.title, s.artist).filter { it.isNotBlank() }.joinToString(" · "),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(str(R.string.log_use), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Tone.primarySoft)
                }
            }

            Spacer(Modifier.height(18.dp))
            NameField(
                label = str(R.string.log_field_title),
                placeholder = str(R.string.log_field_title_hint),
                value = title,
                onValueChange = { title = it },
                imeAction = ImeAction.Next,
                onIme = { artistFocus.requestFocus() },
                focusRequester = titleFocus,
            )
            Spacer(Modifier.height(12.dp))
            NameField(
                label = str(R.string.log_field_artist),
                placeholder = str(R.string.log_field_artist_hint),
                value = artist,
                onValueChange = { artist = it },
                imeAction = ImeAction.Done,
                onIme = save,
                focusRequester = artistFocus,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                str(R.string.log_rename_merge_note),
                fontSize = 12.sp,
                color = Tone.text3,
            )

            Spacer(Modifier.height(20.dp))
            Button(
                onClick = save,
                enabled = title.isNotBlank(),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Text(str(if (unnamed) R.string.log_save_name else R.string.log_save), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/** A labelled 52 dp field in the app's style: filled, outlined, the outline in the accent while typing. */
@Composable
private fun NameField(
    label: String,
    placeholder: String,
    value: String,
    onValueChange: (String) -> Unit,
    imeAction: ImeAction,
    onIme: () -> Unit,
    focusRequester: FocusRequester,
) {
    var focused by remember { mutableStateOf(false) }
    Column {
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .border(
                    if (focused) 1.5.dp else 1.dp,
                    if (focused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    RoundedCornerShape(14.dp),
                )
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (value.isEmpty()) {
                Text(placeholder, style = MaterialTheme.typography.bodyLarge, color = Tone.text3)
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = imeAction),
                keyboardActions = KeyboardActions(onAny = { onIme() }),
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .onFocusChanged { focused = it.isFocused }
                    .semantics { contentDescription = label },
            )
        }
    }
}

@Composable
private fun relativeTime(timestamp: Long): String {
    val now = System.currentTimeMillis()
    return if (now - timestamp < DateUtils.MINUTE_IN_MILLIS) str(R.string.log_just_now)
    else DateUtils.getRelativeTimeSpanString(timestamp, now, DateUtils.MINUTE_IN_MILLIS).toString()
}

/** 48dp rounded squircle BPM badge (Coral tinted when in target range, neutral surfaceHigh otherwise). */
@Composable
internal fun BpmBadge(bpm: Float, inRange: Boolean) {
    val mint = inTargetColor()
    val bg = if (inRange) mint.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceContainerHigh
    val border = if (inRange) mint.copy(alpha = 0.35f) else MaterialTheme.colorScheme.outline
    val numColor = if (inRange) mint else MaterialTheme.colorScheme.onSurface
    val subColor = if (inRange) mint.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                bpm.roundToInt().toString(),
                color = numColor,
                fontSize = 15.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.ExtraBold,
            )
            Text(
                str(R.string.log_bpm_unit),
                color = subColor,
                fontSize = 8.sp,
                lineHeight = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp,
            )
        }
    }
}

@Composable
private fun EmptyState(searching: Boolean) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            painterResource(if (searching) R.drawable.ic_search else R.drawable.ic_radar),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(48.dp),
        )
        Spacer(Modifier.height(14.dp))
        Text(
            str(if (searching) R.string.log_empty_search_title else R.string.log_empty_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            str(if (searching) R.string.log_empty_search_text else R.string.log_empty_text),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

