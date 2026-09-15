package app.inknote.core.store

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
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
import app.inknote.core.store.db.InkNoteDatabase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VoiceClipStoreTest {

    private val canvas = CanvasSize(360f, 640f)
    private val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { InkNoteStore.schema.create(it) }
    private val database = InkNoteDatabase(driver)
    private val store = InkNoteStore.open(driver)

    private fun clip(
        id: String,
        recordedAt: Long,
        transcript: String? = null,
        audioPath: String? = "/audio/$id.m4a",
        deletedAt: Long? = null,
    ) = VoiceClip(
        id = VoiceClipId(id),
        recordedAt = recordedAt,
        durationMs = 8_400,
        transcript = transcript,
        audioPath = audioPath,
        deletedAt = deletedAt,
    )

    private fun stroke(id: String, createdAt: Long) = Stroke(
        id = StrokeId(id),
        pen = Pen(color = 0xFF1F2430.toInt(), kind = PenKind.BALLPOINT, baseWidth = 3.5f),
        points = listOf(InkPoint(1f, 2f, 0.5f, 0), InkPoint(9f, 4f, 0.7f, 16)),
        createdAt = createdAt,
    )

    private fun note(
        id: String,
        updatedAt: Long,
        strokes: List<Stroke> = emptyList(),
        clips: List<VoiceClip> = emptyList(),
        recognizedText: String? = null,
        recognizedFromRevision: Long? = null,
        deletedAt: Long? = null,
    ) = Note(
        id = NoteId(id),
        canvas = canvas,
        strokes = strokes,
        voiceClips = clips,
        createdAt = 1_000L,
        updatedAt = updatedAt,
        revision = 2L,
        deletedAt = deletedAt,
        recognizedText = recognizedText,
        recognizedFromRevision = recognizedFromRevision,
    )

    @Test
    fun `una nota vocale si rilegge identica`() {
        val original = note(
            "v1",
            updatedAt = 5_000L,
            clips = listOf(clip("c1", 5_000L, transcript = "chiamare l'idraulico")),
        )

        store.save(original)

        assertEquals(original, store.note(NoteId("v1")))
    }

    @Test
    fun `una nota con inchiostro e voce si rilegge intera`() {
        val original = note(
            "mista",
            updatedAt = 5_000L,
            strokes = listOf(stroke("s1", 4_000L)),
            clips = listOf(clip("c1", 5_000L)),
        )

        store.save(original)

        val reread = store.note(NoteId("mista"))!!
        assertTrue(reread.hasInk)
        assertTrue(reread.hasVoice)
        assertEquals(original, reread)
    }

    @Test
    fun `la ricerca trova anche dentro le trascrizioni`() {
        store.save(note("vocale", updatedAt = 2_000L, clips = listOf(clip("c1", 2_000L, transcript = "passare in farmacia"))))
        store.save(note("scritta", updatedAt = 3_000L, recognizedText = "latte e pane", recognizedFromRevision = 2L))

        assertEquals(listOf("vocale"), store.search("farmacia").map { it.id.value })
        assertEquals(listOf("scritta"), store.search("latte").map { it.id.value })
        assertTrue(store.search("bicicletta").isEmpty())
    }

    @Test
    fun `una trascrizione di una registrazione cancellata non si trova più`() {
        store.save(
            note("v1", updatedAt = 2_000L, clips = listOf(clip("c1", 2_000L, transcript = "segreto", deletedAt = 2_500L))),
        )

        assertTrue(store.search("segreto").isEmpty())
    }

    @Test
    fun `le note di sola voce non entrano nella coda dell'OCR`() {
        store.save(note("vocale", updatedAt = 2_000L, clips = listOf(clip("c1", 2_000L))))
        store.save(note("scritta", updatedAt = 3_000L, strokes = listOf(stroke("s1", 3_000L))))

        assertEquals(listOf("scritta"), store.notesNeedingRecognition().map { it.id.value })
    }

    @Test
    fun `la coda delle trascrizioni prende le registrazioni senza testo, dalla più vecchia`() {
        store.save(
            note(
                "v1",
                updatedAt = 4_000L,
                clips = listOf(
                    clip("tarda", 3_000L),
                    clip("vecchia", 1_000L),
                    clip("fatta", 2_000L, transcript = "già trascritta"),
                    clip("cestinata", 2_500L, deletedAt = 2_600L),
                ),
            ),
        )

        assertEquals(
            listOf("vecchia", "tarda"),
            store.clipsNeedingTranscription().map { it.id.value },
        )
    }

    @Test
    fun `un salvataggio da una copia vecchia non cancella una trascrizione già fatta`() {
        store.save(note("v1", updatedAt = 2_000L, clips = listOf(clip("c1", 2_000L))))
        // La trascrizione arriva e viene salvata.
        store.save(note("v1", updatedAt = 2_100L, clips = listOf(clip("c1", 2_000L, transcript = "chiamare Luca"))))

        // Un altro pezzo di codice salva una copia che non l'ha vista.
        store.save(note("v1", updatedAt = 3_000L, clips = listOf(clip("c1", 2_000L, transcript = null))))

        assertEquals("chiamare Luca", store.note(NoteId("v1"))!!.voiceClips.single().transcript)
    }

    @Test
    fun `un salvataggio da una copia vecchia non fa resuscitare un tratto cancellato`() {
        val alive = stroke("s1", 2_000L)
        store.save(note("n1", updatedAt = 2_000L, strokes = listOf(alive)))
        store.save(note("n1", updatedAt = 2_500L, strokes = listOf(alive.copy(deletedAt = 2_500L))))

        // Chi salva ha in mano una copia di prima della gomma.
        store.save(note("n1", updatedAt = 3_000L, strokes = listOf(alive)))

        assertEquals(2_500L, store.note(NoteId("n1"))!!.strokes.single().deletedAt)
    }

    @Test
    fun `un salvataggio da una copia vecchia non fa resuscitare una nota cestinata`() {
        store.save(note("n1", updatedAt = 2_000L))
        store.markDeleted(NoteId("n1"), now = 2_500L)

        store.save(note("n1", updatedAt = 3_000L, deletedAt = null))

        assertEquals(2_500L, store.note(NoteId("n1"))!!.deletedAt)
        assertEquals(0L, store.liveNoteCount())
    }

    @Test
    fun `la pulizia non lascia tratti e registrazioni orfani`() {
        store.save(
            note(
                "antica",
                updatedAt = 1_000L,
                strokes = listOf(stroke("s1", 1_000L)),
                clips = listOf(clip("c1", 1_000L)),
                deletedAt = 1_000L,
            ),
        )

        store.purgeDeleted(before = 5_000L)

        assertNull(store.note(NoteId("antica")))
        // Le chiavi esterne in SQLite sono spente per difetto: senza cancellazione
        // esplicita dei figli questi resterebbero in archivio per sempre.
        assertTrue(database.inkNoteQueries.selectStrokes("antica").executeAsList().isEmpty())
        assertTrue(database.inkNoteQueries.selectVoiceClips("antica").executeAsList().isEmpty())
    }
}
