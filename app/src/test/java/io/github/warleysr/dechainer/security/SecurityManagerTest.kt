package io.github.warleysr.dechainer.security

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldMatch
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Unit tests for [SecurityManager].
 *
 * [SecurityManager] keeps its state (session end time and recovery-key flag) in a
 * `companion object`, so that state is shared across every test in the JVM. Each test
 * therefore resets the session in [reset] to stay isolated — a leftover active session
 * from a previous test would otherwise make [SecurityManager.validateRecoveryCode]
 * short-circuit to `true`.
 */
@RunWith(AndroidJUnit4::class)
class SecurityManagerTest {

    private lateinit var context: Context

    @Before
    fun reset() {
        context = ApplicationProvider.getApplicationContext()
        // Clear any session left active by a previous test.
        SecurityManager.endSession()
        // Clear persisted recovery code so SharedPreferences-backed tests start clean.
        context.getSharedPreferences("recovery_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @After
    fun tearDown() {
        SecurityManager.endSession()
    }

    // region generateRecoveryCode

    @Test
    fun `generateRecoveryCode returns 16 characters by default`() {
        SecurityManager.generateRecoveryCode().length shouldBe 16
    }

    @Test
    fun `generateRecoveryCode honors a custom length`() {
        SecurityManager.generateRecoveryCode(8).length shouldBe 8
        SecurityManager.generateRecoveryCode(32).length shouldBe 32
    }

    @Test
    fun `generateRecoveryCode with length zero returns an empty string`() {
        SecurityManager.generateRecoveryCode(0) shouldBe ""
    }

    @Test
    fun `generateRecoveryCode uses only uppercase A-Z characters`() {
        // Sample several codes to make the character-pool assertion meaningful.
        repeat(50) {
            SecurityManager.generateRecoveryCode() shouldMatch Regex("[A-Z]{16}")
        }
    }

    @Test
    fun `generateRecoveryCode produces distinct codes across calls`() {
        // With a 26^16 space, 100 draws colliding would be astronomically unlikely,
        // so any duplicate signals broken randomness rather than flakiness.
        val codes = (1..100).map { SecurityManager.generateRecoveryCode() }.toSet()
        codes shouldHaveSize 100
    }

    // endregion

    // region session lifecycle

    @Test
    fun `session is inactive after endSession`() {
        SecurityManager.endSession()
        SecurityManager.isSessionActive().shouldBeFalse()
    }

    @Test
    fun `a correct recovery code starts an active session`() {
        SecurityManager.isSessionActive().shouldBeFalse()

        val result = SecurityManager.validateRecoveryCode("SECRET", "SECRET")

        result.shouldBeTrue()
        SecurityManager.isSessionActive().shouldBeTrue()
    }

    @Test
    fun `endSession deactivates an active session`() {
        SecurityManager.validateRecoveryCode("SECRET", "SECRET")
        SecurityManager.isSessionActive().shouldBeTrue()

        SecurityManager.endSession()

        SecurityManager.isSessionActive().shouldBeFalse()
    }

    // endregion

    // region validateRecoveryCode

    @Test
    fun `validateRecoveryCode returns true and matches when the code is correct`() {
        SecurityManager.validateRecoveryCode("ABCDEF", "ABCDEF").shouldBeTrue()
    }

    @Test
    fun `validateRecoveryCode returns false when the code is wrong`() {
        SecurityManager.validateRecoveryCode("WRONG", "ABCDEF").shouldBeFalse()
    }

    @Test
    fun `a wrong recovery code does not start a session`() {
        SecurityManager.validateRecoveryCode("WRONG", "ABCDEF")
        SecurityManager.isSessionActive().shouldBeFalse()
    }

    @Test
    fun `validateRecoveryCode is case-sensitive`() {
        SecurityManager.validateRecoveryCode("secret", "SECRET").shouldBeFalse()
    }

    @Test
    fun `while a session is active any input validates as true`() {
        // First, a correct code opens the session.
        SecurityManager.validateRecoveryCode("SECRET", "SECRET").shouldBeTrue()
        SecurityManager.isSessionActive().shouldBeTrue()

        // Now even a wrong code short-circuits to true because the session is live.
        SecurityManager.validateRecoveryCode("TOTALLY-WRONG", "SECRET").shouldBeTrue()
    }

    // endregion

    // region recovery-code persistence

    @Test
    fun `getRecoveryCode returns null when nothing is stored`() {
        SecurityManager.getRecoveryCode(context).shouldBeNull()
    }

    @Test
    fun `isRecoveryCodeSet is false when nothing is stored`() {
        SecurityManager.isRecoveryCodeSet(context).shouldBeFalse()
    }

    @Test
    fun `saveRecoveryCode persists the code so getRecoveryCode returns it`() {
        val code = SecurityManager.generateRecoveryCode()

        SecurityManager.saveRecoveryCode(context, code)

        SecurityManager.getRecoveryCode(context) shouldBe code
    }

    @Test
    fun `isRecoveryCodeSet is true after saveRecoveryCode`() {
        SecurityManager.saveRecoveryCode(context, "MY-RECOVERY-CODE")

        SecurityManager.isRecoveryCodeSet(context).shouldBeTrue()
    }

    @Test
    fun `saved recovery code round-trips through validateRecoveryCode`() {
        val code = SecurityManager.generateRecoveryCode()
        SecurityManager.saveRecoveryCode(context, code)

        val stored = SecurityManager.getRecoveryCode(context)!!

        SecurityManager.validateRecoveryCode(code, stored).shouldBeTrue()
    }

    // endregion
}
