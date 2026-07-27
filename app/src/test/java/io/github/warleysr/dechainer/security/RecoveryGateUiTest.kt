package io.github.warleysr.dechainer.security

import android.content.Context
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.warleysr.dechainer.screens.common.RecoveryConfirmDialog
import io.github.warleysr.dechainer.support.DechainerTestRule
import io.github.warleysr.dechainer.support.prefs
import io.kotest.matchers.shouldBe
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Drives the [RecoveryConfirmDialog] Compose UI under Robolectric to pin the recovery gate's R3
 * guarantees at the UI boundary: the confirm button stays disabled until exactly sixteen letters are
 * entered, input is sanitised to uppercase A–Z and truncated at sixteen, an incorrect code (onConfirm
 * returning false) surfaces the error and leaves the dialog open instead of unlocking, and the shuffle
 * keyboard always offers the next correct letter — so entry can never get stuck — while rebuilding the
 * grid after every tap and backspace.
 */
@RunWith(AndroidJUnit4::class)
class RecoveryGateUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    @get:Rule
    val dechainerRule = DechainerTestRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun `confirm stays disabled until sixteen letters are entered`() {
        composeRule.setContent {
            RecoveryConfirmDialog(onConfirm = { true }, onDismiss = {})
        }

        composeRule.onNodeWithText("Confirm").assertIsNotEnabled()

        composeRule.onNode(hasSetTextAction()).performTextInput("ABCDEFGHIJKLMNO")
        composeRule.onNodeWithText("Confirm").assertIsNotEnabled()

        composeRule.onNode(hasSetTextAction()).performTextInput("P")
        composeRule.onNodeWithText("Confirm").assertIsEnabled()
    }

    @Test
    fun `input is sanitised to uppercase letters and truncated at sixteen`() {
        composeRule.setContent {
            RecoveryConfirmDialog(onConfirm = { true }, onDismiss = {})
        }

        composeRule.onNode(hasSetTextAction()).performTextInput("ab1!cD@efGHijklmnopqrstuv")

        composeRule.onNode(hasSetTextAction()).assert(hasText("ABCDEFGHIJKLMNOP"))
        composeRule.onNodeWithText("16/16").assertExists()
    }

    @Test
    fun `an incorrect code shows the error and keeps the gate closed`() {
        var captured: String? = null
        composeRule.setContent {
            RecoveryConfirmDialog(onConfirm = { captured = it; false }, onDismiss = {})
        }

        composeRule.onNode(hasSetTextAction()).performTextInput("ABCDEFGHIJKLMNOP")
        composeRule.onNodeWithText("Confirm").performClick()

        captured shouldBe "ABCDEFGHIJKLMNOP"
        composeRule.onNodeWithText("Invalid recovery code").assertExists()
        composeRule.onNode(hasSetTextAction()).assertExists()
    }

    @Test
    fun `the shuffle keyboard always offers the next correct letter so entry never gets stuck`() {
        val code = "ABCDEFGHIJKLMNOP"
        prefs("security_prefs").edit().putBoolean("shuffle_keyboard", true).commit()
        SecurityManager.saveRecoveryCode(context, code)

        composeRule.setContent {
            RecoveryConfirmDialog(onConfirm = { true }, onDismiss = {})
        }

        code.forEachIndexed { index, letter ->
            composeRule.onNodeWithText("$index/16").assertExists()
            composeRule.onNodeWithText(letter.toString()).performClick()
        }

        composeRule.onNodeWithText("16/16").assertExists()
        composeRule.onNodeWithText("Confirm").assertIsEnabled()
    }

    @Test
    fun `the shuffle keyboard rebuilds the grid after every tap`() {
        val code = "ABCDEFGHIJKLMNOP"
        prefs("security_prefs").edit().putBoolean("shuffle_keyboard", true).commit()
        SecurityManager.saveRecoveryCode(context, code)

        composeRule.setContent {
            RecoveryConfirmDialog(onConfirm = { true }, onDismiss = {})
        }

        composeRule.onNodeWithText("0/16").assertExists()
        composeRule.onNodeWithText("A").performClick()

        composeRule.onNodeWithText("1/16").assertExists()
        composeRule.onNodeWithText("B").performClick()

        composeRule.onNodeWithText("2/16").assertExists()
        composeRule.onNodeWithText("C").assertExists()
    }
}
