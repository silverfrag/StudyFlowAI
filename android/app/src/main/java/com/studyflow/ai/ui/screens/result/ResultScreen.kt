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
import androidx.compose.ui.unit.dp
import com.studyflow.ai.data.model.QuestionResultDetail
import com.studyflow.ai.data.model.QuizSubmitResponse
import com.studyflow.ai.ui.components.EmptyStateView
import com.studyflow.ai.ui.theme.ErrorRed
import com.studyflow.ai.ui.theme.ErrorRedBg
import com.studyflow.ai.ui.theme.SuccessGreen
import com.studyflow.ai.ui.theme.SuccessGreenBg

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultScreen(
    result: QuizSubmitResponse?,
    onNavigateToHome: () -> Unit,
    onNavigateToProgress: () -> Unit
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
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = stat.topic,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "$percent% (${stat.correctCount}/${stat.totalQuestions})",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (stat.accuracy < 0.6) ErrorRed else SuccessGreen
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                LinearProgressIndicator(
                                    progress = { stat.accuracy.toFloat() },
                                    modifier = Modifier.fillMaxWidth().height(6.dp),
                                    color = if (stat.accuracy < 0.6) ErrorRed else SuccessGreen
                                )
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

                // Nút điều hướng cuối trang
                item {
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
                        Button(
                            onClick = onNavigateToProgress,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Xem Tiến độ")
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

