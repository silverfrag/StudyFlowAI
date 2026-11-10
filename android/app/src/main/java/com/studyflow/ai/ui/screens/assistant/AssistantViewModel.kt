package com.studyflow.ai.ui.screens.assistant

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.studyflow.ai.data.model.Citation
import com.studyflow.ai.data.model.CourseResponse
import com.studyflow.ai.data.model.DocumentResponse
import com.studyflow.ai.data.repository.StudyFlowRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ChatMessage(
    val id: String,
    val isUser: Boolean,
    val text: String,
    val foundInContext: Boolean = true,
    val citations: List<Citation> = emptyList(),
    val isError: Boolean = false,
    val retryQuestion: String? = null
)

class AssistantViewModel(
    private val repository: StudyFlowRepository = StudyFlowRepository()
) : ViewModel() {

    private val _courses = MutableStateFlow<List<CourseResponse>>(emptyList())
    val courses: StateFlow<List<CourseResponse>> = _courses.asStateFlow()

    private val _selectedCourse = MutableStateFlow<CourseResponse?>(null)
    val selectedCourse: StateFlow<CourseResponse?> = _selectedCourse.asStateFlow()

    private val _documents = MutableStateFlow<List<DocumentResponse>>(emptyList())
    val documents: StateFlow<List<DocumentResponse>> = _documents.asStateFlow()

    private val _selectedDocument = MutableStateFlow<DocumentResponse?>(null)
    val selectedDocument: StateFlow<DocumentResponse?> = _selectedDocument.asStateFlow()

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _isAsking = MutableStateFlow(false)
    val isAsking: StateFlow<Boolean> = _isAsking.asStateFlow()

    private val _coursesLoading = MutableStateFlow(true)
    val coursesLoading: StateFlow<Boolean> = _coursesLoading.asStateFlow()

    fun loadCourses(preferredCourseId: String? = null) {
        viewModelScope.launch {
            _coursesLoading.value = true
            repository.getCourses()
                .onSuccess { list ->
                    _courses.value = list
                    val chosen = if (preferredCourseId != null) {
                        list.find { it.id == preferredCourseId } ?: list.firstOrNull()
                    } else {
                        _selectedCourse.value?.let { cur -> list.find { it.id == cur.id } } ?: list.firstOrNull()
                    }
                    _selectedCourse.value = chosen
                    _coursesLoading.value = false
                    chosen?.let { loadDocumentsForCourse(it.id) }
                }
                .onFailure {
                    _coursesLoading.value = false
                }
        }
    }

    private fun loadDocumentsForCourse(courseId: String) {
        viewModelScope.launch {
            repository.getCourseDocuments(courseId)
                .onSuccess { docs ->
                    _documents.value = docs.filter { it.status == "ready" }
                    _selectedDocument.value = null
                }
                .onFailure {
                    _documents.value = emptyList()
                    _selectedDocument.value = null
                }
        }
    }

    fun selectCourse(course: CourseResponse) {
        _selectedCourse.value = course
        loadDocumentsForCourse(course.id)
    }

    fun selectDocument(document: DocumentResponse?) {
        _selectedDocument.value = document
    }

    fun clearMessages() {
        _messages.value = emptyList()
    }

    fun retryQuestion(errorMessageId: String, questionText: String) {
        if (_isAsking.value) return
        _messages.value = _messages.value.filterNot { it.id == errorMessageId }
        askQuestionInternal(questionText, appendUserMessage = false)
    }

    fun askQuestion(questionText: String) {
        askQuestionInternal(questionText, appendUserMessage = true)
    }

    private fun askQuestionInternal(questionText: String, appendUserMessage: Boolean) {
        val course = _selectedCourse.value ?: return
        val trimmed = questionText.trim()
        if (trimmed.isEmpty() || _isAsking.value) return

        if (appendUserMessage) {
            val userMsg = ChatMessage(
                id = "u-${System.currentTimeMillis()}",
                isUser = true,
                text = trimmed
            )
            _messages.value = _messages.value + userMsg
        }
        _isAsking.value = true

        viewModelScope.launch {
            repository.askAssistant(course.id, _selectedDocument.value?.id, trimmed)
                .onSuccess { res ->
                    val aiMsg = ChatMessage(
                        id = "a-${System.currentTimeMillis()}",
                        isUser = false,
                        text = res.answer,
                        foundInContext = res.foundInContext,
                        citations = res.citations
                    )
                    _messages.value = _messages.value + aiMsg
                    _isAsking.value = false
                }
                .onFailure { err ->
                    val errMsg = ChatMessage(
                        id = "e-${System.currentTimeMillis()}",
                        isUser = false,
                        text = err.message ?: "Không thể kết nối trợ lý AI.",
                        foundInContext = false,
                        isError = true,
                        retryQuestion = trimmed
                    )
                    _messages.value = _messages.value + errMsg
                    _isAsking.value = false
                }
        }
    }
}

