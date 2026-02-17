package com.example.labdesks.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.labdesks.data.DeskStatus
import java.time.LocalDate

@Composable
fun BookingDialog(
    desk: DeskStatus,
    day: LocalDate,
    onDismiss: () -> Unit,
    onConfirm: (name: String, am: Boolean, pm: Boolean) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var am by remember { mutableStateOf(true) }
    var pm by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Book ${desk.label}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Day: ${day}", style = MaterialTheme.typography.bodySmall)

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    label = { Text("Your name") },
                    modifier = Modifier.fillMaxWidth()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FilterChip(
                        selected = am,
                        onClick = { am = !am },
                        label = { Text("AM") }
                    )
                    FilterChip(
                        selected = pm,
                        onClick = { pm = !pm },
                        label = { Text("PM") }
                    )
                }

                // Optional hint: show current bookings
                val amInfo = desk.booking_am?.let { "AM booked by $it" } ?: "AM free"
                val pmInfo = desk.booking_pm?.let { "PM booked by $it" } ?: "PM free"
                Text(amInfo, style = MaterialTheme.typography.labelSmall)
                Text(pmInfo, style = MaterialTheme.typography.labelSmall)
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(name, am, pm) }) { Text("Book") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
