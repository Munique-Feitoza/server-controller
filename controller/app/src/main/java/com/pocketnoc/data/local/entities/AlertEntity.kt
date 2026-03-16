package com.pocketnoc.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "alerts")
data class AlertEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val serverId: Int,
    val serverName: String,
    val type: String,
    val message: String,
    val value: Float,
    val threshold: Float,
    val timestamp: Long = System.currentTimeMillis()
)
