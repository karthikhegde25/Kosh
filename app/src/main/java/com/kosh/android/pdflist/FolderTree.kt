package com.kosh.android.pdflist

/** A folder that contains PDFs, directly or in a subfolder. [relPath] is "" for the root. */
class FolderNode(val name: String, val relPath: String) {
    val folders: MutableMap<String, FolderNode> = LinkedHashMap()
    val files: MutableList<PdfFile> = ArrayList()

    /** Number of PDFs in this folder and everything below it. */
    val totalCount: Int get() = files.size + folders.values.sumOf { it.totalCount }
}

sealed interface TreeRow {
    val depth: Int
}

data class FolderRowData(val node: FolderNode, override val depth: Int, val expanded: Boolean) : TreeRow
data class FileRowData(val file: PdfFile, override val depth: Int) : TreeRow

/**
 * Builds the real folder hierarchy: a PDF in `Download/Work/2026` appears under Download, then Work, then 2026.
 * Folders without any PDF below them are not included.
 */
fun buildFolderTree(files: List<PdfFile>, rootPath: String, rootName: String): FolderNode {
    val root = FolderNode(rootName, "")
    val prefix = rootPath.trimEnd('/') + "/"
    for (file in files) {
        val relative = if (file.path.startsWith(prefix)) file.path.removePrefix(prefix) else file.path.trimStart('/')
        val dirs = relative.split('/').dropLast(1)
        var node = root
        for (dir in dirs) {
            val parent = node
            node = parent.folders.getOrPut(dir) {
                FolderNode(dir, if (parent.relPath.isEmpty()) dir else parent.relPath + "/" + dir)
            }
        }
        node.files += file
    }
    return root
}

/** Flattens the tree into display rows. Only folders whose relPath is in [expanded] show their contents. */
fun flattenTree(root: FolderNode, expanded: Set<String>): List<TreeRow> {
    val out = ArrayList<TreeRow>()
    fun visit(node: FolderNode, depth: Int) {
        val open = node.relPath in expanded
        out += FolderRowData(node, depth, open)
        if (!open) return
        node.folders.values.sortedBy { it.name.lowercase() }.forEach { visit(it, depth + 1) }
        node.files.sortedBy { it.name.lowercase() }.forEach { out += FileRowData(it, depth + 1) }
    }
    visit(root, 0)
    return out
}
