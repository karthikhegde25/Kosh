package com.kosh.android.tools

import android.net.Uri

/**
 * Files handed from one screen to a tool: pressing and holding PDFs on Home and choosing Merge puts them here, and the
 * Merge tool takes them (once) when it opens. Held in memory only.
 */
object PendingToolFiles {
    @Volatile private var files: List<Uri> = emptyList()

    fun put(uris: List<Uri>) { files = uris }

    fun take(): List<Uri> = files.also { files = emptyList() }
}
