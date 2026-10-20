package com.boimin.beatradar.library

import com.boimin.beatradar.data.SetSong
import com.boimin.beatradar.library.PlayLinks.Service
import com.boimin.beatradar.net.SongLink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayLinksTest {

    private val exact = listOf(
        SongLink("spotify", "https://open.spotify.com/track/69kOkLUCkxIZYexIgSG8rq", true),
        SongLink("youtube", "https://www.youtube.com/watch?v=5NV6Rdv1a3I", true),
    )
    private val searches = listOf(
        SongLink("spotify", "https://open.spotify.com/search/x", false),
        SongLink("youtube", "https://www.youtube.com/results?search_query=x", false),
    )

    @Test fun exactTracksOpenDirectly() {
        assertEquals("https://open.spotify.com/track/69kOkLUCkxIZYexIgSG8rq", PlayLinks.url(Service.SPOTIFY, exact, "Get Lucky", "Daft Punk"))
        assertEquals("https://music.youtube.com/watch?v=5NV6Rdv1a3I", PlayLinks.url(Service.YOUTUBE_MUSIC, exact, "Get Lucky", "Daft Punk"))
    }

    @Test fun otherwiseASearchInThatApp() {
        assertEquals("https://open.spotify.com/search/Daft%20Punk%20Get%20Lucky", PlayLinks.url(Service.SPOTIFY, searches, "Get Lucky", "Daft Punk"))
        assertEquals("https://music.youtube.com/search?q=Beyonc%C3%A9+Halo", PlayLinks.url(Service.YOUTUBE_MUSIC, emptyList(), "Halo", "Beyoncé"))
    }

    @Test fun youtubeLinksBecomeYoutubeMusic() {
        assertEquals("https://music.youtube.com/watch?v=5NV6Rdv1a3I", PlayLinks.youtubeMusic("https://youtu.be/5NV6Rdv1a3I?t=3"))
        assertNull(PlayLinks.youtubeMusic("https://www.youtube.com/results?search_query=x"))
    }

    @Test fun setAsText() {
        val songs = listOf(
            SetSong(1, "a", "Levels", "Avicii", 126f, null, 0),
            SetSong(1, "b", "Strobe", "deadmau5", 128.4f, null, 0),
        )
        assertEquals("Warm-up (BeatRadar set)\n1. Levels – Avicii · 126 BPM\n2. Strobe – deadmau5 · 128 BPM", PlayLinks.setText("Warm-up", songs))
    }

    @Test fun recognisesPlaylistShares() {
        assertTrue(PlayLinks.looksLikePlaylist("Run 170 https://open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M?si=1"))
        assertTrue(PlayLinks.looksLikePlaylist("https://music.youtube.com/playlist?list=PLx0sYbCqOb8TBPRdmBHs5Iftvv9TPboYG"))
        assertFalse(PlayLinks.looksLikePlaylist("https://open.spotify.com/track/69kOkLUCkxIZYexIgSG8rq"))
        assertFalse(PlayLinks.looksLikePlaylist("https://www.youtube.com/watch?v=5NV6Rdv1a3I"))
    }
}
