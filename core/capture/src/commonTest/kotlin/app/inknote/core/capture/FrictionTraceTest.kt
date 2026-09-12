package app.inknote.core.capture

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FrictionTraceTest {

    private val clock = FakeClock(now = 0L)
    private val trace = FrictionTrace(clock)

    @Test
    fun `senza tappe non si può giudicare`() {
        assertEquals(FrictionVerdict.INCOMPLETE, trace.verdict())
        assertNull(trace.timeToFirstInk)
    }

    @Test
    fun `un'apertura rapida sta dentro il bilancio`() {
        trace.mark(CaptureMilestone.INTENT)
        clock.advance(120)
        trace.mark(CaptureMilestone.SURFACE_READY)
        clock.advance(90)
        trace.mark(CaptureMilestone.FIRST_FRAME)
        clock.advance(60)
        trace.mark(CaptureMilestone.FIRST_INK)

        assertEquals(270L, trace.timeToFirstInk)
        assertEquals(FrictionVerdict.WITHIN_BUDGET, trace.verdict())
    }

    @Test
    fun `oltre i 400 millisecondi la build non va rilasciata`() {
        trace.mark(CaptureMilestone.INTENT)
        clock.advance(FrictionBudget.TIME_TO_FIRST_INK_MS + 1)
        trace.mark(CaptureMilestone.FIRST_INK)

        assertEquals(FrictionVerdict.OVER_BUDGET, trace.verdict())
    }

    @Test
    fun `esattamente il bilancio è dentro`() {
        trace.mark(CaptureMilestone.INTENT)
        clock.advance(FrictionBudget.TIME_TO_FIRST_INK_MS)
        trace.mark(CaptureMilestone.FIRST_INK)

        assertEquals(FrictionVerdict.WITHIN_BUDGET, trace.verdict())
    }

    @Test
    fun `la prima marcatura vince, perché il primo inchiostro è il primo`() {
        trace.mark(CaptureMilestone.INTENT)
        clock.advance(100)
        trace.mark(CaptureMilestone.FIRST_INK)
        clock.advance(5_000)
        trace.mark(CaptureMilestone.FIRST_INK)

        assertEquals(100L, trace.timeToFirstInk)
    }

    @Test
    fun `le tappe intermedie dicono dove si perde il tempo`() {
        trace.mark(CaptureMilestone.INTENT)
        clock.advance(300)
        trace.mark(CaptureMilestone.SURFACE_READY)
        clock.advance(40)
        trace.mark(CaptureMilestone.FIRST_FRAME)

        assertEquals(300L, trace.span(CaptureMilestone.INTENT, CaptureMilestone.SURFACE_READY))
        assertEquals(40L, trace.span(CaptureMilestone.SURFACE_READY, CaptureMilestone.FIRST_FRAME))
        assertNull(trace.span(CaptureMilestone.INTENT, CaptureMilestone.FIRST_INK))
    }

    @Test
    fun `il rapporto dice il numero e se è dentro`() {
        trace.mark(CaptureMilestone.INTENT)
        clock.advance(900)
        trace.mark(CaptureMilestone.FIRST_INK)

        val report = trace.report()

        assertTrue(report.contains("900ms"), report)
        assertTrue(report.contains("OLTRE"), report)
    }

    @Test
    fun `una misura incompleta lo dice invece di inventare un numero`() {
        trace.mark(CaptureMilestone.INTENT)

        assertTrue(trace.report().contains("incompleta"))
    }
}
