package com.kosh.android.viewer

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.print.PrintManager
import android.util.Base64
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material3.FilterChip
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kosh.android.KoshViewModelFactory
import com.kosh.android.R
import com.kosh.android.reader.UriPrintAdapter
import com.kosh.android.ui.components.sharePdf

@Composable
fun PdfViewerScreen(onBack: () -> Unit, onOpenPdf: (Uri) -> Unit) {
    val vm: ViewerViewModel = viewModel(factory = KoshViewModelFactory.Factory)
    val state by vm.state.collectAsStateWithLifecycle()
    val prefs by vm.prefs.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var pendingAttachment by remember { mutableStateOf<ViewerEvent.AttachmentReady?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var pendingSaveAs by remember { mutableStateOf<ViewerEvent.DoodleSaveAs?>(null) }
    val saveAsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { dest ->
        val p = pendingSaveAs
        pendingSaveAs = null
        if (p != null) vm.finishSaveAs(p.tempPath, dest)
    }
    val doodleSavedFormat = stringResource(R.string.viewer_doodle_saved)
    val openLabel = stringResource(R.string.viewer_open)

    ViewerWindowEffects(prefs)

    val saveAttachment = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { dest ->
        val att = pendingAttachment
        pendingAttachment = null
        if (dest != null && att != null) {
            val ok = runCatching {
                context.contentResolver.openOutputStream(dest)?.use { it.write(Base64.decode(att.base64, Base64.DEFAULT)) } != null
            }.getOrDefault(false)
            vm.postMessage(if (ok) R.string.viewer_attachment_saved else R.string.viewer_attachment_failed)
        }
    }

    val messageTexts = mapOf(
        R.string.viewer_attachment_saved to stringResource(R.string.viewer_attachment_saved),
        R.string.viewer_attachment_failed to stringResource(R.string.viewer_attachment_failed),
        R.string.viewer_copied to stringResource(R.string.viewer_copied),
        R.string.viewer_doodle_save_failed to stringResource(R.string.viewer_doodle_save_failed),
        R.string.viewer_doodle_saved_picked to stringResource(R.string.viewer_doodle_saved_picked),
    )
    LaunchedEffect(Unit) {
        vm.events.collect { e ->
            when (e) {
                is ViewerEvent.OpenLink -> openExternalLink(context, e.href)
                is ViewerEvent.AttachmentReady -> { pendingAttachment = e; saveAttachment.launch(e.fileName) }
                is ViewerEvent.Message -> snackbar.showSnackbar(messageTexts[e.res].orEmpty())
                is ViewerEvent.DoodleSaveAs -> { pendingSaveAs = e; saveAsLauncher.launch(e.suggestedName) }
                is ViewerEvent.DoodleSaved -> scope.launch {
                    val r = snackbar.showSnackbar(doodleSavedFormat.format(e.name), actionLabel = openLabel, duration = SnackbarDuration.Long)
                    if (r == SnackbarResult.ActionPerformed) onOpenPdf(Uri.parse(e.uri))
                }
            }
        }
    }

    BackHandler(enabled = state.doodleActive || state.panel != null || state.searchOpen || state.selection != null || state.tappedHighlight != null || state.autoScrollActive) {
        when {
            state.doodleActive -> vm.exitDoodle()
            state.panel != null -> vm.closePanel()
            state.autoScrollActive -> vm.stopAutoScroll()
            state.selection != null || state.tappedHighlight != null -> vm.dismissSelection()
            state.searchOpen -> vm.closeSearch()
        }
    }

    val uri = remember(vm.uriString) { vm.uriString?.let { Uri.parse(it) } }

    key(state.viewerKey) {
        val viewer = remember { PdfViewer(context) }
        DisposableEffect(viewer) {
            vm.attach(viewer)
            onDispose {
                vm.detach(viewer)
                viewer.destroy()
            }
        }
        LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { viewer.onPause() }
        LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewer.onResume() }

        // The reading theme only tints the pages; the surface behind them follows the system light/dark mode.
        val darkSystem = isSystemInDarkTheme()
        val surfaceCss = if (darkSystem) "#1E1F22" else "#E3E5E8"
        DisposableEffect(viewer, surfaceCss) { viewer.setBackgroundCss(surfaceCss); onDispose { } }
        Box(Modifier.fillMaxSize().background(Color(android.graphics.Color.parseColor(surfaceCss)))) {
            if (!state.crashed) AndroidView(factory = { viewer }, modifier = Modifier.fillMaxSize())

            // ---- loading / failure / crash ----
            when {
                state.crashed -> CenteredMessage(stringResource(R.string.viewer_crashed)) {
                    Button(onClick = { vm.reloadViewer() }) { Text(stringResource(R.string.viewer_reload)) }
                }
                state.phase == LoadPhase.FAILED -> CenteredMessage(state.errorMessage ?: stringResource(R.string.viewer_open_failed)) {
                    Button(onClick = onBack) { Text(stringResource(R.string.viewer_back)) }
                }
                state.phase == LoadPhase.LOADING -> Box(Modifier.align(Alignment.Center)) {
                    if (state.progress in 1..99) LinearProgressIndicator(progress = { state.progress / 100f }, modifier = Modifier.padding(horizontal = 48.dp).fillMaxWidth())
                    else CircularProgressIndicator()
                }
            }

            // ---- top bar / search bar ----
            AnimatedVisibility(
                visible = state.phase == LoadPhase.READY && !state.autoScrollActive && !state.doodleActive && (state.toolbarVisible || state.searchOpen),
                enter = fadeIn(), exit = fadeOut(),
                modifier = Modifier.align(Alignment.TopCenter),
            ) {
                Surface(tonalElevation = 3.dp, shadowElevation = 3.dp, modifier = Modifier.fillMaxWidth()) {
                    if (state.searchOpen) {
                        SearchBar(state, vm)
                    } else {
                        Row(Modifier.statusBarsPadding().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.viewer_back)) }
                            Column(Modifier.weight(1f).clickable { vm.openPanel(ViewerPanel.JUMP) }.padding(vertical = 4.dp)) {
                                Text(state.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                                if (state.pagesCount > 0) {
                                    Text(
                                        stringResource(R.string.viewer_page_of, state.page, state.pagesCount),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            IconButton(onClick = { vm.openSearch() }) { Icon(Icons.Default.Search, stringResource(R.string.viewer_search)) }
                            IconButton(onClick = { vm.toggleBookmark() }) {
                                Icon(
                                    if (state.isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                    stringResource(R.string.viewer_bookmark),
                                )
                            }
                            Box {
                                IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.MoreVert, stringResource(R.string.viewer_more)) }
                                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                    @Composable fun item(label: Int, first: Boolean = false, action: () -> Unit) {
                                        if (!first) HorizontalDivider()
                                        DropdownMenuItem(
                                            text = { Text(stringResource(label)) },
                                            onClick = { menuOpen = false; action() },
                                        )
                                    }
                                    item(R.string.viewer_contents, first = true) { vm.openPanel(ViewerPanel.CONTENTS) }
                                    item(R.string.viewer_go_to_page) { vm.openPanel(ViewerPanel.JUMP) }
                                    item(R.string.viewer_bookmarks) { vm.openPanel(ViewerPanel.BOOKMARKS) }
                                    item(R.string.viewer_highlights) { vm.openPanel(ViewerPanel.HIGHLIGHTS) }
                                    item(R.string.viewer_zoom) { vm.openPanel(ViewerPanel.ZOOM) }
                                    item(R.string.viewer_display) { vm.openPanel(ViewerPanel.DISPLAY) }
                                    item(R.string.viewer_auto_scroll) { vm.openPanel(ViewerPanel.AUTO_SCROLL) }
                                    item(R.string.viewer_doodle) { vm.startDoodle() }
                                    item(R.string.viewer_rotate) { vm.rotate() }
                                    item(R.string.viewer_print) {
                                        if (uri != null) printPdf(context, uri, state.title, state.pagesCount)
                                    }
                                    item(R.string.viewer_share) {
                                        if (uri != null && !sharePdf(context, uri)) vm.postMessage(R.string.viewer_share_failed)
                                    }
                                    item(R.string.viewer_properties) { vm.openPanel(ViewerPanel.INFO) }
                                }
                            }
                        }
                    }
                }
            }

            // ---- fast-scroll handle at the screen edge (like Google Drive): appears while scrolling, drag it to jump ----
            if (state.phase == LoadPhase.READY && !state.autoScrollActive && !state.doodleActive && state.pagesCount > 1 && !state.searchOpen) {
                FastScroller(
                    ratio = state.scrollRatio,
                    page = state.page,
                    pages = state.pagesCount,
                    horizontal = state.horizontalScroll,
                    visible = state.scrubberVisible,
                    onScrollTo = { vm.scrollToRatio(it) },
                    modifier = Modifier.align(Alignment.TopStart).fillMaxSize(),
                )
            }

            // ---- auto scroll controls ----
            if (state.autoScrollActive) {
                Surface(
                    tonalElevation = 6.dp, shadowElevation = 6.dp, shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(16.dp),
                ) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = { vm.toggleAutoScrollPause() }) {
                                Text(stringResource(if (state.autoScrollPaused) R.string.viewer_resume else R.string.viewer_pause))
                            }
                            TextButton(onClick = { vm.stopAutoScroll() }) { Text(stringResource(R.string.viewer_stop)) }
                        }
                        Slider(
                            value = prefs.autoScrollSpeed.toFloat(),
                            onValueChange = { vm.setAutoScrollSpeed(it.toInt()) },
                            valueRange = MIN_AUTO_SCROLL_PX.toFloat()..MAX_AUTO_SCROLL_PX.toFloat(),
                            modifier = Modifier.fillMaxWidth(0.7f),
                        )
                    }
                }
            }

            // ---- doodle: tools along the bottom, save at the top right ----
            if (state.doodleActive) {
                Surface(tonalElevation = 3.dp, shadowElevation = 3.dp, modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth()) {
                    Row(Modifier.statusBarsPadding().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { vm.exitDoodle() }) { Icon(Icons.Default.Close, stringResource(R.string.viewer_doodle_close)) }
                        Text(stringResource(R.string.viewer_doodle), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                        IconButton(onClick = { vm.doodleUndo() }, enabled = state.doodleUndoSteps > 0) {
                            Icon(Icons.AutoMirrored.Filled.Undo, stringResource(R.string.viewer_doodle_undo))
                        }
                        if (state.doodleStrokes > 0) {
                            Button(onClick = { vm.saveDoodle() }, enabled = !state.doodleSaving, modifier = Modifier.padding(start = 4.dp, end = 8.dp)) {
                                Text(stringResource(if (state.doodleSaving) R.string.viewer_doodle_saving else R.string.viewer_doodle_save))
                            }
                        }
                    }
                    if (state.doodleSaving) LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                Surface(
                    tonalElevation = 3.dp, shadowElevation = 3.dp,
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
                ) {
                    Column(Modifier.navigationBarsPadding().padding(vertical = 6.dp)) {
                        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            DOODLE_COLORS.forEachIndexed { i, c ->
                                val selected = !state.doodleEraser && state.doodleColor == c
                                Box(
                                    Modifier.padding(horizontal = 4.dp).size(36.dp).clip(CircleShape).background(Color(c))
                                        .border(if (selected) 3.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, CircleShape)
                                        .clickable { vm.setDoodleColor(c) },
                                )
                            }
                        }
                        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            FilterChip(!state.doodleEraser, { vm.setDoodleEraser(false) }, { Text(stringResource(R.string.viewer_doodle_pen)) }, modifier = Modifier.padding(end = 8.dp))
                            FilterChip(state.doodleEraser, { vm.setDoodleEraser(true) }, { Text(stringResource(R.string.viewer_doodle_eraser)) }, modifier = Modifier.padding(end = 16.dp))
                            DoodleSize.entries.forEach { size ->
                                val label = stringResource(sizeLabel(size))
                                FilterChip(state.doodleSize == size, { vm.setDoodleSize(size) }, { Text(label) }, modifier = Modifier.padding(end = 8.dp))
                            }
                        }
                    }
                }
            }

            // ---- selection / highlight action bars ----
            state.selection?.let { sel ->
                AnchoredBar(sel.anchor) {
                    HighlightColors(onPick = { vm.highlightSelection(it) })
                    CopyButton(sel.text, vm)
                }
            }
            state.tappedHighlight?.let { (h, anchor) ->
                var noteDialog by remember(h.id) { mutableStateOf(false) }
                AnchoredBar(anchor) {
                    if (!h.fromDocument) HighlightColors(onPick = { vm.changeHighlightColor(h.id, it) })
                    CopyButton(h.text, vm)
                    if (!h.fromDocument) {
                        IconButton(onClick = { noteDialog = true }) { Icon(Icons.Default.Edit, stringResource(R.string.viewer_note)) }
                        IconButton(onClick = { vm.deleteHighlight(h.id) }) { Icon(Icons.Default.Delete, stringResource(R.string.viewer_delete)) }
                    }
                }
                if (noteDialog) NoteDialog(h.note.orEmpty(), onDismiss = { noteDialog = false }) { vm.setHighlightNote(h.id, it); noteDialog = false }
            }

            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 64.dp))
        }
    }

    ViewerPanels(state, prefs, vm)

    if (state.doodleConfirmDiscard) {
        AlertDialog(
            onDismissRequest = { vm.dismissDiscardPrompt() },
            title = { Text(stringResource(R.string.viewer_doodle_discard_title)) },
            text = { Text(stringResource(R.string.viewer_doodle_discard_body)) },
            confirmButton = { TextButton(onClick = { vm.exitDoodle(force = true) }) { Text(stringResource(R.string.viewer_doodle_discard), color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { vm.dismissDiscardPrompt() }) { Text(stringResource(R.string.viewer_doodle_keep)) } },
        )
    }

    if (state.phase == LoadPhase.PASSWORD) PasswordDialog(state.passwordWrong, onSubmit = { vm.submitPassword(it) }, onCancel = onBack)
}

