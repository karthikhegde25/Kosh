package com.kosh.android.imagetools

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.kosh.android.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class ImgInput(val uri: Uri, val name: String, val width: Int, val height: Int, val sizeBytes: Long)

data class ResultFile(val name: String, val file: File, val bytes: Long, val originalBytes: Long?, val mime: String)

sealed interface ImgRun {
    data object Idle : ImgRun
    data class Working(val done: Int, val total: Int) : ImgRun
    data class Done(val files: List<ResultFile>) : ImgRun
    data class Failed(val message: String) : ImgRun
}

data class ImageToolState(
    val tool: ImageTool,
    val inputs: List<ImgInput> = emptyList(),
    val overlay: ImgInput? = null,
    val options: ImageOptions = ImageOptions(),
    val loading: Boolean = false,
    /** The picture the settings are shown on (a small copy), or null while there is none / no live preview. */
    val preview: Bitmap? = null,
    /** The first picture, small, for the crop box. */
    val cropSource: Bitmap? = null,
    val palette: List<Int> = emptyList(),
    val metadata: List<MetaItem> = emptyList(),
    val notAnImage: Boolean = false,
    val run: ImgRun = ImgRun.Idle,
    val savedMessage: String? = null,
) {
    val canRun: Boolean
        get() = inputs.size >= tool.minInputs && run !is ImgRun.Working && !loading && when (tool) {
            ImageTool.STAMP -> overlay != null
            ImageTool.WATERMARK -> options.watermarkText.isNotBlank()
            ImageTool.SOCIAL -> options.socialSelected.isNotEmpty()
            ImageTool.INCREASE -> options.increaseBytes() != null
            ImageTool.COMPRESS -> !options.compressByTarget || options.targetBytes() != null
            ImageTool.STRIP -> options.stripReencode || options.stripKinds.isNotEmpty()
            ImageTool.RESIZE -> options.resizeMode == ResizeMode.PERCENT || options.width.toIntOrNull() != null || options.height.toIntOrNull() != null
            else -> true
        }
}

class ImageToolViewModel(app: Application, saved: SavedStateHandle) : AndroidViewModel(app) {

    val tool: ImageTool = ImageTool.fromId(saved.get<String>("imgToolId")) ?: ImageTool.CONVERT

    private val engine = ImageEngine(app)
    private val workDir = File(app.cacheDir, "imgtools-${System.nanoTime()}").apply { mkdirs() }
    private val _state = MutableStateFlow(ImageToolState(tool, options = ImageOptions(format = formatsFor(tool).firstOrNull() ?: OutFormat.PNG)))
    val state: StateFlow<ImageToolState> = _state.asStateFlow()

    private var previewSource: Bitmap? = null
    private var previewScale = 1f
    private var collageSources: List<Bitmap> = emptyList()
    private var overlaySmall: Bitmap? = null
    private var previewJob: Job? = null
    private var runJob: Job? = null

    // ---- choosing pictures ----

