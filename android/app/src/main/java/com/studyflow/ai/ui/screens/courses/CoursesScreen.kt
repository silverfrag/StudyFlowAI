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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.studyflow.ai.data.model.CourseResponse
import com.studyflow.ai.ui.components.EmptyStateView
import com.studyflow.ai.ui.components.ErrorStateView
import com.studyflow.ai.ui.components.LoadingStateView

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoursesScreen(
    onOpenCourseDetail: (String) -> Unit,
    viewModel: CoursesViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val isCreating by viewModel.isCreating.collectAsState()
    val actionError by viewModel.actionError.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var showCreateDialog by remember { mutableStateOf(false) }
    var courseToDelete by remember { mutableStateOf<CourseResponse?>(null) }

    LaunchedEffect(Unit) {
        viewModel.loadCourses()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Môn học", fontWeight = FontWeight.Bold) },
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
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )

                        if (filteredCourses.isEmpty()) {
                            EmptyStateView(
                                title = "Không tìm thấy kết quả",
                                description = "Không có môn học nào khớp với từ khóa '$searchQuery'."
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
        CreateCourseDialog(
            isCreating = isCreating,
            error = actionError,
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
                IconButton(onClick = onDeleteClick) {
                    Icon(
                        imageVector = Icons.Outlined.DeleteOutline,
                        contentDescription = "Xóa môn học",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
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
private fun CreateCourseDialog(
    isCreating: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onConfirm: (String, String?) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = { if (!isCreating) onDismiss() },
        title = { Text("Thêm môn học mới") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Tên môn học *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
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
                enabled = !isCreating
            ) {
                if (isCreating) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text("Tạo môn")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isCreating) {
                Text("Hủy")
            }
        }
    )
}

