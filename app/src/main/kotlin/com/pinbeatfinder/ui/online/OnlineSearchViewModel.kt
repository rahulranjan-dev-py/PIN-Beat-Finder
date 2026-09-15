package com.pinbeatfinder.ui.online

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pinbeatfinder.core.util.AppResult
import com.pinbeatfinder.data.prefs.RecentSearchesRepository
import com.pinbeatfinder.data.repository.BeatDirectoryRepository
import com.pinbeatfinder.data.repository.PostalLookupRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class OnlineSearchViewModel(
    private val repository: PostalLookupRepository,
    private val recents: RecentSearchesRepository,
    private val beatDirectory: BeatDirectoryRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(OnlineSearchState())
    val state: StateFlow<OnlineSearchState> = _state.asStateFlow()

    private var inFlight: Job? = null

    init {
        recents.items
            .onEach { list -> _state.update { it.copy(recents = list) } }
            .launchIn(viewModelScope)
        beatDirectory.observePincodeCounts()
            .onEach { counts -> _state.update { it.copy(localCountsByPin = counts) } }
            .launchIn(viewModelScope)
    }

    fun onIntent(intent: OnlineSearchIntent) {
        when (intent) {
            is OnlineSearchIntent.QueryChanged -> _state.update { it.copy(query = intent.query) }
            OnlineSearchIntent.Submit -> search(_state.value.query)
            OnlineSearchIntent.Retry -> search(_state.value.submittedQuery)
            OnlineSearchIntent.Clear -> {
                inFlight?.cancel()
                _state.update { OnlineSearchState(recents = it.recents, localCountsByPin = it.localCountsByPin) }
            }
            is OnlineSearchIntent.StateFilterSelected -> _state.update {
                it.copy(stateFilter = intent.state, districtFilter = null)
            }
            is OnlineSearchIntent.DistrictFilterSelected -> _state.update { it.copy(districtFilter = intent.district) }
            OnlineSearchIntent.ClearFilters -> _state.update { it.copy(stateFilter = null, districtFilter = null) }

            is OnlineSearchIntent.SearchRecent -> {
                _state.update { it.copy(query = intent.query) }
                search(intent.query)
            }
            is OnlineSearchIntent.TogglePinRecent -> recents.togglePin(intent.query)
            is OnlineSearchIntent.RemoveRecent -> recents.remove(intent.query)
            OnlineSearchIntent.ClearRecents -> recents.clearUnpinned()

            is OnlineSearchIntent.SelectOffice -> _state.update { it.copy(selectedOffice = intent.office) }
            OnlineSearchIntent.DismissDetail -> _state.update { it.copy(selectedOffice = null) }
        }
    }

    /** Entry point for the other tab ("Look up online"): sets the query and searches immediately. */
    fun searchFor(query: String) {
        _state.update { it.copy(query = query) }
        search(query)
    }

    private fun search(raw: String) {
        val query = raw.trim()
        if (query.isEmpty()) return
        inFlight?.cancel()
        inFlight = viewModelScope.launch {
            _state.update {
                it.copy(isLoading = true, error = null, submittedQuery = query, stateFilter = null, districtFilter = null, selectedOffice = null)
            }
            when (val result = repository.lookup(query)) {
                is AppResult.Success -> {
                    _state.update { it.copy(isLoading = false, results = result.value) }
                    if (result.value.isNotEmpty()) recents.record(query)
                }
                is AppResult.Failure -> _state.update { it.copy(isLoading = false, results = emptyList(), error = result.error) }
            }
        }
    }
}
