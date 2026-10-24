package com.boimin.beatradar.ui

import androidx.compose.ui.unit.em
import com.boimin.beatradar.ui.theme.brandSwitchColors
import androidx.compose.material3.TextButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Button
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.boimin.beatradar.R
import com.boimin.beatradar.data.Settings
import com.boimin.beatradar.i18n.str
import com.boimin.beatradar.ui.theme.Tone
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** "120–130 BPM", "140+ BPM", "Up to 110 BPM" or "Any tempo": either end of a tempo range may be open (null). */
@Composable
internal fun tempoRangeLabel(low: Int?, high: Int?): String = when {
    low != null && high != null -> str(R.string.library_bpm_range, low, high)
    low != null -> str(R.string.tempo_at_least_bpm, low)
    high != null -> str(R.string.tempo_up_to_bpm, high)
    else -> str(R.string.library_any_tempo)
}

/** The short form for a preset tile: "120–130", "140+", "≤110". */
internal fun tempoRangeShort(low: Int?, high: Int?): String = when {
    low != null && high != null -> "$low–$high"
    low != null -> "$low+"
    high != null -> "≤$high"
    else -> "–"
}

/** Below this width a stepper can't sit beside another one. */
private val STEPPER_MIN_WIDTH = 125.dp
/** From this width a stepper keeps its full 44 dp − and + buttons. */
private val STEPPER_ROOMY_WIDTH = 140.dp
/** How far apart the ends of a range start when one end comes back. */
private const val DEFAULT_SPAN = 10

private enum class RangeMode(val label: Int) {
    ANY(R.string.tempo_mode_any),
    BETWEEN(R.string.tempo_mode_between),
    AT_LEAST(R.string.tempo_mode_at_least),
    UP_TO(R.string.tempo_mode_up_to),
}

/**
 * Edits a tempo range with an open end allowed: mode chips (Any, if [allowAny] · Between · At least · Up to), then one
 * or two steppers. Everything is a tap or typing, nothing to drag, so it sits well in a bottom sheet and away from the
 * system back gesture. [fallbackLow]/[fallbackHigh] fill an end that a mode switch brings back.
 */
