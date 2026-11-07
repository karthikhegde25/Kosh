package com.kosh.android.ui.screens

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.runtime.setValue
import androidx.compose.ui.zIndex
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kosh.android.KoshViewModelFactory
import com.kosh.android.R
import com.kosh.android.tools.BORDER_MAX
import com.kosh.android.tools.BORDER_MIN
import com.kosh.android.tools.CompressLevel
import com.kosh.android.tools.ImageFormat
import com.kosh.android.tools.InputInfo
import com.kosh.android.tools.NUpStyle
import com.kosh.android.tools.NumberPosition
import com.kosh.android.tools.NumberSize
import com.kosh.android.tools.PageNumberFormat
import com.kosh.android.tools.PdfTool
import com.kosh.android.tools.PdfToolViewModel
import com.kosh.android.tools.SHEET_A4_HEIGHT
import com.kosh.android.tools.SHEET_A4_WIDTH
import com.kosh.android.tools.SHEET_PAGE_COUNTS
import com.kosh.android.tools.SplitMode
import com.kosh.android.tools.EncryptStrength
import com.kosh.android.tools.PAGES_UNKNOWN
import com.kosh.android.tools.fitInto
import com.kosh.android.tools.nUpCells
import com.kosh.android.tools.nUpGrid
import com.kosh.android.tools.sheetGap
import com.kosh.android.tools.sheetMargin
import com.kosh.android.tools.ToolRun
import com.kosh.android.tools.ToolUiState
import com.kosh.android.ui.components.sharePdf
import com.kosh.android.util.formatFileSize

private const val GRID_COLUMNS = 3

/** Some file managers report a .docx as a generic binary or ZIP file, so those are offered too; the file is checked after it is picked. */
private val DOCX_TYPES = arrayOf(
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    "application/octet-stream",
    "application/zip",
)

