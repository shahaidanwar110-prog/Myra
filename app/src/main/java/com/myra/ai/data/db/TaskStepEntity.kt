package com.myra.ai.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "task_step_logs")
data class TaskStepEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val taskId: Long = 0,
    val stepIndex: Int = 1,
    val action: String,
    val target: String,
    val status: String, // "SUCCESS" or "FAILED"
    val resultMessage: String,
    val failureReason: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)
