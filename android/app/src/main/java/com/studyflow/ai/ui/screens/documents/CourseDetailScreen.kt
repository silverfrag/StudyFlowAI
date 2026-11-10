package com.studyflow.ai.ui.screens.documents

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.outlined.*
import kotlinx.coroutines.delay
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.studyflow.ai.data.model.DocumentResponse
import com.studyflow.ai.ui.components.EmptyStateView
import com.studyflow.ai.ui.components.ErrorStateView
import com.studyflow.ai.ui.components.LoadingStateView
import com.studyflow.ai.ui.theme.ErrorRed
import com.studyflow.ai.ui.theme.ErrorRedBg
import com.studyflow.ai.ui.theme.StudyFlowTheme
import com.studyflow.ai.ui.theme.SuccessGreen
import com.studyflow.ai.ui.theme.SuccessGreenBg
import com.studyflow.ai.ui.theme.WarningAmber
import com.studyflow.ai.ui.theme.WarningAmberBg

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourseDetailScreen(
    courseId: String,
    onBack: () -> Unit,
    onOpenAssistantForCourse: (String) -> Unit,
    onStartQuiz: (String) -> Unit,
    viewModel: CourseDetailViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val isUploading by viewModel.isUploading.collectAsState()
    val isCreatingQuiz by viewModel.isCreatingQuiz.collectAsState()
    val actionMessage by viewModel.actionMessage.collectAsState()
    val quizError by viewModel.quizError.collectAsState()

    var showCreateQuizDialog by remember { mutableStateOf(false) }
    var docToDelete by remember { mutableStateOf<DocumentResponse?>(null) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.uploadDocument(context, courseId, uri)
        }
    }

    LaunchedEffect(courseId) {
        viewModel.loadCourseDetail(courseId)
    }

    val hasProcessingDocs = (uiState as? CourseDetailUiState.Success)
        ?.documents?.any { it.status == "processing" } == true

    LaunchedEffect(courseId, hasProcessingDocs) {
        if (hasProcessingDocs) {
            while (true) {
                delay(3500)
                viewModel.refreshDocumentsSilently(courseId)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val titleText = (uiState as? CourseDetailUiState.Success)?.course?.name ?: "Chi tiết môn học"
                    Text(titleText, fontWeight = FontWeight.Bold)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Quay lại")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.loadCourseDetail(courseId) }) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "Làm mới")
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (val state = uiState) {
                is CourseDetailUiState.Loading -> LoadingStateView("Đang tải thông tin môn học...")
                is CourseDetailUiState.Error -> ErrorStateView(state.message) {
                    viewModel.loadCourseDetail(courseId)
                }
                is CourseDetailUiState.Success -> {
                    val readyCount = state.documents.count { it.status == "ready" }
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Thẻ tổng quan môn + hành động nhanh
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                if (!state.course.description.isNullOrBlank()) {
                                    Text(
                                        text = state.course.description,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                }
                                Text(
                                    text = "${state.documents.size} tài liệu ($readyCount sẵn sàng)  •  ${state.course.quizCount} bài quiz",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(12.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = { onOpenAssistantForCourse(courseId) },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Icon(Icons.Outlined.SmartToy, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Hỏi AI")
                                    }
                                    Button(
                                        onClick = { showCreateQuizDialog = true },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Icon(Icons.Outlined.Quiz, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Tạo Quiz")
                                    }
                                }
                            }
                        }

                        // Banner thông báo nếu có
                        actionMessage?.let { msg ->
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = msg,
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.weight(1f)
                                    )
                                    TextButton(onClick = { viewModel.clearMessage() }) {
                                        Text("Đóng")
                                    }
                                }
                            }
                        }

                        // Header phần tài liệu
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Tài liệu (${state.documents.size})",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Hỗ trợ PDF, TXT (tối đa 20MB)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            FilledTonalButton(
                                onClick = {
                                    filePickerLauncher.launch(arrayOf("application/pdf", "text/plain"))
                                },
                                enabled = !isUploading,
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                if (isUploading) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Đang tải...")
                                } else {
                                    Icon(Icons.Outlined.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Tải lên")
                                }
                            }
                        }

                        // Danh sách tài liệu
                        if (state.documents.isEmpty()) {
                            EmptyStateView(
                                title = "Chưa có tài liệu nào",
                                description = "Tải file PDF hoặc TXT để AI có thể đọc, trả lời câu hỏi và tạo quiz cho môn này."
                            )
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(state.documents, key = { it.id }) { doc ->
                                    DocumentCard(
                                        doc = doc,
                                        onRetryUploadClick = {
                                            filePickerLauncher.launch(arrayOf("application/pdf", "text/plain"))
                                        },
                                        onDeleteClick = { docToDelete = doc }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateQuizDialog) {
        val readyDocuments = (uiState as? CourseDetailUiState.Success)
            ?.documents
            ?.filter { it.status == "ready" }
            ?: emptyList()
        CreateQuizDialog(
            readyDocuments = readyDocuments,
            isCreating = isCreatingQuiz,
            error = quizError,
            onDismiss = {
                viewModel.clearQuizError()
                showCreateQuizDialog = false
            },
            onConfirm = { documentId, topic, count ->
                viewModel.generateQuiz(courseId, documentId, topic, count) { quiz ->
                    showCreateQuizDialog = false
                    onStartQuiz(quiz.id)
                }
            }
        )
    }

    docToDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { docToDelete = null },
            title = { Text("Xóa tài liệu?") },
            text = { Text("Bạn có chắc muốn xóa tài liệu '${target.title}'? Dữ liệu trích xuất liên quan cũng sẽ bị xóa.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteDocument(courseId, target.id)
                        docToDelete = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Xóa")
                }
            },
            dismissButton = {
                TextButton(onClick = { docToDelete = null }) {
                    Text("Hủy")
                }
            }
        )
    }
}

