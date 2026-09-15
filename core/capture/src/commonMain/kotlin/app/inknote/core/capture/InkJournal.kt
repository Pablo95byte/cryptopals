package app.inknote.core.capture

import app.inknote.core.model.Note
import app.inknote.core.model.orderStrokes

/**
 * Il giornale di scrittura dell'inchiostro.
 *
 * ## A cosa serve
 *
 * A rendere vera una frase: **la nota è al sicuro dal primo tratto, non dalla
 * conferma.** Ogni tratto chiuso viene aggiunto in coda a un file e forzato su
 * disco, subito, senza passare dal database. Se l'utente rimette il telefono in
 * tasca, preme il tasto laterale, riceve una chiamata o il sistema uccide l'app,
 * l'inchiostro c'è già.
 *
 * ## Perché non il database
 *
 * Aprire SQLite, applicare le migrazioni e iniziare una transazione costa
 * centinaia di millisecondi all'avvio a freddo: è precisamente il bilancio dei
 * 400 ms (decisione D19). Un `append` su file aperto è nell'ordine dei
 * microsecondi. Il database assorbe il giornale dopo, quando nessuno sta
 * aspettando.
 *
 * Questo modulo **non dipende** da `:core:store`, e la separazione è voluta: la
 * regola la fa rispettare il compilatore, non un commento.
 */
class InkJournal(private val sink: InkJournalSink) {

    /** Scrive un tratto in coda al giornale. */
    fun record(record: JournalRecord) {
        sink.append(JournalCodec.encode(record))
    }

    /** Rilegge il giornale così com'è, coda troncata compresa. */
    fun read(): JournalReadResult = JournalCodec.decodeAll(sink.readAll())

    /**
     * Ricostruisce le note presenti nel giornale, pronte da fondere in archivio con
     * `mergeNotes`.
     *
     * Una nota recuperata può già esistere in archivio in una versione più vecchia:
     * l'unione per id dei tratti fa il resto, senza conflitti (decisione D8).
     */
    fun recover(): List<RecoveredNote> {
        val result = read()
        return result.records
            .groupBy { it.noteId }
            .map { (noteId, records) ->
                val strokes = orderStrokes(records.map { it.stroke })
                val first = records.first()
                RecoveredNote(
                    note = Note(
                        id = noteId,
                        canvas = first.canvas,
                        strokes = strokes,
                        createdAt = first.noteCreatedAt,
                        updatedAt = strokes.maxOfOrNull { it.createdAt } ?: first.noteCreatedAt,
                        // Una revisione per tratto, come se la nota fosse stata
                        // costruita tratto per tratto: così il merge con la copia in
                        // archivio non la considera più vecchia di quello che è.
                        revision = strokes.size + 1L,
                    ),
                    hadTornTail = result.hadTornTail,
                )
            }
    }

    /**
     * Svuota il giornale.
     *
     * Da chiamare **solo** dopo che i record sono in archivio. Svuotarlo prima è
     * l'unico modo di perdere inchiostro con questo meccanismo.
     */
    fun clear() {
        sink.clear()
    }
}

/**
 * Una nota ritrovata nel giornale.
 *
 * @param hadTornTail `true` se la coda del giornale era illeggibile, cioè il
 *   processo è morto durante la scrittura di un tratto. Va portato fino
 *   all'interfaccia: è meglio dire all'utente che un tratto si è perso che fargli
 *   scoprire da solo una nota incompleta.
 */
data class RecoveredNote(
    val note: Note,
    val hadTornTail: Boolean,
)
