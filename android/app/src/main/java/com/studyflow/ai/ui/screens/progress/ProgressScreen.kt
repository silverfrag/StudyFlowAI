package com.studyflow.ai.ui.screens.progress

import android.content.res.Configuration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
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
import com.studyflow.ai.ui.components.LoadingStateView
import com.studyflow.ai.ui.theme.StudyFlowTheme
import com.studyflow.ai.ui.theme.studyFlowSemanticColors

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
    val isStartingPractice by viewModel.isStartingPractice.collectAsState()
    val startingTopic by viewModel.startingTopic.collectAsState()
    val practiceError by viewModel.practiceError.collectAsState()
    var skipNextResume by remember { mutableStateOf(true) }

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
        topBar = {
            TopAppBar(
                title = { Text("Tiến độ & Ôn tập", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { viewModel.loadProgress() }) {
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
                is ProgressUiState.Loading -> LoadingStateView("Đang tổng hợp tiến độ học tập...")
                is ProgressUiState.Error -> ErrorStateView(state.message) { viewModel.loadProgress() }
                is ProgressUiState.Success -> {
                    val summary = state.summary
                    val recommendations = state.recommendations

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
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Tổng quan điểm số
                            item {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(20.dp),
                                        horizontalArrangement = Arrangement.SpaceEvenly
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(
                                                text = "${summary.averageScore}",
                                                style = MaterialTheme.typography.headlineMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Text(
                                                text = "Điểm TB (/10)",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        VerticalDivider(modifier = Modifier.height(48.dp))
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(
                                                text = "${summary.totalQuizzesCompleted}",
                                                style = MaterialTheme.typography.headlineMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = "Bài quiz đã làm",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }

                            // Gợi ý ôn tập theo topic yếu
                            item {
                                Text(
                                    text = "Topic cần ôn tập",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            practiceError?.let { errMsg ->
                                item {
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.errorContainer
                                        )
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = errMsg,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onErrorContainer,
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
                                item {
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(14.dp),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                                    ) {
                                        Column(modifier = Modifier.padding(16.dp)) {
                                            Text(
                                                text = "Chưa phát hiện topic yếu",
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = "Tất cả các chủ đề bạn đã làm (>= 2 câu) đều đạt từ 60% trở lên, hoặc bạn cần làm thêm câu hỏi ở cùng chủ đề để đủ dữ liệu đánh giá.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            } else {
                                items(recommendations) { item ->
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
    val percent = (item.accuracy * 100).toInt()
    val semanticColors = studyFlowSemanticColors()
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.topic,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = item.courseName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.clickable(onClick = onOpenCourse)
                    )
                }
                Surface(
                    color = semanticColors.warningContainer,
                    contentColor = semanticColors.onWarningContainer,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "$percent% (${item.correctCount}/${item.totalAnswered})",
                        color = semanticColors.warning,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { item.accuracy.toFloat() },
                modifier = Modifier.fillMaxWidth().height(6.dp),
                color = semanticColors.error
            )
            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = item.recommendedAction,
                style = MaterialTheme.typography.bodySmall
            )

            item.suggestedDocument?.let { doc ->
                Spacer(modifier = Modifier.height(6.dp))
                val pageSuffix = if (item.suggestedPages.isNotEmpty()) {
                    " • Trang ${item.suggestedPages.take(3).joinToString(", ")}"
                } else {
                    ""
                }
                Text(
                    text = "Tài liệu gợi ý: ${doc.title}$pageSuffix",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clickable(onClick = onOpenDocumentOrCourse)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val primaryTargetPage = item.suggestedPages.firstOrNull()
                val viewButtonLabel = if (item.suggestedDocument != null && primaryTargetPage != null) {
                    "Xem trang $primaryTargetPage"
                } else {
                    "Xem tài liệu"
                }
                OutlinedButton(
                    onClick = onOpenDocumentOrCourse,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(viewButtonLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Button(
                    onClick = onPracticeClick,
                    enabled = !isBusy,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    if (isThisTopicLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Đang tạo...")
                    } else {
                        Text("Luyện quiz", maxLines = 1)
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

