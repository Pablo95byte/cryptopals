package app.inknote.core.capture

import app.inknote.core.model.Clock

/**
 * Le tappe fra il gesto dell'utente e l'inchiostro.
 *
 * Sono di due famiglie, e non vanno sommate (D36). Da [INTENT] a [FIRST_FRAME] è il
 * **telefono** che lavora: è il tempo che l'utente aspetta. Da [TOUCH] a [INK_DRAWN] è
 * di nuovo il telefono, ma **dopo che l'utente ha deciso di toccare**. In mezzo c'è la
 * mano, che ci mette quello che ci mette e non è attrito nostro.
 */
enum class CaptureMilestone {
    /** Il sistema ci ha consegnato l'intenzione: tocco sul widget, sul blocco, sul tasto. */
    INTENT,

    /** Il processo è in piedi e la superficie di disegno esiste. */
    SURFACE_READY,

    /**
     * Primo fotogramma disegnato: il foglio è sullo schermo e riceve i tocchi.
     *
     * È la tappa che conta per la missione. Da qui un dito che tocca il vetro lascia
     * inchiostro; prima, il tocco non arriverebbe a noi.
     */
    FIRST_FRAME,

    /** Il dito tocca il vetro: l'istante dell'hardware, non quello in cui ce lo dicono. */
    TOUCH,

    /** Primo campione di inchiostro accettato e registrato. */
    INK_ACCEPTED,

    /** Primo inchiostro visibile sullo schermo. */
    INK_DRAWN,
}

/**
 * Da dove parte l'apertura. Sono due fenomeni fisici diversi e non vanno mescolati.
 */
enum class StartKind {
    /**
     * Il processo non esisteva. Va creato dal sistema, le classi vanno caricate e
     * verificate, il framework va inizializzato: la maggior parte di quel tempo **non è
     * nostra** e non si ottimizza dal nostro lato.
     */
    COLD,

    /**
     * Il processo era già vivo. Resta la nostra parte più un fotogramma, ed è qui che si
     * può essere davvero rapidi.
     */
    WARM,
}

/**
 * I tetti, per tappa e per tipo di apertura (D32).
 *
 * **Perché a freddo non si scende a 100 ms.** Fra il tocco e il nostro primo istruzione
 * il sistema fa: gestione del tocco nel launcher, creazione del processo, caricamento e
 * verifica delle classi, inizializzazione del framework. Nessuno di quei passaggi è
 * codice nostro e nessuno si può saltare. La nostra parte — `onCreate`, la superficie di
 * disegno, il primo fotogramma — è la fetta più piccola. Un tetto di 100 ms a freddo
 * sarebbe un numero che non si può rispettare, e un tetto che non si rispetta viene
 * ignorato: tanto vale non averlo.
 *
 * **Perché a caldo sì.** Con il processo già vivo resta la nostra parte più un
 * fotogramma. Lì 100 ms sono ambiziosi ma raggiungibili, ed è il caso più frequente per
 * chi usa l'app ogni giorno.
 *
 * **La conseguenza strategica:** il modo di stare a 100 ms il più spesso possibile non è
 * scrivere codice più furbo, è **restare piccoli** — un processo leggero sopravvive più a
 * lungo nella cache del sistema, quindi la seconda apertura della giornata è calda. È la
 * seconda ragione, arrivata dopo, per cui `:androidApp` non ha nessuna dipendenza
 * esterna (D23).
 */
object FrictionBudget {

    /** Dal gesto al foglio pronto a ricevere inchiostro (D32, D36). */
    fun timeToReady(kind: StartKind): Long = when (kind) {
        StartKind.COLD -> 400
        StartKind.WARM -> 100
    }

