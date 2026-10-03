package com.del.bitsay.core.backup

import com.del.bitsay.core.model.Item
import com.del.bitsay.core.model.Kind
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupArchiveTest {

    private val payload = BackupCodec.encode(
        Backup(
            exportedAt = 1_759_312_000_000,
            items = (1..200).map {
                Item(
                    id = it.toLong(),
                    kind = if (it % 2 == 0) Kind.TODO else Kind.NOTE,
                    text = "第 $it 条：今天天气不错，去公园走走吧 🌿",
                    createdAt = 1_759_310_000_000 + it,
                    updatedAt = 1_759_310_000_000 + it,
                )
            },
        ),
    )

    @Test
    fun `a round trip returns the payload byte for byte`() {
        assertArrayEquals(payload, BackupArchive.decompress(BackupArchive.compress(payload)))
    }

    @Test
    fun `compression actually shrinks a realistic payload`() {
        val compressed = BackupArchive.compress(payload).size

        assertTrue(
            "expected a real reduction, got ${payload.size} -> $compressed",
            compressed < payload.size / 3,
        )
    }

    @Test
    fun `the stream is recognised by its gzip magic`() {
        assertTrue(BackupArchive.isCompressed(BackupArchive.compress(payload)))
    }

    @Test
    fun `the decompressed payload still decodes to the same notes`() {
        val restored = BackupCodec.decode(BackupArchive.decompress(BackupArchive.compress(payload)))

        assertTrue(restored.items.first().text.contains("🌿"))
        assertTrue(restored.items.any { it.isTodo })
    }

    @Test(expected = BackupException::class)
    fun `an empty file is rejected instead of crashing the gunzip`() {
        BackupArchive.decompress(ByteArray(0))
    }

    @Test(expected = BackupException::class)
    fun `a file that is not gzip is rejected`() {
        BackupArchive.decompress("not a backup".toByteArray())
    }

    @Test(expected = BackupException::class)
    fun `a corrupted stream fails loudly rather than importing junk`() {
        val broken = BackupArchive.compress(payload)
        // Damage the deflate body, past the 10-byte gzip header.
        for (i in 12 until minOf(broken.size, 40)) broken[i] = 0x00

        BackupArchive.decompress(broken)
    }

    @Test
    fun `a two byte file is not mistaken for gzip`() {
        assertFalse(BackupArchive.isCompressed(byteArrayOf(0x1F)))
        assertFalse(BackupArchive.isCompressed("{}".toByteArray()))
    }
}