@Composable
private fun SearchBar(state: ViewerUiState, vm: ViewerViewModel) {
    var text by remember { mutableStateOf(state.searchQuery) }
    Row(Modifier.statusBarsPadding().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { vm.closeSearch() }) { Icon(Icons.Default.Close, stringResource(R.string.viewer_close_search)) }
        OutlinedTextField(
            value = text, onValueChange = { text = it; vm.search(it) },
            modifier = Modifier.weight(1f).padding(vertical = 6.dp), singleLine = true,
            placeholder = { Text(stringResource(R.string.viewer_search_hint)) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { vm.findNext() }),
        )
        val label = when {
            state.searchQuery.isBlank() -> ""
            state.searchPending && state.searchTotal == 0 -> "…"
            !state.searchFound || state.searchTotal == 0 -> stringResource(R.string.viewer_no_matches)
            else -> "${state.searchCurrent}/${state.searchTotal}"
        }
        Text(label, Modifier.padding(horizontal = 6.dp), style = MaterialTheme.typography.labelMedium)
        IconButton(onClick = { vm.findPrevious() }) { Icon(Icons.Default.KeyboardArrowUp, stringResource(R.string.viewer_previous_match)) }
        IconButton(onClick = { vm.findNext() }) { Icon(Icons.Default.KeyboardArrowDown, stringResource(R.string.viewer_next_match)) }
    }
}

