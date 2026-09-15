package app.inknote.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class StrokePointCodecTest {

    @Test
    fun `i campioni sopravvivono al giro di andata e ritorno`() {
        val points = listOf(
            InkPoint(x = 12.5f, y = -3.25f, pressure = 0.42f, tMs = 0),
            InkPoint(x = 0f, y = 0f, pressure = InkPoint.NO_PRESSURE, tMs = 16),
            InkPoint(x = 1234.75f, y = 999.125f, pressure = 1f, tMs = 32_000),
        )

        assertEquals(points, StrokePointCodec.decode(StrokePointCodec.encode(points)))
    }

    @Test
    fun `la pressione assente resta assente e non diventa zero`() {
        val decoded = StrokePointCodec.decode(
            StrokePointCodec.encode(listOf(InkPoint(1f, 1f, pressure = InkPoint.NO_PRESSURE))),
        )

        assertTrue(!decoded.single().hasPressure, "la pressione è stata inventata alla rilettura")
    }

    @Test
    fun `la dimensione è prevedibile`() {
        val encoded = StrokePointCodec.encode(List(100) { InkPoint(it.toFloat(), 0f) })

        assertEquals(1 + 100 * StrokePointCodec.BYTES_PER_POINT, encoded.size)
    }

    @Test
    fun `un tratto vuoto si codifica e si rilegge`() {
        assertEquals(emptyList(), StrokePointCodec.decode(StrokePointCodec.encode(emptyList())))
        assertEquals(emptyList(), StrokePointCodec.decode(ByteArray(0)))
    }

    @Test
    fun `il primo byte è la versione del formato`() {
        assertEquals(StrokePointCodec.VERSION.toByte(), StrokePointCodec.encode(listOf(InkPoint(1f, 2f)))[0])
    }

    @Test
    fun `una versione futura non viene letta a caso`() {
        val fromTheFuture = StrokePointCodec.encode(listOf(InkPoint(1f, 2f))).also { it[0] = 99 }

        assertFailsWith<IllegalArgumentException> { StrokePointCodec.decode(fromTheFuture) }
    }

    @Test
    fun `un tratto troncato è un errore, non una nota vuota`() {
        val truncated = StrokePointCodec.encode(listOf(InkPoint(1f, 2f))).copyOf(10)

        assertFailsWith<IllegalArgumentException> { StrokePointCodec.decode(truncated) }
    }
}
