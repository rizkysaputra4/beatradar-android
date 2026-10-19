package com.boimin.beatradar.sync

import com.boimin.beatradar.data.BeatLogEntry
import com.boimin.beatradar.data.NameSuggestion
import com.boimin.beatradar.data.PrintUpload
import com.boimin.beatradar.net.ApiJson
import com.boimin.beatradar.net.Suggestion
import com.boimin.beatradar.net.UnnamedIn
import com.boimin.beatradar.net.UnnamedItemResult

/** Unnamed fingerprints and name suggestions (PLAN §5c). Pure, so it is unit-tested. */
object UnnamedRules {
    const val MAX_ITEMS = 50
    /** A whole song's landmarks are ~85 KB in base64; the server takes 2 MB per request. */
    const val MAX_BATCH_BYTES = 1_500_000
    /** A song not heard for this long has finished playing, so its landmarks are complete. */
    const val QUIET_MS = 5 * 60_000L

    /** clientRef is the entry's local id (not `id:playCount` like measurements): one upload per unnamed entry. */
    fun upload(entry: BeatLogEntry, prints: String): UnnamedIn? {
        if (entry.title != null || entry.remoteInstallId != null || prints.isEmpty()) return null
        return UnnamedIn(entry.id.toString(), prints, entry.bpm.takeIf { it in 60f..200f })
    }

    fun batches(items: List<UnnamedIn>): List<List<UnnamedIn>> {
        val out = mutableListOf<List<UnnamedIn>>()
        var current = mutableListOf<UnnamedIn>()
        var bytes = 0
        for (item in items) {
            val size = ApiJson.encodeToString(UnnamedIn.serializer(), item).length + 1
            if (current.isNotEmpty() && (current.size >= MAX_ITEMS || bytes + size > MAX_BATCH_BYTES)) {
                out += current
                current = mutableListOf()
                bytes = 0
            }
            current += item
            bytes += size
        }
        if (current.isNotEmpty()) out += current
        return out
    }

    /** New [PrintUpload] state for an answer; null = leave pending and stop for now (the daily cap). */
    fun state(r: UnnamedItemResult): Int? = when (r.status) {
        "stored", "suggested", "duplicate" -> PrintUpload.SENT
        "rejected" -> if (r.reason == "daily_cap") null else PrintUpload.SKIPPED
        else -> null
    }

    fun toLocal(s: Suggestion) = NameSuggestion(s.slug, s.title, s.artist, s.score, s.state)

    /** What to do with `GET /v1/suggestions`, given which of its entries are still unnamed here. */
    data class Plan(val show: Map<Long, Suggestion>, val forget: List<Long>)

    /**
     * Suggestions for entries that are still unnamed are shown; those for entries that are gone or got a name
     * meanwhile are forgotten on the server. Unparseable client refs are ignored.
     */
    fun plan(list: List<Suggestion>, stillUnnamed: Set<Long>): Plan {
        val byId = list.mapNotNull { s -> s.clientRef.toLongOrNull()?.let { it to s } }.toMap()
        return Plan(byId.filterKeys { it in stillUnnamed }, byId.keys.filter { it !in stillUnnamed })
    }

    /** "Is this Halo – Beyoncé?" */
    fun question(s: NameSuggestion): String = "Is this ${s.title}${if (s.artist.isNotBlank()) " – ${s.artist}" else ""}?"
}
