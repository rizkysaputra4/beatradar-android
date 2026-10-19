package com.boimin.beatradar.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NamingTest {

    @Test fun anyNameBeatsNoName() {
        for (s in NameSource.entries) assertTrue(NameSource.shouldReplace(null, s))
    }

    @Test fun strongerSourcesReplaceWeakerOnly() {
        assertTrue(NameSource.shouldReplace(NameSource.MANUAL, NameSource.SHAZAM))
        assertTrue(NameSource.shouldReplace(NameSource.MANUAL, NameSource.SERVER))
        assertTrue(NameSource.shouldReplace(NameSource.SERVER, NameSource.MEDIA_SESSION))
        assertFalse(NameSource.shouldReplace(NameSource.MEDIA_SESSION, NameSource.MANUAL))
        assertFalse(NameSource.shouldReplace(NameSource.SHAZAM, NameSource.SERVER))
        assertFalse(NameSource.shouldReplace(NameSource.SERVER, NameSource.MANUAL))
    }

    @Test fun equalSourcesDoNotOverwrite() {
        // A Shazam result for another song must not rename a song the media session already named.
        assertFalse(NameSource.shouldReplace(NameSource.MEDIA_SESSION, NameSource.SHAZAM))
        assertFalse(NameSource.shouldReplace(NameSource.MANUAL, NameSource.MANUAL))
    }

    @Test fun recognitionAndLegacyLabels() {
        assertEquals(NameSource.SHAZAM, NameSource.fromRecognition("Shazam"))
        assertEquals(NameSource.NOW_PLAYING, NameSource.fromRecognition("Now Playing"))
        assertEquals(NameSource.SHAZAM, NameSource.fromLegacyLabel("Shazam"))
        assertEquals(NameSource.NOW_PLAYING, NameSource.fromLegacyLabel("Now Playing"))
        assertEquals(NameSource.MEDIA_SESSION, NameSource.fromLegacyLabel("Spotify"))
        assertEquals(NameSource.MEDIA_SESSION, NameSource.fromLegacyLabel(null))
    }

    @Test fun keyIgnoresCaseAccentsAndPunctuation() {
        assertEquals(SongKey.of("Despacito", "Luis Fonsi"), SongKey.of("DESPACITO!", "luis fonsi"))
        assertEquals("beyonce|halo", SongKey.of("Halo", "Beyoncé"))
        assertEquals("ac dc|thunderstruck", SongKey.of("Thunderstruck", "AC/DC"))
    }

    @Test fun keyDropsVideoNoiseAndFeaturing() {
        val plain = SongKey.of("Get Lucky", "Daft Punk")
        assertEquals(plain, SongKey.of("Get Lucky (Official Video)", "Daft Punk"))
        assertEquals(plain, SongKey.of("Get Lucky [Lyrics]", "Daft Punk"))
        assertEquals(plain, SongKey.of("Get Lucky (feat. Pharrell Williams)", "Daft Punk"))
        assertEquals(plain, SongKey.of("Get Lucky", "Daft Punk ft. Pharrell Williams"))
        assertEquals(plain, SongKey.of("Get Lucky - Remastered 2013", "Daft Punk"))
    }

    @Test fun keyKeepsRealVersions() {
        // A remix or live version is a different recording with its own tempo.
        assertNotEquals(SongKey.of("Get Lucky", "Daft Punk"), SongKey.of("Get Lucky (Radio Remix)", "Daft Punk"))
        assertNotEquals(SongKey.of("Shadow", "X"), SongKey.of("Shadow (Clean Bandit Remix)", "X"))
    }

    @Test fun keyWithoutArtist() {
        assertEquals("|untitled", SongKey.of("Untitled", null))
    }

    @Test fun labelShowsPlaceholderUntilNamed() {
        val unnamed = BeatLogEntry(timestamp = 0, title = null, artist = null, sourceApp = null, bpm = 120f, confidence = 0f, inRange = false, placeholderNo = 7)
        assertEquals("Song #7", unnamed.label)
        assertEquals("Halo", unnamed.copy(title = "Halo").label)
        assertEquals("Unknown song", unnamed.copy(placeholderNo = null).label)
    }
}
