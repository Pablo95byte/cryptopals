package app.inknote.core.capture

import app.inknote.core.model.Note
import app.inknote.core.model.NoteId
import app.inknote.core.model.orderPhotoClips
import app.inknote.core.model.orderStrokes
import app.inknote.core.model.orderTextClips
import app.inknote.core.model.orderVoiceClips

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
    fun recover(): JournalRecovery {
        val result = read()
        val notes = result.records
            .groupBy { it.noteId }
            .map { (noteId, records) -> rebuild(noteId, records) }
        return JournalRecovery(
            notes = notes,
            hadTornTail = result.hadTornTail,
            consumedBytes = result.consumedBytes,
        )
    }

    private fun rebuild(noteId: NoteId, records: List<JournalRecord>): Note {
        val first = records.first()
        val strokes = orderStrokes(records.mapNotNull { it.stroke })
        // Un testo corretto arriva due volte, la versione vecchia col tombstone e poi di
        // nuovo: si tiene una copia per id, e il tombstone vince (D8, D38).
        val texts = records.mapNotNull { (it.item as? JournalItem.Text)?.clip }
            .groupBy { it.id }
            .map { (_, copies) -> copies.firstOrNull { it.isDeleted } ?: copies.first() }
        val photos = records.mapNotNull { (it.item as? JournalItem.Photo)?.clip }
            .groupBy { it.id }
            .map { (_, copies) -> copies.firstOrNull { it.isDeleted } ?: copies.first() }
        // Una registrazione può arrivare più volte (una trascrizione aggiunta dopo, un
        // tombstone): si fondono i campi, e fra "c'è" e "non c'è ancora" vince "c'è" (D26).
        val voices = records.mapNotNull { (it.item as? JournalItem.Voice)?.clip }
            .groupBy { it.id }
            .map { (_, copies) ->
                copies.first().copy(
                    durationMs = copies.maxOf { it.durationMs },
                    transcript = copies.firstNotNullOfOrNull { it.transcript },
                    audioPath = copies.firstNotNullOfOrNull { it.audioPath },
                    deletedAt = copies.mapNotNull { it.deletedAt }.minOrNull(),
                )
            }

        val parts = strokes.size + texts.size + photos.size + voices.size
        val latest = listOfNotNull(
            strokes.maxOfOrNull { it.createdAt },
            texts.maxOfOrNull { it.deletedAt ?: it.writtenAt },
            photos.maxOfOrNull { it.deletedAt ?: it.takenAt },
            voices.maxOfOrNull { it.deletedAt ?: it.recordedAt },
        ).maxOrNull()

        return Note(
            id = noteId,
            canvas = first.canvas,
            strokes = strokes,
            voiceClips = orderVoiceClips(voices),
            textClips = orderTextClips(texts),
            photoClips = orderPhotoClips(photos),
            createdAt = first.noteCreatedAt,
            updatedAt = latest ?: first.noteCreatedAt,
            // Una revisione per pezzo, come se la nota fosse stata costruita pezzo per
            // pezzo: così il merge con la copia in archivio non la considera più
            // vecchia di quello che è.
            revision = parts + 1L,
        )
    }

    /**
     * Toglie dal giornale ciò che [recovery] ha letto, e **solo** quello.
     *
     * Da chiamare **solo** dopo che quelle note sono in archivio: svuotare prima è
     * l'unico modo di perdere inchiostro con questo meccanismo (D22). Un tratto
     * arrivato dopo la lettura resta dov'è, e lo prenderà l'assorbimento successivo.
     */
    fun discard(recovery: JournalRecovery) {
        sink.discardPrefix(recovery.consumedBytes)
    }
}

/**
 * Le note ritrovate nel giornale.
 *
 * @param hadTornTail `true` se una parte del giornale era illeggibile, cioè il
 *   processo è morto durante la scrittura di un tratto. Va portato fino
 *   all'interfaccia: è meglio dire all'utente che un tratto si è perso che fargli
 *   scoprire da solo una nota incompleta.
 * @param consumedBytes quanto del giornale è stato letto: è ciò che [InkJournal.discard]
 *   toglierà, e nient'altro.
 */
data class JournalRecovery(
    val notes: List<Note>,
    val hadTornTail: Boolean,
    val consumedBytes: Int,
) {
    val isEmpty: Boolean get() = notes.isEmpty()
}
