package com.rizky.beatradar.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.rizky.beatradar.data.Settings
import com.rizky.beatradar.data.ThemeMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    settings: Settings,
    hasNotificationAccess: () -> Boolean,
    onChange: ((Settings) -> Settings) -> Unit,
    onOpenNotificationAccess: () -> Unit,
    onDismiss: () -> Unit,
) {
    var access by remember { mutableStateOf(hasNotificationAccess()) }
    // Re-check when returning from the system settings screen.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { access = hasNotificationAccess() }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(start = 24.dp, end = 24.dp, bottom = 32.dp)) {
            Text("Settings", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(20.dp))

            SectionLabel("Appearance")
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
            Spacer(Modifier.height(20.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))

            SwitchRow("Vibrate on match", "Buzzes even when the phone is on silent", settings.vibrate) { on ->
                onChange { it.copy(vibrate = on) }
            }
            SwitchRow("Count half / double tempo", "For example, 65 BPM counts as 130", settings.halfDoubleMatch) { on ->
                onChange { it.copy(halfDoubleMatch = on) }
            }
            SwitchRow(
                "Save clips of unnamed songs",
                "Keeps 12 s (about 0.5 MB) so you can play it to Shazam later from the log. Deleted once named.",
                settings.saveClips,
            ) { on -> onChange { it.copy(saveClips = on) } }
            Spacer(Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            SectionLabel("Song names")
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
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 10.dp),
    )
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
