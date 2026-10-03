package com.del.bitsay.core.backup

import com.del.bitsay.core.model.Item
import com.del.bitsay.core.model.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupCodecTest {

    private val sample = listOf(
        Item(
            id = 1,
            kind = Kind.NOTE,
            text = "第一行\n第二行 \"引号\" 与 \\反斜杠\\ 和制表\t符",
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
        Item(
            id = 3,
            kind = Kind.NOTE,
            // The common case: never edited, never ticked, so nothing beyond the defaults.
            text = "只有创建时间",
            createdAt = 1_759_313_000_000,
            updatedAt = 1_759_313_000_000,
        ),
    )

    @Test
    fun `round trips every field exactly`() {
        val decoded = BackupCodec.decode(BackupCodec.encode(Backup(exportedAt = 42, items = sample)))

        assertEquals(BackupCodec.APP_ID, decoded.app)
        assertEquals(BackupCodec.SCHEMA, decoded.schema)
        assertEquals(42L, decoded.exportedAt)
        assertEquals(sample, decoded.items)
    }

    @Test
    fun `round trips an empty backup`() {
        val decoded = BackupCodec.decode(BackupCodec.encode(Backup(exportedAt = 7)))

        assertEquals(7L, decoded.exportedAt)
        assertTrue(decoded.items.isEmpty())
    }

    @Test
    fun `survives unicode, emoji and control characters`() {
        val nasty = listOf(
            Item(
                id = 9,
                kind = Kind.NOTE,
                text = "中文 🌿😀 \u3000全角空格 \u0001 控制符 \n\n 空行",
                createdAt = 1,
                updatedAt = 1,
            ),
        )

        assertEquals(nasty, BackupCodec.decode(BackupCodec.encode(Backup(items = nasty))).items)
    }

    @Test
    fun `survives sparse and non sequential ids`() {
        val odd = listOf(
            Item(id = 5, kind = Kind.NOTE, text = "a", createdAt = 100, updatedAt = 100),
            Item(id = 900_000, kind = Kind.NOTE, text = "b", createdAt = 100, updatedAt = 100),
            Item(id = 12, kind = Kind.NOTE, text = "c", createdAt = 100, updatedAt = 100),
        )

        assertEquals(odd, BackupCodec.decode(BackupCodec.encode(Backup(items = odd))).items)
    }

    @Test
    fun `survives timestamps that jump backwards`() {
        val odd = listOf(
            Item(id = 1, kind = Kind.NOTE, text = "a", createdAt = 5_000, updatedAt = 5_000),
            Item(id = 2, kind = Kind.NOTE, text = "b", createdAt = 1_000, updatedAt = 1_000),
        )

        assertEquals(odd, BackupCodec.decode(BackupCodec.encode(Backup(items = odd))).items)
    }

    @Test
    fun `entries with blank text are dropped`() {
        val withBlank = listOf(
            Item(id = 1, kind = Kind.NOTE, text = "   ", createdAt = 1, updatedAt = 1),
            Item(id = 2, kind = Kind.NOTE, text = "ok", createdAt = 1, updatedAt = 1),
        )

        val decoded = BackupCodec.decode(BackupCodec.encode(Backup(items = withBlank)))
        assertEquals(listOf("ok"), decoded.items.map { it.text })
    }

    @Test
    fun `a very long note round trips byte for byte`() {
        val long = "开" + "很长的正文内容。".repeat(2_000) + "尾"
        val items = listOf(Item(id = 1, kind = Kind.NOTE, text = long, createdAt = 1, updatedAt = 1))

        val decoded = BackupCodec.decode(BackupCodec.encode(Backup(items = items)))
        assertEquals(long, decoded.items.single().text)
    }

    // ------------------------------------------------------------------ the point of the format

    @Test
    fun `the payload is barely larger than the text it carries`() {
        val items = (1..2_000).map {
            Item(
                id = it.toLong(),
                kind = Kind.NOTE,
                text = "第 $it 条：今天要确认下周三之前的会议安排，顺便问一下价格。",
                createdAt = 1_790_900_000_000 + it,
                updatedAt = 1_790_900_000_000 + it,
            )
        }
        val textBytes = items.sumOf { it.text.toByteArray(Charsets.UTF_8).size }
        val encoded = BackupCodec.encode(Backup(items = items)).size

        // Ids, timestamps and flags cost a handful of bytes per row. Anything approaching 2x
        // would mean the container is wasting space again.
        assertTrue(
            "encoded $encoded vs text $textBytes — container overhead is too high",
            encoded < textBytes * 1.25,
        )
    }

    @Test
    fun `the payload is much smaller than an equivalent JSON document`() {
        val items = (1..500).map {
            Item(
                id = it.toLong(),
                kind = Kind.NOTE,
                text = "第 $it 条：今天要确认下周三之前的会议安排。",
                createdAt = 1_790_900_000_000 + it,
                updatedAt = 1_790_900_000_000 + it,
            )
        }
        // Bytes on both sides: Chinese notes are 3 bytes per character in UTF-8, so comparing a
        // Kotlin String's length against a ByteArray's size would flatter the binary format.
        val jsonBytes = items.joinToString(",", "[", "]") {
            """{"id":${it.id},"kind":"note","text":"${it.text}","createdAt":${it.createdAt}}"""
        }.toByteArray(Charsets.UTF_8).size
        val encoded = BackupCodec.encode(Backup(items = items)).size

        // Measured ~57% on realistic short notes; the per-row saving is the keys and the
        // 13-digit timestamp that varint deltas replace.
        assertTrue(
            "binary $encoded vs json $jsonBytes (${encoded * 100 / jsonBytes}%)",
            encoded < jsonBytes * 7 / 10,
        )
    }

    // ------------------------------------------------------------------ corruption

    @Test(expected = BackupFormatException::class)
    fun `rejects a foreign file`() {
        BackupCodec.decode("{\"app\":\"something-else\"}".toByteArray())
    }

    @Test(expected = BackupFormatException::class)
    fun `rejects an empty file`() {
        BackupCodec.decode(ByteArray(0))
    }

    @Test(expected = BackupFormatException::class)
    fun `rejects a truncated payload instead of importing half a database`() {
        val full = BackupCodec.encode(Backup(items = sample))

        BackupCodec.decode(full.copyOfRange(0, full.size - 12))
    }

    @Test(expected = BackupFormatException::class)
    fun `rejects a header claiming more rows than the file can hold`() {
        val full = BackupCodec.encode(Backup(items = sample))
        // Byte 4 is the record count (a single byte for small payloads); inflate it absurdly.
        full[4] = 0x7F

        BackupCodec.decode(full)
    }

    @Test
    fun `rejects a future schema`() {
        val encoded = BackupCodec.encode(Backup(schema = BackupCodec.SCHEMA + 1, items = sample))

        val error = runCatching { BackupCodec.decode(encoded) }.exceptionOrNull()

        assertTrue(error is BackupFormatException)
        assertTrue(error!!.message!!.contains("更新的版本"))
    }

    @Test
    fun `a shorter backup really is shorter`() {
        val a = BackupCodec.encode(Backup(items = sample))
        val b = BackupCodec.encode(Backup(items = sample.drop(2)))

        assertNotEquals(a.size, b.size)
    }
}
