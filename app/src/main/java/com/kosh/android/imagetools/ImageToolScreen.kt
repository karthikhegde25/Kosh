@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.kosh.android.imagetools

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kosh.android.KoshViewModelFactory
import com.kosh.android.R
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

fun imageToolTitle(t: ImageTool): Int = when (t) {
    ImageTool.CONVERT -> R.string.img_convert
    ImageTool.COMPRESS -> R.string.img_compress
    ImageTool.RESIZE -> R.string.img_resize
    ImageTool.CROP -> R.string.img_crop
    ImageTool.ROTATE -> R.string.img_rotate
    ImageTool.COLOR -> R.string.img_color
    ImageTool.FILTERS -> R.string.img_filters
    ImageTool.BORDERS -> R.string.img_borders
    ImageTool.ROUNDED -> R.string.img_rounded
    ImageTool.WATERMARK -> R.string.img_watermark
    ImageTool.STAMP -> R.string.img_stamp
    ImageTool.SOCIAL -> R.string.img_social
    ImageTool.PALETTE -> R.string.img_palette
    ImageTool.COLLAGE -> R.string.img_collage
    ImageTool.INCREASE -> R.string.img_increase
    ImageTool.STRIP -> R.string.img_strip
}

fun imageToolDescription(t: ImageTool): Int = when (t) {
    ImageTool.CONVERT -> R.string.img_convert_desc
    ImageTool.COMPRESS -> R.string.img_compress_desc
    ImageTool.RESIZE -> R.string.img_resize_desc
    ImageTool.CROP -> R.string.img_crop_desc
    ImageTool.ROTATE -> R.string.img_rotate_desc
    ImageTool.COLOR -> R.string.img_color_desc
    ImageTool.FILTERS -> R.string.img_filters_desc
    ImageTool.BORDERS -> R.string.img_borders_desc
    ImageTool.ROUNDED -> R.string.img_rounded_desc
    ImageTool.WATERMARK -> R.string.img_watermark_desc
    ImageTool.STAMP -> R.string.img_stamp_desc
    ImageTool.SOCIAL -> R.string.img_social_desc
    ImageTool.PALETTE -> R.string.img_palette_desc
    ImageTool.COLLAGE -> R.string.img_collage_desc
    ImageTool.INCREASE -> R.string.img_increase_desc
    ImageTool.STRIP -> R.string.img_strip_desc
}

