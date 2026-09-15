package app.inknote.core.capture

import app.inknote.core.model.PenKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CaptureSessionTest {

    private val clock = FakeClock(now = 1_000L)
    private val sink = InMemoryInkJournalSink()
    private val journal = InkJournal(sink)

    private fun session() = CaptureSession(canvas = CANVAS, journal = journal, clock = clock)

    /** Disegna un tratto completo, come farebbe la superficie di disegno. */
    private fun CaptureSession.write(pen: app.inknote.core.model.Pen = BIRO, samples: Int = 6) {
        beginStroke(pen)
        repeat(samples) { addSample(x = it * 4f, y = 10f, pressure = 0.5f, tMs = it * 12) }
        endStroke()
    }

    @Test
    fun `una sessione appena aperta è vuota`() {
        val session = session()

        assertTrue(session.isEmpty)
        assertFalse(session.isDrawing)
        assertTrue(session.note().isEmpty)
    }

    @Test
    fun `il primo tratto è nel giornale appena si stacca il dito`() {
        val session = session()

        session.write()

        // Nessuna conferma, nessun salvataggio esplicito: deve essere già al sicuro.
        assertEquals(1, journal.read().records.size)
        assertEquals(session.noteId, journal.read().records.single().noteId)
    }

    @Test
    fun `un secondo contatto mentre si scrive viene ignorato`() {
        val session = session()
        assertTrue(session.beginStroke(BIRO))

        // È il palmo della mano appoggiato al vetro, non un secondo tratto voluto.
        assertFalse(session.beginStroke(BIRO), "il secondo contatto non deve aprire un tratto")
    }

    @Test
    fun `un tratto annullato non lascia traccia`() {
        val session = session()
        session.beginStroke(BIRO)
        session.addSample(1f, 1f, 0.5f, 0)

        session.cancelStroke()

        assertTrue(session.isEmpty)
        assertTrue(journal.read().records.isEmpty())
        assertFalse(session.isDrawing)
    }

    @Test
    fun `chiudere senza aver aperto non produce nulla`() {
        assertNull(session().endStroke())
    }

    @Test
    fun `un tocco senza movimento resta un punto e viene salvato`() {
        val session = session()
        session.beginStroke(BIRO)
        session.addSample(20f, 20f, 0.6f, 0)

        val stroke = session.endStroke()

        assertEquals(1, stroke!!.points.size)
        assertEquals(1, journal.read().records.size)
    }

    @Test
    fun `i campioni fuori da un tratto vengono rifiutati`() {
        assertFalse(session().addSample(1f, 1f, 0.5f, 0))
    }

    @Test
    fun `la nota cresce di una revisione per tratto`() {
        val session = session()

        session.write()
        assertEquals(2L, session.note().revision)

        clock.advance(500)
        session.write()
        assertEquals(3L, session.note().revision)
    }

    @Test
    fun `la nota esce nell'ordine di disegno e non in quello di scrittura`() {
        val session = session()
        session.write(pen = BIRO)
        clock.advance(500)
        session.write(pen = BIRO.copy(kind = PenKind.HIGHLIGHTER))

        val kinds = session.note().strokes.map { it.pen.kind }

        assertEquals(listOf(PenKind.HIGHLIGHTER, PenKind.BALLPOINT), kinds)
    }

    @Test
    fun `l'istante della nota segue l'ultimo tratto`() {
        val session = session()
        session.write()
        clock.set(9_000L)
        session.write()

        assertEquals(1_000L, session.note().createdAt)
        assertEquals(9_000L, session.note().updatedAt)
    }

    @Test
    fun `se il giornale non accetta la scrittura l'inchiostro resta sullo schermo`() {
        val brokenJournal = InkJournal(
            object : InkJournalSink {
                override fun append(record: ByteArray) = throw IllegalStateException("disco pieno")
                override fun readAll() = ByteArray(0)
                override fun clear() = Unit
            },
        )
        val session = CaptureSession(canvas = CANVAS, journal = brokenJournal, clock = clock)

        session.write()

        assertEquals(1, session.strokeCount, "far sparire il tratto disegnato è il comportamento peggiore")
        assertEquals(1, session.journalFailures, "ma l'interfaccia deve poterlo sapere e avvisare")
    }

    @Test
    fun `dopo la morte del processo la nota si ricostruisce dal giornale`() {
        val session = session()
        session.write()
        clock.advance(400)
        session.write()
        val expected = session.note()

        // Il processo muore qui: nessuno ha chiamato l'archivio.
        val recovered = InkJournal(InMemoryInkJournalSink(sink.readAll())).recover().single()

        assertEquals(expected.id, recovered.note.id)
        assertEquals(expected.strokes, recovered.note.strokes)
        assertEquals(expected.canvas, recovered.note.canvas)
        assertEquals(expected.createdAt, recovered.note.createdAt)
        assertFalse(recovered.hadTornTail)
    }
}
