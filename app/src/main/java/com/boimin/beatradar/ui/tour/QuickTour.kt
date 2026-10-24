package com.boimin.beatradar.ui.tour

import androidx.activity.compose.BackHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.size
import androidx.compose.animation.togetherWith
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toSize
import com.boimin.beatradar.R
import com.boimin.beatradar.i18n.str
import com.boimin.beatradar.ui.theme.Tone
import kotlin.math.min

/** Where the tour's targets are on screen, in root coordinates. Screens report theirs with [tourTarget]. */
class TourTargets {
    internal val bounds = mutableStateMapOf<TourTarget, Rect>()
}

/** Provided by AppRoot; null (e.g. in a preview) makes [tourTarget] a no-op. */
val LocalTourTargets = staticCompositionLocalOf<TourTargets?> { null }

/** Marks this element as [target], so a tour step can spotlight it. Gone from the screen, it's forgotten. */
fun Modifier.tourTarget(target: TourTarget): Modifier = composed {
    val targets = LocalTourTargets.current ?: return@composed Modifier
    DisposableEffect(targets, target) { onDispose { targets.bounds.remove(target) } }
    Modifier.onGloballyPositioned { coordinates ->
        val r = coordinates.boundsInRoot()
        if (targets.bounds[target] != r) targets.bounds[target] = r
    }
}

/**
 * The full quick tour (Settings → "Take the quick tour"): the screen dimmed except for the step's target, and a card
 * with what it's for. Next walks the steps; a step before a new tab points at that tab and moves there only when its
 * button or the tab is tapped. Skip (on every step but the last) or Back on the first step closes it where it is
 * ([onClose] `finished` false); the last step's button finishes it. Other taps are held, so nothing changes under it.
 */
@Composable
fun QuickTourOverlay(index: Int, targets: TourTargets, onStep: (Int) -> Unit, onClose: (finished: Boolean) -> Unit) {
    val step = QuickTourRules.step(index)
    val last = QuickTourRules.isLast(index)
    BackHandler { if (index > 0) onStep(index - 1) else onClose(false) }
    val next = { if (last) onClose(true) else onStep(index + 1) }
    // On a step that points at the next tab, tapping that tab goes on, as tapping it would without the tour.
    CoachMark(step, targets, onTapTarget = next.takeIf { step.goTo != null }) {
        TourCard(
            step = step,
            index = index,
            last = last,
            onBack = { onStep(index - 1) },
            onNext = next,
            onSkip = { onClose(false) },
        )
    }
}

/**
 * A tab's tip, the first time the tab opens: its tour steps (one, or a few in a row), with Next and then Got it. Back,
 * or a tap anywhere outside the card, closes it: it's a hint, not a gate.
 */
@Composable
fun TabTip(steps: List<TourStep>, targets: TourTargets, onDismiss: () -> Unit) {
    var index by remember(steps) { mutableIntStateOf(0) }
    val step = steps[index.coerceIn(0, steps.lastIndex)]
    val last = index >= steps.lastIndex
    val next: () -> Unit = { if (last) onDismiss() else index++ }
    BackHandler(onBack = onDismiss)
    CoachMark(step, targets, onTapTarget = next, onTapOutside = onDismiss) {
        TipCard(step, position = if (steps.size > 1) index + 1 to steps.size else null, last = last, onNext = next)
    }
}

/**
 * The dimmed screen with [step]'s target cut out and ringed, and [card] next to it (below when it fits, else above;
 * centred with no target). Touches never reach the app: one on the target calls [onTapTarget], elsewhere
 * [onTapOutside].
 */
