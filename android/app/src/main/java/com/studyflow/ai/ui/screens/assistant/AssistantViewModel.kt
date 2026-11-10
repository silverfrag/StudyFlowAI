package com.studyflow.ai.ui.screens.assistant

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.studyflow.ai.data.model.Citation
import com.studyflow.ai.data.model.CourseResponse
import com.studyflow.ai.data.model.DocumentResponse
import com.studyflow.ai.data.repository.StudyFlowRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
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

    private val _messagesByCourse = MutableStateFlow<Map<String, List<ChatMessage>>>(emptyMap())
    val messages: StateFlow<List<ChatMessage>> = combine(_selectedCourse, _messagesByCourse) { course, map ->
        if (course == null) emptyList() else map[course.id].orEmpty()
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _askingCourseIds = MutableStateFlow<Set<String>>(emptySet())
    val isAsking: StateFlow<Boolean> = combine(_selectedCourse, _askingCourseIds) { course, askingIds ->
        course != null && askingIds.contains(course.id)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _coursesLoading = MutableStateFlow(true)
    val coursesLoading: StateFlow<Boolean> = _coursesLoading.asStateFlow()

    private var loadCoursesJob: Job? = null
    private var loadDocsJob: Job? = null

    fun loadCourses(preferredCourseId: String? = null, silent: Boolean = false) {
        loadCoursesJob?.cancel()
        loadCoursesJob = viewModelScope.launch {
            if (!silent || _courses.value.isEmpty()) {
                _coursesLoading.value = true
            }
            repository.getCourses()
                .onSuccess { list ->
                    _courses.value = list
                    val prevCourseId = _selectedCourse.value?.id
                    val chosen = if (preferredCourseId != null) {
                        list.find { it.id == preferredCourseId } ?: list.firstOrNull()
                    } else {
                        prevCourseId?.let { curId -> list.find { it.id == curId } } ?: list.firstOrNull()
                    }
                    _selectedCourse.value = chosen
                    _coursesLoading.value = false
                    if (chosen != null) {
                        val sameCourse = chosen.id == prevCourseId
                        loadDocumentsForCourse(chosen.id, preserveSelection = sameCourse)
                    } else {
                        _documents.value = emptyList()
                        _selectedDocument.value = null
                    }
                }
                .onFailure {
                    _coursesLoading.value = false
                }
        }
    }

    private fun loadDocumentsForCourse(courseId: String, preserveSelection: Boolean) {
        loadDocsJob?.cancel()
        loadDocsJob = viewModelScope.launch {
            repository.getCourseDocuments(courseId)
                .onSuccess { docs ->
                    if (_selectedCourse.value?.id == courseId) {
                        val readyDocs = docs.filter { it.status == "ready" }
                        _documents.value = readyDocs
                        _selectedDocument.value = if (preserveSelection) {
                            _selectedDocument.value?.let { prev -> readyDocs.find { it.id == prev.id } }
                        } else {
                            null
                        }
                    }
                }
                .onFailure {
                    if (_selectedCourse.value?.id == courseId && !preserveSelection) {
                        _documents.value = emptyList()
                        _selectedDocument.value = null
                    }
                }
        }
    }

    fun selectCourse(course: CourseResponse) {
        if (_selectedCourse.value?.id == course.id) return
        _selectedCourse.value = course
        _documents.value = emptyList()
        _selectedDocument.value = null
        loadDocumentsForCourse(course.id, preserveSelection = false)
    }

    fun selectDocument(document: DocumentResponse?) {
        _selectedDocument.value = document
    }

    fun clearMessages() {
        val courseId = _selectedCourse.value?.id ?: return
        _messagesByCourse.value = _messagesByCourse.value - courseId
    }

    fun retryQuestion(errorMessageId: String, questionText: String) {
        val course = _selectedCourse.value ?: return
        if (_askingCourseIds.value.contains(course.id)) return
        val updated = _messagesByCourse.value[course.id].orEmpty().filterNot { it.id == errorMessageId }
        _messagesByCourse.value = _messagesByCourse.value + (course.id to updated)
        askQuestionInternal(course, questionText, appendUserMessage = false)
    }

    fun askQuestion(questionText: String) {
        val course = _selectedCourse.value ?: return
        askQuestionInternal(course, questionText, appendUserMessage = true)
    }

    private fun appendMessage(courseId: String, message: ChatMessage) {
        val current = _messagesByCourse.value[courseId].orEmpty()
        _messagesByCourse.value = _messagesByCourse.value + (courseId to (current + message))
    }

    private fun askQuestionInternal(
        course: CourseResponse,
        questionText: String,
        appendUserMessage: Boolean
    ) {
        val trimmed = questionText.trim()
        if (trimmed.isEmpty() || _askingCourseIds.value.contains(course.id)) return
        val documentId = _selectedDocument.value?.id

        if (appendUserMessage) {
            val userMsg = ChatMessage(
                id = "u-${System.currentTimeMillis()}",
                isUser = true,
                text = trimmed
            )
            appendMessage(course.id, userMsg)
        }
        _askingCourseIds.value = _askingCourseIds.value + course.id

        viewModelScope.launch {
            repository.askAssistant(course.id, documentId, trimmed)
                .onSuccess { res ->
                    val aiMsg = ChatMessage(
                        id = "a-${System.currentTimeMillis()}",
                        isUser = false,
                        text = res.answer,
                        foundInContext = res.foundInContext,
                        citations = res.citations
                    )
                    appendMessage(course.id, aiMsg)
                    _askingCourseIds.value = _askingCourseIds.value - course.id
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
                    appendMessage(course.id, errMsg)
                    _askingCourseIds.value = _askingCourseIds.value - course.id
                }
        }
    }
}

