package com.boimin.beatradar.library

import androidx.compose.foundation.background
import com.boimin.beatradar.ui.tempoRangeShort
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.lazy.rememberLazyListState
import com.boimin.beatradar.R
import com.boimin.beatradar.data.Settings
import com.boimin.beatradar.i18n.str
import com.boimin.beatradar.i18n.strPlural
import com.boimin.beatradar.net.SongSummary
import com.boimin.beatradar.ui.BpmBadge
import com.boimin.beatradar.ui.theme.brandSwitchColors
import kotlinx.coroutines.launch
import com.boimin.beatradar.ui.Skeleton
import com.boimin.beatradar.ui.SkeletonBox
import com.boimin.beatradar.ui.SkeletonSongRow
import com.boimin.beatradar.ui.SkeletonSongList

/** A set someone shared as a link: its songs, and "Copy this set" into My sets. */
@Composable
internal fun SharedSetScreen(state: SharedSetState, vm: LibraryViewModel, settings: Settings, onSaved: (Long) -> Unit, modifier: Modifier) {
    val scope = rememberCoroutineScope()
    val set = state.set
    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { TextButton(onClick = vm::closeSharedSet) { Text(str(R.string.sets_back)) } }
        when {
            set != null -> {
                item {
                    Card {
                        Text(set.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        val sorted = set.songs.sortedBy { it.bpm }
                        Text(
                            listOfNotNull(
                                strPlural(R.plurals.sets_song_count, set.songs.size, set.songs.size),
                                bpmSpan(sorted.firstOrNull()?.bpm, sorted.lastOrNull()?.bpm),
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        set.sharedBy?.let { by ->
                            Text(
                                str(R.string.sets_shared_by, by),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.clickable { vm.openProfile(by) },
                            )
                        }
                        Button(onClick = { scope.launch { vm.createSetWith(set.name, set.songs)?.let(onSaved) } }) { Text(str(R.string.sets_copy)) }
                    }
                }
                items(set.songs.sortedBy { it.bpm }, key = { it.slug }) { s ->
                    SongRow(s, settings.inTarget(s.bpm), onClick = { vm.open(s.slug) })
                }
            }
            state.error != null -> item { Text(state.error, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp)) }
            else -> item {
                Skeleton {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SkeletonBox(Modifier.fillMaxWidth().height(124.dp), corner = 16.dp)
                        repeat(5) { SkeletonSongRow() }
                    }
                }
            }
        }
    }
}

/** "Build me a set": N minutes of well-confirmed songs in the target range, ready to save. */
@Composable
internal fun BuildSetScreen(state: BuildState, vm: LibraryViewModel, settings: Settings, onSaved: (Long) -> Unit, modifier: Modifier) {
    var minutes by rememberSaveable { mutableIntStateOf(state.minutes) }
    var onlyMine by rememberSaveable { mutableStateOf(state.onlyMine) }
    val range = settings.targetRange
    val scope = rememberCoroutineScope()
    val result = state.result
    val list = rememberLazyListState()
    // A fresh result under the fold (a short screen): bring it up, else Build seems to do nothing.
    LaunchedEffect(result, state.loading) {
        if (result == null || state.loading) return@LaunchedEffect
        val header = list.layoutInfo.visibleItemsInfo.firstOrNull { it.index == RESULT_ITEM }
        if (header == null || header.offset + header.size > list.layoutInfo.viewportEndOffset) list.animateScrollToItem(RESULT_ITEM)
    }
    LazyColumn(
        modifier.fillMaxSize(),
        state = list,
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { TextButton(onClick = vm::closeBuild) { Text(str(R.string.sets_back)) } }
        item {
            Card {
                Text(str(R.string.sets_build_me_a_set), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    str(R.string.sets_build_body, tempoRangeShort(settings.targetLow, settings.targetHigh)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (m in listOf(15, 30, 45, 60)) {
                        Chip(str(R.string.sets_minutes, m), selected = minutes == m, modifier = Modifier.weight(1f)) { minutes = m }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(str(R.string.sets_only_heard), style = MaterialTheme.typography.bodyMedium)
                        Text(str(R.string.sets_only_heard_body), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = onlyMine, onCheckedChange = { onlyMine = it }, colors = brandSwitchColors())
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { vm.runBuild(range, settings.halfDoubleMatch, minutes, onlyMine) }, enabled = !state.loading) {
                        Text(if (state.loading) str(R.string.sets_building) else str(R.string.sets_build))
                    }
                    if (result != null && result.songs.isNotEmpty()) {
                        TextButton(onClick = { vm.runBuild(range, settings.halfDoubleMatch, minutes, onlyMine, again = true) }) { Text(str(R.string.sets_try_another)) }
                    }
                }
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        }
        if (state.loading) {
            item { SkeletonSongList(6) }
        } else if (result != null) {
            item {
                val got = result.totalMs / 60_000
                val want = result.targetMs / 60_000
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        when {
                            result.songs.isEmpty() -> str(R.string.sets_build_empty)
                            result.totalMs < result.targetMs -> strPlural(R.plurals.sets_build_short, want.toInt(), got, want)
                            else -> strPlural(R.plurals.sets_build_result, result.songs.size, result.songs.size, got)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (result.songs.any { it.estimated }) {
                        Text(str(R.string.sets_estimated), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (result.songs.isNotEmpty()) {
                        val setName = str(R.string.sets_build_name, minutes, tempoRangeShort(settings.targetLow, settings.targetHigh))
                        Button(onClick = {
                            scope.launch { vm.createSetWith(setName, result.songs.map { it.summary() })?.let(onSaved) }
                        }) { Text(str(R.string.sets_save_as_set)) }
                    }
                }
            }
            items(result.songs, key = { it.slug }) { s ->
                SongRow(s.summary(), settings.inTarget(s.bpm), onClick = { vm.open(s.slug) }, trailing = "%d:%02d".format(s.durationMs / 60_000, s.durationMs / 1000 % 60))
            }
        }
    }
}

/** The result's header in [BuildSetScreen]'s list, after the back button and the options card. */
private const val RESULT_ITEM = 2

@Composable
private fun Card(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) { content() }
}

@Composable
internal fun Chip(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceContainerHigh)
            .border(1.dp, if (selected) MaterialTheme.colorScheme.primary else Color.Transparent, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            fontWeight = FontWeight.SemiBold,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            softWrap = false,
            // Four chips across a 320 dp screen with a large font: "15 min" shrinks rather than losing "min".
            autoSize = TextAutoSize.StepBased(minFontSize = 9.sp, maxFontSize = 12.sp),
        )
    }
}

@Composable
private fun SongRow(song: SongSummary, inTarget: Boolean, onClick: () -> Unit, trailing: String? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SongArtwork(song.artworkUrl, sizeDp = 42, cornerDp = 10)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(song.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(song.artist, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        trailing?.let {
            Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(8.dp))
        }
        BpmBadge(song.bpm, inTarget)
    }
}
