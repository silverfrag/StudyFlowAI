package com.studyflow.ai.ui.screens.quiz

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.studyflow.ai.data.model.AnswerSubmission
import com.studyflow.ai.data.model.QuizResponse
import com.studyflow.ai.data.model.QuizSubmitResponse
import com.studyflow.ai.data.repository.StudyFlowRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface QuizUiState {
    data object Loading : QuizUiState
    data class Success(val quiz: QuizResponse) : QuizUiState
    data class Error(val message: String) : QuizUiState
}

class QuizViewModel(
    private val repository: StudyFlowRepository = StudyFlowRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow<QuizUiState>(QuizUiState.Loading)
    val uiState: StateFlow<QuizUiState> = _uiState.asStateFlow()

    private val _currentIndex = MutableStateFlow(0)
    val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()

    private val _selectedAnswers = MutableStateFlow<Map<String, String>>(emptyMap())
    val selectedAnswers: StateFlow<Map<String, String>> = _selectedAnswers.asStateFlow()

    private val _isSubmitting = MutableStateFlow(false)
    val isSubmitting: StateFlow<Boolean> = _isSubmitting.asStateFlow()

    private val _submitError = MutableStateFlow<String?>(null)
    val submitError: StateFlow<String?> = _submitError.asStateFlow()

    fun loadQuiz(quizId: String) {
        viewModelScope.launch {
            _uiState.value = QuizUiState.Loading
            repository.getQuiz(quizId)
                .onSuccess { quiz ->
                    _currentIndex.value = 0
                    _selectedAnswers.value = emptyMap()
                    _uiState.value = QuizUiState.Success(quiz)
                }
                .onFailure { err ->
                    _uiState.value = QuizUiState.Error(err.message ?: "Không thể tải bài kiểm tra.")
                }
        }
    }

    fun selectOption(questionId: String, optionId: String) {
        _selectedAnswers.value = _selectedAnswers.value.toMutableMap().apply {
            put(questionId, optionId)
        }
    }

    fun nextQuestion() {
        val total = (_uiState.value as? QuizUiState.Success)?.quiz?.questions?.size ?: return
        if (_currentIndex.value < total - 1) {
            _currentIndex.value += 1
        }
    }

    fun previousQuestion() {
        if (_currentIndex.value > 0) {
            _currentIndex.value -= 1
        }
    }

    fun countUnanswered(): Int {
        val quiz = (_uiState.value as? QuizUiState.Success)?.quiz ?: return 0
        return quiz.questions.count { !_selectedAnswers.value.containsKey(it.id) }
    }

    fun submitQuiz(onSuccess: (QuizSubmitResponse) -> Unit) {
        val quiz = (_uiState.value as? QuizUiState.Success)?.quiz ?: return
        if (_isSubmitting.value) return

        val submissions = quiz.questions.map { q ->
            AnswerSubmission(
                questionId = q.id,
                selectedOptionId = _selectedAnswers.value[q.id]
            )
        }

        viewModelScope.launch {
            _isSubmitting.value = true
            _submitError.value = null
            repository.submitQuiz(quiz.id, submissions)
                .onSuccess { res ->
                    _isSubmitting.value = false
                    onSuccess(res)
                }
                .onFailure { err ->
                    _isSubmitting.value = false
                    _submitError.value = err.message ?: "Lỗi nộp bài kiểm tra."
                }
        }
    }
}

