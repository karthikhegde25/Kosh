package com.kosh.android.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kosh.android.DraftViewModel
import com.kosh.android.R
import com.kosh.android.pdf.ExportQuality
import com.kosh.android.pdf.LayoutMode
import com.kosh.android.pdf.PageOrientation
import com.kosh.android.pdf.PaperSize
import com.kosh.android.scanner.ExportState
import com.kosh.android.scanner.ExportViewModel
import com.kosh.android.util.clampTitle
import com.kosh.android.util.suggestedExportName

/**
 * Last step of a scan: choose how the images become pages ("Images as pages" or "Images on pages"),
 * name the file, then Done creates the PDF where the user picks.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportScreen(
    draftVm: DraftViewModel,
    exportVm: ExportViewModel,
    onBack: () -> Unit,
    onSuccess: () -> Unit,
) {
    val draft by draftVm.draft.collectAsStateWithLifecycle()
    val options by exportVm.options.collectAsStateWithLifecycle()
    val state by exportVm.state.collectAsStateWithLifecycle()
    val doc = draft
    if (doc == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    var name by remember(doc.id) { mutableStateOf(doc.title) }
    val running = state is ExportState.Running

    val createDocument = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        // Cancelling the picker is not an error: nothing happens and the user can choose again.
        if (uri != null) exportVm.start(uri, name.trim().ifEmpty { doc.title }, doc.pages)
    }

    LaunchedEffect(state) { if (state is ExportState.Success) onSuccess() }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.export_screen_title)) }, navigationIcon = { ReaderBackButton(onBack) }) },
        bottomBar = {
            androidx.compose.material3.Surface(tonalElevation = 3.dp) {
                Button(
                    onClick = { createDocument.launch(suggestedExportName(name.trim().ifEmpty { doc.title }, "pdf")) },
                    enabled = !running && doc.pages.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth().padding(16.dp).heightIn(min = 56.dp),
                ) { Text(stringResource(R.string.export_done_create)) }
            }
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = clampTitle(it) },
                label = { Text(stringResource(R.string.document_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            Text(
                pluralStringResource(R.plurals.page_count, doc.pages.size, doc.pages.size),
                style = MaterialTheme.typography.labelLarge,
            )

            Text(stringResource(R.string.export_how_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 4.dp))
            Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LayoutOption(
                    selected = options.layout == LayoutMode.AS_PAGES,
                    title = stringResource(R.string.export_as_pages),
                    description = stringResource(R.string.export_as_pages_desc),
                ) { exportVm.setLayout(LayoutMode.AS_PAGES) }
                LayoutOption(
                    selected = options.layout == LayoutMode.ON_PAGES,
                    title = stringResource(R.string.export_on_pages),
                    description = stringResource(R.string.export_on_pages_desc),
                ) { exportVm.setLayout(LayoutMode.ON_PAGES) }
            }

            if (options.layout == LayoutMode.ON_PAGES) {
                Text(stringResource(R.string.export_paper), style = MaterialTheme.typography.titleSmall)
                ChipRow(PaperSize.entries, options.paper, { it.name.lowercase().replaceFirstChar { c -> c.uppercase() } }) { exportVm.setPaper(it) }
                Text(stringResource(R.string.export_orientation), style = MaterialTheme.typography.titleSmall)
                ChipRow(
                    PageOrientation.entries,
                    options.orientation,
                    { orientationLabel(it) },
                ) { exportVm.setOrientation(it) }
            }

            Text(stringResource(R.string.export_quality), style = MaterialTheme.typography.titleSmall)
            ChipRow(ExportQuality.entries, options.quality, { qualityLabel(it) }) { exportVm.setQuality(it) }
            Text(
                stringResource(R.string.export_location_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 16.dp),
            )
        }
    }

    when (val s = state) {
        is ExportState.Running -> AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.export_creating)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.export_progress, (s.done + 1).coerceAtMost(s.total), s.total))
                    if (s.total > 0) LinearProgressIndicator(progress = { s.done / s.total.toFloat() }, modifier = Modifier.fillMaxWidth())
                    else CircularProgressIndicator()
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = exportVm::cancel) { Text(stringResource(R.string.cancel)) } },
        )
        is ExportState.Failed -> AlertDialog(
            onDismissRequest = exportVm::reset,
            title = { Text(stringResource(R.string.export_failed_title)) },
            text = { Text(stringResource(R.string.export_failed_body)) },
            confirmButton = { TextButton(onClick = exportVm::retry) { Text(stringResource(R.string.editor_retry)) } },
            dismissButton = { TextButton(onClick = exportVm::reset) { Text(stringResource(R.string.cancel)) } },
        )
        else -> Unit
    }
}

@Composable
private fun orientationLabel(o: PageOrientation) = stringResource(
    when (o) {
        PageOrientation.AUTO -> R.string.orientation_auto
        PageOrientation.PORTRAIT -> R.string.orientation_portrait
        PageOrientation.LANDSCAPE -> R.string.orientation_landscape
    },
)

@Composable
private fun qualityLabel(q: ExportQuality) = stringResource(
    when (q) {
        ExportQuality.STANDARD -> R.string.quality_standard
        ExportQuality.HIGH -> R.string.quality_high
    },
)

@Composable
private fun <T> ChipRow(items: List<T>, selected: T, label: @Composable (T) -> String, onSelect: (T) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { item ->
            FilterChip(
                selected = item == selected,
                onClick = { onSelect(item) },
                label = { Text(label(item)) },
                leadingIcon = if (item == selected) {
                    { Icon(Icons.Default.Check, contentDescription = null) }
                } else null,
            )
        }
    }
}

@Composable
private fun LayoutOption(selected: Boolean, title: String, description: String, onClick: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = selected, onClick = null)
            Column(Modifier.padding(start = 12.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(description, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