/** One screen for every PDF tool: choose file(s), set the options, pick where to save, watch it run, see the result. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfToolScreen(onBack: () -> Unit, onOpenPdf: (Uri) -> Unit) {
    val vm: PdfToolViewModel = viewModel(factory = KoshViewModelFactory.Factory)
    val state by vm.state.collectAsStateWithLifecycle()

    val pickOne = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) vm.addFiles(listOf(uri)) }
    val pickMany = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris -> vm.addFiles(uris) }
    val createFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri -> if (uri != null) vm.run(uri) }
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri -> if (uri != null) vm.run(uri) }
    val choose = {
        when {
            vm.tool == PdfTool.DOCX_TO_PDF -> pickOne.launch(DOCX_TYPES)
            state.multiInput -> pickMany.launch(arrayOf("application/pdf"))
            else -> pickOne.launch(arrayOf("application/pdf"))
        }
    }

    BackHandler(enabled = state.run is ToolRun.Running) { vm.cancel() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(toolTitle(vm.tool))) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.tools_back)) }
                },
            )
        },
        bottomBar = {
            if (state.inputs.isNotEmpty() && state.run is ToolRun.Idle) {
                Surface(tonalElevation = 3.dp, shadowElevation = 3.dp) {
                    Button(
                        onClick = { if (state.needsFolder) pickFolder.launch(null) else createFile.launch(vm.suggestedFileName()) },
                        enabled = state.canRun,
                        modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp).height(52.dp),
                    ) { Text(runLabel(state)) }
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (val run = state.run) {
                is ToolRun.Running -> ProgressContent(run, onCancel = vm::cancel)
                is ToolRun.Done -> ResultContent(
                    run, onOpenPdf = onOpenPdf, onDone = onBack, onAnother = vm::startOver,
                )
                is ToolRun.Failed -> FailedContent(run.detail, run.wrongPassword, onBack = vm::backToOptions)
                is ToolRun.NoGain -> NoGainContent(run, onBack = vm::backToOptions)
                ToolRun.Idle -> if (state.inputs.isEmpty()) {
                    IntroContent(state, onChoose = choose)
                } else {
                    OptionsContent(vm, state, onChooseMore = choose)
                }
            }
        }
    }
}

@Composable
private fun runLabel(s: ToolUiState): String = when (s.tool) {
    PdfTool.MERGE -> stringResource(R.string.tool_run_merge)
    PdfTool.SPLIT -> stringResource(if (s.needsFolder) R.string.tool_run_split else R.string.tool_run_split_one)
    PdfTool.EXTRACT -> stringResource(R.string.tool_run_extract, s.selected.size)
    PdfTool.ROTATE -> if (s.selected.isEmpty()) stringResource(R.string.tool_run_rotate_all) else stringResource(R.string.tool_run_rotate, s.selected.size)
    PdfTool.DELETE -> stringResource(R.string.tool_run_delete, s.selected.size)
    PdfTool.PAGE_NUMBERS -> stringResource(R.string.tool_run_numbers)
    PdfTool.PDF_TO_IMAGES -> stringResource(R.string.tool_run_images)
    PdfTool.DOCX_TO_PDF -> stringResource(R.string.tool_run_docx)
    PdfTool.PAGES_PER_SHEET -> stringResource(R.string.tool_run_sheets)
    PdfTool.REORDER -> stringResource(R.string.tool_run_reorder)
    PdfTool.COMPRESS -> stringResource(R.string.tool_run_compress)
    PdfTool.ENCRYPT -> stringResource(R.string.tool_run_encrypt)
    PdfTool.DECRYPT -> stringResource(R.string.tool_run_decrypt)
    PdfTool.IMAGE_TO_PDF -> ""
}

// ---- before a file is chosen ---------------------------------------------------------------------------------------------

@Composable
private fun IntroContent(s: ToolUiState, onChoose: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(stringResource(toolDescription(s.tool)), style = MaterialTheme.typography.bodyLarge)
        if (s.errorRes != null) {
            Text(stringResource(s.errorRes), color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 12.dp))
        }
        if (s.opening) {
            CircularProgressIndicator(Modifier.padding(top = 24.dp))
        } else {
            Button(onClick = onChoose, modifier = Modifier.fillMaxWidth().padding(top = 24.dp).height(52.dp)) {
                Text(stringResource(if (s.tool == PdfTool.DOCX_TO_PDF) R.string.tool_choose_docx else if (s.multiInput) R.string.tool_choose_pdfs else R.string.tool_choose_pdf))
            }
        }
    }
}

// ---- options -------------------------------------------------------------------------------------------------------------------

@Composable
private fun OptionsContent(vm: PdfToolViewModel, s: ToolUiState, onChooseMore: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item(key = "files") { FilesCard(vm, s, onChooseMore) }
        if (s.errorRes != null) {
            item(key = "error") {
                Text(stringResource(s.errorRes), color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            }
        }
        item(key = "options") { ToolOptions(vm, s) }
        if (s.tool == PdfTool.REORDER && s.pageCount > 0) reorderItems(vm, s)
        if (s.usesPageGrid && s.pageCount > 0) {
            item(key = "selector") { PageSelectorHeader(vm, s) }
            items(count = (s.pageCount + GRID_COLUMNS - 1) / GRID_COLUMNS, key = { "row$it" }) { row ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (c in 0 until GRID_COLUMNS) {
                        val page = row * GRID_COLUMNS + c
                        if (page < s.pageCount) {
                            PageCell(vm, s, page, Modifier.weight(1f))
                        } else {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FilesCard(vm: PdfToolViewModel, s: ToolUiState, onChooseMore: () -> Unit) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        s.inputs.forEachIndexed { i, input ->
            Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(input.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
                        Text(fileDetail(input), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (s.multiInput) {
                        IconButton(onClick = { vm.moveInput(i, -1) }, enabled = i > 0) { Icon(Icons.Default.KeyboardArrowUp, stringResource(R.string.tool_move_up)) }
                        IconButton(onClick = { vm.moveInput(i, 1) }, enabled = i < s.inputs.lastIndex) { Icon(Icons.Default.KeyboardArrowDown, stringResource(R.string.tool_move_down)) }
                        IconButton(onClick = { vm.removeInput(i) }) { Icon(Icons.Default.Close, stringResource(R.string.tool_remove)) }
                    }
                }
            }
        }
        if (s.opening) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
        } else {
            OutlinedButton(onClick = onChooseMore) {
                Text(stringResource(if (s.tool == PdfTool.DOCX_TO_PDF) R.string.tool_change_docx else if (s.multiInput) R.string.tool_add_more_pdfs else R.string.tool_change_pdf))
            }
        }
    }
}

@Composable
private fun fileDetail(i: InputInfo): String {
    if (i.pageCount == PAGES_UNKNOWN) return i.sizeBytes?.let { formatFileSize(it) }.orEmpty()
    if (i.pageCount == 0) return if (i.sizeBytes != null) stringResource(R.string.tool_password_protected) + " · " + formatFileSize(i.sizeBytes) else stringResource(R.string.tool_password_protected)
    val pages = pluralText(R.plurals.tool_pages, i.pageCount, i.pageCount)
    return if (i.sizeBytes != null) "$pages · ${formatFileSize(i.sizeBytes)}" else pages
}

@Composable
private fun SectionLabel(res: Int) {
    Text(stringResource(res), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Chips(content: @Composable () -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) { content() }
}

@Composable
private fun ToolOptions(vm: PdfToolViewModel, s: ToolUiState) {
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        when (s.tool) {
            PdfTool.ROTATE -> {
                SectionLabel(R.string.tool_angle)
                Chips {
                    FilterChip(s.rotation == 90, { vm.setRotation(90) }, { Text(stringResource(R.string.tool_rotate_right)) })
                    FilterChip(s.rotation == 180, { vm.setRotation(180) }, { Text(stringResource(R.string.tool_rotate_180)) })
                    FilterChip(s.rotation == 270, { vm.setRotation(270) }, { Text(stringResource(R.string.tool_rotate_left)) })
                }
            }
            PdfTool.SPLIT -> SplitOptions(vm, s)
            PdfTool.PAGE_NUMBERS -> NumberOptions(vm, s)
            PdfTool.ENCRYPT -> EncryptOptions(vm, s)
            PdfTool.DOCX_TO_PDF -> DocxOptions(vm, s)
            PdfTool.PAGES_PER_SHEET -> DocxOptions(vm, s, word = false)
            PdfTool.DECRYPT -> DecryptOptions(vm, s)
            PdfTool.COMPRESS -> {
                SectionLabel(R.string.tool_compress_level)
                Chips {
                    FilterChip(s.compressLevel == CompressLevel.LIGHT, { vm.setCompressLevel(CompressLevel.LIGHT) }, { Text(stringResource(R.string.tool_level_light)) })
                    FilterChip(s.compressLevel == CompressLevel.BALANCED, { vm.setCompressLevel(CompressLevel.BALANCED) }, { Text(stringResource(R.string.tool_level_balanced)) })
                    FilterChip(s.compressLevel == CompressLevel.STRONG, { vm.setCompressLevel(CompressLevel.STRONG) }, { Text(stringResource(R.string.tool_level_strong)) })
                }
                Text(stringResource(R.string.tool_compress_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
            }
            PdfTool.PDF_TO_IMAGES -> {
                SectionLabel(R.string.tool_image_format)
                Chips {
                    FilterChip(s.imageFormat == ImageFormat.JPG, { vm.setImageFormat(ImageFormat.JPG) }, { Text("JPG") })
                    FilterChip(s.imageFormat == ImageFormat.PNG, { vm.setImageFormat(ImageFormat.PNG) }, { Text("PNG") })
                }
                SectionLabel(R.string.tool_resolution)
                Chips {
                    listOf(100, 150, 200, 300).forEach { dpi ->
                        FilterChip(s.imageDpi == dpi, { vm.setImageDpi(dpi) }, { Text("$dpi dpi") })
                    }
                }
            }
            else -> Unit
        }
    }
}

@Composable
private fun SplitOptions(vm: PdfToolViewModel, s: ToolUiState) {
    SectionLabel(R.string.tool_split_by)
    Chips {
        FilterChip(s.splitMode == SplitMode.RANGES, { vm.setSplitMode(SplitMode.RANGES) }, { Text(stringResource(R.string.tool_split_ranges)) })
        FilterChip(s.splitMode == SplitMode.EVERY_PAGE, { vm.setSplitMode(SplitMode.EVERY_PAGE) }, { Text(stringResource(R.string.tool_split_every_page)) })
        FilterChip(s.splitMode == SplitMode.EVERY_N, { vm.setSplitMode(SplitMode.EVERY_N) }, { Text(stringResource(R.string.tool_split_every_n)) })
    }
    when (s.splitMode) {
        SplitMode.RANGES -> {
            OutlinedTextField(
                value = s.splitRanges, onValueChange = vm::setSplitRanges, modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.tool_split_ranges_label)) },
                supportingText = { Text(stringResource(R.string.tool_split_ranges_hint, s.pageCount)) },
                isError = s.splitRanges.isNotBlank() && s.splitGroups == null, singleLine = true,
            )
            SwitchLine(R.string.tool_split_as_one, s.splitAsOne, vm::setSplitAsOne)
            if (s.splitAsOne) {
                Text(stringResource(R.string.tool_split_as_one_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        SplitMode.EVERY_N -> OutlinedTextField(
            value = s.splitN, onValueChange = vm::setSplitN, modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.tool_split_n_label)) }, singleLine = true,
            isError = s.splitGroups == null, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        SplitMode.EVERY_PAGE -> Unit
    }
    val count = s.splitGroups?.size?.let { if (s.splitMode == SplitMode.RANGES && s.splitAsOne) 1 else it }
    if (count != null) {
        Text(pluralText(R.plurals.tool_split_result, count, count), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun DocxOptions(vm: PdfToolViewModel, s: ToolUiState, word: Boolean = true) {
    SectionLabel(R.string.tool_sheet_pages)
    Chips {
        SHEET_PAGE_COUNTS.forEach { n -> FilterChip(s.sheetPerPage == n, { vm.setSheetPerPage(n) }, { Text("$n") }) }
    }
    SectionLabel(R.string.tool_sheet_orientation)
    Chips {
        FilterChip(!s.sheetLandscape, { vm.setSheetLandscape(false) }, { Text(stringResource(R.string.tool_sheet_portrait)) })
        FilterChip(s.sheetLandscape, { vm.setSheetLandscape(true) }, { Text(stringResource(R.string.tool_sheet_landscape)) })
    }
    Spacer(Modifier.height(4.dp))
    SwitchLine(R.string.tool_sheet_border, s.sheetBorder, vm::setSheetBorder)
    if (s.sheetBorder) {
        Text(stringResource(R.string.tool_sheet_border_width, "%.1f".format(s.sheetBorderWidth)), style = MaterialTheme.typography.bodyMedium)
        Slider(
            value = s.sheetBorderWidth, onValueChange = vm::setSheetBorderWidth,
            valueRange = BORDER_MIN..BORDER_MAX, steps = ((BORDER_MAX - BORDER_MIN) / 0.5f).toInt() - 1,
        )
    }
    Box(Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) { SheetPreview(s.sheetStyle) }
    Text(
        stringResource(if (word) R.string.tool_sheet_note else R.string.tool_sheet_note_pdf),
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp),
    )
}

/** A small drawing of one sheet with its pages and frames, using the same layout maths as the conversion. */
@Composable
private fun SheetPreview(style: NUpStyle) {
    val sheetColor = MaterialTheme.colorScheme.surfaceVariant
    val pageColor = MaterialTheme.colorScheme.surface
    val frameColor = MaterialTheme.colorScheme.onSurface
    val edgeColor = MaterialTheme.colorScheme.outlineVariant
    val sheetW = if (style.landscape) SHEET_A4_HEIGHT else SHEET_A4_WIDTH
    val sheetH = if (style.landscape) SHEET_A4_WIDTH else SHEET_A4_HEIGHT
    Canvas(Modifier.height(if (style.landscape) 150.dp else 210.dp).aspectRatio(sheetW / sheetH)) {
        val k = size.width / sheetW
        drawRect(sheetColor)
        val (columns, rows) = nUpGrid(style.perSheet, style.landscape)
        nUpCells(sheetW, sheetH, columns, rows, sheetMargin(style.perSheet), sheetGap(style.perSheet)).forEach { cell ->
            // The pages of a Word file are A4 portrait.
            val put = fitInto(SHEET_A4_WIDTH, SHEET_A4_HEIGHT, cell)
            val topLeft = Offset(put.x * k, size.height - (put.y + put.height) * k)
            val box = Size(put.width * k, put.height * k)
            drawRect(pageColor, topLeft, box)
            drawRect(if (style.border) frameColor else edgeColor, topLeft, box, style = Stroke(if (style.border) maxOf(1f, style.borderWidth * k) else 1f))
        }
    }
}

