package com.boimin.beatradar.rewards

import com.boimin.beatradar.ui.CalmBottomSheet
import com.boimin.beatradar.ui.rememberCalmSheetState
import com.boimin.beatradar.ui.theme.Tone
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.boimin.beatradar.BuildConfig
import com.boimin.beatradar.R
import coil3.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import com.boimin.beatradar.account.AccountViewModel
import com.boimin.beatradar.i18n.appLocale
import com.boimin.beatradar.i18n.str
import com.boimin.beatradar.i18n.strPlural
import com.boimin.beatradar.net.BadgeView
import com.boimin.beatradar.net.BoardEntry
import com.boimin.beatradar.net.Discovery
import com.boimin.beatradar.net.Rewards
import com.boimin.beatradar.ui.theme.Brand
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.roundToInt
import com.boimin.beatradar.ui.Skeleton
import com.boimin.beatradar.ui.SkeletonBox

/**
 * How big the header flame is: it grows a step at a time with the beats earned by contributing, so the chip itself
 * shows how much someone has given to the Library.
 */
object Flame {
    /** Beats needed for each next size. */
    val STEPS = longArrayOf(1, 25, 100, 300, 1_000, 3_000, 10_000)
    const val MIN_DP = 12
    const val STEP_DP = 2

    fun level(beats: Long): Int = STEPS.count { beats >= it }

    fun sizeDp(beats: Long): Int = MIN_DP + STEP_DP * level(beats)
}

/**
 * Header chip: a flame that grows with contributions, then streak and all-time beats, e.g. "🔥 7d · 1,240". [compact]
 * (a narrow header) leaves out the beats, which the sheet behind the chip shows anyway.
 */
