package app.inknote.core.capture

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FrictionTraceTest {

    private val clock = FakeClock(now = 0L)

    private fun trace(kind: StartKind = StartKind.COLD) = FrictionTrace(clock, kind)

    @Test
    fun `senza tappe non si può giudicare`() {
        val trace = trace()

        assertEquals(FrictionVerdict.INCOMPLETE, trace.verdict())
        assertEquals(FrictionVerdict.INCOMPLETE, trace.drawnVerdict())
        assertNull(trace.timeToInkAccepted)
    }

    @Test
    fun `il tetto a caldo è più severo di quello a freddo`() {
        // Non è un capriccio: a freddo la maggior parte del tempo è creazione del
        // processo e inizializzazione del framework, che non è codice nostro.
        assertTrue(
            FrictionBudget.timeToInkAccepted(StartKind.WARM) <
                FrictionBudget.timeToInkAccepted(StartKind.COLD),
        )
        assertEquals(100L, FrictionBudget.timeToInkAccepted(StartKind.WARM))
        assertEquals(400L, FrictionBudget.timeToInkAccepted(StartKind.COLD))
    }

    @Test
    fun `lo stesso tempo può essere dentro a freddo e oltre a caldo`() {
        fun measure(kind: StartKind): FrictionVerdict {
            val local = FakeClock(now = 0L)
            val trace = FrictionTrace(local, kind)
            trace.mark(CaptureMilestone.INTENT)
            local.advance(250)
            trace.mark(CaptureMilestone.INK_ACCEPTED)
            return trace.verdict()
        }

        assertEquals(FrictionVerdict.WITHIN_BUDGET, measure(StartKind.COLD))
        assertEquals(FrictionVerdict.OVER_BUDGET, measure(StartKind.WARM))
    }

    @Test
    fun `l'inchiostro accettato conta separatamente da quello visibile`() {
        val trace = trace(StartKind.WARM)
        trace.mark(CaptureMilestone.INTENT)
        clock.advance(60)
        trace.mark(CaptureMilestone.INK_ACCEPTED)
        clock.advance(70)
        trace.mark(CaptureMilestone.INK_DRAWN)

        // L'idea è al sicuro a 60 ms; il tratto si vede a 130. Sono due promesse
        // diverse, con due tetti diversi.
        assertEquals(60L, trace.timeToInkAccepted)
        assertEquals(130L, trace.timeToInkDrawn)
        assertEquals(FrictionVerdict.WITHIN_BUDGET, trace.verdict())
        assertEquals(FrictionVerdict.WITHIN_BUDGET, trace.drawnVerdict())
    }

    @Test
    fun `si può accettare inchiostro prima di averne disegnato`() {
        val trace = trace(StartKind.WARM)
        trace.mark(CaptureMilestone.INTENT)
        clock.advance(40)
        // La superficie di disegno riceve i tocchi appena esiste, prima del primo
        // fotogramma: l'idea è già al sicuro anche se sullo schermo non c'è ancora niente.
        trace.mark(CaptureMilestone.INK_ACCEPTED)

        assertEquals(40L, trace.timeToInkAccepted)
        assertNull(trace.timeToInkDrawn)
        assertEquals(FrictionVerdict.WITHIN_BUDGET, trace.verdict())
        assertEquals(FrictionVerdict.INCOMPLETE, trace.drawnVerdict())
    }

    @Test
    fun `esattamente il bilancio è dentro`() {
        val trace = trace(StartKind.WARM)
        trace.mark(CaptureMilestone.INTENT)
        clock.advance(FrictionBudget.timeToInkAccepted(StartKind.WARM))
        trace.mark(CaptureMilestone.INK_ACCEPTED)

        assertEquals(FrictionVerdict.WITHIN_BUDGET, trace.verdict())
    }

    @Test
    fun `un millisecondo oltre è oltre`() {
        val trace = trace(StartKind.COLD)
        trace.mark(CaptureMilestone.INTENT)
        clock.advance(FrictionBudget.timeToInkAccepted(StartKind.COLD) + 1)
        trace.mark(CaptureMilestone.INK_ACCEPTED)

        assertEquals(FrictionVerdict.OVER_BUDGET, trace.verdict())
    }

    @Test
    fun `la prima marcatura vince, perché il primo inchiostro è il primo`() {
        val trace = trace()
        trace.mark(CaptureMilestone.INTENT)
        clock.advance(100)
        trace.mark(CaptureMilestone.INK_ACCEPTED)
        clock.advance(5_000)
        trace.mark(CaptureMilestone.INK_ACCEPTED)

        assertEquals(100L, trace.timeToInkAccepted)
    }

    @Test
    fun `l'intenzione si può marcare a un istante passato`() {
        clock.set(1_000L)
        val trace = trace()
        // L'avvio del processo, che è successo prima che il nostro codice esistesse.
        trace.markAt(CaptureMilestone.INTENT, atMillis = 800L)
        trace.mark(CaptureMilestone.INK_ACCEPTED)

        assertEquals(200L, trace.timeToInkAccepted)
    }

    @Test
    fun `anche marcando a mano la prima vince`() {
        val trace = trace()
        trace.markAt(CaptureMilestone.INTENT, atMillis = 500L)
        trace.markAt(CaptureMilestone.INTENT, atMillis = 100L)
        clock.set(700L)
        trace.mark(CaptureMilestone.INK_ACCEPTED)

        assertEquals(200L, trace.timeToInkAccepted)
    }

    @Test
    fun `le tappe intermedie dicono dove si perde il tempo`() {
        val trace = trace()
        trace.mark(CaptureMilestone.INTENT)
        clock.advance(300)
        trace.mark(CaptureMilestone.SURFACE_READY)
        clock.advance(40)
        trace.mark(CaptureMilestone.FIRST_FRAME)

        assertEquals(300L, trace.span(CaptureMilestone.INTENT, CaptureMilestone.SURFACE_READY))
        assertEquals(40L, trace.span(CaptureMilestone.SURFACE_READY, CaptureMilestone.FIRST_FRAME))
        assertNull(trace.span(CaptureMilestone.INTENT, CaptureMilestone.INK_ACCEPTED))
    }

    @Test
    fun `il rapporto dice il numero, se è dentro, e da dove si partiva`() {
        val trace = trace(StartKind.COLD)
        trace.mark(CaptureMilestone.INTENT)
        clock.advance(900)
        trace.mark(CaptureMilestone.INK_ACCEPTED)

        val report = trace.report()

        assertTrue(report.contains("900ms"), report)
        assertTrue(report.contains("OLTRE"), report)
        assertTrue(report.contains("freddo"), report)
    }

    @Test
    fun `il rapporto a caldo lo dice`() {
        val trace = trace(StartKind.WARM)
        trace.mark(CaptureMilestone.INTENT)
        clock.advance(50)
        trace.mark(CaptureMilestone.INK_ACCEPTED)

        assertTrue(trace.report().contains("caldo"))
        assertTrue(trace.report().contains("entro 100ms"))
    }

    @Test
    fun `una misura incompleta lo dice invece di inventare un numero`() {
        val trace = trace()
        trace.mark(CaptureMilestone.INTENT)

        assertTrue(trace.report().contains("incompleta"))
    }
}
