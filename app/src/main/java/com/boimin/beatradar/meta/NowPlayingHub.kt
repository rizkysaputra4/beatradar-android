package com.boimin.beatradar.meta

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow

/** What a music app on this phone says it is playing (from its media session). */
data class TrackInfo(
    val title: String?,
    val artist: String?,
    val packageName: String,
    val playing: Boolean,
)

/** A song named by Shazam or Pixel "Now Playing" (from their notifications). */
data class Recognition(
    val title: String,
    val artist: String?,
    val source: String,
    val timeMs: Long,
)

/** In-process bridge between [NowPlayingListener] and the capture service / UI. */
object NowPlayingHub {
    val track = MutableStateFlow<TrackInfo?>(null)
    val recognitions = MutableSharedFlow<Recognition>(extraBufferCapacity = 8)
}
