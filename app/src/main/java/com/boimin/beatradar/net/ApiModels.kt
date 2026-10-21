package com.boimin.beatradar.net

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Request and response bodies of the BeatRadar Library API (`beatradar-backend/docs/openapi.json`). */
val ApiJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = true
}

@Serializable data class ApiErrorBody(val error: ApiErrorDetail)
@Serializable data class ApiErrorDetail(val code: String, val message: String = "")

// Installs

@Serializable data class RegisterInstall(val installId: String, val installSecret: String, val platform: String? = "android", val appVersion: String? = null)
@Serializable data class InstallToken(val installToken: String, val integrityRequestHash: String)
@Serializable data class IntegrityRequest(val integrityToken: String)
@Serializable data class IntegrityResult(val integrityOk: Boolean)

// Measurements and recognition

@Serializable data class MeasurementIn(
    val clientRef: String,
    val title: String,
    val artist: String,
    val titleSource: String,
    val bpm: Float,
    val confidence: Float,
    val heardAt: String,
    val prints: String? = null,
)
@Serializable data class MeasurementBatch(val items: List<MeasurementIn>)
@Serializable data class ItemResult(
    val clientRef: String,
    val status: String,
    val reason: String? = null,
    val songSlug: String? = null,
    val printsAccepted: Boolean = false,
)
@Serializable data class UploadResult(val results: List<ItemResult>)

@Serializable data class RecognizeIn(val landmarks: String)
@Serializable data class RecognizedSong(val slug: String, val title: String, val artist: String)
@Serializable data class RecognizeOut(val song: RecognizedSong? = null, val score: Int = 0, val state: String? = null)

// Accounts

@Serializable data class Credentials(val email: String, val password: String)
@Serializable data class EmailOnly(val email: String)
@Serializable data class GoogleSignIn(val idToken: String)
@Serializable data class RefreshRequest(val refreshToken: String)
@Serializable data class Me(
    val id: String,
    val email: String? = null,
    val emailVerified: Boolean = false,
    val nickname: String? = null,
    val onLeaderboard: Boolean = true,
    val hasPassword: Boolean = false,
    val googleLinked: Boolean = false,
)
@Serializable data class Session(val accessToken: String, val refreshToken: String, val expiresIn: Long, val user: Me)
@Serializable data class ProfileUpdate(
    val nickname: String? = null,
    val onLeaderboard: Boolean? = null,
    val showStreak: Boolean? = null,
    val showTempoZone: Boolean? = null,
    val showLoves: Boolean? = null,
    /** Stock picture ids from `/v1/profile-art`. */
    val avatar: String? = null,
    val banner: String? = null,
)

// Library

@Serializable data class SongSummary(
    val slug: String,
    val title: String,
    val artist: String,
    val bpm: Float,
    val bpmAlt: Float? = null,
    val listeners: Int = 0,
    val confirmations: Int = 0,
    val loveCount: Int = 0,
    val artworkUrl: String? = null,
    val lovedByMe: Boolean = false,
)
@Serializable data class SongPage(val items: List<SongSummary>, val nextCursor: String? = null)
@Serializable data class SongLink(val service: String, val url: String, val exact: Boolean)
@Serializable data class SongDetail(
    val slug: String,
    val title: String,
    val artist: String,
    val bpm: Float,
    val bpmAlt: Float? = null,
    val bpmSpread: Float? = null,
    val listeners: Int = 0,
    val confirmations: Int = 0,
    val measurements: Int = 0,
    val loveCount: Int = 0,
    val artworkUrl: String? = null,
    val lovedByMe: Boolean = false,
    val genre: String? = null,
    val releaseYear: Int? = null,
    val durationMs: Int? = null,
    val links: List<SongLink> = emptyList(),
    val broughtInBy: String? = null,
    /** This phone (or another of the signed-in user's) brought the song into the Library. */
    val discoveredByMe: Boolean = false,
    /** "Which tempo feels right?", while the song is also heard at [bpmAlt]. */
    val tempoVotes: TempoVotes? = null,
    val createdAt: String? = null,
) {
    fun summary() = SongSummary(slug, title, artist, bpm, bpmAlt, listeners, confirmations, loveCount, artworkUrl, lovedByMe)
}
@Serializable data class Trending(val items: List<SongSummary> = emptyList(), val fallback: Boolean = false)
@Serializable data class GenreFacet(val genre: String, val songs: Int = 0)
@Serializable data class DecadeFacet(val decade: Int, val songs: Int = 0)
@Serializable data class Facets(val genres: List<GenreFacet> = emptyList(), val decades: List<DecadeFacet> = emptyList())
@Serializable data class TempoVotes(val forBpm: Int = 0, val forAlt: Int = 0, val myVote: Float? = null)
@Serializable data class TempoVoteIn(val bpm: Float)
@Serializable data class LoveState(val loved: Boolean, val loveCount: Int)
@Serializable data class ReportIn(val reason: String, val note: String? = null)
@Serializable data class NicknameReport(val note: String? = null)

// BPM of my playlist

