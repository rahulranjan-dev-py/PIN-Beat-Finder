package com.pinbeatfinder.ui.online

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pinbeatfinder.core.util.AppResult
import com.pinbeatfinder.data.repository.PostalLookupRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class OnlineSearchViewModel(
    private val repository: PostalLookupRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(OnlineSearchState())
    val state: StateFlow<OnlineSearchState> = _state.asStateFlow()

    private var inFlight: Job? = null

    fun onIntent(intent: OnlineSearchIntent) {
        when (intent) {
            is OnlineSearchIntent.QueryChanged -> _state.update { it.copy(query = intent.query) }
            OnlineSearchIntent.Submit -> search(_state.value.query)
            OnlineSearchIntent.Retry -> search(_state.value.submittedQuery)
            OnlineSearchIntent.Clear -> {
                inFlight?.cancel()
                _state.value = OnlineSearchState()
            }
            is OnlineSearchIntent.StateFilterSelected -> _state.update {
                it.copy(stateFilter = intent.state, districtFilter = null)
            }
            is OnlineSearchIntent.DistrictFilterSelected -> _state.update { it.copy(districtFilter = intent.district) }
            OnlineSearchIntent.ClearFilters -> _state.update { it.copy(stateFilter = null, districtFilter = null) }
        }
    }

    private fun search(raw: String) {
        val query = raw.trim()
        if (query.isEmpty()) return
        inFlight?.cancel()
        inFlight = viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null, submittedQuery = query, stateFilter = null, districtFilter = null) }
            when (val result = repository.lookup(query)) {
                is AppResult.Success -> _state.update { it.copy(isLoading = false, results = result.value) }
                is AppResult.Failure -> _state.update { it.copy(isLoading = false, results = emptyList(), error = result.error) }
            }
        }
    }
}
