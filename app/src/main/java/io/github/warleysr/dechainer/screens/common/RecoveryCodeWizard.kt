package io.github.warleysr.dechainer.screens.common

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.NoPhotography
import androidx.compose.material.icons.outlined.Password
import androidx.compose.material.icons.outlined.PhonelinkErase
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.warleysr.dechainer.R
import io.github.warleysr.dechainer.security.SecurityManager

private const val STEP_INTRO = 0
private const val STEP_WRITE = 1
private const val STEP_CONFIRM = 2
private const val STEP_STORE = 3
private const val STEP_COUNT = 4

@Composable
fun RecoveryCodeWizard(
    onFinish: (String) -> Unit,
    modifier: Modifier = Modifier,
    onCancel: (() -> Unit)? = null,
    replacing: Boolean = false
) {
    val code = rememberSaveable { SecurityManager.generateRecoveryCode() }
    var step by rememberSaveable { mutableIntStateOf(STEP_INTRO) }
    var revealed by rememberSaveable { mutableStateOf(false) }
    var written by rememberSaveable { mutableStateOf(false) }
    var typed by rememberSaveable { mutableStateOf("") }
    var stored by rememberSaveable { mutableStateOf(false) }

    fun goBack() {
        if (step > STEP_INTRO) step-- else onCancel?.invoke()
    }

    BackHandler(enabled = step > STEP_INTRO || onCancel != null, onBack = ::goBack)

    val canAdvance = when (step) {
        STEP_WRITE -> revealed && written
        STEP_CONFIRM -> typed == code
        STEP_STORE -> stored
        else -> true
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 12.dp)
    ) {
        StepIndicator(current = step, total = STEP_COUNT)
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.recovery_wizard_step, step + 1, STEP_COUNT),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )

        val scrollState = rememberScrollState()
        LaunchedEffect(step) { scrollState.scrollTo(0) }
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when (step) {
                STEP_INTRO -> IntroStep()
                STEP_WRITE -> WriteStep(
                    code = code,
                    revealed = revealed,
                    written = written,
                    onReveal = { revealed = true },
                    onWrittenChange = { written = it }
                )
                STEP_CONFIRM -> ConfirmStep(
                    code = code,
                    typed = typed,
                    onTypedChange = { typed = it }
                )
                STEP_STORE -> StoreStep(
                    replacing = replacing,
                    stored = stored,
                    onStoredChange = { stored = it }
                )
            }
        }

        HorizontalDivider(
            color = if (scrollState.canScrollForward) MaterialTheme.colorScheme.outlineVariant
            else Color.Transparent
        )
        if (step == STEP_INTRO) {
            Text(
                stringResource(R.string.recovery_wizard_intro_hint),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (step > STEP_INTRO || onCancel != null) {
                OutlinedButton(onClick = ::goBack) {
                    Text(
                        stringResource(if (step > STEP_INTRO) R.string.recovery_wizard_back else R.string.cancel)
                    )
                }
            }
            Button(
                enabled = canAdvance,
                onClick = { if (step == STEP_STORE) onFinish(code) else step++ }
            ) {
                Text(
                    stringResource(if (step == STEP_STORE) R.string.recovery_wizard_finish else R.string.recovery_wizard_next)
                )
            }
        }
    }
}

@Composable
private fun StepIndicator(current: Int, total: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (index in 0 until total) {
            val reached = index <= current
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(
                        if (reached) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
            ) {
                if (index < current) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                } else {
                    Text(
                        "${index + 1}",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (reached) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (index < total - 1) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 6.dp)
                        .height(2.dp)
                        .background(
                            if (index < current) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outlineVariant
                        )
                )
            }
        }
    }
}

@Composable
private fun StepHeader(icon: ImageVector, title: String, text: String? = null) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(80.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer)
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(40.dp)
        )
    }
    Spacer(Modifier.height(20.dp))
    Text(
        title,
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center
    )
    if (text != null) {
        Spacer(Modifier.height(12.dp))
        StepText(text)
    }
}

