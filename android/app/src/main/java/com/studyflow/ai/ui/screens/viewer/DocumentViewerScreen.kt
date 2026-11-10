package com.studyflow.ai.ui.screens.viewer

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.ZoomOutMap
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.studyflow.ai.ui.components.ErrorStateView
import com.studyflow.ai.ui.components.LoadingStateView
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentViewerScreen(
    documentId: String,
    initialPage: Int = 1,
    documentTitle: String = "",
    suggestedPages: List<Int> = emptyList(),
    focusTopic: String = "",
    onBack: () -> Unit,
    viewModel: DocumentViewerViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    var showJumpPageDialog by remember { mutableStateOf(false) }

    LaunchedEffect(documentId, initialPage) {
        viewModel.openDocument(
            context = context,
            documentId = documentId,
            initialPage = initialPage,
            titleHint = documentTitle,
            suggestedPages = suggestedPages
        )
    }

    val currentReadyFile: Pair<File, String>? = when (val s = uiState) {
        is DocumentViewerUiState.PdfReady -> s.file to s.mimeType
        is DocumentViewerUiState.TextReady -> s.file to s.mimeType
        else -> null
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = documentTitle.ifBlank { "Xem tài liệu" },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val subtitle = when (val s = uiState) {
                            is DocumentViewerUiState.PdfReady -> "Trang ${s.currentPage} / ${s.pageCount}"
                            is DocumentViewerUiState.TextReady -> "Tài liệu văn bản (TXT)"
                            else -> null
                        }
                        if (subtitle != null) {
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Quay lại")
                    }
                },
                actions = {
                    if (currentReadyFile != null) {
                        IconButton(
                            onClick = {
                                val opened = openExternally(
                                    context = context,
                                    file = currentReadyFile.first,
                                    mimeType = currentReadyFile.second
                                )
                                if (!opened) {
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar(
                                            "Thiết bị chưa có ứng dụng đọc tệp ngoài; bạn đang xem trực tiếp trong StudyFlow."
                                        )
                                    }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                                contentDescription = "Mở bằng ứng dụng ngoài"
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            val state = uiState
            if (state is DocumentViewerUiState.PdfReady &&
                (focusTopic.isNotBlank() || state.suggestedPages.isNotEmpty())
            ) {
                FocusReferenceBanner(
                    focusTopic = focusTopic,
                    suggestedPages = state.suggestedPages,
                    currentPage = state.currentPage,
                    onJumpToPage = { page -> viewModel.goToPage(page) }
                )
            } else if (state is DocumentViewerUiState.TextReady && focusTopic.isNotBlank()) {
                FocusReferenceBanner(
                    focusTopic = focusTopic,
                    suggestedPages = emptyList(),
                    currentPage = 1,
                    onJumpToPage = {}
                )
            }

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when (state) {
                    is DocumentViewerUiState.Loading -> LoadingStateView(state.message)
                    is DocumentViewerUiState.Error -> ErrorStateView(state.message) {
                        viewModel.openDocument(
                            context = context,
                            documentId = documentId,
                            initialPage = initialPage,
                            titleHint = documentTitle,
                            suggestedPages = suggestedPages,
                            forceReload = true
                        )
                    }
                    is DocumentViewerUiState.PdfReady -> {
                        PdfViewerPane(
                            state = state,
                            onPrevPage = { viewModel.previousPage() },
                            onNextPage = { viewModel.nextPage() },
                            onOpenJumpDialog = { showJumpPageDialog = true }
                        )
                    }
                    is DocumentViewerUiState.TextReady -> {
                        TextViewerPane(content = state.content)
                    }
                }
            }
        }
    }

    val pdfState = uiState as? DocumentViewerUiState.PdfReady
    if (showJumpPageDialog && pdfState != null) {
        JumpToPageDialog(
            currentPage = pdfState.currentPage,
            pageCount = pdfState.pageCount,
            onDismiss = { showJumpPageDialog = false },
            onConfirm = { target ->
                showJumpPageDialog = false
                viewModel.goToPage(target)
            }
        )
    }
}

@Composable
private fun FocusReferenceBanner(
    focusTopic: String,
    suggestedPages: List<Int>,
    currentPage: Int,
    onJumpToPage: (Int) -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (focusTopic.isNotBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.Bookmark,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Trọng tâm cần ôn: $focusTopic",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (suggestedPages.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Trang gợi ý:",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    suggestedPages.forEach { pg ->
                        FilterChip(
                            selected = currentPage == pg,
                            onClick = { onJumpToPage(pg) },
                            label = { Text("Trang $pg", style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PdfViewerPane(
    state: DocumentViewerUiState.PdfReady,
    onPrevPage: () -> Unit,
    onNextPage: () -> Unit,
    onOpenJumpDialog: () -> Unit
) {
    var scale by remember(state.currentPage) { mutableFloatStateOf(1f) }
    var offset by remember(state.currentPage) { mutableStateOf(Offset.Zero) }
    var viewportSize by remember { mutableStateOf(IntSize.Zero) }

    Column(modifier = Modifier.fillMaxSize()) {
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                .clipToBounds()
                .onSizeChanged { viewportSize = it }
                .pointerInput(state.currentPage) {
                    detectTapGestures(
                        onDoubleTap = {
                            if (scale > 1.05f) {
                                scale = 1f
                                offset = Offset.Zero
                            } else {
                                scale = 2.2f
                            }
                        }
                    )
                }
                .pointerInput(state.currentPage) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        val nextScale = (scale * zoom).coerceIn(1f, 4f)
                        scale = nextScale
                        if (nextScale <= 1.02f) {
                            offset = Offset.Zero
                        } else {
                            val maxX = (viewportSize.width * (nextScale - 1f)) / 2f
                            val maxY = (viewportSize.height * (nextScale - 1f)) / 2f
                            offset = Offset(
                                x = (offset.x + pan.x).coerceIn(-maxX, maxX),
                                y = (offset.y + pan.y).coerceIn(-maxY, maxY)
                            )
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            val bitmap: Bitmap? = state.pageBitmap
            if (bitmap != null) {
                val innerMaxW = (maxWidth - 24.dp).coerceAtLeast(1.dp)
                val innerMaxH = (maxHeight - 24.dp).coerceAtLeast(1.dp)
                val boxRatio = innerMaxW.value / innerMaxH.value
                val bmpRatio = bitmap.width.toFloat() / bitmap.height.coerceAtLeast(1).toFloat()
                val sheetSizeModifier = if (bmpRatio >= boxRatio) {
                    Modifier.width(innerMaxW).aspectRatio(bmpRatio)
                } else {
                    Modifier.height(innerMaxH).aspectRatio(bmpRatio)
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    shadowElevation = 4.dp,
                    color = Color.White,
                    modifier = sheetSizeModifier.graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    }
                ) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Trang ${state.currentPage}",
                        contentScale = ContentScale.FillBounds,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            if (state.isRenderingPage) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .padding(14.dp)
                            .size(26.dp),
                        strokeWidth = 2.5.dp
                    )
                }
            }
        }

        // Thanh điều khiển trang PDF
        Surface(
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                FilledTonalIconButton(
                    onClick = onPrevPage,
                    enabled = state.currentPage > 1
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowLeft,
                        contentDescription = "Trang trước"
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.clickable(onClick = onOpenJumpDialog)
                    ) {
                        Text(
                            text = "Trang ${state.currentPage} / ${state.pageCount}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                        )
                    }

                    if (scale > 1.05f) {
                        AssistChip(
                            onClick = {
                                scale = 1f
                                offset = Offset.Zero
                            },
                            label = { Text("1x", style = MaterialTheme.typography.labelMedium) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.ZoomOutMap,
                                    contentDescription = "Thu về 1x",
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        )
                    }
                }

                FilledTonalIconButton(
                    onClick = onNextPage,
                    enabled = state.currentPage < state.pageCount
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                        contentDescription = "Trang sau"
                    )
                }
            }
        }
    }
}

@Composable
private fun TextViewerPane(content: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            SelectionContainer {
                Text(
                    text = content.ifBlank { "(Tài liệu trống)" },
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.SansSerif,
                        lineHeight = 22.sp
                    ),
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
    }
}

@Composable
private fun JumpToPageDialog(
    currentPage: Int,
    pageCount: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var input by remember(currentPage) { mutableStateOf(currentPage.toString()) }
    val parsed = input.trim().toIntOrNull()
    val isValid = parsed != null && parsed in 1..pageCount

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Đi tới trang") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Nhập số trang từ 1 đến $pageCount:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = input,
                    onValueChange = { raw ->
                        if (raw.length <= 5 && raw.all { it.isDigit() }) {
                            input = raw
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = input.isNotBlank() && !isValid,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { parsed?.let(onConfirm) },
                enabled = isValid
            ) {
                Text("Mở trang")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Hủy")
            }
        }
    )
}

private fun openExternally(context: Context, file: File, mimeType: String): Boolean {
    return try {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Mở tài liệu bằng"))
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: Exception) {
        false
    }
}

