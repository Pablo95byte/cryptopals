package app.inknote.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class NoteMergeTest {

    private val id = NoteId("nota-1")

    private fun note(
        strokes: List<Stroke>,
        updatedAt: Long,
        revision: Long,
        deletedAt: Long? = null,
        recognizedText: String? = null,
        recognizedFromRevision: Long? = null,
    ) = Note(
        id = id,
        canvas = CANVAS,
        strokes = orderStrokes(strokes),
        createdAt = 1_000L,
        updatedAt = updatedAt,
        revision = revision,
        deletedAt = deletedAt,
        recognizedText = recognizedText,
        recognizedFromRevision = recognizedFromRevision,
    )

    @Test
    fun `due dispositivi che disegnano offline non si perdono i tratti`() {
        val base = stroke("base", createdAt = 1_000L)
        val onlyLocal = stroke("locale", createdAt = 2_000L)
        val onlyRemote = stroke("remoto", createdAt = 2_100L)

        val merged = mergeNotes(
            local = note(listOf(base, onlyLocal), updatedAt = 2_000L, revision = 2L),
            remote = note(listOf(base, onlyRemote), updatedAt = 2_100L, revision = 2L),
        )

        assertEquals(listOf("base", "locale", "remoto"), merged.strokes.map { it.id.value })
        assertEquals(2_100L, merged.updatedAt)
    }

    @Test
    fun `un tratto cancellato non resuscita`() {
        val alive = stroke("a", createdAt = 1_000L)
        val erased = alive.copy(deletedAt = 1_500L)

        val merged = mergeNotes(
            local = note(listOf(alive), updatedAt = 3_000L, revision = 9L),
            remote = note(listOf(erased), updatedAt = 1_500L, revision = 2L),
        )

        assertEquals(1_500L, merged.strokes.single().deletedAt)
        assertTrue(merged.visibleStrokes.isEmpty())
    }

    @Test
    fun `la nota cancellata resta cancellata anche se l'altra copia è più recente`() {
        val merged = mergeNotes(
            local = note(emptyList(), updatedAt = 5_000L, revision = 7L),
            remote = note(emptyList(), updatedAt = 2_000L, revision = 2L, deletedAt = 2_000L),
        )

        assertEquals(2_000L, merged.deletedAt)
    }

    @Test
    fun `il merge è idempotente`() {
        val n = note(listOf(stroke("a", createdAt = 1_000L)), updatedAt = 1_000L, revision = 2L)

        assertEquals(n, mergeNotes(n, n))
        assertEquals(n, mergeNotes(mergeNotes(n, n), n))
    }

    @Test
    fun `il merge non dipende dall'ordine degli operandi`() {
        val local = note(
            strokes = listOf(stroke("a", createdAt = 1_000L), stroke("b", createdAt = 2_000L)),
            updatedAt = 2_000L,
            revision = 3L,
            recognizedText = "ciao",
            recognizedFromRevision = 3L,
        )
        val remote = note(
            strokes = listOf(stroke("a", createdAt = 1_000L, deletedAt = 2_500L), stroke("c", createdAt = 2_600L)),
            updatedAt = 2_600L,
            revision = 2L,
        )

        assertEquals(mergeNotes(local, remote), mergeNotes(remote, local))
    }

    @Test
    fun `l'OCR sopravvive se solo una copia lo ha calcolato`() {
        val inked = listOf(stroke("a", createdAt = 1_000L))

        val merged = mergeNotes(
            local = note(inked, updatedAt = 2_000L, revision = 2L),
            remote = note(inked, updatedAt = 1_000L, revision = 1L, recognizedText = "spesa", recognizedFromRevision = 1L),
        )

        assertEquals("spesa", merged.recognizedText)
        assertNotNull(merged.recognizedFromRevision)
        assertTrue(merged.needsRecognition, "la revisione è avanzata: il testo va ricalcolato")
    }

    @Test
    fun `fondere note diverse è un errore di programmazione`() {
        assertFailsWith<IllegalArgumentException> {
            mergeNotes(
                local = note(emptyList(), updatedAt = 1L, revision = 1L),
                remote = note(emptyList(), updatedAt = 1L, revision = 1L).copy(id = NoteId("altra")),
            )
        }
    }
}

/** Le registrazioni vocali si fondono con le stesse regole dei tratti (D25). */
class VoiceClipMergeTest {

    private val id = NoteId("nota-1")

    private fun clip(
        clipId: String,
        recordedAt: Long,
        transcript: String? = null,
        audioPath: String? = null,
        deletedAt: Long? = null,
    ) = VoiceClip(
        id = VoiceClipId(clipId),
        recordedAt = recordedAt,
        durationMs = 5_000,
        transcript = transcript,
        audioPath = audioPath,
        deletedAt = deletedAt,
    )

    private fun note(clips: List<VoiceClip>, updatedAt: Long, revision: Long) = Note(
        id = id,
        canvas = CANVAS,
        strokes = emptyList(),
        voiceClips = orderVoiceClips(clips),
        createdAt = 1_000L,
        updatedAt = updatedAt,
        revision = revision,
    )

    @Test
    fun `due dispositivi che registrano offline non si perdono le registrazioni`() {
        val merged = mergeNotes(
            local = note(listOf(clip("locale", 2_000L)), updatedAt = 2_000L, revision = 2L),
            remote = note(listOf(clip("remota", 2_100L)), updatedAt = 2_100L, revision = 2L),
        )

        assertEquals(listOf("locale", "remota"), merged.voiceClips.map { it.id.value })
    }

    @Test
    fun `una trascrizione già calcolata non si perde nel merge`() {
        val merged = mergeNotes(
            local = note(listOf(clip("c1", 2_000L)), updatedAt = 5_000L, revision = 9L),
            remote = note(listOf(clip("c1", 2_000L, transcript = "chiamare Luca")), updatedAt = 2_000L, revision = 2L),
        )

        assertEquals("chiamare Luca", merged.voiceClips.single().transcript)
    }

    @Test
    fun `l'audio ritrovato su un solo dispositivo sopravvive`() {
        val merged = mergeNotes(
            local = note(listOf(clip("c1", 2_000L, audioPath = null)), updatedAt = 5_000L, revision = 9L),
            remote = note(listOf(clip("c1", 2_000L, audioPath = "/audio/c1.m4a")), updatedAt = 2_000L, revision = 2L),
        )

        assertEquals("/audio/c1.m4a", merged.voiceClips.single().audioPath)
    }

    @Test
    fun `una registrazione cancellata non resuscita`() {
        val merged = mergeNotes(
            local = note(listOf(clip("c1", 2_000L)), updatedAt = 9_000L, revision = 9L),
            remote = note(listOf(clip("c1", 2_000L, deletedAt = 3_000L)), updatedAt = 3_000L, revision = 2L),
        )

        assertEquals(3_000L, merged.voiceClips.single().deletedAt)
        assertTrue(merged.visibleVoiceClips.isEmpty())
    }

    @Test
    fun `il merge delle registrazioni non dipende dall'ordine degli operandi`() {
        val local = note(
            clips = listOf(clip("a", 2_000L, transcript = "primo"), clip("b", 2_500L)),
            updatedAt = 2_500L,
            revision = 3L,
        )
        val remote = note(
            clips = listOf(clip("a", 2_000L, audioPath = "/audio/a.m4a"), clip("c", 2_800L, deletedAt = 2_900L)),
            updatedAt = 2_900L,
            revision = 2L,
        )

        assertEquals(mergeNotes(local, remote), mergeNotes(remote, local))
    }
}
