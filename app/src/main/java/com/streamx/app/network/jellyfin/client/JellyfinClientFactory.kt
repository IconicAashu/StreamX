package com.streamx.app.network.jellyfin.client

import com.streamx.app.utils.StreamXLog
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.net.URI
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Standard Jellyfin Authentication & Identification Interceptor.
 * Sends client identification header without logging sensitive tokens or passwords.
 */
class JellyfinAuthInterceptor(
    private val tokenProvider: () -> String?
) : Interceptor {

    private val deviceId = UUID.nameUUIDFromBytes("STREAMX-ANDROID-CLIENT".toByteArray()).toString()

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val token = tokenProvider()

        val authHeader = JellyfinClientFactory.buildAuthHeader(deviceId, token)

        val requestBuilder = original.newBuilder()
            .header("X-Emby-Authorization", authHeader)
            .header("Accept", "application/json")

        if (!token.isNullOrBlank()) {
            requestBuilder.header("X-Emby-Token", token)
        }

        val request = requestBuilder.build()
        val response = chain.proceed(request)

        if (response.code == 401) {
            StreamXLog.e("JellyfinAuth", "Received HTTP 401 Unauthorized from Jellyfin server")
        }

        return response
    }
}

object JellyfinClientFactory {

    fun buildAuthHeader(deviceId: String, token: String? = null): String {
        return buildString {
            append("MediaBrowser Client=\"STREAMX\", Device=\"Android\", DeviceId=\"$deviceId\", Version=\"1.0.0\"")
            if (!token.isNullOrBlank()) {
                append(", Token=\"$token\"")
            }
        }
    }

    fun validateServerUrl(rawUrl: String): Result<String> {
        val trimmed = rawUrl.trim().trimEnd('/')
        if (trimmed.isBlank()) {
            return Result.failure(IllegalArgumentException("Server URL cannot be empty"))
        }

        if (trimmed.contains("://")) {
            val scheme = trimmed.substringBefore("://").lowercase()
            if (scheme != "http" && scheme != "https") {
                return Result.failure(IllegalArgumentException("Unsupported protocol: $scheme. Use http or https."))
            }
        }

        val normalized = if (!trimmed.startsWith("http://", ignoreCase = true) &&
            !trimmed.startsWith("https://", ignoreCase = true)
        ) {
            "http://$trimmed"
        } else {
            trimmed
        }

        return try {
            val uri = URI(normalized)
            val scheme = uri.scheme?.lowercase()
            if (scheme != "http" && scheme != "https") {
                return Result.failure(IllegalArgumentException("Unsupported protocol: $scheme. Use http or https."))
            }
            if (uri.host.isNullOrBlank()) {
                return Result.failure(IllegalArgumentException("Invalid host in URL: $normalized"))
            }
            Result.success(normalized)
        } catch (e: Exception) {
            Result.failure(IllegalArgumentException("Malformed server URL: ${e.message}"))
        }
    }

    fun createOkHttpClient(tokenProvider: () -> String?): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .addInterceptor(JellyfinAuthInterceptor(tokenProvider))
            .build()
    }

    fun createRetrofit(baseUrl: String, okHttpClient: OkHttpClient): Retrofit {
        val normalizedBase = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        return Retrofit.Builder()
            .baseUrl(normalizedBase)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }
}
