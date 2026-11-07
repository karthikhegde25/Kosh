package com.kosh.android.ui.screens

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import com.kosh.android.notes.tts.NoteSpeaker
import com.kosh.android.notes.tts.Reading
import com.kosh.android.notes.tts.SpeakerState
import com.kosh.android.notes.ui.buildChecklistView
import com.kosh.android.notes.ui.checklistHit
import com.kosh.android.notes.ui.ChecklistTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.background
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.produceState
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.core.content.FileProvider
import com.kosh.android.imaging.ThumbnailLoader
import com.kosh.android.ui.components.rememberImagePicker
import com.kosh.android.ui.components.sharePdf
import java.io.File
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.automirrored.filled.FormatIndentDecrease
import androidx.compose.material.icons.automirrored.filled.FormatIndentIncrease
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.FormatStrikethrough
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Title
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import com.kosh.android.notes.format.ImagePiece
import com.kosh.android.notes.format.TextPiece
import com.kosh.android.notes.format.NoteImageTokens
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.text.TextRange
import androidx.compose.runtime.key
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kosh.android.KoshViewModelFactory
import com.kosh.android.R
import com.kosh.android.notes.export.NoteExportFormat
import com.kosh.android.notes.format.FormatOp
import com.kosh.android.notes.format.NoteFormatConverter
import com.kosh.android.notes.ui.EditorEvent
import com.kosh.android.notes.ui.NoteEditorViewModel
import com.kosh.android.notes.ui.MarkdownPreview
import com.kosh.android.notes.ui.SaveStatus
import com.kosh.android.util.suggestedExportName
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditorScreen(onBack: () -> Unit) {
    val vm: NoteEditorViewModel = viewModel(factory = KoshViewModelFactory.Factory)
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var preview by rememberSaveable { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var showExport by remember { mutableStateOf(false) }
    var confirmTrash by remember { mutableStateOf(false) }
    var imageToView by remember { mutableStateOf<File?>(null) }
    var imageToRemove by remember { mutableStateOf<String?>(null) }
    val pickImages = rememberImagePicker { vm.addImages(it) }

    // Reading aloud: the note is read-only while it is being read.
    val speaker = remember { NoteSpeaker(context) }
    DisposableEffect(Unit) { onDispose { speaker.shutdown() } }
    val speech by speaker.state.collectAsStateWithLifecycle()
    val reading = speech.reading != Reading.IDLE
    var showRead by remember { mutableStateOf(false) }
    var changeVoiceOnly by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { speaker.stop() }
    BackHandler(enabled = reading) { speaker.stop() }

    // Save whenever the app leaves the foreground, so nothing is lost if the process is killed.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { vm.flush() }

    val pdfLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(NoteExportFormat.PDF.mimeType)) { vm.finishExport(it) }
    val mdLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(NoteExportFormat.MARKDOWN.mimeType)) { vm.finishExport(it) }
    val txtLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(NoteExportFormat.PLAIN_TEXT.mimeType)) { vm.finishExport(it) }

    fun startExport(format: NoteExportFormat) {
        vm.beginExport(format) // snapshot taken now; later edits don't change this export
        val name = suggestedExportName(state.title, format.extension)
        when (format) {
            NoteExportFormat.PDF -> pdfLauncher.launch(name)
            NoteExportFormat.MARKDOWN -> mdLauncher.launch(name)
            NoteExportFormat.PLAIN_TEXT -> txtLauncher.launch(name)
        }
    }

    fun formatLabel(f: NoteExportFormat) = when (f) {
        NoteExportFormat.PDF -> "PDF"
        NoteExportFormat.MARKDOWN -> "Markdown (.md)"
        NoteExportFormat.PLAIN_TEXT -> "text (.txt)"
    }

    LaunchedEffect(Unit) {
        vm.events.collect { event ->
            when (event) {
                is EditorEvent.Exported ->
                    snackbar.showSnackbar(context.getString(R.string.export_done, formatLabel(event.format)))
                is EditorEvent.ExportFailed -> {
                    val result = snackbar.showSnackbar(
                        message = context.getString(R.string.export_failed, formatLabel(event.format)),
                        actionLabel = context.getString(R.string.editor_retry),
                    )
                    if (result == SnackbarResult.ActionPerformed) vm.retryExport()
                }
                EditorEvent.Duplicated -> snackbar.showSnackbar(context.getString(R.string.editor_duplicated))
                EditorEvent.Trashed -> onBack()
                EditorEvent.ImagesFailed -> snackbar.showSnackbar(context.getString(R.string.notes_image_failed))
                EditorEvent.ShareFailed -> snackbar.showSnackbar(context.getString(R.string.notes_share_pdf_failed))
                is EditorEvent.ShareReady -> {
                    val uri = runCatching {
                        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", event.file)
                    }.getOrNull()
                    if (uri == null || !sharePdf(context, uri)) {
                        snackbar.showSnackbar(context.getString(R.string.notes_share_pdf_failed))
                    }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.reader_back))
                    }
                },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        when (state.saveStatus) {
                            SaveStatus.SAVING -> Text(stringResource(R.string.editor_saving), style = MaterialTheme.typography.bodyMedium)
                            SaveStatus.SAVED -> Text(stringResource(R.string.editor_saved), style = MaterialTheme.typography.bodyMedium)
                            SaveStatus.FAILED -> {
                                Text(
                                    stringResource(R.string.editor_save_failed),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.error,
                                )
                                TextButton(onClick = vm::flush) { Text(stringResource(R.string.editor_retry)) }
                            }
                            SaveStatus.IDLE -> Unit
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = { preview = false; changeVoiceOnly = false; showRead = true },
                        enabled = !reading && state.loaded,
                    ) {
                        Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = stringResource(R.string.read_aloud))
                    }
                    IconButton(onClick = { preview = !preview }, enabled = !reading) {
                        Icon(
                            if (preview) Icons.Default.Edit else Icons.Default.Visibility,
                            contentDescription = stringResource(if (preview) R.string.editor_mode_edit else R.string.editor_mode_preview),
                        )
                    }
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.menu_more))
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(if (state.isPinned) R.string.note_unpin else R.string.note_pin)) },
                            onClick = { menuOpen = false; vm.togglePin() },
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text(stringResource(if (state.isArchived) R.string.note_unarchive else R.string.note_archive)) },
                            onClick = { menuOpen = false; vm.toggleArchive() },
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.note_duplicate)) },
                            onClick = { menuOpen = false; vm.duplicate(context.getString(R.string.note_copy_suffix)) },
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.notes_add_image)) },
                            enabled = !reading,
                            onClick = { menuOpen = false; pickImages() },
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.editor_copy_all)) },
                            onClick = {
                                menuOpen = false
                                clipboard.setText(AnnotatedString(NoteFormatConverter.toPlainText(state.title, state.body.text)))
                                scope.launch { snackbar.showSnackbar(context.getString(R.string.editor_copied)) }
                            },
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.editor_share_text)) },
                            onClick = {
                                menuOpen = false
                                val send = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_SUBJECT, state.title)
                                    putExtra(Intent.EXTRA_TEXT, NoteFormatConverter.toPlainText(state.title, state.body.text))
                                }
                                runCatching { context.startActivity(Intent.createChooser(send, null)) }
                            },
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.notes_share_pdf)) },
                            onClick = { menuOpen = false; vm.sharePdf() },
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.editor_export)) },
                            onClick = { menuOpen = false; showExport = true },
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.note_move_to_trash), color = MaterialTheme.colorScheme.error) },
                            onClick = { menuOpen = false; confirmTrash = true },
                        )
                    }
                },
            )
        },
        bottomBar = {
            if (reading) {
                ReadingBar(
                    speech = speech,
                    onPauseResume = { if (speech.reading == Reading.SPEAKING) speaker.pause() else speaker.resume() },
                    onStop = speaker::stop,
                    onRate = speaker::setRate,
                    onVoice = { changeVoiceOnly = true; showRead = true },
                )
            } else if (!preview && state.loaded) {
                FormattingBar(
                    canUndo = state.canUndo,
                    canRedo = state.canRedo,
                    onOp = vm::applyFormat,
                    onUndo = vm::undo,
                    onRedo = vm::redo,
                    onAddImage = pickImages,
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (!state.loaded) {
            Column(Modifier.fillMaxSize().padding(padding)) {}
            return@Scaffold
        }
        Column(Modifier.fillMaxSize().padding(padding)) {
            val transparent = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            )
            TextField(
                value = state.title,
                onValueChange = vm::onTitleChange,
                placeholder = { Text(stringResource(R.string.editor_title_hint), style = MaterialTheme.typography.titleLarge) },
                textStyle = MaterialTheme.typography.titleLarge,
                singleLine = true,
                readOnly = reading,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                colors = transparent,
                modifier = Modifier.fillMaxWidth(),
            )
            if (preview) {
                if (state.body.text.isBlank()) {
                    Text(
                        stringResource(R.string.editor_preview_empty),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                } else {
                    MarkdownPreview(
                        content = state.body.text,
                        images = state.images,
                        onOpenImage = { imageToView = it },
                        onToggleChecklist = vm::toggleChecklist,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            } else {
                NoteBodyEditor(
                    value = state.body,
                    revision = state.bodyRevision,
                    onValueChange = vm::onBodyChange,
                    onToggleChecklist = vm::toggleChecklist,
                    images = state.images,
                    focusTick = state.focusTick,
                    onOpenImage = { imageToView = it },
                    onRemoveImage = { imageToRemove = it },
                    readOnly = reading,
                    highlight = speech.highlight,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }

    if (showRead) {
        ReadAloudDialog(
            speech = speech,
            starting = !changeVoiceOnly,
            onVoice = speaker::setVoice,
            onRate = speaker::setRate,
            onStart = {
                showRead = false
                focusManager.clearFocus()
                keyboard?.hide()
                if (!speaker.start(state.body.text)) {
                    scope.launch { snackbar.showSnackbar(context.getString(R.string.read_nothing)) }
                }
            },
            onDismiss = { showRead = false },
            onOpenSettings = {
                runCatching { context.startActivity(Intent("com.android.settings.TTS_SETTINGS")) }
            },
        )
    }

    if (showExport) {
        AlertDialog(
            onDismissRequest = { showExport = false },
            title = { Text(stringResource(R.string.export_title)) },
            text = {
                Column {
                    ExportOption(R.string.export_pdf) { showExport = false; startExport(NoteExportFormat.PDF) }
                    ExportOption(R.string.export_markdown) { showExport = false; startExport(NoteExportFormat.MARKDOWN) }
                    ExportOption(R.string.export_text) { showExport = false; startExport(NoteExportFormat.PLAIN_TEXT) }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showExport = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    imageToView?.let { file ->
        val big by produceState<Bitmap?>(null, file.path) { value = ThumbnailLoader.load(file.path, 1600) }
        AlertDialog(
            onDismissRequest = { imageToView = null },
            text = {
                val bmp = big
                if (bmp != null) {
                    val image = remember(bmp) { bmp.asImageBitmap() }
                    Image(bitmap = image, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxWidth())
                } else {
                    Text(stringResource(R.string.reader_opening))
                }
            },
            confirmButton = { TextButton(onClick = { imageToView = null }) { Text(stringResource(R.string.ok)) } },
        )
    }

    imageToRemove?.let { name ->
        AlertDialog(
            onDismissRequest = { imageToRemove = null },
            title = { Text(stringResource(R.string.notes_image_remove_title)) },
            text = { Text(stringResource(R.string.notes_image_remove_body)) },
            confirmButton = {
                TextButton(onClick = { imageToRemove = null; vm.removeImage(name) }) {
                    Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { imageToRemove = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    if (confirmTrash) {
        AlertDialog(
            onDismissRequest = { confirmTrash = false },
            title = { Text(stringResource(R.string.editor_trash_title)) },
            text = { Text(stringResource(R.string.editor_trash_body)) },
            confirmButton = {
                TextButton(onClick = { confirmTrash = false; vm.moveToTrash() }) {
                    Text(stringResource(R.string.note_move_to_trash), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmTrash = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun ExportOption(labelRes: Int, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(labelRes), modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun FormattingBar(
    canUndo: Boolean,
    canRedo: Boolean,
    onOp: (FormatOp) -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onAddImage: () -> Unit,
) {
    Surface(tonalElevation = 3.dp, modifier = Modifier.navigationBarsPadding().imePadding()) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(0.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FormatButton(Icons.Default.AddPhotoAlternate, R.string.notes_add_image, onClick = onAddImage)
            FormatButton(Icons.AutoMirrored.Filled.Undo, R.string.fmt_undo, enabled = canUndo, onClick = onUndo)
            FormatButton(Icons.AutoMirrored.Filled.Redo, R.string.fmt_redo, enabled = canRedo, onClick = onRedo)
            FormatButton(Icons.Default.FormatBold, R.string.fmt_bold) { onOp(FormatOp.BOLD) }
            FormatButton(Icons.Default.FormatItalic, R.string.fmt_italic) { onOp(FormatOp.ITALIC) }
            FormatButton(Icons.Default.FormatStrikethrough, R.string.fmt_strike) { onOp(FormatOp.STRIKE) }
            FormatButton(Icons.Default.Title, R.string.fmt_heading) { onOp(FormatOp.HEADING) }
            FormatButton(Icons.AutoMirrored.Filled.FormatListBulleted, R.string.fmt_bullets) { onOp(FormatOp.BULLET) }
            FormatButton(Icons.Default.FormatListNumbered, R.string.fmt_numbers) { onOp(FormatOp.NUMBERED) }
            FormatButton(Icons.Default.Checklist, R.string.fmt_checklist) { onOp(FormatOp.CHECKLIST) }
            FormatButton(Icons.AutoMirrored.Filled.FormatIndentIncrease, R.string.fmt_indent) { onOp(FormatOp.INDENT) }
            FormatButton(Icons.AutoMirrored.Filled.FormatIndentDecrease, R.string.fmt_outdent) { onOp(FormatOp.OUTDENT) }
        }
    }
}

@Composable
private fun FormatButton(icon: ImageVector, labelRes: Int, enabled: Boolean = true, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled) {
        Icon(icon, contentDescription = stringResource(labelRes))
    }
}

/**
 * The note text, with pictures sitting between the paragraphs where they were inserted. The note is stored as one Markdown
 * string; here it is shown as alternating pieces: an editable text field for each run of text, and a picture for each
 * image line. Editing a piece writes back into the single string, so undo, formatting and saving all work as before.
 * Checklist lines (`- [ ] item`) are drawn as real check boxes that can be ticked without opening the keyboard.
 */
@Composable
private fun NoteBodyEditor(
    value: TextFieldValue,
    revision: Int,
    onValueChange: (TextFieldValue) -> Unit,
    onToggleChecklist: (Int) -> Unit,
    images: List<File>,
    focusTick: Int,
    onOpenImage: (File) -> Unit,
    onRemoveImage: (String) -> Unit,
    readOnly: Boolean,
    highlight: IntRange?,
    modifier: Modifier = Modifier,
) {
    // The text field works on its own copy, which is updated the moment a key is pressed. Taking the value back from the
    // ViewModel on every keystroke made the keyboard lose the word it was composing (no autocorrect, no suggestion taps)
    // and let an out-of-date value overwrite fast typing. The ViewModel's copy is adopted only when its revision changes.
    var local by remember { mutableStateOf(value) }
    var seenRevision by remember { mutableIntStateOf(revision) }
    if (revision != seenRevision) {
        seenRevision = revision
        local = value
    }
    val pieces = remember(local.text) { NoteImageTokens.split(local.text) }
    val startTick = remember { focusTick }
    val latestChange by rememberUpdatedState(onValueChange)
    val lastText = pieces.indexOfLast { it is TextPiece }

    Column(modifier.verticalScroll(rememberScrollState())) {
        pieces.forEachIndexed { index, piece ->
            when (piece) {
                is TextPiece -> key("text$index") {
                    TextPieceField(
                        piece = piece,
                        selection = local.selection,
                        composition = local.composition?.takeIf { it.start >= piece.start && it.end <= piece.end }
                            ?.let { TextRange(it.start - piece.start, it.end - piece.start) },
                        showHint = pieces.size == 1 && local.text.isEmpty(),
                        isLast = index == lastText,
                        focusTick = if (focusTick != startTick) focusTick else 0,
                        readOnly = readOnly,
                        highlight = highlight
                            ?.takeIf { it.first >= piece.start && it.first < piece.end }
                            ?.let { (it.first - piece.start)..(minOf(it.last, piece.end - 1) - piece.start) },
                        onToggleChecklist = { if (!readOnly) onToggleChecklist(it) },
                        onPieceChange = { new ->
                            // Write the piece back into the whole note text and move the caret by the piece's offset.
                            val text = local.text.replaceRange(piece.start, piece.end, new.text)
                            val sel = TextRange(piece.start + new.selection.start, piece.start + new.selection.end)
                            val composing = new.composition?.let { TextRange(piece.start + it.start, piece.start + it.end) }
                            val next = TextFieldValue(text, sel, composing)
                            local = next
                            latestChange(next)
                        },
                    )
                }
                is ImagePiece -> key("image${piece.name}") {
                    InlineNoteImage(
                        file = images.firstOrNull { it.name == piece.name },
                        onOpen = onOpenImage,
                        onRemove = { if (!readOnly) onRemoveImage(piece.name) },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TextPieceField(
    piece: TextPiece,
    selection: TextRange,
    composition: TextRange?,
    showHint: Boolean,
    isLast: Boolean,
    focusTick: Int,
    readOnly: Boolean,
    highlight: IntRange?,
    onToggleChecklist: (Int) -> Unit,
    onPieceChange: (TextFieldValue) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val transformation = remember(colors.onSurfaceVariant, colors.primary, highlight) {
        ChecklistTransformation(
            boxColor = colors.onSurfaceVariant,
            tickedColor = colors.primary,
            tickedTextColor = colors.onSurfaceVariant.copy(alpha = 0.7f),
            markerColor = colors.onSurfaceVariant.copy(alpha = 0.55f),
            highlight = highlight,
            highlightColor = colors.primary.copy(alpha = 0.35f),
        )
    }
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val scrollTarget = remember { BringIntoViewRequester() }
    val density = LocalDensity.current

    // While reading aloud, keep the word being read in view with some room around it, so the note moves on by itself.
    LaunchedEffect(highlight, layout) {
        val word = highlight ?: return@LaunchedEffect
        val result = layout ?: return@LaunchedEffect
        val length = result.layoutInput.text.length
        if (length == 0) return@LaunchedEffect
        val map = buildChecklistView(piece.text).originalToTransformed
        val at = map[word.first.coerceIn(0, map.lastIndex)].coerceIn(0, length - 1)
        val box = result.getBoundingBox(at)
        val room = with(density) { 96.dp.toPx() }
        runCatching { scrollTarget.bringIntoView(Rect(0f, box.top - room, 1f, box.bottom + room * 1.6f)) }
    }
    val slop = with(LocalDensity.current) { 20.dp.toPx() }
    val latestToggle by rememberUpdatedState(onToggleChecklist)
    val firstLine = piece.firstLine
    val textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.onSurface)
    val focus = remember { FocusRequester() }

    // The caret belongs to this piece only when the note's selection lies inside it.
    val inside = selection.start >= piece.start && selection.end <= piece.end
    val local = if (inside) TextRange(selection.start - piece.start, selection.end - piece.start) else TextRange.Zero
    val fieldValue = TextFieldValue(piece.text, local, composition)

    LaunchedEffect(focusTick) {
        if (focusTick > 0 && inside) {
            runCatching { focus.requestFocus() }
        }
    }

    BasicTextField(
        value = fieldValue,
        onValueChange = onPieceChange,
        readOnly = readOnly,
        textStyle = textStyle,
        cursorBrush = SolidColor(colors.primary),
        visualTransformation = transformation,
        onTextLayout = { layout = it },
        decorationBox = { inner ->
            Box {
                if (showHint) {
                    Text(stringResource(R.string.editor_body_hint), style = textStyle, color = colors.onSurfaceVariant)
                }
                inner()
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = if (isLast) 320.dp else 32.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .bringIntoViewRequester(scrollTarget)
            .focusRequester(focus)
            .pointerInput(Unit) {
                awaitEachGesture {
                    // Watched before the text field sees it, so a tap on a box doesn't also move the cursor.
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    val result = layout ?: return@awaitEachGesture
                    val line = checklistHit(result, down.position, slop) ?: return@awaitEachGesture
                    down.consume()
                    val up = waitForUpOrCancellation(PointerEventPass.Initial) ?: return@awaitEachGesture
                    up.consume()
                    val moved = (up.position - down.position).getDistance()
                    if (moved <= viewConfiguration.touchSlop * 2) latestToggle(firstLine + line)
                }
            },
    )
}

/** A picture inside the note. Tap to view it larger; the x removes it from the note. */
@Composable
private fun InlineNoteImage(file: File?, onOpen: (File) -> Unit, onRemove: () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        val bmp by produceState<Bitmap?>(null, file?.path) { value = file?.let { ThumbnailLoader.load(it.path, 1200) } }
        val shape = RoundedCornerShape(10.dp)
        val bitmap = bmp
        if (file != null && bitmap != null) {
            val image = remember(bitmap) { bitmap.asImageBitmap() }
            Image(
                bitmap = image,
                contentDescription = stringResource(R.string.notes_image_desc, 1),
                contentScale = ContentScale.FillWidth,
                modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp).clip(shape).clickable { onOpen(file) },
            )
        } else {
            Box(
                Modifier.fillMaxWidth().height(96.dp).clip(shape).background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                if (file == null) Text(stringResource(R.string.notes_image_missing), color = MaterialTheme.colorScheme.onSurfaceVariant)
                else CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
            }
        }
        IconButton(onClick = onRemove, modifier = Modifier.align(Alignment.TopEnd)) {
            Icon(
                Icons.Default.Close, contentDescription = stringResource(R.string.notes_image_remove_title),
                tint = Color.White, modifier = Modifier.background(Color.Black.copy(alpha = 0.45f), CircleShape).padding(4.dp),
            )
        }
    }
}

/** The small bar shown at the bottom while a note is being read: pause or resume, stop, speed and voice. */
@Composable
private fun ReadingBar(
    speech: SpeakerState,
    onPauseResume: () -> Unit,
    onStop: () -> Unit,
    onRate: (Float) -> Unit,
    onVoice: () -> Unit,
) {
    var speedOpen by remember { mutableStateOf(false) }
    Surface(tonalElevation = 3.dp, modifier = Modifier.navigationBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val speaking = speech.reading == Reading.SPEAKING
            IconButton(onClick = onPauseResume) {
                Icon(
                    if (speaking) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = stringResource(if (speaking) R.string.read_pause else R.string.read_resume),
                )
            }
            IconButton(onClick = onStop) {
                Icon(Icons.Default.Stop, contentDescription = stringResource(R.string.read_stop))
            }
            Box {
                TextButton(onClick = { speedOpen = true }) {
                    Icon(Icons.Default.Speed, contentDescription = stringResource(R.string.read_speed))
                    Text(" " + speedText(speech.rate))
                }
                DropdownMenu(expanded = speedOpen, onDismissRequest = { speedOpen = false }) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).width(240.dp)) {
                        RateSlider(speech.rate, onRate)
                    }
                }
            }
            IconButton(onClick = onVoice) {
                Icon(Icons.Default.RecordVoiceOver, contentDescription = stringResource(R.string.read_voice))
            }
        }
    }
}

private fun speedText(rate: Float) = "%.1f\u00D7".format(rate)

/** Speed from 0.5\u00D7 to 2\u00D7. The speed is applied when the finger lifts, so reading isn't restarted again and again while sliding. */
@Composable
private fun RateSlider(rate: Float, onRate: (Float) -> Unit) {
    var shown by remember(rate) { mutableStateOf(rate) }
    Text(stringResource(R.string.read_speed_value, speedText(shown)), style = MaterialTheme.typography.bodyMedium)
    Slider(
        value = shown,
        onValueChange = { shown = it },
        onValueChangeFinished = { onRate(shown) },
        valueRange = NoteSpeaker.MIN_RATE..NoteSpeaker.MAX_RATE,
        steps = 14,
    )
}

@Composable
private fun ReadAloudDialog(
    speech: SpeakerState,
    starting: Boolean,
    onVoice: (String) -> Unit,
    onRate: (Float) -> Unit,
    onStart: () -> Unit,
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.read_aloud)) },
        text = {
            Column {
                when {
                    speech.unavailable || (speech.ready && speech.voices.isEmpty()) -> {
                        Text(stringResource(R.string.read_no_voices))
                        TextButton(onClick = onOpenSettings) { Text(stringResource(R.string.read_open_settings)) }
                    }
                    !speech.ready -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                        Text(stringResource(R.string.read_loading), Modifier.padding(start = 12.dp))
                    }
                    else -> {
                        Text(stringResource(R.string.read_voice), style = MaterialTheme.typography.titleSmall)
                        LazyColumn(Modifier.heightIn(max = 260.dp)) {
                            items(speech.voices, key = { it.name }) { voice ->
                                Row(
                                    Modifier.fillMaxWidth().clickable { onVoice(voice.name) }.padding(vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    RadioButton(selected = voice.name == speech.voiceName, onClick = { onVoice(voice.name) })
                                    Text(voice.label, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        RateSlider(speech.rate, onRate)
                    }
                }
            }
        },
        confirmButton = {
            if (speech.ready && speech.voices.isNotEmpty()) {
                TextButton(onClick = if (starting) onStart else onDismiss) {
                    Text(stringResource(if (starting) R.string.read_start else R.string.ok))
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
