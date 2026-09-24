package app.inknote.core.ink

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StrokeBuilderTest {

    private fun builder() = StrokeBuilder(pen = BIRO, startedAt = 1_000L)

    @Test
    fun `un tratto senza campioni non produce nulla`() {
        val builder = builder()
        assertTrue(builder.isEmpty)
        assertNull(builder.build())
    }

    @Test
    fun `i campioni troppo vicini vengono scartati`() {
        val builder = builder()

        assertTrue(builder.add(0f, 0f, tMs = 0))
        assertFalse(builder.add(0.1f, 0f, tMs = 4), "0.1 unità è rumore del digitizer")
        assertTrue(builder.add(5f, 0f, tMs = 8))
    }

    @Test
    fun `l'ultimo campione scartato non viene perso alla chiusura`() {
        val builder = builder()
        builder.add(0f, 0f, tMs = 0)
        builder.add(10f, 0f, tMs = 10)
        builder.add(10.2f, 0f, tMs = 14) // scartato, ma è dove il dito si è staccato

        val stroke = builder.build()!!

        assertEquals(10.2f, stroke.points.last().x, "il punto finale definisce la lunghezza del tratto")
    }

    @Test
    fun `la penna ferma sul foglio è un punto voluto`() {
        val builder = builder()
        builder.add(0f, 0f, tMs = 0)

        assertFalse(builder.add(0f, 0f, tMs = 10), "10 ms fermi sono ancora rumore")
        assertTrue(builder.add(0f, 0f, tMs = 60), "60 ms fermi sono una pausa voluta")
    }

    @Test
    fun `il tratto prodotto conserva penna e istante di inizio`() {
        val builder = builder()
        repeat(10) { builder.add(it * 3f, 0f, tMs = it * 8) }

        val stroke = builder.build()!!

        assertEquals(BIRO, stroke.pen)
        assertEquals(1_000L, stroke.createdAt)
        assertNull(stroke.deletedAt)
    }
}
