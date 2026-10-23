package io.github.warleysr.dechainer.screens.common

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.github.warleysr.dechainer.security.SecurityManager
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RecoveryGateTest {

    private lateinit var context: Context
    private lateinit var gate: RecoveryGate

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        gate = RecoveryGate(context)
        SecurityManager.endSession()
    }

    @After
    fun tearDown() {
        SecurityManager.endSession()
    }

    @Test
    fun `change executes action immediately when session is active`() {
        SecurityManager.startSession()

        var tighteningExecuted = false
        gate.change(loosening = false) {
            tighteningExecuted = true
        }

        assertTrue(tighteningExecuted)

        var looseningExecuted = false
        gate.change(loosening = true) {
            looseningExecuted = true
        }

        assertTrue(looseningExecuted)
    }

    @Test
    fun `change does not execute tightening action immediately when session is not active`() {
        SecurityManager.endSession()

        var tighteningExecuted = false
        gate.change(loosening = false) {
            tighteningExecuted = true
        }

        // Tightening without active session schedules a delayed change (5 seconds undo window)
        assertFalse(tighteningExecuted)
    }
}
