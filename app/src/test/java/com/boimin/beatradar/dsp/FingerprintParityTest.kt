package com.boimin.beatradar.dsp

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * The server fingerprints whole studio tracks with a port of [Fingerprinter] (PLAN §5e). Both must make the same
 * landmarks from the same audio: `fingerprint_parity.json` holds this implementation's output for
 * `fingerprint_parity.pcm`, and the server's test (`prints/fingerprinter.rs`) checks against the same file.
 *
 * WRITE_PARITY=1 rewrites the JSON (into build/, to copy into both test folders) after a deliberate change.
 */
class FingerprintParityTest {

    private fun pcm(): FloatArray {
        val bytes = javaClass.classLoader!!.getResource("fingerprint_parity.pcm")!!.readBytes()
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        return FloatArray(bytes.size / 2) { buf.getShort() / 32768f }
    }

    private fun landmarks(): List<Landmark> {
        val x = pcm()
        val f = Fingerprinter(11_025)
        val out = mutableListOf<Landmark>()
        var i = 0
        while (i < x.size) {
            val n = minOf(551, x.size - i)
            f.process(x.copyOfRange(i, i + n), n) { out += it }
            i += n
        }
        return out
    }

    @Test fun sameLandmarksAsTheServerFixture() {
        val made = landmarks()
        if (System.getenv("WRITE_PARITY") == "1") {
            val json = made.joinToString(",", "{\"landmarks\":[", "]}\n") { "[${it.hash},${it.frame}]" }
            File("build/fingerprint_parity.json").writeText(json)
            return
        }
        val text = javaClass.classLoader!!.getResource("fingerprint_parity.json")!!.readText()
        val expected = Regex("""\[(-?\d+),(-?\d+)]""").findAll(text).map { Landmark(it.groupValues[1].toInt(), it.groupValues[2].toInt()) }.toSet()
        val got = made.toSet()
        val overlap = got.intersect(expected).size.toDouble() / maxOf(got.size, expected.size)
        assertTrue("fixture too small: ${expected.size}", expected.size > 200)
        assertTrue("overlap $overlap", overlap >= 0.99)
    }
}
