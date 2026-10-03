package com.del.bitsay.core.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MiniJsonTest {

    @Test
    fun `parses a flat object`() {
        val value = MiniJson.parse("""{"a":1,"b":"two","c":true,"d":null}""") as Map<*, *>
        assertEquals(1L, value["a"])
        assertEquals("two", value["b"])
        assertEquals(true, value["c"])
        assertNull(value["d"])
    }

    @Test
    fun `parses nesting, arrays and whitespace`() {
        val value = MiniJson.parse(
            """
            {
              "items": [ {"id": 1}, {"id": 2} ],
              "nested": { "deep": [1, 2, 3] }
            }
            """.trimIndent(),
        ) as Map<*, *>

        val items = value["items"] as List<*>
        assertEquals(2, items.size)
        assertEquals(2L, (items[1] as Map<*, *>)["id"])
        assertEquals(listOf(1L, 2L, 3L), ((value["nested"] as Map<*, *>)["deep"] as List<*>))
    }

    @Test
    fun `handles every escape sequence`() {
        // Written as a regular Kotlin string so the JSON escapes stay literal.
        val json = "{\"s\":\"quote:\\\" backslash:\\\\ slash:\\/ nl:\\n tab:\\t " +
            "cr:\\r bs:\\b ff:\\f uni:\\u4e2d\"}"
        val value = MiniJson.parse(json) as Map<*, *>
        assertEquals(
            "quote:\" backslash:\\ slash:/ nl:\n tab:\t cr:\r bs:\b ff:\u000C uni:中",
            value["s"],
        )
    }

    @Test
    fun `keeps big integers exact`() {
        val value = MiniJson.parse("""{"t":1759312345678}""") as Map<*, *>
        assertEquals(1759312345678L, value["t"])
    }

    @Test
    fun `parses negative and fractional numbers`() {
        val value = MiniJson.parse("""{"a":-12,"b":1.5,"c":1e3}""") as Map<*, *>
        assertEquals(-12L, value["a"])
        assertEquals(1.5, value["b"] as Double, 0.0)
        assertEquals(1000.0, value["c"] as Double, 0.0)
    }

    @Test
    fun `parses empty containers`() {
        assertEquals(emptyMap<String, Any?>(), MiniJson.parse("{}"))
        assertEquals(emptyList<Any?>(), MiniJson.parse("[]"))
    }

    @Test(expected = MiniJson.JsonException::class)
    fun `rejects trailing content`() {
        MiniJson.parse("""{"a":1} garbage""")
    }

    @Test(expected = MiniJson.JsonException::class)
    fun `rejects truncated input`() {
        MiniJson.parse("""{"a":""")
    }

    @Test(expected = MiniJson.JsonException::class)
    fun `rejects unknown escapes`() {
        MiniJson.parse("""{"a":"\q"}""")
    }

    @Test
    fun `escape round trips through parse`() {
        val nasty = "line1\nline2\ttab \"quoted\" \\back\\ 中文 😀 \u0001"
        val encoded = MiniJson.escape(nasty)
        assertTrue(encoded.startsWith("\""))
        val decoded = MiniJson.parse("""{"v":$encoded}""") as Map<*, *>
        assertEquals(nasty, decoded["v"])
    }
}
