package com.kosh.android.viewer

import org.json.JSONArray
import org.json.JSONObject

internal fun parseQuads(a: JSONArray?): List<Quad> {
    if (a == null) return emptyList()
    return (0 until a.length()).mapNotNull { i ->
        a.optJSONObject(i)?.let {
            Quad(it.optDouble("x").toFloat(), it.optDouble("y").toFloat(), it.optDouble("w").toFloat(), it.optDouble("h").toFloat())
        }
    }
}

internal fun quadsToJson(quads: List<Quad>): String {
    val a = JSONArray()
    for (q in quads) a.put(JSONObject().put("x", q.x.toDouble()).put("y", q.y.toDouble()).put("w", q.w.toDouble()).put("h", q.h.toDouble()))
    return a.toString()
}

internal fun parseOutline(json: String): List<OutlineNode> {
    fun walk(a: JSONArray): List<OutlineNode> = (0 until a.length()).mapNotNull { i ->
        a.optJSONObject(i)?.let {
            OutlineNode(it.optString("id"), it.optString("title"), it.optInt("page"), walk(it.optJSONArray("children") ?: JSONArray()))
        }
    }
    return runCatching { walk(JSONArray(json)) }.getOrDefault(emptyList())
}

internal fun parseAttachments(json: String): List<AttachmentItem> = runCatching {
    val a = JSONArray(json)
    (0 until a.length()).mapNotNull { i -> a.optJSONObject(i)?.let { AttachmentItem(it.optString("id"), it.optString("title")) } }
}.getOrDefault(emptyList())

internal fun parseSelection(json: String): TextSelection? {
    if (json.isBlank()) return null
    return runCatching {
        val o = JSONObject(json)
        val an = o.getJSONObject("anchor")
        TextSelection(
            page = o.getInt("page"),
            text = o.getString("text"),
            quads = parseQuads(o.optJSONArray("quads")),
            anchor = Anchor(an.optDouble("x").toFloat(), an.optDouble("y").toFloat(), an.optDouble("w").toFloat(), an.optDouble("h").toFloat()),
        )
    }.getOrNull()
}

internal fun parseProperties(json: String): DocProperties? = runCatching {
    val o = JSONObject(json)
    val custom = mutableMapOf<String, String>()
    o.optJSONObject("custom")?.let { c -> c.keys().forEach { k -> custom[k] = c.optString(k) } }
    DocProperties(
        title = o.optString("title", "-"), subject = o.optString("subject", "-"), author = o.optString("author", "-"),
        creator = o.optString("creator", "-"), producer = o.optString("producer", "-"),
        creationDate = o.optString("creationDate", "-"), modDate = o.optString("modDate", "-"),
        keywords = o.optString("keywords", "-"), language = o.optString("language", "-"),
        pdfVersion = o.optString("pdfVersion", "-"), sizeBytes = o.optLong("size", 0L),
        linearized = o.optBoolean("linearized"), encryptFilterName = o.optString("encryptFilterName"),
        acroForm = o.optBoolean("acroForm"), signatures = o.optBoolean("signatures"), xfa = o.optBoolean("xfa"),
        custom = custom,
    )
}.getOrNull()

internal fun parseDocumentHighlights(json: String): List<HighlightItem> = runCatching {
    val a = JSONArray(json)
    (0 until a.length()).mapNotNull { i ->
        a.optJSONObject(i)?.let {
            HighlightItem(
                id = it.optLong("id"), page = it.optInt("page"), text = it.optString("text"),
                quads = parseQuads(it.optJSONArray("quads")),
                colorArgb = hexToArgb(it.optString("color")) ?: 0xFFFFFF98.toInt(),
                note = it.optString("note").ifBlank { null }, label = it.optString("label").ifBlank { null },
                fromDocument = true,
            )
        }
    }
}.getOrDefault(emptyList())

/** Highlights drawn by the page overlay: id, page, colour and quads. */
internal fun highlightsToJs(items: List<HighlightItem>): String {
    val a = JSONArray()
    for (h in items) {
        a.put(
            JSONObject().put("id", h.id).put("page", h.page).put("color", argbToCss(h.colorArgb))
                .put("quads", JSONArray(quadsToJson(h.quads))),
        )
    }
    return a.toString()
}

/** Semi-transparent so text stays readable under the highlight. */
internal fun argbToCss(argb: Int): String {
    val r = (argb shr 16) and 0xFF
    val g = (argb shr 8) and 0xFF
    val b = argb and 0xFF
    return "rgba($r,$g,$b,0.55)"
}