@Composable
fun ImageToolScreen(onBack: () -> Unit) {
    val vm: ImageToolViewModel = viewModel(factory = KoshViewModelFactory.Factory)
    val state by vm.state.collectAsStateWithLifecycle()
    val o = state.options
    val tool = vm.tool
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current

    val pickMany = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris -> vm.addInputs(uris) }
    val pickOne = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> if (uri != null) vm.addInputs(listOf(uri)) }
    val pickOverlay = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> if (uri != null) vm.setOverlay(uri) }
    val saveImage = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/*")) { uri -> if (uri != null) vm.saveTo(uri) }
    val saveZip = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri -> if (uri != null) vm.saveTo(uri) }

    LaunchedEffect(state.savedMessage) {
        state.savedMessage?.let { snackbar.showSnackbar(it); vm.messageShown() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(imageToolTitle(tool))) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.tools_back)) }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(stringResource(imageToolDescription(tool)), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

            // ---- pictures ----
            InputsSection(
                state = state,
                onPick = { if (tool.maxInputs == 1) pickOne.launch("image/*") else pickMany.launch("image/*") },
                onRemove = vm::removeInput,
            )
            if (tool == ImageTool.STAMP) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.img_stamp_overlay), style = MaterialTheme.typography.titleSmall)
                        state.overlay?.let { Text("${it.name} · ${it.width}×${it.height}", style = MaterialTheme.typography.bodySmall) }
                        OutlinedButton(onClick = { pickOverlay.launch("image/*") }) {
                            Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                            Text(stringResource(if (state.overlay == null) R.string.img_choose_overlay else R.string.img_change_overlay))
                        }
                    }
                }
            }
            if (state.notAnImage) Text(stringResource(R.string.img_not_an_image), color = MaterialTheme.colorScheme.error)

            if (state.inputs.size >= tool.minInputs || tool == ImageTool.COLLAGE) {
                // ---- settings ----
                OptionsFor(tool, state, vm)

                // ---- live result ----
                val shown = if (tool == ImageTool.CROP) null else state.preview
                if (tool == ImageTool.CROP) {
                    state.cropSource?.let { src ->
                        CropEditor(src, o.crop, o.cropAspect.ratio, state.inputs.first().width, state.inputs.first().height) { box -> vm.update { it.copy(crop = box) } }
                    }
                } else if (shown != null && tool in ImageToolViewModel.LIVE_PREVIEW) {
                    PreviewImage(
                        shown,
                        drag = if (tool == ImageTool.STAMP && o.stampPosition == Corner.FREE) { x, y -> vm.update { it.copy(stampX = x, stampY = y) } } else null,
                    )
                }

                // ---- run ----
                RunSection(state, vm, tool, saveImage = { saveImage.launch(vm.suggestedSaveName()) }, saveZip = { saveZip.launch(vm.suggestedSaveName()) })
                if (tool == ImageTool.PALETTE && state.palette.isNotEmpty()) PaletteResult(state.palette)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------------

@Composable
private fun InputsSection(state: ImageToolState, onPick: () -> Unit, onRemove: (Int) -> Unit) {
    val tool = state.tool
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            state.inputs.forEachIndexed { i, input ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(input.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                        Text("${input.width}×${input.height} · ${formatBytes(input.sizeBytes)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { onRemove(i) }) { Icon(Icons.Default.Close, contentDescription = stringResource(R.string.img_remove)) }
                }
            }
            val canAdd = state.inputs.size < tool.maxInputs || tool.maxInputs == 1
            if (canAdd) {
                OutlinedButton(onClick = onPick, enabled = !state.loading) {
                    Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                    Text(
                        stringResource(
                            when {
                                state.inputs.isEmpty() && tool.maxInputs == 1 -> R.string.img_choose_one
                                state.inputs.isEmpty() -> R.string.img_choose_many
                                tool.maxInputs == 1 -> R.string.img_change_one
                                else -> R.string.img_add_more
                            },
                        ),
                    )
                }
            }
            if (tool == ImageTool.COLLAGE && state.inputs.size < 2) Text(stringResource(R.string.img_collage_need), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun PreviewImage(bmp: Bitmap, drag: ((Float, Float) -> Unit)?) {
    val checker = MaterialTheme.colorScheme.surfaceVariant
    var modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp).clip(MaterialTheme.shapes.medium).background(checker)
    if (drag != null) {
        modifier = modifier.pointerInput(Unit) {
            // Dragging on the preview places the stamp; the picture is shown fitted, so use its own box.
            fun set(p: Offset) {
                val boxW = size.width.toFloat(); val boxH = size.height.toFloat()
                val k = min(boxW / bmp.width, boxH / bmp.height)
                val iw = bmp.width * k; val ih = bmp.height * k
                val left = (boxW - iw) / 2f; val top = (boxH - ih) / 2f
                drag(((p.x - left) / iw).coerceIn(0f, 1f), ((p.y - top) / ih).coerceIn(0f, 1f))
            }
            detectDragGestures(onDragStart = { set(it) }, onDrag = { change, _ -> set(change.position) })
        }
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        Image(bmp.asImageBitmap(), contentDescription = stringResource(R.string.img_preview), contentScale = ContentScale.Fit, modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp))
    }
    if (drag != null) Text(stringResource(R.string.img_stamp_drag), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** The picture with a box you can drag, or resize from its corners. The box is kept as fractions of the picture. */
