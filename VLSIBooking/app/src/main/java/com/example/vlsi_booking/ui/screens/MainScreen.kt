package com.example.vlsi_booking.ui.screens

import android.app.DatePickerDialog
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.vlsi_booking.data.model.DeskStatus
import com.example.vlsi_booking.ui.MainViewModel
import com.example.vlsi_booking.ui.components.BookingDialog
import java.time.LocalDate
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.random.Random

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    vm: MainViewModel,
    defaultBookingName: String = "",
    onLogout: () -> Unit,
    onChangePassword: (
        oldPassword: String,
        newPassword: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) -> Unit
) {
    val state by vm.state.collectAsState()

    var bookingDesk by remember { mutableStateOf<DeskStatus?>(null) }
    var infoDesk by remember { mutableStateOf<DeskStatus?>(null) }
    var showMyBookings by remember { mutableStateOf(false) }

    var showChangePassword by remember { mutableStateOf(false) }

    var breakoutStatus by remember { mutableStateOf(BreakoutStatus.INACTIVE) }
    val isBreakoutActive = breakoutStatus != BreakoutStatus.INACTIVE
    val brokenDeskIds = remember { mutableStateMapOf<Int, Boolean>() }
    var breakoutDesksSnapshot by remember { mutableStateOf<List<DeskStatus>>(emptyList()) }
    var breakoutStartToken by remember { mutableIntStateOf(0) }

    LaunchedEffect(isBreakoutActive) {
        if (isBreakoutActive) {
            bookingDesk = null
            infoDesk = null
            showMyBookings = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("VLSI desks") },
                actions = {
                    TextButton(
                        onClick = { vm.refresh() },
                        enabled = !isBreakoutActive
                    ) { Text("Refresh") }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(12.dp)
        ) {
            HeaderRow(date = state.selectedDate, onPickDate = vm::setDate, enabled = !isBreakoutActive)
            Spacer(Modifier.height(12.dp))

            if (state.isLoading) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Spacer(Modifier.height(12.dp))
            }

            state.errorMessage?.let { msg ->
                ErrorCard(message = msg, onDismiss = vm::clearError)
                Spacer(Modifier.height(12.dp))
            }

            @Suppress("UnusedBoxWithConstraintsScope")
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                val gap = 10.dp
                val columns = 6
                val tileWidth = (maxWidth - gap * (columns - 1)) / columns
                val labelAreaHeight = 36.dp

                Text(
                    text = "EXIT",
                    modifier = Modifier
                        .width(tileWidth)
                        .align(Alignment.TopStart)
                        .padding(top = 6.dp),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Clip
                )

                Text(
                    text = "EXIT",
                    modifier = Modifier
                        .width(tileWidth)
                        .align(Alignment.TopEnd)
                        .padding(top = 6.dp),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Clip
                )

                DeskGrid(
                    desks = if (isBreakoutActive) breakoutDesksSnapshot else state.desks,
                    onDeskClick = { desk ->
                        if (isBreakoutActive) return@DeskGrid
                        when (deskKind(desk)) {
                            DeskKind.BOOKABLE_GREEN,
                            DeskKind.BOOKABLE_YELLOW -> bookingDesk = desk
                            DeskKind.RED_INFO -> infoDesk = desk
                            DeskKind.HIDDEN -> Unit
                        }
                    },
                    brokenDeskIds = if (isBreakoutActive) brokenDeskIds.keys else emptySet(),
                    clicksEnabled = !isBreakoutActive,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = labelAreaHeight)
                )

                BreakoutOverlay(
                    status = breakoutStatus,
                    startToken = breakoutStartToken,
                    desks = breakoutDesksSnapshot,
                    brokenDeskIds = brokenDeskIds,
                    modifier = Modifier.fillMaxSize(),
                    onLose = {
                        breakoutStatus = BreakoutStatus.INACTIVE
                        brokenDeskIds.clear()
                    },
                    onWin = {
                        breakoutStatus = BreakoutStatus.WON
                    }
                )

                if (breakoutStatus == BreakoutStatus.WON) {
                    Text(
                        text = "YOU WON",
                        modifier = Modifier.align(Alignment.Center),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            vm.loadMyBookings(defaultBookingName)
                            showMyBookings = true
                        },
                        enabled = !isBreakoutActive,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "BOOKINGS",
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Clip
                        )
                    }

                    Button(
                        onClick = {
                            if (isBreakoutActive) {
                                breakoutStatus = BreakoutStatus.INACTIVE
                                brokenDeskIds.clear()
                            } else {
                                breakoutDesksSnapshot = state.desks
                                brokenDeskIds.clear()
                                breakoutStatus = BreakoutStatus.PLAYING
                                breakoutStartToken += 1
                            }
                        },
                        enabled = true,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = if (isBreakoutActive) "BACK" else "BREAKOUT",
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Clip
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            showChangePassword = true
                        },
                        enabled = !isBreakoutActive,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "PASSWORD",
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Clip
                        )
                    }

                    Button(
                        onClick = onLogout,
                        enabled = !isBreakoutActive,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "LOGOUT",
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Clip
                        )
                    }
                }
            }
        }
    }

    if (!isBreakoutActive) bookingDesk?.let { desk ->
        val amFree = desk.booking_am == null
        val pmFree = desk.booking_pm == null

        BookingDialog(
            desk = desk,
            amAvailable = amFree,
            pmAvailable = pmFree,
            initialName = defaultBookingName,
            onDismiss = { bookingDesk = null },
            onConfirm = { name: String, am: Boolean, pm: Boolean ->
                vm.bookDesk(desk.id, name, am, pm)
                bookingDesk = null
            }
        )
    }

    if (!isBreakoutActive) infoDesk?.let { desk ->
        DeskInfoDialog(
            desk = desk,
            onDismiss = { infoDesk = null }
        )
    }

    if (!isBreakoutActive && showMyBookings) {
        val byDeskId = remember(state.desks) { state.desks.associateBy { it.id } }
        AlertDialog(
            onDismissRequest = { showMyBookings = false },
            title = { Text("Le mie prenotazioni") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Data: ${state.selectedDate}")
                    if (state.isMyBookingsLoading) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    } else if (state.myBookings.isEmpty()) {
                        Text("Nessuna prenotazione")
                    } else {
                        state.myBookings.forEach { b ->
                            val label = byDeskId[b.desk_id]?.label ?: "Desk ${b.desk_id}"
                            Text("$label — ${b.slot}")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showMyBookings = false }) { Text("Chiudi") }
            }
        )
    }

    if (!isBreakoutActive && showChangePassword) {
        ChangePasswordDialog(
            onDismiss = { showChangePassword = false },
            onConfirm = { oldP, newP, onOk, onErr ->
                onChangePassword(oldP, newP, onOk, onErr)
            }
        )
    }
}

