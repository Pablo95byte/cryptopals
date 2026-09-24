package app.inknote.core.geometry

import app.inknote.core.model.CanvasSize
import app.inknote.core.model.InkPoint
import app.inknote.core.model.Note
import app.inknote.core.model.NoteId
import app.inknote.core.model.Pen
import app.inknote.core.model.PenKind
import app.inknote.core.model.Stroke
import app.inknote.core.model.StrokeId
import app.inknote.core.model.VoiceClip
import app.inknote.core.model.VoiceClipId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NoteFramingTest {

    private val config = NoteFramingConfig.Default

    private fun inkNote(
        from: Pair<Float, Float> = 100f to 200f,
        to: Pair<Float, Float> = 140f to 220f,
    ) = Note(
        id = NoteId("n"),
        canvas = CanvasSize(360f, 640f),
        strokes = listOf(
            Stroke(
                id = StrokeId("s"),
                pen = Pen(color = 0xFF1F2430.toInt(), kind = PenKind.BALLPOINT, baseWidth = 4f),
                points = listOf(
                    InkPoint(from.first, from.second, 0.5f, 0),
                    InkPoint(to.first, to.second, 0.6f, 16),
                ),
                createdAt = 1_000L,
            ),
        ),
        createdAt = 1_000L,
        updatedAt = 1_000L,
        revision = 2L,
    )

    @Test
    fun `si inquadra l'inchiostro e non il foglio`() {
        val frame = NoteFraming.fit(inkNote(), Bounds(0f, 0f, 160f, 160f))!!

        // Tratto da (100,200) a (140,220), penna spessa 4, più 4 di margine.
        assertEquals(94f, frame.source.minX, 0.01f)
        assertEquals(194f, frame.source.minY, 0.01f)
        assertEquals(146f, frame.source.maxX, 0.01f)
        assertEquals(226f, frame.source.maxY, 0.01f)
    }

    @Test
    fun `l'ingrandimento ha un tetto`() {
        val frame = NoteFraming.fit(inkNote(from = 10f to 10f, to = 12f to 12f), Bounds(0f, 0f, 300f, 300f))!!

        assertEquals(config.maxScale, frame.scale, 0.001f)
    }

    @Test
    fun `una nota grande viene rimpicciolita per starci dentro`() {
        val box = Bounds(0f, 0f, 140f, 140f)

        val frame = NoteFraming.fit(inkNote(from = 0f to 0f, to = 340f to 600f), box)!!

        assertTrue(frame.scale < 1f, "scala inattesa: ${frame.scale}")
        assertTrue(frame.source.width * frame.scale <= box.width + 0.01f)
        assertTrue(frame.source.height * frame.scale <= box.height + 0.01f)
    }

    @Test
    fun `il dettaglio lo decide il riquadro`() {
        assertEquals(RenderQuality.SCREEN, NoteFraming.qualityFor(Bounds(0f, 0f, 300f, 250f)))
        assertEquals(RenderQuality.WIDGET, NoteFraming.qualityFor(Bounds(0f, 0f, 300f, 130f)))
        assertEquals(RenderQuality.THUMBNAIL, NoteFraming.qualityFor(Bounds(0f, 0f, 300f, 80f)))
    }

    @Test
    fun `una nota di sola voce non si inquadra`() {
        val voice = Note(
            id = NoteId("v"),
            canvas = CanvasSize(360f, 640f),
            strokes = emptyList(),
            voiceClips = listOf(
                VoiceClip(id = VoiceClipId("c"), recordedAt = 1_000L, durationMs = 4_000, transcript = "ciao"),
            ),
            createdAt = 1_000L,
            updatedAt = 1_000L,
            revision = 2L,
        )

        // Chi chiama mostra la trascrizione: non c'è inchiostro da ritagliare.
        assertNull(NoteFraming.fit(voice, Bounds(0f, 0f, 160f, 160f)))
    }

    @Test
    fun `un riquadro di area nulla non produce inquadratura`() {
        assertNull(NoteFraming.fit(inkNote(), Bounds(0f, 0f, 0f, 100f)))
    }
}