@Composable
private fun CropEditor(src: Bitmap, box: CropBox, ratio: Double?, imgW: Int, imgH: Int, onChange: (CropBox) -> Unit) {
    val currentBox by rememberUpdatedState(box)
    val primary = MaterialTheme.colorScheme.primary
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.fillMaxWidth().aspectRatio(src.width.toFloat() / src.height).clip(MaterialTheme.shapes.small)) {
            Image(src.asImageBitmap(), contentDescription = stringResource(R.string.img_preview), contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize())
            Canvas(
                Modifier.fillMaxSize().pointerInput(ratio) {
                    var mode = -1 // 0..3 corners (TL, TR, BL, BR), 4 = move, -1 = nothing
                    val grab = 44.dp.toPx()
                    detectDragGestures(
                        onDragStart = { p ->
                            val b = currentBox
                            val w = size.width.toFloat(); val h = size.height.toFloat()
                            val corners = listOf(
                                Offset((b.x * w).toFloat(), (b.y * h).toFloat()), Offset(((b.x + b.w) * w).toFloat(), (b.y * h).toFloat()),
                                Offset((b.x * w).toFloat(), ((b.y + b.h) * h).toFloat()), Offset(((b.x + b.w) * w).toFloat(), ((b.y + b.h) * h).toFloat()),
                            )
                            val nearest = corners.withIndex().minByOrNull { (_, c) -> (c - p).getDistance() }!!
                            mode = when {
                                (nearest.value - p).getDistance() <= grab -> nearest.index
                                p.x >= b.x * w && p.x <= (b.x + b.w) * w && p.y >= b.y * h && p.y <= (b.y + b.h) * h -> 4
                                else -> -1
                            }
                        },
                        onDrag = { change, delta ->
                            change.consume()
                            val b = currentBox
                            val dx = delta.x / size.width; val dy = delta.y / size.height
                            val next = when (mode) {
                                4 -> CropBox((b.x + dx).coerceIn(0.0, 1.0 - b.w), (b.y + dy).coerceIn(0.0, 1.0 - b.h), b.w, b.h)
                                in 0..3 -> {
                                    val left = mode == 0 || mode == 2; val top = mode == 0 || mode == 1
                                    var nx = if (left) b.x + dx else b.x
                                    var ny = if (top) b.y + dy else b.y
                                    var nr = if (left) b.x + b.w else b.x + b.w + dx
                                    var nb = if (top) b.y + b.h else b.y + b.h + dy
                                    nx = nx.coerceIn(0.0, 1.0); ny = ny.coerceIn(0.0, 1.0); nr = nr.coerceIn(0.0, 1.0); nb = nb.coerceIn(0.0, 1.0)
                                    var nw = abs(nr - nx).coerceAtLeast(0.02); var nh = abs(nb - ny).coerceAtLeast(0.02)
                                    if (ratio != null) {
                                        nh = nw * imgW / (imgH * ratio)
                                        if (nh > 1.0) { nh = 1.0; nw = nh * imgH * ratio / imgW }
                                    }
                                    val fx = if (left) b.x + b.w - nw else b.x
                                    val fy = if (top) b.y + b.h - nh else b.y
                                    clampCropBox(CropBox(fx, fy, nw, nh))
                                }
                                else -> b
                            }
                            if (next != b) onChange(next)
                        },
                    )
                },
            ) {
                val b = currentBox
                val x = (b.x * size.width).toFloat(); val y = (b.y * size.height).toFloat()
                val w = (b.w * size.width).toFloat(); val h = (b.h * size.height).toFloat()
                val dim = Color(0x99000000)
                drawRect(dim, Offset.Zero, Size(size.width, y))
                drawRect(dim, Offset(0f, y + h), Size(size.width, size.height - y - h))
                drawRect(dim, Offset(0f, y), Size(x, h))
                drawRect(dim, Offset(x + w, y), Size(size.width - x - w, h))
                drawRect(Color.White, Offset(x, y), Size(w, h), style = Stroke(2.dp.toPx()))
                for (c in listOf(Offset(x, y), Offset(x + w, y), Offset(x, y + h), Offset(x + w, y + h))) drawCircle(primary, 9.dp.toPx(), c)
            }
        }
        Text(stringResource(R.string.img_crop_drag), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun RunSection(state: ImageToolState, vm: ImageToolViewModel, tool: ImageTool, saveImage: () -> Unit, saveZip: () -> Unit) {
    val run = state.run
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        when (run) {
            is ImgRun.Working -> {
                Text(stringResource(R.string.img_working, run.done.coerceAtMost(run.total), run.total))
                LinearProgressIndicator(progress = { if (run.total == 0) 0f else run.done.toFloat() / run.total }, modifier = Modifier.fillMaxWidth())
                TextButton(onClick = vm::cancel) { Text(stringResource(R.string.cancel)) }
            }
            else -> Button(onClick = vm::run, enabled = state.canRun, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                Text(stringResource(R.string.img_run))
            }
        }
        if (run is ImgRun.Failed) Text(run.message, color = MaterialTheme.colorScheme.error)
        if (run is ImgRun.Done) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.img_done, run.files.size), style = MaterialTheme.typography.titleSmall)
                    run.files.take(12).forEach { f ->
                        val detail = if (f.originalBytes != null) "${formatBytes(f.originalBytes)} → ${formatBytes(f.bytes)}" else formatBytes(f.bytes)
                        Text("${f.name} · $detail", style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    if (run.files.size > 12) Text(stringResource(R.string.img_and_more, run.files.size - 12), style = MaterialTheme.typography.bodySmall)
                    Button(onClick = { if (vm.savesZip()) saveZip() else saveImage() }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(if (run.files.size > 1) R.string.img_save_zip else R.string.img_save))
                    }
                }
            }
        }
    }
}

