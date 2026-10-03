package com.del.bitsay.core.backup

import android.content.Context
import android.net.Uri
import com.del.bitsay.core.repo.ItemRepository
import com.del.bitsay.core.repo.ImportResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Backup / restore use-cases. The encoding itself lives in [BackupCodec] (pure, tested);
 * this class only adds the repository and the file plumbing.
 */
class BackupManager(
    private val repository: ItemRepository,
    private val appVersion: String,
    private val clock: () -> Long = System::currentTimeMillis,
    private val zone: ZoneId = ZoneId.systemDefault(),
) {

    /** Snapshot the whole database and encode it. */
    suspend fun export(): ExportPayload {
        val items = repository.all()
        val payload = BackupCodec.encode(
            Backup(
                appVersion = appVersion,
                exportedAt = clock(),
                items = items,
            ),
        )
        return ExportPayload(payload = payload, count = items.size)
    }

    suspend fun import(payload: ByteArray, replace: Boolean): ImportResult {
        val backup = BackupCodec.decode(payload)
        return repository.restore(backup.items, replace)
    }

    /** Reads and validates without touching the database — used to size the confirm dialog. */
    fun peek(payload: ByteArray): Backup = BackupCodec.decode(payload)

    /**
     * `.bitsay.gz` rather than a bare `.bitsay`: the payload really is gzip, and Android maps
     * `.gz` to a real MIME type. That is what lets the import picker filter the list down to
     * backup files — an unknown extension resolves to `application/octet-stream` and would show
     * every unrelated file on the phone.
     */
    fun suggestedFileName(): String {
        val stamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmm")
            .withZone(zone)
            .format(Instant.ofEpochMilli(clock()))
        return "bitsay-$stamp.bitsay.gz"
    }
}

/** Result of [BackupManager.export]: the encoded payload plus how many rows it holds. */
data class ExportPayload(val payload: ByteArray, val count: Int)

/** Thin Storage-Access-Framework glue: no permissions, the user picks the file. */
object BackupFiles {

    /** The export picker's suggested type; the payload really is gzip. */
    const val MIME = "application/gzip"

    /**
     * Only `.gz` files. Everything this app writes lands in that bucket, so the import picker
     * shows backups instead of the whole Downloads folder — an unknown extension would resolve
     * to `application/octet-stream` and match every unrelated file on the phone.
     */
    val IMPORT_MIME_TYPES = arrayOf("application/gzip")

    suspend fun write(context: Context, uri: Uri, bytes: ByteArray) = withContext(Dispatchers.IO) {
        context.contentResolver.openOutputStream(uri, "wt")?.use { out ->
            out.write(bytes)
            out.flush()
        } ?: error("无法写入所选文件")
    }

    suspend fun read(context: Context, uri: Uri): ByteArray = withContext(Dispatchers.IO) {
        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: error("无法读取所选文件")
    }
}
