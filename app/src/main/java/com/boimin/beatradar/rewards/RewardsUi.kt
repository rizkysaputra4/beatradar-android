package com.boimin.beatradar.rewards

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.boimin.beatradar.R
import com.boimin.beatradar.account.AccountViewModel
import com.boimin.beatradar.library.Pill
import com.boimin.beatradar.net.BadgeView
import com.boimin.beatradar.net.BoardEntry
import com.boimin.beatradar.net.PublicProfile
import com.boimin.beatradar.net.Rewards
import com.boimin.beatradar.ui.theme.Brand
import java.text.NumberFormat

/** Header chip: streak and all-time beats, e.g. "🔥 7 · 1,240". Opens the Rewards sheet. */
@Composable
fun BeatsChip(rewards: Rewards?, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(R.drawable.ic_fire), contentDescription = "Streak", tint = Brand.Coral, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(4.dp))
        Text(
            "${rewards?.streak ?: 0} · ${number(rewards?.beatsAll ?: 0)}",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier,
        )
    }
}

/** Beats, rank, streak, badges and the leaderboards (PLAN §7b). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RewardsSheet(vm: AccountViewModel, onSignIn: () -> Unit, onDismiss: () -> Unit) {
    val rewards by vm.rewards.collectAsStateWithLifecycle()
    val board by vm.board.collectAsStateWithLifecycle()
    var allTime by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) { vm.loadRewards() }
    LaunchedEffect(allTime) { vm.loadBoard(allTime) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, bottom = 32.dp)) {
            Text("Your beats", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(16.dp))
            val r = rewards
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Stat("This week", number(r?.beatsWeek ?: 0), r?.rankWeek?.let { "#$it" })
                Stat("All time", number(r?.beatsAll ?: 0), r?.rankAll?.let { "#$it" })
                Stat("Streak", "${r?.streak ?: 0}", if ((r?.streak ?: 0) == 1L) "day" else "days")
            }
            if (r != null && !r.signedIn) {
                Spacer(Modifier.height(12.dp))
                Text(
                    "Sign in to keep your beats and join the leaderboard",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onSignIn).padding(vertical = 4.dp),
                )
            }
            Spacer(Modifier.height(20.dp))
            Text("Badges", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(10.dp))
            BadgeGrid(r?.badges.orEmpty())
            Spacer(Modifier.height(20.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))
            Text("Leaderboard", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(10.dp))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                listOf(false to "This week", true to "All time").forEachIndexed { i, (all, label) ->
                    SegmentedButton(selected = allTime == all, onClick = { allTime = all }, shape = SegmentedButtonDefaults.itemShape(i, 2)) { Text(label) }
                }
            }
            Spacer(Modifier.height(8.dp))
            val b = board
            if (b != null) {
                if (!allTime) b.lastGrooveMaster?.let {
                    Text("Last week's Groove Master: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(6.dp))
                }
                if (b.entries.isEmpty()) {
                    Text("Nobody on the board yet. Measure a song to be first.", style = MaterialTheme.typography.bodyMedium)
                }
                val me = b.me
                b.entries.forEach { BoardRow(it, highlight = me != null && it.rank == me.rank && it.nickname == me.nickname) }
                // The user's own row stays visible even outside the top 100.
                if (me != null && b.entries.none { it.nickname == me.nickname }) {
                    HorizontalDivider(Modifier.padding(vertical = 4.dp))
                    BoardRow(me, highlight = true)
                }
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String, sub: String?) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (sub != null) Text(sub, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun BadgeGrid(badges: List<BadgeView>) {
    badges.chunked(3).forEach { row ->
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            row.forEach { BadgeTile(it, Modifier.weight(1f)) }
            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun BadgeTile(badge: BadgeView, modifier: Modifier) {
    Column(modifier.alpha(if (badge.earned) 1f else 0.45f), horizontalAlignment = Alignment.CenterHorizontally) {
        BadgeIcon(badge.code, badge.tier, badge.earned, 44)
        Spacer(Modifier.height(4.dp))
        Text(
            badge.name + if (badge.count > 1) " ×${badge.count}" else "",
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        val next = badge.nextAt
        if (next != null && next > 0) {
            Spacer(Modifier.height(4.dp))
            LinearProgressIndicator(progress = { (badge.progress.toFloat() / next).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth(0.8f))
            Text("${number(badge.progress)} / ${number(next)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Badge art: a medal in the tier's metal (crown for Groove Master), grey while locked. */
@Composable
fun BadgeIcon(code: String, tier: Int, earned: Boolean, sizeDp: Int) {
    val tint = when {
        !earned -> MaterialTheme.colorScheme.outline
        code == "groove_master" -> Brand.Coral
        tier >= 3 -> Color(0xFFD4AF37)
        tier == 2 -> Color(0xFF9EA3AD)
        else -> Color(0xFFC07A3C)
    }
    Box(
        Modifier.size(sizeDp.dp).clip(CircleShape).background(tint.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painterResource(if (code == "groove_master") R.drawable.ic_crown else if (code == "top_10") R.drawable.ic_trophy else R.drawable.ic_badge),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size((sizeDp * 0.6f).dp),
        )
    }
}

@Composable
private fun BoardRow(e: BoardEntry, highlight: Boolean) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (highlight) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("#${e.rank}", style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(48.dp))
        if (e.grooveMaster > 0) {
            Icon(painterResource(R.drawable.ic_crown), contentDescription = "Groove Master", tint = Brand.Coral, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
        }
        Text(e.nickname, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(number(e.beats), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
    }
}

/** Shown once on app open for badges earned since the last look. */
@Composable
fun BadgeCelebration(names: List<String>, onDone: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDone,
        icon = { BadgeIcon("new", 1, true, 56) },
        title = { Text(if (names.size == 1) "New badge!" else "${names.size} new badges!") },
        text = { Text(names.joinToString(" · "), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
        confirmButton = { TextButton(onClick = onDone) { Text("Nice") } },
    )
}

/** Someone's public profile: nickname, beats, badges. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileSheet(profile: PublicProfile, onReport: () -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(start = 24.dp, end = 24.dp, bottom = 32.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(R.drawable.ic_account), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(profile.nickname, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text("${number(profile.beatsAll)} beats", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (profile.grooveMaster > 0) Pill("Groove Master ×${profile.grooveMaster}")
            }
            Spacer(Modifier.height(16.dp))
            if (profile.badges.isEmpty()) Text("No badges yet", style = MaterialTheme.typography.bodyMedium)
            profile.badges.chunked(3).forEach { row ->
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { b ->
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            BadgeIcon(b.code, b.tier, true, 40)
                            Text(b.name + if (b.count > 1) " ×${b.count}" else "", style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
                        }
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onReport) { Text("Report this nickname") }
        }
    }
}

private fun number(n: Long): String = NumberFormat.getIntegerInstance().format(n)
