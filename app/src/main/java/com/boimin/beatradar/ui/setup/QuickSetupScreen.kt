package com.boimin.beatradar.ui.setup

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.boimin.beatradar.R
import com.boimin.beatradar.data.Settings
import com.boimin.beatradar.i18n.LanguageSheet
import com.boimin.beatradar.i18n.currentLanguageName
import com.boimin.beatradar.i18n.appLocale
import com.boimin.beatradar.i18n.str
import com.boimin.beatradar.net.Me
import com.boimin.beatradar.rewards.ProfileAvatar
import com.boimin.beatradar.ui.TempoRangeEditor
import com.boimin.beatradar.ui.tempoRangeLabel
import com.boimin.beatradar.ui.theme.Brand
import com.boimin.beatradar.ui.theme.Tone
import com.boimin.beatradar.ui.theme.brandSwitchColors
import com.boimin.beatradar.ui.theme.inTargetColor
import kotlinx.coroutines.launch
import kotlin.random.Random

private const val STEP_USE = 0
private const val STEP_HEAR = 1
private const val STEP_LIBRARY = 2
private const val STEP_ACCOUNT = 3

/**
 * Quick Setup (docs/plans/03-quick-setup.md): four light steps over the Listen tab on first run, so the features that
 * hide in Settings work from the start. What for (a target preset), the mic, the Library, the account. Every step can
 * be skipped, nothing blocks, and each choice can be changed in Settings later. Steps move with their buttons only (no
 * free swipe), so a stepper in "Set my own" can't page by accident.
 */
