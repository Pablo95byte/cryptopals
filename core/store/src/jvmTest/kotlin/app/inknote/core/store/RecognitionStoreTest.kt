package app.inknote.core.store

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.inknote.core.capture.InMemoryInkJournalSink
import app.inknote.core.capture.InkJournal
import app.inknote.core.capture.JournalItem
import app.inknote.core.capture.JournalRecord
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
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Il testo riconosciuto e le trascrizioni che arrivano dopo, da fuori (D62, D63). */
class RecognitionStoreTest {

    private val canvas = CanvasSize(360f, 640f)
    private val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { InkNoteStore.schema.create(it) }
    private val store = InkNoteStore.open(driver)

    private fun stroke(id: String, at: Long) = Stroke(
        id = StrokeId(id),
        pen = Pen(color = 0xFF1F2430.toInt(), kind = PenKind.BALLPOINT, baseWidth = 4.8f),
        points = listOf(InkPoint(10f, 10f, InkPoint.NO_PRESSURE, 0), InkPoint(40f, 20f, InkPoint.NO_PRESSURE, 16)),
        createdAt = at,
    )

    private fun inked(id: String = "n") =
        Note.empty(canvas, now = 1_000L, id = NoteId(id)).withStroke(stroke("s1", 1_100L), now = 1_100L)

    @Test
    fun `il testo riconosciuto si scrive se la nota è ancora quella letta`() {
        val note = inked()
        store.save(note)

        assertTrue(store.setRecognizedText(note.id, "latte pane", forRevision = note.revision))

        val saved = store.note(note.id)!!
        assertEquals("latte pane", saved.recognizedText)
        assertFalse(saved.needsRecognition)
        assertTrue(store.notesNeedingRecognition().isEmpty())
        assertEquals(listOf(note.id), store.search("pane").map { it.id })
    }

    @Test
    fun `se intanto la nota è cresciuta, il testo vecchio non si scrive`() {
        val read = inked()
        store.save(read)
        // Mentre il riconoscimento lavora, arriva un tratto nuovo.
        store.save(read.withStroke(stroke("s2", 2_000L), now = 2_000L))

        assertFalse(store.setRecognizedText(read.id, "latte", forRevision = read.revision))

        assertNull(store.note(read.id)!!.recognizedText)
        assertEquals(listOf(read.id), store.notesNeedingRecognition().map { it.id })
    }

    @Test
    fun `un salvataggio da una copia vecchia non cancella il testo riconosciuto`() {
        val stale = inked()
        store.save(stale)
        store.setRecognizedText(stale.id, "latte", forRevision = stale.revision)

        // Chi aveva letto la nota prima del riconoscimento la salva, per esempio tenendola.
        store.save(stale.withSorted(3_000L))

        assertEquals("latte", store.note(stale.id)!!.recognizedText)
        assertEquals(listOf(stale.id), store.search("latte").map { it.id })
    }

    @Test
    fun `un riconoscimento più recente sostituisce quello vecchio`() {
        val first = inked()
        store.save(first)
        store.setRecognizedText(first.id, "latte", forRevision = first.revision)
        val grown = store.note(first.id)!!.withStroke(stroke("s2", 2_000L), now = 2_000L)
        store.save(grown)

        assertTrue(store.setRecognizedText(first.id, "latte e pane", forRevision = grown.revision))

        assertEquals("latte e pane", store.note(first.id)!!.recognizedText)
    }

    @Test
    fun `una nota non riconosciuta nulla esce comunque dalla coda`() {
        // Uno scarabocchio senza parole: il riconoscimento ha finito, e non deve rigirarci
        // sopra a ogni apertura.
        val note = inked()
        store.save(note)

        store.setRecognizedText(note.id, "", forRevision = note.revision)

        assertTrue(store.notesNeedingRecognition().isEmpty())
    }

    private fun voiced(clipId: String = "v1", audio: String = "voice/v1.m4a") =
        Note.empty(canvas, now = 1_000L, id = NoteId("voce"))
            .withVoiceClip(VoiceClip(VoiceClipId(clipId), recordedAt = 1_200L, durationMs = 3_000, audioPath = audio), now = 1_200L)

    @Test
    fun `la trascrizione si scrive una volta e rende la nota cercabile`() {
        store.save(voiced())
        assertEquals(1, store.clipsNeedingTranscription().size)

        assertTrue(store.setTranscript(VoiceClipId("v1"), "chiamare Marco domani"))
        assertFalse(store.setTranscript(VoiceClipId("v1"), "altro"))

        assertEquals("chiamare Marco domani", store.note(NoteId("voce"))!!.voiceClips.single().transcript)
        assertTrue(store.clipsNeedingTranscription().isEmpty())
        assertEquals(listOf("voce"), store.search("marco").map { it.id.value })
    }

    @Test
    fun `l'eliminazione definitiva riporta anche i file audio`() {
        store.save(voiced())
        store.markDeleted(NoteId("voce"), now = 2_000L)

        val files = store.purgeDeleted(before = 10_000L)

        assertEquals(listOf("voice/v1.m4a"), files)
    }

    @Test
    fun `una registrazione dal giornale arriva in archivio`() {
        val journal = InkJournal(InMemoryInkJournalSink())
        val clip = VoiceClip(VoiceClipId("g1"), recordedAt = 2_000L, durationMs = 1_500, audioPath = "voice/g1.m4a")
        journal.record(JournalRecord(NoteId("dal-giornale"), 1_000L, canvas, JournalItem.Voice(clip)))

        JournalIngest(store).ingest(journal)

        val saved = store.note(NoteId("dal-giornale"))!!
        assertEquals(listOf(clip), saved.voiceClips)
        assertEquals(1, store.clipsNeedingTranscription().size)
    }
}
