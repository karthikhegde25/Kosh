package com.kosh.android.viewer

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kosh.android.R
import com.kosh.android.data.db.Bookmark
import com.kosh.android.data.db.HighlightDao
import com.kosh.android.data.db.HighlightEntity
import com.kosh.android.data.db.ReaderDataRepository
import com.kosh.android.reader.ReadingProgressRepository
import com.kosh.android.util.documentKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import java.util.UUID

enum class LoadPhase { LOADING, READY, PASSWORD, FAILED }

enum class ViewerPanel { CONTENTS, JUMP, BOOKMARKS, HIGHLIGHTS, ZOOM, DISPLAY, AUTO_SCROLL, INFO }

data class ViewerUiState(
    val title: String = "",
    val phase: LoadPhase = LoadPhase.LOADING,
    val progress: Int = 0,
    val errorMessage: String? = null,
    val passwordWrong: Boolean = false,
    val page: Int = 1,
    val pagesCount: Int = 0,
    val scale: Float = 1f,
    val rotation: Int = 0,
    val toolbarVisible: Boolean = true,
    val scrubberVisible: Boolean = false,
    /** 0..1 position of the view along the document; drives the fast-scroll handle. */
    val scrollRatio: Float = 0f,
    val horizontalScroll: Boolean = false,
    val searchOpen: Boolean = false,
    val searchQuery: String = "",
    val searchCurrent: Int = 0,
    val searchTotal: Int = 0,
    val searchPending: Boolean = false,
    val searchFound: Boolean = true,
    val outline: List<OutlineNode> = emptyList(),
    val attachments: List<AttachmentItem> = emptyList(),
    val properties: DocProperties? = null,
    val bookmarks: List<Bookmark> = emptyList(),
    val highlights: List<HighlightItem> = emptyList(),
    val selection: TextSelection? = null,
    val tappedHighlight: Pair<HighlightItem, Anchor>? = null,
    val lastHighlightColor: Int = HIGHLIGHT_COLORS.first(),
    val autoScrollActive: Boolean = false,
    val autoScrollPaused: Boolean = false,
    val panel: ViewerPanel? = null,
    val doodleActive: Boolean = false,
    val doodleStrokes: Int = 0,
    val doodleUndoSteps: Int = 0,
    val doodleEraser: Boolean = false,
    /** Move tool: scroll and zoom the page, draw nothing. */
    val doodleHand: Boolean = false,
    val doodleColor: Int = DOODLE_COLORS[5],
    val doodleSize: DoodleSize = DoodleSize.MEDIUM,
    val doodleSaving: Boolean = false,
    val doodleConfirmDiscard: Boolean = false,
    val crashed: Boolean = false,
    /** Bumped to rebuild the WebView after the renderer process died. */
    val viewerKey: Int = 0,
) {
    val isBookmarked: Boolean get() = bookmarks.any { it.pageIndex == page - 1 }
}

sealed interface ViewerEvent {
    data class OpenLink(val href: String) : ViewerEvent
    data class AttachmentReady(val base64: String, val fileName: String) : ViewerEvent
    data class Message(@StringRes val res: Int) : ViewerEvent
    /** The doodled copy was written next to the original. */
    data class DoodleSaved(val uri: String, val name: String) : ViewerEvent
    /** The original's folder isn't writable: let the user pick where the copy goes. */
    data class DoodleSaveAs(val tempPath: String, val suggestedName: String) : ViewerEvent
}

val HIGHLIGHT_COLORS: List<Int> = listOf(0xFFFFFF98.toInt(), 0xFFA8F0A0.toInt(), 0xFF9FD8FF.toInt(), 0xFFFFB6D9.toInt(), 0xFFFFC98B.toInt())