@Serializable data class PlaylistIn(val link: String)
@Serializable data class PlaylistTrack(val position: Int, val title: String, val artist: String, val song: SongSummary? = null)
@Serializable data class PlaylistBpm(
    val service: String,
    val name: String? = null,
    val total: Int = 0,
    val matched: Int = 0,
    val tracks: List<PlaylistTrack> = emptyList(),
)

// Sets: backup, share links, Build me a Set

@Serializable data class SetIn(
    val id: String,
    val name: String,
    val songs: List<String>,
    val createdAt: String,
    val updatedAt: String,
    val deleted: Boolean = false,
)
@Serializable data class SetPush(val sets: List<SetIn>)
@Serializable data class SetPushResult(val applied: Int = 0)
@Serializable data class SetOut(
    val id: String,
    val name: String,
    val songs: List<SongSummary> = emptyList(),
    val createdAt: String,
    val updatedAt: String,
    val deleted: Boolean = false,
)
@Serializable data class SetPage(val sets: List<SetOut>, val cursor: String, val hasMore: Boolean)
@Serializable data class ShareIn(val clientRef: String, val name: String, val songs: List<String>)
@Serializable data class ShareOut(val slug: String, val url: String)
@Serializable data class SharedSet(
    val slug: String,
    val name: String,
    val songs: List<SongSummary> = emptyList(),
    val sharedBy: String? = null,
)
@Serializable data class BuildIn(
    val bpmMin: Int,
    val bpmMax: Int,
    val halfDouble: Boolean,
    val minutes: Int,
    val onlyMine: Boolean,
    val exclude: List<String> = emptyList(),
    val seed: Int = 0,
)
@Serializable data class BuiltSong(
    val slug: String,
    val title: String,
    val artist: String,
    val bpm: Float,
    val bpmAlt: Float? = null,
    val listeners: Int = 0,
    val confirmations: Int = 0,
    val loveCount: Int = 0,
    val artworkUrl: String? = null,
    val lovedByMe: Boolean = false,
    val durationMs: Int,
    val estimated: Boolean = false,
) {
    fun summary() = SongSummary(slug, title, artist, bpm, bpmAlt, listeners, confirmations, loveCount, artworkUrl, lovedByMe)
}
@Serializable data class YoutubeQueueIn(val songs: List<String>)
/** [url] opens the songs found as one temporary YouTube playlist; [missing] slugs aren't on YouTube yet. */
@Serializable data class YoutubeQueue(val url: String? = null, val found: Int = 0, val missing: List<String> = emptyList())
@Serializable data class BuiltSet(val songs: List<BuiltSong> = emptyList(), val totalMs: Long = 0, val targetMs: Long = 0)

// What I've shared

@Serializable data class SharedKind(val kind: String, val count: Long = 0, val lastAt: String? = null, val kept: String = "")
/** [deletionEmail]: where to ask for deletion (an admin deletes); [reference] identifies this phone in the request. */
@Serializable data class SharedSummary(val items: List<SharedKind> = emptyList(), val deletionEmail: String? = null, val reference: String = "")

// Crowd names and supporters

@Serializable data class NameProposalIn(val title: String, val artist: String)
@Serializable data class NameProposalOut(val recorded: Boolean = false)
@Serializable data class SupporterIn(val productId: String, val purchaseToken: String)
@Serializable data class SupporterOut(val supporter: Boolean = false)

// Log backup

@Serializable data class LogEntryIn(
    val installId: String? = null,
    val clientRef: String,
    val title: String? = null,
    val artist: String? = null,
    val bpm: Float,
    val confidence: Float,
    val inRange: Boolean,
    val heardAt: String,
    val updatedAt: String,
    val deleted: Boolean = false,
)
@Serializable data class LogPush(val entries: List<LogEntryIn>)
@Serializable data class PushResult(val applied: Int)
@Serializable data class LogEntryOut(
    val installId: String,
    val clientRef: String,
    val title: String? = null,
    val artist: String? = null,
    val bpm: Float,
    val confidence: Float,
    val inRange: Boolean,
    val heardAt: String,
    val updatedAt: String,
    val deleted: Boolean = false,
    val songSlug: String? = null,
)
@Serializable data class LogPage(val entries: List<LogEntryOut>, val cursor: String, val hasMore: Boolean)

// Rewards

