package com.kosh.android.viewer

import android.content.Context
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream

/**
 * Serves everything the viewer page needs from a private virtual host: bundled assets under /app/ and the one
 * document that was opened under /doc/. Nothing else is reachable, so the page can never touch the network.
 */
class ViewerResourceLoader(private val context: Context) {

    @Volatile var allowedDocument: String? = null

    fun intercept(request: WebResourceRequest): WebResourceResponse {
        val url = request.url
        if (url.host != HOST) return status(403, "Blocked")
        val path = url.encodedPath.orEmpty()
        return try {
            when {
                path.startsWith("/app/") -> asset(path.removePrefix("/app/"))
                path.startsWith("/doc/") -> document(Uri.decode(path.removePrefix("/doc/")))
                else -> status(404, "Not found")
            }
        } catch (e: Exception) {
            status(404, "Not found")
        }
    }

    private fun asset(relative: String): WebResourceResponse {
        val clean = Uri.decode(relative)
        if (clean.split('/').any { it == ".." || it.isEmpty() }) return status(403, "Blocked")
        val stream = context.assets.open("pdfviewer/$clean")
        return response(mimeFor(clean), stream, null)
    }

    private fun document(original: String): WebResourceResponse {
        if (original != allowedDocument) return status(403, "Blocked")
        val uri = Uri.parse(original)
        return when (uri.scheme) {
            "file" -> {
                val f = File(uri.path.orEmpty())
                response("application/pdf", FileInputStream(f), f.length())
            }
            "content" -> {
                val s = context.contentResolver.openInputStream(uri) ?: return status(404, "Not found")
                response("application/pdf", s, null)
            }
            else -> status(404, "Not found")
        }
    }

    private fun response(mime: String, stream: InputStream, length: Long?): WebResourceResponse {
        val headers = mutableMapOf("Access-Control-Allow-Origin" to "*", "Cache-Control" to "no-store")
        if (length != null) headers["Content-Length"] = length.toString()
        return WebResourceResponse(mime, if (mime.startsWith("text/")) "utf-8" else null, 200, "OK", headers, stream)
    }

    private fun status(code: Int, reason: String) =
        WebResourceResponse("text/plain", "utf-8", code, reason, mapOf("Access-Control-Allow-Origin" to "*"), ByteArrayInputStream(ByteArray(0)))

    companion object {
        const val HOST = "kosh.viewer.local"
        const val PAGE_URL = "https://$HOST/app/viewer.html"

        fun documentUrl(uriString: String): String = "https://$HOST/doc/" + Uri.encode(uriString)

        fun mimeFor(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
            "html" -> "text/html"
            "js", "mjs" -> "text/javascript"
            "css" -> "text/css"
            "svg" -> "image/svg+xml"
            "png" -> "image/png"
            "wasm" -> "application/wasm"
            "json" -> "application/json"
            else -> "application/octet-stream"
        }
    }
}
