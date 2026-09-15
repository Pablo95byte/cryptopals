package app.inknote.core.capture

import app.inknote.core.model.Clock

/** Le tappe fra il gesto dell'utente e l'inchiostro. */
enum class CaptureMilestone {
    /** Il sistema ci ha consegnato l'intenzione: tocco sul widget, sul blocco, sul tasto. */
    INTENT,

    /** Il processo è in piedi e la superficie di disegno esiste. */
    SURFACE_READY,

    /** Primo fotogramma effettivamente disegnato. */
    FIRST_FRAME,

    /**
     * Primo campione di inchiostro **accettato e registrato**.
     *
     * È la tappa che conta per la missione: da qui l'idea non si perde più. Non richiede
     * che qualcosa sia stato disegnato — la superficie di disegno esiste e riceve i
     * tocchi già prima del primo fotogramma.
     */
    INK_ACCEPTED,

    /**
     * Primo inchiostro **visibile** sullo schermo.
     *
     * È la tappa che conta per la sensazione. Arriva dopo [INK_ACCEPTED] di almeno un
     * intervallo di aggiornamento dello schermo, e quel ritardo non è nostro.
     */
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

    /** Dal gesto al momento in cui l'idea è al sicuro. */
    fun timeToInkAccepted(kind: StartKind): Long = when (kind) {
        StartKind.COLD -> 400
        StartKind.WARM -> 100
    }

    /** Dal gesto al momento in cui l'utente vede il proprio tratto. */
    fun timeToInkDrawn(kind: StartKind): Long = when (kind) {
        StartKind.COLD -> 500
        StartKind.WARM -> 150
    }
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

    /** Il numero che conta per la missione: dal gesto a idea al sicuro. */
    val timeToInkAccepted: Long? get() = span(CaptureMilestone.INTENT, CaptureMilestone.INK_ACCEPTED)

    /** Il numero che conta per la sensazione: dal gesto a inchiostro visibile. */
    val timeToInkDrawn: Long? get() = span(CaptureMilestone.INTENT, CaptureMilestone.INK_DRAWN)

    fun verdict(): FrictionVerdict = verdictFor(timeToInkAccepted, FrictionBudget.timeToInkAccepted(startKind))

    fun drawnVerdict(): FrictionVerdict = verdictFor(timeToInkDrawn, FrictionBudget.timeToInkDrawn(startKind))

    private fun verdictFor(elapsed: Long?, budget: Long): FrictionVerdict = when {
        elapsed == null -> FrictionVerdict.INCOMPLETE
        elapsed <= budget -> FrictionVerdict.WITHIN_BUDGET
        else -> FrictionVerdict.OVER_BUDGET
    }

    /**
     * Riga da mostrare nel misuratore in modalità debug.
     *
     * Riporta anche le tappe intermedie: senza di quelle si sa *che* è lento e non
     * *dove*, e la cura è diversa a seconda del punto.
     */
    fun report(): String {
        val accepted = timeToInkAccepted ?: return "attrito: misura incompleta"
        val budget = FrictionBudget.timeToInkAccepted(startKind)
        val label = if (startKind == StartKind.COLD) "freddo" else "caldo"
        val marker = if (verdict() == FrictionVerdict.WITHIN_BUDGET) "entro" else "OLTRE"

        return buildString {
            append("attrito $label: ${accepted}ms ($marker ${budget}ms)")
            timeToInkDrawn?.let { append(" · visibile ${it}ms") }
            append(" · superficie ${span(CaptureMilestone.INTENT, CaptureMilestone.SURFACE_READY) ?: '?'}ms")
            append(" · 1° fotogramma ${span(CaptureMilestone.INTENT, CaptureMilestone.FIRST_FRAME) ?: '?'}ms")
        }
    }
}
