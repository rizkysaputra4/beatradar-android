package com.boimin.beatradar.ui

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
    onlyInRange: Boolean,
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
    onRename: (BeatLogEntry, String, String) -> Unit,
    /** Opens a song's Library page (entries the server knows). */
    onOpenSong: (String) -> Unit,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    onDelete: (BeatLogEntry) -> Unit,
    onClear: () -> Unit,
    onExport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirmClear by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<BeatLogEntry?>(null) }

    Column(modifier.fillMaxSize()) {
        // Low-profile Search Bar + CSV & Clear actions
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
                    .alpha(if (entries.isNotEmpty()) 1f else 0.45f)
                    .clickable(enabled = entries.isNotEmpty(), onClick = onExport)
                    .padding(horizontal = 11.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "CSV",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
                label = "● In Target",
                selected = onlyInRange,
                activeColor = Brand.Coral,
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
                                onIdentify = { onIdentify(entry) },
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

@Composable
private fun LogRow(
    entry: BeatLogEntry,
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
            .clickable(onClick = onRename)
            .padding(12.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BpmBadge(entry.bpm, entry.inRange)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (unnamed && entry.placeholderNo != null) "Unnamed Track #${entry.placeholderNo}" else entry.label,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (unnamed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (entry.playCount > 1) {
                        Spacer(Modifier.width(6.dp))
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                .padding(horizontal = 5.dp, vertical = 1.dp),
                        ) {
                            Text(
                                "${entry.playCount}×",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    compactMetaLine(entry),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            if (unnamed) {
                if (entry.clipPath != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                .clickable(onClick = onPlayClip),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                painterResource(R.drawable.ic_play),
                                contentDescription = "Play clip",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(13.dp),
                            )
                        }
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
                                .clickable(onClick = onIdentify)
                                .padding(horizontal = 9.dp, vertical = 5.dp),
                        ) {
                            Text(
                                "Shazam",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                } else {
                    Box(
                        Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .clickable(onClick = onRename),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_edit),
                            contentDescription = "Rename",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(13.dp),
                        )
                    }
                }
            } else if (onOpenSong != null) {
                Box(
                    Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .clickable(onClick = onOpenSong),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painterResource(R.drawable.ic_open),
                        contentDescription = "Open in Library",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(13.dp),
                    )
                }
            }
        }

        if (suggestion != null) {
            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            Spacer(Modifier.height(7.dp))
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
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
                Box(
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
                        .clickable(onClick = onAccept)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Text("Accept", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.width(2.dp))
                Box(
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onReject)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Text("Dismiss", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
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
    val bg = if (inRange) Brand.Coral.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceContainerHigh
    val border = if (inRange) Brand.Coral.copy(alpha = 0.35f) else MaterialTheme.colorScheme.outline
    val numColor = if (inRange) Brand.Coral else MaterialTheme.colorScheme.onSurface
    val subColor = if (inRange) Brand.Coral.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant
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
