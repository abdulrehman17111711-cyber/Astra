package com.astra.ai.memory

import kotlinx.coroutines.flow.Flow

interface HistoryRepository {
    fun getAllHistoryFlow(): Flow<List<HistoryEntity>>
    suspend fun getRecentHistory(limit: Int = 20): List<HistoryEntity>
    suspend fun recordInteraction(
        query: String,
        response: String,
        state: String,
        toolName: String? = null,
        success: Boolean = true
    ): Long
    suspend fun deleteHistory(id: Long)
    suspend fun clearHistory()
}

class DefaultHistoryRepository(private val historyDao: HistoryDao) : HistoryRepository {
    override fun getAllHistoryFlow(): Flow<List<HistoryEntity>> = historyDao.getAllHistoryFlow()

    override suspend fun getRecentHistory(limit: Int): List<HistoryEntity> =
        historyDao.getRecentHistory(limit)

    override suspend fun recordInteraction(
        query: String,
        response: String,
        state: String,
        toolName: String?,
        success: Boolean
    ): Long {
        return historyDao.insertHistory(
            HistoryEntity(
                query = query,
                response = response,
                state = state,
                toolName = toolName,
                success = success
            )
        )
    }

    override suspend fun deleteHistory(id: Long) {
        historyDao.deleteById(id)
    }

    override suspend fun clearHistory() {
        historyDao.clearHistory()
    }
}
