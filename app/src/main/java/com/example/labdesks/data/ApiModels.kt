package com.example.labdesks.data

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class DeskStatus(
    val id: Int,
    val row: Int,
    val col: Int,
    val desk_type: String,
    val label: String,

    // Staff fields (can be null)
    val holder_name: String? = null,
    val current_occupant: String? = null,
    val holder_away: Boolean = false,
    val away_start: String? = null,
    val away_end: String? = null,
    val away_temp_occupant: String? = null,

    // Thesis fields (can be null)
    val booking_am: String? = null,
    val booking_pm: String? = null,
)

@JsonClass(generateAdapter = true)
data class BookingCreate(
    val desk_id: Int,
    val day: String,        // "YYYY-MM-DD"
    val booked_by: String,
    val am: Boolean,
    val pm: Boolean
)

@JsonClass(generateAdapter = true)
data class BookingOut(
    val id: Int,
    val desk_id: Int,
    val day: String,
    val slot: String,       // "AM" / "PM"
    val booked_by: String
)
