package com.pocketnoc.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.pocketnoc.data.local.dao.ServerDao
import com.pocketnoc.data.local.dao.AlertDao
import com.pocketnoc.data.local.entities.ServerEntity
import com.pocketnoc.data.local.entities.AlertEntity

@Database(entities = [ServerEntity::class, AlertEntity::class], version = 5, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun serverDao(): ServerDao
    abstract fun alertDao(): AlertDao
}
