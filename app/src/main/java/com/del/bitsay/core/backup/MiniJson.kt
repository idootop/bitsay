package com.del.bitsay.core.backup

import java.util.Locale

/**
 * Minimal, dependency-free JSON reader/writer.
 *
 * Why hand-rolled: the backup payload is the only JSON this app ever touches, and pulling in
 * a JSON library (or a serialization compiler plugin) would cost APK size and build time for
 * a fixed, flat shape. `org.json` is not an option either — it is an Android framework class
 * and therefore stubbed out in JVM unit tests. This file is pure Kotlin, so the whole backup
 * format is covered by fast, hermetic unit tests.
 */
object MiniJson {

    class JsonException(message: String) : IllegalArgumentException(message)

    // ------------------------------------------------------------------ writing

    fun escape(value: String): String {
        val sb = StringBuilder(value.length + 16)
        sb.append('"')
        for (ch in value) {
            when (ch) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                '\b' -> sb.append("\\b")
                '\u000C' -> sb.append("\\f")
                else -> if (ch < ' ') {
                    sb.append(String.format(Locale.ROOT, "\\u%04x", ch.code))
                } else {
                    sb.append(ch)
                }
            }
        }
        sb.append('"')
        return sb.toString()
    }

    // ------------------------------------------------------------------ parsing

    /** @return `Map<String, Any?>`, `List<Any?>`, `String`, `Long`, `Double`, `Boolean` or null. */
    fun parse(text: String): Any? {
        val parser = Parser(text)
        val value = parser.readDocument()
        return value
    }

    private class Parser(private val src: String) {
        private var pos = 0

        fun readDocument(): Any? {
            val value = readValue()
            skipWhitespace()
            if (pos < src.length) fail("unexpected trailing content")
            return value
        }

        private fun readValue(): Any? {
            skipWhitespace()
            if (pos >= src.length) fail("unexpected end of input")
            return when (val c = src[pos]) {
                '{' -> readObject()
                '[' -> readArray()
                '"' -> readString()
                't' -> readLiteral("true", true)
                'f' -> readLiteral("false", false)
                'n' -> readLiteral("null", null)
                else -> if (c == '-' || c in '0'..'9') readNumber() else fail("unexpected '$c'")
            }
        }

        private fun readObject(): Map<String, Any?> {
            expect('{')
            val out = LinkedHashMap<String, Any?>()
            skipWhitespace()
            if (peek() == '}') {
                pos++
                return out
            }
            while (true) {
                skipWhitespace()
                val key = readString()
                skipWhitespace()
                expect(':')
                out[key] = readValue()
                skipWhitespace()
                when (val c = next()) {
                    ',' -> continue
                    '}' -> return out
                    else -> fail("expected ',' or '}' but found '$c'")
                }
            }
        }

        private fun readArray(): List<Any?> {
            expect('[')
            val out = ArrayList<Any?>()
            skipWhitespace()
            if (peek() == ']') {
                pos++
                return out
            }
            while (true) {
                out.add(readValue())
                skipWhitespace()
                when (val c = next()) {
                    ',' -> continue
                    ']' -> return out
                    else -> fail("expected ',' or ']' but found '$c'")
                }
            }
        }

        private fun readString(): String {
            expect('"')
            val sb = StringBuilder()
            while (true) {
                if (pos >= src.length) fail("unterminated string")
                when (val c = src[pos++]) {
                    '"' -> return sb.toString()
                    '\\' -> {
                        if (pos >= src.length) fail("unterminated escape")
                        when (val esc = src[pos++]) {
                            '"' -> sb.append('"')
                            '\\' -> sb.append('\\')
                            '/' -> sb.append('/')
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\u000C')
                            'u' -> {
                                if (pos + 4 > src.length) fail("truncated \\u escape")
                                val hex = src.substring(pos, pos + 4)
                                pos += 4
                                val code = hex.toIntOrNull(16) ?: fail("bad \\u escape '$hex'")
                                sb.append(code.toChar())
                            }
                            else -> fail("unknown escape '\\$esc'")
                        }
                    }
                    else -> sb.append(c)
                }
            }
        }

        private fun readNumber(): Any {
            val start = pos
            if (peek() == '-') pos++
            while (pos < src.length && src[pos] in '0'..'9') pos++
            var floating = false
            if (pos < src.length && src[pos] == '.') {
                floating = true
                pos++
                while (pos < src.length && src[pos] in '0'..'9') pos++
            }
            if (pos < src.length && (src[pos] == 'e' || src[pos] == 'E')) {
                floating = true
                pos++
                if (pos < src.length && (src[pos] == '+' || src[pos] == '-')) pos++
                while (pos < src.length && src[pos] in '0'..'9') pos++
            }
            val token = src.substring(start, pos)
            if (token.isEmpty() || token == "-") fail("malformed number '$token'")
            return if (floating) {
                token.toDoubleOrNull() ?: fail("malformed number '$token'")
            } else {
                token.toLongOrNull() ?: token.toDoubleOrNull() ?: fail("malformed number '$token'")
            }
        }

        private fun <T> readLiteral(literal: String, value: T): T {
            if (!src.startsWith(literal, pos)) fail("expected '$literal'")
            pos += literal.length
            return value
        }

        private fun skipWhitespace() {
            while (pos < src.length && src[pos].isWhitespace()) pos++
        }

        private fun peek(): Char = if (pos < src.length) src[pos] else '\u0000'

        private fun next(): Char {
            if (pos >= src.length) fail("unexpected end of input")
            return src[pos++]
        }

        private fun expect(c: Char) {
            if (pos >= src.length || src[pos] != c) fail("expected '$c'")
            pos++
        }

        private fun fail(message: String): Nothing =
            throw JsonException("$message (at offset $pos)")
    }
}
