package com.kosh.android.notes.ui

import android.net.Uri
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kosh.android.notes.data.NoteImageImporter
import com.kosh.android.notes.data.NoteImageStore
import com.kosh.android.notes.data.NoteRepository
import com.kosh.android.notes.export.PdfNoteWriter
import com.kosh.android.util.suggestedExportName
import com.kosh.android.notes.export.NoteExportFormat
import com.kosh.android.notes.export.NoteExporter
import com.kosh.android.notes.export.NoteSnapshot
import com.kosh.android.notes.format.FormatOp
import com.kosh.android.notes.format.NoteImageTokens
import com.kosh.android.notes.format.NoteFormatting
import com.kosh.android.notes.format.TextState
import com.kosh.android.notes.format.UndoStack
import com.kosh.android.notes.format.toggleChecklistLine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.withContext
import java.io.File
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

enum class SaveStatus { IDLE, SAVING, SAVED, FAILED }

data class EditorUiState(
    val loaded: Boolean = false,
    val title: String = "",
    val body: TextFieldValue = TextFieldValue(""),
    val saveStatus: SaveStatus = SaveStatus.IDLE,
    val isPinned: Boolean = false,
    val isArchived: Boolean = false,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    /** Image files that belong to the note. Where each one shows is decided by the image lines in [body]. */
    val images: List<File> = emptyList(),
    /** Goes up when the caret should be given back to the text (after a picture is inserted). */
    val focusTick: Int = 0,
    /**
     * Goes up whenever the text was changed by something other than the person typing (undo, a toolbar action, a picture,
     * an automatic list bullet). While typing the text field keeps its own copy, so a slow round trip through here can never
     * overwrite letters that were just typed; it only takes this copy when the number changes.
     */
    val bodyRevision: Int = 0,
)

sealed interface EditorEvent {
    data class Exported(val format: NoteExportFormat) : EditorEvent
    data class ExportFailed(val format: NoteExportFormat) : EditorEvent
    data object Duplicated : EditorEvent
    data object Trashed : EditorEvent
    data object ImagesFailed : EditorEvent
    data class ShareReady(val file: File) : EditorEvent
    data object ShareFailed : EditorEvent
}

