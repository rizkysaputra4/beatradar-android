package com.boimin.beatradar.account

import android.app.Application
import android.content.Context
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.boimin.beatradar.R
import com.boimin.beatradar.app
import com.boimin.beatradar.data.ClipStore
import com.boimin.beatradar.i18n.str
import com.boimin.beatradar.net.BadgeView
import com.boimin.beatradar.net.ApiException
import com.boimin.beatradar.net.BoardView
import com.boimin.beatradar.net.Me
import com.boimin.beatradar.net.Discoveries
import com.boimin.beatradar.net.Rewards
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/** Sign-in sheet progress: what's busy, and the last error or info line. */
data class SignInState(
    val busy: Boolean = false,
    val error: String? = null,
    val info: String? = null,
    /** The last sign-in failed because the address isn't verified yet: offer "Resend". */
    val canResend: Boolean = false,
)

/** Accounts (PLAN §3, M2) and rewards (§7b, M5b) for the UI. */
class AccountViewModel(application: Application) : AndroidViewModel(application) {
    private val account = application.app.account
    private val library = application.app.library
    private val dao = application.app.database.beatLog()

    val user: StateFlow<Me?> = account.user

    private val _signIn = MutableStateFlow(SignInState())
    val signIn: StateFlow<SignInState> = _signIn

    private val _discoveries = MutableStateFlow<Discoveries?>(null)
    /** Songs this user (or phone) brought into the Library. */
    val discoveries: StateFlow<Discoveries?> = _discoveries

    private val _rewards = MutableStateFlow<Rewards?>(null)
    val rewards: StateFlow<Rewards?> = _rewards

    private val _board = MutableStateFlow<BoardView?>(null)
    val board: StateFlow<BoardView?> = _board

    private val _celebrate = MutableStateFlow<List<BadgeView>>(emptyList())
    /** Badges earned or upgraded since the last look, to celebrate once. */
    val celebrate: StateFlow<List<BadgeView>> = _celebrate

    private val _toast = MutableStateFlow<String?>(null)
    val toast: StateFlow<String?> = _toast

    /** Runs once the user is signed in (e.g. a love tapped as a guest). */
    private var afterSignIn: (() -> Unit)? = null

    init {
        viewModelScope.launch {
            account.signedOut.collect { reason ->
                // Nothing of the previous account may stay on screen: drop its beats, badges and board, then show
                // this phone's own (guest) beats.
                clearAccountData()
                loadRewards()
                if (reason == SignOutReason.SESSION_LOST) _toast.value = text(R.string.account_session_lost)
            }
        }
        viewModelScope.launch { account.refreshMe() }
        // The server awards beats a few seconds after a measurement arrives (its consensus job): refresh the flame then,
        // and once more in case that job was busy. A burst of uploads makes one refresh.
        viewModelScope.launch {
            application.app.sync.uploaded.collectLatest {
                for (wait in REWARDS_AFTER_UPLOAD_MS) {
                    delay(wait)
                    loadRewards()
                }
            }
        }
    }

    fun setAfterSignIn(action: (() -> Unit)?) {
        afterSignIn = action
        _signIn.value = SignInState()
    }

    private fun signInStep(block: suspend () -> Unit) {
        _signIn.value = SignInState(busy = true)
        viewModelScope.launch {
            _signIn.value = try {
                block()
                SignInState()
            } catch (e: ApiException) {
                SignInState(error = describe(e), canResend = e.code == "email_not_verified")
            }
        }
    }

    private fun signedIn() {
        afterSignIn?.invoke()
        afterSignIn = null
        clearAccountData()
        loadRewards()
    }

    fun login(email: String, password: String) = signInStep {
        account.login(email, password)
        signedIn()
    }

    fun register(email: String, password: String) = signInStep {
        account.register(email, password)
        signedIn()
    }

    fun google(idToken: String) = signInStep {
        account.signInWithGoogle(idToken)
        signedIn()
    }

    fun googleFailed(message: String) {
        _signIn.value = SignInState(error = message)
    }

