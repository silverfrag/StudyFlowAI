package com.studyflow.ai.ui.screens.assistant

import android.content.res.Configuration
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.studyflow.ai.data.model.Citation
import com.studyflow.ai.ui.components.EmptyStateView
import com.studyflow.ai.ui.components.LoadingStateView
import com.studyflow.ai.ui.theme.StudyFlowTheme

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AssistantScreen(
    preferredCourseId: String? = null,
    onNavigateToCourses: () -> Unit,
    viewModel: AssistantViewModel = viewModel()
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val courses by viewModel.courses.collectAsState()
    val selectedCourse by viewModel.selectedCourse.collectAsState()
    val documents by viewModel.documents.collectAsState()
    val selectedDocument by viewModel.selectedDocument.collectAsState()
    val messages by viewModel.messages.collectAsState()
    val isAsking by viewModel.isAsking.collectAsState()
    val coursesLoading by viewModel.coursesLoading.collectAsState()

    var inputText by remember { mutableStateOf("") }
    var courseMenuExpanded by remember { mutableStateOf(false) }
    var docMenuExpanded by remember { mutableStateOf(false) }
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
                title = { Text("Trợ lý AI", fontWeight = FontWeight.Bold) },
                actions = {
                    if (messages.isNotEmpty()) {
                        IconButton(onClick = { viewModel.clearMessages() }, enabled = !isAsking) {
                            Icon(Icons.Outlined.DeleteSweep, contentDescription = "Xóa hội thoại")
                        }
                    }
                    IconButton(
                        onClick = { viewModel.loadCourses(preferredCourseId, silent = courses.isNotEmpty()) },
                        enabled = !isAsking
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
                // Bộ chọn môn học
                Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
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

                // Danh sách tin nhắn
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    if (messages.isEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.AutoAwesome,
                                contentDescription = null,
                                modifier = Modifier.size(56.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Hỏi đáp theo tài liệu",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Đặt câu hỏi về kiến thức trong môn '${selectedCourse?.name ?: ""}'. Trợ lý sẽ trả lời kèm nguồn trích dẫn xác thực.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            if (documents.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(18.dp))
                                Text(
                                    text = "Gợi ý câu hỏi nhanh:",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                val suggestions = listOf(
                                    "Tóm tắt nội dung chính",
                                    "Liệt kê khái niệm quan trọng",
                                    "Giải thích quy trình cốt lõi"
                                )
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    suggestions.forEach { prompt ->
                                        SuggestionChip(
                                            onClick = { viewModel.askQuestion(prompt) },
                                            enabled = !isAsking && selectedCourse != null,
                                            label = { Text(prompt) },
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
                                    }
                                )
                            }
                            if (isAsking) {
                                item {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(8.dp)
                                    ) {
                                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Đang tìm kiếm tài liệu và tổng hợp...",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
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
                            enabled = inputText.isNotBlank() && !isAsking && selectedCourse != null
                        ) {
                            Icon(Icons.AutoMirrored.Outlined.Send, contentDescription = "Gửi câu hỏi")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatBubble(
    message: ChatMessage,
    onRetry: (() -> Unit)? = null
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

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = align
    ) {
        Card(
            modifier = Modifier.widthIn(max = 310.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = containerColor, contentColor = contentColor)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyMedium
                )

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
                            text = "Nguồn tham khảo:",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        message.citations.forEach { cite ->
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    val pageText = cite.pageNumber?.let { " • Trang $it" } ?: ""
                                    Text(
                                        text = "${cite.documentTitle}$pageText",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "\"${cite.snippet}\"",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
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

