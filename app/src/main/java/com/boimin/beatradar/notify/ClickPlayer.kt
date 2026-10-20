package com.boimin.beatradar.notify

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * "Practice click": a metronome at a song's tempo (or any BPM) for musicians and dancers. One bar of four clicks
 * (the first accented) is rendered once and looped by the audio hardware, so the timing never drifts.
 *
 * The microphone would hear the click and measure it instead of the music, so listening stops while it plays
 * (the caller stops capture; [CaptureService][com.boimin.beatradar.capture.CaptureService] stops the click when
 * listening starts).
 */
object ClickPlayer {
    const val MIN_BPM = 40
    const val MAX_BPM = 240
    private const val RATE = 44_100
    private const val BEATS_PER_BAR = 4
    private const val CLICK_MS = 25

    private var track: AudioTrack? = null
    private val _playingBpm = MutableStateFlow<Int?>(null)
    /** The tempo clicking now, or null when quiet. */
    val playingBpm: StateFlow<Int?> = _playingBpm

    @Synchronized
    fun play(bpm: Int) {
        stop()
        val bar = bar(bpm.coerceIn(MIN_BPM, MAX_BPM))
        val t = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            .setAudioFormat(AudioFormat.Builder().setSampleRate(RATE).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(bar.size * 2)
            .build()
        t.write(bar, 0, bar.size)
        t.setLoopPoints(0, bar.size, -1)
        t.play()
        track = t
        _playingBpm.value = bpm.coerceIn(MIN_BPM, MAX_BPM)
    }

    @Synchronized
    fun stop() {
        track?.let { runCatching { it.stop() }; it.release() }
        track = null
        _playingBpm.value = null
    }

    /** One bar of PCM: a short decaying tone on every beat, higher on the first. */
    internal fun bar(bpm: Int): ShortArray {
        val beat = (RATE * 60.0 / bpm).roundToInt()
        val out = ShortArray(beat * BEATS_PER_BAR)
        val clickLen = RATE * CLICK_MS / 1000
        for (b in 0 until BEATS_PER_BAR) {
            val freq = if (b == 0) 1_600.0 else 1_000.0
            val gain = if (b == 0) 0.9 else 0.6
            for (i in 0 until minOf(clickLen, beat)) {
                val env = exp(-i / (clickLen / 5.0))
                out[b * beat + i] = (sin(2 * PI * freq * i / RATE) * env * gain * Short.MAX_VALUE).toInt().toShort()
            }
        }
        return out
    }
}
