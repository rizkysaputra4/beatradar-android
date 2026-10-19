package com.boimin.beatradar.data

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Short song clips (12 s, 22 kHz mono WAV, ~0.5 MB) kept so an unnamed song can be played to Shazam
 * later. Each clip is deleted as soon as its song gets a name. They stay on the phone unless the user turns on
 * *Help name unknown songs*, which sends them (re-encoded, [ClipEncoder]) when the server asks (PLAN §5d).
 */
object ClipStore {
    const val SAMPLE_RATE = 22_050
    const val SECONDS = 12

    private var player: MediaPlayer? = null

    fun save(context: Context, entryId: Long, pcm: ShortArray): String {
        val dir = File(context.filesDir, "clips").apply { mkdirs() }
        val file = File(dir, "$entryId.wav")
        FileOutputStream(file).use { out ->
            val data = ByteBuffer.allocate(pcm.size * 2).order(ByteOrder.LITTLE_ENDIAN)
            pcm.forEach { data.putShort(it) }
            out.write(wavHeader(pcm.size * 2))
            out.write(data.array())
        }
        return file.absolutePath
    }

    fun delete(path: String?) {
        if (path != null) File(path).delete()
    }

    /** The clips beyond the [limit] most recently heard songs, which get deleted. */
    fun overLimit(clips: List<ClipRef>, limit: Int): List<ClipRef> =
        clips.sortedWith(compareByDescending<ClipRef> { it.timestamp }.thenByDescending { it.id }).drop(limit.coerceAtLeast(0))

    /** Keeps clips only for the [limit] most recently heard songs; older entries stay in the log without one. */
    suspend fun trim(dao: BeatLogDao, limit: Int) {
        for (clip in overLimit(dao.clipRefs(), limit)) {
            delete(clip.clipPath)
            dao.setClip(clip.id, null)
        }
    }

    /**
     * Plays the clip through the speaker, by default twice so an app listening on the mic (Shazam) can hear it.
     * Playing again while a clip plays stops it first.
     */
    fun play(path: String, times: Int = 2) {
        stop()
        var plays = 0
        player = MediaPlayer().apply {
            setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            setDataSource(path)
            setOnCompletionListener { mp -> if (++plays < times) mp.start() else stop() }
            prepare()
            start()
        }
    }

    fun stop() {
        player?.release()
        player = null
    }

    private fun wavHeader(dataBytes: Int): ByteArray = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
        put("RIFF".toByteArray()); putInt(36 + dataBytes); put("WAVE".toByteArray())
        put("fmt ".toByteArray()); putInt(16); putShort(1); putShort(1)
        putInt(SAMPLE_RATE); putInt(SAMPLE_RATE * 2); putShort(2); putShort(16)
        put("data".toByteArray()); putInt(dataBytes)
    }.array()
}
