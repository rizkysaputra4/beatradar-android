package com.boimin.beatradar.library

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.remember
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.boimin.beatradar.R
import com.boimin.beatradar.data.SetRules
import com.boimin.beatradar.i18n.str
import com.boimin.beatradar.i18n.strPlural
import com.boimin.beatradar.net.SongSummary
import com.boimin.beatradar.ui.BpmBadge
import com.boimin.beatradar.ui.CalmBottomSheet
import com.boimin.beatradar.ui.rememberCalmSheetState
import kotlinx.coroutines.launch

/**
 * One song in a list, the same in Discover and in a set: artwork, title, artist, optional counters, BPM. Long-press
 * starts selecting; while selecting ([selected] not null) a checkbox shows and a tap toggles it.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun LibrarySongRow(
    title: String,
    artist: String,
    artworkUrl: String?,
    bpm: Float,
    inTarget: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    selected: Boolean? = null,
    counters: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            // Opaque either way, so nothing behind the row (the swipe-to-delete layer) shows through.
            .background(
                if (selected == true) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.12f).compositeOver(MaterialTheme.colorScheme.surfaceContainer)
                } else {
                    MaterialTheme.colorScheme.surfaceContainer
                },
            )
            .border(1.dp, if (selected == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // A selected song shows a check where its artwork is, so the row keeps its size and layout.
        if (selected == true) SelectTile(sizeDp = 46, cornerDp = 11) else SongArtwork(artworkUrl, sizeDp = 46, cornerDp = 11)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(artist, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            counters?.let {
                Spacer(Modifier.height(3.dp))
                it()
            }
        }
        Spacer(Modifier.width(10.dp))
        BpmBadge(bpm, inRange = inTarget)
        trailing?.invoke()
    }
}

/** The check that stands in for a selected song's artwork: violet with a tick. */
@Composable
internal fun SelectTile(sizeDp: Int, cornerDp: Int) {
    Box(
        Modifier
            .size(sizeDp.dp)
            .clip(RoundedCornerShape(cornerDp.dp))
            .background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(R.drawable.ic_check), contentDescription = str(R.string.library_selected), tint = Color.White, modifier = Modifier.size(24.dp))
    }
}

/**
 * Swipe a row to the left to delete it. The row springs back: [onDelete] only asks (a confirmation popup decides).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SwipeToDelete(onDelete: () -> Unit, enabled: Boolean = true, content: @Composable () -> Unit) {
    val latest by rememberUpdatedState(onDelete)
    // The swipe never settles as "dismissed": it reports the delete and springs back. A dismissed state would be saved
    // with the row's key and, when an undone song comes back, restored as a second swipe.
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) latest()
            false
        },
    )
    SwipeToDismissBox(
        state = state,
        enableDismissFromStartToEnd = false,
        gesturesEnabled = enabled,
        backgroundContent = {
            // Only while the row is being swiped: at rest nothing is behind it.
            if (state.dismissDirection == SwipeToDismissBoxValue.EndToStart) Box(
                Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.error.copy(alpha = 0.85f))
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(painterResource(R.drawable.ic_delete), contentDescription = str(R.string.library_remove), tint = MaterialTheme.colorScheme.onError)
            }
        },
    ) { content() }
}

/** The bar shown while selecting songs: how many, an action, and a way out. */
/** As tall as Discover's filter pills, which the bar covers there; other screens use the same height. */
internal val SelectionBarHeight = 34.dp

@Composable
internal fun SelectionBar(count: Int, onCancel: () -> Unit, modifier: Modifier = Modifier, action: @Composable () -> Unit) {
    // Fills the space of what it covers (the filters, the top bar), so nothing under it moves. It also takes the taps
    // meant for what it covers.
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f).compositeOver(MaterialTheme.colorScheme.background))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .padding(vertical = 3.dp)
                .clip(RoundedCornerShape(50))
                .clickable(onClick = onCancel)
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(str(R.string.library_cancel), fontSize = 14.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.primary)
        }
        Text(strPlural(R.plurals.library_selected_count, count, count), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        action()
    }
}

/** The small filled action in a [SelectionBar], e.g. "Add to set". */
@Composable
internal fun SelectionAction(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxHeight()
            .padding(vertical = 3.dp)
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.primary)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color.White)
    }
}

/** From Discover: put the selected songs into one of the sets, or a new one. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AddManyToSetSheet(songs: List<SongSummary>, vm: LibraryViewModel, onDone: (String) -> Unit, onDismiss: () -> Unit) {
    val sets by vm.sets.collectAsStateWithLifecycle()
    var newName by rememberSaveable { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    CalmBottomSheet(onDismissRequest = onDismiss, sheetState = rememberCalmSheetState()) {
        Column(Modifier.padding(start = 24.dp, end = 24.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(strPlural(R.plurals.sets_add_songs_title, songs.size, songs.size), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            sets.forEach { set ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            vm.addSongsToSet(set.id, songs)
                            onDone(set.name)
                        }
                        .padding(vertical = 10.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(painterResource(R.drawable.ic_list), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
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
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = {
                        val name = newName
                        scope.launch { if (vm.createSetWith(name, songs) != null) onDone(SetRules.cleanName(name) ?: name) }
                    },
                    enabled = SetRules.cleanName(newName) != null,
                ) { Text(str(R.string.sets_create)) }
            }
        }
    }
}

/** A set's tempo span ("118–128 BPM", "124 BPM", or "No songs yet"), as [SetRules.span] in the app's language. */
@Composable
internal fun bpmSpan(min: Float?, max: Float?): String {
    val span = SetRules.spanBpm(min, max) ?: return str(R.string.library_no_songs_yet)
    return if (span.first == span.last) str(R.string.library_bpm_value, span.first) else str(R.string.library_bpm_range, span.first, span.last)
}
