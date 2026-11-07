package com.kosh.android.tools

import org.w3c.dom.Element
import java.io.InputStream

/*
 * Reads a PowerPoint (.pptx) deck into slides of positioned shapes (all in points): filled boxes, text, pictures and simple
 * tables. Position and size of placeholders come from the slide layout / master when the slide does not give its own.
 * Charts, SmartArt, rotation, gradients and animations are not drawn. No Android classes, so it runs on a plain JVM.
 */

internal data class PptxBox(val x: Float, val y: Float, val w: Float, val h: Float)

internal data class PptxRun(val text: String, val size: Float?, val bold: Boolean, val italic: Boolean, val color: Int?)

internal data class PptxParagraph(
    val runs: List<PptxRun>,
    /** 0 left, 1 centre, 2 right. */
    val align: Int,
    val bullet: Boolean,
    val level: Int,
)

internal sealed class PptxShape {
    abstract val box: PptxBox

    data class Block(override val box: PptxBox, val fill: Int?, val line: Int?, val paragraphs: List<PptxParagraph>, val middle: Boolean) : PptxShape()
    data class Picture(override val box: PptxBox, val path: String) : PptxShape()
    data class Table(override val box: PptxBox, val colWidths: List<Float>, val rows: List<List<String>>) : PptxShape()
}

internal data class PptxSlide(val background: Int?, val shapes: List<PptxShape>)

internal data class PptxDeck(val width: Float, val height: Float, val slides: List<PptxSlide>)

internal class PptxParser(private val read: (String) -> InputStream?) {

    private var theme: Map<String, Int> = emptyMap()

    fun parse(): PptxDeck {
        val presentationPath = "ppt/presentation.xml"
        val presentation = read(presentationPath)?.let(::parseXmlPart) ?: throw DocxException("This isn't a PowerPoint (.pptx) file")
        val relations = readRelationships(presentationPath, read)

        val size = presentation.child("sldSz")
        val width = emu(size?.attr("cx")) ?: DEFAULT_W
        val height = emu(size?.attr("cy")) ?: DEFAULT_H

        val slides = ArrayList<PptxSlide>()
        for (id in presentation.child("sldIdLst")?.children("sldId").orEmpty()) {
            val path = id.rel("id")?.let { relations[it] } ?: continue
            slides += readSlide(path)
        }
        if (slides.isEmpty()) throw DocxException("This presentation has no slides")
        return PptxDeck(width, height, slides)
    }

    private fun emu(value: String?): Float? = value?.toLongOrNull()?.let { it / 12700f }

    // ---- theme colours -----------------------------------------------------------------------------------------------------

    private fun loadTheme(masterPath: String?) {
        theme = emptyMap()
        val master = masterPath ?: return
        val themePath = readRelationships(master, read).values.firstOrNull { it.contains("/theme/") } ?: return
        val scheme = read(themePath)?.let(::parseXmlPart)?.child("themeElements")?.child("clrScheme") ?: return
        val map = HashMap<String, Int>()
        for (c in scheme.childElements()) {
            val inner = c.childElements().firstOrNull() ?: continue
            val rgb = if (inner.localName == "srgbClr") inner.attr("val") else inner.attr("lastClr")
            rgb?.toIntOrNull(16)?.let { map[c.localName] = 0xFF000000.toInt() or it }
        }
        // Slides refer to the two light/dark pairs by their mapped names.
        map["dk1"]?.let { map["tx1"] = it }
        map["lt1"]?.let { map["bg1"] = it }
        map["dk2"]?.let { map["tx2"] = it }
        map["lt2"]?.let { map["bg2"] = it }
        theme = map
    }

    private fun colorOf(holder: Element?): Int? {
        val c = holder?.childElements()?.firstOrNull { it.localName == "srgbClr" || it.localName == "schemeClr" || it.localName == "sysClr" } ?: return null
        val base = when (c.localName) {
            "srgbClr" -> c.attr("val")?.toIntOrNull(16)?.let { 0xFF000000.toInt() or it }
            "sysClr" -> c.attr("lastClr")?.toIntOrNull(16)?.let { 0xFF000000.toInt() or it }
            else -> theme[c.attr("val")]
        } ?: return null
        val lum = c.child("lumMod")?.attr("val")?.toIntOrNull()?.div(100000f)
        val off = c.child("lumOff")?.attr("val")?.toIntOrNull()?.div(100000f) ?: 0f
        if (lum == null && off == 0f) return base
        fun adjust(channel: Int) = ((channel * (lum ?: 1f)) + 255 * off).toInt().coerceIn(0, 255)
        val r = adjust(base shr 16 and 0xFF)
        val g = adjust(base shr 8 and 0xFF)
        val b = adjust(base and 0xFF)
        return 0xFF000000.toInt() or (r shl 16) or (g shl 8) or b
    }

