package com.pocketnoc.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

import com.google.gson.annotations.SerializedName

private const val ALERT_THRESHOLDS_DATASTORE_NAME = "alert_thresholds"
private val Context.alertThresholdsDataStore by preferencesDataStore(name = ALERT_THRESHOLDS_DATASTORE_NAME)

/**
 * Configurações de alertas para notificações
 */
data class AlertThresholdConfig(
    @SerializedName("cpu_threshold_percent")
    val cpuThresholdPercent: Float = 80f,
    @SerializedName("memory_threshold_percent")
    val memoryThresholdPercent: Float = 85f,
    @SerializedName("disk_threshold_percent")
    val diskThresholdPercent: Float = 90f,
    @SerializedName("temperature_threshold_celsius")
    val temperatureThresholdCelsius: Float = 80f,
    @SerializedName("reboot_threshold_minutes")
    val rebootThresholdMinutes: Long = 5L
)

/**
 * Repository para gerenciar configurações de alertas usando DataStore
 */
class AlertThresholdRepository(context: Context) {

    private val dataStore = context.alertThresholdsDataStore

    private object Keys {
        val CPU_THRESHOLD = floatPreferencesKey("cpu_threshold_percent")
        val MEMORY_THRESHOLD = floatPreferencesKey("memory_threshold_percent")
        val DISK_THRESHOLD = floatPreferencesKey("disk_threshold_percent")
        val TEMPERATURE_THRESHOLD = floatPreferencesKey("temperature_threshold_celsius")
        val REBOOT_THRESHOLD = longPreferencesKey("reboot_threshold_minutes")
    }

    val alertThresholdsFlow: Flow<AlertThresholdConfig> = dataStore.data.map { preferences ->
        AlertThresholdConfig(
            cpuThresholdPercent = preferences[Keys.CPU_THRESHOLD] ?: 80f,
            memoryThresholdPercent = preferences[Keys.MEMORY_THRESHOLD] ?: 85f,
            diskThresholdPercent = preferences[Keys.DISK_THRESHOLD] ?: 90f,
            temperatureThresholdCelsius = preferences[Keys.TEMPERATURE_THRESHOLD] ?: 80f,
            rebootThresholdMinutes = preferences[Keys.REBOOT_THRESHOLD] ?: 5L
        )
    }

    suspend fun updateCpuThreshold(value: Float) {
        dataStore.edit { preferences ->
            preferences[Keys.CPU_THRESHOLD] = value
        }
    }

    suspend fun updateMemoryThreshold(value: Float) {
        dataStore.edit { preferences ->
            preferences[Keys.MEMORY_THRESHOLD] = value
        }
    }

    suspend fun updateDiskThreshold(value: Float) {
        dataStore.edit { preferences ->
            preferences[Keys.DISK_THRESHOLD] = value
        }
    }

    suspend fun updateTemperatureThreshold(value: Float) {
        dataStore.edit { preferences ->
            preferences[Keys.TEMPERATURE_THRESHOLD] = value
        }
    }

    suspend fun updateRebootThreshold(value: Long) {
        dataStore.edit { preferences ->
            preferences[Keys.REBOOT_THRESHOLD] = value
        }
    }

    suspend fun updateAllThresholds(config: AlertThresholdConfig) {
        dataStore.edit { preferences ->
            preferences[Keys.CPU_THRESHOLD] = config.cpuThresholdPercent
            preferences[Keys.MEMORY_THRESHOLD] = config.memoryThresholdPercent
            preferences[Keys.DISK_THRESHOLD] = config.diskThresholdPercent
            preferences[Keys.TEMPERATURE_THRESHOLD] = config.temperatureThresholdCelsius
            preferences[Keys.REBOOT_THRESHOLD] = config.rebootThresholdMinutes
        }
    }

    suspend fun resetToDefaults() {
        updateAllThresholds(AlertThresholdConfig())
    }
}
