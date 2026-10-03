package com.del.bitsay.core.backup

import com.del.bitsay.core.model.Item
import com.del.bitsay.core.model.Kind

/** In-memory representation of a backup. */
data class Backup(
    val schema: Int = BackupCodec.SCHEMA,
    val app: String = BackupCodec.APP_ID,
    val appVersion: String = "",
    val exportedAt: Long = 0L,
    val items: List<Item> = emptyList(),
)

/** Thrown when a file is not a readable bitsay backup. */
class BackupFormatException(message: String) : IllegalArgumentException(message)

/**
 * The backup payload: a purpose-built binary encoding, gzipped by [BackupArchive].
 *
 * ### Why binary instead of JSON
 *
 * Measured on 10 000 realistic Chinese notes (see PROGERSS §7):
 *
 * | container               | raw      | gzip-9   |
 * |-------------------------|----------|----------|
 * | note text alone (floor) |  835 KB  |  98.5 KB |
 * | compact JSON            | 1517 KB  | 178.7 KB |
 * | **this encoding**       |  868 KB  | 121.2 KB |
 *
 * Binary is 57% of JSON *before* compression and still **32% smaller after** it, because an
 * entropy coder cannot win back what the container already wasted: JSON re-spells `"createdAt":`
 * and a 13-digit timestamp on every record, while this format spends a single varint on a
 * timestamp *delta* and a single bit on `done`.
 *
 * The techniques are the boring, mature ones — varint, zigzag, delta — the same thing Protocol
 * Buffers does, minus the code generator and the runtime, for two flat columns of data.
 *
 * ### Layout (little-endian varints)
 *
 * ```
 * "BSB1"                     magic
 * uvarint                    schema
 * uvarint                    exportedAt (milliseconds since epoch)
 * uvarint                    item count
 * item*:
 *   uvarint                  id delta from the previous item
 *   uvarint                  flags: bit0 done, bit1 has updatedAt, bit2 has doneAt, bit3 todo
 *   svarint                  createdAt delta from the previous item's createdAt
 *   svarint                  updatedAt - createdAt   (only when bit1)
 *   svarint                  doneAt - createdAt      (only when bit2)
 *   uvarint length, bytes    UTF-8 text
 * ```
 *
 * Decoding validates every length against the bytes actually left, so a truncated or foreign
 * file fails loudly with [BackupFormatException] instead of half-importing a database.
 */
object BackupCodec {

    const val SCHEMA = 1
    const val APP_ID = "bitsay"

    private val MAGIC = byteArrayOf(
        'B'.code.toByte(),
        'S'.code.toByte(),
        'B'.code.toByte(),
        '1'.code.toByte(),
    )

    private const val FLAG_DONE = 1
    private const val FLAG_HAS_UPDATED = 1 shl 1
    private const val FLAG_HAS_DONE_AT = 1 shl 2
    private const val FLAG_TODO = 1 shl 3

    /** Refuse absurd lengths before allocating anything. */
    private const val MAX_TEXT_BYTES = 1 shl 20

    // ------------------------------------------------------------------ writing

    fun encode(backup: Backup): ByteArray {
        val out = ByteSink(128 + backup.items.size * 96)
        out.bytes(MAGIC)
        out.varint(backup.schema.toLong())
        out.varint(backup.exportedAt)
        out.varint(backup.items.size.toLong())

        var previousId = 0L
        var previousCreatedAt = 0L
        backup.items.forEach { item ->
            out.varint(item.id - previousId)
            previousId = item.id

            var flags = 0
            if (item.done) flags = flags or FLAG_DONE
            if (item.updatedAt != item.createdAt) flags = flags or FLAG_HAS_UPDATED
            if (item.doneAt != null) flags = flags or FLAG_HAS_DONE_AT
            if (item.kind == Kind.TODO) flags = flags or FLAG_TODO
            out.varint(flags.toLong())

            out.svarint(item.createdAt - previousCreatedAt)
            previousCreatedAt = item.createdAt
            if (item.updatedAt != item.createdAt) out.svarint(item.updatedAt - item.createdAt)
            item.doneAt?.let { out.svarint(it - item.createdAt) }

            val text = item.text.toByteArray(Charsets.UTF_8)
            out.varint(text.size.toLong())
            out.bytes(text)
        }
        return out.toByteArray()
    }

