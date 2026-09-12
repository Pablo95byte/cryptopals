package app.inknote.core.capture

import app.inknote.core.model.Clock

/** Le tappe fra il gesto dell'utente e l'inchiostro sullo schermo. */
enum class CaptureMilestone {
    /** Il sistema ci ha consegnato l'intenzione: tocco sul widget, sul blocco, sul tasto. */
    INTENT,

    /** Il processo è in piedi e la superficie di disegno esiste. */
    SURFACE_READY,

    /** Primo fotogramma effettivamente disegnato. */
    FIRST_FRAME,

    /** Primo campione di inchiostro accettato. È la tappa che conta. */
    FIRST_INK,
}

/** Il tetto oltre il quale una build non si rilascia (decisione D19). */
object FrictionBudget {
    const val TIME_TO_FIRST_INK_MS: Long = 400
}

enum class FrictionVerdict {
    /** Entro il bilancio. */
    WITHIN_BUDGET,

    /** Oltre il bilancio: la build non va rilasciata così. */
    OVER_BUDGET,

    /** Tappe insufficienti per giudicare. */
    INCOMPLETE,
}

/**
 * Misura l'attrito di apertura.
 *
 * Esiste perché "attrito zero" senza un numero è uno slogan, e gli slogan non
 * sopravvivono alla prima settimana in cui qualcuno aggiunge un'animazione di
 * apertura. Questo oggetto vive nel codice di produzione, non nei test: le tappe si
 * marcano sempre, e in modalità debug il risultato si mostra a schermo.
 */
class FrictionTrace(private val clock: Clock) {

    private val marks = HashMap<CaptureMilestone, Long>()

    /**
     * Marca una tappa. Le marcature successive alla prima vengono ignorate: la tappa
     * [CaptureMilestone.FIRST_INK] è il *primo* inchiostro, non l'ultimo.
     */
    fun mark(milestone: CaptureMilestone) {
        marks.getOrPut(milestone) { clock.nowMillis() }
    }

    fun at(milestone: CaptureMilestone): Long? = marks[milestone]

    /** Millisecondi fra due tappe, o `null` se una delle due non è stata marcata. */
    fun span(from: CaptureMilestone, to: CaptureMilestone): Long? {
        val start = marks[from] ?: return null
        val end = marks[to] ?: return null
        return end - start
    }

    /** Il numero che conta: dal gesto dell'utente al primo inchiostro. */
    val timeToFirstInk: Long? get() = span(CaptureMilestone.INTENT, CaptureMilestone.FIRST_INK)

    fun verdict(): FrictionVerdict {
        val elapsed = timeToFirstInk ?: return FrictionVerdict.INCOMPLETE
        return if (elapsed <= FrictionBudget.TIME_TO_FIRST_INK_MS) {
            FrictionVerdict.WITHIN_BUDGET
        } else {
            FrictionVerdict.OVER_BUDGET
        }
    }

    /** Riga da mostrare nel misuratore in modalità debug. */
    fun report(): String {
        val total = timeToFirstInk ?: return "attrito: misura incompleta"
        val surface = span(CaptureMilestone.INTENT, CaptureMilestone.SURFACE_READY)
        val frame = span(CaptureMilestone.INTENT, CaptureMilestone.FIRST_FRAME)
        val marker = if (verdict() == FrictionVerdict.WITHIN_BUDGET) "entro" else "OLTRE"
        return "attrito: ${total}ms ($marker ${FrictionBudget.TIME_TO_FIRST_INK_MS}ms) " +
            "· superficie ${surface ?: '?'}ms · primo fotogramma ${frame ?: '?'}ms"
    }
}
