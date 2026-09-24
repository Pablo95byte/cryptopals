package app.inknote.core.store

import app.inknote.core.capture.InkJournal
import app.inknote.core.model.mergeNotes

/**
 * Porta l'inchiostro dal giornale di scrittura all'archivio.
 *
 * È il punto in cui le due metà del percorso si incontrano: la cattura scrive in
 * fretta e senza database (decisione D20), questo assorbe con calma quando nessuno
 * sta aspettando — all'avvio dell'app, o dopo la conferma.
 *
 * ## L'ordine delle operazioni è la correttezza
 *
 * Prima si salva, **poi** si svuota il giornale. Mai il contrario, e mai svuotarlo
 * "tanto poi salviamo": se il salvataggio non riesce, il giornale resta lì ed è
 * l'unica copia dell'inchiostro. Un giornale svuotato troppo presto è il solo modo
 * di perdere una nota con questo meccanismo.
 */
class JournalIngest(private val store: NoteStore) {

    /**
     * @param clearOnSuccess se svuotare il giornale dopo aver salvato tutto. Passare
     *   `false` serve a ispezionare senza consumare.
     */
    fun ingest(journal: InkJournal, clearOnSuccess: Boolean = true): IngestResult {
        val recovery = journal.recover()

        for (recovered in recovery.notes) {
            val existing = store.note(recovered.id)
            // La fusione, non la sostituzione: in archivio possono esserci tratti che
            // il giornale non ha mai visto, e viceversa. L'unione per id non perde
            // niente da nessuna delle due parti (decisione D8).
            val toSave = if (existing == null) recovered else mergeNotes(existing, recovered)
            store.save(toSave)
        }

        // Anche con zero note: dei byte illeggibili non diventeranno mai leggibili, e
        // lasciarli lì li farebbe rileggere e segnalare a ogni avvio.
        if (clearOnSuccess) journal.discard(recovery)

        return IngestResult(
            notesIngested = recovery.notes.size,
            hadTornTail = recovery.hadTornTail,
        )
    }
}

/**
 * @param hadTornTail `true` se il giornale era troncato, cioè un tratto si è perso
 *   con la morte del processo. Va portato fino all'interfaccia: meglio dirlo che
 *   lasciare che l'utente trovi una nota incompleta senza spiegazione.
 */
data class IngestResult(
    val notesIngested: Int,
    val hadTornTail: Boolean,
)
