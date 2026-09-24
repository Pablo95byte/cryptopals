package app.inknote.core.capture

import app.inknote.core.model.NoteId
import app.inknote.core.model.VoiceClip
import app.inknote.core.model.VoiceClipId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Le registrazioni nel giornale (D63). */
class VoiceJournalTest {

    private val sink = InMemoryInkJournalSink()
    private val journal = InkJournal(sink)

    private fun voice(
        id: String,
        transcript: String? = null,
        durationMs: Int = 4_200,
        deletedAt: Long? = null,
    ) = JournalRecord(
        noteId = NoteId("n1"),
        noteCreatedAt = 1_000L,
        canvas = CANVAS,
        item = JournalItem.Voice(
            VoiceClip(
                id = VoiceClipId(id),
                recordedAt = 2_000L,
                durationMs = durationMs,
                transcript = transcript,
                audioPath = "voice/$id.m4a",
                deletedAt = deletedAt,
            ),
        ),
    )

    @Test
    fun `una registrazione scritta nel giornale si rilegge identica`() {
        val written = voice("v1")

        journal.record(written)

        assertEquals(listOf(written), journal.read().records)
    }

    @Test
    fun `una trascrizione vuota resta diversa da una trascrizione assente`() {
        // "Non c'era niente da capire" è un esito; "non ancora trascritta" è una coda.
        journal.record(voice("vuota", transcript = ""))
        journal.record(voice("assente", transcript = null))

        val clips = journal.read().records.map { (it.item as JournalItem.Voice).clip }

        assertEquals("", clips[0].transcript)
        assertNull(clips[1].transcript)
    }

    @Test
    fun `solo le registrazioni si scrivono con la versione 3`() {
        // Un'app più vecchia deve poter leggere tratti, testi e foto anche dopo che una
        // versione nuova ha scritto nel giornale: per lei una registrazione è una versione
        // futura, e si ferma senza consumarla.
        journal.record(record("n1", stroke("s1", 2_000L)))
        val afterInk = sink.readAll().size
        journal.record(voice("v1"))

        val bytes = sink.readAll()
        assertEquals(2, bytes[0].toInt())
        assertEquals(3, bytes[afterInk].toInt())
    }

    @Test
    fun `il recupero porta la registrazione nella nota`() {
        journal.record(record("n1", stroke("s1", 2_000L)))
        journal.record(voice("v1"))

        val note = journal.recover().notes.single()

        assertEquals(listOf("v1"), note.voiceClips.map { it.id.value })
        assertEquals("voice/v1.m4a", note.voiceClips.single().audioPath)
        assertTrue(note.hasInk && note.hasVoice)
    }

    @Test
    fun `due copie della stessa registrazione si fondono senza perdere niente`() {
        journal.record(voice("v1", transcript = null))
        journal.record(voice("v1", transcript = "comprare il pane"))
        journal.record(voice("v1", deletedAt = 9_000L))

        val clip = journal.recover().notes.single().voiceClips.single()

        assertEquals("comprare il pane", clip.transcript)
        assertEquals(9_000L, clip.deletedAt)
    }

    @Test
    fun `la sessione mette la registrazione nel giornale e nella nota`() {
        val clock = FakeClock(10_000L)
        val session = CaptureSession(CANVAS, journal, clock)
        assertTrue(session.isEmpty)

        val clip = session.addVoice(path = "voice/a.m4a", durationMs = 3_000)

        assertFalse(session.isEmpty)
        // Registrata a partire da tre secondi fa: l'istante è l'inizio, non la fine.
        assertEquals(7_000L, clip.recordedAt)
        assertEquals(listOf(clip), session.note().voiceClips)
        assertEquals(listOf(clip.id), journal.recover().notes.single().voiceClips.map { it.id })
    }
}
