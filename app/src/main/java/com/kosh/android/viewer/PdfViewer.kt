package com.kosh.android.viewer

import android.content.Context
import android.net.Uri
import android.widget.FrameLayout
import org.json.JSONObject
import java.util.concurrent.CopyOnWriteArrayList

/**
 * The PDF viewer engine: a WebView running pdf.js, wrapped so the rest of the app talks to it with plain Kotlin calls
 * and listener events. Commands issued before the page is ready are queued and run once it is.
 */
class PdfViewer(context: Context) : FrameLayout(context) {

    private val listeners = CopyOnWriteArrayList<PdfListener>()
    private val loader = ViewerResourceLoader(context)
    private val queue = ArrayDeque<String>()
    private var ready = false
    private var destroyed = false

    var currentPage: Int = 1
        private set
    var pagesCount: Int = 0
        private set
    var scale: Float = 1f
        private set
    var scaleValue: String = ""
        private set
    var isLoaded: Boolean = false
        private set

    /** Fired when "Highlight" is chosen in the native selection menu. */
    var onHighlightAction: (() -> Unit)?
        get() = web.onHighlightAction
        set(v) { web.onHighlightAction = v }

    private val web: PdfJsWebView = PdfJsWebView(
        context, loader,
        WebInterface(context.cacheDir) { block -> if (!destroyed) { for (l in listeners) block(l) } },
        onPageDone = { flushQueue() },
        onCrash = { destroyInternal(); for (l in listeners) l.onRendererCrashed() },
    )

    private val tracker = object : PdfListener {
        override fun onLoadSuccess(pagesCount: Int) { this@PdfViewer.pagesCount = pagesCount; isLoaded = true }
        override fun onPageChange(page: Int) { currentPage = page }
        override fun onScaleChange(scale: Float, scaleValue: String) { this@PdfViewer.scale = scale; this@PdfViewer.scaleValue = scaleValue }
    }

