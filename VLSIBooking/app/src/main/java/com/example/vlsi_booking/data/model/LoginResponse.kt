package com.example.vlsi_booking.data.model

data class LoginResponse(
    val token: String,
    val username: String,
    val expires_at: String
)
