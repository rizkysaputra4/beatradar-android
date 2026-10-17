package com.rizky.beatradar.ui

import android.text.format.DateUtils
import androidx.compose.foundation.background
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
import com.rizky.beatradar.R
import com.rizky.beatradar.data.BeatLogEntry
import com.rizky.beatradar.ui.theme.Brand
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
    onIdentify: (BeatLogEntry) -> Unit,
    onDelete: (BeatLogEntry) -> Unit,
    onClear: () -> Unit,
    onExport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirmClear by remember { mutableStateOf(false) }

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

        if (entries.isEmpty()) {
            EmptyState(searching = query.isNotBlank() || onlyInRange || onlyIdentified)
        } else {
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(entries, key = { it.id }) { entry ->
                    SwipeToDelete(onDelete = { onDelete(entry) }) { LogRow(entry, onIdentify = { onIdentify(entry) }) }
                }
            }
        }
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

@Composable
private fun LogRow(entry: BeatLogEntry, onIdentify: () -> Unit) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            BpmBadge(entry.bpm, entry.inRange)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    entry.title ?: "Unknown song",
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
                    relativeTime(entry.timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (entry.title == null && entry.clipPath != null) {
                // A saved clip: play it to Shazam to get the name.
                FilledTonalIconButton(onClick = onIdentify) {
                    Icon(painterResource(R.drawable.ic_search), contentDescription = "Identify with Shazam", modifier = Modifier.size(20.dp))
                }
            } else if (entry.inRange) {
                Icon(painterResource(R.drawable.ic_check), contentDescription = "In range", tint = Brand.Coral)
            }
        }
    }
}

private fun relativeTime(timestamp: Long): String {
    val now = System.currentTimeMillis()
    return if (now - timestamp < DateUtils.MINUTE_IN_MILLIS) "Just now"
    else DateUtils.getRelativeTimeSpanString(timestamp, now, DateUtils.MINUTE_IN_MILLIS).toString()
}

@Composable
private fun BpmBadge(bpm: Float, inRange: Boolean) {
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