@Composable
private fun CoachMark(
    step: TourStep,
    targets: TourTargets,
    onTapTarget: (() -> Unit)? = null,
    onTapOutside: (() -> Unit)? = null,
    card: @Composable () -> Unit,
) {
    var area by remember { mutableStateOf(Rect.Zero) }
    val density = LocalDensity.current
    val pad = with(density) { 8.dp.toPx() }
    // The step's own target, else its tab's button (its target isn't on screen: a set is open, the list scrolled).
    // Kept inside the screen, so the ring never runs off an edge.
    val hole: Rect? = step.target?.let { t ->
        val r = targets.bounds[t]?.takeUnless { it.isEmpty } ?: targets.bounds[QuickTourRules.fallback(step.tab)]?.takeUnless { it.isEmpty }
        r?.inflate(pad)?.intersect(area.deflate(with(density) { 4.dp.toPx() }))?.translate(-area.topLeft)?.takeUnless { it.isEmpty }
    }
    // The spotlight glides from one target to the next; it appears in place the first time.
    val spot = remember { Animatable(Rect.Zero, Rect.VectorConverter) }
    LaunchedEffect(hole) {
        if (hole == null) return@LaunchedEffect
        if (spot.value == Rect.Zero) spot.snapTo(hole) else spot.animateTo(hole, spring(stiffness = Spring.StiffnessMediumLow))
    }
    val spotAlpha by animateFloatAsState(if (hole != null) 1f else 0f, label = "tourSpot")
    val scrim = Color.Black.copy(alpha = 0.72f)
    val ring = MaterialTheme.colorScheme.primary
    val currentHole by rememberUpdatedState(hole)
    val tapTarget by rememberUpdatedState(onTapTarget)
    val tapOutside by rememberUpdatedState(onTapOutside)

    Box(Modifier.fillMaxSize().onGloballyPositioned { area = Rect(it.positionInRoot(), it.size.toSize()) }) {
        Canvas(
            Modifier
                .fillMaxSize()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                // Holds every touch: the screen under the tour stays as it is.
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            awaitPointerEvent().changes.forEach { change ->
                                if (change.changedToUp()) {
                                    if (currentHole?.contains(change.position) == true) tapTarget?.invoke() else tapOutside?.invoke()
                                }
                                change.consume()
                            }
                        }
                    }
                },
        ) {
            drawRect(scrim)
            val r = spot.value
            if (spotAlpha > 0f && r != Rect.Zero) {
                val corner = CornerRadius(min(r.height / 2, 24.dp.toPx()))
                drawRoundRect(Color.Black, r.topLeft, r.size, corner, alpha = spotAlpha, blendMode = BlendMode.DstOut)
                drawRoundRect(ring, r.topLeft, r.size, corner, alpha = spotAlpha, style = Stroke(2.dp.toPx()))
            }
        }
        val insets = WindowInsets.safeDrawing
        Layout(content = card, modifier = Modifier.fillMaxSize()) { measurables, constraints ->
            val margin = 16.dp.roundToPx()
            val gap = 12.dp.roundToPx()
            val width = min(constraints.maxWidth - 2 * margin - insets.getLeft(this, layoutDirection) - insets.getRight(this, layoutDirection), 420.dp.roundToPx())
                .coerceAtLeast(0)
            val placeable = measurables.first().measure(Constraints(minWidth = width, maxWidth = width, maxHeight = constraints.maxHeight))
            val top = insets.getTop(this) + margin
            val bottom = constraints.maxHeight - insets.getBottom(this) - margin
            // Below the target when it fits (and there's more room there), else above, else wherever there's more room.
            val y = if (hole == null) {
                (constraints.maxHeight - placeable.height) / 2
            } else {
                val below = bottom - (hole.bottom.toInt() + gap)
                val above = (hole.top.toInt() - gap) - top
                when {
                    placeable.height <= below && (below >= above || placeable.height > above) -> hole.bottom.toInt() + gap
                    placeable.height <= above -> hole.top.toInt() - gap - placeable.height
                    below >= above -> hole.bottom.toInt() + gap
                    else -> hole.top.toInt() - gap - placeable.height
                }
            }.coerceIn(top, (bottom - placeable.height).coerceAtLeast(top))
            layout(constraints.maxWidth, constraints.maxHeight) {
                placeable.place((constraints.maxWidth - placeable.width) / 2, y)
            }
        }
    }
}

/**
 * The step's card: the tab it's on and how far along, Skip, then the title over a line or two of why (on a step before a
 * new tab: what's there and how to go), then Back and Next (or "Open <tab>").
 */