    // ------------------------------------------------------------------ reading

    fun decode(bytes: ByteArray): Backup {
        val input = ByteSource(bytes)
        val magic = input.bytes(MAGIC.size)
        if (!magic.contentEquals(MAGIC)) {
            throw BackupFormatException("这不是比特记的备份文件")
        }

        val schema = input.varint()
        if (schema > SCHEMA) {
            throw BackupFormatException("备份来自更新的版本（schema=$schema），请先升级 App")
        }
        val exportedAt = input.varint()
        val count = input.varint()
        if (count < 0L || count > input.remaining()) {
            throw BackupFormatException("备份文件已损坏：记录数 $count 与实际内容不符")
        }

        val items = ArrayList<Item>(count.toInt())
        var previousId = 0L
        var previousCreatedAt = 0L
        repeat(count.toInt()) {
            val id = previousId + input.varint()
            previousId = id
            val flags = input.varint().toInt()

            val createdAt = previousCreatedAt + input.svarint()
            previousCreatedAt = createdAt
            val updatedAt = if (flags and FLAG_HAS_UPDATED != 0) {
                createdAt + input.svarint()
            } else {
                createdAt
            }
            val doneAt = if (flags and FLAG_HAS_DONE_AT != 0) createdAt + input.svarint() else null

            val length = input.varint()
            if (length < 0L || length > MAX_TEXT_BYTES) {
                throw BackupFormatException("备份文件已损坏：文本长度 $length 不合理")
            }
            val text = input.utf8(length.toInt())

            // Same rule as the old JSON reader: entries with nothing in them are dropped.
            if (text.isNotBlank()) {
                items += Item(
                    id = id,
                    kind = if (flags and FLAG_TODO != 0) Kind.TODO else Kind.NOTE,
                    text = text,
                    done = flags and FLAG_DONE != 0,
                    createdAt = createdAt,
                    updatedAt = updatedAt,
                    doneAt = doneAt,
                )
            }
        }

        return Backup(
            schema = schema.toInt(),
            app = APP_ID,
            appVersion = "",
            exportedAt = exportedAt,
            items = items,
        )
    }
}

// ---------------------------------------------------------------------- byte plumbing

private class ByteSink(capacity: Int) {
    private var buffer = ByteArray(capacity.coerceAtLeast(64))
    private var size = 0

    fun varint(value: Long) {
        var v = value
        while (true) {
            val chunk = (v and 0x7F).toInt()
            v = v ushr 7
            if (v == 0L) {
                put(chunk)
                return
            }
            put(chunk or 0x80)
        }
    }

    /** Zigzag: small negative deltas stay small instead of always costing ten bytes. */
    fun svarint(value: Long) = varint((value shl 1) xor (value shr 63))

    fun bytes(value: ByteArray) {
        ensure(size + value.size)
        value.copyInto(buffer, size)
        size += value.size
    }

    private fun put(byte: Int) {
        ensure(size + 1)
        buffer[size++] = byte.toByte()
    }

    private fun ensure(needed: Int) {
        if (needed <= buffer.size) return
        var next = buffer.size
        while (next < needed) next = next shl 1
        buffer = buffer.copyOf(next)
    }

    fun toByteArray(): ByteArray = buffer.copyOf(size)
}

private class ByteSource(private val data: ByteArray) {
    private var pos = 0

    fun remaining(): Int = data.size - pos

    fun bytes(count: Int): ByteArray {
        if (count < 0 || count > remaining()) fail("文件被截断")
        val out = data.copyOfRange(pos, pos + count)
        pos += count
        return out
    }

    fun utf8(count: Int): String = String(bytes(count), Charsets.UTF_8)

    fun varint(): Long {
        var result = 0L
        var shift = 0
        while (true) {
            if (pos >= data.size) fail("文件被截断")
            val byte = data[pos++].toInt() and 0xFF
            result = result or ((byte and 0x7F).toLong() shl shift)
            if (byte and 0x80 == 0) return result
            shift += 7
            if (shift > 63) fail("数字编码异常")
        }
    }

    fun svarint(): Long {
        val raw = varint()
        return (raw ushr 1) xor -(raw and 1L)
    }

    private fun fail(why: String): Nothing = throw BackupFormatException("备份文件已损坏：$why")
}
