package com.boimin.beatradar.ui

import androidx.activity.compose.BackHandler
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.foundation.layout.fillMaxHeight
import com.boimin.beatradar.ui.theme.Tone
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.graphics.Brush
import androidx.compose.material3.SnackbarDuration
import androidx.compose.ui.unit.sp
import com.boimin.beatradar.library.SetsScreen
import androidx.compose.ui.platform.LocalContext
import com.boimin.beatradar.app
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.boimin.beatradar.R
import com.boimin.beatradar.i18n.str
import com.boimin.beatradar.account.AccountViewModel
import com.boimin.beatradar.account.SignInSheet
import com.boimin.beatradar.billing.Coffee
import com.boimin.beatradar.billing.CoffeeState
import com.boimin.beatradar.data.BeatLogEntry
import com.boimin.beatradar.data.Settings
import com.boimin.beatradar.library.LibraryActions
import com.boimin.beatradar.library.LibraryScreen
import com.boimin.beatradar.library.LibraryViewModel
import com.boimin.beatradar.net.SongSummary
import com.boimin.beatradar.rewards.BadgeCelebration
import com.boimin.beatradar.rewards.BeatsChip
import com.boimin.beatradar.rewards.ProfileActions
import com.boimin.beatradar.rewards.ProfileAvatar
import com.boimin.beatradar.rewards.ProfileScreen
import com.boimin.beatradar.rewards.RewardsSheet
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Everything the UI can ask the Activity to do (permissions, system screens, the service). */
class AppActions(
    val start: () -> Unit,
    val stop: () -> Unit,
    val pause: () -> Unit,
    val resume: () -> Unit,
    val identify: () -> Unit,
    /** Opens Shazam and plays the entry's saved clip to it. */
    val identifyClip: (BeatLogEntry) -> Unit,
    val openNotificationAccess: () -> Unit,
    /** Android share sheet with the song's share link. */
    val shareSong: (SongSummary) -> Unit,
    /** Android share sheet with plain text. */
    val shareText: (String) -> Unit,
    /** Opens a streaming link: the app if installed, else the browser. */
    val openUrl: (String) -> Unit,
    val coffee: StateFlow<CoffeeState>,
    val buyCoffee: (Coffee) -> Unit,
    val coffeeThanksShown: () -> Unit,
    /** A light buzz, e.g. for a new badge. */
    val celebrateHaptic: () -> Unit,
)

private const val TAB_LISTEN = 0
private const val TAB_LOG = 1
private const val TAB_LIBRARY = 2
private const val TAB_SETS = 3

