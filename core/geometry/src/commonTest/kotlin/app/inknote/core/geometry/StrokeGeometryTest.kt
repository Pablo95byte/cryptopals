package app.inknote.core.geometry

import app.inknote.core.model.CanvasSize
import app.inknote.core.model.InkPoint
import app.inknote.core.model.Note
import app.inknote.core.model.Pen
import app.inknote.core.model.PenKind
import app.inknote.core.model.Stroke
import app.inknote.core.model.StrokeId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StrokeGeometryTest {

    private fun stroke(id: String, createdAt: Long, kind: PenKind = PenKind.BALLPOINT, deletedAt: Long? = null) = Stroke(
        id = StrokeId(id),
        pen = Pen(color = 0xFF101010.toInt(), kind = kind, baseWidth = 4f),
        points = List(8) { InkPoint(x = it * 5f, y = 0f, tMs = it * 10) },
        createdAt = createdAt,
        deletedAt = deletedAt,
    )

    private fun note(strokes: List<Stroke>) = Note(
        id = app.inknote.core.model.NoteId("n"),
        canvas = CanvasSize(360f, 360f),
        strokes = strokes,
        createdAt = 0L,
        updatedAt = 0L,
    )

    @Test
    fun `una nota produce un contorno per tratto visibile`() {
        val outlines = StrokeGeometry.outlines(
            note(listOf(stroke("a", 1_000L), stroke("b", 2_000L, deletedAt = 2_500L))),
        )

        assertEquals(listOf("a"), outlines.map { it.strokeId.value })
        assertTrue(outlines.single().outline.pointCount > 10)
    }

    @Test
    fun `i contorni escono nell'ordine di disegno, evidenziatore per primo`() {
        // withStroke applica l'ordine canonico: è quello che deve arrivare al renderer.
        val note = Note.empty(CanvasSize(360f, 360f), now = 0L)
            .withStroke(stroke("penna", 1_000L), now = 1_000L)
            .withStroke(stroke("evidenziatore", 5_000L, kind = PenKind.HIGHLIGHTER), now = 5_000L)

        val outlines = StrokeGeometry.outlines(note)

        assertEquals(
            listOf("evidenziatore", "penna"),
            outlines.map { it.strokeId.value },
            "l'evidenziatore va riempito prima, o copre l'inchiostro",
        )
    }

    @Test
    fun `la qualità widget costa meno punti di quella a schermo`() {
        val stroke = stroke("a", 1_000L)

        val screen = StrokeGeometry.outline(stroke, RenderQuality.SCREEN)
        val widget = StrokeGeometry.outline(stroke, RenderQuality.WIDGET)
        val thumbnail = StrokeGeometry.outline(stroke, RenderQuality.THUMBNAIL)

        assertTrue(widget.pointCount < screen.pointCount, "${widget.pointCount} non è meno di ${screen.pointCount}")
        assertTrue(thumbnail.pointCount < widget.pointCount)
    }

    @Test
    fun `un tratto senza campioni non produce contorno`() {
        val empty = stroke("a", 1_000L).copy(points = emptyList())

        assertTrue(StrokeGeometry.outline(empty).isEmpty)
    }

    @Test
    fun `il rettangolo dell'inchiostro comprende lo spessore della penna`() {
        val bounds = Bounds.of(note(listOf(stroke("a", 1_000L))))!!

        assertEquals(-2f, bounds.minX, 0.001f, "metà dei 4 di spessore")
        assertEquals(37f, bounds.maxX, 0.001f)
        assertEquals(4f, bounds.height, 0.001f)
    }

    @Test
    fun `una nota vuota non ha rettangolo`() {
        assertTrue(Bounds.of(note(emptyList())) == null)
        assertTrue(Bounds.of(note(listOf(stroke("a", 1L, deletedAt = 2L)))) == null)
    }

    @Test
    fun `il rettangolo si può allargare per il margine del widget`() {
        val bounds = Bounds(0f, 0f, 10f, 10f).inflate(4f)

        assertEquals(Bounds(-4f, -4f, 14f, 14f), bounds)
    }
}
