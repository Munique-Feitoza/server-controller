package com.pocketnoc.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "telemetry_history")
data class TelemetryHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val serverId: Int,
    val timestamp: Long,
    val cpuPercent: Float,
    val ramPercent: Float,
    val diskPercent: Float,
    val pingLatencyMs: Double?,
    val loadAvg1m: Float
)
