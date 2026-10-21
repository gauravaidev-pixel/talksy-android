package com.talksy.app.data.remote

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.talksy.app.BuildConfig
import com.talksy.app.data.local.SessionStore
import kotlinx.serialization.json.Json
import okhttp3.Authenticator
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit

object ApiClient {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
    }

    fun create(session: SessionStore): ApiService {
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC
            else HttpLoggingInterceptor.Level.NONE
        }

        val client = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val builder = chain.request().newBuilder()
                    .header("Accept", "application/json")
                // tokenBlocking(): never send a request without the token just because the async warm-up is still running
                session.tokenBlocking()?.let { builder.header("Authorization", "Bearer $it") }
                chain.proceed(builder.build())
            }
            .authenticator(refreshAuthenticator(session, BuildConfig.BASE_URL.trimEnd('/') + "/"))
            .addInterceptor(logging)
            .build()

        val base = BuildConfig.BASE_URL.trimEnd('/') + "/"
        return Retrofit.Builder()
            .baseUrl(base)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(ApiService::class.java)
    }

    // ------------------------------------------------------------------ silent token refresh

    private val refreshLock = Any()
    private val plainClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private fun responseCount(response: Response): Int {
        var n = 1
        var r: Response? = response
        while (r?.priorResponse != null) { n++; r = r.priorResponse }
        return n
    }

    /**
     * On 401 (expired / invalid access token) get a new access token with the refresh token and retry once.
     * Only if the server explicitly rejects the refresh token do we log the user out (a network error never does).
     */
    private fun refreshAuthenticator(session: SessionStore, baseUrl: String) = Authenticator { _, response ->
        val sent = response.request.header("Authorization") ?: return@Authenticator null // login etc: nothing to refresh
        if (response.request.url.encodedPath.endsWith("/api/auth/refresh")) return@Authenticator null
        if (responseCount(response) >= 2) return@Authenticator null
        synchronized(refreshLock) {
            val current = session.accessToken
            if (current != null && "Bearer $current" != sent) {
                // another request already refreshed while we were waiting for the lock
                return@Authenticator response.request.newBuilder().header("Authorization", "Bearer $current").build()
            }
            val refresh = session.refreshTokenBlocking()
            if (refresh.isNullOrBlank()) {
                session.clearBlocking()
                return@Authenticator null
            }
            val fresh = requestNewAccessToken(baseUrl, refresh)
            if (fresh == null) {
                session.clearBlocking()
                return@Authenticator null
            }
            session.saveAccessBlocking(fresh)
            response.request.newBuilder().header("Authorization", "Bearer $fresh").build()
        }
    }

    /** Returns the new access token, null if the server rejected the refresh token, throws IOException on network errors. */
    private fun requestNewAccessToken(baseUrl: String, refreshToken: String): String? {
        val request = Request.Builder()
            .url(baseUrl + "api/auth/refresh")
            .post("".toRequestBody(null))
            .header("Authorization", "Bearer $refreshToken")
            .header("Accept", "application/json")
            .build()
        plainClient.newCall(request).execute().use { res ->
            if (res.code == 401 || res.code == 422 || res.code == 404) return null
            if (!res.isSuccessful) throw IOException("Token refresh failed (${res.code})")
            val body = res.body?.string().orEmpty()
            return JSONObject(body).optString("access_token").ifBlank { null }
        }
    }
}
