package com.boimin.beatradar.rewards

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.unit.sp
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

/** Header chip: Coral dot + streak and all-time beats, e.g. "● 7d · 1,240". Opens the Rewards sheet. */
@Composable
fun BeatsChip(rewards: Rewards?, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(Brand.Coral),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            "${rewards?.streak ?: 0}d · ${number(rewards?.beatsAll ?: 0)}",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
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

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    ) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 32.dp),
        ) {
            val r = rewards
            val streakDays = r?.streak ?: 0L

            // Header Row: Title + Coral Streak Pill
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Your Beats",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Row(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Brand.Coral.copy(alpha = 0.14f))
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        painterResource(R.drawable.ic_fire),
                        contentDescription = null,
                        tint = Brand.Coral,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "$streakDays ${if (streakDays == 1L) "Day" else "Day"} Streak",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Brand.Coral,
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            // 2-Column Bento Stats: This Week & All Time
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                BentoStatCard(
                    label = "THIS WEEK",
                    value = number(r?.beatsWeek ?: 0),
                    sub = r?.rankWeek?.let { "Rank #$it" },
                    modifier = Modifier.weight(1f),
                )
                BentoStatCard(
                    label = "ALL TIME",
                    value = number(r?.beatsAll ?: 0),
                    sub = r?.rankAll?.let { "Rank #$it" },
                    modifier = Modifier.weight(1f),
                )
            }

            if (r != null && !r.signedIn) {
                Spacer(Modifier.height(12.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                        .clickable(onClick = onSignIn)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Text(
                        "Sign in to keep your beats and join the leaderboard →",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            Spacer(Modifier.height(18.dp))
            Text(
                "BADGES",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            BadgeGrid(r?.badges.orEmpty())

            Spacer(Modifier.height(18.dp))

            // Leaderboard Header + Compact Week / All Time Toggle
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "LEADERBOARD",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Row(
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .padding(2.dp),
                ) {
                    listOf(false to "Week", true to "All Time").forEach { (all, label) ->
                        val active = allTime == all
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (active) MaterialTheme.colorScheme.primary else Color.Transparent)
                                .clickable { allTime = all }
                                .padding(horizontal = 9.dp, vertical = 4.dp),
                        ) {
                            Text(
                                label,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            val b = board
            if (b != null) {
                if (!allTime) b.lastGrooveMaster?.let {
                    Text(
                        "Last week's Groove Master: $it",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                }
                if (b.entries.isEmpty()) {
                    Text(
                        "Nobody on the board yet. Measure a song to be first.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                val me = b.me
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    b.entries.forEach { entry ->
                        BoardRow(entry, highlight = me != null && entry.rank == me.rank && entry.nickname == me.nickname)
                    }
                    // The user's own row stays visible even outside the top 100.
                    if (me != null && b.entries.none { it.nickname == me.nickname }) {
                        HorizontalDivider(Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outline)
                        BoardRow(me, highlight = true)
                    }
                }
            }
        }
    }
}

@Composable
private fun BentoStatCard(label: String, value: String, sub: String?, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
            .padding(12.dp),
    ) {
        Text(
            label,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.7.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(3.dp))
        Text(
            value,
            fontSize = 22.sp,
            lineHeight = 25.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = (-0.8).sp,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (sub != null) {
            Spacer(Modifier.height(2.dp))
            Text(
                sub,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun BadgeGrid(badges: List<BadgeView>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        badges.chunked(3).forEach { row ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Max),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                row.forEach { BadgeTile(it, Modifier.weight(1f).fillMaxHeight()) }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun BadgeTile(badge: BadgeView, modifier: Modifier) {
    val isGrooveMaster = badge.earned && badge.code == "groove_master"
    val borderColor = if (isGrooveMaster) Brand.Coral.copy(alpha = 0.35f) else MaterialTheme.colorScheme.outline
    Column(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .alpha(if (badge.earned) 1f else 0.55f)
            .padding(horizontal = 6.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        BadgeIcon(badge.code, badge.tier, badge.earned, 34)
        Spacer(Modifier.height(5.dp))
        Text(
            badge.name + if (badge.count > 1) " ×${badge.count}" else "",
            fontSize = 10.sp,
            lineHeight = 12.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(5.dp))
        val next = badge.nextAt
        if (next != null && next > 0) {
            val ratio = (badge.progress.toFloat() / next).coerceIn(0f, 1f)
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Box(
                    Modifier
                        .fillMaxWidth(0.85f)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.outline),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(ratio)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(2.dp))
                            .background(MaterialTheme.colorScheme.primary),
                    )
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    "${number(badge.progress)} / ${number(next)}",
                    fontSize = 8.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            Text(
                if (badge.earned) "Earned" else "Locked",
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                color = if (isGrooveMaster) Brand.Coral else if (badge.earned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Badge art: a medal in the tier's metal (crown for Groove Master), grey while locked. */
@Composable
fun BadgeIcon(code: String, tier: Int, earned: Boolean, sizeDp: Int) {
    val tint = when {
        !earned -> MaterialTheme.colorScheme.onSurfaceVariant
        code == "groove_master" -> Brand.Coral
        tier >= 3 -> Color(0xFFD4AF37)
        tier == 2 -> Color(0xFF9EA3AD)
        else -> Color(0xFFC07A3C)
    }
    Box(
        Modifier
            .size(sizeDp.dp)
            .clip(CircleShape)
            .background(tint.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painterResource(if (code == "groove_master") R.drawable.ic_crown else if (code == "top_10") R.drawable.ic_trophy else R.drawable.ic_badge),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size((sizeDp * 0.56f).dp),
        )
    }
}

@Composable
private fun BoardRow(e: BoardEntry, highlight: Boolean) {
    val bg = if (highlight) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceContainerHigh
    val border = if (highlight) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f) else Color.Transparent
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "#${e.rank}",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.ExtraBold,
            color = if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(36.dp),
        )
        if (e.grooveMaster > 0) {
            Icon(
                painterResource(R.drawable.ic_crown),
                contentDescription = "Groove Master",
                tint = Brand.Coral,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(5.dp))
        }
        Text(
            if (highlight) "${e.nickname} (You)" else e.nickname,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            number(e.beats),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.ExtraBold,
            color = if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
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
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    ) {
        Column(Modifier.padding(start = 24.dp, end = 24.dp, bottom = 32.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(R.drawable.ic_account), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(profile.nickname, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                    Text("${number(profile.beatsAll)} beats", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (profile.grooveMaster > 0) Pill("Groove Master ×${profile.grooveMaster}")
            }
            Spacer(Modifier.height(16.dp))
            if (profile.badges.isEmpty()) Text("No badges yet", style = MaterialTheme.typography.bodyMedium)
            profile.badges.chunked(3).forEach { row ->
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { b ->
                        Column(
                            Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                                .padding(vertical = 10.dp, horizontal = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            BadgeIcon(b.code, b.tier, true, 36)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                b.name + if (b.count > 1) " ×${b.count}" else "",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                            )
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
