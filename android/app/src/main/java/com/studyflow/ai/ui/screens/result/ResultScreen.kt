package com.studyflow.ai.ui.screens.result

import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material3.*
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
import com.studyflow.ai.data.model.QuestionResultDetail
import com.studyflow.ai.data.model.QuizSubmitResponse
import com.studyflow.ai.data.model.SourceCitation
import com.studyflow.ai.data.model.TopicStat
import com.studyflow.ai.ui.components.CelebrationConfettiCanvas
import com.studyflow.ai.ui.components.EmptyStateView
import com.studyflow.ai.ui.components.ScoreRingGauge
import com.studyflow.ai.ui.components.TactileButton
import com.studyflow.ai.ui.components.TactileSurface
import com.studyflow.ai.ui.theme.StudyFlowTheme
import com.studyflow.ai.ui.theme.studyFlowSemanticColors
import java.util.Locale

private enum class ResultDetailFilter {
    ALL,
    WRONG,
    CORRECT
}

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
    var detailFilter by remember { mutableStateOf(ResultDetailFilter.ALL) }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0, 0, 0, 0),
                title = {
                    Text(
                        text = "Kết quả học tập",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
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
            val (heroStart, heroEnd, heroLip, verdictLabel) = when {
                result.score >= 8.0 -> QuadHeroStyle(
                    Color(0xFF059669), Color(0xFF10B981), Color(0xFF046C4E), "Xuất sắc"
                )
                result.score >= 6.0 -> QuadHeroStyle(
                    Color(0xFF5B52F0), Color(0xFF7C3AED), Color(0xFF3F36C9), "Khá tốt"
                )
                else -> QuadHeroStyle(
                    Color(0xFFD97706), Color(0xFFF59E0B), Color(0xFFB45309), "Cần ôn tập"
                )
            }

            val indexedDetails = remember(result.details) {
                result.details.mapIndexed { idx, detail -> (idx + 1) to detail }
            }
            val visibleDetails = remember(indexedDetails, detailFilter) {
                when (detailFilter) {
                    ResultDetailFilter.ALL -> indexedDetails
                    ResultDetailFilter.WRONG -> indexedDetails.filter { !it.second.isCorrect }
                    ResultDetailFilter.CORRECT -> indexedDetails.filter { it.second.isCorrect }
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Full-width Gradient Celebration Banner + ScoreRingGauge + Confetti
                item(key = "celebration_hero") {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .padding(top = 5.dp)
                                .clip(RoundedCornerShape(26.dp))
                                .background(heroLip)
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 5.dp)
                                .clip(RoundedCornerShape(26.dp))
                                .background(Brush.linearGradient(listOf(heroStart, heroEnd)))
                        ) {
                            CelebrationConfettiCanvas(modifier = Modifier.matchParentSize())
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(18.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = Color.White.copy(alpha = 0.14f)
                                ) {
                                    ScoreRingGauge(
                                        scoreOutOfTen = result.score,
                                        size = 94.dp,
                                        strokeWidth = 8.dp,
                                        ringColor = Color.White,
                                        trackColor = Color.White.copy(alpha = 0.25f),
                                        primaryTextColor = Color.White,
                                        secondaryTextColor = Color.White.copy(alpha = 0.85f),
                                        subLabelText = "ĐIỂM",
                                        modifier = Modifier.padding(4.dp)
                                    )
                                }

                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Surface(
                                        color = Color.Black.copy(alpha = 0.18f),
                                        shape = RoundedCornerShape(50)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.EmojiEvents,
                                                contentDescription = null,
                                                tint = Color(0xFFFDE68A),
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Text(
                                                text = "$verdictLabel · ${result.correctCount}/${result.totalQuestions} đúng",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = Color.White
                                            )
                                        }
                                    }

                                    Text(
                                        text = if (result.incorrectCount == 0) {
                                            "Hoàn hảo! Không sai câu nào"
                                        } else {
                                            "${result.correctCount} đúng · ${result.incorrectCount} câu cần xem lại"
                                        },
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color.White
                                    )
                                    Text(
                                        text = "Chấm điểm xác định theo đáp án chuẩn từ tài liệu nguồn.",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White.copy(alpha = 0.86f)
                                    )
                                }
                            }
                        }
                    }
                }

                // Pastel Bento Topic Mastery Tiles
                if (result.topicStats.isNotEmpty()) {
                    item(key = "topic_header") {
                        Text(
                            text = "Phân tích theo chủ đề",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                    val topicRows = result.topicStats.chunked(2)
                    itemsIndexed(topicRows, key = { idx, _ -> "topic_row_$idx" }) { _, rowStats ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(IntrinsicSize.Max),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            rowStats.forEach { stat ->
                                TopicBentoCard(
                                    stat = stat,
                                    modifier = Modifier.weight(1f).fillMaxHeight()
                                )
                            }
                            if (rowStats.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }

                // Study Advice Bento Card
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
                    item(key = "study_advice") {
                        TactileSurface(
                            modifier = Modifier.fillMaxWidth(),
                            containerColor = if (hasMistakes) semanticColors.butterBento else semanticColors.mintBento,
                            lipColor = if (hasMistakes) semanticColors.butterLip else semanticColors.mintLip,
                            borderColor = if (hasMistakes) semanticColors.butterBorder else semanticColors.mintBorder,
                            shape = RoundedCornerShape(20.dp),
                            lipHeight = 4.dp
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Outlined.AutoAwesome,
                                        contentDescription = null,
                                        tint = if (hasMistakes) semanticColors.warning else semanticColors.success,
                                        modifier = Modifier.size(17.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (hasMistakes) "Gợi ý ôn tập" else "Nhận xét học tập",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = if (hasMistakes) semanticColors.butterContent else semanticColors.mintContent
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = adviceText,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(IntrinsicSize.Max),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    TactileButton(
                                        onClick = onNavigateToProgress,
                                        modifier = Modifier.weight(1f).fillMaxHeight(),
                                        fillHeight = true,
                                        containerColor = MaterialTheme.colorScheme.surface,
                                        contentColor = MaterialTheme.colorScheme.onSurface,
                                        lipColor = if (hasMistakes) semanticColors.butterBorder else semanticColors.mintBorder,
                                        shape = RoundedCornerShape(14.dp),
                                        lipHeight = 3.dp,
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
                                    ) {
                                        Text("Mở Tiến độ", maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    val missedDocId = firstMissedCite?.documentId
                                    if (missedDocId != null) {
                                        val targetPg = missedDocPages.firstOrNull() ?: firstMissedCite.pageNumber ?: 1
                                        TactileButton(
                                            onClick = {
                                                onOpenCitationDocument(
                                                    missedDocId,
                                                    targetPg,
                                                    firstMissedCite.documentTitle,
                                                    missedDocPages,
                                                    weakTopics.firstOrNull()?.topic ?: ""
                                                )
                                            },
                                            modifier = Modifier.weight(1f).fillMaxHeight(),
                                            fillHeight = true,
                                            shape = RoundedCornerShape(14.dp),
                                            lipHeight = 3.dp,
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
                                        ) {
                                            Text("Xem trang $targetPg", maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                    } else if (hasMistakes && onNavigateToCourseDetail != null) {
                                        TactileButton(
                                            onClick = onNavigateToCourseDetail,
                                            modifier = Modifier.weight(1f).fillMaxHeight(),
                                            fillHeight = true,
                                            shape = RoundedCornerShape(14.dp),
                                            lipHeight = 3.dp,
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
                                        ) {
                                            Text("Tài liệu môn", maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Detail Filter Chips Row
                item(key = "details_header_and_filters") {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "Chi tiết đáp án & Giải thích",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ResultFilterChip(
                                label = "Tất cả (${result.details.size})",
                                selected = detailFilter == ResultDetailFilter.ALL,
                                activeBg = semanticColors.selectedPill,
                                activeFg = semanticColors.onSelectedPill,
                                inactiveBg = MaterialTheme.colorScheme.surface,
                                inactiveFg = MaterialTheme.colorScheme.onSurfaceVariant,
                                borderColor = semanticColors.cardBorder,
                                onClick = { detailFilter = ResultDetailFilter.ALL }
                            )
                            ResultFilterChip(
                                label = "Làm sai (${result.incorrectCount})",
                                selected = detailFilter == ResultDetailFilter.WRONG,
                                activeBg = semanticColors.error,
                                activeFg = Color.White,
                                inactiveBg = semanticColors.coralBento,
                                inactiveFg = semanticColors.coralContent,
                                borderColor = semanticColors.coralBorder,
                                onClick = { detailFilter = ResultDetailFilter.WRONG }
                            )
                            ResultFilterChip(
                                label = "Làm đúng (${result.correctCount})",
                                selected = detailFilter == ResultDetailFilter.CORRECT,
                                activeBg = semanticColors.success,
                                activeFg = Color.White,
                                inactiveBg = semanticColors.mintBento,
                                inactiveFg = semanticColors.mintContent,
                                borderColor = semanticColors.mintBorder,
                                onClick = { detailFilter = ResultDetailFilter.CORRECT }
                            )
                        }
                    }
                }

                if (visibleDetails.isEmpty()) {
                    item(key = "empty_filter_result") {
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, semanticColors.cardBorder),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Không có câu hỏi nào trong mục lọc này.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                } else {
                    itemsIndexed(visibleDetails, key = { _, pair -> pair.second.questionId }) { _, (questionNum, item) ->
                        QuestionResultCard(
                            questionNumber = questionNum,
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
                }

                // Bottom Navigation Actions
                item(key = "bottom_nav_actions") {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        TactileButton(
                            onClick = onNavigateToProgress,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            lipHeight = 4.dp
                        ) {
                            Text("Xem Tiến độ & Gợi ý")
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(IntrinsicSize.Max),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            TactileButton(
                                onClick = onNavigateToHome,
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                                fillHeight = true,
                                containerColor = MaterialTheme.colorScheme.surface,
                                contentColor = MaterialTheme.colorScheme.onSurface,
                                lipColor = semanticColors.tactileDefaultLip,
                                borderColor = semanticColors.cardBorder,
                                shape = RoundedCornerShape(14.dp),
                                lipHeight = 3.dp
                            ) {
                                Text("Về Trang chủ", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            if (onNavigateToCourseDetail != null) {
                                TactileButton(
                                    onClick = onNavigateToCourseDetail,
                                    modifier = Modifier.weight(1f).fillMaxHeight(),
                                    fillHeight = true,
                                    containerColor = semanticColors.lavenderBento,
                                    contentColor = semanticColors.lavenderContent,
                                    lipColor = semanticColors.lavenderLip,
                                    borderColor = semanticColors.lavenderBorder,
                                    shape = RoundedCornerShape(14.dp),
                                    lipHeight = 3.dp
                                ) {
                                    Text("Chi tiết môn", maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(18.dp))
                }
            }
        }
    }
}

private data class QuadHeroStyle(
    val start: Color,
    val end: Color,
    val lip: Color,
    val verdict: String
)

@Composable
private fun TopicBentoCard(
    stat: TopicStat,
    modifier: Modifier = Modifier
) {
    val semanticColors = studyFlowSemanticColors()
    val percent = (stat.accuracy * 100).toInt()
    val isWeak = stat.accuracy < 0.60

    TactileSurface(
        modifier = modifier,
        containerColor = if (isWeak) semanticColors.butterBento else semanticColors.mintBento,
        lipColor = if (isWeak) semanticColors.butterLip else semanticColors.mintLip,
        borderColor = if (isWeak) semanticColors.butterBorder else semanticColors.mintBorder,
        shape = RoundedCornerShape(18.dp),
        lipHeight = 3.dp,
        fillHeight = true
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$percent%",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (isWeak) semanticColors.warning else semanticColors.success
                )
                if (isWeak) {
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(50)
                    ) {
                        Text(
                            text = if (stat.totalQuestions >= 2) "Cần ôn" else "Chưa đạt",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = semanticColors.warning,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = stat.topic,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${stat.correctCount} / ${stat.totalQuestions} câu đúng",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isWeak) semanticColors.butterContent else semanticColors.mintContent
                )
            }
        }
    }
}

@Composable
private fun ResultFilterChip(
    label: String,
    selected: Boolean,
    activeBg: Color,
    activeFg: Color,
    inactiveBg: Color,
    inactiveFg: Color,
    borderColor: Color,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = if (selected) activeBg else inactiveBg,
        border = BorderStroke(1.dp, if (selected) activeBg else borderColor)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = if (selected) activeFg else inactiveFg,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
        )
    }
}

@Composable
private fun QuestionResultCard(
    questionNumber: Int,
    detail: QuestionResultDetail,
    onOpenCitation: (SourceCitation) -> Unit = {}
) {
    val semanticColors = studyFlowSemanticColors()
    val feedbackContainer = if (detail.isCorrect) semanticColors.mintBento else semanticColors.coralBento
    val feedbackBorder = if (detail.isCorrect) semanticColors.mintBorder else semanticColors.coralBorder
    val statusAccent = if (detail.isCorrect) semanticColors.success else semanticColors.error
    val statusText = if (detail.isCorrect) "Đúng" else "Làm sai"

    TactileSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        borderColor = if (detail.isCorrect) semanticColors.cardBorder else semanticColors.coralBorder,
        lipColor = if (detail.isCorrect) semanticColors.tactileDefaultLip else semanticColors.coralLip,
        lipHeight = 3.dp
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = feedbackContainer,
                    border = BorderStroke(1.dp, feedbackBorder),
                    shape = RoundedCornerShape(50)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = if (detail.isCorrect) Icons.Outlined.CheckCircle else Icons.Outlined.Cancel,
                            contentDescription = null,
                            tint = statusAccent,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "Câu $questionNumber · $statusText",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = statusAccent
                        )
                    }
                }
            }

            Text(
                text = detail.prompt,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            val chosenText = detail.selectedOptionId ?: "Bỏ trống"
            Surface(
                color = feedbackContainer,
                border = BorderStroke(1.dp, feedbackBorder),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(11.dp)) {
                    Text(
                        text = "Bạn chọn: $chosenText  ·  Đáp án đúng: ${detail.correctOptionId}",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = statusAccent
                    )
                    Spacer(modifier = Modifier.height(5.dp))
                    Text(
                        text = detail.explanation,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    detail.citation?.let { cite ->
                        val pageText = cite.pageNumber?.let { " · Tr.$it" } ?: ""
                        Spacer(modifier = Modifier.height(8.dp))
                        val canOpenDoc = !cite.documentId.isNullOrBlank()
                        Surface(
                            color = if (canOpenDoc) semanticColors.selectedPill else MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(50),
                            modifier = if (canOpenDoc) {
                                Modifier.clickable { onOpenCitation(cite) }
                            } else {
                                Modifier
                            }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "Nguồn: ${cite.documentTitle}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (canOpenDoc) semanticColors.onSelectedPill else MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                if (pageText.isNotEmpty()) {
                                    Text(
                                        text = pageText,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = if (canOpenDoc) semanticColors.onSelectedPill else MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1
                                    )
                                }
                                if (canOpenDoc) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                                        contentDescription = "Mở tài liệu",
                                        tint = semanticColors.onSelectedPill,
                                        modifier = Modifier.size(12.dp)
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
