package com.example.data.local

import android.content.Context
import android.provider.Settings
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
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
        private val KEY_SESSION_TIMESTAMP = longPreferencesKey("session_timestamp")
        private val KEY_DEVICE_ID = stringPreferencesKey("device_id")
        private val KEY_USER_ROLE = stringPreferencesKey("user_role")
        private val KEY_ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        private val KEY_WORKER_ONLINE = booleanPreferencesKey("worker_online")
    }

    val isAuthenticated: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_IS_AUTHENTICATED] ?: false
    }

    val isOnboardingCompleted: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_ONBOARDING_COMPLETED] ?: false
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

    val sessionTimestamp: Flow<Long> = context.dataStore.data.map { preferences ->
        preferences[KEY_SESSION_TIMESTAMP] ?: 0L
    }

    val deviceId: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[KEY_DEVICE_ID]
    }

    val userRole: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[KEY_USER_ROLE]
    }

    suspend fun saveSession(
        isAuthenticated: Boolean,
        phone: String? = null,
        userId: String? = null,
        name: String? = null,
        district: String? = null,
        deviceId: String? = null,
        role: String? = null
    ) {
        context.dataStore.edit { preferences ->
            preferences[KEY_IS_AUTHENTICATED] = isAuthenticated
            phone?.let { preferences[KEY_USER_PHONE] = it } ?: preferences.remove(KEY_USER_PHONE)
            userId?.let { preferences[KEY_USER_ID] = it } ?: preferences.remove(KEY_USER_ID)
            name?.let { preferences[KEY_USER_NAME] = it } ?: preferences.remove(KEY_USER_NAME)
            district?.let { preferences[KEY_USER_DISTRICT] = it } ?: preferences.remove(KEY_USER_DISTRICT)
            role?.let { preferences[KEY_USER_ROLE] = it } ?: preferences.remove(KEY_USER_ROLE)
            if (isAuthenticated) {
                preferences[KEY_SESSION_TIMESTAMP] = System.currentTimeMillis()
                deviceId?.let { preferences[KEY_DEVICE_ID] = it }
            } else {
                preferences.remove(KEY_SESSION_TIMESTAMP)
                preferences.remove(KEY_DEVICE_ID)
            }
        }
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_ONBOARDING_COMPLETED] = completed
        }
    }

    suspend fun getIsOnboardingCompleted(): Boolean {
        return context.dataStore.data.first()[KEY_ONBOARDING_COMPLETED] ?: false
    }

    // B4: worker online/availability preference. Defaults to online (true) so a fresh install or a
    // user who has never toggled still appears available; a paused/offline choice persists across
    // restarts.
    suspend fun setWorkerOnline(online: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_WORKER_ONLINE] = online
        }
    }

    suspend fun getWorkerOnline(): Boolean {
        return context.dataStore.data.first()[KEY_WORKER_ONLINE] ?: true
    }

    suspend fun saveFcmToken(token: String) {
        context.dataStore.edit { preferences ->
            preferences[KEY_FCM_TOKEN] = token
        }
    }

    suspend fun clearSession() {
        context.dataStore.edit { preferences ->
            val wasOnboardingCompleted = preferences[KEY_ONBOARDING_COMPLETED] ?: false
            preferences.clear()
            if (wasOnboardingCompleted) {
                preferences[KEY_ONBOARDING_COMPLETED] = true
            }
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

    suspend fun getUserName(): String? {
        return context.dataStore.data.first()[KEY_USER_NAME]
    }

    suspend fun getUserDistrict(): String? {
        return context.dataStore.data.first()[KEY_USER_DISTRICT]
    }

    suspend fun getUserRole(): String? {
        return context.dataStore.data.first()[KEY_USER_ROLE]
    }

    suspend fun getSessionTimestamp(): Long {
        return context.dataStore.data.first()[KEY_SESSION_TIMESTAMP] ?: 0L
    }

    suspend fun getDeviceId(): String? {
        return context.dataStore.data.first()[KEY_DEVICE_ID]
    }

    suspend fun isSessionValid(timeoutMillis: Long = 7 * 24 * 60 * 60 * 1000): Boolean {
        val timestamp = getSessionTimestamp()
        return System.currentTimeMillis() - timestamp < timeoutMillis
    }

    fun getDeviceId(context: Context): String {
        return Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown_device"
    }
}
