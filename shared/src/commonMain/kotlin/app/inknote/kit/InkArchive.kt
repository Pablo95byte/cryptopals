package app.inknote.kit

import app.inknote.core.capture.InkJournal
import app.inknote.core.capture.InkJournalSink
import app.inknote.core.geometry.Bounds
import app.inknote.core.geometry.NoteFraming
import app.inknote.core.model.Note
import app.inknote.core.model.NoteId
import app.inknote.core.model.NoteExport
import app.inknote.core.model.DateHint
import app.inknote.core.model.DateHints
import app.inknote.core.model.ExportTarget
import app.inknote.core.model.Resurface
import app.inknote.core.model.Resurfaced
import app.inknote.core.model.VoiceClip
import app.inknote.core.model.VoiceClipId
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

    /** I percorsi relativi delle foto visibili, nell'ordine in cui sono state scattate (D38). */
    fun photoPaths(note: Note): List<String> = note.visiblePhotoClips.map { it.path }

    /**
     * Elimina davvero le note cestinate da più di trenta giorni, come su Android (D39).
     *
     * @return i percorsi di foto e registrazioni da cancellare dal disco: i file li conosce
     *   la piattaforma.
     */
    fun purge(nowMillis: Long): List<String> = store.purgeDeleted(before = nowMillis - PURGE_AFTER_DAYS * DAY_MS)

    /**
     * Il testo con cui presentare una nota nell'elenco: quello digitato, se no quello
     * letto nella scrittura, se no la prima trascrizione.
     */
    fun captionOf(note: Note): String? =
        note.typedText
            ?: note.recognizedText?.trim()?.ifEmpty { null }
            ?: note.visibleVoiceClips.firstNotNullOfOrNull { clip -> clip.transcript?.trim()?.ifEmpty { null } }

    /** Le registrazioni visibili della nota, per il foglio aperto e la condivisione (D63). */
    fun voiceItems(note: Note): List<VoiceItem> = note.visibleVoiceClips.map(VoiceItem::of)

    // --- Riconoscimento della scrittura (D2, D62) ---

    /** Le note il cui inchiostro va letto, dalla più recente. */
    fun toRecognize(limit: Int): List<Note> = store.notesNeedingRecognition(limit)

    /**
     * Il testo letto nell'inchiostro della nota alla revisione [revision].
     *
     * @return `false` se la nota è cambiata nel frattempo: resta in coda, e la prossima
     *   passata la rilegge per intero.
     */
    fun setRecognized(id: String, text: String, revision: Long): Boolean =
        store.setRecognizedText(NoteId(id), text, forRevision = revision)

    // --- Trascrizione delle registrazioni (D63) ---

    /** Le registrazioni ancora da trascrivere, dalla più vecchia. */
    fun toTranscribe(limit: Int): List<VoiceItem> = store.clipsNeedingTranscription(limit).map(VoiceItem::of)

    /** @return `false` se la registrazione aveva già una trascrizione, che non si sovrascrive. */
    fun setTranscript(clipId: String, text: String): Boolean = store.setTranscript(VoiceClipId(clipId), text)

    // --- Smistamento a carte (D52) ---

    /** Le note da smistare, dalla più vecchia. */
    fun toSort(limit: Int): List<Note> = store.notesToSort(limit)

    fun toSortCount(): Long = store.notesToSortCount()

    /** "Tieni": la nota resta nell'archivio e esce dalla coda. */
    fun keep(id: String, nowMillis: Long) {
        val note = store.note(NoteId(id)) ?: return
        store.save(note.withSorted(nowMillis))
    }

    // --- Riemersione (D52) ---

    /**
     * La nota vecchia di oggi, o `null`.
     *
     * @param utcOffsetMillis lo scarto del fuso dell'utente adesso: "oggi" è un fatto locale.
     */
    fun resurfaced(nowMillis: Long, utcOffsetMillis: Long): Resurfaced? {
        // Un giorno di margine: l'età si conta in giorni di calendario locali, e una nota
        // di sei giorni e venti ore può già essere "una settimana fa". Decide `pick`.
        val candidates = store.notesCreatedBefore(
            before = nowMillis - (Resurface.MIN_AGE_DAYS - 1) * DAY_MS,
            limit = RESURFACE_CANDIDATES,
        )
        return Resurface.pick(candidates, nowMillis, utcOffsetMillis)
    }

    // --- Date riconosciute (D52) ---

    /** Un appuntamento scritto nella nota, da proporre come promemoria, o `null`. */
    fun dateHint(note: Note, nowMillis: Long, utcOffsetMillis: Long): DateHint? =
        DateHints.find(note.searchableText, nowMillis, utcOffsetMillis)

    private companion object {
        const val DAY_MS = 86_400_000L
        const val PURGE_AFTER_DAYS = 30L

        /** Abbastanza per scegliere bene, poche per leggerle in un attimo. */
        const val RESURFACE_CANDIDATES = 400
    }
}

/**
 * Una registrazione vista da Swift (D63): tipi semplici, senza i tipi valore di Kotlin.
 *
 * @param path percorso relativo del file audio, `null` se il file non c'è più.
 * @param transcript `null` finché non è trascritta; vuota se non c'era niente da capire.
 */
data class VoiceItem(
    val id: String,
    val path: String?,
    val durationMs: Int,
    val transcript: String?,
) {
    internal companion object {
        fun of(clip: VoiceClip) = VoiceItem(
            id = clip.id.value,
            path = clip.audioPath,
            durationMs = clip.durationMs,
            transcript = clip.transcript,
        )
    }
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
