package app.inknote.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NoteExportTest {

    private fun note(
        id: String = "n1",
        recognized: String? = null,
        recognizedFromRevision: Long? = null,
        transcripts: List<String?> = emptyList(),
        withInk: Boolean = recognized != null,
        deletedAt: Long? = null,
        revision: Long = 2L,
    ) = Note(
        id = NoteId(id),
        canvas = CANVAS,
        strokes = if (withInk) listOf(stroke("s", createdAt = 1_000L)) else emptyList(),
        voiceClips = transcripts.mapIndexed { index, transcript ->
            VoiceClip(
                id = VoiceClipId("c$index"),
                recordedAt = 1_000L + index,
                durationMs = 4_000,
                transcript = transcript,
            )
        },
        createdAt = 1_000L,
        updatedAt = 2_000L,
        revision = revision,
        deletedAt = deletedAt,
        recognizedText = recognized,
        recognizedFromRevision = recognizedFromRevision,
    )

    @Test
    fun `una nota scritta esce come testo nudo e come markdown`() {
        val content = NoteExport.prepare(
            note(recognized = "latte, pane, caffè", recognizedFromRevision = 2L),
            dateLabel = "13 settembre 2026",
        )!!

        assertEquals("latte, pane, caffè", content.text)
        assertTrue(content.markdown!!.startsWith("latte, pane, caffè"))
        assertTrue(content.markdown.contains("Scritta a mano · 13 settembre 2026"))
        assertTrue(content.isComplete)
        assertTrue(content.hasInkImage)
    }

    @Test
    fun `il testo nudo non porta decorazioni`() {
        val content = NoteExport.prepare(
            note(recognized = "chiamare Luca", recognizedFromRevision = 2L),
            dateLabel = "13 settembre 2026",
        )!!

        // Va anche in un messaggio: nessun ---, nessuna citazione, nessuna data.
        assertFalse(content.text!!.contains("---"))
        assertFalse(content.text.contains(">"))
        assertFalse(content.text.contains("2026"))
    }

    @Test
    fun `una nota dettata dice che è dettata`() {
        val content = NoteExport.prepare(
            note(recognized = null, withInk = false, transcripts = listOf("passare in farmacia")),
        )!!

        assertEquals("passare in farmacia", content.text)
        assertTrue(content.markdown!!.contains("> passare in farmacia"))
        assertTrue(content.markdown.contains("Dettata"))
        assertFalse(content.hasInkImage)
    }

    @Test
    fun `una nota scritta e dettata lo dice`() {
        val content = NoteExport.prepare(
            note(recognized = "riunione giovedì", recognizedFromRevision = 2L, transcripts = listOf("portare il preventivo")),
        )!!

        assertEquals("riunione giovedì\n\nportare il preventivo", content.text)
        assertTrue(content.markdown!!.contains("Scritta a mano e dettata"))
    }

    @Test
    fun `con l'inchiostro non ancora riconosciuto non c'è testo, ma c'è l'immagine`() {
        val content = NoteExport.prepare(note(recognized = null, withInk = true))!!

        assertNull(content.text, "non c'è ancora niente da scrivere")
        assertTrue(content.hasInkImage, "ma l'inchiostro si può mandare come immagine")
        assertFalse(content.isComplete)
    }

    @Test
    fun `un riconoscimento incompleto viene detto anche nel file`() {
        // La nota è cresciuta dopo che l'OCR era stato calcolato.
        val content = NoteExport.prepare(
            note(recognized = "latte pane", recognizedFromRevision = 1L, revision = 3L),
        )!!

        assertFalse(content.isComplete)
        assertTrue(
            content.markdown!!.contains("controlla il testo"),
            "la nota vivrà altrove: chi la rilegge lì deve saperlo",
        )
    }

    @Test
    fun `una registrazione non ancora trascritta rende l'invio incompleto`() {
        val content = NoteExport.prepare(
            note(recognized = "spesa", recognizedFromRevision = 2L, transcripts = listOf(null)),
        )!!

        assertFalse(content.isComplete)
        assertEquals("spesa", content.text, "il resto si manda comunque")
    }

    @Test
    fun `il nome del file viene dalle prime parole, senza accenti`() {
        val content = NoteExport.prepare(
            note(recognized = "Riunione giovedì 15: portare il preventivo e le foto", recognizedFromRevision = 2L),
        )!!

        assertEquals("riunione-giovedi-15-portare-il-preventivo", content.fileBaseName)
    }

    @Test
    fun `senza testo il nome del file ripiega sull'identificativo`() {
        val content = NoteExport.prepare(note(id = "abcdef1234567890", recognized = null, withInk = true))!!

        assertEquals("nota-abcdef12", content.fileBaseName)
    }

    @Test
    fun `una nota vuota non si manda`() {
        assertNull(NoteExport.prepare(note(recognized = null, withInk = false)))
    }

    @Test
    fun `una nota cestinata non si manda`() {
        assertNull(NoteExport.prepare(note(recognized = "latte", deletedAt = 3_000L)))
    }

    @Test
    fun `senza etichetta della data la riga finale resta pulita`() {
        val content = NoteExport.prepare(note(recognized = "latte", recognizedFromRevision = 2L))!!

        assertTrue(content.markdown!!.contains("Scritta a mano"))
        assertFalse(content.markdown.contains("Scritta a mano ·"), "nessun separatore appeso nel vuoto")
    }
}