    /**
     * Dal dito sul vetro all'inchiostro disegnato: se il tratto resta indietro rispetto
     * al dito, scrivere diventa difficile. Vale uguale a freddo e a caldo, perché a quel
     * punto il foglio c'è già (D36).
     */
    const val TOUCH_TO_INK_MS: Long = 50
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
 * sopravvivono alla prima settimana in cui qualcuno aggiunge un'animazione di apertura.
 * Vive nel codice di produzione, non nei test: un numero che nessuno vede è un numero
 * che peggiora.
 *
 * @param startKind se il processo esisteva già. Cambia il tetto, non la misura.
 */
class FrictionTrace(
    private val clock: Clock,
    val startKind: StartKind,
) {

    private val marks = HashMap<CaptureMilestone, Long>()

    /**
     * Marca una tappa. Le marcature successive alla prima vengono ignorate: la tappa
     * del primo inchiostro è il *primo*, non l'ultimo.
     */
    fun mark(milestone: CaptureMilestone) {
        marks.getOrPut(milestone) { clock.nowMillis() }
    }

    /**
     * Marca una tappa a un istante già noto.
     *
     * Serve per [CaptureMilestone.INTENT]: su un avvio a freddo il momento in cui
     * l'utente ha toccato è **prima** che il nostro codice esista, e l'unico riferimento
     * onesto è l'avvio del processo, che il sistema ci sa dire. Marcare l'intenzione
     * all'ingresso di `onCreate` misurerebbe un tempo più breve di quello che l'utente ha
     * davvero aspettato.
     */
    fun markAt(milestone: CaptureMilestone, atMillis: Long) {
        marks.getOrPut(milestone) { atMillis }
    }

    fun at(milestone: CaptureMilestone): Long? = marks[milestone]

    /** Millisecondi fra due tappe, o `null` se una delle due non è stata marcata. */
    fun span(from: CaptureMilestone, to: CaptureMilestone): Long? {
        val start = marks[from] ?: return null
        val end = marks[to] ?: return null
        return end - start
    }

    /** Il numero che conta per la missione: dal gesto al foglio pronto. */
    val timeToReady: Long? get() = span(CaptureMilestone.INTENT, CaptureMilestone.FIRST_FRAME)

    /** Il numero che conta mentre si scrive: dal dito sul vetro all'inchiostro visibile. */
    val touchToInk: Long? get() = span(CaptureMilestone.TOUCH, CaptureMilestone.INK_DRAWN)

    fun verdict(): FrictionVerdict = verdictFor(timeToReady, FrictionBudget.timeToReady(startKind))

    fun touchVerdict(): FrictionVerdict = verdictFor(touchToInk, FrictionBudget.TOUCH_TO_INK_MS)

    private fun verdictFor(elapsed: Long?, budget: Long): FrictionVerdict = when {
        elapsed == null -> FrictionVerdict.INCOMPLETE
        elapsed <= budget -> FrictionVerdict.WITHIN_BUDGET
        else -> FrictionVerdict.OVER_BUDGET
    }

    /**
     * Riga da mostrare nel misuratore in modalità debug.
     *
     * Riporta anche la tappa intermedia: senza si sa *che* è lento e non *dove*, e la
     * cura è diversa a seconda del punto. **Non** riporta il tempo fra il foglio pronto
     * e il primo tocco: è quanto ci mette la mano, e mostrarlo come attrito era l'errore
     * che D36 ha corretto.
     */
    fun report(): String {
        val ready = timeToReady ?: return "attrito: misura incompleta"
        val budget = FrictionBudget.timeToReady(startKind)
        val label = if (startKind == StartKind.COLD) "freddo" else "caldo"

        return buildString {
            append("pronto $label: ${ready}ms (${marker(verdict())} ${budget}ms)")
            touchToInk?.let {
                append(" · tocco→inchiostro ${it}ms (${marker(touchVerdict())} ${FrictionBudget.TOUCH_TO_INK_MS}ms)")
            }
            append(" · superficie ${span(CaptureMilestone.INTENT, CaptureMilestone.SURFACE_READY) ?: '?'}ms")
        }
    }

    private fun marker(verdict: FrictionVerdict) = if (verdict == FrictionVerdict.WITHIN_BUDGET) "entro" else "OLTRE"
}
