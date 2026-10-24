package com.boimin.beatradar.account

import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.boimin.beatradar.R
import com.boimin.beatradar.i18n.str
import com.boimin.beatradar.ui.theme.Tone
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val FieldShape = RoundedCornerShape(14.dp)

/**
 * *Continue with Google* / *Use email* (PLAN §8), and the 6-digit code steps: confirming a new address, and a password
 * reset. Every step is one card in the middle of the screen: a title (with a back arrow) and a line saying what happens
 * next, then the form.
 * The step lives in [AccountViewModel.signIn]; the sheet closes itself once signed in.
 */
@Composable
fun SignInSheet(vm: AccountViewModel, onDismiss: () -> Unit) {
    val state by vm.signIn.collectAsStateWithLifecycle()
    val user by vm.user.collectAsStateWithLifecycle()
    val mode = state.mode

    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var code by rememberSaveable { mutableStateOf("") }
    var newPassword by rememberSaveable { mutableStateOf("") }
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
    // A refused code is gone from the boxes, so the next one can be typed straight in.
    LaunchedEffect(state.codeRejections) { if (state.codeRejections > 0) code = "" }

    val sentTo = SignInRules.maskEmail(state.codeEmail ?: email)
    val emailReady = email.contains('@')
    val submitForm = {
        code = ""
        when (mode) {
            SignInMode.SIGN_IN -> if (emailReady && password.isNotEmpty()) vm.login(email, password)
            SignInMode.REGISTER -> if (emailReady && SignInRules.passwordOk(password)) vm.register(email, password)
            SignInMode.FORGOT -> if (emailReady) vm.forgotPassword(email)
            else -> Unit
        }
    }
    val submitReset = { if (SignInRules.codeComplete(code) && SignInRules.passwordOk(newPassword)) vm.resetWithCode(code, newPassword) }

    // A card in the middle of the screen (not a bottom sheet). It stays clear of the system bars and moves up with the
    // keyboard; a tap outside it closes it.
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(
            Modifier
                .fillMaxSize()
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(20.dp),
            contentAlignment = Alignment.Center,
        ) {
            // A dialog sits outside the app's Surface, so text would default to black: give it the theme's colour.
            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
                Column(
                    Modifier
                        .widthIn(max = 440.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(28.dp))
                        .background(Tone.sheet)
                        // Taps on the card itself don't fall through to the backdrop.
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                        .verticalScroll(rememberScrollState())
                        .animateContentSize()
                        .padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 24.dp),
                ) {
                    Header(
                        title = when (mode) {
                            SignInMode.CHOOSE, SignInMode.SIGN_IN -> str(R.string.account_sign_in)
                            SignInMode.REGISTER -> str(R.string.account_create_title)
                            SignInMode.FORGOT, SignInMode.RESET_CODE -> str(R.string.account_reset_title)
                            SignInMode.VERIFY_CODE -> str(R.string.account_verify_title)
                        },
                        body = when (mode) {
                            SignInMode.CHOOSE, SignInMode.REGISTER -> str(R.string.account_pitch)
                            SignInMode.SIGN_IN -> str(R.string.account_sign_in_text)
                            SignInMode.FORGOT -> str(R.string.account_forgot_text)
                            SignInMode.VERIFY_CODE -> str(R.string.account_code_sent_to, sentTo)
                            SignInMode.RESET_CODE -> str(R.string.account_reset_code_sent, sentTo)
                        },
                        onBack = SignInRules.back(mode, state.codeFrom, signedIn = user != null)?.let { to -> { vm.setSignInMode(to) } },
                    )

                    when (mode) {
                        SignInMode.CHOOSE -> {
                            OutlinedButton(
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
                                shape = FieldShape,
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                modifier = Modifier.fillMaxWidth().height(52.dp),
                            ) {
                                if (state.busy) {
                                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(painterResource(R.drawable.ic_google), contentDescription = null, tint = Color.Unspecified, modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.width(12.dp))
                                    Text(str(R.string.account_continue_google), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                                }
                            }
                            Spacer(Modifier.height(10.dp))
                            Button(
                                onClick = { vm.setSignInMode(SignInMode.SIGN_IN) },
                                enabled = !state.busy,
                                shape = FieldShape,
                                colors = ButtonDefaults.buttonColors(containerColor = Tone.pill, contentColor = MaterialTheme.colorScheme.onSurface),
                                modifier = Modifier.fillMaxWidth().height(52.dp),
                            ) {
                                Icon(painterResource(R.drawable.ic_mail), contentDescription = null, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(12.dp))
                                Text(str(R.string.account_use_email), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }

                        SignInMode.SIGN_IN, SignInMode.REGISTER -> {
                            ModeTabs(register = mode == SignInMode.REGISTER, enabled = !state.busy) { register ->
                                vm.setSignInMode(if (register) SignInMode.REGISTER else SignInMode.SIGN_IN)
                            }
                            Spacer(Modifier.height(16.dp))
                            EmailField(email, imeAction = ImeAction.Next) { email = it }
                            Spacer(Modifier.height(10.dp))
                            PasswordField(
                                password,
                                str(R.string.account_password),
                                if (mode == SignInMode.REGISTER) ContentType.NewPassword else ContentType.Password,
                                onDone = submitForm,
                            ) { password = it }
                            if (mode == SignInMode.REGISTER) {
                                PasswordRule(SignInRules.passwordOk(password))
                            } else {
                                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                                    TextButton(onClick = { vm.setSignInMode(SignInMode.FORGOT) }) { Text(str(R.string.account_forgot)) }
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            PrimaryButton(
                                text = str(if (mode == SignInMode.REGISTER) R.string.account_create else R.string.account_sign_in),
                                enabled = emailReady && if (mode == SignInMode.REGISTER) SignInRules.passwordOk(password) else password.isNotEmpty(),
                                busy = state.busy,
                                onClick = submitForm,
                            )
                        }

                        SignInMode.FORGOT -> {
                            EmailField(email, imeAction = ImeAction.Send, onDone = submitForm) { email = it }
                            Spacer(Modifier.height(16.dp))
                            PrimaryButton(str(R.string.account_send_reset), enabled = emailReady, busy = state.busy, onClick = submitForm)
                        }

                        SignInMode.VERIFY_CODE -> {
                            CodeBoxes(code, focusOn = mode, enabled = !state.busy) { typed ->
                                code = typed
                                // The 6th digit sends it: nothing else to tap.
                                if (SignInRules.codeComplete(typed) && !state.busy) vm.verifyCode(typed)
                            }
                            Spacer(Modifier.height(16.dp))
                            PrimaryButton(str(R.string.account_verify), enabled = SignInRules.codeComplete(code), busy = state.busy, onClick = { vm.verifyCode(code) })
                            Spacer(Modifier.height(4.dp))
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
                            Text(str(R.string.account_code_link_hint), fontSize = 12.sp, lineHeight = 17.sp, color = Tone.text3, modifier = Modifier.padding(horizontal = 4.dp))
                        }

                        SignInMode.RESET_CODE -> {
                            CodeBoxes(code, focusOn = mode, enabled = !state.busy) { code = it }
                            Spacer(Modifier.height(16.dp))
                            PasswordField(newPassword, str(R.string.account_new_password), ContentType.NewPassword, onDone = submitReset) { newPassword = it }
                            PasswordRule(SignInRules.passwordOk(newPassword))
                            Spacer(Modifier.height(8.dp))
                            PrimaryButton(
                                str(R.string.account_set_password),
                                enabled = SignInRules.codeComplete(code) && SignInRules.passwordOk(newPassword),
                                busy = state.busy,
                                onClick = submitReset,
                            )
                            Spacer(Modifier.height(4.dp))
                            ResendButton(state.codeSentAtMs, onResend = vm::resendCode)
                        }
                    }

                    state.error?.let { Notice(it, error = true) }
                    state.info?.let { Notice(it, error = false) }
                }
            }
        }
    }
}

/** Title (with a back arrow when there's a step to go back to) and what happens next. */
@Composable
private fun Header(title: String, body: String, onBack: (() -> Unit)?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) {
            IconButton(onClick = onBack, modifier = Modifier.offset(x = (-12).dp)) {
                Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = str(R.string.account_back), tint = Tone.text2)
            }
        }
        Text(
            title,
            fontSize = 22.sp,
            lineHeight = 28.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.offset(x = if (onBack != null) (-8).dp else 0.dp).semantics { heading() },
        )
    }
    Spacer(Modifier.height(6.dp))
    Text(body, fontSize = 14.sp, lineHeight = 20.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(20.dp))
}

