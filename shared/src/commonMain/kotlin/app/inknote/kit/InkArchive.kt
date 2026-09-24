package app.inknote.kit

import app.inknote.core.capture.InkJournal
import app.inknote.core.capture.InkJournalSink
import app.inknote.core.geometry.Bounds
import app.inknote.core.geometry.NoteFraming
import app.inknote.core.model.Note
import app.inknote.core.model.NoteId
import app.inknote.core.model.NoteExport
import app.inknote.core.model.ExportTarget
import app.inknote.core.store.JournalIngest
import app.inknote.core.store.NoteStore

/**
 * L'archivio visto da Swift (D48): le stesse operazioni dell'archivio Android, con tipi
 * semplici. L'API è sincrona come `NoteStore` (D14): Swift la chiama da una coda sua,
 * mai dal thread dell'interfaccia.
 */
class InkArchive(private val store: NoteStore) {

    /**
     * Porta in archivio quello che il foglio ha scritto nel giornale: prima si salva, poi
     * si svuota (D22). Poi l'indice di ricerca vecchio si ricalcola (D27).
     *
     * @return `true` se una parte del giornale era illeggibile: un tratto si è perso, e
     *   l'interfaccia deve dirlo.
     */
    fun ingest(journalSink: InkJournalSink): Boolean {
        val result = JournalIngest(store).ingest(InkJournal(journalSink))
        store.reindexSearch()
        return result.hadTornTail
    }

    fun recent(limit: Int): List<Note> = store.recentNotes(limit)

    fun search(term: String, limit: Int): List<Note> = store.search(term, limit)

    fun note(id: String): Note? = store.note(NoteId(id))

    fun count(): Long = store.liveNoteCount()

    /** Un tombstone, non una cancellazione (D8, D26). */
    fun delete(id: String, nowMillis: Long) {
        store.markDeleted(NoteId(id), nowMillis)
    }

    /** Registra che la nota è stata mandata col foglio di condivisione (D31). */
    fun markShared(id: String, nowMillis: Long) {
        val note = store.note(NoteId(id)) ?: return
        store.save(note.withExport(ExportTarget.SystemShare, nowMillis))
    }

    /** Il testo da mandare fuori, già composto, o `null` se non c'è testo (D31). */
    fun shareText(note: Note, dateLabel: String): String? = NoteExport.prepare(note, dateLabel)?.text

    /** L'id come stringa: da Swift i tipi valore di Kotlin non sono comodi. */
    fun idOf(note: Note): String = note.id.value
}

/**
 * L'inchiostro di una nota inquadrato in un riquadro, pronto da riempire: per l'elenco e
 * per la nota aperta. Si inquadra l'inchiostro, non il foglio (`NoteFraming`).
 */
object InkPreview {

    fun shapes(note: Note, width: Float, height: Float, padding: Float): List<InkShape> {
        val innerWidth = width - 2 * padding
        val innerHeight = height - 2 * padding
        if (innerWidth <= 0f || innerHeight <= 0f) return emptyList()
        val frame = NoteFraming.fit(note, Bounds(0f, 0f, innerWidth, innerHeight)) ?: return emptyList()
        val offsetX = padding + (innerWidth - frame.source.width * frame.scale) / 2f - frame.source.minX * frame.scale
        val offsetY = padding + (innerHeight - frame.source.height * frame.scale) / 2f - frame.source.minY * frame.scale
        return note.visibleStrokes.map { stroke ->
            InkShape.of(stroke, frame.quality, frame.scale, offsetX, offsetY)
        }
    }

    /** L'altezza giusta per l'inchiostro a una certa larghezza, dentro dei limiti. */
    fun heightFor(note: Note, width: Float, min: Float, max: Float): Float {
        val ink = Bounds.of(note)?.inflate(8f) ?: return min
        if (ink.width <= 0f) return min
        return (width * ink.height / ink.width).coerceIn(min, max)
    }
}
