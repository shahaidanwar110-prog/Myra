package com.myra.ai.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "task_history")
data class TaskEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val command: String,
    val status: String,
    val resultMessage: String,
    val timestamp: Long = System.currentTimeMillis()
)