@Composable
private fun PaletteResult(colors: List<Int>) {
    val context = LocalContext.current
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                colors.forEach { c ->
                    Column(
                        Modifier.clickable { copyText(context, argbToHex(c)) }.width72(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(Modifier.size(56.dp).clip(CircleShape).background(Color(c)).border(1.dp, MaterialTheme.colorScheme.outline, CircleShape))
                        Text(argbToHex(c), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            Text(stringResource(R.string.img_palette_tap), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick = { copyText(context, colors.joinToString(", ") { argbToHex(it) }) }) {
                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                Text(stringResource(R.string.img_palette_copy_all))
            }
        }
    }
}

private fun Modifier.width72(): Modifier = this.then(Modifier.size(width = 72.dp, height = 84.dp))

private fun copyText(context: Context, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("colours", text))
}

// ---------------------------------------------------------------------------------------------------------------------
// Settings of each tool
// ---------------------------------------------------------------------------------------------------------------------

@Composable
private fun OptionsFor(tool: ImageTool, state: ImageToolState, vm: ImageToolViewModel) {
    val o = state.options
    val upd = vm::update
    val first = state.inputs.firstOrNull()
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            when (tool) {
                ImageTool.CONVERT -> {}
                ImageTool.COMPRESS -> {
                    Label(R.string.img_mode)
                    Chips(listOf(false to R.string.img_by_quality, true to R.string.img_by_size), o.compressByTarget) { v -> upd { it.copy(compressByTarget = v) } }
                    if (o.compressByTarget) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            NumberField(R.string.img_target_size, o.targetValue, decimal = true, modifier = Modifier.weight(1f)) { v -> upd { it.copy(targetValue = v) } }
                            Chips(listOf(false to R.string.img_kb, true to R.string.img_mb), o.targetMegabytes) { v -> upd { it.copy(targetMegabytes = v) } }
                        }
                        if (o.format == OutFormat.JPG) SwitchRow(R.string.img_exact_size, o.exactSize) { v -> upd { it.copy(exactSize = v) } }
                    }
                }
                ImageTool.RESIZE -> ResizeOptions(o, first, upd)
                ImageTool.CROP -> {
                    Label(R.string.img_aspect)
                    Chips(CropAspect.entries.map { it to null }, o.cropAspect, labels = { it.label }) { a -> vm.setCropAspect(a) }
                    first?.let { f ->
                        val r = o.crop.toRect(f.width, f.height)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("X" to r.x, "Y" to r.y, "W" to r.w, "H" to r.h).forEachIndexed { i, (label, value) ->
                                NumberField(label, value.toString(), modifier = Modifier.weight(1f)) { text ->
                                    val n = text.toIntOrNull() ?: return@NumberField
                                    when (i) {
                                        0 -> vm.setCropPixels(n, r.y, r.w, r.h)
                                        1 -> vm.setCropPixels(r.x, n, r.w, r.h)
                                        2 -> vm.setCropPixels(r.x, r.y, n, if (o.cropAspect.ratio != null) (n / o.cropAspect.ratio).roundToInt() else r.h)
                                        else -> vm.setCropPixels(r.x, r.y, if (o.cropAspect.ratio != null) (n * o.cropAspect.ratio).roundToInt() else r.w, n)
                                    }
                                }
                            }
                        }
                        if (state.inputs.size > 1) Text(stringResource(R.string.img_crop_same), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                ImageTool.ROTATE -> {
                    Label(R.string.img_rotation)
                    Chips(listOf(0 to null, 90 to null, 180 to null, 270 to null), o.rotation, labels = { "$it°" }) { v -> upd { it.copy(rotation = v) } }
                    Label(R.string.img_mirror)
                    Chips(
                        listOf(Mirror.NONE to R.string.img_mirror_none, Mirror.HORIZONTAL to R.string.img_mirror_h, Mirror.VERTICAL to R.string.img_mirror_v, Mirror.BOTH to R.string.img_mirror_both),
                        o.mirror,
                    ) { v -> upd { it.copy(mirror = v) } }
                }
                ImageTool.COLOR -> {
                    Slide(R.string.img_brightness, o.brightness, -100..100) { v -> upd { it.copy(brightness = v) } }
                    Slide(R.string.img_contrast, o.contrast, -100..100) { v -> upd { it.copy(contrast = v) } }
                    Slide(R.string.img_saturation, o.saturation, -100..100) { v -> upd { it.copy(saturation = v) } }
                    Slide(R.string.img_hue, o.hue, -180..180) { v -> upd { it.copy(hue = v) } }
                    Slide(R.string.img_exposure, o.exposure, -100..100) { v -> upd { it.copy(exposure = v) } }
                    TextButton(onClick = { upd { it.copy(brightness = 0, contrast = 0, saturation = 0, hue = 0, exposure = 0) } }) { Text(stringResource(R.string.img_reset_all)) }
                }
                ImageTool.FILTERS -> {
                    Label(R.string.img_effect)
                    Chips(FilterEffect.entries.map { it to effectLabel(it) }, o.effect) { v -> upd { it.copy(effect = v) } }
                    Slide(R.string.img_brightness, o.filterBrightness, -100..100) { v -> upd { it.copy(filterBrightness = v) } }
                    Slide(R.string.img_contrast, o.filterContrast, -100..100) { v -> upd { it.copy(filterContrast = v) } }
                }
                ImageTool.BORDERS -> {
                    SwitchRow(R.string.img_pad_all, o.padAll) { v -> upd { it.copy(padAll = v) } }
                    if (o.padAll) Slide(R.string.img_padding, o.padding, 0..400) { v -> upd { it.copy(padding = v) } }
                    else {
                        Slide(R.string.img_pad_top, o.padTop, 0..400) { v -> upd { it.copy(padTop = v) } }
                        Slide(R.string.img_pad_right, o.padRight, 0..400) { v -> upd { it.copy(padRight = v) } }
                        Slide(R.string.img_pad_bottom, o.padBottom, 0..400) { v -> upd { it.copy(padBottom = v) } }
                        Slide(R.string.img_pad_left, o.padLeft, 0..400) { v -> upd { it.copy(padLeft = v) } }
                    }
                    Label(R.string.img_fill)
                    Chips(listOf(false to R.string.img_fill_solid, true to R.string.img_fill_gradient), o.gradient) { v -> upd { it.copy(gradient = v) } }
                    ColorRow(o.fillColor, allowTransparent = o.format.alpha) { c -> upd { it.copy(fillColor = c) } }
                    if (o.gradient) {
                        ColorRow(o.fillColor2, allowTransparent = false) { c -> upd { it.copy(fillColor2 = c) } }
                        Chips(
                            listOf(0 to R.string.img_dir_down, 1 to R.string.img_dir_right, 2 to R.string.img_dir_diagonal),
                            o.gradientDirection,
                        ) { v -> upd { it.copy(gradientDirection = v) } }
                    }
                    Slide(R.string.img_outer_radius, o.outerRadius, 0..300) { v -> upd { it.copy(outerRadius = v) } }
                    if (o.outerRadius > 0 && !o.format.alpha) Text(stringResource(R.string.img_needs_alpha), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                ImageTool.ROUNDED -> {
                    SwitchRow(R.string.img_full_round, o.fullRound) { v -> upd { it.copy(fullRound = v) } }
                    if (!o.fullRound) Slide(R.string.img_radius, o.radius, 0..600) { v -> upd { it.copy(radius = v) } }
                }
                ImageTool.WATERMARK -> {
                    OutlinedTextField(
                        value = o.watermarkText, onValueChange = { v -> upd { it.copy(watermarkText = v) } },
                        label = { Text(stringResource(R.string.img_wm_text)) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    )
                    Label(R.string.img_position)
                    Chips(
                        listOf(Corner.BOTTOM_RIGHT to R.string.img_pos_br, Corner.BOTTOM_LEFT to R.string.img_pos_bl, Corner.TOP_RIGHT to R.string.img_pos_tr, Corner.TOP_LEFT to R.string.img_pos_tl, Corner.CENTER to R.string.img_pos_center),
                        o.watermarkPosition,
                    ) { v -> upd { it.copy(watermarkPosition = v) } }
                    Slide(R.string.img_font_size, o.watermarkSize, 8..300) { v -> upd { it.copy(watermarkSize = v) } }
                    Slide(R.string.img_opacity, o.watermarkOpacity, 5..100) { v -> upd { it.copy(watermarkOpacity = v) } }
                }
                ImageTool.STAMP -> {
                    Label(R.string.img_position)
                    Chips(
                        listOf(
                            Corner.BOTTOM_RIGHT to R.string.img_pos_br, Corner.BOTTOM_LEFT to R.string.img_pos_bl, Corner.TOP_RIGHT to R.string.img_pos_tr,
                            Corner.TOP_LEFT to R.string.img_pos_tl, Corner.CENTER to R.string.img_pos_center, Corner.FREE to R.string.img_pos_free,
                        ),
                        o.stampPosition,
                    ) { v -> upd { it.copy(stampPosition = v) } }
                    Slide(R.string.img_stamp_size, o.stampSize, 2..100) { v -> upd { it.copy(stampSize = v) } }
                    Slide(R.string.img_opacity, o.stampOpacity, 5..100) { v -> upd { it.copy(stampOpacity = v) } }
                    Slide(R.string.img_rotation, o.stampRotation, -180..180) { v -> upd { it.copy(stampRotation = v) } }
                    Slide(R.string.img_margin, o.stampMargin, 0..30) { v -> upd { it.copy(stampMargin = v) } }
                }
                ImageTool.SOCIAL -> {
                    Label(R.string.img_sizes)
                    SOCIAL_PRESETS.forEach { p ->
                        Row(Modifier.fillMaxWidth().clickable { vm.toggleSocial(p.id) }, verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = p.id in o.socialSelected, onCheckedChange = { vm.toggleSocial(p.id) })
                            Column {
                                Text(p.label, style = MaterialTheme.typography.bodyMedium)
                                Text("${p.w}×${p.h}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    Label(R.string.img_fit)
                    Chips(listOf(SocialFit.FILL to R.string.img_fit_fill, SocialFit.FIT to R.string.img_fit_inside, SocialFit.LETTERBOX to R.string.img_fit_letterbox), o.socialFit) { v -> upd { it.copy(socialFit = v) } }
                    if (o.socialFit == SocialFit.LETTERBOX) ColorRow(o.letterboxColor, allowTransparent = false) { c -> upd { it.copy(letterboxColor = c) } }
                }
                ImageTool.PALETTE -> Slide(R.string.img_colour_count, o.paletteCount, 5..12) { v -> upd { it.copy(paletteCount = v) } }
                ImageTool.COLLAGE -> {
                    val layouts = collageLayouts(state.inputs.size.coerceIn(2, 4))
                    Label(R.string.img_layout)
                    Chips(layouts.map { it to layoutLabel(it) }, o.collageLayout.takeIf { it in layouts } ?: layouts.first()) { v -> upd { it.copy(collageLayout = v) } }
                    Label(R.string.img_aspect)
                    Chips(CollageAspect.entries.map { it to null }, o.collageAspect, labels = { it.label }) { v -> upd { it.copy(collageAspect = v) } }
                    Slide(R.string.img_width_px, o.collageWidth, 600..3000) { v -> upd { it.copy(collageWidth = v) } }
                    Slide(R.string.img_gap, o.collageGap, 0..100) { v -> upd { it.copy(collageGap = v) } }
                    Slide(R.string.img_radius, o.collageRadius, 0..200) { v -> upd { it.copy(collageRadius = v) } }
                    Label(R.string.img_background)
                    ColorRow(o.collageBackground, allowTransparent = o.format.alpha) { c -> upd { it.copy(collageBackground = c) } }
                    Chips(listOf(true to R.string.img_cell_fill, false to R.string.img_cell_fit), o.collageFill) { v -> upd { it.copy(collageFill = v) } }
                }
                ImageTool.INCREASE -> {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NumberField(R.string.img_extra_size, o.increaseValue, decimal = true, modifier = Modifier.weight(1f)) { v -> upd { it.copy(increaseValue = v) } }
                        Chips(listOf(false to R.string.img_kb, true to R.string.img_mb), o.increaseMegabytes) { v -> upd { it.copy(increaseMegabytes = v) } }
                    }
                    if (o.increaseBytes() == null) Text(stringResource(R.string.img_increase_range), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(
                        value = o.increaseFill, onValueChange = { v -> upd { it.copy(increaseFill = v.take(16)) } },
                        label = { Text(stringResource(R.string.img_fill_chars)) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    )
                    first?.let { f ->
                        o.increaseBytes()?.let { extra -> Text(stringResource(R.string.img_new_size, formatBytes(f.sizeBytes), formatBytes(f.sizeBytes + extra)), style = MaterialTheme.typography.bodySmall) }
                    }
                }
                ImageTool.STRIP -> {
                    if (state.metadata.isEmpty()) Text(stringResource(R.string.img_no_metadata), style = MaterialTheme.typography.bodyMedium)
                    else {
                        Text(stringResource(R.string.img_found_metadata), style = MaterialTheme.typography.titleSmall)
                        state.metadata.forEach { m ->
                            Row(Modifier.fillMaxWidth().clickable { vm.toggleStripKind(m.kind) }, verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = m.kind in o.stripKinds, onCheckedChange = { vm.toggleStripKind(m.kind) })
                                Column(Modifier.weight(1f)) {
                                    Text(stringResource(metaLabel(m.kind)), style = MaterialTheme.typography.bodyMedium)
                                    Text(formatBytes(m.bytes.toLong()), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                        TextButton(onClick = vm::selectAllStrip) { Text(stringResource(R.string.img_select_all)) }
                    }
                    SwitchRow(R.string.img_reencode, o.stripReencode) { v -> upd { it.copy(stripReencode = v) } }
                    Text(stringResource(R.string.img_reencode_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // format, quality and name are shared by the tools that write pictures
            val formats = formatsFor(tool)
            if (formats.isNotEmpty()) {
                Label(R.string.img_format)
                Chips(formats.map { it to null }, o.format, labels = { it.extension.uppercase() }) { v -> upd { it.copy(format = v) } }
                val showQuality = o.format.lossy && !(tool == ImageTool.COMPRESS && o.compressByTarget) && tool != ImageTool.ROUNDED
                if (showQuality) Slide(R.string.img_quality, o.quality, 1..100) { v -> upd { it.copy(quality = v) } }
            }
            OutlinedTextField(
                value = o.customName, onValueChange = { v -> upd { it.copy(customName = v) } },
                label = { Text(stringResource(R.string.img_file_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ResizeOptions(o: ImageOptions, first: ImgInput?, upd: ((ImageOptions) -> ImageOptions) -> Unit) {
    Label(R.string.img_mode)
    Chips(listOf(ResizeMode.EXACT to R.string.img_rs_exact, ResizeMode.PERCENT to R.string.img_rs_percent, ResizeMode.FIT_BOX to R.string.img_rs_fit), o.resizeMode) { v -> upd { it.copy(resizeMode = v) } }
    if (o.resizeMode == ResizeMode.PERCENT) {
        Slide(R.string.img_percent, o.percent, 1..400) { v -> upd { it.copy(percent = v) } }
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField(R.string.img_width_px, o.width, modifier = Modifier.weight(1f)) { v ->
                upd { cur ->
                    val w = v.toIntOrNull()
                    val h = if (cur.lockAspect && cur.resizeMode == ResizeMode.EXACT && first != null && w != null) (w.toDouble() * first.height / first.width).roundToInt().toString() else cur.height
                    cur.copy(width = v, height = h, widthLed = true)
                }
            }
            NumberField(R.string.img_height_px, o.height, modifier = Modifier.weight(1f)) { v ->
                upd { cur ->
                    val h = v.toIntOrNull()
                    val w = if (cur.lockAspect && cur.resizeMode == ResizeMode.EXACT && first != null && h != null) (h.toDouble() * first.width / first.height).roundToInt().toString() else cur.width
                    cur.copy(height = v, width = w, widthLed = false)
                }
            }
        }
        if (o.resizeMode == ResizeMode.EXACT) {
            SwitchRow(R.string.img_lock_aspect, o.lockAspect) { v -> upd { it.copy(lockAspect = v) } }
            SwitchRow(R.string.img_letterbox, o.letterbox) { v -> upd { it.copy(letterbox = v) } }
            if (o.letterbox) ColorRow(o.letterboxColor, allowTransparent = false) { c -> upd { it.copy(letterboxColor = c) } }
        }
    }
    if (first != null && (o.resizeMode == ResizeMode.PERCENT || o.width.toIntOrNull() != null || o.height.toIntOrNull() != null)) {
        val w = o.width.toIntOrNull() ?: first.width; val h = o.height.toIntOrNull() ?: first.height
        val (rw, rh) = resizeTarget(o.resizeMode, first.width, first.height, w, h, o.percent, o.lockAspect, o.widthLed)
        val shown = if (o.letterbox && o.resizeMode == ResizeMode.EXACT) Pair(w, h) else Pair(rw, rh)
        Text(stringResource(R.string.img_result_size, shown.first, shown.second), style = MaterialTheme.typography.bodySmall)
    }
}

// ---------------------------------------------------------------------------------------------------------------------
// Small building blocks
// ---------------------------------------------------------------------------------------------------------------------

@Composable
private fun Label(res: Int) = Text(stringResource(res), style = MaterialTheme.typography.titleSmall)

/** Chips for choosing one value. A label comes from a string resource ([Pair.second]) or from [labels]. */
@Composable
private fun <T> Chips(options: List<Pair<T, Int?>>, selected: T, labels: ((T) -> String)? = null, onSelect: (T) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        options.forEach { (value, res) ->
            val text = if (res != null) stringResource(res) else labels?.invoke(value) ?: value.toString()
            FilterChip(selected = value == selected, onClick = { onSelect(value) }, label = { Text(text) })
        }
    }
}

@Composable
private fun SwitchRow(label: Int, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(stringResource(label), modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun Slide(label: Int, value: Int, range: IntRange, onChange: (Int) -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(label), style = MaterialTheme.typography.bodyMedium)
            Text(value.toString(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        }
        Slider(value = value.toFloat(), onValueChange = { onChange(it.roundToInt().coerceIn(range.first, range.last)) }, valueRange = range.first.toFloat()..range.last.toFloat())
    }
}

@Composable
private fun NumberField(label: Int, value: String, decimal: Boolean = false, modifier: Modifier = Modifier, onChange: (String) -> Unit) =
    NumberField(stringResource(label), value, decimal, modifier, onChange)

@Composable
private fun NumberField(label: String, value: String, decimal: Boolean = false, modifier: Modifier = Modifier, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { v -> onChange(v.filter { it.isDigit() || (decimal && (it == '.' || it == ',')) }.take(9)) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
        modifier = modifier,
    )
}

@Composable
private fun ColorRow(selected: Int, allowTransparent: Boolean, onSelect: (Int) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        val colors = if (allowTransparent) SWATCHES + TRANSPARENT else SWATCHES
        colors.forEach { c ->
            val on = c == selected
            Box(
                Modifier.size(36.dp).clip(CircleShape).background(if (c == TRANSPARENT) Color.White else Color(c))
                    .border(if (on) 3.dp else 1.dp, if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, CircleShape)
                    .clickable { onSelect(c) },
                contentAlignment = Alignment.Center,
            ) {
                if (c == TRANSPARENT) Icon(Icons.Default.Close, contentDescription = stringResource(R.string.img_transparent), modifier = Modifier.size(18.dp), tint = Color.Gray)
                else if (on) Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp), tint = if (c == WHITE) Color.Black else Color.White)
            }
        }
    }
}

private fun effectLabel(e: FilterEffect): Int = when (e) {
    FilterEffect.NONE -> R.string.img_fx_none
    FilterEffect.GRAYSCALE -> R.string.img_fx_grayscale
    FilterEffect.SEPIA -> R.string.img_fx_sepia
    FilterEffect.INVERT -> R.string.img_fx_invert
    FilterEffect.VINTAGE -> R.string.img_fx_vintage
    FilterEffect.WARM -> R.string.img_fx_warm
    FilterEffect.COOL -> R.string.img_fx_cool
    FilterEffect.POSTERIZE -> R.string.img_fx_posterize
    FilterEffect.THRESHOLD -> R.string.img_fx_threshold
    FilterEffect.VIGNETTE -> R.string.img_fx_vignette
    FilterEffect.BLUR -> R.string.img_fx_blur
    FilterEffect.SHARPEN -> R.string.img_fx_sharpen
}

private fun layoutLabel(id: String): Int = when (id) {
    "side" -> R.string.img_lay_side
    "stack" -> R.string.img_lay_stack
    "big_left" -> R.string.img_lay_big_left
    "big_top" -> R.string.img_lay_big_top
    "row" -> R.string.img_lay_row
    "column" -> R.string.img_lay_column
    else -> R.string.img_lay_grid
}

private fun metaLabel(k: MetaKind): Int = when (k) {
    MetaKind.EXIF -> R.string.img_meta_exif
    MetaKind.XMP -> R.string.img_meta_xmp
    MetaKind.IPTC -> R.string.img_meta_iptc
    MetaKind.ICC -> R.string.img_meta_icc
    MetaKind.COMMENT -> R.string.img_meta_comment
    MetaKind.TEXT -> R.string.img_meta_text
    MetaKind.TIME -> R.string.img_meta_time
    MetaKind.OTHER -> R.string.img_meta_other
}