@Composable
internal fun TempoRangeEditor(
    low: Int?,
    high: Int?,
    fallbackLow: Int,
    fallbackHigh: Int,
    allowAny: Boolean,
    onChange: (low: Int?, high: Int?) -> Unit,
) {
    val floor = Settings.BPM_FLOOR
    val ceiling = Settings.BPM_CEILING
    val mode = when {
        low != null && high != null -> RangeMode.BETWEEN
        low != null -> RangeMode.AT_LEAST
        high != null -> RangeMode.UP_TO
        else -> RangeMode.ANY
    }
    fun pick(m: RangeMode) = when (m) {
        RangeMode.ANY -> onChange(null, null)
        RangeMode.BETWEEN -> {
            // From "140+" or "up to 110" the missing end comes back from the fallback, or [DEFAULT_SPAN] away when the
            // fallback is on the wrong side; never a range as wide as nothing (140–140).
            val (a, b) = when {
                low != null && high != null -> low to high
                low != null -> low to (if (fallbackHigh > low) fallbackHigh else low + DEFAULT_SPAN)
                high != null -> (if (fallbackLow < high) fallbackLow else high - DEFAULT_SPAN) to high
                else -> fallbackLow to maxOf(fallbackHigh, fallbackLow + 1)
            }
            onChange(a.coerceIn(floor, ceiling), b.coerceIn(floor, ceiling))
        }
        RangeMode.AT_LEAST -> onChange(low ?: fallbackLow, null)
        RangeMode.UP_TO -> onChange(null, high ?: fallbackHigh)
    }
    // In Between the two ends push each other rather than cross.
    fun setLow(v: Int) {
        val a = v.coerceIn(floor, ceiling)
        onChange(a, high?.let { maxOf(it, a) })
    }
    fun setHigh(v: Int) {
        val b = v.coerceIn(floor, ceiling)
        onChange(low?.let { minOf(it, b) }, b)
    }

    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            RangeMode.entries.filter { allowAny || it != RangeMode.ANY }.forEach { m ->
                ModeChip(str(m.label), selected = m == mode, Modifier.weight(1f)) { pick(m) }
            }
        }
        Spacer(Modifier.height(12.dp))
        when (mode) {
            RangeMode.BETWEEN -> BoxWithConstraints {
                // Side by side each stepper needs room for − + and the number; on a narrow sheet (a 320 dp phone with a
                // large font, or a large display size) they stack instead of squeezing the number out.
                val sideBySide = (maxWidth - 10.dp) / 2 >= STEPPER_MIN_WIDTH * LocalDensity.current.fontScale.coerceAtLeast(1f)
                if (sideBySide) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        TempoStepper(str(R.string.listen_from), low!!, Modifier.weight(1f), ::setLow)
                        TempoStepper(str(R.string.listen_to), high!!, Modifier.weight(1f), ::setHigh)
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        TempoStepper(str(R.string.listen_from), low!!, Modifier.fillMaxWidth(), ::setLow)
                        TempoStepper(str(R.string.listen_to), high!!, Modifier.fillMaxWidth(), ::setHigh)
                    }
                }
            }
            RangeMode.AT_LEAST -> TempoStepper(str(R.string.tempo_mode_at_least), low!!, Modifier.fillMaxWidth(), ::setLow)
            RangeMode.UP_TO -> TempoStepper(str(R.string.tempo_mode_up_to), high!!, Modifier.fillMaxWidth(), ::setHigh)
            // Same height as a stepper, so picking a mode doesn't make the sheet jump.
            RangeMode.ANY -> Box(Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(RoundedCornerShape(14.dp)).background(Tone.track), Alignment.Center) {
                Text(str(R.string.tempo_any_hint), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ModeChip(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Box(
        modifier
            .heightIn(min = 36.dp)
            .clip(shape)
            .background(if (selected) Tone.selected else Tone.track)
            .border(1.dp, if (selected) MaterialTheme.colorScheme.primary else Color.Transparent, shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        // Four chips across a 320 dp sheet with a large font: "Between" shrinks instead of turning into "Betw…".
        Text(
            label,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            autoSize = TextAutoSize.StepBased(minFontSize = 9.sp, maxFontSize = 13.sp),
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
        )
    }
}

/**
 * "From 140": a field at least 56 dp tall with − and + (hold to repeat); the number itself can be typed over. The
 * buttons shrink on a narrow stepper so the number keeps its room.
 */
@Composable
private fun TempoStepper(label: String, value: Int, modifier: Modifier, onValue: (Int) -> Unit) {
    // Steps count from what is shown, not from [value]: a saved setting comes back a moment later, and a held button
    // would otherwise repeat the same step. It follows [value] again once the button is let go.
    var shown by remember { mutableIntStateOf(value) }
    var holding by remember { mutableStateOf(false) }
    LaunchedEffect(value, holding) { if (!holding) shown = value }
    fun set(v: Int) {
        shown = v.coerceIn(Settings.BPM_FLOOR, Settings.BPM_CEILING)
        onValue(shown)
    }
    BoxWithConstraints(modifier) {
        val button = if (maxWidth < STEPPER_ROOMY_WIDTH) 36.dp else 44.dp
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Tone.track)
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RepeatStepButton(R.drawable.ic_remove, str(R.string.listen_step_lower, label), button, onHold = { holding = it }) { set(shown - 1) }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    label,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    autoSize = TextAutoSize.StepBased(minFontSize = 8.sp, maxFontSize = 11.sp),
                )
                BpmNumberField(shown, ::set)
            }
            RepeatStepButton(R.drawable.ic_add, str(R.string.listen_step_raise, label), button, onHold = { holding = it }) { set(shown + 1) }
        }
    }
}

/** The number in a stepper, typed in place: tapping selects it, Done or tapping elsewhere applies it. */
@Composable
private fun BpmNumberField(value: Int, onCommit: (Int) -> Unit) {
    val focus = LocalFocusManager.current
    var text by remember(value) { mutableStateOf(TextFieldValue(value.toString())) }
    var focused by remember { mutableStateOf(false) }
    fun commit() {
        val typed = text.text.toIntOrNull()
        if (typed != null && typed != value) onCommit(typed) else text = TextFieldValue(value.toString())
    }
    BasicTextField(
        value = text,
        // Nothing above the ceiling can be typed, so a typo like 999 doesn't quietly become a 200–200 range.
        onValueChange = { if (it.text.length <= 3 && it.text.all(Char::isDigit) && (it.text.toIntOrNull() ?: 0) <= Settings.BPM_CEILING) text = it },
        singleLine = true,
        textStyle = TextStyle(
            fontSize = 20.sp,
            lineHeight = 24.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface,
        ),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged {
                if (it.isFocused && !focused) text = text.copy(selection = TextRange(0, text.text.length))
                if (!it.isFocused && focused) commit()
                focused = it.isFocused
            },
    )
}

/** A round − / + ([size], 44 dp unless the stepper is narrow) that steps once on press and keeps stepping while held. */
@Composable
private fun RepeatStepButton(icon: Int, description: String, size: Dp, onHold: (Boolean) -> Unit, onStep: () -> Unit) {
    val step by rememberUpdatedState(onStep)
    val hold by rememberUpdatedState(onHold)
    val focus = LocalFocusManager.current
    var pressed by remember { mutableStateOf(false) }
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(if (pressed) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else Color.Transparent)
            .semantics {
                role = Role.Button
                onClick(label = description) { step(); true }
            }
            .pointerInput(Unit) {
                coroutineScope {
                    awaitEachGesture {
                        awaitFirstDown()
                        // A number being typed is applied first, so the step starts from it.
                        focus.clearFocus()
                        pressed = true
                        hold(true)
                        val repeat = launch {
                            step()
                            delay(400)
                            while (true) {
                                step()
                                delay(60)
                            }
                        }
                        waitForUpOrCancellation()
                        repeat.cancel()
                        pressed = false
                        hold(false)
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), contentDescription = description, tint = Tone.text2, modifier = Modifier.size(22.dp))
    }
}

