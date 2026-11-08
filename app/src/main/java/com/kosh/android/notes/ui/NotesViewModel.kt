package com.kosh.android.notes.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kosh.android.notes.data.Note
import com.kosh.android.notes.data.NoteFolder
import com.kosh.android.notes.data.NoteImageStore
import com.kosh.android.notes.data.NoteRepository
import com.kosh.android.notes.data.NoteSort
import com.kosh.android.notes.data.NotesPreferencesRepository
import com.kosh.android.notes.data.NotesPrefs
import com.kosh.android.notes.data.NotesShow
import com.kosh.android.notes.data.NotesView
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class NotesUiState(
    val loading: Boolean = true,
    val view: NotesView = NotesView.ACTIVE,
    val folders: List<FolderItem> = emptyList(),
    /** The folder being looked into, or null in the main list. */
    val currentFolder: NoteFolder? = null,
    val pinned: List<Note> = emptyList(),
    val notes: List<Note> = emptyList(),
    /** Ids of the notes picked with a long press. Only notes that are on screen stay picked. */
    val selected: Set<String> = emptySet(),
    val prefs: NotesPrefs = NotesPrefs(),
    val searching: Boolean = false,
    /** Every folder, for the "move to folder" list. */
    val allFolders: List<NoteFolder> = emptyList(),
) {
    val isEmpty: Boolean get() = pinned.isEmpty() && notes.isEmpty() && folders.isEmpty()
    val selecting: Boolean get() = selected.isNotEmpty()
    val visibleIds: List<String> get() = (pinned + notes).map { it.id }
}

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class NotesViewModel(
    private val repo: NoteRepository,
    private val prefsRepo: NotesPreferencesRepository,
    private val imageStore: NoteImageStore,
) : ViewModel() {

    private val view = MutableStateFlow(NotesView.ACTIVE)
    private val openFolderId = MutableStateFlow<String?>(null)
    private val picked = MutableStateFlow<Set<String>>(emptySet())
    private val _query = MutableStateFlow("")
    private val _searching = MutableStateFlow(false)

    /** Text shown in the search field (updates immediately; filtering is debounced below). */
    val query: StateFlow<String> = _query.asStateFlow()

    private class Core(
        val view: NotesView,
        val listing: NotesListing,
        val folder: NoteFolder?,
        val allFolders: List<NoteFolder>,
        val prefs: NotesPrefs,
    )

    private val core = combine(
        view.flatMapLatest { v -> repo.observe(v).map { v to it } },
        repo.observeFolders(),
        _query.debounce(150),
        prefsRepo.prefs,
        openFolderId,
    ) { (v, notes), folders, q, prefs, openId ->
        val open = folders.firstOrNull { it.id == openId }
        Core(v, buildListing(v, notes, folders, open?.id, q, prefs), open, folders.sortedBy { it.name.lowercase() }, prefs)
    }

    val state: StateFlow<NotesUiState> = combine(core, _searching, picked) { c, searching, pickedIds ->
        val visible = (c.listing.pinned + c.listing.notes).mapTo(HashSet()) { it.id }
        NotesUiState(
            loading = false,
            view = c.view,
            folders = c.listing.folders,
            currentFolder = c.folder,
            pinned = c.listing.pinned,
            notes = c.listing.notes,
            selected = pickedIds.filterTo(HashSet()) { it in visible },
            prefs = c.prefs,
            searching = searching,
            allFolders = c.allFolders,
        )
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NotesUiState())

    // ---- where we are ----------------------------------------------------------------------------------------------------

    fun setView(v: NotesView) {
        view.value = v
        openFolderId.value = null
        picked.value = emptySet()
        stopSearch()
    }

    fun openFolder(id: String) {
        openFolderId.value = id
        picked.value = emptySet()
        stopSearch()
    }

    fun closeFolder() {
        openFolderId.value = null
        picked.value = emptySet()
    }

    fun startSearch() { _searching.value = true }

    fun stopSearch() {
        _searching.value = false
        _query.value = ""
    }

    fun setQuery(q: String) { _query.value = q }

    fun setSort(sort: NoteSort, ascending: Boolean) {
        viewModelScope.launch { prefsRepo.setSort(sort, ascending) }
    }

    fun setShow(show: NotesShow) {
        viewModelScope.launch { prefsRepo.setShow(show) }
    }

    fun setGrid(grid: Boolean) {
        viewModelScope.launch { prefsRepo.setGrid(grid) }
    }

    // ---- picking several notes ------------------------------------------------------------------------------------------

    fun toggleSelected(id: String) {
        picked.value = picked.value.let { if (id in it) it - id else it + id }
    }

    fun selectAll() { picked.value = state.value.visibleIds.toSet() }

    fun clearSelection() { picked.value = emptySet() }

    private fun selectedIds(): List<String> = state.value.selected.toList()

    /** Moves the picked notes to the trash; returns their ids so the screen can offer Undo. */
    fun trashSelected(): List<String> {
        val ids = selectedIds()
        clearSelection()
        viewModelScope.launch { repo.moveToTrashMany(ids) }
        return ids
    }

    fun restoreIds(ids: List<String>) { viewModelScope.launch { repo.restoreMany(ids) } }

    fun restoreSelected() {
        val ids = selectedIds()
        clearSelection()
        viewModelScope.launch { repo.restoreMany(ids) }
    }

    fun unarchiveSelected() {
        val ids = selectedIds()
        clearSelection()
        viewModelScope.launch { repo.setArchivedMany(ids, false) }
    }

    fun deleteSelectedPermanently() {
        val ids = selectedIds()
        clearSelection()
        deleteIdsPermanently(ids)
    }

    private fun deleteIdsPermanently(ids: List<String>) {
        viewModelScope.launch {
            repo.deleteManyPermanently(ids)
            withContext(Dispatchers.IO) { ids.forEach { imageStore.deleteAll(it) } }
        }
    }

    // ---- folders ----------------------------------------------------------------------------------------------------------

    fun createFolder(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch { repo.createFolder(name) }
    }

    /** Creates a folder and files [ids] in it. */
    fun createFolderAndMove(name: String, ids: List<String>) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val folderId = repo.createFolder(name)
            repo.moveToFolder(ids, folderId)
        }
        clearSelection()
    }

    fun renameFolder(id: String, name: String) {
        if (name.isBlank()) return
        viewModelScope.launch { repo.renameFolder(id, name) }
    }

    fun deleteFolder(id: String) {
        if (openFolderId.value == id) openFolderId.value = null
        viewModelScope.launch { repo.deleteFolderWithNotes(id) }
    }

    fun setFolderColor(id: String, color: Int?) {
        viewModelScope.launch { repo.setFolderColor(id, color) }
    }

    /** Files [ids] in [folderId], or takes them out of their folder when it is null. */
    fun moveToFolder(ids: List<String>, folderId: String?) {
        viewModelScope.launch { repo.moveToFolder(ids, folderId) }
        clearSelection()
    }

    // ---- single notes ------------------------------------------------------------------------------------------------------

    fun togglePin(note: Note) {
        viewModelScope.launch { repo.setPinned(note.id, !note.isPinned) }
    }

    fun setArchived(note: Note, archived: Boolean) {
        viewModelScope.launch { repo.setArchived(note.id, archived) }
    }

    fun moveToTrash(note: Note) {
        viewModelScope.launch { repo.moveToTrash(note.id) }
    }

    fun restore(note: Note) {
        viewModelScope.launch { repo.restore(note.id) }
    }

    fun deletePermanently(note: Note) { deleteIdsPermanently(listOf(note.id)) }

    fun duplicate(note: Note, copySuffix: String) {
        viewModelScope.launch {
            val newId = repo.duplicate(note.id, copySuffix) ?: return@launch
            withContext(Dispatchers.IO) { imageStore.copyAll(note.id, newId) }
        }
    }
}
