package com.boimin.beatradar.library

import android.content.Context
import com.boimin.beatradar.R
import com.boimin.beatradar.data.SetSong
import com.boimin.beatradar.i18n.str
import com.boimin.beatradar.net.SongLink
import java.net.URLEncoder
import kotlin.math.roundToInt

/**
 * BeatRadar plays nothing, so a Set ends in a music app: each song opens in Spotify or YouTube Music (the track itself
 * when enrichment found it, else a search there), or the whole list goes out as plain text.
 */
object PlayLinks {
    enum class Service(val label: String) {
        SPOTIFY("Spotify"),
        YOUTUBE_MUSIC("YouTube Music"),
    }

    /** The song in [service]: its exact track page from [links] when known, else a search for it. */
    fun url(service: Service, links: List<SongLink>, title: String, artist: String): String {
        val query = "$artist $title"
        return when (service) {
            Service.SPOTIFY -> links.firstOrNull { it.service == "spotify" && it.exact }?.url
                ?: "https://open.spotify.com/search/${encode(query).replace("+", "%20")}"
            Service.YOUTUBE_MUSIC -> links.firstOrNull { it.service == "youtube" && it.exact }?.url?.let(::youtubeMusic)
                ?: "https://music.youtube.com/search?q=${encode(query)}"
        }
    }

    /** A YouTube video link as the same video in YouTube Music, or null when it isn't one. */
    fun youtubeMusic(url: String): String? {
        val id = Regex("""(?:[?&]v=|youtu\.be/|/shorts/)([A-Za-z0-9_-]{6,})""").find(url)?.groupValues?.get(1) ?: return null
        return "https://music.youtube.com/watch?v=$id"
    }

    /** True when shared text holds a Spotify or YouTube playlist link (the server checks it properly). */
    fun looksLikePlaylist(text: String): Boolean =
        text.contains("spotify.com/playlist/") || text.contains("spotify.com/intl-") && text.contains("/playlist/") ||
            text.contains("spotify:playlist:") || (text.contains("youtube.com/") || text.contains("youtu.be/")) && text.contains("list=")

    // The charset-name overload: encode(String, Charset) is API 33+ and would crash on Android 10–12.
    private fun encode(s: String): String = URLEncoder.encode(s, "UTF-8")
}
