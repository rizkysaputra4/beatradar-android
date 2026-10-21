package com.boimin.beatradar.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.boimin.beatradar.R
import com.boimin.beatradar.i18n.str
import com.boimin.beatradar.ui.theme.Tone

/*
 * Skeletons: grey shapes the size of what is loading, with a soft light sweeping across, shown while a screen waits for
 * the server. They keep the layout where the content will land, so nothing jumps when it arrives. A spinner is still
 * right for a button that is working (signing in, a download).
 */

/** Where the light is, in px; one for all shapes in a [Skeleton], so they sweep together. */
private val LocalSweep = compositionLocalOf<State<Float>?> { null }

/** Wraps a loading layout: its [SkeletonBox]es share one sweep, and it reads as "Loading" to a screen reader. */
@Composable
internal fun Skeleton(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val width = with(LocalDensity.current) { LocalConfiguration.current.screenWidthDp.dp.toPx() }
    val sweep = rememberInfiniteTransition(label = "skeleton").animateFloat(
        initialValue = -width,
        targetValue = width * 2,
        animationSpec = infiniteRepeatable(tween(1300, easing = LinearEasing), RepeatMode.Restart),
        label = "sweep",
    )
    val loading = str(R.string.app_loading)
    Box(modifier.semantics(mergeDescendants = true) { contentDescription = loading }) {
        CompositionLocalProvider(LocalSweep provides sweep) { content() }
    }
}

/** One grey shape. Inside a [Skeleton] the light sweeps over it; on its own it is still. */
@Composable
internal fun SkeletonBox(modifier: Modifier = Modifier, corner: Dp = 6.dp) {
    val sweep = LocalSweep.current
    val base = Tone.track
    val light = Tone.pill
    val band = with(LocalDensity.current) { 220.dp.toPx() }
    Box(
        modifier
            .clip(RoundedCornerShape(corner))
            .background(base)
            .drawBehind {
                // In each shape's own space: all shapes sweep in step, which reads as one light passing over the list.
                val x = sweep?.value ?: return@drawBehind
                drawRect(
                    Brush.linearGradient(
                        colors = listOf(base, light, base),
                        start = Offset(x - band / 2, 0f),
                        end = Offset(x + band / 2, size.height),
                    ),
                )
            },
    )
}

/** A Discover song row while it loads: 46 dp art, title and artist lines, counters, the BPM badge. */
@Composable
internal fun SkeletonSongRow() {
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape)
            .padding(11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SkeletonBox(Modifier.size(46.dp), corner = 11.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SkeletonBox(Modifier.fillMaxWidth(0.62f).height(13.dp))
            SkeletonBox(Modifier.fillMaxWidth(0.4f).height(10.dp))
            SkeletonBox(Modifier.fillMaxWidth(0.3f).height(8.dp))
        }
        Spacer(Modifier.width(10.dp))
        SkeletonBox(Modifier.size(48.dp), corner = 12.dp)
    }
}

/** [count] loading song rows, spaced like the list they stand in for. */
@Composable
internal fun SkeletonSongList(count: Int, modifier: Modifier = Modifier) {
    Skeleton(modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { repeat(count) { SkeletonSongRow() } }
    }
}
