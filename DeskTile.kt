package com.example.labdesks.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.labdesks.data.DeskStatus

@Composable
fun DeskTile(
    desk: DeskStatus,
    onClick: () -> Unit
) {
    val isThesis = desk.desk_type == "tesisti"
    val isStaff = desk.desk_type == "staff"
    val isBlocked = desk.desk_type == "bloccata"

    val container = when {
        isBlocked -> MaterialTheme.colorScheme.surfaceVariant
        isThesis -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.secondaryContainer
    }

    val enabled = isThesis && !isBlocked

    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = container),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 92.dp)
            .then(
                if (enabled) Modifier.clickable { onClick() } else Modifier
            )
    ) {
        Column(Modifier.padding(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    desk.label,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                TypeChip(desk.desk_type)
            }

            Spacer(Modifier.height(6.dp))

            when {
                isThesis -> ThesisBadges(am = desk.booking_am, pm = desk.booking_pm)
                isStaff -> StaffOccupancy(desk = desk)
                else -> Text("Not available", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun TypeChip(type: String) {
    val text = when (type) {
        "tesisti" -> "Thesis"
        "staff" -> "Staff"
        "bloccata" -> "Blocked"
        else -> type
    }
    AssistChip(
        onClick = {},
        label = { Text(text) },
        enabled = false
    )
}

@Composable
private fun ThesisBadges(am: String?, pm: String?) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (am != null) BadgeRow(label = "AM", value = am) else MutedRow("AM free")
        if (pm != null) BadgeRow(label = "PM", value = pm) else MutedRow("PM free")
    }
}

@Composable
private fun StaffOccupancy(desk: DeskStatus) {
    val occupant = desk.current_occupant ?: desk.holder_name ?: "—"
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            "Occupant: $occupant",
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (desk.holder_away) {
            Text(
                "Away ${desk.away_start ?: ""} → ${desk.away_end ?: ""}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        } else {
            Text(
                "Present",
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

@Composable
private fun BadgeRow(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SuggestionChip(onClick = {}, label = { Text(label) }, enabled = false)
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun MutedRow(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