@Composable
private fun NumberOptions(vm: PdfToolViewModel, s: ToolUiState) {
    SectionLabel(R.string.tool_number_format)
    Chips {
        FilterChip(s.numberFormat == PageNumberFormat.NUMBER, { vm.setNumberFormat(PageNumberFormat.NUMBER) }, { Text("1") })
        FilterChip(s.numberFormat == PageNumberFormat.PAGE_N, { vm.setNumberFormat(PageNumberFormat.PAGE_N) }, { Text("Page 1") })
        FilterChip(s.numberFormat == PageNumberFormat.N_OF_TOTAL, { vm.setNumberFormat(PageNumberFormat.N_OF_TOTAL) }, { Text("1 / ${s.pageCount}") })
        FilterChip(s.numberFormat == PageNumberFormat.PAGE_N_OF_TOTAL, { vm.setNumberFormat(PageNumberFormat.PAGE_N_OF_TOTAL) }, { Text("Page 1 of ${s.pageCount}") })
    }
    SectionLabel(R.string.tool_number_position)
    Chips {
        NumberPosition.entries.forEach { p ->
            FilterChip(s.numberPosition == p, { vm.setNumberPosition(p) }, { Text(stringResource(positionLabel(p))) })
        }
    }
    SectionLabel(R.string.tool_number_size)
    Chips {
        FilterChip(s.numberSize == NumberSize.SMALL, { vm.setNumberSize(NumberSize.SMALL) }, { Text(stringResource(R.string.tool_size_small)) })
        FilterChip(s.numberSize == NumberSize.MEDIUM, { vm.setNumberSize(NumberSize.MEDIUM) }, { Text(stringResource(R.string.tool_size_medium)) })
        FilterChip(s.numberSize == NumberSize.LARGE, { vm.setNumberSize(NumberSize.LARGE) }, { Text(stringResource(R.string.tool_size_large)) })
    }
    OutlinedTextField(
        value = s.numberStart, onValueChange = vm::setNumberStart, modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        label = { Text(stringResource(R.string.tool_number_start)) }, singleLine = true,
        isError = s.numberStart.toIntOrNull() == null, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    )
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.tool_number_skip_first), Modifier.weight(1f))
        Switch(checked = s.numberSkipFirst, onCheckedChange = vm::setNumberSkipFirst)
    }
}

