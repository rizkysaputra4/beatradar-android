package com.boimin.beatradar.account

import android.app.Activity
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

private enum class Mode { CHOOSE, SIGN_IN, REGISTER, FORGOT }

/** *Continue with Google* / *Use email* (PLAN §8). Closes itself once signed in. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignInSheet(vm: AccountViewModel, onDismiss: () -> Unit) {
    val state by vm.signIn.collectAsStateWithLifecycle()
    val user by vm.user.collectAsStateWithLifecycle()

    var mode by rememberSaveable { mutableStateOf(Mode.CHOOSE) }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    val activity = LocalContext.current as? Activity
    val scope = rememberCoroutineScope()
    LaunchedEffect(user) {
        if (user == null) return@LaunchedEffect
        // Signed in with email: offer to save it to the password manager. Runs in the Activity's scope, since this
        // sheet closes right away.
        if ((mode == Mode.SIGN_IN || mode == Mode.REGISTER) && activity is ComponentActivity) {
            val (e, p) = email to password
            activity.lifecycleScope.launch { PasswordSaver.offer(activity, e, p) }
        }
        onDismiss()
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(start = 24.dp, end = 24.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                when (mode) {
                    Mode.CHOOSE, Mode.SIGN_IN -> "Sign in"
                    Mode.REGISTER -> "Create an account"
                    Mode.FORGOT -> "Reset your password"
                },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            if (mode == Mode.CHOOSE) {
                Text(
                    "Love songs, back up your log to every phone, and join the leaderboards. Counting BPM works without an account.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    onClick = {
                        if (activity != null) scope.launch {
                            when (val r = GoogleAuth.signIn(activity)) {
                                is GoogleAuth.Result.Token -> vm.google(r.idToken)
                                is GoogleAuth.Result.Failed -> vm.googleFailed(r.message)
                                GoogleAuth.Result.Cancelled -> Unit
                            }
                        }
                    },
                    enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Continue with Google") }
                OutlinedButton(onClick = { mode = Mode.SIGN_IN }, modifier = Modifier.fillMaxWidth()) { Text("Use email") }
            } else {
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    // Lets the password manager fill a saved login.
                    modifier = Modifier.fillMaxWidth().semantics { contentType = ContentType.Username + ContentType.EmailAddress },
                )
                if (mode != Mode.FORGOT) {
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Password") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth().semantics {
                            contentType = if (mode == Mode.REGISTER) ContentType.NewPassword else ContentType.Password
                        },
                    )
                }
                val ready = email.contains('@') && (mode == Mode.FORGOT || password.isNotEmpty())
                Button(
                    onClick = {
                        when (mode) {
                            Mode.SIGN_IN -> vm.login(email, password)
                            Mode.REGISTER -> vm.register(email, password)
                            Mode.FORGOT -> vm.forgotPassword(email)
                            Mode.CHOOSE -> Unit
                        }
                    },
                    enabled = ready && !state.busy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        when (mode) {
                            Mode.REGISTER -> "Create account"
                            Mode.FORGOT -> "Send reset link"
                            else -> "Sign in"
                        },
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    when (mode) {
                        Mode.SIGN_IN -> {
                            TextButton(onClick = { mode = Mode.REGISTER }) { Text("Create account") }
                            Spacer(Modifier.weight(1f))
                            TextButton(onClick = { mode = Mode.FORGOT }) { Text("Forgot password?") }
                        }
                        else -> TextButton(onClick = { mode = Mode.SIGN_IN }) { Text("Back to sign in") }
                    }
                }
            }
            if (state.busy) CircularProgressIndicator(Modifier.size(24.dp).align(Alignment.CenterHorizontally))
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
            state.info?.let { Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium) }
            if (state.canResend) TextButton(onClick = { vm.resendVerification(email) }) { Text("Resend verification email") }
            Spacer(Modifier.height(4.dp))
        }
    }
}
