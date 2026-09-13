package app.inknote.core.store

import app.cash.sqldelight.db.SqlDriver
import app.inknote.core.model.CanvasSize
import app.inknote.core.model.ExportRecord
import app.inknote.core.model.ExportTarget
import app.inknote.core.model.Note
import app.inknote.core.model.NoteId
import app.inknote.core.model.NoteSearch
import app.inknote.core.model.SearchText
import app.inknote.core.model.Pen
import app.inknote.core.model.PenKind
import app.inknote.core.model.Stroke
import app.inknote.core.model.StrokeId
import app.inknote.core.model.StrokePointCodec
import app.inknote.core.model.VoiceClip
import app.inknote.core.model.VoiceClipId
import app.inknote.core.model.orderExports
import app.inknote.core.model.orderStrokes
import app.inknote.core.model.orderVoiceClips
import app.inknote.core.store.db.InkNoteDatabase
import app.inknote.core.store.db.Note as NoteRow
import app.inknote.core.store.db.Stroke as StrokeRow
import app.inknote.core.store.db.Note_export as ExportRow
import app.inknote.core.store.db.SearchCandidates as SearchCandidate
import app.inknote.core.store.db.Voice_clip as VoiceClipRow

/**
 * Apre l'archivio su un driver fornito dalla piattaforma.
 *
 * Il driver non viene creato qui di proposito: su Android serve il `Context`, su
 * iOS no, e mettere quella differenza nel core lo legherebbe alle piattaforme. I
 * moduli dell'app costruiscono il driver ed eseguono le migrazioni; il core si
 * limita a fornire lo schema.
 */
object InkNoteStore {

    /** Lo schema, per creare il database e applicare le migrazioni dal lato piattaforma. */
    val schema = InkNoteDatabase.Schema

    fun open(driver: SqlDriver): NoteStore = SqlDelightNoteStore(InkNoteDatabase(driver))
}

internal class SqlDelightNoteStore(private val database: InkNoteDatabase) : NoteStore {

    private val queries = database.inkNoteQueries

    private companion object {
        /**
         * Quante candidate al massimo la prima parola può tirare su.
         *
         * Con una parola corta e un archivio grande si potrebbe troncare, e qualche
         * risultato buono resterebbe fuori. È un compromesso accettato: alzarlo vuol
         * dire scandire più righe a ogni tasto digitato, e su un archivio personale
         * una parola di quattro lettere o più ne seleziona poche.
         */
        const val CANDIDATE_LIMIT = 500L
    }

    override fun note(id: NoteId): Note? {
        val row = queries.selectNote(id.value).executeAsOneOrNull() ?: return null
        return row.toNote()
    }

    override fun recentNotes(limit: Int): List<Note> =
        queries.selectRecentNotes(limit.toLong()).executeAsList().map { it.toNote() }

    override fun search(term: String, limit: Int): List<Note> {
        val tokens = SearchText.tokenize(term)
        // Nessuna parola non vuol dire "tutte le note": chi chiama non intendeva quello.
        if (tokens.isEmpty()) return emptyList()

        // La prima parola è la più lunga, quindi in genere la più selettiva: restringe
        // in SQL. Il resto — tutte le parole presenti, e in che ordine presentarle — si
        // decide qui, su poche candidate (vedi NoteSearch).
        val candidates = queries
            .searchCandidates(token = tokens.first(), limit = CANDIDATE_LIMIT)
            .executeAsList()

        val matching = candidates
            .filter { NoteSearch.matchesText(it.haystack, tokens) }
            .sortedWith(
                compareByDescending<SearchCandidate> { NoteSearch.scoreText(it.haystack, tokens) }
                    .thenByDescending { it.updated_at }
                    .thenBy { it.id },
            )
            .take(limit)

        // Solo adesso si leggono le note intere: al massimo `limit`.
        return matching.mapNotNull { note(NoteId(it.id)) }
    }

    override fun notesNeedingSearchIndex(limit: Int): List<Note> =
        queries.selectNotesNeedingSearchIndex(version = SearchText.VERSION.toLong(), limit = limit.toLong())
            .executeAsList()
            .map { it.toNote() }

    override fun notesToSend(target: ExportTarget, limit: Int): List<Note> =
        queries.selectNotesToSend(target = target.value, limit = limit.toLong())
            .executeAsList()
            .map { it.toNote() }

    override fun reindexSearch(limit: Int): Int {
        val stale = notesNeedingSearchIndex(limit)
        // Il salvataggio ricalcola l'indice: non c'è una seconda strada per scriverlo,
        // e quindi non c'è modo di scriverlo in modo diverso da qui.
        for (note in stale) save(note)
        return stale.size
    }

    override fun notesNeedingRecognition(limit: Int): List<Note> =
        queries.selectNotesNeedingRecognition(limit.toLong()).executeAsList().map { it.toNote() }

    override fun clipsNeedingTranscription(limit: Int): List<VoiceClip> =
        queries.selectClipsNeedingTranscription(limit.toLong()).executeAsList().map { it.toVoiceClip() }

    override fun liveNoteCount(): Long = queries.countLiveNotes().executeAsOne()