@Composable
fun QuickSetupScreen(
    settings: Settings,
    onChange: ((Settings) -> Settings) -> Unit,
    /** True granted, false refused, null not asked yet. */
    micPermission: Boolean?,
    onRequestPermissions: () -> Unit,
    hasNotificationAccess: () -> Boolean,
    onOpenNotificationAccess: () -> Unit,
    user: Me?,
    avatarUrl: String?,
    onSignIn: () -> Unit,
    /** Closed: back to the Listen tab ([start] when its last button was tapped with the mic allowed). */
    onDone: (start: Boolean) -> Unit,
) {
    val pager = rememberPagerState { QuickSetupRules.STEPS }
    val scope = rememberCoroutineScope()
    val page = pager.currentPage
    val last = QuickSetupRules.STEPS - 1
    fun go(to: Int) {
        scope.launch { pager.animateScrollToPage(to) }
    }
    fun finish(start: Boolean) {
        onChange(QuickSetupRules::finish)
        onDone(start)
    }
    fun next() = if (page >= last) finish(start = false) else go(page + 1)
    BackHandler(enabled = page > 0) { go(page - 1) }

    // Step 1's choice, applied on Continue: a preset, or "Set my own" with its range.
    var preset by rememberSaveable { mutableStateOf(QuickSetupRules.presetOf(settings)) }
    var ownRange by rememberSaveable { mutableStateOf(false) }
    var ownLow by rememberSaveable { mutableStateOf(settings.targetLow) }
    var ownHigh by rememberSaveable { mutableStateOf(settings.targetHigh) }
    // Step 3's answers, starting from what's set (on: contribute, as before; off: clips).
    var contribute by rememberSaveable { mutableStateOf(settings.contribute) }
    var shareClips by rememberSaveable { mutableStateOf(settings.contribute && settings.shareClips) }
    var choosingLanguage by remember { mutableStateOf(false) }
    if (choosingLanguage) LanguageSheet(onDismiss = { choosingLanguage = false })

    // A Surface, so taps don't fall through to the tabs underneath.
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background, contentColor = MaterialTheme.colorScheme.onBackground) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(start = 12.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (page == STEP_USE) {
                    LanguageChip(onClick = { choosingLanguage = true })
                } else {
                    Box(
                        Modifier.size(44.dp).clip(CircleShape).clickable(onClickLabel = str(R.string.app_setup_back), role = Role.Button) { go(page - 1) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = str(R.string.app_setup_back), tint = Tone.text2, modifier = Modifier.size(22.dp))
                    }
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = ::next) { Text(str(R.string.app_setup_skip), color = Tone.text2, fontWeight = FontWeight.Medium) }
            }

            HorizontalPager(state = pager, userScrollEnabled = false, modifier = Modifier.weight(1f).fillMaxWidth()) { step ->
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp)
                        .padding(top = 8.dp, bottom = 16.dp),
                ) {
                    when (step) {
                        STEP_USE -> UseStep(
                            settings = settings,
                            preset = preset,
                            ownRange = ownRange,
                            ownLow = ownLow,
                            ownHigh = ownHigh,
                            onPreset = { preset = it; ownRange = false },
                            onOwnRange = { ownRange = true; preset = null },
                            onOwnRangeChange = { lo, hi -> ownLow = lo; ownHigh = hi },
                        )
                        STEP_HEAR -> HearStep(micPermission, hasNotificationAccess, onOpenNotificationAccess)
                        STEP_LIBRARY -> LibraryStep(
                            contribute = contribute,
                            shareClips = shareClips,
                            onContribute = { contribute = it; if (!it) shareClips = false },
                            onShareClips = { shareClips = it },
                        )
                        else -> AccountStep(user, avatarUrl, onSignIn)
                    }
                }
            }

            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                PageDots(page, QuickSetupRules.STEPS)
                Spacer(Modifier.height(16.dp))
                val mic = micPermission
                PrimaryButton(
                    when {
                        page == STEP_HEAR && mic == null -> str(R.string.app_setup_allow_mic)
                        page == last -> str(R.string.app_setup_start)
                        else -> str(R.string.app_setup_continue)
                    },
                ) {
                    when (page) {
                        STEP_USE -> {
                            val p = preset
                            when {
                                ownRange -> onChange { QuickSetupRules.custom(it, ownLow, ownHigh) }
                                p != null -> onChange { QuickSetupRules.apply(it, p) }
                            }
                            next()
                        }
                        // Asked once, explained above; the answer (either way) turns this into Continue.
                        STEP_HEAR -> if (mic == null) onRequestPermissions() else next()
                        STEP_LIBRARY -> {
                            onChange { QuickSetupRules.consent(it, contribute, shareClips) }
                            next()
                        }
                        else -> finish(start = mic == true)
                    }
                }
                // "Skip setup": everything left keeps its safe default. The slot stays on the last step, so the button
                // above never jumps.
                Box(Modifier.heightIn(min = 44.dp), contentAlignment = Alignment.Center) {
                    if (page < last) {
                        TextButton(onClick = { finish(start = false) }) {
                            Text(str(R.string.app_setup_skip_all), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}

// Steps

@Composable
private fun ColumnScope.UseStep(
    settings: Settings,
    preset: SetupPreset?,
    ownRange: Boolean,
    ownLow: Int?,
    ownHigh: Int?,
    onPreset: (SetupPreset) -> Unit,
    onOwnRange: () -> Unit,
    onOwnRangeChange: (Int?, Int?) -> Unit,
) {
    StepHeader(R.drawable.ic_logo, str(R.string.app_setup_use_title), str(R.string.app_setup_use_body), logo = true)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SetupPreset.entries.forEach { p ->
            val range = if (p.min != null && p.max != null) tempoRangeLabel(p.min, p.max) else str(R.string.app_setup_preset_free_desc)
            ChoiceCard(
                icon = p.icon,
                title = str(p.label),
                subtitle = if (p.halfDouble) str(R.string.app_setup_with_half_double, range) else range,
                selected = !ownRange && p == preset,
                onClick = { onPreset(p) },
            )
        }
        ChoiceCard(
            icon = R.drawable.ic_edit,
            title = str(R.string.app_setup_own_range),
            subtitle = if (ownRange) tempoRangeLabel(ownLow, ownHigh) else str(R.string.app_setup_own_range_desc),
            selected = ownRange,
            onClick = onOwnRange,
        )
    }
    AnimatedVisibility(visible = ownRange, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
        Column(
            Modifier
                .padding(top = 8.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Tone.sheet)
                .border(1.dp, Tone.sheetLine, RoundedCornerShape(16.dp))
                .padding(12.dp),
        ) {
            TempoRangeEditor(
                low = ownLow,
                high = ownHigh,
                fallbackLow = settings.minBpm,
                fallbackHigh = settings.maxBpm,
                allowAny = false,
                onChange = onOwnRangeChange,
            )
        }
    }
    ChangeLater()
}

@Composable
private fun ColumnScope.HearStep(micPermission: Boolean?, hasNotificationAccess: () -> Boolean, onOpenNotificationAccess: () -> Unit) {
    var access by remember { mutableStateOf(hasNotificationAccess()) }
    // Back from the system's notification access screen.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { access = hasNotificationAccess() }

    StepHeader(R.drawable.ic_mic, str(R.string.app_setup_hear_title), str(R.string.app_setup_hear_body))
    InfoCard {
        StatusRow(
            icon = R.drawable.ic_mic,
            title = str(R.string.app_setup_mic_title),
            subtitle = when (micPermission) {
                true -> str(R.string.app_setup_mic_allowed)
                false -> str(R.string.app_setup_mic_denied)
                null -> str(R.string.app_setup_mic_desc)
            },
            subtitleColor = when (micPermission) {
                true -> inTargetColor()
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
            done = micPermission == true,
        )
    }
    Spacer(Modifier.height(10.dp))
    InfoCard {
        StatusRow(
            icon = R.drawable.ic_note,
            title = str(R.string.app_setup_names_title),
            subtitle = str(R.string.app_setup_names_desc),
            done = access,
            onClick = onOpenNotificationAccess.takeUnless { access },
            trailing = if (access) null else {
                { SmallChip(str(R.string.app_setup_allow), onClick = onOpenNotificationAccess) }
            },
        )
    }
    ChangeLater()
}

@Composable
private fun ColumnScope.LibraryStep(contribute: Boolean, shareClips: Boolean, onContribute: (Boolean) -> Unit, onShareClips: (Boolean) -> Unit) {
    StepHeader(R.drawable.ic_library_music, str(R.string.app_setup_library_title), str(R.string.app_setup_library_body))
    InfoCard {
        ToggleRow(str(R.string.app_consent_library_title), str(R.string.app_consent_library_body), contribute, onChange = onContribute)
    }
    Spacer(Modifier.height(10.dp))
    InfoCard {
        // Clips go with fingerprints, so they need the Library on.
        ToggleRow(str(R.string.app_consent_clips_title), str(R.string.app_consent_clips_body), shareClips, enabled = contribute, onChange = onShareClips)
    }
    ChangeLater()
}

@Composable
private fun ColumnScope.AccountStep(user: Me?, avatarUrl: String?, onSignIn: () -> Unit) {
    // A nickname like the server gives, so the leaderboard spot feels real; kept while the step shows.
    val sample = rememberSaveable { QuickSetupRules.sampleNickname(Random.nextInt(0, Int.MAX_VALUE)) }
    StepHeader(R.drawable.ic_trophy, str(R.string.app_setup_account_title), str(R.string.app_setup_account_body))
    InfoCard {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.clearAndSetSemantics {}) { ProfileAvatar(user?.nickname ?: sample, 48, avatarUrl.takeIf { user != null }) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(user?.nickname ?: sample, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    if (user != null) str(R.string.app_setup_signed_in) else str(R.string.app_setup_nickname_preview),
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    color = if (user != null) inTargetColor() else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (user != null) {
                Spacer(Modifier.width(8.dp))
                Icon(painterResource(R.drawable.ic_check_circle), contentDescription = null, tint = inTargetColor(), modifier = Modifier.size(22.dp))
            }
        }
    }
    if (user == null) {
        Spacer(Modifier.height(12.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clip(RoundedCornerShape(14.dp))
                .border(1.dp, Tone.chipLine, RoundedCornerShape(14.dp))
                .clickable(role = Role.Button, onClick = onSignIn)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(str(R.string.app_setup_sign_in), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Tone.primarySoft, textAlign = TextAlign.Center)
        }
    }
    Spacer(Modifier.height(20.dp))
    Text(
        str(R.string.app_setup_tips).uppercase(appLocale()),
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.6.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
    )
    InfoCard {
        TipRow(R.drawable.ic_graphic_eq, str(R.string.app_setup_tip_widget))
        TipRow(R.drawable.ic_radar, str(R.string.app_setup_tip_tile))
    }
}

// Parts

/** An icon tile, then the step's title over a line of why. */
@Composable
private fun StepHeader(@DrawableRes icon: Int, title: String, body: String, logo: Boolean = false) {
    Box(Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)).background(Tone.logoTile), contentAlignment = Alignment.Center) {
        // The logo in its own colours, as in the header; other steps' icons in the soft violet.
        Icon(
            painterResource(icon),
            contentDescription = null,
            tint = if (logo) Color.Unspecified else Tone.primarySoft,
            modifier = Modifier.size(if (logo) 26.dp else 28.dp),
        )
    }
    Spacer(Modifier.height(16.dp))
    Text(title, fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })
    Spacer(Modifier.height(6.dp))
    Text(body, fontSize = 15.sp, lineHeight = 21.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(20.dp))
}

/** "You can change this anytime in Settings." under a step. */
@Composable
private fun ChangeLater() {
    Spacer(Modifier.height(14.dp))
    Text(str(R.string.app_consent_change_later), fontSize = 12.sp, lineHeight = 17.sp, color = Tone.text3, modifier = Modifier.padding(horizontal = 4.dp))
}

/** A choice as a card: icon in a circle, title over subtitle, a check when picked (violet outline, as the presets). */
@Composable
private fun ChoiceCard(@DrawableRes icon: Int, title: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    val line by animateColorAsState(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent, label = "choiceLine")
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clip(shape)
            .background(if (selected) Tone.selected else Tone.track)
            .border(1.5.dp, line, shape)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(38.dp).clip(CircleShape).background(if (selected) MaterialTheme.colorScheme.primary else Tone.pill), contentAlignment = Alignment.Center) {
            Icon(painterResource(icon), contentDescription = null, tint = if (selected) Color.White else Tone.primarySoft, modifier = Modifier.size(19.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, lineHeight = 19.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, fontSize = 12.sp, lineHeight = 16.sp, color = if (selected) Tone.text2 else MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (selected) {
            Spacer(Modifier.width(8.dp))
            Icon(painterResource(R.drawable.ic_check_circle), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        }
    }
}

/** One card of rows, on the track colour as Settings' cards. */
@Composable
private fun InfoCard(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Tone.track).animateContentSize(), content = content)
}

/** A permission or feature: icon, title over its state, then a ✓ when it's on (or [trailing], e.g. Allow). */
@Composable
private fun StatusRow(
    @DrawableRes icon: Int,
    title: String,
    subtitle: String,
    done: Boolean,
    subtitleColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(36.dp).clip(CircleShape).background(Tone.pill), contentAlignment = Alignment.Center) {
            Icon(painterResource(icon), contentDescription = null, tint = Tone.primarySoft, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, lineHeight = 19.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, fontSize = 12.sp, lineHeight = 17.sp, color = subtitleColor)
        }
        Spacer(Modifier.width(10.dp))
        when {
            done -> Icon(painterResource(R.drawable.ic_check_circle), contentDescription = null, tint = inTargetColor(), modifier = Modifier.size(24.dp))
            trailing != null -> trailing()
        }
    }
}

