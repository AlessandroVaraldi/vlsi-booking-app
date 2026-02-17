package com.example.vlsi_booking.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.vlsi_booking.data.model.DeskStatus

@Composable
fun BookingDialog(
    desk: DeskStatus,
    amAvailable: Boolean,
    pmAvailable: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (name: String, am: Boolean, pm: Boolean) -> Unit
) {
    var name by remember { mutableStateOf("") }

    // Preselect only available slots
    var am by remember { mutableStateOf(amAvailable) }
    var pm by remember { mutableStateOf(pmAvailable && !amAvailable) } // if only PM available, default PM=true

    // If AM not available, force AM=false
    LaunchedEffect(amAvailable) { if (!amAvailable) am = false }
    LaunchedEffect(pmAvailable) { if (!pmAvailable) pm = false }

    val canConfirm = name.trim().isNotEmpty() && (am || pm)

    val availabilityMsg = when {
        amAvailable && pmAvailable -> "AM and PM are available."
        amAvailable && !pmAvailable -> "Only AM is available."
        !amAvailable && pmAvailable -> "Only PM is available."
        else -> "No slots available." // should not happen because we open dialog only if green/yellow
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Book ${desk.label}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(availabilityMsg)

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Your name") },
                    singleLine = true
                )

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FilterChip(
                        selected = am,
                        onClick = { if (amAvailable) am = !am },
                        label = { Text("AM") },
                        enabled = amAvailable
                    )
                    FilterChip(
                        selected = pm,
                        onClick = { if (pmAvailable) pm = !pm },
                        label = { Text("PM") },
                        enabled = pmAvailable
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(name.trim(), am, pm) },
                enabled = canConfirm
            ) { Text("Book") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
