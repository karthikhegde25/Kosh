package com.kosh.android.ui.screens

import android.net.Uri
import android.os.Environment
import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.foundation.combinedClickable
import androidx.activity.compose.BackHandler
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.produceState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kosh.android.KoshViewModelFactory
import com.kosh.android.PdfListState
import com.kosh.android.PdfListViewModel
import com.kosh.android.R
import com.kosh.android.pdflist.FileRowData
import com.kosh.android.pdflist.FolderRowData
import com.kosh.android.pdflist.PdfFile
import com.kosh.android.pdflist.PdfThumbnailLoader
import com.kosh.android.pdflist.RenameResult
import com.kosh.android.pdflist.StoragePermission
import com.kosh.android.pdflist.buildFolderTree
import com.kosh.android.pdflist.filterFiles
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import com.kosh.android.pdflist.flattenTree
import com.kosh.android.ui.components.sharePdf
import com.kosh.android.tools.FileKind
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Slideshow
import androidx.compose.material.icons.filled.TableChart
import kotlinx.coroutines.Job
import com.kosh.android.util.formatFileSize
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfHomeScreen(
    onOpenPdf: (Uri) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val vm: PdfListViewModel = viewModel(factory = KoshViewModelFactory.Factory)
    val state by vm.state.collectAsStateWithLifecycle()
    val selected by vm.selected.collectAsStateWithLifecycle()
    var hasAccess by remember { mutableStateOf(StoragePermission.hasAccess(context)) }
    val pagerState = rememberPagerState(pageCount = { 2 })
    val snackbar = remember { SnackbarHostState() }
    var renameTarget by remember { mutableStateOf<PdfFile?>(null) }
    var deleteTarget by remember { mutableStateOf<PdfFile?>(null) }
    // A Word/Excel/PowerPoint file being converted (to open it, or to save it as PDF), with the job so it can be cancelled.
    var converting by remember { mutableStateOf<String?>(null) }
    var convertJob by remember { mutableStateOf<Job?>(null) }

    // Search: a bar with the keyboard open replaces the tab row; Back closes it and returns to the All tab.
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    val searchFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    fun closeSearch() {
        keyboard?.hide()
        searching = false
        query = ""
        scope.launch { pagerState.scrollToPage(0) }
    }
    LaunchedEffect(searching) {
        if (searching) {
            searchFocus.requestFocus()
            keyboard?.show()
        }
    }
    BackHandler(enabled = searching) { closeSearch() }

    // Back leaves selection mode first.
    BackHandler(enabled = selected.isNotEmpty()) { vm.clearSelection() }

    // The user grants All files access in system settings, so re-check whenever we come back.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        hasAccess = StoragePermission.hasAccess(context)
        // Coming back from the viewer: a doodle copy or converted file may have just been written.
        if (hasAccess) vm.refresh(silent = true)
    }
    LaunchedEffect(hasAccess) { if (hasAccess) vm.refresh(silent = true) }

    val legacyPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasAccess = granted || StoragePermission.hasAccess(context)
    }
    val openDocument = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            onOpenPdf(uri)
        }
    }
    val pickPdf = { openDocument.launch(arrayOf(FileKind.PDF.mime) + FileKind.officeMimeTypes) }

    val actions = PdfActions(
        onOpen = { file ->
            if (!file.kind.isOffice) onOpenPdf(Uri.fromFile(File(file.path)))
            else {
                converting = file.name
                convertJob = scope.launch {
                    try {
                        vm.viewablePdf(file).fold(
                            onSuccess = { onOpenPdf(Uri.fromFile(it)) },
                            onFailure = { snackbar.showSnackbar(context.getString(R.string.office_convert_failed, file.name)) },
                        )
                    } finally {
                        converting = null
                    }
                }
            }
        },
        onSavePdf = { file ->
            converting = file.name
            convertJob = scope.launch {
                try {
                    vm.saveAsPdf(file).fold(
                        onSuccess = { snackbar.showSnackbar(context.getString(R.string.office_saved_pdf, it.name)) },
                        onFailure = { snackbar.showSnackbar(context.getString(R.string.office_convert_failed, file.name)) },
                    )
                } finally {
                    converting = null
                }
            }
        },
        onShare = { file ->
            if (!sharePdf(context, Uri.fromFile(File(file.path)), file.kind.mime)) {
                scope.launch { snackbar.showSnackbar(context.getString(R.string.reader_share_failed)) }
            }
        },
        onRename = { renameTarget = it },
        onDelete = { deleteTarget = it },
        selection = selected.toSet(),
        onToggleSelect = { vm.toggleSelected(it.path) },
    )

    // The app-wide top bar (logo, name, settings) is shown by the main screen; this tab has its own content only.
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (!hasAccess) {
                AccessRequest(
                    onAllow = {
                        if (StoragePermission.needsSettingsScreen) StoragePermission.openAllFilesSettings(context)
                        else legacyPermission.launch(android.Manifest.permission.READ_EXTERNAL_STORAGE)
                    },
                    onPickSingle = pickPdf,
                )
                return@Column
            }

            if (searching) {
                Row(Modifier.fillMaxWidth().padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = ::closeSearch) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.search_close))
                    }
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.weight(1f).focusRequester(searchFocus),
                        placeholder = { Text(stringResource(R.string.search_hint)) },
                        singleLine = true,
                        trailingIcon = {
                            if (query.isNotEmpty()) {
                                IconButton(onClick = { query = ""; searchFocus.requestFocus() }) {
                                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.search_clear))
                                }
                            }
                        },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                    )
                }
            } else Row(verticalAlignment = Alignment.CenterVertically) {
                // Same width as the open-file button on the right, so the tabs sit in the middle of the row.
                IconButton(onClick = { searching = true }) {
                    Icon(Icons.Default.Search, contentDescription = stringResource(R.string.search_files))
                }
                PrimaryTabRow(selectedTabIndex = pagerState.currentPage, modifier = Modifier.weight(1f)) {
                    Tab(selected = pagerState.currentPage == 0, onClick = { scope.launch { pagerState.animateScrollToPage(0) } }, text = { Text(stringResource(R.string.tab_all)) })
                    Tab(selected = pagerState.currentPage == 1, onClick = { scope.launch { pagerState.animateScrollToPage(1) } }, text = { Text(stringResource(R.string.tab_folders)) })
                }
                IconButton(onClick = pickPdf) {
                    Icon(Icons.Default.FolderOpen, contentDescription = stringResource(R.string.pdfs_open_file))
                }
            }

            when (val s = state) {
                PdfListState.Idle, PdfListState.Scanning -> Centered {
                    CircularProgressIndicator()
                    Text(stringResource(R.string.pdfs_scanning), modifier = Modifier.padding(top = 16.dp))
                }
                PdfListState.Failed -> Centered {
                    Text(stringResource(R.string.pdfs_error), textAlign = TextAlign.Center)
                    OutlinedButton(onClick = { vm.refresh() }, modifier = Modifier.padding(top = 16.dp)) {
                        Text(stringResource(R.string.pdfs_refresh))
                    }
                }
                is PdfListState.Loaded -> when {
                    searching -> {
                        val matches = remember(s.files, query) { filterFiles(s.files, query) }
                        if (matches.isEmpty()) Centered {
                            Text(stringResource(R.string.search_no_results, query.trim()), textAlign = TextAlign.Center)
                        } else AllPdfsList(matches, actions)
                    }
                    s.files.isEmpty() -> Centered {
                        Text(stringResource(R.string.pdfs_empty_title), style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringResource(R.string.pdfs_empty_body),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                    else -> HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                        if (page == 0) AllPdfsList(s.files, actions) else FolderTreeList(s.files, actions)
                    }
                }
            }
        }
    }

    converting?.let { name ->
        AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.office_converting_title)) },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(32.dp))
                    Spacer(Modifier.width(16.dp))
                    Text(stringResource(R.string.office_converting_body, name))
                }
            },
            confirmButton = {
                TextButton(onClick = { convertJob?.cancel() }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    renameTarget?.let { target ->
        RenameDialog(
            file = target,
            onDismiss = { renameTarget = null },
            onSubmit = { name -> vm.rename(target, name) },
            onRenamed = { renamed ->
                renameTarget = null
                scope.launch { snackbar.showSnackbar(context.getString(R.string.msg_renamed, renamed.name)) }
            },
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.delete_title)) },
            text = { Text(stringResource(R.string.delete_body, target.name)) },
            confirmButton = {
                TextButton(onClick = {
                    deleteTarget = null
                    scope.launch {
                        val ok = vm.delete(target)
                        snackbar.showSnackbar(
                            context.getString(if (ok) R.string.msg_deleted else R.string.delete_failed, target.name),
                        )
                    }
                }) { Text(stringResource(R.string.delete_confirm), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

private class PdfActions(
    val onOpen: (PdfFile) -> Unit,
    val onSavePdf: (PdfFile) -> Unit,
    val onShare: (PdfFile) -> Unit,
    val onRename: (PdfFile) -> Unit,
    val onDelete: (PdfFile) -> Unit,
    val selection: Set<String>,
    val onToggleSelect: (PdfFile) -> Unit,
)

@Composable
private fun AllPdfsList(files: List<PdfFile>, actions: PdfActions) {
    LazyColumn(Modifier.fillMaxSize()) {
        items(files, key = { it.path }) { file ->
            PdfRow(file = file, showFolder = true, indent = 0.dp, actions = actions)
            HorizontalDivider()
        }
    }
}

/** Real folder hierarchy: a PDF in Download/Work/2026 is shown under Download, then Work, then 2026. */
@Composable
private fun FolderTreeList(files: List<PdfFile>, actions: PdfActions) {
    val rootPath = remember { Environment.getExternalStorageDirectory().absolutePath }
    val rootName = stringResource(R.string.folder_root)
    val tree = remember(files, rootName) { buildFolderTree(files, rootPath, rootName) }
    // Paths of expanded folders ("" is the storage root, open by default).
    var expanded by rememberSaveable { mutableStateOf<List<String>>(arrayListOf("")) }
    val rows = remember(tree, expanded) { flattenTree(tree, expanded.toSet()) }

    LazyColumn(Modifier.fillMaxSize()) {
        items(rows, key = { row ->
            when (row) {
                is FolderRowData -> "d:" + row.node.relPath
                is FileRowData -> "f:" + row.file.path
            }
        }) { row ->
            val indent = (row.depth * 16).coerceAtMost(80).dp
            when (row) {
                is FolderRowData -> FolderRow(row, indent) {
                    val path = row.node.relPath
                    expanded = if (path in expanded) expanded - path else expanded + path
                }
                is FileRowData -> {
                    PdfRow(file = row.file, showFolder = false, indent = indent, actions = actions)
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun FolderRow(row: FolderRowData, indent: Dp, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(onClick = onToggle)
            .padding(start = 16.dp + indent, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (row.expanded) Icons.Default.FolderOpen else Icons.Default.Folder,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(row.node.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val n = row.node.totalCount
            Text(
                pluralStringResource(R.plurals.file_count, n, n),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            if (row.expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
            contentDescription = stringResource(if (row.expanded) R.string.folder_collapse else R.string.folder_expand),
        )
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) { content() }
}

@Composable
private fun AccessRequest(onAllow: () -> Unit, onPickSingle: () -> Unit) {
    Centered {
        Icon(
            Icons.Default.PictureAsPdf,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 16.dp),
        )
        Text(stringResource(R.string.access_title), style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Text(
            stringResource(R.string.access_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
        )
        Button(onClick = onAllow, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
            Text(stringResource(R.string.access_allow))
        }
        OutlinedButton(onClick = onPickSingle, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(top = 8.dp)) {
            Text(stringResource(R.string.access_alternative))
        }
    }
}

/** Word, Excel and PowerPoint files have no page to preview until they are converted, so they show a coloured file-type tile. */
@Composable
private fun OfficeTile(kind: FileKind) {
    val (icon, color) = when (kind) {
        FileKind.DOCX -> Icons.Default.Description to androidx.compose.ui.graphics.Color(0xFF2B579A)
        FileKind.XLSX -> Icons.Default.TableChart to androidx.compose.ui.graphics.Color(0xFF217346)
        else -> Icons.Default.Slideshow to androidx.compose.ui.graphics.Color(0xFFC43E1C)
    }
    Box(
        modifier = Modifier.size(width = 48.dp, height = 64.dp).clip(RoundedCornerShape(4.dp)).background(color.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = null, tint = color)
            Text(kind.extension.uppercase(), style = MaterialTheme.typography.labelSmall, color = color)
        }
    }
}

/** A small preview of the PDF's first page; a PDF icon is shown while it loads or if it can't be rendered. */
@Composable
private fun PdfPreview(file: PdfFile) {
    if (file.kind.isOffice) { OfficeTile(file.kind); return }
    val density = androidx.compose.ui.platform.LocalDensity.current
    val widthPx = with(density) { 48.dp.roundToPx() }
    val preview by produceState<android.graphics.Bitmap?>(null, file.path, file.modifiedMillis) {
        value = PdfThumbnailLoader.load(file.path, file.modifiedMillis, widthPx * 2)
    }
    Box(
        modifier = Modifier
            .size(width = 48.dp, height = 64.dp)
            .clip(RoundedCornerShape(4.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(4.dp))
            .background(androidx.compose.ui.graphics.Color.White),
        contentAlignment = Alignment.Center,
    ) {
        val bmp = preview
        if (bmp != null) {
            val image = remember(bmp) { bmp.asImageBitmap() }
            Image(
                bitmap = image,
                contentDescription = stringResource(R.string.pdf_preview_desc, file.name),
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopCenter,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
    }
}

/** One PDF: tap the row to open it; Share, Rename and Delete are in the 3-dots menu at the right. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun PdfRow(file: PdfFile, showFolder: Boolean, indent: Dp, actions: PdfActions) {
    var menuOpen by remember { mutableStateOf(false) }
    val selecting = actions.selection.isNotEmpty()
    val isSelected = file.path in actions.selection
    val date = DateUtils.getRelativeTimeSpanString(
        file.modifiedMillis, System.currentTimeMillis(), DateUtils.DAY_IN_MILLIS,
    ).toString()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else androidx.compose.ui.graphics.Color.Transparent)
            .heightIn(min = 72.dp)
            // Tap opens the PDF, or picks/unpicks it once something is picked; press and hold starts picking.
            .combinedClickable(
                onClick = { if (selecting) actions.onToggleSelect(file) else actions.onOpen(file) },
                onLongClick = { actions.onToggleSelect(file) },
            )
            .padding(start = 16.dp + indent, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PdfPreview(file)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(file.name, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                stringResource(R.string.pdf_file_meta, formatFileSize(file.sizeBytes), date) +
                    if (showFolder && file.folder.isNotEmpty()) " · ${file.folder}" else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (selecting) {
            Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                if (isSelected) Icon(Icons.Default.CheckCircle, contentDescription = stringResource(R.string.selected), tint = MaterialTheme.colorScheme.primary)
            }
        } else Column {
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.note_more, file.name))
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_share)) },
                    leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                    onClick = { menuOpen = false; actions.onShare(file) },
                )
                if (file.kind.isOffice) {
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_save_as_pdf)) },
                        leadingIcon = { Icon(Icons.Default.PictureAsPdf, contentDescription = null) },
                        onClick = { menuOpen = false; actions.onSavePdf(file) },
                    )
                }
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_rename)) },
                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                    onClick = { menuOpen = false; actions.onRename(file) },
                )
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) },
                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                    onClick = { menuOpen = false; actions.onDelete(file) },
                )
            }
        }
    }
}

@Composable
private fun RenameDialog(
    file: PdfFile,
    onDismiss: () -> Unit,
    onSubmit: suspend (String) -> RenameResult,
    onRenamed: (PdfFile) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val extension = remember(file.path) { File(file.path).extension }
    // The caret starts at the end so the end of a long name is what you see, and the whole name can be scrolled through.
    var text by remember(file.path) {
        File(file.path).nameWithoutExtension.let { name -> mutableStateOf(TextFieldValue(name, TextRange(name.length))) }
    }
    var errorRes by remember { mutableStateOf<Int?>(null) }
    var busy by remember { mutableStateOf(false) }

    fun submit() {
        if (busy) return // ignore double taps while a rename is in progress
        busy = true
        scope.launch {
            when (val result = onSubmit(text.text)) {
                is RenameResult.Success -> onRenamed(result.file)
                RenameResult.InvalidName -> errorRes = R.string.rename_invalid
                RenameResult.NameExists -> errorRes = R.string.rename_exists
                RenameResult.Failed -> errorRes = R.string.rename_failed
            }
            busy = false
        }
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(stringResource(R.string.rename_title)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it; errorRes = null },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.rename_label)) },
                // The file type is shown below the box rather than inside it, where it used to cover the end of the name.
                isError = errorRes != null,
                supportingText = {
                    val res = errorRes
                    if (res != null) Text(stringResource(res))
                    else if (extension.isNotEmpty()) Text(stringResource(R.string.rename_keeps_extension, extension))
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
            )
        },
        confirmButton = {
            TextButton(onClick = ::submit, enabled = !busy) { Text(stringResource(R.string.rename_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) { Text(stringResource(R.string.cancel)) }
        },
    )
}
