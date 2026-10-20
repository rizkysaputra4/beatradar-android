package com.boimin.beatradar.library

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.unit.sp
import com.boimin.beatradar.R
import androidx.compose.ui.platform.LocalContext
import com.boimin.beatradar.capture.CaptureService
import com.boimin.beatradar.capture.Monitor
import com.boimin.beatradar.net.SongDetail
import com.boimin.beatradar.net.SongSummary
import com.boimin.beatradar.net.TempoVotes
import com.boimin.beatradar.ui.theme.Brand
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
internal fun SongDetailScreen(state: DetailState, vm: LibraryViewModel, actions: LibraryActions, modifier: Modifier) {
    var reporting by remember { mutableStateOf(false) }
    var addingToSet by remember { mutableStateOf(false) }
    var addingSimilar by remember { mutableStateOf<SongSummary?>(null) }
    var practising by remember { mutableStateOf(false) }
    val song = state.song
    Column(modifier.fillMaxSize()) {
        // Top Action Bar
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                Modifier
                    .height(32.dp)
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(50))
                    .clickable(onClick = vm::close)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painterResource(R.drawable.ic_back),
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    "Back",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.weight(1f))
            if (song != null) {
                val inSets by remember(song.slug) { vm.setsWith(song.slug) }.collectAsStateWithLifecycle(emptyList())
                Box(
                    Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(if (inSets.isNotEmpty()) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer)
                        .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                        .clickable { addingToSet = true },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painterResource(if (inSets.isNotEmpty()) R.drawable.ic_set_added else R.drawable.ic_set_add),
                        contentDescription = if (inSets.isNotEmpty()) "In ${inSets.size} set(s): edit" else "Add to set",
                        tint = if (inSets.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(15.dp),
                    )
                }
                LovePillButton(song.lovedByMe, song.loveCount) {
                    val target = !song.lovedByMe
                    if (actions.signedIn) vm.setLoved(song.slug, target) else actions.requireSignIn { vm.setLoved(song.slug, target) }
                }
                Box(
                    Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                        .clickable { actions.share(song.summary()) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painterResource(R.drawable.ic_share),
                        contentDescription = "Share",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp),
                    )
                }
                Box(
                    Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                        .clickable { reporting = true },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painterResource(R.drawable.ic_flag),
                        contentDescription = "Report",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
        when {
            song != null -> SongPage(
                song,
                state.similar,
                vm,
                actions,
                onAddSimilar = { addingSimilar = it },
                onPractise = { practising = true },
            )
            state.loading -> Centered { CircularProgressIndicator() }
            else -> Message("Couldn't load this song", state.error ?: "", retry = vm::refreshDetail)
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
            startBpm = song.bpm.roundToInt(),
            listening = monitor.active,
            onStopListening = { CaptureService.send(context, CaptureService.ACTION_STOP) },
            onDismiss = { practising = false },
        )
    }
    if (reporting && song != null) {
        ReportDialog(
            onDismiss = { reporting = false },
            onSend = { reason, note -> reporting = false; vm.report(song.slug, reason, note) },
        )
    }
}

