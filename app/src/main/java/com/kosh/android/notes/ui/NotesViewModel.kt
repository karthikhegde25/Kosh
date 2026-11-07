package com.kosh.android.notes.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kosh.android.notes.data.Note
import com.kosh.android.notes.data.NoteImageStore
import com.kosh.android.notes.data.NoteRepository
import com.kosh.android.notes.data.NoteSort
import com.kosh.android.notes.data.NotesPreferencesRepository
import com.kosh.android.notes.data.NotesPrefs
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
    val pinned: List<Note> = emptyList(),
    val notes: List<Note> = emptyList(),
    val prefs: NotesPrefs = NotesPrefs(),
    val searching: Boolean = false,
) {
    val isEmpty: Boolean get() = pinned.isEmpty() && notes.isEmpty()
}

/** Filters by query (title and body, case-insensitive) and sorts. Pure, so it is unit-tested. */
fun filterAndSort(notes: List<Note>, query: String, prefs: NotesPrefs): List<Note> {
    val q = query.trim()
    val filtered = if (q.isEmpty()) notes else notes.filter {
        it.title.contains(q, ignoreCase = true) || it.content.contains(q, ignoreCase = true)
    }
    val base: Comparator<Note> = when (prefs.sort) {
        NoteSort.UPDATED -> compareBy { it.updatedAt }
        NoteSort.CREATED -> compareBy { it.createdAt }
        NoteSort.TITLE -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.title }
    }
    return filtered.sortedWith(if (prefs.ascending) base else base.reversed())
}

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class NotesViewModel(
    private val repo: NoteRepository,
    private val prefsRepo: NotesPreferencesRepository,
    private val imageStore: NoteImageStore,
) : ViewModel() {

    private val view = MutableStateFlow(NotesView.ACTIVE)
    private val _query = MutableStateFlow("")
    private val _searching = MutableStateFlow(false)

    /** Text shown in the search field (updates immediately; filtering is debounced below). */
    val query: StateFlow<String> = _query.asStateFlow()

    val state: StateFlow<NotesUiState> = combine(
        view.flatMapLatest { v -> repo.observe(v).map { v to it } },
        _query.debounce(150),
        prefsRepo.prefs,
        _searching,
    ) { (v, notes), q, prefs, searching ->
        val sorted = filterAndSort(notes, q, prefs)
        val (pinned, others) = if (v == NotesView.ACTIVE) sorted.partition { it.isPinned } else emptyList<Note>() to sorted
        NotesUiState(loading = false, view = v, pinned = pinned, notes = others, prefs = prefs, searching = searching)
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NotesUiState())

    fun setView(v: NotesView) {
        view.value = v
        stopSearch()
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

    fun setGrid(grid: Boolean) {
        viewModelScope.launch { prefsRepo.setGrid(grid) }
    }

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

    fun deletePermanently(note: Note) {
        viewModelScope.launch {
            repo.deletePermanently(note.id)
            withContext(Dispatchers.IO) { imageStore.deleteAll(note.id) }
        }
    }

    fun duplicate(note: Note, copySuffix: String) {
        viewModelScope.launch {
            val newId = repo.duplicate(note.id, copySuffix) ?: return@launch
            withContext(Dispatchers.IO) { imageStore.copyAll(note.id, newId) }
        }
    }
}
