package com.pinbeatfinder.ui.online

import com.pinbeatfinder.core.util.AppError
import com.pinbeatfinder.data.prefs.RecentSearch
import com.pinbeatfinder.domain.model.PostOffice

data class OnlineSearchState(
    val query: String = "",
    val submittedQuery: String = "",
    val isLoading: Boolean = false,
    /** Everything the providers returned for [submittedQuery]; filters never touch this. */
    val results: List<PostOffice> = emptyList(),
    val error: AppError? = null,
    val stateFilter: String? = null,
    val districtFilter: String? = null,
    /** Pinned first, then most recent; shown before a search is made. */
    val recents: List<RecentSearch> = emptyList(),
    /** PIN -> number of rows in the offline directory, for the "N local beats" bridge. */
    val localCountsByPin: Map<String, Int> = emptyMap(),
    /** Result whose detail sheet is open, if any. */
    val selectedOffice: PostOffice? = null,
) {
    val hasSearched: Boolean get() = submittedQuery.isNotEmpty()
    val hasFilters: Boolean get() = stateFilter != null || districtFilter != null

    /** Distinct states present in the results, alphabetical. */
    val states: List<String>
        get() = results.map { it.state }.filter { it.isNotBlank() }.distinct().sorted()

    /** Distinct districts, narrowed to the selected state when one is chosen. */
    val districts: List<String>
        get() = results.asSequence()
            .filter { stateFilter == null || it.state == stateFilter }
            .map { it.district }.filter { it.isNotBlank() }.distinct().sorted().toList()

    /** Results after applying the chips; this is what the list shows. */
    val visibleResults: List<PostOffice>
        get() = results.filter {
            (stateFilter == null || it.state == stateFilter) &&
                (districtFilter == null || it.district == districtFilter)
        }

    fun localCountFor(office: PostOffice): Int = localCountsByPin[office.pincode] ?: 0
}

sealed interface OnlineSearchIntent {
    data class QueryChanged(val query: String) : OnlineSearchIntent
    data object Submit : OnlineSearchIntent
    data object Retry : OnlineSearchIntent
    data object Clear : OnlineSearchIntent
    data class StateFilterSelected(val state: String?) : OnlineSearchIntent
    data class DistrictFilterSelected(val district: String?) : OnlineSearchIntent
    data object ClearFilters : OnlineSearchIntent

    /** Re-run a recent/pinned query. */
    data class SearchRecent(val query: String) : OnlineSearchIntent
    data class TogglePinRecent(val query: String) : OnlineSearchIntent
    data class RemoveRecent(val query: String) : OnlineSearchIntent
    data object ClearRecents : OnlineSearchIntent

    data class SelectOffice(val office: PostOffice) : OnlineSearchIntent
    data object DismissDetail : OnlineSearchIntent
}

/** Human-readable message for an [AppError]; lives here so both tabs phrase errors the same way. */
fun AppError.userMessage(): String = when (this) {
    AppError.Offline -> "You are offline. Connect to the internet for All-India lookups, or use the Local Beats tab."
    AppError.Timeout -> "The postal server took too long to respond. Please try again."
    is AppError.NotFound -> "No post office found. Check the PIN code or spelling."
    is AppError.Http -> if (code == 0) "Postal data services are unreachable ($message). Please try again later." else "Postal server error ($code). Please try again later."
    is AppError.Unknown -> "Something went wrong: ${cause.message ?: cause::class.simpleName}"
}
