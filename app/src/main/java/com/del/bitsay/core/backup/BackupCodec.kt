package com.del.bitsay.core.backup

import com.del.bitsay.core.model.Item
import com.del.bitsay.core.model.Kind

/** In-memory representation of a `.json` backup file. */
data class Backup(
    val schema: Int = BackupCodec.SCHEMA,
    val app: String = BackupCodec.APP_ID,
    val appVersion: String = "",
    val exportedAt: Long = 0L,
    val items: List<Item> = emptyList(),
)

/**
 * Serialises / deserialises the backup payload. Pure Kotlin — fully unit tested, no I/O here.
 *
 * ```json
 * {
 *   "app": "bitsay",
 *   "schema": 1,
 *   "appVersion": "1.0.0",
 *   "exportedAt": 1759312000000,
 *   "count": 2,
 *   "items": [
 *     { "id": 1, "kind": "note", "text": "买牛奶", "done": false,
 *       "createdAt": 1759310000000, "updatedAt": 1759310000000, "doneAt": null }
 *   ]
 * }
 * ```
 */
object BackupCodec {

    const val SCHEMA = 1
    const val APP_ID = "bitsay"

    fun encode(backup: Backup): String {
        val sb = StringBuilder(256 + backup.items.size * 160)
        sb.append("{\n")
        sb.append("  \"app\": ").append(MiniJson.escape(backup.app)).append(",\n")
        sb.append("  \"schema\": ").append(backup.schema).append(",\n")
        sb.append("  \"appVersion\": ").append(MiniJson.escape(backup.appVersion)).append(",\n")
        sb.append("  \"exportedAt\": ").append(backup.exportedAt).append(",\n")
        sb.append("  \"count\": ").append(backup.items.size).append(",\n")
        sb.append("  \"items\": [")
        backup.items.forEachIndexed { index, item ->
            if (index > 0) sb.append(',')
            sb.append("\n    {")
            sb.append("\"id\": ").append(item.id).append(", ")
            sb.append("\"kind\": ").append(MiniJson.escape(item.kind.name.lowercase())).append(", ")
            sb.append("\"text\": ").append(MiniJson.escape(item.text)).append(", ")
            sb.append("\"done\": ").append(item.done).append(", ")
            sb.append("\"createdAt\": ").append(item.createdAt).append(", ")
            sb.append("\"updatedAt\": ").append(item.updatedAt).append(", ")
            sb.append("\"doneAt\": ").append(item.doneAt ?: "null")
            sb.append('}')
        }
        if (backup.items.isNotEmpty()) sb.append("\n  ")
        sb.append("]\n}\n")
        return sb.toString()
    }

    fun decode(text: String): Backup {
        val root = MiniJson.parse(text)
        val map = root as? Map<*, *>
            ?: throw MiniJson.JsonException("备份文件格式不正确：顶层不是一个 JSON 对象")

        val app = map.string("app") ?: APP_ID
        val schema = (map.long("schema") ?: SCHEMA.toLong()).toInt()
        if (schema > SCHEMA) {
            throw MiniJson.JsonException("备份文件来自更新的版本（schema=$schema），请先升级 App")
        }

        val rawItems = map["items"]
            ?: throw MiniJson.JsonException("备份文件里没有 items 字段")
        val list = rawItems as? List<*>
            ?: throw MiniJson.JsonException("备份文件里 items 不是数组")

        val items = list.mapNotNull { element ->
            (element as? Map<*, *>)?.let { decodeItem(it) }
        }

        return Backup(
            schema = schema,
            app = app,
            appVersion = map.string("appVersion").orEmpty(),
            exportedAt = map.long("exportedAt") ?: 0L,
            items = items,
        )
    }

    private fun decodeItem(map: Map<*, *>): Item? {
        val text = map.string("text") ?: return null
        if (text.isBlank()) return null
        val createdAt = map.long("createdAt") ?: 0L
        val updatedAt = map.long("updatedAt") ?: createdAt
        val kind = when (val k = map["kind"]) {
            is String -> if (k.equals("todo", ignoreCase = true)) Kind.TODO else Kind.NOTE
            is Number -> Kind.ofCode(k.toInt())
            else -> Kind.NOTE
        }
        return Item(
            id = map.long("id") ?: 0L,
            kind = kind,
            text = text,
            done = map.bool("done") ?: false,
            createdAt = createdAt,
            updatedAt = updatedAt,
            doneAt = map.long("doneAt"),
        )
    }

    private fun Map<*, *>.string(key: String): String? = this[key] as? String

    private fun Map<*, *>.long(key: String): Long? = when (val v = this[key]) {
        is Long -> v
        is Double -> v.toLong()
        is Number -> v.toLong()
        is String -> v.toLongOrNull()
        else -> null
    }

    private fun Map<*, *>.bool(key: String): Boolean? = when (val v = this[key]) {
        is Boolean -> v
        is Number -> v.toInt() != 0
        is String -> v.equals("true", ignoreCase = true) || v == "1"
        else -> null
    }
}
