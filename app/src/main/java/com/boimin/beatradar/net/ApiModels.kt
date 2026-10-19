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
@Serializable data class ProfileUpdate(val nickname: String? = null, val onLeaderboard: Boolean? = null)

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
    val links: List<SongLink> = emptyList(),
    val broughtInBy: String? = null,
    val createdAt: String? = null,
) {
    fun summary() = SongSummary(slug, title, artist, bpm, bpmAlt, listeners, confirmations, loveCount, artworkUrl, lovedByMe)
}
@Serializable data class LoveState(val loved: Boolean, val loveCount: Int)
@Serializable data class ReportIn(val reason: String, val note: String? = null)
@Serializable data class NicknameReport(val note: String? = null)

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
)
@Serializable data class BoardEntry(val rank: Long, val nickname: String, val beats: Long, val grooveMaster: Long = 0)
@Serializable data class BoardView(
    val period: String = "week",
    val entries: List<BoardEntry> = emptyList(),
    val me: BoardEntry? = null,
    val lastGrooveMaster: String? = null,
    val refreshedAt: String? = null,
)
@Serializable data class PublicBadge(val code: String, val name: String, val tier: Int = 0, val count: Int = 0)
@Serializable data class PublicProfile(val nickname: String, val beatsAll: Long = 0, val grooveMaster: Long = 0, val badges: List<PublicBadge> = emptyList())

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
