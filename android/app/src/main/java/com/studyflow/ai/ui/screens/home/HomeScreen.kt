package com.studyflow.ai.ui.screens.home

import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Assignment
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.studyflow.ai.data.local.StudentProfile
import com.studyflow.ai.data.local.extractProfileInitials
import com.studyflow.ai.data.model.ProgressSummaryResponse
import com.studyflow.ai.data.model.RecentActivity
import com.studyflow.ai.ui.components.EmptyStateView
import com.studyflow.ai.ui.components.ErrorStateView
import com.studyflow.ai.ui.components.HomeSkeletonView
import com.studyflow.ai.ui.components.MiniAccuracyRing
import com.studyflow.ai.ui.components.StudyHeroArtwork
import com.studyflow.ai.ui.components.TactileButton
import com.studyflow.ai.ui.components.TactileSurface
import com.studyflow.ai.ui.theme.StudyFlowTheme
import com.studyflow.ai.ui.theme.studyFlowSemanticColors
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    profile: StudentProfile = StudentProfile(),
    isDarkTheme: Boolean = false,
    onToggleDarkMode: () -> Unit = {},
    onOpenProfile: () -> Unit = {},
    onNavigateToCourses: () -> Unit,
    onNavigateToAssistant: () -> Unit,
    onNavigateToProgress: () -> Unit,
    onOpenCourseDetail: (String) -> Unit,
    viewModel: HomeViewModel = viewModel()
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState by viewModel.uiState.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val semanticColors = studyFlowSemanticColors()
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
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            val initials = remember(profile.displayName) {
                extractProfileInitials(profile.displayName)
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable(onClick = onOpenProfile)
                        .semantics { contentDescription = "Mở hồ sơ học tập" },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(11.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = semanticColors.midnightInk,
                        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
                        modifier = Modifier.size(44.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = initials,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = semanticColors.midnightOnInk
                            )
                        }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Text(
                            text = "Xin chào, ${profile.displayName}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "StudyFlow AI",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onBackground,
                            maxLines = 1
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        onClick = onToggleDarkMode,
                        color = if (isDarkTheme) semanticColors.lavenderBento else semanticColors.butterBento,
                        border = BorderStroke(
                            1.dp,
                            if (isDarkTheme) semanticColors.lavenderBorder else semanticColors.butterBorder
                        ),
                        shape = RoundedCornerShape(50),
                        modifier = Modifier.semantics {
                            contentDescription = if (isDarkTheme) {
                                "Chuyển sang giao diện Sáng"
                            } else {
                                "Chuyển sang giao diện Tối"
                            }
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = if (isDarkTheme) Icons.Outlined.LightMode else Icons.Outlined.DarkMode,
                                contentDescription = null,
                                tint = if (isDarkTheme) semanticColors.lavenderContent else semanticColors.butterContent,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = if (isDarkTheme) "Sáng" else "Tối",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (isDarkTheme) semanticColors.lavenderContent else semanticColors.butterContent,
                                maxLines = 1
                            )
                        }
                    }
                    IconButton(
                        onClick = { viewModel.refreshSummary() },
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
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (val state = uiState) {
                is HomeUiState.Loading -> HomeSkeletonView()
                is HomeUiState.Error -> ErrorStateView(state.message) { viewModel.loadSummary() }
                is HomeUiState.Success -> {
                    PullToRefreshBox(
                        isRefreshing = isRefreshing,
                        onRefresh = { viewModel.refreshSummary() },
                        modifier = Modifier.fillMaxSize()
                    ) {
                        if (state.summary.totalCourses == 0) {
                            EmptyStateView(
                                title = "Chưa có môn học nào",
                                description = "Hãy tạo môn học đầu tiên và tải tài liệu lên để AI đồng hành cùng bạn.",
                                actionLabel = "Tạo môn học ngay",
                                onActionClick = onNavigateToCourses,
                                icon = Icons.AutoMirrored.Outlined.MenuBook
                            )
                        } else {
                            HomeContent(
                                summary = state.summary,
                                profile = profile,
                                isDarkTheme = isDarkTheme,
                                onOpenProfile = onOpenProfile,
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
}

@Composable
private fun HomeContent(
    summary: ProgressSummaryResponse,
    profile: StudentProfile = StudentProfile(),
    isDarkTheme: Boolean = false,
    onOpenProfile: () -> Unit = {},
    onNavigateToCourses: () -> Unit,
    onNavigateToAssistant: () -> Unit,
    onNavigateToProgress: () -> Unit,
    onOpenCourseDetail: (String) -> Unit
) {
    val semanticColors = studyFlowSemanticColors()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Midnight Ink Hero Card + Custom Vector Book Artwork + Indigo 3D CTA
        ContinueLearningHeroCard(
            summary = summary,
            onOpenCourseDetail = onOpenCourseDetail,
            onNavigateToCourses = onNavigateToCourses
        )

        // Asymmetric Bento Stats Grid (Left Tall Lavender + Right Stacked Mint & Buttercream)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Max),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TactileSurface(
                modifier = Modifier
                    .weight(0.90f)
                    .fillMaxHeight(),
                containerColor = semanticColors.lavenderBento,
                lipColor = semanticColors.lavenderLip,
                borderColor = semanticColors.lavenderBorder,
                shape = RoundedCornerShape(22.dp),
                lipHeight = 4.dp,
                fillHeight = true,
                onClick = onNavigateToCourses
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "MÔN HỌC",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = semanticColors.lavenderContent
                        )
                        Icon(
                            imageVector = Icons.Outlined.NorthEast,
                            contentDescription = null,
                            tint = semanticColors.lavenderContent,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = String.format(Locale.US, "%02d", summary.totalCourses),
                            style = MaterialTheme.typography.displayMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Môn đang theo học",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = semanticColors.lavenderContent.copy(alpha = 0.85f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .weight(1.10f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Mint Bento: Quiz đã làm
                TactileSurface(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    containerColor = semanticColors.mintBento,
                    lipColor = semanticColors.mintLip,
                    borderColor = semanticColors.mintBorder,
                    shape = RoundedCornerShape(20.dp),
                    lipHeight = 4.dp,
                    fillHeight = true,
                    onClick = onNavigateToProgress
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = String.format(Locale.US, "%02d", summary.totalQuizzesCompleted),
                                style = MaterialTheme.typography.headlineLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Quiz đã làm",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = semanticColors.mintContent
                            )
                        }
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(13.dp),
                            border = BorderStroke(1.dp, semanticColors.mintBorder),
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Outlined.EmojiEvents,
                                    contentDescription = null,
                                    tint = semanticColors.success,
                                    modifier = Modifier.size(21.dp)
                                )
                            }
                        }
                    }
                }

                // Buttercream Bento: Điểm trung bình thực tế từ backend
                TactileSurface(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    containerColor = semanticColors.butterBento,
                    lipColor = semanticColors.butterLip,
                    borderColor = semanticColors.butterBorder,
                    shape = RoundedCornerShape(20.dp),
                    lipHeight = 4.dp,
                    fillHeight = true,
                    onClick = onNavigateToProgress
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val hasQuizzes = summary.totalQuizzesCompleted > 0
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = if (hasQuizzes) {
                                    String.format(Locale.US, "%.1f", summary.averageScore)
                                } else {
                                    "--"
                                },
                                style = MaterialTheme.typography.headlineLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (hasQuizzes) "Điểm trung bình" else "Chưa có lượt làm",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = semanticColors.butterContent,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (hasQuizzes) {
                            MiniAccuracyRing(
                                accuracy = summary.averageScore / 10.0,
                                size = 46.dp,
                                strokeWidth = 5.dp,
                                ringColorOverride = semanticColors.warning,
                                trackColorOverride = semanticColors.butterBorder
                            )
                        } else {
                            Surface(
                                color = MaterialTheme.colorScheme.surface,
                                shape = RoundedCornerShape(13.dp),
                                border = BorderStroke(1.dp, semanticColors.butterBorder),
                                modifier = Modifier.size(40.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Outlined.Quiz,
                                        contentDescription = null,
                                        tint = semanticColors.warning,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Quick Access Bento Strip
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Truy cập nhanh",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold
            )
            TextButton(
                onClick = onNavigateToProgress,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "Xem tiến độ",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Max),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            QuickActionBentoTile(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                title = "Hỏi AI",
                subtitle = "Theo tài liệu",
                icon = Icons.Outlined.AutoAwesome,
                containerColor = semanticColors.skyBento,
                lipColor = semanticColors.skyLip,
                borderColor = semanticColors.skyBorder,
                iconTint = semanticColors.info,
                subtitleColor = semanticColors.skyContent,
                onClick = onNavigateToAssistant
            )
            QuickActionBentoTile(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                title = "Làm quiz",
                subtitle = "Luyện tập",
                icon = Icons.AutoMirrored.Outlined.Assignment,
                containerColor = semanticColors.lavenderBento,
                lipColor = semanticColors.lavenderLip,
                borderColor = semanticColors.lavenderBorder,
                iconTint = MaterialTheme.colorScheme.primary,
                subtitleColor = semanticColors.lavenderContent,
                onClick = {
                    val targetCourseId = summary.recentActivity?.courseId
                    if (targetCourseId != null) {
                        onOpenCourseDetail(targetCourseId)
                    } else {
                        onNavigateToCourses()
                    }
                }
            )
            QuickActionBentoTile(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                title = "Tài liệu",
                subtitle = "PDF & TXT",
                icon = Icons.Outlined.Description,
                containerColor = semanticColors.coralBento,
                lipColor = semanticColors.coralLip,
                borderColor = semanticColors.coralBorder,
                iconTint = semanticColors.error,
                subtitleColor = semanticColors.coralContent,
                onClick = onNavigateToCourses
            )
        }

        // Compact Profile & Theme Bento Strip
        TactileSurface(
            modifier = Modifier.fillMaxWidth(),
            containerColor = MaterialTheme.colorScheme.surface,
            lipColor = semanticColors.tactileDefaultLip,
            borderColor = semanticColors.cardBorder,
            shape = RoundedCornerShape(18.dp),
            lipHeight = 3.dp,
            onClick = onOpenProfile
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    color = semanticColors.lavenderBento,
                    border = BorderStroke(1.dp, semanticColors.lavenderBorder),
                    shape = CircleShape,
                    modifier = Modifier.size(38.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Outlined.PersonOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(1.dp)
                ) {
                    Text(
                        text = "Hồ sơ học tập · ${profile.displayName}",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Mục tiêu ${profile.dailyTargetMinutes} phút/ngày · Giao diện ${if (isDarkTheme) "Tối" else "Sáng"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Icon(
                    imageVector = Icons.Outlined.ChevronRight,
                    contentDescription = "Mở hồ sơ",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))
    }
}

@Composable
private fun ContinueLearningHeroCard(
    summary: ProgressSummaryResponse,
    onOpenCourseDetail: (String) -> Unit,
    onNavigateToCourses: () -> Unit
) {
    val semanticColors = studyFlowSemanticColors()
    val recent = summary.recentActivity

    TactileSurface(
        modifier = Modifier.fillMaxWidth(),
        containerColor = semanticColors.midnightInk,
        lipColor = semanticColors.midnightInkLip,
        borderColor = semanticColors.floatingDockBorder,
        shape = RoundedCornerShape(26.dp),
        lipHeight = 5.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        color = Color(0xFF282F5E),
                        shape = RoundedCornerShape(50)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.AutoAwesome,
                                contentDescription = null,
                                tint = Color(0xFFB4B0FF),
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = "TIẾP TỤC HỌC",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFFE0DEFF)
                            )
                        }
                    }

                    if (recent != null) {
                        Text(
                            text = recent.courseName,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = semanticColors.midnightOnInk,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Gần nhất: ${recent.lastQuizTitle} · ${String.format(Locale.US, "%.1f", recent.lastScore)}/10",
                            style = MaterialTheme.typography.bodySmall,
                            color = semanticColors.midnightSubtle,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    } else {
                        Text(
                            text = "Bắt đầu ôn luyện cùng AI",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = semanticColors.midnightOnInk
                        )
                        Text(
                            text = "Mở một môn học để tải giáo trình PDF/TXT, hỏi AI và tạo đề trắc nghiệm.",
                            style = MaterialTheme.typography.bodySmall,
                            color = semanticColors.midnightSubtle
                        )
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))
                StudyHeroArtwork()
            }

            TactileButton(
                onClick = {
                    if (recent != null) onOpenCourseDetail(recent.courseId) else onNavigateToCourses()
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White,
                lipColor = semanticColors.primaryLip,
                shape = RoundedCornerShape(16.dp),
                lipHeight = 4.dp,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 11.dp)
            ) {
                Text(
                    text = if (recent != null) "Vào môn học ngay" else "Khám phá môn học",
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.Outlined.NorthEast,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun QuickActionBentoTile(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String,
    icon: ImageVector,
    containerColor: Color,
    lipColor: Color,
    borderColor: Color,
    iconTint: Color,
    subtitleColor: Color,
    onClick: () -> Unit
) {
    TactileSurface(
        modifier = modifier,
        containerColor = containerColor,
        lipColor = lipColor,
        borderColor = borderColor,
        shape = RoundedCornerShape(20.dp),
        lipHeight = 4.dp,
        fillHeight = true,
        onClick = onClick
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, borderColor),
                modifier = Modifier.size(38.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = subtitleColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
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
                averageScore = 8.0,
                recentActivity = RecentActivity(
                    courseId = "c1",
                    courseName = "Kiểm thử phần mềm",
                    lastQuizTitle = "Quiz Tổng hợp",
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
