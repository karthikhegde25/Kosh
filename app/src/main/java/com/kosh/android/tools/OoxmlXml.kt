package com.kosh.android.tools

import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.InputStream
import javax.xml.parsers.DocumentBuilderFactory

/* XML helpers shared by the Word, Excel and PowerPoint readers (all three are ZIPs of XML parts). No Android classes. */

internal const val R_NS = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"

/** Reads one XML part and returns its root element. DTDs and external entities are refused: the file isn't trusted. */
internal fun parseXmlPart(stream: InputStream): Element = stream.use {
    val factory = DocumentBuilderFactory.newInstance()
    factory.isNamespaceAware = true
    runCatching { factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
    runCatching { factory.setFeature("http://xml.org/sax/features/external-general-entities", false) }
    runCatching { factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
    runCatching { factory.isExpandEntityReferences = false }
    try {
        factory.newDocumentBuilder().parse(it).documentElement
    } catch (e: Exception) {
        throw DocxException("The file is damaged")
    }
}

/** A relationship target relative to the folder [baseDir] of the part that holds it ("ppt/slides" + "../media/a.png"). */
internal fun resolveTarget(baseDir: String, target: String): String {
    val parts = ArrayList<String>()
    val full = if (target.startsWith("/")) target.removePrefix("/") else "$baseDir/$target"
    for (part in full.split('/')) {
        when (part) {
            "", "." -> Unit
            ".." -> if (parts.isNotEmpty()) parts.removeAt(parts.lastIndex)
            else -> parts += part
        }
    }
    return parts.joinToString("/")
}

/** Relationship id to part path, from the ".rels" file that belongs to [partPath]. External links are left out. */
internal fun readRelationships(partPath: String, read: (String) -> InputStream?): Map<String, String> {
    val dir = partPath.substringBeforeLast('/', "")
    val name = partPath.substringAfterLast('/')
    val stream = read((if (dir.isEmpty()) "" else "$dir/") + "_rels/$name.rels") ?: return emptyMap()
    val out = HashMap<String, String>()
    for (rel in parseXmlPart(stream).children("Relationship")) {
        val id = rel.getAttribute("Id")
        val target = rel.getAttribute("Target")
        if (id.isNotEmpty() && target.isNotEmpty() && !rel.getAttribute("TargetMode").equals("External", ignoreCase = true)) {
            out[id] = resolveTarget(dir, target)
        }
    }
    return out
}

internal fun Element.childElements(): List<Element> {
    val out = ArrayList<Element>()
    var n: Node? = firstChild
    while (n != null) {
        if (n.nodeType == Node.ELEMENT_NODE) out += n as Element
        n = n.nextSibling
    }
    return out
}

internal fun Element.children(name: String): List<Element> = childElements().filter { it.localName == name }

internal fun Element.child(name: String): Element? = childElements().firstOrNull { it.localName == name }

internal fun Element.rel(name: String): String? = getAttributeNS(R_NS, name).takeIf { it.isNotEmpty() }

/** An attribute without a namespace (most Excel and PowerPoint attributes), or null when absent. */
internal fun Element.attr(name: String): String? = getAttribute(name).takeIf { it.isNotEmpty() }

internal fun Element.firstDescendant(match: (Element) -> Boolean): Element? {
    for (child in childElements()) {
        if (match(child)) return child
        child.firstDescendant(match)?.let { return it }
    }
    return null
}

/** Text of an element's descendants named [name] ("t" in a rich-text run), joined. */
internal fun Element.descendantText(name: String): String {
    val sb = StringBuilder()
    fun walk(e: Element) {
        for (c in e.childElements()) {
            if (c.localName == name) sb.append(c.textContent) else walk(c)
        }
    }
    walk(this)
    return sb.toString()
}
