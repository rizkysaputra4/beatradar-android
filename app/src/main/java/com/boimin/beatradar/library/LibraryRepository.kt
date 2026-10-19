package com.boimin.beatradar.library

import com.boimin.beatradar.account.AccountRepository
import com.boimin.beatradar.dsp.Landmark
import com.boimin.beatradar.net.BoardView
import com.boimin.beatradar.net.Landmarks
import com.boimin.beatradar.net.LoveState
import com.boimin.beatradar.net.NicknameReport
import com.boimin.beatradar.net.PublicProfile
import com.boimin.beatradar.net.RecognizeIn
import com.boimin.beatradar.net.RecognizeOut
import com.boimin.beatradar.net.ReportIn
import com.boimin.beatradar.net.Rewards
import com.boimin.beatradar.net.SongDetail
import com.boimin.beatradar.net.SongPage

/** How the Library list is ordered (`sort` on `GET /v1/songs`). */
enum class SongSort(val api: String, val label: String) {
    POPULAR("popular", "Popular"),
    CONFIRMED("confirmed", "Confirmed"),
    LOVED("loved", "Most loved"),
    RECENT("recent", "New"),
    BPM("bpm", "BPM"),
}

data class SongQuery(
    val text: String = "",
    val bpmMin: Int? = null,
    val bpmMax: Int? = null,
    val halfDouble: Boolean = false,
    val sort: SongSort = SongSort.POPULAR,
    val lovedOnly: Boolean = false,
) {
    fun params(cursor: String?): Map<String, Any?> = mapOf(
        "q" to text.trim().takeIf { it.isNotEmpty() },
        "bpmMin" to bpmMin,
        "bpmMax" to bpmMax,
        "halfDouble" to halfDouble.takeIf { it },
        "sort" to sort.api,
        "loved" to lovedOnly.takeIf { it },
        "cursor" to cursor,
    )
}

/** The shared Library and rewards on the server (PLAN §7, §7b). Every call throws ApiException on failure. */
class LibraryRepository(private val account: AccountRepository) {
    private val api get() = account.api

    suspend fun songs(query: SongQuery, cursor: String? = null): SongPage = api.get("v1/songs", query.params(cursor))

    /** Old slugs of merged songs answer with the surviving song; check [SongDetail.slug]. */
    suspend fun song(slug: String): SongDetail = api.get("v1/songs/$slug")

    suspend fun setLoved(slug: String, loved: Boolean): LoveState =
        if (loved) api.put("v1/songs/$slug/love") else api.delete("v1/songs/$slug/love")

    suspend fun myLoves(cursor: String? = null): SongPage = api.get("v1/me/loves", mapOf("cursor" to cursor))

    suspend fun report(slug: String, reason: String, note: String?) {
        api.post<ReportIn, Unit>("v1/songs/$slug/reports", ReportIn(reason, note?.takeIf { it.isNotBlank() }))
    }

    /** "What is this?" from the last ~12 s of landmarks. Throws on timeout/offline; the caller falls back. */
    suspend fun recognize(landmarks: List<Landmark>): RecognizeOut =
        api.post("v1/recognize", RecognizeIn(Landmarks.pack(landmarks.takeLast(Landmarks.MAX_QUERY))), timeoutMs = RECOGNIZE_TIMEOUT_MS)

    suspend fun rewards(): Rewards = api.get("v1/me/rewards")

    suspend fun leaderboard(allTime: Boolean): BoardView = api.get("v1/leaderboard", mapOf("period" to if (allTime) "all" else "week"))

    suspend fun profile(nickname: String): PublicProfile = api.get("v1/users/$nickname")

    suspend fun reportNickname(nickname: String, note: String?) {
        api.post<NicknameReport, Unit>("v1/users/$nickname/reports", NicknameReport(note))
    }

    companion object {
        const val RECOGNIZE_TIMEOUT_MS = 1_500L
    }
}
