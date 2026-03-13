package com.pocketnoc.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "session_prefs")

@Singleton
class SessionManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        private val SELECTED_SERVER_ID = intPreferencesKey("selected_server_id")
        private val AUTH_TOKEN = stringPreferencesKey("auth_token")
    }

    val selectedServerId: Flow<Int?> = context.dataStore.data
        .map { preferences ->
            preferences[SELECTED_SERVER_ID]
        }

    val authToken: Flow<String?> = context.dataStore.data
        .map { preferences ->
            preferences[AUTH_TOKEN]
        }

    suspend fun saveSession(serverId: Int, token: String) {
        context.dataStore.edit { preferences ->
            preferences[SELECTED_SERVER_ID] = serverId
            preferences[AUTH_TOKEN] = token
        }
    }

    suspend fun clearSession() {
        context.dataStore.edit { preferences ->
            preferences.clear()
        }
    }
}