@Composable
private fun StepText(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun IntroStep() {
    StepHeader(
        icon = Icons.Outlined.Key,
        title = stringResource(R.string.recovery_wizard_intro_title)
    )
    Spacer(Modifier.height(20.dp))
    InfoCard {
        Text(
            stringResource(R.string.recovery_wizard_intro_how),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        TipRow(Icons.Outlined.Key, stringResource(R.string.recovery_wizard_intro_step_generate))
        TipRow(Icons.Outlined.EditNote, stringResource(R.string.recovery_wizard_intro_step_write))
        TipRow(Icons.Outlined.Keyboard, stringResource(R.string.recovery_wizard_intro_step_confirm))
        TipRow(Icons.Outlined.Lock, stringResource(R.string.recovery_wizard_intro_step_store))
    }
    Spacer(Modifier.height(16.dp))
    StepText(stringResource(R.string.recovery_wizard_intro_text))
}

@Composable
private fun WriteStep(
    code: String,
    revealed: Boolean,
    written: Boolean,
    onReveal: () -> Unit,
    onWrittenChange: (Boolean) -> Unit
) {
    StepHeader(
        icon = Icons.Outlined.EditNote,
        title = stringResource(R.string.recovery_wizard_write_title),
        text = stringResource(R.string.recovery_wizard_write_text)
    )
    Spacer(Modifier.height(24.dp))
    if (revealed) {
        CodeDisplay(code)
    } else {
        FilledTonalButton(onClick = onReveal, modifier = Modifier.height(52.dp)) {
            Icon(Icons.Outlined.Visibility, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.recovery_wizard_reveal))
        }
    }
    Spacer(Modifier.height(16.dp))
    CheckRow(
        checked = written,
        enabled = revealed,
        text = stringResource(R.string.recovery_wizard_written),
        onCheckedChange = onWrittenChange
    )
}

@Composable
private fun CodeDisplay(code: String) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        BasicText(
            code,
            style = MaterialTheme.typography.headlineMedium.copy(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                textAlign = TextAlign.Center
            ),
            maxLines = 1,
            autoSize = TextAutoSize.StepBased(minFontSize = 14.sp, maxFontSize = 28.sp),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 16.dp)
        )
    }
}

@Composable
private fun ConfirmStep(code: String, typed: String, onTypedChange: (String) -> Unit) {
    StepHeader(
        icon = Icons.Outlined.Password,
        title = stringResource(R.string.recovery_wizard_confirm_title),
        text = stringResource(R.string.recovery_wizard_confirm_text)
    )
    Spacer(Modifier.height(24.dp))
    val complete = typed.length == code.length
    val matches = typed == code
    OutlinedTextField(
        value = typed,
        onValueChange = { input ->
            onTypedChange(input.uppercase().filter { it in 'A'..'Z' }.take(code.length))
        },
        label = { Text(stringResource(R.string.enter_recovery_code)) },
        placeholder = { Text(stringResource(R.string.recovery_code_hint)) },
        textStyle = MaterialTheme.typography.titleLarge.copy(
            fontFamily = FontFamily.Monospace,
            letterSpacing = 2.sp
        ),
        singleLine = true,
        isError = complete && !matches,
        trailingIcon = if (matches) {
            { Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
        } else null,
        supportingText = {
            Text(
                when {
                    matches -> stringResource(R.string.recovery_wizard_confirm_match)
                    complete -> stringResource(R.string.recovery_wizard_confirm_mismatch)
                    else -> "${typed.length}/${code.length}"
                }
            )
        },
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Characters,
            autoCorrectEnabled = false
        ),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun StoreStep(replacing: Boolean, stored: Boolean, onStoredChange: (Boolean) -> Unit) {
    StepHeader(
        icon = Icons.Outlined.Inventory2,
        title = stringResource(R.string.recovery_wizard_store_title),
        text = stringResource(R.string.recovery_wizard_store_text)
    )
    Spacer(Modifier.height(24.dp))
    InfoCard {
        TipRow(Icons.Outlined.PhonelinkErase, stringResource(R.string.recovery_wizard_store_tip_away))
        TipRow(Icons.Outlined.Search, stringResource(R.string.recovery_wizard_store_tip_findable))
        TipRow(Icons.Outlined.NoPhotography, stringResource(R.string.recovery_wizard_store_tip_digital))
        if (replacing) {
            TipRow(Icons.Outlined.DeleteOutline, stringResource(R.string.recovery_wizard_store_tip_old))
        }
    }
    Spacer(Modifier.height(16.dp))
    CheckRow(
        checked = stored,
        text = stringResource(R.string.recovery_wizard_stored),
        onCheckedChange = onStoredChange
    )
}

@Composable
private fun InfoCard(content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            content()
        }
    }
}

@Composable
private fun TipRow(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun CheckRow(
    checked: Boolean,
    text: String,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(vertical = 4.dp)
    ) {
        Checkbox(checked = checked, enabled = enabled, onCheckedChange = onCheckedChange)
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = if (enabled) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        )
    }
}
