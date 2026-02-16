package com.example.labdesks.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.labdesks.data.DeskStatus
import java.time.LocalDate

@Composable
fun DeskScreen(vm: DeskViewModel) {
    val state by vm.state.collectAsState()

    var bookingDesk: DeskStatus? by remember { mutableStateOf(null) }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(state.snackMessage) {
        state.snackMessage?.let {
            snackbarHostState.showSnackbar(it)
            vm.consumeSnack()
        }
    }

    val pullRefreshState = rememberPullRefreshState(
        refreshing = state.loading,
        onRefresh = { vm.refresh() }
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Lab Desks") },
                actions = {
                    TextButton(onClick = { vm.prevDay() }) { Text("Prev") }
                    TextButton(onClick = { vm.nextDay() }) { Text("Next") }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .pullRefresh(pullRefreshState)
        ) {
            Column(Modifier.fillMaxSize().padding(16.dp)) {
                DayHeader(
                    day = state.day,
                    onDayChange = { vm.setDay(it) }
                )

                Spacer(Modifier.height(12.dp))

                if (state.errorMessage != null) {
                    ErrorCard(message = state.errorMessage!!, onRetry = { vm.refresh() })
                    Spacer(Modifier.height(12.dp))
                }

                LazyVerticalGrid(
                    columns = GridCells.Fixed(6),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(state.desks) { desk ->
                        DeskTile(
                            desk = desk,
                            onClick = {
                                if (desk.desk_type == "tesisti") bookingDesk = desk
                            }
                        )
                    }
                }
            }

            PullRefreshIndicator(
                refreshing = state.loading,
                state = pullRefreshState,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }
    }

    bookingDesk?.let { d ->
        BookingDialog(
            desk = d,
            day = state.day,
            onDismiss = { bookingDesk = null },
            onConfirm = { name, am, pm ->
                vm.book(d.id, name, am, pm)
                bookingDesk = null
            }
        )
    }
}

@Composable
private fun DayHeader(day: LocalDate, onDayChange: (LocalDate) -> Unit) {
    // Minimal date control: show date + allow manual edit as ISO string.
    var text by remember(day) { mutableStateOf(day.toString()) }
    Card(shape = MaterialTheme.shapes.large) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("Selected day", style = MaterialTheme.typography.labelMedium)
                Text(day.toString(), style = MaterialTheme.typography.titleMedium)
            }
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                label = { Text("YYYY-MM-DD") },
                modifier = Modifier.width(160.dp)
            )
            Spacer(Modifier.width(8.dp))
            Button(onClick = {
                runCatching { LocalDate.parse(text.trim()) }
                    .onSuccess { onDayChange(it) }
            }) { Text("Go") }
        }
    }
}

@Composable
private fun ErrorCard(message: String, onRetry: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        shape = MaterialTheme.shapes.large
    ) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                message,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.width(8.dp))
            Button(onClick = onRetry) { Text("Retry") }
        }
    }
}
