package com.astra.ai.memory

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Historical record of assistant interactions, queries, tool executions, and responses.
 */
@Entity(tableName = "interaction_history")
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val query: String,
    val response: String,
    val state: String,
    val toolName: String? = null,
    val success: Boolean = true,
    val timestamp: Long = System.currentTimeMillis()
)
