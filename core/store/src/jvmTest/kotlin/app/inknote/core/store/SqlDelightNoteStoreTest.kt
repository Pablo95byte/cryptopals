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
import app.inknote.core.model.StrokePointCodec
import app.inknote.core.store.db.InkNoteDatabase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * L'archivio girato su SQLite in memoria. Non è un surrogato: è lo stesso motore e
 * lo stesso schema che finiranno sul telefono, con il driver JDBC al posto di
 * quello di piattaforma.
 */
class SqlDelightNoteStoreTest {

    private val canvas = CanvasSize(360f, 640f)
    private val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { InkNoteStore.schema.create(it) }
    private val database = InkNoteDatabase(driver)
    private val store: NoteStore = InkNoteStore.open(driver)

    private fun stroke(
        id: String,
        createdAt: Long,
        kind: PenKind = PenKind.BALLPOINT,
        deletedAt: Long? = null,
    ) = Stroke(
        id = StrokeId(id),
        pen = Pen(color = 0xFF1F2430.toInt(), kind = kind, baseWidth = 3.5f),
        points = listOf(
            InkPoint(x = 12.25f, y = 40.5f, pressure = 0.4f, tMs = 0),
            InkPoint(x = 30.75f, y = 44f, pressure = InkPoint.NO_PRESSURE, tMs = 16),
            InkPoint(x = 51f, y = 39.125f, pressure = 0.9f, tMs = 33),
        ),
        createdAt = createdAt,
        deletedAt = deletedAt,
    )

    private fun note(
        id: String,
        updatedAt: Long,
        strokes: List<Stroke> = listOf(stroke("$id-s1", updatedAt)),
        deletedAt: Long? = null,
        recognizedText: String? = null,
        recognizedFromRevision: Long? = null,
        revision: Long = 2L,
    ) = Note(
        id = NoteId(id),
        canvas = canvas,
        strokes = strokes,
        createdAt = 1_000L,
        updatedAt = updatedAt,
        revision = revision,
        deletedAt = deletedAt,
        recognizedText = recognizedText,
        recognizedFromRevision = recognizedFromRevision,
    )

    @Test
    fun `una nota salvata si rilegge identica, inchiostro compreso`() {
        val original = note("n1", updatedAt = 5_000L, recognizedText = "spesa", recognizedFromRevision = 2L)

        store.save(original)

        assertEquals(original, store.note(NoteId("n1")))
    }

    @Test
    fun `una nota inesistente è null e non un errore`() {
        assertNull(store.note(NoteId("mai-scritta")))
    }

    @Test
    fun `l'elenco parte dalla più recente e salta le cancellate`() {
        store.save(note("vecchia", updatedAt = 1_000L))
        store.save(note("recente", updatedAt = 9_000L))
        store.save(note("cestinata", updatedAt = 5_000L, deletedAt = 5_500L))

        assertEquals(listOf("recente", "vecchia"), store.recentNotes().map { it.id.value })
        assertEquals(2L, store.liveNoteCount())
    }

    @Test
    fun `la ricerca lavora sul testo riconosciuto`() {
        store.save(note("spesa", updatedAt = 2_000L, recognizedText = "latte pane e caffè", recognizedFromRevision = 2L))
        store.save(note("muta", updatedAt = 3_000L))

        assertEquals(listOf("spesa"), store.search("pane").map { it.id.value })
        assertTrue(store.search("bicicletta").isEmpty())
    }

    @Test
    fun `una ricerca vuota non restituisce tutto l'archivio`() {
        store.save(note("n1", updatedAt = 2_000L, recognizedText = "qualcosa", recognizedFromRevision = 2L))

        assertTrue(store.search("").isEmpty())
        assertTrue(store.search("   ").isEmpty())
    }

    @Test
    fun `le note cancellate non compaiono nella ricerca`() {
        store.save(
            note("cestinata", updatedAt = 2_000L, deletedAt = 2_100L, recognizedText = "pane", recognizedFromRevision = 2L),
        )

        assertTrue(store.search("pane").isEmpty())
    }

    @Test
    fun `cancellare lascia il tombstone e avanza la revisione`() {
        store.save(note("n1", updatedAt = 5_000L, revision = 4L))

        store.markDeleted(NoteId("n1"), now = 6_000L)

        val reread = assertNotNull(store.note(NoteId("n1")), "il tombstone deve restare leggibile")
        assertEquals(6_000L, reread.deletedAt)
        assertEquals(5L, reread.revision)
        assertEquals(0L, store.liveNoteCount())
    }

    @Test
    fun `cancellare due volte non cambia il tombstone`() {
        store.save(note("n1", updatedAt = 5_000L))
        store.markDeleted(NoteId("n1"), now = 6_000L)

        store.markDeleted(NoteId("n1"), now = 9_000L)

        assertEquals(6_000L, store.note(NoteId("n1"))!!.deletedAt)
    }

