package com.rizky.beatradar.data

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Short song clips (12 s, 22 kHz mono WAV, ~0.5 MB) kept so an unnamed song can be played to Shazam
 * later. Each clip is deleted as soon as its song gets a name.
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

    /** Plays the clip twice through the speaker, so an app listening on the mic (Shazam) can hear it. */
    fun play(path: String) {
        stop()
        var plays = 0
        player = MediaPlayer().apply {
            setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            setDataSource(path)
            setOnCompletionListener { mp -> if (++plays < 2) mp.start() else stop() }
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
