package com.example.vlsi_booking.data.api

import com.example.vlsi_booking.data.model.DeskStatus
import com.example.vlsi_booking.data.model.BookingCreate
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface Api {
    @GET("desks")
    suspend fun getDesks(@Query("day") day: String): List<DeskStatus>

    @POST("bookings")
    suspend fun createBooking(@Body req: BookingCreate): List<Map<String, Any>>
}
