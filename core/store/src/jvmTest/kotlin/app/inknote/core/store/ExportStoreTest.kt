package app.inknote.core.store

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.inknote.core.model.CanvasSize
import app.inknote.core.model.ExportTarget
import app.inknote.core.model.InkPoint
import app.inknote.core.model.Note
import app.inknote.core.model.NoteId
import app.inknote.core.model.Pen
import app.inknote.core.model.PenKind
import app.inknote.core.model.Stroke
import app.inknote.core.model.StrokeId
import app.inknote.core.store.db.InkNoteDatabase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** La traccia degli invii, sull'archivio vero (D31). */
class ExportStoreTest {

    private val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { InkNoteStore.schema.create(it) }
    private val database = InkNoteDatabase(driver)
    private val store = InkNoteStore.open(driver)

    private fun note(id: String, updatedAt: Long = 1_000L, revision: Long = 2L) = Note(
        id = NoteId(id),
        canvas = CanvasSize(360f, 640f),
        strokes = listOf(
            Stroke(
                id = StrokeId("$id-s"),
                pen = Pen(color = 0xFF1F2430.toInt(), kind = PenKind.BALLPOINT, baseWidth = 3.5f),
                points = listOf(InkPoint(1f, 2f, 0.5f, 0), InkPoint(9f, 4f, 0.7f, 16)),
                createdAt = 1_000L,
            ),
        ),
        createdAt = 1_000L,
        updatedAt = updatedAt,
        revision = revision,
    )

    @Test
    fun `un invio registrato si rilegge`() {
        val sent = note("n1").withExport(ExportTarget.Notion, now = 5_000L)

        store.save(sent)

        val reread = store.note(NoteId("n1"))!!
        assertTrue(reread.wasSentTo(ExportTarget.Notion))
        assertEquals(5_000L, reread.exports.single().sentAt)
        assertEquals(sent, reread)
    }

    @Test
    fun `una nota mai mandata è in coda`() {
        store.save(note("n1"))

        assertEquals(listOf("n1"), store.notesToSend(ExportTarget.Notion).map { it.id.value })
    }

    @Test
    fun `una nota già mandata esce dalla coda`() {
        store.save(note("n1").withExport(ExportTarget.Notion, now = 5_000L))

        assertTrue(store.notesToSend(ExportTarget.Notion).isEmpty())
        // Mandata a Notion non vuol dire mandata altrove.
        assertEquals(listOf("n1"), store.notesToSend(ExportTarget.Markdown).map { it.id.value })
    }

    @Test
    fun `una nota cresciuta dopo l'invio torna in coda`() {
        val sent = note("n1").withExport(ExportTarget.Notion, now = 5_000L)
        store.save(sent)
        assertTrue(store.notesToSend(ExportTarget.Notion).isEmpty())

        val grown = store.note(NoteId("n1"))!!.withStroke(
            Stroke(
                id = StrokeId("s2"),
                pen = Pen(color = 0xFF1F2430.toInt(), kind = PenKind.BALLPOINT, baseWidth = 3.5f),
                points = listOf(InkPoint(20f, 20f, 0.5f, 0)),
                createdAt = 6_000L,
            ),
            now = 6_000L,
        )
        store.save(grown)

        assertEquals(listOf("n1"), store.notesToSend(ExportTarget.Notion).map { it.id.value })
    }

    @Test
    fun `le note cestinate non sono in coda`() {
        store.save(note("n1"))
        store.markDeleted(NoteId("n1"), now = 2_000L)

        assertTrue(store.notesToSend(ExportTarget.Notion).isEmpty())
    }

    @Test
    fun `un salvataggio da una copia vecchia non fa retrocedere l'invio registrato`() {
        val old = note("n1", revision = 2L)
        store.save(old.withExport(ExportTarget.Notion, now = 5_000L))
        // La nota cresce e viene rimandata.
        store.save(note("n1", revision = 4L, updatedAt = 6_000L).withExport(ExportTarget.Notion, now = 7_000L))

        // Qualcuno salva la copia vecchia, con l'invio vecchio.
        store.save(old.withExport(ExportTarget.Notion, now = 5_000L))

        val reread = store.note(NoteId("n1"))!!
        assertEquals(4L, reread.exports.single().revision, "retrocedere qui vorrebbe dire duplicare la nota in Notion")
        assertEquals(7_000L, reread.exports.single().sentAt)
    }

    @Test
    fun `destinazioni diverse convivono`() {
        store.save(
            note("n1")
                .withExport(ExportTarget.Notion, now = 5_000L)
                .withExport(ExportTarget.SystemShare, now = 6_000L),
        )

        assertEquals(2, store.note(NoteId("n1"))!!.exports.size)
    }

    @Test
    fun `la pulizia non lascia invii orfani`() {
        store.save(note("antica", updatedAt = 1_000L).withExport(ExportTarget.Notion, now = 1_000L).copy(deletedAt = 1_000L))

        store.purgeDeleted(before = 5_000L)

        assertNull(store.note(NoteId("antica")))
        assertTrue(database.inkNoteQueries.selectExports("antica").executeAsList().isEmpty())
    }
}
