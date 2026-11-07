package com.kosh.android.tools

import android.graphics.Bitmap
import android.net.Uri
import android.util.LruCache
import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kosh.android.R
import com.kosh.android.reader.OpenOutcome
import com.kosh.android.reader.PdfDocumentRepository
import com.kosh.android.reader.PdfRenderEngine
import com.kosh.android.reader.ReaderError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** [InputInfo.pageCount] for a file whose pages can't be counted before it is converted (a Word file). */
const val PAGES_UNKNOWN = -1

data class InputInfo(val name: String, val pageCount: Int, val sizeBytes: Long?, val uri: Uri)

sealed interface ToolRun {
    data object Idle : ToolRun
    data class Running(val done: Int, val total: Int) : ToolRun
    data class Done(val outputs: List<ToolOutput>, val inFolder: Boolean, val sizeBefore: Long? = null) : ToolRun
    data class NoGain(val before: Long, val after: Long) : ToolRun
    data class Failed(val detail: String?, val wrongPassword: Boolean = false) : ToolRun
}

data class ToolUiState(
    val tool: PdfTool,
    val opening: Boolean = false,
    @StringRes val errorRes: Int? = null,
    val inputs: List<InputInfo> = emptyList(),
    /** Chosen pages of the (single) input, 0-based. */
    val selected: Set<Int> = emptySet(),
    val rangeText: String = "",
    val rangeInvalid: Boolean = false,
    val rotation: Int = 90,
    /** Reorder: the original (0-based) page numbers in their new order. */
    val order: List<Int> = emptyList(),
    val compressLevel: CompressLevel = CompressLevel.BALANCED,
    val splitMode: SplitMode = SplitMode.RANGES,
    val splitRanges: String = "",
    val splitN: String = "2",
    /** With page ranges: put every range into one file instead of one file per range. */
    val splitAsOne: Boolean = false,
    val imageFormat: ImageFormat = ImageFormat.JPG,
    val imageDpi: Int = 150,
    val numberFormat: PageNumberFormat = PageNumberFormat.NUMBER,
    val numberPosition: NumberPosition = NumberPosition.BOTTOM_CENTER,
    val numberSize: NumberSize = NumberSize.MEDIUM,
    val numberStart: String = "1",
    val numberSkipFirst: Boolean = false,
    /** Encrypt: the password to set. Decrypt: the password the PDF currently has. */
    val password: String = "",
    val passwordConfirm: String = "",
    val showPassword: Boolean = false,
    val strength: EncryptStrength = EncryptStrength.STRONG,
    val allowPrint: Boolean = true,
    val allowCopy: Boolean = true,
    val allowEdit: Boolean = true,
    /** Decrypt: the PDF asks for a password before it can be read. */
    val locked: Boolean = false,
    /** Decrypt: the PDF is protected at all (it may open without a password but still have restrictions). */
    val encrypted: Boolean = false,
    /** Word to PDF: how the converted pages are placed on A4 sheets. */
    val sheetPerPage: Int = 1,
    val sheetLandscape: Boolean = false,
    val sheetBorder: Boolean = false,
    val sheetBorderWidth: Float = 1f,
    val run: ToolRun = ToolRun.Idle,
) {
    val pageCount: Int get() = inputs.firstOrNull()?.pageCount ?: 0
    val totalPages: Int get() = inputs.sumOf { it.pageCount }
    val usesPageGrid: Boolean get() = tool == PdfTool.EXTRACT || tool == PdfTool.ROTATE || tool == PdfTool.DELETE || tool == PdfTool.PDF_TO_IMAGES
    val multiInput: Boolean get() = tool == PdfTool.MERGE
    /** True when the result is several files, so the user picks a folder instead of one file name. */
    val needsFolder: Boolean get() = (tool == PdfTool.SPLIT && !(splitMode == SplitMode.RANGES && splitAsOne)) || tool == PdfTool.PDF_TO_IMAGES

    /** Files a split would produce with the current settings, or null if the settings aren't valid yet. */
    val splitGroups: List<List<Int>>?
        get() = when (splitMode) {
            SplitMode.RANGES -> parseRangeGroups(splitRanges, pageCount)
            SplitMode.EVERY_PAGE -> splitEveryN(pageCount, 1).takeIf { it.isNotEmpty() }
            SplitMode.EVERY_N -> splitN.toIntOrNull()?.takeIf { it in 1..pageCount }?.let { splitEveryN(pageCount, it) }
        }

    val canRun: Boolean
        get() = run !is ToolRun.Running && inputs.isNotEmpty() && when (tool) {
            PdfTool.MERGE -> inputs.size >= 2
            PdfTool.SPLIT -> splitGroups != null
            PdfTool.EXTRACT -> selected.isNotEmpty()
            PdfTool.DELETE -> selected.isNotEmpty() && selected.size < pageCount
            PdfTool.PDF_TO_IMAGES, PdfTool.ROTATE, PdfTool.COMPRESS -> true
            PdfTool.REORDER -> order != (0 until pageCount).toList()
            PdfTool.PAGE_NUMBERS -> numberStart.toIntOrNull()?.let { it in 0..99999 } == true
            PdfTool.ENCRYPT -> password.isNotEmpty() && password == passwordConfirm
            PdfTool.DECRYPT -> encrypted && (!locked || password.isNotEmpty())
            PdfTool.DOCX_TO_PDF, PdfTool.PAGES_PER_SHEET -> true
            PdfTool.IMAGE_TO_PDF -> false
        }

    val sheetStyle: NUpStyle get() = NUpStyle(sheetPerPage, sheetLandscape, sheetBorder, sheetBorderWidth)
}

