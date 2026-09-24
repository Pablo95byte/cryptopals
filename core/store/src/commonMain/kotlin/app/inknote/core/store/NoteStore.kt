package app.inknote.core.store

import app.inknote.core.model.ExportTarget
import app.inknote.core.model.Note
import app.inknote.core.model.NoteId
import app.inknote.core.model.VoiceClip
import app.inknote.core.model.VoiceClipId

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
     * Cerca nel testo prodotto dall'OCR sull'inchiostro **e** nelle trascrizioni del
     * parlato: per chi cerca sono la stessa cosa. Una nota non ancora riconosciuta non
     * compare nei risultati: è il compromesso della decisione D2, e l'interfaccia deve
     * renderlo evidente all'utente invece di far sembrare che la nota non esista.
     *
     * Il confronto ignora accenti e maiuscole e **non chiede l'ordine delle parole**:
     * "pane latte" trova "latte, pane, caffè" (D27). I risultati escono dal più
     * pertinente, non dal più recente.
     */
    fun search(term: String, limit: Int = 50): List<Note>

    /**
     * Le note il cui testo riconosciuto è assente o si riferisce a una revisione
     * precedente.
     *
     * Le note di sola voce non compaiono: non hanno inchiostro da leggere, e tenerle in
     * coda la farebbe girare a vuoto per sempre.
     */
    fun notesNeedingRecognition(limit: Int = 20): List<Note>

    /** Le registrazioni vocali ancora da trascrivere, dalla più vecchia. */
    fun clipsNeedingTranscription(limit: Int = 20): List<VoiceClip>

    /**
     * Scrive il testo riconosciuto sull'inchiostro (D2, D62), **solo** se la nota è
     * ancora alla revisione [forRevision], quella su cui il riconoscimento è stato fatto.
     *
     * Il riconoscimento dura secondi, fuori da ogni transazione: se intanto la nota è
     * cresciuta, il testo descrive un inchiostro che non c'è più, e scriverlo la
     * toglierebbe dalla coda con un indice incompleto.
     *
     * @return `true` se il testo è stato scritto.
     */
    fun setRecognizedText(id: NoteId, text: String, forRevision: Long): Boolean

    /**
     * Scrive la trascrizione di una registrazione (D25, D63). Una trascrizione già
     * presente non si sovrascrive: una registrazione non cambia, e la sua trascrizione è
     * definitiva.
     *
     * @return `true` se la trascrizione è stata scritta.
     */
    fun setTranscript(clipId: VoiceClipId, transcript: String): Boolean

    /**
     * Le note il cui indice di ricerca è stato calcolato da una normalizzazione
     * precedente, o mai.
     *
     * Ci finiscono le note salvate da una versione più vecchia dell'app e quelle
     * arrivate da una migrazione, che non può normalizzare da sola (lo `lower()` di
     * SQLite non sa togliere gli accenti).
     */
    fun notesNeedingSearchIndex(limit: Int = 50): List<Note>

    /**
     * Le note vive che non sono mai state mandate a [target], o che sono cresciute dopo
     * l'ultimo invio.
     *
     * È la coda di "manda tutte quelle nuove" (D31): tenerla come interrogazione, e non
     * come stato da mantenere, evita che si disallinei.
     */
    fun notesToSend(target: ExportTarget, limit: Int = 50): List<Note>

    /**
     * Ricalcola l'indice di ricerca delle note che ne hanno bisogno.
     *
     * Da chiamare all'avvio, fuori dal percorso critico. Ritorna quante note ha
     * reindicizzato: se il numero è pari al limite, conviene richiamarla.
     */
    fun reindexSearch(limit: Int = 50): Int

    fun liveNoteCount(): Long

    /**
     * La coda dello smistamento a carte (D52): note vive con qualcosa dentro, mai tenute né
     * mandate fuori, dalla più vecchia.
     */
    fun notesToSort(limit: Int = 100): List<Note>

    fun notesToSortCount(): Long

    /** Le candidate alla riemersione (D52): note vive scritte prima di [before], dalla più recente. */
    fun notesCreatedBefore(before: Long, limit: Int = 500): List<Note>

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
     *
     * @return i percorsi relativi dei file delle note eliminate — foto e registrazioni: i
     *   file stanno su disco e non nel database, e toccherà alla piattaforma cancellarli
     *   (D38, D63).
     */
    fun purgeDeleted(before: Long): List<String>
}
