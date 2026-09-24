package app.inknote.core.ink

import app.inknote.core.model.InkPoint
import app.inknote.core.model.distanceTo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CatmullRomTest {

    @Test
    fun `la curva parte e finisce esattamente sui campioni originali`() {
        val input = listOf(InkPoint(0f, 0f), InkPoint(10f, 4f), InkPoint(20f, 0f))

        val curve = CatmullRom.resample(input, spacing = 1.5f)

        assertEquals(0f, curve.first().x, 0.001f)
        assertEquals(0f, curve.first().y, 0.001f)
        assertEquals(20f, curve.last().x, 0.001f)
        assertEquals(0f, curve.last().y, 0.001f)
    }

    @Test
    fun `il passo di ricampionamento viene rispettato`() {
        val spacing = 1.5f
        val curve = CatmullRom.resample(listOf(InkPoint(0f, 0f), InkPoint(37f, 12f)), spacing)

        val gaps = (1 until curve.size).map { curve[it - 1].distanceTo(curve[it]) }

        assertTrue(gaps.max() <= spacing * 1.2f, "passo massimo ${gaps.max()} oltre il consentito")
        assertTrue(curve.size > 20, "una diagonale di 39 unità a passo 1.5 deve dare molti punti")
    }

    @Test
    fun `la curva non produce valori non finiti`() {
        val nasty = listOf(
            InkPoint(0f, 0f),
            InkPoint(0f, 0f), // campione duplicato: il digitizer li manda
            InkPoint(5f, 5f),
            InkPoint(5f, 5f),
            InkPoint(5f, 0f),
        )

        val curve = CatmullRom.resample(nasty, spacing = 1f)

        assertTrue(curve.all { it.x.isFinite() && it.y.isFinite() })
        assertFalse(curve.any { it.x.isNaN() || it.y.isNaN() })
    }

    @Test
    fun `il tempo lungo la curva non torna indietro`() {
        val input = line(count = 8, step = 6f, msPerSample = 10)

        val curve = CatmullRom.resample(input, spacing = 1.5f)

        for (i in 1 until curve.size) {
            assertTrue(curve[i].tMs >= curve[i - 1].tMs, "il tempo è tornato indietro all'indice $i")
        }
    }

    @Test
    fun `la pressione viene interpolata e resta nell'intervallo`() {
        val input = line(count = 6, step = 5f) { i -> i / 5f }

        val curve = CatmullRom.resample(input, spacing = 1f)

        assertTrue(curve.all { it.pressure in 0f..1f }, "pressione fuori 0..1")
        assertTrue(curve.last().pressure > curve.first().pressure)
    }

    @Test
    fun `la pressione assente non viene inventata`() {
        val curve = CatmullRom.resample(line(count = 6, step = 5f), spacing = 1f)

        assertTrue(curve.none { it.hasPressure })
    }

    @Test
    fun `un solo campione resta un solo campione`() {
        val dot = listOf(InkPoint(3f, 3f))

        assertEquals(dot, CatmullRom.resample(dot, spacing = 1f))
    }
}
