package com.studyflow.ai.ui.screens.quiz

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.util.Locale
import androidx.lifecycle.viewmodel.compose.viewModel
import com.studyflow.ai.data.model.QuizSubmitResponse
import com.studyflow.ai.ui.components.EmptyStateView
import com.studyflow.ai.ui.components.ErrorStateView
import com.studyflow.ai.ui.components.LoadingStateView

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuizScreen(
    quizId: String,
    onBack: () -> Unit,
    onQuizSubmitted: (QuizSubmitResponse) -> Unit,
    viewModel: QuizViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val currentIndex by viewModel.currentIndex.collectAsState()
    val selectedAnswers by viewModel.selectedAnswers.collectAsState()
    val isSubmitting by viewModel.isSubmitting.collectAsState()
    val submitError by viewModel.submitError.collectAsState()

    var showConfirmSubmitDialog by remember { mutableStateOf(false) }
    var showExitConfirmDialog by remember { mutableStateOf(false) }

    val hasProgress = (uiState as? QuizUiState.Success)?.quiz?.questions?.isNotEmpty() == true &&
        selectedAnswers.isNotEmpty()

    BackHandler(enabled = hasProgress || isSubmitting) {
        if (!isSubmitting) {
            showExitConfirmDialog = true
        }
    }

    LaunchedEffect(quizId) {
        viewModel.loadQuiz(quizId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Kiểm tra kiến thức", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (isSubmitting) return@IconButton
                            if (hasProgress) showExitConfirmDialog = true else onBack()
                        }
                    ) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Thoát")
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (val state = uiState) {
                is QuizUiState.Loading -> LoadingStateView("Đang tải câu hỏi trắc nghiệm...")
                is QuizUiState.Error -> ErrorStateView(state.message) { viewModel.loadQuiz(quizId) }
                is QuizUiState.Success -> {
                    val questions = state.quiz.questions
                    if (questions.isEmpty()) {
                        EmptyStateView(
                            title = "Không có câu hỏi",
                            description = "Bài quiz này chưa có câu hỏi nào."
                        )
                    } else {
                        val currentQuestion = questions[currentIndex]
                        val selectedOption = selectedAnswers[currentQuestion.id]

                        Column(modifier = Modifier.fillMaxSize()) {
                            // Tiến độ & Chủ đề
                            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = String.format(Locale.US, "%02d / %02d", currentIndex + 1, questions.size),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    AssistChip(
                                        onClick = {},
                                        label = { Text(currentQuestion.topic) }
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                LinearProgressIndicator(
                                    progress = { (currentIndex + 1).toFloat() / questions.size },
                                    modifier = Modifier.fillMaxWidth().height(6.dp),
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                // Thanh nhảy nhanh giữa các câu hỏi (hiện rõ câu đang xem / đã trả lời / bỏ trống)
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    itemsIndexed(questions, key = { _, q -> q.id }) { idx, q ->
                                        val isCurrent = idx == currentIndex
                                        val isAnswered = selectedAnswers.containsKey(q.id)
                                        val containerColor = when {
                                            isCurrent -> MaterialTheme.colorScheme.primary
                                            isAnswered -> MaterialTheme.colorScheme.primaryContainer
                                            else -> MaterialTheme.colorScheme.surfaceVariant
                                        }
                                        val contentColor = when {
                                            isCurrent -> MaterialTheme.colorScheme.onPrimary
                                            isAnswered -> MaterialTheme.colorScheme.onPrimaryContainer
                                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                        Surface(
                                            onClick = { viewModel.jumpToQuestion(idx) },
                                            enabled = !isSubmitting,
                                            shape = RoundedCornerShape(8.dp),
                                            color = containerColor,
                                            contentColor = contentColor
                                        ) {
                                            Text(
                                                text = "${idx + 1}",
                                                style = MaterialTheme.typography.labelLarge,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            // Nội dung câu hỏi & các lựa chọn
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .verticalScroll(rememberScrollState())
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    text = currentQuestion.prompt,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )

                                Spacer(modifier = Modifier.height(4.dp))

                                currentQuestion.options.forEach { option ->
                                    val isSelected = selectedOption == option.id
                                    OutlinedCard(
                                        onClick = { viewModel.selectOption(currentQuestion.id, option.id) },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(12.dp),
                                        border = BorderStroke(
                                            width = if (isSelected) 2.dp else 1.dp,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.outlineVariant
                                        ),
                                        colors = CardDefaults.outlinedCardColors(
                                            containerColor = if (isSelected)
                                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                                            else MaterialTheme.colorScheme.surface,
                                            contentColor = MaterialTheme.colorScheme.onSurface
                                        )
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(14.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            RadioButton(
                                                selected = isSelected,
                                                onClick = { viewModel.selectOption(currentQuestion.id, option.id) }
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "${option.id}. ${option.text}",
                                                style = MaterialTheme.typography.bodyLarge
                                            )
                                        }
                                    }
                                }

                                if (submitError != null) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.errorContainer,
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Text(
                                                text = submitError!!,
                                                color = MaterialTheme.colorScheme.onErrorContainer,
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = "Các đáp án đã chọn vẫn được giữ nguyên. Nhấn 'Thử nộp lại' để gửi lại.",
                                                color = MaterialTheme.colorScheme.onErrorContainer,
                                                style = MaterialTheme.typography.labelSmall
                                            )
                                            Spacer(modifier = Modifier.height(8.dp))
                                            OutlinedButton(
                                                onClick = { viewModel.submitQuiz(onQuizSubmitted) },
                                                enabled = !isSubmitting,
                                                shape = RoundedCornerShape(8.dp)
                                            ) {
                                                Text("Thử nộp lại")
                                            }
                                        }
                                    }
                                }
                            }

                            // Thanh nút điều hướng Câu trước / Câu tiếp / Nộp bài
                            Surface(tonalElevation = 3.dp) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = { viewModel.previousQuestion() },
                                        enabled = currentIndex > 0 && !isSubmitting,
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Text("Câu trước")
                                    }

                                    if (currentIndex < questions.size - 1) {
                                        Button(
                                            onClick = { viewModel.nextQuestion() },
                                            enabled = !isSubmitting,
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(10.dp)
                                        ) {
                                            Text("Câu tiếp")
                                        }
                                    } else {
                                        Button(
                                            onClick = {
                                                val unanswered = viewModel.countUnanswered()
                                                if (unanswered > 0) {
                                                    showConfirmSubmitDialog = true
                                                } else {
                                                    viewModel.submitQuiz(onQuizSubmitted)
                                                }
                                            },
                                            enabled = !isSubmitting,
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(10.dp)
                                        ) {
                                            if (isSubmitting) {
                                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                            } else {
                                                Text("Nộp bài")
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

    if (showConfirmSubmitDialog) {
        val unanswered = viewModel.countUnanswered()
        AlertDialog(
            onDismissRequest = { showConfirmSubmitDialog = false },
            title = { Text("Xác nhận nộp bài") },
            text = {
                Text("Bạn còn $unanswered câu chưa chọn đáp án. Câu bỏ trống sẽ được tính là sai. Bạn vẫn muốn nộp bài?")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmSubmitDialog = false
                        viewModel.submitQuiz(onQuizSubmitted)
                    }
                ) {
                    Text("Nộp ngay")
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmSubmitDialog = false }) {
                    Text("Làm tiếp")
                }
            }
        )
    }

    if (showExitConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showExitConfirmDialog = false },
            title = { Text("Thoát bài kiểm tra?") },
            text = {
                Text("Bạn đã chọn ${selectedAnswers.size} câu trả lời. Nếu thoát bây giờ, tiến trình làm bài hiện tại sẽ không được lưu.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showExitConfirmDialog = false
                        onBack()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Thoát")
                }
            },
            dismissButton = {
                TextButton(onClick = { showExitConfirmDialog = false }) {
                    Text("Tiếp tục làm")
                }
            }
        )
    }
}

