package com.boimin.beatradar.rewards

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.boimin.beatradar.R
import com.boimin.beatradar.i18n.appLocale
import com.boimin.beatradar.i18n.str
import com.boimin.beatradar.i18n.strPlural
import com.boimin.beatradar.net.BadgeView
import com.boimin.beatradar.ui.CalmBottomSheet
import com.boimin.beatradar.ui.rememberCalmSheetState
import com.boimin.beatradar.ui.theme.Brand
import com.boimin.beatradar.ui.theme.Tone
import java.text.NumberFormat
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** What the app knows about a badge: its glyph, and its name and description (translatable, unlike the server's). */
private class BadgeInfo(@DrawableRes val icon: Int, @StringRes val name: Int, @StringRes val description: Int)

private val INFO = mapOf(
    "first_beat" to BadgeInfo(R.drawable.badge_first_beat, R.string.badge_first_beat, R.string.badge_first_beat_desc),
    "tempo_keeper" to BadgeInfo(R.drawable.badge_tempo_keeper, R.string.badge_tempo_keeper, R.string.badge_tempo_keeper_desc),
    "trailblazer" to BadgeInfo(R.drawable.badge_trailblazer, R.string.badge_trailblazer, R.string.badge_trailblazer_desc),
    "on_beat" to BadgeInfo(R.drawable.badge_on_beat, R.string.badge_on_beat, R.string.badge_on_beat_desc),
    "full_spectrum" to BadgeInfo(R.drawable.badge_full_spectrum, R.string.badge_full_spectrum, R.string.badge_full_spectrum_desc),
    "crowd_pleaser" to BadgeInfo(R.drawable.badge_crowd_pleaser, R.string.badge_crowd_pleaser, R.string.badge_crowd_pleaser_desc),
    "fact_checker" to BadgeInfo(R.drawable.badge_fact_checker, R.string.badge_fact_checker, R.string.badge_fact_checker_desc),
    "top_10" to BadgeInfo(R.drawable.badge_top_10, R.string.badge_top_10, R.string.badge_top_10_desc),
    "groove_master" to BadgeInfo(R.drawable.badge_groove_master, R.string.badge_groove_master, R.string.badge_groove_master_desc),
    "cadence_runner" to BadgeInfo(R.drawable.badge_cadence_runner, R.string.badge_cadence_runner, R.string.badge_cadence_runner_desc),
    "cool_down" to BadgeInfo(R.drawable.badge_cool_down, R.string.badge_cool_down, R.string.badge_cool_down_desc),
    "collector" to BadgeInfo(R.drawable.badge_collector, R.string.badge_collector, R.string.badge_collector_desc),
    "marathon" to BadgeInfo(R.drawable.badge_marathon, R.string.badge_marathon, R.string.badge_marathon_desc),
    "podium" to BadgeInfo(R.drawable.badge_podium, R.string.badge_podium, R.string.badge_podium_desc),
    "founding" to BadgeInfo(R.drawable.badge_founding, R.string.badge_founding, R.string.badge_founding_desc),
    "supporter" to BadgeInfo(R.drawable.badge_supporter, R.string.badge_supporter, R.string.badge_supporter_desc),
)

private val BRONZE = Color(0xFFC98A4F)
private val SILVER = Color(0xFFB9BEC8)
private val GOLD = Color(0xFFE2B84A)
private val LOCKED = Color(0xFF5C5A6B)

private fun metal(tier: Int) = when {
    tier >= 3 -> GOLD
    tier == 2 -> SILVER
    else -> BRONZE
}

@Composable
private fun tierName(tier: Int) = str(
    when {
        tier >= 3 -> R.string.badge_tier_gold
        tier == 2 -> R.string.badge_tier_silver
        else -> R.string.badge_tier_bronze
    },
)

/** The badge's name in the app's language; the server's (English) name for a badge this version doesn't know. */
@Composable
fun badgeName(code: String, fallback: String): String = INFO[code]?.let { str(it.name) } ?: fallback

/** Has tiers (bronze, silver, gold), rather than being earned once or counted. */
private val BadgeView.tiered: Boolean get() = !repeatable && tiers.size > 1

/** The colour a badge shows in: its metal when tiered, coral for Groove Master, else violet; grey while locked. */
private fun color(code: String, tier: Int, earned: Boolean, tiered: Boolean, repeatable: Boolean): Color = when {
    !earned -> LOCKED
    code == "groove_master" -> Brand.Coral
    code == "top_10" -> GOLD
    code == "podium" -> SILVER
    tiered -> metal(tier)
    repeatable -> GOLD
    else -> Brand.Violet
}

/** Share of the way to the next tier, 0 when there's no next tier or nothing done yet. */
private val BadgeView.ratio: Float
    get() {
        val next = nextAt ?: return 0f
        if (next <= 0) return 0f
        val from = tiers.getOrNull(tier - 1) ?: 0L
        return ((progress - from).toFloat() / (next - from)).coerceIn(0f, 1f)
    }

/**
 * Badge art: a round tile with the badge's glyph. Earned, the ring is the badge's colour (bronze, silver or gold for
 * tiered badges, with dots for the tier); locked, the glyph is grey and a violet ring fills toward the next tier.
 * Weekly badges earned more than once show the count.
 */
@Composable
fun BadgeArt(code: String, tier: Int, sizeDp: Int, ratio: Float = 0f, tiered: Boolean = false, repeatable: Boolean = false, count: Int = 0) {
    val earned = tier > 0 || count > 0
    val col = color(code, tier, earned, tiered, repeatable)
    val track = Tone.track
    Box(Modifier.size(sizeDp.dp)) {
        Canvas(Modifier.fillMaxSize()) {
            val u = size.minDimension / 64f
            val c = Offset(size.width / 2, size.height / 2)
            drawCircle(if (earned) col.copy(alpha = 0.16f) else track, radius = 23 * u, center = c)
            val ring = Stroke(width = 3 * u, cap = StrokeCap.Round)
            if (earned) {
                drawCircle(col, radius = 27 * u, center = c, style = ring)
            } else {
                drawCircle(Color(0xFF2A2935), radius = 27 * u, center = c, style = Stroke(width = 3 * u))
                if (ratio > 0f) {
                    drawArc(
                        Brand.Violet, startAngle = -90f, sweepAngle = 360f * ratio, useCenter = false,
                        topLeft = Offset(c.x - 27 * u, c.y - 27 * u), size = Size(54 * u, 54 * u), style = ring,
                    )
                }
            }
        }
        Icon(
            painterResource(INFO[code]?.icon ?: R.drawable.badge_first_beat),
            contentDescription = null,
            tint = col,
            modifier = Modifier.align(Alignment.Center).size((sizeDp * 24 / 64).dp),
        )
        // Tier dots on a small plate at the bottom of the ring.
        if (tiered && earned) {
            Row(
                Modifier
                    .align(Alignment.BottomCenter)
                    .offset(y = (-sizeDp * 1.5f / 64).dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color(0xFF0F0E15))
                    .padding(horizontal = (sizeDp * 3f / 64).dp, vertical = (sizeDp * 2f / 64).dp),
                horizontalArrangement = Arrangement.spacedBy((sizeDp * 2f / 64).dp),
            ) {
                repeat(3) { i ->
                    Box(Modifier.size((sizeDp * 4f / 64).dp).clip(CircleShape).background(if (i < tier) col else Color(0xFF3A3846)))
                }
            }
        }
        if (repeatable && count > 1) {
            Box(
                Modifier.align(Alignment.TopEnd).clip(RoundedCornerShape(50)).background(col).padding(horizontal = 5.dp, vertical = 1.dp),
            ) {
                Text(str(R.string.badge_times_short, count), fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF0C0B10))
            }
        }
    }
}

