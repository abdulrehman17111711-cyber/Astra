package com.astra.ai.core

/**
 * Standard Result type for operations in Astra AI.
 */
sealed interface AstraResult<out T> {
    data class Success<out T>(val data: T) : AstraResult<T>
    data class Error(val message: String, val cause: Throwable? = null) : AstraResult<Nothing>
    data object Loading : AstraResult<Nothing>

    val isSuccess: Boolean
        get() = this is Success

    val isError: Boolean
        get() = this is Error

    fun getOrNull(): T? = when (this) {
        is Success -> data
        else -> null
    }

    fun exceptionOrNull(): Throwable? = when (this) {
        is Error -> cause ?: Exception(message)
        else -> null
    }
}
