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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.boimin.beatradar.R
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
                            "Search logged songs…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                    .clickable(onClickLabel = "Weekly tempo recap", onClick = onShowRecap)
                    .padding(horizontal = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("My week", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
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
                    contentDescription = "Clear log",
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
                label = "● BPM ${bpmRange.first}–${bpmRange.last}",
                selected = onlyInRange,
                activeColor = inTargetColor(),
                onClick = onToggleInRange,
            )
            LogFilterPill(
                label = "Named",
                selected = onlyIdentified,
                activeColor = MaterialTheme.colorScheme.primary,
                onClick = onToggleIdentified,
            )
            if (unnamedWithClip > 0 || onlyUnnamed) {
                val badgeText = buildString {
                    append("$unnamedWithClip Unnamed")
                    if (suggested > 0) append(" · $suggested Match")
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

    renaming?.let { entry ->
        RenameDialog(
            entry = entry,
            onDismiss = { renaming = null },
            onSave = { title, artist -> renaming = null; onRename(entry, title, artist) },
        )
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear the whole log?") },
            text = { Text("All ${entries.size} songs will be removed. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = { confirmClear = false; onClear() }) { Text("Clear") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
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
        BpmBox(minText, "Min BPM") { minText = it; apply(it, maxText) }
        Text("–", color = MaterialTheme.colorScheme.onSurfaceVariant)
        BpmBox(maxText, "Max BPM") { maxText = it; apply(minText, it) }
        Spacer(Modifier.weight(1f))
        if (custom) {
            Text(
                "Use target",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onReset).padding(horizontal = 8.dp, vertical = 6.dp),
            )
        } else {
            Text("Listen target", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                Icon(painterResource(R.drawable.ic_delete), "Delete", tint = MaterialTheme.colorScheme.onErrorContainer)
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
                        if (unnamed && entry.placeholderNo != null) "Unnamed song #${entry.placeholderNo}" else entry.label,
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
                            if (clipPlaying) "Stop" else "Play clip",
                            highlighted = clipPlaying,
                            onClick = onPlayClip,
                        )
                        RowAction(null, "Shazam", highlighted = false, onClick = onIdentify)
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
                Text(
                    buildAnnotatedString {
                        append("Suggested: ")
                        withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)) {
                            append(sugLabel)
                        }
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                RowAction(null, "Accept", highlighted = true, onClick = onAccept)
                Spacer(Modifier.width(4.dp))
                Box(
                    Modifier
                        .clip(RoundedCornerShape(15.dp))
                        .clickable(onClick = onReject)
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                ) {
                    Text("Dismiss", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

private fun compactMetaLine(entry: BeatLogEntry): String = listOfNotNull(
    entry.artist?.takeIf { it.isNotBlank() },
    when {
        entry.sourceApp != null -> entry.sourceApp
        entry.remoteInstallId != null -> "Other phone"
        entry.phoneApp != null -> entry.phoneApp
        entry.clipPath != null && entry.title == null -> "12s clip saved"
        else -> "Ambient"
    },
    relativeTime(entry.timestamp),
).joinToString(" · ")

/** Type a title and artist by hand. Always applies, and merges with an entry of the same song. */
@Composable
private fun RenameDialog(entry: BeatLogEntry, onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var title by remember { mutableStateOf(entry.title.orEmpty()) }
    var artist by remember { mutableStateOf(entry.artist.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (entry.title == null) "Name ${entry.label}" else "Rename song") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Title") }, singleLine = true)
                OutlinedTextField(value = artist, onValueChange = { artist = it }, label = { Text("Artist") }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(title.trim(), artist.trim()) }, enabled = title.isNotBlank()) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun relativeTime(timestamp: Long): String {
    val now = System.currentTimeMillis()
    return if (now - timestamp < DateUtils.MINUTE_IN_MILLIS) "Just now"
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
                "BPM",
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
            if (searching) "No matching songs" else "No songs yet",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            if (searching) "Try a different search or filter." else "Play some music and tap the dial. Every song's BPM lands here.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** "3 songs without a name · 2 suggested names · Identify". */
internal fun unnamedLineText(count: Int, suggested: Int): String = buildString {
    append(if (count == 1) "1 song without a name" else "$count songs without a name")
    if (suggested > 0) append(if (suggested == 1) " · 1 suggested name" else " · $suggested suggested names")
    append(" · Identify")
}
