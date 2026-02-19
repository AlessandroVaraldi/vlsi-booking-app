package com.example.vlsi_booking.data.model

data class ChangePasswordRequest(
    val old_password: String,
    val new_password: String
)
