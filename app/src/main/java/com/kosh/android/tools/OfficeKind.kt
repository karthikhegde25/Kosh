package com.kosh.android.tools

/** The kinds of file the Home tab lists. No Android classes, so it can be tested on a plain JVM. */
enum class FileKind(val extension: String, val mime: String) {
    PDF("pdf", "application/pdf"),
    DOCX("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
    XLSX("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
    PPTX("pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation");

    val isOffice get() = this != PDF

    companion object {
        fun fromFileName(name: String): FileKind? {
            // Office writes "~$name.docx" next to a file that is open; those are lock stubs, not documents.
            if (name.startsWith("~$") || name.startsWith(".")) return null
            val ext = name.substringAfterLast('.', "").lowercase()
            return entries.firstOrNull { it.extension == ext }
        }

        val officeMimeTypes: Array<String> get() = entries.filter { it.isOffice }.map { it.mime }.toTypedArray()
    }
}

/** "Report.docx" -> "Report.pdf"; if that is taken, "Report (1).pdf", "Report (2).pdf"... [exists] says whether a name is taken. */
fun uniquePdfName(sourceName: String, exists: (String) -> Boolean): String {
    val base = sourceName.substringBeforeLast('.').ifBlank { "Document" }
    var candidate = "$base.pdf"
    var n = 1
    while (exists(candidate)) candidate = "$base (${n++}).pdf"
    return candidate
}
