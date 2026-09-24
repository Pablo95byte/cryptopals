package app.inknote.core.capture

import app.inknote.core.model.Note
import app.inknote.core.model.NoteId
import app.inknote.core.model.Stroke
import app.inknote.core.model.mergeNotes
import app.inknote.core.model.orderStrokes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InkJournalTest {

    private val sink = InMemoryInkJournalSink()
    private val journal = InkJournal(sink)

    @Test
    fun `un tratto scritto nel giornale si rilegge identico`() {
        val written = record("n1", stroke("s1", createdAt = 2_000L))

        journal.record(written)

        assertEquals(listOf(written), journal.read().records)
    }

    @Test
    fun `un giornale vuoto non ha record e non è un errore`() {
        val result = journal.read()

        assertTrue(result.records.isEmpty())
        assertFalse(result.hadTornTail)
    }

    @Test
    fun `i tratti di note diverse si separano in recupero`() {
        journal.record(record("n1", stroke("a", 2_000L)))
        journal.record(record("n2", stroke("b", 3_000L)))
        journal.record(record("n1", stroke("c", 4_000L)))

        val recovered = journal.recover().notes.associateBy { it.id.value }

        assertEquals(2, recovered.size)
        assertEquals(listOf("a", "c"), recovered.getValue("n1").strokes.map { it.id.value })
        assertEquals(listOf("b"), recovered.getValue("n2").strokes.map { it.id.value })
    }

    @Test
    fun `un processo morto a metà scrittura costa un tratto, non il giornale`() {
        journal.record(record("n1", stroke("salvo", 2_000L)))
        journal.record(record("n1", stroke("perso-a-metà", 3_000L)))
        sink.truncate(bytesLost = 12)

        val result = journal.read()

        assertEquals(listOf("salvo"), result.records.map { it.stroke.id.value })
        assertTrue(result.hadTornTail, "la coda troncata deve essere segnalata")
        assertTrue(result.discardedBytes > 0)
    }

    @Test
    fun `un record con checksum sbagliato non viene letto a caso`() {
        journal.record(record("n1", stroke("primo", 2_000L)))
        journal.record(record("n1", stroke("guasto", 3_000L)))
        sink.corruptLastByte()

        val result = journal.read()

        assertEquals(listOf("primo"), result.records.map { it.stroke.id.value })
        assertTrue(result.hadTornTail)
    }

    @Test
    fun `la coda troncata viene riportata sulla nota recuperata`() {
        journal.record(record("n1", stroke("salvo", 2_000L)))
        journal.record(record("n1", stroke("mezzo", 3_000L)))
        sink.truncate(bytesLost = 8)

        val recovery = journal.recover()

        assertTrue(recovery.hadTornTail, "l'interfaccia deve poter avvisare che un tratto si è perso")
        assertEquals(1, recovery.notes.single().strokes.size)
    }

    @Test
    fun `un giornale di una versione futura non viene interpretato né consumato`() {
        journal.record(record("n1", stroke("s1", 2_000L)))
        val bytes = sink.readAll().also { it[0] = 99 }
        val futureSink = InMemoryInkJournalSink(bytes)
        val futureJournal = InkJournal(futureSink)

        val recovery = futureJournal.recover()
        futureJournal.discard(recovery)

        assertTrue(recovery.isEmpty)
        assertEquals(0, recovery.consumedBytes, "l'ha scritto un'app più nuova: non è spazzatura")
        assertEquals(bytes.size, futureSink.readAll().size, "svuotare non deve toccarlo")
    }

    @Test
    fun `un tratto rotto non nasconde i tratti scritti dopo`() {
        // Il caso reale: il processo muore a metà di un tratto, e all'apertura
        // successiva la nuova sessione scrive in coda, dopo i byte rotti. Fermarsi al
        // primo record illeggibile renderebbe invisibile la nota nuova, e lo
        // svuotamento dopo l'assorbimento la cancellerebbe.
        journal.record(record("n1", stroke("a", 2_000L)))
        journal.record(record("n1", stroke("rotto", 3_000L)))
        sink.truncate(bytesLost = 12)
        journal.record(record("n2", stroke("c", 9_000L)))
        journal.record(record("n2", stroke("d", 9_500L)))

        val result = journal.read()

        assertEquals(listOf("a", "c", "d"), result.records.map { it.stroke.id.value })
        assertTrue(result.hadTornTail, "il tratto rotto va comunque segnalato")
    }

    @Test
    fun `un record corrotto in mezzo costa solo quel record`() {
        journal.record(record("n1", stroke("a", 2_000L)))
        val middleStart = sink.readAll().size
        journal.record(record("n1", stroke("guasto", 3_000L)))
        journal.record(record("n1", stroke("c", 4_000L)))
        val bytes = sink.readAll().also { it[middleStart + 20] = (it[middleStart + 20] + 1).toByte() }

        val result = InkJournal(InMemoryInkJournalSink(bytes)).read()

        assertEquals(listOf("a", "c"), result.records.map { it.stroke.id.value })
        assertTrue(result.hadTornTail)
    }

    @Test
    fun `lo svuotamento non tocca i tratti arrivati dopo la lettura`() {
        // Fra la lettura e lo svuotamento l'archivio impiega il suo tempo, e intanto la
        // cattura può aggiungere un tratto. Cancellare il file intero lo perderebbe.
        journal.record(record("n1", stroke("letto", 2_000L)))
        val recovery = journal.recover()
        journal.record(record("n2", stroke("arrivato-dopo", 3_000L)))

        journal.discard(recovery)

        assertEquals(listOf("arrivato-dopo"), journal.read().records.map { it.stroke.id.value })
    }

    @Test
    fun `lo svuotamento toglie anche i byte illeggibili già letti`() {
        journal.record(record("n1", stroke("a", 2_000L)))
        journal.record(record("n1", stroke("rotto", 3_000L)))
        sink.truncate(bytesLost = 12)

        journal.discard(journal.recover())

        assertEquals(0, sink.readAll().size, "altrimenti verrebbero segnalati a ogni avvio")
    }

    @Test
    fun `una nota recuperata si fonde con quella in archivio senza perdere inchiostro`() {
        // Il caso reale: due tratti erano già finiti in archivio, il terzo no perché
        // il sistema ha ucciso l'app subito dopo averlo disegnato.
        val inStore = noteOf("n1", listOf(stroke("a", 2_000L), stroke("b", 3_000L)))
        journal.record(record("n1", stroke("c", 4_000L)))

        val merged = mergeNotes(inStore, journal.recover().notes.single())

        assertEquals(listOf("a", "b", "c"), merged.strokes.map { it.id.value })
        assertEquals(4_000L, merged.updatedAt)
    }

    @Test
    fun `svuotare dopo una lettura completa lo azzera`() {
        journal.record(record("n1", stroke("s1", 2_000L)))

        journal.discard(journal.recover())

        assertTrue(journal.read().records.isEmpty())
    }

    /** Una nota come sarebbe in archivio, per provare la fusione col giornale. */
    private fun noteOf(id: String, strokes: List<Stroke>) = Note(
        id = NoteId(id),
        canvas = CANVAS,
        strokes = orderStrokes(strokes),
        createdAt = 1_000L,
        updatedAt = strokes.maxOf { it.createdAt },
        revision = strokes.size + 1L,
    )
}
