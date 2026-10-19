package com.boimin.beatradar.data

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.SystemClock
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Re-encodes a saved clip ([ClipStore]'s 16-bit mono WAV) to AAC-LC in an MP4 (.m4a) at ~32 kbps before it is shared
 * (PLAN §5d): ~50 KB instead of ~0.5 MB, and playable in any browser.
 */
object ClipEncoder {
    const val BIT_RATE = 32_000
    private const val TIMEOUT_US = 10_000L
    /** Encoding 12 s takes well under a second; give up on a stuck codec. */
    private const val DEADLINE_MS = 15_000L

    class Encoded(val file: File, val durationMs: Int)

    fun encode(wav: File, out: File): Encoded {
        val (sampleRate, pcm) = readWav(wav)
        val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, 1).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16 * 1024)
        }
        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        out.delete()
        val muxer = MediaMuxer(out.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var track = -1
        var muxing = false
        try {
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()
            val info = MediaCodec.BufferInfo()
            var offset = 0
            var inputDone = false
            val deadline = SystemClock.elapsedRealtime() + DEADLINE_MS
            while (true) {
                if (SystemClock.elapsedRealtime() > deadline) throw IOException("AAC encoder timed out")
                if (!inputDone) {
                    val i = codec.dequeueInputBuffer(TIMEOUT_US)
                    if (i >= 0) {
                        val buf = codec.getInputBuffer(i) ?: throw IOException("no input buffer")
                        buf.clear()
                        // Whole 16-bit samples only.
                        val n = minOf(buf.remaining(), pcm.size - offset) and 1.inv()
                        val ptsUs = (offset / 2).toLong() * 1_000_000L / sampleRate
                        if (n <= 0) {
                            codec.queueInputBuffer(i, 0, 0, ptsUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            buf.put(pcm, offset, n)
                            codec.queueInputBuffer(i, 0, n, ptsUs, 0)
                            offset += n
                        }
                    }
                }
                val o = codec.dequeueOutputBuffer(info, TIMEOUT_US)
                if (o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    track = muxer.addTrack(codec.outputFormat)
                    muxer.start()
                    muxing = true
                } else if (o >= 0) {
                    val buf = codec.getOutputBuffer(o) ?: throw IOException("no output buffer")
                    // The codec config travels in the track format, not as a sample.
                    if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) info.size = 0
                    if (info.size > 0 && muxing) {
                        buf.position(info.offset)
                        buf.limit(info.offset + info.size)
                        muxer.writeSampleData(track, buf, info)
                    }
                    codec.releaseOutputBuffer(o, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                }
            }
        } finally {
            runCatching { codec.stop() }
            codec.release()
            if (muxing) runCatching { muxer.stop() }
            muxer.release()
        }
        if (!muxing) throw IOException("AAC encoder produced no audio")
        return Encoded(out, (pcm.size / 2 * 1000L / sampleRate).toInt())
    }

    /** Sample rate and little-endian 16-bit PCM of a mono WAV, read from its `fmt ` and `data` chunks. */
    internal fun readWav(file: File): Pair<Int, ByteArray> {
        val bytes = file.readBytes()
        val b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        if (bytes.size < 12 || String(bytes, 0, 4) != "RIFF" || String(bytes, 8, 4) != "WAVE") throw IOException("not a WAV file")
        var pos = 12
        var rate = ClipStore.SAMPLE_RATE
        while (pos + 8 <= bytes.size) {
            val id = String(bytes, pos, 4)
            val size = b.getInt(pos + 4)
            val start = pos + 8
            when (id) {
                "fmt " -> rate = b.getInt(start + 4)
                "data" -> return rate to bytes.copyOfRange(start, minOf(bytes.size, start + size))
            }
            pos = start + size + (size and 1)
        }
        throw IOException("WAV file has no data")
    }
}
