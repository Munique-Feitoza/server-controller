package com.pocketnoc.data.local.dao

import androidx.room.*
import com.pocketnoc.data.local.entities.AlertEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AlertDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlert(alert: AlertEntity)

    @Query("SELECT * FROM alerts ORDER BY timestamp DESC")
    fun getAllAlerts(): Flow<List<AlertEntity>>

    @Query("SELECT * FROM alerts WHERE serverId = :serverId ORDER BY timestamp DESC")
    fun getAlertsByServer(serverId: Int): Flow<List<AlertEntity>>

    @Query("DELETE FROM alerts")
    suspend fun deleteAll()
}
