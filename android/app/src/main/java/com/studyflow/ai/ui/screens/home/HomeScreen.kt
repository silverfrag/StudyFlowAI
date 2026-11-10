package com.studyflow.ai.ui.screens.home

import android.content.res.Configuration
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Assignment
import androidx.compose.material.icons.automirrored.outlined.MenuBook
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
import com.studyflow.ai.data.model.ProgressSummaryResponse
import com.studyflow.ai.data.model.RecentActivity
import com.studyflow.ai.ui.components.EmptyStateView
import com.studyflow.ai.ui.components.ErrorStateView
import com.studyflow.ai.ui.components.LoadingStateView
import com.studyflow.ai.ui.theme.StudyFlowTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToCourses: () -> Unit,
    onNavigateToAssistant: () -> Unit,
    onNavigateToProgress: () -> Unit,
    onOpenCourseDetail: (String) -> Unit,
    viewModel: HomeViewModel = viewModel()
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState by viewModel.uiState.collectAsState()
    var skipNextResume by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        viewModel.loadSummary(silent = uiState is HomeUiState.Success)
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                if (skipNextResume) {
                    skipNextResume = false
                } else {
                    viewModel.loadSummary(silent = true)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "StudyFlow AI",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Xin chào! Hôm nay bạn muốn học gì?",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.loadSummary() }) {
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
                is HomeUiState.Loading -> LoadingStateView("Đang tải tổng quan học tập...")
                is HomeUiState.Error -> ErrorStateView(state.message) { viewModel.loadSummary() }
                is HomeUiState.Success -> {
                    if (state.summary.totalCourses == 0) {
                        EmptyStateView(
                            title = "Chưa có môn học nào",
                            description = "Hãy tạo môn học đầu tiên và tải tài liệu lên để AI đồng hành cùng bạn.",
                            actionLabel = "Tạo môn học ngay",
                            onActionClick = onNavigateToCourses
                        )
                    } else {
                        HomeContent(
                            summary = state.summary,
                            onNavigateToCourses = onNavigateToCourses,
                            onNavigateToAssistant = onNavigateToAssistant,
                            onNavigateToProgress = onNavigateToProgress,
                            onOpenCourseDetail = onOpenCourseDetail
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeContent(
    summary: ProgressSummaryResponse,
    onNavigateToCourses: () -> Unit,
    onNavigateToAssistant: () -> Unit,
    onNavigateToProgress: () -> Unit,
    onOpenCourseDetail: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Thẻ thống kê nhanh
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatCard(
                modifier = Modifier.weight(1f),
                title = "Môn học",
                value = summary.totalCourses.toString(),
                icon = Icons.AutoMirrored.Outlined.MenuBook,
                onClick = onNavigateToCourses
            )
            StatCard(
                modifier = Modifier.weight(1f),
                title = "Quiz đã làm",
                value = summary.totalQuizzesCompleted.toString(),
                icon = Icons.Outlined.Quiz,
                onClick = onNavigateToProgress
            )
        }

        // Thẻ Tiếp tục học
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Tiếp tục học",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                val recent = summary.recentActivity
                if (recent != null) {
                    Text(
                        text = recent.courseName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${recent.lastQuizTitle} • Điểm: ${recent.lastScore}/10",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                    )
                    if (summary.totalQuizzesCompleted > 0) {
                        val scoreRatio = (summary.averageScore / 10.0).toFloat().coerceIn(0f, 1f)
                        val percent = (scoreRatio * 100).toInt()
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Tiến độ điểm TB",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "$percent% (${summary.averageScore}/10)",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { scoreRatio },
                            modifier = Modifier.fillMaxWidth().height(6.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = { onOpenCourseDetail(recent.courseId) },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Vào môn học")
                    }
                } else {
                    Text(
                        text = "Bạn chưa làm bài kiểm tra nào gần đây. Hãy mở môn học để bắt đầu ôn luyện.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = onNavigateToCourses,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Khám phá môn học")
                    }
                }
            }
        }

        // Truy cập nhanh: Hỏi AI, Làm quiz, Tài liệu (Màn hình 01)
        Text(
            text = "Truy cập nhanh",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Max),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            QuickActionCard(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                title = "Hỏi AI",
                subtitle = "Hỏi đáp tài liệu",
                icon = Icons.Outlined.SmartToy,
                onClick = onNavigateToAssistant
            )
            QuickActionCard(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                title = "Làm quiz",
                subtitle = "Luyện trắc nghiệm",
                icon = Icons.AutoMirrored.Outlined.Assignment,
                onClick = {
                    val targetCourseId = summary.recentActivity?.courseId
                    if (targetCourseId != null) {
                        onOpenCourseDetail(targetCourseId)
                    } else {
                        onNavigateToCourses()
                    }
                }
            )
            QuickActionCard(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                title = "Tài liệu",
                subtitle = "PDF & TXT",
                icon = Icons.Outlined.FolderOpen,
                onClick = onNavigateToCourses
            )
        }
    }
}

@Composable
private fun StatCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier,
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun QuickActionCard(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    OutlinedCard(
        modifier = modifier,
        onClick = onClick,
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun HomeContentPreview() {
    StudyFlowTheme {
        HomeContent(
            summary = ProgressSummaryResponse(
                totalCourses = 4,
                totalQuizzesCompleted = 12,
                averageScore = 6.5,
                recentActivity = RecentActivity(
                    courseId = "c1",
                    courseName = "Cơ sở dữ liệu",
                    lastQuizTitle = "Chương 1: Khóa chính",
                    lastScore = 8.0,
                    lastActiveAt = "2026-11-10T07:00:00Z"
                )
            ),
            onNavigateToCourses = {},
            onNavigateToAssistant = {},
            onNavigateToProgress = {},
            onOpenCourseDetail = {}
        )
    }
}

