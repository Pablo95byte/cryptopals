package app.inknote.core.store

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.inknote.core.model.CanvasSize
import app.inknote.core.model.ExportTarget
import app.inknote.core.model.Note
import app.inknote.core.model.NoteId
import app.inknote.core.model.TextClip
import app.inknote.core.model.TextClipId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Lo smistamento a carte e la riemersione sull'archivio vero (D52). */
class SortingStoreTest {

    private val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { InkNoteStore.schema.create(it) }
    private val store = InkNoteStore.open(driver)

    private fun note(id: String, created: Long) =
        Note.empty(CanvasSize(360f, 640f), now = created, id = NoteId(id))
            .withTextClip(TextClip(TextClipId("t$id"), created, "idea $id"), now = created)

    @Test
    fun `la coda parte dalla nota più vecchia`() {
        store.save(note("nuova", 3_000L))
        store.save(note("vecchia", 1_000L))
        store.save(note("media", 2_000L))

        assertEquals(listOf("vecchia", "media", "nuova"), store.notesToSort().map { it.id.value })
        assertEquals(3L, store.notesToSortCount())
    }

    @Test
    fun `tenuta o mandata fuori, la nota esce dalla coda`() {
        store.save(note("tenuta", 1_000L).withSorted(5_000L))
        store.save(note("mandata", 2_000L).withExport(ExportTarget.SystemShare, 5_000L))
        store.save(note("in-coda", 3_000L))

        assertEquals(listOf("in-coda"), store.notesToSort().map { it.id.value })
    }

    @Test
    fun `buttata, la nota esce dalla coda`() {
        store.save(note("buttata", 1_000L))
        store.markDeleted(NoteId("buttata"), now = 2_000L)

        assertTrue(store.notesToSort().isEmpty())
    }

    @Test
    fun `una nota vuota non chiede di essere smistata`() {
        store.save(Note.empty(CanvasSize(360f, 640f), now = 1_000L, id = NoteId("vuota")))

        assertEquals(0L, store.notesToSortCount())
    }

    @Test
    fun `un salvataggio da una copia vecchia non rimette una nota in coda`() {
        val original = note("n", 1_000L)
        store.save(original.withSorted(5_000L))

        store.save(original)

        assertEquals(5_000L, store.note(NoteId("n"))!!.sortedAt)
        assertTrue(store.notesToSort().isEmpty())
    }

    @Test
    fun `smistata due volte vale il primo istante`() {
        store.save(note("n", 1_000L).withSorted(5_000L))
        store.save(note("n", 1_000L).withSorted(3_000L))

        assertEquals(3_000L, store.note(NoteId("n"))!!.sortedAt)
    }

    @Test
    fun `le candidate alla riemersione sono le note scritte prima di un istante`() {
        store.save(note("vecchia", 1_000L))
        store.save(note("recente", 9_000L))

        assertEquals(listOf("vecchia"), store.notesCreatedBefore(before = 5_000L).map { it.id.value })
    }
}
