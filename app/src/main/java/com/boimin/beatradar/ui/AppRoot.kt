package com.boimin.beatradar.ui

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
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.boimin.beatradar.R
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
import com.boimin.beatradar.rewards.ProfileSheet
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
    val profile by libraryVm.profile.collectAsStateWithLifecycle()
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
    // Back in the app (e.g. after listening with the screen off): beats may have come in meanwhile.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { accountVm.loadRewards() }
    // A share link (App Link) opens the song in the Library.
    LaunchedEffect(openSong) {
        openSong?.let { slug ->
            tab = TAB_LIBRARY
            libraryVm.open(slug)
            vm.openSong.value = null
        }
    }
    // A playlist or set link shared to BeatRadar opens in the Sets tab.
    val sharedPlaylist by libraryVm.sharedPlaylistLink.collectAsStateWithLifecycle()
    LaunchedEffect(sharedPlaylist) {
        sharedPlaylist?.let { link ->
            tab = TAB_SETS
            libraryVm.close()
            libraryVm.openPlaylist(link)
            libraryVm.sharedPlaylistLink.value = null
        }
    }
    val sharedSetLink by libraryVm.sharedSetLink.collectAsStateWithLifecycle()
    LaunchedEffect(sharedSetLink) {
        sharedSetLink?.let { slug ->
            tab = TAB_SETS
            libraryVm.close()
            libraryVm.openSharedSet(slug)
            libraryVm.sharedSetLink.value = null
        }
    }
    val recent by vm.recent.collectAsStateWithLifecycle()
    val songOpen by libraryVm.detail.collectAsStateWithLifecycle()
    val setOpen by libraryVm.openSetId.collectAsStateWithLifecycle()
    LaunchedEffect(accountToast) { accountToast?.let { snackbar.showSnackbar(it); accountVm.toastShown() } }
    LaunchedEffect(logMessage) { logMessage?.let { snackbar.showSnackbar(it); vm.messageShown() } }
    LaunchedEffect(libraryMessage) { libraryMessage?.let { snackbar.showSnackbar(it); libraryVm.messageShown() } }
    // "Removed … · Undo".
    val undo by libraryVm.undo.collectAsStateWithLifecycle()
    LaunchedEffect(undo) {
        undo?.let { u ->
            // Cleared only after the snackbar closes: clearing it first would cancel this effect, and the snackbar with it.
            val result = snackbar.showSnackbar(u.text, actionLabel = "Undo", duration = SnackbarDuration.Short)
            libraryVm.undoShown()
            if (result == SnackbarResult.ActionPerformed) u.undo()
        }
    }
    LaunchedEffect(celebrate) { if (celebrate.isNotEmpty()) actions.celebrateHaptic() }

    val requireSignIn: (() -> Unit) -> Unit = { then ->
        accountVm.setAfterSignIn(then)
        showSignIn = true
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            // A song page has its own bar (back, share, ⋮), so the app header steps aside.
            // An open set has its own bar too (back, share, ⋮).
            if (!(songOpen != null && (tab == TAB_LIBRARY || tab == TAB_SETS)) && !(tab == TAB_SETS && setOpen != null)) {
                Header(
                    when (tab) {
                        TAB_LISTEN -> "BeatRadar"
                        TAB_LOG -> "Activity Log"
                        TAB_SETS -> "My sets"
                        else -> "Discover"
                    },
                    rewards = { BeatsChip(rewards) { showRewards = true } },
                    onSettings = { showSettings = true },
                )
            }
        },
        bottomBar = {
            TabBar(selected = tab, onSelect = {
                // Another tab: any open song page closes.
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
        when (tab) {
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
                bpmRange = logBpmRange ?: (settings.minBpm..settings.maxBpm),
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
                        val result = snackbar.showSnackbar("Song removed", actionLabel = "Undo", withDismissAction = true)
                        if (result == SnackbarResult.ActionPerformed) vm.restore(entry) else vm.forgetClip(entry)
                    }
                },
                onClear = vm::clearLog,
                modifier = modifier,
            )
            else -> {
                val libraryActions = LibraryActions(
                    signedIn = user != null,
                    requireSignIn = requireSignIn,
                    share = actions.shareSong,
                    openUrl = actions.openUrl,
                    shareText = actions.shareText,
                    updateSettings = vm::updateSettings,
                )
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
        onOpenSong = { slug -> showRewards = false; tab = TAB_LIBRARY; libraryVm.open(slug) },
        onDismiss = { showRewards = false },
    )
    profile?.let { p -> ProfileSheet(p, onReport = { libraryVm.reportNickname(p.nickname); libraryVm.closeProfile() }, onDismiss = libraryVm::closeProfile) }
    if (celebrate.isNotEmpty()) BadgeCelebration(celebrate, onDone = accountVm::celebrated)
    if (showCoffee) CoffeeSheet(coffee, actions.buyCoffee, onDismiss = { showCoffee = false; actions.coffeeThanksShown() })
    if (confirmSignOut) {
        // PLAN §7a: keep = the local copy stays and stops syncing; remove = cleared here, still in the account.
        AlertDialog(
            onDismissRequest = { confirmSignOut = false },
            title = { Text("Keep this phone's log?") },
            text = { Text("Your log stays backed up in your account either way.") },
            confirmButton = { TextButton(onClick = { confirmSignOut = false; accountVm.signOut(keepLog = true) }) { Text("Keep") } },
            dismissButton = { TextButton(onClick = { confirmSignOut = false; accountVm.signOut(keepLog = false) }) { Text("Remove") } },
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
            step = "1 of 2",
            title = "Contribute to the Library?",
            body = "BeatRadar can share the titles, tempos and audio fingerprints of the songs it hears, so anyone can " +
                "search a public tempo library. Fingerprints can't be turned back into sound, voices or words. " +
                "No account needed; if you sign in, your nickname shows on leaderboards.",
            onAllow = { step = 2 },
            onDeny = { onAnswer(false, false) },
        )
    } else {
        ConsentDialog(
            step = "2 of 2",
            title = "Help name unknown songs?",
            body = "When BeatRadar can't name a song, it can send a 12-second clip of it, so an admin can listen and name " +
                "it for everyone. Only music is clipped, clips are deleted within 30 days, and turning this off later " +
                "deletes yours from the server.",
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
                    "You can change this anytime in Settings.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { Button(onClick = onAllow) { Text("Allow") } },
        dismissButton = { TextButton(onClick = onDeny) { Text("Don't allow") } },
    )
}

/** Three tips with Play's localised prices, then a thank-you (PLAN §8). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CoffeeSheet(state: CoffeeState, onBuy: (Coffee) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(start = 24.dp, end = 24.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Buy me a coffee ☕", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            val ready = state as? CoffeeState.Ready
            when {
                ready == null -> Text("Tips aren't available on this device.")
                ready.thanks -> Text("Thank you! That keeps BeatRadar going. 💜", style = MaterialTheme.typography.bodyLarge)
                ready.pending -> Text("Thanks! Your payment is pending; it completes once your payment method confirms it.")
                else -> {
                    Text(
                        "BeatRadar is free and stays free. A tip unlocks nothing; it just says thanks.",
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

/** Slim header: squircle logo badge + title on the left, beats chip and bordered settings button on the right. */
@Composable
private fun Header(title: String, rewards: @Composable () -> Unit, onSettings: () -> Unit) {
    // Design: a 40 dp logo tile, the title at 20 sp bold, the beats chip and a bare 36 dp settings button.
    Row(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .height(60.dp)
            .padding(start = 18.dp, end = 12.dp),
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
        Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        rewards()
        Spacer(Modifier.width(6.dp))
        Box(
            Modifier
                .size(36.dp)
                .clip(CircleShape)
                .clickable(onClick = onSettings),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(R.drawable.ic_settings), contentDescription = "Settings", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }
    }
}

/** The floating tab bar: a 62 dp pill, four 48 dp items, the active one filled. */
@Composable
private fun TabBar(selected: Int, onSelect: (Int) -> Unit) {
    val tabs = listOf(
        "Listen" to R.drawable.ic_graphic_eq,
        "Log" to R.drawable.ic_history,
        "Library" to R.drawable.ic_library_music,
        "Sets" to R.drawable.ic_queue,
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
                Text(label, color = tint, fontSize = 11.sp, lineHeight = 13.sp, fontWeight = if (active) FontWeight.Medium else FontWeight.Normal)
            }
        }
    }
}
