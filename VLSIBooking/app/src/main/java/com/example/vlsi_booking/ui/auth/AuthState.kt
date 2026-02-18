package com.example.vlsi_booking.ui.auth

data class AuthState(
    val isLoading: Boolean = true,
    val isLoggedIn: Boolean = false,
    val username: String = "",
    val token: String = "",
    val errorMessage: String? = null
)
