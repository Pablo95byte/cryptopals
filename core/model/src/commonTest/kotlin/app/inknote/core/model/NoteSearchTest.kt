package app.inknote.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NoteSearchTest {

    private var nextId = 0

    private fun note(
        text: String?,
        updatedAt: Long = 1_000L,
        transcript: String? = null,
        id: String = "n${nextId++}",
    ) = Note(
        id = NoteId(id),
        canvas = CANVAS,
        strokes = emptyList(),
        voiceClips = if (transcript == null) {
            emptyList()
        } else {
            listOf(VoiceClip(id = VoiceClipId("$id-c"), recordedAt = updatedAt, durationMs = 1_000, transcript = transcript))
        },
        createdAt = 1_000L,
        updatedAt = updatedAt,
        revision = 2L,
        recognizedText = text,
        recognizedFromRevision = if (text == null) null else 2L,
    )

    @Test
    fun `le parole si trovano in qualsiasi ordine`() {
        val spesa = note("latte, pane, caffè")

        // È il difetto che si incontra per primo: con LIKE questa ricerca non trovava
        // niente, perché "pane latte" non è una sottostringa di "latte, pane, caffè".
        assertTrue(NoteSearch.matches(spesa, SearchText.tokenize("pane latte")))
    }

    @Test
    fun `gli accenti non servono per trovare`() {
        val note = note("prendere un caffè da Nanà")

        assertTrue(NoteSearch.matches(note, SearchText.tokenize("caffe")))
        assertTrue(NoteSearch.matches(note, SearchText.tokenize("nana")))
    }

    @Test
    fun `bastano pezzi di parola`() {
        val note = note("passare in farmacia")

        assertTrue(NoteSearch.matches(note, SearchText.tokenize("farm")))
    }

    @Test
    fun `servono tutte le parole, non una qualsiasi`() {
        val note = note("latte e pane")

        assertTrue(NoteSearch.matches(note, SearchText.tokenize("latte pane")))
        assertFalse(NoteSearch.matches(note, SearchText.tokenize("latte bicicletta")))
    }

    @Test
    fun `si cerca anche nelle trascrizioni del parlato`() {
        val vocale = note(text = null, transcript = "chiamare l'idraulico mercoledì")

        assertTrue(NoteSearch.matches(vocale, SearchText.tokenize("idraulico")))
        assertTrue(NoteSearch.matches(vocale, SearchText.tokenize("mercoledi")))
    }

    @Test
    fun `una nota senza testo riconosciuto non si trova`() {
        // È il compromesso dichiarato di D2, e l'interfaccia deve dirlo all'utente
        // invece di far sembrare che la nota non esista.
        assertFalse(NoteSearch.matches(note(text = null), SearchText.tokenize("qualsiasi")))
    }

    @Test
    fun `una ricerca senza parole non trova niente`() {
        assertFalse(NoteSearch.matches(note("latte"), emptyList()))
        assertTrue(NoteSearch.filter(listOf(note("latte")), "   ").isEmpty())
    }

    @Test
    fun `chi comincia la parola viene prima di chi la contiene`() {
        val inizio = note("pane integrale", updatedAt = 1_000L)
        val dentro = note("accompanare gli ospiti", updatedAt = 9_000L)

        val ordered = NoteSearch.filter(listOf(dentro, inizio), "pan")

        assertEquals(
            listOf(inizio.id, dentro.id).map { it.value },
            ordered.map { it.id.value },
            "la nota più recente non deve vincere su una risposta migliore",
        )
    }

    @Test
    fun `a pari risposta vince la più recente`() {
        val vecchia = note("pane", updatedAt = 1_000L)
        val recente = note("pane", updatedAt = 9_000L)

        assertEquals(
            listOf(recente.id.value, vecchia.id.value),
            NoteSearch.filter(listOf(vecchia, recente), "pane").map { it.id.value },
        )
    }

    @Test
    fun `più parole trovate valgono più di una`() {
        val due = note("latte e pane")
        val una = note("solo pane")

        assertTrue(NoteSearch.score(due, SearchText.tokenize("latte pane")) > NoteSearch.score(una, SearchText.tokenize("latte pane")))
    }

    @Test
    fun `l'ordine dei risultati non cambia fra due ricerche identiche`() {
        val notes = List(6) { note("pane numero $it", updatedAt = 5_000L, id = "id-$it") }

        assertEquals(
            NoteSearch.filter(notes, "pane").map { it.id.value },
            NoteSearch.filter(notes.reversed(), "pane").map { it.id.value },
        )
    }

    @Test
    fun `una registrazione cancellata non rende trovabile la nota`() {
        val note = note(text = null, transcript = "segreto").let {
            it.copy(voiceClips = it.voiceClips.map { clip -> clip.copy(deletedAt = 2_000L) })
        }

        assertFalse(NoteSearch.matches(note, SearchText.tokenize("segreto")))
    }
}
