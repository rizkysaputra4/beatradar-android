package com.boimin.beatradar.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.boimin.beatradar.R
import com.boimin.beatradar.rewards.ProfileAvatar
import com.boimin.beatradar.data.Settings
import com.boimin.beatradar.data.ThemeMode
import com.boimin.beatradar.i18n.LanguageSheet
import com.boimin.beatradar.i18n.appLocale
import com.boimin.beatradar.i18n.currentLanguageName
import com.boimin.beatradar.i18n.str
import com.boimin.beatradar.i18n.strPlural
import com.boimin.beatradar.net.Me
import com.boimin.beatradar.ui.theme.Brand
import com.boimin.beatradar.ui.theme.Tone
import com.boimin.beatradar.ui.theme.brandSwitchColors
import kotlin.math.roundToInt

/**
 * Settings, in the app's sheet style (as Language and the Listen tab's Target range): a title over a subtitle, then
 * sections of grouped cards. Rows that open something have an icon in a circle and a chevron; choices are pill
 * toggles; Done closes it.
 */
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
    var choosingLanguage by remember { mutableStateOf(false) }
    if (choosingLanguage) LanguageSheet(onDismiss = { choosingLanguage = false })
    // Re-check when returning from the system settings screen.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { access = hasNotificationAccess() }

    CalmBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberCalmSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = Tone.sheet,
        // Tone.sheet isn't a theme colour, so untinted text would otherwise take the caller's (black) colour.
        contentColor = MaterialTheme.colorScheme.onSurface,
        dragHandle = {
            Box(Modifier.padding(top = 10.dp, bottom = 16.dp).size(36.dp, 4.dp).clip(RoundedCornerShape(2.dp)).background(Color(0xFF3A3946)))
        },
    ) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 24.dp)) {
            Text(str(R.string.settings_title), fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(2.dp))
            Text(str(R.string.settings_subtitle), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

            SectionLabel(str(R.string.settings_section_account))
            AccountSection(account)

            SectionLabel(str(R.string.settings_section_appearance))
            Card {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                    Text(str(R.string.settings_theme_title), fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(10.dp))
                    PillToggle(
                        options = listOf(
                            ThemeMode.SYSTEM to str(R.string.settings_theme_system),
                            ThemeMode.LIGHT to str(R.string.settings_theme_light),
                            ThemeMode.DARK to str(R.string.settings_theme_dark),
                        ),
                        selected = settings.theme,
                        onSelect = { mode -> onChange { it.copy(theme = mode) } },
                    )
                }
                Divider()
                // Language: the phone's unless picked here; other languages download from the server.
                NavRow(R.drawable.ic_language, str(R.string.language_title), currentLanguageName(), onClick = { choosingLanguage = true })
            }

            SectionLabel(str(R.string.settings_section_listening))
            Card {
                SwitchRow(str(R.string.settings_vibrate_title), str(R.string.settings_vibrate_desc), settings.vibrate) { on ->
                    onChange { it.copy(vibrate = on) }
                }
                Divider()
                SwitchRow(str(R.string.settings_half_double_title), str(R.string.settings_half_double_desc), settings.halfDoubleMatch) { on ->
                    onChange { it.copy(halfDoubleMatch = on) }
                }
                Divider()
                SwitchRow(str(R.string.settings_save_clips_title), str(R.string.settings_save_clips_desc), settings.saveClips) { on ->
                    onChange { it.copy(saveClips = on) }
                }
                Divider()
                SwitchRow(
                    str(R.string.settings_clip_all_title),
                    str(R.string.settings_clip_all_desc),
                    settings.clipAllSongs,
                    enabled = settings.saveClips,
                ) { on -> onChange { it.copy(clipAllSongs = on) } }
                Divider()
                Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp).alpha(if (settings.saveClips) 1f else 0.38f)) {
                    Text(
                        strPlural(R.plurals.settings_clip_limit, settings.clipLimit, settings.clipLimit, clipStorageMb(settings.clipLimit)),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(Modifier.height(10.dp))
                    PillToggle(
                        options = Settings.CLIP_LIMITS.map { it to it.toString() },
                        selected = settings.clipLimit,
                        enabled = settings.saveClips,
                        onSelect = { limit -> onChange { it.copy(clipLimit = limit) } },
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(str(R.string.settings_clip_limit_note), fontSize = 12.sp, color = Tone.text3)
                }
            }

            SectionLabel(str(R.string.settings_section_song_names))
            Card {
                NavRow(
                    R.drawable.ic_note,
                    str(R.string.settings_access_title),
                    str(if (access) R.string.settings_access_allowed else R.string.settings_access_not_allowed),
                    onClick = onOpenNotificationAccess,
                    trailing = {
                        ActionChip(
                            str(if (access) R.string.settings_access_manage else R.string.settings_access_allow),
                            filled = !access,
                            onClick = onOpenNotificationAccess,
                        )
                    },
                    subtitleColor = if (access) Brand.Mint else Tone.text3,
                )
                Column(Modifier.padding(start = 14.dp, end = 14.dp, bottom = 12.dp)) {
                    Text(str(R.string.settings_song_names_desc), fontSize = 12.sp, lineHeight = 17.sp, color = Tone.text3)
                    Spacer(Modifier.height(8.dp))
                    Text(str(R.string.settings_auto_shazam_tip), fontSize = 12.sp, lineHeight = 17.sp, color = Tone.text3)
                }
            }

            SectionLabel(str(R.string.settings_section_library))
            Card {
                SwitchRow(str(R.string.settings_contribute_title), str(R.string.settings_contribute_desc), settings.contribute) { on ->
                    onChange { it.copy(contribute = on) }
                }
                Divider()
                val canShareClips = settings.contribute && settings.saveClips
                SwitchRow(
                    str(R.string.settings_share_clips_title),
                    str(if (canShareClips) R.string.settings_share_clips_desc else R.string.settings_share_clips_desc_needs),
                    settings.shareClips && canShareClips,
                    enabled = canShareClips,
                ) { on -> onChange { it.copy(shareClips = on) } }
                Divider()
                NavRow(R.drawable.ic_list, str(R.string.settings_shared_link), str(R.string.settings_shared_link_desc), onClick = account.onShowShared)
            }
            if (account.coffeeAvailable) {
                Spacer(Modifier.height(10.dp))
                Card {
                    NavRow(R.drawable.ic_coffee, str(R.string.settings_coffee_title), str(R.string.settings_coffee_desc), onClick = account.onCoffee, iconTint = Brand.Flame)
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
                Text(str(R.string.settings_done), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
            }
        }
    }
}

