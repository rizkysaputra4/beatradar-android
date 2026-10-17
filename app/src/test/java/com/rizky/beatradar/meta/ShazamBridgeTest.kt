package com.rizky.beatradar.meta

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShazamBridgeTest {

    @Test fun shazamResultIsTitleAndArtist() {
        val r = ShazamBridge.parseShazam("Blinding Lights", "The Weeknd", 5)!!
        assertEquals("Blinding Lights", r.title)
        assertEquals("The Weeknd", r.artist)
        assertEquals("Shazam", r.source)
    }

    @Test fun shazamStatusNotificationsAreIgnored() {
        assertNull(ShazamBridge.parseShazam("Auto Shazam is on", "Tap to turn off", 0))
        assertNull(ShazamBridge.parseShazam("Listening for music", null, 0))
        assertNull(ShazamBridge.parseShazam("  ", "x", 0))
    }

    @Test fun nowPlayingSplitsOnLastBy() {
        val r = ShazamBridge.parseNowPlaying("Stand by Me by Ben E. King", null, 0)!!
        assertEquals("Stand by Me", r.title)
        assertEquals("Ben E. King", r.artist)
    }

    @Test fun nowPlayingWithoutByUsesText() {
        val r = ShazamBridge.parseNowPlaying("Levitating", "Dua Lipa", 0)!!
        assertEquals("Levitating", r.title)
        assertEquals("Dua Lipa", r.artist)
    }
}
