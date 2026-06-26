package com.bearrushmod.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "app_preferences")

data class UserSession(
    val userId: String,
    val email: String,
    val username: String,
    val accessToken: String,
    val refreshToken: String = "",
    val coins: Int
)

class DataStoreManager(private val context: Context) {
    private val THEME_KEY = booleanPreferencesKey("dark_mode")
    private val USER_ID_KEY = stringPreferencesKey("user_id")
    private val USER_EMAIL_KEY = stringPreferencesKey("user_email")
    private val USERNAME_KEY = stringPreferencesKey("username")
    private val ACCESS_TOKEN_KEY = stringPreferencesKey("access_token")
    private val REFRESH_TOKEN_KEY = stringPreferencesKey("refresh_token")
    private val COINS_KEY = intPreferencesKey("coins")

    val isDarkMode: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[THEME_KEY] ?: false
        }

    suspend fun saveDarkMode(isDark: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[THEME_KEY] = isDark
        }
    }

    val userSession: Flow<UserSession?> = context.dataStore.data
        .map { preferences ->
            val userId = preferences[USER_ID_KEY]
            val email = preferences[USER_EMAIL_KEY]
            val username = preferences[USERNAME_KEY]
            val accessToken = preferences[ACCESS_TOKEN_KEY]
            val refreshToken = preferences[REFRESH_TOKEN_KEY] ?: ""
            val coins = preferences[COINS_KEY] ?: 100
            if (userId != null && email != null && accessToken != null) {
                UserSession(userId, email, username ?: "", accessToken, refreshToken, coins)
            } else {
                null
            }
        }

    suspend fun saveUserSession(session: UserSession) {
        context.dataStore.edit { preferences ->
            preferences[USER_ID_KEY] = session.userId
            preferences[USER_EMAIL_KEY] = session.email
            preferences[USERNAME_KEY] = session.username
            preferences[ACCESS_TOKEN_KEY] = session.accessToken
            preferences[REFRESH_TOKEN_KEY] = session.refreshToken
            preferences[COINS_KEY] = session.coins
        }
    }

    suspend fun updateCoins(coins: Int) {
        context.dataStore.edit { preferences ->
            preferences[COINS_KEY] = coins
        }
    }

    suspend fun clearUserSession() {
        context.dataStore.edit { preferences ->
            preferences.remove(USER_ID_KEY)
            preferences.remove(USER_EMAIL_KEY)
            preferences.remove(USERNAME_KEY)
            preferences.remove(ACCESS_TOKEN_KEY)
            preferences.remove(REFRESH_TOKEN_KEY)
            preferences.remove(COINS_KEY)
        }
    }
}
