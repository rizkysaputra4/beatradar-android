package com.boimin.beatradar.ui

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.boimin.beatradar.R
import com.boimin.beatradar.data.BeatLogEntry
import com.boimin.beatradar.sync.UnnamedRules
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
        Row(
            Modifier.padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = { Text("Search songs") },
                leadingIcon = { Icon(painterResource(R.drawable.ic_search), null) },
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onExport, enabled = entries.isNotEmpty()) {
                Icon(painterResource(R.drawable.ic_share), contentDescription = "Export CSV")
            }
            IconButton(onClick = { confirmClear = true }, enabled = entries.isNotEmpty()) {
                Icon(painterResource(R.drawable.ic_delete), contentDescription = "Clear log")
            }
        }

        Row(
            Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = onlyInRange,
                onClick = onToggleInRange,
                label = { Text("In range") },
                leadingIcon = if (onlyInRange) { { Icon(painterResource(R.drawable.ic_check), null, Modifier.size(16.dp)) } } else null,
                shape = RoundedCornerShape(50),
            )
            FilterChip(
                selected = onlyIdentified,
                onClick = onToggleIdentified,
                label = { Text("Identified") },
                leadingIcon = if (onlyIdentified) { { Icon(painterResource(R.drawable.ic_check), null, Modifier.size(16.dp)) } } else null,
                shape = RoundedCornerShape(50),
            )
        }

        if (unnamedWithClip > 0 || onlyUnnamed) {
            UnnamedLine(unnamedWithClip, suggested, onlyUnnamed, onToggleUnnamed)
        }

        // Pull down to sync: uploads, and brings in entries from the user's other phones when signed in.
        PullToRefreshBox(isRefreshing = refreshing, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) {
            if (entries.isEmpty()) {
                EmptyState(searching = query.isNotBlank() || onlyInRange || onlyIdentified || onlyUnnamed)
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(entries, key = { it.id }) { entry ->
                        SwipeToDelete(onDelete = { onDelete(entry) }) {
                            LogRow(
                                entry,
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
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(painterResource(R.drawable.ic_delete), "Delete", tint = MaterialTheme.colorScheme.onErrorContainer)
            }
        },
    ) { content() }
}

/** "3 songs without a name · Identify": filters the log to them, or back to everything. */
@Composable
private fun UnnamedLine(count: Int, suggested: Int, active: Boolean, onToggle: () -> Unit) {
    val text = if (active) "Showing songs without a name · Show all" else unnamedLineText(count, suggested)
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onToggle)
            .padding(vertical = 4.dp),
    )
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
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth(),
        onClick = onRename,
    ) {
        val suggestion = entry.suggestion?.takeIf { entry.title == null }
        Row(Modifier.padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = if (suggestion != null) 4.dp else 14.dp), verticalAlignment = Alignment.CenterVertically) {
            BpmBadge(entry.bpm, entry.inRange)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    entry.label,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val detail = listOfNotNull(entry.artist, entry.sourceApp).joinToString(" · ")
                if (detail.isNotEmpty()) {
                    Text(
                        detail,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    heardLine(entry),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (entry.title == null) {
                if (entry.clipPath != null) {
                    // A saved clip: replay it to recall the song, or play it to Shazam to get the name.
                    IconButton(onClick = onPlayClip) {
                        Icon(painterResource(R.drawable.ic_play), contentDescription = "Play clip", modifier = Modifier.size(20.dp))
                    }
                    FilledTonalIconButton(onClick = onIdentify) {
                        Icon(painterResource(R.drawable.ic_search), contentDescription = "Identify with Shazam", modifier = Modifier.size(20.dp))
                    }
                } else {
                    IconButton(onClick = onRename) {
                        Icon(painterResource(R.drawable.ic_edit), contentDescription = "Rename", modifier = Modifier.size(20.dp))
                    }
                }
            } else {
                if (onOpenSong != null) {
                    // In the Library: open its page (shared BPM, listeners, links).
                    IconButton(onClick = onOpenSong) {
                        Icon(painterResource(R.drawable.ic_library), contentDescription = "Open in Library", modifier = Modifier.size(20.dp))
                    }
                }
                if (entry.inRange) Icon(painterResource(R.drawable.ic_check), contentDescription = "In range", tint = Brand.Coral)
            }
        }
        if (suggestion != null) {
            // The crowd may know this one (PLAN §5c): one tap names it.
            Row(Modifier.fillMaxWidth().padding(start = 86.dp, end = 8.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    UnnamedRules.question(suggestion),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onAccept) { Text("Yes") }
                TextButton(onClick = onReject) { Text("No") }
            }
        }
    }
}

/** "Heard 3× · 5 minutes ago · nearby speaker" (or "on phone · Spotify"). */
private fun heardLine(entry: BeatLogEntry): String = listOfNotNull(
    "Heard ${entry.playCount}×".takeIf { entry.playCount > 1 },
    relativeTime(entry.timestamp),
    when {
        entry.remoteInstallId != null -> "from your other phone"
        entry.phoneApp != null -> "on phone · ${entry.phoneApp}"
        else -> "nearby speaker"
    },
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

@Composable
internal fun BpmBadge(bpm: Float, inRange: Boolean) {
    val bg = if (inRange) Brand.Coral else MaterialTheme.colorScheme.primaryContainer
    val fg = if (inRange) Brand.OnCoral else MaterialTheme.colorScheme.onPrimaryContainer
    Box(
        Modifier
            .size(58.dp)
            .clip(CircleShape)
            .background(bg),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(bpm.roundToInt().toString(), color = fg, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("BPM", color = fg, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun EmptyState(searching: Boolean) {
    Column(
        Modifier
            .fillMaxSize()
            // Scrollable so pull-to-refresh works on an empty log too.
            .verticalScroll(rememberScrollState())
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            painterResource(if (searching) R.drawable.ic_search else R.drawable.ic_radar),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(56.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            if (searching) "No matching songs" else "No songs yet",
            style = MaterialTheme.typography.titleLarge,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            if (searching) "Try a different search or filter." else "Play some music and tap Start. Every song's BPM lands here.",
            style = MaterialTheme.typography.bodyMedium,
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
