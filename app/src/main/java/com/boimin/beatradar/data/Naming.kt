package com.boimin.beatradar.data

import java.text.Normalizer
import java.util.Locale

/** Where a log entry's name came from. Stronger sources replace weaker ones, never the reverse (PLAN §5b). */
enum class NameSource(val rank: Int) {
    /** The media session of a music app playing on this phone. */
    MEDIA_SESSION(3),
    SHAZAM(3),
    NOW_PLAYING(3),
    /** Recognised by the server's shared fingerprints (later milestone). */
    SERVER(2),
    /** Typed by the user. */
    MANUAL(1);

    companion object {
        /** True when a name from [new] may replace a name from [old] automatically. A hand edit always applies. */
        fun shouldReplace(old: NameSource?, new: NameSource): Boolean = old == null || new.rank > old.rank

        /** Maps a [com.boimin.beatradar.meta.Recognition.source] label. */
        fun fromRecognition(source: String): NameSource =
            if (source.equals("Now Playing", ignoreCase = true)) NOW_PLAYING else SHAZAM

        /** Best guess for entries named before the source was stored, from their source label. */
        fun fromLegacyLabel(label: String?): NameSource = when {
            label.equals("Shazam", ignoreCase = true) -> SHAZAM
            label.equals("Now Playing", ignoreCase = true) -> NOW_PLAYING
            else -> MEDIA_SESSION
        }
    }
}

/**
 * Normalised "artist|title", so the same song written differently by different apps is one entry:
 * lowercase, no accents or punctuation, without "(Official Video)", "[Lyrics]", "- Remastered 2011" or "feat. …".
 */
object SongKey {
    private val NOISE = Regex(
        """\b(official|video|audio|lyrics?|visuali[sz]er|remaster(ed)?|hd|hq|4k|mv|m/v|explicit|radio edit|color coded)\b""",
        RegexOption.IGNORE_CASE,
    )
    private val BRACKETS = Regex("""[(\[]([^)\]]*)[)\]]""")
    private val DASH_SUFFIX = Regex("""\s+-\s+(.*)$""")
    private val FEAT = Regex("""\s*\b(feat\.?|ft\.?|featuring)\s.*$""", RegexOption.IGNORE_CASE)
    private val MARKS = Regex("""\p{M}+""")
    private val NOT_WORD = Regex("""[^\p{L}\p{N}]+""")

    fun of(title: String, artist: String?): String = "${clean(artist.orEmpty(), isTitle = false)}|${clean(title, isTitle = true)}"

    internal fun clean(raw: String, isTitle: Boolean): String {
        var s = raw
        if (isTitle) {
            s = BRACKETS.replace(s) { m ->
                val inner = m.groupValues[1]
                if (NOISE.containsMatchIn(inner) || FEAT.containsMatchIn(" $inner")) " " else m.value
            }
            DASH_SUFFIX.find(s)?.let { m -> if (NOISE.containsMatchIn(m.groupValues[1])) s = s.substring(0, m.range.first) }
        }
        s = FEAT.replace(s, "")
        s = Normalizer.normalize(s, Normalizer.Form.NFD).replace(MARKS, "")
        return s.lowercase(Locale.ROOT).replace(NOT_WORD, " ").trim().replace(Regex("\\s+"), " ")
    }
}
