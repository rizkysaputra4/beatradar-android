package com.boimin.beatradar.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.boimin.beatradar.R
import com.boimin.beatradar.ui.theme.Brand
import java.util.Locale

/**
 * 👥 submitted · ✓ confirmed · ♥ loves (PLAN §8). [labelled] = the larger song-page version with words;
 * a song nobody has confirmed yet shows an outlined ✓ 0.
 */
@Composable
fun SongCounters(listeners: Int, confirmations: Int, loves: Int?, labelled: Boolean = false, modifier: Modifier = Modifier) {
    val iconSize = if (labelled) 20.dp else 15.dp
    val style = if (labelled) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.labelMedium
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val description = buildString {
        append("$listeners ${if (listeners == 1) "person" else "people"} measured it, $confirmations confirmed")
        if (loves != null) append(", $loves loves")
    }
    Row(
        modifier.semantics(mergeDescendants = true) { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(if (labelled) 16.dp else 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Counter(R.drawable.ic_groups, compactCount(listeners), if (labelled) "submitted" else null, muted, iconSize, style)
        Counter(
            if (confirmations > 0) R.drawable.ic_verified else R.drawable.ic_verified_outline,
            compactCount(confirmations),
            if (labelled) "confirmed" else null,
            if (confirmations > 0) MaterialTheme.colorScheme.primary else muted,
            iconSize,
            style,
        )
        if (loves != null) Counter(R.drawable.ic_favorite, compactCount(loves), null, if (loves > 0) Brand.Coral else muted, iconSize, style)
    }
}

@Composable
private fun Counter(icon: Int, value: String, label: String?, tint: Color, size: androidx.compose.ui.unit.Dp, style: androidx.compose.ui.text.TextStyle) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(painterResource(icon), contentDescription = null, tint = tint, modifier = Modifier.size(size))
        Spacer(Modifier.width(4.dp))
        Text(if (label != null) "$value $label" else value, style = style, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** 950 → "950", 1234 → "1.2k", 25300 → "25k", 1_500_000 → "1.5M". */
fun compactCount(n: Int): String = when {
    n < 1_000 -> n.toString()
    n < 10_000 -> trim(String.format(Locale.US, "%.1f", n / 1_000.0)) + "k"
    n < 1_000_000 -> "${n / 1_000}k"
    else -> trim(String.format(Locale.US, "%.1f", n / 1_000_000.0)) + "M"
}

private fun trim(s: String) = s.removeSuffix(".0")

/** "15 of 18 people measured within 2 BPM of this tempo", or a nudge when nobody has confirmed it yet. */
fun confirmationExplainer(listeners: Int, confirmations: Int): String = when {
    listeners <= 1 -> "Only one person has measured this song. Be the first to confirm it."
    else -> "$confirmations of $listeners people measured within 2 BPM of this tempo."
}
