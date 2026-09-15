package app.inknote.core.ink

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StrokeSimplifierTest {

    @Test
    fun `una retta si riduce ai suoi estremi`() {
        val simplified = StrokeSimplifier.simplify(line(count = 50), tolerance = 0.3f)

        assertEquals(2, simplified.size)
        assertEquals(0f, simplified.first().x)
        assertEquals(98f, simplified.last().x)
    }

    @Test
    fun `una curva non viene appiattita`() {
        val spike = listOf(
            point(0f, 0f),
            point(10f, 30f),
            point(20f, 0f),
        )

        assertEquals(3, StrokeSimplifier.simplify(spike, tolerance = 0.3f).size)
    }

    @Test
    fun `la tolleranza decide quanto si semplifica`() {
        val wobbly = List(41) { i -> point(i * 2f, if (i % 2 == 0) 0f else 0.4f) }

        val fine = StrokeSimplifier.simplify(wobbly, tolerance = 0.1f)
        val coarse = StrokeSimplifier.simplify(wobbly, tolerance = 2f)

        assertTrue(fine.size > coarse.size, "${fine.size} non è maggiore di ${coarse.size}")
        assertEquals(2, coarse.size)
    }

    @Test
    fun `tratti brevissimi restano intatti`() {
        val dot = listOf(point(1f, 1f))
        val dash = listOf(point(1f, 1f), point(2f, 2f))

        assertEquals(dot, StrokeSimplifier.simplify(dot, tolerance = 0.3f))
        assertEquals(dash, StrokeSimplifier.simplify(dash, tolerance = 0.3f))
    }

    @Test
    fun `campioni tutti coincidenti non mandano in crisi il calcolo`() {
        val stuck = List(10) { point(5f, 5f) }

        assertEquals(2, StrokeSimplifier.simplify(stuck, tolerance = 0.3f).size)
    }

    private fun point(x: Float, y: Float) = app.inknote.core.model.InkPoint(x, y)
}
