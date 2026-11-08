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
import com.kosh.android.notes.ui.FolderItem
import com.kosh.android.notes.data.NoteFolder
import com.kosh.android.notes.data.FOLDER_COLORS
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import com.kosh.android.notes.data.NotesShow
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.RestoreFromTrash
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Surface
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.focus.onFocusChanged
import kotlinx.coroutines.launch

private sealed interface FolderDialog {
    data object Create : FolderDialog
    data class CreateAndMove(val ids: List<String>) : FolderDialog
    data class Rename(val folder: NoteFolder) : FolderDialog
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun NotesScreen(
    onOpenNote: (String) -> Unit,
    onNewNote: (String?) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val vm: NotesViewModel = viewModel(factory = KoshViewModelFactory.Factory)
    val state by vm.state.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val gridState = rememberLazyGridState()
    var showSort by remember { mutableStateOf(false) }
    var plusMenu by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<Note?>(null) }
    var confirmDeleteSelected by remember { mutableStateOf(false) }
    var folderDialog by remember { mutableStateOf<FolderDialog?>(null) }
    var moveIds by remember { mutableStateOf<List<String>?>(null) }
    var deleteFolderTarget by remember { mutableStateOf<NoteFolder?>(null) }
    var colorFolderTarget by remember { mutableStateOf<NoteFolder?>(null) }

    val inMainView = state.view == NotesView.ACTIVE
    val folder = state.currentFolder

    // Back steps out one level at a time: picking, then a folder or Archive/Trash, then the app. The last handler wins.
    BackHandler(enabled = folder != null && inMainView) { vm.closeFolder() }
    BackHandler(enabled = !inMainView) { vm.setView(NotesView.ACTIVE) }
    BackHandler(enabled = state.selecting) { vm.clearSelection() }

    val title = when (state.view) {
        NotesView.ACTIVE -> R.string.notes_title
        NotesView.ARCHIVE -> R.string.notes_archive
        NotesView.TRASH -> R.string.notes_trash
    }

    val openNote: (Note) -> Unit = { note ->
        if (state.selecting) vm.toggleSelected(note.id)
        else handleOpen(note, state.view, onOpenNote) {
            scope.launch { snackbar.showSnackbar(context.getString(R.string.note_trashed_open)) }
        }
    }
    val askMove: (List<String>) -> Unit = { moveIds = it }

