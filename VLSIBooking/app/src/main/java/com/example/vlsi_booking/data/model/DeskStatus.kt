package com.example.vlsi_booking.data.model

data class DeskStatus(
    val id: Int,
    val row: Int,
    val col: Int,
    val desk_type: String,
    val label: String,

    // thesis bookings
    val booking_am: String?,
    val booking_pm: String?,

    // staff fields (if your backend returns them)
    val holder_name: String? = null,
    val current_occupant: String? = null,
    val holder_away: Boolean? = null
)
