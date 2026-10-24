package com.boimin.beatradar.library

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.boimin.beatradar.R
import com.boimin.beatradar.app
import com.boimin.beatradar.i18n.str
import com.boimin.beatradar.i18n.strPlural
import com.boimin.beatradar.net.ApiException
import com.boimin.beatradar.net.BuildIn
import com.boimin.beatradar.net.BuiltSet
import com.boimin.beatradar.net.Facets
import com.boimin.beatradar.net.PlaylistBpm
import com.boimin.beatradar.net.SharedSet
import com.boimin.beatradar.net.Trending
import com.boimin.beatradar.account.AccountViewModel
import com.boimin.beatradar.net.MyProfile
import com.boimin.beatradar.net.ProfileArt
import com.boimin.beatradar.net.ProfileSet
import com.boimin.beatradar.net.ProfileUpdate
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
import com.boimin.beatradar.data.Settings
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.async
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

/** The tempo a set is built at: either end may be open (null). */
data class BuildRange(val low: Int?, val high: Int?, val halfDouble: Boolean)

/** "Build me a Set": the request and what came back. [range] is null until one is built (the screen starts at the target). */
data class BuildState(
    val minutes: Int = 30,
    val onlyMine: Boolean = false,
    val range: BuildRange? = null,
    val loading: Boolean = false,
    val result: BuiltSet? = null,
    val error: String? = null,
    val seed: Int = 0,
)

/** Discover's extras for the current range: trending songs and the genre/decade filters worth offering. */
data class DiscoverExtras(val trending: Trending? = null, val facets: Facets = Facets())

class LibraryViewModel(application: Application, private val saved: SavedStateHandle) : AndroidViewModel(application) {
    private val repo = application.app.library
    private val setDao = application.app.database.sets()
    private val logDao = application.app.database.beatLog()
    private val signedIn: Boolean get() = getApplication<Application>().app.account.user.value != null
    private val context: Context get() = getApplication()

    private fun describe(e: ApiException): String = describe(context, e)

    /** A set changed: signed in, it goes to the account backup soon. */
    private fun setsChanged() {
        if (signedIn) getApplication<Application>().app.requestSync()
    }

    // Sets (the user's song lists by tempo)

    /** All sets, most recently changed first. */
    val sets: StateFlow<List<SetSummary>> = setDao.observeSets().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setSongs(setId: Long): Flow<List<SetSong>> = setDao.observeSongs(setId)

    /** A set's songs right now (to play it from its card). */
    suspend fun setSongsNow(setId: Long): List<SetSong> = setDao.songs(setId)

