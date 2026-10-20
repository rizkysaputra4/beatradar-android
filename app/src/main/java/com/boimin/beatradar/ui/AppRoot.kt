package com.boimin.beatradar.ui

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
    val exportLog: () -> Unit,
    /** Android share sheet with the song's share link. */
    val shareSong: (SongSummary) -> Unit,
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

@Composable
fun AppRoot(vm: MainViewModel, accountVm: AccountViewModel, libraryVm: LibraryViewModel, settings: Settings, actions: AppActions) {
    val monitor by vm.monitor.collectAsStateWithLifecycle()
    val log by vm.log.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val onlyInRange by vm.onlyInRange.collectAsStateWithLifecycle()
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
    var confirmSignOut by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) { accountVm.loadRewards() }
    // A share link (App Link) opens the song in the Library.
    LaunchedEffect(openSong) {
        openSong?.let { slug ->
            tab = TAB_LIBRARY
            libraryVm.open(slug)
            vm.openSong.value = null
        }
    }
    LaunchedEffect(accountToast) { accountToast?.let { snackbar.showSnackbar(it); accountVm.toastShown() } }
    LaunchedEffect(logMessage) { logMessage?.let { snackbar.showSnackbar(it); vm.messageShown() } }
    LaunchedEffect(libraryMessage) { libraryMessage?.let { snackbar.showSnackbar(it); libraryVm.messageShown() } }
    LaunchedEffect(celebrate) { if (celebrate.isNotEmpty()) actions.celebrateHaptic() }

    val requireSignIn: (() -> Unit) -> Unit = { then ->
        accountVm.setAfterSignIn(then)
        showSignIn = true
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Header(
                when (tab) {
                    TAB_LISTEN -> "BeatRadar"
                    TAB_LOG -> "Activity Log"
                    else -> "Discover"
                },
                rewards = { BeatsChip(rewards) { showRewards = true } },
                onSettings = { showSettings = true },
            )
        },
        bottomBar = { TabBar(selected = tab, onSelect = { tab = it }) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val modifier = Modifier.padding(padding)
        when (tab) {
            TAB_LISTEN -> ListenScreen(
                state = monitor,
                settings = settings,
                actions = actions,
                onSettingsChange = vm::updateSettings,
                suggestion = currentEntry?.takeIf { it.title == null }?.suggestion,
                onAcceptSuggestion = { currentEntry?.let(vm::acceptSuggestion) },
                onRejectSuggestion = { currentEntry?.let(vm::rejectSuggestion) },
                modifier = modifier,
            )
            TAB_LOG -> LogScreen(
                entries = log,
                query = query,
                onQueryChange = { vm.query.value = it },
                onlyInRange = onlyInRange,
                targetLabel = "${settings.minBpm}–${settings.maxBpm}",
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
                onRename = vm::rename,
                onOpenSong = { slug -> tab = TAB_LIBRARY; libraryVm.open(slug) },
                refreshing = refreshing,
                onRefresh = vm::refresh,
                onDelete = { entry ->
                    vm.delete(entry)
                    scope.launch {
                        val result = snackbar.showSnackbar("Song removed", actionLabel = "Undo", withDismissAction = true)
                        if (result == SnackbarResult.ActionPerformed) vm.restore(entry) else vm.forgetClip(entry)
                    }
                },
                onClear = vm::clearLog,
                onExport = actions.exportLog,
                modifier = modifier,
            )
            else -> LibraryScreen(
                vm = libraryVm,
                settings = settings,
                actions = LibraryActions(
                    signedIn = user != null,
                    requireSignIn = requireSignIn,
                    share = actions.shareSong,
                    openUrl = actions.openUrl,
                ),
                modifier = modifier,
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
            ),
            onDismiss = { showSettings = false },
        )
    }
    if (showSignIn) SignInSheet(accountVm, onDismiss = { showSignIn = false })
    if (showRewards) RewardsSheet(accountVm, onSignIn = { showRewards = false; requireSignIn {} }, onDismiss = { showRewards = false })
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
    Row(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .height(54.dp)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(R.drawable.ic_radar),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp),
            )
        }
        Spacer(Modifier.width(9.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )
        rewards()
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                .clickable(onClick = onSettings),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(R.drawable.ic_settings),
                contentDescription = "Settings",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

/** Floating Pill Dock navigation bar, matching the surface cards and 1.dp outline system. */
@Composable
private fun TabBar(selected: Int, onSelect: (Int) -> Unit) {
    val tabs = listOf("Listen" to R.drawable.ic_radar, "Log" to R.drawable.ic_list, "Library" to R.drawable.ic_library)
    Row(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 18.dp, vertical = 10.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
            .padding(5.dp),
    ) {
        tabs.forEachIndexed { i, (label, icon) ->
            val active = i == selected
            Row(
                Modifier
                    .weight(1f)
                    .height(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (active) MaterialTheme.colorScheme.primary else Color.Transparent)
                    .clickable { onSelect(i) },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val tint = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                Icon(painterResource(icon), contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    label,
                    color = tint,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}
