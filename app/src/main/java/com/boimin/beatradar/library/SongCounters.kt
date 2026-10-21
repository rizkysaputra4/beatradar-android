package com.boimin.beatradar.library

import android.content.Context
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
import com.boimin.beatradar.i18n.str
import com.boimin.beatradar.i18n.strPlural
import com.boimin.beatradar.ui.theme.Brand
import java.util.Locale

/**
 * 👥 submitted · ✓ confirmed · ♥ loves (PLAN §8). [labelled] = the larger song-page version with words;
 * a song nobody has confirmed yet shows an outlined ✓ 0.
 */
@Composable
fun SongCounters(listeners: Int, confirmations: Int, loves: Int?, labelled: Boolean = false, modifier: Modifier = Modifier) {
    val iconSize = if (labelled) 18.dp else 13.dp
    val style = if (labelled) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.labelSmall
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val description = if (loves != null) {
        strPlural(R.plurals.song_counters_description_loves, listeners, listeners, confirmations, loves)
    } else {
        strPlural(R.plurals.song_counters_description, listeners, listeners, confirmations)
    }
    Row(
        modifier.semantics(mergeDescendants = true) { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(if (labelled) 14.dp else 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (labelled) {
            Counter(R.drawable.ic_groups, str(R.string.song_counter_submitted, compactCount(listeners)), muted, iconSize, style)
        }
        Counter(
            if (confirmations > 0) R.drawable.ic_verified else R.drawable.ic_verified_outline,
            if (labelled) str(R.string.song_counter_confirmed, compactCount(confirmations)) else compactCount(confirmations),
            if (confirmations > 0) Brand.Success else muted,
            iconSize,
            style,
        )
        if (loves != null) {
            Counter(
                R.drawable.ic_favorite,
                compactCount(loves),
                if (loves > 0) Brand.Coral else muted,
                iconSize,
                style,
            )
        }
    }
}

@Composable
private fun Counter(icon: Int, text: String, tint: Color, size: androidx.compose.ui.unit.Dp, style: androidx.compose.ui.text.TextStyle) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(painterResource(icon), contentDescription = null, tint = tint, modifier = Modifier.size(size))
        Spacer(Modifier.width(3.dp))
        Text(text, style = style, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

