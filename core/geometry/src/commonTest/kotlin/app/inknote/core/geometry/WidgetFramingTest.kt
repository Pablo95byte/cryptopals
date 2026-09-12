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
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class WidgetFramingTest {

    private val config = WidgetLayoutConfig.Default

    /** Le misure nominali dei widget, in unità logiche. */
    private val small = Bounds(0f, 0f, 155f, 155f)
    private val medium = Bounds(0f, 0f, 329f, 155f)
    private val large = Bounds(0f, 0f, 329f, 329f)

    private fun inkNote(
        id: String,
        from: Pair<Float, Float> = 100f to 200f,
        to: Pair<Float, Float> = 140f to 220f,
    ) = Note(
        id = NoteId(id),
        canvas = CanvasSize(360f, 640f),
        strokes = listOf(
            Stroke(
                id = StrokeId("$id-s"),
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

    private fun voiceNote(id: String, transcript: String? = "chiamare l'idraulico") = Note(
        id = NoteId(id),
        canvas = CanvasSize(360f, 640f),
        strokes = emptyList(),
        voiceClips = listOf(
            VoiceClip(
                id = VoiceClipId("$id-c"),
                recordedAt = 1_000L,
                durationMs = 8_400,
                transcript = transcript,
            ),
        ),
        createdAt = 1_000L,
        updatedAt = 1_000L,
        revision = 2L,
    )

    private fun emptyNote(id: String) = Note(
        id = NoteId(id),
        canvas = CanvasSize(360f, 640f),
        strokes = emptyList(),
        createdAt = 1_000L,
        updatedAt = 1_000L,
        revision = 1L,
    )

    @Test
    fun `il piccolo mostra una nota e si tocca tutto`() {
        val layout = WidgetFraming.layout(List(4) { inkNote("n$it") }, WidgetSize.SMALL, small)

        assertEquals(1, layout.cells.size)
        assertTrue(layout.wholeWidgetCaptures, "nel piccolo un pulsante ruberebbe spazio alla nota")
        assertEquals(small, layout.captureTarget)
        assertEquals(3, layout.hiddenNoteCount)
    }

    @Test
    fun `il medio mostra tre note in fila, con l'area di cattura`() {
        val layout = WidgetFraming.layout(List(3) { inkNote("n$it") }, WidgetSize.MEDIUM, medium)

        assertEquals(3, layout.cells.size)
        assertFalse(layout.wholeWidgetCaptures)
        assertEquals(44f, layout.captureTarget.width)
        assertEquals(44f, layout.captureTarget.height)
    }

    @Test
    fun `il grande mostra quattro note su due righe`() {
        val layout = WidgetFraming.layout(List(4) { inkNote("n$it") }, WidgetSize.LARGE, large)

        assertEquals(4, layout.cells.size)
        val rows = layout.cells.map { it.rect.minY }.distinct()
        assertEquals(2, rows.size, "due righe")
    }

    @Test
    fun `con meno note la griglia si accorcia invece di lasciare buchi`() {
        val layout = WidgetFraming.layout(listOf(inkNote("a"), inkNote("b")), WidgetSize.LARGE, large)

        assertEquals(2, layout.cells.size)
        assertEquals(1, layout.cells.map { it.rect.minY }.distinct().size, "una riga sola")
    }

    @Test
    fun `la leggibilità viene prima della quantità`() {
        // Un medio molto basso non può tenere tre caselle leggibili: ne tiene meno.
        val squat = Bounds(0f, 0f, 329f, 140f)

        val layout = WidgetFraming.layout(List(3) { inkNote("n$it") }, WidgetSize.MEDIUM, squat)

        assertTrue(layout.cells.isNotEmpty())
        assertTrue(
            layout.cells.all { minOf(it.rect.width, it.rect.height) >= config.minCellSide },
            "nessuna casella sotto il minimo leggibile",
        )
    }

    @Test
    fun `se nemmeno una casella è leggibile il widget resta solo una porta`() {
        val tiny = Bounds(0f, 0f, 80f, 80f)

        val layout = WidgetFraming.layout(List(3) { inkNote("n$it") }, WidgetSize.SMALL, tiny)

        assertTrue(layout.isEmpty, "meglio niente che una nota illeggibile")
        assertTrue(layout.wholeWidgetCaptures)
        assertEquals(3, layout.hiddenNoteCount)
    }

    @Test
    fun `senza note tutto il widget apre il foglio`() {
        val layout = WidgetFraming.layout(emptyList(), WidgetSize.LARGE, large)

        assertTrue(layout.isEmpty)
        assertTrue(layout.wholeWidgetCaptures, "un piccolo + in un riquadro vuoto è una porta sprecata")
        assertEquals(large, layout.captureTarget)
    }

    @Test
    fun `le note vuote e quelle cestinate non occupano caselle`() {
        val notes = listOf(
            emptyNote("vuota"),
            inkNote("buona"),
            inkNote("cestinata").copy(deletedAt = 2_000L),
        )

        val layout = WidgetFraming.layout(notes, WidgetSize.MEDIUM, medium)

        assertEquals(listOf("buona"), layout.cells.map { it.noteId.value })
        assertEquals(0, layout.hiddenNoteCount)
    }

    @Test
    fun `l'inchiostro si inquadra sull'inchiostro e non sul foglio`() {
        val layout = WidgetFraming.layout(listOf(inkNote("a")), WidgetSize.SMALL, small)

        val ink = assertIs<WidgetCellContent.Ink>(layout.cells.single().content)
        // Tratto da (100,200) a (140,220), penna spessa 4, più 4 di margine.
        assertEquals(94f, ink.source.minX, 0.01f)
        assertEquals(194f, ink.source.minY, 0.01f)
        assertEquals(146f, ink.source.maxX, 0.01f)
        assertEquals(226f, ink.source.maxY, 0.01f)
    }

    @Test
    fun `l'ingrandimento ha un tetto`() {
        // Una nota minuscola in una casella grande: senza tetto diventerebbe un manifesto.
        val layout = WidgetFraming.layout(
            listOf(inkNote("a", from = 10f to 10f, to = 12f to 12f)),
            WidgetSize.LARGE,
            large,
        )

        val ink = assertIs<WidgetCellContent.Ink>(layout.cells.single().content)
        assertEquals(config.maxScale, ink.scale, 0.001f)
    }

    @Test
    fun `una nota grande viene rimpicciolita per starci dentro`() {
        val layout = WidgetFraming.layout(
            listOf(inkNote("a", from = 0f to 0f, to = 340f to 600f)),
            WidgetSize.SMALL,
            small,
        )

        val ink = assertIs<WidgetCellContent.Ink>(layout.cells.single().content)
        assertTrue(ink.scale < 1f, "scala inattesa: ${ink.scale}")
        assertTrue(ink.source.width * ink.scale <= layout.cells.single().rect.width + 0.01f)
        assertTrue(ink.source.height * ink.scale <= layout.cells.single().rect.height + 0.01f)
    }

    @Test
    fun `il dettaglio lo decide la casella, non il formato`() {
        val bigCell = WidgetFraming.layout(listOf(inkNote("a")), WidgetSize.LARGE, large)
        val smallCells = WidgetFraming.layout(List(3) { inkNote("n$it") }, WidgetSize.MEDIUM, medium)

        assertEquals(
            RenderQuality.SCREEN,
            assertIs<WidgetCellContent.Ink>(bigCell.cells.single().content).quality,
        )
        assertEquals(
            RenderQuality.THUMBNAIL,
            assertIs<WidgetCellContent.Ink>(smallCells.cells.first().content).quality,
        )
    }

    @Test
    fun `una nota vocale entra nel widget`() {
        val layout = WidgetFraming.layout(listOf(voiceNote("v")), WidgetSize.MEDIUM, medium)

        val voice = assertIs<WidgetCellContent.Voice>(layout.cells.single().content)
        assertEquals(8_400, voice.durationMs)
        assertEquals("chiamare l'idraulico", voice.transcript)
    }

    @Test
    fun `una nota vocale non ancora trascritta entra comunque`() {
        val layout = WidgetFraming.layout(listOf(voiceNote("v", transcript = null)), WidgetSize.SMALL, small)

        assertEquals(null, assertIs<WidgetCellContent.Voice>(layout.cells.single().content).transcript)
    }

    @Test
    fun `se una nota ha inchiostro e voce vince l'inchiostro`() {
        val mixed = inkNote("m").copy(voiceClips = voiceNote("m").voiceClips)

        val layout = WidgetFraming.layout(listOf(mixed), WidgetSize.SMALL, small)

        assertIs<WidgetCellContent.Ink>(layout.cells.single().content)
    }

    @Test
    fun `la prima nota va nella prima casella`() {
        val notes = listOf(inkNote("prima"), inkNote("seconda"), inkNote("terza"))

        val layout = WidgetFraming.layout(notes, WidgetSize.MEDIUM, medium)

        assertEquals(listOf("prima", "seconda", "terza"), layout.cells.map { it.noteId.value })
        assertEquals(
            layout.cells.map { it.rect.minX }.sorted(),
            layout.cells.map { it.rect.minX },
            "le caselle vanno riempite da sinistra",
        )
    }

    @Test
    fun `le caselle stanno dentro il widget e non si sovrappongono`() {
        val layout = WidgetFraming.layout(List(4) { inkNote("n$it") }, WidgetSize.LARGE, large)

        for (cell in layout.cells) {
            assertTrue(cell.rect.minX >= large.minX && cell.rect.maxX <= large.maxX, "fuori in orizzontale")
            assertTrue(cell.rect.minY >= large.minY && cell.rect.maxY <= large.maxY, "fuori in verticale")
        }
        for (a in layout.cells) {
            for (b in layout.cells) {
                if (a === b) continue
                val overlaps = a.rect.minX < b.rect.maxX && b.rect.minX < a.rect.maxX &&
                    a.rect.minY < b.rect.maxY && b.rect.minY < a.rect.maxY
                assertFalse(overlaps, "${a.noteId.value} e ${b.noteId.value} si sovrappongono")
            }
        }
    }

    @Test
    fun `l'area di cattura non si sovrappone alle note, in nessun formato`() {
        for ((size, box) in listOf(WidgetSize.MEDIUM to medium, WidgetSize.LARGE to large)) {
            val layout = WidgetFraming.layout(List(4) { inkNote("n$it") }, size, box)

            assertFalse(layout.wholeWidgetCaptures, "$size")
            for (cell in layout.cells) {
                val overlaps = cell.rect.minX < layout.captureTarget.maxX &&
                    layout.captureTarget.minX < cell.rect.maxX &&
                    cell.rect.minY < layout.captureTarget.maxY &&
                    layout.captureTarget.minY < cell.rect.maxY
                assertFalse(overlaps, "$size: la casella ${cell.noteId.value} invade l'area di cattura")
            }
        }
    }

    @Test
    fun `sul medio l'area di cattura sta di lato, non in alto`() {
        val layout = WidgetFraming.layout(List(3) { inkNote("n$it") }, WidgetSize.MEDIUM, medium)

        // Una striscia in alto si mangerebbe più spazio del contenuto: 44 punti su 131
        // di altezza utile contro 44 su 305 di larghezza.
        assertTrue(layout.captureTarget.minX > medium.width / 2f, "non è sul lato destro")
        assertTrue(layout.cells.all { it.rect.maxX <= layout.captureTarget.minX }, "le note non le stanno a sinistra")
        assertTrue(layout.cells.all { it.rect.height > layout.captureTarget.height }, "le caselle sono più alte del pulsante")
    }

    @Test
    fun `sul grande l'area di cattura sta in alto`() {
        val layout = WidgetFraming.layout(List(4) { inkNote("n$it") }, WidgetSize.LARGE, large)

        assertTrue(layout.cells.all { it.rect.minY >= layout.captureTarget.maxY })
    }

    @Test
    fun `a parità di ingresso l'uscita è sempre la stessa`() {
        val notes = List(4) { inkNote("n$it") }

        assertEquals(
            WidgetFraming.layout(notes, WidgetSize.LARGE, large),
            WidgetFraming.layout(notes, WidgetSize.LARGE, large),
        )
    }

    @Test
    fun `un'area di cattura sotto i 44 punti è un errore di programmazione`() {
        assertFailsWith<IllegalArgumentException> { WidgetLayoutConfig(captureTargetSide = 40f) }
    }
}
