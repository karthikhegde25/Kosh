package com.kosh.android.viewer

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.Rect
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.kosh.android.R

/** A locked-down WebView that only ever shows the bundled viewer page. */
@SuppressLint("SetJavaScriptEnabled", "ViewConstructor")
class PdfJsWebView(
    context: Context,
    private val loader: ViewerResourceLoader,
    jwi: WebInterface,
    private val onPageDone: () -> Unit,
    private val onCrash: () -> Unit,
) : WebView(context) {

    /** Called when the user picks "Highlight" in the native text-selection menu. */
    var onHighlightAction: (() -> Unit)? = null

    init {
        setBackgroundColor(Color.TRANSPARENT)
        overScrollMode = OVER_SCROLL_NEVER
        isVerticalScrollBarEnabled = false
        isHorizontalScrollBarEnabled = false
        with(settings) {
            javaScriptEnabled = true
            domStorageEnabled = false
            allowFileAccess = false
            allowContentAccess = false
            blockNetworkLoads = true
            setSupportZoom(false)
            builtInZoomControls = false
            displayZoomControls = false
            textZoom = 100
            mediaPlaybackRequiresUserGesture = true
            setGeolocationEnabled(false)
        }
        addJavascriptInterface(jwi, "JWI")
        webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse =
                loader.intercept(request)

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = true

            override fun onPageFinished(view: WebView, url: String?) {
                evaluateJavascript("setupHelper()", null)
                onPageDone()
            }

            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                onCrash()
                return true
            }
        }
    }

    override fun startActionMode(callback: ActionMode.Callback?, type: Int): ActionMode? {
        val inner = callback ?: return super.startActionMode(callback, type)
        return super.startActionMode(HighlightCallback(inner), type)
    }

    private inner class HighlightCallback(private val d: ActionMode.Callback) : ActionMode.Callback2() {
        override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
            val ok = d.onCreateActionMode(mode, menu)
            menu.add(Menu.NONE, HIGHLIGHT_ID, 0, context.getString(R.string.viewer_highlight))
            return ok
        }

        override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean = d.onPrepareActionMode(mode, menu)

        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
            if (item.itemId == HIGHLIGHT_ID) {
                onHighlightAction?.invoke()
                mode.finish()
                return true
            }
            return d.onActionItemClicked(mode, item)
        }

        override fun onDestroyActionMode(mode: ActionMode) = d.onDestroyActionMode(mode)

        override fun onGetContentRect(mode: ActionMode, view: View, outRect: Rect) {
            if (d is ActionMode.Callback2) d.onGetContentRect(mode, view, outRect) else super.onGetContentRect(mode, view, outRect)
        }
    }

    private companion object {
        const val HIGHLIGHT_ID = 0x4B01
    }
}
