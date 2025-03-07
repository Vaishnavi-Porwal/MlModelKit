package com.example.mlmodelkit

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {
    private const val BASE_URL = "http://192.168.170.116:5000"  // Ensure your device and backend are on the same network

    private val logger = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY  // Logs requests & responses (use Level.NONE in production)
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)  // Increased timeout duration
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(logger)  // Add logging interceptor
        .build()

    val instance: TextExtractionApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)  // Use custom OkHttpClient
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(TextExtractionApiService::class.java)
    }
}
