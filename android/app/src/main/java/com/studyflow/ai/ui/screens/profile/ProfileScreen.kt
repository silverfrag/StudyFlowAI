package com.studyflow.ai.ui.screens.profile

import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.studyflow.ai.data.local.AppThemeMode
import com.studyflow.ai.data.local.StudentProfile
import com.studyflow.ai.data.local.extractProfileInitials
import com.studyflow.ai.data.model.ProgressSummaryResponse
import com.studyflow.ai.data.model.RecentActivity
import com.studyflow.ai.ui.components.ShimmerBox
import com.studyflow.ai.ui.components.TactileButton
import com.studyflow.ai.ui.components.TactileSurface
import com.studyflow.ai.ui.theme.StudyFlowTheme
import com.studyflow.ai.ui.theme.studyFlowSemanticColors
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    profile: StudentProfile,
    themeMode: AppThemeMode,
    isDarkTheme: Boolean,
    onSelectThemeMode: (AppThemeMode) -> Unit,
    onToggleDarkMode: () -> Unit,
    onUpdateProfile: (displayName: String, studyGoal: String, dailyTargetMinutes: Int) -> Unit,
    onBack: () -> Unit,
    onOpenCourseDetail: (String) -> Unit,
    onNavigateToCourses: () -> Unit,
    onNavigateToAssistant: () -> Unit,
    onNavigateToProgress: () -> Unit,
    viewModel: ProfileViewModel = viewModel()
) {
    val statsState by viewModel.statsState.collectAsState()
    var showEditDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.loadStats(silent = statsState is ProfileStatsUiState.Success)
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0, 0, 0, 0),
                title = {
                    Text(
                        text = "Hồ sơ học tập",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "Quay lại"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.loadStats() }) {
                        Icon(
                            imageVector = Icons.Outlined.Refresh,
                            contentDescription = "Làm mới số liệu"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        ProfileContent(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            profile = profile,
            themeMode = themeMode,
            isDarkTheme = isDarkTheme,
            statsState = statsState,
            onSelectThemeMode = onSelectThemeMode,
            onToggleDarkMode = onToggleDarkMode,
            onEditProfileClick = { showEditDialog = true },
            onRetryStats = { viewModel.loadStats() },
            onOpenCourseDetail = onOpenCourseDetail,
            onNavigateToCourses = onNavigateToCourses,
            onNavigateToAssistant = onNavigateToAssistant,
            onNavigateToProgress = onNavigateToProgress
        )
    }

    if (showEditDialog) {
        EditProfileDialog(
            initialProfile = profile,
            onDismiss = { showEditDialog = false },
            onSave = { name, goal, minutes ->
                onUpdateProfile(name, goal, minutes)
                showEditDialog = false
            }
        )
    }
}

