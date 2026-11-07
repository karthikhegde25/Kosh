package com.kosh.android.reader

import android.graphics.Bitmap
import android.util.LruCache

/** Bounded LRU cache of rendered pages. Bitmaps still on screen are simply garbage collected later. */
class PageRenderCache(maxBytes: Int = defaultMaxBytes()) {
    private val cache = object : LruCache<String, Bitmap>(maxBytes) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    fun get(index: Int, width: Int): Bitmap? = cache.get(key(index, width))
    fun put(index: Int, width: Int, bitmap: Bitmap) { cache.put(key(index, width), bitmap) }
    fun clear() = cache.evictAll()

    private fun key(index: Int, width: Int) = "$index@$width"

    private companion object {
        fun defaultMaxBytes(): Int = minOf(Runtime.getRuntime().maxMemory() / 6, 96L * 1024 * 1024).toInt()
    }
}
