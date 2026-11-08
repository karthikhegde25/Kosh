package com.kosh.android.pdflist

import com.kosh.android.tools.FileKind

data class PdfFile(
    val path: String,
    val name: String,
    val folder: String,
    val sizeBytes: Long,
    val modifiedMillis: Long,
    /** PDF, or a Word, Excel or PowerPoint file (shown next to the PDFs and converted to PDF when opened). */
    val kind: FileKind = FileKind.PDF,
)
