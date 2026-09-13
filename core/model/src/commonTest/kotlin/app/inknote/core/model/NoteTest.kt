package app.inknote.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NoteTest {

    @Test
    fun `una nota nuova è vuota e non ha niente da riconoscere`() {
        val note = Note.empty(CANVAS, now = 1_000L)

        assertTrue(note.isEmpty)
        assertFalse(note.isDeleted)
        assertNull(note.recognizedText)
        // Senza inchiostro non c'è nulla da leggere: mettere una nota vuota nella coda
        // dell'OCR la farebbe girare a vuoto per sempre.
        assertFalse(note.needsRecognition)
    }

    @Test
    fun `appena c'è inchiostro la nota va riconosciuta`() {
        val note = Note.empty(CANVAS, now = 1_000L)
            .withStroke(stroke("a", createdAt = 1_100L), now = 1_100L)

        assertTrue(note.needsRecognition)
    }

    @Test
    fun `aggiungere un tratto avanza la revisione`() {
        val note = Note.empty(CANVAS, now = 1_000L)
        val updated = note.withStroke(stroke("a", createdAt = 1_100L), now = 1_100L)

        assertEquals(2L, updated.revision)
        assertEquals(1_100L, updated.updatedAt)
        assertEquals(1, updated.visibleStrokes.size)
    }

    @Test
    fun `la gomma marca il tratto senza rimuoverlo`() {
        val note = Note.empty(CANVAS, now = 1_000L)
            .withStroke(stroke("a", createdAt = 1_100L), now = 1_100L)

        val erased = note.withStrokeDeleted(StrokeId("a"), now = 1_200L)

        assertEquals(1, erased.strokes.size, "il tombstone deve restare in archivio")
        assertEquals(0, erased.visibleStrokes.size)
        assertEquals(3L, erased.revision)
    }

    @Test
    fun `cancellare un tratto già cancellato non cambia nulla`() {
        val note = Note.empty(CANVAS, now = 1_000L)
            .withStroke(stroke("a", createdAt = 1_100L), now = 1_100L)
            .withStrokeDeleted(StrokeId("a"), now = 1_200L)

        assertEquals(note, note.withStrokeDeleted(StrokeId("a"), now = 1_300L))
        assertEquals(note, note.withStrokeDeleted(StrokeId("mai-esistito"), now = 1_300L))
    }

    @Test
    fun `l'evidenziatore finisce sotto l'inchiostro`() {
        val ink = stroke("ink", createdAt = 1_000L)
        val marker = stroke("hl", createdAt = 9_000L, kind = PenKind.HIGHLIGHTER)

        val ordered = orderStrokes(listOf(ink, marker))

        assertEquals(listOf("hl", "ink"), ordered.map { it.id.value })
    }

    @Test
    fun `l'ordine di disegno non dipende dall'ordine di inserimento`() {
        val a = stroke("a", createdAt = 1_000L)
        val b = stroke("b", createdAt = 2_000L)
        val c = stroke("c", createdAt = 2_000L)

        assertEquals(
            orderStrokes(listOf(a, b, c)).map { it.id.value },
            orderStrokes(listOf(c, b, a)).map { it.id.value },
        )
    }
}

internal val CANVAS = CanvasSize(360f, 360f)

internal fun stroke(
    id: String,
    createdAt: Long,
    kind: PenKind = PenKind.BALLPOINT,
    deletedAt: Long? = null,
    points: List<InkPoint> = listOf(InkPoint(0f, 0f), InkPoint(10f, 10f)),
): Stroke = Stroke(
    id = StrokeId(id),
    pen = Pen(color = 0xFF101010.toInt(), kind = kind),
    points = points,
    createdAt = createdAt,
    deletedAt = deletedAt,
)

/** Registrare dove una nota è già andata, per non duplicarla (D31). */
class ExportRecordTest {

    private val note = Note.empty(CANVAS, now = 1_000L)
        .withStroke(stroke("a", createdAt = 1_100L), now = 1_100L)

    @Test
    fun `una nota nuova non è stata mandata da nessuna parte`() {
        assertFalse(note.wasSentTo(ExportTarget.Notion))
        assertFalse(note.needsResendTo(ExportTarget.Notion))
        assertTrue(note.exports.isEmpty())
    }

    @Test
    fun `registrare un invio non modifica la nota`() {
        val sent = note.withExport(ExportTarget.Notion, now = 5_000L)

        assertTrue(sent.wasSentTo(ExportTarget.Notion))
        // Mandare una nota non la cambia: se la revisione salisse, ogni invio renderebbe
        // vecchi tutti gli altri invii della stessa nota.
        assertEquals(note.revision, sent.revision)
        assertEquals(note.updatedAt, sent.updatedAt)
        assertFalse(sent.needsResendTo(ExportTarget.Notion))
    }

    @Test
    fun `se la nota cresce dopo l'invio, ha senso rimandarla`() {
        val sent = note.withExport(ExportTarget.Notion, now = 5_000L)

        val grown = sent.withStroke(stroke("b", createdAt = 6_000L), now = 6_000L)

        assertTrue(grown.needsResendTo(ExportTarget.Notion))
        assertFalse(grown.needsResendTo(ExportTarget.Markdown), "mai mandata lì: non è un rinvio")
    }

    @Test
    fun `destinazioni diverse si registrano separatamente`() {
        val sent = note
            .withExport(ExportTarget.Notion, now = 5_000L)
            .withExport(ExportTarget.SystemShare, now = 6_000L)

        assertEquals(2, sent.exports.size)
        assertTrue(sent.wasSentTo(ExportTarget.Notion))
        assertTrue(sent.wasSentTo(ExportTarget.SystemShare))
    }

    @Test
    fun `rimandare alla stessa destinazione aggiorna, non accumula`() {
        val sent = note
            .withExport(ExportTarget.Notion, now = 5_000L)
            .withExport(ExportTarget.Notion, now = 9_000L)

        assertEquals(1, sent.exports.size)
        assertEquals(9_000L, sent.exports.single().sentAt)
    }

    @Test
    fun `il merge tiene l'invio della revisione più avanzata`() {
        val id = NoteId("n1")
        fun copy(revision: Long, exportRevision: Long, sentAt: Long) = Note(
            id = id,
            canvas = CANVAS,
            strokes = emptyList(),
            exports = listOf(ExportRecord(ExportTarget.Notion, sentAt = sentAt, revision = exportRevision)),
            createdAt = 1_000L,
            updatedAt = sentAt,
            revision = revision,
        )

        val merged = mergeNotes(
            local = copy(revision = 5L, exportRevision = 3L, sentAt = 3_000L),
            remote = copy(revision = 5L, exportRevision = 5L, sentAt = 9_000L),
        )

        // Perdere l'invio più avanzato farebbe rimandare la nota al dispositivo che non
        // lo sa, e la duplicherebbe nell'archivio altrui.
        assertEquals(5L, merged.exports.single().revision)
        assertEquals(mergeNotes(merged, merged), merged)
    }
}