    fun resendVerification(email: String) {
        viewModelScope.launch {
            runCatching { account.resendVerification(email) }
            _signIn.value = SignInState(info = text(R.string.account_verification_resent))
        }
    }

    fun forgotPassword(email: String) {
        viewModelScope.launch {
            _signIn.value = try {
                account.forgotPassword(email)
                SignInState(info = text(R.string.account_reset_sent))
            } catch (e: ApiException) {
                SignInState(error = describe(e))
            }
        }
    }

    /** [keepLog] false clears this phone's log (it's still in the account). */
    fun signOut(keepLog: Boolean) {
        viewModelScope.launch {
            account.logout()
            if (!keepLog) clearLocalLog()
        }
    }

    fun deleteAccount() {
        viewModelScope.launch {
            try {
                account.deleteAccount()
                _toast.value = text(R.string.account_deleted)
            } catch (e: ApiException) {
                _toast.value = describe(e)
            }
        }
    }

    private fun clearAccountData() {
        _rewards.value = null
        _discoveries.value = null
        _board.value = null
        _celebrate.value = emptyList()
    }

    private suspend fun clearLocalLog() {
        dao.allClips().forEach(ClipStore::delete)
        dao.clear()
    }

    fun rename(nickname: String) {
        viewModelScope.launch {
            _toast.value = try {
                account.updateProfile(nickname = nickname.trim())
                null
            } catch (e: ApiException) {
                describe(e)
            }
        }
    }

    fun setOnLeaderboard(on: Boolean) {
        viewModelScope.launch {
            try {
                account.updateProfile(onLeaderboard = on)
            } catch (e: ApiException) {
                _toast.value = describe(e)
            }
        }
    }

    /** Beats and badges; new badges are celebrated once (the call marks them seen). */
    fun loadRewards() {
        viewModelScope.launch { runCatching { library.discoveries() }.getOrNull()?.let { _discoveries.value = it } }
        viewModelScope.launch {
            val r = runCatching { library.rewards() }.getOrNull() ?: return@launch
            _rewards.value = r
            if (r.newBadges.isNotEmpty()) {
                _celebrate.value = r.newBadges.mapNotNull { code -> r.badges.firstOrNull { it.code == code } }
            }
        }
    }

    fun loadBoard(allTime: Boolean) {
        viewModelScope.launch {
            _board.value = try {
                library.leaderboard(allTime)
            } catch (e: ApiException) {
                _toast.value = describe(e)
                null
            }
        }
    }

    fun celebrated() {
        _celebrate.value = emptyList()
    }

    fun toastShown() {
        _toast.value = null
    }

    private fun text(@StringRes id: Int): String = getApplication<Application>().str(id)

    private fun describe(e: ApiException): String = describe(getApplication(), e)

    companion object {
        /** Friendly text for the server's error codes. */
        fun describe(context: Context, e: ApiException): String = when (e.code) {
            "offline" -> context.str(R.string.account_error_offline)
            "invalid_credentials" -> context.str(R.string.account_error_invalid_credentials)
            "email_not_verified" -> context.str(R.string.account_error_email_not_verified)
            "locked" -> context.str(R.string.account_error_locked)
            "empty_password" -> context.str(R.string.account_error_empty_password)
            "weak_password" -> context.str(R.string.account_error_weak_password)
            "email_taken" -> context.str(R.string.account_error_email_taken)
            "use_google" -> context.str(R.string.account_error_use_google)
            "nickname_taken" -> context.str(R.string.account_error_nickname_taken)
            "nickname_not_allowed" -> context.str(R.string.account_error_nickname_not_allowed)
            "token_reused", "invalid_refresh" -> context.str(R.string.account_error_signed_out)
            "sign_in_required" -> context.str(R.string.account_error_sign_in_required)
            else -> when {
                e.http == 429 -> context.str(R.string.account_error_rate_limited)
                e.http >= 500 -> context.str(R.string.account_error_server)
                else -> e.message
            }
        }
    }
}

/** After an upload, check for new beats this long later (the server's consensus job runs about 5 s after it), and again. */
private val REWARDS_AFTER_UPLOAD_MS = listOf(7_000L, 15_000L)
