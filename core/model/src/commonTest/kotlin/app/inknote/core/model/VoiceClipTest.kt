package app.inknote.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VoiceClipTest {

    private fun clip(
        id: String,
        recordedAt: Long,
        transcript: String? = null,
        audioPath: String? = "/audio/$id.m4a",
        deletedAt: Long? = null,
    ) = VoiceClip(
        id = VoiceClipId(id),
        recordedAt = recordedAt,
        durationMs = 8_000,
        transcript = transcript,
        audioPath = audioPath,
        deletedAt = deletedAt,
    )

    @Test
    fun `una registrazione appena fatta aspetta la trascrizione`() {
        val fresh = clip("c1", recordedAt = 2_000L)

        assertTrue(fresh.needsTranscription)
        assertTrue(fresh.hasAudio)
        assertFalse(fresh.isDeleted)
    }

    @Test
    fun `una durata negativa è un errore di programmazione`() {
        assertFailsWith<IllegalArgumentException> {
            VoiceClip(id = VoiceClipId("c1"), recordedAt = 1L, durationMs = -1)
        }
    }

    @Test
    fun `una nota di sola voce non è vuota`() {
        val note = Note.empty(CANVAS, now = 1_000L)
            .withVoiceClip(clip("c1", recordedAt = 1_100L), now = 1_100L)

        assertFalse(note.isEmpty)
        assertTrue(note.hasVoice)
        assertFalse(note.hasInk)
    }

    @Test
    fun `una nota di sola voce non finisce nella coda dell'OCR`() {
        val note = Note.empty(CANVAS, now = 1_000L)
            .withVoiceClip(clip("c1", recordedAt = 1_100L), now = 1_100L)

        // Senza la condizione su hasInk la coda del riconoscimento girerebbe su questa
        // nota per sempre, senza mai avere inchiostro da leggere.
        assertFalse(note.needsRecognition)
    }

    @Test
    fun `una nota con inchiostro va riconosciuta`() {
        val note = Note.empty(CANVAS, now = 1_000L)
            .withStroke(stroke("s1", createdAt = 1_100L), now = 1_100L)

        assertTrue(note.needsRecognition)
    }

    @Test
    fun `una nota può avere insieme inchiostro e voce`() {
        val note = Note.empty(CANVAS, now = 1_000L)
            .withStroke(stroke("s1", createdAt = 1_100L), now = 1_100L)
            .withVoiceClip(clip("c1", recordedAt = 1_200L), now = 1_200L)

        assertTrue(note.hasInk)
        assertTrue(note.hasVoice)
        assertEquals(3L, note.revision)
    }

    @Test
    fun `cancellare una registrazione lascia il tombstone`() {
        val note = Note.empty(CANVAS, now = 1_000L)
            .withVoiceClip(clip("c1", recordedAt = 1_100L), now = 1_100L)

        val erased = note.withVoiceClipDeleted(VoiceClipId("c1"), now = 1_200L)

        assertEquals(1, erased.voiceClips.size, "il tombstone deve restare")
        assertTrue(erased.visibleVoiceClips.isEmpty())
        assertTrue(erased.isEmpty)
        assertEquals(3L, erased.revision)
    }

    @Test
    fun `cancellare due volte non cambia nulla`() {
        val note = Note.empty(CANVAS, now = 1_000L)
            .withVoiceClip(clip("c1", recordedAt = 1_100L), now = 1_100L)
            .withVoiceClipDeleted(VoiceClipId("c1"), now = 1_200L)

        assertEquals(note, note.withVoiceClipDeleted(VoiceClipId("c1"), now = 1_300L))
        assertEquals(note, note.withVoiceClipDeleted(VoiceClipId("mai-esistita"), now = 1_300L))
    }

    @Test
    fun `le registrazioni escono in ordine di registrazione`() {
        val ordered = orderVoiceClips(
            listOf(clip("tarda", 9_000L), clip("prima", 1_000L), clip("mezzo", 5_000L)),
        )

        assertEquals(listOf("prima", "mezzo", "tarda"), ordered.map { it.id.value })
    }

    @Test
    fun `il testo cercabile unisce OCR e trascrizioni`() {
        val note = Note.empty(CANVAS, now = 1_000L)
            .withStroke(stroke("s1", createdAt = 1_100L), now = 1_100L)
            .withVoiceClip(clip("c1", recordedAt = 1_200L, transcript = "chiamare l'idraulico"), now = 1_200L)
            .copy(recognizedText = "latte pane", recognizedFromRevision = 3L)

        assertEquals("latte pane chiamare l'idraulico", note.searchableText)
    }

    @Test
    fun `una registrazione cancellata non è più cercabile`() {
        val note = Note.empty(CANVAS, now = 1_000L)
            .withVoiceClip(clip("c1", recordedAt = 1_200L, transcript = "segreto"), now = 1_200L)
            .withVoiceClipDeleted(VoiceClipId("c1"), now = 1_300L)

        assertEquals("", note.searchableText)
    }

    @Test
    fun `le registrazioni da trascrivere sono solo quelle vive e senza testo`() {
        val note = Note.empty(CANVAS, now = 1_000L)
            .withVoiceClip(clip("da-fare", 1_100L), now = 1_100L)
            .withVoiceClip(clip("fatta", 1_200L, transcript = "ciao"), now = 1_200L)
            .withVoiceClip(clip("cestinata", 1_300L), now = 1_300L)
            .withVoiceClipDeleted(VoiceClipId("cestinata"), now = 1_400L)

        assertEquals(listOf("da-fare"), note.voiceClipsNeedingTranscription.map { it.id.value })
    }
}
