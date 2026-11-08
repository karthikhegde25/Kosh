package com.kosh.android

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kosh.android.data.model.CropCorners
import com.kosh.android.data.model.FilterMode
import com.kosh.android.data.repository.DraftRepository
import com.kosh.android.data.repository.ImportSummary
import com.kosh.android.data.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File

/** Result of an import, delivered once to whichever screen shows it. */
data class ImportNotice(val summary: ImportSummary, val openEditor: Boolean)

class DraftViewModel(
    private val repo: DraftRepository,
    private val settingsRepo: SettingsRepository,
) : ViewModel() {

    val draft = repo.draft

    private val _importing = MutableStateFlow(false)
    val importing: StateFlow<Boolean> = _importing.asStateFlow()

    private val _notice = MutableStateFlow<ImportNotice?>(null)
    val notice: StateFlow<ImportNotice?> = _notice.asStateFlow()

    fun importImages(uris: List<Uri>, openEditor: Boolean) {
        if (uris.isEmpty() || _importing.value) return
        viewModelScope.launch {
            _importing.value = true
            try {
                val filter = settingsRepo.settings.first().defaultFilter
                _notice.value = ImportNotice(repo.importImages(uris, filter), openEditor)
            } finally {
                _importing.value = false
            }
        }
    }

    fun consumeNotice() {
        _notice.value = null
    }

    // The camera screen asks the Scanner tab to show its Edit sub-tab once photos have been taken.
    private val _editRequested = MutableStateFlow(false)
    val editRequested: StateFlow<Boolean> = _editRequested.asStateFlow()

    fun requestEdit() {
        _editRequested.value = true
    }

    fun consumeEditRequest() {
        _editRequested.value = false
    }

    suspend fun newCaptureFile(): File = repo.newCaptureFile()

    /** Adds a camera capture as a page. Returns its id, or null if the file couldn't be read. */
    suspend fun addCapturedFile(file: File): String? {
        val filter = settingsRepo.settings.first().defaultFilter
        return repo.addCapturedFile(file, filter)?.id
    }

    fun renameDraft(title: String) = repo.renameDraft(title)

    fun rotatePage(id: String, delta: Int) = repo.rotatePage(id, delta)
    fun setFilter(id: String, mode: FilterMode) = repo.setFilter(id, mode)
    fun setFilterAll(mode: FilterMode) = repo.setFilterAll(mode)
    fun setCrop(id: String, corners: CropCorners?) = repo.setCrop(id, corners)
    fun movePage(id: String, delta: Int) = repo.movePage(id, delta)
    fun page(id: String) = repo.page(id)

    fun deletePage(id: String) {
        viewModelScope.launch { repo.deletePage(id) }
    }

    fun duplicatePage(id: String) {
        viewModelScope.launch { repo.duplicatePage(id) }
    }

    /** Throws the draft away (and its temporary files). Exported PDFs are never affected. */
    fun discardDraft() {
        viewModelScope.launch { repo.discardDraft() }
    }
}
