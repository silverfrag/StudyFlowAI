package com.studyflow.ai.ui.screens.documents

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.studyflow.ai.data.model.CourseResponse
import com.studyflow.ai.data.model.DocumentResponse
import com.studyflow.ai.data.model.QuizResponse
import com.studyflow.ai.data.model.QuizSummaryResponse
import com.studyflow.ai.data.repository.StudyFlowRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

sealed interface CourseDetailUiState {
    data object Loading : CourseDetailUiState
    data class Success(
        val course: CourseResponse,
        val documents: List<DocumentResponse>,
        val quizzes: List<QuizSummaryResponse> = emptyList()
    ) : CourseDetailUiState
    data class Error(val message: String) : CourseDetailUiState
}

class CourseDetailViewModel(
    private val repository: StudyFlowRepository = StudyFlowRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow<CourseDetailUiState>(CourseDetailUiState.Loading)
    val uiState: StateFlow<CourseDetailUiState> = _uiState.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _isUploading = MutableStateFlow(false)
    val isUploading: StateFlow<Boolean> = _isUploading.asStateFlow()

    private val _isCreatingQuiz = MutableStateFlow(false)
    val isCreatingQuiz: StateFlow<Boolean> = _isCreatingQuiz.asStateFlow()

    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = _actionMessage.asStateFlow()

    private val _quizError = MutableStateFlow<String?>(null)
    val quizError: StateFlow<String?> = _quizError.asStateFlow()

    private val _processingStartMs = MutableStateFlow<Map<String, Long>>(emptyMap())
    val processingStartMs: StateFlow<Map<String, Long>> = _processingStartMs.asStateFlow()

    private fun syncProcessingTimestamps(
        docs: List<DocumentResponse>,
        resetIds: Set<String> = emptySet()
    ) {
        val now = System.currentTimeMillis()
        val prev = _processingStartMs.value
        val next = mutableMapOf<String, Long>()
        docs.filter { it.status == "processing" }.forEach { doc ->
            next[doc.id] = if (doc.id in resetIds) {
                now
            } else {
                prev[doc.id] ?: parseIsoTimestampMillis(doc.createdAt) ?: now
            }
        }
        _processingStartMs.value = next
    }

    private fun parseIsoTimestampMillis(isoTimestamp: String): Long? {
        val normalized = Regex("""^(\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2})(?:\.\d+)?(Z|[+-]\d{2}:\d{2})?$""")
            .matchEntire(isoTimestamp.trim()) ?: return null
        val base = normalized.groupValues[1]
        val zoneRaw = normalized.groupValues[2]
        val zoneNormalized = if (zoneRaw.isEmpty() || zoneRaw == "Z") "+0000" else zoneRaw.replace(":", "")
        return try {
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US).parse("$base$zoneNormalized")?.time
        } catch (_: Exception) {
            null
        }
    }

    fun loadCourseDetail(courseId: String) {
        viewModelScope.launch {
            _uiState.value = CourseDetailUiState.Loading
            val courseRes = repository.getCourse(courseId)
            val docsRes = repository.getCourseDocuments(courseId)
            val quizzesRes = repository.getCourseQuizzes(courseId)

            if (courseRes.isSuccess && docsRes.isSuccess) {
                val docs = docsRes.getOrThrow()
                syncProcessingTimestamps(docs)
                _uiState.value = CourseDetailUiState.Success(
                    course = courseRes.getOrThrow(),
                    documents = docs,
                    quizzes = quizzesRes.getOrElse { emptyList() }
                )
            } else {
                val err = courseRes.exceptionOrNull()?.message
                    ?: docsRes.exceptionOrNull()?.message
                    ?: "Không thể tải thông tin môn học."
                _uiState.value = CourseDetailUiState.Error(err)
            }
        }
    }

    fun refreshCourseDetail(courseId: String) {
        viewModelScope.launch {
            _isRefreshing.value = true
            coroutineScope {
                val courseDeferred = async { repository.getCourse(courseId) }
                val docsDeferred = async { repository.getCourseDocuments(courseId) }
                val quizzesDeferred = async { repository.getCourseQuizzes(courseId) }

                val courseRes = courseDeferred.await()
                val docsRes = docsDeferred.await()
                val quizzesRes = quizzesDeferred.await()

                if (courseRes.isSuccess && docsRes.isSuccess) {
                    val docs = docsRes.getOrThrow()
                    syncProcessingTimestamps(docs)
                    _uiState.value = CourseDetailUiState.Success(
                        course = courseRes.getOrThrow(),
                        documents = docs,
                        quizzes = quizzesRes.getOrElse { emptyList() }
                    )
                } else if (_uiState.value !is CourseDetailUiState.Success) {
                    val err = courseRes.exceptionOrNull()?.message
                        ?: docsRes.exceptionOrNull()?.message
                        ?: "Không thể tải thông tin môn học."
                    _uiState.value = CourseDetailUiState.Error(err)
                }
            }
            _isRefreshing.value = false
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
                        .onSuccess { uploadedDoc ->
                            _isUploading.value = false
                            _actionMessage.value = "Đã tải lên '$fileName'. Tài liệu đang được xử lý."
                            val latest = _uiState.value as? CourseDetailUiState.Success
                            if (latest != null) {
                                val updatedDocs = listOf(uploadedDoc) + latest.documents.filterNot { it.id == uploadedDoc.id }
                                syncProcessingTimestamps(updatedDocs, resetIds = setOf(uploadedDoc.id))
                                _uiState.value = latest.copy(documents = updatedDocs)
                                refreshDocumentsSilently(courseId)
                            } else {
                                loadCourseDetail(courseId)
                            }
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
        if (_uiState.value !is CourseDetailUiState.Success) return
        viewModelScope.launch {
            coroutineScope {
                val docsDeferred = async { repository.getCourseDocuments(courseId).getOrNull() }
                val quizzesDeferred = async { repository.getCourseQuizzes(courseId).getOrNull() }
                val courseDeferred = async { repository.getCourse(courseId).getOrNull() }

                val docs = docsDeferred.await()
                val quizzes = quizzesDeferred.await()
                val course = courseDeferred.await()

                val latest = _uiState.value as? CourseDetailUiState.Success ?: return@coroutineScope
                val mergedDocs = docs ?: latest.documents
                syncProcessingTimestamps(mergedDocs)
                _uiState.value = latest.copy(
                    course = course ?: latest.course,
                    documents = mergedDocs,
                    quizzes = quizzes ?: latest.quizzes
                )
            }
        }
    }

    fun retryDocument(courseId: String, documentId: String) {
        viewModelScope.launch {
            _actionMessage.value = null
            repository.retryDocument(documentId)
                .onSuccess { updatedDoc ->
                    val latest = _uiState.value as? CourseDetailUiState.Success
                    if (latest != null) {
                        val mappedDocs = latest.documents.map { doc ->
                            if (doc.id == documentId) updatedDoc else doc
                        }
                        syncProcessingTimestamps(mappedDocs, resetIds = setOf(documentId))
                        _uiState.value = latest.copy(documents = mappedDocs)
                    } else {
                        syncProcessingTimestamps(listOf(updatedDoc), resetIds = setOf(documentId))
                    }
                    _actionMessage.value = "Đã gửi yêu cầu xử lý lại tài liệu."
                    refreshDocumentsSilently(courseId)
                }
                .onFailure { err ->
                    _actionMessage.value = err.message ?: "Không thể xử lý lại tài liệu."
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

