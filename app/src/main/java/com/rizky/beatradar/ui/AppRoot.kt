package com.rizky.beatradar.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.rizky.beatradar.R
import com.rizky.beatradar.data.BeatLogEntry
import com.rizky.beatradar.data.Settings
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
)

@Composable
fun AppRoot(vm: MainViewModel, settings: Settings, actions: AppActions) {
    val monitor by vm.monitor.collectAsStateWithLifecycle()
    val log by vm.log.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val onlyInRange by vm.onlyInRange.collectAsStateWithLifecycle()
    val onlyIdentified by vm.onlyIdentified.collectAsStateWithLifecycle()

    var tab by rememberSaveable { mutableIntStateOf(0) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = { Header(if (tab == 0) "BeatRadar" else "Song log", onSettings = { showSettings = true }) },
        bottomBar = { TabBar(selected = tab, onSelect = { tab = it }) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val modifier = Modifier.padding(padding)
        when (tab) {
            0 -> ListenScreen(
                state = monitor,
                settings = settings,
                actions = actions,
                onSettingsChange = vm::updateSettings,
                modifier = modifier,
            )
            else -> LogScreen(
                entries = log,
                query = query,
                onQueryChange = { vm.query.value = it },
                onlyInRange = onlyInRange,
                onlyIdentified = onlyIdentified,
                onToggleInRange = { vm.onlyInRange.value = !onlyInRange },
                onToggleIdentified = { vm.onlyIdentified.value = !onlyIdentified },
                onIdentify = actions.identifyClip,
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
        }
    }

    if (showSettings) {
        SettingsSheet(
            settings = settings,
            hasNotificationAccess = vm::hasNotificationAccess,
            onChange = vm::updateSettings,
            onOpenNotificationAccess = actions.openNotificationAccess,
            onDismiss = { showSettings = false },
        )
    }
}

/** Slim header: logo + title on the left, settings on the right. */
@Composable
private fun Header(title: String, onSettings: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .height(52.dp)
            .padding(start = 20.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painterResource(R.drawable.ic_radar),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        IconButton(onClick = onSettings) {
            Icon(
                painterResource(R.drawable.ic_settings),
                contentDescription = "Settings",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

/** Small pill tab bar, styled like the cards. */
@Composable
private fun TabBar(selected: Int, onSelect: (Int) -> Unit) {
    val tabs = listOf("Listen" to R.drawable.ic_radar, "Log" to R.drawable.ic_list)
    Row(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 56.dp, vertical = 10.dp)
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(4.dp),
    ) {
        tabs.forEachIndexed { i, (label, icon) ->
            val active = i == selected
            Row(
                Modifier
                    .weight(1f)
                    .height(42.dp)
                    .clip(RoundedCornerShape(50))
                    .background(if (active) MaterialTheme.colorScheme.primary else Color.Transparent)
                    .clickable { onSelect(i) },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val tint = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                Icon(painterResource(icon), contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(label, color = tint, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