    /** The colour of a fill element (`solidFill` directly inside [props]), or null when there is none or it is not solid. */
    private fun solid(props: Element?): Int? = colorOf(props?.child("solidFill"))

    // ---- one slide --------------------------------------------------------------------------------------------------------

    private class Inherited(val boxes: Map<String, PptxBox>)

    private fun readSlide(path: String): PptxSlide {
        val root = read(path)?.let(::parseXmlPart) ?: return PptxSlide(null, emptyList())
        val relations = readRelationships(path, read)
        val layoutPath = relations.values.firstOrNull { it.contains("/slideLayouts/") }
        val layoutRoot = layoutPath?.let { read(it) }?.let(::parseXmlPart)
        val masterPath = layoutPath?.let { readRelationships(it, read).values.firstOrNull { p -> p.contains("/slideMasters/") } }
        val masterRoot = masterPath?.let { read(it) }?.let(::parseXmlPart)
        loadTheme(masterPath)

        val placeholders = HashMap<String, PptxBox>()
        for (source in listOfNotNull(masterRoot, layoutRoot)) collectPlaceholders(source, placeholders)

        val background = solid(root.child("cSld")?.child("bg")?.child("bgPr"))
            ?: solid(layoutRoot?.child("cSld")?.child("bg")?.child("bgPr"))
            ?: solid(masterRoot?.child("cSld")?.child("bg")?.child("bgPr"))

        val shapes = ArrayList<PptxShape>()
        val tree = root.child("cSld")?.child("spTree")
        if (tree != null) readTree(tree, relations, path, placeholders, Transform.NONE, shapes)
        return PptxSlide(background, shapes)
    }

    private fun placeholderKey(ph: Element): List<String> {
        val type = ph.attr("type") ?: "body"
        val idx = ph.attr("idx")
        // Slides say "title" and layouts may say "ctrTitle"; both are looked up under the same name.
        val canonical = if (type == "ctrTitle") "title" else type
        return listOfNotNull(idx?.let { "idx:$it" }, "type:$canonical")
    }

    private fun collectPlaceholders(root: Element, into: MutableMap<String, PptxBox>) {
        val tree = root.child("cSld")?.child("spTree") ?: return
        for (sp in tree.children("sp")) {
            val ph = sp.child("nvSpPr")?.child("nvPr")?.child("ph") ?: continue
            val box = boxOf(sp.child("spPr")?.child("xfrm"), Transform.NONE) ?: continue
            for (key in placeholderKey(ph)) into[key] = box
        }
    }

    /** Child coordinates inside a group are mapped to the slide with a scale and offset. */
    private data class Transform(val sx: Float, val sy: Float, val dx: Float, val dy: Float) {
        companion object { val NONE = Transform(1f, 1f, 0f, 0f) }
    }

    private fun boxOf(xfrm: Element?, t: Transform): PptxBox? {
        val off = xfrm?.child("off") ?: return null
        val ext = xfrm.child("ext") ?: return null
        val x = emu(off.attr("x")) ?: return null
        val y = emu(off.attr("y")) ?: return null
        val w = emu(ext.attr("cx")) ?: return null
        val h = emu(ext.attr("cy")) ?: return null
        return PptxBox(x * t.sx + t.dx, y * t.sy + t.dy, w * t.sx, h * t.sy)
    }

    private fun readTree(
        tree: Element,
        rels: Map<String, String>,
        slidePath: String,
        placeholders: Map<String, PptxBox>,
        t: Transform,
        out: MutableList<PptxShape>,
    ) {
        for (node in tree.childElements()) {
            when (node.localName) {
                "sp" -> readShape(node, placeholders, t)?.let { out += it }
                "pic" -> readPicture(node, rels, placeholders, t)?.let { out += it }
                "graphicFrame" -> readTable(node, t)?.let { out += it }
                "grpSp" -> {
                    val xfrm = node.child("grpSpPr")?.child("xfrm")
                    val box = boxOf(xfrm, t)
                    val chOff = xfrm?.child("chOff")
                    val chExt = xfrm?.child("chExt")
                    val cw = emu(chExt?.attr("cx"))
                    val ch = emu(chExt?.attr("cy"))
                    val inner = if (box != null && cw != null && ch != null && cw > 0 && ch > 0) {
                        val sx = box.w / cw
                        val sy = box.h / ch
                        Transform(sx, sy, box.x - (emu(chOff?.attr("x")) ?: 0f) * sx, box.y - (emu(chOff?.attr("y")) ?: 0f) * sy)
                    } else t
                    readTree(node, rels, slidePath, placeholders, inner, out)
                }
            }
        }
    }

