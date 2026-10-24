package com.boimin.beatradar.account

import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.boimin.beatradar.R
import com.boimin.beatradar.i18n.str
import com.boimin.beatradar.ui.CalmBottomSheet
import com.boimin.beatradar.ui.rememberCalmSheetState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * *Continue with Google* / *Use email* (PLAN §8), and the 6-digit code steps: confirming a new address, and a password
 * reset. The step lives in [AccountViewModel.signIn]; the sheet closes itself once signed in.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignInSheet(vm: AccountViewModel, onDismiss: () -> Unit) {
    val state by vm.signIn.collectAsStateWithLifecycle()
    val user by vm.user.collectAsStateWithLifecycle()
    val mode = state.mode

    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var code by rememberSaveable { mutableStateOf("") }
    var newPassword by rememberSaveable { mutableStateOf("") }
    var confirmPassword by rememberSaveable { mutableStateOf("") }
    val activity = LocalActivity.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(state.done) {
        if (!state.done) return@LaunchedEffect
        // Signed in with email: offer to save it to the password manager. Runs in the Activity's scope, since this
        // sheet closes right away. A code step opened from Settings has no password to save.
        val login = when (mode) {
            SignInMode.SIGN_IN, SignInMode.REGISTER -> email to password
            SignInMode.VERIFY_CODE -> (state.codeEmail ?: email) to password
            SignInMode.RESET_CODE -> (state.codeEmail ?: email) to newPassword
            else -> null
        }
        if (login != null && login.second.isNotEmpty() && activity is ComponentActivity) {
            val (e, p) = login
            activity.lifecycleScope.launch { PasswordSaver.offer(activity, e, p) }
        }
        onDismiss()
    }
    // A refused code is gone from the field, so the next one can be typed straight in.
    LaunchedEffect(state.codeRejections) { if (state.codeRejections > 0) code = "" }

    CalmBottomSheet(onDismissRequest = onDismiss, sheetState = rememberCalmSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.imePadding().verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                when (mode) {
                    SignInMode.CHOOSE, SignInMode.SIGN_IN -> str(R.string.account_sign_in)
                    SignInMode.REGISTER -> str(R.string.account_create_title)
                    SignInMode.FORGOT, SignInMode.RESET_CODE -> str(R.string.account_reset_title)
                    SignInMode.VERIFY_CODE -> str(R.string.account_verify_title)
                },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            when (mode) {
                SignInMode.CHOOSE -> {
                    Text(
                        str(R.string.account_pitch),
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
                    ) { Text(str(R.string.account_continue_google)) }
                    OutlinedButton(onClick = { vm.setSignInMode(SignInMode.SIGN_IN) }, modifier = Modifier.fillMaxWidth()) {
                        Text(str(R.string.account_use_email))
                    }
                }
                SignInMode.VERIFY_CODE -> {
                    val sentTo = SignInRules.maskEmail(state.codeEmail ?: email)
                    Text(str(R.string.account_code_sent_to, sentTo), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    CodeField(code, focusOn = mode) { typed ->
                        code = typed
                        // The 6th digit sends it: nothing else to tap.
                        if (SignInRules.codeComplete(typed) && !state.busy) vm.verifyCode(typed)
                    }
                    Button(
                        onClick = { vm.verifyCode(code) },
                        enabled = SignInRules.codeComplete(code) && !state.busy,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(str(R.string.account_verify)) }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ResendButton(state.codeSentAtMs, onResend = vm::resendCode)
                        Spacer(Modifier.weight(1f))
                        // Signed in already (Settings' "Email not verified"): the address is the account's, not a choice.
                        if (user == null) {
                            TextButton(onClick = { vm.setSignInMode(SignInRules.backFromCode(state.codeFrom)) }) {
                                Text(str(R.string.account_use_another_email))
                            }
                        }
                    }
                    Text(str(R.string.account_code_link_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                SignInMode.RESET_CODE -> {
                    val sentTo = SignInRules.maskEmail(state.codeEmail ?: email)
                    Text(str(R.string.account_reset_code_sent, sentTo), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    CodeField(code, focusOn = mode) { code = it }
                    PasswordField(newPassword, str(R.string.account_new_password), ContentType.NewPassword, supporting = str(R.string.account_password_hint)) {
                        newPassword = it
                    }
                    val differs = confirmPassword.isNotEmpty() && confirmPassword != newPassword
                    PasswordField(
                        confirmPassword,
                        str(R.string.account_confirm_password),
                        ContentType.NewPassword,
                        supporting = if (differs) str(R.string.account_passwords_differ) else null,
                        isError = differs,
                    ) { confirmPassword = it }
                    val ready = SignInRules.codeComplete(code) && newPassword.length >= SignInRules.MIN_PASSWORD && newPassword == confirmPassword
                    Button(
                        onClick = { vm.resetWithCode(code, newPassword) },
                        enabled = ready && !state.busy,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(str(R.string.account_set_password)) }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ResendButton(state.codeSentAtMs, onResend = vm::resendCode)
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { vm.setSignInMode(SignInMode.SIGN_IN) }) { Text(str(R.string.account_back_to_sign_in)) }
                    }
                }
                else -> {
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text(str(R.string.account_email)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        // Lets the password manager fill a saved login.
                        modifier = Modifier.fillMaxWidth().semantics { contentType = ContentType.Username + ContentType.EmailAddress },
                    )
                    if (mode != SignInMode.FORGOT) {
                        PasswordField(
                            password,
                            str(R.string.account_password),
                            if (mode == SignInMode.REGISTER) ContentType.NewPassword else ContentType.Password,
                            supporting = if (mode == SignInMode.REGISTER) str(R.string.account_password_hint) else null,
                        ) { password = it }
                    }
                    val ready = email.contains('@') && when (mode) {
                        SignInMode.FORGOT -> true
                        SignInMode.REGISTER -> password.length >= SignInRules.MIN_PASSWORD
                        else -> password.isNotEmpty()
                    }
                    Button(
                        onClick = {
                            code = ""
                            when (mode) {
                                SignInMode.SIGN_IN -> vm.login(email, password)
                                SignInMode.REGISTER -> vm.register(email, password)
                                SignInMode.FORGOT -> vm.forgotPassword(email)
                                else -> Unit
                            }
                        },
                        enabled = ready && !state.busy,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            when (mode) {
                                SignInMode.REGISTER -> str(R.string.account_create)
                                SignInMode.FORGOT -> str(R.string.account_send_reset)
                                else -> str(R.string.account_sign_in)
                            },
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        when (mode) {
                            SignInMode.SIGN_IN -> {
                                TextButton(onClick = { vm.setSignInMode(SignInMode.REGISTER) }) { Text(str(R.string.account_create)) }
                                Spacer(Modifier.weight(1f))
                                TextButton(onClick = { vm.setSignInMode(SignInMode.FORGOT) }) { Text(str(R.string.account_forgot)) }
                            }
                            else -> TextButton(onClick = { vm.setSignInMode(SignInMode.SIGN_IN) }) { Text(str(R.string.account_back_to_sign_in)) }
                        }
                    }
                }
            }
            if (state.busy) CircularProgressIndicator(Modifier.size(24.dp).align(Alignment.CenterHorizontally))
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
            state.info?.let { Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium) }
            Spacer(Modifier.height(4.dp))
        }
    }
}

