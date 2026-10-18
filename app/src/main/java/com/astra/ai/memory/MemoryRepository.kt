package com.astra.ai.memory

import kotlinx.coroutines.flow.Flow

interface MemoryRepository {
    fun getAllMemoriesFlow(): Flow<List<MemoryEntity>>
    suspend fun getAllMemories(): List<MemoryEntity>
    suspend fun saveMemory(key: String, value: String, category: String = "general", importance: Int = 1): Long
    suspend fun deleteMemory(id: Long)
    suspend fun searchMemories(query: String): List<MemoryEntity>
    suspend fun clearAll()
}

class DefaultMemoryRepository(private val memoryDao: MemoryDao) : MemoryRepository {
    override fun getAllMemoriesFlow(): Flow<List<MemoryEntity>> = memoryDao.getAllMemoriesFlow()

    override suspend fun getAllMemories(): List<MemoryEntity> = memoryDao.getAllMemories()

    override suspend fun saveMemory(key: String, value: String, category: String, importance: Int): Long {
        return memoryDao.insertMemory(
            MemoryEntity(
                key = key.trim(),
                value = value.trim(),
                category = category.trim(),
                importance = importance
            )
        )
    }

    override suspend fun deleteMemory(id: Long) {
        memoryDao.deleteMemoryById(id)
    }

    override suspend fun searchMemories(query: String): List<MemoryEntity> {
        return memoryDao.searchMemories(query)
    }

    override suspend fun clearAll() {
        memoryDao.clearAll()
    }
}
