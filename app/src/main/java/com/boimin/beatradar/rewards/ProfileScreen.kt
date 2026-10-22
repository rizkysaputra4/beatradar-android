package com.boimin.beatradar.rewards

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.TextAutoSize
import com.boimin.beatradar.R
import com.boimin.beatradar.data.SetSummary
import com.boimin.beatradar.i18n.appLocale
import com.boimin.beatradar.i18n.str
import com.boimin.beatradar.i18n.strPlural
import com.boimin.beatradar.library.BarIcon
import com.boimin.beatradar.library.Message
import com.boimin.beatradar.library.ProfilePage
import com.boimin.beatradar.net.MyProfile
import com.boimin.beatradar.net.ProfileArt
import com.boimin.beatradar.net.ProfileSet
import com.boimin.beatradar.net.ProfileUpdate
import com.boimin.beatradar.net.PublicProfile
import com.boimin.beatradar.ui.CalmBottomSheet
import com.boimin.beatradar.ui.NicknameDialog
import com.boimin.beatradar.ui.rememberCalmSheetState
import com.boimin.beatradar.ui.Skeleton
import com.boimin.beatradar.ui.SkeletonBox
import com.boimin.beatradar.ui.theme.Brand
import com.boimin.beatradar.ui.theme.Tone
import java.text.NumberFormat
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.first

/** What a profile page can ask for. */
class ProfileActions(
    val onBack: () -> Unit,
    val onSettings: () -> Unit,
    val onSignIn: () -> Unit,
    val onRetry: () -> Unit,
    val onOpenSong: (String) -> Unit,
    val onOpenSet: (String) -> Unit,
    /** Beats, all badges with their progress, and the leaderboards. */
    val onOpenRewards: () -> Unit,
    val onReport: () -> Unit,
    val onRename: (String) -> Unit,
    val onSections: (ProfileUpdate) -> Unit,
    val onPin: (slug: String, pinned: Boolean) -> Unit,
    /** My sets on this phone, to pick one for the profile. */
    val mySets: List<SetSummary>,
    /** Shares the set and pins it to my profile. */
    val onShowSet: (setId: Long) -> Unit,
    /** Near the end of the page: the next page of pinned sets. */
    val onMoreSets: () -> Unit,
    /** The stock pictures for the picker (null until [onLoadArt] has loaded them). */
    val art: ProfileArt?,
    val onLoadArt: () -> Unit,
)

/**
 * A profile page. Someone else's shows what they made public; mine shows everything, each opt-in section with a chip
 * for whether others see it, and Settings in the bar. Guests get a way to sign in.
 */
@Composable
fun ProfileScreen(page: ProfilePage, signedIn: Boolean, a: ProfileActions, modifier: Modifier = Modifier) {
    val p = page.mine?.profile ?: page.profile
    var menu by remember { mutableStateOf(false) }
    Column(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp).height(52.dp), verticalAlignment = Alignment.CenterVertically) {
            BarIcon(R.drawable.ic_arrow_back, str(R.string.library_back), 24, MaterialTheme.colorScheme.onSurface, a.onBack)
            Text(
                str(R.string.profile_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f).padding(start = 4.dp),
            )
            if (page.isMe) {
                BarIcon(R.drawable.ic_settings, str(R.string.app_settings), 22, Tone.text2, a.onSettings)
            } else if (p != null) {
                Box {
                    BarIcon(R.drawable.ic_more, str(R.string.library_more), 22, Tone.text2) { menu = true }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text(str(R.string.rewards_profile_report)) }, onClick = { menu = false; a.onReport() })
                    }
                }
            }
        }
        when {
            page.isMe && !signedIn -> GuestProfile(a)
            p == null && page.error != null -> Message(page.error, "", retry = a.onRetry)
            p == null -> ProfileSkeleton()
            else -> ProfileBody(p, page.mine, page.sets, page.setsNext != null, a)
        }
    }
}

@Composable
private fun GuestProfile(a: ProfileActions) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 100.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(28.dp))
        AvatarRing(null, 88)
        Spacer(Modifier.height(16.dp))
        Text(str(R.string.profile_guest_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(6.dp))
        Text(
            str(R.string.profile_guest_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(18.dp))
        Button(onClick = a.onSignIn) { Text(str(R.string.settings_account_sign_in)) }
        TextButton(onClick = a.onOpenRewards) { Text(str(R.string.profile_all_badges)) }
    }
}

