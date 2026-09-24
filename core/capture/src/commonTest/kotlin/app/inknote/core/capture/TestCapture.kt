package app.inknote.core.capture

import app.inknote.core.model.CanvasSize
import app.inknote.core.model.Clock
import app.inknote.core.model.InkPoint
import app.inknote.core.model.NoteId
import app.inknote.core.model.Pen
import app.inknote.core.model.PenKind
import app.inknote.core.model.Stroke
import app.inknote.core.model.StrokeId

internal val CANVAS = CanvasSize(360f, 640f)

internal val BIRO = Pen(color = 0xFF1F2430.toInt(), kind = PenKind.BALLPOINT, baseWidth = 3.5f)

/** Orologio pilotato dai test: i timestamp sono la base di tutto, devono essere fermi. */
internal class FakeClock(private var now: Long = 1_000L) : Clock {
    override fun nowMillis(): Long = now
    fun advance(millis: Long) {
        now += millis
    }
    fun set(millis: Long) {
        now = millis
    }
}

internal fun stroke(
    id: String,
    createdAt: Long,
    kind: PenKind = PenKind.BALLPOINT,
) = Stroke(
    id = StrokeId(id),
    pen = BIRO.copy(kind = kind),
    points = listOf(
        InkPoint(x = 10.5f, y = 20.25f, pressure = 0.3f, tMs = 0),
        InkPoint(x = 30f, y = 24f, pressure = InkPoint.NO_PRESSURE, tMs = 16),
    ),
    createdAt = createdAt,
)

internal fun record(noteId: String, stroke: Stroke, noteCreatedAt: Long = 1_000L) = JournalRecord(
    noteId = NoteId(noteId),
    noteCreatedAt = noteCreatedAt,
    canvas = CANVAS,
    stroke = stroke,
)