@Composable
private fun SongPage(
    song: SongDetail,
    similar: List<SongSummary>,
    vm: LibraryViewModel,
    actions: LibraryActions,
    onAddSimilar: (SongSummary) -> Unit,
    onPractise: () -> Unit,
) {
    var explain by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Horizontal Track Hero Card
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SongArtwork(song.artworkUrl, sizeDp = 68, cornerDp = 14)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    song.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    song.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val facts = listOfNotNull(
                    song.genre,
                    song.releaseYear?.toString(),
                    song.durationMs?.let { "%d:%02d".format(it / 60_000, it / 1000 % 60) },
                )
                if (facts.isNotEmpty()) {
                    Text(facts.joinToString(" · "), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (song.discoveredByMe) {
                    Spacer(Modifier.height(7.dp))
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Brand.Coral.copy(alpha = 0.14f))
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    ) {
                        Text("Discovered by you", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Brand.Coral)
                    }
                }
                song.broughtInBy?.takeIf { !song.discoveredByMe }?.let { nick ->
                    Spacer(Modifier.height(7.dp))
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
                            .clickable { vm.openProfile(nick) }
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    ) {
                        Text(
                            "Brought in by @$nick",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }

        // 2-Column Bento Grid: Consensus BPM & Accuracy
        Row(
            Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Max),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Consensus BPM Card
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(16.dp))
                    // The tempo is the page's focal stat: primary-tinted, the accuracy card stays neutral.
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                    .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.28f), RoundedCornerShape(16.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    "CONSENSUS BPM",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    buildAnnotatedString {
                        append(song.bpm.roundToInt().toString())
                        withStyle(SpanStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp, color = MaterialTheme.colorScheme.primary)) {
                            append(" BPM")
                        }
                    },
                    fontSize = 34.sp,
                    lineHeight = 36.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-1.5).sp,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    when {
                        song.bpmAlt == null -> "Verified community tempo"
                        song.bpmAlt < song.bpm -> "½× heard at ${song.bpmAlt.roundToInt()} BPM"
                        else -> "2× heard at ${song.bpmAlt.roundToInt()} BPM"
                    },
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Accuracy & Confirmations Card
            val ratio = if (song.listeners > 0) (song.confirmations.toFloat() / song.listeners).coerceIn(0f, 1f) else 0f
            val pct = (ratio * 100).roundToInt()
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
                    .clickable { explain = !explain }
                    .padding(14.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    "ACCURACY",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        "$pct%",
                        fontSize = 22.sp,
                        lineHeight = 24.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (song.confirmations > 0) Brand.Success else MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "${compactCount(song.confirmations)} / ${compactCount(song.listeners)}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(6.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(ratio)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(3.dp))
                            .background(Brand.Success),
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "Measured within ±2 BPM",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (explain) {
            Text(
                confirmationExplainer(song.listeners, song.confirmations),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }

        val alt = song.bpmAlt
        val votes = song.tempoVotes
        if (alt != null && votes != null) TempoVoteCard(song.bpm, alt, votes, onVote = { vm.voteTempo(song.slug, it) })

        // Open Track In Streaming Links Card
        if (song.links.isNotEmpty()) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
                    .padding(14.dp),
            ) {
                Text(
                    "OPEN TRACK IN",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    for (link in song.links.sortedBy { SERVICES.keys.indexOf(it.service) }) {
                        Box(
                            Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
                                .clickable { actions.openUrl(link.url) }
                                .padding(vertical = 9.dp, horizontal = 6.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "${SERVICES[link.service] ?: link.service} ↗",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
        TextButton(onClick = onPractise) { Text("Practise at ${song.bpm.roundToInt()} BPM: play a click") }

        if (similar.isNotEmpty()) {
            Text(
                "MORE AT ${song.bpm.roundToInt()} ±2 BPM",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            similar.forEach { s ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
                        .clickable { vm.open(s.slug) }
                        .padding(start = 10.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SongArtwork(s.artworkUrl, sizeDp = 36, cornerDp = 8)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(s.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${s.artist} · ${s.bpm.roundToInt()} BPM", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    TextButton(onClick = { onAddSimilar(s) }) { Text("+ Set") }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

/**
 * "Which tempo feels right?": the song is heard at two octaves (87 or 174?). Listeners say which one they move to; once
 * most agree on the other one, it becomes the song's main BPM.
 */
@Composable
private fun TempoVoteCard(bpm: Float, alt: Float, votes: TempoVotes, onVote: (Float) -> Unit) {
    val total = votes.forBpm + votes.forAlt
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            "WHICH TEMPO FEELS RIGHT?",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "Some hear ${bpm.roundToInt()}, some ${alt.roundToInt()}. Which one do you move to?",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for ((tempo, count) in listOf(bpm to votes.forBpm, alt to votes.forAlt).sortedBy { it.first }) {
                val mine = votes.myVote != null && abs(votes.myVote - tempo) < 0.5f
                Column(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (mine) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceContainerHigh)
                        .border(1.dp, if (mine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                        .clickable(onClickLabel = "I move to ${tempo.roundToInt()} BPM") { if (!mine) onVote(tempo) }
                        .padding(vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        "${tempo.roundToInt()} BPM",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (mine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        when {
                            mine -> "Your vote · $count"
                            count == 1 -> "1 vote"
                            else -> "$count votes"
                        },
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (total > 0) {
            val share = votes.forBpm.toFloat() / total
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            ) {
                // The main tempo's share, on the side its button is on.
                Box(
                    Modifier
                        .fillMaxWidth(share)
                        .fillMaxHeight()
                        .align(if (bpm < alt) Alignment.CenterStart else Alignment.CenterEnd)
                        .clip(RoundedCornerShape(3.dp))
                        .background(MaterialTheme.colorScheme.primary),
                )
            }
        }
    }
}

private val SERVICES = linkedMapOf("spotify" to "Spotify", "apple_music" to "Apple", "youtube" to "YouTube")

@Composable
private fun LovePillButton(loved: Boolean, count: Int, onClick: () -> Unit) {
    val bg = if (loved) Brand.Coral.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceContainer
    val border = if (loved) Brand.Coral.copy(alpha = 0.4f) else MaterialTheme.colorScheme.outline
    val fg = if (loved) Brand.Coral else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        Modifier
            .height(32.dp)
            .clip(RoundedCornerShape(50))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painterResource(if (loved) R.drawable.ic_favorite else R.drawable.ic_favorite_border),
            contentDescription = if (loved) "Unlove" else "Love",
            tint = if (loved) Brand.Coral else fg,
            modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.width(5.dp))
        Text(
            compactCount(count),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = fg,
        )
    }
}

@Composable
private fun ReportDialog(onDismiss: () -> Unit, onSend: (String, String?) -> Unit) {
    val reasons = listOf("wrong_bpm" to "Wrong BPM", "wrong_title" to "Wrong title or artist", "other" to "Something else")
    var reason by remember { mutableStateOf(reasons.first().first) }
    var note by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Report this song") },
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
                OutlinedTextField(value = note, onValueChange = { note = it.take(500) }, label = { Text("Details (optional)") })
            }
        },
        confirmButton = { TextButton(onClick = { onSend(reason, note.trim().ifEmpty { null }) }) { Text("Send") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
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
