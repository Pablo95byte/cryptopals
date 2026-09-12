package app.inknote.core.store

import app.cash.sqldelight.db.SqlDriver
import app.inknote.core.model.CanvasSize
import app.inknote.core.model.Note
import app.inknote.core.model.NoteId
import app.inknote.core.model.Pen
import app.inknote.core.model.PenKind
import app.inknote.core.model.Stroke
import app.inknote.core.model.StrokeId
import app.inknote.core.model.StrokePointCodec
import app.inknote.core.model.orderStrokes
import app.inknote.core.store.db.InkNoteDatabase
import app.inknote.core.store.db.Note as NoteRow
import app.inknote.core.store.db.Stroke as StrokeRow

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

    override fun note(id: NoteId): Note? {
        val row = queries.selectNote(id.value).executeAsOneOrNull() ?: return null
        return row.toNote(strokesOf(row.id))
    }

    override fun recentNotes(limit: Int): List<Note> =
        queries.selectRecentNotes(limit.toLong()).executeAsList().map { it.toNote(strokesOf(it.id)) }

    override fun search(term: String, limit: Int): List<Note> {
        // Un termine vuoto farebbe combaciare qualunque nota: non è una ricerca,
        // è l'elenco, e chi chiama non intendeva quello.
        if (term.isBlank()) return emptyList()
        return queries.searchNotes(term, limit.toLong()).executeAsList().map { it.toNote(strokesOf(it.id)) }
    }

    override fun notesNeedingRecognition(limit: Int): List<Note> =
        queries.selectNotesNeedingRecognition(limit.toLong()).executeAsList().map { it.toNote(strokesOf(it.id)) }

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
            )
            queries.updateNote(
                canvasWidth = note.canvas.width.toDouble(),
                canvasHeight = note.canvas.height.toDouble(),
                updatedAt = note.updatedAt,
                revision = note.revision,
                deletedAt = note.deletedAt,
                recognizedText = note.recognizedText,
                recognizedFromRevision = note.recognizedFromRevision,
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
            queries.purgeDeletedNotes(before)
        }
    }

    private fun strokesOf(noteId: String): List<Stroke> =
        orderStrokes(queries.selectStrokes(noteId).executeAsList().map { it.toStroke() })

    private fun NoteRow.toNote(strokes: List<Stroke>) = Note(
        id = NoteId(id),
        canvas = CanvasSize(width = canvas_width.toFloat(), height = canvas_height.toFloat()),
        strokes = strokes,
        createdAt = created_at,
        updatedAt = updated_at,
        revision = revision,
        deletedAt = deleted_at,
        recognizedText = recognized_text,
        recognizedFromRevision = recognized_from_revision,
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