private fun positionLabel(p: NumberPosition): Int = when (p) {
    NumberPosition.BOTTOM_LEFT -> R.string.tool_pos_bottom_left
    NumberPosition.BOTTOM_CENTER -> R.string.tool_pos_bottom_center
    NumberPosition.BOTTOM_RIGHT -> R.string.tool_pos_bottom_right
    NumberPosition.TOP_LEFT -> R.string.tool_pos_top_left
    NumberPosition.TOP_CENTER -> R.string.tool_pos_top_center
    NumberPosition.TOP_RIGHT -> R.string.tool_pos_top_right
}

// ---- page picking ----------------------------------------------------------------------------------------------------------------------

@Composable
private fun PageSelectorHeader(vm: PdfToolViewModel, s: ToolUiState) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SectionLabel(R.string.tool_pages)
        OutlinedTextField(
            value = s.rangeText, onValueChange = vm::setRangeText, modifier = Modifier.fillMaxWidth(), singleLine = true,
            label = { Text(stringResource(R.string.tool_pages_label)) },
            supportingText = { Text(stringResource(if (s.rangeInvalid) R.string.tool_pages_invalid else R.string.tool_pages_hint, s.pageCount)) },
            isError = s.rangeInvalid,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = vm::selectAllPages) { Text(stringResource(R.string.tool_select_all)) }
            TextButton(onClick = vm::clearSelection, enabled = s.selected.isNotEmpty()) { Text(stringResource(R.string.tool_clear)) }
            Spacer(Modifier.weight(1f))
            Text(stringResource(R.string.tool_selected_count, s.selected.size, s.pageCount), style = MaterialTheme.typography.bodySmall)
        }
        val noteRes = when (s.tool) {
            PdfTool.ROTATE -> R.string.tool_note_rotate_all
            PdfTool.PDF_TO_IMAGES -> R.string.tool_note_images_all
            PdfTool.DELETE -> R.string.tool_note_delete
            else -> null
        }
        if (noteRes != null) Text(stringResource(noteRes), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PageCell(vm: PdfToolViewModel, s: ToolUiState, page: Int, modifier: Modifier) {
    val selected = page in s.selected
    val thumb by produceState<Bitmap?>(null, page, s.inputs) { value = vm.thumbnail(page) }
    val shape = RoundedCornerShape(8.dp)
    val accent = if (s.tool == PdfTool.DELETE) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    val turn = if (selected && s.tool == PdfTool.ROTATE) s.rotation.toFloat() else 0f
    Box(
        modifier.aspectRatio(0.72f).clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(if (selected) 3.dp else 1.dp, if (selected) accent else MaterialTheme.colorScheme.outlineVariant, shape)
            .clickable { vm.togglePage(page) },
        contentAlignment = Alignment.Center,
    ) {
        val bmp = thumb
        if (bmp != null) {
            val image = remember(bmp) { bmp.asImageBitmap() }
            val shrink = if (turn % 180f != 0f) 0.7f else 1f
            Image(
                bitmap = image, contentDescription = stringResource(R.string.tool_page_n, page + 1), contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().padding(4.dp).graphicsLayer { rotationZ = turn; scaleX = shrink; scaleY = shrink },
            )
        } else {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        }
        if (selected && s.tool == PdfTool.DELETE) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.error.copy(alpha = 0.25f)))
        }
        Surface(color = if (selected) accent else MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(topEnd = 6.dp), modifier = Modifier.align(Alignment.BottomStart)) {
            Text(
                "${page + 1}", style = MaterialTheme.typography.labelSmall,
                color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
        if (selected) {
            Surface(color = accent, shape = CircleShape, modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(22.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(14.dp))
                }
            }
        }
    }
}

