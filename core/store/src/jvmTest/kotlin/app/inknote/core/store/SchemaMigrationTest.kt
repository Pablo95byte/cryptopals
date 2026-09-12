package app.inknote.core.store

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.inknote.core.model.InkPoint
import app.inknote.core.model.NoteId
import app.inknote.core.model.PenKind
import app.inknote.core.model.StrokePointCodec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * La migrazione dello schema, provata su un archivio scritto dalla versione 1.
 *
 * Non è un test di cortesia. `verifySqlDelightMigration` controlla che le istruzioni
 * di migrazione **descrivano** lo stesso schema dei file `.sq`, ma non che una nota
 * già salvata si rilegga dopo. Questo lo controlla, e lo fa adesso che non ci sono
 * utenti: il momento per scoprire che le migrazioni non funzionano è questo, non dopo
 * la pubblicazione.
 */
class SchemaMigrationTest {

    private val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)

    /** Lo schema come era nella versione 1: prima che esistessero le note vocali. */
    private fun createVersion1Schema() {
        listOf(
            """
            CREATE TABLE note (
                id TEXT NOT NULL PRIMARY KEY,
                canvas_width REAL NOT NULL,
                canvas_height REAL NOT NULL,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL,
                revision INTEGER NOT NULL,
                deleted_at INTEGER,
                recognized_text TEXT,
                recognized_from_revision INTEGER,
                widget_image_path TEXT
            )
            """.trimIndent(),
            "CREATE INDEX note_updated_at ON note(updated_at DESC)",
            """
            CREATE TABLE stroke (
                id TEXT NOT NULL PRIMARY KEY,
                note_id TEXT NOT NULL REFERENCES note(id) ON DELETE CASCADE,
                pen_color INTEGER NOT NULL,
                pen_kind TEXT NOT NULL,
                pen_base_width REAL NOT NULL,
                created_at INTEGER NOT NULL,
                deleted_at INTEGER,
                points BLOB NOT NULL
            )
            """.trimIndent(),
            "CREATE INDEX stroke_note_id ON stroke(note_id)",
        ).forEach { driver.execute(null, it, 0, null) }
    }

    /** Una nota come l'avrebbe scritta la versione 1 dell'app. */
    private fun insertVersion1Note() {
        driver.execute(
            null,
            """
            INSERT INTO note(
                id, canvas_width, canvas_height, created_at, updated_at, revision,
                deleted_at, recognized_text, recognized_from_revision, widget_image_path
            ) VALUES ('vecchia', 360.0, 640.0, 1000, 2000, 3, NULL, 'latte pane', 3, '/cache/vecchia.png')
            """.trimIndent(),
            0,
            null,
        )

        val points = StrokePointCodec.encode(
            listOf(
                InkPoint(x = 10.5f, y = 20.25f, pressure = 0.4f, tMs = 0),
                InkPoint(x = 40f, y = 22f, pressure = InkPoint.NO_PRESSURE, tMs = 16),
            ),
        )
        driver.execute(
            null,
            """
            INSERT INTO stroke(id, note_id, pen_color, pen_kind, pen_base_width, created_at, deleted_at, points)
            VALUES ('s1', 'vecchia', -14671312, 'BALLPOINT', 3.5, 1500, NULL, ?)
            """.trimIndent(),
            1,
        ) {
            bindBytes(0, points)
        }
    }

    @Test
    fun `una nota della versione 1 si rilegge intera dopo la migrazione`() {
        createVersion1Schema()
        insertVersion1Note()

        InkNoteStore.schema.migrate(driver, 1L, 2L)

        val note = assertNotNull(InkNoteStore.open(driver).note(NoteId("vecchia")))
        assertEquals(360f, note.canvas.width)
        assertEquals(1_000L, note.createdAt)
        assertEquals(3L, note.revision)
        assertEquals("latte pane", note.recognizedText)
        assertEquals(1, note.strokes.size)
        assertEquals(PenKind.BALLPOINT, note.strokes.single().pen.kind)
        // I campioni passano dal formato binario versionato: se la migrazione li avesse
        // toccati, qui si vedrebbe.
        assertEquals(2, note.strokes.single().points.size)
        assertEquals(10.5f, note.strokes.single().points.first().x)
        assertFalse(note.strokes.single().points[1].hasPressure)
    }

    @Test
    fun `dopo la migrazione la nota vecchia non ha registrazioni e non pretende di averne`() {
        createVersion1Schema()
        insertVersion1Note()

        InkNoteStore.schema.migrate(driver, 1L, 2L)

        val note = InkNoteStore.open(driver).note(NoteId("vecchia"))!!
        assertTrue(note.voiceClips.isEmpty())
        assertFalse(note.hasVoice)
        assertTrue(note.hasInk)
    }

    @Test
    fun `dopo la migrazione si possono aggiungere registrazioni alla nota vecchia`() {
        createVersion1Schema()
        insertVersion1Note()
        InkNoteStore.schema.migrate(driver, 1L, 2L)
        val store = InkNoteStore.open(driver)

        val withVoice = store.note(NoteId("vecchia"))!!.withVoiceClip(
            clip = app.inknote.core.model.VoiceClip(
                id = app.inknote.core.model.VoiceClipId("c1"),
                recordedAt = 9_000L,
                durationMs = 4_000,
                transcript = "aggiungere le pile",
                audioPath = "/audio/c1.m4a",
            ),
            now = 9_000L,
        )
        store.save(withVoice)

        val reread = store.note(NoteId("vecchia"))!!
        assertTrue(reread.hasInk)
        assertTrue(reread.hasVoice)
        assertEquals(listOf("vecchia"), store.search("pile").map { it.id.value })
    }

    @Test
    fun `il percorso dell'immagine del widget sopravvive alla migrazione`() {
        createVersion1Schema()
        insertVersion1Note()

        InkNoteStore.schema.migrate(driver, 1L, 2L)

        assertEquals("/cache/vecchia.png", InkNoteStore.open(driver).widgetImagePath(NoteId("vecchia")))
    }
}
