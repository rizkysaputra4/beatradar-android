package com.boimin.beatradar.library

import androidx.annotation.StringRes
import com.boimin.beatradar.R
import com.boimin.beatradar.account.AccountRepository
import com.boimin.beatradar.dsp.Landmark
import com.boimin.beatradar.net.BoardView
import com.boimin.beatradar.net.BuildIn
import com.boimin.beatradar.net.BuiltSet
import com.boimin.beatradar.net.Facets
import com.boimin.beatradar.net.NameProposalIn
import com.boimin.beatradar.net.NameProposalOut
import com.boimin.beatradar.net.ShareIn
import com.boimin.beatradar.net.ShareOut
import com.boimin.beatradar.net.SharedSet
import com.boimin.beatradar.net.SharedSummary
import com.boimin.beatradar.net.SupporterIn
import com.boimin.beatradar.net.SupporterOut
import com.boimin.beatradar.net.Trending
import com.boimin.beatradar.net.YoutubeQueue
import com.boimin.beatradar.net.YoutubeQueueIn
import com.boimin.beatradar.net.Discoveries
import com.boimin.beatradar.net.Milestones
import com.boimin.beatradar.net.PlaylistBpm
import com.boimin.beatradar.net.PlaylistIn
import com.boimin.beatradar.net.TempoVoteIn
import com.boimin.beatradar.net.TempoVotes
import com.boimin.beatradar.net.ApiJson
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
enum class SongSort(val api: String, @StringRes val label: Int) {
    POPULAR("popular", R.string.library_sort_popular),
    CONFIRMED("confirmed", R.string.library_sort_confirmed),
    LOVED("loved", R.string.library_sort_most_loved),
    RECENT("recent", R.string.library_sort_new),
    BPM("bpm", R.string.library_sort_bpm),
}

data class SongQuery(
    val text: String = "",
    val bpmMin: Int? = null,
    val bpmMax: Int? = null,
    val halfDouble: Boolean = false,
    val sort: SongSort = SongSort.POPULAR,
    val lovedOnly: Boolean = false,
    /** Genre from the Library's facets, e.g. "Dance". */
    val genre: String? = null,
    /** First year of a decade, e.g. 2010. */
    val decade: Int? = null,
) {
    fun params(cursor: String?): Map<String, Any?> = mapOf(
        "q" to text.trim().takeIf { it.isNotEmpty() },
        "bpmMin" to bpmMin,
        "bpmMax" to bpmMax,
        "halfDouble" to halfDouble.takeIf { it },
        "sort" to sort.api,
        "loved" to lovedOnly.takeIf { it },
        "genre" to genre,
        "decade" to decade,
        "cursor" to cursor,
    )

    fun rangeParams(): Map<String, Any?> = mapOf("bpmMin" to bpmMin, "bpmMax" to bpmMax, "halfDouble" to halfDouble.takeIf { it })
}

/** The shared Library and rewards on the server (PLAN §7, §7b). Every call throws ApiException on failure. */
class LibraryRepository(private val account: AccountRepository) {
    private val api get() = account.api

    suspend fun songs(query: SongQuery, cursor: String? = null): SongPage = api.get("v1/songs", query.params(cursor))

    /** "Same tempo, more songs": the most confirmed songs within [spread] BPM of [bpm]. */
    suspend fun similar(bpm: Float, spread: Int = 2): SongPage =
        api.get("v1/songs", mapOf("bpmMin" to (bpm - spread), "bpmMax" to (bpm + spread), "sort" to "confirmed"))

    /** Songs most measured this week in the range (or the most confirmed, with `fallback`). */
    suspend fun trending(query: SongQuery): Trending = api.get("v1/songs/trending", query.rangeParams())

    /** Genres and decades worth filtering by in the range. */
    suspend fun facets(query: SongQuery): Facets = api.get("v1/songs/facets", query.rangeParams())

    suspend fun shareSet(clientRef: String, name: String, slugs: List<String>): ShareOut =
        api.post("v1/set-shares", ShareIn(clientRef, name, slugs))

    suspend fun sharedSet(slug: String): SharedSet = api.get("v1/set-shares/$slug")

    suspend fun buildSet(request: BuildIn): BuiltSet = api.post("v1/sets/build", request)

    /** The songs as one YouTube queue (a temporary playlist link), in order. */
    suspend fun youtubeQueue(slugs: List<String>): YoutubeQueue = api.post("v1/sets/youtube-queue", YoutubeQueueIn(slugs), timeoutMs = YOUTUBE_TIMEOUT_MS)

    /** Proposes a name for an unnamed song whose fingerprint was uploaded (crowd names; an admin decides). */
    suspend fun proposeName(clientRef: String, title: String, artist: String): NameProposalOut =
        api.post("v1/unnamed/$clientRef/name", NameProposalIn(title, artist))

    suspend fun shared(): SharedSummary = api.get("v1/me/shared")

    suspend fun claimSupporter(productId: String, purchaseToken: String): SupporterOut =
        api.post("v1/me/supporter", SupporterIn(productId, purchaseToken))

    /** Old slugs of merged songs answer with the surviving song; check [SongDetail.slug]. */
    suspend fun song(slug: String): SongDetail = api.get("v1/songs/$slug")

    suspend fun setLoved(slug: String, loved: Boolean): LoveState =
        if (loved) api.put("v1/songs/$slug/love") else api.delete("v1/songs/$slug/love")

    /** "Which tempo feels right?": [bpm] is the song's bpm or bpmAlt. One vote per person, changeable. */
    suspend fun voteTempo(slug: String, bpm: Float): TempoVotes =
        api.call("PUT", "v1/songs/$slug/tempo-vote", TempoVotes.serializer(), ApiJson.encodeToString(TempoVoteIn.serializer(), TempoVoteIn(bpm)))

    /** Every track of a Spotify or YouTube playlist with its Library tempo, if known. */
    suspend fun playlist(link: String): PlaylistBpm = api.post("v1/playlists/lookup", PlaylistIn(link), timeoutMs = PLAYLIST_TIMEOUT_MS)

    /** Songs this user (or phone) brought into the Library. */
    suspend fun discoveries(): Discoveries = api.get("v1/me/discoveries")

    /** Listener milestones reached since the last call: each is returned once. */
    suspend fun milestones(): Milestones = api.get("v1/me/milestones")

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
        /** A long playlist takes a few pages from Spotify or YouTube. */
        const val PLAYLIST_TIMEOUT_MS = 45_000L
        /** The server may look a few songs up on YouTube first. */
        const val YOUTUBE_TIMEOUT_MS = 30_000L
    }
}