// ---- running / result ------------------------------------------------------------------------------------------------------------------

@Composable
private fun ProgressContent(run: ToolRun.Running, onCancel: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(stringResource(R.string.tool_working), style = MaterialTheme.typography.titleMedium)
        if (run.total > 0) {
            LinearProgressIndicator(progress = { run.done.toFloat() / run.total }, modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp))
            Text(stringResource(R.string.tool_progress, run.done.coerceAtMost(run.total), run.total))
        } else {
            LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 16.dp))
        }
        TextButton(onClick = onCancel, modifier = Modifier.padding(top = 8.dp)) { Text(stringResource(R.string.tool_cancel)) }
    }
}

@Composable
private fun ResultContent(run: ToolRun.Done, onOpenPdf: (Uri) -> Unit, onDone: () -> Unit, onAnother: () -> Unit) {
    val context = LocalContext.current
    val single = run.outputs.singleOrNull()?.takeIf { !run.inFolder && it.name.endsWith(".pdf", ignoreCase = true) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Text(stringResource(R.string.tool_done), style = MaterialTheme.typography.headlineSmall)
            Text(
                if (run.inFolder) pluralText(R.plurals.tool_saved_files, run.outputs.size, run.outputs.size)
                else stringResource(R.string.tool_saved_one),
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp),
            )
        }
        items(run.outputs, key = { it.uri.toString() }) { out ->
            Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                Column(Modifier.fillMaxWidth().padding(12.dp)) {
                    Text(out.name, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    val detail = pluralText(R.plurals.tool_pages, out.pages, out.pages) + (out.sizeBytes?.let { " · " + formatFileSize(it) } ?: "")
                    Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val before = run.sizeBefore
                    val after = out.sizeBytes
                    if (before != null && after != null && before > 0 && after < before) {
                        Text(
                            stringResource(R.string.tool_compress_result, formatFileSize(before), formatFileSize(after), ((before - after) * 100 / before).toInt()),
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                if (single != null) {
                    Button(onClick = { onOpenPdf(single.uri) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.tool_open)) }
                    OutlinedButton(onClick = { sharePdf(context, single.uri) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.tool_share)) }
                }
                OutlinedButton(onClick = onAnother, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.tool_another)) }
                TextButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.tool_close)) }
            }
        }
    }
}