/** "Sign in | Create account": two halves of one pill, the chosen one raised. */
@Composable
private fun ModeTabs(register: Boolean, enabled: Boolean, onPick: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(44.dp).clip(FieldShape).background(Tone.track).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        listOf(false to R.string.account_sign_in, true to R.string.account_create).forEach { (isRegister, label) ->
            val chosen = isRegister == register
            val bg by animateColorAsState(if (chosen) Tone.chip else Color.Transparent, label = "tab")
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .height(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(bg)
                    .clickable(enabled = enabled && !chosen) { onPick(isRegister) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    str(label),
                    fontSize = 14.sp,
                    fontWeight = if (chosen) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (chosen) MaterialTheme.colorScheme.onSurface else Tone.text3,
                )
            }
        }
    }
}

@Composable
private fun EmailField(value: String, imeAction: ImeAction, onDone: () -> Unit = {}, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.trim()) },
        label = { Text(str(R.string.account_email)) },
        leadingIcon = { Icon(painterResource(R.drawable.ic_mail), contentDescription = null, modifier = Modifier.size(20.dp)) },
        singleLine = true,
        shape = FieldShape,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = imeAction),
        keyboardActions = KeyboardActions(onSend = { onDone() }),
        // Lets the password manager fill a saved login.
        modifier = Modifier.fillMaxWidth().semantics { contentType = ContentType.Username + ContentType.EmailAddress },
    )
}

