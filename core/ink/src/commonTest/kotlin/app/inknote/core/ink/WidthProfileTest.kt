package app.inknote.core.ink

import app.inknote.core.model.PenKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WidthProfileTest {

    private val config = InkConfig.Default

    @Test
    fun `più pressione vuol dire tratto più spesso`() {
        val points = line(count = 20) { i -> i / 19f }

        val widths = WidthProfile.compute(points, BIRO, config)

        assertTrue(widths[12] > widths[7], "${widths[12]} non è maggiore di ${widths[7]}")
    }

    @Test
    fun `lo spessore resta dentro i limiti della penna`() {
        val points = line(count = 30) { i -> if (i % 3 == 0) 0f else 1f }

        val widths = WidthProfile.compute(points, BIRO, config)

        val minimum = BIRO.baseWidth * config.minWidthFactor
        assertTrue(widths.all { it in minimum..BIRO.baseWidth }, "spessori fuori limite: ${widths.toList()}")
    }

    @Test
    fun `senza pressione lo spessore lo decide la velocità`() {
        val slow = line(count = 24, step = 2f, msPerSample = 20)
        val fast = line(count = 24, step = 2f, msPerSample = 1)

        val slowWidths = WidthProfile.compute(slow, BIRO, config)
        val fastWidths = WidthProfile.compute(fast, BIRO, config)

        assertTrue(
            fastWidths[12] < slowWidths[12],
            "gesto veloce ${fastWidths[12]} non più sottile di quello lento ${slowWidths[12]}",
        )
    }

    @Test
    fun `il tratto è affilato in entrata e in uscita`() {
        val points = line(count = 30, msPerSample = 20)

        val widths = WidthProfile.compute(points, BIRO, config)

        assertTrue(widths.first() < widths[15], "l'attacco del tratto non è affilato")
        assertTrue(widths.last() < widths[15], "lo stacco del tratto non è affilato")
    }

    @Test
    fun `l'evidenziatore ha spessore costante`() {
        val highlighter = BIRO.copy(kind = PenKind.HIGHLIGHTER, baseWidth = 12f)

        val widths = WidthProfile.compute(line(count = 20) { i -> i / 19f }, highlighter, config)

        assertTrue(widths.all { it == 12f }, "l'evidenziatore non deve modulare")
    }

    @Test
    fun `il pennarello modula meno della biro`() {
        val points = line(count = 30) { i -> i / 29f }

        val biro = WidthProfile.compute(points, BIRO, config)
        val marker = WidthProfile.compute(points, BIRO.copy(kind = PenKind.MARKER), config)

        val biroRange = biro.max() - biro.min()
        val markerRange = marker.max() - marker.min()
        assertTrue(markerRange < biroRange, "$markerRange non è minore di $biroRange")
    }

    @Test
    fun `nessun campione nessuno spessore`() {
        assertEquals(0, WidthProfile.compute(emptyList(), BIRO, config).size)
    }
}