@Composable
private fun ProfileSkeleton() {
    Skeleton(Modifier.padding(horizontal = 20.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Spacer(Modifier.height(16.dp))
            SkeletonBox(Modifier.size(88.dp), corner = 44.dp)
            SkeletonBox(Modifier.size(width = 180.dp, height = 24.dp))
            SkeletonBox(Modifier.fillMaxWidth().height(72.dp), corner = 18.dp)
            SkeletonBox(Modifier.fillMaxWidth().height(120.dp), corner = 18.dp)
        }
    }
}

@Composable
private fun ProfileBody(p: PublicProfile, mine: MyProfile?, sets: List<ProfileSet>, moreSets: Boolean, a: ProfileActions) {
    var renaming by rememberSaveable { mutableStateOf(false) }
    var picking by rememberSaveable { mutableStateOf(false) }
    var choosing by rememberSaveable { mutableStateOf(false) }
    val scroll = rememberScrollState()
    // Pinned sets have no limit: the next page loads as the page nears its end (also when it doesn't fill the screen).
    LaunchedEffect(moreSets, sets.size) {
        if (moreSets) {
            snapshotFlow { scroll.maxValue - scroll.value < LOAD_MORE_PX }.first { it }
            a.onMoreSets()
        }
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(scroll).padding(horizontal = 18.dp).padding(bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Hero(p, onRename = if (mine != null) ({ renaming = true }) else null, onPictures = if (mine != null) ({ choosing = true }) else null)
        Stats(p)
        if (mine != null) VisibilityCard(mine.public) { a.onSections(ProfileUpdate(onLeaderboard = it)) }

        // Badges: a row of what they earned; mine also leads to every badge with its progress.
        SectionCard(
            title = str(R.string.rewards_badges_header),
            trailing = if (mine != null) ({ LinkText(str(R.string.profile_see_all), a.onOpenRewards) }) else null,
        ) {
            if (p.badges.isEmpty()) Hint(str(R.string.rewards_profile_no_badges)) else EarnedBadges(p)
        }

        // Streak and tempo zone side by side: mine always, someone else's when they show them.
        val showStreak = mine != null || p.streak != null
        val showZone = mine != null || p.tempoZone != null
        if (showStreak || showZone) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (showStreak) {
                    val days = p.streak ?: 0
                    MiniCard(
                        title = str(R.string.profile_streak_header),
                        shown = mine?.showStreak,
                        onShown = { a.onSections(ProfileUpdate(showStreak = it)) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(painterResource(R.drawable.ic_fire), null, tint = if (days > 0) Brand.Flame else Tone.text3, modifier = Modifier.size(22.dp))
                            Spacer(Modifier.width(4.dp))
                            BigNumber(number(days))
                        }
                        Hint(strPlural(R.plurals.profile_streak_days, days.toInt(), days))
                    }
                }
                if (showZone) {
                    val z = p.tempoZone
                    MiniCard(
                        title = str(R.string.profile_tempo_zone_header),
                        shown = mine?.showTempoZone,
                        onShown = { a.onSections(ProfileUpdate(showTempoZone = it)) },
                        modifier = Modifier.weight(1f),
                    ) {
                        if (z == null) {
                            Hint(str(R.string.profile_tempo_zone_empty))
                        } else {
                            BigNumber(str(R.string.profile_tempo_zone_range, z.low, z.high))
                            Hint(strPlural(R.plurals.profile_tempo_zone_songs, z.songs.toInt(), number(z.songs)))
                        }
                    }
                }
            }
        }

        val loves = p.loves
        if (mine != null || !loves.isNullOrEmpty()) {
            SectionCard(
                title = str(R.string.profile_loves_header),
                trailing = mine?.let { m -> { VisibilityChip(m.showLoves) { a.onSections(ProfileUpdate(showLoves = it)) } } },
            ) {
                if (loves.isNullOrEmpty()) Hint(str(R.string.profile_loves_empty))
                Expandable(loves.orEmpty()) { SongLine(it.title, str(R.string.rewards_discovery_artist_bpm, it.artist, it.bpm.roundToInt())) { a.onOpenSong(it.slug) } }
            }
        }

        if (p.broughtIn.isNotEmpty()) {
            SectionCard(title = str(R.string.rewards_profile_brought_in_header, number(p.broughtInTotal))) {
                Expandable(p.broughtIn) { d ->
                    SongLine(
                        d.title,
                        str(R.string.rewards_discovery_artist_bpm, d.artist, d.bpm.roundToInt()),
                        trailing = strPlural(R.plurals.rewards_discovery_listeners, d.listeners, number(d.listeners.toLong())),
                    ) { a.onOpenSong(d.slug) }
                }
            }
        }

        if (mine != null || sets.isNotEmpty()) {
            SectionCard(title = str(R.string.profile_sets_header)) {
                if (mine != null) {
                    Hint(str(R.string.profile_sets_hint))
                    Text(
                        str(R.string.profile_add_set),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Tone.primarySoft,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .border(1.dp, Tone.chipLine, RoundedCornerShape(12.dp))
                            .clickable { picking = true }
                            .padding(vertical = 10.dp),
                    )
                }
                sets.forEach { set ->
                    val songs = strPlural(R.plurals.sets_song_count, set.songs.toInt(), set.songs.toInt())
                    val span = if (set.bpmLow != null && set.bpmHigh != null) str(R.string.profile_set_bpm, set.bpmLow.roundToInt(), set.bpmHigh.roundToInt()) else null
                    SetLine(set.name, listOfNotNull(songs, span).joinToString(" · "), onClick = { a.onOpenSet(set.slug) }) {
                        if (mine != null) LinkText(str(R.string.profile_remove_set)) { a.onPin(set.slug, false) }
                    }
                }
                if (moreSets) {
                    Skeleton { SkeletonBox(Modifier.fillMaxWidth().height(44.dp), corner = 12.dp) }
                }
            }
        }
    }
    if (picking) SetPicker(a.mySets, onPick = { picking = false; a.onShowSet(it) }, onDismiss = { picking = false })
    if (choosing) {
        LaunchedEffect(Unit) { a.onLoadArt() }
        ArtPicker(
            a.art,
            avatarUrl = p.avatarUrl,
            bannerUrl = p.bannerUrl,
            onAvatar = { a.onSections(ProfileUpdate(avatar = it)) },
            onBanner = { a.onSections(ProfileUpdate(banner = it)) },
            onDismiss = { choosing = false },
        )
    }
    if (renaming) NicknameDialog(p.nickname, onSave = { renaming = false; a.onRename(it) }, onDismiss = { renaming = false })
}

