package io.github.warleysr.dechainer.screens.setup

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import io.github.warleysr.dechainer.screens.common.RecoveryCodeWizard
import io.github.warleysr.dechainer.security.SecurityManager

@Composable
fun SetupRecovery(paddingValues: PaddingValues) {
    val context = LocalContext.current
    RecoveryCodeWizard(
        onFinish = { code -> SecurityManager.saveRecoveryCode(context, code) },
        modifier = Modifier.padding(paddingValues)
    )
}
