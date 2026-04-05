package com.pocketnoc.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.pocketnoc.data.local.dao.AlertDao
import com.pocketnoc.data.local.dao.ServerDao
import com.pocketnoc.data.local.dao.TelemetryHistoryDao
import com.pocketnoc.data.local.entities.AlertEntity
import com.pocketnoc.data.local.entities.ServerEntity
import com.pocketnoc.data.local.entities.TelemetryHistoryEntity

@Database(
    entities = [ServerEntity::class, AlertEntity::class, TelemetryHistoryEntity::class],
    version = 6,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun serverDao(): ServerDao
    abstract fun alertDao(): AlertDao
    abstract fun telemetryHistoryDao(): TelemetryHistoryDao
}
