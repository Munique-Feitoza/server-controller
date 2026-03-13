package com.pocketnoc.data.local.dao

import androidx.room.*
import com.pocketnoc.data.local.entities.ServerEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ServerDao {
    @Query("SELECT * FROM servers")
    fun getAllServers(): Flow<List<ServerEntity>>
    @Query("SELECT * FROM servers WHERE id = :id")
    suspend fun getServerById(id: Int): ServerEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertServer(server: ServerEntity)

    @Delete
    suspend fun deleteServer(server: ServerEntity)

    @Update
    suspend fun updateServer(server: ServerEntity)
}
