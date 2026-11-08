package com.kosh.android.viewer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kosh.android.R
import com.kosh.android.util.formatFileSize

/** Bottom sheets and dialogs for the reader's panels. Only the one named in [ViewerUiState.panel] is shown. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewerPanels(state: ViewerUiState, prefs: ViewerPreferences, vm: ViewerViewModel) {
    val panel = state.panel ?: return
    when (panel) {
        ViewerPanel.JUMP -> JumpDialog(state.page, state.pagesCount, onDismiss = vm::closePanel, onGo = { vm.jumpToPageText(it) })
        ViewerPanel.INFO -> InfoDialog(state.properties, state.title, onDismiss = vm::closePanel)
        else -> {
            val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ModalBottomSheet(onDismissRequest = vm::closePanel, sheetState = sheet) {
                Column(Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
                    when (panel) {
                        ViewerPanel.CONTENTS -> ContentsPanel(state, vm)
                        ViewerPanel.BOOKMARKS -> BookmarksPanel(state, vm)
                        ViewerPanel.HIGHLIGHTS -> HighlightsPanel(state, vm)
                        ViewerPanel.ZOOM -> ZoomPanel(state, prefs, vm)
                        ViewerPanel.DISPLAY -> DisplayPanel(prefs, vm)
                        ViewerPanel.AUTO_SCROLL -> AutoScrollPanel(prefs, vm)
                        else -> Unit
                    }
                }
            }
        }
    }
}

@Composable
private fun PanelTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
}

@Composable
private fun EmptyText(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(20.dp))
}

// ---- contents (outline + attachments) -------------------------------------------------------------

@Composable
private fun ContentsPanel(state: ViewerUiState, vm: ViewerViewModel) {
    var tab by remember { mutableIntStateOf(0) }
    var expanded by remember { mutableStateOf(emptySet<String>()) }
    TabRow(selectedTabIndex = tab) {
        Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text(stringResource(R.string.viewer_outline)) })
        Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text(stringResource(R.string.viewer_attachments)) })
    }
    if (tab == 0) {
        val rows = flattenOutline(state.outline, expanded)
        if (rows.isEmpty()) EmptyText(stringResource(R.string.viewer_no_outline))
        else LazyColumn(Modifier.fillMaxWidth()) {
            items(rows, key = { it.node.id }) { row ->
                val n = row.node
                Row(
                    Modifier.fillMaxWidth().clickable { vm.goToPage(n.page + 1); vm.closePanel() }
                        .padding(start = (16 + row.depth * 20).dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (n.children.isNotEmpty()) {
                        val open = n.id in expanded
                        IconButton(onClick = { expanded = if (open) expanded - n.id else expanded + n.id }) {
                            Icon(if (open) Icons.Default.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
                        }
                    } else Box(Modifier.size(48.dp))
                    Text(n.title.ifBlank { "—" }, Modifier.weight(1f).padding(vertical = 10.dp), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text("${n.page + 1}", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(start = 8.dp, end = 8.dp))
                }
            }
        }
    } else {
        if (state.attachments.isEmpty()) EmptyText(stringResource(R.string.viewer_no_attachments))
        else LazyColumn(Modifier.fillMaxWidth()) {
            items(state.attachments, key = { it.id }) { a ->
                Text(
                    a.title, Modifier.fillMaxWidth().clickable { vm.downloadAttachment(a.id); vm.closePanel() }.padding(horizontal = 20.dp, vertical = 14.dp),
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

// ---- bookmarks -------------------------------------------------------------------------------------

@Composable
private fun BookmarksPanel(state: ViewerUiState, vm: ViewerViewModel) {
    PanelTitle(stringResource(R.string.viewer_bookmarks))
    TextButton(onClick = { vm.toggleBookmark() }, modifier = Modifier.padding(horizontal = 8.dp)) {
        Text(stringResource(if (state.isBookmarked) R.string.viewer_remove_bookmark else R.string.viewer_add_bookmark, state.page))
    }
    if (state.bookmarks.isEmpty()) EmptyText(stringResource(R.string.viewer_no_bookmarks))
    else LazyColumn(Modifier.fillMaxWidth()) {
        items(state.bookmarks, key = { it.id }) { b ->
            Row(
                Modifier.fillMaxWidth().clickable { vm.goToPage(b.pageIndex + 1); vm.closePanel() }.padding(start = 20.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.viewer_page_n, b.pageIndex + 1), Modifier.weight(1f).padding(vertical = 14.dp))
                IconButton(onClick = { vm.deleteBookmark(b.id) }) { Icon(Icons.Default.Delete, stringResource(R.string.viewer_delete)) }
            }
        }
    }
}

// ---- highlights ------------------------------------------------------------------------------------

@Composable
private fun HighlightsPanel(state: ViewerUiState, vm: ViewerViewModel) {
    PanelTitle(stringResource(R.string.viewer_highlights))
    if (state.highlights.isEmpty()) EmptyText(stringResource(R.string.viewer_no_highlights))
    else LazyColumn(Modifier.fillMaxWidth()) {
        items(state.highlights, key = { it.id }) { h ->
            Row(Modifier.fillMaxWidth().clickable { vm.showHighlight(h) }.padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.Top) {
                Box(Modifier.padding(top = 4.dp).size(14.dp).clip(CircleShape).background(Color(h.colorArgb)).border(1.dp, MaterialTheme.colorScheme.outline, CircleShape))
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    val tag = if (h.fromDocument) " · " + stringResource(R.string.viewer_in_pdf) else ""
                    Text(stringResource(R.string.viewer_page_n, h.page) + tag, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (h.text.isNotBlank()) Text(h.text, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    if (!h.note.isNullOrBlank()) Text(h.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
                if (!h.fromDocument) IconButton(onClick = { vm.deleteHighlight(h.id) }) { Icon(Icons.Default.Delete, stringResource(R.string.viewer_delete)) }
            }
            HorizontalDivider()
        }
    }
}

// ---- zoom --------------------------------------------------------------------------------------------

@Composable
private fun ZoomPanel(state: ViewerUiState, prefs: ViewerPreferences, vm: ViewerViewModel) {
    PanelTitle(stringResource(R.string.viewer_zoom) + " · " + "${(state.scale * 100).toInt()}%")
    Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ChipRow {
            FilterChip(false, { vm.zoomPreset(ZoomPreset.FIT_WIDTH) }, { Text(stringResource(R.string.viewer_fit_width)) })
            FilterChip(false, { vm.zoomPreset(ZoomPreset.FIT_PAGE) }, { Text(stringResource(R.string.viewer_fit_page)) })
            FilterChip(false, { vm.zoomPreset(ZoomPreset.ACTUAL) }, { Text(stringResource(R.string.viewer_actual_size)) })
        }
        ChipRow {
            QUICK_ZOOM_STEPS.forEach { z ->
                FilterChip(kotlin.math.abs(state.scale - z) < 0.02f, { vm.zoomTo(z) }, { Text("${(z * 100).toInt()}%") })
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { vm.zoomOut() }) { Text("−") }
            TextButton(onClick = { vm.zoomIn() }) { Text("+") }
        }
        Text(stringResource(R.string.viewer_double_tap_zoom), style = MaterialTheme.typography.titleSmall)
        ChipRow {
            QUICK_ZOOM_STEPS.filter { it > 1f }.forEach { z ->
                FilterChip(kotlin.math.abs(prefs.doubleTapZoom - z) < 0.02f, { vm.updatePrefs { it.doubleTapZoom(z) } }, { Text("${(z * 100).toInt()}%") })
            }
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun ChipRow(content: @Composable () -> Unit) {
    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) { content() }
}

// ---- display -----------------------------------------------------------------------------------------

@Composable
private fun DisplayPanel(prefs: ViewerPreferences, vm: ViewerViewModel) {
    PanelTitle(stringResource(R.string.viewer_display))
    Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.viewer_theme), style = MaterialTheme.typography.titleSmall)
        ChipRow {
            ViewerTheme.entries.forEach { t ->
                FilterChip(prefs.theme == t, { vm.updatePrefs { it.theme(t) } }, { Text(stringResource(themeLabel(t))) })
            }
        }
        Text(stringResource(R.string.viewer_brightness), style = MaterialTheme.typography.titleSmall)
        SwitchRow(stringResource(R.string.viewer_system_brightness), prefs.brightness <= 0f) { on ->
            vm.updatePrefs { it.brightness(if (on) 0f else 0.7f) }
        }
        if (prefs.brightness > 0f) {
            Slider(value = prefs.brightness, onValueChange = { v -> vm.updatePrefs { it.brightness(v) } }, valueRange = 0.05f..1f)
        }
        Text(stringResource(R.string.viewer_scroll_direction), style = MaterialTheme.typography.titleSmall)
        ChipRow {
            FilterChip(prefs.scrollDirection == ScrollDirection.VERTICAL, { vm.updatePrefs { it.scrollDirection(ScrollDirection.VERTICAL) } }, { Text(stringResource(R.string.viewer_vertical)) })
            FilterChip(prefs.scrollDirection == ScrollDirection.HORIZONTAL, { vm.updatePrefs { it.scrollDirection(ScrollDirection.HORIZONTAL) } }, { Text(stringResource(R.string.viewer_horizontal)) })
        }
        SwitchRow(stringResource(R.string.viewer_lock_horizontal), prefs.lockHorizontal && prefs.scrollDirection == ScrollDirection.VERTICAL) { on ->
            vm.updatePrefs { it.lockHorizontal(on) }
        }
        SwitchRow(stringResource(R.string.viewer_snap_pages), prefs.snapPages) { on -> vm.updatePrefs { it.snapPages(on) } }
        SwitchRow(stringResource(R.string.viewer_auto_hide_toolbar), prefs.autoHideToolbar) { on -> vm.updatePrefs { it.autoHideToolbar(on) } }
        SwitchRow(stringResource(R.string.viewer_scrubber_on_scroll), prefs.scrubberOnScroll) { on -> vm.updatePrefs { it.scrubberOnScroll(on) } }
        SwitchRow(stringResource(R.string.viewer_tap_to_turn), prefs.tapToTurn) { on -> vm.updatePrefs { it.tapToTurn(on) } }
        SwitchRow(stringResource(R.string.viewer_keep_screen_on), prefs.keepScreenOn) { on -> vm.updatePrefs { it.keepScreenOn(on) } }
        SwitchRow(stringResource(R.string.viewer_remember_passwords), prefs.rememberPasswords) { on -> vm.updatePrefs { it.rememberPasswords(on) } }
        Text(stringResource(R.string.viewer_orientation), style = MaterialTheme.typography.titleSmall)
        ChipRow {
            OrientationLock.entries.forEach { o ->
                FilterChip(prefs.orientation == o, { vm.updatePrefs { it.orientation(o) } }, { Text(stringResource(orientationLabel(o))) })
            }
        }
    }
}

private fun themeLabel(t: ViewerTheme) = when (t) {
    ViewerTheme.LIGHT -> R.string.viewer_theme_light
    ViewerTheme.DARK -> R.string.viewer_theme_dark
    ViewerTheme.SEPIA -> R.string.viewer_theme_sepia
    ViewerTheme.BLACK -> R.string.viewer_theme_black
}

private fun orientationLabel(o: OrientationLock) = when (o) {
    OrientationLock.AUTO -> R.string.viewer_orientation_auto
    OrientationLock.PORTRAIT -> R.string.viewer_orientation_portrait
    OrientationLock.LANDSCAPE -> R.string.viewer_orientation_landscape
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

// ---- auto scroll --------------------------------------------------------------------------------------

@Composable
private fun AutoScrollPanel(prefs: ViewerPreferences, vm: ViewerViewModel) {
    PanelTitle(stringResource(R.string.viewer_auto_scroll))
    Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ChipRow {
            AUTO_SCROLL_PRESETS.forEachIndexed { i, px ->
                FilterChip(prefs.autoScrollSpeed == px, { vm.setAutoScrollSpeed(px) }, { Text("${i + 1}") })
            }
        }
        Slider(
            value = prefs.autoScrollSpeed.toFloat(), onValueChange = { vm.setAutoScrollSpeed(it.toInt()) },
            valueRange = MIN_AUTO_SCROLL_PX.toFloat()..MAX_AUTO_SCROLL_PX.toFloat(),
        )
        Button(onClick = { vm.startAutoScroll() }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.viewer_start)) }
    }
}

// ---- dialogs --------------------------------------------------------------------------------------------

@Composable
private fun JumpDialog(current: Int, total: Int, onDismiss: () -> Unit, onGo: (String) -> Boolean) {
    var text by remember { mutableStateOf(current.toString()) }
    var error by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.viewer_go_to_page)) },
        text = {
            OutlinedTextField(
                text, { text = it; error = false }, singleLine = true, isError = error,
                label = { Text(stringResource(R.string.viewer_page_range, total)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
        },
        confirmButton = { TextButton(onClick = { if (!onGo(text)) error = true }) { Text(stringResource(R.string.viewer_go)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.viewer_cancel)) } },
    )
}

@Composable
private fun InfoDialog(props: DocProperties?, title: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.viewer_properties)) },
        text = {
            if (props == null) Text(stringResource(R.string.viewer_properties_unavailable))
            else Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                InfoRow(R.string.viewer_prop_file, title)
                InfoRow(R.string.viewer_prop_title, props.title)
                InfoRow(R.string.viewer_prop_author, props.author)
                InfoRow(R.string.viewer_prop_subject, props.subject)
                InfoRow(R.string.viewer_prop_keywords, props.keywords)
                InfoRow(R.string.viewer_prop_created, props.creationDate)
                InfoRow(R.string.viewer_prop_modified, props.modDate)
                InfoRow(R.string.viewer_prop_creator, props.creator)
                InfoRow(R.string.viewer_prop_producer, props.producer)
                InfoRow(R.string.viewer_prop_version, props.pdfVersion)
                if (props.sizeBytes > 0) InfoRow(R.string.viewer_prop_size, formatFileSize(props.sizeBytes))
                InfoRow(R.string.viewer_prop_language, props.language)
                if (props.encryptFilterName.isNotBlank()) InfoRow(R.string.viewer_prop_encrypted, props.encryptFilterName)
                props.custom.forEach { (k, v) -> InfoRow(k, v) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.viewer_close)) } },
    )
}

@Composable
private fun InfoRow(label: Int, value: String) = InfoRow(stringResource(label), value)

@Composable
private fun InfoRow(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value.ifBlank { "-" })
    }
}
