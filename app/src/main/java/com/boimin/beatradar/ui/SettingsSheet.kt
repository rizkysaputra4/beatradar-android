package com.boimin.beatradar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import com.boimin.beatradar.R
import com.boimin.beatradar.data.Settings
import com.boimin.beatradar.net.Me
import com.boimin.beatradar.data.ThemeMode
import com.boimin.beatradar.ui.theme.brandSwitchColors
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    settings: Settings,
    hasNotificationAccess: () -> Boolean,
    onChange: ((Settings) -> Settings) -> Unit,
    onOpenNotificationAccess: () -> Unit,
    account: AccountSettings,
    onDismiss: () -> Unit,
) {
    var access by remember { mutableStateOf(hasNotificationAccess()) }
    // Re-check when returning from the system settings screen.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { access = hasNotificationAccess() }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, bottom = 32.dp)) {
            Text("Settings", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(16.dp))

            SectionLabel("Account")
            SettingsCard { AccountSection(account) }

            SectionLabel("Appearance")
            SettingsCard(Modifier.padding(vertical = 8.dp)) {
                val themes = listOf(ThemeMode.SYSTEM to "System", ThemeMode.LIGHT to "Light", ThemeMode.DARK to "Dark")
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    themes.forEachIndexed { i, (mode, label) ->
                        SegmentedButton(
                            selected = settings.theme == mode,
                            onClick = { onChange { it.copy(theme = mode) } },
                            shape = SegmentedButtonDefaults.itemShape(i, themes.size),
                        ) { Text(label) }
                    }
                }
            }

            SectionLabel("Listening & clips")
            SettingsCard {
                SwitchRow("Vibrate on match", "Buzzes even when the phone is on silent", settings.vibrate) { on ->
                    onChange { it.copy(vibrate = on) }
                }
                SwitchRow("Count half / double tempo", "For example, 65 BPM counts as 130", settings.halfDoubleMatch) { on ->
                    onChange { it.copy(halfDoubleMatch = on) }
                }
                SwitchRow(
                    "Save clips of unnamed songs",
                    "Keeps 12 s of a song without a name to replay or play to Shazam later from the log. Deleted once named; stays on your phone unless you turn on Help name unknown songs.",
                    settings.saveClips,
                ) { on -> onChange { it.copy(saveClips = on) } }
                SwitchRow(
                    "All songs, not just matches",
                    "Off: only songs that matched your BPM range get a clip",
                    settings.clipAllSongs,
                    enabled = settings.saveClips,
                ) { on -> onChange { it.copy(clipAllSongs = on) } }
                Text(
                    "Keep clips of the last ${settings.clipLimit} songs · about ${clipStorage(settings.clipLimit)}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (settings.saveClips) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                    modifier = Modifier.padding(top = 8.dp, bottom = 8.dp),
                )
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    Settings.CLIP_LIMITS.forEachIndexed { i, limit ->
                        SegmentedButton(
                            selected = settings.clipLimit == limit,
                            onClick = { onChange { it.copy(clipLimit = limit) } },
                            shape = SegmentedButtonDefaults.itemShape(i, Settings.CLIP_LIMITS.size),
                            enabled = settings.saveClips,
                        ) { Text(limit.toString()) }
                    }
                }
                Text(
                    "Older clips are deleted automatically; their songs stay in the log.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp, bottom = 8.dp),
                )
            }

            SectionLabel("Song names")
            SettingsCard(Modifier.padding(vertical = 12.dp)) {
                Text(
                    "Reads song titles from music apps, Shazam and Pixel “Now Playing”. Nothing leaves your phone.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (access) "Access allowed ✓" else "Access not allowed",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    FilledTonalButton(onClick = onOpenNotificationAccess) { Text(if (access) "Manage" else "Allow") }
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    "Tip: turn on Auto Shazam in the Shazam app and songs around you get named automatically.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            SectionLabel("Library")
            SettingsCard {
                SwitchRow(
                    "Contribute to the Library",
                    "Shares the titles, tempos and audio fingerprints BeatRadar hears (never audio) to build a public tempo library",
                    settings.contribute,
                ) { on -> onChange { it.copy(contribute = on) } }
                val canShareClips = settings.contribute && settings.saveClips
                SwitchRow(
                    "Help name unknown songs",
                    "Sends a 12-second clip of songs BeatRadar couldn't name, so an admin can listen and name them for everyone. " +
                        "Only music, kept at most 30 days. Turning this off deletes your clips from the server." +
                        if (canShareClips) "" else " Needs Contribute to the Library and Save clips of unnamed songs.",
                    settings.shareClips && canShareClips,
                    enabled = canShareClips,
                ) { on -> onChange { it.copy(shareClips = on) } }
            }
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = account.onShowShared).padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("What I've shared →", style = MaterialTheme.typography.bodyLarge)
                    Text("Everything this phone sent and how long it's kept.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (account.coffeeAvailable) {
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = account.onCoffee).padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(painterResource(R.drawable.ic_coffee), null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Buy me a coffee ☕", style = MaterialTheme.typography.bodyLarge)
                        Text("A small tip for the developer. Unlocks nothing; BeatRadar stays free.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

/** Small caps-style label above a card, like the revamped screens' section headers. */
@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 6.dp),
    )
}

