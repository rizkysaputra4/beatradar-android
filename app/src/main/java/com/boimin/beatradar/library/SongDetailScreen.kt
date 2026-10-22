package com.boimin.beatradar.library

import androidx.compose.ui.graphics.Color
import com.boimin.beatradar.ui.tempoRangeShort
import com.boimin.beatradar.ui.theme.Tone
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.boimin.beatradar.R
import com.boimin.beatradar.capture.CaptureService
import com.boimin.beatradar.capture.Monitor
import com.boimin.beatradar.data.Settings
import com.boimin.beatradar.i18n.str
import com.boimin.beatradar.i18n.strPlural
import com.boimin.beatradar.net.SongDetail
import com.boimin.beatradar.net.SongSummary
import com.boimin.beatradar.net.TempoVotes
import com.boimin.beatradar.ui.theme.Brand
import com.boimin.beatradar.ui.theme.inTargetColor
import kotlin.math.abs
import kotlin.math.roundToInt
import com.boimin.beatradar.ui.Skeleton
import com.boimin.beatradar.ui.SkeletonBox
import com.boimin.beatradar.ui.SkeletonSongRow

/**
 * A song's page: its own bar (back, share, ⋮ with Report), the song, its tempo in plain words, practice and tempo
 * feedback, Add to set and Love, where to play it, and more songs at the same tempo.
 */
@Composable
internal fun SongDetailScreen(state: DetailState, vm: LibraryViewModel, actions: LibraryActions, settings: Settings, modifier: Modifier) {
    var reporting by remember { mutableStateOf<String?>(null) }
    var addingToSet by remember { mutableStateOf(false) }
    var addingSimilar by remember { mutableStateOf<SongSummary?>(null) }
    var practising by remember { mutableStateOf(false) }
    var voting by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    val song = state.song
    Column(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp).height(52.dp), verticalAlignment = Alignment.CenterVertically) {
            BarIcon(R.drawable.ic_arrow_back, str(R.string.library_back), 24, MaterialTheme.colorScheme.onSurface, vm::close)
            Spacer(Modifier.weight(1f))
            if (song != null) {
                BarIcon(R.drawable.ic_share, str(R.string.library_share), 22, Tone.text2) { actions.share(song.summary()) }
                Spacer(Modifier.width(4.dp))
                Box {
                    BarIcon(R.drawable.ic_more, str(R.string.library_more), 22, Tone.text2) { menu = true }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text(str(R.string.song_report)) }, onClick = { menu = false; reporting = "wrong_bpm" })
                    }
                }
            }
        }
        when {
            song != null -> SongPage(
                song,
                state.similar,
                vm,
                actions,
                settings,
                onAddToSet = { addingToSet = true },
                onAddSimilar = { addingSimilar = it },
                onPractise = { practising = true },
                // Two tempos people hear: vote. One tempo: say it's wrong, with the tempo heard.
                onFeelsDifferent = { if (song.bpmAlt != null && song.tempoVotes != null) voting = true else reporting = "wrong_bpm" },
            )
            state.loading -> SongPageSkeleton()
            else -> Message(str(R.string.song_load_failed), state.error ?: "", retry = vm::refreshDetail)
        }
    }
    if (addingToSet && song != null) {
        AddToSetSheet(song.summary(), vm, onDismiss = { addingToSet = false })
    }
    addingSimilar?.let { AddToSetSheet(it, vm, onDismiss = { addingSimilar = null }) }
    if (practising && song != null) {
        val monitor by Monitor.state.collectAsStateWithLifecycle()
        val context = LocalContext.current
        PracticeSheet(
            songTitle = listOf(song.title, song.artist).filter { it.isNotBlank() }.joinToString(" · "),
            startBpm = song.bpm.roundToInt(),
            listening = monitor.active,
            onStopListening = { CaptureService.send(context, CaptureService.ACTION_STOP) },
            onDismiss = { practising = false },
        )
    }
    if (voting && song != null) {
        val alt = song.bpmAlt
        val votes = song.tempoVotes
        if (alt != null && votes != null) {
            TempoVoteDialog(song.bpm, alt, votes, onVote = { vm.voteTempo(song.slug, it) }, onDismiss = { voting = false })
        }
    }
    reporting?.let { reason ->
        if (song != null) {
            ReportDialog(
                initialReason = reason,
                onDismiss = { reporting = null },
                onSend = { r, note -> reporting = null; vm.report(song.slug, r, note) },
            )
        }
    }
}

