package io.github.warleysr.dechainer.screens.common

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.*
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.warleysr.dechainer.R

@Composable
fun RecoveryConfirmDialog(
    onConfirm: (String) -> Boolean,
    onDismiss: () -> Unit
) {
    val digits = remember { mutableStateListOf("", "", "", "", "", "", "", "") }
    val focusRequesters = remember { List(8) { FocusRequester() } }
    var isError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.enter_recovery_code)) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(8) { index ->
                        if (index == 4) {
                            Text("-", fontWeight = FontWeight.Bold, fontSize = 24.sp)
                        }
                        DigitBox(
                            value = digits[index],
                            onValueChange = { char ->
                                digits[index] = char
                                if (char.isNotEmpty() && index < 7) {
                                    focusRequesters[index + 1].requestFocus()
                                }
                                isError = false
                            },
                            onBackspace = {
                                if (index > 0) {
                                    focusRequesters[index - 1].requestFocus()
                                }
                            },
                            focusRequester = focusRequesters[index],
                            isError = isError
                        )
                    }
                }
                if (isError) {
                    Text(
                        text = stringResource(R.string.invalid_recovery_code),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val fullCode = digits.joinToString("")
                    if (fullCode.length == 8) {
                        if (!onConfirm(fullCode)) {
                            isError = true
                        }
                    } else {
                        isError = true
                    }
                }
            ) { Text(stringResource(R.string.confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )

    LaunchedEffect(Unit) {
        focusRequesters[0].requestFocus()
    }
}

@Composable
fun DigitBox(
    value: String,
    onValueChange: (String) -> Unit,
    onBackspace: () -> Unit,
    focusRequester: FocusRequester,
    isError: Boolean
) {
    val borderColor = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline
    
    Box(
        modifier = Modifier
            .size(24.dp, 32.dp)
            .border(1.dp, borderColor, MaterialTheme.shapes.small),
        contentAlignment = Alignment.Center
    ) {
        BasicTextField(
            value = value,
            onValueChange = { input ->
                val char = input.uppercase().lastOrNull()?.toString() ?: ""
                if (char.isEmpty() || "ABCDEFGHIJKLMNOPQRSTUVWXYZ".contains(char)) {
                    onValueChange(char)
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .wrapContentHeight(Alignment.CenterVertically)
                .focusRequester(focusRequester)
                .onKeyEvent { keyEvent ->
                    if (keyEvent.type == KeyEventType.KeyDown && 
                        keyEvent.key == Key.Backspace && 
                        value.isEmpty()) {
                        onBackspace()
                        true
                    } else false
                },
            textStyle = MaterialTheme.typography.titleLarge.copy(
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            ),
            singleLine = true,
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters)
        )
    }
}
