package com.kosh.android.pdflist

/**
 * The files whose name or folder contains every word of [query] (ignoring case). A blank query matches everything.
 * No Android classes, so it can be tested on a plain JVM.
 */
fun filterFiles(files: List<PdfFile>, query: String): List<PdfFile> {
    val words = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (words.isEmpty()) return files
    return files.filter { f ->
        val haystack = (f.name + " " + f.folder).lowercase()
        words.all { it in haystack }
    }
}
