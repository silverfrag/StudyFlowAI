package com.studyflow.ai.ui.screens.courses

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.studyflow.ai.data.model.CourseResponse
import com.studyflow.ai.data.repository.StudyFlowRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface CoursesUiState {
    data object Loading : CoursesUiState
    data class Success(val courses: List<CourseResponse>) : CoursesUiState
    data class Error(val message: String) : CoursesUiState
}

class CoursesViewModel(
    private val repository: StudyFlowRepository = StudyFlowRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow<CoursesUiState>(CoursesUiState.Loading)
    val uiState: StateFlow<CoursesUiState> = _uiState.asStateFlow()

    private val _isCreating = MutableStateFlow(false)
    val isCreating: StateFlow<Boolean> = _isCreating.asStateFlow()

    private val _actionError = MutableStateFlow<String?>(null)
    val actionError: StateFlow<String?> = _actionError.asStateFlow()

    init {
        loadCourses()
    }

    fun loadCourses() {
        viewModelScope.launch {
            _uiState.value = CoursesUiState.Loading
            repository.getCourses()
                .onSuccess { list -> _uiState.value = CoursesUiState.Success(list) }
                .onFailure { err -> _uiState.value = CoursesUiState.Error(err.message ?: "Lỗi tải danh sách môn học.") }
        }
    }

    fun createCourse(name: String, description: String?, onSuccess: () -> Unit) {
        if (name.isBlank()) {
            _actionError.value = "Tên môn học không được để trống."
            return
        }
        viewModelScope.launch {
            _isCreating.value = true
            _actionError.value = null
            repository.createCourse(name.trim(), description?.trim()?.takeIf { it.isNotEmpty() })
                .onSuccess {
                    _isCreating.value = false
                    onSuccess()
                    loadCourses()
                }
                .onFailure { err ->
                    _isCreating.value = false
                    _actionError.value = err.message ?: "Không thể tạo môn học."
                }
        }
    }

    fun deleteCourse(courseId: String) {
        viewModelScope.launch {
            repository.deleteCourse(courseId)
                .onSuccess { loadCourses() }
                .onFailure { err -> _actionError.value = err.message }
        }
    }

    fun clearError() {
        _actionError.value = null
    }
}

