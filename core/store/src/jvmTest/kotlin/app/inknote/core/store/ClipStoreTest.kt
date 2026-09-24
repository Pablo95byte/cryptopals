package app.inknote.core.store

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.inknote.core.model.CanvasSize
import app.inknote.core.model.ExportTarget
import app.inknote.core.model.Note
import app.inknote.core.model.NoteId
import app.inknote.core.model.PhotoClip
import app.inknote.core.model.PhotoClipId
import app.inknote.core.model.TextClip
import app.inknote.core.model.TextClipId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Testo digitato e foto sull'archivio vero (D38). */
class ClipStoreTest {

    private val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { InkNoteStore.schema.create(it) }
    private val store = InkNoteStore.open(driver)

    private fun empty(id: String) = Note.empty(CanvasSize(360f, 640f), now = 1_000L, id = NoteId(id))

    private fun text(id: String, text: String, at: Long = 2_000L) = TextClip(TextClipId(id), at, text)

    private fun photo(id: String) = PhotoClip(PhotoClipId(id), 2_000L, "photos/$id.jpg")

    @Test
    fun `una nota con testo e foto si rilegge identica`() {
        val note = empty("n1")
            .withTextClip(text("t1", "Chiamare l'idraulico"), now = 2_000L)
            .withPhotoClip(photo("p1"), now = 2_100L)

        store.save(note)

        assertEquals(note, store.note(NoteId("n1")))
    }

    @Test
    fun `il testo digitato si trova subito, senza aspettare nessun riconoscimento`() {
        store.save(empty("n1").withTextClip(text("t1", "Perché il caffè è finito"), now = 2_000L))
        store.save(empty("n2").withTextClip(text("t2", "Tutt'altro"), now = 2_000L))

        assertEquals(listOf("n1"), store.search("CAFFE perche").map { it.id.value })
    }

    @Test
    fun `un testo cancellato non si trova più`() {
        val note = empty("n1").withTextClip(text("t1", "segreto"), now = 2_000L)
        store.save(note)

        store.save(note.withTextClipDeleted(TextClipId("t1"), now = 3_000L))

        assertTrue(store.search("segreto").isEmpty())
    }

    @Test
    fun `un salvataggio da una copia vecchia non resuscita un testo cancellato`() {
        val original = empty("n1").withTextClip(text("t1", "da cancellare"), now = 2_000L)
        store.save(original.withTextClipDeleted(TextClipId("t1"), now = 3_000L))

        store.save(original)

        assertFalse(store.note(NoteId("n1"))!!.hasText, "il tombstone si mette, non si toglie (invariante 15)")
    }

    @Test
    fun `un salvataggio da una copia vecchia non resuscita una foto cancellata`() {
        val original = empty("n1").withPhotoClip(photo("p1"), now = 2_000L)
        store.save(original.withPhotoClipDeleted(PhotoClipId("p1"), now = 3_000L))

        store.save(original)

        assertFalse(store.note(NoteId("n1"))!!.hasPhoto)
    }

    @Test
    fun `una nota di solo testo o sola foto è in coda per l'invio`() {
        store.save(empty("testo").withTextClip(text("t1", "ciao"), now = 2_000L))
        store.save(empty("foto").withPhotoClip(photo("p1"), now = 2_000L))
        store.save(empty("vuota"))

        assertEquals(
            setOf("testo", "foto"),
            store.notesToSend(ExportTarget.SystemShare).map { it.id.value }.toSet(),
        )
    }

    @Test
    fun `eliminare davvero una nota restituisce i file delle sue foto`() {
        store.save(empty("n1").withPhotoClip(photo("p1"), now = 2_000L))
        store.markDeleted(NoteId("n1"), now = 3_000L)

        val paths = store.purgeDeleted(before = 4_000L)

        assertEquals(listOf("photos/p1.jpg"), paths, "altrimenti il file resterebbe su disco per sempre")
        assertEquals(null, store.note(NoteId("n1")))
    }

    @Test
    fun `una nota non ancora eliminabile tiene le sue foto`() {
        store.save(empty("n1").withPhotoClip(photo("p1"), now = 2_000L))
        store.markDeleted(NoteId("n1"), now = 3_000L)

        assertEquals(emptyList(), store.purgeDeleted(before = 2_500L))
    }
}
