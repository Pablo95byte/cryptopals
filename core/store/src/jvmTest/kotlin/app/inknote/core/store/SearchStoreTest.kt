package app.inknote.core.store

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.inknote.core.model.CanvasSize
import app.inknote.core.model.Note
import app.inknote.core.model.NoteId
import app.inknote.core.model.VoiceClip
import app.inknote.core.model.VoiceClipId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * La ricerca sull'archivio vero, con l'indice normalizzato (D27).
 *
 * Il confronto e l'ordinamento hanno i loro test in `core:model`; qui si verifica che
 * il giro completo — scrittura dell'indice, restringimento in SQL, rifinitura in
 * Kotlin — dia i risultati attesi.
 */
class SearchStoreTest {

    private val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { InkNoteStore.schema.create(it) }
    private val store = InkNoteStore.open(driver)

    private fun save(
        id: String,
        text: String? = null,
        transcript: String? = null,
        updatedAt: Long = 1_000L,
    ) {
        store.save(
            Note(
                id = NoteId(id),
                canvas = CanvasSize(360f, 640f),
                strokes = emptyList(),
                voiceClips = if (transcript == null) {
                    emptyList()
                } else {
                    listOf(
                        VoiceClip(
                            id = VoiceClipId("$id-c"),
                            recordedAt = updatedAt,
                            durationMs = 3_000,
                            transcript = transcript,
                        ),
                    )
                },
                createdAt = 1_000L,
                updatedAt = updatedAt,
                revision = 2L,
                recognizedText = text,
                recognizedFromRevision = if (text == null) null else 2L,
            ),
        )
    }

    @Test
    fun `si trova senza mettere gli accenti`() {
        save("spesa", text = "prendere un caffè")

        assertEquals(listOf("spesa"), store.search("caffe").map { it.id.value })
    }

    @Test
    fun `si trova anche scrivendo con gli accenti`() {
        save("spesa", text = "prendere un caffe")

        assertEquals(listOf("spesa"), store.search("caffè").map { it.id.value })
    }

    @Test
    fun `le maiuscole non ASCII non impediscono di trovare`() {
        save("n1", text = "PERCHÉ chiamare Luca")

        assertEquals(listOf("n1"), store.search("perche").map { it.id.value })
    }

    @Test
    fun `l'ordine delle parole non conta`() {
        save("spesa", text = "latte, pane, caffè e detersivo")

        // Con il vecchio LIKE questa ricerca non trovava niente.
        assertEquals(listOf("spesa"), store.search("pane latte").map { it.id.value })
        assertEquals(listOf("spesa"), store.search("detersivo caffe").map { it.id.value })
    }

    @Test
    fun `servono tutte le parole`() {
        save("spesa", text = "latte e pane")

        assertTrue(store.search("latte bicicletta").isEmpty())
    }

    @Test
    fun `basta un pezzo di parola`() {
        save("n1", text = "passare in farmacia")

        assertEquals(listOf("n1"), store.search("farm").map { it.id.value })
    }

    @Test
    fun `si cerca nelle trascrizioni con le stesse regole`() {
        save("vocale", transcript = "chiamare l'idraulico mercoledì mattina")

        assertEquals(listOf("vocale"), store.search("mercoledi idraulico").map { it.id.value })
    }

    @Test
    fun `chi risponde meglio esce prima, anche se è più vecchio`() {
        save("dentro", text = "accompanare gli ospiti", updatedAt = 9_000L)
        save("inizio", text = "pane integrale", updatedAt = 1_000L)

        assertEquals(listOf("inizio", "dentro"), store.search("pan").map { it.id.value })
    }

    @Test
    fun `a pari risposta esce prima la più recente`() {
        save("vecchia", text = "pane", updatedAt = 1_000L)
        save("recente", text = "pane", updatedAt = 9_000L)

        assertEquals(listOf("recente", "vecchia"), store.search("pane").map { it.id.value })
    }

    @Test
    fun `il limite dei risultati viene rispettato`() {
        repeat(10) { save("n$it", text = "pane numero $it", updatedAt = 1_000L + it) }

        assertEquals(3, store.search("pane", limit = 3).size)
    }

    @Test
    fun `una ricerca senza parole non restituisce l'archivio`() {
        save("n1", text = "qualcosa")

        assertTrue(store.search("").isEmpty())
        assertTrue(store.search("   ").isEmpty())
        assertTrue(store.search("!?!").isEmpty())
    }

    @Test
    fun `le note cancellate non si trovano`() {
        save("n1", text = "pane")
        store.markDeleted(NoteId("n1"), now = 2_000L)

        assertTrue(store.search("pane").isEmpty())
    }

    @Test
    fun `una nota appena salvata non ha bisogno di reindicizzazione`() {
        save("n1", text = "pane")

        assertTrue(store.notesNeedingSearchIndex().isEmpty())
        assertEquals(0, store.reindexSearch())
    }

    @Test
    fun `una nota senza testo riconosciuto non si trova, ed è il compromesso dichiarato`() {
        save("muta", text = null)

        assertTrue(store.search("qualsiasi").isEmpty())
    }
}
