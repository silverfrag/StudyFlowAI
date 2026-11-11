package com.studyflow.ai.ui.screens.viewer

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import com.studyflow.ai.ui.components.TactileSurface
import com.studyflow.ai.ui.theme.BeVietnamProFontFamily
import com.studyflow.ai.ui.theme.studyFlowSemanticColors
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
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0, 0, 0, 0),
                title = {
                    Column {
                        Text(
                            text = documentTitle.ifBlank { "Xem tài liệu" },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
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
    val semanticColors = studyFlowSemanticColors()
    Surface(
        color = semanticColors.lavenderBento,
        border = BorderStroke(1.dp, semanticColors.lavenderBorder),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
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
                        fontWeight = FontWeight.ExtraBold,
                        color = semanticColors.lavenderContent,
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
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Trang gợi ý:",
                        style = MaterialTheme.typography.labelSmall,
                        color = semanticColors.lavenderContent
                    )
                    suggestedPages.forEach { pg ->
                        val isCur = currentPage == pg
                        Surface(
                            onClick = { onJumpToPage(pg) },
                            shape = RoundedCornerShape(50),
                            color = if (isCur) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, if (isCur) MaterialTheme.colorScheme.primary else semanticColors.lavenderBorder)
                        ) {
                            Text(
                                text = "Trang $pg",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (isCur) Color.White else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
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
    val semanticColors = studyFlowSemanticColors()
    var scale by remember(state.currentPage) { mutableFloatStateOf(1f) }
    var offset by remember(state.currentPage) { mutableStateOf(Offset.Zero) }
    var viewportSize by remember { mutableStateOf(IntSize.Zero) }

    Column(modifier = Modifier.fillMaxSize()) {
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
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
                    shape = RoundedCornerShape(12.dp),
                    shadowElevation = 4.dp,
                    border = BorderStroke(1.dp, semanticColors.cardBorder),
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
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                    border = BorderStroke(1.dp, semanticColors.cardBorder)
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

        // Rounded Pill Page Control Dock
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                color = semanticColors.floatingDockContainer,
                border = BorderStroke(1.dp, semanticColors.floatingDockBorder),
                shape = RoundedCornerShape(50),
                shadowElevation = 6.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Surface(
                        onClick = onPrevPage,
                        enabled = state.currentPage > 1,
                        shape = CircleShape,
                        color = if (state.currentPage > 1) Color(0xFF282F5E) else Color.Transparent,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowLeft,
                                contentDescription = "Trang trước",
                                tint = if (state.currentPage > 1) Color.White else Color.White.copy(alpha = 0.35f)
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.primary,
                            shape = RoundedCornerShape(50),
                            modifier = Modifier.clickable(onClick = onOpenJumpDialog)
                        ) {
                            Text(
                                text = "Trang ${state.currentPage} / ${state.pageCount}",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp)
                            )
                        }

                        if (scale > 1.05f) {
                            Surface(
                                onClick = {
                                    scale = 1f
                                    offset = Offset.Zero
                                },
                                shape = RoundedCornerShape(50),
                                color = Color(0xFF282F5E)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.ZoomOutMap,
                                        contentDescription = "Thu về 1x",
                                        tint = Color.White,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = "1x",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }

                    Surface(
                        onClick = onNextPage,
                        enabled = state.currentPage < state.pageCount,
                        shape = CircleShape,
                        color = if (state.currentPage < state.pageCount) Color(0xFF282F5E) else Color.Transparent,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                                contentDescription = "Trang sau",
                                tint = if (state.currentPage < state.pageCount) Color.White else Color.White.copy(alpha = 0.35f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TextViewerPane(content: String) {
    val semanticColors = studyFlowSemanticColors()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        TactileSurface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            lipHeight = 3.dp,
            borderColor = semanticColors.cardBorder
        ) {
            SelectionContainer {
                Text(
                    text = content.ifBlank { "(Tài liệu trống)" },
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = BeVietnamProFontFamily,
                        lineHeight = 23.sp
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
        title = { Text("Đi tới trang", fontWeight = FontWeight.Bold) },
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
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = input.isNotBlank() && !isValid,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { parsed?.let(onConfirm) },
                enabled = isValid,
                shape = RoundedCornerShape(12.dp)
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