@Composable
private fun TourCard(step: TourStep, index: Int, last: Boolean, onBack: () -> Unit, onNext: () -> Unit, onSkip: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Tone.sheet)
            .border(1.dp, Tone.sheetLine, shape)
            .padding(start = 18.dp, end = 10.dp, top = 6.dp, bottom = 10.dp)
            // TalkBack reads each new step as it comes.
            .semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
            TabPill(step.tab)
            Spacer(Modifier.width(8.dp))
            Text(
                str(R.string.app_consent_step, index + 1, QuickTourRules.STEPS.size),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = Tone.text3,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            if (!last) {
                TextButton(onClick = onSkip) { Text(str(R.string.app_tour_skip), color = Tone.text2, fontWeight = FontWeight.Medium) }
            }
        }
        // The text cross-fades from step to step, so a new step reads as new even where the card doesn't move.
        AnimatedContent(targetState = step, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "tourText") { s ->
            Column(Modifier.fillMaxWidth().padding(top = 4.dp, end = 8.dp)) {
                val goTo = s.goTo
                Text(
                    if (goTo != null) str(s.title, str(goTo.label)) else str(s.title),
                    fontSize = 18.sp,
                    lineHeight = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.semantics { heading() },
                )
                Spacer(Modifier.height(6.dp))
                Text(str(s.body), fontSize = 14.sp, lineHeight = 20.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (goTo != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(str(R.string.app_tour_go_hint), fontSize = 13.sp, lineHeight = 18.sp, color = Tone.primarySoft)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.End) {
            if (index > 0) {
                TextButton(onClick = onBack) { Text(str(R.string.app_setup_back), color = Tone.text2, fontWeight = FontWeight.Medium) }
                Spacer(Modifier.width(4.dp))
            }
            val goTo = step.goTo
            Box(
                Modifier
                    .heightIn(min = 44.dp)
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.primary)
                    .clickable(role = Role.Button, onClick = onNext)
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    when {
                        last -> str(R.string.app_tour_finish, str(TourTab.LISTEN.label))
                        goTo != null -> str(R.string.app_tour_open, str(goTo.label))
                        else -> str(R.string.app_tour_next)
                    },
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    maxLines = 1,
                )
            }
        }
    }
}

/** A tab's tip: the tab's name (and n of m when it has a few), what it's for, and Next or Got it. */
@Composable
private fun TipCard(step: TourStep, position: Pair<Int, Int>?, last: Boolean, onNext: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Tone.sheet)
            .border(1.dp, Tone.sheetLine, shape)
            // Taps on the card itself don't count as "outside".
            .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } }
            .padding(start = 18.dp, end = 10.dp, top = 14.dp, bottom = 10.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TabPill(step.tab)
            if (position != null) {
                Spacer(Modifier.width(8.dp))
                Text(str(R.string.app_consent_step, position.first, position.second), fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Tone.text3)
            }
        }
        AnimatedContent(targetState = step, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "tipText") { s ->
            Column(Modifier.fillMaxWidth().padding(top = 10.dp, end = 8.dp)) {
                Text(
                    str(s.title),
                    fontSize = 18.sp,
                    lineHeight = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.semantics { heading() },
                )
                Spacer(Modifier.height(6.dp))
                Text(str(s.body), fontSize = 14.sp, lineHeight = 20.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Box(
                Modifier
                    .heightIn(min = 44.dp)
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.primary)
                    .clickable(role = Role.Button, onClick = onNext)
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(str(if (last) R.string.app_tour_got_it else R.string.app_tour_next), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White, maxLines = 1)
            }
        }
    }
}

/** Where the step is: the tab's icon and name, as in the tab bar. */
@Composable
private fun TabPill(tab: TourTab) {
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(Tone.chip)
            .border(1.dp, Tone.chipLine, RoundedCornerShape(50))
            .padding(start = 8.dp, end = 10.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(tab.icon), contentDescription = null, tint = Tone.primarySoft, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(5.dp))
        Text(str(tab.label), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Tone.text2, maxLines = 1)
    }
}