    init {
        addView(web, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        listeners += tracker
        web.loadUrl(ViewerResourceLoader.PAGE_URL)
    }

    fun addListener(l: PdfListener) { listeners += l }
    fun removeListener(l: PdfListener) { listeners -= l }

    private fun flushQueue() {
        ready = true
        while (queue.isNotEmpty()) web.evaluateJavascript(queue.removeFirst(), null)
    }

    // ---- JS plumbing ------------------------------------------------------------------------

    private fun exec(js: String) {
        if (destroyed) return
        if (ready) web.evaluateJavascript(js, null) else queue.addLast(js)
    }

    private fun call(fn: String, vararg args: Any?) {
        exec(fn + "(" + args.joinToString(",") { jsArg(it) } + ")")
    }

    private fun jsArg(a: Any?): String = when (a) {
        null -> "null"
        is String -> JSONObject.quote(a)
        is Boolean, is Int, is Long -> a.toString()
        is Float -> if (a.isFinite()) a.toString() else "0"
        is Double -> if (a.isFinite()) a.toString() else "0"
        else -> JSONObject.quote(a.toString())
    }

    // ---- document ---------------------------------------------------------------------------

    fun open(uri: Uri) {
        val s = uri.toString()
        loader.allowedDocument = s
        isLoaded = false
        val p = JSONObject().put("url", ViewerResourceLoader.documentUrl(s)).put("originalUrl", s)
        call("openUrl", p.toString())
    }

    fun submitPassword(password: String) = call("submitPassword", password)

    // ---- navigation & zoom --------------------------------------------------------------------

    fun goToPage(page: Int) = call("goToPage", page)
    fun scrollToRatio(ratio: Float, horizontal: Boolean) = call("scrollToRatio", ratio, horizontal)
    fun zoomPreset(preset: ZoomPreset) = call("setScaleValue", preset.token)
    fun zoomTo(scale: Float) = call("scalePageTo", clampScale(scale))
    fun zoomToAt(scale: Float, x: Float, y: Float) = call("scalePageToAt", clampScale(scale), x, y)
    fun zoomIn() = call("zoomIn")
    fun zoomOut() = call("zoomOut")

    /** Scale that [preset] would produce for the current page and window, or null if unknown. */
    fun actualScaleFor(preset: ZoomPreset, callback: (Float?) -> Unit) {
        if (!ready) { callback(null); return }
        web.evaluateJavascript("getActualScaleFor(${jsArg(preset.token)})") { r ->
            callback(r?.toFloatOrNull()?.takeIf { it > 0f })
        }
    }

    // ---- layout & appearance ------------------------------------------------------------------

    fun setScrollDirection(d: ScrollDirection) = call("setScrollMode", d.ordinal)
    fun setRotation(degrees: Int) = call("setRotation", degrees)
    fun setSnap(enabled: Boolean, direction: ScrollDirection) =
        call("setSnap", if (!enabled) "none" else if (direction == ScrollDirection.HORIZONTAL) "x" else "y")
    fun setHorizontalLock(enabled: Boolean) = call("setHorizontalScrollLock", enabled)
    fun setTheme(theme: ViewerTheme) = call("setReadingTheme", theme.js)
    fun setBackgroundCss(css: String) = call("setContainerBackgroundColor", css)
    fun setContentPadding(topPx: Int, bottomPx: Int) = call("setContentPadding", topPx, bottomPx)

    // ---- search -------------------------------------------------------------------------------

    fun find(query: String, matchCase: Boolean = false, entireWord: Boolean = false, diacritics: Boolean = false) =
        call("startFind", query, matchCase, entireWord, diacritics)
    fun findNext() = call("findNext")
    fun findPrevious() = call("findPrevious")
    fun stopFind() = call("stopFind")

    // ---- selection & highlights -----------------------------------------------------------------

    fun clearSelection() = call("clearTextSelection")
    fun showHighlights(items: List<HighlightItem>) = call("applyStoredHighlights", highlightsToJs(items))
    fun scrollToHighlight(id: Long) = call("scrollToHighlight", id)
    fun scrollToQuad(page: Int, quad: Quad) = exec("scrollToPageQuad($page,${quadLiteral(quad)})")

    private fun quadLiteral(q: Quad) = "{x:${q.x},y:${q.y},w:${q.w},h:${q.h}}"

    // ---- doodle -------------------------------------------------------------------------------------

    fun setDoodleMode(on: Boolean) = call("setDoodleMode", on)
    fun setDoodleEraser(eraser: Boolean) = call("setDoodleTool", if (eraser) "eraser" else "pen")
    fun setDoodleHand() = call("setDoodleTool", "hand")
    fun setDoodleColor(argb: Int) = call("setDoodleColor", argbToHex(argb))
    fun setDoodleSize(size: DoodleSize) = call("setDoodleWidth", size.fraction)
    fun doodleUndo() = call("doodleUndo")
    fun clearDoodles() = call("clearDoodles")

    /** Asks the page to write the strokes into a new copy of the PDF; the result arrives via [PdfListener.onDoodleSaved]. */
    fun saveDoodled() = call("saveDoodled")

    // ---- attachments ------------------------------------------------------------------------------

    fun downloadAttachment(id: String) = call("downloadAttachment", id)

    // ---- auto scroll ------------------------------------------------------------------------------

    fun startAutoScroll(pxPerSecond: Int) = call("startAutoScroll", pxPerSecond)
    fun stopAutoScroll() = call("stopAutoScroll")
    fun pauseAutoScroll() = call("pauseAutoScroll")
    fun resumeAutoScroll() = call("resumeAutoScroll")
    fun setAutoScrollSpeed(pxPerSecond: Int) = call("setAutoScrollSpeed", pxPerSecond)

    // ---- lifecycle --------------------------------------------------------------------------------

    fun onPause() { if (!destroyed) web.onPause() }
    fun onResume() { if (!destroyed) web.onResume() }

    fun destroy() = destroyInternal()

    private fun destroyInternal() {
        if (destroyed) return
        destroyed = true
        queue.clear()
        removeAllViews()
        web.stopLoading()
        web.destroy()
    }
}
