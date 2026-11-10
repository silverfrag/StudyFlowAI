package com.studyflow.ai.ui.screens.result

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.studyflow.ai.data.model.QuestionResultDetail
import com.studyflow.ai.data.model.QuizSubmitResponse
import com.studyflow.ai.data.model.SourceCitation
import com.studyflow.ai.data.model.TopicStat
import com.studyflow.ai.ui.components.EmptyStateView
import com.studyflow.ai.ui.theme.ErrorRed
import com.studyflow.ai.ui.theme.ErrorRedBg
import com.studyflow.ai.ui.theme.StudyFlowTheme
import com.studyflow.ai.ui.theme.SuccessGreen
import com.studyflow.ai.ui.theme.SuccessGreenBg
import com.studyflow.ai.ui.theme.WarningAmber
import com.studyflow.ai.ui.theme.WarningAmberBg

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultScreen(
    result: QuizSubmitResponse?,
    onNavigateToHome: () -> Unit,
    onNavigateToProgress: () -> Unit,
    onNavigateToCourseDetail: (() -> Unit)? = null
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Kết quả học tập", fontWeight = FontWeight.Bold) }
            )
        }
    ) { padding ->
        if (result == null) {
            Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                EmptyStateView(
                    title = "Không có dữ liệu kết quả",
                    description = "Vui lòng hoàn thành một bài kiểm tra để xem phân tích kết quả.",
                    actionLabel = "Về Trang chủ",
                    onActionClick = onNavigateToHome
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Thẻ Điểm tổng quan
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "${result.score} / 10",
                                style = MaterialTheme.typography.displayMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "${result.correctCount} đúng  ·  ${result.incorrectCount} sai",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }

                // Phân tích theo chủ đề
                if (result.topicStats.isNotEmpty()) {
                    item {
                        Text(
                            text = "Phân tích theo chủ đề",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    items(result.topicStats) { stat ->
                        val percent = (stat.accuracy * 100).toInt()
                        val isWeak = stat.accuracy < 0.60
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = stat.topic,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (isWeak) {
                                        Surface(
                                            color = WarningAmberBg,
                                            shape = RoundedCornerShape(6.dp),
                                            modifier = Modifier.padding(end = 8.dp)
                                        ) {
                                            Text(
                                                text = "Cần ôn tập",
                                                color = WarningAmber,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.SemiBold,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    Text(
                                        text = "$percent% (${stat.correctCount}/${stat.totalQuestions})",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isWeak) ErrorRed else SuccessGreen
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                LinearProgressIndicator(
                                    progress = { stat.accuracy.toFloat() },
                                    modifier = Modifier.fillMaxWidth().height(6.dp),
                                    color = if (isWeak) ErrorRed else SuccessGreen
                                )
                            }
                        }
                    }

                    val weakTopics = result.topicStats.filter { it.accuracy < 0.60 }
                    if (weakTopics.isNotEmpty()) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = WarningAmberBg)
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Text(
                                        text = "Gợi ý ôn tập",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = WarningAmber
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Bạn đạt dưới 60% ở: ${weakTopics.joinToString(", ") { it.topic }}. Mở Tiến độ để xem tài liệu và làm quiz củng cố.",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    FilledTonalButton(
                                        onClick = onNavigateToProgress,
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text("Xem gợi ý ôn tập")
                                    }
                                }
                            }
                        }
                    }
                }

                // Chi tiết từng câu
                item {
                    Text(
                        text = "Chi tiết đáp án & Giải thích",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                items(result.details) { item ->
                    QuestionResultCard(item)
                }

                // Nút điều hướng cuối trang (Trang chủ / Chi tiết môn / Tiến độ)
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = onNavigateToProgress,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Xem Tiến độ & Gợi ý")
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedButton(
                                onClick = onNavigateToHome,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Về Trang chủ")
                            }
                            if (onNavigateToCourseDetail != null) {
                                OutlinedButton(
                                    onClick = onNavigateToCourseDetail,
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("Chi tiết môn")
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
private fun QuestionResultCard(detail: QuestionResultDetail) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    imageVector = if (detail.isCorrect) Icons.Outlined.CheckCircle else Icons.Outlined.Cancel,
                    contentDescription = null,
                    tint = if (detail.isCorrect) SuccessGreen else ErrorRed,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = detail.prompt,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            val chosenText = detail.selectedOptionId ?: "Bỏ trống"
            Surface(
                color = if (detail.isCorrect) SuccessGreenBg else ErrorRedBg,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text(
                        text = "Bạn chọn: $chosenText  |  Đáp án đúng: ${detail.correctOptionId}",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = if (detail.isCorrect) SuccessGreen else ErrorRed
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = detail.explanation,
                        style = MaterialTheme.typography.bodySmall
                    )
                    detail.citation?.let { cite ->
                        val pageText = cite.pageNumber?.let { " (Trang $it)" } ?: ""
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Nguồn: ${cite.documentTitle}$pageText",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ResultScreenPreview() {
    StudyFlowTheme {
        ResultScreen(
            result = QuizSubmitResponse(
                attemptId = "att-1",
                quizId = "q-1",
                score = 8.0,
                totalQuestions = 10,
                correctCount = 8,
                incorrectCount = 2,
                submittedAt = "2026-11-10T07:00:00Z",
                topicStats = listOf(
                    TopicStat("Khóa chính/ngoại khóa", 5, 5, 1.0),
                    TopicStat("Phép JOIN", 4, 2, 0.5)
                ),
                details = listOf(
                    QuestionResultDetail(
                        questionId = "q1",
                        prompt = "Thuộc tính khóa chính có thể nhận NULL không?",
                        selectedOptionId = "B",
                        correctOptionId = "B",
                        isCorrect = true,
                        explanation = "Khóa chính luôn NOT NULL và duy nhất.",
                        citation = SourceCitation("Giao_trinh_CSDL.pdf", 12)
                    )
                )
            ),
            onNavigateToHome = {},
            onNavigateToProgress = {},
            onNavigateToCourseDetail = {}
        )
    }
}

