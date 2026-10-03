package com.del.bitsay.core.backup

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream
import java.util.zip.Deflater
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * Wraps the binary backup payload in gzip.
 *
 * Compression is still the single biggest lever: the payload is mostly UTF-8 Chinese (up to 3
 * bytes per character) plus low-entropy ids and timestamp deltas, and deflate takes 10 000 notes
 * from 868 KB down to 121 KB. Binary encoding removed the redundancy the *container* was adding;
 * gzip removes the redundancy that is inherent to the text itself.
 */
object BackupArchive {

    private const val MAGIC_0 = 0x1F
    private const val MAGIC_1 = 0x8B

    fun compress(payload: ByteArray): ByteArray {
        val out = ByteArrayOutputStream(payload.size / 3 + 64)
        BestGzipOutputStream(out).use { it.write(payload) }
        return out.toByteArray()
    }

    fun decompress(bytes: ByteArray): ByteArray {
        if (bytes.isEmpty()) throw BackupException(BackupError.EMPTY)
        if (!isCompressed(bytes)) throw BackupException(BackupError.NOT_A_BACKUP)
        return try {
            GZIPInputStream(bytes.inputStream()).use { it.readBytes() }
        } catch (e: IOException) {
            throw BackupException(BackupError.CORRUPT)
        }
    }

    fun isCompressed(bytes: ByteArray): Boolean =
        bytes.size >= 2 &&
            (bytes[0].toInt() and 0xFF) == MAGIC_0 &&
            (bytes[1].toInt() and 0xFF) == MAGIC_1

    /**
     * `GZIPOutputStream` always deflates at the default level; a backup is written once and read
     * many times, so it is worth asking for the smaller stream. Worth ~3% on real note text —
     * small, but free at export time.
     */
    private class BestGzipOutputStream(out: OutputStream) : GZIPOutputStream(out, 16 * 1024) {
        init {
            def.setLevel(Deflater.BEST_COMPRESSION)
        }
    }
}
