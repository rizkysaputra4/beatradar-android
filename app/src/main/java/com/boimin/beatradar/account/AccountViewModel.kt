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

/** Sign-in sheet progress: the step it shows, what's busy, and the last error or info line. */
data class SignInState(
    val mode: SignInMode = SignInMode.CHOOSE,
    val busy: Boolean = false,
    val error: String? = null,
    val info: String? = null,
    /** The address the last code went to (as the server normalised it), for VERIFY_CODE and RESET_CODE. */
    val codeEmail: String? = null,
    /** The form a code step came from: "Use another email" goes back there. */
    val codeFrom: SignInMode = SignInMode.SIGN_IN,
    /** When the last code was sent from this phone, for the resend countdown (null: resend at once). */
    val codeSentAtMs: Long? = null,
    /** How many codes were refused: each refusal empties the code field once. */
    val codeRejections: Int = 0,
    /** Signed in: the sheet closes (and offers to save an email password). */
    val done: Boolean = false,
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

    /**
     * The password typed at sign-up or sign-in, kept in memory only while the code step shows: the server sets it when
     * the code confirms a new registration.
     */
    private var pendingPassword: String? = null

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
        pendingPassword = null
        _signIn.value = SignInState()
    }

    /** Moves the sheet to another form (Sign in, Create account, Forgot), clearing the last message. */
    fun setSignInMode(mode: SignInMode) {
        if (mode != SignInMode.VERIFY_CODE && mode != SignInMode.RESET_CODE) pendingPassword = null
        _signIn.value = _signIn.value.copy(mode = mode, busy = false, error = null, info = null)
    }

    /**
     * Settings' "Email not verified": the code step for the signed-in account's [email], with a fresh code. An older
     * account sends no password with its code.
     */
    fun startVerification(email: String) {
        afterSignIn = null
        pendingPassword = null
        _signIn.value = SignInState(mode = SignInMode.VERIFY_CODE, codeEmail = email, codeFrom = SignInMode.SIGN_IN)
        resendCode()
    }

    /**
     * Runs [block] with the sheet busy. An error lands in the state; a sign-in or sign-up of [email] refused because the
     * address isn't confirmed yet goes to the code step, with a fresh code.
     */
    private fun signInStep(email: String? = null, block: suspend () -> Unit) {
        val from = _signIn.value.mode
        _signIn.value = _signIn.value.copy(busy = true, error = null, info = null)
        viewModelScope.launch {
            _signIn.value = try {
                block()
                _signIn.value.copy(busy = false)
            } catch (e: ApiException) {
                if (email != null && SignInRules.needsCode(e.code)) {
                    runCatching { account.resendVerification(email) }
                    codeStep(email, from)
                } else {
                    failed(e)
                }
            }
        }
    }

    private fun codeStep(email: String, from: SignInMode, mode: SignInMode = SignInMode.VERIFY_CODE) = SignInState(
        mode = mode,
        codeEmail = email.trim(),
        codeFrom = from,
        codeSentAtMs = System.currentTimeMillis(),
    )

    private fun failed(e: ApiException): SignInState {
        val s = _signIn.value.copy(busy = false, error = describe(e), info = null)
        return when {
            // The server wants the password typed at sign-up with the code: back to the form to type it again.
            e.code == "password_required" -> s.copy(mode = SignInRules.backFromCode(s.codeFrom))
            SignInRules.clearsCode(e.code) -> s.copy(codeRejections = s.codeRejections + 1)
            else -> s
        }
    }

    private fun signedIn() {
        pendingPassword = null
        afterSignIn?.invoke()
        afterSignIn = null
        clearAccountData()
        loadRewards()
        _signIn.value = _signIn.value.copy(done = true)
    }

    fun login(email: String, password: String) {
        // Kept for the code step, should the server want the address confirmed first.
        pendingPassword = password
        signInStep(email) {
            account.login(email, password)
            signedIn()
        }
    }

    fun register(email: String, password: String) {
        pendingPassword = password
        signInStep(email) {
            val r = account.register(email, password)
            if (r.verificationRequired) {
                _signIn.value = codeStep(r.email ?: email, from = SignInMode.REGISTER)
            } else {
                signedIn()
            }
        }
    }

    /** Confirms the address with [code], then signs in. */
    fun verifyCode(code: String) {
        val email = _signIn.value.codeEmail ?: return
        signInStep {
            account.verifyCode(email, code, pendingPassword)
            signedIn()
        }
    }

    /** Sets the new password with [code], then signs in with it. */
    fun resetWithCode(code: String, password: String) {
        val email = _signIn.value.codeEmail ?: return
        signInStep {
            account.resetWithCode(email, code, password)
            try {
                account.login(email, password)
            } catch (e: ApiException) {
                // The password is set; only the sign-in after it failed. Sign in by hand.
                _signIn.value = SignInState(mode = SignInMode.SIGN_IN, info = text(R.string.account_password_changed), error = describe(e))
                return@signInStep
            }
            signedIn()
        }
    }

    /** "Resend": another code (and link) for the step shown. Ignored while the countdown runs. */
    fun resendCode() {
        val s = _signIn.value
        val email = s.codeEmail ?: return
        if (SignInRules.resendWaitSeconds(s.codeSentAtMs, System.currentTimeMillis()) > 0) return
        // Opened from Settings (no code sent from here yet), the step itself says a code is on its way.
        val tapped = s.codeSentAtMs != null
        _signIn.value = s.copy(codeSentAtMs = System.currentTimeMillis(), error = null, info = null)
        viewModelScope.launch {
            val sent = runCatching {
                if (s.mode == SignInMode.RESET_CODE) account.forgotPassword(email) else account.resendVerification(email)
            }
            val error = (sent.exceptionOrNull() as? ApiException)?.let(::describe)
            if (tapped || error != null) {
                _signIn.value = _signIn.value.copy(info = if (error == null) text(R.string.account_verification_resent) else null, error = error)
            }
        }
    }

    fun google(idToken: String) = signInStep {
        account.signInWithGoogle(idToken)
        signedIn()
    }

    fun googleFailed(message: String) {
        _signIn.value = _signIn.value.copy(busy = false, error = message, info = null)
    }

    /** Mails a reset code (and link) to [email], then shows the code step. */
    fun forgotPassword(email: String) = signInStep {
        account.forgotPassword(email)
        _signIn.value = codeStep(email, from = SignInMode.FORGOT, mode = SignInMode.RESET_CODE)
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
        /** Friendly text for the server's error codes ([SignInRules.errorText]), else the server's own message. */
        fun describe(context: Context, e: ApiException): String =
            SignInRules.errorText(e.code, e.http)?.let { context.str(it) } ?: e.message
    }
}

/** After an upload, check for new beats this long later (the server's consensus job runs about 5 s after it), and again. */
private val REWARDS_AFTER_UPLOAD_MS = listOf(7_000L, 15_000L)
