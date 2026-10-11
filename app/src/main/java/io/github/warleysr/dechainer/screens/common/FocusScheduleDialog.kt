package io.github.warleysr.dechainer.screens.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.warleysr.dechainer.R
import io.github.warleysr.dechainer.models.FocusSchedule
import io.github.warleysr.dechainer.screens.tabs.TimeOfDayWheels
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun FocusScheduleDialog(
    initialSchedule: FocusSchedule? = null,
    onSave: (FocusSchedule) -> Unit,
    onDismiss: () -> Unit
) {
    var hour by remember { mutableIntStateOf(initialSchedule?.hour ?: 8) }
    var minute by remember { mutableIntStateOf(initialSchedule?.minute ?: 0) }
    // Default to Mon-Fri (1..5) if new schedule
    var selectedDays by remember {
        mutableStateOf(initialSchedule?.daysOfWeek ?: setOf(1, 2, 3, 4, 5))
    }

    val locale = Locale.getDefault()
    val allDays = remember {
        listOf(
            DayOfWeek.MONDAY,
            DayOfWeek.TUESDAY,
            DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY,
            DayOfWeek.FRIDAY,
            DayOfWeek.SATURDAY,
            DayOfWeek.SUNDAY
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (initialSchedule == null) stringResource(R.string.focus_schedule_dialog_title)
                else stringResource(R.string.focus_edit_schedule)
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                TimeOfDayWheels(
                    label = stringResource(R.string.focus_schedule_time),
                    hour = hour,
                    minute = minute,
                    onHourChange = { hour = it },
                    onMinuteChange = { minute = it }
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = stringResource(R.string.focus_schedule_days),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Quick selector buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SuggestionChip(
                        onClick = { selectedDays = setOf(1, 2, 3, 4, 5) },
                        label = { Text(stringResource(R.string.focus_schedule_days_weekdays)) }
                    )
                    SuggestionChip(
                        onClick = { selectedDays = (1..7).toSet() },
                        label = { Text(stringResource(R.string.focus_schedule_days_all)) }
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Days of week selector chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    allDays.forEach { day ->
                        val dayValue = day.value
                        val isSelected = dayValue in selectedDays
                        val label = day.getDisplayName(TextStyle.SHORT, locale).trimEnd('.')

                        val containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        val contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(containerColor)
                                .clickable {
                                    selectedDays = if (isSelected) {
                                        selectedDays - dayValue
                                    } else {
                                        selectedDays + dayValue
                                    }
                                }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = contentColor,
                                textAlign = TextAlign.Center,
                                maxLines = 1
                            )
                        }
                    }
                }

                if (selectedDays.isEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.focus_schedule_select_days_warning),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val schedule = (initialSchedule ?: FocusSchedule(
                        daysOfWeek = selectedDays,
                        hour = hour,
                        minute = minute
                    )).copy(
                        daysOfWeek = selectedDays,
                        hour = hour,
                        minute = minute
                    )
                    onSave(schedule)
                },
                enabled = selectedDays.isNotEmpty()
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
