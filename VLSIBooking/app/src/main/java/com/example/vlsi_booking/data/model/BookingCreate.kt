package com.example.vlsi_booking.data.model

data class BookingCreate(
    val desk_id: Int,
    val day: String,       // YYYY-MM-DD
    val booked_by: String,
    val am: Boolean,
    val pm: Boolean
)