    private fun placeholderBox(nvPr: Element?, placeholders: Map<String, PptxBox>): PptxBox? {
        val ph = nvPr?.child("ph") ?: return null
        return placeholderKey(ph).firstNotNullOfOrNull { placeholders[it] }
    }

    private fun readShape(sp: Element, placeholders: Map<String, PptxBox>, t: Transform): PptxShape? {
        val props = sp.child("spPr")
        val nvPr = sp.child("nvSpPr")?.child("nvPr")
        val box = boxOf(props?.child("xfrm"), t) ?: placeholderBox(nvPr, placeholders)?.let { PptxBox(it.x * t.sx + t.dx, it.y * t.sy + t.dy, it.w * t.sx, it.h * t.sy) } ?: return null
        val fill = solid(props)
        val line = props?.child("ln")?.let { solid(it) }
        val phType = nvPr?.child("ph")?.attr("type")
        val titleLike = phType == "title" || phType == "ctrTitle"
        val body = sp.child("txBody")
        val paragraphs = body?.children("p").orEmpty().map { readParagraph(it, if (titleLike) TITLE_SIZE else null, titleLike) }
            .let { list -> list.dropLastWhile { p -> p.runs.isEmpty() } }
        val shrink = body?.child("bodyPr")?.child("normAutofit")?.attr("fontScale")?.toIntOrNull()?.div(100000f)
        val scaled = if (shrink == null) paragraphs else paragraphs.map { p -> p.copy(runs = p.runs.map { r -> r.copy(size = r.size?.times(shrink)) }) }
        if (fill == null && line == null && scaled.isEmpty()) return null
        val middle = body?.child("bodyPr")?.attr("anchor") == "ctr" || (titleLike && phType == "ctrTitle")
        return PptxShape.Block(box, fill, line, scaled, middle)
    }

    private fun readParagraph(p: Element, defaultSize: Float?, defaultBold: Boolean): PptxParagraph {
        val props = p.child("pPr")
        val align = when (props?.attr("algn")) { "ctr" -> 1; "r" -> 2; else -> 0 }
        val bullet = props != null && props.child("buChar") != null || props?.child("buAutoNum") != null
        val level = props?.attr("lvl")?.toIntOrNull() ?: 0
        val runs = ArrayList<PptxRun>()
        for (r in p.childElements()) {
            when (r.localName) {
                "r" -> {
                    val text = r.child("t")?.textContent ?: continue
                    val rp = r.child("rPr")
                    runs += PptxRun(
                        text = text,
                        size = rp?.attr("sz")?.toFloatOrNull()?.div(100f) ?: defaultSize,
                        bold = rp?.attr("b")?.let { it == "1" || it == "true" } ?: defaultBold,
                        italic = rp?.attr("i")?.let { it == "1" || it == "true" } ?: false,
                        color = solid(rp),
                    )
                }
                "br" -> runs += PptxRun("\n", defaultSize, false, false, null)
                "fld" -> r.child("t")?.textContent?.let { runs += PptxRun(it, defaultSize, defaultBold, false, null) }
            }
        }
        return PptxParagraph(runs, align, bullet, level)
    }

    private fun readPicture(pic: Element, rels: Map<String, String>, placeholders: Map<String, PptxBox>, t: Transform): PptxShape? {
        val embed = pic.child("blipFill")?.child("blip")?.rel("embed") ?: return null
        val path = rels[embed] ?: return null
        val box = boxOf(pic.child("spPr")?.child("xfrm"), t) ?: placeholderBox(pic.child("nvPicPr")?.child("nvPr"), placeholders) ?: return null
        return PptxShape.Picture(box, path)
    }

    private fun readTable(frame: Element, t: Transform): PptxShape? {
        val table = frame.child("graphic")?.child("graphicData")?.child("tbl") ?: return null
        val box = boxOf(frame.child("xfrm"), t) ?: return null
        val widths = table.child("tblGrid")?.children("gridCol").orEmpty().map { emu(it.attr("w")) ?: 0f }.map { it * t.sx }
        val rows = table.children("tr").map { tr ->
            tr.children("tc").map { tc ->
                tc.child("txBody")?.children("p").orEmpty().joinToString("\n") { it.descendantText("t") }
            }
        }
        if (rows.isEmpty()) return null
        return PptxShape.Table(box, widths, rows)
    }

    companion object {
        private const val DEFAULT_W = 720f
        private const val DEFAULT_H = 405f
        private const val TITLE_SIZE = 32f
    }
}