@Composable
private fun ChangePasswordDialog(
    onDismiss: () -> Unit,
    onConfirm: (
        oldPassword: String,
        newPassword: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current

    var oldPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var newPassword2 by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val canSubmit = oldPassword.isNotBlank() && newPassword.isNotBlank() && newPassword2.isNotBlank() && !isSubmitting

    AlertDialog(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        title = { Text("Cambia password") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = oldPassword,
                    onValueChange = { oldPassword = it; error = null },
                    label = { Text("Password attuale") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    enabled = !isSubmitting,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = newPassword,
                    onValueChange = { newPassword = it; error = null },
                    label = { Text("Nuova password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    enabled = !isSubmitting,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = newPassword2,
                    onValueChange = { newPassword2 = it; error = null },
                    label = { Text("Ripeti nuova password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    enabled = !isSubmitting,
                    modifier = Modifier.fillMaxWidth()
                )

                if (isSubmitting) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }

                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = canSubmit,
                onClick = {
                    if (newPassword != newPassword2) {
                        error = "Le nuove password non coincidono"
                        return@TextButton
                    }
                    if (newPassword.length < 4) {
                        error = "Password troppo corta"
                        return@TextButton
                    }
                    isSubmitting = true
                    onConfirm(
                        oldPassword,
                        newPassword,
                        {
                            isSubmitting = false
                            Toast.makeText(context, "Password cambiata", Toast.LENGTH_SHORT).show()
                            onDismiss()
                        },
                        { msg ->
                            isSubmitting = false
                            error = msg
                        }
                    )
                }
            ) { Text("Salva") }
        },
        dismissButton = {
            TextButton(
                enabled = !isSubmitting,
                onClick = onDismiss
            ) { Text("Annulla") }
        }
    )
}

@Composable
private fun HeaderRow(date: LocalDate, onPickDate: (LocalDate) -> Unit, enabled: Boolean) {
    val context = androidx.compose.ui.platform.LocalContext.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "Date: $date",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.weight(1f))
        FilledTonalButton(enabled = enabled, onClick = {
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
private fun DeskGrid(
    desks: List<DeskStatus>,
    onDeskClick: (DeskStatus) -> Unit,
    brokenDeskIds: Set<Int> = emptySet(),
    clicksEnabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    // Keep all 24 items so layout stays 4x6 in the correct positions
    LazyVerticalGrid(
        columns = GridCells.Fixed(6),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier
    ) {
        items(desks, key = { it.id }) { desk ->
            DeskTile(
                desk = desk,
                isBroken = brokenDeskIds.contains(desk.id),
                clicksEnabled = clicksEnabled,
                onClick = { onDeskClick(desk) }
            )
        }
    }
}


@Composable
private fun DeskTile(desk: DeskStatus, isBroken: Boolean, clicksEnabled: Boolean, onClick: () -> Unit) {
    val kind = deskKind(desk)

    // For blocked desks: invisible placeholder that still occupies space
    if (kind == DeskKind.HIDDEN || isBroken) {
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
            .clickable(enabled = clicksEnabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = desk.label,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

private enum class BreakoutStatus { INACTIVE, PLAYING, WON }

@Composable
private fun BreakoutOverlay(
    status: BreakoutStatus,
    startToken: Int,
    desks: List<DeskStatus>,
    brokenDeskIds: MutableMap<Int, Boolean>,
    modifier: Modifier = Modifier,
    onLose: () -> Unit,
    onWin: () -> Unit
) {
    if (status == BreakoutStatus.INACTIVE) return

    @Suppress("UnusedBoxWithConstraintsScope")
    BoxWithConstraints(
        modifier = modifier
    ) {
        val density = androidx.compose.ui.platform.LocalDensity.current
        val gameColor = MaterialTheme.colorScheme.onBackground

        val columns = 6
        val gapPx = with(density) { 10.dp.toPx() }
        val labelAreaPx = with(density) { 36.dp.toPx() }

        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }

        val tilePx = (widthPx - gapPx * (columns - 1)) / columns
        val rows = max(1, ceil(desks.size / columns.toFloat()).toInt())

        val gridBottomPx = labelAreaPx + rows * tilePx + max(0, rows - 1) * gapPx

        val paddleHeightPx = with(density) { 10.dp.toPx() }
        val paddleWidthPx = (widthPx - gapPx) / 4f // half of a button (button is ~ (width-gap)/2)
        val paddleMarginBottomPx = with(density) { 8.dp.toPx() }
        val paddleY = heightPx - paddleHeightPx - paddleMarginBottomPx

        val ballRadiusPx = with(density) { 8.dp.toPx() }
        val baseSpeedPxPerSec = with(density) { 520.dp.toPx() }

        var paddleCenterX by remember { mutableStateOf(widthPx / 2f) }
        var ballPos by remember { mutableStateOf(Offset(widthPx / 2f, paddleY - ballRadiusPx - 1f)) }
        var ballVel by remember { mutableStateOf(Offset(0f, -baseSpeedPxPerSec)) }
        var countdown by remember { mutableIntStateOf(0) }

        fun clampPaddle(x: Float): Float {
            val half = paddleWidthPx / 2f
            return x.coerceIn(half, widthPx - half)
        }

        LaunchedEffect(status, startToken, widthPx, heightPx) {
            if (status != BreakoutStatus.PLAYING) return@LaunchedEffect

            paddleCenterX = widthPx / 2f
            val angleDeg = Random.nextFloat() * (120f - 60f) + 60f
            val angleRad = Math.toRadians(angleDeg.toDouble())
            val vx = (baseSpeedPxPerSec * kotlin.math.cos(angleRad)).toFloat()
            val vy = (-baseSpeedPxPerSec * kotlin.math.sin(angleRad)).toFloat()
            ballVel = Offset(vx, vy)
            ballPos = Offset(paddleCenterX, paddleY - ballRadiusPx - 1f)
        }

        LaunchedEffect(status, startToken) {
            if (status != BreakoutStatus.PLAYING) {
                countdown = 0
                return@LaunchedEffect
            }

            countdown = 3
            repeat(3) {
                delay(1000)
                if (status != BreakoutStatus.PLAYING) return@LaunchedEffect
                countdown -= 1
            }
        }

        LaunchedEffect(status, startToken) {
            if (status != BreakoutStatus.PLAYING) return@LaunchedEffect
            var lastNanos = 0L

            while (true) {
                val now = withFrameNanos { it }
                if (lastNanos == 0L) {
                    lastNanos = now
                    continue
                }
                if (status != BreakoutStatus.PLAYING) break

                if (countdown > 0) {
                    // Keep the ball parked above the paddle during countdown
                    ballPos = Offset(paddleCenterX, paddleY - ballRadiusPx - 1f)
                    lastNanos = now
                    continue
                }

                val dt = (now - lastNanos) / 1_000_000_000f
                lastNanos = now

                val prevPos = ballPos
                var nextPos = prevPos + ballVel * dt
                var nextVel = ballVel

                // Wall collisions
                if (nextPos.x - ballRadiusPx <= 0f) {
                    nextPos = nextPos.copy(x = ballRadiusPx)
                    nextVel = nextVel.copy(x = abs(nextVel.x))
                } else if (nextPos.x + ballRadiusPx >= widthPx) {
                    nextPos = nextPos.copy(x = widthPx - ballRadiusPx)
                    nextVel = nextVel.copy(x = -abs(nextVel.x))
                }

                if (nextPos.y - ballRadiusPx <= 0f) {
                    nextPos = nextPos.copy(y = ballRadiusPx)
                    nextVel = nextVel.copy(y = abs(nextVel.y))
                }

                // Paddle collision (only when moving down)
                val paddleLeft = paddleCenterX - paddleWidthPx / 2f
                val paddleRight = paddleCenterX + paddleWidthPx / 2f
                val paddleTop = paddleY
                val paddleBottom = paddleY + paddleHeightPx
                val prevBottom = prevPos.y + ballRadiusPx
                val nextBottom = nextPos.y + ballRadiusPx

                val crossesPaddleTop =
                    nextVel.y > 0f &&
                        prevBottom <= paddleTop &&
                        nextBottom >= paddleTop

                val withinPaddleX =
                    nextPos.x >= (paddleLeft - ballRadiusPx) &&
                        nextPos.x <= (paddleRight + ballRadiusPx)

                if (crossesPaddleTop && withinPaddleX) {
                    nextPos = nextPos.copy(y = paddleTop - ballRadiusPx - 0.5f)

                    // Breakout-like bounce: angle depends on where you hit the paddle
                    val speed = hypot(nextVel.x, nextVel.y).coerceAtLeast(baseSpeedPxPerSec)
                    val rel = ((nextPos.x - paddleCenterX) / (paddleWidthPx / 2f)).coerceIn(-1f, 1f)
                    val maxBounceAngleRad = Math.toRadians(75.0).toFloat()
                    val angle = rel * maxBounceAngleRad
                    val newVx = (speed * kotlin.math.sin(angle.toDouble())).toFloat()
                    val newVy = (-speed * kotlin.math.cos(angle.toDouble())).toFloat()
                    nextVel = Offset(newVx, newVy)
                } else if (
                    // Safety: if we end up inside the paddle while moving down, push out and reflect
                    nextVel.y > 0f &&
                        nextPos.y + ballRadiusPx >= paddleTop &&
                        nextPos.y - ballRadiusPx <= paddleBottom &&
                        nextPos.x >= paddleLeft &&
                        nextPos.x <= paddleRight
                ) {
                    nextPos = nextPos.copy(y = paddleTop - ballRadiusPx - 0.5f)
                    nextVel = nextVel.copy(y = -abs(nextVel.y))
                }

                // Brick collision
                if (status == BreakoutStatus.PLAYING) {
                    val ballRect = Rect(
                        left = nextPos.x - ballRadiusPx,
                        top = nextPos.y - ballRadiusPx,
                        right = nextPos.x + ballRadiusPx,
                        bottom = nextPos.y + ballRadiusPx
                    )

                    var brickHit = false
                    for (index in desks.indices) {
                        val desk = desks[index]
                        if (deskKind(desk) == DeskKind.HIDDEN) continue
                        if (brokenDeskIds.containsKey(desk.id)) continue

                        val col = index % columns
                        val row = index / columns
                        val left = col * (tilePx + gapPx)
                        val top = labelAreaPx + row * (tilePx + gapPx)
                        val right = left + tilePx
                        val bottom = top + tilePx

                        val brickRect = Rect(left, top, right, bottom)
                        if (brickRect.overlaps(ballRect)) {
                            brokenDeskIds[desk.id] = true

                            val overlapLeft = ballRect.right - brickRect.left
                            val overlapRight = brickRect.right - ballRect.left
                            val overlapTop = ballRect.bottom - brickRect.top
                            val overlapBottom = brickRect.bottom - ballRect.top
                            val minOverlap = min(min(overlapLeft, overlapRight), min(overlapTop, overlapBottom))
                            nextVel = if (minOverlap == overlapLeft || minOverlap == overlapRight) {
                                nextVel.copy(x = -nextVel.x)
                            } else {
                                nextVel.copy(y = -nextVel.y)
                            }

                            brickHit = true
                            break
                        }
                    }

                    if (brickHit) {
                        val breakableCount = desks.count { deskKind(it) != DeskKind.HIDDEN }
                        if (breakableCount > 0 && brokenDeskIds.size >= breakableCount) {
                            onWin()
                            break
                        }
                    }
                }

                // Lose condition
                if (nextPos.y - ballRadiusPx > heightPx) {
                    onLose()
                    break
                }

                ballPos = nextPos
                ballVel = nextVel
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(status) {
                    if (status != BreakoutStatus.PLAYING) return@pointerInput
                    detectDragGestures(
                        onDragStart = { paddleCenterX = clampPaddle(it.x) },
                        onDrag = { change, _ ->
                            change.consume()
                            paddleCenterX = clampPaddle(change.position.x)
                        }
                    )
                }
        ) {
            if (status == BreakoutStatus.PLAYING && countdown > 0) {
                val countdownCenterYPx = (min(gridBottomPx, paddleY) + paddleY) / 2f
                val countdownYDp = with(density) { (countdownCenterYPx - 28f).toDp() }
                Text(
                    text = countdown.toString(),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .offset(y = countdownYDp),
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold,
                    color = gameColor
                )
            }

            androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                if (status == BreakoutStatus.PLAYING) {
                    // Paddle
                    drawRoundRect(
                        color = gameColor,
                        topLeft = Offset(paddleCenterX - paddleWidthPx / 2f, paddleY),
                        size = androidx.compose.ui.geometry.Size(paddleWidthPx, paddleHeightPx),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(paddleHeightPx / 2f, paddleHeightPx / 2f),
                        style = Fill
                    )

                    // Ball
                    drawCircle(
                        color = gameColor,
                        radius = ballRadiusPx,
                        center = ballPos,
                        style = Fill
                    )
                }
            }
        }
    }
}

private operator fun Offset.plus(other: Offset): Offset = Offset(x + other.x, y + other.y)

private operator fun Offset.times(scalar: Float): Offset = Offset(x * scalar, y * scalar)


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
