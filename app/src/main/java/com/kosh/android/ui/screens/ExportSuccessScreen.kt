package com.kosh.android.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kosh.android.R
import com.kosh.android.scanner.ExportState
import com.kosh.android.scanner.ExportViewModel
import com.kosh.android.ui.components.sharePdf
import com.kosh.android.util.formatFileSize
import kotlinx.coroutines.launch

/** Shown after a PDF was created: open it, share it, rename it or start another scan. */
@Composable
fun ExportSuccessScreen(
    exportVm: ExportViewModel,
    onOpen: (Uri) -> Unit,
    onScanMore: () -> Unit,
    onDone: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by exportVm.state.collectAsStateWithLifecycle()
    val result = (state as? ExportState.Success)?.result
    val snackbar = remember { SnackbarHostState() }
    var renaming by remember { mutableStateOf(false) }

    if (result == null) {
        // Nothing to show (for example after the process was recreated): go home.
        LaunchedEffect(Unit) { onDone() }
        return
    }
    BackHandler { onDone() }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(stringResource(R.string.export_success_title), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
            Text(result.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp), textAlign = TextAlign.Center)
            Text(
                pluralStringResource(R.plurals.page_count, result.pageCount, result.pageCount) +
                    (result.sizeBytes?.takeIf { it > 0 }?.let { " · " + formatFileSize(it) } ?: ""),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 24.dp),
            )
            Button(onClick = { onOpen(result.uri) }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                Text(stringResource(R.string.export_open_pdf))
            }
            Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        if (!sharePdf(context, result.uri)) {
                            scope.launch { snackbar.showSnackbar(context.getString(R.string.reader_share_failed)) }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                ) { Text(stringResource(R.string.action_share)) }
                OutlinedButton(onClick = { renaming = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                    Text(stringResource(R.string.action_rename))
                }
                OutlinedButton(onClick = onScanMore, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                    Text(stringResource(R.string.export_scan_more))
                }
                TextButton(onClick = onDone) { Text(stringResource(R.string.export_finish)) }
            }
        }
    }

    if (renaming) {
        var text by remember(result.name) { mutableStateOf(result.name.removeSuffix(".pdf")) }
        var error by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { renaming = false },
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
                    if (exportVm.rename(text)) renaming = false else error = true
                }) { Text(stringResource(R.string.rename_confirm)) }
            },
            dismissButton = { TextButton(onClick = { renaming = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}
