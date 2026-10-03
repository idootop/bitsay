package com.del.bitsay.core.backup

import com.del.bitsay.core.model.Item
import com.del.bitsay.core.model.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupCodecTest {

    private val sample = listOf(
        Item(
            id = 1,
            kind = Kind.NOTE,
            text = "第一行\n第二行 \"引号\" 与 \\反斜杠\\",
            createdAt = 1_759_310_000_000,
            updatedAt = 1_759_310_600_000,
        ),
        Item(
            id = 2,
            kind = Kind.TODO,
            text = "买牛奶 🥛",
            done = true,
            createdAt = 1_759_311_000_000,
            updatedAt = 1_759_312_000_000,
            doneAt = 1_759_312_000_000,
        ),
    )

    @Test
    fun `round trips every field`() {
        val json = BackupCodec.encode(Backup(appVersion = "1.0.0", exportedAt = 42, items = sample))
        val decoded = BackupCodec.decode(json)

        assertEquals(BackupCodec.APP_ID, decoded.app)
        assertEquals(BackupCodec.SCHEMA, decoded.schema)
        assertEquals("1.0.0", decoded.appVersion)
        assertEquals(42L, decoded.exportedAt)
        assertEquals(sample, decoded.items)
    }

    @Test
    fun `encoded document is valid json with the documented shape`() {
        val json = BackupCodec.encode(Backup(appVersion = "1.0.0", exportedAt = 42, items = sample))
        val root = MiniJson.parse(json) as Map<*, *>

        assertEquals("bitsay", root["app"])
        assertEquals(1L, root["schema"])
        assertEquals(2L, root["count"])
        val first = (root["items"] as List<*>).first() as Map<*, *>
        assertEquals("note", first["kind"])
        assertEquals(1L, first["id"])
        assertNull(first["doneAt"])

        val second = (root["items"] as List<*>)[1] as Map<*, *>
        assertEquals("todo", second["kind"])
        assertEquals(true, second["done"])
        assertEquals(1_759_312_000_000L, second["doneAt"])
    }

    @Test
    fun `an export with no records still decodes`() {
        val decoded = BackupCodec.decode(BackupCodec.encode(Backup(exportedAt = 1)))
        assertTrue(decoded.items.isEmpty())
    }

    @Test
    fun `tolerates hand written kind values and missing optional fields`() {
        val json = """
            {
              "app": "bitsay",
              "items": [
                {"text": "by name", "kind": "todo"},
                {"text": "by number", "kind": 1, "done": 1},
                {"text": "no kind at all"}
              ]
            }
        """.trimIndent()

        val decoded = BackupCodec.decode(json)
        assertEquals(Kind.TODO, decoded.items[0].kind)
        assertEquals(Kind.TODO, decoded.items[1].kind)
        assertTrue(decoded.items[1].done)
        assertEquals(Kind.NOTE, decoded.items[2].kind)
        // updatedAt falls back to createdAt
        assertEquals(decoded.items[2].createdAt, decoded.items[2].updatedAt)
    }

    @Test
    fun `drops entries without usable text`() {
        val json = """{"items":[{"id":1},{"id":2,"text":"   "},{"id":3,"text":"ok"}]}"""
        val decoded = BackupCodec.decode(json)
        assertEquals(listOf("ok"), decoded.items.map { it.text })
    }

    @Test(expected = MiniJson.JsonException::class)
    fun `rejects a non object document`() {
        BackupCodec.decode("[1,2,3]")
    }

    @Test(expected = MiniJson.JsonException::class)
    fun `rejects a document without items`() {
        BackupCodec.decode("""{"app":"bitsay"}""")
    }

    @Test(expected = MiniJson.JsonException::class)
    fun `rejects a document whose items is not an array`() {
        BackupCodec.decode("""{"items":{"a":1}}""")
    }

    @Test(expected = MiniJson.JsonException::class)
    fun `rejects a backup from a newer schema`() {
        BackupCodec.decode("""{"schema":99,"items":[]}""")
    }
}
