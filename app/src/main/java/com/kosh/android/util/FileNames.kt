package com.kosh.android.util

private val FORBIDDEN_CHARS = charArrayOf('/', '\\', ':', '*', '?', '"', '<', '>', '|')

/**
 * Builds the new file name for a rename, keeping the file's original [extension].
 * Returns null if [input] can't be used as a file name. A trailing ".pdf" the user typed is not doubled.
 */
fun buildRenamedFileName(input: String, extension: String): String? {
    var base = input.trim()
    if (extension.isNotEmpty() && base.endsWith(".$extension", ignoreCase = true)) {
        base = base.dropLast(extension.length + 1).trim()
    }
    if (base.isEmpty() || base == "." || base == "..") return null
    if (base.endsWith(".")) return null
    if (base.any { it in FORBIDDEN_CHARS || it.isISOControl() }) return null
    val full = if (extension.isEmpty()) base else "$base.$extension"
    // Most Android file systems limit a name to 255 bytes.
    return if (full.toByteArray(Charsets.UTF_8).size > 255) null else full
}

/**
 * Suggested file name for exporting a note: the title with illegal characters removed, whitespace collapsed,
 * limited in length, falling back to "Note", plus the given extension (without a dot).
 */
fun suggestedExportName(title: String, extension: String): String {
    val cleaned = title
        .filter { it !in FORBIDDEN_CHARS && !it.isISOControl() }
        .replace(Regex("\\s+"), " ")
        .trim()
        .trim('.', ' ')
    val limited = if (cleaned.length > 100) cleaned.take(100).trim() else cleaned
    val base = limited.ifEmpty { "Note" }
    return "$base.$extension"
}
