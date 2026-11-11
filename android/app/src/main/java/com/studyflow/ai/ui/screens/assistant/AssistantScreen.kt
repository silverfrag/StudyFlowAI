package com.studyflow.ai.ui.screens.assistant

import android.content.res.Configuration
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
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
import com.studyflow.ai.ui.components.TactileSurface
import com.studyflow.ai.ui.theme.StudyFlowTheme
import com.studyflow.ai.ui.theme.rememberCourseVisualStyle
import com.studyflow.ai.ui.theme.studyFlowSemanticColors
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
    val semanticColors = studyFlowSemanticColors()

    var inputText by remember { mutableStateOf("") }
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

    LaunchedEffect(messages.size, isAsking) {
        val lastIdx = messages.size + if (isAsking) 0 else -1
        if (lastIdx >= 0) {
            listState.animateScrollToItem(lastIdx)
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 12.dp, top = 10.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Trợ lý AI",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold
                    )
                    selectedCourse?.let { course ->
                        val subtitle = activeSession?.title ?: "Đoạn chat mới"
                        Text(
                            text = "${course.name} · $subtitle",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (selectedCourse != null) {
                        Surface(
                            onClick = { showHistorySheet = true },
                            enabled = !isAsking,
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, semanticColors.cardBorder)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.History,
                                    contentDescription = "Lịch sử phiên chat",
                                    modifier = Modifier.size(15.dp),
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (sessions.isNotEmpty()) "Lịch sử (${sessions.size})" else "Lịch sử",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        if (activeSession != null || messages.isNotEmpty()) {
                            Surface(
                                onClick = { viewModel.startNewSession() },
                                enabled = !isAsking && !messagesLoading,
                                shape = RoundedCornerShape(50),
                                color = semanticColors.selectedPill
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Add,
                                        contentDescription = "Đoạn chat mới",
                                        modifier = Modifier.size(15.dp),
                                        tint = semanticColors.onSelectedPill
                                    )
                                    Text(
                                        text = "Mới",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = semanticColors.onSelectedPill
                                    )
                                }
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
                                enabled = !isAsking && !messagesLoading,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.DeleteOutline,
                                    contentDescription = "Xóa đoạn chat hiện tại",
                                    modifier = Modifier.size(19.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    IconButton(
                        onClick = { viewModel.loadCourses(preferredCourseId, silent = courses.isNotEmpty()) },
                        enabled = !isAsking && !messagesLoading,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Refresh,
                            contentDescription = "Làm mới",
                            modifier = Modifier.size(19.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
        ) {
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
                // Row 1: Horizontal 1-Tap Course Context Switcher Pills + Document Scope Selector
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(courses, key = { it.id }) { course ->
                        val isSelected = selectedCourse?.id == course.id
                        val courseStyle = rememberCourseVisualStyle(seed = course.id)
                        Surface(
                            onClick = {
                                if (!isAsking && !messagesLoading) {
                                    viewModel.selectCourse(course)
                                }
                            },
                            shape = RoundedCornerShape(50),
                            color = if (isSelected) semanticColors.selectedPill else MaterialTheme.colorScheme.surface,
                            border = BorderStroke(
                                width = 1.dp,
                                color = if (isSelected) semanticColors.selectedPill else semanticColors.cardBorder
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(if (isSelected) Color(0xFFA5B4FC) else courseStyle.accent)
                                )
                                Text(
                                    text = course.name,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                    color = if (isSelected) semanticColors.onSelectedPill else MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                // Compact Document Filter Pill & Active Session Scope Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (documents.isNotEmpty()) {
                        Box(modifier = Modifier.weight(1f, fill = false)) {
                            Surface(
                                onClick = { docMenuExpanded = true },
                                shape = RoundedCornerShape(50),
                                color = semanticColors.lavenderBento,
                                border = BorderStroke(1.dp, semanticColors.lavenderBorder)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Description,
                                        contentDescription = null,
                                        tint = semanticColors.lavenderContent,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = selectedDocument?.title ?: "Tài liệu: Tất cả (${documents.size})",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = semanticColors.lavenderContent,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Icon(
                                        imageVector = Icons.Outlined.KeyboardArrowDown,
                                        contentDescription = null,
                                        tint = semanticColors.lavenderContent,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                            DropdownMenu(
                                expanded = docMenuExpanded,
                                onDismissRequest = { docMenuExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Tất cả tài liệu đã sẵn sàng (${documents.size})") },
                                    onClick = {
                                        viewModel.selectDocument(null)
                                        docMenuExpanded = false
                                    }
                                )
                                documents.forEach { doc ->
                                    DropdownMenuItem(
                                        text = { Text(doc.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                        onClick = {
                                            viewModel.selectDocument(doc)
                                            docMenuExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    } else {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = semanticColors.butterBento,
                            border = BorderStroke(1.dp, semanticColors.butterBorder)
                        ) {
                            Text(
                                text = "Môn này chưa có tài liệu sẵn sàng",
                                style = MaterialTheme.typography.labelSmall,
                                color = semanticColors.butterContent,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }

                    if (sessions.isNotEmpty()) {
                        Surface(
                            onClick = { showHistorySheet = true },
                            enabled = !isAsking && !messagesLoading,
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, semanticColors.cardBorder)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = if (activeSession != null) Icons.Outlined.Forum else Icons.Outlined.EditNote,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = activeSession?.title ?: if (sessionsLoading) "Đang tải..." else "${sessions.size} phiên",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.widthIn(max = 120.dp)
                                )
                            }
                        }
                    }
                }

                // Error notice bar
                historyError?.let { errMsg ->
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(12.dp),
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

                // Conversation / Empty 2x2 Bento Prompt Grid
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    if (messagesLoading) {
                        LoadingStateView("Đang tải lịch sử đoạn chat...")
                    } else if (messages.isEmpty()) {
                        val hasExistingSessions = sessions.isNotEmpty()
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = if (hasExistingSessions) Arrangement.Top else Arrangement.Center
                        ) {
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = semanticColors.lavenderBento,
                                border = BorderStroke(1.dp, semanticColors.lavenderBorder),
                                modifier = Modifier.size(56.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Outlined.AutoAwesome,
                                        contentDescription = null,
                                        modifier = Modifier.size(28.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Đoạn chat mới · ${selectedCourse?.name ?: ""}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Trả lời bám sát giáo trình môn '${selectedCourse?.name ?: ""}' kèm nguồn & số trang xác thực.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )

                            if (documents.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(14.dp))
                                Text(
                                    text = "Gợi ý hỏi nhanh",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(8.dp))

                                val promptCards = listOf(
                                    QuadPrompt(
                                        title = "Tóm tắt ý chính",
                                        prompt = "Tóm tắt nội dung chính từng tài liệu",
                                        icon = Icons.Outlined.Summarize,
                                        bg = semanticColors.lavenderBento,
                                        lip = semanticColors.lavenderLip,
                                        border = semanticColors.lavenderBorder,
                                        fg = semanticColors.lavenderContent
                                    ),
                                    QuadPrompt(
                                        title = "Khái niệm cốt lõi",
                                        prompt = "Tổng hợp các khái niệm quan trọng nhất",
                                        icon = Icons.Outlined.Lightbulb,
                                        bg = semanticColors.mintBento,
                                        lip = semanticColors.mintLip,
                                        border = semanticColors.mintBorder,
                                        fg = semanticColors.mintContent
                                    ),
                                    QuadPrompt(
                                        title = "Quy trình & Kỹ thuật",
                                        prompt = "Giải thích quy trình và kỹ thuật cốt lõi",
                                        icon = Icons.Outlined.AccountTree,
                                        bg = semanticColors.butterBento,
                                        lip = semanticColors.butterLip,
                                        border = semanticColors.butterBorder,
                                        fg = semanticColors.butterContent
                                    ),
                                    QuadPrompt(
                                        title = "Ví dụ thực tế",
                                        prompt = "Liệt kê ví dụ thực tế trong tài liệu",
                                        icon = Icons.AutoMirrored.Outlined.MenuBook,
                                        bg = semanticColors.skyBento,
                                        lip = semanticColors.skyLip,
                                        border = semanticColors.skyBorder,
                                        fg = semanticColors.skyContent
                                    )
                                )

                                promptCards.chunked(2).forEachIndexed { rowIdx, rowItems ->
                                    if (rowIdx > 0) Spacer(modifier = Modifier.height(10.dp))
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(IntrinsicSize.Max),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        rowItems.forEach { card ->
                                            TactileSurface(
                                                modifier = Modifier.weight(1f).fillMaxHeight(),
                                                containerColor = card.bg,
                                                lipColor = card.lip,
                                                borderColor = card.border,
                                                shape = RoundedCornerShape(18.dp),
                                                lipHeight = 3.dp,
                                                fillHeight = true,
                                                enabled = !isAsking && selectedCourse != null,
                                                onClick = { viewModel.askQuestion(card.prompt) }
                                            ) {
                                                Column(
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .padding(12.dp),
                                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Icon(
                                                            imageVector = card.icon,
                                                            contentDescription = null,
                                                            tint = card.fg,
                                                            modifier = Modifier.size(18.dp)
                                                        )
                                                        Icon(
                                                            imageVector = Icons.Outlined.NorthEast,
                                                            contentDescription = null,
                                                            tint = card.fg.copy(alpha = 0.7f),
                                                            modifier = Modifier.size(14.dp)
                                                        )
                                                    }
                                                    Text(
                                                        text = card.title,
                                                        style = MaterialTheme.typography.labelLarge,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.onSurface
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            if (hasExistingSessions) {
                                Spacer(modifier = Modifier.height(16.dp))
                                HorizontalDivider(color = semanticColors.cardBorder)
                                Spacer(modifier = Modifier.height(10.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Hoặc tiếp tục chat cũ (${selectedCourse?.name})",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
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
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
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
                                    ThinkingTypingBubble()
                                }
                            } else {
                                val lastMsg = messages.lastOrNull()
                                if (lastMsg != null && !lastMsg.isUser && !lastMsg.isError && lastMsg.foundInContext) {
                                    item(key = "follow_up_${lastMsg.id}") {
                                        val followUps = listOf(
                                            "Giải thích chi tiết hơn",
                                            "Cho ví dụ cụ thể",
                                            "Tạo câu hỏi ôn tập"
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
                                                    Surface(
                                                        onClick = { viewModel.askQuestion(followUp) },
                                                        enabled = selectedCourse != null,
                                                        shape = RoundedCornerShape(50),
                                                        color = semanticColors.butterBento,
                                                        border = BorderStroke(1.dp, semanticColors.butterBorder)
                                                    ) {
                                                        Text(
                                                            text = followUp,
                                                            style = MaterialTheme.typography.labelMedium,
                                                            fontWeight = FontWeight.Bold,
                                                            color = semanticColors.butterContent,
                                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
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
                }

                // Rounded Pill Composer Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        placeholder = {
                            Text(
                                text = "Hỏi về tài liệu ${selectedCourse?.name ?: ""}...",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        modifier = Modifier.weight(1f),
                        maxLines = 3,
                        shape = RoundedCornerShape(28.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = semanticColors.cardBorder
                        )
                    )
                    val canSend = inputText.isNotBlank() && !isAsking && !messagesLoading && selectedCourse != null
                    Surface(
                        onClick = {
                            if (canSend) {
                                viewModel.askQuestion(inputText)
                                inputText = ""
                            }
                        },
                        enabled = canSend,
                        shape = CircleShape,
                        color = if (canSend) MaterialTheme.colorScheme.primary else semanticColors.cardBorder,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.Send,
                                contentDescription = "Gửi câu hỏi",
                                tint = if (canSend) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
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

private data class QuadPrompt(
    val title: String,
    val prompt: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val bg: Color,
    val lip: Color,
    val border: Color,
    val fg: Color
)

@Composable
private fun ThinkingTypingBubble() {
    val semanticColors = studyFlowSemanticColors()
    val infiniteTransition = rememberInfiniteTransition(label = "thinkingDots")
    val dot1 by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(480), RepeatMode.Reverse),
        label = "d1"
    )
    val dot2 by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(480, delayMillis = 160), RepeatMode.Reverse),
        label = "d2"
    )
    val dot3 by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(480, delayMillis = 320), RepeatMode.Reverse),
        label = "d3"
    )

    TactileSurface(
        modifier = Modifier.wrapContentWidth(),
        shape = RoundedCornerShape(18.dp),
        lipHeight = 3.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(dot1, dot2, dot3).forEach { alpha ->
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .alpha(alpha)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                    )
                }
            }
            Text(
                text = "Đang truy xuất đa tài liệu...",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun RecentSessionShortcutCard(
    session: AssistantSessionResponse,
    onClick: () -> Unit
) {
    val semanticColors = studyFlowSemanticColors()
    TactileSurface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        lipHeight = 3.dp,
        borderColor = semanticColors.cardBorder,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = session.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
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
                text = "$docScope · ${session.messageCount} tin nhắn",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
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
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Outlined.AddComment, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Bắt đầu đoạn chat mới cho môn $courseName",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
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
                    shape = RoundedCornerShape(14.dp),
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
                            shape = RoundedCornerShape(14.dp),
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
    val semanticColors = studyFlowSemanticColors()
    val align = if (message.isUser) Alignment.End else Alignment.Start
    val containerColor = when {
        message.isUser -> MaterialTheme.colorScheme.primary
        message.isError -> semanticColors.coralBento
        else -> MaterialTheme.colorScheme.surface
    }
    val contentColor = when {
        message.isUser -> Color.White
        message.isError -> semanticColors.coralContent
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

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = align
    ) {
        if (message.isUser) {
            Surface(
                modifier = Modifier.widthIn(max = 300.dp),
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 6.dp),
                color = containerColor,
                contentColor = contentColor
            ) {
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 15.dp, vertical = 12.dp)
                )
            }
        } else {
            TactileSurface(
                modifier = Modifier.fillMaxWidth(0.96f),
                containerColor = containerColor,
                lipColor = if (message.isError) semanticColors.coralLip else semanticColors.tactileDefaultLip,
                borderColor = if (message.isError) semanticColors.coralBorder else semanticColors.cardBorder,
                shape = RoundedCornerShape(20.dp),
                lipHeight = 3.dp
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    if (!message.isError) {
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
                                    fontWeight = FontWeight.ExtraBold,
                                    color = primaryColor
                                )
                            }
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(50),
                                modifier = Modifier.clickable {
                                    clipboardManager.setText(AnnotatedString(message.text))
                                    copied = true
                                }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (copied) Icons.Outlined.Check else Icons.Outlined.ContentCopy,
                                        contentDescription = "Sao chép câu trả lời",
                                        tint = if (copied) primaryColor else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(13.dp)
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

                    if (message.isError) {
                        Text(
                            text = message.text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = contentColor
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
                            style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp),
                            color = contentColor
                        )
                    }

                    if (message.isError && message.retryQuestion != null && onRetry != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = onRetry,
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Thử lại")
                        }
                    } else if (!message.isError) {
                        if (!message.foundInContext) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Không tìm thấy trích dẫn phù hợp trong tài liệu đã nạp.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else if (message.citations.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            HorizontalDivider(color = semanticColors.cardBorder)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Nguồn tham khảo (${message.citations.size})",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = primaryColor
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            message.citations.forEach { cite ->
                                var expanded by remember(cite.chunkId) { mutableStateOf(false) }
                                Surface(
                                    color = semanticColors.lavenderBento.copy(alpha = 0.65f),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 3.dp)
                                        .animateContentSize()
                                        .clickable { expanded = !expanded }
                                ) {
                                    Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                                        Box(
                                            modifier = Modifier
                                                .width(4.dp)
                                                .fillMaxHeight()
                                                .background(primaryColor)
                                        )
                                        Column(
                                            modifier = Modifier
                                                .weight(1f)
                                                .padding(horizontal = 10.dp, vertical = 9.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                val pageText = cite.pageNumber?.let { " · Tr. $it" } ?: ""
                                                Text(
                                                    text = "${cite.documentTitle}$pageText",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                if (onOpenCitation != null) {
                                                    Surface(
                                                        color = semanticColors.selectedPill,
                                                        shape = RoundedCornerShape(50),
                                                        modifier = Modifier
                                                            .padding(start = 6.dp, end = 4.dp)
                                                            .clickable { onOpenCitation(cite) }
                                                    ) {
                                                        Row(
                                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            Text(
                                                                text = cite.pageNumber?.let { "Mở tr.$it" } ?: "Mở",
                                                                style = MaterialTheme.typography.labelSmall,
                                                                fontWeight = FontWeight.Bold,
                                                                color = semanticColors.onSelectedPill,
                                                                maxLines = 1
                                                            )
                                                            Spacer(modifier = Modifier.width(3.dp))
                                                            Icon(
                                                                imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                                                                contentDescription = "Mở tài liệu",
                                                                tint = semanticColors.onSelectedPill,
                                                                modifier = Modifier.size(11.dp)
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
                                            Spacer(modifier = Modifier.height(4.dp))
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
