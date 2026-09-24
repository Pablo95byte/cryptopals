package app.inknote.core.capture

import app.inknote.core.ink.InkConfig
import app.inknote.core.ink.StrokeBuilder
import app.inknote.core.model.CanvasSize
import app.inknote.core.model.Clock
import app.inknote.core.model.Note
import app.inknote.core.model.NoteId
import app.inknote.core.model.Pen
import app.inknote.core.model.Stroke
import app.inknote.core.model.StrokeId
import app.inknote.core.model.orderStrokes

/**
 * Una sessione di scrittura: dall'apertura del foglio alla conferma.
 *
 * Non conosce il database, non conosce la UI e non ha dipendenze da iniettare. È
 * quello che serve perché la superficie di cattura possa accettare il primo tocco
 * al primo fotogramma (decisioni D19 e D20).
 *
 * Ogni tratto chiuso viene consegnato al giornale prima di tornare al chiamante:
 * quando [endStroke] ritorna, quell'inchiostro è su disco oppure in coda per
 * andarci, a seconda di come la piattaforma scrive (D35).
 */
class CaptureSession(
    val canvas: CanvasSize,
    private val journal: InkJournal,
    private val clock: Clock,
    val noteId: NoteId = NoteId.random(),
    private val config: InkConfig = InkConfig.Default,
    val createdAt: Long = clock.nowMillis(),
) {

    private val strokes = ArrayList<Stroke>()
    private var active: StrokeBuilder? = null

    /**
     * Quante volte la scrittura sul giornale è fallita.
     *
     * Non è una statistica: se è maggiore di zero l'interfaccia deve avvisare, perché
     * il disco pieno è l'unico caso in cui la promessa "al sicuro dal primo tratto"
     * non vale. L'inchiostro resta comunque in memoria — vedi [endStroke].
     */
    var journalFailures: Int = 0
        private set

    val strokeCount: Int get() = strokes.size

    val isEmpty: Boolean get() = strokes.isEmpty()

    val isDrawing: Boolean get() = active != null

    /**
     * Inizia un tratto.
     *
     * @return `false` se un tratto è già in corso. Un secondo contatto mentre si
     *   scrive è il palmo della mano o un dito appoggiato, non un secondo tratto
     *   voluto: va ignorato, non disegnato.
     */
    fun beginStroke(pen: Pen, id: StrokeId = StrokeId.random()): Boolean {
        if (active != null) return false
        active = StrokeBuilder(pen = pen, startedAt = clock.nowMillis(), config = config, id = id)
        return true
    }

    /** Registra un campione nel tratto in corso. `false` se non c'è un tratto aperto. */
    fun addSample(x: Float, y: Float, pressure: Float, tMs: Int): Boolean =
        active?.add(x = x, y = y, pressure = pressure, tMs = tMs) ?: false

    /**
     * Chiude il tratto in corso e lo mette al sicuro nel giornale.
     *
     * @return il tratto, oppure `null` se non c'era nulla da chiudere.
     */
    fun endStroke(): Stroke? {
        val builder = active ?: return null
        active = null

        val stroke = builder.build() ?: return null
        strokes += stroke

        // Se il giornale non accetta la scrittura — disco pieno, permessi — il tratto
        // resta comunque in memoria. Far sparire dallo schermo l'inchiostro appena
        // disegnato sarebbe il comportamento peggiore possibile: l'utente perde
        // fiducia nell'app e non saprà mai perché.
        runCatching {
            journal.record(
                JournalRecord(
                    noteId = noteId,
                    noteCreatedAt = createdAt,
                    canvas = canvas,
                    stroke = stroke,
                ),
            )
        }.onFailure { journalFailures++ }

        return stroke
    }

    /** Butta via il tratto in corso: gesto annullato, tocco accidentale. */
    fun cancelStroke() {
        active = null
    }

    /** La nota così com'è adesso, nell'ordine di disegno canonico. */
    fun note(): Note {
        val ordered = orderStrokes(strokes)
        return Note(
            id = noteId,
            canvas = canvas,
            strokes = ordered,
            createdAt = createdAt,
            updatedAt = ordered.maxOfOrNull { it.createdAt } ?: createdAt,
            revision = ordered.size + 1L,
        )
    }
}
