package com.boimin.beatradar.library

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.boimin.beatradar.app
import com.boimin.beatradar.net.ApiException
import com.boimin.beatradar.net.PublicProfile
import com.boimin.beatradar.net.SongDetail
import com.boimin.beatradar.net.SongSummary
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
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

data class DetailState(
    val slug: String,
    val song: SongDetail? = null,
    val loading: Boolean = true,
    val error: String? = null,
)

class LibraryViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = application.app.library

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
        // My measurement of a song just went up: its counters changed.
        viewModelScope.launch {
            application.app.sync.uploaded.collect { slugs ->
                val open = _detail.value?.song?.slug
                if (open != null && open in slugs) refreshDetail()
                if (_list.value.items.any { it.slug in slugs }) reload()
            }
        }
    }

    fun ensureLoaded() {
        if (!_list.value.loadedOnce && !_list.value.loading) reload()
    }

    fun reload() {
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
