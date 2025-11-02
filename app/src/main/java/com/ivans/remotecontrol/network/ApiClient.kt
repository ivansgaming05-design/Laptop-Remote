package com.ivans.remotecontrol.network

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import com.ivans.remotecontrol.utils.PreferencesManager
import android.util.Log

object ApiClient {

    private var retrofit: Retrofit? = null
    private var currentBaseUrl: String = "http://localhost:8080/"
    private var preferencesManager: PreferencesManager? = null

    val apiService: ApiService
        get() = getRetrofit().create(ApiService::class.java)

    fun setPreferencesManager(pm: PreferencesManager) {
        preferencesManager = pm
        Log.d("ApiClient", "PreferencesManager set")
    }

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
            .addInterceptor { chain ->
                val originalRequest = chain.request()

                // Skip auth for specific endpoints
                val skipAuthPaths = listOf(
                    "/api/ping",
                    "/api/auth/challenge",
                    "/api/auth/unlock"
                )

                val shouldSkipAuth = skipAuthPaths.any { path ->
                    originalRequest.url.encodedPath.contains(path)
                }

                if (shouldSkipAuth) {
                    // Proceed without auth header
                    val response = chain.proceed(originalRequest)
                    Log.d("ApiClient", "Request to ${originalRequest.url} (no auth required): ${response.code}")
                    return@addInterceptor response
                }

                // Add Authorization header for protected endpoints
                val sessionToken = getSessionToken()
                val requestBuilder = originalRequest.newBuilder()

                if (sessionToken.isNotEmpty()) {
                    requestBuilder.header("Authorization", "Bearer $sessionToken")
                    Log.d("ApiClient", "Adding auth header for ${originalRequest.url}")
                } else {
                    Log.d("ApiClient", "No session token available for ${originalRequest.url}")
                }

                val newRequest = requestBuilder.build()
                val response = chain.proceed(newRequest)

                // Handle auth failures
                when (response.code) {
                    401 -> {
                        Log.w("ApiClient", "Unauthorized request to ${originalRequest.url} - clearing session")
                        clearSession()
                    }
                    423 -> {
                        Log.w("ApiClient", "Server locked for ${originalRequest.url}")
                        clearSession()
                    }
                    else -> {
                        Log.d("ApiClient", "Request to ${originalRequest.url}: ${response.code}")
                    }
                }

                response
            }
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

        Log.d("ApiClient", "Creating Retrofit with URL: $currentBaseUrl")

        return Retrofit.Builder()
            .baseUrl(currentBaseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    fun updateServerUrl(newUrl: String) {
        val formattedUrl = if (newUrl.endsWith("/")) newUrl else "$newUrl/"
        Log.d("ApiClient", "Updating URL from $currentBaseUrl to $formattedUrl")

        // Force update even if URLs appear the same
        currentBaseUrl = formattedUrl
        retrofit = null // Force recreation on next access

        Log.d("ApiClient", "URL updated to $currentBaseUrl")
    }

    fun getCurrentUrl(): String {
        return currentBaseUrl
    }

    private fun getSessionToken(): String {
        val token = preferencesManager?.getSessionToken() ?: ""
        if (token.isNotEmpty()) {
            Log.d("ApiClient", "Using session token: ${token.take(10)}...")
        }
        return token
    }

    fun clearSession() {
        Log.d("ApiClient", "Clearing session token")
        preferencesManager?.clearSessionToken()
    }

    fun hasValidSession(): Boolean {
        val token = getSessionToken()
        val isExpired = preferencesManager?.isSessionExpired() ?: true
        return token.isNotEmpty() && !isExpired
    }

    suspend fun testConnection(): Boolean {
        return try {
            val response = apiService.ping()
            response.isSuccessful
        } catch (e: Exception) {
            Log.e("ApiClient", "Connection test failed", e)
            false
        }
    }

    suspend fun checkAuthStatus(): AuthResult {
        return try {
            val response = apiService.getAuthStatus()
            if (response.isSuccessful) {
                val status = response.body()
                when {
                    status?.locked == true -> AuthResult.LOCKED
                    status?.temporarilyLocked == true -> AuthResult.TEMPORARILY_LOCKED
                    else -> AuthResult.UNLOCKED
                }
            } else {
                AuthResult.ERROR
            }
        } catch (e: Exception) {
            Log.e("ApiClient", "Auth status check failed", e)
            AuthResult.ERROR
        }
    }

    suspend fun performAuthChallenge(password: String): AuthResult {
        return try {
            // Step 1: Get challenge
            val challengeResponse = apiService.getAuthChallenge()
            if (!challengeResponse.isSuccessful) {
                return when (challengeResponse.code()) {
                    429 -> AuthResult.TEMPORARILY_LOCKED
                    else -> AuthResult.ERROR
                }
            }

            val challenge = challengeResponse.body()?.challenge
            if (challenge == null) {
                return AuthResult.ERROR
            }

            // Step 2: Generate HMAC response
            val hmacResponse = generateHMACResponse(password, challenge)

            // Step 3: Submit unlock request
            val unlockResponse = apiService.unlockServer(mapOf("response" to hmacResponse))
            if (unlockResponse.isSuccessful) {
                val unlockData = unlockResponse.body()
                val sessionToken = unlockData?.sessionToken

                if (sessionToken != null) {
                    // Save session token
                    preferencesManager?.setSessionToken(sessionToken)
                    preferencesManager?.setLastAuthTime()
                    preferencesManager?.clearFailedAttempts()

                    Log.d("ApiClient", "Authentication successful")
                    AuthResult.SUCCESS
                } else {
                    AuthResult.ERROR
                }
            } else {
                when (unlockResponse.code()) {
                    401 -> {
                        preferencesManager?.incrementFailedAttempts()
                        AuthResult.INVALID_PASSWORD
                    }
                    429 -> AuthResult.TEMPORARILY_LOCKED
                    else -> AuthResult.ERROR
                }
            }
        } catch (e: Exception) {
            Log.e("ApiClient", "Auth challenge failed", e)
            AuthResult.ERROR
        }
    }

    private fun generateHMACResponse(password: String, challenge: String): String {
        return try {
            val secretKeySpec = javax.crypto.spec.SecretKeySpec(
                password.toByteArray(Charsets.UTF_8),
                "HmacSHA256"
            )
            val mac = javax.crypto.Mac.getInstance("HmacSHA256")
            mac.init(secretKeySpec)
            val hmacBytes = mac.doFinal(challenge.toByteArray(Charsets.UTF_8))

            // Convert to hex string
            hmacBytes.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            Log.e("ApiClient", "HMAC generation failed", e)
            throw RuntimeException("Failed to generate HMAC response", e)
        }
    }

    enum class AuthResult {
        SUCCESS,
        LOCKED,
        TEMPORARILY_LOCKED,
        INVALID_PASSWORD,
        ERROR,
        UNLOCKED
    }
}