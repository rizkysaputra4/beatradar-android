package com.boimin.beatradar.account

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.boimin.beatradar.app
import com.boimin.beatradar.data.ClipStore
import com.boimin.beatradar.net.ApiException
import com.boimin.beatradar.net.BoardView
import com.boimin.beatradar.net.Me
import com.boimin.beatradar.net.Rewards
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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

    private val _rewards = MutableStateFlow<Rewards?>(null)
    val rewards: StateFlow<Rewards?> = _rewards

    private val _board = MutableStateFlow<BoardView?>(null)
    val board: StateFlow<BoardView?> = _board

    private val _celebrate = MutableStateFlow<List<String>>(emptyList())
    /** Names of badges earned since the last look, to celebrate once. */
    val celebrate: StateFlow<List<String>> = _celebrate

    private val _toast = MutableStateFlow<String?>(null)
    val toast: StateFlow<String?> = _toast

    /** Runs once the user is signed in (e.g. a love tapped as a guest). */
    private var afterSignIn: (() -> Unit)? = null

    init {
        viewModelScope.launch { account.signedOut.collect { reason -> if (reason == SignOutReason.SESSION_LOST) _toast.value = "You were signed out. Sign in again to keep backing up your log." } }
        viewModelScope.launch { account.refreshMe() }
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
            _signIn.value = SignInState(info = "Sent again. Check your inbox (and spam).")
        }
    }

    fun forgotPassword(email: String) {
        viewModelScope.launch {
            _signIn.value = try {
                account.forgotPassword(email)
                SignInState(info = "If that address has an account, a reset link is on its way.")
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
            loadRewards()
        }
    }

    fun deleteAccount() {
        viewModelScope.launch {
            try {
                account.deleteAccount()
                _toast.value = "Your account was deleted"
                loadRewards()
            } catch (e: ApiException) {
                _toast.value = describe(e)
            }
        }
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
        viewModelScope.launch {
            val r = runCatching { library.rewards() }.getOrNull() ?: return@launch
            _rewards.value = r
            if (r.newBadges.isNotEmpty()) {
                _celebrate.value = r.newBadges.map { code -> r.badges.firstOrNull { it.code == code }?.name ?: code }
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

    companion object {
        /** Friendly text for the server's error codes. */
        fun describe(e: ApiException): String = when (e.code) {
            "offline" -> "You're offline"
            "invalid_credentials" -> "Wrong email or password"
            "email_not_verified" -> "Verify your email first: open the link we sent you."
            "locked" -> "Too many attempts. Try again in a few minutes."
            "empty_password" -> "Enter a password."
            "weak_password" -> "That password is too long."
            "email_taken" -> "There's already an account with this email. Sign in instead."
            "use_google" -> "This email signs in with Google."
            "nickname_taken" -> "That nickname is taken"
            "nickname_not_allowed" -> "That nickname isn't allowed"
            "token_reused", "invalid_refresh" -> "You were signed out. Please sign in again."
            "sign_in_required" -> "Sign in first"
            else -> when {
                e.http == 429 -> "Too many tries. Wait a minute and try again."
                e.http >= 500 -> "The server is having trouble. Try again later."
                else -> e.message
            }
        }
    }
}
