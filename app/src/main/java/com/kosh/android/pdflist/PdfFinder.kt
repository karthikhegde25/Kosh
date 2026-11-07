package com.kosh.android.pdflist

import android.os.Environment
import com.kosh.android.tools.FileKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.coroutineContext

/**
 * Walks the device's main shared storage for *.pdf, *.docx, *.xlsx and *.pptx files. Requires storage access. Skips hidden folders
 * and the `Android` data folder, which is not readable by other apps anyway.
 */
class PdfFinder {

    suspend fun findAll(): List<PdfFile> = withContext(Dispatchers.IO) {
        val root = Environment.getExternalStorageDirectory()
        val found = ArrayList<PdfFile>()
        val walk = root.walkTopDown()
            .maxDepth(MAX_DEPTH)
            .onEnter { dir ->
                dir == root || (!dir.name.startsWith(".") && !(dir.parentFile == root && dir.name.equals("Android", true)))
            }
        for (file in walk) {
            coroutineContext.ensureActive()
            val kind = if (file.isFile) FileKind.fromFileName(file.name) else null
            if (kind != null) {
                val size = file.length()
                if (size > 0) {
                    found += PdfFile(
                        path = file.absolutePath,
                        name = file.name,
                        folder = file.parentFile?.name.orEmpty(),
                        sizeBytes = size,
                        modifiedMillis = file.lastModified(),
                        kind = kind,
                    )
                }
            }
        }
        found.sortedByDescending { it.modifiedMillis }
    }

    private companion object {
        const val MAX_DEPTH = 12
    }
}
