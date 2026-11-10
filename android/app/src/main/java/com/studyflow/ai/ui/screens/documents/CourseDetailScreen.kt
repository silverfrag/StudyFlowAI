package com.studyflow.ai.ui.screens.documents

import android.content.res.Configuration
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.studyflow.ai.data.model.DocumentResponse
import com.studyflow.ai.data.model.QuizSummaryResponse
import com.studyflow.ai.ui.components.EmptyStateView
import com.studyflow.ai.ui.components.ErrorStateView
import com.studyflow.ai.ui.components.LoadingStateView
import com.studyflow.ai.ui.theme.StudyFlowTheme
import com.studyflow.ai.ui.theme.studyFlowSemanticColors
import java.text.SimpleDateFormat
import java.util.Locale

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
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState by viewModel.uiState.collectAsState()
    val currentUiState by rememberUpdatedState(uiState)
    val isUploading by viewModel.isUploading.collectAsState()
    val isCreatingQuiz by viewModel.isCreatingQuiz.collectAsState()
    val actionMessage by viewModel.actionMessage.collectAsState()
    val quizError by viewModel.quizError.collectAsState()
    val processingStartMs by viewModel.processingStartMs.collectAsState()

    var currentTimeMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
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

    DisposableEffect(lifecycleOwner, courseId) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && currentUiState is CourseDetailUiState.Success) {
                viewModel.refreshDocumentsSilently(courseId)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val hasProcessingDocs = (uiState as? CourseDetailUiState.Success)
        ?.documents?.any { it.status == "processing" } == true

    LaunchedEffect(courseId, hasProcessingDocs) {
        if (hasProcessingDocs) {
            currentTimeMs = System.currentTimeMillis()
            while (true) {
                delay(3500)
                currentTimeMs = System.currentTimeMillis()
                viewModel.refreshDocumentsSilently(courseId)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val titleText = (uiState as? CourseDetailUiState.Success)?.course?.name ?: "Chi tiết môn học"
                    Text(titleText, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
                    val quizDisplayCount = maxOf(state.course.quizCount, state.quizzes.size)

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Thẻ tổng quan môn + hành động nhanh
                        item(key = "overview") {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
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
                                        text = "${state.documents.size} tài liệu ($readyCount sẵn sàng)  •  $quizDisplayCount bài quiz",
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
                        }

                        // Banner thông báo nếu có
                        actionMessage?.let { msg ->
                            item(key = "action_message") {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
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
                        }

                        // Header phần tài liệu
                        item(key = "docs_header") {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
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
                        }

                        if (state.documents.isEmpty()) {
                            item(key = "docs_empty") {
                                OutlinedCard(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.fillMaxWidth().padding(20.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.UploadFile,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(36.dp)
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = "Chưa có tài liệu nào",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "Tải file PDF hoặc TXT để AI có thể đọc, trả lời câu hỏi và tạo quiz cho môn này.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.height(12.dp))
                                        OutlinedButton(
                                            onClick = {
                                                filePickerLauncher.launch(arrayOf("application/pdf", "text/plain"))
                                            },
                                            enabled = !isUploading,
                                            shape = RoundedCornerShape(10.dp)
                                        ) {
                                            Text("Chọn tệp PDF / TXT")
                                        }
                                    }
                                }
                            }
                        } else {
                            items(state.documents, key = { "doc-${it.id}" }) { doc ->
                                val startedAt = processingStartMs[doc.id] ?: currentTimeMs
                                val isStaleProcessing = doc.status == "processing" &&
                                    (currentTimeMs - startedAt) >= 180_000L
                                val canRetryDoc = doc.status == "error" || isStaleProcessing
                                DocumentCard(
                                    doc = doc,
                                    isStaleProcessing = isStaleProcessing,
                                    onRetryProcessClick = if (canRetryDoc) {
                                        { viewModel.retryDocument(courseId, doc.id) }
                                    } else null,
                                    onDeleteClick = { docToDelete = doc }
                                )
                            }
                        }

                        // Header phần bài kiểm tra đã tạo
                        item(key = "quizzes_header") {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Bài kiểm tra (${state.quizzes.size})",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Làm lại quiz đã tạo hoặc tạo đề mới",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                TextButton(
                                    onClick = { showCreateQuizDialog = true },
                                    enabled = readyCount > 0
                                ) {
                                    Text("Tạo mới")
                                }
                            }
                        }

                        if (state.quizzes.isEmpty()) {
                            item(key = "quizzes_empty") {
                                OutlinedCard(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Quiz,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(28.dp)
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column {
                                            Text(
                                                text = "Chưa có bài kiểm tra nào",
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Text(
                                                text = "Nhấn 'Tạo Quiz' sau khi tài liệu sẵn sàng để bắt đầu luyện tập trắc nghiệm.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        } else {
                            items(state.quizzes, key = { "quiz-${it.id}" }) { quiz ->
                                QuizSummaryCard(
                                    quiz = quiz,
                                    onStartClick = { onStartQuiz(quiz.id) }
                                )
                            }
                        }

                        item(key = "bottom_spacer") {
                            Spacer(modifier = Modifier.height(12.dp))
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
            onClearError = { viewModel.clearQuizError() },
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
    isStaleProcessing: Boolean = false,
    onRetryProcessClick: (() -> Unit)? = null,
    onDeleteClick: () -> Unit
) {
    val semanticColors = studyFlowSemanticColors()
    val (statusLabel, statusTextColor, statusBgColor) = when (doc.status) {
        "ready" -> Triple("Sẵn sàng", semanticColors.success, semanticColors.successContainer)
        "error" -> Triple("Lỗi xử lý", semanticColors.error, semanticColors.errorContainer)
        else -> Triple("Đang xử lý", semanticColors.warning, semanticColors.warningContainer)
    }
    val dateText = formatShortDate(doc.createdAt)
    val sizeText = "${(doc.fileSize / 1024).coerceAtLeast(1)} KB"
    val metaText = if (dateText.isNotEmpty()) "$sizeText · $dateText" else sizeText

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
                        contentColor = statusTextColor,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            if (doc.status == "processing") {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(11.dp),
                                    strokeWidth = 1.6.dp,
                                    color = statusTextColor
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                            }
                            Text(
                                text = statusLabel,
                                color = statusTextColor,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = metaText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (doc.status == "processing") {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (isStaleProcessing) {
                            "Xử lý lâu hơn dự kiến. Bạn có thể nhấn thử xử lý lại hoặc tải lại tệp."
                        } else {
                            "Đang trích xuất & lập chỉ mục AI (tệp lớn có thể mất 1–2 phút)..."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else if (doc.status == "error") {
                    val msg = doc.errorMessage?.takeIf { it.isNotBlank() }
                        ?: "Trích xuất thất bại. Vui lòng thử xử lý lại hoặc xóa tệp này."
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.bodySmall,
                        color = semanticColors.error
                    )
                }
            }
            if ((doc.status == "error" || isStaleProcessing) && onRetryProcessClick != null) {
                IconButton(onClick = onRetryProcessClick) {
                    Icon(
                        imageVector = Icons.Outlined.Refresh,
                        contentDescription = "Thử xử lý lại",
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

@Composable
private fun QuizSummaryCard(
    quiz: QuizSummaryResponse,
    onStartClick: () -> Unit
) {
    val dateText = formatShortDate(quiz.createdAt)
    val attemptText = if (quiz.attemptCount == 0) {
        "Chưa làm"
    } else {
        "Đã làm ${quiz.attemptCount} lần"
    }
    val scoreText = quiz.latestScore?.let {
        " · Điểm gần nhất: ${String.format(Locale.US, "%.1f", it)}/10"
    } ?: ""
    val metaLine = buildString {
        append("${quiz.totalQuestions} câu · $attemptText$scoreText")
        if (dateText.isNotEmpty()) append(" · $dateText")
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
                imageVector = Icons.Outlined.Quiz,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = quiz.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = metaLine,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            FilledTonalButton(
                onClick = onStartClick,
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(if (quiz.attemptCount > 0) "Làm lại" else "Làm bài")
            }
        }
    }
}

private fun formatShortDate(isoTimestamp: String): String {
    val dateMatch = Regex("""^(\d{4})-(\d{2})-(\d{2})""").find(isoTimestamp) ?: return ""
    val normalized = Regex("""^(\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2})(?:\.\d+)?(Z|[+-]\d{2}:\d{2})?$""")
        .matchEntire(isoTimestamp.trim())
    if (normalized != null) {
        val base = normalized.groupValues[1]
        val zoneRaw = normalized.groupValues[2]
        val zoneNormalized = if (zoneRaw.isEmpty() || zoneRaw == "Z") "+0000" else zoneRaw.replace(":", "")
        return try {
            val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US)
            val date = parser.parse("$base$zoneNormalized")
            if (date != null) {
                SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(date)
            } else {
                "${dateMatch.groupValues[3]}/${dateMatch.groupValues[2]}/${dateMatch.groupValues[1]}"
            }
        } catch (_: Exception) {
            "${dateMatch.groupValues[3]}/${dateMatch.groupValues[2]}/${dateMatch.groupValues[1]}"
        }
    }
    return "${dateMatch.groupValues[3]}/${dateMatch.groupValues[2]}/${dateMatch.groupValues[1]}"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateQuizDialog(
    readyDocuments: List<DocumentResponse>,
    isCreating: Boolean,
    error: String?,
    onClearError: () -> Unit,
    onDismiss: () -> Unit,
    onConfirm: (String?, String?, Int) -> Unit
) {
    val semanticColors = studyFlowSemanticColors()
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
                        color = semanticColors.warningContainer,
                        contentColor = semanticColors.onWarningContainer,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Môn học chưa có tài liệu ở trạng thái 'Sẵn sàng'. Hãy tải lên tệp PDF/TXT và chờ xử lý xong.",
                            style = MaterialTheme.typography.bodySmall,
                            color = semanticColors.onWarningContainer,
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
                                    onClearError()
                                }
                            )
                            readyDocuments.forEach { doc ->
                                DropdownMenuItem(
                                    text = { Text(doc.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                    onClick = {
                                        selectedDoc = doc
                                        docMenuExpanded = false
                                        onClearError()
                                    }
                                )
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = topic,
                    onValueChange = {
                        topic = it
                        onClearError()
                    },
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
                        onValueChange = {
                            questionCount = it
                            onClearError()
                        },
                        valueRange = 3f..10f,
                        steps = 6
                    )
                }
                if (!error.isNullOrBlank()) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
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
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
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
                    id = "d3",
                    courseId = "c1",
                    title = "Chuong_4_Dang_xu_ly.pdf",
                    fileSize = 1288000,
                    mimeType = "application/pdf",
                    status = "processing",
                    errorMessage = null,
                    createdAt = "2026-11-10T07:10:00Z"
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
                onRetryProcessClick = {},
                onDeleteClick = {}
            )
            QuizSummaryCard(
                quiz = QuizSummaryResponse(
                    id = "q1",
                    courseId = "c1",
                    title = "Quiz: Quy trình kiểm thử",
                    totalQuestions = 5,
                    attemptCount = 2,
                    latestScore = 8.0,
                    createdAt = "2026-11-10T08:00:00Z"
                ),
                onStartClick = {}
            )
        }
    }
}

