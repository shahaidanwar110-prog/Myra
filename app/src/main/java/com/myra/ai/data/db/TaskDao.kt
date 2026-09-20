package com.myra.ai.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    @Insert
    suspend fun insertTask(task: TaskEntity): Long

    @Insert
    suspend fun insertStepLog(stepLog: TaskStepEntity): Long

    @Query("SELECT * FROM task_history ORDER BY timestamp DESC")
    fun getAllTasks(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM task_step_logs ORDER BY timestamp DESC")
    fun getAllStepLogs(): Flow<List<TaskStepEntity>>

    @Query("SELECT * FROM task_step_logs WHERE taskId = :taskId ORDER BY stepIndex ASC")
    fun getStepLogsForTask(taskId: Long): Flow<List<TaskStepEntity>>

    @Query("DELETE FROM task_history")
    suspend fun clearHistory()

    @Query("DELETE FROM task_step_logs")
    suspend fun clearStepLogs()
}
