package com.myra.ai.ai

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import java.util.UUID

enum class AgentType {
    PHONE,   // UI Agent - operates accessibility/screen. Global UI lock (only 1 active at a time)
    CONTENT, // Content Agent - writes captions, descriptions, drafts
    CODER,   // Coder Agent - generates websites and app projects
    CHAT     // Chat/Research Agent - Q&A, general research
}

enum class AgentStatus {
    QUEUED,
    RUNNING,
    COMPLETED,
    FAILED,
    CANCELLED
}

data class AgentTask(
    val id: String = UUID.randomUUID().toString(),
    val type: AgentType,
    val name: String,
    val description: String,
    val status: AgentStatus = AgentStatus.QUEUED,
    val errorMessage: String? = null,
    val job: Job? = null
)

class AgentOrchestrator(
    private val scope: CoroutineScope
) {
    private val maxParallelSemaphore = Semaphore(3) // Max 3 agents running concurrently
    private val uiLock = Mutex() // Global UI lock: only 1 PHONE/UI agent active at a time

    private val _tasks = MutableStateFlow<List<AgentTask>>(emptyList())
    val tasks: StateFlow<List<AgentTask>> = _tasks.asStateFlow()

    fun runAgentTask(
        type: AgentType,
        name: String,
        description: String,
        block: suspend () -> Unit
    ): AgentTask {
        val taskId = UUID.randomUUID().toString()

        val taskJob = scope.launch {
            updateTaskStatus(taskId, AgentStatus.QUEUED)
            maxParallelSemaphore.acquire()
            try {
                if (type == AgentType.PHONE) {
                    uiLock.withLock {
                        executeTask(taskId, block)
                    }
                } else {
                    executeTask(taskId, block)
                }
            } finally {
                maxParallelSemaphore.release()
            }
        }

        val newTask = AgentTask(
            id = taskId,
            type = type,
            name = name,
            description = description,
            status = AgentStatus.QUEUED,
            job = taskJob
        )

        _tasks.value = _tasks.value + newTask
        return newTask
    }

    private suspend fun executeTask(taskId: String, block: suspend () -> Unit) {
        val task = _tasks.value.find { it.id == taskId } ?: return
        if (task.status == AgentStatus.CANCELLED) return

        updateTaskStatus(taskId, AgentStatus.RUNNING)
        try {
            block()
            updateTaskStatus(taskId, AgentStatus.COMPLETED)
        } catch (e: CancellationException) {
            updateTaskStatus(taskId, AgentStatus.CANCELLED)
            throw e
        } catch (e: Exception) {
            updateTaskStatus(taskId, AgentStatus.FAILED, errorMessage = e.localizedMessage ?: "Agent task failed")
        }
    }

    private fun updateTaskStatus(taskId: String, newStatus: AgentStatus, errorMessage: String? = null) {
        _tasks.value = _tasks.value.map { task ->
            if (task.id == taskId) {
                task.copy(status = newStatus, errorMessage = errorMessage ?: task.errorMessage)
            } else {
                task
            }
        }
    }

    fun cancelTask(taskId: String) {
        _tasks.value.find { it.id == taskId }?.let { task ->
            task.job?.cancel()
            updateTaskStatus(taskId, AgentStatus.CANCELLED)
        }
    }

    fun cancelAll() {
        val currentTasks = _tasks.value
        for (task in currentTasks) {
            if (task.status == AgentStatus.RUNNING || task.status == AgentStatus.QUEUED) {
                task.job?.cancel()
                updateTaskStatus(task.id, AgentStatus.CANCELLED)
            }
        }
    }

    fun clearCompleted() {
        _tasks.value = _tasks.value.filter { it.status == AgentStatus.RUNNING || it.status == AgentStatus.QUEUED }
    }
}