    // The app-wide top bar is shown by the main screen. This tab only has its own tools: search, sort, view, lists.
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        floatingActionButton = {
            if (inMainView && !state.selecting) {
                // The menu hangs from the button itself; inside a folder the button simply adds a note to it.
                Box {
                    FloatingActionButton(onClick = { if (folder != null) onNewNote(folder.id) else plusMenu = true }) {
                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.notes_new))
                    }
                    DropdownMenu(expanded = plusMenu, onDismissRequest = { plusMenu = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.notes_new_note)) },
                            leadingIcon = { Icon(Icons.Default.Description, contentDescription = null) },
                            onClick = { plusMenu = false; onNewNote(null) },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.notes_new_folder)) },
                            leadingIcon = { Icon(Icons.Default.CreateNewFolder, contentDescription = null) },
                            onClick = { plusMenu = false; folderDialog = FolderDialog.Create },
                        )
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (state.selecting) {
                SelectionBar(
                    count = state.selected.size,
                    view = state.view,
                    onClose = vm::clearSelection,
                    onSelectAll = vm::selectAll,
                    onMove = { askMove(state.selected.toList()) },
                    onTrash = {
                        val count = state.selected.size
                        val ids = vm.trashSelected()
                        scope.launch {
                            val result = snackbar.showSnackbar(
                                message = context.resources.getQuantityString(R.plurals.notes_trashed_many, count, count),
                                actionLabel = context.getString(R.string.note_undo),
                            )
                            if (result == SnackbarResult.ActionPerformed) vm.restoreIds(ids)
                        }
                    },
                    onUnarchive = vm::unarchiveSelected,
                    onRestore = vm::restoreSelected,
                    onDeleteForever = { confirmDeleteSelected = true },
                )
            } else {
                NotesToolbar(
                    headerTitle = when {
                        !inMainView -> stringResource(title)
                        folder != null -> folder.name
                        else -> null
                    },
                    query = query,
                    onQuery = vm::setQuery,
                    grid = state.prefs.grid,
                    onToggleGrid = { vm.setGrid(!state.prefs.grid) },
                    onSort = { showSort = true },
                    onArchive = { vm.setView(NotesView.ARCHIVE) },
                    onTrash = { vm.setView(NotesView.TRASH) },
                    onBack = { if (inMainView) vm.closeFolder() else vm.setView(NotesView.ACTIVE) },
                )
            }
            when {
                state.loading -> Unit
                state.isEmpty -> EmptyNotes(state.view, query, folder != null, state.prefs.show == NotesShow.FOLDERS) { onNewNote(folder?.id) }
                else -> LazyVerticalGrid(
                    state = gridState,
                    columns = if (state.prefs.grid) GridCells.Adaptive(170.dp) else GridCells.Fixed(1),
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 88.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    val hasNotes = state.pinned.isNotEmpty() || state.notes.isNotEmpty()
                    if (state.folders.isNotEmpty()) {
                        if (hasNotes) {
                            item(span = { GridItemSpan(maxLineSpan) }, key = "header-folders") {
                                SectionHeader(stringResource(R.string.notes_section_folders))
                            }
                        }
                        items(state.folders, key = { "folder-" + it.folder.id }) { item ->
                            FolderCard(
                                item = item,
                                enabled = !state.selecting,
                                onOpen = { vm.openFolder(item.folder.id) },
                                onRename = { folderDialog = FolderDialog.Rename(item.folder) },
                                onColor = { colorFolderTarget = item.folder },
                                onDelete = { deleteFolderTarget = item.folder },
                            )
                        }
                    }
                    if (state.pinned.isNotEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }, key = "header-pinned") {
                            SectionHeader(stringResource(R.string.notes_section_pinned))
                        }
                        items(state.pinned, key = { it.id }) { note ->
                            NoteCard(
                                note, state.view, note.id in state.selected, state.selecting,
                                onClick = { openNote(note) }, onLongClick = { vm.toggleSelected(note.id) },
                                actions = { actionsFor(vm, it, state.view, context, scope, snackbar, askMove) { n -> deleteTarget = n } },
                            )
                        }
                    }
                    if (state.notes.isNotEmpty() && (state.pinned.isNotEmpty() || state.folders.isNotEmpty())) {
                        item(span = { GridItemSpan(maxLineSpan) }, key = "header-others") {
                            SectionHeader(
                                stringResource(if (state.pinned.isNotEmpty()) R.string.notes_section_others else R.string.notes_section_notes),
                            )
                        }
                    }
                    items(state.notes, key = { it.id }) { note ->
                        NoteCard(
                            note, state.view, note.id in state.selected, state.selecting,
                            onClick = { openNote(note) }, onLongClick = { vm.toggleSelected(note.id) },
                            actions = { actionsFor(vm, it, state.view, context, scope, snackbar, askMove) { n -> deleteTarget = n } },
                        )
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
                Column(Modifier.selectableGroup().verticalScroll(rememberScrollState())) {
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
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    Text(stringResource(R.string.notes_show_title), style = MaterialTheme.typography.titleSmall)
                    ShowOption(R.string.notes_show_all, NotesShow.ALL, state.prefs.show, vm::setShow)
                    ShowOption(R.string.notes_show_notes, NotesShow.NOTES, state.prefs.show, vm::setShow)
                    ShowOption(R.string.notes_show_folders, NotesShow.FOLDERS, state.prefs.show, vm::setShow)
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

    if (confirmDeleteSelected) {
        val count = state.selected.size
        AlertDialog(
            onDismissRequest = { confirmDeleteSelected = false },
            title = { Text(pluralStringResource(R.plurals.notes_delete_forever_many_title, count, count)) },
            text = { Text(stringResource(R.string.note_delete_forever_body)) },
            confirmButton = {
                TextButton(onClick = { confirmDeleteSelected = false; vm.deleteSelectedPermanently() }) {
                    Text(stringResource(R.string.note_delete_forever), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDeleteSelected = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    moveIds?.let { ids ->
        MoveToFolderDialog(
            folders = state.allFolders,
            onPick = { folderId -> moveIds = null; vm.moveToFolder(ids, folderId) },
            onNewFolder = { moveIds = null; folderDialog = FolderDialog.CreateAndMove(ids) },
            onDismiss = { moveIds = null },
        )
    }

    folderDialog?.let { dialog ->
        val (titleRes, confirmRes, initial) = when (dialog) {
            FolderDialog.Create, is FolderDialog.CreateAndMove -> Triple(R.string.folder_create_title, R.string.folder_create, "")
            is FolderDialog.Rename -> Triple(R.string.folder_rename_title, R.string.action_rename, dialog.folder.name)
        }
        NameDialog(
            titleRes = titleRes,
            confirmRes = confirmRes,
            initial = initial,
            onConfirm = { name ->
                folderDialog = null
                when (dialog) {
                    FolderDialog.Create -> vm.createFolder(name)
                    is FolderDialog.CreateAndMove -> vm.createFolderAndMove(name, dialog.ids)
                    is FolderDialog.Rename -> vm.renameFolder(dialog.folder.id, name)
                }
            },
            onDismiss = { folderDialog = null },
        )
    }

    colorFolderTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { colorFolderTarget = null },
            title = { Text(stringResource(R.string.folder_color_title)) },
            text = {
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    FOLDER_COLORS.forEach { c ->
                        Box(
                            Modifier.size(44.dp).clip(androidx.compose.foundation.shape.CircleShape)
                                .background(androidx.compose.ui.graphics.Color(c))
                                .clickable { vm.setFolderColor(target.id, c); colorFolderTarget = null },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (target.color == c) Icon(Icons.Default.CheckCircle, contentDescription = null, tint = androidx.compose.ui.graphics.Color.White)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { vm.setFolderColor(target.id, null); colorFolderTarget = null }) { Text(stringResource(R.string.folder_color_none)) } },
            dismissButton = { TextButton(onClick = { colorFolderTarget = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    deleteFolderTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteFolderTarget = null },
            title = { Text(stringResource(R.string.folder_delete_title)) },
            text = { Text(stringResource(R.string.folder_delete_body)) },
            confirmButton = {
                TextButton(onClick = { deleteFolderTarget = null; vm.deleteFolder(target.id) }) {
                    Text(stringResource(R.string.folder_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { deleteFolderTarget = null }) { Text(stringResource(R.string.cancel)) } },
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
    askMove: (List<String>) -> Unit,
    askDelete: (Note) -> Unit,
): List<NoteAction> = when (view) {
    NotesView.ACTIVE -> listOf(
        NoteAction(if (note.isPinned) R.string.note_unpin else R.string.note_pin) { vm.togglePin(note) },
        NoteAction(R.string.note_move_to_folder) { askMove(listOf(note.id)) },
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NoteCard(
    note: Note,
    view: NotesView,
    selected: Boolean,
    selecting: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    actions: (Note) -> List<NoteAction>,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val titleText = note.title.ifBlank { stringResource(R.string.notes_untitled) }
    val preview = remember(note.content) { NoteFormatConverter.preview(note.content) }
    Card(
        modifier = Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLongClick),
        colors = if (selected) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer) else CardDefaults.cardColors(),
    ) {
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
                if (selecting) {
                    // While picking, the card shows a tick instead of its menu.
                    Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                        if (selected) Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                } else {
                    Box {
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
    }
}

/** A folder in the list. Tap opens it; its menu renames or deletes it (the notes inside are kept). */
@Composable
private fun FolderCard(item: FolderItem, enabled: Boolean, onOpen: () -> Unit, onRename: () -> Unit, onColor: () -> Unit, onDelete: () -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    val tint = item.folder.color?.let { androidx.compose.ui.graphics.Color(it) }
    Card(
        modifier = Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onOpen),
        colors = if (tint != null) CardDefaults.cardColors(containerColor = tint.copy(alpha = 0.18f).compositeOver(MaterialTheme.colorScheme.surface)) else CardDefaults.cardColors(),
    ) {
        Row(Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Folder, contentDescription = null, tint = tint ?: MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(item.folder.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    pluralStringResource(R.plurals.folder_note_count, item.noteCount, item.noteCount),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (enabled) {
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.note_more, item.folder.name))
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.action_rename)) }, onClick = { menuOpen = false; onRename() })
                        DropdownMenuItem(text = { Text(stringResource(R.string.folder_color)) }, onClick = { menuOpen = false; onColor() })
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.folder_delete), color = MaterialTheme.colorScheme.error) },
                            onClick = { menuOpen = false; onDelete() },
                        )
                    }
                }
            }
        }
    }
}

/** Replaces the toolbar while notes are picked: how many, select all, and what can be done with them in this list. */
@Composable
private fun SelectionBar(
    count: Int,
    view: NotesView,
    onClose: () -> Unit,
    onSelectAll: () -> Unit,
    onMove: () -> Unit,
    onTrash: () -> Unit,
    onUnarchive: () -> Unit,
    onRestore: () -> Unit,
    onDeleteForever: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.Default.Close, contentDescription = stringResource(R.string.sel_close)) }
            Text(
                pluralStringResource(R.plurals.notes_selected_count, count, count),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onSelectAll) { Icon(Icons.Default.SelectAll, contentDescription = stringResource(R.string.sel_select_all)) }
            when (view) {
                NotesView.ACTIVE -> {
                    IconButton(onClick = onMove) { Icon(Icons.Default.CreateNewFolder, contentDescription = stringResource(R.string.note_move_to_folder)) }
                    IconButton(onClick = onTrash) { Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.note_move_to_trash)) }
                }
                NotesView.ARCHIVE -> {
                    IconButton(onClick = onUnarchive) { Icon(Icons.Default.Unarchive, contentDescription = stringResource(R.string.note_unarchive)) }
                    IconButton(onClick = onDeleteForever) { Icon(Icons.Default.DeleteForever, contentDescription = stringResource(R.string.note_delete_forever)) }
                }
                NotesView.TRASH -> {
                    IconButton(onClick = onRestore) { Icon(Icons.Default.RestoreFromTrash, contentDescription = stringResource(R.string.note_restore)) }
                    IconButton(onClick = onDeleteForever) { Icon(Icons.Default.DeleteForever, contentDescription = stringResource(R.string.note_delete_forever)) }
                }
            }
        }
    }
}

