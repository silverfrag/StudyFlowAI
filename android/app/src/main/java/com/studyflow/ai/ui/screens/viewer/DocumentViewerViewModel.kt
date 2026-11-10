package com.studyflow.ai.ui.screens.viewer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.studyflow.ai.data.repository.StudyFlowRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

sealed interface DocumentViewerUiState {
    data class Loading(val message: String = "Đang tải tài liệu...") : DocumentViewerUiState

    data class PdfReady(
        val file: File,
        val mimeType: String,
        val pageCount: Int,
        val currentPage: Int,
        val pageBitmap: Bitmap?,
        val isRenderingPage: Boolean,
        val suggestedPages: List<Int>
    ) : DocumentViewerUiState

    data class TextReady(
        val file: File,
        val mimeType: String,
        val content: String
    ) : DocumentViewerUiState

    data class Error(val message: String) : DocumentViewerUiState
}

class DocumentViewerViewModel(
    private val repository: StudyFlowRepository = StudyFlowRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow<DocumentViewerUiState>(DocumentViewerUiState.Loading())
    val uiState: StateFlow<DocumentViewerUiState> = _uiState.asStateFlow()

    private val rendererLock = Any()
    private var currentDescriptor: ParcelFileDescriptor? = null
    private var currentRenderer: PdfRenderer? = null
    private var currentPdfFile: File? = null
    private var currentMimeType: String = "application/pdf"
    private var loadedDocumentId: String? = null
    private var cachedSuggestedPages: List<Int> = emptyList()
    private var loadJob: Job? = null
    private var renderJob: Job? = null

    @Volatile
    private var isCleared = false

    fun openDocument(
        context: Context,
        documentId: String,
        initialPage: Int = 1,
        titleHint: String? = null,
        suggestedPages: List<Int> = emptyList(),
        forceReload: Boolean = false
    ) {
        val cleanedSuggested = suggestedPages.filter { it >= 1 }.distinct().sorted()
        val safeInitialPage = initialPage.coerceAtLeast(1)

        if (!forceReload && loadedDocumentId == documentId) {
            val currentState = _uiState.value
            if (currentState is DocumentViewerUiState.PdfReady) {
                val boundedTarget = safeInitialPage.coerceIn(1, currentState.pageCount)
                if (boundedTarget != currentState.currentPage) {
                    goToPage(boundedTarget)
                }
                return
            } else if (currentState is DocumentViewerUiState.TextReady) {
                return
            }
        }

        loadedDocumentId = documentId
        cachedSuggestedPages = cleanedSuggested
        loadJob?.cancel()
        renderJob?.cancel()

        val appContext = context.applicationContext
        loadJob = viewModelScope.launch {
            _uiState.value = DocumentViewerUiState.Loading("Đang tải tài liệu từ máy chủ...")

            val downloadResult = repository.downloadDocumentToCache(
                cacheDir = appContext.cacheDir,
                documentId = documentId,
                titleHint = titleHint,
                forceRefresh = forceReload
            )

            downloadResult.onFailure { err ->
                _uiState.value = DocumentViewerUiState.Error(
                    err.message ?: "Không thể tải tệp tài liệu."
                )
                return@launch
            }

            val downloaded = downloadResult.getOrNull() ?: return@launch
            if (!downloaded.isPdf) {
                closeRendererLocked()
                val textResult = withContext(Dispatchers.IO) {
                    runCatching { downloaded.file.readText(Charsets.UTF_8) }
                }
                textResult.onSuccess { text ->
                    _uiState.value = DocumentViewerUiState.TextReady(
                        file = downloaded.file,
                        mimeType = downloaded.mimeType,
                        content = text
                    )
                }.onFailure { err ->
                    _uiState.value = DocumentViewerUiState.Error(
                        "Không thể đọc nội dung tệp văn bản: ${err.localizedMessage ?: "Lỗi định dạng"}"
                    )
                }
                return@launch
            }

            _uiState.value = DocumentViewerUiState.Loading("Đang mở trang tài liệu...")
            val initResult = withContext(Dispatchers.IO) {
                openPdfAndRenderInitialPage(
                    file = downloaded.file,
                    mimeType = downloaded.mimeType,
                    requestedPage = safeInitialPage
                )
            }

            initResult.onSuccess { (pageCount, targetPage, bitmap) ->
                val validSuggestions = cachedSuggestedPages.filter { it in 1..pageCount }
                _uiState.value = DocumentViewerUiState.PdfReady(
                    file = downloaded.file,
                    mimeType = downloaded.mimeType,
                    pageCount = pageCount,
                    currentPage = targetPage,
                    pageBitmap = bitmap,
                    isRenderingPage = false,
                    suggestedPages = validSuggestions
                )
            }.onFailure { err ->
                _uiState.value = DocumentViewerUiState.Error(
                    err.message ?: "Không thể giải mã tệp PDF này."
                )
            }
        }
    }

    fun goToPage(pageOneBased: Int) {
        val state = _uiState.value as? DocumentViewerUiState.PdfReady ?: return
        val target = pageOneBased.coerceIn(1, state.pageCount)
        if (target == state.currentPage && state.pageBitmap != null && !state.isRenderingPage) {
            return
        }

        renderJob?.cancel()
        _uiState.value = state.copy(currentPage = target, isRenderingPage = true)

        renderJob = viewModelScope.launch {
            val bitmapResult = withContext(Dispatchers.IO) {
                renderSinglePage(target - 1)
            }
            val latest = _uiState.value as? DocumentViewerUiState.PdfReady ?: return@launch
            bitmapResult.onSuccess { bmp ->
                _uiState.value = latest.copy(
                    currentPage = target,
                    pageBitmap = bmp,
                    isRenderingPage = false
                )
            }.onFailure {
                _uiState.value = latest.copy(isRenderingPage = false)
            }
        }
    }

    fun previousPage() {
        val state = _uiState.value as? DocumentViewerUiState.PdfReady ?: return
        if (state.currentPage > 1) {
            goToPage(state.currentPage - 1)
        }
    }

    fun nextPage() {
        val state = _uiState.value as? DocumentViewerUiState.PdfReady ?: return
        if (state.currentPage < state.pageCount) {
            goToPage(state.currentPage + 1)
        }
    }

    private fun openPdfAndRenderInitialPage(
        file: File,
        mimeType: String,
        requestedPage: Int
    ): Result<Triple<Int, Int, Bitmap>> {
        synchronized(rendererLock) {
            if (isCleared) {
                return Result.failure(IllegalStateException("Viewer đã đóng."))
            }
            closeRendererInternal()

            var descriptor: ParcelFileDescriptor? = null
            var renderer: PdfRenderer? = null
            return try {
                descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                renderer = PdfRenderer(descriptor)
                val count = renderer.pageCount
                if (count <= 0) {
                    renderer.close()
                    descriptor.close()
                    return Result.failure(IllegalStateException("Tệp PDF không có trang nào để hiển thị."))
                }
                currentDescriptor = descriptor
                currentRenderer = renderer
                currentPdfFile = file
                currentMimeType = mimeType

                val targetPage = requestedPage.coerceIn(1, count)
                val bitmap = renderPageWithRenderer(renderer, targetPage - 1)
                Result.success(Triple(count, targetPage, bitmap))
            } catch (e: Exception) {
                runCatching { renderer?.close() }
                runCatching { descriptor?.close() }
                currentRenderer = null
                currentDescriptor = null
                Result.failure(Exception("Không thể hiển thị PDF (${e.localizedMessage ?: "tệp không hợp lệ"})."))
            }
        }
    }

    private fun renderSinglePage(pageIndexZeroBased: Int): Result<Bitmap> {
        synchronized(rendererLock) {
            if (isCleared) {
                return Result.failure(IllegalStateException("Viewer đã đóng."))
            }
            val renderer = currentRenderer
                ?: return Result.failure(IllegalStateException("PDF renderer chưa sẵn sàng."))
            return try {
                val safeIndex = pageIndexZeroBased.coerceIn(0, (renderer.pageCount - 1).coerceAtLeast(0))
                val bitmap = renderPageWithRenderer(renderer, safeIndex)
                Result.success(bitmap)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    private fun renderPageWithRenderer(renderer: PdfRenderer, pageIndexZeroBased: Int): Bitmap {
        val page = renderer.openPage(pageIndexZeroBased)
        try {
            val baseWidth = page.width.coerceAtLeast(1)
            val baseHeight = page.height.coerceAtLeast(1)
            val aspectRatio = baseHeight.toFloat() / baseWidth.toFloat()
            val targetWidth = 1440
            var renderWidth = targetWidth.coerceIn(720, 2048)
            var renderHeight = (renderWidth * aspectRatio).toInt().coerceAtLeast(1)
            if (renderHeight > 2400) {
                renderHeight = 2400
                renderWidth = (renderHeight / aspectRatio).toInt().coerceAtLeast(1)
            }
            val bitmap = Bitmap.createBitmap(renderWidth, renderHeight, Bitmap.Config.ARGB_8888)
            // Tô nền trắng để slide/PDF có nền trong suốt vẫn hiển thị rõ nét ở cả Light & Dark Mode
            bitmap.eraseColor(AndroidColor.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            return bitmap
        } finally {
            page.close()
        }
    }

    private fun closeRendererLocked() {
        synchronized(rendererLock) {
            closeRendererInternal()
        }
    }

    private fun closeRendererInternal() {
        runCatching { currentRenderer?.close() }
        runCatching { currentDescriptor?.close() }
        currentRenderer = null
        currentDescriptor = null
    }

    override fun onCleared() {
        super.onCleared()
        loadJob?.cancel()
        renderJob?.cancel()
        synchronized(rendererLock) {
            isCleared = true
            closeRendererInternal()
        }
    }
}

