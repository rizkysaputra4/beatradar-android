package com.boimin.beatradar.i18n

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.boimin.beatradar.R
import com.boimin.beatradar.net.ApiException
import com.boimin.beatradar.ui.theme.Tone
import kotlinx.coroutines.launch
import java.util.Locale

/** The language the app shows now, in its own words, for the Settings row ("Bahasa Indonesia", "English"). */
@Composable
fun currentLanguageName(): String {
    val pack by Strings.pack.collectAsState()
    return pack?.nativeName ?: "English"
}

/** Above this many languages the list gets a search field. */
private const val SEARCH_FROM = 6

/** Marks the "follow the phone" choice while it switches (its code is null). */
private const val SYSTEM = "system"

/**
 * Settings → Language, in the app's sheet style (as the Listen tab's Target range sheet).
 *
 * On top, "Follow phone language": what the phone is set to, and whether BeatRadar has it (if not, it shows English).
 * Below, every language: English (built in) and the ones the server has packs for, by their own name with the English
 * one under it. Picking one downloads its core strings and switches at once, so the sheet itself changes language; the
 * rest of the pack downloads behind it, with its progress under the language in use. A failed switch says why on its
 * row, with Try again. The sheet stays open to show the switch; Done closes it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguageSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pack by Strings.pack.collectAsState()
    val filling by LanguageSync.filling.collectAsState()

    var languages by remember { mutableStateOf(LanguageSync.lastList) }
    // Why the server's list didn't load: no connection, or the server failed. Null while loading or once loaded.
    var loadFailed by remember { mutableStateOf<Int?>(null) }
    var choice by remember { mutableStateOf(LanguageSync.choice(context)) }
    // The row switching now (SYSTEM for "follow the phone"), and the row whose switch failed, with why.
    var switching by remember { mutableStateOf<String?>(null) }
    var failed by remember { mutableStateOf<Pair<String, Int>?>(null) }
    var query by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        try {
            languages = LanguageSync.available()
        } catch (e: ApiException) {
            when {
                // A server without language packs yet: English is all there is.
                e.http == 404 -> languages = emptyList()
                // Shown from the last load already: keep it rather than swap it for an error.
                languages != null -> Unit
                e.offline -> loadFailed = R.string.language_list_failed
                else -> loadFailed = R.string.language_list_error
            }
        }
    }

    fun pick(code: String?) {
        val row = code ?: SYSTEM
        if (switching != null) return
        switching = row
        failed = null
        scope.launch {
            try {
                LanguageSync.choose(context, code)
                choice = code
            } catch (e: ApiException) {
                failed = row to if (e.offline) R.string.language_offline else R.string.language_unavailable
            } finally {
                switching = null
            }
        }
    }

    val phone = Locale.forLanguageTag(LanguageSync.systemTag())
    val phoneName = phone.getDisplayName(phone).replaceFirstChar { it.titlecase(phone) }
    // The pack following the phone would use, or null when that's English (built in, or not on the server).
    val phonePack = languages?.let { list ->
        list.firstOrNull { it.code.equals(phone.toLanguageTag(), ignoreCase = true) }
            ?: list.firstOrNull { Locale.forLanguageTag(it.code).language == phone.language }
    }
    val phoneIsEnglish = phone.language == LanguageSync.ENGLISH

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        // A short drag on the list used to close the sheet; it closes with Done, Back or a tap outside.
        sheetGesturesEnabled = false,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = Tone.sheet,
        // Tone.sheet isn't a theme colour, so the sheet can't work out its text colour: without this, untinted text
        // takes whatever the caller had (black outside a Surface) and disappears in dark mode.
        contentColor = MaterialTheme.colorScheme.onSurface,
        dragHandle = {
            Box(Modifier.padding(top = 10.dp, bottom = 16.dp).size(36.dp, 4.dp).clip(RoundedCornerShape(2.dp)).background(Color(0xFF3A3946)))
        },
    ) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 24.dp)) {
            Text(str(R.string.language_title), fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(2.dp))
            Text(str(R.string.language_subtitle), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

            Spacer(Modifier.height(16.dp))
            FollowPhoneCard(
                phoneName = phoneName,
                status = when {
                    phoneIsEnglish || phonePack != null -> null
                    languages == null -> null
                    else -> str(R.string.language_phone_unavailable, phoneName)
                },
                selected = choice == null,
                busy = switching == SYSTEM,
                error = failed?.takeIf { it.first == SYSTEM }?.second,
                onClick = { pick(null) },
            )

            Spacer(Modifier.height(20.dp))
            Text(
                str(R.string.language_all),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))

            val all = languages.orEmpty()
            if (all.size + 1 > SEARCH_FROM) {
                SearchField(query, onChange = { query = it })
                Spacer(Modifier.height(8.dp))
            }
            val q = query.trim()
            val shown = if (q.isEmpty()) all else all.filter { it.nativeName.contains(q, ignoreCase = true) || it.name.contains(q, ignoreCase = true) || it.code.startsWith(q, ignoreCase = true) }
            // The language in use: the one picked, or the phone's when following it.
            val active = choice ?: (phonePack?.code ?: LanguageSync.ENGLISH)
            val progress = pack?.takeIf { filling && it.missing.isNotEmpty() }?.let { it.loaded.count { c -> c in it.chunks } to it.chunks.size }

            // No animateContentSize here: on a scrolling column it fights the scroll at the ends and the list shakes.
            Column(
                Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (q.isEmpty() || "english".contains(q, ignoreCase = true) || q.equals("en", ignoreCase = true)) {
                    LanguageRow(
                        code = LanguageSync.ENGLISH,
                        title = "English",
                        subtitle = str(R.string.language_built_in),
                        selected = choice == LanguageSync.ENGLISH,
                        inUse = active == LanguageSync.ENGLISH,
                        busy = switching == LanguageSync.ENGLISH,
                        progress = null,
                        error = failed?.takeIf { it.first == LanguageSync.ENGLISH }?.second,
                        onClick = { pick(LanguageSync.ENGLISH) },
                    )
                }
                shown.forEach { l ->
                    LanguageRow(
                        code = l.code,
                        title = l.nativeName,
                        subtitle = l.name.takeIf { it != l.nativeName },
                        selected = choice == l.code,
                        inUse = active == l.code,
                        busy = switching == l.code,
                        progress = progress?.takeIf { pack?.code == l.code },
                        error = failed?.takeIf { it.first == l.code }?.second,
                        onClick = { pick(l.code) },
                    )
                }
                when {
                    languages == null && loadFailed == null -> repeat(2) { PlaceholderRow() }
                    loadFailed != null -> Text(str(loadFailed!!), fontSize = 12.sp, color = Tone.text3, modifier = Modifier.padding(top = 4.dp))
                    q.isNotEmpty() && shown.isEmpty() -> Text(str(R.string.language_no_match), fontSize = 13.sp, color = Tone.text3, modifier = Modifier.padding(vertical = 8.dp))
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
                Text(str(R.string.language_done), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
            }
        }
    }
}

/** "Follow phone language / English (United Kingdom)": the default, set apart from the list above it. */
@Composable
private fun FollowPhoneCard(phoneName: String, status: String?, selected: Boolean, busy: Boolean, error: Int?, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) Tone.selected else Tone.track)
            .border(1.dp, if (selected) MaterialTheme.colorScheme.primary else Color.Transparent, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(36.dp).clip(CircleShape).background(Tone.pill), contentAlignment = Alignment.Center) {
                Icon(painterResource(R.drawable.ic_settings), contentDescription = null, tint = Tone.text2, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(str(R.string.language_follow_phone), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                Text(phoneName, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(12.dp))
            Trailing(selected = selected, busy = busy)
        }
        status?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, fontSize = 12.sp, color = Tone.text3)
        }
        error?.let { ErrorLine(it, onRetry = onClick) }
    }
}

