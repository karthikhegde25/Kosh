package com.kosh.android.scanner

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kosh.android.data.db.ScanRecord
import com.kosh.android.data.db.ScanRecordRepository
import com.kosh.android.data.model.ScannedPage
import com.kosh.android.data.repository.SettingsRepository
import com.kosh.android.pdf.ExportQuality
import com.kosh.android.pdf.ExportResult
import com.kosh.android.pdf.LayoutMode
import com.kosh.android.pdf.PageOrientation
import com.kosh.android.pdf.PaperSize
import com.kosh.android.pdf.PdfExportOptions
import com.kosh.android.pdf.PdfGenerator
import com.kosh.android.pdf.ScanFileOps
import com.kosh.android.util.buildRenamedFileName
import com.kosh.android.util.suggestedExportName
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

sealed interface ExportState {
    data object Idle : ExportState
    data class Running(val done: Int, val total: Int) : ExportState
    data class Success(val result: ExportResult) : ExportState
    data class Failed(val reason: String?) : ExportState
}

class ExportViewModel(
    private val generator: PdfGenerator,
    private val fileOps: ScanFileOps,
    private val records: ScanRecordRepository,
    private val settingsRepo: SettingsRepository,
) : ViewModel() {

    private val _options = MutableStateFlow(PdfExportOptions())
    val options: StateFlow<PdfExportOptions> = _options.asStateFlow()

    private val _state = MutableStateFlow<ExportState>(ExportState.Idle)
    val state: StateFlow<ExportState> = _state.asStateFlow()

    private class Pending(val uri: Uri, val title: String, val pages: List<ScannedPage>)

    private var pending: Pending? = null
    private var job: Job? = null
    private var recordId: String? = null

    init {
        // Start from the defaults chosen in Settings.
        viewModelScope.launch {
            val s = settingsRepo.settings.first()
            _options.update { it.copy(paper = s.defaultPaper, quality = s.defaultQuality) }
        }
    }

    fun setLayout(mode: LayoutMode) = _options.update { it.copy(layout = mode) }
    fun setPaper(paper: PaperSize) = _options.update { it.copy(paper = paper) }
    fun setOrientation(o: PageOrientation) = _options.update { it.copy(orientation = o) }
    fun setQuality(q: ExportQuality) = _options.update { it.copy(quality = q) }

    /** [uri] is the document the user created with the system file picker. */
    fun start(uri: Uri, title: String, pages: List<ScannedPage>) {
        if (_state.value is ExportState.Running || pages.isEmpty()) return // ignore duplicate taps
        fileOps.keepAccess(uri)
        pending = Pending(uri, title, pages.toList())
        run()
    }

    fun retry() {
        if (_state.value !is ExportState.Running && pending != null) run()
    }

    fun cancel() {
        job?.cancel()
        _state.value = ExportState.Idle
    }

    /** Back to a clean state after the user has left the success screen. */
    fun reset() {
        job?.cancel()
        pending = null
        recordId = null
        _state.value = ExportState.Idle
    }

    private fun run() {
        val p = pending ?: return
        val options = _options.value
        _state.value = ExportState.Running(0, p.pages.size)
        job = viewModelScope.launch {
            val result = generator.generate(
                pages = p.pages,
                title = p.title,
                displayName = suggestedExportName(p.title, "pdf"),
                options = options,
                destination = p.uri,
                onProgress = { done, total -> _state.value = ExportState.Running(done, total) },
            )
            result.fold(
                onSuccess = { r ->
                    val id = UUID.randomUUID().toString()
                    recordId = id
                    runCatching {
                        records.add(ScanRecord(id, r.name, r.uri.toString(), r.pageCount, r.sizeBytes ?: 0L, System.currentTimeMillis()))
                    }
                    _state.value = ExportState.Success(r)
                },
                onFailure = { _state.value = ExportState.Failed(it.message) },
            )
        }
    }

    /** Renames the exported file. False if the name is invalid or the storage app can't rename it. */
    fun rename(newBaseName: String): Boolean {
        val current = (_state.value as? ExportState.Success)?.result ?: return false
        val newName = buildRenamedFileName(newBaseName, "pdf") ?: return false
        val newUri = fileOps.rename(current.uri, newName) ?: return false
        val updated = current.copy(uri = newUri, name = newName)
        _state.value = ExportState.Success(updated)
        recordId?.let { id -> viewModelScope.launch { runCatching { records.rename(id, newName, newUri.toString()) } } }
        return true
    }
}
