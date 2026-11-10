package com.studyflow.ai.ui.screens.courses

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import com.studyflow.ai.data.model.CourseResponse
import com.studyflow.ai.ui.components.EmptyStateView
import com.studyflow.ai.ui.components.ErrorStateView
import com.studyflow.ai.ui.components.LoadingStateView
import com.studyflow.ai.ui.theme.StudyFlowTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoursesScreen(
    onOpenCourseDetail: (String) -> Unit,
    viewModel: CoursesViewModel = viewModel()
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState by viewModel.uiState.collectAsState()
    val isCreating by viewModel.isCreating.collectAsState()
    val actionError by viewModel.actionError.collectAsState()

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
        topBar = {
            TopAppBar(
                title = { Text("Môn học", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { viewModel.loadCourses() }) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "Làm mới")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showCreateDialog = true },
                icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                text = { Text("Thêm môn") }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (!showCreateDialog && courseToEdit == null && !actionError.isNullOrBlank()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(12.dp),
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
                is CoursesUiState.Loading -> LoadingStateView("Đang tải danh sách môn học...")
                is CoursesUiState.Error -> ErrorStateView(state.message) { viewModel.loadCourses() }
                is CoursesUiState.Success -> {
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
                            description = "Nhấn 'Thêm môn' để tạo môn học mới và bắt đầu tải tài liệu lên.",
                            actionLabel = "Thêm môn học",
                            onActionClick = { showCreateDialog = true }
                        )
                    } else {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
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
                            shape = RoundedCornerShape(12.dp)
                        )

                        if (filteredCourses.isEmpty()) {
                            EmptyStateView(
                                title = "Không tìm thấy kết quả",
                                description = "Không có môn học nào khớp với từ khóa '$searchQuery'.",
                                actionLabel = "Xóa bộ lọc",
                                onActionClick = { searchQuery = "" }
                            )
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(16.dp),
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
                                item { Spacer(modifier = Modifier.height(72.dp)) }
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
            title = { Text("Xóa môn học?") },
            text = { Text("Bạn có chắc muốn xóa môn '${target.name}' và toàn bộ tài liệu, câu hỏi liên quan?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteCourse(target.id)
                        courseToDelete = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Xóa")
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
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = course.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row {
                    IconButton(onClick = onEditClick) {
                        Icon(
                            imageVector = Icons.Outlined.Edit,
                            contentDescription = "Chỉnh sửa môn học",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onDeleteClick) {
                        Icon(
                            imageVector = Icons.Outlined.DeleteOutline,
                            contentDescription = "Xóa môn học",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (!course.description.isNullOrBlank()) {
                Text(
                    text = course.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(10.dp))
            } else {
                Spacer(modifier = Modifier.height(6.dp))
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AssistChip(
                    onClick = onClick,
                    label = { Text("${course.documentCount} tài liệu") },
                    leadingIcon = {
                        Icon(
                            Icons.Outlined.Description,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                )
                AssistChip(
                    onClick = onClick,
                    label = { Text("${course.quizCount} quiz") },
                    leadingIcon = {
                        Icon(
                            Icons.Outlined.Quiz,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                )
            }
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
        title = { Text(title) },
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
                enabled = !isSaving
            ) {
                if (isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
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