    override fun save(note: Note) {
        database.transaction {
            queries.insertNoteIfAbsent(
                id = note.id.value,
                canvasWidth = note.canvas.width.toDouble(),
                canvasHeight = note.canvas.height.toDouble(),
                createdAt = note.createdAt,
                updatedAt = note.updatedAt,
                revision = note.revision,
                deletedAt = note.deletedAt,
                recognizedText = note.recognizedText,
                recognizedFromRevision = note.recognizedFromRevision,
                recognizedTextNormalized = SearchText.normalize(note.recognizedText),
                searchVersion = SearchText.VERSION.toLong(),
            )
            queries.updateNote(
                canvasWidth = note.canvas.width.toDouble(),
                canvasHeight = note.canvas.height.toDouble(),
                updatedAt = note.updatedAt,
                revision = note.revision,
                deletedAt = note.deletedAt,
                recognizedText = note.recognizedText,
                recognizedFromRevision = note.recognizedFromRevision,
                recognizedTextNormalized = SearchText.normalize(note.recognizedText),
                searchVersion = SearchText.VERSION.toLong(),
                id = note.id.value,
            )
            for (stroke in note.strokes) {
                queries.insertStrokeIfAbsent(
                    id = stroke.id.value,
                    noteId = note.id.value,
                    penColor = stroke.pen.color.toLong(),
                    penKind = stroke.pen.kind.name,
                    penBaseWidth = stroke.pen.baseWidth.toDouble(),
                    createdAt = stroke.createdAt,
                    deletedAt = stroke.deletedAt,
                    points = StrokePointCodec.encode(stroke.points),
                )
                queries.updateStrokeDeletedAt(deletedAt = stroke.deletedAt, id = stroke.id.value)
            }
            for (clip in note.voiceClips) {
                queries.insertVoiceClipIfAbsent(
                    id = clip.id.value,
                    noteId = note.id.value,
                    recordedAt = clip.recordedAt,
                    durationMs = clip.durationMs.toLong(),
                    transcript = clip.transcript,
                    transcriptNormalized = SearchText.normalize(clip.transcript),
                    audioPath = clip.audioPath,
                    deletedAt = clip.deletedAt,
                )
                queries.updateVoiceClip(
                    transcript = clip.transcript,
                    transcriptNormalized = SearchText.normalize(clip.transcript),
                    audioPath = clip.audioPath,
                    deletedAt = clip.deletedAt,
                    id = clip.id.value,
                )
            }
            for (record in note.exports) {
                queries.insertExportIfAbsent(
                    noteId = note.id.value,
                    target = record.target.value,
                    sentAt = record.sentAt,
                    revision = record.revision,
                )
                queries.updateExport(
                    sentAt = record.sentAt,
                    revision = record.revision,
                    noteId = note.id.value,
                    target = record.target.value,
                )
            }
        }
    }

    override fun markDeleted(id: NoteId, now: Long) {
        queries.markNoteDeleted(deletedAt = now, updatedAt = now, id = id.value)
    }

    override fun widgetImagePath(id: NoteId): String? =
        queries.selectWidgetImagePath(id.value).executeAsOneOrNull()?.widget_image_path

    override fun setWidgetImagePath(id: NoteId, path: String?) {
        queries.setWidgetImagePath(path = path, id = id.value)
    }

    override fun purgeDeleted(before: Long) {
        database.transaction {
            // I figli prima della nota: la cascata sulle chiavi esterne non è
            // garantita, vedi il commento nello schema.
            queries.purgeStrokesOfDeletedNotes(before)
            queries.purgeVoiceClipsOfDeletedNotes(before)
            queries.purgeExportsOfDeletedNotes(before)
            queries.purgeDeletedNotes(before)
        }
    }

    private fun strokesOf(noteId: String): List<Stroke> =
        orderStrokes(queries.selectStrokes(noteId).executeAsList().map { it.toStroke() })

    private fun voiceClipsOf(noteId: String): List<VoiceClip> =
        orderVoiceClips(queries.selectVoiceClips(noteId).executeAsList().map { it.toVoiceClip() })

    private fun exportsOf(noteId: String): List<ExportRecord> =
        orderExports(queries.selectExports(noteId).executeAsList().map { it.toExportRecord() })

    private fun NoteRow.toNote() = Note(
        id = NoteId(id),
        canvas = CanvasSize(width = canvas_width.toFloat(), height = canvas_height.toFloat()),
        strokes = strokesOf(id),
        voiceClips = voiceClipsOf(id),
        exports = exportsOf(id),
        createdAt = created_at,
        updatedAt = updated_at,
        revision = revision,
        deletedAt = deleted_at,
        recognizedText = recognized_text,
        recognizedFromRevision = recognized_from_revision,
    )

    private fun ExportRow.toExportRecord() = ExportRecord(
        target = ExportTarget(target),
        sentAt = sent_at,
        revision = revision,
    )

    private fun VoiceClipRow.toVoiceClip() = VoiceClip(
        id = VoiceClipId(id),
        recordedAt = recorded_at,
        durationMs = duration_ms.toInt(),
        transcript = transcript,
        audioPath = audio_path,
        deletedAt = deleted_at,
    )

    private fun StrokeRow.toStroke() = Stroke(
        id = StrokeId(id),
        pen = Pen(
            color = pen_color.toInt(),
            // Una punta sconosciuta viene da una versione più nuova dell'app: la
            // nota va mostrata comunque, con la punta predefinita, non persa.
            kind = PenKind.entries.firstOrNull { it.name == pen_kind } ?: PenKind.BALLPOINT,
            baseWidth = pen_base_width.toFloat(),
        ),
        points = StrokePointCodec.decode(points),
        createdAt = created_at,
        deletedAt = deleted_at,
    )
}
