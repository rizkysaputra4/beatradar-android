package com.boimin.beatradar.account

import androidx.annotation.StringRes
import com.boimin.beatradar.R
import java.util.Locale

/** What the sign-in sheet shows. */
enum class SignInMode {
    CHOOSE,
    SIGN_IN,
    REGISTER,
    FORGOT,
    /** The 6-digit code that confirms the address (after sign-up, or a sign-in refused as unverified). */
    VERIFY_CODE,
    /** The code from the reset email, with the new password. */
    RESET_CODE,
}

/** The sign-in sheet's pure rules: codes, the resend wait, where an answer leads, and the error texts. */
object SignInRules {
    /** Codes are 6 digits, valid 15 minutes. */
    const val CODE_LENGTH = 6

    /** The server ignores a resend within a minute of the last one, so the button waits as long. */
    const val RESEND_WAIT_MS = 60_000L

    /** The server's minimum for a new password (`weak_password` below it). */
    const val MIN_PASSWORD = 8

    /** What a code field keeps of typed or pasted text: digits only ("482 913" → "482913"), at most [CODE_LENGTH]. */
    fun cleanCode(input: String): String = input.filter { it in '0'..'9' }.take(CODE_LENGTH)

    fun codeComplete(code: String): Boolean = code.length == CODE_LENGTH && code.all { it in '0'..'9' }

    /** Seconds until "Resend" may be tapped again; 0 when it may now (or no code was sent from here). */
    fun resendWaitSeconds(sentAtMs: Long?, nowMs: Long): Int {
        if (sentAtMs == null) return 0
        val left = sentAtMs + RESEND_WAIT_MS - nowMs
        return if (left <= 0) 0 else ((left + 999) / 1_000).toInt()
    }

    /** "0:42" for the resend countdown. */
    fun clock(seconds: Int): String = String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60)

    /**
     * "r***@gmail.com": enough to recognise the address on a shared screen. The first letter stays; a one-letter name
     * is just starred.
     */
    fun maskEmail(email: String): String {
        val at = email.lastIndexOf('@')
        if (at <= 0) return email
        val name = email.substring(0, at)
        val shown = if (name.length > 1) name.take(1) else ""
        return "$shown***${email.substring(at)}"
    }

    /** The server refused a sign-in or sign-up because the address must be confirmed first: ask for the code. */
    fun needsCode(errorCode: String): Boolean = errorCode == "email_not_verified" || errorCode == "email_pending"

    /** A wrong code isn't worth keeping in the field; the next try starts empty. */
    fun clearsCode(errorCode: String): Boolean = errorCode == "invalid_code" || errorCode == "code_exhausted"

    /** "Use another email" (or a refused password) goes back to the form the code came from. */
    fun backFromCode(from: SignInMode): SignInMode = when (from) {
        SignInMode.REGISTER -> SignInMode.REGISTER
        SignInMode.FORGOT, SignInMode.RESET_CODE -> SignInMode.FORGOT
        else -> SignInMode.SIGN_IN
    }

    /** The text for a server error code, or null when the server's own message is the best there is. */
    @StringRes
    fun errorText(code: String, http: Int): Int? = when (code) {
        "offline" -> R.string.account_error_offline
        "invalid_credentials" -> R.string.account_error_invalid_credentials
        "email_not_verified" -> R.string.account_error_email_not_verified
        "email_pending" -> R.string.account_error_email_pending
        "locked" -> R.string.account_error_locked
        "empty_password" -> R.string.account_error_empty_password
        "weak_password" -> R.string.account_error_weak_password
        "breached_password" -> R.string.account_error_breached_password
        "password_required" -> R.string.account_error_password_required
        "invalid_email" -> R.string.account_error_invalid_email
        "email_taken" -> R.string.account_error_email_taken
        "use_google" -> R.string.account_error_use_google
        "invalid_code" -> R.string.account_error_invalid_code
        "code_exhausted" -> R.string.account_error_code_exhausted
        "invalid_token" -> R.string.account_error_invalid_token
        "nickname_taken" -> R.string.account_error_nickname_taken
        "nickname_not_allowed" -> R.string.account_error_nickname_not_allowed
        "token_reused", "invalid_refresh" -> R.string.account_error_signed_out
        "sign_in_required" -> R.string.account_error_sign_in_required
        else -> when {
            http == 429 -> R.string.account_error_rate_limited
            http >= 500 -> R.string.account_error_server
            else -> null
        }
    }
}