@Serializable data class BadgeView(
    val code: String,
    val name: String,
    val description: String = "",
    val tier: Int = 0,
    val maxTier: Int = 1,
    val count: Int = 0,
    val progress: Long = 0,
    val nextAt: Long? = null,
    /** What each tier takes, e.g. [10, 100, 1000]. */
    val tiers: List<Long> = emptyList(),
    /** Earned again each time (weekly finishes): a count instead of tiers. */
    val repeatable: Boolean = false,
    val earnedAt: String? = null,
) {
    /** Tier 0 = locked; 1–3 = bronze, silver, gold. */
    val earned: Boolean get() = tier > 0
}
@Serializable data class Rewards(
    val beatsWeek: Long = 0,
    val beatsAll: Long = 0,
    val rankWeek: Long? = null,
    val rankAll: Long? = null,
    val streak: Long = 0,
    val badges: List<BadgeView> = emptyList(),
    val newBadges: List<String> = emptyList(),
    val signedIn: Boolean = false,
    /** Bought a coffee: a small badge next to the nickname. */
    val supporter: Boolean = false,
    /** My stock avatar, a path on the API host (`/media/avatars/…`). */
    val avatarUrl: String? = null,
)
@Serializable data class BoardEntry(
    val rank: Long,
    val nickname: String,
    val beats: Long,
    val grooveMaster: Long = 0,
    val supporter: Boolean = false,
    val avatarUrl: String? = null,
)
@Serializable data class BoardView(
    val period: String = "week",
    val entries: List<BoardEntry> = emptyList(),
    val me: BoardEntry? = null,
    val lastGrooveMaster: String? = null,
    val refreshedAt: String? = null,
)
/** A song the user brought in, with the highest listener milestone it reached (10, 100, 1000). */
@Serializable data class Discovery(
    val slug: String,
    val title: String,
    val artist: String,
    val bpm: Float,
    val listeners: Int = 0,
    val artworkUrl: String? = null,
    val milestone: Int? = null,
)
@Serializable data class Discoveries(val total: Long = 0, val items: List<Discovery> = emptyList())
@Serializable data class Milestone(val slug: String, val title: String, val artist: String, val milestone: Int, val listeners: Int = 0)
@Serializable data class Milestones(val items: List<Milestone> = emptyList())
@Serializable data class PublicBadge(val code: String, val name: String, val tier: Int = 0, val count: Int = 0)
@Serializable data class PublicProfile(
    val nickname: String,
    /** Stock pictures, paths on the API host (`/media/avatars/…`, `/media/banners/…`). */
    val avatarUrl: String? = null,
    val bannerUrl: String? = null,
    val beatsAll: Long = 0,
    val rankAll: Long? = null,
    val rankWeek: Long? = null,
    val grooveMaster: Long = 0,
    val supporter: Boolean = false,
    /** `YYYY-MM`: the month only, never the day. */
    val joined: String? = null,
    val badges: List<PublicBadge> = emptyList(),
    /** Songs they brought in that earned Trailblazer: the same shape as my own discoveries, without milestones. */
    val broughtIn: List<Discovery> = emptyList(),
    val broughtInTotal: Long = 0,
    /** Shared sets pinned to the profile: the first page, and the cursor for the next. */
    val pinnedSets: List<ProfileSet> = emptyList(),
    val pinnedSetsNext: String? = null,
    /** Opt-in sections: null unless the owner shows them (always filled on my own). */
    val streak: Long? = null,
    val tempoZone: TempoZone? = null,
    val loves: List<Discovery>? = null,
)
@Serializable data class ProfileSetPage(val items: List<ProfileSet> = emptyList(), val nextCursor: String? = null)
@Serializable data class ProfileSet(val slug: String, val name: String, val songs: Long = 0, val bpmLow: Float? = null, val bpmHigh: Float? = null)
/** The middle half of someone's recent tempos, e.g. 158–172 BPM over 40 songs. */
@Serializable data class TempoZone(val low: Int, val high: Int, val songs: Long = 0)
/** My own profile with everything on it, and the switches for what others see. */
@Serializable data class MyProfile(
    val profile: PublicProfile,
    val public: Boolean = true,
    val showStreak: Boolean = false,
    val showTempoZone: Boolean = false,
    val showLoves: Boolean = false,
    val sharedSets: List<MySharedSet> = emptyList(),
)
@Serializable data class MySharedSet(val slug: String, val name: String, val songs: Long = 0, val pinned: Boolean = false)
@Serializable data class PinIn(val pinned: Boolean)
@Serializable data class ArtItem(val id: String, val url: String)
/** The stock of avatars and background pictures to pick from. */
@Serializable data class ProfileArt(val avatars: List<ArtItem> = emptyList(), val banners: List<ArtItem> = emptyList())
@Serializable data class PinOut(val pinned: Boolean)

// Unnamed songs and name suggestions (PLAN §5c)

@Serializable data class UnnamedIn(val clientRef: String, val prints: String, val bpm: Float? = null)
@Serializable data class UnnamedBatch(val items: List<UnnamedIn>)
@Serializable data class Suggestion(
    val clientRef: String,
    val slug: String,
    val title: String,
    val artist: String,
    val bpm: Float? = null,
    val score: Int = 0,
    val state: String = "candidate",
)
@Serializable data class UnnamedItemResult(val clientRef: String, val status: String, val reason: String? = null, val suggestion: Suggestion? = null)
@Serializable data class UnnamedResult(val results: List<UnnamedItemResult>)
@Serializable data class SuggestionList(val items: List<Suggestion>)
@Serializable data class RejectResult(val next: Suggestion? = null)

// Clips of unknown songs, only with Help name unknown songs on (PLAN §5d)
@Serializable data class ClipConsentIn(val appVersion: String)
@Serializable data class WantedClips(val clientRefs: List<String> = emptyList())
