package com.boimin.beatradar.account

import android.app.Activity
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.boimin.beatradar.BuildConfig
import com.boimin.beatradar.R
import com.boimin.beatradar.i18n.str
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential

/** "Continue with Google" through Credential Manager. The ID token goes to `POST /v1/auth/google`. */
object GoogleAuth {
    sealed interface Result {
        data class Token(val idToken: String) : Result
        data object Cancelled : Result
        data class Failed(val message: String) : Result
    }

    val configured: Boolean get() = !BuildConfig.GOOGLE_WEB_CLIENT_ID.startsWith("REPLACE_ME")

    suspend fun signIn(activity: Activity): Result {
        if (!configured) return Result.Failed(activity.str(R.string.account_google_not_configured))
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(GetSignInWithGoogleOption.Builder(BuildConfig.GOOGLE_WEB_CLIENT_ID).build())
            .build()
        return try {
            val credential = CredentialManager.create(activity).getCredential(activity, request).credential
            if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                Result.Token(GoogleIdTokenCredential.createFrom(credential.data).idToken)
            } else {
                Result.Failed(activity.str(R.string.account_google_no_account))
            }
        } catch (_: GetCredentialCancellationException) {
            Result.Cancelled
        } catch (e: GetCredentialException) {
            Result.Failed(e.message ?: activity.str(R.string.account_google_failed))
        }
    }
}
