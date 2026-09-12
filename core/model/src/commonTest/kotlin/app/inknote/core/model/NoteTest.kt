package app.inknote.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NoteTest {

    @Test
    fun `una nota nuova è vuota e va riconosciuta`() {
        val note = Note.empty(CANVAS, now = 1_000L)
        assertTrue(note.isEmpty)
        assertFalse(note.isDeleted)
        assertTrue(note.needsRecognition)
        assertNull(note.recognizedText)
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
