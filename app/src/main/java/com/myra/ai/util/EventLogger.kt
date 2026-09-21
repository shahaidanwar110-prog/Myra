package com.myra.ai.util

import android.content.Context
import com.myra.ai.data.db.AppDatabase
import com.myra.ai.data.db.EventLogEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

object EventLogger {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var database: AppDatabase? = null
    private var defaultUncaughtHandler: Thread.UncaughtExceptionHandler? = null

    fun init(context: Context) {
        if (database == null) {
            database = AppDatabase.getInstance(context.applicationContext)
        }
        setupUncaughtExceptionHandler()
    }

    private fun setupUncaughtExceptionHandler() {
        if (defaultUncaughtHandler == null) {
            defaultUncaughtHandler = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
                logCrash("UncaughtException", throwable)
                defaultUncaughtHandler?.uncaughtException(thread, throwable)
            }
        }
    }

    fun logEvent(
        eventType: String,
        tag: String,
        message: String,
        reason: String? = null,
        status: String = "INFO"
    ) {
        database?.let { db ->
            scope.launch {
                try {
                    db.eventLogDao().insertLog(
                        EventLogEntity(
                            eventType = eventType,
                            tag = tag,
                            message = message,
                            reason = reason,
                            status = status
                        )
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun logServiceStart(serviceName: String, details: String? = null) {
        logEvent(
            eventType = "SERVICE_START",
            tag = serviceName,
            message = "$serviceName started",
            reason = details,
            status = "SUCCESS"
        )
    }

    fun logServiceStop(serviceName: String, reason: String? = null) {
        logEvent(
            eventType = "SERVICE_STOP",
            tag = serviceName,
            message = "$serviceName stopped",
            reason = reason,
            status = "INFO"
        )
    }

    fun logPermissionFailure(permissionTag: String, reason: String) {
        logEvent(
            eventType = "PERMISSION_FAILURE",
            tag = permissionTag,
            message = "Permission failure: $permissionTag",
            reason = reason,
            status = "WARNING"
        )
    }

    fun logCommandResult(command: String, isSuccess: Boolean, resultOrReason: String) {
        logEvent(
            eventType = "COMMAND_RESULT",
            tag = "Command",
            message = "Command: '$command'",
            reason = resultOrReason,
            status = if (isSuccess) "SUCCESS" else "FAILED"
        )
    }

    fun logCrash(tag: String, throwable: Throwable) {
        logEvent(
            eventType = "CRASH",
            tag = tag,
            message = throwable.localizedMessage ?: "Application crash",
            reason = throwable.stackTraceToString(),
            status = "CRASH"
        )
    }

    fun getAllLogs(context: Context): Flow<List<EventLogEntity>> {
        val db = database ?: AppDatabase.getInstance(context.applicationContext).also { database = it }
        return db.eventLogDao().getAllLogs()
    }

    fun clearLogs(context: Context) {
        database?.let { db ->
            scope.launch {
                try {
                    db.eventLogDao().clearLogs()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }
}