@Composable
private fun FailedContent(detail: String?, wrongPassword: Boolean, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(
            stringResource(if (wrongPassword) R.string.tool_wrong_password else R.string.tool_failed),
            style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error,
        )
        if (!wrongPassword && !detail.isNullOrBlank()) Text(detail, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
        Button(onClick = onBack, modifier = Modifier.padding(top = 16.dp)) { Text(stringResource(R.string.tool_try_again)) }
    }
}

/** "1 page" / "5 pages" from a plurals resource; the count is also the format argument. */
@Composable
private fun pluralText(id: Int, count: Int, vararg args: Any): String =
    LocalContext.current.resources.getQuantityString(id, count, *(if (args.isEmpty()) arrayOf<Any>(count) else args))

@Composable
private fun NoGainContent(run: ToolRun.NoGain, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(stringResource(R.string.tool_no_gain_title), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(R.string.tool_no_gain_body, formatFileSize(run.before), formatFileSize(run.after)),
            modifier = Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodyMedium,
        )
        Button(onClick = onBack, modifier = Modifier.padding(top = 16.dp)) { Text(stringResource(R.string.tool_try_again)) }
    }
}

private val ROW_HEIGHT = 88.dp

/** Pages in their new order. Press and hold a row, then drag it up or down. */
private fun androidx.compose.foundation.lazy.LazyListScope.reorderItems(vm: PdfToolViewModel, s: ToolUiState) {
    item(key = "reorder-header") {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.tool_reorder_hint), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.tool_lossless_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = vm::reverseOrder) { Text(stringResource(R.string.tool_reverse)) }
                OutlinedButton(onClick = vm::resetOrder, enabled = s.order != (0 until s.pageCount).toList()) { Text(stringResource(R.string.tool_reset_order)) }
            }
        }
    }
    items(s.order, key = { "page$it" }) { page ->
        ReorderRow(vm, s, page)
    }
}

