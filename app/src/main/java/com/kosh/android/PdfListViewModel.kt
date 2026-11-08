package com.kosh.android

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kosh.android.pdflist.PdfFile
import com.kosh.android.pdflist.PdfFileOps
import com.kosh.android.pdflist.PdfFinder
import com.kosh.android.pdflist.RenameResult
import com.kosh.android.tools.OfficePdfConverter
import com.kosh.android.tools.uniquePdfName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface PdfListState {
    data object Idle : PdfListState
    data object Scanning : PdfListState
    data class Loaded(val files: List<PdfFile>) : PdfListState
    data object Failed : PdfListState
}

class PdfListViewModel(
    private val finder: PdfFinder,
    private val ops: PdfFileOps,
    private val office: OfficePdfConverter,
) : ViewModel() {

    init {
        viewModelScope.launch(Dispatchers.IO) { runCatching { office.prune() } }
    }

    private val _state = MutableStateFlow<PdfListState>(PdfListState.Idle)
    val state: StateFlow<PdfListState> = _state.asStateFlow()
    private var job: Job? = null

    /** Paths of the PDFs picked by pressing and holding, in the order they were picked. Empty when not selecting. */
    private val _selected = MutableStateFlow<List<String>>(emptyList())
    val selected: StateFlow<List<String>> = _selected.asStateFlow()

    fun toggleSelected(path: String) { _selected.update { if (path in it) it - path else it + path } }
    fun clearSelection() { _selected.value = emptyList() }

    /** The picked PDFs that are still in the list, in the order they were picked. */
    fun selectedFiles(): List<PdfFile> {
        val files = (_state.value as? PdfListState.Loaded)?.files.orEmpty().associateBy { it.path }
        return _selected.value.mapNotNull { files[it] }
    }

    /** Deletes every picked PDF and returns how many could not be deleted. */
    suspend fun deleteSelected(): Int {
        var failed = 0
        for (file in selectedFiles()) if (!delete(file)) failed++
        clearSelection()
        return failed
    }

    /**
     * Safe to call repeatedly; a new scan replaces any scan still running. A [silent] refresh keeps showing the current
     * list while it rescans (used when coming back from the reader), so the screen doesn't flash or lose its place.
     */
    fun refresh(silent: Boolean = false) {
        job?.cancel()
        if (!silent || _state.value !is PdfListState.Loaded) _state.value = PdfListState.Scanning
        job = viewModelScope.launch {
            _state.value = try {
                PdfListState.Loaded(finder.findAll()).also { loaded ->
                    val paths = loaded.files.map { it.path }.toSet()
                    _selected.update { picked -> picked.filter { it in paths } }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                if (silent && _state.value is PdfListState.Loaded) _state.value else PdfListState.Failed
            }
        }
    }

    /** Renames on disk, then updates the list in place (no full rescan). */
    suspend fun rename(file: PdfFile, baseName: String): RenameResult {
        val result = ops.rename(file, baseName)
        if (result is RenameResult.Success) {
            updateLoaded { files -> files.map { if (it.path == file.path) result.file else it } }
        }
        return result
    }

    /** Deletes on disk, then removes the item from the list. Returns false if the file couldn't be deleted. */
    suspend fun delete(file: PdfFile): Boolean {
        val gone = ops.delete(file)
        if (gone) updateLoaded { files -> files.filter { it.path != file.path } }
        return gone
    }

    /** A PDF the reader can open for [file]: the file itself for a PDF, otherwise a converted copy kept in the cache. */
    suspend fun viewablePdf(file: PdfFile): Result<File> =
        if (!file.kind.isOffice) Result.success(File(file.path)) else office.viewable(File(file.path), file.kind)

    /** Converts a Word, Excel or PowerPoint file and saves the PDF next to it, never replacing a file that exists. */
    suspend fun saveAsPdf(file: PdfFile): Result<PdfFile> {
        val source = File(file.path)
        val folder = source.absoluteFile.parentFile ?: return Result.failure(java.io.IOException("No folder"))
        val target = File(folder, uniquePdfName(file.name) { File(folder, it).exists() })
        return office.convert(source, file.kind, target).map {
            ops.scan(target.absolutePath)
            val saved = PdfFile(
                path = target.absolutePath,
                name = target.name,
                folder = folder.name,
                sizeBytes = target.length(),
                modifiedMillis = target.lastModified(),
            )
            updateLoaded { files -> (files + saved).sortedByDescending { it.modifiedMillis } }
            saved
        }
    }

    private fun updateLoaded(transform: (List<PdfFile>) -> List<PdfFile>) {
        _state.update { s -> if (s is PdfListState.Loaded) PdfListState.Loaded(transform(s.files)) else s }
        dropMissingFromSelection()
    }

    /** A PDF that was deleted or renamed can't stay picked. */
    private fun dropMissingFromSelection() {
        val paths = (_state.value as? PdfListState.Loaded)?.files?.map { it.path }?.toSet() ?: return
        _selected.update { picked -> picked.filter { it in paths } }
    }
}
