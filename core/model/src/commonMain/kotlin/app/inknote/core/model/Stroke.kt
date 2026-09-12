package app.inknote.core.model

/**
 * Un campione grezzo del tratto, così come arriva dal digitizer.
 *
 * @param x coordinata nello spazio logico del canvas.
 * @param y coordinata nello spazio logico del canvas.
 * @param pressure pressione normalizzata 0..1, oppure [NO_PRESSURE] quando il
 *   dispositivo non la riporta (dito su schermo senza force touch).
 * @param tMs millisecondi dall'inizio del tratto. Serve al calcolo dello spessore
 *   per velocità quando la pressione non c'è.
 */
data class InkPoint(
    val x: Float,
    val y: Float,
    val pressure: Float = NO_PRESSURE,
    val tMs: Int = 0,
) {
    /** `false` quando il campione non porta pressione: lo spessore va dedotto dalla velocità. */
    val hasPressure: Boolean get() = pressure >= 0f

    companion object {
        /**
         * Sentinella per "pressione non disponibile".
         *
         * Usiamo un valore negativo invece di `Float?` perché questi punti stanno
         * in liste da centinaia di elementi disegnate a 120 Hz: un tipo nullable
         * significherebbe boxing su ogni campione.
         */
        const val NO_PRESSURE: Float = -1f
    }
}

/**
 * Un tratto: una passata di penna, dal tocco al rilascio.
 *
 * I tratti sono immutabili e non vengono mai modificati dopo la creazione. Si
 * aggiungono, oppure si cancellano marcando [deletedAt]. Questa scelta è quella
 * che rende possibile il merge senza conflitti descritto in [mergeNotes].
 *
 * @param createdAt timestamp di creazione: assieme a [id] definisce l'ordine di
 *   disegno. L'ordine conta, perché un tratto sopra un altro lo copre.
 */
data class Stroke(
    val id: StrokeId,
    val pen: Pen,
    val points: List<InkPoint>,
    val createdAt: Long,
    val deletedAt: Long? = null,
) {
    val isDeleted: Boolean get() = deletedAt != null
}