@Composable
private fun CenteredMessage(text: String, action: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(32.dp)) {
            Text(text, style = MaterialTheme.typography.bodyLarge)
            action()
        }
    }
}

@Composable
private fun AnchoredBar(anchor: Anchor, content: @Composable RowScope.() -> Unit) {
    val cfg = LocalConfiguration.current
    val barW = 300f
    val x = (anchor.x + anchor.w / 2f - barW / 2f).coerceIn(8f, (cfg.screenWidthDp - barW - 8f).coerceAtLeast(8f))
    val y = (anchor.y + anchor.h + 16f).coerceIn(72f, (cfg.screenHeightDp - 150f).coerceAtLeast(72f))
    Surface(
        modifier = Modifier.offset(x = x.dp, y = y.dp), shape = RoundedCornerShape(28.dp),
        tonalElevation = 6.dp, shadowElevation = 8.dp,
    ) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, content = content)
    }
}

@Composable
private fun HighlightColors(onPick: (Int) -> Unit) {
    HIGHLIGHT_COLORS.forEach { c ->
        Box(
            Modifier.padding(4.dp).size(32.dp).clip(CircleShape).background(Color(c))
                .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape).clickable { onPick(c) },
        )
    }
}

@Composable
private fun CopyButton(text: String, vm: ViewerViewModel) {
    val clipboard = LocalClipboardManager.current
    TextButton(onClick = { clipboard.setText(AnnotatedString(text)); vm.postMessage(R.string.viewer_copied); vm.dismissSelection() }) {
        Text(stringResource(R.string.viewer_copy))
    }
}

