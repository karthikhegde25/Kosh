package com.kosh.android.ui.screens

import android.graphics.Bitmap
import android.net.Uri
import android.text.format.DateUtils
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.filled.Add
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import com.kosh.android.util.FilterMatrices
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kosh.android.DraftViewModel
import com.kosh.android.KoshViewModelFactory
import com.kosh.android.R
import com.kosh.android.SettingsViewModel
import com.kosh.android.data.db.ScanRecord
import com.kosh.android.data.model.FilterMode
import com.kosh.android.data.model.ScannedPage
import com.kosh.android.imaging.PageRenderer
import com.kosh.android.pdf.ExportQuality
import com.kosh.android.pdf.PaperSize
import com.kosh.android.scanner.RecentScansViewModel
import com.kosh.android.ui.components.ChoiceRow
import com.kosh.android.ui.components.rememberImagePicker
import com.kosh.android.ui.components.sharePdf
import com.kosh.android.ui.components.toMessage
import com.kosh.android.util.clampTitle
import com.kosh.android.util.formatFileSize
import kotlinx.coroutines.launch

/**
 * The Scanner tab. "Scan" starts a scan, imports photos, lists recent PDFs and holds the scan settings.
 * "Edit" shows every photo of the current scan in a row, with rotate, crop and delete, and the Save as PDF button.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScannerScreen(
    draftVm: DraftViewModel,
    onOpenCamera: () -> Unit,
    onOpenPdf: (Uri) -> Unit,
    onCrop: (String) -> Unit,
    onEnhance: (String) -> Unit,
    onSaveAsPdf: () -> Unit,
) {
    val context = LocalContext.current
    val draft by draftVm.draft.collectAsStateWithLifecycle()
    val importing by draftVm.importing.collectAsStateWithLifecycle()
    val notice by draftVm.notice.collectAsStateWithLifecycle()
    val editRequested by draftVm.editRequested.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var sub by rememberSaveable { mutableIntStateOf(0) }
    val photoCount = draft?.pages?.size ?: 0

    val pickImages = rememberImagePicker { draftVm.importImages(it, openEditor = true) }

    // New photos (imported here, or taken with the camera) are shown in the Edit tab.
    LaunchedEffect(notice) {
        val n = notice ?: return@LaunchedEffect
        draftVm.consumeNotice()
        if (n.openEditor && n.summary.added > 0) sub = 1
        snackbar.showSnackbar(n.summary.toMessage(context))
    }
    LaunchedEffect(editRequested) {
        if (editRequested) {
            sub = 1
            draftVm.consumeEditRequest()
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            PrimaryTabRow(selectedTabIndex = sub) {
                Tab(selected = sub == 0, onClick = { sub = 0 }, text = { Text(stringResource(R.string.scanner_tab_scan)) })
                Tab(
                    selected = sub == 1,
                    onClick = { sub = 1 },
                    text = { Text(stringResource(R.string.scanner_tab_edit) + if (photoCount > 0) " ($photoCount)" else "") },
                )
            }
            if (sub == 0) {
                ScanTab(
                    importing = importing,
                    photoCount = photoCount,
                    onOpenCamera = onOpenCamera,
                    onImport = pickImages,
                    onEdit = { sub = 1 },
                    onOpenPdf = onOpenPdf,
                    snackbar = snackbar,
                )
            } else {
                EditTab(
                    draftVm = draftVm,
                    importing = importing,
                    onOpenCamera = onOpenCamera,
                    onImport = pickImages,
                    onCrop = onCrop,
                    onEnhance = onEnhance,
                    onSaveAsPdf = onSaveAsPdf,
                )
            }
        }
    }
}

// ---- Scan sub-tab --------------------------------------------------------------------------------

@Composable
private fun ScanTab(
    importing: Boolean,
    photoCount: Int,
    onOpenCamera: () -> Unit,
    onImport: () -> Unit,
    onEdit: () -> Unit,
    onOpenPdf: (Uri) -> Unit,
    snackbar: SnackbarHostState,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val recentVm: RecentScansViewModel = viewModel(factory = KoshViewModelFactory.Factory)
    val recents by recentVm.records.collectAsStateWithLifecycle()
    var renameTarget by remember { mutableStateOf<ScanRecord?>(null) }
    var deleteTarget by remember { mutableStateOf<ScanRecord?>(null) }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Button(onClick = onOpenCamera, modifier = Modifier.fillMaxWidth().height(64.dp)) {
            Text(stringResource(R.string.scan_document), style = MaterialTheme.typography.titleMedium)
        }
        OutlinedButton(onClick = onImport, enabled = !importing, modifier = Modifier.fillMaxWidth().height(56.dp)) {
            Text(stringResource(R.string.import_images))
        }
        if (importing) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Text(stringResource(R.string.importing), style = MaterialTheme.typography.bodySmall)
        }

        if (photoCount > 0) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.draft_in_progress), style = MaterialTheme.typography.labelMedium)
                        Text(
                            pluralStringResource(R.plurals.page_count, photoCount, photoCount),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                    TextButton(onClick = onEdit) { Text(stringResource(R.string.scanner_tab_edit)) }
                }
            }
        }

        Spacer(Modifier.height(4.dp))
        Text(stringResource(R.string.recent_scans), style = MaterialTheme.typography.titleMedium)
        if (recents.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(stringResource(R.string.recent_empty_title), style = MaterialTheme.typography.titleSmall)
                Text(
                    stringResource(R.string.recent_empty_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            recents.forEach { record ->
                RecentScanRow(
                    record = record,
                    onOpen = { onOpenPdf(Uri.parse(record.uri)) },
                    onShare = {
                        if (!sharePdf(context, Uri.parse(record.uri))) {
                            scope.launch { snackbar.showSnackbar(context.getString(R.string.reader_share_failed)) }
                        }
                    },
                    onRename = { renameTarget = record },
                    onDelete = { deleteTarget = record },
                    onForget = { recentVm.forget(record) },
                )
            }
        }

        ScanSettingsSection(snackbar)
    }

    renameTarget?.let { record ->
        var text by remember(record.id) { mutableStateOf(record.name.removeSuffix(".pdf")) }
        var error by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text(stringResource(R.string.rename_title)) },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it; error = false },
                    label = { Text(stringResource(R.string.rename_label)) },
                    suffix = { Text(".pdf") },
                    isError = error,
                    supportingText = if (error) {
                        { Text(stringResource(R.string.rename_scan_failed)) }
                    } else null,
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { if (recentVm.rename(record, text)) renameTarget = null else error = true }
                }) { Text(stringResource(R.string.rename_confirm)) }
            },
            dismissButton = { TextButton(onClick = { renameTarget = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    deleteTarget?.let { record ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.delete_title)) },
            text = { Text(stringResource(R.string.delete_body, record.name)) },
            confirmButton = {
                TextButton(onClick = {
                    deleteTarget = null
                    scope.launch {
                        val ok = recentVm.delete(record)
                        snackbar.showSnackbar(context.getString(if (ok) R.string.msg_deleted else R.string.delete_failed, record.name))
                    }
                }) { Text(stringResource(R.string.delete_confirm), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

/** Scanner-only settings (they live here, not in the app settings): enhancement, paper, quality, drafts. */
@Composable
private fun ScanSettingsSection(snackbar: SnackbarHostState) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settingsVm: SettingsViewModel = viewModel(factory = KoshViewModelFactory.Factory)
    val settings by settingsVm.settings.collectAsStateWithLifecycle()
    var open by rememberSaveable { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }

    Card(Modifier.fillMaxWidth()) {
        Column {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable { open = !open }.padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.scan_settings_title), style = MaterialTheme.typography.titleSmall)
                    Text(
                        stringResource(R.string.scan_settings_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(if (open) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null)
            }
            if (open) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(stringResource(R.string.settings_default_filter), style = MaterialTheme.typography.titleSmall)
                    Column(Modifier.selectableGroup()) {
                        val filter = settings?.defaultFilter ?: FilterMode.ORIGINAL
                        FilterMode.entries.forEach { mode ->
                            ChoiceRow(stringResource(filterLabelRes(mode)), mode == filter) { settingsVm.setDefaultFilter(mode) }
                        }
                    }
                    Text(stringResource(R.string.settings_default_paper), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
                    Column(Modifier.selectableGroup()) {
                        val paper = settings?.defaultPaper ?: PaperSize.A4
                        PaperSize.entries.forEach { p ->
                            ChoiceRow(p.name.lowercase().replaceFirstChar { it.uppercase() }, p == paper) { settingsVm.setDefaultPaper(p) }
                        }
                    }
                    Text(stringResource(R.string.settings_default_quality), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
                    Column(Modifier.selectableGroup()) {
                        val quality = settings?.defaultQuality ?: ExportQuality.STANDARD
                        ExportQuality.entries.forEach { q ->
                            val label = stringResource(if (q == ExportQuality.STANDARD) R.string.quality_standard else R.string.quality_high)
                            ChoiceRow(label, q == quality) { settingsVm.setDefaultQuality(q) }
                        }
                    }
                    Text(stringResource(R.string.scan_clear_drafts_title), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
                    Text(
                        stringResource(R.string.settings_clear_drafts_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                    OutlinedButton(onClick = { confirmClear = true }, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.settings_clear_drafts))
                    }
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.clear_drafts_title)) },
            text = { Text(stringResource(R.string.clear_drafts_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    settingsVm.clearDrafts()
                    scope.launch { snackbar.showSnackbar(context.getString(R.string.drafts_cleared)) }
                }) { Text(stringResource(R.string.clear)) }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun RecentScanRow(
    record: ScanRecord,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onForget: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val date = DateUtils.getRelativeTimeSpanString(record.createdAt, System.currentTimeMillis(), DateUtils.DAY_IN_MILLIS).toString()
    Card(Modifier.fillMaxWidth().clickable(onClick = onOpen)) {
        Row(Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(record.name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    pluralStringResource(R.plurals.page_count, record.pageCount, record.pageCount) +
                        (if (record.sizeBytes > 0) " · " + formatFileSize(record.sizeBytes) else "") + " · " + date,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.note_more, record.name))
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.export_open_pdf)) }, onClick = { menuOpen = false; onOpen() })
                    HorizontalDivider()
                    DropdownMenuItem(text = { Text(stringResource(R.string.action_share)) }, onClick = { menuOpen = false; onShare() })
                    HorizontalDivider()
                    DropdownMenuItem(text = { Text(stringResource(R.string.action_rename)) }, onClick = { menuOpen = false; onRename() })
                    HorizontalDivider()
                    DropdownMenuItem(text = { Text(stringResource(R.string.recent_forget)) }, onClick = { menuOpen = false; onForget() })
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) },
                        onClick = { menuOpen = false; onDelete() },
                    )
                }
            }
        }
    }
}

