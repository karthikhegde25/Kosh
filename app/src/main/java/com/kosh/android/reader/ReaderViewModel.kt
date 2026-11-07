package com.kosh.android.reader

import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kosh.android.data.db.AnnotationType
import com.kosh.android.data.db.Bookmark
import com.kosh.android.data.db.PdfAnnotation
import com.kosh.android.data.db.ReaderDataRepository
import com.kosh.android.data.model.NormalizedPoint
import com.kosh.android.reader.annotation.AnnotatedPdfExporter
import com.kosh.android.reader.annotation.toDrawItem
import com.kosh.android.util.AnnotationCodec
import com.kosh.android.util.clampPage
import com.kosh.android.util.documentKey
import com.kosh.android.util.thinPoints
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

sealed interface ReaderEvent {
    data object BookmarkFailed : ReaderEvent
    data object AnnotationFailed : ReaderEvent
    data object AnnotatedExported : ReaderEvent
    data object AnnotatedExportFailed : ReaderEvent
}

sealed interface AnnotatedExportState {
    data object Idle : AnnotatedExportState
    data class Running(val done: Int, val total: Int) : AnnotatedExportState
}

class ReaderViewModel(
    private val documents: PdfDocumentRepository,
    private val progress: ReadingProgressRepository,
    private val readerData: ReaderDataRepository,
    private val prefsRepo: ReaderPreferencesRepository,
    private val annotatedExporter: AnnotatedPdfExporter,
    handle: SavedStateHandle,
) : ViewModel() {

    /** The URI string as passed in the navigation route. */
    val uriString: String? = handle.get<String>("uri")
    private val uri: Uri? = uriString?.let { Uri.parse(it) }
    private val key: String? = uriString?.let { documentKey(it) }

    private val _state = MutableStateFlow<ReaderUiState>(ReaderUiState.Loading)
    val state: StateFlow<ReaderUiState> = _state.asStateFlow()

    private val cache = PageRenderCache()
    private var engine: PdfRenderEngine? = null
    private var prefetchJob: Job? = null
    private var openJob: Job? = null
    private var exportJob: Job? = null

    // ---- preferences, bookmarks, annotations ---------------------------------------------------

    val prefs: StateFlow<ReaderPreferences> =
        prefsRepo.prefs.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReaderPreferences())

    val bookmarks: StateFlow<List<Bookmark>> =
        (key?.let { readerData.observeBookmarks(it) } ?: emptyFlow<List<Bookmark>>())
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val annotations: StateFlow<List<PdfAnnotation>> =
        (key?.let { readerData.observeAnnotations(it) } ?: emptyFlow<List<PdfAnnotation>>())
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _events = Channel<ReaderEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private val _canUndo = MutableStateFlow(false)
    val canUndoAnnotation: StateFlow<Boolean> = _canUndo.asStateFlow()

    private val _export = MutableStateFlow<AnnotatedExportState>(AnnotatedExportState.Idle)
    val annotatedExport: StateFlow<AnnotatedExportState> = _export.asStateFlow()

    private sealed interface UndoAction {
        data class Added(val annotation: PdfAnnotation) : UndoAction
        data class Deleted(val annotation: PdfAnnotation) : UndoAction
    }

    private val undoStack = ArrayDeque<UndoAction>()

    init { open() }

    fun retry() {
        _state.value = ReaderUiState.Loading
        open()
    }

    private fun open() {
        openJob?.cancel()
        val target = uri
        if (target == null) {
            _state.value = ReaderUiState.Error(ReaderError.NOT_FOUND)
            return
        }
        openJob = viewModelScope.launch {
            when (val outcome = documents.open(target)) {
                is OpenOutcome.Failed -> _state.value = ReaderUiState.Error(outcome.error)
                is OpenOutcome.Opened -> {
                    engine?.close()
                    engine = outcome.engine
                    cache.clear()
                    val saved = key?.let { progress.load(it) } ?: 0
                    // Clamp in case the file changed and the saved page no longer exists.
                    _state.value = ReaderUiState.Ready(outcome.info, clampPage(saved, outcome.info.pageCount))
                }
            }
        }
    }

    /** Renders on a background thread, using the bounded cache. Returns null if the page can't be rendered. */
    suspend fun renderPage(index: Int, widthPx: Int): Bitmap? {
        cache.get(index, widthPx)?.let { return it }
        val e = engine ?: return null
        return withContext(Dispatchers.IO) {
            e.render(index, widthPx)?.also { cache.put(index, widthPx, it) }
        }
    }

    /** Warms the cache for the page after the current one; replaced whenever the page changes. */
    fun prefetch(index: Int, widthPx: Int) {
        val count = (state.value as? ReaderUiState.Ready)?.doc?.pageCount ?: return
        if (index !in 0 until count || cache.get(index, widthPx) != null) return
        prefetchJob?.cancel()
        prefetchJob = viewModelScope.launch { renderPage(index, widthPx) }
    }

    fun saveProgress(pageIndex: Int) {
        key?.let { progress.save(it, pageIndex) }
    }

    // ---- preferences -----------------------------------------------------------------------------

    fun setTheme(theme: ReaderTheme) { viewModelScope.launch { prefsRepo.setTheme(theme) } }
    fun setLayout(layout: ReaderLayout) { viewModelScope.launch { prefsRepo.setLayout(layout) } }
    fun setInvert(invert: Boolean) { viewModelScope.launch { prefsRepo.setInvert(invert) } }
    fun setAutoScrollSpeed(speed: Float) { viewModelScope.launch { prefsRepo.setAutoScrollSpeed(speed) } }

    // ---- bookmarks -------------------------------------------------------------------------------

    fun addBookmark(pageIndex: Int, label: String?) {
        val k = key ?: return
        viewModelScope.launch {
            try {
                readerData.addBookmark(Bookmark(UUID.randomUUID().toString(), k, pageIndex, label?.trim()?.take(60)?.ifEmpty { null }, System.currentTimeMillis()))
            } catch (e: Exception) {
                _events.send(ReaderEvent.BookmarkFailed)
            }
        }
    }

    fun removeBookmark(id: String) {
        viewModelScope.launch {
            try { readerData.deleteBookmark(id) } catch (e: Exception) { _events.send(ReaderEvent.BookmarkFailed) }
        }
    }

    // ---- annotations -----------------------------------------------------------------------------

    fun addAnnotation(pageIndex: Int, type: AnnotationType, colorArgb: Long, size: Float, points: List<NormalizedPoint>, note: String? = null) {
        val k = key ?: return
        if (points.isEmpty()) return
        val annotation = PdfAnnotation(
            id = UUID.randomUUID().toString(),
            documentKey = k,
            pageIndex = pageIndex,
            type = type.name,
            colorArgb = colorArgb,
            strokeWidth = size,
            points = AnnotationCodec.encode(if (type == AnnotationType.TEXT) points.take(1) else thinPoints(points)),
            note = note,
            createdAt = System.currentTimeMillis(),
        )
        viewModelScope.launch {
            try {
                readerData.addAnnotation(annotation)
                pushUndo(UndoAction.Added(annotation))
            } catch (e: Exception) {
                _events.send(ReaderEvent.AnnotationFailed) // the stroke was not saved; tell the user
            }
        }
    }

    fun deleteAnnotation(id: String) {
        val annotation = annotations.value.firstOrNull { it.id == id } ?: return
        viewModelScope.launch {
            try {
                readerData.deleteAnnotation(id)
                pushUndo(UndoAction.Deleted(annotation))
            } catch (e: Exception) {
                _events.send(ReaderEvent.AnnotationFailed)
            }
        }
    }

    /** Undoes the latest annotation action: removes a just-added one or restores a just-deleted one. */
    fun undoAnnotation() {
        val action = undoStack.removeLastOrNull() ?: return
        _canUndo.value = undoStack.isNotEmpty()
        viewModelScope.launch {
            try {
                when (action) {
                    is UndoAction.Added -> readerData.deleteAnnotation(action.annotation.id)
                    is UndoAction.Deleted -> readerData.addAnnotation(action.annotation)
                }
            } catch (e: Exception) {
                _events.send(ReaderEvent.AnnotationFailed)
            }
        }
    }

    private fun pushUndo(action: UndoAction) {
        undoStack.addLast(action)
        if (undoStack.size > 50) undoStack.removeFirst()
        _canUndo.value = true
    }

    /** Writes a new PDF with the annotations drawn in. The original PDF is never modified. */
    fun exportAnnotatedCopy(destination: Uri) {
        val ready = state.value as? ReaderUiState.Ready ?: return
        val e = engine ?: return
        if (_export.value is AnnotatedExportState.Running) return
        val byPage = annotations.value.groupBy({ it.pageIndex }, { it.toDrawItem() })
        _export.value = AnnotatedExportState.Running(0, ready.doc.pageCount)
        exportJob = viewModelScope.launch {
            val result = annotatedExporter.export(e, ready.doc, byPage, destination) { done, total ->
                _export.value = AnnotatedExportState.Running(done, total)
            }
            _export.value = AnnotatedExportState.Idle
            _events.send(if (result.isSuccess) ReaderEvent.AnnotatedExported else ReaderEvent.AnnotatedExportFailed)
        }
    }

    fun cancelAnnotatedExport() {
        exportJob?.cancel()
        _export.value = AnnotatedExportState.Idle
    }

    override fun onCleared() {
        prefetchJob?.cancel()
        exportJob?.cancel()
        engine?.close()
        engine = null
        cache.clear()
    }
}
