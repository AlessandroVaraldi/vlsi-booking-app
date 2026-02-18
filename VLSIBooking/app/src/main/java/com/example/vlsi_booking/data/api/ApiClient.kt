package com.example.vlsi_booking.data.api

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object ApiClient {
    // Cambia questa in base a emulatore/telefono
    // Emulatore: "http://10.0.2.2:8000/"
    // Telefono:  "http://192.168.1.20:8000/"
    private const val BASE_URL = "https://pc-lse-1860.polito.it/"
    // private const val BASE_URL = "https://labdesk.lan/"

    private val client: OkHttpClient by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
            redactHeader("Authorization")
        }
        OkHttpClient.Builder()
            .addInterceptor { chain ->
                val original = chain.request()
                val token = AuthTokenHolder.token
                val req = if (!token.isNullOrBlank()) {
                    original.newBuilder()
                        .header("Authorization", "Bearer $token")
                        .build()
                } else {
                    original
                }
                chain.proceed(req)
            }
            .addInterceptor(logging)
            .build()
    }

    val api: Api by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(Api::class.java)
    }
}
