package com.studyflow.ai.ui.screens.documents

import android.content.res.Configuration
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.studyflow.ai.data.model.DocumentResponse
import com.studyflow.ai.data.model.QuizSummaryResponse
import com.studyflow.ai.ui.components.DetailSkeletonView
import com.studyflow.ai.ui.components.ErrorStateView
import com.studyflow.ai.ui.components.StatusPillBadge
import com.studyflow.ai.ui.components.TactileButton
import com.studyflow.ai.ui.components.TactileSurface
import com.studyflow.ai.ui.theme.MidnightInk
import com.studyflow.ai.ui.theme.StudyFlowTheme
import com.studyflow.ai.ui.theme.extractCourseMonogram
import com.studyflow.ai.ui.theme.rememberCourseVisualStyle
import com.studyflow.ai.ui.theme.studyFlowSemanticColors
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Locale

private enum class CourseDetailTab {
    ALL,
    DOCUMENTS,
    QUIZZES
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourseDetailScreen(
    courseId: String,
    onBack: () -> Unit,
    onOpenAssistantForCourse: (String) -> Unit,
    onStartQuiz: (String) -> Unit,
    onOpenDocument: (documentId: String, title: String) -> Unit = { _, _ -> },
    viewModel: CourseDetailViewModel = viewModel()
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState by viewModel.uiState.collectAsState()
    val currentUiState by rememberUpdatedState(uiState)
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val isUploading by viewModel.isUploading.collectAsState()
    val isCreatingQuiz by viewModel.isCreatingQuiz.collectAsState()
    val actionMessage by viewModel.actionMessage.collectAsState()
    val quizError by viewModel.quizError.collectAsState()
    val processingStartMs by viewModel.processingStartMs.collectAsState()

    var currentTimeMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var showCreateQuizDialog by remember { mutableStateOf(false) }
    var docToDelete by remember { mutableStateOf<DocumentResponse?>(null) }
    var selectedTab by remember(courseId) { mutableStateOf(CourseDetailTab.ALL) }

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
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0, 0, 0, 0),
                title = {
                    val titleText = (uiState as? CourseDetailUiState.Success)?.course?.name ?: "Chi tiết môn học"
                    Text(
                        text = titleText,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Quay lại")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refreshCourseDetail(courseId) }) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "Làm mới")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (val state = uiState) {
                is CourseDetailUiState.Loading -> DetailSkeletonView()
                is CourseDetailUiState.Error -> ErrorStateView(state.message) {
                    viewModel.loadCourseDetail(courseId)
                }
                is CourseDetailUiState.Success -> {
                    val semanticColors = studyFlowSemanticColors()
                    val courseStyle = rememberCourseVisualStyle(seed = courseId)
                    val readyCount = state.documents.count { it.status == "ready" }
                    val quizDisplayCount = maxOf(state.course.quizCount, state.quizzes.size)
                    val showDocs = selectedTab != CourseDetailTab.QUIZZES
                    val showQuizzes = selectedTab != CourseDetailTab.DOCUMENTS

                    PullToRefreshBox(
                        isRefreshing = isRefreshing,
                        onRefresh = { viewModel.refreshCourseDetail(courseId) },
                        modifier = Modifier.fillMaxSize()
                    ) {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Saturated Subject Showcase Hero Card + Tactile Dual CTA
                            item(key = "overview") {
                                Box(modifier = Modifier.fillMaxWidth()) {
                                    Box(
                                        modifier = Modifier
                                            .matchParentSize()
                                            .padding(top = 5.dp)
                                            .clip(RoundedCornerShape(24.dp))
                                            .background(courseStyle.tactileLip)
                                    )
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 5.dp)
                                            .clip(RoundedCornerShape(24.dp))
                                            .background(
                                                Brush.linearGradient(
                                                    colors = listOf(courseStyle.accent, courseStyle.gradientEnd)
                                                )
                                            )
                                            .padding(18.dp)
                                    ) {
                                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                                            ) {
                                                Surface(
                                                    color = Color.White.copy(alpha = 0.20f),
                                                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.35f)),
                                                    shape = RoundedCornerShape(14.dp),
                                                    modifier = Modifier.size(48.dp)
                                                ) {
                                                    Box(contentAlignment = Alignment.Center) {
                                                        Text(
                                                            text = extractCourseMonogram(state.course.name),
                                                            style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp),
                                                            fontWeight = FontWeight.ExtraBold,
                                                            color = Color.White
                                                        )
                                                    }
                                                }
                                                Column(
                                                    modifier = Modifier.weight(1f),
                                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                                ) {
                                                    Text(
                                                        text = "${state.documents.size} tài liệu ($readyCount sẵn sàng) · $quizDisplayCount bài quiz",
                                                        style = MaterialTheme.typography.labelLarge,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color.White
                                                    )
                                                    if (!state.course.description.isNullOrBlank()) {
                                                        Text(
                                                            text = state.course.description,
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = Color.White.copy(alpha = 0.88f),
                                                            maxLines = 2,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }
                                                }
                                            }

                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(IntrinsicSize.Max),
                                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                                            ) {
                                                TactileButton(
                                                    onClick = { onOpenAssistantForCourse(courseId) },
                                                    modifier = Modifier.weight(1f).fillMaxHeight(),
                                                    fillHeight = true,
                                                    containerColor = semanticColors.midnightInk,
                                                    contentColor = semanticColors.midnightOnInk,
                                                    lipColor = semanticColors.midnightInkLip,
                                                    borderColor = Color.White.copy(alpha = 0.22f),
                                                    shape = RoundedCornerShape(14.dp),
                                                    lipHeight = 3.dp,
                                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp)
                                                ) {
                                                    Icon(
                                                        Icons.Outlined.AutoAwesome,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(
                                                        text = "Hỏi AI môn này",
                                                        fontWeight = FontWeight.Bold,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }

                                                TactileButton(
                                                    onClick = { showCreateQuizDialog = true },
                                                    modifier = Modifier.weight(1f).fillMaxHeight(),
                                                    fillHeight = true,
                                                    containerColor = Color.White,
                                                    contentColor = MidnightInk,
                                                    lipColor = Color(0xFFD6D8EC),
                                                    shape = RoundedCornerShape(14.dp),
                                                    lipHeight = 3.dp,
                                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp)
                                                ) {
                                                    Icon(
                                                        Icons.Outlined.Quiz,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(
                                                        text = "Tạo Quiz mới",
                                                        fontWeight = FontWeight.ExtraBold,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // Banner thông báo thao tác
                            item(key = "action_message") {
                                AnimatedVisibility(visible = actionMessage != null) {
                                    actionMessage?.let { msg ->
                                        Surface(
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(14.dp),
                                            color = semanticColors.lavenderBento,
                                            border = BorderStroke(1.dp, semanticColors.lavenderBorder)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(
                                                    text = msg,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = semanticColors.lavenderContent,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                TextButton(onClick = { viewModel.clearMessage() }) {
                                                    Text("Đóng")
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // Midnight Ink Pill Segmented Tabs
                            item(key = "segmented_tabs") {
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(50),
                                    color = MaterialTheme.colorScheme.surface,
                                    border = BorderStroke(1.dp, semanticColors.cardBorder)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(4.dp),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        val tabEntries = listOf(
                                            CourseDetailTab.ALL to "Tất cả",
                                            CourseDetailTab.DOCUMENTS to "Tài liệu (${state.documents.size})",
                                            CourseDetailTab.QUIZZES to "Bài quiz (${state.quizzes.size})"
                                        )
                                        tabEntries.forEach { (tab, label) ->
                                            val isSelected = selectedTab == tab
                                            Surface(
                                                onClick = { selectedTab = tab },
                                                modifier = Modifier.weight(1f),
                                                shape = RoundedCornerShape(50),
                                                color = if (isSelected) semanticColors.selectedPill else Color.Transparent
                                            ) {
                                                Box(
                                                    modifier = Modifier.padding(vertical = 9.dp, horizontal = 6.dp),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        text = label,
                                                        style = MaterialTheme.typography.labelLarge,
                                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                                        color = if (isSelected) {
                                                            semanticColors.onSelectedPill
                                                        } else {
                                                            MaterialTheme.colorScheme.onSurfaceVariant
                                                        },
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            if (showDocs) {
                                item(key = "docs_header") {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Tài liệu nguồn",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.ExtraBold
                                            )
                                            Text(
                                                text = "Hỗ trợ PDF & TXT tối đa 20MB",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        TactileButton(
                                            onClick = {
                                                filePickerLauncher.launch(arrayOf("application/pdf", "text/plain"))
                                            },
                                            enabled = !isUploading,
                                            fillWidth = false,
                                            containerColor = semanticColors.lavenderBento,
                                            contentColor = semanticColors.lavenderContent,
                                            lipColor = semanticColors.lavenderLip,
                                            borderColor = semanticColors.lavenderBorder,
                                            shape = RoundedCornerShape(50),
                                            lipHeight = 3.dp,
                                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                                        ) {
                                            if (isUploading) {
                                                CircularProgressIndicator(modifier = Modifier.size(15.dp), strokeWidth = 2.dp)
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Đang tải...")
                                            } else {
                                                Icon(Icons.Outlined.UploadFile, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(5.dp))
                                                Text("Tải PDF / TXT", maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            }
                                        }
                                    }
                                }

                                if (state.documents.isEmpty()) {
                                    item(key = "docs_empty") {
                                        TactileSurface(
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(20.dp),
                                            lipHeight = 3.dp
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
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(
                                                    text = "Tải file PDF hoặc TXT để AI có thể đọc, trả lời câu hỏi và tạo quiz cho môn này.",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Spacer(modifier = Modifier.height(14.dp))
                                                TactileButton(
                                                    onClick = {
                                                        filePickerLauncher.launch(arrayOf("application/pdf", "text/plain"))
                                                    },
                                                    enabled = !isUploading,
                                                    fillWidth = false
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
                                            onOpenClick = { onOpenDocument(doc.id, doc.title) },
                                            onRetryProcessClick = if (canRetryDoc) {
                                                { viewModel.retryDocument(courseId, doc.id) }
                                            } else null,
                                            onDeleteClick = { docToDelete = doc }
                                        )
                                    }
                                }
                            }

                            if (showQuizzes) {
                                item(key = "quizzes_header") {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(top = if (showDocs) 6.dp else 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Bài kiểm tra (${state.quizzes.size})",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.ExtraBold
                                            )
                                            Text(
                                                text = "Làm lại quiz đã tạo hoặc tạo đề mới",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        TactileButton(
                                            onClick = { showCreateQuizDialog = true },
                                            enabled = readyCount > 0,
                                            fillWidth = false,
                                            containerColor = semanticColors.lavenderBento,
                                            contentColor = semanticColors.lavenderContent,
                                            lipColor = semanticColors.lavenderLip,
                                            borderColor = semanticColors.lavenderBorder,
                                            shape = RoundedCornerShape(50),
                                            lipHeight = 3.dp,
                                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                                        ) {
                                            Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Tạo mới", maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                    }
                                }

                                if (state.quizzes.isEmpty()) {
                                    item(key = "quizzes_empty") {
                                        TactileSurface(
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(18.dp),
                                            lipHeight = 3.dp
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
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                    Text(
                                                        text = "Nhấn 'Tạo Quiz mới' sau khi tài liệu sẵn sàng để bắt đầu luyện tập trắc nghiệm.",
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
                            }

                            item(key = "bottom_spacer") {
                                Spacer(modifier = Modifier.height(14.dp))
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
            title = { Text("Xóa tài liệu?", fontWeight = FontWeight.Bold) },
            text = { Text("Bạn có chắc muốn xóa tài liệu '${target.title}'? Dữ liệu trích xuất liên quan cũng sẽ bị xóa.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteDocument(courseId, target.id)
                        docToDelete = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Xóa", fontWeight = FontWeight.Bold)
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
    onOpenClick: () -> Unit = {},
    onRetryProcessClick: (() -> Unit)? = null,
    onDeleteClick: () -> Unit
) {
    val semanticColors = studyFlowSemanticColors()
    val isPdf = doc.mimeType.contains("pdf", ignoreCase = true) || doc.title.endsWith(".pdf", ignoreCase = true)

    val (statusLabel, statusTextColor, statusBgColor) = when (doc.status) {
        "ready" -> Triple("Sẵn sàng", semanticColors.success, semanticColors.successContainer)
        "error" -> Triple("Lỗi xử lý", semanticColors.error, semanticColors.errorContainer)
        else -> Triple("Đang xử lý", semanticColors.warning, semanticColors.warningContainer)
    }
    val fileBadgeBg = if (isPdf) semanticColors.coralBento else semanticColors.skyBento
    val fileBadgeText = when {
        isPdf -> semanticColors.error
        else -> semanticColors.info
    }

    val dateText = formatShortDate(doc.createdAt)
    val sizeText = if (doc.fileSize >= 1024L * 1024L) {
        String.format(Locale.US, "%.1f MB", doc.fileSize / (1024f * 1024f))
    } else {
        "${(doc.fileSize / 1024).coerceAtLeast(1)} KB"
    }
    val typeTag = if (isPdf) "PDF" else "TXT"
    val metaText = if (dateText.isNotEmpty()) "$sizeText · $dateText" else sizeText

    TactileSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        lipHeight = 3.dp,
        onClick = onOpenClick
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = fileBadgeBg,
                border = BorderStroke(1.dp, if (isPdf) semanticColors.coralBorder else semanticColors.skyBorder),
                shape = RoundedCornerShape(13.dp),
                modifier = Modifier.size(46.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = typeTag,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = fileBadgeText
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(
                    text = doc.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatusPillBadge(
                        label = statusLabel,
                        containerColor = statusBgColor,
                        contentColor = statusTextColor,
                        isPulsing = doc.status == "processing"
                    )
                    Text(
                        text = metaText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (doc.status == "processing") {
                    Text(
                        text = if (isStaleProcessing) {
                            "Xử lý lâu hơn dự kiến. Bạn có thể nhấn thử xử lý lại hoặc tải lại tệp."
                        } else {
                            "Đang trích xuất & lập chỉ mục AI..."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else if (doc.status == "error") {
                    val msg = doc.errorMessage?.takeIf { it.isNotBlank() }
                        ?: "Trích xuất thất bại. Vui lòng thử xử lý lại hoặc xóa tệp này."
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
    val semanticColors = studyFlowSemanticColors()
    val dateText = formatShortDate(quiz.createdAt)
    val attemptText = if (quiz.attemptCount == 0) {
        "Chưa làm"
    } else {
        "Đã làm ${quiz.attemptCount} lần"
    }
    val metaLine = buildString {
        append("${quiz.totalQuestions} câu · $attemptText")
        if (dateText.isNotEmpty()) append(" · $dateText")
    }

    TactileSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        lipHeight = 3.dp
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = semanticColors.lavenderBento,
                border = BorderStroke(1.dp, semanticColors.lavenderBorder),
                shape = RoundedCornerShape(13.dp),
                modifier = Modifier.size(46.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Outlined.Quiz,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(
                    text = quiz.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    quiz.latestScore?.let { sc ->
                        val (bg, fg) = when {
                            sc >= 8.0 -> semanticColors.successContainer to semanticColors.onSuccessContainer
                            sc >= 6.0 -> semanticColors.lavenderBento to semanticColors.lavenderContent
                            else -> semanticColors.warningContainer to semanticColors.onWarningContainer
                        }
                        Surface(color = bg, shape = RoundedCornerShape(50)) {
                            Text(
                                text = "${String.format(Locale.US, "%.1f", sc)}/10",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = fg,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Text(
                        text = metaLine,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            TactileButton(
                onClick = onStartClick,
                fillWidth = false,
                shape = RoundedCornerShape(12.dp),
                lipHeight = 3.dp,
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text(
                    text = if (quiz.attemptCount > 0) "Làm lại" else "Làm bài",
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
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
        title = { Text("Tạo bài Quiz MCQ", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (readyDocuments.isEmpty()) {
                    Surface(
                        color = semanticColors.warningContainer,
                        contentColor = semanticColors.onWarningContainer,
                        shape = RoundedCornerShape(10.dp),
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
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
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
                    shape = RoundedCornerShape(12.dp),
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
                        shape = RoundedCornerShape(10.dp),
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
                enabled = !isCreating && readyDocuments.isNotEmpty(),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (isCreating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        trackColor = Color.White.copy(alpha = 0.28f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Đang tạo...")
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
