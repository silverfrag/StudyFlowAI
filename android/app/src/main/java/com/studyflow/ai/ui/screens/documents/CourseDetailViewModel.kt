package com.studyflow.ai.ui.screens.documents

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.studyflow.ai.data.model.CourseResponse
import com.studyflow.ai.data.model.DocumentResponse
import com.studyflow.ai.data.model.QuizResponse
import com.studyflow.ai.data.repository.StudyFlowRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface CourseDetailUiState {
    data object Loading : CourseDetailUiState
    data class Success(
        val course: CourseResponse,
        val documents: List<DocumentResponse>
    ) : CourseDetailUiState
    data class Error(val message: String) : CourseDetailUiState
}

class CourseDetailViewModel(
    private val repository: StudyFlowRepository = StudyFlowRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow<CourseDetailUiState>(CourseDetailUiState.Loading)
    val uiState: StateFlow<CourseDetailUiState> = _uiState.asStateFlow()

    private val _isUploading = MutableStateFlow(false)
    val isUploading: StateFlow<Boolean> = _isUploading.asStateFlow()

    private val _isCreatingQuiz = MutableStateFlow(false)
    val isCreatingQuiz: StateFlow<Boolean> = _isCreatingQuiz.asStateFlow()

    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = _actionMessage.asStateFlow()

    private val _quizError = MutableStateFlow<String?>(null)
    val quizError: StateFlow<String?> = _quizError.asStateFlow()

    fun loadCourseDetail(courseId: String) {
        viewModelScope.launch {
            _uiState.value = CourseDetailUiState.Loading
            val courseRes = repository.getCourse(courseId)
            val docsRes = repository.getCourseDocuments(courseId)

            if (courseRes.isSuccess && docsRes.isSuccess) {
                _uiState.value = CourseDetailUiState.Success(
                    course = courseRes.getOrThrow(),
                    documents = docsRes.getOrThrow()
                )
            } else {
                val err = courseRes.exceptionOrNull()?.message
                    ?: docsRes.exceptionOrNull()?.message
                    ?: "Không thể tải thông tin môn học."
                _uiState.value = CourseDetailUiState.Error(err)
            }
        }
    }

    fun uploadDocument(context: Context, courseId: String, uri: Uri) {
        viewModelScope.launch {
            _isUploading.value = true
            _actionMessage.value = null
            try {
                val contentResolver = context.contentResolver
                var fileName = "document.pdf"
                var fileSize = -1L
                contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (nameIdx >= 0) fileName = cursor.getString(nameIdx)
                        val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (sizeIdx >= 0) fileSize = cursor.getLong(sizeIdx)
                    }
                }
                val lowerName = fileName.lowercase()
                if (!lowerName.endsWith(".pdf") && !lowerName.endsWith(".txt")) {
                    throw Exception("Chỉ hỗ trợ tải lên tài liệu định dạng .pdf hoặc .txt.")
                }
                if (fileSize == 0L) {
                    throw Exception("Tệp tải lên đang trống (0 byte).")
                }
                val maxBytes = 20L * 1024L * 1024L
                if (fileSize > maxBytes) {
                    throw Exception("Dung lượng tệp vượt quá giới hạn tối đa 20MB.")
                }

                val mimeType = contentResolver.getType(uri)
                    ?: if (lowerName.endsWith(".txt")) "text/plain" else "application/pdf"
                val stream = contentResolver.openInputStream(uri)
                    ?: throw Exception("Không thể đọc nội dung tệp đã chọn.")

                stream.use { input ->
                    repository.uploadDocument(courseId, fileName, mimeType, input)
                        .onSuccess {
                            _isUploading.value = false
                            _actionMessage.value = "Đã tải lên '$fileName'. Tài liệu đang được xử lý."
                            loadCourseDetail(courseId)
                        }
                        .onFailure { err ->
                            _isUploading.value = false
                            _actionMessage.value = err.message ?: "Lỗi tải lên tài liệu."
                        }
                }
            } catch (e: Exception) {
                _isUploading.value = false
                _actionMessage.value = e.message ?: "Không thể mở tệp."
            }
        }
    }

    fun refreshDocumentsSilently(courseId: String) {
        val current = _uiState.value as? CourseDetailUiState.Success ?: return
        viewModelScope.launch {
            repository.getCourseDocuments(courseId).onSuccess { docs ->
                _uiState.value = current.copy(documents = docs)
            }
        }
    }

    fun deleteDocument(courseId: String, documentId: String) {
        viewModelScope.launch {
            repository.deleteDocument(documentId)
                .onSuccess { loadCourseDetail(courseId) }
                .onFailure { err -> _actionMessage.value = err.message }
        }
    }

    fun generateQuiz(
        courseId: String,
        documentId: String? = null,
        topic: String?,
        questionCount: Int,
        onQuizCreated: (QuizResponse) -> Unit
    ) {
        viewModelScope.launch {
            _isCreatingQuiz.value = true
            _quizError.value = null
            repository.createQuiz(
                courseId = courseId,
                documentId = documentId,
                topic = topic?.trim()?.takeIf { it.isNotEmpty() },
                count = questionCount
            ).onSuccess { quiz ->
                _isCreatingQuiz.value = false
                onQuizCreated(quiz)
            }.onFailure { err ->
                _isCreatingQuiz.value = false
                _quizError.value = err.message ?: "Không thể tạo bài kiểm tra."
            }
        }
    }

    fun clearMessage() {
        _actionMessage.value = null
    }

    fun clearQuizError() {
        _quizError.value = null
    }
}