class ViewerViewModel(
    private val appContext: Context,
    private val prefsRepo: ViewerPreferencesRepository,
    private val progressRepo: ReadingProgressRepository,
    private val readerData: ReaderDataRepository,
    private val highlightDao: HighlightDao,
    handle: SavedStateHandle,
) : ViewModel() {

    val uriString: String? = handle.get<String>("uri")
    private val uri: Uri? = uriString?.let { Uri.parse(it) }
    private val key: String? = uriString?.let { documentKey(it) }

    private val _state = MutableStateFlow(ViewerUiState(title = uri?.let { displayName(it) }.orEmpty()))
    val state: StateFlow<ViewerUiState> = _state.asStateFlow()

    val prefs: StateFlow<ViewerPreferences> =
        prefsRepo.prefs.stateIn(viewModelScope, SharingStarted.Eagerly, ViewerPreferences())

    private val eventChannel = Channel<ViewerEvent>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()

    private var viewer: PdfViewer? = null
    private var appliedPrefs: ViewerPreferences? = null
    private var triedStored = false
    private var waitingOnStored = false
    private var submittedPassword: String? = null
    private var appHighlights: List<HighlightItem> = emptyList()
    private var documentHighlights: List<HighlightItem> = emptyList()
    private var scrubberJob: Job? = null
    private var zoomSaveJob: Job? = null
    private var scaleJob: Job? = null
    private var progressJob: Job? = null
    private var lastScrollOffset = 0
    private var ignoreScrollHideUntil = 0L

    init {
        if (key != null) {
            viewModelScope.launch {
                readerData.observeBookmarks(key).collect { list -> _state.update { it.copy(bookmarks = list) } }
            }
            viewModelScope.launch {
                highlightDao.observe(key).collect { list ->
                    appHighlights = list.map { it.toItem() }
                    publishHighlights()
                }
            }
        }
        viewModelScope.launch { prefs.collect { applyPrefs(it) } }
    }

    // ---- binding to the engine ---------------------------------------------------------------------

    private val listener = object : PdfListener {
        override fun onLoadSuccess(pagesCount: Int) = handleLoaded(pagesCount)

        override fun onLoadFailed(message: String, name: String) {
            _state.update { it.copy(phase = LoadPhase.FAILED, errorMessage = message.ifBlank { name }) }
        }

        override fun onPasswordDialogChange(open: Boolean) {
            if (open) handlePasswordRequest()
        }

        override fun onProgressChange(percent: Int) { _state.update { it.copy(progress = percent) } }

        override fun onPageChange(page: Int) {
            _state.update { it.copy(page = page) }
            if (key != null && state.value.phase == LoadPhase.READY) {
                progressJob?.cancel()
                progressJob = viewModelScope.launch { delay(400); progressRepo.save(key, page - 1) }
            }
        }

        override fun onScaleChange(scale: Float, scaleValue: String) {
            // Pinching fires this many times a second; only publish the value once it settles so the screen isn't re-composed each time.
            scaleJob?.cancel()
            scaleJob = viewModelScope.launch { delay(200); _state.update { it.copy(scale = scale) } }
            if (key != null && state.value.phase == LoadPhase.READY && scaleValue.isNotBlank()) {
                zoomSaveJob?.cancel()
                zoomSaveJob = viewModelScope.launch { delay(300); prefsRepo.saveZoom(key, scaleValue) }
            }
        }

        override fun onScroll(offset: Int, total: Int, ratio: Float, horizontal: Boolean) {
            _state.update { it.copy(scrollRatio = ratio, horizontalScroll = horizontal) }
            val moved = kotlin.math.abs(offset - lastScrollOffset) > 12
            lastScrollOffset = offset
            if (state.value.phase != LoadPhase.READY || !moved) return
            if (prefs.value.scrubberOnScroll) showScrubberBriefly()
            if (prefs.value.autoHideToolbar && state.value.toolbarVisible && !state.value.searchOpen &&
                System.currentTimeMillis() >= ignoreScrollHideUntil
            ) {
                _state.update { it.copy(toolbarVisible = false) }
            }
        }

        override fun onSingleClick(x: Float, y: Float, width: Float, height: Float) = handleSingleTap(x, width)
        override fun onDoubleClick(x: Float, y: Float) = handleDoubleTap(x, y)
        override fun onLinkClick(href: String) { eventChannel.trySend(ViewerEvent.OpenLink(href)) }

        override fun onHighlightTapped(id: Long, anchor: Anchor) {
            val h = state.value.highlights.firstOrNull { it.id == id } ?: return
            _state.update { it.copy(tappedHighlight = h to anchor, selection = null) }
        }

        override fun onTextSelected(selection: TextSelection?) {
            _state.update { it.copy(selection = selection, tappedHighlight = if (selection != null) null else it.tappedHighlight) }
        }

        override fun onFindMatchStart() { _state.update { it.copy(searchPending = true) } }
        override fun onFindMatchChange(current: Int, total: Int) {
            _state.update { it.copy(searchCurrent = current, searchTotal = total) }
        }
        override fun onFindMatchComplete(found: Boolean) { _state.update { it.copy(searchPending = false, searchFound = found) } }

        override fun onOutlineLoaded(outline: List<OutlineNode>) { _state.update { it.copy(outline = outline) } }
        override fun onAttachmentsLoaded(items: List<AttachmentItem>) { _state.update { it.copy(attachments = items) } }
        override fun onPropertiesLoaded(properties: DocProperties) { _state.update { it.copy(properties = properties) } }
        override fun onDocumentHighlightsLoaded(items: List<HighlightItem>) {
            documentHighlights = items
            publishHighlights()
        }

        override fun onDoodleChanged(strokes: Int, undoSteps: Int) {
            _state.update { it.copy(doodleStrokes = strokes, doodleUndoSteps = undoSteps) }
        }

        override fun onDoodleSaved(file: java.io.File) { finishDoodleSave(file) }

        override fun onDoodleSaveFailed(message: String) {
            _state.update { it.copy(doodleSaving = false) }
            eventChannel.trySend(ViewerEvent.Message(R.string.viewer_doodle_save_failed))
        }

        override fun onAutoScrollEnd() { _state.update { it.copy(autoScrollActive = false, autoScrollPaused = false) } }
        override fun onAttachmentData(base64: String, fileName: String) {
            eventChannel.trySend(ViewerEvent.AttachmentReady(base64, fileName))
        }
        override fun onRendererCrashed() { _state.update { it.copy(crashed = true) } }
    }

    fun attach(v: PdfViewer) {
        viewer?.removeListener(listener)
        viewer = v
        v.addListener(listener)
        v.onHighlightAction = { highlightSelection(state.value.lastHighlightColor) }
        appliedPrefs = null
        triedStored = false
        waitingOnStored = false
        _state.update { it.copy(phase = LoadPhase.LOADING, progress = 0, crashed = false, selection = null, tappedHighlight = null) }
        applyPrefs(prefs.value)
        uri?.let { v.open(it) }
    }

    fun detach(v: PdfViewer) {
        if (viewer === v) {
            v.removeListener(listener)
            v.onHighlightAction = null
            viewer = null
        }
    }

    /** Call when the WebView's process died: the screen then builds a fresh one and re-attaches. */
    fun reloadViewer() {
        _state.update { it.copy(crashed = false, viewerKey = it.viewerKey + 1, phase = LoadPhase.LOADING) }
    }

    private fun applyPrefs(p: ViewerPreferences) {
        val v = viewer ?: return
        val old = appliedPrefs
        appliedPrefs = p
        if (old == null || old.theme != p.theme) v.setTheme(p.theme)
        if (old == null || old.scrollDirection != p.scrollDirection) v.setScrollDirection(p.scrollDirection)
        if (old == null || old.snapPages != p.snapPages || old.scrollDirection != p.scrollDirection) v.setSnap(p.snapPages, p.scrollDirection)
        if (old == null || old.lockHorizontal != p.lockHorizontal || old.scrollDirection != p.scrollDirection) {
            v.setHorizontalLock(p.lockHorizontal && p.scrollDirection == ScrollDirection.VERTICAL)
        }
    }

    // ---- load flow -------------------------------------------------------------------------------------

    private fun handleLoaded(pagesCount: Int) {
        val v = viewer ?: return
        ignoreScrollHideUntil = System.currentTimeMillis() + 2000
        _state.update { it.copy(phase = LoadPhase.READY, pagesCount = pagesCount, passwordWrong = false, errorMessage = null) }
        val pw = submittedPassword
        if (pw != null && key != null && prefs.value.rememberPasswords) viewModelScope.launch { prefsRepo.savePassword(key, pw) }
        submittedPassword = null
        viewModelScope.launch {
            val savedZoom = key?.let { prefsRepo.loadZoom(it) }
            val token = ZoomPreset.entries.firstOrNull { it.token == savedZoom }
            val numeric = savedZoom?.toFloatOrNull()
            when {
                token != null -> v.zoomPreset(token)
                numeric != null -> v.zoomTo(numeric)
                else -> v.zoomPreset(ZoomPreset.FIT_WIDTH)
            }
            val saved = key?.let { progressRepo.load(it) }
            if (saved != null && saved in 0 until pagesCount) v.goToPage(saved + 1)
            v.showHighlights(appHighlights)
        }
    }

    private fun handlePasswordRequest() {
        if (!triedStored) {
            triedStored = true
            if (key != null && prefs.value.rememberPasswords) {
                viewModelScope.launch {
                    val stored = prefsRepo.loadPassword(key)
                    if (stored != null) {
                        waitingOnStored = true
                        viewer?.submitPassword(stored)
                    } else {
                        _state.update { it.copy(phase = LoadPhase.PASSWORD, passwordWrong = false) }
                    }
                }
                return
            }
            _state.update { it.copy(phase = LoadPhase.PASSWORD, passwordWrong = false) }
            return
        }
        if (waitingOnStored) {
            // The remembered password no longer works (the file changed): forget it and ask again.
            waitingOnStored = false
            if (key != null) viewModelScope.launch { prefsRepo.forgetPassword(key) }
            _state.update { it.copy(phase = LoadPhase.PASSWORD, passwordWrong = false) }
            return
        }
        _state.update { it.copy(phase = LoadPhase.PASSWORD, passwordWrong = submittedPassword != null) }
    }

    fun submitPassword(password: String) {
        if (password.isEmpty()) return
        submittedPassword = password
        _state.update { it.copy(phase = LoadPhase.LOADING, passwordWrong = false) }
        viewer?.submitPassword(password)
    }

    // ---- taps ---------------------------------------------------------------------------------------------

    private fun handleSingleTap(x: Float, width: Float) {
        val s = state.value
        if (s.panel != null) { closePanel(); return }
        if (s.selection != null || s.tappedHighlight != null) {
            _state.update { it.copy(selection = null, tappedHighlight = null) }
            return
        }
        if (s.autoScrollActive) { toggleAutoScrollPause(); return }
        if (prefs.value.tapToTurn) {
            when (tapZone(x, width)) {
                TapZone.PREVIOUS -> { goToPage(stepPage(s.page, s.pagesCount, -1)); return }
                TapZone.NEXT -> { goToPage(stepPage(s.page, s.pagesCount, 1)); return }
                TapZone.CENTER -> Unit
            }
        }
        _state.update { it.copy(toolbarVisible = !it.toolbarVisible) }
    }

    private fun handleDoubleTap(x: Float, y: Float) {
        val v = viewer ?: return
        v.actualScaleFor(ZoomPreset.FIT_WIDTH) { fit ->
            v.zoomToAt(doubleTapTargetScale(v.scale, fit ?: 1f, prefs.value.doubleTapZoom), x, y)
        }
    }

    // ---- navigation & zoom ------------------------------------------------------------------------------

    fun goToPage(page: Int) { viewer?.goToPage(page) }
    fun jumpToPageText(text: String): Boolean {
        val p = parsePageInput(text, state.value.pagesCount) ?: return false
        goToPage(p)
        closePanel()
        return true
    }
    /** Moves the view to [ratio] (0..1) of the document; used by the fast-scroll handle while it is dragged. */
    fun scrollToRatio(ratio: Float) {
        ignoreScrollHideUntil = System.currentTimeMillis() + 1000
        viewer?.scrollToRatio(ratio, state.value.horizontalScroll)
    }
    fun zoomPreset(p: ZoomPreset) { viewer?.zoomPreset(p) }
    fun zoomTo(scale: Float) { viewer?.zoomTo(scale) }
    fun zoomIn() { viewer?.zoomIn() }
    fun zoomOut() { viewer?.zoomOut() }
    fun rotate() {
        val r = (state.value.rotation + 90) % 360
        _state.update { it.copy(rotation = r) }
        viewer?.setRotation(r)
    }

    private fun showScrubberBriefly() {
        _state.update { it.copy(scrubberVisible = true) }
        scrubberJob?.cancel()
        scrubberJob = viewModelScope.launch { delay(1600); _state.update { it.copy(scrubberVisible = false) } }
    }

    fun toggleToolbar() { _state.update { it.copy(toolbarVisible = !it.toolbarVisible) } }

    // ---- search --------------------------------------------------------------------------------------------

    fun openSearch() { _state.update { it.copy(searchOpen = true, toolbarVisible = true) } }
    fun closeSearch() {
        viewer?.stopFind()
        _state.update { it.copy(searchOpen = false, searchQuery = "", searchCurrent = 0, searchTotal = 0, searchPending = false, searchFound = true) }
    }
    fun search(query: String) {
        _state.update { it.copy(searchQuery = query) }
        if (query.isBlank()) {
            viewer?.stopFind()
            _state.update { it.copy(searchCurrent = 0, searchTotal = 0, searchFound = true, searchPending = false) }
        } else viewer?.find(query)
    }
    fun findNext() { viewer?.findNext() }
    fun findPrevious() { viewer?.findPrevious() }

    // ---- bookmarks ---------------------------------------------------------------------------------------------

    fun toggleBookmark() {
        val k = key ?: return
        val s = state.value
        val existing = s.bookmarks.firstOrNull { it.pageIndex == s.page - 1 }
        viewModelScope.launch {
            if (existing != null) readerData.deleteBookmark(existing.id)
            else readerData.addBookmark(Bookmark(UUID.randomUUID().toString(), k, s.page - 1, null, System.currentTimeMillis()))
        }
    }
    fun deleteBookmark(id: String) { viewModelScope.launch { readerData.deleteBookmark(id) } }

    // ---- highlights ----------------------------------------------------------------------------------------------

    private fun publishHighlights() {
        val merged = sortHighlights(appHighlights + documentHighlights)
        _state.update { cur ->
            val tapped = cur.tappedHighlight?.let { (h, a) -> merged.firstOrNull { it.id == h.id }?.let { it to a } }
            cur.copy(highlights = merged, tappedHighlight = tapped)
        }
        viewer?.showHighlights(appHighlights)
    }

    fun highlightSelection(color: Int) {
        val k = key ?: return
        val sel = state.value.selection ?: return
        _state.update { it.copy(selection = null, lastHighlightColor = color) }
        viewer?.clearSelection()
        viewModelScope.launch {
            highlightDao.insert(
                HighlightEntity(
                    documentKey = k, page = sel.page, text = sel.text, quadsJson = quadsToJson(sel.quads),
                    color = color, note = null, createdAt = System.currentTimeMillis(),
                ),
            )
        }
    }

    fun dismissSelection() {
        viewer?.clearSelection()
        _state.update { it.copy(selection = null, tappedHighlight = null) }
    }

    fun changeHighlightColor(id: Long, color: Int) { if (id > 0) viewModelScope.launch { highlightDao.setColor(id, color) } }
    fun setHighlightNote(id: Long, note: String) { if (id > 0) viewModelScope.launch { highlightDao.setNote(id, note.ifBlank { null }) } }
    fun deleteHighlight(id: Long) {
        if (id <= 0) return
        _state.update { it.copy(tappedHighlight = null) }
        viewModelScope.launch { highlightDao.delete(id) }
    }
    fun showHighlight(h: HighlightItem) {
        closePanel()
        val v = viewer ?: return
        v.goToPage(h.page)
        viewModelScope.launch {
            delay(350)
            if (h.fromDocument) h.quads.firstOrNull()?.let { v.scrollToQuad(h.page, it) } else v.scrollToHighlight(h.id)
        }
    }
    fun nextHighlight(forward: Boolean, from: Long?) { neighbourHighlight(state.value.highlights, from, forward)?.let { showHighlight(it) } }

    // ---- attachments -----------------------------------------------------------------------------------------------

    fun downloadAttachment(id: String) { viewer?.downloadAttachment(id) }

    // ---- auto scroll -------------------------------------------------------------------------------------------------

    fun startAutoScroll() {
        viewer?.startAutoScroll(prefs.value.autoScrollSpeed)
        _state.update { it.copy(autoScrollActive = true, autoScrollPaused = false, toolbarVisible = false, panel = null) }
    }
    fun stopAutoScroll() {
        viewer?.stopAutoScroll()
        _state.update { it.copy(autoScrollActive = false, autoScrollPaused = false) }
    }
    fun toggleAutoScrollPause() {
        val paused = !state.value.autoScrollPaused
        if (paused) viewer?.pauseAutoScroll() else viewer?.resumeAutoScroll()
        _state.update { it.copy(autoScrollPaused = paused) }
    }
    fun setAutoScrollSpeed(px: Int) {
        val v = px.coerceIn(MIN_AUTO_SCROLL_PX, MAX_AUTO_SCROLL_PX)
        viewer?.setAutoScrollSpeed(v)
        updatePrefs { it.autoScrollSpeed(v) }
    }

    // ---- doodle ------------------------------------------------------------------------------------------------------

    fun startDoodle() {
        val v = viewer ?: return
        if (state.value.autoScrollActive) stopAutoScroll()
        v.clearSelection()
        val s = state.value
        v.setDoodleColor(s.doodleColor)
        v.setDoodleSize(s.doodleSize)
        // Doodling always starts with the pen.
        v.setDoodleEraser(s.doodleEraser)
        v.setDoodleMode(true)
        _state.update { it.copy(doodleActive = true, doodleHand = false, panel = null, toolbarVisible = false, selection = null, tappedHighlight = null, searchOpen = false) }
    }

    /** Leaves doodle mode; with unsaved strokes it asks first unless [force] is set. */
    fun exitDoodle(force: Boolean = false) {
        val s = state.value
        if (!force && s.doodleStrokes > 0 && !s.doodleSaving) {
            _state.update { it.copy(doodleConfirmDiscard = true) }
            return
        }
        viewer?.setDoodleMode(false)
        viewer?.clearDoodles()
        _state.update { it.copy(doodleActive = false, doodleConfirmDiscard = false, doodleStrokes = 0, doodleUndoSteps = 0, doodleSaving = false, toolbarVisible = true) }
    }

    fun dismissDiscardPrompt() { _state.update { it.copy(doodleConfirmDiscard = false) } }

    fun setDoodleEraser(eraser: Boolean) {
        viewer?.setDoodleEraser(eraser)
        _state.update { it.copy(doodleEraser = eraser, doodleHand = false) }
    }

    /** Move tool: the page scrolls and zooms as usual and nothing is drawn. */
    fun setDoodleHand() {
        viewer?.setDoodleHand()
        _state.update { it.copy(doodleHand = true) }
    }

    fun setDoodleColor(argb: Int) {
        viewer?.setDoodleColor(argb)
        viewer?.setDoodleEraser(false)
        _state.update { it.copy(doodleColor = argb, doodleEraser = false, doodleHand = false) }
    }

    fun setDoodleSize(size: DoodleSize) {
        viewer?.setDoodleSize(size)
        _state.update { it.copy(doodleSize = size) }
    }

    fun doodleUndo() { viewer?.doodleUndo() }

    fun saveDoodle() {
        if (state.value.doodleStrokes == 0 || state.value.doodleSaving) return
        _state.update { it.copy(doodleSaving = true) }
        viewer?.saveDoodled()
    }

    /** Puts the finished copy next to the original (never over it); falls back to a "save as" picker. */
    private fun finishDoodleSave(temp: java.io.File) {
        val u = uri
        val pdf = if (u?.scheme == "file") java.io.File(u.path.orEmpty()) else null
        // A Word/Excel/PowerPoint file is viewed as a converted copy in the cache; the doodle belongs beside the original.
        val origin = pdf?.parentFile?.let { java.io.File(it, com.kosh.android.tools.OfficePdfConverter.ORIGIN_FILE) }
            ?.takeIf { it.isFile }?.let { runCatching { java.io.File(it.readText().trim()) }.getOrNull() }
        val base = (origin ?: pdf)?.nameWithoutExtension ?: state.value.title
        viewModelScope.launch {
            val dir = (origin ?: pdf)?.parentFile?.takeIf { origin != null || pdf != null }
            val saved = if (dir != null && dir.canWrite()) {
                withContext(Dispatchers.IO) {
                    runCatching {
                        val target = java.io.File(dir, doodleFileName(base) { java.io.File(dir, it).exists() })
                        temp.copyTo(target, overwrite = false)
                        runCatching {
                            android.media.MediaScannerConnection.scanFile(appContext, arrayOf(target.absolutePath), arrayOf("application/pdf"), null)
                        }
                        target
                    }.getOrNull()
                }
            } else null
            if (saved != null) {
                temp.delete()
                eventChannel.trySend(ViewerEvent.DoodleSaved(Uri.fromFile(saved).toString(), saved.name))
                exitDoodle(force = true)
            } else {
                eventChannel.trySend(ViewerEvent.DoodleSaveAs(temp.absolutePath, doodleFileName(base) { false }))
                _state.update { it.copy(doodleSaving = false) }
            }
        }
    }

    /** Called after the user chose a destination in the "save as" picker (or cancelled). */
    fun finishSaveAs(tempPath: String, destination: Uri?) {
        val temp = java.io.File(tempPath)
        if (destination == null) { _state.update { it.copy(doodleSaving = false) }; return }
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    appContext.contentResolver.openOutputStream(destination)?.use { out -> temp.inputStream().use { it.copyTo(out) } } != null
                }.getOrDefault(false)
            }
            if (ok) {
                temp.delete()
                eventChannel.trySend(ViewerEvent.Message(R.string.viewer_doodle_saved_picked))
                exitDoodle(force = true)
            } else {
                eventChannel.trySend(ViewerEvent.Message(R.string.viewer_doodle_save_failed))
            }
        }
    }

    // ---- panels & prefs --------------------------------------------------------------------------------------------

    fun openPanel(p: ViewerPanel) { _state.update { it.copy(panel = p) } }
    fun closePanel() { _state.update { it.copy(panel = null) } }

    fun updatePrefs(block: (ViewerPreferencesRepository.MutablePrefs) -> Unit) {
        viewModelScope.launch { prefsRepo.update(block) }
    }

    fun postMessage(@StringRes res: Int) { eventChannel.trySend(ViewerEvent.Message(res)) }

    override fun onCleared() {
        // The page is saved after a short delay while reading; make sure leaving quickly doesn't lose it.
        if (key != null && state.value.phase == LoadPhase.READY) progressRepo.save(key, state.value.page - 1)
        zoomSaveJob?.cancel()
        super.onCleared()
    }

    // ---- helpers -------------------------------------------------------------------------------------------------------

    private fun HighlightEntity.toItem() = HighlightItem(
        id = id, page = page, text = text, quads = parseQuads(runCatching { JSONArray(quadsJson) }.getOrNull()),
        colorArgb = color, note = note, label = null, fromDocument = false,
    )

    private fun displayName(u: Uri): String {
        val raw = when (u.scheme) {
            "content" -> runCatching {
                appContext.contentResolver.query(u, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                    if (c.moveToFirst()) c.getString(0) else null
                }
            }.getOrNull()
            else -> u.lastPathSegment
        } ?: u.lastPathSegment ?: ""
        return raw.removeSuffix(".pdf").removeSuffix(".PDF")
    }
}