@Composable
private fun SongPage(
    song: SongDetail,
    similar: List<SongSummary>,
    vm: LibraryViewModel,
    actions: LibraryActions,
    settings: Settings,
    onAddToSet: () -> Unit,
    onAddSimilar: (SongSummary) -> Unit,
    onPractise: () -> Unit,
    onFeelsDifferent: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp),
    ) {
        // The song: 112 dp art, title 24 sp, artist, facts, and who brought it in.
        Row(Modifier.padding(start = 4.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            SongArtwork(song.artworkUrl, sizeDp = 112, cornerDp = 12)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(song.title, fontSize = 24.sp, lineHeight = 29.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                Text(song.artist, fontSize = 16.sp, lineHeight = 19.sp, color = Tone.text2, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val facts = listOfNotNull(
                    song.genre,
                    song.releaseYear?.toString(),
                    song.durationMs?.let { "%d:%02d".format(it / 60_000, it / 1000 % 60) },
                )
                if (facts.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text(facts.joinToString(" · "), fontSize = 13.sp, lineHeight = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                when {
                    song.discoveredByMe -> {
                        Spacer(Modifier.height(6.dp))
                        Text(str(R.string.song_added_by_you), fontSize = 13.sp, lineHeight = 15.sp, color = Tone.primarySoft)
                    }
                    song.broughtInBy != null -> {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            str(R.string.song_brought_in_by, song.broughtInBy),
                            fontSize = 13.sp,
                            lineHeight = 15.sp,
                            color = Tone.primarySoft,
                            modifier = Modifier.clickable { vm.openProfile(song.broughtInBy) },
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(22.dp))
        // The tempo, in plain words: how sure it is, and whether it fits your target.
        val inTarget = settings.inTarget(song.bpm)
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .padding(18.dp),
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    "${song.bpm.roundToInt()}",
                    fontSize = 52.sp,
                    lineHeight = 52.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (inTarget) inTargetColor() else MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.width(8.dp))
                Text(str(R.string.library_bpm), fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 6.dp))
            }
            Spacer(Modifier.height(12.dp))
            Text(confidenceText(song.listeners, song.confirmations), fontSize = 14.sp, lineHeight = 17.sp, color = Tone.text2)
            song.bpmAlt?.let {
                Spacer(Modifier.height(4.dp))
                Text(str(R.string.song_also_heard_at, it.roundToInt()), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (settings.rangeEnabled) {
                Spacer(Modifier.height(4.dp))
                Text(
                    tempoRangeShort(settings.targetLow, settings.targetHigh).let { if (inTarget) str(R.string.song_in_target, it) else str(R.string.song_outside_target, it) },
                    fontSize = 13.sp,
                    lineHeight = 15.sp,
                    color = if (inTarget) inTargetColor() else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PillButton(R.drawable.ic_note, str(R.string.song_play_click), Modifier.weight(1f), onClick = onPractise)
                PillButton(R.drawable.ic_vote, str(R.string.song_feels_different), Modifier.weight(1f), onClick = onFeelsDifferent)
            }
        }

        Spacer(Modifier.height(16.dp))
        // Add to set and Love together, in the thumb zone.
        val inSets by remember(song.slug) { vm.setsWith(song.slug) }.collectAsStateWithLifecycle(emptyList())
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier
                    .weight(1f)
                    .height(52.dp)
                    .clip(RoundedCornerShape(26.dp))
                    .background(MaterialTheme.colorScheme.primary)
                    .clickable(onClick = onAddToSet),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(painterResource(R.drawable.ic_playlist_add), contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    if (inSets.isNotEmpty()) strPlural(R.plurals.song_in_sets, inSets.size, inSets.size) else str(R.string.sets_add_to_set),
                    fontWeight = FontWeight.Medium,
                    color = Color.White,
                    maxLines = 1,
                    softWrap = false,
                    autoSize = TextAutoSize.StepBased(minFontSize = 13.sp, maxFontSize = 16.sp),
                )
            }
            Row(
                Modifier
                    .height(54.dp)
                    .clip(RoundedCornerShape(26.dp))
                    .border(1.dp, Tone.line, RoundedCornerShape(26.dp))
                    .clickable {
                        val target = !song.lovedByMe
                        if (actions.signedIn) vm.setLoved(song.slug, target) else actions.requireSignIn { vm.setLoved(song.slug, target) }
                    }
                    .padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painterResource(if (song.lovedByMe) R.drawable.ic_favorite else R.drawable.ic_favorite_border),
                    contentDescription = null,
                    tint = Brand.Heart,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(if (song.loveCount > 0) str(R.string.song_love_count, compactCount(song.loveCount)) else str(R.string.song_love), fontSize = 15.sp, fontWeight = FontWeight.Medium)
            }
        }

        if (song.links.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (link in song.links.sortedBy { SERVICES.keys.indexOf(it.service) }) {
                    Box(
                        Modifier
                            .weight(1f)
                            .height(46.dp)
                            .clip(RoundedCornerShape(22.dp))
                            .border(1.dp, Tone.line, RoundedCornerShape(22.dp))
                            .clickable { actions.openUrl(link.url) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            SERVICES[link.service] ?: link.service,
                            maxLines = 1,
                            softWrap = false,
                            autoSize = TextAutoSize.StepBased(minFontSize = 11.sp, maxFontSize = 14.sp),
                            modifier = Modifier.padding(horizontal = 6.dp),
                        )
                    }
                }
            }
        }

        if (similar.isNotEmpty()) {
            val bpm = song.bpm.roundToInt()
            Spacer(Modifier.height(22.dp))
            Text(
                str(R.string.song_more_at, bpm - 2, bpm + 2),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            similar.forEach { s ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { vm.open(s.slug) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SongArtwork(s.artworkUrl, sizeDp = 40, cornerDp = 8)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(s.title, fontSize = 15.sp, lineHeight = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${s.artist} · ${str(R.string.library_bpm_value, s.bpm.roundToInt())}", fontSize = 13.sp, lineHeight = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    BarIcon(R.drawable.ic_playlist_add, str(R.string.song_add_to_set_description, s.title), 22, Tone.primarySoft) { onAddSimilar(s) }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

/** "Measured once · needs 2 more to confirm" rather than "100%" from one phone. */
@Composable
internal fun confidenceText(listeners: Int, confirmations: Int): String = when {
    listeners <= 1 -> str(R.string.song_confidence_once)
    listeners == 2 -> str(R.string.song_confidence_two)
    else -> strPlural(R.plurals.song_confidence_confirmed, listeners, confirmations, listeners)
}

private val SERVICES = linkedMapOf("spotify" to "Spotify", "apple_music" to "Apple Music", "youtube" to "YouTube")

@Composable
private fun PillButton(icon: Int, label: String, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(Tone.pill)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(6.dp))
        // Half a 320 dp screen, a large font or a longer language: the label shrinks, then takes a second line.
        Text(
            label,
            fontWeight = FontWeight.Medium,
            lineHeight = 15.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            autoSize = TextAutoSize.StepBased(minFontSize = 12.sp, maxFontSize = 14.sp),
        )
    }
}

/** "Which tempo feels right?": the two tempos people hear, with the votes so far. */
@Composable
private fun TempoVoteDialog(bpm: Float, alt: Float, votes: TempoVotes, onVote: (Float) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(str(R.string.song_vote_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(str(R.string.song_vote_body, bpm.roundToInt(), alt.roundToInt()), style = MaterialTheme.typography.bodyMedium)
                for ((tempo, count) in listOf(bpm to votes.forBpm, alt to votes.forAlt).sortedBy { it.first }) {
                    val mine = votes.myVote != null && abs(votes.myVote - tempo) < 0.5f
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (mine) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceContainerHigh)
                            .clickable { if (!mine) onVote(tempo) }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(str(R.string.library_bpm_value, tempo.roundToInt()), fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Text(if (mine) str(R.string.song_your_vote, count) else strPlural(R.plurals.song_votes, count, count), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(str(R.string.song_done)) } },
    )
}

@Composable
private fun ReportDialog(initialReason: String, onDismiss: () -> Unit, onSend: (String, String?) -> Unit) {
    val reasons = listOf(
        "wrong_bpm" to str(R.string.song_reason_wrong_bpm),
        "wrong_title" to str(R.string.song_reason_wrong_title),
        "other" to str(R.string.song_reason_other),
    )
    var reason by remember { mutableStateOf(initialReason) }
    var note by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(str(R.string.song_report)) },
        text = {
            Column {
                reasons.forEach { (code, label) ->
                    Row(
                        Modifier.fillMaxWidth().selectable(selected = reason == code, onClick = { reason = code }).padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = reason == code, onClick = null)
                        Spacer(Modifier.width(8.dp))
                        Text(label)
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it.take(500) },
                    label = { Text(if (reason == "wrong_bpm") str(R.string.song_note_tempo) else str(R.string.song_note_details)) },
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSend(reason, note.trim().ifEmpty { null }) }) { Text(str(R.string.song_send)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(str(R.string.library_cancel)) } },
    )
}

/** Small "badge" pill used on profiles. */
@Composable
internal fun Pill(text: String) {
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/** [SongPage] while it loads: the 112 dp art and title, the tempo card, the actions and a few similar songs. */
@Composable
private fun SongPageSkeleton() {
    Skeleton(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
        Column {
            Row(Modifier.padding(start = 4.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                SkeletonBox(Modifier.size(112.dp), corner = 12.dp)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SkeletonBox(Modifier.fillMaxWidth(0.85f).height(22.dp))
                    SkeletonBox(Modifier.fillMaxWidth(0.55f).height(16.dp))
                    SkeletonBox(Modifier.fillMaxWidth(0.4f).height(12.dp))
                }
            }
            Spacer(Modifier.height(22.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SkeletonBox(Modifier.size(width = 110.dp, height = 48.dp), corner = 10.dp)
                SkeletonBox(Modifier.fillMaxWidth(0.7f).height(13.dp))
                SkeletonBox(Modifier.fillMaxWidth(0.45f).height(12.dp))
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SkeletonBox(Modifier.weight(1f).height(48.dp), corner = 24.dp)
                SkeletonBox(Modifier.weight(1f).height(48.dp), corner = 24.dp)
            }
            Spacer(Modifier.height(24.dp))
            SkeletonBox(Modifier.fillMaxWidth(0.4f).height(12.dp))
            Spacer(Modifier.height(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { repeat(3) { SkeletonSongRow() } }
        }
    }
}
