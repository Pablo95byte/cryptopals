package app.inknote.kit

import app.inknote.core.capture.InMemoryInkJournalSink
import app.inknote.core.capture.InkJournal
import app.inknote.core.model.Clock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** La facciata del foglio per iOS (D48): deve comportarsi come il foglio di Android. */
class InkSheetTest {

    private val sink = InMemoryInkJournalSink()
    private var now = 1_000L
    private val sheet = InkSheet(390f, 844f, sink, Clock { now })

    private fun scribble(pencil: Boolean = false, force: Float = -1f) {
        // Un istante diverso per ogni tratto: a parità di istante l'ordine lo decide l'id,
        // che è casuale, e il test passerebbe o cadrebbe a seconda della fortuna.
        now += 1_000L
        assertTrue(sheet.begin(pencil = pencil, timestampMs = 0L))
        repeat(8) { sheet.add(x = 20f + it * 6f, y = 40f + it * 2f, force = force, timestampMs = it * 12L) }
        sheet.end()
    }

    @Test
    fun `un tratto chiuso diventa un contorno e finisce nel giornale`() {
        scribble()

        assertEquals(1, sheet.committedShapes.size)
        assertTrue(sheet.committedShapes.single().pointCount > 4)
        assertEquals(1, InkJournal(sink).recover().notes.single().strokes.size)
    }

    @Test
    fun `mentre si scrive c'è un contorno vivo, e dopo il sollevamento no`() {
        sheet.begin(pencil = false, timestampMs = 0L)
        sheet.add(10f, 10f, -1f, 0L)
        sheet.add(30f, 12f, -1f, 16L)

        assertNotNull(sheet.liveShape())
        sheet.end()
        assertNull(sheet.liveShape())
    }

    @Test
    fun `un secondo dito mentre si scrive non apre un tratto`() {
        sheet.begin(pencil = false, timestampMs = 0L)

        assertFalse(sheet.begin(pencil = false, timestampMs = 5L), "è il palmo, non un tratto")
    }

    @Test
    fun `col dito la pressione non si inventa, col pennino sì che si usa`() {
        scribble(pencil = false, force = -1f)
        scribble(pencil = true, force = 0.7f)

        val strokes = InkJournal(sink).recover().notes.single().strokes
        assertFalse(strokes.first().points.first().hasPressure, "il dito non ha pressione (invariante 6)")
        assertTrue(strokes.last().points.first().hasPressure)
    }

    @Test
    fun `il dito scrive col pennarello e il pennino con la biro`() {
        scribble(pencil = false)
        scribble(pencil = true, force = 0.5f)

        val widths = InkJournal(sink).recover().notes.single().strokes.map { it.pen.baseWidth }
        assertTrue(widths[0] > widths[1], "D42: col dito il tratto è più spesso")
    }

    @Test
    fun `testo e foto entrano nella stessa nota dei tratti`() {
        scribble()
        sheet.commitText("comprare il pane")
        sheet.addPhoto("photos/p1.jpg")

        val note = InkJournal(sink).recover().notes.single()
        assertEquals(sheet.noteId, note.id.value)
        assertEquals("comprare il pane", note.typedText)
        assertEquals(1, note.photoClips.size)
        assertFalse(sheet.isEmpty)
    }

    @Test
    fun `un foglio appena aperto è vuoto`() {
        assertTrue(sheet.isEmpty)
        assertTrue(sheet.committedShapes.isEmpty())
    }
}

class InkArchiveTest {

    @Test
    fun `l'anteprima sta dentro il riquadro, margini compresi`() {
        val sink = InMemoryInkJournalSink()
        val sheet = InkSheet(390f, 844f, sink, Clock { 1_000L })
        sheet.begin(pencil = false, timestampMs = 0L)
        repeat(10) { sheet.add(200f + it * 10f, 500f + it * 3f, -1f, it * 12L) }
        sheet.end()
        val note = InkJournal(sink).recover().notes.single()

        val shapes = InkPreview.shapes(note, width = 160f, height = 120f, padding = 12f)

        assertEquals(1, shapes.size)
        val shape = shapes.single()
        for (i in 0 until shape.pointCount) {
            assertTrue(shape.x(i) in 0f..160f && shape.y(i) in 0f..120f, "punto $i fuori: ${shape.x(i)}, ${shape.y(i)}")
        }
    }

    @Test
    fun `una nota senza inchiostro non ha anteprima`() {
        val sink = InMemoryInkJournalSink()
        val sheet = InkSheet(390f, 844f, sink, Clock { 1_000L })
        sheet.commitText("solo testo")
        val note = InkJournal(sink).recover().notes.single()

        assertTrue(InkPreview.shapes(note, 160f, 120f, 12f).isEmpty())
    }
}

class InkArchiveSortingTest {

    private val driver = app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver(
        app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver.IN_MEMORY,
    ).also { app.inknote.core.store.InkNoteStore.schema.create(it) }
    private val archive = InkArchive(app.inknote.core.store.InkNoteStore.open(driver))

    private fun capture(text: String, at: Long): String {
        val sink = InMemoryInkJournalSink()
        val sheet = InkSheet(390f, 844f, sink, Clock { at })
        sheet.commitText(text)
        archive.ingest(sink)
        return sheet.noteId
    }

    @Test
    fun `tenere una nota la toglie dalla coda`() {
        val id = capture("idea", 1_000L)
        assertEquals(1L, archive.toSortCount())

        archive.keep(id, 2_000L)

        assertEquals(0L, archive.toSortCount())
    }

    @Test
    fun `la data scritta nella nota diventa un suggerimento`() {
        val id = capture("dentista domani alle 10", 1_000L)
        val note = archive.note(id)!!

        val hint = archive.dateHint(note, nowMillis = 1_000L, utcOffsetMillis = 0L)

        assertNotNull(hint)
        assertEquals(86_400_000L + 10 * 3_600_000L, hint.atMillis)
    }

    @Test
    fun `una nota di una settimana fa riemerge`() {
        val day = 86_400_000L
        val id = capture("idea vecchia", 1_000L)

        val resurfaced = archive.resurfaced(nowMillis = 1_000L + 7 * day, utcOffsetMillis = 0L)

        assertEquals(id, resurfaced?.note?.id?.value)
    }

    @Test
    fun `la pulizia del cestino restituisce le foto da cancellare dal disco`() {
        val day = 86_400_000L
        val sink = InMemoryInkJournalSink()
        val sheet = InkSheet(390f, 844f, sink, Clock { 1_000L })
        sheet.addPhoto("photos/lavagna.jpg")
        archive.ingest(sink)
        val note = archive.note(sheet.noteId)!!
        assertEquals(listOf("photos/lavagna.jpg"), archive.photoPaths(note))

        archive.delete(sheet.noteId, nowMillis = 2_000L)

        assertEquals(emptyList(), archive.purge(nowMillis = 2_000L + 29 * day), "non prima di trenta giorni")
        assertEquals(listOf("photos/lavagna.jpg"), archive.purge(nowMillis = 2_000L + 31 * day))
        assertNull(archive.note(sheet.noteId))
    }
}
