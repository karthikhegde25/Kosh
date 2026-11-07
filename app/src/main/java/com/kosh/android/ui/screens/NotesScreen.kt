package com.kosh.android.ui.screens

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material3.OutlinedTextField
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.layout.PaddingValues
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.background
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kosh.android.KoshViewModelFactory
import com.kosh.android.R
import com.kosh.android.notes.data.Note
import com.kosh.android.notes.data.NoteSort
import com.kosh.android.notes.data.NotesView
import com.kosh.android.notes.format.NoteFormatConverter
import com.kosh.android.notes.ui.NotesViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesScreen(
    onOpenNote: (String) -> Unit,
    onNewNote: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val vm: NotesViewModel = viewModel(factory = KoshViewModelFactory.Factory)
    val state by vm.state.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val gridState = rememberLazyGridState()
    var menuOpen by remember { mutableStateOf(false) }
    var showSort by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<Note?>(null) }
    val searchFocus = remember { FocusRequester() }

    val inMainView = state.view == NotesView.ACTIVE
    // Back from Archive or Trash goes to the notes list instead of leaving the app.
    BackHandler(enabled = !inMainView) { vm.setView(NotesView.ACTIVE) }
    val title = when (state.view) {
        NotesView.ACTIVE -> R.string.notes_title
        NotesView.ARCHIVE -> R.string.notes_archive
        NotesView.TRASH -> R.string.notes_trash
    }

    // The app-wide top bar is shown by the main screen. This tab only has its own tools: search, sort, view, lists.
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        floatingActionButton = {
            if (inMainView) {
                FloatingActionButton(onClick = onNewNote) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.notes_new))
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            NotesToolbar(
                inMainView = inMainView,
                titleRes = title,
                query = query,
                onQuery = vm::setQuery,
                grid = state.prefs.grid,
                onToggleGrid = { vm.setGrid(!state.prefs.grid) },
                onSort = { showSort = true },
                onArchive = { vm.setView(NotesView.ARCHIVE) },
                onTrash = { vm.setView(NotesView.TRASH) },
                onBackToMain = { vm.setView(NotesView.ACTIVE) },
            )
            when {
                state.loading -> Unit
                state.isEmpty -> EmptyNotes(state.view, query, onNewNote)
                else -> LazyVerticalGrid(
                    state = gridState,
                    columns = if (state.prefs.grid) GridCells.Adaptive(170.dp) else GridCells.Fixed(1),
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 88.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    if (state.pinned.isNotEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }, key = "header-pinned") {
                            SectionHeader(stringResource(R.string.notes_section_pinned))
                        }
                        items(state.pinned, key = { it.id }) { note ->
                            NoteCard(note, state.view, onClick = {
                                handleOpen(note, state.view, onOpenNote) {
                                    scope.launch { snackbar.showSnackbar(context.getString(R.string.note_trashed_open)) }
                                }
                            }, actions = { actionsFor(vm, it, state.view, context, scope, snackbar) { n -> deleteTarget = n } })
                        }
                        if (state.notes.isNotEmpty()) {
                            item(span = { GridItemSpan(maxLineSpan) }, key = "header-others") {
                                SectionHeader(stringResource(R.string.notes_section_others))
                            }
                        }
                    }
                    items(state.notes, key = { it.id }) { note ->
                        NoteCard(note, state.view, onClick = {
                            handleOpen(note, state.view, onOpenNote) {
                                scope.launch { snackbar.showSnackbar(context.getString(R.string.note_trashed_open)) }
                            }
                        }, actions = { actionsFor(vm, it, state.view, context, scope, snackbar) { n -> deleteTarget = n } })
                    }
                }
            }
        }
    }

    if (showSort) {
        AlertDialog(
            onDismissRequest = { showSort = false },
            title = { Text(stringResource(R.string.notes_sort)) },
            text = {
                Column(Modifier.selectableGroup()) {
                    SortOption(R.string.notes_sort_updated, NoteSort.UPDATED, state.prefs.sort) { vm.setSort(it, state.prefs.ascending) }
                    SortOption(R.string.notes_sort_created, NoteSort.CREATED, state.prefs.sort) { vm.setSort(it, state.prefs.ascending) }
                    SortOption(R.string.notes_sort_title, NoteSort.TITLE, state.prefs.sort) { vm.setSort(it, state.prefs.ascending) }
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(stringResource(R.string.notes_sort_ascending))
                        Switch(
                            checked = state.prefs.ascending,
                            onCheckedChange = { vm.setSort(state.prefs.sort, it) },
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showSort = false }) { Text(stringResource(R.string.notes_sort_done)) } },
        )
    }

    deleteTarget?.let { note ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.note_delete_forever_title)) },
            text = { Text(stringResource(R.string.note_delete_forever_body)) },
            confirmButton = {
                TextButton(onClick = { vm.deletePermanently(note); deleteTarget = null }) {
                    Text(stringResource(R.string.note_delete_forever), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

private fun handleOpen(note: Note, view: NotesView, onOpen: (String) -> Unit, onTrashed: () -> Unit) {
    if (view == NotesView.TRASH) onTrashed() else onOpen(note.id)
}

/** The actions in a card's overflow menu, depending on which list is showing. */
private data class NoteAction(val labelRes: Int, val destructive: Boolean = false, val run: () -> Unit)

private fun actionsFor(
    vm: NotesViewModel,
    note: Note,
    view: NotesView,
    context: android.content.Context,
    scope: kotlinx.coroutines.CoroutineScope,
    snackbar: SnackbarHostState,
    askDelete: (Note) -> Unit,
): List<NoteAction> = when (view) {
    NotesView.ACTIVE -> listOf(
        NoteAction(if (note.isPinned) R.string.note_unpin else R.string.note_pin) { vm.togglePin(note) },
        NoteAction(R.string.note_archive) { vm.setArchived(note, true) },
        NoteAction(R.string.note_duplicate) { vm.duplicate(note, context.getString(R.string.note_copy_suffix)) },
        NoteAction(R.string.note_move_to_trash, destructive = true) {
            vm.moveToTrash(note)
            scope.launch {
                val result = snackbar.showSnackbar(
                    message = context.getString(R.string.note_trashed),
                    actionLabel = context.getString(R.string.note_undo),
                )
                if (result == SnackbarResult.ActionPerformed) vm.restore(note)
            }
        },
    )
    NotesView.ARCHIVE -> listOf(
        NoteAction(R.string.note_unarchive) { vm.setArchived(note, false) },
        NoteAction(R.string.note_move_to_trash, destructive = true) { vm.moveToTrash(note) },
    )
    NotesView.TRASH -> listOf(
        NoteAction(R.string.note_restore) { vm.restore(note) },
        NoteAction(R.string.note_delete_forever, destructive = true) { askDelete(note) },
    )
}

@Composable
private fun NoteCard(note: Note, view: NotesView, onClick: () -> Unit, actions: (Note) -> List<NoteAction>) {
    var menuOpen by remember { mutableStateOf(false) }
    val titleText = note.title.ifBlank { stringResource(R.string.notes_untitled) }
    val preview = remember(note.content) { NoteFormatConverter.preview(note.content) }
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp)) {
            Column(Modifier.weight(1f).padding(top = 4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (note.isPinned && view == NotesView.ACTIVE) {
                        Icon(
                            Icons.Default.PushPin,
                            contentDescription = stringResource(R.string.note_pinned_indicator),
                            modifier = Modifier.padding(end = 6.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Text(
                        titleText,
                        style = MaterialTheme.typography.titleMedium,
                        fontStyle = if (note.title.isBlank()) FontStyle.Italic else null,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (preview.isNotEmpty()) {
                    Text(
                        preview,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                Text(
                    DateUtils.formatDateTime(
                        androidx.compose.ui.platform.LocalContext.current,
                        note.updatedAt,
                        DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_MONTH,
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            Column {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.note_more, titleText))
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    actions(note).forEachIndexed { index, action ->
                        if (index > 0) HorizontalDivider()
                        DropdownMenuItem(
                            text = {
                                Text(
                                    stringResource(action.labelRes),
                                    color = if (action.destructive) MaterialTheme.colorScheme.error else androidx.compose.ui.graphics.Color.Unspecified,
                                )
                            },
                            onClick = { menuOpen = false; action.run() },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
    )
}

@Composable
private fun EmptyNotes(view: NotesView, query: String, onNewNote: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        when {
            query.isNotBlank() -> Text(
                stringResource(R.string.notes_empty_search, query.trim()),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            view == NotesView.ARCHIVE -> Text(stringResource(R.string.notes_empty_archive), style = MaterialTheme.typography.titleMedium)
            view == NotesView.TRASH -> Text(stringResource(R.string.notes_empty_trash), style = MaterialTheme.typography.titleMedium)
            else -> {
                Text(stringResource(R.string.notes_empty_title), style = MaterialTheme.typography.titleLarge)
                Text(
                    stringResource(R.string.notes_empty_body),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
                )
                Button(onClick = onNewNote, modifier = Modifier.heightIn(min = 56.dp)) {
                    Text(stringResource(R.string.notes_empty_action))
                }
            }
        }
    }
}

@Composable
private fun SortOption(labelRes: Int, value: NoteSort, selected: NoteSort, onSelect: (NoteSort) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .selectable(selected = value == selected, onClick = { onSelect(value) }, role = Role.RadioButton),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = value == selected, onClick = null)
        Text(stringResource(labelRes), modifier = Modifier.padding(start = 12.dp))
    }
}

@Composable
private fun NotesToolbar(
    inMainView: Boolean,
    titleRes: Int,
    query: String,
    onQuery: (String) -> Unit,
    grid: Boolean,
    onToggleGrid: () -> Unit,
    onSort: () -> Unit,
    onArchive: () -> Unit,
    onTrash: () -> Unit,
    onBackToMain: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Column {
        if (!inMainView) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBackToMain) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.notes_back))
                }
                Text(stringResource(titleRes), style = MaterialTheme.typography.titleMedium)
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            // A slim search field (40dp instead of the 56dp outlined field).
            Row(
                Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Default.Search, contentDescription = stringResource(R.string.notes_search),
                    modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Box(Modifier.weight(1f).padding(horizontal = 8.dp), contentAlignment = Alignment.CenterStart) {
                    BasicTextField(
                        value = query,
                        onValueChange = onQuery,
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (query.isEmpty()) {
                        Text(
                            stringResource(R.string.notes_search_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }
                if (query.isNotEmpty()) {
                    Icon(
                        Icons.Default.Close, contentDescription = stringResource(R.string.notes_search_close),
                        modifier = Modifier.size(20.dp).clickable { onQuery("") },
                    )
                }
            }
            IconButton(onClick = onSort) {
                Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = stringResource(R.string.notes_sort))
            }
            IconButton(onClick = onToggleGrid) {
                Icon(
                    if (grid) Icons.AutoMirrored.Filled.ViewList else Icons.Default.GridView,
                    contentDescription = stringResource(if (grid) R.string.notes_view_list else R.string.notes_view_grid),
                )
            }
            if (inMainView) {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.menu_more))
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.notes_archive)) }, onClick = { menuOpen = false; onArchive() })
                    HorizontalDivider()
                    DropdownMenuItem(text = { Text(stringResource(R.string.notes_trash)) }, onClick = { menuOpen = false; onTrash() })
                }
            }
        }
    }
}
