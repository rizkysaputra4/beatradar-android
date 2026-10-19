package com.boimin.beatradar.capture

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/** One hit the mic heard, on the [android.os.SystemClock.elapsedRealtime] clock; [strength] is 0..1. */
data class MicHit(val atMs: Long, val strength: Float)

/** Hits as they are heard, for the ripples. Written by [CaptureService], read by the UI while it is on screen. */
object MicHits {
    private val mutable = MutableSharedFlow<MicHit>(extraBufferCapacity = 16, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val hits: SharedFlow<MicHit> = mutable

    internal fun emit(hit: MicHit) = mutable.tryEmit(hit)
}
