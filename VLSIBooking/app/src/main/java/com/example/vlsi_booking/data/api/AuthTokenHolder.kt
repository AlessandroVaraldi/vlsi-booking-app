package com.example.vlsi_booking.data.api

object AuthTokenHolder {
    @Volatile
    var token: String? = null
}
