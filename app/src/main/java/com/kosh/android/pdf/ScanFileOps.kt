package com.kosh.android.pdf

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import java.io.FileNotFoundException

/** Rename and delete for exported scans, which live behind Storage Access Framework URIs. */
class ScanFileOps(private val resolver: ContentResolver) {

    /** Keeps access to a document the user created through the picker, so it can be reopened after a restart. */
    fun keepAccess(uri: Uri) {
        runCatching {
            resolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
    }

    /** Returns the document's new URI, or null if the storage provider can't rename it. */
    fun rename(uri: Uri, newDisplayName: String): Uri? = try {
        DocumentsContract.renameDocument(resolver, uri, newDisplayName)
    } catch (e: Exception) {
        null
    }

    /** True if the file is gone afterwards (deleted, or it was already missing). */
    fun delete(uri: Uri): Boolean = try {
        DocumentsContract.deleteDocument(resolver, uri)
    } catch (e: FileNotFoundException) {
        true
    } catch (e: Exception) {
        false
    }
}
