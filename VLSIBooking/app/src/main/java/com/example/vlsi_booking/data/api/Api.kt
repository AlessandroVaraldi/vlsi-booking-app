package com.example.vlsi_booking.data.api

import com.example.vlsi_booking.data.model.DeskStatus
import com.example.vlsi_booking.data.model.BookingCreate
import com.example.vlsi_booking.data.model.BookingOut
import com.example.vlsi_booking.data.model.LoginRequest
import com.example.vlsi_booking.data.model.LoginResponse
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface Api {
    @GET("desks")
    suspend fun getDesks(@Query("day") day: String): List<DeskStatus>

    @GET("bookings")
    suspend fun getBookings(@Query("day") day: String): List<BookingOut>

    @POST("auth/login")
    suspend fun login(@Body req: LoginRequest): LoginResponse

    @POST("auth/signup")
    suspend fun signup(@Body req: LoginRequest): LoginResponse

    @POST("bookings")
    suspend fun createBooking(@Body req: BookingCreate): List<BookingOut>
}
