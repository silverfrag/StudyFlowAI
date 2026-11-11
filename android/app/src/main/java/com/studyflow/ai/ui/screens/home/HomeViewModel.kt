package com.studyflow.ai.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.studyflow.ai.data.model.ProgressSummaryResponse
import com.studyflow.ai.data.repository.StudyFlowRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface HomeUiState {
    data object Loading : HomeUiState
    data class Success(val summary: ProgressSummaryResponse) : HomeUiState
    data class Error(val message: String) : HomeUiState
}

class HomeViewModel(
    private val repository: StudyFlowRepository = StudyFlowRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    fun loadSummary(silent: Boolean = false) {
        viewModelScope.launch {
            if (!silent || _uiState.value !is HomeUiState.Success) {
                _uiState.value = HomeUiState.Loading
            }
            repository.getProgressSummary()
                .onSuccess { summary ->
                    _uiState.value = HomeUiState.Success(summary)
                }
                .onFailure { err ->
                    if (!silent || _uiState.value !is HomeUiState.Success) {
                        _uiState.value = HomeUiState.Error(err.message ?: "Lỗi tải dữ liệu trang chủ.")
                    }
                }
        }
    }

    fun refreshSummary() {
        viewModelScope.launch {
            _isRefreshing.value = true
            repository.getProgressSummary()
                .onSuccess { summary ->
                    _uiState.value = HomeUiState.Success(summary)
                }
                .onFailure { err ->
                    if (_uiState.value !is HomeUiState.Success) {
                        _uiState.value = HomeUiState.Error(err.message ?: "Lỗi tải dữ liệu trang chủ.")
                    }
                }
            _isRefreshing.value = false
        }
    }
}