    @Test
    fun `salvare di nuovo non porta via i tratti già in archivio`() {
        // Regressione: con INSERT OR REPLACE la riga della nota verrebbe cancellata
        // e reinserita, e la cascata sulla chiave esterna spazzerebbe via i tratti.
        val first = note("n1", updatedAt = 5_000L, strokes = listOf(stroke("s1", 5_000L)))
        store.save(first)

        store.save(first.withStroke(stroke("s2", 6_000L), now = 6_000L))

        assertEquals(listOf("s1", "s2"), store.note(NoteId("n1"))!!.strokes.map { it.id.value })
    }

    @Test
    fun `salvare di nuovo non azzera l'immagine del widget`() {
        val saved = note("n1", updatedAt = 5_000L)
        store.save(saved)
        store.setWidgetImagePath(NoteId("n1"), "/cache/n1.png")

        store.save(saved.withStroke(stroke("s2", 6_000L), now = 6_000L))

        assertEquals("/cache/n1.png", store.widgetImagePath(NoteId("n1")))
    }

    @Test
    fun `il salvataggio unisce e non cancella i tratti assenti dalla lista`() {
        store.save(note("n1", updatedAt = 5_000L, strokes = listOf(stroke("s1", 5_000L), stroke("s2", 5_100L))))

        // Una copia incompleta della nota non deve poter far sparire inchiostro.
        store.save(note("n1", updatedAt = 7_000L, strokes = listOf(stroke("s1", 5_000L))))

        assertEquals(2, store.note(NoteId("n1"))!!.strokes.size)
    }

    @Test
    fun `la gomma su un tratto viene persistita`() {
        store.save(note("n1", updatedAt = 5_000L, strokes = listOf(stroke("s1", 5_000L))))

        store.save(note("n1", updatedAt = 6_000L, strokes = listOf(stroke("s1", 5_000L, deletedAt = 6_000L))))

        val reread = store.note(NoteId("n1"))!!
        assertEquals(6_000L, reread.strokes.single().deletedAt)
        assertTrue(reread.visibleStrokes.isEmpty())
    }

    @Test
    fun `i tratti tornano nell'ordine di disegno`() {
        store.save(
            note(
                "n1",
                updatedAt = 5_000L,
                strokes = listOf(
                    stroke("penna", 1_000L),
                    stroke("evidenziatore", 9_000L, kind = PenKind.HIGHLIGHTER),
                ),
            ),
        )

        assertEquals(
            listOf("evidenziatore", "penna"),
            store.note(NoteId("n1"))!!.strokes.map { it.id.value },
            "l'evidenziatore va disegnato per primo",
        )
    }

    @Test
    fun `l'elenco da riconoscere salta le note già riconosciute alla revisione attuale`() {
        store.save(note("da-fare", updatedAt = 2_000L, revision = 3L))
        store.save(note("vecchio-ocr", updatedAt = 3_000L, revision = 4L, recognizedText = "ciao", recognizedFromRevision = 2L))
        store.save(note("aggiornata", updatedAt = 4_000L, revision = 5L, recognizedText = "ciao", recognizedFromRevision = 5L))

        assertEquals(
            setOf("da-fare", "vecchio-ocr"),
            store.notesNeedingRecognition().map { it.id.value }.toSet(),
        )
    }

    @Test
    fun `un salvataggio da una copia vecchia non fa retrocedere la nota`() {
        store.save(note("n1", updatedAt = 6_000L, revision = 5L))

        store.save(note("n1", updatedAt = 1_000L, revision = 2L))

        // Revisione e istante sono monotoni per costruzione: se retrocedono, la nota
        // finisce sotto la revisione del suo stesso invio registrato e la coda di invio
        // la rimanda per sempre.
        val reread = store.note(NoteId("n1"))!!
        assertEquals(5L, reread.revision)
        assertEquals(6_000L, reread.updatedAt)
    }

    @Test
    fun `la pulizia rimuove solo i tombstone più vecchi della soglia`() {
        store.save(note("antica", updatedAt = 1_000L, deletedAt = 1_000L))
        store.save(note("appena-cestinata", updatedAt = 8_000L, deletedAt = 8_000L))
        store.save(note("viva", updatedAt = 9_000L))

        store.purgeDeleted(before = 5_000L)

        assertNull(store.note(NoteId("antica")))
        assertNotNull(store.note(NoteId("appena-cestinata")))
        assertNotNull(store.note(NoteId("viva")))
    }

    @Test
    fun `una punta arrivata da una versione più nuova non fa perdere la nota`() {
        store.save(note("n1", updatedAt = 5_000L, strokes = emptyList()))
        database.inkNoteQueries.insertStrokeIfAbsent(
            id = "dal-futuro",
            noteId = "n1",
            penColor = 0xFF000000.toInt().toLong(),
            penKind = "AEROGRAFO",
            penBaseWidth = 4.0,
            createdAt = 5_000L,
            deletedAt = null,
            points = StrokePointCodec.encode(listOf(InkPoint(1f, 1f))),
        )

        val reread = store.note(NoteId("n1"))!!

        assertEquals(PenKind.BALLPOINT, reread.strokes.single().pen.kind)
        assertEquals(1, reread.visibleStrokes.size)
    }
}
