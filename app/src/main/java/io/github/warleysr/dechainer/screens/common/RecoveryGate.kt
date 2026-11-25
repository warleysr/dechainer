package io.github.warleysr.dechainer.screens.common

import android.content.Context
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.warleysr.dechainer.R
import io.github.warleysr.dechainer.data.PermanentRestrictionsManager
import io.github.warleysr.dechainer.data.StatsRepository
import io.github.warleysr.dechainer.security.SecurityManager
import kotlinx.coroutines.delay

/**
 * Gates a sensitive action behind the recovery-code dialog, unless a recovery session is already
 * active or no recovery code has been set yet (first run, before [SetupRecovery][io.github.warleysr.dechainer.screens.setup.SetupRecovery]).
 * Once the code is right, the configured access challenges run and only then does the session start.
 * Replaces the `pendingAction`/`showRecoveryDialog` pair every settings screen used to hand-roll.
 */
class RecoveryGate(
    private val context: Context,
    private val permanentKey: String? = null
) {
    private var pendingAction by mutableStateOf<(() -> Unit)?>(null)
    private var pendingCancel: (() -> Unit)? = null

    var isChallenging by mutableStateOf(false)
        private set

    var isPermanentBlockedDialogVisible by mutableStateOf(false)
        private set

    val isDialogVisible: Boolean get() = pendingAction != null && !isChallenging

    /** Whether a recovery session is active — screens use this to enable/disable gated controls. */
    var isSessionActive by mutableStateOf(SecurityManager.isSessionActive())
        internal set

    fun updateSessionActive() {
        val active = SecurityManager.isSessionActive()
        if (isSessionActive != active) {
            isSessionActive = active
        }
    }

    fun showPermanentBlockedDialog() {
        isPermanentBlockedDialogVisible = true
    }

    fun dismissPermanentBlockedDialog() {
        isPermanentBlockedDialogVisible = false
    }

    private fun checkBlocked(key: String?, onCancel: (() -> Unit)?): Boolean {
        if (key != null && PermanentRestrictionsManager.isPermanent(context, key)) {
            onCancel?.invoke()
            isPermanentBlockedDialogVisible = true
            return true
        }
        return false
    }

    /** [onCancel] runs if the user dismisses the dialog instead of confirming — for rolling back optimistic UI updates. */
    fun run(onCancel: (() -> Unit)? = null, action: () -> Unit) {
        runForFeature(permanentKey, onCancel, action)
    }

    fun runForFeature(featureKey: String?, onCancel: (() -> Unit)? = null, action: () -> Unit) {
        if (checkBlocked(featureKey, onCancel)) return
        val storedCode = SecurityManager.getRecoveryCode(context)
        if (storedCode == null || SecurityManager.isSessionActive()) {
            action()
        } else {
            pendingAction = action
            pendingCancel = onCancel
        }
    }

    /** Loosening needs a session; anything stricter is applied after an undo window instead. [onCancel] runs on dismissed dialog or Undo. */
    fun change(loosening: Boolean, onCancel: (() -> Unit)? = null, action: () -> Unit) {
        changeForFeature(permanentKey, loosening, onCancel, action)
    }

    fun changeForFeature(featureKey: String?, loosening: Boolean, onCancel: (() -> Unit)? = null, action: () -> Unit) {
        if (loosening && checkBlocked(featureKey, onCancel)) return
        if (loosening) runForFeature(null, onCancel, action)
        else if (SecurityManager.isSessionActive()) action()
        else PendingChange.schedule(
            context.getString(R.string.restriction_pending),
            context.getString(R.string.undo),
            onCancel,
            action
        )
    }

    fun confirm(code: String): Boolean {
        val storedCode = SecurityManager.getRecoveryCode(context) ?: return true
        if (!SecurityManager.validateRecoveryCode(code, storedCode)) return false
        if (SecurityManager.isSessionActive() || SecurityManager.getAccessChallenges(context).isEmpty()) {
            challengesDone()
        } else {
            isChallenging = true
        }
        return true
    }

    fun challengesDone() {
        if (!SecurityManager.isSessionActive()) {
            SecurityManager.startSession()
            StatsRepository.recordRecoverySessionOpened(context)
        }
        updateSessionActive()
        pendingAction?.invoke()
        clear()
    }

    fun dismiss() {
        pendingCancel?.invoke()
        clear()
    }

    private fun clear() {
        pendingAction = null
        pendingCancel = null
        isChallenging = false
    }
}


@Composable
fun rememberRecoveryGate(permanentKey: String? = null): RecoveryGate {
    val context = LocalContext.current
    val gate = remember(permanentKey) { RecoveryGate(context, permanentKey) }

    LaunchedEffect(gate, SecurityManager.sessionEndTime) {
        gate.updateSessionActive()
        while (gate.isSessionActive) {
            val remaining = SecurityManager.sessionEndTime - System.currentTimeMillis()
            if (remaining <= 0) {
                gate.updateSessionActive()
                break
            }
            delay(remaining.coerceAtMost(1000L))
            gate.updateSessionActive()
        }
    }

    return gate
}

@Composable
fun RecoveryGateDialog(gate: RecoveryGate) {
    val context = LocalContext.current
    if (gate.isPermanentBlockedDialogVisible) {
        AlertDialog(
            onDismissRequest = { gate.dismissPermanentBlockedDialog() },
            title = { Text(stringResource(R.string.permanent_restrictions)) },
            text = { Text(stringResource(R.string.permanent_banner_text)) },
            confirmButton = {
                TextButton(onClick = { gate.dismissPermanentBlockedDialog() }) {
                    Text(stringResource(R.string.close))
                }
            }
        )
    }
    if (gate.isDialogVisible) {
        RecoveryConfirmDialog(
            onConfirm = { code -> gate.confirm(code) },
            onDismiss = { gate.dismiss() }
        )
    }
    if (gate.isChallenging) {
        Dialog(
            onDismissRequest = { gate.dismiss() },
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
        ) {
            Surface(modifier = Modifier.fillMaxSize()) {
                ChallengeRunner(
                    challenges = remember { SecurityManager.getAccessChallenges(context) },
                    onDone = { gate.challengesDone() },
                    onGiveUp = { gate.dismiss() }
                )
            }
        }
    }
}
