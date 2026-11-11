package com.studyflow.ai.ui.screens.progress

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.studyflow.ai.data.model.ProgressSummaryResponse
import com.studyflow.ai.data.model.QuizResponse
import com.studyflow.ai.data.model.RecommendationItem
import com.studyflow.ai.data.repository.StudyFlowRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface ProgressUiState {
    data object Loading : ProgressUiState
    data class Success(
        val summary: ProgressSummaryResponse,
        val recommendations: List<RecommendationItem>
    ) : ProgressUiState
    data class Error(val message: String) : ProgressUiState
}

class ProgressViewModel(
    private val repository: StudyFlowRepository = StudyFlowRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow<ProgressUiState>(ProgressUiState.Loading)
    val uiState: StateFlow<ProgressUiState> = _uiState.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _isStartingPractice = MutableStateFlow(false)
    val isStartingPractice: StateFlow<Boolean> = _isStartingPractice.asStateFlow()

    private val _startingTopic = MutableStateFlow<String?>(null)
    val startingTopic: StateFlow<String?> = _startingTopic.asStateFlow()

    private val _practiceError = MutableStateFlow<String?>(null)
    val practiceError: StateFlow<String?> = _practiceError.asStateFlow()

    fun loadProgress(silent: Boolean = false) {
        viewModelScope.launch {
            if (!silent || _uiState.value !is ProgressUiState.Success) {
                _uiState.value = ProgressUiState.Loading
            }
            _practiceError.value = null
            val summaryRes = repository.getProgressSummary()
            val recRes = repository.getRecommendations()

            if (summaryRes.isSuccess && recRes.isSuccess) {
                _uiState.value = ProgressUiState.Success(
                    summary = summaryRes.getOrThrow(),
                    recommendations = recRes.getOrThrow()
                )
            } else if (!silent || _uiState.value !is ProgressUiState.Success) {
                val err = summaryRes.exceptionOrNull()?.message
                    ?: recRes.exceptionOrNull()?.message
                    ?: "Không thể tải dữ liệu tiến độ."
                _uiState.value = ProgressUiState.Error(err)
            }
        }
    }

    fun refreshProgress() {
        viewModelScope.launch {
            _isRefreshing.value = true
            _practiceError.value = null
            val summaryRes = repository.getProgressSummary()
            val recRes = repository.getRecommendations()

            if (summaryRes.isSuccess && recRes.isSuccess) {
                _uiState.value = ProgressUiState.Success(
                    summary = summaryRes.getOrThrow(),
                    recommendations = recRes.getOrThrow()
                )
            } else if (_uiState.value !is ProgressUiState.Success) {
                val err = summaryRes.exceptionOrNull()?.message
                    ?: recRes.exceptionOrNull()?.message
                    ?: "Không thể tải dữ liệu tiến độ."
                _uiState.value = ProgressUiState.Error(err)
            }
            _isRefreshing.value = false
        }
    }

    fun startPracticeQuiz(
        courseId: String,
        topic: String,
        onQuizCreated: (QuizResponse) -> Unit
    ) {
        viewModelScope.launch {
            _isStartingPractice.value = true
            _startingTopic.value = topic
            _practiceError.value = null
            repository.createQuiz(courseId = courseId, documentId = null, topic = topic, count = 5)
                .onSuccess { quiz ->
                    _isStartingPractice.value = false
                    _startingTopic.value = null
                    onQuizCreated(quiz)
                }
                .onFailure { err ->
                    _isStartingPractice.value = false
                    _startingTopic.value = null
                    _practiceError.value = err.message ?: "Không thể tạo bài quiz luyện tập."
                }
        }
    }

    fun clearPracticeError() {
        _practiceError.value = null
    }
}