@Composable
fun BeatsChip(rewards: Rewards?, compact: Boolean = false, onClick: () -> Unit) {
    val beats = rewards?.beatsAll ?: 0L
    val size by animateDpAsState(Flame.sizeDp(beats).dp, spring(dampingRatio = 0.5f, stiffness = 300f), label = "flame")
    // A quick pop each time new beats come in while the app is open.
    val pop = remember { Animatable(1f) }
    var lastBeats by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(beats) {
        val before = lastBeats
        lastBeats = beats
        if (before != null && beats > before) {
            pop.animateTo(1.35f, tween(140))
            pop.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = 400f))
        }
    }
    // Design: a 38 dp chip, the day streak by a flame and all-time beats by a note.
    Row(
        Modifier
            .height(38.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Tone.chip)
            .border(1.dp, Tone.line, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = if (compact) 8.dp else 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painterResource(R.drawable.ic_fire),
            contentDescription = str(R.string.rewards_chip_streak_description),
            tint = if (beats > 0) Brand.Flame else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.size(18.dp).scale(pop.value),
        )
        Spacer(Modifier.width(3.dp))
        Text("${rewards?.streak ?: 0}", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
        if (!compact) {
            Spacer(Modifier.width(10.dp))
            Icon(painterResource(R.drawable.ic_note), contentDescription = str(R.string.rewards_chip_beats_description), tint = Tone.primarySoft, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(2.dp))
            Text(number(beats), fontSize = 14.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

/** Beats, rank, streak, badges and the leaderboards (PLAN §7b). A row on the board opens that person's profile. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RewardsSheet(
    vm: AccountViewModel,
    onSignIn: () -> Unit,
    onOpenSong: (String) -> Unit,
    onOpenProfile: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val rewards by vm.rewards.collectAsStateWithLifecycle()
    val discoveries by vm.discoveries.collectAsStateWithLifecycle()
    val board by vm.board.collectAsStateWithLifecycle()
    var allTime by rememberSaveable { mutableStateOf(false) }
    // The badge whose detail is open, by code so it follows fresh progress.
    var opened by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { vm.loadRewards() }
    LaunchedEffect(allTime) { vm.loadBoard(allTime) }

    CalmBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberCalmSheetState(skipPartiallyExpanded = true),
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
                    str(R.string.rewards_title),
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
                        strPlural(R.plurals.rewards_streak_days, streakDays.toInt(), streakDays),
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
                    label = str(R.string.rewards_this_week),
                    value = r?.let { number(it.beatsWeek) },
                    sub = r?.rankWeek?.let { str(R.string.rewards_rank, it) },
                    modifier = Modifier.weight(1f),
                )
                BentoStatCard(
                    label = str(R.string.rewards_all_time),
                    value = r?.let { number(it.beatsAll) },
                    sub = r?.rankAll?.let { str(R.string.rewards_rank, it) },
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
                        str(R.string.rewards_sign_in_prompt),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            Spacer(Modifier.height(18.dp))
            val badges = r?.badges.orEmpty()
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    str(R.string.rewards_badges_header),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (badges.isNotEmpty()) {
                    Text(
                        str(R.string.badge_count, badges.count { it.earned || it.count > 0 }, badges.size),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Tone.primarySoft,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            BadgeGrid(badges, onOpen = { opened = it.code })

            discoveries?.takeIf { it.total > 0 }?.let { d ->
                Spacer(Modifier.height(18.dp))
                Text(
                    str(R.string.rewards_brought_in_header, number(d.total)),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    d.items.take(DISCOVERIES_SHOWN).forEach { DiscoveryRow(it, onClick = { onOpenSong(it.slug) }) }
                }
            }

            Spacer(Modifier.height(18.dp))

            // Leaderboard Header + Compact Week / All Time Toggle
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    str(R.string.rewards_leaderboard_header),
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
                    listOf(false to str(R.string.rewards_toggle_week), true to str(R.string.rewards_toggle_all_time)).forEach { (all, label) ->
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
            if (b == null) {
                Skeleton {
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        repeat(5) { SkeletonBox(Modifier.fillMaxWidth().height(34.dp), corner = 10.dp) }
                    }
                }
            } else {
                if (!allTime) b.lastGrooveMaster?.let {
                    Text(
                        str(R.string.rewards_last_groove_master, it),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                }
                if (b.entries.isEmpty()) {
                    Text(
                        str(R.string.rewards_board_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                val me = b.me
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    b.entries.forEach { entry ->
                        BoardRow(entry, highlight = me != null && entry.rank == me.rank && entry.nickname == me.nickname, onClick = { onOpenProfile(entry.nickname) })
                    }
                    // The user's own row stays visible even outside the top 100.
                    if (me != null && b.entries.none { it.nickname == me.nickname }) {
                        HorizontalDivider(Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outline)
                        BoardRow(me, highlight = true, onClick = { onOpenProfile(me.nickname) })
                    }
                }
            }
        }
    }
    rewards?.badges?.firstOrNull { it.code == opened }?.let { BadgeDetailSheet(it, onDismiss = { opened = null }) }
}

private val AVATAR_COLORS = listOf(Brand.Violet, Brand.Coral, Brand.MintDeep, Brand.Indigo)

/**
 * A nickname's avatar: the stock picture at [avatarUrl] (a path on the API host) when there is one, over the
 * nickname's first letter on a colour picked from the nickname, which shows while it loads or when there is none.
 */
@Composable
fun ProfileAvatar(nickname: String?, size: Int, avatarUrl: String? = null) {
    val bg = nickname?.let { AVATAR_COLORS[Math.floorMod(it.lowercase(Locale.ROOT).hashCode(), AVATAR_COLORS.size)] }
        ?: MaterialTheme.colorScheme.primary
    Box(Modifier.size(size.dp).clip(CircleShape).background(bg), contentAlignment = Alignment.Center) {
        Text(
            nickname?.firstOrNull()?.uppercase(appLocale()) ?: "?",
            fontSize = (size * 0.42f).sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
        )
        mediaUrl(avatarUrl)?.let {
            AsyncImage(model = it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
        }
    }
}

/** A stock picture path from the API (`/media/…`) as a full URL. */
fun mediaUrl(path: String?): String? = path?.let { BuildConfig.API_BASE_URL.trimEnd('/') + it }

@Composable
private fun BentoStatCard(label: String, value: String?, sub: String?, modifier: Modifier = Modifier) {
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
        // Still loading: a bar the height of the number.
        if (value == null) Skeleton { SkeletonBox(Modifier.padding(vertical = 3.dp).size(width = 72.dp, height = 19.dp)) } else Text(
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

private const val DISCOVERIES_SHOWN = 5

@Composable
internal fun DiscoveryRow(d: Discovery, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(d.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                str(R.string.rewards_discovery_artist_bpm, d.artist, d.bpm.roundToInt()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                strPlural(R.plurals.rewards_discovery_listeners, d.listeners, number(d.listeners.toLong())),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (d.milestone != null) Brand.Coral else MaterialTheme.colorScheme.onSurface,
            )
            d.milestone?.let { Text(str(R.string.rewards_discovery_milestone, number(it.toLong())), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
private fun BoardRow(e: BoardEntry, highlight: Boolean, onClick: () -> Unit) {
    val bg = if (highlight) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceContainerHigh
    val border = if (highlight) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f) else Color.Transparent
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            str(R.string.rewards_board_rank, e.rank),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.ExtraBold,
            color = if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(36.dp),
        )
        ProfileAvatar(e.nickname, 26, e.avatarUrl)
        Spacer(Modifier.width(8.dp))
        if (e.grooveMaster > 0) {
            Icon(
                painterResource(R.drawable.ic_crown),
                contentDescription = str(R.string.rewards_groove_master),
                tint = Brand.Coral,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(5.dp))
        }
        Text(
            (if (highlight) str(R.string.rewards_board_you, e.nickname) else e.nickname) + if (e.supporter) " ☕" else "",
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
fun BadgeCelebration(badges: List<BadgeView>, onDone: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDone,
        icon = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { badges.take(3).forEach { BadgeArt(it, 64) } }
        },
        title = { Text(strPlural(R.plurals.badge_celebration_title, badges.size, badges.size)) },
        text = {
            Text(badges.map { badgeName(it.code, it.name) }.joinToString(" · "), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        },
        confirmButton = { TextButton(onClick = onDone) { Text(str(R.string.badge_celebration_ok)) } },
    )
}

private fun number(n: Long): String = NumberFormat.getIntegerInstance().format(n)