/** The background picture (or a soft glow) with the avatar on it, the nickname, then joined month and supporter. */
@Composable
private fun Hero(p: PublicProfile, onRename: (() -> Unit)?, onPictures: (() -> Unit)?) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.BottomCenter) {
            val banner = mediaUrl(p.bannerUrl)
            if (banner != null) {
                AsyncImage(model = banner, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
            }
            // Fades into the page, so the name below always reads.
            Box(
                Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                if (banner != null) Color.Transparent else Brand.Violet.copy(alpha = 0.22f),
                                MaterialTheme.colorScheme.background.copy(alpha = if (banner != null) 0.85f else 0f),
                            ),
                        ),
                    ),
            )
            Box(Modifier.padding(bottom = 4.dp)) {
                Box(if (onPictures != null) Modifier.clip(CircleShape).clickable(onClickLabel = str(R.string.profile_pictures), onClick = onPictures) else Modifier) {
                    AvatarRing(p.nickname, 88, p.avatarUrl)
                }
                if (onPictures != null) {
                    Box(
                        Modifier.align(Alignment.BottomEnd).size(30.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary).clickable(onClick = onPictures),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(painterResource(R.drawable.ic_edit), str(R.string.profile_pictures), tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                p.nickname,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (onRename != null) {
                Spacer(Modifier.width(4.dp))
                Box(Modifier.size(32.dp).clip(CircleShape).clickable(onClickLabel = str(R.string.settings_account_rename), onClick = onRename), contentAlignment = Alignment.Center) {
                    Icon(painterResource(R.drawable.ic_edit), str(R.string.settings_account_rename), tint = Tone.primarySoft, modifier = Modifier.size(18.dp))
                }
            }
        }
        val joined = joinedMonth(p.joined, appLocale())
        if (joined != null || p.supporter) {
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                joined?.let { Text(str(R.string.rewards_profile_joined, it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                if (p.supporter) {
                    Text(
                        str(R.string.profile_supporter),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Brand.Flame,
                        modifier = Modifier.clip(RoundedCornerShape(50)).background(Brand.Flame.copy(alpha = 0.14f)).padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun AvatarRing(nickname: String?, size: Int, avatarUrl: String? = null) {
    Box(
        Modifier.size((size + 8).dp).clip(CircleShape).background(MaterialTheme.colorScheme.background).border(2.dp, Brand.Violet.copy(alpha = 0.55f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (nickname != null) {
            ProfileAvatar(nickname, size, avatarUrl)
        } else {
            Box(Modifier.size(size.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
                Icon(painterResource(R.drawable.ic_account), null, tint = Tone.text2, modifier = Modifier.size((size / 2).dp))
            }
        }
    }
}

/** Beats, all-time rank and Groove Master wins in one card, split by thin lines. */
@Composable
private fun Stats(p: PublicProfile) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(18.dp))
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Stat(number(p.beatsAll), str(R.string.profile_stat_beats), Modifier.weight(1f))
        StatDivider()
        Stat(p.rankAll?.let { str(R.string.rewards_board_rank, it) } ?: "–", str(R.string.profile_stat_rank), Modifier.weight(1f))
        StatDivider()
        Stat(number(p.grooveMaster), str(R.string.rewards_groove_master), Modifier.weight(1f))
    }
}

@Composable
private fun Stat(value: String, label: String, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 22.sp, lineHeight = 26.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
        Text(
            label,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            autoSize = TextAutoSize.StepBased(minFontSize = 8.sp, maxFontSize = 11.sp),
        )
    }
}

@Composable
private fun StatDivider() {
    Box(Modifier.width(1.dp).height(32.dp).background(MaterialTheme.colorScheme.outline))
}

@Composable
private fun VisibilityCard(public: Boolean, onPublic: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(if (public) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f) else MaterialTheme.colorScheme.surfaceContainerHigh)
            .border(1.dp, if (public) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f) else MaterialTheme.colorScheme.outline, RoundedCornerShape(18.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(R.drawable.ic_groups), null, tint = if (public) Tone.primarySoft else Tone.text3, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(str(R.string.rewards_profile_public), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(
                str(if (public) R.string.rewards_profile_public_desc else R.string.rewards_profile_hidden_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(8.dp))
        Switch(checked = public, onCheckedChange = onPublic)
    }
}

@Composable
private fun SectionCard(title: String, trailing: (@Composable () -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(18.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(Modifier.fillMaxWidth().heightIn(min = 28.dp), verticalAlignment = Alignment.CenterVertically) {
            SectionTitle(title, Modifier.weight(1f))
            trailing?.invoke()
        }
        content()
    }
}

/** Half-width card for a single figure; on my page, its visibility chip sits under the title. */
@Composable
private fun MiniCard(title: String, shown: Boolean?, onShown: (Boolean) -> Unit, modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(18.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        SectionTitle(title)
        content()
        if (shown != null) {
            Spacer(Modifier.height(2.dp))
            VisibilityChip(shown, onShown)
        }
    }
}

@Composable
private fun SectionTitle(title: String, modifier: Modifier = Modifier) {
    Text(title, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = modifier)
}

/** Whether others see a section of my profile; tap to switch. */
@Composable
private fun VisibilityChip(shown: Boolean, onShown: (Boolean) -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(if (shown) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else Color.Transparent)
            .border(1.dp, if (shown) Color.Transparent else Tone.chipLine, RoundedCornerShape(50))
            .clickable { onShown(!shown) }
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(if (shown) R.drawable.ic_groups else R.drawable.ic_account), null, tint = if (shown) Tone.primarySoft else Tone.text3, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(5.dp))
        // A half-width card on a 320 dp phone with a large font: "Only you" shrinks instead of breaking in two.
        Text(
            str(if (shown) R.string.profile_section_public else R.string.profile_section_private),
            fontWeight = FontWeight.Bold,
            color = if (shown) Tone.primarySoft else Tone.text3,
            maxLines = 1,
            softWrap = false,
            autoSize = TextAutoSize.StepBased(minFontSize = 8.sp, maxFontSize = 11.sp),
        )
    }
}

@Composable
private fun BigNumber(text: String) {
    Text(text, fontSize = 22.sp, lineHeight = 26.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun LinkText(text: String, onClick: () -> Unit) {
    Text(
        text,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        color = Tone.primarySoft,
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(horizontal = 6.dp, vertical = 4.dp),
    )
}

@Composable
private fun EarnedBadges(p: PublicProfile) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(vertical = 2.dp)) {
        items(p.badges, key = { it.code }) { b ->
            Column(Modifier.width(76.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                BadgeArt(b.code, b.tier.coerceAtLeast(1), 56, count = b.count)
                Spacer(Modifier.height(6.dp))
                Text(
                    badgeName(b.code, b.name).let { if (b.count > 1) str(R.string.badge_name_count, it, b.count) else it },
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun SongLine(title: String, sub: String, trailing: String? = null, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(Tone.selected), contentAlignment = Alignment.Center) {
            Icon(painterResource(R.drawable.ic_note), null, tint = Tone.primarySoft, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            Text(trailing, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SetLine(name: String, sub: String, onClick: () -> Unit, trailing: (@Composable () -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(Tone.selected), contentAlignment = Alignment.Center) {
            Icon(painterResource(R.drawable.ic_queue), null, tint = Tone.primarySoft, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        trailing?.let { Spacer(Modifier.width(8.dp)); it() }
    }
}

/** My avatar and background, picked from the stock; a tap changes it right away. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ArtPicker(
    art: ProfileArt?,
    avatarUrl: String?,
    bannerUrl: String?,
    onAvatar: (String) -> Unit,
    onBanner: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var banners by rememberSaveable { mutableStateOf(false) }
    CalmBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberCalmSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    ) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 24.dp)) {
            Text(str(R.string.profile_pictures), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(10.dp))
            Row(Modifier.clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(3.dp)) {
                listOf(false to R.string.profile_pictures_avatar, true to R.string.profile_pictures_background).forEach { (b, label) ->
                    val on = banners == b
                    Text(
                        str(label),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (on) MaterialTheme.colorScheme.primary else Color.Transparent)
                            .clickable { banners = b }
                            .padding(horizontal = 14.dp, vertical = 7.dp),
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            val items = if (banners) art?.banners else art?.avatars
            when {
                art == null -> Skeleton { SkeletonBox(Modifier.fillMaxWidth().height(220.dp), corner = 14.dp) }
                items.isNullOrEmpty() -> Hint(str(R.string.profile_pictures_empty))
                banners -> LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.height(420.dp),
                ) {
                    gridItems(items, key = { it.id }) { item ->
                        val selected = bannerUrl == item.url
                        AsyncImage(
                            model = mediaUrl(item.url),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(3f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                .border(if (selected) 3.dp else 0.dp, if (selected) MaterialTheme.colorScheme.primary else Color.Transparent, RoundedCornerShape(12.dp))
                                .clickable { onBanner(item.id) },
                        )
                    }
                }
                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(76.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.height(420.dp),
                ) {
                    gridItems(items, key = { it.id }) { item ->
                        val selected = avatarUrl == item.url
                        AsyncImage(
                            model = mediaUrl(item.url),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .aspectRatio(1f)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                .border(if (selected) 3.dp else 0.dp, if (selected) MaterialTheme.colorScheme.primary else Color.Transparent, CircleShape)
                                .clickable { onAvatar(item.id) },
                        )
                    }
                }
            }
        }
    }
}

/** The first [COLLAPSED] items, and a "Show all" that opens the rest. */
@Composable
private fun <T> Expandable(items: List<T>, row: @Composable (T) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    (if (open) items else items.take(COLLAPSED)).forEach { row(it) }
    if (items.size > COLLAPSED) {
        LinkText(str(if (open) R.string.profile_show_less else R.string.profile_show_all)) { open = !open }
    }
}

/** My sets on this phone; picking one shares it and shows it on my profile. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SetPicker(sets: List<SetSummary>, onPick: (Long) -> Unit, onDismiss: () -> Unit) {
    CalmBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberCalmSheetState(),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    ) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 32.dp)) {
            Text(str(R.string.profile_pick_set_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(10.dp))
            val usable = sets.filter { it.songs > 0 }
            if (usable.isEmpty()) Hint(str(R.string.profile_pick_set_empty))
            usable.forEach { set ->
                val span = if (set.minBpm != null && set.maxBpm != null) str(R.string.profile_set_bpm, set.minBpm.roundToInt(), set.maxBpm.roundToInt()) else null
                SetLine(set.name, listOfNotNull(strPlural(R.plurals.sets_song_count, set.songs, set.songs), span).joinToString(" · "), onClick = { onPick(set.id) })
            }
        }
    }
}

private const val COLLAPSED = 3
/** How close to the end (px) the next page of sets starts loading. */
private const val LOAD_MORE_PX = 800

/** "2026-10" as "October 2026" in the app's language; null if the server sent something else. */
private fun joinedMonth(joined: String?, locale: Locale): String? =
    joined?.let { runCatching { YearMonth.parse(it).format(DateTimeFormatter.ofPattern("LLLL yyyy", locale)) }.getOrNull() }

private fun number(n: Long): String = NumberFormat.getIntegerInstance().format(n)
