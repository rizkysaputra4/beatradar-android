package com.boimin.beatradar.sync

import com.boimin.beatradar.data.BeatLogEntry
import com.boimin.beatradar.data.Settings

/** Sharing clips of unknown songs (PLAN §5d). Pure, so it is unit-tested. */
object ClipRules {
    /** The server takes clips up to 200 KB; a 12 s clip at 32 kbps is ~50 KB. */
    const val MAX_BYTES = 200 * 1024

    /** Clips go up only with the switch on, and with what it builds on: clips kept, unnamed fingerprints sent. */
    fun sharing(s: Settings): Boolean = s.shareClips && s.saveClips && s.contribute

    enum class ConsentStep { SEND, WITHDRAW, NONE }

    /** Tell the server once when sharing starts, and withdraw (deleting the clips there) once when it stops. */
    fun consentStep(sharing: Boolean, consentSent: Boolean): ConsentStep = when {
        sharing && !consentSent -> ConsentStep.SEND
        !sharing && consentSent -> ConsentStep.WITHDRAW
        else -> ConsentStep.NONE
    }

    /**
     * The clips to send for the server's wanted list (client refs are local entry ids): entries still unnamed, on this
     * phone, that still have their clip file. In the server's order.
     */
    fun toUpload(wanted: List<String>, entries: List<BeatLogEntry>, fileExists: (String) -> Boolean): List<Pair<Long, String>> {
        val byId = entries.associateBy { it.id }
        return wanted.mapNotNull { ref ->
            val e = ref.toLongOrNull()?.let(byId::get) ?: return@mapNotNull null
            val path = e.clipPath?.takeIf { e.title == null && e.remoteInstallId == null && fileExists(it) } ?: return@mapNotNull null
            e.id to path
        }.distinctBy { it.first }
    }
}
