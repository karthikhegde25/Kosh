package com.kosh.android.ui.screens

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.automirrored.filled.StickyNote2
import androidx.compose.material3.Icon
import java.io.File
import kotlinx.coroutines.launch
import com.kosh.android.ui.components.sharePdfs
import com.kosh.android.tools.PendingToolFiles
import com.kosh.android.PdfListViewModel
import com.kosh.android.KoshViewModelFactory
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Alignment
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.LibraryAdd
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Close
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.clickable
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.kosh.android.DraftViewModel
import com.kosh.android.R
import com.kosh.android.navigation.Routes
import com.kosh.android.tools.PdfTool
import com.kosh.android.ui.components.KoshTopBar

/**
 * The three tabs (Home, Tools, Notes) under one shared top row: logo, app name and app settings.
 * Each tab keeps its own tools and settings inside its own content, so the areas never mix.
 */
@Composable
fun MainScreen(
    draftVm: DraftViewModel,
    onOpenPdf: (Uri) -> Unit,
    onOpenNote: (String) -> Unit,
    onOpenCamera: () -> Unit,
    onOpenCrop: (String) -> Unit,
    onOpenEnhance: (String) -> Unit,
    onSaveAsPdf: () -> Unit,
    onOpenTool: (PdfTool) -> Unit,
    onOpenSettings: () -> Unit,
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val pdfVm: PdfListViewModel = viewModel(factory = KoshViewModelFactory.Factory)
    val picked by pdfVm.selected.collectAsStateWithLifecycle()
    val selecting = tab == 0 && picked.isNotEmpty()
    var confirmDelete by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { KoshTopBar(onOpenSettings) },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            // While PDFs are picked on Home, the three tabs give way to what can be done with them.
            if (selecting) {
                SelectionBar(
                    count = picked.size,
                    canMerge = picked.size > 1 && pdfVm.selectedFiles().all { !it.kind.isOffice },
                    onClose = { pdfVm.clearSelection() },
                    onShare = {
                        val uris = pdfVm.selectedFiles().map { Uri.fromFile(File(it.path)) }
                        if (!sharePdfs(context, uris, pdfVm.selectedFiles().map { it.kind.mime }.distinct().singleOrNull() ?: "*/*")) scope.launch { snackbar.showSnackbar(context.getString(R.string.reader_share_failed)) }
                    },
                    onMerge = {
                        PendingToolFiles.put(pdfVm.selectedFiles().map { Uri.fromFile(File(it.path)) })
                        pdfVm.clearSelection()
                        onOpenTool(PdfTool.MERGE)
                    },
                    onDelete = { confirmDelete = true },
                )
            } else NavigationBar {
                NavigationBarItem(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    icon = { Icon(Icons.Default.Home, contentDescription = null) },
                    label = { Text(stringResource(R.string.tab_home)) },
                )
                NavigationBarItem(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    icon = { Icon(Icons.Default.Build, contentDescription = null) },
                    label = { Text(stringResource(R.string.tab_tools)) },
                )
                NavigationBarItem(
                    selected = tab == 2,
                    onClick = { tab = 2 },
                    icon = { Icon(Icons.AutoMirrored.Filled.StickyNote2, contentDescription = null) },
                    label = { Text(stringResource(R.string.tab_notes)) },
                )
            }
        },
    ) { padding ->
        if (confirmDelete) {
            val count = picked.size
            AlertDialog(
                onDismissRequest = { confirmDelete = false },
                title = { Text(context.resources.getQuantityString(R.plurals.delete_many_title, count, count)) },
                text = { Text(stringResource(R.string.delete_many_body)) },
                confirmButton = {
                    TextButton(onClick = {
                        confirmDelete = false
                        scope.launch {
                            val failed = pdfVm.deleteSelected()
                            val message = if (failed == 0) context.resources.getQuantityString(R.plurals.msg_deleted_many, count, count)
                            else context.resources.getQuantityString(R.plurals.delete_many_failed, failed, failed)
                            snackbar.showSnackbar(message)
                        }
                    }) { Text(stringResource(R.string.delete_confirm), color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } },
            )
        }
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (tab) {
                0 -> PdfHomeScreen(onOpenPdf = onOpenPdf)
                1 -> ToolsScreen(
                    draftVm = draftVm,
                    onOpenCamera = onOpenCamera,
                    onOpenPdf = onOpenPdf,
                    onCrop = onOpenCrop,
                    onEnhance = onOpenEnhance,
                    onSaveAsPdf = onSaveAsPdf,
                    onOpenTool = onOpenTool,
                )
                else -> NotesScreen(
                    onOpenNote = onOpenNote,
                    onNewNote = { onOpenNote(Routes.NEW_NOTE) },
                )
            }
        }
    }
}

/** Replaces the tab bar while PDFs are picked on Home: share them, merge them (two or more), or delete them. */
@Composable
private fun SelectionBar(
    count: Int,
    canMerge: Boolean,
    onClose: () -> Unit,
    onShare: () -> Unit,
    onMerge: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(tonalElevation = 3.dp, shadowElevation = 3.dp) {
        Row(Modifier.fillMaxWidth().navigationBarsPadding().heightIn(min = 72.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.Default.Close, contentDescription = stringResource(R.string.selection_close)) }
            Text(stringResource(R.string.selection_count, count), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, maxLines = 1)
            SelectionAction(Icons.Default.Share, R.string.action_share, onShare)
            if (canMerge) SelectionAction(Icons.Default.LibraryAdd, R.string.action_merge, onMerge)
            SelectionAction(Icons.Default.Delete, R.string.action_delete, onDelete, tint = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun SelectionAction(icon: ImageVector, label: Int, onClick: () -> Unit, tint: Color = Color.Unspecified) {
    Column(
        Modifier.clickable(onClick = onClick).widthIn(min = 72.dp).padding(vertical = 8.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, tint = if (tint == Color.Unspecified) MaterialTheme.colorScheme.onSurface else tint)
        Text(stringResource(label), style = MaterialTheme.typography.labelMedium, color = if (tint == Color.Unspecified) MaterialTheme.colorScheme.onSurface else tint)
    }
}
