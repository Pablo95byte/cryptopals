package app.inknote.core.store

import app.inknote.core.model.Note
import app.inknote.core.model.NoteId

/**
 * L'archivio locale delle note.
 *
 * ## Sincrono, per ora
 *
 * Le operazioni sono sincrone e senza flussi osservabili. È una scelta, non una
 * dimenticanza: sulle quantità in gioco — note singole, elenchi di poche decine —
 * SQLite locale risponde in frazioni di millisecondo, e un'API sincrona è
 * verificabile e identica su iOS e Android. Chi chiama decide su quale thread
 * stare, e l'elenco si ricarica interrogando di nuovo. Se e quando servirà
 * l'osservazione continua, si aggiunge sopra senza cambiare questo contratto.
 *
 * ## Il salvataggio unisce, non sostituisce
 *
 * [save] scrive la nota e i tratti che le appartengono, ma **non cancella** i
 * tratti già in archivio che non compaiono nella lista. È coerente con
 * l'invariante del modello: un tratto non si rimuove, si marca cancellato. Così
 * un salvataggio partito da una copia incompleta della nota non può far sparire
 * inchiostro.
 */
interface NoteStore {

    /** La nota con questo id, tombstone compresi, oppure `null` se non esiste. */
    fun note(id: NoteId): Note?

    /** Le note vive, dalla più recente. */
    fun recentNotes(limit: Int = 200): List<Note>

    /**
     * Le note vive il cui testo riconosciuto contiene [term].
     *
     * Cerca nel testo prodotto dall'OCR, non nell'inchiostro: una nota non ancora
     * riconosciuta non compare nei risultati. È il compromesso della decisione D2
     * e l'interfaccia deve renderlo evidente all'utente.
     */
    fun search(term: String, limit: Int = 50): List<Note>

    /** Le note il cui testo riconosciuto è assente o si riferisce a una revisione precedente. */
    fun notesNeedingRecognition(limit: Int = 20): List<Note>

    fun liveNoteCount(): Long

    /** Scrive la nota e i suoi tratti in una sola transazione. */
    fun save(note: Note)

    /** Marca la nota cancellata senza rimuoverla: la cancellazione deve poter viaggiare. */
    fun markDeleted(id: NoteId, now: Long)

    /** Percorso della PNG ridotta usata dal widget, oppure `null` se non ancora generata. */
    fun widgetImagePath(id: NoteId): String?

    fun setWidgetImagePath(id: NoteId, path: String?)

    /**
     * Rimuove definitivamente le note cancellate prima di [before], tratti compresi.
     *
     * È l'unico punto in cui qualcosa viene davvero eliminato. Serve perché i
     * tombstone non possono crescere per sempre, e va chiamato con una finestra
     * abbondante: un tombstone eliminato prima che tutti i dispositivi l'abbiano
     * visto fa riapparire la nota al sync successivo.
     */
    fun purgeDeleted(before: Long)
}
