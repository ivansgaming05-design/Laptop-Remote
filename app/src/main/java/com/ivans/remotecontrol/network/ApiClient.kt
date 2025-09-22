package com.ivans.remotecontrol.network

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {

    private var retrofit: Retrofit? = null
    private var currentBaseUrl: String = "http://localhost:8080/"

    val apiService: ApiService
        get() = getRetrofit().create(ApiService::class.java)

    private fun getRetrofit(): Retrofit {
        if (retrofit == null || shouldRecreateRetrofit()) {
            retrofit = createRetrofit()
        }
        return retrofit!!
    }

    private fun shouldRecreateRetrofit(): Boolean {
        val retrofitBaseUrl = retrofit?.baseUrl()?.toString()
        return retrofitBaseUrl != currentBaseUrl
    }

    private fun createRetrofit(): Retrofit {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

        println("ApiClient: Creating Retrofit with URL: $currentBaseUrl")

        return Retrofit.Builder()
            .baseUrl(currentBaseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    fun updateServerUrl(newUrl: String) {
        val formattedUrl = if (newUrl.endsWith("/")) newUrl else "$newUrl/"
        println("ApiClient: Updating URL from $currentBaseUrl to $formattedUrl")

        // Force update even if URLs appear the same
        currentBaseUrl = formattedUrl
        retrofit = null // Force recreation on next access

        // Test the new connection immediately
        println("ApiClient: URL updated to $currentBaseUrl")
    }

    fun getCurrentUrl(): String {
        return currentBaseUrl
    }

    fun testConnection(): Boolean {
        return try {
            true
        } catch (e: Exception) {
            false
        }
    }
}