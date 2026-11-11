package com.studyflow.ai.ui.screens.quiz

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.studyflow.ai.data.model.QuizSubmitResponse
import com.studyflow.ai.ui.components.EmptyStateView
import com.studyflow.ai.ui.components.ErrorStateView
import com.studyflow.ai.ui.components.LoadingStateView
import com.studyflow.ai.ui.components.TactileButton
import com.studyflow.ai.ui.components.TactileSurface
import com.studyflow.ai.ui.theme.studyFlowSemanticColors
import java.util.Locale

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
    val semanticColors = studyFlowSemanticColors()
    val haptics = LocalHapticFeedback.current

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
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0, 0, 0, 0),
                title = {
                    val quizTitle = (uiState as? QuizUiState.Success)?.quiz?.title ?: "Kiểm tra kiến thức"
                    Text(
                        text = quizTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (isSubmitting) return@IconButton
                            if (hasProgress) showExitConfirmDialog = true else onBack()
                        }
                    ) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Thoát")
                    }
                },
                actions = {
                    val questions = (uiState as? QuizUiState.Success)?.quiz?.questions
                    if (!questions.isNullOrEmpty()) {
                        Surface(
                            color = semanticColors.selectedPill,
                            shape = RoundedCornerShape(50),
                            modifier = Modifier.padding(end = 12.dp)
                        ) {
                            Text(
                                text = String.format(Locale.US, "%02d / %02d", currentIndex + 1, questions.size),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = semanticColors.onSelectedPill,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
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
                            // Top Soft Lavender Progress Band + Quick Question Jumpers
                            Surface(
                                color = semanticColors.lavenderBento.copy(alpha = 0.5f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 10.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    // Segmented Step Progress Bars (up to 15 clean bars, fallback smooth bar)
                                    if (questions.size <= 15) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            questions.forEachIndexed { idx, q ->
                                                val barColor = when {
                                                    idx == currentIndex -> MaterialTheme.colorScheme.primary
                                                    selectedAnswers.containsKey(q.id) -> semanticColors.success
                                                    idx < currentIndex -> MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                                                    else -> semanticColors.lavenderBorder
                                                }
                                                Box(
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .height(6.dp)
                                                        .clip(RoundedCornerShape(50))
                                                        .background(barColor)
                                                )
                                            }
                                        }
                                    } else {
                                        LinearProgressIndicator(
                                            progress = { (currentIndex + 1).toFloat() / questions.size },
                                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50))
                                        )
                                    }

                                    // Scrollable Question Number Pills
                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        itemsIndexed(questions, key = { _, q -> q.id }) { idx, q ->
                                            val isCurrent = idx == currentIndex
                                            val isAnswered = selectedAnswers.containsKey(q.id)
                                            val containerColor = when {
                                                isCurrent -> semanticColors.selectedPill
                                                isAnswered -> semanticColors.mintBento
                                                else -> MaterialTheme.colorScheme.surface.copy(alpha = 0.65f)
                                            }
                                            val contentColor = when {
                                                isCurrent -> semanticColors.onSelectedPill
                                                isAnswered -> semanticColors.mintContent
                                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                                            }
                                            val borderColor = when {
                                                isCurrent -> semanticColors.selectedPill
                                                isAnswered -> semanticColors.mintBorder
                                                else -> semanticColors.lavenderBorder
                                            }
                                            Surface(
                                                onClick = { viewModel.jumpToQuestion(idx) },
                                                enabled = !isSubmitting,
                                                shape = RoundedCornerShape(10.dp),
                                                color = containerColor,
                                                border = BorderStroke(1.dp, borderColor),
                                                contentColor = contentColor
                                            ) {
                                                Text(
                                                    text = "${idx + 1}",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 5.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // Question & Tactile A/B/C/D Deck Area
                            AnimatedContent(
                                targetState = currentIndex,
                                modifier = Modifier.weight(1f),
                                transitionSpec = {
                                    if (targetState >= initialState) {
                                        (slideInHorizontally(tween(220)) { it / 8 } + fadeIn(tween(200)))
                                            .togetherWith(slideOutHorizontally(tween(180)) { -it / 8 } + fadeOut(tween(160)))
                                    } else {
                                        (slideInHorizontally(tween(220)) { -it / 8 } + fadeIn(tween(200)))
                                            .togetherWith(slideOutHorizontally(tween(180)) { it / 8 } + fadeOut(tween(160)))
                                    }
                                },
                                label = "questionTransition"
                            ) { animatedIdx ->
                                val q = questions[animatedIdx]
                                val chosenOpt = selectedAnswers[q.id]

                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .verticalScroll(rememberScrollState())
                                        .padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    // Topic Tag + Prompt Card
                                    TactileSurface(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(22.dp),
                                        lipHeight = 4.dp
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(16.dp),
                                            verticalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Surface(
                                                color = semanticColors.lavenderBento,
                                                shape = RoundedCornerShape(8.dp)
                                            ) {
                                                Text(
                                                    text = q.topic.uppercase(Locale.forLanguageTag("vi-VN")),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    color = semanticColors.lavenderContent,
                                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                                                )
                                            }
                                            Text(
                                                text = q.prompt,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(2.dp))

                                    // 3D-Lipped Tactile Option Cards (A / B / C / D)
                                    q.options.forEach { option ->
                                        val isSelected = chosenOpt == option.id
                                        val cardBg by animateColorAsState(
                                            targetValue = if (isSelected) semanticColors.lavenderBento else MaterialTheme.colorScheme.surface,
                                            animationSpec = tween(180),
                                            label = "optBg"
                                        )
                                        val lipBg by animateColorAsState(
                                            targetValue = if (isSelected) semanticColors.primaryLip else semanticColors.tactileDefaultLip,
                                            animationSpec = tween(180),
                                            label = "optLip"
                                        )
                                        val borderColor by animateColorAsState(
                                            targetValue = if (isSelected) MaterialTheme.colorScheme.primary else semanticColors.cardBorder,
                                            animationSpec = tween(180),
                                            label = "optBorder"
                                        )
                                        val badgeBg by animateColorAsState(
                                            targetValue = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                            animationSpec = tween(180),
                                            label = "optBadgeBg"
                                        )

                                        TactileSurface(
                                            modifier = Modifier.fillMaxWidth(),
                                            containerColor = cardBg,
                                            lipColor = lipBg,
                                            borderColor = borderColor,
                                            shape = RoundedCornerShape(18.dp),
                                            lipHeight = 4.dp,
                                            enabled = !isSubmitting,
                                            onClick = {
                                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                viewModel.selectOption(q.id, option.id)
                                            }
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 14.dp, vertical = 13.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                                            ) {
                                                Surface(
                                                    color = badgeBg,
                                                    shape = CircleShape,
                                                    modifier = Modifier.size(34.dp)
                                                ) {
                                                    Box(contentAlignment = Alignment.Center) {
                                                        Text(
                                                            text = option.id,
                                                            style = MaterialTheme.typography.titleSmall,
                                                            fontWeight = FontWeight.ExtraBold,
                                                            color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }
                                                }
                                                Text(
                                                    text = option.text,
                                                    style = MaterialTheme.typography.bodyLarge,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                    color = if (isSelected) semanticColors.lavenderContent else MaterialTheme.colorScheme.onSurface,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                if (isSelected) {
                                                    Icon(
                                                        imageVector = Icons.Outlined.Check,
                                                        contentDescription = "Đã chọn",
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    if (submitError != null) {
                                        Surface(
                                            color = semanticColors.coralBento,
                                            border = BorderStroke(1.dp, semanticColors.coralBorder),
                                            shape = RoundedCornerShape(16.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(modifier = Modifier.padding(14.dp)) {
                                                Text(
                                                    text = submitError!!,
                                                    color = semanticColors.coralContent,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(
                                                    text = "Các đáp án đã chọn vẫn được giữ nguyên. Nhấn 'Thử nộp lại' để gửi lại.",
                                                    color = semanticColors.coralContent.copy(alpha = 0.85f),
                                                    style = MaterialTheme.typography.labelSmall
                                                )
                                                Spacer(modifier = Modifier.height(10.dp))
                                                OutlinedButton(
                                                    onClick = { viewModel.submitQuiz(onQuizSubmitted) },
                                                    enabled = !isSubmitting,
                                                    shape = RoundedCornerShape(12.dp)
                                                ) {
                                                    Text("Thử nộp lại")
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // Sticky Tactile Bottom Bar
                            Surface(
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, semanticColors.cardBorder),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(IntrinsicSize.Max)
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    TactileButton(
                                        onClick = { viewModel.previousQuestion() },
                                        enabled = currentIndex > 0 && !isSubmitting,
                                        modifier = Modifier.weight(1f).fillMaxHeight(),
                                        fillHeight = true,
                                        containerColor = MaterialTheme.colorScheme.surface,
                                        contentColor = MaterialTheme.colorScheme.onSurface,
                                        lipColor = semanticColors.tactileDefaultLip,
                                        borderColor = semanticColors.cardBorder,
                                        shape = RoundedCornerShape(14.dp),
                                        lipHeight = 3.dp
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Câu trước", maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }

                                    if (currentIndex < questions.size - 1) {
                                        TactileButton(
                                            onClick = { viewModel.nextQuestion() },
                                            enabled = !isSubmitting,
                                            modifier = Modifier.weight(1.15f).fillMaxHeight(),
                                            fillHeight = true,
                                            shape = RoundedCornerShape(14.dp),
                                            lipHeight = 3.dp
                                        ) {
                                            Text("Câu tiếp", maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    } else {
                                        TactileButton(
                                            onClick = {
                                                val unanswered = viewModel.countUnanswered()
                                                if (unanswered > 0) {
                                                    showConfirmSubmitDialog = true
                                                } else {
                                                    viewModel.submitQuiz(onQuizSubmitted)
                                                }
                                            },
                                            enabled = !isSubmitting,
                                            modifier = Modifier.weight(1.15f).fillMaxHeight(),
                                            fillHeight = true,
                                            containerColor = semanticColors.success,
                                            contentColor = Color.White,
                                            lipColor = Color(0xFF046C4E),
                                            shape = RoundedCornerShape(14.dp),
                                            lipHeight = 3.dp
                                        ) {
                                            if (isSubmitting) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(16.dp),
                                                    strokeWidth = 2.dp,
                                                    color = Color.White,
                                                    trackColor = Color.White.copy(alpha = 0.28f)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text("Đang nộp...", fontWeight = FontWeight.ExtraBold, maxLines = 1)
                                            } else {
                                                Text("Nộp bài", fontWeight = FontWeight.ExtraBold, maxLines = 1)
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
            title = { Text("Xác nhận nộp bài", fontWeight = FontWeight.Bold) },
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
            title = { Text("Thoát bài kiểm tra?", fontWeight = FontWeight.Bold) },
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
