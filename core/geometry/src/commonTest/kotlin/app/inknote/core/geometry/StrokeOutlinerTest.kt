package app.inknote.core.geometry

import app.inknote.core.model.InkPoint
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StrokeOutlinerTest {

    @Test
    fun `il contorno di una retta è larga quanto il tratto`() {
        val points = listOf(InkPoint(0f, 0f), InkPoint(40f, 0f))
        val widths = floatArrayOf(6f, 6f)

        val outline = StrokeOutliner.outline(points, widths, capSegments = 8)

        val ys = (0 until outline.pointCount).map { outline.y(it) }
        assertEquals(3f, ys.max(), 0.01f, "il bordo superiore deve stare a metà spessore")
        assertEquals(-3f, ys.min(), 0.01f, "il bordo inferiore deve stare a metà spessore")
    }

    @Test
    fun `le punte arrotondate sporgono oltre gli estremi`() {
        val points = listOf(InkPoint(0f, 0f), InkPoint(40f, 0f))

        val outline = StrokeOutliner.outline(points, floatArrayOf(6f, 6f), capSegments = 8)

        val xs = (0 until outline.pointCount).map { outline.x(it) }
        assertTrue(xs.max() > 40f, "la punta finale non sporge: ${xs.max()}")
        assertTrue(xs.min() < 0f, "la punta iniziale non sporge: ${xs.min()}")
        assertEquals(43f, xs.max(), 0.01f)
        assertEquals(-3f, xs.min(), 0.01f)
    }

    @Test
    fun `il contorno è un poligono con area non nulla`() {
        val points = List(12) { i -> InkPoint(i * 4f, if (i % 2 == 0) 0f else 3f) }
        val widths = FloatArray(12) { 4f }

        val outline = StrokeOutliner.outline(points, widths, capSegments = 6)

        assertTrue(shoelaceArea(outline) > 100f, "area troppo piccola: ${shoelaceArea(outline)}")
    }

    @Test
    fun `un punto solo diventa un tondo`() {
        val outline = StrokeOutliner.outline(listOf(InkPoint(10f, 10f)), floatArrayOf(8f), capSegments = 8)

        val radii = (0 until outline.pointCount).map { i ->
            val dx = outline.x(i) - 10f
            val dy = outline.y(i) - 10f
            kotlin.math.sqrt(dx * dx + dy * dy)
        }
        assertTrue(radii.all { abs(it - 4f) < 0.01f }, "non è un tondo di raggio 4: $radii")
    }

    @Test
    fun `una penna rimasta ferma produce un tondo e non un contorno degenere`() {
        val stuck = List(20) { InkPoint(5f, 5f, tMs = it * 8) }

        val outline = StrokeOutliner.outline(stuck, FloatArray(20) { 5f }, capSegments = 8)

        assertTrue(outline.pointCount in 6..40, "punti inattesi: ${outline.pointCount}")
        assertTrue(shoelaceArea(outline) > 10f)
    }

    @Test
    fun `nessuna coordinata non finita nemmeno su campioni duplicati`() {
        val points = listOf(
            InkPoint(0f, 0f), InkPoint(0f, 0f), InkPoint(5f, 5f), InkPoint(5f, 5f), InkPoint(9f, 0f),
        )

        val outline = StrokeOutliner.outline(points, FloatArray(5) { 3f }, capSegments = 4)

        assertTrue(outline.points.all { it.isFinite() })
    }

    @Test
    fun `meno segmenti di punta vuol dire meno punti da disegnare`() {
        val points = List(10) { InkPoint(it * 3f, 0f) }
        val widths = FloatArray(10) { 3f }

        val detailed = StrokeOutliner.outline(points, widths, capSegments = 8)
        val cheap = StrokeOutliner.outline(points, widths, capSegments = 3)

        assertTrue(cheap.pointCount < detailed.pointCount)
    }

    @Test
    fun `un tratto senza campioni non produce contorno`() {
        assertTrue(StrokeOutliner.outline(emptyList(), FloatArray(0)).isEmpty)
    }

    private fun shoelaceArea(outline: Outline): Float {
        var sum = 0f
        val n = outline.pointCount
        for (i in 0 until n) {
            val j = (i + 1) % n
            sum += outline.x(i) * outline.y(j) - outline.x(j) * outline.y(i)
        }
        return abs(sum) / 2f
    }
}
