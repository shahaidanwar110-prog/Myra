package com.myra.ai.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "event_logs")
data class EventLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val eventType: String,
    val tag: String,
    val message: String,
    val reason: String? = null,
    val status: String = "INFO"
)
