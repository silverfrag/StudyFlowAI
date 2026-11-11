package com.studyflow.ai.ui.screens.progress

import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.studyflow.ai.data.model.RecommendationItem
import com.studyflow.ai.data.model.SuggestedDocument
import com.studyflow.ai.ui.components.EmptyStateView
import com.studyflow.ai.ui.components.ErrorStateView
import com.studyflow.ai.ui.components.HomeSkeletonView
import com.studyflow.ai.ui.components.MiniAccuracyRing
import com.studyflow.ai.ui.components.ScoreRingGauge
import com.studyflow.ai.ui.components.TactileButton
import com.studyflow.ai.ui.components.TactileSurface
import com.studyflow.ai.ui.theme.StudyFlowTheme
import com.studyflow.ai.ui.theme.rememberCourseVisualStyle
import com.studyflow.ai.ui.theme.studyFlowSemanticColors
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgressScreen(
    onOpenCourseDetail: (String) -> Unit,
    onOpenDocument: (documentId: String, page: Int, title: String, suggestedPages: List<Int>, topic: String) -> Unit = { _, _, _, _, _ -> },
    onStartPracticeQuiz: (quizId: String, courseId: String) -> Unit,
    onNavigateToCourses: () -> Unit,
    viewModel: ProgressViewModel = viewModel()
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState by viewModel.uiState.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val isStartingPractice by viewModel.isStartingPractice.collectAsState()
    val startingTopic by viewModel.startingTopic.collectAsState()
    val practiceError by viewModel.practiceError.collectAsState()
    var skipNextResume by remember { mutableStateOf(true) }
    val semanticColors = studyFlowSemanticColors()

    LaunchedEffect(Unit) {
        viewModel.loadProgress(silent = uiState is ProgressUiState.Success)
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                if (skipNextResume) {
                    skipNextResume = false
                } else {
                    viewModel.loadProgress(silent = true)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Tiến độ & Ôn tập",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = "Phân tích kết quả thực tế từ bài quiz",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = { viewModel.refreshProgress() },
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Refresh,
                        contentDescription = "Làm mới",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (val state = uiState) {
                is ProgressUiState.Loading -> HomeSkeletonView()
                is ProgressUiState.Error -> ErrorStateView(state.message) { viewModel.loadProgress() }
                is ProgressUiState.Success -> {
                    val summary = state.summary
                    val recommendations = state.recommendations

                    PullToRefreshBox(
                        isRefreshing = isRefreshing,
                        onRefresh = { viewModel.refreshProgress() },
                        modifier = Modifier.fillMaxSize()
                    ) {
                        if (summary.totalQuizzesCompleted == 0) {
                            EmptyStateView(
                                title = "Chưa có dữ liệu làm bài",
                                description = "Hãy hoàn thành một vài bài kiểm tra trắc nghiệm để hệ thống phân tích điểm mạnh và gợi ý topic yếu cho bạn.",
                                actionLabel = "Đi tới Môn học",
                                onActionClick = onNavigateToCourses
                            )
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                // Analytics Bento Row: Dark Obsidian Score Ring Left + Mint & Coral Tiles Right
                                item(key = "analytics_bento") {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(IntrinsicSize.Max),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        // Midnight Ink Ring Card
                                        TactileSurface(
                                            modifier = Modifier
                                                .weight(0.95f)
                                                .fillMaxHeight(),
                                            containerColor = semanticColors.midnightInk,
                                            lipColor = semanticColors.midnightInkLip,
                                            borderColor = semanticColors.floatingDockBorder,
                                            shape = RoundedCornerShape(24.dp),
                                            lipHeight = 5.dp,
                                            fillHeight = true
                                        ) {
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .padding(14.dp),
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                ScoreRingGauge(
                                                    scoreOutOfTen = summary.averageScore,
                                                    size = 90.dp,
                                                    strokeWidth = 8.dp,
                                                    ringColor = Color(0xFF34D399),
                                                    trackColor = Color.White.copy(alpha = 0.16f),
                                                    primaryTextColor = semanticColors.midnightOnInk,
                                                    secondaryTextColor = semanticColors.midnightSubtle,
                                                    subLabelText = "ĐIỂM TB"
                                                )
                                                Spacer(modifier = Modifier.height(8.dp))
                                                val progressLabel = when {
                                                    summary.averageScore >= 8.0 -> "Phong độ xuất sắc"
                                                    summary.averageScore >= 6.5 -> "Đang tiến bộ tốt"
                                                    else -> "Cần củng cố thêm"
                                                }
                                                Text(
                                                    text = progressLabel,
                                                    style = MaterialTheme.typography.labelMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = semanticColors.midnightSubtle
                                                )
                                            }
                                        }

                                        // Right Stacked Bento Tiles
                                        Column(
                                            modifier = Modifier
                                                .weight(1.05f)
                                                .fillMaxHeight(),
                                            verticalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            TactileSurface(
                                                modifier = Modifier.fillMaxWidth().weight(1f),
                                                containerColor = semanticColors.mintBento,
                                                lipColor = semanticColors.mintLip,
                                                borderColor = semanticColors.mintBorder,
                                                shape = RoundedCornerShape(20.dp),
                                                lipHeight = 4.dp,
                                                fillHeight = true
                                            ) {
                                                Column(
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                                    verticalArrangement = Arrangement.Center
                                                ) {
                                                    Text(
                                                        text = String.format(Locale.US, "%02d bài quiz", summary.totalQuizzesCompleted),
                                                        style = MaterialTheme.typography.titleLarge,
                                                        fontWeight = FontWeight.ExtraBold,
                                                        color = MaterialTheme.colorScheme.onSurface
                                                    )
                                                    Spacer(modifier = Modifier.height(2.dp))
                                                    Text(
                                                        text = "Đã hoàn thành",
                                                        style = MaterialTheme.typography.labelMedium,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = semanticColors.mintContent
                                                    )
                                                }
                                            }

                                            val hasWeakTopics = recommendations.isNotEmpty()
                                            TactileSurface(
                                                modifier = Modifier.fillMaxWidth().weight(1f),
                                                containerColor = if (hasWeakTopics) semanticColors.coralBento else semanticColors.lavenderBento,
                                                lipColor = if (hasWeakTopics) semanticColors.coralLip else semanticColors.lavenderLip,
                                                borderColor = if (hasWeakTopics) semanticColors.coralBorder else semanticColors.lavenderBorder,
                                                shape = RoundedCornerShape(20.dp),
                                                lipHeight = 4.dp,
                                                fillHeight = true
                                            ) {
                                                Column(
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                                    verticalArrangement = Arrangement.Center
                                                ) {
                                                    Text(
                                                        text = String.format(Locale.US, "%02d topic yếu", recommendations.size),
                                                        style = MaterialTheme.typography.titleLarge,
                                                        fontWeight = FontWeight.ExtraBold,
                                                        color = MaterialTheme.colorScheme.onSurface
                                                    )
                                                    Spacer(modifier = Modifier.height(2.dp))
                                                    Text(
                                                        text = if (hasWeakTopics) "Cần ôn tập ngay" else "Đang nắm vững",
                                                        style = MaterialTheme.typography.labelMedium,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = if (hasWeakTopics) semanticColors.coralContent else semanticColors.lavenderContent
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                // Flashcard Header
                                item(key = "rec_header") {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Flashcard gợi ý ôn tập",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.ExtraBold
                                        )
                                        Text(
                                            text = "Ngưỡng < 60%",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                practiceError?.let { errMsg ->
                                    item(key = "practice_error") {
                                        Surface(
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(14.dp),
                                            color = semanticColors.coralBento,
                                            border = BorderStroke(1.dp, semanticColors.coralBorder)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(12.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(
                                                    text = errMsg,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = semanticColors.coralContent,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                TextButton(onClick = { viewModel.clearPracticeError() }) {
                                                    Text("Đóng")
                                                }
                                            }
                                        }
                                    }
                                }

                                if (recommendations.isEmpty()) {
                                    item(key = "no_weak_topics") {
                                        TactileSurface(
                                            modifier = Modifier.fillMaxWidth(),
                                            containerColor = semanticColors.mintBento,
                                            lipColor = semanticColors.mintLip,
                                            borderColor = semanticColors.mintBorder,
                                            shape = RoundedCornerShape(20.dp),
                                            lipHeight = 4.dp
                                        ) {
                                            Column(modifier = Modifier.padding(18.dp)) {
                                                Text(
                                                    text = "Chưa phát hiện topic yếu",
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    color = semanticColors.mintContent
                                                )
                                                Spacer(modifier = Modifier.height(6.dp))
                                                Text(
                                                    text = "Tất cả các chủ đề bạn đã làm (>= 2 câu) đều đạt từ 60% trở lên, hoặc bạn cần làm thêm câu hỏi ở cùng chủ đề để đủ dữ liệu đánh giá.",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                        }
                                    }
                                } else {
                                    items(recommendations, key = { "${it.courseId}_${it.topic}" }) { item ->
                                        RecommendationCard(
                                            item = item,
                                            isBusy = isStartingPractice,
                                            isThisTopicLoading = isStartingPractice && startingTopic == item.topic,
                                            onOpenDocumentOrCourse = {
                                                val doc = item.suggestedDocument
                                                if (doc != null) {
                                                    val targetPage = item.suggestedPages.firstOrNull() ?: 1
                                                    onOpenDocument(
                                                        doc.id,
                                                        targetPage,
                                                        doc.title,
                                                        item.suggestedPages,
                                                        item.topic
                                                    )
                                                } else {
                                                    onOpenCourseDetail(item.courseId)
                                                }
                                            },
                                            onOpenCourse = { onOpenCourseDetail(item.courseId) },
                                            onPracticeClick = {
                                                viewModel.startPracticeQuiz(item.courseId, item.topic) { quiz ->
                                                    onStartPracticeQuiz(quiz.id, item.courseId)
                                                }
                                            }
                                        )
                                    }
                                }

                                item(key = "bottom_space") {
                                    Spacer(modifier = Modifier.height(14.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecommendationCard(
    item: RecommendationItem,
    isBusy: Boolean,
    isThisTopicLoading: Boolean = false,
    onOpenDocumentOrCourse: () -> Unit,
    onOpenCourse: () -> Unit,
    onPracticeClick: () -> Unit
) {
    val semanticColors = studyFlowSemanticColors()
    val courseStyle = rememberCourseVisualStyle(seed = item.courseId)

    TactileSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        lipHeight = 4.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Top Colored Flashcard Accent Ribbon
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .background(
                        Brush.horizontalGradient(listOf(courseStyle.accent, courseStyle.gradientEnd))
                    )
            )

            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Surface(
                            color = courseStyle.bentoSurface,
                            border = BorderStroke(1.dp, courseStyle.bentoBorder),
                            shape = RoundedCornerShape(50),
                            modifier = Modifier.clickable(onClick = onOpenCourse)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(courseStyle.accent)
                                )
                                Text(
                                    text = item.courseName,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = courseStyle.onContainer,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Text(
                            text = item.topic,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))
                    MiniAccuracyRing(
                        accuracy = item.accuracy,
                        size = 52.dp,
                        strokeWidth = 5.dp
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Độ chính xác ${item.correctCount}/${item.totalAnswered} câu · ${item.recommendedAction}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                item.suggestedDocument?.let { doc ->
                    Spacer(modifier = Modifier.height(8.dp))
                    val pageSuffix = if (item.suggestedPages.isNotEmpty()) {
                        " · Trang ${item.suggestedPages.take(3).joinToString(", ")}"
                    } else {
                        ""
                    }
                    Surface(
                        color = semanticColors.lavenderBento.copy(alpha = 0.7f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.clickable(onClick = onOpenDocumentOrCourse)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.MenuBook,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "${doc.title}$pageSuffix",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = semanticColors.lavenderContent,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Max),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val primaryTargetPage = item.suggestedPages.firstOrNull()
                    val viewButtonLabel = if (item.suggestedDocument != null && primaryTargetPage != null) {
                        "Xem trang $primaryTargetPage"
                    } else {
                        "Xem tài liệu"
                    }
                    TactileButton(
                        onClick = onOpenDocumentOrCourse,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        fillHeight = true,
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        lipColor = semanticColors.tactileDefaultLip,
                        borderColor = semanticColors.cardBorder,
                        shape = RoundedCornerShape(14.dp),
                        lipHeight = 3.dp,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp)
                    ) {
                        Text(viewButtonLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    TactileButton(
                        onClick = onPracticeClick,
                        enabled = !isBusy,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        fillHeight = true,
                        shape = RoundedCornerShape(14.dp),
                        lipHeight = 3.dp,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp)
                    ) {
                        if (isThisTopicLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Đang tạo...", maxLines = 1)
                        } else {
                            Text("Luyện quiz", maxLines = 1, overflow = TextOverflow.Ellipsis)
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
private fun RecommendationCardPreview() {
    StudyFlowTheme {
        Box(modifier = Modifier.padding(16.dp)) {
            RecommendationCard(
                item = RecommendationItem(
                    topic = "Phép JOIN trong quan hệ",
                    courseId = "c1",
                    courseName = "Cơ sở dữ liệu",
                    totalAnswered = 6,
                    correctCount = 2,
                    accuracy = 0.33,
                    recommendedAction = "Ôn lại lý thuyết phép nối và làm quiz luyện tập củng cố.",
                    suggestedDocument = SuggestedDocument("d1", "Giao_trinh_CSDL_Chuong2.pdf"),
                    suggestedPages = listOf(8, 12)
                ),
                isBusy = false,
                onOpenDocumentOrCourse = {},
                onOpenCourse = {},
                onPracticeClick = {}
            )
        }
    }
}
