package com.example.labdesks.data

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface Api {
    @GET("desks")
    suspend fun getDesks(@Query("day") day: String): List<DeskStatus>

    @POST("bookings")
    suspend fun createBooking(@Body req: BookingCreate): List<BookingOut>
}