/**
 * A tempo range to pick in a bottom sheet: the range editor, then the Listen tab's presets. Discover's filter, Build me a
 * set and the Log's filter use it; [allowAny] adds "Any" (no tempo filter). [onHalfDouble] adds the half/double switch,
 * [onUseTarget] a way back to the Listen tab's target. Changes apply as they are made; swipe down, tap outside or Done
 * closes it.
 */
@Composable
internal fun TempoSheet(
    low: Int?,
    high: Int?,
    settings: Settings,
    allowAny: Boolean,
    onRange: (low: Int?, high: Int?) -> Unit,
    onDismiss: () -> Unit,
    halfDouble: Boolean = false,
    onHalfDouble: ((Boolean) -> Unit)? = null,
    onUseTarget: (() -> Unit)? = null,
) {
    CalmBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberCalmSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = Tone.sheet,
        // Tone.sheet isn't a theme colour, so untinted text would not follow dark mode without this.
        contentColor = MaterialTheme.colorScheme.onSurface,
        dragHandle = {
            Box(Modifier.padding(top = 10.dp, bottom = 16.dp).size(36.dp, 4.dp).clip(RoundedCornerShape(2.dp)).background(Tone.sheetLine))
        },
    ) {
        // A short screen or an open keyboard leaves less room than the sheet needs: it scrolls rather than squeezing
        // fixed-height parts (Done, the steppers) out of shape.
        Column(Modifier.imePadding().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 28.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(str(R.string.library_tempo_title), fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(
                    tempoRangeLabel(low, high),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (low != null || high != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(16.dp))
            TempoRangeEditor(
                low = low,
                high = high,
                fallbackLow = settings.minBpm,
                fallbackHigh = settings.maxBpm,
                allowAny = allowAny,
                onChange = onRange,
            )
            Spacer(Modifier.height(16.dp))
            TempoPresets(low, high, onRange)
            if (onUseTarget != null) {
                TextButton(onClick = onUseTarget, modifier = Modifier.padding(top = 4.dp)) {
                    Text("${str(R.string.log_use_target)} · ${tempoRangeShort(settings.targetLow, settings.targetHigh)}")
                }
            }
            if (onHalfDouble != null) {
                Spacer(Modifier.height(16.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onHalfDouble(!halfDouble) }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(str(R.string.settings_half_double_title), fontSize = 15.sp)
                        Text(str(R.string.settings_half_double_desc), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = halfDouble, onCheckedChange = onHalfDouble, colors = brandSwitchColors())
                }
            }
            Spacer(Modifier.height(16.dp))
            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(str(R.string.library_done)) }
        }
    }
}

/**
 * The tempo presets, as on the Listen tab: Groove, House, Run, then "Custom" showing what the editor holds when no preset
 * matches (there is nothing more to open, so it can't be tapped).
 */
@Composable
internal fun TempoPresets(low: Int?, high: Int?, onPick: (low: Int?, high: Int?) -> Unit) {
    val preset = TEMPO_PRESETS.firstOrNull { it.min == low && it.max == high }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TEMPO_PRESETS.forEach { p ->
            PresetTile(str(p.label), "${p.min}–${p.max}", selected = p == preset, Modifier.weight(1f)) { onPick(p.min, p.max) }
        }
        PresetTile(
            str(R.string.listen_preset_custom),
            tempoRangeShort(low, high),
            selected = preset == null && (low != null || high != null),
            Modifier.weight(1f),
            onClick = null,
        )
    }
}

/** A preset: 52 dp tall, name over range; the selected one filled violet-dark with a violet outline. */
@Composable
private fun PresetTile(name: String, range: String, selected: Boolean, modifier: Modifier, onClick: (() -> Unit)?) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier
            .height(52.dp)
            .clip(shape)
            .background(if (selected) Tone.selected else Tone.track)
            .border(1.dp, if (selected) MaterialTheme.colorScheme.primary else Color.Transparent, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // Four tiles across a 320 dp sheet with a large font or a long word ("Benutzerdefiniert"): the text shrinks
        // instead of running into the tile's edge.
        Text(
            name,
            lineHeight = 1.15.em,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            autoSize = TextAutoSize.StepBased(minFontSize = 9.sp, maxFontSize = 13.sp),
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Text(
            range,
            lineHeight = 1.15.em,
            color = if (selected) Tone.text2 else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            softWrap = false,
            autoSize = TextAutoSize.StepBased(minFontSize = 8.sp, maxFontSize = 11.sp),
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}
