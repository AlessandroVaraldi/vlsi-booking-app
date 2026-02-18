package com.example.vlsi_booking.data.model

data class BookingOut(
    val id: Int,
    val desk_id: Int,
    val day: String,
    val slot: String,
    val booked_by: String
)
