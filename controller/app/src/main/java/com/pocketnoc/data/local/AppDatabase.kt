package com.pocketnoc.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.pocketnoc.data.local.dao.ServerDao
import com.pocketnoc.data.local.entities.ServerEntity

@Database(entities = [ServerEntity::class], version = 4, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun serverDao(): ServerDao
}