@Composable
private fun ToggleRow(title: String, body: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, role = Role.Switch) { onChange(!checked) }
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .alpha(if (enabled) 1f else 0.38f),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, lineHeight = 19.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(body, fontSize = 12.sp, lineHeight = 17.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked && enabled, onCheckedChange = null, enabled = enabled, colors = brandSwitchColors())
    }
}

@Composable
private fun TipRow(@DrawableRes icon: Int, text: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(painterResource(icon), contentDescription = null, tint = Brand.Flame, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(text, fontSize = 13.sp, lineHeight = 18.sp, color = Tone.text2, modifier = Modifier.weight(1f))
    }
}

/** The language in use as a pill, top left of the first step: picking another one switches the steps at once. */
@Composable
private fun LanguageChip(onClick: () -> Unit) {
    val label = str(R.string.app_setup_language)
    Row(
        Modifier
            .widthIn(max = 200.dp)
            .heightIn(min = 36.dp)
            .clip(RoundedCornerShape(50))
            .background(Tone.chip)
            .border(1.dp, Tone.chipLine, RoundedCornerShape(50))
            .clickable(onClickLabel = label, role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(R.drawable.ic_language), contentDescription = null, tint = Tone.primarySoft, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(currentLanguageName(), fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Tone.text2, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Icon(painterResource(R.drawable.ic_expand_more), contentDescription = null, tint = Tone.text3, modifier = Modifier.size(16.dp))
    }
}

/** A small pill button inside a row, violet: the thing to do. */
@Composable
private fun SmallChip(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.primary)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
    ) {
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
    }
}

/** Where the steps are: the current one a wider violet pill. */
@Composable
private fun PageDots(current: Int, count: Int) {
    val description = str(R.string.app_consent_step, current + 1, count)
    Row(Modifier.semantics { contentDescription = description }, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(count) { i ->
            val width by animateDpAsState(if (i == current) 22.dp else 8.dp, label = "dot")
            Box(
                Modifier
                    .size(width, 8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (i == current) MaterialTheme.colorScheme.primary else Tone.ring),
            )
        }
    }
}

/** The step's one main button: full width, 52 dp, violet, as Settings' Done. */
@Composable
private fun PrimaryButton(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.primary)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White, textAlign = TextAlign.Center)
    }
}
