package com.boimin.beatradar.library

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.boimin.beatradar.app
import com.boimin.beatradar.net.ApiException
import com.boimin.beatradar.net.BuildIn
import com.boimin.beatradar.net.BuiltSet
import com.boimin.beatradar.net.Facets
import com.boimin.beatradar.net.PlaylistBpm
import com.boimin.beatradar.net.SharedSet
import com.boimin.beatradar.net.Trending
import com.boimin.beatradar.net.PublicProfile
import com.boimin.beatradar.net.SongLink
import com.boimin.beatradar.net.SongDetail
import com.boimin.beatradar.net.SongSummary
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import com.boimin.beatradar.data.SetRules
import com.boimin.beatradar.data.SetSong
import com.boimin.beatradar.data.SetSummary
import com.boimin.beatradar.data.SongSet
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The Library list: songs loaded so far, paging and load state. */
data class ListState(
    val items: List<SongSummary> = emptyList(),
    val nextCursor: String? = null,
    val loading: Boolean = false,
    /** Set when the last load failed: "offline" or the server's message. */
    val error: String? = null,
    val loadedOnce: Boolean = false,
)

/** "BPM of my playlist": the link being looked up and what came back. */
data class PlaylistState(
    val link: String = "",
    val loading: Boolean = false,
    val error: String? = null,
    val result: PlaylistBpm? = null,
)

data class DetailState(
    val slug: String,
    val song: SongDetail? = null,
    val loading: Boolean = true,
    val error: String? = null,
    /** "Same tempo, more songs": other well-confirmed songs within ±2 BPM. */
    val similar: List<SongSummary> = emptyList(),
)

/** A change that can be taken back from the snackbar. */
class Undoable(val text: String, val undo: () -> Unit)

/** A set someone shared (`/set/<slug>`), opened to view and copy. */
data class SharedSetState(val slug: String, val set: SharedSet? = null, val error: String? = null)

/** "Build me a Set": the request and what came back. */
data class BuildState(
    val minutes: Int = 30,
    val onlyMine: Boolean = false,
    val loading: Boolean = false,
    val result: BuiltSet? = null,
    val error: String? = null,
    val seed: Int = 0,
)

/** Discover's extras for the current range: trending songs and the genre/decade filters worth offering. */
data class DiscoverExtras(val trending: Trending? = null, val facets: Facets = Facets())

class LibraryViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = application.app.library
    private val setDao = application.app.database.sets()
    private val signedIn: Boolean get() = getApplication<Application>().app.account.user.value != null

    /** A set changed: signed in, it goes to the account backup soon. */
    private fun setsChanged() {
        if (signedIn) getApplication<Application>().app.requestSync()
    }

    // Sets (the user's song lists by tempo)

    /** All sets, most recently changed first. */
    val sets: StateFlow<List<SetSummary>> = setDao.observeSets().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setSongs(setId: Long): Flow<List<SetSong>> = setDao.observeSongs(setId)

    /** Ids of the sets [slug] is in. */
    fun setsWith(slug: String): Flow<List<Long>> = setDao.observeSetsWith(slug)

    /** Creates a set and returns its id (null for an empty name). Adds [song] to it if given. */
    suspend fun createSet(name: String, song: SongSummary? = null): Long? {
        val clean = SetRules.cleanName(name) ?: return null
        val now = System.currentTimeMillis()
        val id = setDao.insertSet(SongSet(name = clean, createdAt = now, updatedAt = now))
        song?.let { addToSet(id, it) }
        setsChanged()
        return id
    }

    fun renameSet(id: Long, name: String) {
        val clean = SetRules.cleanName(name) ?: return
        viewModelScope.launch { setDao.rename(id, clean, System.currentTimeMillis()); setsChanged() }
    }

    /** Signed in, a delete is a tombstone so it reaches the user's other phones. */
    fun deleteSet(id: Long) {
        viewModelScope.launch {
            if (signedIn) setDao.markDeleted(id, System.currentTimeMillis()) else setDao.deleteSet(id)
            setsChanged()
        }
    }

    fun toggleInSet(setId: Long, song: SongSummary, inSet: Boolean) {
        viewModelScope.launch {
            if (inSet) setDao.remove(setId, song.slug, System.currentTimeMillis()) else addToSet(setId, song)
            setsChanged()
        }
    }

    fun removeFromSet(setId: Long, slug: String) {
        viewModelScope.launch { setDao.remove(setId, slug, System.currentTimeMillis()); setsChanged() }
    }

    /** Several songs into a set at once (Discover's selection), skipping ones already in it. */
    fun addSongsToSet(setId: Long, songs: List<SongSummary>) {
        viewModelScope.launch {
            songs.distinctBy { it.slug }.forEach { addToSet(setId, it) }
            setsChanged()
        }
    }

    /** Songs out of a set, with an Undo snackbar to put them back. */
    fun removeSongs(setId: Long, songs: List<SetSong>) {
        if (songs.isEmpty()) return
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            songs.forEach { setDao.remove(setId, it.slug, now) }
            setsChanged()
            _undo.value = Undoable(
                if (songs.size == 1) "Removed “${songs[0].title}”" else "Removed ${songs.size} songs",
            ) {
                viewModelScope.launch {
                    setDao.insertSongs(songs)
                    setDao.touch(setId, System.currentTimeMillis())
                    setsChanged()
                }
            }
        }
    }

    private val _undo = MutableStateFlow<Undoable?>(null)
    /** A change the snackbar offers to undo. */
    val undo: StateFlow<Undoable?> = _undo

    fun undoShown() {
        _undo.value = null
    }

    /** Looks up the length of a set's songs that don't have one yet, for the set's total time. */
    fun fillDurations(songs: List<SetSong>) {
        val missing = songs.filter { it.durationMs == null }.map { it.slug }.distinct()
        if (missing.isEmpty()) return
        viewModelScope.launch {
            for (slug in missing) {
                val ms = try {
                    repo.song(slug).durationMs ?: 0
                } catch (e: ApiException) {
                    if (e.offline) return@launch else 0
                }
                setDao.setDuration(slug, ms)
            }
        }
    }

    /** Adds [song] to a set (from a quick "+" button), without toggling it off when it's already there. */
    fun addToSetById(setId: Long, song: SongSummary) {
        viewModelScope.launch { addToSet(setId, song); setsChanged() }
    }

    /** Shares a set as a link (sharing again updates the same link). Returns the text for the share sheet. */
    suspend fun shareSet(setId: Long): String? {
        val set = setDao.byId(setId) ?: return null
        val songs = setDao.songs(setId)
        if (songs.isEmpty()) {
            _message.value = "Add songs before sharing the set"
            return null
        }
        return try {
            val out = repo.shareSet(set.syncId, set.name, songs.map { it.slug })
            setDao.setShareSlug(setId, out.slug)
            "${set.name} · ${SetRules.span(songs.minOf { it.bpm }, songs.maxOf { it.bpm })} on BeatRadar ${out.url}"
        } catch (e: ApiException) {
            _message.value = describe(e)
            null
        }
    }

    // A shared set from a link

    private val _sharedSet = MutableStateFlow<SharedSetState?>(null)
    val sharedSet: StateFlow<SharedSetState?> = _sharedSet

    /** A `/set/<slug>` link was opened. */
    val sharedSetLink = MutableStateFlow<String?>(null)

    fun openSharedSet(slug: String) {
        _sharedSet.value = SharedSetState(slug)
        viewModelScope.launch {
            _sharedSet.value = try {
                SharedSetState(slug, set = repo.sharedSet(slug))
            } catch (e: ApiException) {
                SharedSetState(slug, error = if (e.http == 404) "This shared set doesn't exist anymore" else describe(e))
            }
        }
    }

    fun closeSharedSet() {
        _sharedSet.value = null
    }

    // Build me a Set

    private val _build = MutableStateFlow<BuildState?>(null)
    val build: StateFlow<BuildState?> = _build

    fun openBuild() {
        _build.value = BuildState()
    }

    fun closeBuild() {
        _build.value = null
    }

    /** Builds a set in [range]; [again] mixes other equally good songs in. */
    fun runBuild(range: IntRange, halfDouble: Boolean, minutes: Int, onlyMine: Boolean, again: Boolean = false) {
        val seed = if (again) (_build.value?.seed ?: 0) + 1 else 0
        _build.value = BuildState(minutes, onlyMine, loading = true, seed = seed)
        viewModelScope.launch {
            try {
                val result = repo.buildSet(BuildIn(range.first, range.last, halfDouble, minutes, onlyMine, seed = seed))
                _build.update { it?.copy(loading = false, result = result) }
            } catch (e: ApiException) {
                _build.update { it?.copy(loading = false, error = describe(e)) }
            }
        }
    }

    // Discover extras

    private val _extras = MutableStateFlow(DiscoverExtras())
    val extras: StateFlow<DiscoverExtras> = _extras

    private fun loadExtras() {
        val q = query.value
        viewModelScope.launch {
            val trending = runCatching { repo.trending(q) }.getOrNull()
            val facets = runCatching { repo.facets(q) }.getOrNull() ?: Facets()
            _extras.value = DiscoverExtras(trending, facets)
        }
    }

    /** A new set holding [songs] (e.g. a playlist's measured tracks). Returns its id, or null for an empty name. */
    suspend fun createSetWith(name: String, songs: List<SongSummary>): Long? {
        val id = createSet(name) ?: return null
        songs.distinctBy { it.slug }.forEach { addToSet(id, it) }
        setsChanged()
        return id
    }

    private val linkCache = mutableMapOf<String, List<SongLink>>()

    /** The set open in the Sets tab, or null for the list (the app header hides while one is open). */
    val openSetId = MutableStateFlow<Long?>(null)


    /**
     * The song in [service]: its own track page when the Library knows it, else a search there. Offline, the search
     * still works.
     */
    suspend fun playUrl(slug: String, title: String, artist: String, service: PlayLinks.Service): String {
        val links = linkCache[slug] ?: try {
            repo.song(slug).links.also { linkCache[slug] = it }
        } catch (_: ApiException) {
            emptyList()
        }
        return PlayLinks.url(service, links, title, artist)
    }

    /** The set as one YouTube queue, or null (with a message) when the server can't be reached. */
    suspend fun youtubeQueue(slugs: List<String>): com.boimin.beatradar.net.YoutubeQueue? = try {
        repo.youtubeQueue(slugs)
    } catch (e: ApiException) {
        _message.value = describe(e)
        null
    }

    // BPM of my playlist

    private val _playlist = MutableStateFlow<PlaylistState?>(null)
    /** The playlist screen, or null when closed. */
    val playlist: StateFlow<PlaylistState?> = _playlist

    /** A playlist link shared to BeatRadar from another app, waiting for the Library tab to open it. */
    val sharedPlaylistLink = MutableStateFlow<String?>(null)

    fun openPlaylist(link: String = "") {
        _playlist.value = PlaylistState(link = link)
        if (link.isNotBlank()) lookupPlaylist(link)
    }

    fun closePlaylist() {
        _playlist.value = null
    }

    fun lookupPlaylist(link: String) {
        _playlist.value = PlaylistState(link = link, loading = true)
        viewModelScope.launch {
            try {
                val result = repo.playlist(link.trim())
                _playlist.update { p -> if (p?.link == link) p.copy(loading = false, result = result) else p }
            } catch (e: ApiException) {
                val message = when (e.code) {
                    "not_a_playlist", "playlist_not_found", "playlist_unavailable" -> e.message
                    else -> describe(e)
                }
                _playlist.update { p -> if (p?.link == link) p.copy(loading = false, error = message) else p }
            }
        }
    }

    // Which tempo feels right?

    fun voteTempo(slug: String, bpm: Float) {
        viewModelScope.launch {
            try {
                val votes = repo.voteTempo(slug, bpm)
                _detail.update { d ->
                    val song = d?.song
                    if (song?.slug != slug) d else d.copy(song = song.copy(tempoVotes = votes))
                }
            } catch (e: ApiException) {
                _message.value = describe(e)
            }
        }
    }

    private suspend fun addToSet(setId: Long, song: SongSummary) {
        setDao.add(SetSong(setId, song.slug, song.title, song.artist, song.bpm, song.artworkUrl, System.currentTimeMillis()))
    }

    val query = MutableStateFlow(SongQuery())
    private val _list = MutableStateFlow(ListState())
    val list: StateFlow<ListState> = _list

    private val _detail = MutableStateFlow<DetailState?>(null)
    /** The open song page, or null for the list. */
    val detail: StateFlow<DetailState?> = _detail

    private val _profile = MutableStateFlow<PublicProfile?>(null)
    val profile: StateFlow<PublicProfile?> = _profile

    private val _message = MutableStateFlow<String?>(null)
    /** A one-off message for a snackbar (e.g. "Report sent"). */
    val message: StateFlow<String?> = _message

    private var loadJob: Job? = null

    init {
        @OptIn(FlowPreview::class)
        viewModelScope.launch { query.drop(1).debounce(350).collect { reload() } }
        // Signing in or out changes which songs are "loved by me".
        viewModelScope.launch { application.app.account.user.map { it?.id }.distinctUntilChanged().drop(1).collect { refresh() } }
        // My measurement of a song just went up: its counters changed.
        viewModelScope.launch {
            application.app.sync.uploaded.collect { slugs ->
                val open = _detail.value?.song?.slug
                if (open != null && open in slugs) refreshDetail()
                if (_list.value.items.any { it.slug in slugs }) reload()
            }
        }
    }

    /**
     * The tab was opened (or the account changed): fetch fresh counters, loves and new songs. The current list stays on
     * screen while it loads, and an open song page refreshes too.
     */
    fun refresh() {
        reload()
        refreshDetail()
    }

    fun reload() {
        loadExtras()
        loadJob?.cancel()
        _list.update { it.copy(loading = true, error = null) }
        loadJob = viewModelScope.launch {
            try {
                val page = repo.songs(query.value)
                _list.value = ListState(page.items, page.nextCursor, loading = false, loadedOnce = true)
            } catch (e: ApiException) {
                _list.update { it.copy(loading = false, error = describe(e), loadedOnce = true) }
            }
        }
    }

    fun loadMore() {
        val s = _list.value
        val cursor = s.nextCursor ?: return
        if (s.loading) return
        _list.update { it.copy(loading = true) }
        loadJob = viewModelScope.launch {
            try {
                val page = repo.songs(query.value, cursor)
                _list.update { st ->
                    val known = st.items.map { it.slug }.toSet()
                    st.copy(items = st.items + page.items.filter { it.slug !in known }, nextCursor = page.nextCursor, loading = false)
                }
            } catch (e: ApiException) {
                _list.update { it.copy(loading = false, error = describe(e)) }
            }
        }
    }

    fun open(slug: String) {
        _detail.value = DetailState(slug)
        refreshDetail()
    }

    fun close() {
        _detail.value = null
    }

    fun refreshDetail() {
        val slug = _detail.value?.slug ?: return
        viewModelScope.launch {
            try {
                val song = repo.song(slug)
                _detail.update { d -> if (d?.slug == slug) d.copy(song = song, loading = false, error = null) else d }
                val similar = runCatching { repo.similar(song.bpm).items.filter { it.slug != song.slug }.take(8) }.getOrDefault(emptyList())
                _detail.update { d -> if (d?.slug == slug) d.copy(similar = similar) else d }
            } catch (e: ApiException) {
                _detail.update { d -> if (d?.slug == slug) d.copy(loading = false, error = describe(e)) else d }
            }
        }
    }

    /** Optimistic: the heart flips at once and settles on the server's count. */
    fun setLoved(slug: String, loved: Boolean) {
        fun apply(l: Boolean, count: Int?) {
            _list.update { st ->
                st.copy(items = st.items.map { if (it.slug == slug) it.copy(lovedByMe = l, loveCount = count ?: (it.loveCount + if (l) 1 else -1).coerceAtLeast(0)) else it })
            }
            _detail.update { d ->
                val song = d?.song
                if (song?.slug != slug) d else d.copy(song = song.copy(lovedByMe = l, loveCount = count ?: (song.loveCount + if (l) 1 else -1).coerceAtLeast(0)))
            }
        }
        apply(loved, null)
        viewModelScope.launch {
            try {
                val state = repo.setLoved(slug, loved)
                apply(state.loved, state.loveCount)
            } catch (e: ApiException) {
                apply(!loved, null)
                _message.value = describe(e)
            }
        }
    }

    fun report(slug: String, reason: String, note: String?) {
        viewModelScope.launch {
            _message.value = try {
                repo.report(slug, reason, note)
                "Thanks, the report was sent"
            } catch (e: ApiException) {
                describe(e)
            }
        }
    }

    fun openProfile(nickname: String) {
        viewModelScope.launch {
            try {
                _profile.value = repo.profile(nickname)
            } catch (e: ApiException) {
                _message.value = if (e.http == 404) "This profile is hidden" else describe(e)
            }
        }
    }

    fun closeProfile() {
        _profile.value = null
    }

    fun reportNickname(nickname: String) {
        viewModelScope.launch {
            _message.value = try {
                repo.reportNickname(nickname, null)
                "Thanks, the nickname was reported"
            } catch (e: ApiException) {
                describe(e)
            }
        }
    }

    /** A short message for the snackbar (e.g. "Added 3 songs to …"). */
    fun say(text: String) {
        _message.value = text
    }

    fun messageShown() {
        _message.value = null
    }

    companion object {
        fun describe(e: ApiException): String = when {
            e.offline -> "You're offline"
            e.code == "sign_in_required" -> "Sign in first"
            e.http == 404 -> "Not found"
            e.http == 429 -> "Too many requests. Try again in a minute."
            e.http >= 500 -> "The Library is having trouble. Try again later."
            else -> e.message
        }
    }
}
