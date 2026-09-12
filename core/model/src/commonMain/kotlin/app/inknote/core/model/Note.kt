package app.inknote.core.model

/**
 * Dimensione logica del foglio su cui sono espresse le coordinate dei tratti.
 *
 * I tratti non sono salvati in pixel dello schermo: sono salvati in queste unità,
 * e ogni renderer scala di `target / canvas`. Senza questo, la stessa nota
 * risulterebbe di dimensioni diverse sul telefono, sul widget piccolo e sul
 * widget grande, e su un futuro iPad non sarebbe rileggibile.
 */
data class CanvasSize(val width: Float, val height: Float) {
    init {
        require(width > 0f && height > 0f) { "canvas non valido: ${width}x$height" }
    }
}

/**
 * Una nota scritta a mano.
 *
 * ## Campi pensati per il sync
 *
 * La v1 è tutta locale, ma i campi che servono al sync sono qui dal primo giorno:
 * [updatedAt], [revision] e [deletedAt]. Aggiungerli dopo avrebbe voluto dire una
 * migrazione sui dati degli utenti già installati, che è il modo più rapido di
 * perdere note altrui e prendersi una stella.
 *
 * @param revision contatore monotono incrementato a ogni modifica locale. Serve a
 *   capire se [recognizedText] è ancora valido e a ordinare le versioni quando i
 *   timestamp di due dispositivi non sono allineati.
 * @param deletedAt tombstone. Non cancelliamo le righe: una cancellazione deve
 *   poter viaggiare, altrimenti al primo sync la nota cancellata torna indietro.
 * @param recognizedText esito dell'OCR, `null` se non ancora calcolato.
 * @param recognizedFromRevision revisione su cui l'OCR è stato calcolato: se è
 *   diversa da [revision], il testo è vecchio e va ricalcolato.
 */
data class Note(
    val id: NoteId,
    val canvas: CanvasSize,
    val strokes: List<Stroke>,
    val createdAt: Long,
    val updatedAt: Long,
    val revision: Long = 1L,
    val deletedAt: Long? = null,
    val recognizedText: String? = null,
    val recognizedFromRevision: Long? = null,
) {
    val isDeleted: Boolean get() = deletedAt != null

    /** I tratti da disegnare, nell'ordine di disegno. */
    val visibleStrokes: List<Stroke> get() = strokes.filter { !it.isDeleted }

    val isEmpty: Boolean get() = visibleStrokes.isEmpty()

    /** `true` se [recognizedText] si riferisce a una versione precedente della nota. */
    val needsRecognition: Boolean get() = recognizedFromRevision != revision

    /** Aggiunge un tratto producendo una nuova versione della nota. */
    fun withStroke(stroke: Stroke, now: Long): Note = copy(
        strokes = orderStrokes(strokes + stroke),
        updatedAt = now,
        revision = revision + 1,
    )

    /** Marca un tratto come cancellato (gomma) senza rimuoverlo dalla lista. */
    fun withStrokeDeleted(strokeId: StrokeId, now: Long): Note {
        val index = strokes.indexOfFirst { it.id == strokeId }
        if (index < 0 || strokes[index].isDeleted) return this
        return copy(
            strokes = strokes.toMutableList().also { it[index] = it[index].copy(deletedAt = now) },
            updatedAt = now,
            revision = revision + 1,
        )
    }

    companion object {
        fun empty(canvas: CanvasSize, now: Long, id: NoteId = NoteId.random()): Note = Note(
            id = id,
            canvas = canvas,
            strokes = emptyList(),
            createdAt = now,
            updatedAt = now,
        )
    }
}

/**
 * Ordine di disegno canonico: prima gli evidenziatori, poi per istante di
 * creazione, e a parità per id.
 *
 * Deve essere una funzione totale e deterministica, non l'ordine di arrivo: due
 * dispositivi che hanno fuso la stessa nota devono disegnarla identica, e
 * l'evidenziatore va sotto l'inchiostro o lo copre.
 */
fun orderStrokes(strokes: List<Stroke>): List<Stroke> = strokes.sortedWith(
    compareBy({ if (it.pen.kind == PenKind.HIGHLIGHTER) 0 else 1 }, { it.createdAt }, { it.id.value }),
)