@Composable
private fun NameDialog(titleRes: Int, confirmRes: Int, initial: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf(initial) }
    val focus = remember { FocusRequester() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(titleRes)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                label = { Text(stringResource(R.string.folder_name_hint)) },
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
            androidx.compose.runtime.LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name.trim()) }, enabled = name.isNotBlank()) { Text(stringResource(confirmRes)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun MoveToFolderDialog(
    folders: List<NoteFolder>,
    onPick: (String?) -> Unit,
    onNewFolder: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.move_title)) },
        text = {
            Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                MoveRow(Icons.Default.CreateNewFolder, stringResource(R.string.move_new_folder), onNewFolder)
                folders.forEach { f -> MoveRow(Icons.Default.Folder, f.name) { onPick(f.id) } }
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                MoveRow(Icons.Default.Description, stringResource(R.string.move_no_folder)) { onPick(null) }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun MoveRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text(label, modifier = Modifier.padding(start = 12.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ShowOption(labelRes: Int, value: NotesShow, selected: NotesShow, onSelect: (NotesShow) -> Unit) {
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
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
    )
}

@Composable
private fun EmptyNotes(view: NotesView, query: String, inFolder: Boolean, foldersOnly: Boolean, onNewNote: () -> Unit) {
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
            inFolder -> {
                Text(stringResource(R.string.notes_empty_folder), style = MaterialTheme.typography.titleMedium)
                Button(onClick = onNewNote, modifier = Modifier.padding(top = 16.dp).heightIn(min = 56.dp)) {
                    Text(stringResource(R.string.notes_empty_action))
                }
            }
            foldersOnly -> {
                Text(stringResource(R.string.notes_empty_folders), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.notes_empty_folders_body),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
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
    headerTitle: String?,
    query: String,
    onQuery: (String) -> Unit,
    grid: Boolean,
    onToggleGrid: () -> Unit,
    onSort: () -> Unit,
    onArchive: () -> Unit,
    onTrash: () -> Unit,
    onBack: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Column {
        if (headerTitle != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.notes_back))
                }
                Text(headerTitle, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
            if (headerTitle == null) {
                // The menu hangs from its own button, so it opens under the dots at the right edge, not at the left.
                Box {
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
}
