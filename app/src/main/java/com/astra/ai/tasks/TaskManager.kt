package com.astra.ai.tasks

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

enum class TaskStatus {
    PENDING,
    RUNNING,
    COMPLETED,
    FAILED,
    CANCELLED
}

data class AssistantTask(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val description: String,
    val status: TaskStatus = TaskStatus.PENDING,
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null
)

interface TaskManager {
    val tasks: StateFlow<List<AssistantTask>>
    fun enqueueTask(title: String, description: String): AssistantTask
    fun updateTaskStatus(id: String, status: TaskStatus)
    fun cancelTask(id: String)
    fun clearFinishedTasks()
}

class DefaultTaskManager : TaskManager {
    private val _tasks = MutableStateFlow<List<AssistantTask>>(emptyList())
    override val tasks: StateFlow<List<AssistantTask>> = _tasks.asStateFlow()

    override fun enqueueTask(title: String, description: String): AssistantTask {
        val task = AssistantTask(title = title, description = description)
        _tasks.value = _tasks.value + task
        return task
    }

    override fun updateTaskStatus(id: String, status: TaskStatus) {
        _tasks.value = _tasks.value.map { task ->
            if (task.id == id) {
                task.copy(
                    status = status,
                    completedAt = if (status == TaskStatus.COMPLETED || status == TaskStatus.FAILED || status == TaskStatus.CANCELLED) {
                        System.currentTimeMillis()
                    } else null
                )
            } else task
        }
    }

    override fun cancelTask(id: String) {
        updateTaskStatus(id, TaskStatus.CANCELLED)
    }

    override fun clearFinishedTasks() {
        _tasks.value = _tasks.value.filter {
            it.status == TaskStatus.PENDING || it.status == TaskStatus.RUNNING
        }
    }
}
