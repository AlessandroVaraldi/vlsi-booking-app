package com.example.vlsi_booking.ui.screens

import android.app.DatePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.vlsi_booking.data.model.DeskStatus
import com.example.vlsi_booking.ui.MainViewModel
import com.example.vlsi_booking.ui.components.BookingDialog
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(vm: MainViewModel) {
    val state by vm.state.collectAsState()

    var bookingDesk by remember { mutableStateOf<DeskStatus?>(null) }
    var infoDesk by remember { mutableStateOf<DeskStatus?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Lab desks") },
                actions = { TextButton(onClick = { vm.refresh() }) { Text("Refresh") } }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(12.dp)
        ) {
            HeaderRow(date = state.selectedDate, onPickDate = vm::setDate)
            Spacer(Modifier.height(12.dp))

            if (state.isLoading) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Spacer(Modifier.height(12.dp))
            }

            state.errorMessage?.let { msg ->
                ErrorCard(message = msg, onDismiss = vm::clearError)
                Spacer(Modifier.height(12.dp))
            }

            DeskGrid(
                desks = state.desks,
                onDeskClick = { desk ->
                    when (deskKind(desk)) {
                        DeskKind.BOOKABLE_GREEN,
                        DeskKind.BOOKABLE_YELLOW -> bookingDesk = desk
                        DeskKind.RED_INFO -> infoDesk = desk
                        DeskKind.HIDDEN -> Unit
                    }
                }
            )
        }
    }

    bookingDesk?.let { desk ->
        val amFree = desk.booking_am == null
        val pmFree = desk.booking_pm == null

        BookingDialog(
            desk = desk,
            amAvailable = amFree,
            pmAvailable = pmFree,
            onDismiss = { bookingDesk = null },
            onConfirm = { name: String, am: Boolean, pm: Boolean ->
                vm.bookDesk(desk.id, name, am, pm)
                bookingDesk = null
            }
        )
    }

    infoDesk?.let { desk ->
        DeskInfoDialog(
            desk = desk,
            onDismiss = { infoDesk = null }
        )
    }
}

@Composable
private fun HeaderRow(date: LocalDate, onPickDate: (LocalDate) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "Date: $date",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.weight(1f))
        FilledTonalButton(onClick = {
            DatePickerDialog(
                context,
                { _, year, month, dayOfMonth ->
                    onPickDate(LocalDate.of(year, month + 1, dayOfMonth))
                },
                date.year, date.monthValue - 1, date.dayOfMonth
            ).show()
        }) { Text("Change") }
    }
}

@Composable
private fun ErrorCard(message: String, onDismiss: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(message, modifier = Modifier.weight(1f))
            TextButton(onClick = onDismiss) { Text("OK") }
        }
    }
}

private enum class DeskKind { BOOKABLE_GREEN, BOOKABLE_YELLOW, RED_INFO, HIDDEN }

private fun deskKind(desk: DeskStatus): DeskKind {
    return when (desk.desk_type) {
        "bloccata" -> DeskKind.HIDDEN
        "staff" -> DeskKind.RED_INFO
        "tesisti" -> {
            val amFree = desk.booking_am == null
            val pmFree = desk.booking_pm == null
            when {
                amFree && pmFree -> DeskKind.BOOKABLE_GREEN
                amFree xor pmFree -> DeskKind.BOOKABLE_YELLOW
                else -> DeskKind.RED_INFO // fully booked
            }
        }
        else -> DeskKind.RED_INFO
    }
}

@Composable
private fun DeskGrid(desks: List<DeskStatus>, onDeskClick: (DeskStatus) -> Unit) {
    // Keep all 24 items so layout stays 4x6 in the correct positions
    LazyVerticalGrid(
        columns = GridCells.Fixed(6),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(desks, key = { it.id }) { desk ->
            DeskTile(desk = desk, onClick = { onDeskClick(desk) })
        }
    }
}


@Composable
private fun DeskTile(desk: DeskStatus, onClick: () -> Unit) {
    val kind = deskKind(desk)

    // For blocked desks: invisible placeholder that still occupies space
    if (kind == DeskKind.HIDDEN) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
        )
        return
    }

    val fill = when (kind) {
        DeskKind.BOOKABLE_GREEN -> Color(0xFF2E7D32)   // green
        DeskKind.BOOKABLE_YELLOW -> Color(0xFFF9A825)  // yellow
        DeskKind.RED_INFO -> Color(0xFFC62828)         // red
        DeskKind.HIDDEN -> Color.Transparent
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .background(fill, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        // no text inside
    }
}


@Composable
private fun DeskInfoDialog(desk: DeskStatus, onDismiss: () -> Unit) {
    val isThesis = desk.desk_type == "tesisti"
    val isStaff = desk.desk_type == "staff"

    val lines = buildList {
        add("Desk: ${desk.label} (id ${desk.id})")
        add("Type: ${desk.desk_type}")

        if (isThesis) {
            add("AM: ${desk.booking_am ?: "free"}")
            add("PM: ${desk.booking_pm ?: "free"}")
            val amFree = desk.booking_am == null
            val pmFree = desk.booking_pm == null
            if (!amFree && !pmFree) add("Status: fully booked")
            else if (amFree xor pmFree) add("Status: partially available")
            else add("Status: available")
        }

        if (isStaff) {
            add("Holder: ${desk.holder_name ?: "—"}")
            add("Current occupant: ${desk.current_occupant ?: desk.holder_name ?: "—"}")
            if (desk.holder_away == true) add("Holder is away (temporary occupant active)")
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Desk info") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                lines.forEach { Text(it) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}
