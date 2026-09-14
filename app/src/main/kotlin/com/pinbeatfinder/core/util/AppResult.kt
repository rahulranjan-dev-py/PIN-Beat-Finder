package com.pinbeatfinder.core.util

/** Failure categories the UI can present distinctly (offline vs. timeout vs. not found). */
sealed interface AppError {
    data object Offline : AppError
    data object Timeout : AppError
    data class NotFound(val message: String) : AppError
    data class Http(val code: Int, val message: String) : AppError
    data class Unknown(val cause: Throwable) : AppError
}

sealed interface AppResult<out T> {
    data class Success<T>(val value: T) : AppResult<T>
    data class Failure(val error: AppError) : AppResult<Nothing>

    val isSuccess: Boolean get() = this is Success
}
