package com.boimin.beatradar.net

import com.boimin.beatradar.dsp.Landmark
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Base64

class LandmarksTest {

    @Test fun packsLittleEndianHashThenFrame() {
        val bytes = Base64.getDecoder().decode(Landmarks.pack(listOf(Landmark(0x01020304, 5))))
        assertArrayEquals(byteArrayOf(4, 3, 2, 1, 5, 0, 0, 0), bytes)
    }

    @Test fun negativeHashesSurvive() {
        val l = listOf(Landmark(-123456789, 70_000), Landmark(Int.MIN_VALUE, 0), Landmark(Int.MAX_VALUE, 1))
        assertEquals(l, Landmarks.unpack(Landmarks.pack(l)))
    }

    @Test fun roundTripsAWholeSong() {
        val l = List(3_600) { Landmark(it * 7919, it / 3) }
        val packed = Landmarks.pack(l)
        assertEquals(l, Landmarks.unpack(packed))
        // 8 bytes each, standard base64 with padding (what the server decodes).
        assertEquals(((3_600 * 8 + 2) / 3) * 4, packed.length)
    }

    @Test fun emptyIsEmpty() {
        assertEquals("", Landmarks.pack(emptyList()))
        assertEquals(emptyList<Landmark>(), Landmarks.unpack(""))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsPartialPairs() {
        Landmarks.unpack(Base64.getEncoder().encodeToString(ByteArray(12)))
    }
}