@Composable
private fun NoteDialog(initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.viewer_note)) },
        text = { OutlinedTextField(text, { text = it }, modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 6) },
        confirmButton = { TextButton(onClick = { onSave(text) }) { Text(stringResource(R.string.viewer_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.viewer_cancel)) } },
    )
}

@Composable
private fun PasswordDialog(wrong: Boolean, onSubmit: (String) -> Unit, onCancel: () -> Unit) {
    var pw by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.viewer_password_title)) },
        text = {
            Column {
                Text(stringResource(R.string.viewer_password_body))
                OutlinedTextField(
                    pw, { pw = it }, singleLine = true, visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp), isError = wrong,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onSubmit(pw) }),
                )
                if (wrong) Text(stringResource(R.string.viewer_password_wrong), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = { onSubmit(pw) }) { Text(stringResource(R.string.viewer_open)) } },
        dismissButton = { TextButton(onClick = onCancel) { Text(stringResource(R.string.viewer_cancel)) } },
    )
}

@Composable
private fun ViewerWindowEffects(prefs: ViewerPreferences) {
    val activity = LocalContext.current.findActivity() ?: return
    DisposableEffect(prefs.brightness) {
        val lp = activity.window.attributes
        lp.screenBrightness = if (prefs.brightness <= 0f) WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE else prefs.brightness
        activity.window.attributes = lp
        onDispose {
            val l = activity.window.attributes
            l.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            activity.window.attributes = l
        }
    }
    DisposableEffect(prefs.keepScreenOn) {
        if (prefs.keepScreenOn) activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
    DisposableEffect(prefs.orientation) {
        activity.requestedOrientation = when (prefs.orientation) {
            OrientationLock.AUTO -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            OrientationLock.PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            OrientationLock.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
        onDispose { activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun printPdf(context: Context, uri: Uri, name: String, pages: Int) {
    runCatching {
        val pm = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
        pm.print(name.ifBlank { "PDF" }, UriPrintAdapter(context, uri, name.ifBlank { "PDF" }, pages), null)
    }
}

/** Only web, mail and phone links are handed to other apps. */
private fun openExternalLink(context: Context, href: String) {
    val u = runCatching { Uri.parse(href) }.getOrNull() ?: return
    if (u.scheme?.lowercase() !in setOf("http", "https", "mailto", "tel")) return
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, u).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
    }
}

private fun sizeLabel(size: DoodleSize) = when (size) {
    DoodleSize.THIN -> R.string.viewer_doodle_size_thin
    DoodleSize.MEDIUM -> R.string.viewer_doodle_size_medium
    DoodleSize.THICK -> R.string.viewer_doodle_size_thick
}

/**
 * A handle on the right edge (bottom edge when scrolling sideways) that follows the scroll position and can be dragged to
 * move through the document quickly. While it is dragged a bubble shows which page you are on.
 */
@Composable
private fun FastScroller(
    ratio: Float,
    page: Int,
    pages: Int,
    horizontal: Boolean,
    visible: Boolean,
    onScrollTo: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    var dragging by remember { mutableStateOf(false) }
    var dragRatio by remember { mutableStateOf(0f) }
    val latestRatio by rememberUpdatedState(ratio)
    val latestScrollTo by rememberUpdatedState(onScrollTo)
    val shown = if (dragging) dragRatio else ratio
    val handleLong = 64.dp
    val handleShort = 28.dp
    val density = androidx.compose.ui.platform.LocalDensity.current

    BoxWithConstraints(modifier.statusBarsPadding().navigationBarsPadding().padding(top = 56.dp, bottom = 8.dp, start = 8.dp, end = 4.dp)) {
        val trackPx = with(density) { (if (horizontal) maxWidth else maxHeight).toPx() }
        val handlePx = with(density) { handleLong.toPx() }
        val room = (trackPx - handlePx).coerceAtLeast(1f)
        val offsetPx = (shown.coerceIn(0f, 1f) * room).toInt()
        AnimatedVisibility(visible = visible || dragging, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.matchParentSize()) {
            Box(Modifier.fillMaxSize()) {
                val bubbleText = "${if (dragging) ratioToPage(dragRatio, pages) else page} / $pages"
                val handleModifier = if (horizontal) {
                    Modifier.align(Alignment.BottomStart).offset { androidx.compose.ui.unit.IntOffset(offsetPx, 0) }.size(handleLong, handleShort + 12.dp)
                } else {
                    Modifier.align(Alignment.TopEnd).offset { androidx.compose.ui.unit.IntOffset(0, offsetPx) }.size(handleShort + 12.dp, handleLong)
                }
                if (dragging) {
                    val bubbleModifier = if (horizontal) {
                        Modifier.align(Alignment.BottomStart).offset { androidx.compose.ui.unit.IntOffset(offsetPx, with(density) { -(handleShort + 20.dp).roundToPx() }) }
                    } else {
                        Modifier.align(Alignment.TopEnd).offset { androidx.compose.ui.unit.IntOffset(with(density) { -(handleShort + 20.dp).roundToPx() }, offsetPx + with(density) { 12.dp.roundToPx() }) }
                    }
                    Surface(modifier = bubbleModifier, shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.inverseSurface, shadowElevation = 6.dp) {
                        Text(bubbleText, Modifier.padding(horizontal = 14.dp, vertical = 8.dp), color = MaterialTheme.colorScheme.inverseOnSurface, style = MaterialTheme.typography.labelLarge)
                    }
                }
                Box(
                    handleModifier.pointerInput(horizontal, room) {
                        val detect: suspend androidx.compose.ui.input.pointer.PointerInputScope.() -> Unit = {
                            if (horizontal) {
                                detectHorizontalDragGestures(
                                    onDragStart = { dragging = true; dragRatio = latestRatio },
                                    onDragEnd = { dragging = false },
                                    onDragCancel = { dragging = false },
                                ) { change, amount ->
                                    change.consume()
                                    dragRatio = (dragRatio + amount / room).coerceIn(0f, 1f)
                                    latestScrollTo(dragRatio)
                                }
                            } else {
                                detectVerticalDragGestures(
                                    onDragStart = { dragging = true; dragRatio = latestRatio },
                                    onDragEnd = { dragging = false },
                                    onDragCancel = { dragging = false },
                                ) { change, amount ->
                                    change.consume()
                                    dragRatio = (dragRatio + amount / room).coerceIn(0f, 1f)
                                    latestScrollTo(dragRatio)
                                }
                            }
                        }
                        detect()
                    },
                    contentAlignment = if (horizontal) Alignment.BottomCenter else Alignment.CenterEnd,
                ) {
                    Surface(
                        shape = RoundedCornerShape(topStart = 18.dp, bottomStart = 18.dp, topEnd = 6.dp, bottomEnd = 6.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                        shadowElevation = 4.dp, tonalElevation = 4.dp,
                        modifier = if (horizontal) Modifier.size(handleLong, handleShort) else Modifier.size(handleShort, handleLong),
                    ) {
                        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.KeyboardArrowUp, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Icon(Icons.Default.KeyboardArrowDown, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}
