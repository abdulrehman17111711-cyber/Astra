package com.astra.ai.memory

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Long-term memory entity for Astra AI storing facts, preferences, and user notes.
 */
@Entity(tableName = "assistant_memories")
data class MemoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val key: String,
    val value: String,
    val category: String = "general",
    val importance: Int = 1,
    val timestamp: Long = System.currentTimeMillis()
)
