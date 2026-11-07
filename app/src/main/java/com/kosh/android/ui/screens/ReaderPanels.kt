package com.kosh.android.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kosh.android.R
import com.kosh.android.data.db.AnnotationType
import com.kosh.android.data.db.Bookmark
import com.kosh.android.data.db.PdfAnnotation
import com.kosh.android.reader.MAX_AUTO_SCROLL_SPEED
import com.kosh.android.reader.MIN_AUTO_SCROLL_SPEED
import com.kosh.android.reader.ReaderLayout
import com.kosh.android.reader.ReaderPreferences
import com.kosh.android.reader.ReaderTheme

/** Bookmarks and annotation lists. Both are the user's own app data, separate from the PDF. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookmarksSheet(
    bookmarks: List<Bookmark>,
    annotations: List<PdfAnnotation>,
    currentPage: Int,
    onAddBookmark: () -> Unit,
    onGoTo: (Int) -> Unit,
    onDeleteBookmark: (String) -> Unit,
    onDeleteAnnotation: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var tab by remember { mutableIntStateOf(0) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        PrimaryTabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text(stringResource(R.string.panel_bookmarks, bookmarks.size)) })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text(stringResource(R.string.panel_annotations, annotations.size)) })
        }
        if (tab == 0) {
            val alreadyHere = bookmarks.any { it.pageIndex == currentPage }
            Button(
                onClick = onAddBookmark,
                enabled = !alreadyHere,
                modifier = Modifier.fillMaxWidth().padding(16.dp).heightIn(min = 48.dp),
            ) {
                Text(
                    if (alreadyHere) stringResource(R.string.bookmark_exists, currentPage + 1)
                    else stringResource(R.string.bookmark_add_page, currentPage + 1),
                )
            }
            if (bookmarks.isEmpty()) {
                Text(
                    stringResource(R.string.bookmarks_empty),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp),
                )
            } else {
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(bookmarks, key = { it.id }) { b ->
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable { onGoTo(b.pageIndex); onDismiss() }.padding(start = 16.dp, end = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(R.string.reader_page_label, b.pageIndex + 1), style = MaterialTheme.typography.titleSmall)
                                if (!b.label.isNullOrBlank()) Text(b.label, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            IconButton(onClick = { onDeleteBookmark(b.id) }) {
                                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.bookmark_delete, b.pageIndex + 1))
                            }
                        }
                        HorizontalDivider()
                    }
                }
            }
        } else {
            Text(
                stringResource(R.string.annotations_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
            if (annotations.isEmpty()) {
                Text(stringResource(R.string.annotations_empty), modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp))
            } else {
                val sorted = annotations.sortedWith(compareBy({ it.pageIndex }, { it.createdAt }))
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(sorted, key = { it.id }) { a ->
                        val kind = stringResource(
                            when (a.annotationType) {
                                AnnotationType.PEN -> R.string.tool_pen
                                AnnotationType.HIGHLIGHTER -> R.string.tool_highlighter
                                AnnotationType.TEXT -> R.string.tool_note
                            },
                        )
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable { onGoTo(a.pageIndex); onDismiss() }.padding(start = 16.dp, end = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(R.string.reader_page_label, a.pageIndex + 1), style = MaterialTheme.typography.titleSmall)
                                Text(
                                    if (a.note.isNullOrBlank()) kind else "$kind · ${a.note}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            IconButton(onClick = { onDeleteAnnotation(a.id) }) {
                                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.annotation_delete, a.pageIndex + 1, kind))
                            }
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
        androidx.compose.foundation.layout.Spacer(Modifier.padding(8.dp))
    }
}

@Composable
fun BookmarkDialog(page: Int, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var label by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.bookmark_add_title, page + 1)) },
        text = {
            OutlinedTextField(
                value = label,
                onValueChange = { label = it.take(60) },
                label = { Text(stringResource(R.string.bookmark_label_optional)) },
                singleLine = true,
            )
        },
        confirmButton = { TextButton(onClick = { onSave(label) }) { Text(stringResource(R.string.bookmark_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
fun TextNoteDialog(onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.note_dialog_title)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(200) },
                label = { Text(stringResource(R.string.note_dialog_label)) },
                minLines = 2,
                maxLines = 5,
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(text.trim()) }, enabled = text.isNotBlank()) { Text(stringResource(R.string.bookmark_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** Reading theme, explicit page-colour inversion, and layout. Nothing here modifies the PDF. */
@Composable
fun DisplaySettingsDialog(
    prefs: ReaderPreferences,
    onTheme: (ReaderTheme) -> Unit,
    onInvert: (Boolean) -> Unit,
    onLayout: (ReaderLayout) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.display_title)) },
        text = {
            Column {
                Text(stringResource(R.string.display_theme), style = MaterialTheme.typography.titleSmall)
                Column(Modifier.selectableGroup()) {
                    DisplayChoice(stringResource(R.string.theme_system), prefs.theme == ReaderTheme.SYSTEM) { onTheme(ReaderTheme.SYSTEM) }
                    DisplayChoice(stringResource(R.string.theme_light), prefs.theme == ReaderTheme.LIGHT) { onTheme(ReaderTheme.LIGHT) }
                    DisplayChoice(stringResource(R.string.theme_dark), prefs.theme == ReaderTheme.DARK) { onTheme(ReaderTheme.DARK) }
                }
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 56.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(Modifier.weight(1f).padding(end = 8.dp)) {
                        Text(stringResource(R.string.display_invert))
                        Text(
                            stringResource(R.string.display_invert_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = prefs.invertPages, onCheckedChange = onInvert)
                }
                Text(stringResource(R.string.display_layout), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
                Column(Modifier.selectableGroup()) {
                    DisplayChoice(stringResource(R.string.layout_continuous), prefs.layout == ReaderLayout.CONTINUOUS) { onLayout(ReaderLayout.CONTINUOUS) }
                    DisplayChoice(stringResource(R.string.layout_single), prefs.layout == ReaderLayout.SINGLE_PAGE) { onLayout(ReaderLayout.SINGLE_PAGE) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.notes_sort_done)) } },
    )
}

@Composable
private fun DisplayChoice(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(selected = selected, onClick = onClick, role = Role.RadioButton),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(label, modifier = Modifier.padding(start = 12.dp))
    }
}

@Composable
fun AutoScrollDialog(speed: Float, running: Boolean, onSpeed: (Float) -> Unit, onStart: () -> Unit, onStop: () -> Unit, onDismiss: () -> Unit) {
    var value by remember { mutableFloatStateOf(speed) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.autoscroll_title)) },
        text = {
            Column {
                Text(stringResource(R.string.autoscroll_speed, value))
                Slider(
                    value = value,
                    onValueChange = { value = it },
                    onValueChangeFinished = { onSpeed(value) },
                    valueRange = MIN_AUTO_SCROLL_SPEED..MAX_AUTO_SCROLL_SPEED,
                )
                Text(stringResource(R.string.autoscroll_hint), style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(onClick = { onSpeed(value); if (running) onStop() else onStart(); onDismiss() }) {
                Text(stringResource(if (running) R.string.autoscroll_stop else R.string.autoscroll_start))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
