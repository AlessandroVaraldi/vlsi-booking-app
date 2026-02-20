package com.example.vlsi_booking.data.api

import com.example.vlsi_booking.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {
    // Configured in Gradle as BuildConfig.BACKEND_BASE_URL.
    // Must include trailing "/" for Retrofit.
    private val baseUrl: String = BuildConfig.BACKEND_BASE_URL

    private val client: OkHttpClient by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
            redactHeader("Authorization")
        }
        OkHttpClient.Builder()
            // Render free tier can take up to ~1 minute to wake up from sleep.
            .callTimeout(70, TimeUnit.SECONDS)
            .connectTimeout(70, TimeUnit.SECONDS)
            .readTimeout(70, TimeUnit.SECONDS)
            .writeTimeout(70, TimeUnit.SECONDS)
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
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(Api::class.java)
    }
}