class PdfToolViewModel(
    private val repo: PdfDocumentRepository,
    private val runner: PdfToolsRunner,
    private val edit: PdfEditEngine,
    private val box: PdfBoxEngine,
    private val docx: DocxPdfEngine,
    handle: SavedStateHandle,
) : ViewModel() {

    val tool: PdfTool = PdfTool.fromId(handle.get<String>("toolId")) ?: PdfTool.MERGE

    private val _state = MutableStateFlow(ToolUiState(tool))
    val state: StateFlow<ToolUiState> = _state.asStateFlow()

    private val engines = ArrayList<PdfRenderEngine>()
    private val thumbs = LruCache<Int, Bitmap>(80)
    private var job: Job? = null

    init {
        // PDFs picked on the Home tab before choosing Merge arrive ready to be joined.
        if (tool == PdfTool.MERGE) PendingToolFiles.take().takeIf { it.isNotEmpty() }?.let { addFiles(it) }
    }

    // ---- choosing files ---------------------------------------------------------------------------------------------

    fun addFiles(uris: List<Uri>) {
        if (uris.isEmpty()) return
        _state.update { it.copy(opening = true, errorRes = null) }
        if (tool == PdfTool.DECRYPT) {
            openForDecrypt(uris.first())
            return
        }
        if (tool == PdfTool.DOCX_TO_PDF) {
            openDocx(uris.first())
            return
        }
        viewModelScope.launch {
            val single = !state.value.multiInput
            val opened = ArrayList<Pair<PdfRenderEngine, InputInfo>>()
            var error: ReaderError? = null
            for (uri in (if (single) uris.take(1) else uris)) {
                when (val out = repo.open(uri)) {
                    is OpenOutcome.Opened -> opened += out.engine to InputInfo(out.info.name, out.info.pageCount, out.info.sizeBytes, uri)
                    is OpenOutcome.Failed -> error = out.error
                }
            }
            if (opened.isNotEmpty() && single) closeEngines()
            thumbs.evictAll()
            engines += opened.map { it.first }
            _state.update { s ->
                val inputs = if (single) opened.map { it.second } else s.inputs + opened.map { it.second }
                val fresh = if (single && opened.isNotEmpty()) {
                    s.copy(selected = emptySet(), rangeText = "", rangeInvalid = false, splitRanges = "", order = (0 until (opened.first().second.pageCount)).toList())
                } else s
                fresh.copy(opening = false, inputs = inputs, errorRes = error?.let { e -> errorText(e) }, run = ToolRun.Idle)
            }
        }
    }

    /** The Decrypt tool has to accept PDFs that Android can't open, so it checks the file with PdfBox instead. */
    private fun openForDecrypt(uri: Uri) {
        viewModelScope.launch {
            val name = withContext(Dispatchers.IO) { runner.describe(uri, 0, "PDF") }
            when (val found = box.inspect(uri, "")) {
                is Inspection.Readable -> _state.update {
                    it.copy(
                        opening = false, inputs = listOf(InputInfo(name.name, found.pages, name.sizeBytes, uri)),
                        locked = false, encrypted = found.encrypted, password = "", run = ToolRun.Idle,
                    )
                }
                Inspection.NeedsPassword -> _state.update {
                    it.copy(
                        opening = false, inputs = listOf(InputInfo(name.name, 0, name.sizeBytes, uri)),
                        locked = true, encrypted = true, password = "", run = ToolRun.Idle,
                    )
                }
                Inspection.Unreadable -> _state.update { it.copy(opening = false, errorRes = R.string.error_corrupt) }
            }
        }
    }

    /** Word files aren't opened as PDFs: the file is only checked, and its pages are counted once it has been converted. */
    private fun openDocx(uri: Uri) {
        viewModelScope.launch {
            if (!docx.looksLikeDocx(uri)) {
                _state.update { it.copy(opening = false, errorRes = R.string.error_docx) }
                return@launch
            }
            val info = withContext(Dispatchers.IO) { runner.describe(uri, 0, "Document.docx") }
            _state.update {
                it.copy(opening = false, errorRes = null, inputs = listOf(InputInfo(info.name, PAGES_UNKNOWN, info.sizeBytes, uri)), run = ToolRun.Idle)
            }
        }
    }

    fun removeInput(index: Int) {
        if (index !in engines.indices) return
        engines.removeAt(index).close()
        _state.update { it.copy(inputs = it.inputs.filterIndexed { i, _ -> i != index }) }
    }

    fun moveInput(index: Int, delta: Int) {
        val to = index + delta
        if (index !in engines.indices || to !in engines.indices) return
        engines.add(to, engines.removeAt(index))
        _state.update { s ->
            val list = s.inputs.toMutableList()
            list.add(to, list.removeAt(index))
            s.copy(inputs = list)
        }
    }

    fun dismissError() { _state.update { it.copy(errorRes = null) } }

    @StringRes
    private fun errorText(e: ReaderError): Int = when (e) {
        ReaderError.NOT_FOUND -> R.string.error_not_found
        ReaderError.ACCESS_DENIED -> R.string.error_access_denied
        ReaderError.PASSWORD_PROTECTED -> R.string.error_password
        ReaderError.CORRUPT -> R.string.error_corrupt
        ReaderError.EMPTY -> R.string.error_empty
        ReaderError.UNSUPPORTED -> R.string.error_unsupported
    }

    // ---- page selection ------------------------------------------------------------------------------------------------

    fun togglePage(page: Int) {
        _state.update { s ->
            val next = if (page in s.selected) s.selected - page else s.selected + page
            s.copy(selected = next, rangeText = formatPageRanges(next), rangeInvalid = false)
        }
    }

    fun selectAllPages() {
        _state.update { s ->
            val all = (0 until s.pageCount).toSet()
            s.copy(selected = all, rangeText = formatPageRanges(all), rangeInvalid = false)
        }
    }

    fun clearSelection() { _state.update { it.copy(selected = emptySet(), rangeText = "", rangeInvalid = false) } }

    fun setRangeText(text: String) {
        _state.update { s ->
            if (text.isBlank()) return@update s.copy(rangeText = text, selected = emptySet(), rangeInvalid = false)
            val pages = parsePageRanges(text, s.pageCount)
            if (pages == null) s.copy(rangeText = text, rangeInvalid = true) else s.copy(rangeText = text, selected = pages.toSet(), rangeInvalid = false)
        }
    }

    // ---- options -----------------------------------------------------------------------------------------------------------

    fun setCompressLevel(level: CompressLevel) { _state.update { it.copy(compressLevel = level) } }

    /** Moves the page at position [from] to position [to] in the new order. */
    fun moveInOrder(from: Int, to: Int) {
        _state.update { s ->
            if (from !in s.order.indices || to !in s.order.indices || from == to) return@update s
            val list = s.order.toMutableList()
            list.add(to, list.removeAt(from))
            s.copy(order = list)
        }
    }

    fun reverseOrder() { _state.update { it.copy(order = it.order.reversed()) } }
    fun resetOrder() { _state.update { it.copy(order = (0 until it.pageCount).toList()) } }

    fun setSheetPerPage(count: Int) { _state.update { it.copy(sheetPerPage = count) } }
    fun setSheetLandscape(landscape: Boolean) { _state.update { it.copy(sheetLandscape = landscape) } }
    fun setSheetBorder(on: Boolean) { _state.update { it.copy(sheetBorder = on) } }
    fun setSheetBorderWidth(points: Float) { _state.update { it.copy(sheetBorderWidth = points.coerceIn(BORDER_MIN, BORDER_MAX)) } }
    fun setRotation(degrees: Int) { _state.update { it.copy(rotation = degrees) } }
    fun setSplitMode(mode: SplitMode) { _state.update { it.copy(splitMode = mode) } }
    fun setSplitAsOne(on: Boolean) { _state.update { it.copy(splitAsOne = on) } }
    fun setSplitRanges(text: String) { _state.update { it.copy(splitRanges = text) } }
    fun setSplitN(text: String) { _state.update { it.copy(splitN = text.filter(Char::isDigit).take(4)) } }
    fun setPassword(text: String) { _state.update { it.copy(password = text.take(127)) } }
    fun setPasswordConfirm(text: String) { _state.update { it.copy(passwordConfirm = text.take(127)) } }
    fun setShowPassword(show: Boolean) { _state.update { it.copy(showPassword = show) } }
    fun setStrength(strength: EncryptStrength) { _state.update { it.copy(strength = strength) } }
    fun setAllowPrint(on: Boolean) { _state.update { it.copy(allowPrint = on) } }
    fun setAllowCopy(on: Boolean) { _state.update { it.copy(allowCopy = on) } }
    fun setAllowEdit(on: Boolean) { _state.update { it.copy(allowEdit = on) } }
    fun setImageFormat(f: ImageFormat) { _state.update { it.copy(imageFormat = f) } }
    fun setImageDpi(dpi: Int) { _state.update { it.copy(imageDpi = dpi) } }
    fun setNumberFormat(f: PageNumberFormat) { _state.update { it.copy(numberFormat = f) } }
    fun setNumberPosition(p: NumberPosition) { _state.update { it.copy(numberPosition = p) } }
    fun setNumberSize(s: NumberSize) { _state.update { it.copy(numberSize = s) } }
    fun setNumberStart(text: String) { _state.update { it.copy(numberStart = text.filter(Char::isDigit).take(5)) } }
    fun setNumberSkipFirst(skip: Boolean) { _state.update { it.copy(numberSkipFirst = skip) } }

    // ---- thumbnails ------------------------------------------------------------------------------------------------------------

    suspend fun thumbnail(page: Int): Bitmap? {
        thumbs.get(page)?.let { return it }
        val engine = engines.firstOrNull() ?: return null
        val bmp = withContext(Dispatchers.Default) { engine.render(page, 260) } ?: return null
        thumbs.put(page, bmp)
        return bmp
    }

    // ---- running -------------------------------------------------------------------------------------------------------------------

    /** Suggested name when the result is one file. */
    fun suggestedFileName(): String {
        val base = state.value.inputs.firstOrNull()?.name ?: "Document"
        return when (tool) {
            PdfTool.MERGE -> toolFileName(base, "merged")
            PdfTool.EXTRACT -> toolFileName(base, "extracted")
            PdfTool.ROTATE -> toolFileName(base, "rotated")
            PdfTool.DELETE -> toolFileName(base, "edited")
            PdfTool.PAGE_NUMBERS -> toolFileName(base, "numbered")
            PdfTool.REORDER -> toolFileName(base, "reordered")
            PdfTool.COMPRESS -> toolFileName(base, "compressed")
            PdfTool.SPLIT -> toolFileName(base, "ranges")
            PdfTool.ENCRYPT -> toolFileName(base, "protected")
            PdfTool.DECRYPT -> toolFileName(base, "unlocked")
            PdfTool.DOCX_TO_PDF -> docxPdfFileName(base)
            PdfTool.PAGES_PER_SHEET -> toolFileName(base, "${state.value.sheetPerPage} per sheet")
            else -> toolFileName(base, "new")
        }
    }

    fun run(destination: Uri) {
        val s = state.value
        if (!s.canRun || (engines.isEmpty() && tool != PdfTool.DECRYPT && tool != PdfTool.DOCX_TO_PDF)) return
        _state.update { it.copy(run = ToolRun.Running(0, 0)) }
        val progress: (Int, Int) -> Unit = { done, total -> _state.update { it.copy(run = ToolRun.Running(done, total)) } }
        job = viewModelScope.launch {
            if (tool == PdfTool.DOCX_TO_PDF) {
                docx.convert(s.inputs[0].uri, s.sheetStyle, suggestedFileName(), destination, progress).fold(
                    onSuccess = { out -> _state.update { it.copy(run = ToolRun.Done(out, false)) } },
                    onFailure = { e -> _state.update { it.copy(run = ToolRun.Failed(e.message)) } },
                )
                return@launch
            }
            if (tool == PdfTool.COMPRESS) {
                val edited = edit.compress(s.inputs[0].uri, s.compressLevel, destination, progress)
                edited.fold(
                    onSuccess = { o ->
                        when (o) {
                            is EditOutcome.Saved -> {
                                val out = runner.describe(destination, o.pages, suggestedFileName())
                                _state.update { it.copy(run = ToolRun.Done(listOf(out), false, sizeBefore = o.before)) }
                            }
                            is EditOutcome.NoGain -> _state.update { it.copy(run = ToolRun.NoGain(o.before, o.after)) }
                        }
                    },
                    onFailure = { e -> _state.update { it.copy(run = ToolRun.Failed(e.message)) } },
                )
                return@launch
            }
            val result = if (tool == PdfTool.PDF_TO_IMAGES) {
                val pages = if (s.selected.isEmpty()) (0 until s.pageCount).toList() else s.selected.sorted()
                runner.writeImages(engines[0], pages, s.inputs[0].name, s.imageFormat, s.imageDpi, destination, progress)
            } else {
                val dest = if (s.needsFolder) ToolDestination.Folder(destination) else ToolDestination.File(destination)
                box.run(jobsFor(s), dest, progress)
            }
            result.fold(
                onSuccess = { out -> _state.update { it.copy(run = ToolRun.Done(out, s.needsFolder)) } },
                onFailure = { e -> _state.update { it.copy(run = ToolRun.Failed(e.message, wrongPassword = e is WrongPasswordException)) } },
            )
        }
    }

    /** What each PDF tool asks PdfBox to do. Pages are 0-based; the output name is only used when saving into a folder. */
    private fun jobsFor(s: ToolUiState): List<PdfJob> {
        val source = s.inputs[0].uri
        val base = s.inputs[0].name
        val all = (0 until s.pageCount).toList()
        val name = suggestedFileName()
        return when (tool) {
            PdfTool.MERGE -> listOf(PdfJob(name, PdfOp.Merge(s.inputs.map { it.uri }), s.totalPages))
            PdfTool.SPLIT -> {
                val groups = s.splitGroups.orEmpty()
                if (s.splitMode == SplitMode.RANGES && s.splitAsOne) {
                    // Every range in one file; its pages keep the order they have in the PDF.
                    val keep = groups.flatten().distinct().sorted()
                    listOf(PdfJob(name, PdfOp.Pages(source, keep), keep.size))
                } else {
                    groups.mapIndexed { i, g -> PdfJob(partFileName(base, i + 1, groups.size), PdfOp.Pages(source, g), g.size) }
                }
            }
            PdfTool.EXTRACT -> s.selected.sorted().let { keep -> listOf(PdfJob(name, PdfOp.Pages(source, keep), keep.size)) }
            PdfTool.DELETE -> all.filter { it !in s.selected }.let { keep -> listOf(PdfJob(name, PdfOp.Pages(source, keep), keep.size)) }
            PdfTool.ROTATE -> {
                val turn = all.filter { s.selected.isEmpty() || it in s.selected }.associateWith { s.rotation }
                listOf(PdfJob(name, PdfOp.Pages(source, null, turn), s.pageCount))
            }
            PdfTool.REORDER -> listOf(PdfJob(name, PdfOp.Reorder(source, s.order), s.pageCount))
            PdfTool.PAGE_NUMBERS -> listOf(
                PdfJob(
                    name,
                    PdfOp.Numbers(source, NumberingStyle(s.numberFormat, s.numberPosition, s.numberStart.toIntOrNull() ?: 1, s.numberSize, s.numberSkipFirst)),
                    s.pageCount,
                ),
            )
            PdfTool.ENCRYPT -> listOf(PdfJob(name, PdfOp.Encrypt(source, s.password, s.strength, s.allowPrint, s.allowCopy, s.allowEdit), s.pageCount))
            PdfTool.DECRYPT -> listOf(PdfJob(name, PdfOp.Decrypt(source, s.password), s.pageCount))
            PdfTool.PAGES_PER_SHEET -> listOf(PdfJob(name, PdfOp.NUp(listOf(source), s.sheetStyle), sheetCount(s.pageCount, s.sheetPerPage)))
            PdfTool.IMAGE_TO_PDF, PdfTool.PDF_TO_IMAGES, PdfTool.COMPRESS, PdfTool.DOCX_TO_PDF -> emptyList()
        }
    }

    fun cancel() {
        job?.cancel()
        _state.update { it.copy(run = ToolRun.Idle) }
    }

    /** Back to choosing files, e.g. after a result has been shown. */
    fun startOver() {
        job?.cancel()
        closeEngines()
        thumbs.evictAll()
        _state.value = ToolUiState(tool)
    }

    fun backToOptions() { _state.update { it.copy(run = ToolRun.Idle) } }

    private fun closeEngines() {
        engines.forEach { it.close() }
        engines.clear()
    }

    override fun onCleared() {
        job?.cancel()
        closeEngines()
        super.onCleared()
    }
}