@Composable
private fun androidx.compose.foundation.lazy.LazyItemScope.ReorderRow(vm: PdfToolViewModel, s: ToolUiState, page: Int) {
    val thumb by produceState<Bitmap?>(null, page, s.inputs) { value = vm.thumbnail(page) }
    var dragging by remember(page) { androidx.compose.runtime.mutableStateOf(false) }
    var dragDy by remember(page) { androidx.compose.runtime.mutableFloatStateOf(0f) }
    val position = s.order.indexOf(page)
    val moved = position != page
    val shape = RoundedCornerShape(10.dp)
    Surface(
        shape = shape,
        color = if (dragging) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        shadowElevation = if (dragging) 8.dp else 0.dp,
        modifier = Modifier
            .then(if (dragging) Modifier else Modifier.animateItem())
            .fillMaxWidth()
            .height(ROW_HEIGHT)
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .graphicsLayer { translationY = if (dragging) dragDy else 0f }
            .zIndex(if (dragging) 1f else 0f)
            .pointerInput(page) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { dragging = true; dragDy = 0f },
                    onDrag = { change, amount ->
                        change.consume()
                        dragDy += amount.y
                        val rowPx = ROW_HEIGHT.toPx()
                        var current = vm.state.value.order.indexOf(page)
                        val last = vm.state.value.order.lastIndex
                        while (dragDy > rowPx / 2 && current < last) { vm.moveInOrder(current, current + 1); current++; dragDy -= rowPx }
                        while (dragDy < -rowPx / 2 && current > 0) { vm.moveInOrder(current, current - 1); current--; dragDy += rowPx }
                    },
                    onDragEnd = { dragging = false; dragDy = 0f },
                    onDragCancel = { dragging = false; dragDy = 0f },
                )
            },
    ) {
        Row(Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.fillMaxHeight().aspectRatio(0.72f).clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.surface), contentAlignment = Alignment.Center) {
                val bmp = thumb
                if (bmp != null) {
                    val image = remember(bmp) { bmp.asImageBitmap() }
                    Image(bitmap = image, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                } else {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                }
            }
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(stringResource(R.string.tool_page_n, page + 1), style = MaterialTheme.typography.bodyLarge)
                Text(
                    stringResource(R.string.tool_now_position, position + 1),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (moved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(Icons.Default.DragHandle, contentDescription = stringResource(R.string.tool_drag_handle), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PasswordField(value: String, onChange: (String) -> Unit, label: Int, show: Boolean, isError: Boolean = false, supporting: Int? = null) {
    OutlinedTextField(
        value = value, onValueChange = onChange, singleLine = true, isError = isError,
        label = { Text(stringResource(label)) },
        supportingText = supporting?.let { res -> { Text(stringResource(res)) } },
        visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
    )
}

@Composable
private fun SwitchLine(label: Int, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(label), Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun EncryptOptions(vm: PdfToolViewModel, s: ToolUiState) {
    SectionLabel(R.string.tool_password)
    PasswordField(s.password, vm::setPassword, R.string.tool_password_new, s.showPassword)
    PasswordField(
        s.passwordConfirm, vm::setPasswordConfirm, R.string.tool_password_confirm, s.showPassword,
        isError = s.passwordConfirm.isNotEmpty() && s.password != s.passwordConfirm,
        supporting = if (s.passwordConfirm.isNotEmpty() && s.password != s.passwordConfirm) R.string.tool_password_mismatch else null,
    )
    SwitchLine(R.string.tool_show_password, s.showPassword, vm::setShowPassword)
    SectionLabel(R.string.tool_encryption)
    Chips {
        FilterChip(s.strength == EncryptStrength.STANDARD, { vm.setStrength(EncryptStrength.STANDARD) }, { Text(stringResource(R.string.tool_aes_128)) })
        FilterChip(s.strength == EncryptStrength.STRONG, { vm.setStrength(EncryptStrength.STRONG) }, { Text(stringResource(R.string.tool_aes_256)) })
    }
    SectionLabel(R.string.tool_permissions)
    SwitchLine(R.string.tool_allow_print, s.allowPrint, vm::setAllowPrint)
    SwitchLine(R.string.tool_allow_copy, s.allowCopy, vm::setAllowCopy)
    SwitchLine(R.string.tool_allow_edit, s.allowEdit, vm::setAllowEdit)
    Text(
        stringResource(R.string.tool_encrypt_note),
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp),
    )
}

@Composable
private fun DecryptOptions(vm: PdfToolViewModel, s: ToolUiState) {
    when {
        !s.encrypted -> Text(stringResource(R.string.tool_not_protected), color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
        s.locked -> {
            SectionLabel(R.string.tool_password)
            PasswordField(s.password, vm::setPassword, R.string.tool_password_current, s.showPassword)
            SwitchLine(R.string.tool_show_password, s.showPassword, vm::setShowPassword)
        }
        else -> Text(stringResource(R.string.tool_restricted_note), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
    }
    Text(
        stringResource(R.string.tool_decrypt_note),
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp),
    )
}
