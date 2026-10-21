package com.talksy.app.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

private val Context.dataStore by preferencesDataStore(name = "talksy_session")

/**
 * Persists the JWT + a cached copy of the user's basic profile.
 * An in-memory copy of the token is kept so the OkHttp interceptor can read it
 * synchronously on every request.
 */
class SessionStore(private val context: Context) {

    private object Keys {
        val ACCESS = stringPreferencesKey("access_token")
        val REFRESH = stringPreferencesKey("refresh_token")
        val USER_ID = stringPreferencesKey("user_id")
        val NAME = stringPreferencesKey("name")
        val USERNAME = stringPreferencesKey("username")
        val PICTURE = stringPreferencesKey("picture")
        val THEME = stringPreferencesKey("theme")
    }

    @Volatile var accessToken: String? = null
        private set

    val isLoggedInFlow: Flow<Boolean> = context.dataStore.data.map { !it[Keys.ACCESS].isNullOrBlank() }
    val userIdFlow: Flow<String?> = context.dataStore.data.map { it[Keys.USER_ID] }

    /** "system" | "light" | "dark" */
    val themeFlow: Flow<String> = context.dataStore.data.map { it[Keys.THEME] ?: "system" }
    suspend fun setTheme(mode: String) { context.dataStore.edit { it[Keys.THEME] = mode } }

    suspend fun load() {
        accessToken = context.dataStore.data.first()[Keys.ACCESS]
    }

    // ---- used from OkHttp threads (never the main thread), so blocking is fine ----

    /** The access token, loading it from disk first if the async warm-up hasn't finished yet. */
    fun tokenBlocking(): String? = accessToken ?: runBlocking { load(); accessToken }

    fun refreshTokenBlocking(): String? = runBlocking { context.dataStore.data.first()[Keys.REFRESH] }

    fun saveAccessBlocking(token: String) = runBlocking {
        accessToken = token
        context.dataStore.edit { it[Keys.ACCESS] = token }
    }

    fun clearBlocking() = runBlocking { clear() }

    suspend fun save(access: String, refresh: String, userId: String, name: String, username: String, picture: String) {
        accessToken = access
        context.dataStore.edit {
            it[Keys.ACCESS] = access
            it[Keys.REFRESH] = refresh
            it[Keys.USER_ID] = userId
            it[Keys.NAME] = name
            it[Keys.USERNAME] = username
            it[Keys.PICTURE] = picture
        }
    }

    suspend fun clear() {
        accessToken = null
        // keep the theme choice across logouts
        context.dataStore.edit {
            it.remove(Keys.ACCESS); it.remove(Keys.REFRESH); it.remove(Keys.USER_ID)
            it.remove(Keys.NAME); it.remove(Keys.USERNAME); it.remove(Keys.PICTURE)
        }
    }
}
