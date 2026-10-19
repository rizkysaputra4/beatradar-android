package com.boimin.beatradar.net

import com.boimin.beatradar.dsp.Landmark
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Base64

/** Wire format for fingerprints (PLAN §5a): base64 of little-endian (int32 hash, int32 frame) pairs. */
object Landmarks {
    /** The server takes at most this many landmarks in one recognize query (~12 s). */
    const val MAX_QUERY = 600
    /** And at most this many in one measurement's upload. */
    const val MAX_UPLOAD = 8_000

    fun pack(landmarks: List<Landmark>): String {
        val buf = ByteBuffer.allocate(landmarks.size * 8).order(ByteOrder.LITTLE_ENDIAN)
        for (l in landmarks) buf.putInt(l.hash).putInt(l.frame)
        return Base64.getEncoder().encodeToString(buf.array())
    }

    fun unpack(base64: String): List<Landmark> {
        val bytes = Base64.getDecoder().decode(base64)
        require(bytes.size % 8 == 0) { "landmark data must be a multiple of 8 bytes" }
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        return List(bytes.size / 8) { Landmark(buf.getInt(), buf.getInt()) }
    }
}
