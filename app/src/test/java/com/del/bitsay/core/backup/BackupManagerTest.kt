package com.del.bitsay.core.backup

import com.del.bitsay.core.model.Kind
import com.del.bitsay.core.repo.FakeItemStore
import com.del.bitsay.core.repo.ItemRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class BackupManagerTest {

    private var now = 1_759_312_000_000L
    private val store = FakeItemStore()
    private val repository = ItemRepository(store, { now }, UnconfinedTestDispatcher())
    private val manager = BackupManager(
        repository = repository,
        appVersion = "1.0.0",
        clock = { now },
        zone = ZoneId.of("Asia/Shanghai"),
    )

    @Test
    fun `export then import into an empty database reproduces the data`() = runTest {
        repository.add(Kind.NOTE, "笔记一")
        repository.add(Kind.TODO, "待办一")
        val todoId = repository.todos.value.single().id
        repository.setDone(todoId, true)

        val payload = manager.export()
        val original = store.snapshot().sortedBy { it.id }

        val freshStore = FakeItemStore()
        val freshRepository = ItemRepository(freshStore, { now }, UnconfinedTestDispatcher())
        val freshManager = BackupManager(freshRepository, "1.0.0", { now })

        val result = freshManager.import(payload.payload, replace = true)

        assertEquals(2, result.total)
        assertEquals(original, freshStore.snapshot().sortedBy { it.id })
    }

    @Test
    fun `export reports the number of records written`() = runTest {
        repository.add(Kind.NOTE, "a")
        repository.add(Kind.NOTE, "b")
        repository.add(Kind.TODO, "c")

        val payload = manager.export()

        assertEquals(3, payload.count)
        assertEquals(3, BackupCodec.decode(payload.payload).items.size)
    }

    @Test
    fun `peek validates without touching the database`() = runTest {
        repository.add(Kind.NOTE, "keep me")
        val payload = manager.export().payload

        val preview = manager.peek(payload)

        assertEquals(1, preview.items.size)
        assertEquals(1, store.count(Kind.NOTE))
    }

    @Test
    fun `merge import never loses local data`() = runTest {
        repository.add(Kind.NOTE, "local")
        manager.import(BackupCodec.encode(Backup(items = listOf(
                com.del.bitsay.core.model.Item(
                    id = 500,
                    kind = Kind.NOTE,
                    text = "from another phone",
                    createdAt = 1,
                    updatedAt = 2,
                ),
            ))), replace = false)

        val texts = store.snapshot().map { it.text }.toSet()
        assertEquals(setOf("local", "from another phone"), texts)
    }

    @Test
    fun `suggested file name is stable and sortable`() {
        now = LocalDateTime.of(2026, 10, 1, 12, 0)
            .atZone(ZoneId.of("Asia/Shanghai"))
            .toInstant()
            .toEpochMilli()

        assertEquals("bitsay-20261001-1200.bitsay.gz", manager.suggestedFileName())
    }
}
