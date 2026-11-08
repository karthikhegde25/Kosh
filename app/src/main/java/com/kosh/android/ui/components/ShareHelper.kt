package com.kosh.android.ui.components

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/**
 * Shares a PDF through the Android share sheet. `file://` paths are converted to a FileProvider content URI
 * (raw file paths are never shared); `content://` URIs are passed on with a temporary read grant.
 * Returns false if the file can't be shared.
 */
fun sharePdf(context: Context, uri: Uri, mime: String = "application/pdf"): Boolean {
    val shareUri: Uri = if (uri.scheme == "file") {
        runCatching {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", File(uri.path.orEmpty()))
        }.getOrNull() ?: return false
    } else uri
    val send = Intent(Intent.ACTION_SEND).apply {
        type = mime
        putExtra(Intent.EXTRA_STREAM, shareUri)
        clipData = ClipData.newRawUri(null, shareUri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    return try {
        context.startActivity(Intent.createChooser(send, null))
        true
    } catch (e: ActivityNotFoundException) {
        false
    }
}

/** Shares several PDFs in one go. Returns false if none of them can be shared. */
fun sharePdfs(context: Context, uris: List<Uri>, mime: String = "application/pdf"): Boolean {
    val shareUris = uris.mapNotNull { uri ->
        if (uri.scheme == "file") {
            runCatching { FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", File(uri.path.orEmpty())) }.getOrNull()
        } else uri
    }
    if (shareUris.isEmpty()) return false
    if (shareUris.size == 1) return sharePdf(context, shareUris[0], mime)
    val send = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
        type = mime
        putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(shareUris))
        clipData = ClipData.newRawUri(null, shareUris[0]).also { clip -> shareUris.drop(1).forEach { clip.addItem(ClipData.Item(it)) } }
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    return try {
        context.startActivity(Intent.createChooser(send, null))
        true
    } catch (e: ActivityNotFoundException) {
        false
    }
}