/** "ACCOUNT": the section's name over its card, as the Rewards sheet's headers. */
@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(appLocale()),
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.8.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, top = 20.dp, bottom = 8.dp),
    )
}

/** One section's rows, on the sheet's track colour with rounded corners. */
@Composable
private fun Card(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Tone.track), content = content)
}

@Composable
private fun Divider() = HorizontalDivider(Modifier.padding(horizontal = 14.dp), thickness = 1.dp, color = Tone.sheetLine)

/** A row that opens something: an icon in a circle, a title over a subtitle, then a chevron (or [trailing]). */
@Composable
private fun NavRow(
    @DrawableRes icon: Int,
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
    iconTint: Color = Tone.primarySoft,
    subtitleColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(36.dp).clip(CircleShape).background(Tone.pill), contentAlignment = Alignment.Center) {
            Icon(painterResource(icon), contentDescription = null, tint = iconTint, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            subtitle?.let { Text(it, fontSize = 12.sp, color = subtitleColor, maxLines = 2, overflow = TextOverflow.Ellipsis) }
        }
        Spacer(Modifier.width(12.dp))
        if (trailing != null) {
            trailing()
        } else {
            Icon(painterResource(R.drawable.ic_expand_more), contentDescription = null, tint = Tone.text3, modifier = Modifier.size(20.dp).rotate(-90f))
        }
    }
}

/** A small pill button inside a row: violet when it's the thing to do, else quiet. */
@Composable
private fun ActionChip(label: String, filled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(if (filled) MaterialTheme.colorScheme.primary else Tone.pill)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
    ) {
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (filled) Color.White else Tone.text2)
    }
}

/** A choice of a few options as one pill track, the picked one filled violet (as the leaderboard's Week / All Time). */
@Composable
private fun <T> PillToggle(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit, enabled: Boolean = true) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Tone.pill).padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        options.forEach { (value, label) ->
            val on = value == selected
            Box(
                Modifier
                    .weight(1f)
                    .height(36.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (on) MaterialTheme.colorScheme.primary else Color.Transparent)
                    .clickable(enabled = enabled) { onSelect(value) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    fontSize = 13.sp,
                    fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (on) Color.White else Tone.text2,
                )
            }
        }
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onChange(!checked) }
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .alpha(if (enabled) 1f else 0.38f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, fontSize = 12.sp, lineHeight = 17.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled, colors = brandSwitchColors())
    }
}

/** 15 (MB) for 30 clips of ~0.5 MB. */
internal fun clipStorageMb(limit: Int): Int = (limit * Settings.CLIP_MB).roundToInt()

/** Change the nickname: 3–24 characters, checked again by the server. */
@Composable
internal fun NicknameDialog(current: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var nickname by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(str(R.string.settings_nickname_title)) },
        text = {
            OutlinedTextField(
                value = nickname,
                onValueChange = { nickname = it.take(24) },
                singleLine = true,
                supportingText = { Text(str(R.string.settings_nickname_hint)) },
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(nickname) }, enabled = nickname.trim().length >= 3) { Text(str(R.string.settings_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(str(R.string.settings_cancel)) } },
    )
}

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
        Card {
            Column(Modifier.padding(14.dp)) {
                Text(str(R.string.settings_account_signed_out), fontSize = 13.sp, lineHeight = 19.sp, color = Tone.text2)
                Spacer(Modifier.height(12.dp))
                Box(
                    Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.primary).clickable(onClick = a.onSignIn),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(str(R.string.settings_account_sign_in), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                }
            }
        }
        return
    }
    var renaming by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val name = user.nickname ?: str(R.string.settings_account_no_nickname)
    Card {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            ProfileAvatar(user.nickname, 40)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(user.email ?: str(R.string.settings_account_google), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(8.dp))
            ActionChip(str(R.string.settings_account_rename), filled = false, onClick = { renaming = true })
        }
        Divider()
        SwitchRow(str(R.string.settings_account_leaderboard_title), str(R.string.settings_account_leaderboard_desc), user.onLeaderboard) { a.onLeaderboard(it) }
        Divider()
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = a.onSignOut) { Text(str(R.string.settings_account_sign_out), color = Tone.text2) }
            TextButton(onClick = { confirmDelete = true }) { Text(str(R.string.settings_account_delete), color = MaterialTheme.colorScheme.error) }
        }
    }
    if (renaming) NicknameDialog(user.nickname.orEmpty(), onSave = { renaming = false; a.onRename(it) }, onDismiss = { renaming = false })
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(str(R.string.settings_delete_title)) },
            text = { Text(str(R.string.settings_delete_text)) },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; a.onDeleteAccount() }) { Text(str(R.string.settings_delete_confirm), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(str(R.string.settings_cancel)) } },
        )
    }
}