/** [BadgeArt] for a badge from the server. */
@Composable
fun BadgeArt(badge: BadgeView, sizeDp: Int) =
    BadgeArt(badge.code, badge.tier, sizeDp, badge.ratio, badge.tiered, badge.repeatable, badge.count)

/** Earned badges first (highest tier first), then the locked ones closest to done. */
fun sortBadges(badges: List<BadgeView>): List<BadgeView> =
    badges.sortedWith(compareByDescending<BadgeView> { it.earned || it.count > 0 }.thenByDescending { it.tier }.thenByDescending { it.ratio })

/** The 4-column badge grid of the Rewards sheet. */
@Composable
fun BadgeGrid(badges: List<BadgeView>, onOpen: (BadgeView) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        sortBadges(badges).chunked(4).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                row.forEach { BadgeTile(it, Modifier.weight(1f), onClick = { onOpen(it) }) }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun BadgeTile(badge: BadgeView, modifier: Modifier, onClick: () -> Unit) {
    val earned = badge.earned || badge.count > 0
    val next = badge.nextAt
    val sub = when {
        badge.repeatable -> if (badge.count > 0) str(R.string.badge_times_short, badge.count) else str(R.string.badge_locked)
        next != null && next > 1 -> str(R.string.badge_progress, number(badge.progress), number(next))
        badge.tiered && earned -> tierName(badge.tier)
        earned -> str(R.string.badge_earned)
        else -> str(R.string.badge_locked)
    }
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(top = 8.dp, bottom = 6.dp, start = 2.dp, end = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.alpha(if (earned || badge.ratio > 0f) 1f else 0.7f)) { BadgeArt(badge, 56) }
        Spacer(Modifier.height(4.dp))
        Box(Modifier.height(28.dp), contentAlignment = Alignment.Center) {
            Text(
                badgeName(badge.code, badge.name),
                fontSize = 11.sp,
                lineHeight = 13.sp,
                fontWeight = FontWeight.Bold,
                color = if (earned) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            sub,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (badge.tiered && badge.tier >= 3) GOLD else Tone.text3,
            maxLines = 1,
        )
    }
}

/** Tap a badge: what it's for, its tiers with what each takes, and how far to the next one. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BadgeDetailSheet(badge: BadgeView, onDismiss: () -> Unit) {
    val earned = badge.earned || badge.count > 0
    val col = color(badge.code, badge.tier, earned, badge.tiered, badge.repeatable)
    CalmBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberCalmSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = Tone.sheet,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 24.dp)) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                BadgeArt(badge, 112)
                Spacer(Modifier.height(10.dp))
                Text(badgeName(badge.code, badge.name), fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(8.dp))
                val status = when {
                    badge.repeatable && badge.count > 0 -> strPlural(R.plurals.badge_detail_times, badge.count, badge.count)
                    !earned -> str(R.string.badge_locked)
                    else -> {
                        val what = if (badge.tiered) tierName(badge.tier) else str(R.string.badge_earned)
                        date(badge.earnedAt)?.let { str(R.string.badge_detail_since, what, it) } ?: what
                    }
                }
                Box(
                    Modifier.clip(RoundedCornerShape(50)).background(if (earned) col else Tone.pill).padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Text(
                        status.uppercase(appLocale()),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.6.sp,
                        color = if (earned) Color(0xFF0C0B10) else Tone.text2,
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    INFO[badge.code]?.let { str(it.description) } ?: badge.description,
                    fontSize = 14.sp,
                    lineHeight = 21.sp,
                    color = Tone.text2,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
            }

            if (badge.tiered) {
                Spacer(Modifier.height(20.dp))
                Text(str(R.string.badge_detail_tiers), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp, color = Tone.text3)
                Spacer(Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    badge.tiers.forEachIndexed { i, need ->
                        val t = i + 1
                        val reached = badge.tier >= t
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Tone.track).padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.size(10.dp).clip(CircleShape).background(metal(t)))
                            Spacer(Modifier.width(12.dp))
                            Text(tierName(t), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            Text(number(need), fontSize = 13.sp, color = Tone.text3)
                            Spacer(Modifier.width(10.dp))
                            when {
                                reached -> Icon(painterResource(R.drawable.ic_check), contentDescription = null, tint = Brand.Mint, modifier = Modifier.size(16.dp))
                                t == badge.tier + 1 -> Text(number(badge.progress), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Tone.primarySoft)
                                else -> Spacer(Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }

            val next = badge.nextAt
            if (next != null && next > 1 && !badge.repeatable) {
                Spacer(Modifier.height(14.dp))
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Tone.track).padding(14.dp),
                ) {
                    Row {
                        Text(
                            if (badge.tiered) str(R.string.badge_detail_to, tierName(badge.tier + 1)) else str(R.string.badge_detail_to_earn),
                            fontSize = 13.sp,
                            color = Tone.text2,
                            modifier = Modifier.weight(1f),
                        )
                        Text(str(R.string.badge_detail_more, number((next - badge.progress).coerceAtLeast(0))), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(8.dp))
                    Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(Color(0xFF2F2E3B))) {
                        Box(Modifier.fillMaxWidth(badge.ratio).height(6.dp).clip(RoundedCornerShape(3.dp)).background(Brand.Violet))
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.primary)
                    .clickable(onClick = onDismiss),
                contentAlignment = Alignment.Center,
            ) {
                Text(str(R.string.badge_detail_done), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
            }
        }
    }
}

/** "14 Oct 2026" in the app's language, or null when the server sent no (or an unreadable) date. */
@Composable
private fun date(iso: String?): String? {
    val locale = appLocale()
    return iso?.let { runCatching { OffsetDateTime.parse(it).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)) }.getOrNull() }
}

private fun number(n: Long): String = NumberFormat.getIntegerInstance().format(n)
