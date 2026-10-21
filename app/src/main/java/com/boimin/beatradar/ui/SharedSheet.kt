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
import com.boimin.beatradar.R
import com.boimin.beatradar.i18n.str
import com.boimin.beatradar.library.LibraryViewModel
import com.boimin.beatradar.net.ApiException
import com.boimin.beatradar.net.SharedSummary
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Titles and what each kind of shared data is, in plain words (string resources). */
internal val SHARED_LABELS = mapOf(
    "measurements" to (R.string.shared_kind_measurements to R.string.shared_kind_measurements_desc),
    "fingerprints" to (R.string.shared_kind_fingerprints to R.string.shared_kind_fingerprints_desc),
    "clips" to (R.string.shared_kind_clips to R.string.shared_kind_clips_desc),
    "log" to (R.string.shared_kind_log to R.string.shared_kind_log_desc),
    "loves" to (R.string.shared_kind_loves to R.string.shared_kind_loves_desc),
    "tempo_votes" to (R.string.shared_kind_tempo_votes to R.string.shared_kind_tempo_votes_desc),
    "name_proposals" to (R.string.shared_kind_name_proposals to R.string.shared_kind_name_proposals_desc),
    "shared_sets" to (R.string.shared_kind_shared_sets to R.string.shared_kind_shared_sets_desc),
    "synced_sets" to (R.string.shared_kind_synced_sets to R.string.shared_kind_synced_sets_desc),
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
            error = LibraryViewModel.describe(context, e)
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetGesturesEnabled = false, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(str(R.string.shared_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(
                str(if (summary?.items?.any { it.kind == "log" } == true) R.string.shared_intro_with_account else R.string.shared_intro),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            val s = summary
            if (s == null && error == null) CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally).padding(16.dp))
            s?.items?.forEach { k ->
                val labels = SHARED_LABELS[k.kind]
                val title = labels?.let { str(it.first) } ?: k.kind
                val what = labels?.let { str(it.second) }.orEmpty()
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
                        listOfNotNull(k.lastAt?.let { str(R.string.shared_last_at, date(it)) }, k.kept).joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (s != null) {
                Text(str(R.string.shared_delete_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
                val to = s.deletionEmail
                if (to != null) {
                    Text(
                        str(R.string.shared_delete_email, to),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(s.reference, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                    Button(onClick = { emailRequest(context, to, s.reference, email) }) { Text(str(R.string.shared_delete_button)) }
                } else {
                    Text(
                        str(R.string.shared_delete_not_set_up, s.reference),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    str(R.string.shared_stop_sharing),
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
        appendLine(context.str(R.string.shared_email_intro))
        appendLine()
        appendLine(context.str(R.string.shared_email_example))
        appendLine()
        appendLine(context.str(R.string.shared_email_reference, reference))
        accountEmail?.let { appendLine(context.str(R.string.shared_email_account, it)) }
    }
    val intent = Intent(Intent.ACTION_SENDTO, "mailto:".toUri())
        .putExtra(Intent.EXTRA_EMAIL, arrayOf(to))
        .putExtra(Intent.EXTRA_SUBJECT, context.str(R.string.shared_email_subject))
        .putExtra(Intent.EXTRA_TEXT, body)
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, context.str(R.string.shared_no_email_app, to, reference), Toast.LENGTH_LONG).show()
    }
}

private fun date(iso: String): String = runCatching {
    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withZone(ZoneId.systemDefault()).format(Instant.parse(iso))
}.getOrDefault("")
