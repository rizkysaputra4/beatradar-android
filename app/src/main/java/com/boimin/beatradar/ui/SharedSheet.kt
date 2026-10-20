package com.boimin.beatradar.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.boimin.beatradar.library.LibraryViewModel
import com.boimin.beatradar.net.ApiException
import com.boimin.beatradar.net.SharedSummary
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Titles and what each kind of shared data is, in plain words. */
internal val SHARED_LABELS = mapOf(
    "measurements" to ("Tempo measurements" to "Song name, BPM and time of each song this phone measured. Never audio."),
    "fingerprints" to ("Audio fingerprints" to "Number patterns that recognise a song. They can't be played back."),
    "clips" to ("Clips of unknown songs" to "12-second audio clips, only with Help name unknown songs on."),
    "log" to ("Log backup" to "Your log, so it's on all your phones."),
    "loves" to ("Loves" to "Songs you loved."),
    "tempo_votes" to ("Tempo votes" to "Your answers to \"Which tempo feels right?\"."),
    "name_proposals" to ("Names you suggested" to "Names you typed for unknown songs, for an admin to check."),
    "shared_sets" to ("Shared set links" to "Sets you shared as a link."),
    "synced_sets" to ("Sets backup" to "Your sets, so they're on all your phones."),
)

/**
 * "What I've shared": everything this phone (and the account) sent to the server and how long it's kept. Deleting
 * goes through an admin: the user emails a request with this phone's reference, and an admin deletes it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SharedSheet(load: suspend () -> SharedSummary, email: String?, onDismiss: () -> Unit) {
    var summary by remember { mutableStateOf<SharedSummary?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        try {
            summary = load()
        } catch (e: ApiException) {
            error = LibraryViewModel.describe(e)
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("What I've shared", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(
                "Everything this phone${if (summary?.items?.any { it.kind == "log" } == true) " and your account" else ""} sent to BeatRadar, and how long it's kept.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            val s = summary
            if (s == null && error == null) CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally).padding(16.dp))
            s?.items?.forEach { k ->
                val (title, what) = SHARED_LABELS[k.kind] ?: (k.kind to "")
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text("$title · ${k.count}", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                    Text(what, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        listOfNotNull(k.lastAt?.let { "Last ${date(it)}" }, k.kept).joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (s != null) {
                Text("Want something deleted?", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
                val to = s.deletionEmail
                if (to != null) {
                    Text(
                        "Email $to and say what to delete. Keep the reference below in the email so we can find your data:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(s.reference, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                    Button(onClick = { emailRequest(context, to, s.reference, email) }) { Text("Email a deletion request") }
                } else {
                    Text(
                        "Deletion requests aren't set up on this server yet. Your reference: ${s.reference}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    "To stop sharing from now on, turn off Contribute to the Library and Help name unknown songs in Settings.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Opens the email app with the request filled in: the reference, and the account email when signed in. */
private fun emailRequest(context: Context, to: String, reference: String, accountEmail: String?) {
    val body = buildString {
        appendLine("Please delete the following data I shared with BeatRadar:")
        appendLine()
        appendLine("(for example: all of it, or only tempo measurements, clips, loves…)")
        appendLine()
        appendLine("Reference: $reference")
        accountEmail?.let { appendLine("Account: $it") }
    }
    val intent = Intent(Intent.ACTION_SENDTO, "mailto:".toUri())
        .putExtra(Intent.EXTRA_EMAIL, arrayOf(to))
        .putExtra(Intent.EXTRA_SUBJECT, "BeatRadar deletion request")
        .putExtra(Intent.EXTRA_TEXT, body)
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "No email app. Write to $to with reference $reference", Toast.LENGTH_LONG).show()
    }
}

private fun date(iso: String): String = runCatching {
    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withZone(ZoneId.systemDefault()).format(Instant.parse(iso))
}.getOrDefault("")
