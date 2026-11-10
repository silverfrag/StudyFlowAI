package com.studyflow.ai.ui.screens.assistant

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.studyflow.ai.data.model.AssistantSessionResponse
import com.studyflow.ai.data.model.ChatHistoryItem
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
    val documentId: String? = null,
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

    private val _sessionsByCourse = MutableStateFlow<Map<String, List<AssistantSessionResponse>>>(emptyMap())
    val sessions: StateFlow<List<AssistantSessionResponse>> = combine(_selectedCourse, _sessionsByCourse) { course, map ->
        if (course == null) emptyList() else map[course.id].orEmpty()
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _activeSessionIdByCourse = MutableStateFlow<Map<String, String>>(emptyMap())
    val activeSession: StateFlow<AssistantSessionResponse?> = combine(
        _selectedCourse,
        _sessionsByCourse,
        _activeSessionIdByCourse
    ) { course, sessionsMap, activeMap ->
        if (course == null) null
        else {
            val activeId = activeMap[course.id] ?: return@combine null
            sessionsMap[course.id].orEmpty().find { it.id == activeId }
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _draftModeCourseIds = MutableStateFlow<Set<String>>(emptySet())

    private val _messagesByCourse = MutableStateFlow<Map<String, List<ChatMessage>>>(emptyMap())
    val messages: StateFlow<List<ChatMessage>> = combine(_selectedCourse, _messagesByCourse) { course, map ->
        if (course == null) emptyList() else map[course.id].orEmpty()
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _askingCourseIds = MutableStateFlow<Set<String>>(emptySet())
    val isAsking: StateFlow<Boolean> = combine(_selectedCourse, _askingCourseIds) { course, askingIds ->
        course != null && askingIds.contains(course.id)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _loadingSessionCourseIds = MutableStateFlow<Set<String>>(emptySet())
    val sessionsLoading: StateFlow<Boolean> = combine(_selectedCourse, _loadingSessionCourseIds) { course, ids ->
        course != null && ids.contains(course.id)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _loadingMessageCourseIds = MutableStateFlow<Set<String>>(emptySet())
    val messagesLoading: StateFlow<Boolean> = combine(_selectedCourse, _loadingMessageCourseIds) { course, ids ->
        course != null && ids.contains(course.id)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _historyErrorByCourse = MutableStateFlow<Map<String, String>>(emptyMap())
    val historyError: StateFlow<String?> = combine(_selectedCourse, _historyErrorByCourse) { course, map ->
        if (course == null) null else map[course.id]
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _coursesLoading = MutableStateFlow(true)
    val coursesLoading: StateFlow<Boolean> = _coursesLoading.asStateFlow()

    private var loadCoursesJob: Job? = null
    private var loadDocsJob: Job? = null
    private var loadSessionsJob: Job? = null
    private var loadMessagesJob: Job? = null

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
                        val shouldAutoSelect = !sameCourse || (
                            !_draftModeCourseIds.value.contains(chosen.id) &&
                                _activeSessionIdByCourse.value[chosen.id] == null
                            )
                        loadSessionsForCourse(chosen.id, autoSelectIfNeeded = shouldAutoSelect)
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
                            val activeId = _activeSessionIdByCourse.value[courseId]
                            val activeSess = _sessionsByCourse.value[courseId].orEmpty().find { it.id == activeId }
                            activeSess?.documentId?.let { did -> readyDocs.find { it.id == did } }
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

    private fun loadSessionsForCourse(courseId: String, autoSelectIfNeeded: Boolean) {
        loadSessionsJob?.cancel()
        loadSessionsJob = viewModelScope.launch {
            _loadingSessionCourseIds.value = _loadingSessionCourseIds.value + courseId
            repository.getCourseAssistantSessions(courseId)
                .onSuccess { sessionList ->
                    _loadingSessionCourseIds.value = _loadingSessionCourseIds.value - courseId
                    _sessionsByCourse.value = _sessionsByCourse.value + (courseId to sessionList)
                    _historyErrorByCourse.value = _historyErrorByCourse.value - courseId

                    val activeId = _activeSessionIdByCourse.value[courseId]
                    val inDraftMode = _draftModeCourseIds.value.contains(courseId)

                    if (activeId != null && sessionList.none { it.id == activeId }) {
                        _activeSessionIdByCourse.value = _activeSessionIdByCourse.value - courseId
                        if (!_askingCourseIds.value.contains(courseId)) {
                            _messagesByCourse.value = _messagesByCourse.value - courseId
                        }
                        if (!inDraftMode && sessionList.isNotEmpty()) {
                            selectSessionInternal(sessionList.first(), syncDocument = true)
                        }
                    } else if (activeId == null &&
                        !inDraftMode &&
                        autoSelectIfNeeded &&
                        sessionList.isNotEmpty() &&
                        !_askingCourseIds.value.contains(courseId) &&
                        _messagesByCourse.value[courseId].isNullOrEmpty()
                    ) {
                        selectSessionInternal(sessionList.first(), syncDocument = true)
                    } else if (activeId != null &&
                        _messagesByCourse.value[courseId].isNullOrEmpty() &&
                        !_askingCourseIds.value.contains(courseId)
                    ) {
                        sessionList.find { it.id == activeId }?.let {
                            selectSessionInternal(it, syncDocument = false)
                        }
                    }
                }
                .onFailure { err ->
                    _loadingSessionCourseIds.value = _loadingSessionCourseIds.value - courseId
                    _historyErrorByCourse.value = _historyErrorByCourse.value + (
                        courseId to (err.message ?: "Không thể tải danh sách phiên chat.")
                        )
                }
        }
    }

    private fun refreshSessionsQuietly(courseId: String) {
        viewModelScope.launch {
            repository.getCourseAssistantSessions(courseId)
                .onSuccess { sessionList ->
                    _sessionsByCourse.value = _sessionsByCourse.value + (courseId to sessionList)
                }
        }
    }

    private fun selectSessionInternal(session: AssistantSessionResponse, syncDocument: Boolean) {
        val courseId = session.courseId
        if (_askingCourseIds.value.contains(courseId)) return

        val previousSessionId = _activeSessionIdByCourse.value[courseId]
        loadMessagesJob?.cancel()
        _draftModeCourseIds.value = _draftModeCourseIds.value - courseId
        _historyErrorByCourse.value = _historyErrorByCourse.value - courseId
        _activeSessionIdByCourse.value = _activeSessionIdByCourse.value + (courseId to session.id)
        if (previousSessionId != session.id) {
            _messagesByCourse.value = _messagesByCourse.value - courseId
        }

        if (syncDocument && _selectedCourse.value?.id == courseId) {
            _selectedDocument.value = session.documentId?.let { did ->
                _documents.value.find { it.id == did }
            }
        }

        _loadingMessageCourseIds.value = _loadingMessageCourseIds.value + courseId
        loadMessagesJob = viewModelScope.launch {
            repository.getAssistantSessionMessages(session.id)
                .onSuccess { apiMessages ->
                    _loadingMessageCourseIds.value = _loadingMessageCourseIds.value - courseId
                    if (_activeSessionIdByCourse.value[courseId] == session.id &&
                        !_draftModeCourseIds.value.contains(courseId)
                    ) {
                        val mapped = apiMessages.map { m ->
                            ChatMessage(
                                id = m.id,
                                isUser = m.role == "user",
                                text = m.content,
                                foundInContext = m.foundInContext,
                                citations = m.citations,
                                documentId = m.documentId
                            )
                        }
                        _messagesByCourse.value = _messagesByCourse.value + (courseId to mapped)
                    }
                }
                .onFailure { err ->
                    _loadingMessageCourseIds.value = _loadingMessageCourseIds.value - courseId
                    _historyErrorByCourse.value = _historyErrorByCourse.value + (
                        courseId to (err.message ?: "Không thể tải lịch sử tin nhắn.")
                        )
                }
        }
    }

    fun selectCourse(course: CourseResponse) {
        if (_selectedCourse.value?.id == course.id) return
        loadMessagesJob?.cancel()
        _selectedCourse.value = course
        _documents.value = emptyList()
        _selectedDocument.value = null
        loadDocumentsForCourse(course.id, preserveSelection = false)

        val shouldAutoSelect = !_draftModeCourseIds.value.contains(course.id) &&
            _activeSessionIdByCourse.value[course.id] == null
        loadSessionsForCourse(course.id, autoSelectIfNeeded = shouldAutoSelect)
    }

    fun selectDocument(document: DocumentResponse?) {
        _selectedDocument.value = document
    }

    fun selectSession(session: AssistantSessionResponse) {
        selectSessionInternal(session, syncDocument = true)
    }

    fun startNewSession() {
        val courseId = _selectedCourse.value?.id ?: return
        if (_askingCourseIds.value.contains(courseId)) return
        loadMessagesJob?.cancel()
        _loadingMessageCourseIds.value = _loadingMessageCourseIds.value - courseId
        _draftModeCourseIds.value = _draftModeCourseIds.value + courseId
        _activeSessionIdByCourse.value = _activeSessionIdByCourse.value - courseId
        _messagesByCourse.value = _messagesByCourse.value - courseId
        _historyErrorByCourse.value = _historyErrorByCourse.value - courseId
    }

    fun clearUnsavedDraft() {
        val courseId = _selectedCourse.value?.id ?: return
        if (_askingCourseIds.value.contains(courseId)) return
        if (_activeSessionIdByCourse.value[courseId] == null) {
            _messagesByCourse.value = _messagesByCourse.value - courseId
        }
    }

    fun deleteSession(session: AssistantSessionResponse) {
        val courseId = session.courseId
        if (_askingCourseIds.value.contains(courseId)) return
        viewModelScope.launch {
            repository.deleteAssistantSession(session.id)
                .onSuccess {
                    val remaining = _sessionsByCourse.value[courseId].orEmpty().filterNot { it.id == session.id }
                    _sessionsByCourse.value = _sessionsByCourse.value + (courseId to remaining)
                    _historyErrorByCourse.value = _historyErrorByCourse.value - courseId
                    if (_activeSessionIdByCourse.value[courseId] == session.id) {
                        _draftModeCourseIds.value = _draftModeCourseIds.value + courseId
                        _activeSessionIdByCourse.value = _activeSessionIdByCourse.value - courseId
                        _messagesByCourse.value = _messagesByCourse.value - courseId
                    }
                }
                .onFailure { err ->
                    _historyErrorByCourse.value = _historyErrorByCourse.value + (
                        courseId to (err.message ?: "Không thể xóa phiên chat.")
                        )
                }
        }
    }

    fun dismissHistoryError() {
        val courseId = _selectedCourse.value?.id ?: return
        _historyErrorByCourse.value = _historyErrorByCourse.value - courseId
    }

    fun reloadCurrentCourseHistory() {
        val course = _selectedCourse.value ?: return
        loadSessionsForCourse(
            course.id,
            autoSelectIfNeeded = !_draftModeCourseIds.value.contains(course.id) &&
                _activeSessionIdByCourse.value[course.id] == null
        )
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
        val courseId = course.id
        if (trimmed.isEmpty() || _askingCourseIds.value.contains(courseId)) return

        val documentId = _selectedDocument.value?.id
        val docTitle = _selectedDocument.value?.title
        val currentSessionId = _activeSessionIdByCourse.value[courseId]
        val currentSession = currentSessionId?.let { sid ->
            _sessionsByCourse.value[courseId].orEmpty().find { it.id == sid }
        }

        val docScopeSwitched = currentSession != null && currentSession.documentId != documentId
        val historyPayload = if (docScopeSwitched) {
            emptyList()
        } else {
            _messagesByCourse.value[courseId].orEmpty()
                .filter { !it.isError && it.text.isNotBlank() }
                .takeLastWhile { it.documentId == documentId }
                .takeLast(4)
                .map {
                    ChatHistoryItem(
                        role = if (it.isUser) "user" else "assistant",
                        content = it.text.take(1500)
                    )
                }
        }

        if (appendUserMessage) {
            val userMsg = ChatMessage(
                id = "u-${System.currentTimeMillis()}",
                isUser = true,
                text = trimmed,
                documentId = documentId
            )
            appendMessage(courseId, userMsg)
        }
        _askingCourseIds.value = _askingCourseIds.value + courseId

        viewModelScope.launch {
            repository.askAssistant(courseId, documentId, trimmed, historyPayload, currentSessionId)
                .onSuccess { res ->
                    val aiMsg = ChatMessage(
                        id = "a-${System.currentTimeMillis()}",
                        isUser = false,
                        text = res.answer,
                        foundInContext = res.foundInContext,
                        citations = res.citations,
                        documentId = documentId
                    )
                    appendMessage(courseId, aiMsg)
                    _askingCourseIds.value = _askingCourseIds.value - courseId

                    if (res.sessionId != null) {
                        _draftModeCourseIds.value = _draftModeCourseIds.value - courseId
                        _activeSessionIdByCourse.value = _activeSessionIdByCourse.value + (courseId to res.sessionId)
                        val existingList = _sessionsByCourse.value[courseId].orEmpty()
                        if (existingList.none { it.id == res.sessionId }) {
                            val optimistic = AssistantSessionResponse(
                                id = res.sessionId,
                                courseId = courseId,
                                documentId = documentId,
                                documentTitle = docTitle,
                                title = res.sessionTitle ?: trimmed.take(72),
                                messageCount = 2,
                                lastMessagePreview = res.answer.take(120),
                                createdAt = "",
                                updatedAt = ""
                            )
                            _sessionsByCourse.value = _sessionsByCourse.value + (courseId to (listOf(optimistic) + existingList))
                        }
                        refreshSessionsQuietly(courseId)
                    }
                }
                .onFailure { err ->
                    val errMsg = ChatMessage(
                        id = "e-${System.currentTimeMillis()}",
                        isUser = false,
                        text = err.message ?: "Không thể kết nối trợ lý AI.",
                        foundInContext = false,
                        documentId = documentId,
                        isError = true,
                        retryQuestion = trimmed
                    )
                    appendMessage(courseId, errMsg)
                    _askingCourseIds.value = _askingCourseIds.value - courseId
                }
        }
    }
}
