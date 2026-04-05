package com.pocketnoc.data.local.dao

import androidx.room.*
import com.pocketnoc.data.local.entities.TelemetryHistoryEntity

@Dao
interface TelemetryHistoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: TelemetryHistoryEntity)

    /** Retorna as últimas [limit] amostras do servidor, ordenadas da mais antiga para a mais nova. */
    @Query("""
        SELECT * FROM telemetry_history
        WHERE serverId = :serverId
        ORDER BY timestamp DESC
        LIMIT :limit
    """)
    suspend fun getRecentHistory(serverId: Int, limit: Int = 60): List<TelemetryHistoryEntity>

    /** Remove amostras mais antigas que o timestamp fornecido (limpeza automática). */
    @Query("DELETE FROM telemetry_history WHERE serverId = :serverId AND timestamp < :olderThan")
    suspend fun pruneOldEntries(serverId: Int, olderThan: Long)
}
