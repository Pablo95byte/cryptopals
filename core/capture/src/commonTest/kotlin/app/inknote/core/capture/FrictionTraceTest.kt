package app.inknote.core.capture

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FrictionTraceTest {

    private val clock = FakeClock(now = 0L)

    private fun trace(kind: StartKind = StartKind.COLD) = FrictionTrace(clock, kind)

    @Test
    fun `senza tappe non si può giudicare`() {
        val trace = trace()

        assertEquals(FrictionVerdict.INCOMPLETE, trace.verdict())
        assertEquals(FrictionVerdict.INCOMPLETE, trace.touchVerdict())
        assertNull(trace.timeToReady)
    }

    @Test
    fun `il tetto a caldo è più severo di quello a freddo`() {
        // Non è un capriccio: a freddo la maggior parte del tempo è creazione del
        // processo e inizializzazione del framework, che non è codice nostro.
        assertEquals(100L, FrictionBudget.timeToReady(StartKind.WARM))
        assertEquals(400L, FrictionBudget.timeToReady(StartKind.COLD))
    }

    @Test
    fun `lo stesso tempo può essere dentro a freddo e oltre a caldo`() {
        fun measure(kind: StartKind): FrictionVerdict {
            val local = FakeClock(now = 0L)
            val trace = FrictionTrace(local, kind)
            trace.mark(CaptureMilestone.INTENT)
            local.advance(250)
            trace.mark(CaptureMilestone.FIRST_FRAME)
            return trace.verdict()
        }

        assertEquals(FrictionVerdict.WITHIN_BUDGET, measure(StartKind.COLD))
        assertEquals(FrictionVerdict.OVER_BUDGET, measure(StartKind.WARM))
    }

    @Test
    fun `il tempo della mano non è attrito`() {
        // Il caso reale del primo telefono: foglio pronto in 360 ms, poi l'utente ha
        // toccato dopo più di un secondo. Il misuratore di prima diceva 1468 ms, in
        // rosso, e contava la mano invece del telefono (D36).
        val trace = trace(StartKind.COLD)
        trace.mark(CaptureMilestone.INTENT)
        clock.advance(360)
        trace.mark(CaptureMilestone.FIRST_FRAME)
        clock.advance(1_100)
        trace.mark(CaptureMilestone.TOUCH)
        clock.advance(8)
        trace.mark(CaptureMilestone.INK_ACCEPTED)
        clock.advance(10)
        trace.mark(CaptureMilestone.INK_DRAWN)

        assertEquals(360L, trace.timeToReady)
        assertEquals(18L, trace.touchToInk)
        assertEquals(FrictionVerdict.WITHIN_BUDGET, trace.verdict())
        assertEquals(FrictionVerdict.WITHIN_BUDGET, trace.touchVerdict())
        assertFalse(trace.report().contains("1478"), trace.report())
    }

    @Test
    fun `il tocco si misura dall'istante dell'hardware`() {
        clock.set(1_000L)
        val trace = trace(StartKind.WARM)
        // L'evento arriva con il suo istante, che è prima di quando ce lo consegnano.
        trace.markAt(CaptureMilestone.TOUCH, atMillis = 940L)
        trace.mark(CaptureMilestone.INK_DRAWN)

        assertEquals(60L, trace.touchToInk)
        assertEquals(FrictionVerdict.OVER_BUDGET, trace.touchVerdict())
    }

    @Test
    fun `esattamente il bilancio è dentro`() {
        val trace = trace(StartKind.WARM)
        trace.mark(CaptureMilestone.INTENT)
        clock.advance(FrictionBudget.timeToReady(StartKind.WARM))
        trace.mark(CaptureMilestone.FIRST_FRAME)

        assertEquals(FrictionVerdict.WITHIN_BUDGET, trace.verdict())
    }

    @Test
    fun `un millisecondo oltre è oltre`() {
        val trace = trace(StartKind.COLD)
        trace.mark(CaptureMilestone.INTENT)
        clock.advance(FrictionBudget.timeToReady(StartKind.COLD) + 1)
        trace.mark(CaptureMilestone.FIRST_FRAME)

        assertEquals(FrictionVerdict.OVER_BUDGET, trace.verdict())
    }

    @Test
    fun `la prima marcatura vince, perché il primo inchiostro è il primo`() {
        val trace = trace()
        trace.mark(CaptureMilestone.TOUCH)
        clock.advance(20)
        trace.mark(CaptureMilestone.INK_DRAWN)
        clock.advance(5_000)
        trace.mark(CaptureMilestone.INK_DRAWN)

        assertEquals(20L, trace.touchToInk)
    }

    @Test
    fun `l'intenzione si può marcare a un istante passato`() {
        clock.set(1_000L)
        val trace = trace()
        // L'avvio del processo, che è successo prima che il nostro codice esistesse.
        trace.markAt(CaptureMilestone.INTENT, atMillis = 800L)
        trace.mark(CaptureMilestone.FIRST_FRAME)

        assertEquals(200L, trace.timeToReady)
    }

    @Test
    fun `anche marcando a mano la prima vince`() {
        val trace = trace()
        trace.markAt(CaptureMilestone.INTENT, atMillis = 500L)
        trace.markAt(CaptureMilestone.INTENT, atMillis = 100L)
        clock.set(700L)
        trace.mark(CaptureMilestone.FIRST_FRAME)

        assertEquals(200L, trace.timeToReady)
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
        assertNull(trace.touchToInk)
    }

    @Test
    fun `il rapporto dice il numero, se è dentro, e da dove si partiva`() {
        val trace = trace(StartKind.COLD)
        trace.mark(CaptureMilestone.INTENT)
        clock.advance(900)
        trace.mark(CaptureMilestone.FIRST_FRAME)

        val report = trace.report()

        assertTrue(report.contains("900ms"), report)
        assertTrue(report.contains("OLTRE"), report)
        assertTrue(report.contains("freddo"), report)
    }

    @Test
    fun `il rapporto riporta la latenza del tratto quando c'è`() {
        val trace = trace(StartKind.WARM)
        trace.mark(CaptureMilestone.INTENT)
        clock.advance(50)
        trace.mark(CaptureMilestone.FIRST_FRAME)
        clock.advance(700)
        trace.mark(CaptureMilestone.TOUCH)
        clock.advance(16)
        trace.mark(CaptureMilestone.INK_DRAWN)

        val report = trace.report()

        assertTrue(report.contains("caldo"), report)
        assertTrue(report.contains("entro 100ms"), report)
        assertTrue(report.contains("tocco→inchiostro 16ms (entro 50ms)"), report)
    }

    @Test
    fun `una misura incompleta lo dice invece di inventare un numero`() {
        val trace = trace()
        trace.mark(CaptureMilestone.INTENT)

        assertTrue(trace.report().contains("incompleta"))
    }
}
