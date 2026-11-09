package com.studyflow.ai.ui.screens.assistant

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.studyflow.ai.ui.components.EmptyStateView
import com.studyflow.ai.ui.components.LoadingStateView

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssistantScreen(
    preferredCourseId: String? = null,
    onNavigateToCourses: () -> Unit,
    viewModel: AssistantViewModel = viewModel()
) {
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
    val listState = rememberLazyListState()

    LaunchedEffect(preferredCourseId) {
        viewModel.loadCourses(preferredCourseId)
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
                }

                // Danh sách tin nhắn
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    if (messages.isEmpty()) {
                        EmptyStateView(
                            title = "Hỏi đáp theo tài liệu",
                            description = "Đặt câu hỏi về kiến thức trong môn '${selectedCourse?.name ?: ""}'. Trợ lý sẽ trả lời kèm nguồn trích dẫn xác thực."
                        )
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(messages, key = { it.id }) { msg ->
                                ChatBubble(msg)
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
private fun ChatBubble(message: ChatMessage) {
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

                if (!message.isUser && !message.isError) {
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
                                        maxLines = 2
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

