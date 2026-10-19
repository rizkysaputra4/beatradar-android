package com.boimin.beatradar.capture

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

enum class MonitorStatus { STOPPED, LISTENING, PAUSED }

data class MonitorState(
    val status: MonitorStatus = MonitorStatus.STOPPED,
    /** Live tempo, or null while no beat is heard. */
    val bpm: Float? = null,
    /** False while [bpm] is still a quick first guess from the first seconds of a song. */
    val bpmSettled: Boolean = false,
    /** When the latest beat landed, on the [android.os.SystemClock.elapsedRealtime] clock. */
    val lastBeatAtMs: Long? = null,
    /** 0..1 towards a match verdict (tempo held inside the range for 10 s). */
    val matchProgress: Float = 0f,
    val matched: Boolean = false,
    /** The match is a song that was already matched before, so no alert was sent. */
    val alreadyListed: Boolean = false,
    val title: String? = null,
    val artist: String? = null,
    /** "Song #N" of the song playing, while it is logged without a name. */
    val placeholderNo: Int? = null,
    /** Log entry of the song playing, once it is logged. */
    val entryId: Long? = null,
    /** App playing on this phone (the mic is probably hearing its speaker), if any. */
    val phoneApp: String? = null,
) {
    val active: Boolean get() = status != MonitorStatus.STOPPED
}

/** Live state shared by [CaptureService] (writer) and the UI / notification (readers). */
object Monitor {
    private val mutable = MutableStateFlow(MonitorState())
    val state: StateFlow<MonitorState> = mutable

    internal fun update(transform: (MonitorState) -> MonitorState) = mutable.update(transform)
}