    /** Adds pictures (or replaces them when the tool takes one). */
    fun addInputs(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _state.update { it.copy(loading = true, notAnImage = false, run = ImgRun.Idle) }
            val current = if (tool.maxInputs == 1) emptyList() else _state.value.inputs
            val room = (tool.maxInputs - current.size).coerceAtLeast(0)
            val limit = if (tool.maxInputs == 1) 1 else room
            val loaded = withContext(Dispatchers.IO) { uris.take(limit).mapNotNull { describe(it) } }
            val all = current + loaded
            val first = all.firstOrNull()
            _state.update { s ->
                var o = s.options
                if (current.isEmpty() && first != null) {
                    o = o.copy(format = defaultFormat(first.name), crop = CropBox.FULL, width = first.width.toString(), height = first.height.toString())
                }
                s.copy(inputs = all, loading = false, notAnImage = loaded.size < minOf(uris.size, limit), options = o, palette = emptyList())
            }
            loadPreviewSources()
            if (tool == ImageTool.STRIP) loadMetadata()
        }
    }

    fun removeInput(index: Int) {
        _state.update { it.copy(inputs = it.inputs.filterIndexed { i, _ -> i != index }, run = ImgRun.Idle, palette = emptyList()) }
        viewModelScope.launch { loadPreviewSources() }
    }

    fun setOverlay(uri: Uri) {
        viewModelScope.launch {
            val info = withContext(Dispatchers.IO) { describe(uri) }
            if (info == null) { _state.update { it.copy(notAnImage = true) }; return@launch }
            overlaySmall = withContext(Dispatchers.IO) { runCatching { engine.decode(uri, 600) }.getOrNull() }
            _state.update { it.copy(overlay = info, notAnImage = false, run = ImgRun.Idle) }
            refreshPreview()
        }
    }

    private fun describe(uri: Uri): ImgInput? {
        val dims = runCatching { engine.dimensions(uri) }.getOrNull() ?: return null
        var name = "image"; var size = 0L
        getApplication<Application>().contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                c.getString(0)?.let { name = it }
                if (!c.isNull(1)) size = c.getLong(1)
            }
        }
        if (size <= 0) size = runCatching { engine.readBytes(uri).size.toLong() }.getOrDefault(0L)
        return ImgInput(uri, name, dims.first, dims.second, size)
    }

    private fun defaultFormat(fileName: String): OutFormat {
        val wanted = when (fileName.substringAfterLast('.', "").lowercase()) {
            "png" -> OutFormat.PNG
            "webp" -> OutFormat.WEBP
            else -> OutFormat.JPG
        }
        val allowed = formatsFor(tool)
        return if (wanted in allowed) wanted else allowed.firstOrNull() ?: OutFormat.PNG
    }

    // ---- settings ----

    fun update(change: (ImageOptions) -> ImageOptions) {
        _state.update { it.copy(options = change(it.options), run = if (it.run is ImgRun.Working) it.run else ImgRun.Idle) }
        refreshPreview()
    }

    fun setCropAspect(aspect: CropAspect) {
        val first = _state.value.inputs.firstOrNull() ?: return
        update { it.copy(cropAspect = aspect, crop = largestCropBox(first.width, first.height, aspect.ratio)) }
    }

    /** From the numbers typed under the crop box. */
    fun setCropPixels(x: Int, y: Int, w: Int, h: Int) {
        val first = _state.value.inputs.firstOrNull() ?: return
        val cx = x.coerceIn(0, first.width - 1); val cy = y.coerceIn(0, first.height - 1)
        val r = IRect(cx, cy, w.coerceIn(1, first.width - cx), h.coerceIn(1, first.height - cy))
        update { it.copy(crop = CropBox.fromRect(r, first.width, first.height)) }
    }

    fun toggleSocial(id: String) = update { it.copy(socialSelected = if (id in it.socialSelected) it.socialSelected - id else it.socialSelected + id) }

    fun toggleStripKind(kind: MetaKind) = update { it.copy(stripKinds = if (kind in it.stripKinds) it.stripKinds - kind else it.stripKinds + kind) }

    fun selectAllStrip() = update { it.copy(stripKinds = _state.value.metadata.map { m -> m.kind }.toSet()) }

    // ---- preview ----

    private suspend fun loadPreviewSources() {
        val inputs = _state.value.inputs
        withContext(Dispatchers.IO) {
            previewSource = inputs.firstOrNull()?.let { runCatching { engine.decode(it.uri, 900) }.getOrNull() }
            previewScale = previewSource?.let { p -> inputs.first().let { p.width.toFloat() / it.width } } ?: 1f
            collageSources = if (tool == ImageTool.COLLAGE) inputs.mapNotNull { runCatching { engine.decode(it.uri, 500) }.getOrNull() } else emptyList()
        }
        _state.update { it.copy(cropSource = if (tool == ImageTool.CROP) previewSource else null) }
        refreshPreview()
    }

    private fun refreshPreview() {
        previewJob?.cancel()
        if (tool !in LIVE_PREVIEW) return
        previewJob = viewModelScope.launch {
            delay(60)
            val s = _state.value
            val result = withContext(Dispatchers.Default) {
                runCatching {
                    if (tool == ImageTool.COLLAGE) {
                        if (collageSources.size >= 2) engine.renderCollage(collageSources, s.options, width = 700, scale = 700f / s.options.collageWidth) else null
                    } else previewSource?.let { src ->
                        val out = engine.render(tool, s.options, src, previewScale, overlaySmall)
                        // transparency is shown over white so the preview matches what a JPG would look like
                        out
                    }
                }.getOrNull()
            }
            _state.update { it.copy(preview = result) }
        }
    }

    private suspend fun loadMetadata() {
        val first = _state.value.inputs.firstOrNull() ?: return
        val items = withContext(Dispatchers.IO) { runCatching { listMetadata(engine.readBytes(first.uri)) }.getOrDefault(emptyList()) }
        _state.update { it.copy(metadata = items, options = it.options.copy(stripKinds = items.map { m -> m.kind }.toSet(), stripReencode = items.isEmpty())) }
    }

    // ---- running ----

    fun run() {
        val s = _state.value
        if (!s.canRun) return
        runJob?.cancel()
        runJob = viewModelScope.launch {
            _state.update { it.copy(run = ImgRun.Working(0, s.inputs.size), palette = emptyList(), savedMessage = null) }
            try {
                val files = withContext(Dispatchers.Default) { process(s) }
                _state.update { it.copy(run = ImgRun.Done(files)) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: OutOfMemoryError) {
                _state.update { it.copy(run = ImgRun.Failed(getApplication<Application>().getString(R.string.img_err_memory))) }
            } catch (e: Exception) {
                _state.update { it.copy(run = ImgRun.Failed(e.message ?: getApplication<Application>().getString(R.string.img_err_generic))) }
            }
        }
    }

    fun cancel() { runJob?.cancel(); _state.update { it.copy(run = ImgRun.Idle) } }

    private fun progress(done: Int, total: Int) { _state.update { if (it.run is ImgRun.Working) it.copy(run = ImgRun.Working(done, total)) else it } }

    private fun write(name: String, bytes: ByteArray, mime: String, original: Long?): ResultFile {
        val f = File(workDir, name)
        f.parentFile?.mkdirs()
        f.writeBytes(bytes)
        return ResultFile(name, f, bytes.size.toLong(), original, mime)
    }

    private suspend fun process(s: ImageToolState): List<ResultFile> {
        workDir.listFiles()?.forEach { it.delete() }
        val o = s.options
        val names = s.inputs.map { it.name }
        val ctx = kotlinx.coroutines.currentCoroutineContext()
        return when (tool) {
            ImageTool.COLLAGE -> {
                val bitmaps = s.inputs.map { engine.decode(it.uri, 2400) }
                val out = engine.renderCollage(bitmaps, o)
                bitmaps.forEach { it.recycle() }
                val fmt = o.format
                val bytes = engine.encode(out, fmt, o.quality)
                out.recycle()
                listOf(write(outputNames(listOf("collage"), o.customName, "", fmt.extension)[0], bytes, fmt.mime, null))
            }
            ImageTool.PALETTE -> {
                val bmp = engine.decode(s.inputs[0].uri, 1200)
                val colors = engine.palette(bmp, o.paletteCount)
                bmp.recycle()
                if (colors.isEmpty()) throw IOException(getApplication<Application>().getString(R.string.img_err_no_colours))
                val image = engine.paletteImage(colors)
                val bytes = engine.encode(image, OutFormat.PNG, 100)
                image.recycle()
                _state.update { it.copy(palette = colors) }
                listOf(write(outputNames(listOf(names[0]), o.customName, "palette", "png")[0], bytes, "image/png", null))
            }
            ImageTool.INCREASE -> {
                val src = engine.readBytes(s.inputs[0].uri)
                val extra = o.increaseBytes() ?: throw IOException(getApplication<Application>().getString(R.string.img_err_increase_range))
                val kind = containerOf(src)
                if (kind == ImageContainer.OTHER) throw IOException(getApplication<Application>().getString(R.string.img_err_jpg_png_only))
                val out = padFile(src, extra, o.increaseFill)
                val ext = if (kind == ImageContainer.JPEG) "jpg" else "png"
                listOf(write(outputNames(listOf(names[0]), o.customName, "bigger", ext)[0], out, if (ext == "jpg") "image/jpeg" else "image/png", src.size.toLong()))
            }
            ImageTool.STRIP -> {
                val src = engine.readBytes(s.inputs[0].uri)
                val kind = containerOf(src)
                if (o.stripReencode || kind == ImageContainer.OTHER) {
                    val bmp = engine.decode(s.inputs[0].uri, ImageEngine.MAX_SIDE)
                    val ext = names[0].substringAfterLast('.', "").lowercase()
                    val fmt = when {
                        kind == ImageContainer.JPEG -> OutFormat.JPG
                        kind == ImageContainer.PNG -> OutFormat.PNG
                        ext == "webp" -> OutFormat.WEBP
                        else -> OutFormat.PNG
                    }
                    val bytes = engine.encode(bmp, fmt, 95)
                    bmp.recycle()
                    listOf(write(outputNames(listOf(names[0]), o.customName, "clean", fmt.extension)[0], bytes, fmt.mime, src.size.toLong()))
                } else {
                    val out = stripMetadata(src, o.stripKinds)
                    val ext = if (kind == ImageContainer.JPEG) "jpg" else "png"
                    listOf(write(outputNames(listOf(names[0]), o.customName, "clean", ext)[0], out, if (ext == "jpg") "image/jpeg" else "image/png", src.size.toLong()))
                }
            }
            ImageTool.SOCIAL -> {
                val presets = SOCIAL_PRESETS.filter { it.id in o.socialSelected }
                val labels = names.flatMap { n -> presets.map { p -> (if (o.customName.isNotBlank()) safeName(o.customName) else baseName(n)) + "-" + p.id } }
                val outNames = outputNames(labels, "", "", o.format.extension)
                val result = ArrayList<ResultFile>()
                var k = 0
                for ((i, input) in s.inputs.withIndex()) {
                    ctx.ensureActive()
                    progress(i, s.inputs.size)
                    val src = engine.decode(input.uri, 4096)
                    for (p in presets) {
                        val out = engine.renderSocial(src, p, o.socialFit, o.letterboxColor)
                        val bytes = engine.encode(out, o.format, o.quality)
                        out.recycle()
                        result += write(outNames[k++], bytes, o.format.mime, input.sizeBytes)
                    }
                    src.recycle()
                }
                result
            }
            else -> {
                val suffix = when (tool) {
                    ImageTool.CONVERT -> ""
                    ImageTool.COMPRESS -> "compressed"
                    ImageTool.RESIZE -> "resized"
                    ImageTool.CROP -> "cropped"
                    ImageTool.ROTATE -> "rotated"
                    ImageTool.COLOR -> "color"
                    ImageTool.FILTERS -> "filtered"
                    ImageTool.BORDERS -> "framed"
                    ImageTool.ROUNDED -> "rounded"
                    ImageTool.WATERMARK -> "watermarked"
                    else -> "edited"
                }
                val outNames = outputNames(names, o.customName, suffix, o.format.extension)
                val overlayFull = if (tool == ImageTool.STAMP) s.overlay?.let { engine.decode(it.uri, 2048) } else null
                val maxSide = if (tool == ImageTool.COLOR || tool == ImageTool.FILTERS) 4096 else 6000
                val result = ArrayList<ResultFile>()
                try {
                    for ((i, input) in s.inputs.withIndex()) {
                        ctx.ensureActive()
                        progress(i, s.inputs.size)
                        val src = engine.decode(input.uri, maxSide)
                        val out = engine.render(tool, o, src, 1f, overlayFull)
                        val bytes = if (tool == ImageTool.COMPRESS) engine.compress(out, o) else engine.encode(out, o.format, o.quality)
                        if (out !== src) out.recycle()
                        src.recycle()
                        result += write(outNames[i], bytes, o.format.mime, input.sizeBytes)
                    }
                } finally {
                    overlayFull?.recycle()
                }
                result
            }
        }
    }

    // ---- saving ----

    /** True when saving makes one ZIP of several files. */
    fun savesZip(): Boolean = ((_state.value.run as? ImgRun.Done)?.files?.size ?: 0) > 1

    fun suggestedSaveName(): String {
        val done = (_state.value.run as? ImgRun.Done)?.files ?: return "image"
        return if (done.size == 1) done[0].name else "${tool.id.removePrefix("img_")}-images.zip"
    }

    fun saveMime(): String = if (savesZip()) "application/zip" else ((_state.value.run as? ImgRun.Done)?.files?.firstOrNull()?.mime ?: "image/*")

    fun saveTo(destination: Uri) {
        val files = (_state.value.run as? ImgRun.Done)?.files ?: return
        viewModelScope.launch {
            val app = getApplication<Application>()
            val message = withContext(Dispatchers.IO) {
                try {
                    val out = app.contentResolver.openOutputStream(destination, "w") ?: throw IOException("Can't open the destination")
                    if (files.size == 1) {
                        out.use { o -> files[0].file.inputStream().use { it.copyTo(o) } }
                    } else {
                        ZipOutputStream(out.buffered()).use { zip ->
                            for (f in files) {
                                zip.putNextEntry(ZipEntry(f.name))
                                f.file.inputStream().use { it.copyTo(zip) }
                                zip.closeEntry()
                            }
                        }
                    }
                    app.getString(R.string.img_saved)
                } catch (e: Exception) {
                    runCatching { app.contentResolver.delete(destination, null, null) }
                    app.getString(R.string.img_err_save)
                }
            }
            _state.update { it.copy(savedMessage = message) }
        }
    }

    fun messageShown() { _state.update { it.copy(savedMessage = null) } }

    override fun onCleared() {
        workDir.deleteRecursively()
        super.onCleared()
    }

    companion object {
        /** Tools that show their result live under the settings. */
        val LIVE_PREVIEW = setOf(
            ImageTool.ROTATE, ImageTool.COLOR, ImageTool.FILTERS, ImageTool.BORDERS, ImageTool.ROUNDED,
            ImageTool.WATERMARK, ImageTool.STAMP, ImageTool.COLLAGE,
        )
    }
}