@Composable
private fun ProfileContent(
    modifier: Modifier = Modifier,
    profile: StudentProfile,
    themeMode: AppThemeMode,
    isDarkTheme: Boolean,
    statsState: ProfileStatsUiState,
    onSelectThemeMode: (AppThemeMode) -> Unit,
    onToggleDarkMode: () -> Unit,
    onEditProfileClick: () -> Unit,
    onRetryStats: () -> Unit,
    onOpenCourseDetail: (String) -> Unit,
    onNavigateToCourses: () -> Unit,
    onNavigateToAssistant: () -> Unit,
    onNavigateToProgress: () -> Unit
) {
    val semanticColors = studyFlowSemanticColors()
    val initials = remember(profile.displayName) { extractProfileInitials(profile.displayName) }

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Hero Learner Card
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
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        border = BorderStroke(2.dp, Color.White.copy(alpha = 0.28f)),
                        modifier = Modifier.size(60.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.linearGradient(
                                        listOf(
                                            semanticColors.heroGradientStart,
                                            semanticColors.heroGradientEnd
                                        )
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = initials,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White
                            )
                        }
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = profile.displayName,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = semanticColors.midnightOnInk,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = profile.studyGoal,
                            style = MaterialTheme.typography.bodySmall,
                            color = semanticColors.midnightSubtle,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Surface(
                            color = Color.White.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(50)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Schedule,
                                    contentDescription = null,
                                    tint = Color(0xFFB4B0FF),
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = "Mục tiêu: ${profile.dailyTargetMinutes} phút/ngày",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFE9E7FF)
                                )
                            }
                        }
                    }
                }

                TactileButton(
                    onClick = onEditProfileClick,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White,
                    lipColor = semanticColors.primaryLip,
                    shape = RoundedCornerShape(14.dp),
                    lipHeight = 3.dp,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Edit,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Chỉnh sửa thông tin", maxLines = 1)
                }

                Text(
                    text = "Hồ sơ cá nhân & tùy chọn giao diện được lưu cục bộ trên thiết bị.",
                    style = MaterialTheme.typography.labelSmall,
                    color = semanticColors.midnightSubtle.copy(alpha = 0.85f)
                )
            }
        }

        // Theme Switcher Card (Light / Dark / System)
        TactileSurface(
            modifier = Modifier.fillMaxWidth(),
            containerColor = MaterialTheme.colorScheme.surface,
            lipColor = semanticColors.tactileDefaultLip,
            borderColor = semanticColors.cardBorder,
            shape = RoundedCornerShape(22.dp),
            lipHeight = 4.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            color = if (isDarkTheme) semanticColors.lavenderBento else semanticColors.butterBento,
                            border = BorderStroke(
                                1.dp,
                                if (isDarkTheme) semanticColors.lavenderBorder else semanticColors.butterBorder
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (isDarkTheme) Icons.Outlined.DarkMode else Icons.Outlined.LightMode,
                                    contentDescription = null,
                                    tint = if (isDarkTheme) MaterialTheme.colorScheme.primary else semanticColors.warning,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = "Giao diện hiển thị",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.ExtraBold
                            )
                            val modeHint = when (themeMode) {
                                AppThemeMode.SYSTEM -> if (isDarkTheme) "Theo hệ thống (Đang Tối)" else "Theo hệ thống (Đang Sáng)"
                                AppThemeMode.LIGHT -> "Chế độ Sáng (Warm Ivory)"
                                AppThemeMode.DARK -> "Chế độ Tối (Midnight)"
                            }
                            Text(
                                text = modeHint,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Switch(
                        checked = isDarkTheme,
                        onCheckedChange = { onToggleDarkMode() },
                        modifier = Modifier.semantics {
                            contentDescription = "Chuyển chế độ Sáng Tối"
                        }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ThemeOptionPill(
                        modifier = Modifier.weight(1f),
                        label = "Hệ thống",
                        icon = Icons.Outlined.BrightnessAuto,
                        selected = themeMode == AppThemeMode.SYSTEM,
                        onClick = { onSelectThemeMode(AppThemeMode.SYSTEM) }
                    )
                    ThemeOptionPill(
                        modifier = Modifier.weight(1f),
                        label = "Sáng",
                        icon = Icons.Outlined.LightMode,
                        selected = themeMode == AppThemeMode.LIGHT,
                        onClick = { onSelectThemeMode(AppThemeMode.LIGHT) }
                    )
                    ThemeOptionPill(
                        modifier = Modifier.weight(1f),
                        label = "Tối",
                        icon = Icons.Outlined.DarkMode,
                        selected = themeMode == AppThemeMode.DARK,
                        onClick = { onSelectThemeMode(AppThemeMode.DARK) }
                    )
                }
            }
        }

        // Real Backend Learning Summary
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Tổng quan học tập",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                text = "Số liệu thực tế",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        when (statsState) {
            is ProfileStatsUiState.Loading -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    repeat(3) {
                        ShimmerBox(
                            modifier = Modifier
                                .weight(1f)
                                .height(88.dp),
                            shape = RoundedCornerShape(18.dp)
                        )
                    }
                }
            }
            is ProfileStatsUiState.Error -> {
                Surface(
                    color = semanticColors.coralBento,
                    border = BorderStroke(1.dp, semanticColors.coralBorder),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = statsState.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = semanticColors.coralContent,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = onRetryStats) {
                            Text("Thử lại", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            is ProfileStatsUiState.Success -> {
                val summary = statsState.summary
                val avgDisplay = if (summary.totalQuizzesCompleted > 0) {
                    String.format(Locale.US, "%.1f", summary.averageScore)
                } else {
                    "--"
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Max),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ProfileStatBentoTile(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        value = String.format(Locale.US, "%02d", summary.totalCourses),
                        label = "Môn học",
                        bg = semanticColors.lavenderBento,
                        lip = semanticColors.lavenderLip,
                        border = semanticColors.lavenderBorder,
                        labelColor = semanticColors.lavenderContent
                    )
                    ProfileStatBentoTile(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        value = String.format(Locale.US, "%02d", summary.totalQuizzesCompleted),
                        label = "Bài quiz",
                        bg = semanticColors.mintBento,
                        lip = semanticColors.mintLip,
                        border = semanticColors.mintBorder,
                        labelColor = semanticColors.mintContent
                    )
                    ProfileStatBentoTile(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        value = avgDisplay,
                        label = "Điểm TB",
                        bg = semanticColors.butterBento,
                        lip = semanticColors.butterLip,
                        border = semanticColors.butterBorder,
                        labelColor = semanticColors.butterContent
                    )
                }

                summary.recentActivity?.let { recent ->
                    TactileSurface(
                        modifier = Modifier.fillMaxWidth(),
                        containerColor = semanticColors.skyBento,
                        lipColor = semanticColors.skyLip,
                        borderColor = semanticColors.skyBorder,
                        shape = RoundedCornerShape(18.dp),
                        lipHeight = 3.dp,
                        onClick = { onOpenCourseDetail(recent.courseId) }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = "Gần nhất · ${recent.courseName}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = semanticColors.skyContent
                                )
                                Text(
                                    text = "${recent.lastQuizTitle} · ${String.format(Locale.US, "%.1f", recent.lastScore)}/10",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                imageVector = Icons.Outlined.NorthEast,
                                contentDescription = "Mở môn học",
                                tint = semanticColors.info,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }

        // Quick Shortcuts
        Text(
            text = "Điều hướng nhanh",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.ExtraBold
        )

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ProfileShortcutRow(
                title = "Môn học & Tài liệu",
                subtitle = "Quản lý học phần, tải PDF/TXT và tạo đề quiz",
                icon = Icons.AutoMirrored.Outlined.MenuBook,
                iconBg = semanticColors.lavenderBento,
                iconBorder = semanticColors.lavenderBorder,
                iconTint = MaterialTheme.colorScheme.primary,
                onClick = onNavigateToCourses
            )
            ProfileShortcutRow(
                title = "Trợ lý AI theo môn",
                subtitle = "Hỏi đáp RAG có trích dẫn trang tài liệu",
                icon = Icons.Outlined.AutoAwesome,
                iconBg = semanticColors.skyBento,
                iconBorder = semanticColors.skyBorder,
                iconTint = semanticColors.info,
                onClick = onNavigateToAssistant
            )
            ProfileShortcutRow(
                title = "Tiến độ & Topic yếu",
                subtitle = "Xem phân tích điểm yếu và flashcard gợi ý ôn tập",
                icon = Icons.AutoMirrored.Outlined.TrendingUp,
                iconBg = semanticColors.mintBento,
                iconBorder = semanticColors.mintBorder,
                iconTint = semanticColors.success,
                onClick = onNavigateToProgress
            )
        }

        Spacer(modifier = Modifier.height(14.dp))
    }
}

@Composable
private fun ThemeOptionPill(
    modifier: Modifier = Modifier,
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    val semanticColors = studyFlowSemanticColors()
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = if (selected) semanticColors.selectedPill else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        border = BorderStroke(
            width = 1.dp,
            color = if (selected) semanticColors.selectedPill else semanticColors.cardBorder
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) semanticColors.onSelectedPill else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = if (selected) semanticColors.onSelectedPill else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ProfileStatBentoTile(
    modifier: Modifier = Modifier,
    value: String,
    label: String,
    bg: Color,
    lip: Color,
    border: Color,
    labelColor: Color
) {
    TactileSurface(
        modifier = modifier,
        containerColor = bg,
        lipColor = lip,
        borderColor = border,
        shape = RoundedCornerShape(18.dp),
        lipHeight = 3.dp,
        fillHeight = true
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = labelColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ProfileShortcutRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconBg: Color,
    iconBorder: Color,
    iconTint: Color,
    onClick: () -> Unit
) {
    val semanticColors = studyFlowSemanticColors()
    TactileSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        lipHeight = 3.dp,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                color = iconBg,
                border = BorderStroke(1.dp, iconBorder),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.size(40.dp)
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
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                imageVector = Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = semanticColors.cardBorder.copy(alpha = 0.9f).let { MaterialTheme.colorScheme.onSurfaceVariant },
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EditProfileDialog(
    initialProfile: StudentProfile,
    onDismiss: () -> Unit,
    onSave: (String, String, Int) -> Unit
) {
    val semanticColors = studyFlowSemanticColors()
    var name by remember(initialProfile) { mutableStateOf(initialProfile.displayName) }
    var goal by remember(initialProfile) { mutableStateOf(initialProfile.studyGoal) }
    var minutes by remember(initialProfile) { mutableIntStateOf(initialProfile.dailyTargetMinutes) }
    val minutePresets = listOf(30, 45, 60, 90, 120)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Chỉnh sửa hồ sơ", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { if (it.length <= 40) name = it },
                    label = { Text("Tên hiển thị") },
                    placeholder = { Text("VD: Nguyễn Minh Anh") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = goal,
                    onValueChange = { if (it.length <= 80) goal = it },
                    label = { Text("Ngành / Mục tiêu học tập") },
                    placeholder = { Text("VD: KTPM · Ôn thi cuối kỳ") },
                    maxLines = 2,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Mục tiêu mỗi ngày ($minutes phút)",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        minutePresets.forEach { preset ->
                            val isSelected = minutes == preset
                            Surface(
                                onClick = { minutes = preset },
                                shape = RoundedCornerShape(50),
                                color = if (isSelected) semanticColors.selectedPill else MaterialTheme.colorScheme.surfaceVariant,
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) semanticColors.selectedPill else semanticColors.cardBorder
                                )
                            ) {
                                Text(
                                    text = "${preset}p",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) semanticColors.onSelectedPill else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(name, goal, minutes) },
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Lưu hồ sơ", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Hủy")
            }
        }
    )
}

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ProfileContentPreview() {
    StudyFlowTheme {
        ProfileContent(
            profile = StudentProfile(
                displayName = "Minh Anh",
                studyGoal = "Kỹ thuật phần mềm · Thi cuối kỳ",
                dailyTargetMinutes = 60
            ),
            themeMode = AppThemeMode.SYSTEM,
            isDarkTheme = false,
            statsState = ProfileStatsUiState.Success(
                ProgressSummaryResponse(
                    totalCourses = 3,
                    totalQuizzesCompleted = 8,
                    averageScore = 7.8,
                    recentActivity = RecentActivity(
                        courseId = "c1",
                        courseName = "KTPM",
                        lastQuizTitle = "Quiz: Test Case",
                        lastScore = 8.0,
                        lastActiveAt = "2026-11-11T08:00:00Z"
                    )
                )
            ),
            onSelectThemeMode = {},
            onToggleDarkMode = {},
            onEditProfileClick = {},
            onRetryStats = {},
            onOpenCourseDetail = {},
            onNavigateToCourses = {},
            onNavigateToAssistant = {},
            onNavigateToProgress = {}
        )
    }
}

