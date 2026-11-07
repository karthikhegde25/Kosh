package com.kosh.android.pdflist

import android.content.Context
import android.media.MediaScannerConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

sealed interface RenameResult {
    data class Success(val file: PdfFile) : RenameResult
    data object InvalidName : RenameResult
    data object NameExists : RenameResult
    data object Failed : RenameResult // includes "file no longer exists"
}

/** Renames/deletes PDFs on disk and tells the system media index so other apps see the change. */
class PdfFileOps(private val context: Context) {

    suspend fun rename(file: PdfFile, baseName: String): RenameResult = withContext(Dispatchers.IO) {
        val src = File(file.path)
        when (val outcome = FileOperations.rename(src, baseName)) {
            is RenameOutcome.Unchanged -> RenameResult.Success(file)
            is RenameOutcome.Renamed -> {
                val dst = outcome.file
                notifyIndex(src.path, dst.path)
                RenameResult.Success(
                    file.copy(path = dst.absolutePath, name = dst.name, modifiedMillis = dst.lastModified()),
                )
            }
            RenameOutcome.InvalidName -> RenameResult.InvalidName
            RenameOutcome.NameExists -> RenameResult.NameExists
            RenameOutcome.NotFound, RenameOutcome.Failed -> RenameResult.Failed
        }
    }

    /** True if the file is gone afterwards (deleted now, or already missing). */
    suspend fun delete(file: PdfFile): Boolean = withContext(Dispatchers.IO) {
        when (FileOperations.delete(File(file.path))) {
            DeleteOutcome.Deleted -> { notifyIndex(file.path); true }
            DeleteOutcome.AlreadyGone -> true
            DeleteOutcome.Failed -> false
        }
    }

    /** Lets the system's file index (and other apps) know about a file this app just created. */
    fun scan(path: String) = notifyIndex(path)

    private fun notifyIndex(vararg paths: String) {
        runCatching { MediaScannerConnection.scanFile(context, paths, null, null) }
    }
}
