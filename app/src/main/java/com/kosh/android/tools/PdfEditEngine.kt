package com.kosh.android.tools

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.kosh.android.viewer.ViewerResourceLoader
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.io.OutputStream

enum class CompressLevel(val js: String) { LIGHT("light"), BALANCED("balanced"), STRONG("strong") }

sealed interface EditOutcome {
    data class Saved(val before: Long, val after: Long, val pages: Int) : EditOutcome

    /** The PDF couldn't be made noticeably smaller, so nothing was kept. */
    data class NoGain(val before: Long, val after: Long) : EditOutcome
}

/**
 * Compression of the pictures inside a PDF, done by pdf-lib running on a private, hidden web page. The page can only see
 * its own bundled files and the one PDF being edited; the result is streamed straight into the file the user created.
 */
class PdfEditEngine(context: Context) {

    private val app = context.applicationContext
    private val resolver = app.contentResolver

    suspend fun compress(source: Uri, level: CompressLevel, destination: Uri, onProgress: (Int, Int) -> Unit): Result<EditOutcome> =
        execute(source, destination, "compress", JSONObject().put("level", level.js), onProgress)

    private suspend fun execute(
        source: Uri,
        destination: Uri,
        kind: String,
        extra: JSONObject,
        onProgress: (Int, Int) -> Unit,
    ): Result<EditOutcome> {
        val outcome = CompletableDeferred<Result<EditOutcome>>()
        var webView: WebView? = null
        var stream: OutputStream? = null
        var keep = false
        try {
            val opened = withContext(Dispatchers.IO) { resolver.openOutputStream(destination, "w") }
                ?: return Result.failure(IOException("Can't open the destination"))
            stream = opened
            val bridge = EditBridge(opened, outcome, onProgress)
            val loader = ViewerResourceLoader(app).also { it.allowedDocument = source.toString() }
            val args = extra.put("url", ViewerResourceLoader.documentUrl(source.toString()))
            val script = "koshRun(${JSONObject.quote(kind)}, $args)"
            withContext(Dispatchers.Main) {
                val view = WebView(app)
                webView = view
                with(view.settings) {
                    javaScriptEnabled = true
                    allowFileAccess = false
                    allowContentAccess = false
                    blockNetworkLoads = true
                    domStorageEnabled = false
                }
                view.addJavascriptInterface(bridge, "KEDIT")
                view.webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse = loader.intercept(request)
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = true
                    override fun onPageFinished(view: WebView, url: String?) { view.evaluateJavascript(script, null) }
                    override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                        outcome.complete(Result.failure(IOException("The editor stopped unexpectedly")))
                        return true
                    }
                }
                view.loadUrl(EDIT_PAGE_URL)
            }
            val result = withTimeoutOrNull(TIMEOUT_MS) { outcome.await() }
                ?: Result.failure(IOException("This took too long"))
            keep = result.getOrNull() is EditOutcome.Saved && !bridge.writeFailed
            return if (result.isSuccess && bridge.writeFailed) Result.failure(IOException("Couldn't write the file")) else result
        } finally {
            withContext(NonCancellable) {
                withContext(Dispatchers.Main) { webView?.let { runCatching { it.stopLoading(); it.destroy() } } }
                withContext(Dispatchers.IO) {
                    runCatching { stream?.close() }
                    // Only ever a document this run just created; remove it unless a good result was written.
                    if (!keep) runCatching { DocumentsContract.deleteDocument(resolver, destination) }
                }
            }
        }
    }

    private companion object {
        const val EDIT_PAGE_URL = "https://${ViewerResourceLoader.HOST}/app/pdf_edit.html"
        const val TIMEOUT_MS = 15 * 60 * 1000L
    }
}

/** The `KEDIT` object the edit page talks to. JS calls arrive on a background thread. */
internal class EditBridge(
    private val out: OutputStream,
    private val outcome: CompletableDeferred<Result<EditOutcome>>,
    private val onProgress: (Int, Int) -> Unit,
) {
    private val lock = Any()

    @Volatile var writeFailed = false
        private set

    @JavascriptInterface fun begin(total: Int) { /* size is known up front; nothing to prepare */ }

    @JavascriptInterface fun chunk(base64: String?) {
        synchronized(lock) {
            if (writeFailed) return
            try { out.write(Base64.decode(base64.orEmpty(), Base64.DEFAULT)) } catch (e: Exception) { writeFailed = true }
        }
    }

    @JavascriptInterface fun progress(done: Int, total: Int) { onProgress(done, total) }

    @JavascriptInterface fun end(before: Int, after: Int, pages: Int) {
        synchronized(lock) { runCatching { out.flush() } }
        outcome.complete(Result.success(EditOutcome.Saved(before.toLong(), after.toLong(), pages)))
    }

    @JavascriptInterface fun noGain(before: Int, after: Int) {
        outcome.complete(Result.success(EditOutcome.NoGain(before.toLong(), after.toLong())))
    }

    @JavascriptInterface fun fail(message: String?) {
        outcome.complete(Result.failure(IOException(message ?: "The edit failed")))
    }
}