@Composable
private fun DocumentCard(
    doc: DocumentResponse,
    onRetryUploadClick: (() -> Unit)? = null,
    onDeleteClick: () -> Unit
) {
    val (statusLabel, statusTextColor, statusBgColor) = when (doc.status) {
        "ready" -> Triple("Sẵn sàng", SuccessGreen, SuccessGreenBg)
        "error" -> Triple("Lỗi xử lý", ErrorRed, ErrorRedBg)
        else -> Triple("Đang xử lý", WarningAmber, WarningAmberBg)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (doc.mimeType.contains("pdf")) Icons.Outlined.PictureAsPdf else Icons.AutoMirrored.Outlined.Article,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = doc.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = statusBgColor,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = statusLabel,
                            color = statusTextColor,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${(doc.fileSize / 1024).coerceAtLeast(1)} KB",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (doc.status == "error") {
                    val msg = doc.errorMessage?.takeIf { it.isNotBlank() }
                        ?: "Trích xuất thất bại. Vui lòng thử tải lại hoặc xóa tệp này."
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            if (doc.status == "error" && onRetryUploadClick != null) {
                IconButton(onClick = onRetryUploadClick) {
                    Icon(
                        imageVector = Icons.Outlined.UploadFile,
                        contentDescription = "Tải lại tài liệu",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            IconButton(onClick = onDeleteClick) {
                Icon(
                    imageVector = Icons.Outlined.DeleteOutline,
                    contentDescription = "Xóa tài liệu",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateQuizDialog(
    readyDocuments: List<DocumentResponse>,
    isCreating: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onConfirm: (String?, String?, Int) -> Unit
) {
    var selectedDoc by remember(readyDocuments) { mutableStateOf<DocumentResponse?>(null) }
    var docMenuExpanded by remember { mutableStateOf(false) }
    var topic by remember { mutableStateOf("") }
    var questionCount by remember { mutableFloatStateOf(5f) }

    AlertDialog(
        onDismissRequest = { if (!isCreating) onDismiss() },
        title = { Text("Tạo bài Quiz MCQ") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (readyDocuments.isEmpty()) {
                    Surface(
                        color = WarningAmberBg,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Môn học chưa có tài liệu ở trạng thái 'Sẵn sàng'. Hãy tải lên tệp PDF/TXT và chờ xử lý xong.",
                            style = MaterialTheme.typography.bodySmall,
                            color = WarningAmber,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                } else {
                    ExposedDropdownMenuBox(
                        expanded = docMenuExpanded,
                        onExpandedChange = { docMenuExpanded = !docMenuExpanded }
                    ) {
                        OutlinedTextField(
                            value = selectedDoc?.title ?: "Tất cả tài liệu đã sẵn sàng (${readyDocuments.size})",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Tài liệu nguồn (tùy chọn)") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = docMenuExpanded) },
                            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
                            singleLine = true
                        )
                        ExposedDropdownMenu(
                            expanded = docMenuExpanded,
                            onDismissRequest = { docMenuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Tất cả tài liệu đã sẵn sàng (${readyDocuments.size})") },
                                onClick = {
                                    selectedDoc = null
                                    docMenuExpanded = false
                                }
                            )
                            readyDocuments.forEach { doc ->
                                DropdownMenuItem(
                                    text = { Text(doc.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                    onClick = {
                                        selectedDoc = doc
                                        docMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = topic,
                    onValueChange = { topic = it },
                    label = { Text("Chủ đề trọng tâm (tùy chọn)") },
                    placeholder = { Text("VD: Khóa chính, Phép JOIN...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Column {
                    Text(
                        text = "Số lượng câu hỏi: ${questionCount.toInt()} câu",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Slider(
                        value = questionCount,
                        onValueChange = { questionCount = it },
                        valueRange = 3f..10f,
                        steps = 6
                    )
                }
                if (!error.isNullOrBlank()) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = error,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(selectedDoc?.id, topic, questionCount.toInt()) },
                enabled = !isCreating && readyDocuments.isNotEmpty()
            ) {
                if (isCreating) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text("Bắt đầu làm")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isCreating) {
                Text("Hủy")
            }
        }
    )
}

@Preview(showBackground = true)
@Composable
private fun DocumentCardPreview() {
    StudyFlowTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            DocumentCard(
                doc = DocumentResponse(
                    id = "d1",
                    courseId = "c1",
                    title = "Giao_trinh_CSDL_Chuong1.pdf",
                    fileSize = 256000,
                    mimeType = "application/pdf",
                    status = "ready",
                    errorMessage = null,
                    createdAt = "2026-11-10T07:00:00Z"
                ),
                onDeleteClick = {}
            )
            DocumentCard(
                doc = DocumentResponse(
                    id = "d2",
                    courseId = "c1",
                    title = "Bai_giang_loi.pdf",
                    fileSize = 128000,
                    mimeType = "application/pdf",
                    status = "error",
                    errorMessage = "PDF dạng ảnh scan chưa trích xuất được văn bản.",
                    createdAt = "2026-11-10T07:05:00Z"
                ),
                onRetryUploadClick = {},
                onDeleteClick = {}
            )
        }
    }
}