    /** Up to four covers per set, for the mosaic on its card. */
    val setArtwork: StateFlow<Map<Long, List<String>>> = setDao.observeArtwork()
        .map { art -> art.groupBy({ it.setId }, { it.artworkUrl }).mapValues { (_, urls) -> urls.distinct().take(4) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /**
     * "From your log": named songs heard this past week in the target range that are in no set yet, newest first.
     * Only from this phone's log, so it works offline.
     */
    fun logSuggestion(settings: Settings): Flow<List<SongSummary>> =
        combine(logDao.observe("", false, true, false), setDao.observeSlugsInSets()) { log, inSets ->
            val since = System.currentTimeMillis() - LOG_SUGGESTION_DAYS * 24 * 60 * 60 * 1000L
            val taken = inSets.toSet()
            log.asSequence()
                .filter { it.songSlug != null && it.title != null && it.timestamp >= since && it.songSlug !in taken && settings.inTarget(it.bpm) }
                .distinctBy { it.songSlug }
                .map { SongSummary(slug = it.songSlug!!, title = it.title!!, artist = it.artist.orEmpty(), bpm = it.bpm) }
                .toList()
        }

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
                if (songs.size == 1) context.str(R.string.sets_removed_one, songs[0].title) else context.strPlural(R.plurals.sets_removed, songs.size, songs.size),
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
                // The length, and the cover for songs added from the log (which has none).
                val song = try {
                    repo.song(slug)
                } catch (e: ApiException) {
                    if (e.offline) return@launch else null
                }
                setDao.fillFromLibrary(slug, song?.durationMs ?: 0, song?.artworkUrl)
            }
        }
    }

    /** Adds [song] to a set (from a quick "+" button), without toggling it off when it's already there. */
    fun addToSetById(setId: Long, song: SongSummary) {
        viewModelScope.launch { addToSet(setId, song); setsChanged() }
    }

    /** Shares a set as a link (sharing again updates the same link). Returns the text for the share sheet. */
    suspend fun shareSet(setId: Long): String? {
        val (set, songs, out) = publishSet(setId) ?: return null
        return context.str(R.string.sets_share_text, set.name, SetRules.span(context, songs.minOf { it.bpm }, songs.maxOf { it.bpm }), out.url)
    }

    /** Puts the set's current songs online as its share snapshot; null (with a message) when it can't. */
    private suspend fun publishSet(setId: Long): Triple<com.boimin.beatradar.data.SongSet, List<SetSong>, com.boimin.beatradar.net.ShareOut>? {
        val set = setDao.byId(setId) ?: return null
        val songs = setDao.songs(setId)
        if (songs.isEmpty()) {
            _message.value = context.str(R.string.sets_add_before_share)
            return null
        }
        return try {
            val out = repo.shareSet(set.syncId, set.name, songs.map { it.slug })
            setDao.setShareSlug(setId, out.slug)
            Triple(set, songs, out)
        } catch (e: ApiException) {
            _message.value = describe(e)
            null
        }
    }

    /** Shows one of my sets on my profile: shares its current songs, then pins that share. */
    fun showSetOnProfile(setId: Long) {
        if (!signedIn) {
            _message.value = context.str(R.string.library_error_sign_in)
            return
        }
        viewModelScope.launch {
            val (_, _, out) = publishSet(setId) ?: return@launch
            try {
                repo.pinSet(out.slug, true)
                _message.value = context.str(R.string.profile_set_shown)
            } catch (e: ApiException) {
                _message.value = describe(e)
            }
            refreshMyProfile()
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
                SharedSetState(slug, error = if (e.http == 404) context.str(R.string.sets_shared_missing) else describe(e))
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
    fun runBuild(range: BuildRange, minutes: Int, onlyMine: Boolean, again: Boolean = false) {
        val seed = if (again) (_build.value?.seed ?: 0) + 1 else 0
        _build.value = BuildState(minutes, onlyMine, range, loading = true, seed = seed)
        viewModelScope.launch {
            try {
                // An open end stands at the edge of what BeatRadar measures.
                val request = BuildIn(range.low ?: Settings.BPM_FLOOR, range.high ?: Settings.BPM_CEILING, range.halfDouble, minutes, onlyMine, seed = seed)
                val result = repo.buildSet(request)
                _build.update { it?.copy(loading = false, result = result) }
            } catch (e: ApiException) {
                _build.update { it?.copy(loading = false, error = describe(e)) }
            }
        }
    }

    // Discover extras

    private val _extras = MutableStateFlow(DiscoverExtras())
    val extras: StateFlow<DiscoverExtras> = _extras


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

    init {
        // Android ended the app in the background with a song page open: open it again.
        saved.get<String>(OPEN_SONG)?.let(::open)
    }

    private val _profiles = MutableStateFlow<List<ProfilePage>>(emptyList())
    /** Open profile pages, the top one showing; empty when none is open. */
    val profiles: StateFlow<List<ProfilePage>> = _profiles
    private var nextProfileKey = 0

    private val _profileArt = MutableStateFlow<ProfileArt?>(null)
    /** The stock pictures to pick from, loaded when the picker opens. */
    val profileArt: StateFlow<ProfileArt?> = _profileArt

    fun loadProfileArt() {
        viewModelScope.launch {
            try {
                _profileArt.value = repo.profileArt()
            } catch (e: ApiException) {
                _message.value = describe(e)
            }
        }
    }

    /** My nickname, so tapping myself anywhere opens my own page (with its switches). */
    val myNickname = MutableStateFlow<String?>(null)

    private val _message = MutableStateFlow<String?>(null)
    /** A one-off message for a snackbar (e.g. "Report sent"). */
    val message: StateFlow<String?> = _message

    private var loadJob: Job? = null
    private var facetsJob: Job? = null

    /**
     * The query the list was last loaded for. The tab sets its starting range and loads at once; the same change then
     * reaches the debounced reload, which must not load (and blank) the list a second time.
     */
    private var loadedQuery: SongQuery? = null

    init {
        @OptIn(FlowPreview::class)
        viewModelScope.launch { query.drop(1).debounce(350).collect { if (it != loadedQuery) reload(newQuery = true) } }
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

    /** Loads the first page again. [newQuery]: the search or filters changed, so the songs on screen go first. */
    fun reload(newQuery: Boolean = false) {
        val q = query.value
        loadedQuery = q
        loadJob?.cancel()
        facetsJob?.cancel()
        _list.update { if (newQuery) ListState(loading = true, loadedOnce = it.loadedOnce) else it.copy(loading = true, error = null) }
        // Facets only add pills to the filter row, which doesn't move the list: they come when they're ready.
        facetsJob = viewModelScope.launch {
            val facets = runCatching { repo.facets(q) }.getOrNull() ?: Facets()
            _extras.update { it.copy(facets = facets) }
        }
        loadJob = viewModelScope.launch {
            // Trending sits above the songs, so it lands with them: arriving later, it pushed the list down.
            val trending = async { runCatching { repo.trending(q) }.getOrNull() }
            try {
                val page = repo.songs(q)
                _extras.update { it.copy(trending = trending.await()) }
                _list.value = ListState(page.items, page.nextCursor, loading = false, loadedOnce = true)
            } catch (e: ApiException) {
                trending.cancel()
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
        saved[OPEN_SONG] = slug
        _detail.value = DetailState(slug)
        refreshDetail()
    }

    fun close() {
        saved.remove<String>(OPEN_SONG)
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
                context.str(R.string.library_report_sent)
            } catch (e: ApiException) {
                describe(e)
            }
        }
    }

    fun openProfile(nickname: String) {
        if (nickname.equals(myNickname.value, ignoreCase = true)) openMyProfile() else pushProfile(nickname)
    }

    fun openMyProfile() = pushProfile(null)

    /**
     * Opens a profile page over whatever shows. A song page or shared set open underneath closes and comes back on
     * Back, so profile → song → profile → … reads like a stack without a navigation library.
     */
    private fun pushProfile(nickname: String?) {
        if (_profiles.value.lastOrNull()?.let { it.nickname == nickname && it.isMe == (nickname == null) } == true) return
        val song = _detail.value?.slug
        val set = _sharedSet.value?.slug
        if (song != null) close()
        if (set != null) closeSharedSet()
        val page = ProfilePage(key = nextProfileKey++, nickname = nickname, returnToSong = song, returnToSet = set)
        _profiles.update { (it + page).takeLast(MAX_PROFILES) }
        loadProfile(page.key)
    }

    fun backFromProfile() {
        val top = _profiles.value.lastOrNull() ?: return
        _profiles.update { it.dropLast(1) }
        top.returnToSet?.let(::openSharedSet)
        top.returnToSong?.let(::open)
    }

    fun closeProfiles() {
        _profiles.value = emptyList()
    }

    /** Reloads my own page wherever it is open (after signing in or out, or a change). */
    fun refreshMyProfile() = _profiles.value.filter { it.isMe }.forEach { loadProfile(it.key) }

    fun refreshProfile(key: Int) = loadProfile(key)

    private fun loadProfile(key: Int) {
        val page = _profiles.value.firstOrNull { it.key == key } ?: return
        viewModelScope.launch {
            val loaded = try {
                if (page.isMe) {
                    if (signedIn) {
                        val mine = repo.myProfile()
                        page.copy(mine = mine, error = null, moreSets = emptyList(), setsNext = mine.profile.pinnedSetsNext)
                    } else {
                        page.copy(mine = null, error = null)
                    }
                } else {
                    val profile = repo.profile(page.nickname!!)
                    page.copy(profile = profile, error = null, moreSets = emptyList(), setsNext = profile.pinnedSetsNext)
                }
            } catch (e: ApiException) {
                page.copy(error = if (e.http == 404 && !page.isMe) context.str(R.string.library_profile_hidden) else describe(e))
            }
            _profiles.update { stack -> stack.map { if (it.key == key) loaded else it } }
        }
    }

    /** The next page of pinned sets, as the page scrolls near its end. */
    fun loadMoreSets(key: Int) {
        val page = _profiles.value.firstOrNull { it.key == key } ?: return
        val cursor = page.setsNext ?: return
        if (page.loadingSets) return
        _profiles.update { stack -> stack.map { if (it.key == key) it.copy(loadingSets = true) else it } }
        viewModelScope.launch {
            val next = try {
                repo.profileSets(page.nickname, cursor)
            } catch (e: ApiException) {
                _message.value = describe(e)
                null
            }
            _profiles.update { stack ->
                stack.map { p ->
                    when {
                        p.key != key -> p
                        next == null || p.setsNext != cursor -> p.copy(loadingSets = false)
                        else -> p.copy(moreSets = p.moreSets + next.items, setsNext = next.nextCursor, loadingSets = false)
                    }
                }
            }
        }
    }

    private fun updateMine(change: (MyProfile) -> MyProfile) {
        _profiles.update { stack -> stack.map { p -> if (p.isMe && p.mine != null) p.copy(mine = change(p.mine)) else p } }
    }

    /** Who sees my profile, and which opt-in sections. The switch moves at once; a failure puts it back. */
    fun updateMySections(update: ProfileUpdate) {
        updateMine {
            it.copy(
                public = update.onLeaderboard ?: it.public,
                showStreak = update.showStreak ?: it.showStreak,
                showTempoZone = update.showTempoZone ?: it.showTempoZone,
                showLoves = update.showLoves ?: it.showLoves,
                profile = it.profile.copy(
                    avatarUrl = update.avatar?.let { id -> "/media/avatars/$id" } ?: it.profile.avatarUrl,
                    bannerUrl = update.banner?.let { id -> "/media/banners/$id" } ?: it.profile.bannerUrl,
                ),
            )
        }
        viewModelScope.launch {
            try {
                getApplication<Application>().app.account.updateProfile(update)
            } catch (e: ApiException) {
                _message.value = describe(e)
                refreshMyProfile()
            }
        }
    }

    fun renameMe(nickname: String) {
        viewModelScope.launch {
            try {
                val me = getApplication<Application>().app.account.updateProfile(nickname = nickname.trim())
                myNickname.value = me.nickname
                refreshMyProfile()
            } catch (e: ApiException) {
                _message.value = if (e.code == "nickname_taken" || e.code == "nickname_not_allowed") AccountViewModel.describe(context, e) else describe(e)
            }
        }
    }

    fun pinSet(slug: String, pinned: Boolean) {
        updateMine { m -> m.copy(sharedSets = m.sharedSets.map { if (it.slug == slug) it.copy(pinned = pinned) else it }) }
        viewModelScope.launch {
            try {
                repo.pinSet(slug, pinned)
            } catch (e: ApiException) {
                _message.value = describe(e)
            }
            refreshMyProfile()
        }
    }

    fun reportNickname(nickname: String) {
        viewModelScope.launch {
            _message.value = try {
                repo.reportNickname(nickname, null)
                context.str(R.string.library_nickname_reported)
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
        /** How far back "From your log" looks. */
        const val LOG_SUGGESTION_DAYS = 7

        /** The user's words for a failed call: the app's own for common cases, else the server's message. */
        fun describe(context: Context, e: ApiException): String = when {
            e.offline -> context.str(R.string.library_offline)
            e.code == "sign_in_required" -> context.str(R.string.library_error_sign_in)
            e.http == 404 -> context.str(R.string.library_error_not_found)
            e.http == 429 -> context.str(R.string.library_error_too_many)
            e.http >= 500 -> context.str(R.string.library_error_server)
            else -> e.message
        }

    }
}

/**
 * An open profile page: someone's by [nickname], or mine when it is null ([mine] then, else [profile]). Back reopens
 * the song page or shared set it was opened from.
 */
data class ProfilePage(
    val key: Int,
    val nickname: String?,
    val profile: PublicProfile? = null,
    val mine: MyProfile? = null,
    val error: String? = null,
    val returnToSong: String? = null,
    val returnToSet: String? = null,
    /** Pinned sets loaded past the first page, and the cursor for more (null: all loaded). */
    val moreSets: List<ProfileSet> = emptyList(),
    val setsNext: String? = null,
    val loadingSets: Boolean = false,
) {
    val isMe: Boolean get() = nickname == null
    val sets: List<ProfileSet> get() = (mine?.profile ?: profile)?.pinnedSets.orEmpty() + moreSets
}

/** Profile pages kept for Back; older ones drop off. */
private const val MAX_PROFILES = 8

/** [SavedStateHandle] key: the slug of the open song page. */
private const val OPEN_SONG = "openSong"
