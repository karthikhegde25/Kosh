package com.kosh.android.scanner

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kosh.android.data.db.ScanRecord
import com.kosh.android.data.db.ScanRecordRepository
import com.kosh.android.pdf.ScanFileOps
import com.kosh.android.util.buildRenamedFileName
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class RecentScansViewModel(
    private val repo: ScanRecordRepository,
    private val fileOps: ScanFileOps,
) : ViewModel() {

    val records: StateFlow<List<ScanRecord>> =
        repo.records.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Deletes the PDF and forgets the entry. If the file was already gone the entry is simply removed. */
    suspend fun delete(record: ScanRecord): Boolean {
        val gone = fileOps.delete(Uri.parse(record.uri))
        if (gone) repo.remove(record.id)
        return gone
    }

    /** Removes the entry without touching the file (used for entries whose file can't be found). */
    fun forget(record: ScanRecord) {
        viewModelScope.launch { repo.remove(record.id) }
    }

    suspend fun rename(record: ScanRecord, newBaseName: String): Boolean {
        val newName = buildRenamedFileName(newBaseName, "pdf") ?: return false
        val newUri = fileOps.rename(Uri.parse(record.uri), newName) ?: return false
        repo.rename(record.id, newName, newUri.toString())
        return true
    }
}
