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
