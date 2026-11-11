package com.studyflow.ai.ui.screens.courses

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.studyflow.ai.data.model.CourseResponse
import com.studyflow.ai.ui.components.CourseMonogramBadge
import com.studyflow.ai.ui.components.EmptyStateView
import com.studyflow.ai.ui.components.ErrorStateView
import com.studyflow.ai.ui.components.ListSkeletonView
import com.studyflow.ai.ui.components.TactileButton
import com.studyflow.ai.ui.components.TactileSurface
import com.studyflow.ai.ui.theme.StudyFlowTheme
import com.studyflow.ai.ui.theme.rememberCourseVisualStyle
import com.studyflow.ai.ui.theme.studyFlowSemanticColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoursesScreen(
    onOpenCourseDetail: (String) -> Unit,
    viewModel: CoursesViewModel = viewModel()
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState by viewModel.uiState.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val isCreating by viewModel.isCreating.collectAsState()
    val actionError by viewModel.actionError.collectAsState()
    val semanticColors = studyFlowSemanticColors()

    var searchQuery by remember { mutableStateOf("") }
    var showCreateDialog by remember { mutableStateOf(false) }
    var courseToEdit by remember { mutableStateOf<CourseResponse?>(null) }
    var courseToDelete by remember { mutableStateOf<CourseResponse?>(null) }
    var skipNextResume by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        viewModel.loadCourses(silent = uiState is CoursesUiState.Success)
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                if (skipNextResume) {
                    skipNextResume = false
                } else {
                    viewModel.loadCourses(silent = true)
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
            val loadedCourses = (uiState as? CoursesUiState.Success)?.courses
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Môn học",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold
                    )
                    val subtitle = when {
                        loadedCourses == null -> "Quản lý học phần và giáo trình"
                        loadedCourses.isEmpty() -> "Tạo môn học đầu tiên của bạn"
                        else -> "${loadedCourses.size} học phần đang ôn thi"
                    }
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(
                        onClick = { viewModel.refreshCourses() },
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Refresh,
                            contentDescription = "Làm mới",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    TactileButton(
                        onClick = { showCreateDialog = true },
                        fillWidth = false,
                        containerColor = semanticColors.selectedPill,
                        contentColor = semanticColors.onSelectedPill,
                        lipColor = semanticColors.selectedPillLip,
                        borderColor = semanticColors.floatingDockBorder,
                        shape = RoundedCornerShape(50),
                        lipHeight = 3.dp,
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Add,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Thêm môn",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            AnimatedVisibility(visible = !showCreateDialog && courseToEdit == null && !actionError.isNullOrBlank()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = actionError ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { viewModel.clearError() }) {
                            Text("Đóng")
                        }
                    }
                }
            }

            when (val state = uiState) {
                is CoursesUiState.Loading -> ListSkeletonView(itemCount = 4, showSearchSkeleton = true)
                is CoursesUiState.Error -> ErrorStateView(state.message) { viewModel.loadCourses() }
                is CoursesUiState.Success -> {
                    PullToRefreshBox(
                        isRefreshing = isRefreshing,
                        onRefresh = { viewModel.refreshCourses() },
                        modifier = Modifier.fillMaxSize()
                    ) {
                        val filteredCourses = remember(state.courses, searchQuery) {
                            if (searchQuery.isBlank()) state.courses
                            else state.courses.filter {
                                it.name.contains(searchQuery, ignoreCase = true) ||
                                    (it.description?.contains(searchQuery, ignoreCase = true) == true)
                            }
                        }

                        if (state.courses.isEmpty()) {
                            EmptyStateView(
                                title = "Chưa có môn học nào",
                                description = "Nhấn 'Thêm môn' ở góc trên để tạo môn học mới và bắt đầu tải tài liệu lên.",
                                actionLabel = "Thêm môn học",
                                onActionClick = { showCreateDialog = true }
                            )
                        } else {
                            Column(modifier = Modifier.fillMaxSize()) {
                                OutlinedTextField(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 6.dp),
                                    placeholder = { Text("Tìm kiếm môn học...") },
                                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                                    trailingIcon = {
                                        if (searchQuery.isNotEmpty()) {
                                            IconButton(onClick = { searchQuery = "" }) {
                                                Icon(Icons.Outlined.Close, contentDescription = "Xóa từ khóa")
                                            }
                                        }
                                    },
                                    singleLine = true,
                                    shape = RoundedCornerShape(50),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                                        unfocusedBorderColor = semanticColors.cardBorder
                                    )
                                )

                                if (filteredCourses.isEmpty()) {
                                    EmptyStateView(
                                        title = "Không tìm thấy kết quả",
                                        description = "Không có môn học nào khớp với từ khóa '$searchQuery'.",
                                        actionLabel = "Xóa bộ lọc",
                                        onActionClick = { searchQuery = "" },
                                        icon = Icons.Outlined.SearchOff
                                    )
                                } else {
                                    LazyColumn(
                                        modifier = Modifier.fillMaxSize(),
                                        contentPadding = PaddingValues(
                                            start = 16.dp,
                                            end = 16.dp,
                                            top = 8.dp,
                                            bottom = 24.dp
                                        ),
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        items(filteredCourses, key = { it.id }) { course ->
                                            CourseCard(
                                                course = course,
                                                onClick = { onOpenCourseDetail(course.id) },
                                                onEditClick = { courseToEdit = course },
                                                onDeleteClick = { courseToDelete = course }
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
    }

    if (showCreateDialog) {
        CourseFormDialog(
            title = "Thêm môn học mới",
            confirmText = "Tạo môn",
            initialName = "",
            initialDescription = "",
            isSaving = isCreating,
            error = actionError,
            onClearError = { viewModel.clearError() },
            onDismiss = {
                viewModel.clearError()
                showCreateDialog = false
            },
            onConfirm = { name, desc ->
                viewModel.createCourse(name, desc) {
                    showCreateDialog = false
                }
            }
        )
    }

    courseToEdit?.let { editing ->
        CourseFormDialog(
            title = "Chỉnh sửa môn học",
            confirmText = "Lưu thay đổi",
            initialName = editing.name,
            initialDescription = editing.description ?: "",
            isSaving = isCreating,
            error = actionError,
            onClearError = { viewModel.clearError() },
            onDismiss = {
                viewModel.clearError()
                courseToEdit = null
            },
            onConfirm = { name, desc ->
                viewModel.updateCourse(editing.id, name, desc) {
                    courseToEdit = null
                }
            }
        )
    }

    courseToDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { courseToDelete = null },
            title = { Text("Xóa môn học?", fontWeight = FontWeight.Bold) },
            text = { Text("Bạn có chắc muốn xóa môn '${target.name}' và toàn bộ tài liệu, câu hỏi liên quan?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteCourse(target.id)
                        courseToDelete = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Xóa", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { courseToDelete = null }) {
                    Text("Hủy")
                }
            }
        )
    }
}

@Composable
private fun CourseCard(
    course: CourseResponse,
    onClick: () -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val courseStyle = rememberCourseVisualStyle(seed = course.id)
    var menuExpanded by remember { mutableStateOf(false) }

    TactileSurface(
        modifier = Modifier.fillMaxWidth(),
        containerColor = courseStyle.bentoSurface,
        lipColor = courseStyle.tactileLip,
        borderColor = courseStyle.bentoBorder,
        shape = RoundedCornerShape(24.dp),
        lipHeight = 4.dp,
        onClick = onClick
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CourseMonogramBadge(
                    courseName = course.name,
                    courseSeed = course.id,
                    size = 48.dp,
                    solidAccent = true
                )

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = course.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = course.description?.takeIf { it.isNotBlank() }
                            ?: "Nhấn để mở tài liệu và bài kiểm tra",
                        style = MaterialTheme.typography.bodySmall,
                        color = courseStyle.onContainer.copy(alpha = 0.85f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Box {
                    IconButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.MoreVert,
                            contentDescription = "Tùy chọn môn học",
                            tint = courseStyle.onContainer
                        )
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Chỉnh sửa") },
                            leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onEditClick()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Xóa môn học", color = MaterialTheme.colorScheme.error) },
                            leadingIcon = {
                                Icon(
                                    Icons.Outlined.DeleteOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onDeleteClick()
                            }
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CourseCountPill(
                        icon = Icons.Outlined.Description,
                        label = "${course.documentCount} tài liệu",
                        containerColor = courseStyle.pillSurface,
                        contentColor = courseStyle.onPillSurface,
                        iconTint = courseStyle.accent,
                        borderColor = courseStyle.bentoBorder
                    )
                    CourseCountPill(
                        icon = Icons.Outlined.Quiz,
                        label = "${course.quizCount} quiz",
                        containerColor = courseStyle.pillSurface,
                        contentColor = courseStyle.onPillSurface,
                        iconTint = courseStyle.accent,
                        borderColor = courseStyle.bentoBorder
                    )
                }

                Icon(
                    imageVector = Icons.Outlined.NorthEast,
                    contentDescription = null,
                    tint = courseStyle.accent,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun CourseCountPill(
    icon: ImageVector,
    label: String,
    containerColor: Color,
    contentColor: Color,
    iconTint: Color,
    borderColor: Color
) {
    Surface(
        color = containerColor,
        contentColor = contentColor,
        border = BorderStroke(1.dp, borderColor),
        shape = RoundedCornerShape(50)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = iconTint
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = contentColor
            )
        }
    }
}

@Composable
private fun CourseFormDialog(
    title: String,
    confirmText: String,
    initialName: String,
    initialDescription: String,
    isSaving: Boolean,
    error: String?,
    onClearError: () -> Unit,
    onDismiss: () -> Unit,
    onConfirm: (String, String?) -> Unit
) {
    var name by remember(initialName, initialDescription) { mutableStateOf(initialName) }
    var description by remember(initialName, initialDescription) { mutableStateOf(initialDescription) }

    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        onClearError()
                    },
                    label = { Text("Tên môn học *") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = {
                        description = it
                        onClearError()
                    },
                    label = { Text("Mô tả ngắn") },
                    maxLines = 3,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                if (error != null) {
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(name, description) },
                enabled = !isSaving,
                shape = RoundedCornerShape(12.dp)
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        trackColor = Color.White.copy(alpha = 0.28f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Đang lưu...")
                } else {
                    Text(confirmText)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSaving) {
                Text("Hủy")
            }
        }
    )
}

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun CourseCardPreview() {
    StudyFlowTheme {
        Box(modifier = Modifier.padding(16.dp)) {
            CourseCard(
                course = CourseResponse(
                    id = "c1",
                    name = "Cơ sở dữ liệu",
                    description = "Mô hình quan hệ, SQL và chuẩn hóa dữ liệu",
                    documentCount = 3,
                    quizCount = 2,
                    createdAt = "2026-11-10T07:00:00Z"
                ),
                onClick = {},
                onEditClick = {},
                onDeleteClick = {}
            )
        }
    }
}