/** A password with an eye to show it, so nobody needs to type it twice. "Done" on the keyboard sends the form. */
@Composable
private fun PasswordField(value: String, label: String, type: ContentType, onDone: () -> Unit, onChange: (String) -> Unit) {
    var shown by rememberSaveable { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        leadingIcon = { Icon(painterResource(R.drawable.ic_lock), contentDescription = null, modifier = Modifier.size(20.dp)) },
        trailingIcon = {
            IconButton(onClick = { shown = !shown }) {
                Icon(
                    painterResource(if (shown) R.drawable.ic_visibility_off else R.drawable.ic_visibility),
                    contentDescription = str(if (shown) R.string.account_hide_password else R.string.account_show_password),
                    modifier = Modifier.size(20.dp),
                )
            }
        },
        singleLine = true,
        shape = FieldShape,
        visualTransformation = if (shown) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        modifier = Modifier.fillMaxWidth().semantics { contentType = type },
    )
}

/** The one password rule, ticked once it's met. */
@Composable
private fun PasswordRule(ok: Boolean) {
    val tint by animateColorAsState(if (ok) MaterialTheme.colorScheme.primary else Tone.text3, label = "rule")
    Row(Modifier.padding(start = 4.dp, top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(painterResource(R.drawable.ic_check_circle), contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(str(R.string.account_password_hint), fontSize = 13.sp, color = tint)
    }
}

/** The step's main button; shows a spinner in place of its text while the server answers. */
@Composable
private fun PrimaryButton(text: String, enabled: Boolean, busy: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled && !busy,
        shape = FieldShape,
        modifier = Modifier.fillMaxWidth().height(52.dp),
    ) {
        if (busy) {
            CircularProgressIndicator(Modifier.size(20.dp), color = MaterialTheme.colorScheme.primary, strokeWidth = 2.dp)
        } else {
            Text(text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

/**
 * The 6-digit code as six boxes: digits only (a pasted "482 913" works), filled by SMS/email code autofill. The
 * keyboard comes up as soon as the step shows; the box the next digit goes into is outlined.
 */
@Composable
private fun CodeBoxes(code: String, focusOn: SignInMode, enabled: Boolean, onChange: (String) -> Unit) {
    val focus = remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(focusOn) { runCatching { focus.requestFocus() } }
    val label = str(R.string.account_code)
    BasicTextField(
        // The cursor always sits after the last digit, so typing and deleting work from the end.
        value = TextFieldValue(code, TextRange(code.length)),
        onValueChange = { onChange(SignInRules.cleanCode(it.text)) },
        enabled = enabled,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focus)
            .onFocusChanged { focused = it.isFocused }
            .semantics {
                contentType = ContentType.SmsOtpCode
                contentDescription = label
            },
        decorationBox = { _ ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(SignInRules.CODE_LENGTH) { i ->
                    val digit = code.getOrNull(i)
                    val next = focused && i == minOf(code.length, SignInRules.CODE_LENGTH - 1)
                    val shape = RoundedCornerShape(12.dp)
                    val line by animateColorAsState(
                        when {
                            next -> MaterialTheme.colorScheme.primary
                            digit != null -> Tone.chipLine
                            else -> Color.Transparent
                        },
                        label = "codeBox",
                    )
                    Box(
                        Modifier
                            .weight(1f)
                            .height(56.dp)
                            .clip(shape)
                            .background(if (digit != null) Tone.selected else Tone.track)
                            .border(if (next) 2.dp else 1.dp, line, shape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(digit?.toString() ?: "", fontSize = 24.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        },
    )
}

/** A server answer under the form: errors on a red card, good news on a violet one. */
@Composable
private fun Notice(text: String, error: Boolean) {
    Spacer(Modifier.height(16.dp))
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (error) MaterialTheme.colorScheme.errorContainer else Tone.selected)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val tint = if (error) MaterialTheme.colorScheme.onErrorContainer else Tone.primarySoft
        Icon(painterResource(if (error) R.drawable.ic_error else R.drawable.ic_check_circle), contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, fontSize = 14.sp, lineHeight = 19.sp, color = tint)
    }
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