@Composable
fun AppRoot(vm: MainViewModel, accountVm: AccountViewModel, libraryVm: LibraryViewModel, settings: Settings, actions: AppActions) {
    val monitor by vm.monitor.collectAsStateWithLifecycle()
    val log by vm.log.collectAsStateWithLifecycle()
    val playingClip by com.boimin.beatradar.data.ClipStore.playing.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val onlyInRange by vm.onlyInRange.collectAsStateWithLifecycle()
    val logBpmRange by vm.logBpmRange.collectAsStateWithLifecycle()
    val onlyIdentified by vm.onlyIdentified.collectAsStateWithLifecycle()
    val onlyUnnamed by vm.onlyUnnamed.collectAsStateWithLifecycle()
    val unnamedWithClip by vm.unnamedWithClip.collectAsStateWithLifecycle()
    val suggested by vm.suggestedCount.collectAsStateWithLifecycle()
    val currentEntry by vm.currentEntry.collectAsStateWithLifecycle()
    val logMessage by vm.message.collectAsStateWithLifecycle()
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()
    val openSong by vm.openSong.collectAsStateWithLifecycle()
    val user by accountVm.user.collectAsStateWithLifecycle()
    val rewards by accountVm.rewards.collectAsStateWithLifecycle()
    val celebrate by accountVm.celebrate.collectAsStateWithLifecycle()
    val accountToast by accountVm.toast.collectAsStateWithLifecycle()
    val libraryMessage by libraryVm.message.collectAsStateWithLifecycle()
    val profiles by libraryVm.profiles.collectAsStateWithLifecycle()
    val sharedSetOpen by libraryVm.sharedSet.collectAsStateWithLifecycle()
    val mySets by libraryVm.sets.collectAsStateWithLifecycle()
    val profileArt by libraryVm.profileArt.collectAsStateWithLifecycle()
    val coffee by actions.coffee.collectAsStateWithLifecycle()

    var tab by rememberSaveable { mutableIntStateOf(TAB_LISTEN) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showSignIn by rememberSaveable { mutableStateOf(false) }
    var showRewards by rememberSaveable { mutableStateOf(false) }
    var showCoffee by rememberSaveable { mutableStateOf(false) }
    var showShared by rememberSaveable { mutableStateOf(false) }
    var confirmSignOut by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) { accountVm.loadRewards() }
    // My profile page follows the account: signed in or out, renamed.
    LaunchedEffect(user?.id, user?.nickname) {
        libraryVm.myNickname.value = user?.nickname
        libraryVm.refreshMyProfile()
    }
    // Back in the app (e.g. after listening with the screen off): beats may have come in meanwhile.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { accountVm.loadRewards() }
    // A share link (App Link) opens the song in the Library.
    LaunchedEffect(openSong) {
        openSong?.let { slug ->
            libraryVm.closeProfiles()
            tab = TAB_LIBRARY
            libraryVm.open(slug)
            vm.openSong.value = null
        }
    }
    // A playlist or set link shared to BeatRadar opens in the Sets tab.
    val sharedPlaylist by libraryVm.sharedPlaylistLink.collectAsStateWithLifecycle()
    LaunchedEffect(sharedPlaylist) {
        sharedPlaylist?.let { link ->
            libraryVm.closeProfiles()
            tab = TAB_SETS
            libraryVm.close()
            libraryVm.openPlaylist(link)
            libraryVm.sharedPlaylistLink.value = null
        }
    }
    val sharedSetLink by libraryVm.sharedSetLink.collectAsStateWithLifecycle()
    LaunchedEffect(sharedSetLink) {
        sharedSetLink?.let { slug ->
            libraryVm.closeProfiles()
            tab = TAB_SETS
            libraryVm.close()
            libraryVm.openSharedSet(slug)
            libraryVm.sharedSetLink.value = null
        }
    }
    val recent by vm.recent.collectAsStateWithLifecycle()
    val songOpen by libraryVm.detail.collectAsStateWithLifecycle()
    val setOpen by libraryVm.openSetId.collectAsStateWithLifecycle()
    val profilePage = profiles.lastOrNull()
    LaunchedEffect(accountToast) { accountToast?.let { snackbar.showSnackbar(it); accountVm.toastShown() } }
    LaunchedEffect(logMessage) { logMessage?.let { snackbar.showSnackbar(it); vm.messageShown() } }
    LaunchedEffect(libraryMessage) { libraryMessage?.let { snackbar.showSnackbar(it); libraryVm.messageShown() } }
    // "Removed … · Undo".
    val undoLabel = str(R.string.app_undo)
    val songRemoved = str(R.string.app_song_removed)
    val undo by libraryVm.undo.collectAsStateWithLifecycle()
    LaunchedEffect(undo) {
        undo?.let { u ->
            // Cleared only after the snackbar closes: clearing it first would cancel this effect, and the snackbar with it.
            val result = snackbar.showSnackbar(u.text, actionLabel = undoLabel, duration = SnackbarDuration.Short)
            libraryVm.undoShown()
            if (result == SnackbarResult.ActionPerformed) u.undo()
        }
    }
    LaunchedEffect(celebrate) { if (celebrate.isNotEmpty()) actions.celebrateHaptic() }
    // Back from another tab goes to Listen first, as in most Android apps; only Listen leaves the app. An open song
    // page or set handles Back itself (registered later, so it goes first).
    BackHandler(enabled = tab != TAB_LISTEN) {
        libraryVm.close()
        tab = TAB_LISTEN
    }

    val requireSignIn: (() -> Unit) -> Unit = { then ->
        accountVm.setAfterSignIn(then)
        showSignIn = true
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            // A song page has its own bar (back, share, ⋮), so the app header steps aside.
            // An open set and a profile page have their own bar too.
            if (profilePage == null && !(songOpen != null && (tab == TAB_LIBRARY || tab == TAB_SETS)) && !(tab == TAB_SETS && setOpen != null)) {
                Header(
                    when (tab) {
                        TAB_LISTEN -> wordmark()
                        TAB_LOG -> AnnotatedString(str(R.string.app_title_activity_log))
                        TAB_SETS -> AnnotatedString(str(R.string.app_title_my_sets))
                        else -> AnnotatedString(str(R.string.app_title_discover))
                    },
                    rewards = { compact -> BeatsChip(rewards, compact) { showRewards = true } },
                    nickname = user?.nickname,
                    avatarUrl = rewards?.avatarUrl,
                    onProfile = libraryVm::openMyProfile,
                )
            }
        },
        bottomBar = {
            TabBar(selected = tab, onSelect = {
                // Any tab closes profile pages; another tab closes an open song page too.
                libraryVm.closeProfiles()
                if (it != tab) libraryVm.close()
                tab = it
            })
        },
        snackbarHost = {
            // Design: a light card on the dark app, its action in deep violet.
            SnackbarHost(snackbar) { data ->
                Snackbar(
                    data,
                    shape = RoundedCornerShape(12.dp),
                    containerColor = Color(0xFFECE9F5),
                    contentColor = Color(0xFF16151D),
                    actionColor = Color(0xFF4A3FC2),
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
        val modifier = Modifier
        val libraryActions = LibraryActions(
            signedIn = user != null,
            requireSignIn = requireSignIn,
            share = actions.shareSong,
            openUrl = actions.openUrl,
            shareText = actions.shareText,
            updateSettings = vm::updateSettings,
        )
        if (profilePage != null) {
            // A profile page sits over the tabs; a song or shared set opened from it shows over it, and Back returns.
            if (songOpen != null || sharedSetOpen != null) {
                SetsScreen(vm = libraryVm, settings = settings, actions = libraryActions, modifier = modifier)
            } else {
                BackHandler { libraryVm.backFromProfile() }
                ProfileScreen(
                    profilePage,
                    signedIn = user != null,
                    a = ProfileActions(
                        onBack = libraryVm::backFromProfile,
                        onSettings = { showSettings = true },
                        onSignIn = { requireSignIn {} },
                        onRetry = { libraryVm.refreshProfile(profilePage.key) },
                        onOpenSong = libraryVm::open,
                        onOpenSet = libraryVm::openSharedSet,
                        onOpenRewards = { showRewards = true },
                        onReport = { profilePage.nickname?.let(libraryVm::reportNickname) },
                        onRename = libraryVm::renameMe,
                        onSections = { update ->
                            libraryVm.updateMySections(update)
                            // The header shows my avatar too.
                            if (update.avatar != null) scope.launch { kotlinx.coroutines.delay(800); accountVm.loadRewards() }
                        },
                        onPin = libraryVm::pinSet,
                        mySets = mySets,
                        onShowSet = libraryVm::showSetOnProfile,
                        onMoreSets = { libraryVm.loadMoreSets(profilePage.key) },
                        art = profileArt,
                        onLoadArt = libraryVm::loadProfileArt,
                    ),
                    modifier = modifier,
                )
            }
        } else when (tab) {
            TAB_LISTEN -> ListenScreen(
                state = monitor,
                settings = settings,
                actions = actions,
                onSettingsChange = vm::updateSettings,
                suggestion = currentEntry?.takeIf { it.title == null }?.suggestion,
                onAcceptSuggestion = { currentEntry?.let(vm::acceptSuggestion) },
                onRejectSuggestion = { currentEntry?.let(vm::rejectSuggestion) },
                recent = recent,
                onOpenSong = { slug -> tab = TAB_LIBRARY; libraryVm.open(slug) },
                modifier = modifier,
            )
            TAB_LOG -> LogScreen(
                entries = log,
                query = query,
                onQueryChange = { vm.query.value = it },
                onlyInRange = onlyInRange,
                bpmRange = logBpmRange ?: settings.targetRange,
                bpmCustom = logBpmRange != null,
                onBpmRange = { vm.logBpmRange.value = it },
                onResetBpm = { vm.logBpmRange.value = null },
                inTarget = settings::inTarget,
                onlyIdentified = onlyIdentified,
                onToggleInRange = { vm.onlyInRange.value = !onlyInRange },
                onToggleIdentified = { vm.onlyIdentified.value = !onlyIdentified },
                unnamedWithClip = unnamedWithClip,
                suggested = suggested,
                onAcceptSuggestion = vm::acceptSuggestion,
                onRejectSuggestion = vm::rejectSuggestion,
                onlyUnnamed = onlyUnnamed,
                onToggleUnnamed = { vm.onlyUnnamed.value = !onlyUnnamed },
                onIdentify = actions.identifyClip,
                onPlayClip = vm::playClip,
                playingClip = playingClip,
                onRename = vm::rename,
                onOpenSong = { slug -> tab = TAB_LIBRARY; libraryVm.open(slug) },
                refreshing = refreshing,
                onRefresh = vm::refresh,
                onShowRecap = { vm.showRecap.value = true },
                onDelete = { entry ->
                    vm.delete(entry)
                    scope.launch {
                        val result = snackbar.showSnackbar(songRemoved, actionLabel = undoLabel, withDismissAction = true)
                        if (result == SnackbarResult.ActionPerformed) vm.restore(entry) else vm.forgetClip(entry)
                    }
                },
                onClear = vm::clearLog,
                modifier = modifier,
            )
            else -> {
                if (tab == TAB_SETS) {
                    SetsScreen(vm = libraryVm, settings = settings, actions = libraryActions, modifier = modifier)
                } else {
                    LibraryScreen(vm = libraryVm, settings = settings, actions = libraryActions, modifier = modifier)
                }
            }
        }
        // A fade above the floating tab bar, so it never cuts content off abruptly.
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(24.dp)
                .background(Brush.verticalGradient(listOf(Color.Transparent, MaterialTheme.colorScheme.background))),
        )
        }
    }

    if (showSettings) {
        SettingsSheet(
            settings = settings,
            hasNotificationAccess = vm::hasNotificationAccess,
            onChange = vm::updateSettings,
            onOpenNotificationAccess = actions.openNotificationAccess,
            account = AccountSettings(
                user = user,
                onSignIn = { requireSignIn {} },
                onSignOut = { confirmSignOut = true },
                onDeleteAccount = accountVm::deleteAccount,
                onRename = accountVm::rename,
                onLeaderboard = accountVm::setOnLeaderboard,
                coffeeAvailable = coffee is CoffeeState.Ready,
                onCoffee = { showCoffee = true },
                onShowShared = { showSettings = false; showShared = true },
            ),
            onOpenUrl = actions.openUrl,
            onDismiss = { showSettings = false },
        )
    }
    if (showSignIn) SignInSheet(accountVm, onDismiss = { showSignIn = false })
    val showRecap by vm.showRecap.collectAsStateWithLifecycle()
    val recap by vm.recap.collectAsStateWithLifecycle()
    LaunchedEffect(showRecap) { if (showRecap) vm.loadRecap() }
    if (showRecap) {
        recap?.let { r ->
            RecapSheet(r, streak = rewards?.streak, beatsWeek = rewards?.beatsWeek, onDismiss = { vm.showRecap.value = false })
        }
    }
    if (showShared) {
        val library = LocalContext.current.app.library
        SharedSheet(load = { library.shared() }, email = user?.email, onDismiss = { showShared = false })
    }
    if (showRewards) RewardsSheet(
        accountVm,
        onSignIn = { showRewards = false; requireSignIn {} },
        onOpenSong = { slug -> showRewards = false; if (profilePage == null) tab = TAB_LIBRARY; libraryVm.open(slug) },
        onOpenProfile = { nickname -> showRewards = false; libraryVm.openProfile(nickname) },
        onDismiss = { showRewards = false },
    )
    if (celebrate.isNotEmpty()) BadgeCelebration(celebrate, onDone = accountVm::celebrated)
    if (showCoffee) CoffeeSheet(coffee, actions.buyCoffee, onDismiss = { showCoffee = false; actions.coffeeThanksShown() })
    if (confirmSignOut) {
        // PLAN §7a: keep = the local copy stays and stops syncing; remove = cleared here, still in the account.
        AlertDialog(
            onDismissRequest = { confirmSignOut = false },
            title = { Text(str(R.string.app_sign_out_title)) },
            text = { Text(str(R.string.app_sign_out_body)) },
            confirmButton = { TextButton(onClick = { confirmSignOut = false; accountVm.signOut(keepLog = true) }) { Text(str(R.string.app_sign_out_keep)) } },
            dismissButton = { TextButton(onClick = { confirmSignOut = false; accountVm.signOut(keepLog = false) }) { Text(str(R.string.app_sign_out_remove)) } },
        )
    }
    if (!settings.sharingConsentAsked) {
        SharingConsent(
            onAnswer = { contribute, shareClips ->
                vm.updateSettings {
                    // Clips can only be shared if they're kept on the phone in the first place.
                    it.copy(sharingConsentAsked = true, contribute = contribute, shareClips = shareClips, saveClips = it.saveClips || shareClips)
                }
            },
        )
    }
}

