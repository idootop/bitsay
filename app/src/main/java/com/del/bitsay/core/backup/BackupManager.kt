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

    /** Snapshot the whole database as a pretty-printed JSON document. */
    suspend fun export(): ExportPayload {
        val items = repository.all()
        val json = BackupCodec.encode(
            Backup(
                appVersion = appVersion,
                exportedAt = clock(),
                items = items,
            ),
        )
        return ExportPayload(json = json, count = items.size)
    }

    suspend fun import(json: String, replace: Boolean): ImportResult {
        val backup = BackupCodec.decode(json)
        return repository.restore(backup.items, replace)
    }

    /** Reads and validates without touching the database — used to size the confirm dialog. */
    fun peek(json: String): Backup = BackupCodec.decode(json)

    fun suggestedFileName(): String {
        val stamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmm")
            .withZone(zone)
            .format(Instant.ofEpochMilli(clock()))
        return "bitsay-$stamp.json"
    }
}

/** Result of [BackupManager.export]. */
data class ExportPayload(val json: String, val count: Int)

/** Thin Storage-Access-Framework glue: no permissions, the user picks the file. */
object BackupFiles {

    const val MIME = "application/json"

    suspend fun write(context: Context, uri: Uri, text: String) = withContext(Dispatchers.IO) {
        context.contentResolver.openOutputStream(uri, "wt")?.use { out ->
            out.write(text.toByteArray(Charsets.UTF_8))
            out.flush()
        } ?: error("无法写入所选文件")
    }

    suspend fun read(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
        context.contentResolver.openInputStream(uri)?.use { input ->
            input.readBytes().toString(Charsets.UTF_8)
        } ?: error("无法读取所选文件")
    }
}
