package com.isycat.compose.code

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

// JSON as VS Code reads its theme and settings files: comments and trailing commas allowed.
@OptIn(ExperimentalSerializationApi::class)
private val LENIENT = Json {
    allowComments = true
    allowTrailingComma = true
    isLenient = true
}

/** [text] read as JSON with comments: maps (in order), lists, strings, numbers, booleans and nulls. */
internal fun readJson(text: String): Any? = plain(LENIENT.parseToJsonElement(text.trimStart('﻿')))

private fun plain(element: JsonElement): Any? = when (element) {
    is JsonNull -> null
    is JsonObject -> element.entries.associateTo(LinkedHashMap()) { (key, value) -> key to plain(value) }
    is JsonArray -> element.map(::plain)
    is JsonPrimitive -> if (element.isString) element.content else element.booleanOrNull ?: element.longOrNull ?: element.doubleOrNull ?: element.content
}

/** An element of an XML document: its [name], its [attributes] in order, and the elements inside it. */
internal class XmlElement(val name: String, val attributes: Map<String, String>, val children: List<XmlElement>) {
    fun attribute(name: String): String = attributes[name].orEmpty()

    fun has(name: String): Boolean = name in attributes

    fun elements(name: String): List<XmlElement> = children.filter { it.name == name }
}

/**
 * The root element of the XML [text], as a scheme file holds it: elements and their attributes, nothing else read. A
 * scheme is imported from anywhere, so a document type, and with it any entity to expand or fetch, is refused, and
 * text, comments and processing instructions are passed over.
 */
internal fun readXml(text: String): XmlElement = XmlReader(text).document()

private class XmlReader(private val text: String) {
    private var at = 0

    fun document(): XmlElement {
        skipProlog()
        val root = element()
        return root
    }

    private fun skipProlog() {
        while (true) {
            skipSpace()
            when {
                text.startsWith("﻿", at) -> at++
                text.startsWith("<?", at) -> skipPast("?>")
                text.startsWith("<!--", at) -> skipPast("-->")
                text.startsWith("<!DOCTYPE", at, ignoreCase = true) -> fail("a document type is not read")
                else -> return
            }
        }
    }

    private fun element(): XmlElement {
        expect('<')
        val name = name()
        val attributes = LinkedHashMap<String, String>()
        while (true) {
            skipSpace()
            when {
                text.startsWith("/>", at) -> {
                    at += 2
                    return XmlElement(name, attributes, emptyList())
                }
                peek() == '>' -> {
                    at++
                    return XmlElement(name, attributes, content(name))
                }
                else -> {
                    val attribute = name()
                    skipSpace()
                    expect('=')
                    skipSpace()
                    attributes[attribute] = quoted()
                }
            }
        }
    }

    /** The elements inside [name], up to its closing tag. */
    private fun content(name: String): List<XmlElement> {
        val children = ArrayList<XmlElement>()
        while (true) {
            val next = text.indexOf('<', at)
            if (next < 0) fail("<$name> is never closed")
            at = next
            when {
                text.startsWith("</", at) -> {
                    at += 2
                    val closing = name()
                    if (closing != name) fail("</$closing> closes <$name>")
                    skipSpace()
                    expect('>')
                    return children
                }
                text.startsWith("<!--", at) -> skipPast("-->")
                text.startsWith("<![CDATA[", at) -> skipPast("]]>")
                text.startsWith("<?", at) -> skipPast("?>")
                text.startsWith("<!", at) -> fail("a declaration is not read")
                else -> children += element()
            }
        }
    }

    private fun name(): String {
        val start = at
        while (at < text.length && (text[at].isLetterOrDigit() || text[at] in "_-.:")) at++
        if (at == start) fail("a name was expected")
        return text.substring(start, at)
    }

    private fun quoted(): String {
        val quote = peek()
        if (quote != '"' && quote != '\'') fail("a quoted value was expected")
        val end = text.indexOf(quote, at + 1)
        if (end < 0) fail("a value is never closed")
        val value = decoded(text.substring(at + 1, end))
        at = end + 1
        return value
    }

    /** [raw] with XML's character references and its five named entities written out. */
    private fun decoded(raw: String): String {
        if ('&' !in raw) return raw
        return REFERENCE.replace(raw) { match ->
            val name = match.groupValues[1]
            when {
                name.startsWith("#x") -> name.drop(2).toIntOrNull(16)?.let(::character) ?: match.value
                name.startsWith("#") -> name.drop(1).toIntOrNull()?.let(::character) ?: match.value
                else -> NAMED[name] ?: match.value
            }
        }
    }

    private fun character(code: Int): String = if (code < 0x10000) code.toChar().toString() else {
        val offset = code - 0x10000
        charArrayOf((0xD800 + (offset shr 10)).toChar(), (0xDC00 + (offset and 0x3FF)).toChar()).concatToString()
    }

    private fun peek(): Char = text.getOrElse(at) { fail("the document ends early") }

    private fun expect(c: Char) {
        if (peek() != c) fail("'$c' was expected")
        at++
    }

    private fun skipSpace() {
        while (at < text.length && text[at].isWhitespace()) at++
    }

    private fun skipPast(end: String) {
        val found = text.indexOf(end, at)
        if (found < 0) fail("'$end' was expected")
        at = found + end.length
    }

    private fun fail(why: String): Nothing = throw IllegalArgumentException("not a scheme's XML: $why, at $at")

    private companion object {
        val REFERENCE = Regex("&(#x[0-9a-fA-F]+|#[0-9]+|[a-zA-Z]+);")
        val NAMED = mapOf("amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'")
    }
}
