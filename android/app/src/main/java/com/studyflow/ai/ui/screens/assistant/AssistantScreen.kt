package com.studyflow.ai.ui.screens.assistant

import android.content.res.Configuration
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.studyflow.ai.data.model.AssistantSessionResponse
import com.studyflow.ai.data.model.Citation
import com.studyflow.ai.ui.components.EmptyStateView
import com.studyflow.ai.ui.components.LoadingStateView
import com.studyflow.ai.ui.theme.StudyFlowTheme
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AssistantScreen(
    preferredCourseId: String? = null,
    onNavigateToCourses: () -> Unit,
    onOpenCitationDocument: (documentId: String, page: Int, title: String) -> Unit = { _, _, _ -> },
    viewModel: AssistantViewModel = viewModel()
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val courses by viewModel.courses.collectAsState()
    val selectedCourse by viewModel.selectedCourse.collectAsState()
    val documents by viewModel.documents.collectAsState()
    val selectedDocument by viewModel.selectedDocument.collectAsState()
    val sessions by viewModel.sessions.collectAsState()
    val activeSession by viewModel.activeSession.collectAsState()
    val messages by viewModel.messages.collectAsState()
    val isAsking by viewModel.isAsking.collectAsState()
    val sessionsLoading by viewModel.sessionsLoading.collectAsState()
    val messagesLoading by viewModel.messagesLoading.collectAsState()
    val historyError by viewModel.historyError.collectAsState()
    val coursesLoading by viewModel.coursesLoading.collectAsState()

    var inputText by remember { mutableStateOf("") }
    var courseMenuExpanded by remember { mutableStateOf(false) }
    var docMenuExpanded by remember { mutableStateOf(false) }
    var showHistorySheet by remember { mutableStateOf(false) }
    var sessionPendingDelete by remember { mutableStateOf<AssistantSessionResponse?>(null) }
    var skipNextResume by remember { mutableStateOf(true) }
    val latestPreferredCourseId by rememberUpdatedState(preferredCourseId)
    val listState = rememberLazyListState()

    LaunchedEffect(preferredCourseId) {
        viewModel.loadCourses(preferredCourseId, silent = courses.isNotEmpty())
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                if (skipNextResume) {
                    skipNextResume = false
                } else {
                    viewModel.loadCourses(latestPreferredCourseId, silent = true)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Trợ lý AI", fontWeight = FontWeight.Bold)
                        selectedCourse?.let { course ->
                            val subtitle = activeSession?.title ?: "Đoạn chat mới"
                            Text(
                                text = "${course.name} • $subtitle",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                actions = {
                    if (selectedCourse != null) {
                        IconButton(
                            onClick = { showHistorySheet = true },
                            enabled = !isAsking
                        ) {
                            if (sessions.isNotEmpty()) {
                                BadgedBox(
                                    badge = {
                                        Badge {
                                            Text(
                                                text = if (sessions.size > 99) "99+" else sessions.size.toString()
                                            )
                                        }
                                    }
                                ) {
                                    Icon(Icons.Outlined.History, contentDescription = "Lịch sử phiên chat")
                                }
                            } else {
                                Icon(Icons.Outlined.History, contentDescription = "Lịch sử phiên chat")
                            }
                        }
                        if (activeSession != null || messages.isNotEmpty()) {
                            IconButton(
                                onClick = { viewModel.startNewSession() },
                                enabled = !isAsking && !messagesLoading
                            ) {
                                Icon(Icons.Outlined.AddComment, contentDescription = "Đoạn chat mới")
                            }
                            IconButton(
                                onClick = {
                                    val target = activeSession
                                    if (target != null) {
                                        sessionPendingDelete = target
                                    } else {
                                        viewModel.clearUnsavedDraft()
                                    }
                                },
                                enabled = !isAsking && !messagesLoading
                            ) {
                                Icon(Icons.Outlined.DeleteOutline, contentDescription = "Xóa đoạn chat hiện tại")
                            }
                        }
                    }
                    IconButton(
                        onClick = { viewModel.loadCourses(preferredCourseId, silent = courses.isNotEmpty()) },
                        enabled = !isAsking && !messagesLoading
                    ) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "Làm mới")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (coursesLoading) {
                LoadingStateView("Đang tải ngữ cảnh môn học...")
            } else if (courses.isEmpty()) {
                EmptyStateView(
                    title = "Chưa có môn học nào",
                    description = "Bạn cần tạo ít nhất một môn học và tải lên tài liệu PDF/TXT trước khi hỏi AI.",
                    actionLabel = "Đi tới Môn học",
                    onActionClick = onNavigateToCourses
                )
            } else {
                // Bộ chọn môn học (group ngữ cảnh)
                Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                    ExposedDropdownMenuBox(
                        expanded = courseMenuExpanded,
                        onExpandedChange = { courseMenuExpanded = !courseMenuExpanded }
                    ) {
                        OutlinedTextField(
                            value = selectedCourse?.name ?: "Chọn môn học",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Ngữ cảnh môn học") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = courseMenuExpanded) },
                            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
                            shape = RoundedCornerShape(12.dp)
                        )
                        ExposedDropdownMenu(
                            expanded = courseMenuExpanded,
                            onDismissRequest = { courseMenuExpanded = false }
                        ) {
                            courses.forEach { course ->
                                DropdownMenuItem(
                                    text = { Text("${course.name} (${course.documentCount} tài liệu)") },
                                    onClick = {
                                        viewModel.selectCourse(course)
                                        courseMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                // Bộ chọn tài liệu tùy chọn (nếu môn có tài liệu đã sẵn sàng)
                if (documents.isNotEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                        ExposedDropdownMenuBox(
                            expanded = docMenuExpanded,
                            onExpandedChange = { docMenuExpanded = !docMenuExpanded }
                        ) {
                            OutlinedTextField(
                                value = selectedDocument?.title ?: "Tất cả tài liệu đã sẵn sàng (${documents.size})",
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Tài liệu cụ thể (tùy chọn)") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = docMenuExpanded) },
                                modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
                                shape = RoundedCornerShape(12.dp)
                            )
                            ExposedDropdownMenu(
                                expanded = docMenuExpanded,
                                onDismissRequest = { docMenuExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Tất cả tài liệu đã sẵn sàng") },
                                    onClick = {
                                        viewModel.selectDocument(null)
                                        docMenuExpanded = false
                                    }
                                )
                                documents.forEach { doc ->
                                    DropdownMenuItem(
                                        text = { Text(doc.title) },
                                        onClick = {
                                            viewModel.selectDocument(doc)
                                            docMenuExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                } else {
                    Text(
                        text = "Môn này chưa có tài liệu nào ở trạng thái sẵn sàng. Bạn vẫn có thể hỏi thử hoặc tải PDF/TXT lên ở tab Môn học.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }

                // Thanh chuyển nhanh phiên chat theo đúng group môn học
                selectedCourse?.let { currentCourse ->
                    SessionContextStrip(
                        courseName = currentCourse.name,
                        activeSession = activeSession,
                        sessionCount = sessions.size,
                        sessionsLoading = sessionsLoading,
                        hasMessages = messages.isNotEmpty(),
                        isBusy = isAsking || messagesLoading,
                        onOpenHistory = { showHistorySheet = true },
                        onStartNewSession = { viewModel.startNewSession() }
                    )
                }

                // Thông báo lỗi khi tải/xóa lịch sử nếu có
                historyError?.let { errMsg ->
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = errMsg,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = { viewModel.reloadCurrentCourseHistory() }) {
                                Text("Thử lại")
                            }
                            TextButton(onClick = { viewModel.dismissHistoryError() }) {
                                Text("Đóng")
                            }
                        }
                    }
                }

                // Danh sách tin nhắn
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    if (messagesLoading) {
                        LoadingStateView("Đang tải lịch sử đoạn chat...")
                    } else if (messages.isEmpty()) {
                        val hasExistingSessions = sessions.isNotEmpty()
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(
                                    horizontal = 20.dp,
                                    vertical = if (hasExistingSessions) 10.dp else 16.dp
                                ),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = if (hasExistingSessions) Arrangement.Top else Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.AutoAwesome,
                                contentDescription = null,
                                modifier = Modifier.size(if (hasExistingSessions) 34.dp else 52.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(if (hasExistingSessions) 6.dp else 10.dp))
                            Text(
                                text = "Đoạn chat mới • ${selectedCourse?.name ?: ""}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Đặt câu hỏi về kiến thức trong môn '${selectedCourse?.name ?: ""}'. Mỗi đoạn chat lưu tách biệt theo môn và dẫn nguồn xác thực.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            if (documents.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(if (hasExistingSessions) 10.dp else 16.dp))
                                Text(
                                    text = "Gợi ý câu hỏi nhanh:",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                val suggestions = listOf(
                                    "Tóm tắt nội dung chính từng tài liệu",
                                    "Tổng hợp các khái niệm quan trọng nhất",
                                    "Giải thích quy trình và kỹ thuật cốt lõi",
                                    "Liệt kê ví dụ thực tế trong tài liệu"
                                )
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    suggestions.forEach { prompt ->
                                        SuggestionChip(
                                            onClick = { viewModel.askQuestion(prompt) },
                                            enabled = !isAsking && selectedCourse != null,
                                            label = {
                                                Text(
                                                    text = prompt,
                                                    style = MaterialTheme.typography.labelMedium
                                                )
                                            },
                                            icon = {
                                                Icon(
                                                    imageVector = Icons.Outlined.QuestionAnswer,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        )
                                    }
                                }
                            }
                            if (hasExistingSessions) {
                                Spacer(modifier = Modifier.height(12.dp))
                                HorizontalDivider()
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Hoặc tiếp tục chat cũ (${selectedCourse?.name})",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    if (sessions.size > 3) {
                                        TextButton(onClick = { showHistorySheet = true }) {
                                            Text("Xem tất cả (${sessions.size})")
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    sessions.take(3).forEach { sess ->
                                        RecentSessionShortcutCard(
                                            session = sess,
                                            onClick = { viewModel.selectSession(sess) }
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(messages, key = { it.id }) { msg ->
                                ChatBubble(
                                    message = msg,
                                    onRetry = {
                                        msg.retryQuestion?.let { q ->
                                            viewModel.retryQuestion(msg.id, q)
                                        }
                                    },
                                    onOpenCitation = { cite ->
                                        onOpenCitationDocument(
                                            cite.documentId,
                                            cite.pageNumber ?: 1,
                                            cite.documentTitle
                                        )
                                    }
                                )
                            }
                            if (isAsking) {
                                item(key = "loading_indicator") {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(8.dp)
                                    ) {
                                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Đang truy xuất đa tài liệu và tổng hợp câu trả lời...",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            } else {
                                val lastMsg = messages.lastOrNull()
                                if (lastMsg != null && !lastMsg.isUser && !lastMsg.isError && lastMsg.foundInContext) {
                                    item(key = "follow_up_${lastMsg.id}") {
                                        val followUps = listOf(
                                            "Giải thích chi tiết hơn",
                                            "Cho ví dụ cụ thể trong tài liệu",
                                            "Tóm tắt thành ý chính ngắn gọn"
                                        )
                                        Column(modifier = Modifier.padding(top = 2.dp)) {
                                            Text(
                                                text = "Hỏi tiếp theo mạch này:",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Spacer(modifier = Modifier.height(6.dp))
                                            FlowRow(
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                verticalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                followUps.forEach { followUp ->
                                                    SuggestionChip(
                                                        onClick = { viewModel.askQuestion(followUp) },
                                                        enabled = selectedCourse != null,
                                                        label = {
                                                            Text(
                                                                text = followUp,
                                                                style = MaterialTheme.typography.labelMedium
                                                            )
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Ô nhập câu hỏi
                Surface(
                    tonalElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = inputText,
                            onValueChange = { inputText = it },
                            placeholder = { Text("Nhập câu hỏi của bạn...") },
                            modifier = Modifier.weight(1f),
                            maxLines = 3,
                            shape = RoundedCornerShape(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        FilledIconButton(
                            onClick = {
                                viewModel.askQuestion(inputText)
                                inputText = ""
                            },
                            enabled = inputText.isNotBlank() && !isAsking && !messagesLoading && selectedCourse != null
                        ) {
                            Icon(Icons.AutoMirrored.Outlined.Send, contentDescription = "Gửi câu hỏi")
                        }
                    }
                }
            }
        }
    }

    if (showHistorySheet && selectedCourse != null) {
        SessionHistoryBottomSheet(
            courseName = selectedCourse!!.name,
            sessions = sessions,
            activeSessionId = activeSession?.id,
            isLoading = sessionsLoading,
            isBusy = isAsking,
            onDismiss = { showHistorySheet = false },
            onSelectSession = { sess ->
                viewModel.selectSession(sess)
                showHistorySheet = false
            },
            onStartNewSession = {
                viewModel.startNewSession()
                showHistorySheet = false
            },
            onRequestDeleteSession = { sess ->
                sessionPendingDelete = sess
            }
        )
    }

    sessionPendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { sessionPendingDelete = null },
            title = { Text("Xóa đoạn chat?", fontWeight = FontWeight.Bold) },
            text = {
                Text("Đoạn chat \"${target.title}\" và toàn bộ tin nhắn bên trong sẽ bị xóa vĩnh viễn.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteSession(target)
                        sessionPendingDelete = null
                    }
                ) {
                    Text("Xóa", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { sessionPendingDelete = null }) {
                    Text("Hủy")
                }
            }
        )
    }
}

@Composable
private fun SessionContextStrip(
    courseName: String,
    activeSession: AssistantSessionResponse?,
    sessionCount: Int,
    sessionsLoading: Boolean,
    hasMessages: Boolean,
    isBusy: Boolean,
    onOpenHistory: () -> Unit,
    onStartNewSession: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
            modifier = Modifier
                .weight(1f)
                .clickable(enabled = !isBusy) { onOpenHistory() }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (activeSession != null) Icons.Outlined.Forum else Icons.Outlined.EditNote,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                val label = when {
                    activeSession != null -> "Tiếp tục: ${activeSession.title}"
                    sessionsLoading && sessionCount == 0 -> "Đang tải phiên chat..."
                    sessionCount > 0 -> "Đoạn chat mới ($sessionCount phiên đã lưu)"
                    else -> "Đoạn chat mới • $courseName"
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = Icons.Outlined.UnfoldMore,
                    contentDescription = "Chọn phiên chat",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        if (activeSession != null || hasMessages) {
            FilledTonalButton(
                onClick = onStartNewSession,
                enabled = !isBusy,
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier.height(36.dp)
            ) {
                Icon(Icons.Outlined.AddComment, contentDescription = null, modifier = Modifier.size(15.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Chat mới", style = MaterialTheme.typography.labelMedium)
            }
        } else if (sessionCount > 0) {
            OutlinedButton(
                onClick = onOpenHistory,
                enabled = !isBusy,
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier.height(36.dp)
            ) {
                Icon(Icons.Outlined.History, contentDescription = null, modifier = Modifier.size(15.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Chat cũ ($sessionCount)", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun RecentSessionShortcutCard(
    session: AssistantSessionResponse,
    onClick: () -> Unit
) {
    OutlinedCard(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = session.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = formatSessionTime(session.updatedAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            val docScope = session.documentTitle ?: "Tất cả tài liệu"
            Text(
                text = "$docScope • ${session.messageCount} tin nhắn",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SessionHistoryBottomSheet(
    courseName: String,
    sessions: List<AssistantSessionResponse>,
    activeSessionId: String?,
    isLoading: Boolean,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onSelectSession: (AssistantSessionResponse) -> Unit,
    onStartNewSession: () -> Unit,
    onRequestDeleteSession: (AssistantSessionResponse) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Phiên chat — $courseName",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Tách riêng theo ngữ cảnh môn để tránh trôi câu trả lời.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Outlined.Close, contentDescription = "Đóng")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            FilledTonalButton(
                onClick = onStartNewSession,
                enabled = !isBusy,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Outlined.AddComment, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Bắt đầu đoạn chat mới cho môn $courseName")
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (isLoading && sessions.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp))
                }
            } else if (sessions.isEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Môn '$courseName' chưa có đoạn chat nào được lưu. Đặt câu hỏi đầu tiên để tự động tạo phiên mới.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(20.dp)
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 380.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(sessions, key = { it.id }) { session ->
                        val isActive = session.id == activeSessionId
                        OutlinedCard(
                            onClick = { onSelectSession(session) },
                            enabled = !isBusy,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(
                                width = if (isActive) 1.5.dp else 1.dp,
                                color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                            ),
                            colors = CardDefaults.outlinedCardColors(
                                containerColor = if (isActive) {
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                                } else {
                                    MaterialTheme.colorScheme.surface
                                }
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 12.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = session.title,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f, fill = false)
                                        )
                                        if (isActive) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Surface(
                                                color = MaterialTheme.colorScheme.primary,
                                                shape = RoundedCornerShape(6.dp)
                                            ) {
                                                Text(
                                                    text = "Đang mở",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onPrimary,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    val scopeLabel = session.documentTitle ?: "Tất cả tài liệu"
                                    Text(
                                        text = "$scopeLabel • ${session.messageCount} tin nhắn • ${formatSessionTime(session.updatedAt)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    session.lastMessagePreview?.takeIf { it.isNotBlank() }?.let { preview ->
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = preview,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                IconButton(
                                    onClick = { onRequestDeleteSession(session) },
                                    enabled = !isBusy
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.DeleteOutline,
                                        contentDescription = "Xóa phiên chat",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatSessionTime(isoUtc: String): String {
    if (isoUtc.isBlank()) return "Vừa xong"
    return try {
        val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val date = parser.parse(isoUtc) ?: return isoUtc.take(10)
        val formatter = SimpleDateFormat("HH:mm dd/MM", Locale.getDefault()).apply {
            timeZone = TimeZone.getDefault()
        }
        formatter.format(date)
    } catch (_: Exception) {
        isoUtc.take(10)
    }
}

@Composable
private fun ChatBubble(
    message: ChatMessage,
    onRetry: (() -> Unit)? = null,
    onOpenCitation: ((Citation) -> Unit)? = null
) {
    val align = if (message.isUser) Alignment.End else Alignment.Start
    val containerColor = when {
        message.isUser -> MaterialTheme.colorScheme.primary
        message.isError -> MaterialTheme.colorScheme.errorContainer
        else -> MaterialTheme.colorScheme.surface
    }
    val contentColor = when {
        message.isUser -> MaterialTheme.colorScheme.onPrimary
        message.isError -> MaterialTheme.colorScheme.onErrorContainer
        else -> MaterialTheme.colorScheme.onSurface
    }
    val primaryColor = MaterialTheme.colorScheme.primary
    val codeBgColor = MaterialTheme.colorScheme.surfaceVariant
    val codeTextColor = MaterialTheme.colorScheme.onSurface
    @Suppress("DEPRECATION")
    val clipboardManager = LocalClipboardManager.current
    var copied by remember(message.id) { mutableStateOf(false) }

    LaunchedEffect(copied) {
        if (copied) {
            delay(1800)
            copied = false
        }
    }

    val bubbleModifier = if (message.isUser) {
        Modifier.widthIn(max = 300.dp)
    } else {
        Modifier.fillMaxWidth(0.95f)
    }
    val borderStroke = if (!message.isUser && !message.isError) {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    } else {
        null
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = align
    ) {
        Card(
            modifier = bubbleModifier,
            shape = RoundedCornerShape(16.dp),
            border = borderStroke,
            colors = CardDefaults.cardColors(containerColor = containerColor, contentColor = contentColor)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                if (!message.isUser && !message.isError) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Outlined.AutoAwesome,
                                contentDescription = null,
                                tint = primaryColor,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Trợ lý StudyFlow",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = primaryColor
                            )
                        }
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.clickable {
                                clipboardManager.setText(AnnotatedString(message.text))
                                copied = true
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (copied) Icons.Outlined.Check else Icons.Outlined.ContentCopy,
                                    contentDescription = "Sao chép câu trả lời",
                                    tint = if (copied) primaryColor else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (copied) "Đã chép" else "Chép",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (copied) primaryColor else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                if (message.isUser || message.isError) {
                    Text(
                        text = message.text,
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    val formattedBody = remember(message.text, primaryColor, codeBgColor, codeTextColor) {
                        formatAssistantMarkdown(
                            rawText = message.text,
                            headingColor = primaryColor,
                            codeBgColor = codeBgColor,
                            codeTextColor = codeTextColor
                        )
                    }
                    Text(
                        text = formattedBody,
                        style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp)
                    )
                }

                if (message.isError && message.retryQuestion != null && onRetry != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = onRetry,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Thử lại")
                    }
                } else if (!message.isUser && !message.isError) {
                    if (!message.foundInContext) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Không tìm thấy trích dẫn phù hợp trong tài liệu đã nạp.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else if (message.citations.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Nguồn tham khảo (${message.citations.size})",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = primaryColor
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        message.citations.forEach { cite ->
                            var expanded by remember(cite.chunkId) { mutableStateOf(false) }
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp)
                                    .animateContentSize()
                                    .clickable { expanded = !expanded }
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        val pageText = cite.pageNumber?.let { " • Trang $it" } ?: ""
                                        Text(
                                            text = "${cite.documentTitle}$pageText",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.weight(1f)
                                        )
                                        if (onOpenCitation != null) {
                                            Surface(
                                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                                                shape = RoundedCornerShape(6.dp),
                                                modifier = Modifier
                                                    .padding(horizontal = 6.dp)
                                                    .clickable { onOpenCitation(cite) }
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        text = cite.pageNumber?.let { "Mở tr.$it" } ?: "Mở",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = primaryColor
                                                    )
                                                    Spacer(modifier = Modifier.width(3.dp))
                                                    Icon(
                                                        imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                                                        contentDescription = "Mở tài liệu",
                                                        tint = primaryColor,
                                                        modifier = Modifier.size(12.dp)
                                                    )
                                                }
                                            }
                                        }
                                        Icon(
                                            imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                                            contentDescription = if (expanded) "Thu gọn" else "Xem thêm",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "\"${cite.snippet}\"",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = if (expanded) Int.MAX_VALUE else 3,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private val HEADING_REGEX = Regex("^\\s{0,3}(#{1,3})\\s+(.+)$")
private val BULLET_REGEX = Regex("^(\\s*)([-*•])\\s+(.+)$")

private fun formatAssistantMarkdown(
    rawText: String,
    headingColor: Color,
    codeBgColor: Color,
    codeTextColor: Color
): AnnotatedString {
    val normalizedLines = rawText.replace("\r\n", "\n").split("\n")
    var inCodeBlock = false
    var emittedAny = false

    return buildAnnotatedString {
        normalizedLines.forEach { rawLine ->
            val trimmedStart = rawLine.trimStart()
            if (trimmedStart.startsWith("```")) {
                inCodeBlock = !inCodeBlock
                return@forEach
            }

            if (emittedAny) {
                append("\n")
            }
            emittedAny = true

            if (inCodeBlock) {
                withStyle(
                    SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        background = codeBgColor,
                        color = codeTextColor
                    )
                ) {
                    append(" ")
                    append(rawLine)
                    append(" ")
                }
                return@forEach
            }

            val headingMatch = HEADING_REGEX.matchEntire(rawLine)
            if (headingMatch != null) {
                val hashes = headingMatch.groupValues[1]
                val content = headingMatch.groupValues[2].trim()
                val fontSize = if (hashes.length <= 2) 15.sp else 14.sp
                withStyle(
                    SpanStyle(
                        fontWeight = FontWeight.Bold,
                        fontSize = fontSize,
                        color = headingColor
                    )
                ) {
                    appendInlineMarkdown(content, codeBgColor, codeTextColor)
                }
                return@forEach
            }

            val bulletMatch = BULLET_REGEX.matchEntire(rawLine)
            if (bulletMatch != null) {
                val indent = bulletMatch.groupValues[1]
                val content = bulletMatch.groupValues[3]
                val prefix = if (indent.length >= 2) "   ◦ " else "• "
                append(prefix)
                appendInlineMarkdown(content, codeBgColor, codeTextColor)
                return@forEach
            }

            appendInlineMarkdown(rawLine, codeBgColor, codeTextColor)
        }
    }
}

private fun AnnotatedString.Builder.appendInlineMarkdown(
    line: String,
    codeBgColor: Color,
    codeTextColor: Color
) {
    var i = 0
    while (i < line.length) {
        val ch = line[i]
        if (ch == '`') {
            val end = line.indexOf('`', i + 1)
            if (end > i + 1) {
                withStyle(
                    SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        background = codeBgColor,
                        color = codeTextColor
                    )
                ) {
                    append(line.substring(i + 1, end))
                }
                i = end + 1
                continue
            }
        }
        if (ch == '*' && i + 1 < line.length && line[i + 1] == '*') {
            val end = line.indexOf("**", i + 2)
            if (end > i + 2) {
                withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
                    appendInlineMarkdown(line.substring(i + 2, end), codeBgColor, codeTextColor)
                }
                i = end + 2
                continue
            }
        }
        append(ch)
        i++
    }
}

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ChatBubblePreview() {
    StudyFlowTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ChatBubble(
                message = ChatMessage(
                    id = "u1",
                    isUser = true,
                    text = "Khóa chính là gì?"
                )
            )
            ChatBubble(
                message = ChatMessage(
                    id = "a1",
                    isUser = false,
                    text = "Khóa chính (Primary Key) dùng để phân biệt duy nhất mỗi bộ trong quan hệ và không được phép nhận giá trị NULL.",
                    foundInContext = true,
                    citations = listOf(
                        Citation(
                            documentId = "d1",
                            documentTitle = "Giao_trinh_CSDL.pdf",
                            pageNumber = 12,
                            chunkId = "c1",
                            snippet = "Khóa chính là thuộc tính có giá trị duy nhất trong quan hệ và không bao giờ được phép mang giá trị rỗng..."
                        )
                    )
                )
            )
        }
    }
}