// ---- Edit sub-tab --------------------------------------------------------------------------------

/**
 * Edit layout: a slim strip of page icons down the left edge (scroll and tap to pick a page), the picked page filling the
 * rest of the screen with rotate / crop / delete at its top right corner, and the filters in a row along the bottom
 * with "Apply to all" just above it.
 */
@Composable
private fun EditTab(
    draftVm: DraftViewModel,
    importing: Boolean,
    onOpenCamera: () -> Unit,
    onImport: () -> Unit,
    onCrop: (String) -> Unit,
    @Suppress("UNUSED_PARAMETER") onEnhance: (String) -> Unit,
    onSaveAsPdf: () -> Unit,
) {
    val draft by draftVm.draft.collectAsStateWithLifecycle()
    val pages = draft?.pages.orEmpty()
    var selectedIndex by rememberSaveable { mutableIntStateOf(0) }
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var pageMenu by remember { mutableStateOf(false) }
    var addMenu by remember { mutableStateOf(false) }
    var stage by remember { mutableStateOf<Bitmap?>(null) }
    var stageFor by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()

    if (draft == null || pages.isEmpty()) {
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(stringResource(R.string.edit_empty_title), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.edit_empty_body),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
            )
            Button(onClick = onOpenCamera, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                Text(stringResource(R.string.scan_document))
            }
            OutlinedButton(onClick = onImport, enabled = !importing, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(top = 8.dp)) {
                Text(stringResource(R.string.import_images))
            }
        }
        return
    }
    val doc = draft ?: return
    val index = selectedIndex.coerceIn(0, pages.lastIndex)
    val page = pages[index]
    val number = index + 1

    // The page with crop and rotation applied; the filter is applied live on top so tapping a filter is instant.
    LaunchedEffect(page.id, page.rotationDegrees, page.cropCorners) {
        PageRenderer.renderStage(page, 1400)?.let { stage = it; stageFor = page.id }
    }
    var draggingId by remember { mutableStateOf<String?>(null) }
    val dragScope = rememberCoroutineScope()
    LaunchedEffect(index) { if (draggingId == null) listState.animateScrollToItem(index) }
    val stageImage = remember(stage) { stage?.asImageBitmap() }
    val showStage = stageFor == page.id
    val previewFilter = remember(page.filterMode) { filterFor(page.filterMode) }

    Column(Modifier.fillMaxSize()) {
        // ---- header: name, page options, save ----
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).clickable { renaming = true }.padding(vertical = 4.dp)) {
                Text(doc.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    stringResource(R.string.edit_page_of, number, pages.size),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box {
                IconButton(onClick = { pageMenu = true }) { Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.edit_more)) }
                DropdownMenu(expanded = pageMenu, onDismissRequest = { pageMenu = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.action_rename)) }, onClick = { pageMenu = false; renaming = true })
                    HorizontalDivider()
                    DropdownMenuItem(text = { Text(stringResource(R.string.page_rotate_left)) }, onClick = { pageMenu = false; draftVm.rotatePage(page.id, -90) })
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.page_move_earlier)) }, enabled = index > 0,
                        onClick = { pageMenu = false; draftVm.movePage(page.id, -1); selectedIndex = index - 1 },
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.page_move_later)) }, enabled = index < pages.lastIndex,
                        onClick = { pageMenu = false; draftVm.movePage(page.id, 1); selectedIndex = index + 1 },
                    )
                    HorizontalDivider()
                    DropdownMenuItem(text = { Text(stringResource(R.string.note_duplicate)) }, onClick = { pageMenu = false; draftVm.duplicatePage(page.id) })
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.edit_clear_all), color = MaterialTheme.colorScheme.error) },
                        onClick = { pageMenu = false; confirmClear = true },
                    )
                }
            }
            Button(onClick = onSaveAsPdf, enabled = !importing, modifier = Modifier.heightIn(min = 40.dp)) {
                Text(stringResource(R.string.edit_save_pdf))
            }
        }
        if (importing) LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp))

        // ---- page strip (left) + the page (rest of the screen) ----
        Row(Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.width(72.dp).fillMaxHeight(),
                contentPadding = PaddingValues(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(pages, key = { _, p -> p.id }) { i, p ->
                    // Press and hold a page, then drag it up or down to move it.
                    var dy by remember(p.id) { mutableFloatStateOf(0f) }
                    val dragging = draggingId == p.id
                    PageThumb(
                        page = p,
                        number = i + 1,
                        selected = i == index,
                        modifier = Modifier
                            .then(if (dragging) Modifier else Modifier.animateItem())
                            .graphicsLayer { translationY = if (dragging) dy else 0f; scaleX = if (dragging) 1.06f else 1f; scaleY = if (dragging) 1.06f else 1f }
                            .zIndex(if (dragging) 1f else 0f)
                            .pointerInput(p.id) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = {
                                        draggingId = p.id
                                        dy = 0f
                                        selectedIndex = draftVm.draft.value?.pages.orEmpty().indexOfFirst { it.id == p.id }.coerceAtLeast(0)
                                    },
                                    onDrag = { change, amount ->
                                        change.consume()
                                        dy += amount.y
                                        val step = (56.dp / 0.75f + 8.dp).toPx()
                                        val order = { draftVm.draft.value?.pages.orEmpty() }
                                        var current = order().indexOfFirst { it.id == p.id }
                                        val last = order().lastIndex
                                        while (dy > step / 2 && current in 0 until last) { draftVm.movePage(p.id, 1); current++; dy -= step }
                                        while (dy < -step / 2 && current > 0) { draftVm.movePage(p.id, -1); current--; dy += step }
                                        if (current >= 0) selectedIndex = current
                                        // Near the top or bottom of the strip: keep scrolling so the page can travel further.
                                        val info = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == p.id }
                                        if (info != null) {
                                            val centre = info.offset + dy + info.size / 2f
                                            val edge = 56.dp.toPx()
                                            val push = when {
                                                centre < edge -> -28f
                                                centre > listState.layoutInfo.viewportEndOffset - edge -> 28f
                                                else -> 0f
                                            }
                                            if (push != 0f) dragScope.launch { dy += listState.scrollBy(push) }
                                        }
                                    },
                                    onDragEnd = { draggingId = null; dy = 0f },
                                    onDragCancel = { draggingId = null; dy = 0f },
                                )
                            },
                        onClick = { selectedIndex = i },
                    )
                }
                item(key = "add-more") {
                    Box {
                        Box(
                            Modifier.fillMaxWidth().aspectRatio(0.75f).clip(RoundedCornerShape(8.dp))
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                                .clickable { addMenu = true },
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Default.Add, contentDescription = stringResource(R.string.edit_add_more)) }
                        DropdownMenu(expanded = addMenu, onDismissRequest = { addMenu = false }) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.scan_document)) }, onClick = { addMenu = false; onOpenCamera() })
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.import_images)) }, enabled = !importing,
                                onClick = { addMenu = false; onImport() },
                            )
                        }
                    }
                }
            }

            Box(Modifier.weight(1f).fillMaxHeight()) {
                if (stageImage != null && showStage) {
                    Image(
                        bitmap = stageImage,
                        contentDescription = stringResource(R.string.page_number, number),
                        contentScale = ContentScale.Fit,
                        colorFilter = previewFilter,
                        modifier = Modifier.fillMaxSize().padding(8.dp),
                    )
                } else {
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                }
                Surface(
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                    tonalElevation = 3.dp,
                    shadowElevation = 3.dp,
                ) {
                    Row {
                        val rotate = stringResource(R.string.photo_rotate)
                        val crop = stringResource(R.string.photo_crop)
                        val delete = stringResource(R.string.photo_delete)
                        IconButton(onClick = { draftVm.rotatePage(page.id, 90) }) {
                            Icon(Icons.AutoMirrored.Filled.RotateRight, contentDescription = stringResource(R.string.photo_actions_desc, rotate, number))
                        }
                        IconButton(onClick = { onCrop(page.id) }) {
                            Icon(Icons.Default.Crop, contentDescription = stringResource(R.string.photo_actions_desc, crop, number))
                        }
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = stringResource(R.string.photo_actions_desc, delete, number),
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
        }

        // ---- filters ----
        Surface(tonalElevation = 3.dp) {
            Column(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { draftVm.setFilterAll(page.filterMode) }, enabled = pages.size > 1) {
                        Text(stringResource(R.string.edit_apply_all))
                    }
                }
                LazyRow(
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(FilterMode.entries.toList(), key = { it.name }) { mode ->
                        FilterTile(mode, stageImage.takeIf { showStage }, selected = mode == page.filterMode) {
                            draftVm.setFilter(page.id, mode)
                        }
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.page_delete_title)) },
            text = { Text(stringResource(R.string.page_delete_body)) },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; draftVm.deletePage(page.id) }) {
                    Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.edit_clear_title)) },
            text = { Text(stringResource(R.string.edit_clear_body)) },
            confirmButton = {
                TextButton(onClick = { confirmClear = false; draftVm.discardDraft() }) {
                    Text(stringResource(R.string.edit_clear_all), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    if (renaming) {
        var text by remember(doc.id) { mutableStateOf(doc.title) }
        AlertDialog(
            onDismissRequest = { renaming = false },
            title = { Text(stringResource(R.string.rename_title)) },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = clampTitle(it) },
                    label = { Text(stringResource(R.string.document_name)) },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = { renaming = false; draftVm.renameDraft(text) }) { Text(stringResource(R.string.rename_confirm)) }
            },
            dismissButton = { TextButton(onClick = { renaming = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

private fun filterFor(mode: FilterMode): ColorFilter? =
    FilterMatrices.forMode(mode)?.let { ColorFilter.colorMatrix(ColorMatrix(it)) }

/** One page icon in the left strip. The selected page has a thick outline. */
@Composable
private fun PageThumb(page: ScannedPage, number: Int, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    // Re-rendered whenever anything that changes how the page looks changes (crop, rotation, filter).
    val bitmap by produceState<Bitmap?>(null, page.id, page.rotationDegrees, page.filterMode, page.cropCorners) {
        value = PageRenderer.renderThumbnail(page, 200)
    }
    val shape = RoundedCornerShape(8.dp)
    val label = stringResource(R.string.page_number, number)
    Box(
        modifier.fillMaxWidth().aspectRatio(0.75f).clip(shape)
            .border(
                if (selected) 3.dp else 1.dp,
                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                shape,
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        val bmp = bitmap
        if (bmp != null) {
            val image = remember(bmp) { bmp.asImageBitmap() }
            Image(bitmap = image, contentDescription = label, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(3.dp))
        } else {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        }
        Surface(
            color = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            shape = RoundedCornerShape(topEnd = 6.dp),
            modifier = Modifier.align(Alignment.BottomStart),
        ) {
            Text("$number", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
        }
    }
}

/** A filter shown as a small preview of the current page with that filter applied. */
@Composable
private fun FilterTile(mode: FilterMode, preview: androidx.compose.ui.graphics.ImageBitmap?, selected: Boolean, onClick: () -> Unit) {
    val colorFilter = remember(mode) { filterFor(mode) }
    val shape = RoundedCornerShape(8.dp)
    val accent = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    Column(
        Modifier.width(76.dp).clip(shape).clickable(onClick = onClick).padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(64.dp).clip(shape).border(if (selected) 3.dp else 1.dp, accent, shape)) {
            if (preview != null) {
                Image(
                    bitmap = preview,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.TopCenter,
                    colorFilter = colorFilter,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Text(
            stringResource(filterLabelRes(mode)),
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 2,
            minLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
