package com.myra.ai.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface EventLogDao {
    @Insert
    suspend fun insertLog(log: EventLogEntity): Long

    @Query("SELECT * FROM event_logs ORDER BY timestamp DESC")
    fun getAllLogs(): Flow<List<EventLogEntity>>

    @Query("DELETE FROM event_logs")
    suspend fun clearLogs()
}
