package com.kosh.android.viewer

import android.os.Handler
import android.util.Base64
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import android.os.Looper
import android.webkit.JavascriptInterface
import org.json.JSONObject

/** The `JWI` object the page calls. JS calls arrive on a background thread, so every event hops to the main thread. */
class WebInterface(
    private val tempDir: File,
    private val dispatch: (PdfListener.() -> Unit) -> Unit,
) {

    private val main = Handler(Looper.getMainLooper())
    private fun post(block: PdfListener.() -> Unit) = main.post { dispatch(block) }

    @JavascriptInterface fun onLoadSuccess(pages: Int) = post { onLoadSuccess(pages) }
    @JavascriptInterface fun onLoadFailed(message: String?, name: String?) = post { onLoadFailed(message.orEmpty(), name.orEmpty()) }
    @JavascriptInterface fun onPasswordDialogChange(open: Boolean) = post { onPasswordDialogChange(open) }
    @JavascriptInterface fun onProgressChange(percent: Int) = post { onProgressChange(percent) }
    @JavascriptInterface fun onPageChange(page: Int) = post { onPageChange(page) }
    @JavascriptInterface fun onScaleChange(scale: Double, value: String?) = post { onScaleChange(scale.toFloat(), value.orEmpty()) }
    @JavascriptInterface fun onScroll(offset: Int, total: Int, ratio: Double, horizontal: Boolean) = post { onScroll(offset, total, ratio.toFloat(), horizontal) }
    @JavascriptInterface fun onSingleClick(x: Double, y: Double, w: Double, h: Double) =
        post { onSingleClick(x.toFloat(), y.toFloat(), w.toFloat(), h.toFloat()) }
    @JavascriptInterface fun onDoubleClick(x: Double, y: Double) = post { onDoubleClick(x.toFloat(), y.toFloat()) }
    @JavascriptInterface fun onLongClick() = post { onLongClick() }
    @JavascriptInterface fun onLinkClick(href: String?) = post { onLinkClick(href.orEmpty()) }
    @JavascriptInterface fun onHighlightTapped(json: String?) {
        val o = runCatching { JSONObject(json.orEmpty()) }.getOrNull() ?: return
        val anchor = Anchor(o.optDouble("x").toFloat(), o.optDouble("y").toFloat(), o.optDouble("w").toFloat(), o.optDouble("h").toFloat())
        post { onHighlightTapped(o.optLong("id"), anchor) }
    }
    @JavascriptInterface fun onTextSelected(json: String?) { val s = parseSelection(json.orEmpty()); post { onTextSelected(s) } }
    @JavascriptInterface fun onFindMatchStart() = post { onFindMatchStart() }
    @JavascriptInterface fun onFindMatchChange(current: Int, total: Int) = post { onFindMatchChange(current, total) }
    @JavascriptInterface fun onFindMatchComplete(found: Boolean) = post { onFindMatchComplete(found) }
    @JavascriptInterface fun onOutlineLoaded(json: String?) { val o = parseOutline(json.orEmpty()); post { onOutlineLoaded(o) } }
    @JavascriptInterface fun onAttachmentsLoaded(json: String?) { val a = parseAttachments(json.orEmpty()); post { onAttachmentsLoaded(a) } }
    @JavascriptInterface fun onLoadProperties(json: String?) { val p = parseProperties(json.orEmpty()) ?: return; post { onPropertiesLoaded(p) } }
    @JavascriptInterface fun onDocumentHighlightsLoaded(json: String?) {
        val items = parseDocumentHighlights(json.orEmpty()); post { onDocumentHighlightsLoaded(items) }
    }
    @JavascriptInterface fun onAutoScrollEnd() = post { onAutoScrollEnd() }
    @JavascriptInterface fun handleBase64Data(data: String?, fileName: String?, mime: String?) =
        post { onAttachmentData(data.orEmpty(), fileName.orEmpty()) }

    // ---- doodle: the saved PDF arrives in chunks and is written straight to a temp file ----

    private val saveLock = Any()
    private var saveOut: OutputStream? = null
    private var saveFile: File? = null
    private var saveFailed = false

    @JavascriptInterface fun onDoodleChanged(strokes: Int, undoSteps: Int) = post { onDoodleChanged(strokes, undoSteps) }

    @JavascriptInterface fun onSaveBegin(totalBytes: Int) {
        synchronized(saveLock) {
            runCatching { saveOut?.close() }
            saveFailed = false
            val f = File(tempDir, "doodle-save.pdf")
            saveFile = f
            saveOut = try { BufferedOutputStream(FileOutputStream(f)) } catch (e: IOException) { saveFailed = true; null }
        }
    }

    @JavascriptInterface fun onSaveChunk(base64: String?) {
        synchronized(saveLock) {
            val out = saveOut ?: return
            try { out.write(Base64.decode(base64.orEmpty(), Base64.DEFAULT)) } catch (e: Exception) { saveFailed = true }
        }
    }

    @JavascriptInterface fun onSaveEnd() {
        val (file, failed) = synchronized(saveLock) {
            runCatching { saveOut?.close() }.onFailure { saveFailed = true }
            saveOut = null
            saveFile to saveFailed
        }
        if (file != null && !failed) post { onDoodleSaved(file) } else post { onDoodleSaveFailed("Couldn't write the file") }
    }

    @JavascriptInterface fun onSaveFailed(message: String?) {
        synchronized(saveLock) { runCatching { saveOut?.close() }; saveOut = null }
        post { onDoodleSaveFailed(message.orEmpty()) }
    }
}
