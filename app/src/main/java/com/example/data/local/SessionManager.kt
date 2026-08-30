package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "skills_exchange_session")

class SessionManager(private val context: Context) {

    companion object {
        private val KEY_IS_AUTHENTICATED = booleanPreferencesKey("is_authenticated")
        private val KEY_USER_PHONE = stringPreferencesKey("user_phone")
        private val KEY_USER_ID = stringPreferencesKey("user_id")
        private val KEY_USER_NAME = stringPreferencesKey("user_name")
        private val KEY_USER_DISTRICT = stringPreferencesKey("user_district")
        private val KEY_FCM_TOKEN = stringPreferencesKey("fcm_token")
    }

    val isAuthenticated: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_IS_AUTHENTICATED] ?: false
    }

    val userPhone: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[KEY_USER_PHONE]
    }

    val userId: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[KEY_USER_ID]
    }

    val userName: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[KEY_USER_NAME]
    }

    val userDistrict: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[KEY_USER_DISTRICT]
    }

    val fcmToken: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[KEY_FCM_TOKEN]
    }

    suspend fun saveSession(
        isAuthenticated: Boolean,
        phone: String? = null,
        userId: String? = null,
        name: String? = null,
        district: String? = null
    ) {
        context.dataStore.edit { preferences ->
            preferences[KEY_IS_AUTHENTICATED] = isAuthenticated
            phone?.let { preferences[KEY_USER_PHONE] = it } ?: preferences.remove(KEY_USER_PHONE)
            userId?.let { preferences[KEY_USER_ID] = it } ?: preferences.remove(KEY_USER_ID)
            name?.let { preferences[KEY_USER_NAME] = it } ?: preferences.remove(KEY_USER_NAME)
            district?.let { preferences[KEY_USER_DISTRICT] = it } ?: preferences.remove(KEY_USER_DISTRICT)
        }
    }

    suspend fun saveFcmToken(token: String) {
        context.dataStore.edit { preferences ->
            preferences[KEY_FCM_TOKEN] = token
        }
    }

    suspend fun clearSession() {
        context.dataStore.edit { preferences ->
            preferences.clear()
        }
    }

    suspend fun getIsAuthenticated(): Boolean {
        return context.dataStore.data.first()[KEY_IS_AUTHENTICATED] ?: false
    }

    suspend fun getUserPhone(): String? {
        return context.dataStore.data.first()[KEY_USER_PHONE]
    }

    suspend fun getUserId(): String? {
        return context.dataStore.data.first()[KEY_USER_ID]
    }
}
