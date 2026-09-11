package io.github.warleysr.dechainer.screens.common

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import io.github.warleysr.dechainer.security.SecurityManager

/**
 * Gates a sensitive action behind the recovery-code dialog, unless a recovery session is already
 * active or no recovery code has been set yet (first run, before [SetupRecovery][io.github.warleysr.dechainer.screens.setup.SetupRecovery]).
 * Replaces the `pendingAction`/`showRecoveryDialog` pair every settings screen used to hand-roll.
 */
class RecoveryGate(private val context: Context) {
    private var pendingAction by mutableStateOf<(() -> Unit)?>(null)
    private var pendingCancel: (() -> Unit)? = null

    val isDialogVisible: Boolean get() = pendingAction != null

    /** Whether a recovery session is active — screens use this to enable/disable gated controls. */
    val isSessionActive: Boolean get() = SecurityManager.isSessionActive()

    /** [onCancel] runs if the user dismisses the dialog instead of confirming — for rolling back optimistic UI updates. */
    fun run(onCancel: (() -> Unit)? = null, action: () -> Unit) {
        val storedCode = SecurityManager.getRecoveryCode(context)
        if (storedCode == null || SecurityManager.isSessionActive()) {
            action()
        } else {
            pendingAction = action
            pendingCancel = onCancel
        }
    }

    fun confirm(code: String): Boolean {
        val storedCode = SecurityManager.getRecoveryCode(context) ?: return true
        if (!SecurityManager.validateRecoveryCode(code, storedCode)) return false
        pendingAction?.invoke()
        clear()
        return true
    }

    fun dismiss() {
        pendingCancel?.invoke()
        clear()
    }

    private fun clear() {
        pendingAction = null
        pendingCancel = null
    }
}

@Composable
fun rememberRecoveryGate(): RecoveryGate {
    val context = LocalContext.current
    return remember { RecoveryGate(context) }
}

@Composable
fun RecoveryGateDialog(gate: RecoveryGate) {
    if (gate.isDialogVisible) {
        RecoveryConfirmDialog(
            onConfirm = { code -> gate.confirm(code) },
            onDismiss = { gate.dismiss() }
        )
    }
}