/** A rounded surface card holding one settings section. */
@Composable
private fun SettingsCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp)
            .then(modifier),
        content = content,
    )
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp).alpha(if (enabled) 1f else 0.38f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            enabled = enabled,
            colors = brandSwitchColors(),
        )
    }
}

/** "15 MB" for 30 clips of ~0.5 MB. */
internal fun clipStorage(limit: Int): String = "${(limit * Settings.CLIP_MB).roundToInt()} MB"

/** The account part of Settings (PLAN §8: sign in, sign out and delete live here, not in a tab). */
class AccountSettings(
    val user: Me?,
    val onSignIn: () -> Unit,
    val onSignOut: () -> Unit,
    val onDeleteAccount: () -> Unit,
    val onRename: (String) -> Unit,
    val onLeaderboard: (Boolean) -> Unit,
    val coffeeAvailable: Boolean,
    val onCoffee: () -> Unit,
    /** "What I've shared": what this phone sent, and how to ask for it to be deleted. */
    val onShowShared: () -> Unit,
)

@Composable
private fun AccountSection(a: AccountSettings) {
    val user = a.user
    if (user == null) {
        Spacer(Modifier.height(12.dp))
        Text(
            "Sign in to love songs, back up your log and join the leaderboards. Everything else works without an account.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        FilledTonalButton(onClick = a.onSignIn) { Text("Sign in") }
        Spacer(Modifier.height(12.dp))
        return
    }
    var renaming by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(painterResource(R.drawable.ic_account), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(user.nickname ?: "No nickname yet", style = MaterialTheme.typography.bodyLarge)
            Text(user.email ?: "Google account", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        TextButton(onClick = { renaming = true }) { Text("Rename") }
    }
    SwitchRow("Show me on leaderboards", "Under your nickname only, never your name or email", user.onLeaderboard) { a.onLeaderboard(it) }
    Row(Modifier.padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = a.onSignOut) { Text("Sign out") }
        TextButton(onClick = { confirmDelete = true }) { Text("Delete account", color = MaterialTheme.colorScheme.error) }
    }
    if (renaming) {
        var name by remember { mutableStateOf(user.nickname.orEmpty()) }
        AlertDialog(
            onDismissRequest = { renaming = false },
            title = { Text("Nickname") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(24) },
                    singleLine = true,
                    supportingText = { Text("3–24 characters. Shown on leaderboards.") },
                )
            },
            confirmButton = { TextButton(onClick = { renaming = false; a.onRename(name) }, enabled = name.trim().length >= 3) { Text("Save") } },
            dismissButton = { TextButton(onClick = { renaming = false }) { Text("Cancel") } },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete your account?") },
            text = { Text("Your loves, log backup, badges and nickname are deleted for good. Songs you measured stay in the Library without your name. This phone's log stays.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; a.onDeleteAccount() }) { Text("Delete", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}
