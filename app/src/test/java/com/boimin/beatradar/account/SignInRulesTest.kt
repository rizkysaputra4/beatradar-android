package com.boimin.beatradar.account

import com.boimin.beatradar.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SignInRulesTest {

    @Test
    fun codeKeepsSixDigits() {
        assertEquals("482913", SignInRules.cleanCode("482 913"))
        assertEquals("482913", SignInRules.cleanCode("482-913-77"))
        assertEquals("12", SignInRules.cleanCode("a1b2"))
        assertTrue(SignInRules.codeComplete("482913"))
        assertFalse(SignInRules.codeComplete("48291"))
        assertFalse(SignInRules.codeComplete("48291x"))
    }

    @Test
    fun resendWaitsAMinute() {
        val sent = 1_000_000L
        assertEquals(0, SignInRules.resendWaitSeconds(null, sent))
        assertEquals(60, SignInRules.resendWaitSeconds(sent, sent))
        assertEquals(60, SignInRules.resendWaitSeconds(sent, sent + 1))
        assertEquals(1, SignInRules.resendWaitSeconds(sent, sent + 59_999))
        assertEquals(0, SignInRules.resendWaitSeconds(sent, sent + 60_000))
        assertEquals(0, SignInRules.resendWaitSeconds(sent, sent + 90_000))
        assertEquals("1:00", SignInRules.clock(60))
        assertEquals("0:07", SignInRules.clock(7))
    }

    @Test
    fun emailIsMasked() {
        assertEquals("r***@gmail.com", SignInRules.maskEmail("rizky@gmail.com"))
        assertEquals("***@x.io", SignInRules.maskEmail("a@x.io"))
        assertEquals("not-an-email", SignInRules.maskEmail("not-an-email"))
    }

    @Test
    fun unconfirmedAddressGoesToTheCode() {
        assertTrue(SignInRules.needsCode("email_not_verified"))
        assertTrue(SignInRules.needsCode("email_pending"))
        assertFalse(SignInRules.needsCode("invalid_credentials"))
        assertTrue(SignInRules.clearsCode("invalid_code"))
        assertTrue(SignInRules.clearsCode("code_exhausted"))
        assertFalse(SignInRules.clearsCode("offline"))
    }

    @Test
    fun anotherEmailGoesBackToItsForm() {
        assertEquals(SignInMode.REGISTER, SignInRules.backFromCode(SignInMode.REGISTER))
        assertEquals(SignInMode.SIGN_IN, SignInRules.backFromCode(SignInMode.SIGN_IN))
        assertEquals(SignInMode.SIGN_IN, SignInRules.backFromCode(SignInMode.CHOOSE))
        assertEquals(SignInMode.FORGOT, SignInRules.backFromCode(SignInMode.FORGOT))
    }

    @Test
    fun passwordNeedsSixCharacters() {
        assertFalse(SignInRules.passwordOk(""))
        assertFalse(SignInRules.passwordOk("abcde"))
        assertTrue(SignInRules.passwordOk("abcdef"))
        assertTrue(SignInRules.passwordOk("123456"))
    }

    @Test
    fun backArrowGoesOneStepBack() {
        assertNull(SignInRules.back(SignInMode.CHOOSE, SignInMode.SIGN_IN, signedIn = false))
        assertEquals(SignInMode.CHOOSE, SignInRules.back(SignInMode.SIGN_IN, SignInMode.SIGN_IN, signedIn = false))
        assertEquals(SignInMode.CHOOSE, SignInRules.back(SignInMode.REGISTER, SignInMode.SIGN_IN, signedIn = false))
        assertEquals(SignInMode.SIGN_IN, SignInRules.back(SignInMode.FORGOT, SignInMode.SIGN_IN, signedIn = false))
        assertEquals(SignInMode.REGISTER, SignInRules.back(SignInMode.VERIFY_CODE, SignInMode.REGISTER, signedIn = false))
        assertEquals(SignInMode.FORGOT, SignInRules.back(SignInMode.RESET_CODE, SignInMode.FORGOT, signedIn = false))
        // Confirming the account's own address from Settings: nowhere to go back to.
        assertNull(SignInRules.back(SignInMode.VERIFY_CODE, SignInMode.SIGN_IN, signedIn = true))
    }

    @Test
    fun errorCodesHaveTexts() {
        assertEquals(R.string.account_error_invalid_code, SignInRules.errorText("invalid_code", 400))
        assertEquals(R.string.account_error_code_exhausted, SignInRules.errorText("code_exhausted", 400))
        assertEquals(R.string.account_error_email_pending, SignInRules.errorText("email_pending", 409))
        assertEquals(R.string.account_error_invalid_token, SignInRules.errorText("invalid_token", 400))
        assertEquals(R.string.account_error_invalid_email, SignInRules.errorText("invalid_email", 400))
        assertEquals(R.string.account_error_weak_password, SignInRules.errorText("weak_password", 400))
        assertEquals(R.string.account_error_email_not_verified, SignInRules.errorText("email_not_verified", 403))
        assertEquals(R.string.account_error_password_required, SignInRules.errorText("password_required", 400))
        // Unknown codes: by status, else the server's own message.
        assertEquals(R.string.account_error_rate_limited, SignInRules.errorText("http_429", 429))
        assertEquals(R.string.account_error_server, SignInRules.errorText("http_502", 502))
        assertNull(SignInRules.errorText("something_new", 400))
    }
}