/**
 * First run (PLAN §9, §5d): asks, like a permission prompt, whether BeatRadar may contribute to the shared Library and,
 * only then, whether it may share clips of songs it can't name. Both can be changed later in Settings.
 */
@Composable
private fun SharingConsent(onAnswer: (contribute: Boolean, shareClips: Boolean) -> Unit) {
    var step by rememberSaveable { mutableIntStateOf(1) }
    if (step == 1) {
        ConsentDialog(
            step = str(R.string.app_consent_step, 1, 2),
            title = str(R.string.app_consent_library_title),
            body = str(R.string.app_consent_library_body),
            onAllow = { step = 2 },
            onDeny = { onAnswer(false, false) },
        )
    } else {
        ConsentDialog(
            step = str(R.string.app_consent_step, 2, 2),
            title = str(R.string.app_consent_clips_title),
            body = str(R.string.app_consent_clips_body),
            onAllow = { onAnswer(true, true) },
            onDeny = { onAnswer(true, false) },
        )
    }
}

@Composable
private fun ConsentDialog(step: String, title: String, body: String, onAllow: () -> Unit, onDeny: () -> Unit) {
    AlertDialog(
        onDismissRequest = {},
        title = {
            Column {
                Text(step, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(title)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(body)
                Text(
                    str(R.string.app_consent_change_later),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { Button(onClick = onAllow) { Text(str(R.string.app_consent_allow)) } },
        dismissButton = { TextButton(onClick = onDeny) { Text(str(R.string.app_consent_deny)) } },
    )
}

/** Three tips with Play's localised prices, then a thank-you (PLAN §8). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CoffeeSheet(state: CoffeeState, onBuy: (Coffee) -> Unit, onDismiss: () -> Unit) {
    CalmBottomSheet(onDismissRequest = onDismiss, sheetState = rememberCalmSheetState()) {
        Column(Modifier.padding(start = 24.dp, end = 24.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(str(R.string.app_coffee_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            val ready = state as? CoffeeState.Ready
            when {
                ready == null -> Text(str(R.string.app_coffee_unavailable))
                ready.thanks -> Text(str(R.string.app_coffee_thanks), style = MaterialTheme.typography.bodyLarge)
                ready.pending -> Text(str(R.string.app_coffee_pending))
                else -> {
                    Text(
                        str(R.string.app_coffee_intro),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    ready.coffees.forEach { c ->
                        OutlinedButton(onClick = { onBuy(c) }, modifier = Modifier.fillMaxWidth()) {
                            Text(c.label, modifier = Modifier.weight(1f))
                            Text(c.price, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

/** "BeatRadar" as on the splash (drawable/splash_branding.xml): "Beat" in the text colour, "Radar" in violet, extra bold. */
@Composable
private fun wordmark(): AnnotatedString = buildAnnotatedString {
    withStyle(SpanStyle(fontWeight = FontWeight.ExtraBold)) {
        append("Beat")
        withStyle(SpanStyle(color = Tone.wordmarkAccent)) { append("Radar") }
    }
}

/** Slim header: squircle logo badge + title on the left, beats chip and the profile button (Settings live there) on the right. */
@Composable
private fun Header(title: AnnotatedString, rewards: @Composable (compact: Boolean) -> Unit, nickname: String?, avatarUrl: String?, onProfile: () -> Unit) {
    // Design: a 40 dp logo tile, the title at 20 sp bold, the beats chip and a bare 36 dp profile button.
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            // In landscape the navigation bar can sit at a side; keep the profile button clear of it.
            .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.displayCutout).only(WindowInsetsSides.Horizontal)),
    ) {
        // A narrow header (a 320 dp phone, or a large font / display size): the chip drops the beats count, and the
        // title shrinks to fit on one line rather than breaking mid-word.
        val compact = maxWidth < COMPACT_HEADER || (maxWidth < COMPACT_HEADER + 40.dp && LocalDensity.current.fontScale > 1.15f)
        Row(
            Modifier
                .fillMaxWidth()
                .height(60.dp)
                .padding(start = if (compact) 14.dp else 18.dp, end = if (compact) 8.dp else 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Tone.logoTile),
                contentAlignment = Alignment.Center,
            ) {
                // The app logo in its own colours, half the tile like the launcher icon.
                Icon(painterResource(R.drawable.ic_logo), contentDescription = null, tint = Color.Unspecified, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(10.dp))
            Text(
                title,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                autoSize = TextAutoSize.StepBased(minFontSize = 15.sp, maxFontSize = 20.sp),
                modifier = Modifier.weight(1f),
            )
            rewards(compact)
            Spacer(Modifier.width(6.dp))
            val profileLabel = str(R.string.profile_title)
            Box(
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .clickable(onClickLabel = profileLabel, onClick = onProfile)
                    .semantics { contentDescription = profileLabel },
                contentAlignment = Alignment.Center,
            ) {
                // Signed in: my avatar; a guest: the plain account icon. The avatar's initial is hidden from TalkBack,
                // which would otherwise read the button as just "C".
                if (nickname != null) {
                    Box(Modifier.clearAndSetSemantics {}) { ProfileAvatar(nickname, 28, avatarUrl) }
                } else {
                    Icon(painterResource(R.drawable.ic_account), contentDescription = str(R.string.profile_title), tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
                }
            }
        }
    }
}

/** Below this width the header goes compact. */
private val COMPACT_HEADER = 340.dp

/** The floating tab bar: a 62 dp pill, four 48 dp items, the active one filled. */
@Composable
private fun TabBar(selected: Int, onSelect: (Int) -> Unit) {
    val tabs = listOf(
        R.string.app_tab_listen to R.drawable.ic_graphic_eq,
        R.string.app_tab_log to R.drawable.ic_history,
        R.string.app_tab_library to R.drawable.ic_library_music,
        R.string.app_tab_sets to R.drawable.ic_queue,
    )
    Row(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 10.dp)
            .height(62.dp)
            .clip(RoundedCornerShape(31.dp))
            .background(Tone.sheet)
            .border(1.dp, Tone.sheetLine, RoundedCornerShape(31.dp))
            .padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        tabs.forEachIndexed { i, (label, icon) ->
            val active = i == selected
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(25.dp))
                    .background(if (active) MaterialTheme.colorScheme.primary else Color.Transparent)
                    .clickable { onSelect(i) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                val tint = if (active) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                Icon(painterResource(icon), contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
                Text(str(label), color = tint, fontSize = 11.sp, lineHeight = 13.sp, fontWeight = if (active) FontWeight.Medium else FontWeight.Normal)
            }
        }
    }
}