@OptIn(FlowPreview::class)
class NoteEditorViewModel(
    private val repo: NoteRepository,
    private val exporter: NoteExporter,
    private val imageStore: NoteImageStore,
    private val imageImporter: NoteImageImporter,
    private val shareDir: File,
    handle: SavedStateHandle,
) : ViewModel() {

    /** The note's id. For "new" notes a UUID is generated once and kept across process death. */
    val noteId: String = run {
        val routeId = handle.get<String>("id") ?: NEW
        if (routeId != NEW) routeId
        else handle.get<String>("generatedId") ?: UUID.randomUUID().toString().also { handle["generatedId"] = it }
    }

    private val _state = MutableStateFlow(EditorUiState())
    val state: StateFlow<EditorUiState> = _state.asStateFlow()

    private val _events = Channel<EditorEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private val undoStack = UndoStack()
    private val dirty = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    // What is stored in the database right now (null = the note hasn't been created yet).
    private var savedTitle: String? = null
    private var savedContent: String? = null

    private var pendingExport: NoteSnapshot? = null
    private var pendingFormat: NoteExportFormat? = null
    private var lastExportUri: Uri? = null

    init {
        viewModelScope.launch {
            val existing = repo.get(noteId)
            val attached = withContext(Dispatchers.IO) { imageStore.list(noteId) }
            if (existing != null) {
                savedTitle = existing.title
                savedContent = existing.content
                // Pictures added before they could sit inside the text go at the top, where they were shown, and
                // from then on are ordinary lines of the note.
                val content = NoteImageTokens.adoptUnreferenced(existing.content, attached.map { it.name })
                _state.value = EditorUiState(
                    loaded = true,
                    title = existing.title,
                    body = TextFieldValue(content, TextRange(content.length)),
                    isPinned = existing.isPinned,
                    isArchived = existing.isArchived,
                    images = attached,
                )
                if (content != existing.content) dirty.tryEmit(Unit)
            } else {
                val content = NoteImageTokens.adoptUnreferenced("", attached.map { it.name })
                _state.value = EditorUiState(loaded = true, images = attached, body = TextFieldValue(content, TextRange(content.length)))
            }
        }
        viewModelScope.launch {
            dirty.debounce(AUTOSAVE_DELAY_MS).collect { persist() }
        }
    }

    // ---- editing ------------------------------------------------------------------------------

    fun onTitleChange(title: String) {
        if (title == _state.value.title) return
        _state.update { it.copy(title = title.replace("\n", " "), saveStatus = SaveStatus.SAVING) }
        dirty.tryEmit(Unit)
    }

    fun onBodyChange(new: TextFieldValue) {
        val old = _state.value.body
        if (new.text == old.text) { // selection-only change
            _state.update { it.copy(body = new) }
            return
        }
        val oldState = old.toTextState()
        val newState = new.toTextState()
        undoStack.record(oldState, System.currentTimeMillis(), force = false)
        val continued = NoteFormatting.continueList(oldState, newState)
        if (continued != null) {
            setBody(continued, SaveStatus.SAVING)
            return
        }
        // Plain typing: keep the keyboard's value exactly as it came, including its "composing" word, which is what makes
        // autocorrect and tapping a suggestion work.
        _state.update { it.copy(body = new, saveStatus = SaveStatus.SAVING, canUndo = undoStack.canUndo, canRedo = undoStack.canRedo) }
        dirty.tryEmit(Unit)
    }

    fun applyFormat(op: FormatOp) {
        val current = _state.value.body.toTextState()
        val result = NoteFormatting.apply(op, current)
        if (result == current) return
        undoStack.record(current, System.currentTimeMillis(), force = true)
        setBody(result, SaveStatus.SAVING)
    }

    fun toggleChecklist(lineIndex: Int) {
        val current = _state.value.body.toTextState()
        val text = toggleChecklistLine(current.text, lineIndex)
        if (text == current.text) return
        undoStack.record(current, System.currentTimeMillis(), force = true)
        setBody(current.copy(text = text), SaveStatus.SAVING)
    }

    fun undo() {
        val previous = undoStack.undo(_state.value.body.toTextState()) ?: return
        setBody(previous, SaveStatus.SAVING)
    }

    fun redo() {
        val next = undoStack.redo(_state.value.body.toTextState()) ?: return
        setBody(next, SaveStatus.SAVING)
    }

    private fun setBody(t: TextState, status: SaveStatus) {
        _state.update {
            it.copy(
                body = TextFieldValue(t.text, TextRange(t.selStart, t.selEnd)),
                saveStatus = status,
                canUndo = undoStack.canUndo,
                canRedo = undoStack.canRedo,
                bodyRevision = it.bodyRevision + 1,
            )
        }
        dirty.tryEmit(Unit)
    }

    // ---- saving -------------------------------------------------------------------------------

    private fun hasUnsavedChanges(): Boolean {
        val s = _state.value
        if (!s.loaded) return false
        // Never create an empty note: a note with only images still counts as having content.
        if (savedTitle == null && s.title.isBlank() && s.body.text.isBlank() && s.images.isEmpty()) return false
        return savedTitle == null || s.title != savedTitle || s.body.text != savedContent
    }

    private suspend fun persist() {
        if (!hasUnsavedChanges()) {
            _state.update { if (it.saveStatus == SaveStatus.SAVING) it.copy(saveStatus = SaveStatus.SAVED.takeIf { savedTitle != null } ?: SaveStatus.IDLE) else it }
            return
        }
        val title = _state.value.title
        val content = _state.value.body.text
        _state.update { it.copy(saveStatus = SaveStatus.SAVING) }
        val result = repo.enqueueSave(noteId, title, content).await()
        if (result.isSuccess) {
            savedTitle = title
            savedContent = content
            _state.update {
                val stillCurrent = it.title == title && it.body.text == content
                it.copy(saveStatus = if (stillCurrent) SaveStatus.SAVED else it.saveStatus)
            }
        } else {
            _state.update { it.copy(saveStatus = SaveStatus.FAILED) }
        }
    }

    /** Saves right away (screen stopped, user pressed Retry). */
    fun flush() {
        viewModelScope.launch { persist() }
    }

    override fun onCleared() {
        // The screen is gone: hand the latest text to the repository, which writes it even without us.
        if (hasUnsavedChanges()) {
            val s = _state.value
            repo.enqueueSave(noteId, s.title, s.body.text)
        }
    }

    // ---- note actions -------------------------------------------------------------------------

    /** Makes sure the note exists in the database. False if it is a brand-new, still-empty note. */
    private suspend fun ensureExists(): Boolean {
        persist()
        return savedTitle != null
    }

    fun togglePin() {
        viewModelScope.launch {
            if (!ensureExists()) return@launch
            val pinned = !_state.value.isPinned
            repo.setPinned(noteId, pinned)
            _state.update { it.copy(isPinned = pinned) }
        }
    }

    fun toggleArchive() {
        viewModelScope.launch {
            if (!ensureExists()) return@launch
            val archived = !_state.value.isArchived
            repo.setArchived(noteId, archived)
            _state.update { it.copy(isArchived = archived) }
        }
    }

    fun moveToTrash() {
        viewModelScope.launch {
            if (ensureExists()) repo.moveToTrash(noteId)
            _events.send(EditorEvent.Trashed)
        }
    }

    fun duplicate(copySuffix: String) {
        viewModelScope.launch {
            if (!ensureExists()) return@launch
            val newId = repo.duplicate(noteId, copySuffix) ?: return@launch
            withContext(Dispatchers.IO) { imageStore.copyAll(noteId, newId) }
            _events.send(EditorEvent.Duplicated)
        }
    }

    // ---- images -------------------------------------------------------------------------------

    /** Copies the picked images into the note. Each one is shrunk to a compact JPEG. */
    fun addImages(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val (added, failed) = withContext(Dispatchers.IO) {
                val ok = mutableListOf<File>()
                var bad = 0
                for (uri in uris) {
                    val target = imageStore.newFile(noteId)
                    if (imageImporter.import(uri, target)) ok += target else bad++
                }
                ok to bad
            }
            if (added.isNotEmpty()) {
                insertImages(added)
                flush()
            }
            if (failed > 0) _events.send(EditorEvent.ImagesFailed)
        }
    }

    /** Puts each new picture on its own line at the caret, so it sits exactly where the user was typing. */
    private fun insertImages(files: List<File>) {
        val current = _state.value.body
        val before = current.toTextState()
        var text = current.text
        var caret = maxOf(current.selection.start, current.selection.end).coerceIn(0, text.length)
        for (file in files) {
            val (next, nextCaret) = NoteImageTokens.insertAt(text, caret, file.name)
            text = next
            caret = nextCaret
        }
        undoStack.record(before, System.currentTimeMillis(), force = true)
        _state.update { it.copy(images = it.images + files, focusTick = it.focusTick + 1) }
        setBody(TextState(text, caret, caret), SaveStatus.SAVING)
    }

    /** Removes the picture from the text and deletes its file. */
    fun removeImage(name: String) {
        viewModelScope.launch {
            val file = _state.value.images.firstOrNull { it.name == name }
            if (file != null) withContext(Dispatchers.IO) { imageStore.remove(file) }
            val current = _state.value.body
            val text = NoteImageTokens.removeImage(current.text, name)
            val caret = current.selection.start.coerceAtMost(text.length)
            undoStack.record(current.toTextState(), System.currentTimeMillis(), force = true)
            _state.update { it.copy(images = it.images.filter { f -> f.name != name }) }
            setBody(TextState(text, caret, caret), SaveStatus.SAVING)
        }
    }

    /** Builds a PDF (text and images) in the app's cache and hands it to the share sheet. */
    fun sharePdf() {
        val s = _state.value
        viewModelScope.launch {
            val file = withContext(Dispatchers.IO) {
                runCatching {
                    shareDir.mkdirs()
                    shareDir.listFiles()?.forEach { it.delete() } // keep only the latest shared copy
                    val target = File(shareDir, suggestedExportName(s.title, "pdf"))
                    target.outputStream().use { PdfNoteWriter().write(s.title, s.body.text, s.images, it) }
                    target
                }.getOrNull()
            }
            _events.send(if (file != null) EditorEvent.ShareReady(file) else EditorEvent.ShareFailed)
        }
    }

    // ---- export -------------------------------------------------------------------------------

    /** Takes the snapshot that will be exported; later edits don't affect it. */
    fun beginExport(format: NoteExportFormat) {
        val s = _state.value
        pendingExport = NoteSnapshot(s.title, s.body.text, s.images)
        pendingFormat = format
        lastExportUri = null
    }

    /** Called with the picker result. A null [uri] means the user cancelled, which is not an error. */
    fun finishExport(uri: Uri?) {
        val snapshot = pendingExport
        val format = pendingFormat
        if (uri == null || snapshot == null || format == null) {
            pendingExport = null
            pendingFormat = null
            return
        }
        lastExportUri = uri
        runExport(snapshot, format, uri)
    }

    fun retryExport() {
        val snapshot = pendingExport ?: return
        val format = pendingFormat ?: return
        val uri = lastExportUri ?: return
        runExport(snapshot, format, uri)
    }

    private fun runExport(snapshot: NoteSnapshot, format: NoteExportFormat, uri: Uri) {
        viewModelScope.launch {
            val result = exporter.export(snapshot, format, uri)
            if (result.isSuccess) {
                _events.send(EditorEvent.Exported(format))
                pendingExport = null
                pendingFormat = null
            } else {
                _events.send(EditorEvent.ExportFailed(format))
            }
        }
    }

    private fun TextFieldValue.toTextState() = TextState(text, selection.start, selection.end)

    companion object {
        const val NEW = "new"
        private const val AUTOSAVE_DELAY_MS = 600L
    }
}
