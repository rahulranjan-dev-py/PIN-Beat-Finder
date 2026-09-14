package com.pinbeatfinder.ui.online

import com.pinbeatfinder.core.util.AppError
import com.pinbeatfinder.domain.model.PostOffice

data class OnlineSearchState(
    val query: String = "",
    val submittedQuery: String = "",
    val isLoading: Boolean = false,
    val results: List<PostOffice> = emptyList(),
    val error: AppError? = null,
) {
    val hasSearched: Boolean get() = submittedQuery.isNotEmpty()
}

sealed interface OnlineSearchIntent {
    data class QueryChanged(val query: String) : OnlineSearchIntent
    data object Submit : OnlineSearchIntent
    data object Retry : OnlineSearchIntent
    data object Clear : OnlineSearchIntent
}

/** Human-readable message for an [AppError]; lives here so both tabs phrase errors the same way. */
fun AppError.userMessage(): String = when (this) {
    AppError.Offline -> "You are offline. Connect to the internet for All-India lookups, or use the Local Beats tab."
    AppError.Timeout -> "The postal server took too long to respond. Please try again."
    is AppError.NotFound -> "No post office found. Check the PIN code or spelling."
    is AppError.Http -> "Postal server error ($code). Please try again later."
    is AppError.Unknown -> "Something went wrong: ${cause.message ?: cause::class.simpleName}"
}
