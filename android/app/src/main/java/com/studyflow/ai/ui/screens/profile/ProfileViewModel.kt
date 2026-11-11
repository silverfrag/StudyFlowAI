package com.studyflow.ai.ui.screens.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.studyflow.ai.data.model.ProgressSummaryResponse
import com.studyflow.ai.data.repository.StudyFlowRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface ProfileStatsUiState {
    data object Loading : ProfileStatsUiState
    data class Success(val summary: ProgressSummaryResponse) : ProfileStatsUiState
    data class Error(val message: String) : ProfileStatsUiState
}

class ProfileViewModel(
    private val repository: StudyFlowRepository = StudyFlowRepository()
) : ViewModel() {

    private val _statsState = MutableStateFlow<ProfileStatsUiState>(ProfileStatsUiState.Loading)
    val statsState: StateFlow<ProfileStatsUiState> = _statsState.asStateFlow()

    fun loadStats(silent: Boolean = false) {
        viewModelScope.launch {
            if (!silent || _statsState.value !is ProfileStatsUiState.Success) {
                _statsState.value = ProfileStatsUiState.Loading
            }
            repository.getProgressSummary()
                .onSuccess { summary ->
                    _statsState.value = ProfileStatsUiState.Success(summary)
                }
                .onFailure { err ->
                    if (!silent || _statsState.value !is ProfileStatsUiState.Success) {
                        _statsState.value = ProfileStatsUiState.Error(
                            err.message ?: "Không thể tải số liệu học tập."
                        )
                    }
                }
        }
    }
}

