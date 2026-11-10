package com.studyflow.ai.ui.screens.result

import android.content.res.Configuration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
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
import com.studyflow.ai.ui.theme.StudyFlowTheme
import com.studyflow.ai.ui.theme.studyFlowSemanticColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultScreen(
    result: QuizSubmitResponse?,
    onNavigateToHome: () -> Unit,
    onNavigateToProgress: () -> Unit,
    onNavigateToCourseDetail: (() -> Unit)? = null,
    onOpenCitationDocument: (documentId: String, page: Int, title: String, suggestedPages: List<Int>, topic: String) -> Unit = { _, _, _, _, _ -> }
) {
    val semanticColors = studyFlowSemanticColors()
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
                                            color = semanticColors.warningContainer,
                                            contentColor = semanticColors.onWarningContainer,
                                            shape = RoundedCornerShape(6.dp),
                                            modifier = Modifier.padding(end = 8.dp)
                                        ) {
                                            Text(
                                                text = if (stat.totalQuestions >= 2) "Cần ôn tập" else "Chưa đạt",
                                                color = semanticColors.warning,
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
                                        color = if (isWeak) semanticColors.error else semanticColors.success
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                LinearProgressIndicator(
                                    progress = { stat.accuracy.toFloat() },
                                    modifier = Modifier.fillMaxWidth().height(6.dp),
                                    color = if (isWeak) semanticColors.error else semanticColors.success
                                )
                            }
                        }
                    }
                }

                // Gợi ý ôn tập bám sát câu sai và nguồn tài liệu
                val weakTopics = result.topicStats.filter { it.accuracy < 0.60 }
                val adviceText = result.studyAdvice?.takeIf { it.isNotBlank() } ?: when {
                    result.incorrectCount == 0 ->
                        "Bạn đã hoàn thành chính xác toàn bộ ${result.totalQuestions} câu hỏi. Có thể chuyển sang chủ đề tiếp theo hoặc tăng độ khó."
                    weakTopics.isNotEmpty() -> {
                        val missedRefs = result.details
                            .filter { !it.isCorrect }
                            .mapNotNull { d ->
                                d.citation?.let { cite ->
                                    cite.pageNumber?.let { p -> "${cite.documentTitle} (Trang $p)" } ?: cite.documentTitle
                                }
                            }
                            .distinct()
                            .take(2)
                        val refHint = if (missedRefs.isNotEmpty()) {
                            " Đọc lại ${missedRefs.joinToString(", ")} trước khi luyện tiếp."
                        } else {
                            " Xem phần giải thích chi tiết bên dưới để nắm chắc lý do sai."
                        }
                        "Trọng tâm cần xem lại: ${weakTopics.joinToString(", ") { it.topic }}.$refHint"
                    }
                    else -> null
                }

                if (adviceText != null) {
                    val hasMistakes = result.incorrectCount > 0
                    val firstMissedCite = if (hasMistakes) {
                        result.details
                            .filter { !it.isCorrect }
                            .mapNotNull { it.citation }
                            .firstOrNull { !it.documentId.isNullOrBlank() }
                    } else {
                        null
                    }
                    val missedDocPages = if (firstMissedCite?.documentId != null) {
                        result.details
                            .filter { !it.isCorrect && it.citation?.documentId == firstMissedCite.documentId }
                            .mapNotNull { it.citation?.pageNumber }
                            .distinct()
                            .sorted()
                    } else {
                        emptyList()
                    }
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (hasMistakes) {
                                    semanticColors.warningContainer
                                } else {
                                    semanticColors.successContainer
                                },
                                contentColor = if (hasMistakes) {
                                    semanticColors.onWarningContainer
                                } else {
                                    semanticColors.onSuccessContainer
                                }
                            )
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(
                                    text = if (hasMistakes) "Gợi ý ôn tập" else "Nhận xét học tập",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (hasMistakes) semanticColors.warning else semanticColors.success
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = adviceText,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (hasMistakes) {
                                        semanticColors.onWarningContainer
                                    } else {
                                        semanticColors.onSuccessContainer
                                    }
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    FilledTonalButton(
                                        onClick = onNavigateToProgress,
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text("Mở Tiến độ")
                                    }
                                    val missedDocId = firstMissedCite?.documentId
                                    if (missedDocId != null) {
                                        val targetPg = missedDocPages.firstOrNull() ?: firstMissedCite.pageNumber ?: 1
                                        OutlinedButton(
                                            onClick = {
                                                onOpenCitationDocument(
                                                    missedDocId,
                                                    targetPg,
                                                    firstMissedCite.documentTitle,
                                                    missedDocPages,
                                                    weakTopics.firstOrNull()?.topic ?: ""
                                                )
                                            },
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text("Xem trang $targetPg")
                                        }
                                    } else if (hasMistakes && onNavigateToCourseDetail != null) {
                                        OutlinedButton(
                                            onClick = onNavigateToCourseDetail,
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text("Tài liệu môn")
                                        }
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
                    QuestionResultCard(
                        detail = item,
                        onOpenCitation = { cite ->
                            val docId = cite.documentId ?: return@QuestionResultCard
                            onOpenCitationDocument(
                                docId,
                                cite.pageNumber ?: 1,
                                cite.documentTitle,
                                listOfNotNull(cite.pageNumber),
                                ""
                            )
                        }
                    )
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
private fun QuestionResultCard(
    detail: QuestionResultDetail,
    onOpenCitation: (SourceCitation) -> Unit = {}
) {
    val semanticColors = studyFlowSemanticColors()
    val feedbackContainer = if (detail.isCorrect) semanticColors.successContainer else semanticColors.errorContainer
    val feedbackContent = if (detail.isCorrect) semanticColors.onSuccessContainer else semanticColors.onErrorContainer
    val statusAccent = if (detail.isCorrect) semanticColors.success else semanticColors.error

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
                    tint = statusAccent,
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
                color = feedbackContainer,
                contentColor = feedbackContent,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text(
                        text = "Bạn chọn: $chosenText  |  Đáp án đúng: ${detail.correctOptionId}",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = statusAccent
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = detail.explanation,
                        style = MaterialTheme.typography.bodySmall,
                        color = feedbackContent
                    )
                    detail.citation?.let { cite ->
                        val pageText = cite.pageNumber?.let { " (Trang $it)" } ?: ""
                        Spacer(modifier = Modifier.height(6.dp))
                        val canOpenDoc = !cite.documentId.isNullOrBlank()
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = if (canOpenDoc) {
                                Modifier.clickable { onOpenCitation(cite) }
                            } else {
                                Modifier
                            }
                        ) {
                            Text(
                                text = "Nguồn: ${cite.documentTitle}$pageText",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (canOpenDoc) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (canOpenDoc) MaterialTheme.colorScheme.primary else feedbackContent.copy(alpha = 0.8f),
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            if (canOpenDoc) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                                    contentDescription = "Mở tài liệu",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(13.dp)
                                )
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
                studyAdvice = "Bạn còn nhầm điều kiện ghép bản ghi của LEFT JOIN ở 2 câu Phép JOIN. Hãy đọc lại Giao_trinh_CSDL.pdf (Trang 18) rồi làm quiz luyện tập.",
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

