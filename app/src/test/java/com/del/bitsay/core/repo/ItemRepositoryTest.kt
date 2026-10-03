package com.del.bitsay.core.repo

import com.del.bitsay.core.model.Item
import com.del.bitsay.core.model.Kind
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ItemRepositoryTest {

    private var now = 1_000_000L
    private val store = FakeItemStore()
    private val repository = ItemRepository(
        store = store,
        clock = { now },
        dispatcher = UnconfinedTestDispatcher(),
    )

    @Test
    fun `add trims text and stamps both timestamps`() = runTest {
        val id = repository.add(Kind.NOTE, "  买牛奶  ")

        assertNotNull(id)
        val saved = store.findById(id!!)!!
        assertEquals("买牛奶", saved.text)
        assertEquals(1_000_000L, saved.createdAt)
        assertEquals(1_000_000L, saved.updatedAt)
        assertNull(saved.doneAt)
        assertFalse(saved.done)
    }

    @Test
    fun `add refuses blank text`() = runTest {
        assertNull(repository.add(Kind.NOTE, "   "))
        assertNull(repository.add(Kind.TODO, "\n\t"))
        assertEquals(0, store.count(Kind.NOTE))
    }

    @Test
    fun `add caps runaway text`() = runTest {
        val huge = "x".repeat(ItemRepository.MAX_TEXT_LENGTH + 500)
        val id = repository.add(Kind.NOTE, huge)!!
        assertEquals(ItemRepository.MAX_TEXT_LENGTH, store.findById(id)!!.text.length)
    }

    @Test
    fun `notes and todos stay in separate lists`() = runTest {
        repository.add(Kind.NOTE, "a note")
        repository.add(Kind.TODO, "a todo")

        assertEquals(1, repository.notes.value.size)
        assertEquals(1, repository.todos.value.size)
        assertEquals(Kind.NOTE, repository.notes.value.single().kind)
        assertEquals(Kind.TODO, repository.todos.value.single().kind)
    }

    @Test
    fun `updateText bumps updatedAt but never createdAt`() = runTest {
        val id = repository.add(Kind.NOTE, "first")!!
        now = 2_000_000L

        assertTrue(repository.updateText(id, "  second  "))

        val saved = store.findById(id)!!
        assertEquals("second", saved.text)
        assertEquals(1_000_000L, saved.createdAt)
        assertEquals(2_000_000L, saved.updatedAt)
    }

    @Test
    fun `updateText is a no-op when nothing changed`() = runTest {
        val id = repository.add(Kind.NOTE, "same")!!
        now = 2_000_000L

        assertFalse(repository.updateText(id, "same"))
        assertEquals(1_000_000L, store.findById(id)!!.updatedAt)
    }

    @Test
    fun `updateText refuses to blank out an existing note`() = runTest {
        val id = repository.add(Kind.NOTE, "keep me")!!
        assertFalse(repository.updateText(id, "   "))
        assertEquals("keep me", store.findById(id)!!.text)
    }

    @Test
    fun `setDone records and clears doneAt`() = runTest {
        val id = repository.add(Kind.TODO, "walk the dog")!!

        now = 3_000_000L
        assertTrue(repository.setDone(id, true))
        assertEquals(3_000_000L, store.findById(id)!!.doneAt)
        assertTrue(store.findById(id)!!.done)

        now = 4_000_000L
        assertTrue(repository.setDone(id, false))
        assertNull(store.findById(id)!!.doneAt)
        assertFalse(store.findById(id)!!.done)
    }

    @Test
    fun `setDone refuses to tick a note`() = runTest {
        val id = repository.add(Kind.NOTE, "just a note")!!
        assertFalse(repository.setDone(id, true))
        assertFalse(store.findById(id)!!.done)
    }

    @Test
    fun `toggleDone flips the state`() = runTest {
        val id = repository.add(Kind.TODO, "flip")!!
        assertTrue(repository.toggleDone(id))
        assertTrue(store.findById(id)!!.done)
        assertTrue(repository.toggleDone(id))
        assertFalse(store.findById(id)!!.done)
    }

    @Test
    fun `delete removes the row and refreshes the cache`() = runTest {
        val id = repository.add(Kind.NOTE, "bye")!!
        assertTrue(repository.delete(id))
        assertTrue(repository.notes.value.isEmpty())
        assertFalse(repository.delete(id))
    }

    @Test
    fun `open todos sort above finished ones`() = runTest {
        val first = repository.add(Kind.TODO, "done one")!!
        now = 2_000_000L
        repository.add(Kind.TODO, "open one")
        repository.setDone(first, true)

        assertEquals(listOf("open one", "done one"), repository.todos.value.map { it.text })
    }

    // ------------------------------------------------------------------ ordering

    @Test
    fun `the list is ordered by creation time, newest first`() = runTest {
        repository.add(Kind.NOTE, "第一条")
        now = 2_000_000L
        repository.add(Kind.NOTE, "第二条")
        now = 3_000_000L
        repository.add(Kind.NOTE, "第三条")

        assertEquals(listOf("第三条", "第二条", "第一条"), repository.notes.value.map { it.text })
    }

    @Test
    fun `editing an entry does not move it up the list`() = runTest {
        val oldest = repository.add(Kind.NOTE, "先写的")!!
        now = 2_000_000L
        repository.add(Kind.NOTE, "后写的")
        now = 3_000_000L

        repository.updateText(oldest, "先写的（改过）")

        // Sorting by updatedAt would have promoted the edited note; creation order must not budge.
        assertEquals(listOf("后写的", "先写的（改过）"), repository.notes.value.map { it.text })
    }

    @Test
    fun `ticking a todo does not reorder the list either`() = runTest {
        repository.add(Kind.TODO, "先加的")
        now = 2_000_000L
        val newest = repository.add(Kind.TODO, "后加的")!!
        now = 3_000_000L

        repository.setDone(newest, true)

        // It sinks because it is done, but stays below the older entry within that group.
        assertEquals(listOf("先加的", "后加的"), repository.todos.value.map { it.text })
    }

    @Test
    fun `change version increments only on real writes`() = runTest {
        val before = repository.change.value.version
        repository.add(Kind.NOTE, "   ") // rejected
        assertEquals(before, repository.change.value.version)

        val id = repository.add(Kind.NOTE, "real")!!
        assertEquals(before + 1, repository.change.value.version)

        repository.updateText(id, "real") // no change
        assertEquals(before + 1, repository.change.value.version)

        repository.updateText(id, "changed")
        assertEquals(before + 2, repository.change.value.version)
    }

    // ------------------------------------------------------------------ change signal

    @Test
    fun `creating a note marks the change as having revealed a new row`() = runTest {
        assertEquals(0L, repository.change.value.insertedAt)

        repository.add(Kind.NOTE, "新建")

        val change = repository.change.value
        assertEquals(change.version, change.insertedAt)
    }

    @Test
    fun `editing a note does not mark it as an insert`() = runTest {
        val id = repository.add(Kind.NOTE, "新建")!!
        val insertedAt = repository.change.value.insertedAt

        now = 2_000_000L
        repository.updateText(id, "改一下")

        val change = repository.change.value
        assertEquals(insertedAt, change.insertedAt)
        assertTrue(change.version > change.insertedAt)
    }

    @Test
    fun `ticking a todo does not mark it as an insert`() = runTest {
        val id = repository.add(Kind.TODO, "待办")!!
        val insertedAt = repository.change.value.insertedAt
        val version = repository.change.value.version

        now = 2_000_000L
        repository.setDone(id, true)

        val change = repository.change.value
        assertTrue(change.version > version)
        assertEquals(insertedAt, change.insertedAt)
    }

    @Test
    fun `autosave only marks the very first keystroke of a new entry as an insert`() = runTest {
        val id = repository.saveDraft(0L, Kind.NOTE, "打第一个字")
        assertEquals(repository.change.value.version, repository.change.value.insertedAt)
        val insertedAt = repository.change.value.insertedAt

        now = 2_000_000L
        repository.saveDraft(id, Kind.NOTE, "继续打字")

        assertEquals(insertedAt, repository.change.value.insertedAt)
    }

    // ------------------------------------------------------------------ autosave

    @Test
    fun `saveDraft on a brand new entry creates the row and returns its id`() = runTest {
        val id = repository.saveDraft(id = 0L, kind = Kind.NOTE, text = "半句话")

        assertTrue(id > 0L)
        assertEquals("半句话", store.findById(id)!!.text)
        assertEquals(1, repository.notes.value.size)
    }

    @Test
    fun `saveDraft keeps updating the same row instead of inserting again`() = runTest {
        val id = repository.saveDraft(0L, Kind.NOTE, "一")
        now = 2_000_000L
        val again = repository.saveDraft(id, Kind.NOTE, "一句话")
        now = 3_000_000L
        val third = repository.saveDraft(again, Kind.NOTE, "一句话，写完")

        assertEquals(id, again)
        assertEquals(id, third)
        assertEquals(1, store.count(Kind.NOTE))
        assertEquals("一句话，写完", store.findById(id)!!.text)
        assertEquals(1_000_000L, store.findById(id)!!.createdAt)
        assertEquals(3_000_000L, store.findById(id)!!.updatedAt)
    }

    @Test
    fun `saveDraft with unchanged text does not touch the database`() = runTest {
        val id = repository.saveDraft(0L, Kind.NOTE, "稳定")
        val version = repository.change.value.version
        now = 5_000_000L

        assertEquals(id, repository.saveDraft(id, Kind.NOTE, "稳定"))

        assertEquals(version, repository.change.value.version)
        assertEquals(1_000_000L, store.findById(id)!!.updatedAt)
    }

    @Test
    fun `saveDraft of blank text never creates anything`() = runTest {
        assertEquals(0L, repository.saveDraft(0L, Kind.NOTE, "   "))
        assertEquals(0, store.count(Kind.NOTE))
    }

    @Test
    fun `saveDraft of blank text leaves an existing row alone`() = runTest {
        val id = repository.saveDraft(0L, Kind.NOTE, "别删我")
        now = 9_000_000L

        assertEquals(id, repository.saveDraft(id, Kind.NOTE, ""))

        assertEquals("别删我", store.findById(id)!!.text)
        assertEquals(1_000_000L, store.findById(id)!!.updatedAt)
    }

    @Test
    fun `saveDraft trims and caps like the other writes`() = runTest {
        val id = repository.saveDraft(0L, Kind.TODO, "   padded   ")
        assertEquals("padded", store.findById(id)!!.text)

        val huge = repository.saveDraft(id, Kind.TODO, "y".repeat(ItemRepository.MAX_TEXT_LENGTH + 9))
        assertEquals(ItemRepository.MAX_TEXT_LENGTH, store.findById(huge)!!.text.length)
    }


    // ------------------------------------------------------------------ big data

    @Test
    fun `list rows carry only a preview of a very long note`() = runTest {
        val long = "头" + "很长的正文".repeat(400) + "藏在最后的暗号"
        val id = repository.add(Kind.NOTE, long)!!

        val listed = repository.notes.value.single()
        assertEquals(ItemStore.PREVIEW_CHARS, listed.text.length)
        // …but the real row is untouched, which is what the editor and the backup read.
        assertEquals(long, store.findById(id)!!.text)
    }

    @Test
    fun `the backup still contains the whole note, not the preview`() = runTest {
        val long = "开" + "x".repeat(5_000) + "尾"
        repository.add(Kind.NOTE, long)

        assertEquals(long, repository.all().single().text)
    }

    @Test
    fun `search finds a match deep inside a long note, past the preview cut-off`() = runTest {
        val long = "头".repeat(400) + "藏在最后的暗号"
        repository.add(Kind.NOTE, long)
        repository.add(Kind.NOTE, "短笔记")

        val hits = repository.search(Kind.NOTE, "暗号")

        assertEquals(1, hits.size)
        // Results are previews too — the row still has to be cheap to hold in a list.
        assertEquals(ItemStore.PREVIEW_CHARS, hits.single().text.length)
    }

    @Test
    fun `search is scoped to one kind`() = runTest {
        repository.add(Kind.NOTE, "买牛奶")
        repository.add(Kind.TODO, "买牛奶")

        assertEquals(1, repository.search(Kind.NOTE, "买牛奶").size)
        assertEquals(1, repository.search(Kind.TODO, "买牛奶").size)
    }

    @Test
    fun `searching a blank string returns the whole list`() = runTest {
        repository.add(Kind.NOTE, "a")
        repository.add(Kind.NOTE, "b")

        assertEquals(2, repository.search(Kind.NOTE, "   ").size)
    }

    @Test
    fun `search with no matches returns nothing`() = runTest {
        repository.add(Kind.NOTE, "买牛奶")
        assertTrue(repository.search(Kind.NOTE, "不存在的词").isEmpty())
    }

    // ------------------------------------------------------------------ batch operations

    @Test
    fun `deleteMany removes every id in one go`() = runTest {
        val a = repository.add(Kind.NOTE, "a")!!
        val b = repository.add(Kind.NOTE, "b")!!
        val c = repository.add(Kind.NOTE, "c")!!

        assertEquals(2, repository.deleteMany(listOf(a, c)))

        assertEquals(listOf("b"), repository.notes.value.map { it.text })
        assertNull(store.findById(a))
        assertNotNull(store.findById(b))
        assertNull(store.findById(c))
    }

    @Test
    fun `deleteMany ignores ids that are already gone`() = runTest {
        val a = repository.add(Kind.NOTE, "a")!!
        repository.delete(a)

        assertEquals(0, repository.deleteMany(listOf(a, 9_999L)))
    }

    @Test
    fun `deleteMany with an empty selection does not touch the cache`() = runTest {
        repository.add(Kind.NOTE, "keep")
        val version = repository.change.value.version

        assertEquals(0, repository.deleteMany(emptyList()))

        assertEquals(version, repository.change.value.version)
        assertEquals(1, repository.notes.value.size)
    }

    @Test
    fun `setDoneMany ticks a whole selection and skips the notes in it`() = runTest {
        val todoA = repository.add(Kind.TODO, "a")!!
        val todoB = repository.add(Kind.TODO, "b")!!
        val note = repository.add(Kind.NOTE, "just a note")!!

        val changed = repository.setDoneMany(listOf(todoA, todoB, note), done = true)

        assertEquals(2, changed)
        assertTrue(store.findById(todoA)!!.done)
        assertTrue(store.findById(todoB)!!.done)
        assertFalse(store.findById(note)!!.done)
        assertEquals(1_000_000L, store.findById(todoA)!!.doneAt)
    }

    @Test
    fun `setDoneMany can untick as well`() = runTest {
        val id = repository.add(Kind.TODO, "a")!!
        repository.setDone(id, true)

        assertEquals(1, repository.setDoneMany(listOf(id), done = false))

        assertFalse(store.findById(id)!!.done)
        assertNull(store.findById(id)!!.doneAt)
    }

    @Test
    fun `setDoneMany reports zero when nothing actually changed`() = runTest {
        val id = repository.add(Kind.TODO, "a")!!
        repository.setDone(id, true)
        val version = repository.change.value.version

        assertEquals(0, repository.setDoneMany(listOf(id), done = true))

        assertEquals(version, repository.change.value.version)
    }

    // ------------------------------------------------------------------ autosave without reload

    @Test
    fun `saveDraft without reload bumps the change signal but leaves the cache alone`() = runTest {
        repository.add(Kind.NOTE, "existing")
        val cached = repository.notes.value
        val version = repository.change.value.version

        val id = repository.saveDraft(0L, Kind.NOTE, "typed while the editor was open", reload = false)

        assertTrue(id > 0L)
        // The list behind the editor is deliberately stale…
        assertEquals(cached, repository.notes.value)
        // …but the widget observer still hears about the write.
        assertTrue(repository.change.value.version > version)
        assertEquals(repository.change.value.version, repository.change.value.insertedAt)
    }

    @Test
    fun `leaving the editor reloads once and the list catches up`() = runTest {
        repository.add(Kind.NOTE, "existing")
        repository.saveDraft(0L, Kind.NOTE, "新写的", reload = false)
        assertEquals(1, repository.notes.value.size)

        repository.reload()

        assertEquals(2, repository.notes.value.size)
    }

    // ------------------------------------------------------------------ restore

    @Test
    fun `restore with replace wipes everything first`() = runTest {
        repository.add(Kind.NOTE, "local only")
        val incoming = listOf(
            Item(id = 7, kind = Kind.NOTE, text = "from backup", createdAt = 10, updatedAt = 20),
            Item(id = 8, kind = Kind.TODO, text = "todo from backup", createdAt = 10, updatedAt = 30),
        )

        val result = repository.restore(incoming, replace = true)

        assertEquals(2, result.inserted)
        assertEquals(0, result.updated)
        assertEquals(listOf("from backup", "todo from backup"), store.snapshot().map { it.text })
        assertEquals(listOf("from backup"), repository.notes.value.map { it.text })
    }

    @Test
    fun `restore merge keeps local copies that are newer`() = runTest {
        val localId = repository.add(Kind.NOTE, "local new text")!!
        now = 5_000_000L
        repository.updateText(localId, "local newer text")

        val result = repository.restore(
            listOf(
                // older than the local row -> must be ignored
                Item(id = localId, kind = Kind.NOTE, text = "stale", createdAt = 1, updatedAt = 2),
                // brand new -> must be inserted
                Item(id = 99, kind = Kind.NOTE, text = "brand new", createdAt = 1, updatedAt = 3),
            ),
            replace = false,
        )

        assertEquals(1, result.inserted)
        assertEquals(0, result.updated)
        assertTrue(store.snapshot().any { it.text == "local newer text" })
        assertTrue(store.snapshot().any { it.text == "brand new" })
        assertFalse(store.snapshot().any { it.text == "stale" })
    }

    @Test
    fun `restore merge overwrites local copies that are older`() = runTest {
        val localId = repository.add(Kind.NOTE, "old")!! // updatedAt = 1_000_000

        val result = repository.restore(
            listOf(
                Item(id = localId, kind = Kind.NOTE, text = "restored", createdAt = 1, updatedAt = 9_000_000),
            ),
            replace = false,
        )

        assertEquals(0, result.inserted)
        assertEquals(1, result.updated)
        assertEquals("restored", store.findById(localId)!!.text)
    }

    @Test
    fun `restore drops blank entries from a hand edited backup`() = runTest {
        val result = repository.restore(
            listOf(
                Item(id = 1, kind = Kind.NOTE, text = "  ", createdAt = 1, updatedAt = 1),
                Item(id = 2, kind = Kind.NOTE, text = "  keep  ", createdAt = 1, updatedAt = 1),
            ),
            replace = true,
        )

        assertEquals(1, result.total)
        assertEquals(listOf("keep"), store.snapshot().map { it.text })
    }
}
