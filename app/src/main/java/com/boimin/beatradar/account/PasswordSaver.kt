package com.boimin.beatradar.account

import android.app.Activity
import android.util.Log
import androidx.credentials.CreatePasswordRequest
import androidx.credentials.CredentialManager
import androidx.credentials.exceptions.CreateCredentialCancellationException
import androidx.credentials.exceptions.CreateCredentialException

/**
 * Offers to save an email sign-in to the user's password manager (Google Password Manager, or whichever they chose).
 * Compose sheets never "submit" a form the way Android's autofill expects, so without this nothing is offered.
 */
object PasswordSaver {
    private const val TAG = "PasswordSaver"

    /** Shows the system's "Save password?" prompt. Declining, or no password manager, is fine: nothing happens. */
    suspend fun offer(activity: Activity, email: String, password: String) {
        if (email.isBlank() || password.isEmpty()) return
        try {
            CredentialManager.create(activity).createCredential(activity, CreatePasswordRequest(email.trim(), password))
        } catch (_: CreateCredentialCancellationException) {
            // The user said no.
        } catch (e: CreateCredentialException) {
            Log.i(TAG, "password not saved: ${e.type} ${e.message}")
        }
    }
}
