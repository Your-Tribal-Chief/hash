package com.hash.browser.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle

private val TagColor = Color(0xFF22D3EE)
private val AttrColor = Color(0xFFA78BFA)
private val ValueColor = Color(0xFF34D399)
private val CommentColor = Color(0xFF6B7499)

/** Lightweight HTML colouring for the source viewer: tags, attributes, values, comments. */
fun highlightHtml(line: String): AnnotatedString = buildAnnotatedString {
    val text = if (line.length > 4000) line.take(4000) + " …" else line
    var i = 0
    val n = text.length
    while (i < n) {
        val c = text[i]
        when {
            text.startsWith("<!--", i) -> {
                val end = text.indexOf("-->", i).let { if (it < 0) n else it + 3 }
                withStyle(SpanStyle(color = CommentColor)) { append(text.substring(i, end)) }
                i = end
            }
            c == '<' -> {
                val end = text.indexOf('>', i).let { if (it < 0) n else it + 1 }
                appendTag(text.substring(i, end))
                i = end
            }
            else -> {
                val next = text.indexOf('<', i).let { if (it < 0) n else it }
                append(text.substring(i, next))
                i = next
            }
        }
    }
}

private fun AnnotatedString.Builder.appendTag(tag: String) {
    // <name attr="value" ...>
    val nameEnd = tag.indexOfFirst { it == ' ' || it == '>' || it == '\t' }.let { if (it < 0) tag.length else it }
    withStyle(SpanStyle(color = TagColor)) { append(tag.substring(0, nameEnd)) }
    var i = nameEnd
    while (i < tag.length) {
        val c = tag[i]
        if (c == '"' || c == '\'') {
            val close = tag.indexOf(c, i + 1).let { if (it < 0) tag.length else it + 1 }
            withStyle(SpanStyle(color = ValueColor)) { append(tag.substring(i, close)) }
            i = close
        } else if (c == '>' || c == '/') {
            withStyle(SpanStyle(color = TagColor)) { append(c) }
            i++
        } else {
            val start = i
            while (i < tag.length && tag[i] != '"' && tag[i] != '\'' && tag[i] != '>' && tag[i] != '/') i++
            withStyle(SpanStyle(color = AttrColor)) { append(tag.substring(start, i)) }
        }
    }
}
