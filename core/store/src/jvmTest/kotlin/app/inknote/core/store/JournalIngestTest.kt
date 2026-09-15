package app.inknote.core.store

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.inknote.core.capture.CaptureSession
import app.inknote.core.capture.InMemoryInkJournalSink
import app.inknote.core.capture.InkJournal
import app.inknote.core.model.CanvasSize
import app.inknote.core.model.Clock
import app.inknote.core.model.Note
import app.inknote.core.model.NoteId
import app.inknote.core.model.Pen
import app.inknote.core.model.PenKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Il percorso completo: si scrive, il processo muore, all'avvio successivo
 * l'inchiostro finisce in archivio. È lo scenario che giustifica tutto il
 * meccanismo del giornale, quindi va provato per intero e non a pezzi.
 */
class JournalIngestTest {

    private val canvas = CanvasSize(360f, 640f)
    private val pen = Pen(color = 0xFF1F2430.toInt(), kind = PenKind.BALLPOINT, baseWidth = 3.5f)
    private val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { InkNoteStore.schema.create(it) }
    private val store = InkNoteStore.open(driver)
    private val sink = InMemoryInkJournalSink()
    private val journal = InkJournal(sink)
    private val ingest = JournalIngest(store)

    private var now = 1_000L
    private val clock = Clock { now }

    private fun session(noteId: NoteId = NoteId.random()) =
        CaptureSession(canvas = canvas, journal = journal, clock = clock, noteId = noteId)

    private fun CaptureSession.write(samples: Int = 6) {
        beginStroke(pen)
        repeat(samples) { addSample(x = it * 5f, y = 12f, pressure = 0.5f, tMs = it * 12) }
        endStroke()
    }

    @Test
    fun `una nota scritta e mai salvata arriva in archivio al risveglio`() {
        val session = session()
        session.write()
        now += 300
        session.write()

        val result = ingest.ingest(journal)

        assertEquals(1, result.notesIngested)
        assertFalse(result.hadTornTail)
        val stored = assertNotNull(store.note(session.noteId))
        assertEquals(session.note().strokes, stored.strokes)
    }

    @Test
    fun `un giornale vuoto non fa niente`() {
        val result = ingest.ingest(journal)

        assertEquals(0, result.notesIngested)
        assertEquals(0L, store.liveNoteCount())
    }

    @Test
    fun `l'assorbimento non perde i tratti già in archivio`() {
        // Due tratti salvati regolarmente, il terzo solo nel giornale perché il
        // sistema ha ucciso l'app subito dopo averlo disegnato.
        val noteId = NoteId("n1")
        val session = session(noteId)
        session.write()
        now += 200
        session.write()
        ingest.ingest(journal)

        val continued = session(noteId)
        continued.write()
        ingest.ingest(journal)

        assertEquals(3, store.note(noteId)!!.strokes.size)
    }

    @Test
    fun `il giornale si svuota solo dopo il salvataggio`() {
        session().write()

        ingest.ingest(journal)

        assertTrue(journal.read().records.isEmpty(), "assorbito: il giornale va svuotato")
    }

    @Test
    fun `se il salvataggio non riesce il giornale resta l'unica copia`() {
        session().write()
        val brokenIngest = JournalIngest(
            object : NoteStore by store {
                override fun save(note: Note) = throw IllegalStateException("archivio non disponibile")
            },
        )

        assertFailsWith<IllegalStateException> { brokenIngest.ingest(journal) }

        assertEquals(1, journal.read().records.size, "svuotarlo qui vorrebbe dire perdere la nota")
    }

    @Test
    fun `assorbire due volte non duplica niente`() {
        val session = session()
        session.write()

        ingest.ingest(journal)
        val second = ingest.ingest(journal)

        assertEquals(0, second.notesIngested)
        assertEquals(1, store.note(session.noteId)!!.strokes.size)
    }

    @Test
    fun `si può ispezionare il giornale senza consumarlo`() {
        session().write()

        ingest.ingest(journal, clearOnSuccess = false)

        assertEquals(1, journal.read().records.size)
    }

    @Test
    fun `la coda troncata viene segnalata fino all'esito`() {
        val session = session()
        session.write()
        now += 200
        session.write()
        sink.truncate(bytesLost = 10)

        val result = ingest.ingest(journal)

        assertTrue(result.hadTornTail, "l'interfaccia deve poter avvisare che un tratto si è perso")
        assertEquals(1, store.note(session.noteId)!!.strokes.size)
    }

    @Test
    fun `note diverse nello stesso giornale finiscono in note diverse`() {
        session(NoteId("prima")).write()
        now += 500
        session(NoteId("seconda")).write()

        val result = ingest.ingest(journal)

        assertEquals(2, result.notesIngested)
        assertEquals(2L, store.liveNoteCount())
    }
}