/** The 6-digit code: digits only (a pasted "482 913" works), spaced out, filled from an SMS/email code by autofill. */
@Composable
private fun CodeField(code: String, focusOn: SignInMode, onChange: (String) -> Unit) {
    val focus = remember { FocusRequester() }
    // The keyboard comes up for the code as soon as its step shows.
    LaunchedEffect(focusOn) { runCatching { focus.requestFocus() } }
    OutlinedTextField(
        value = code,
        onValueChange = { onChange(SignInRules.cleanCode(it)) },
        label = { Text(str(R.string.account_code)) },
        singleLine = true,
        textStyle = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 6.sp, textAlign = TextAlign.Center),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        modifier = Modifier.fillMaxWidth().focusRequester(focus).semantics { contentType = ContentType.SmsOtpCode },
    )
}

@Composable
private fun PasswordField(
    value: String,
    label: String,
    type: ContentType,
    supporting: String? = null,
    isError: Boolean = false,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        isError = isError,
        supportingText = supporting?.let { { Text(it) } },
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth().semantics { contentType = type },
    )
}

/** "Resend code", or "Resend in 0:42" while the server would still ignore it. */
@Composable
private fun ResendButton(sentAtMs: Long?, onResend: () -> Unit) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(sentAtMs) {
        while (true) {
            now = System.currentTimeMillis()
            if (SignInRules.resendWaitSeconds(sentAtMs, now) == 0) break
            delay(1_000)
        }
    }
    val wait = SignInRules.resendWaitSeconds(sentAtMs, now)
    TextButton(onClick = onResend, enabled = wait == 0) {
        Text(if (wait == 0) str(R.string.account_resend_code) else str(R.string.account_resend_in, SignInRules.clock(wait)))
    }
}