/**
 * A language: a code badge, its own name over its English name (or "Built in"), and a check when picked. The one in
 * use while the rest of it downloads shows how far that is.
 */
@Composable
private fun LanguageRow(
    code: String,
    title: String,
    subtitle: String?,
    selected: Boolean,
    inUse: Boolean,
    busy: Boolean,
    progress: Pair<Int, Int>?,
    error: Int?,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) Tone.selected else Tone.track)
            .border(1.dp, if (selected) MaterialTheme.colorScheme.primary else Color.Transparent, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CodeBadge(code, highlighted = inUse)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                subtitle?.let { Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
            Spacer(Modifier.width(12.dp))
            Trailing(selected = selected, busy = busy)
        }
        progress?.let { (done, total) ->
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(12.dp), strokeWidth = 1.5.dp, color = Tone.primarySoft)
                Spacer(Modifier.width(8.dp))
                Text(str(R.string.language_downloading_rest, done, total), fontSize = 12.sp, color = Tone.primarySoft)
            }
        }
        error?.let { ErrorLine(it, onRetry = onClick) }
    }
}

/** "EN", "ID", "PT-BR": the language's code in a 36 dp circle, violet when it's the one in use. */
@Composable
private fun CodeBadge(code: String, highlighted: Boolean) {
    Box(
        Modifier.size(36.dp).clip(CircleShape).background(if (highlighted) MaterialTheme.colorScheme.primary else Tone.pill),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            code.uppercase(Locale.ROOT).take(5),
            fontSize = if (code.length > 2) 10.sp else 12.sp,
            fontWeight = FontWeight.Bold,
            color = if (highlighted) Color.White else Tone.text2,
        )
    }
}

/** A spinner while the row switches, else a check when it's picked. */
@Composable
private fun Trailing(selected: Boolean, busy: Boolean) {
    when {
        busy -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        selected -> Icon(painterResource(R.drawable.ic_check), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        else -> Spacer(Modifier.size(20.dp))
    }
}

/** Why a switch failed, and Try again. */
@Composable
private fun ErrorLine(message: Int, onRetry: () -> Unit) {
    Spacer(Modifier.height(8.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(str(message), fontSize = 12.sp, color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        Text(
            str(R.string.language_retry),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onRetry).padding(horizontal = 6.dp, vertical = 4.dp),
        )
    }
}

/** Shown while the server's list loads, so the sheet doesn't jump when it arrives. */
@Composable
private fun PlaceholderRow() {
    Row(
        Modifier.fillMaxWidth().height(58.dp).clip(RoundedCornerShape(14.dp)).background(Tone.track).padding(horizontal = 14.dp).alpha(0.6f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(36.dp).clip(CircleShape).background(Tone.pill))
        Spacer(Modifier.width(12.dp))
        Column {
            Box(Modifier.size(120.dp, 12.dp).clip(RoundedCornerShape(6.dp)).background(Tone.pill))
            Spacer(Modifier.height(6.dp))
            Box(Modifier.size(70.dp, 10.dp).clip(RoundedCornerShape(5.dp)).background(Tone.pill))
        }
    }
}

/** A 44 dp search field for long lists. */
@Composable
private fun SearchField(query: String, onChange: (String) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(R.drawable.ic_search), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (query.isEmpty()) Text(str(R.string.language_search), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            BasicTextField(
                value = query,
                onValueChange = onChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
