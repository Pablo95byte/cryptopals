package app.inknote.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Testo digitato e foto (D38): stesse regole dei tratti, perché sono la stessa cosa. */
class ClipsTest {

    private fun empty(id: String = "n1") = Note.empty(CANVAS, now = 1_000L, id = NoteId(id))

    private fun text(id: String, text: String, at: Long = 2_000L) = TextClip(TextClipId(id), at, text)

    private fun photo(id: String, at: Long = 2_000L) = PhotoClip(PhotoClipId(id), at, "photos/$id.jpg")

    @Test
    fun `una nota di solo testo non è vuota ed è cercabile`() {
        val note = empty().withTextClip(text("t1", "Comprare il caffè"), now = 2_000L)

        assertFalse(note.isEmpty)
        assertTrue(note.hasText)
        assertTrue(NoteSearch.matches(note, SearchText.tokenize("caffe")))
    }

    @Test
    fun `una nota di sola foto non è vuota`() {
        val note = empty().withPhotoClip(photo("p1"), now = 2_000L)

        assertFalse(note.isEmpty)
        assertTrue(note.hasPhoto)
    }

    @Test
    fun `un testo vuoto non cambia la nota`() {
        val note = empty()

        assertEquals(note, note.withTextClip(text("t1", "   "), now = 2_000L))
    }

    @Test
    fun `aggiungere testo fa salire la revisione`() {
        val note = empty().withTextClip(text("t1", "ciao"), now = 2_000L)

        assertEquals(2L, note.revision)
        assertEquals(2_000L, note.updatedAt)
    }

    @Test
    fun `correggere un testo è cancellarlo e aggiungerne uno nuovo`() {
        val note = empty()
            .withTextClip(text("t1", "latte"), now = 2_000L)
            .withTextClipDeleted(TextClipId("t1"), now = 3_000L)
            .withTextClip(text("t2", "latte e pane", at = 3_000L), now = 3_000L)

        assertEquals("latte e pane", note.typedText)
        assertEquals(2, note.textClips.size, "il vecchio resta come tombstone")
    }

    @Test
    fun `due dispositivi che scrivono testo offline non perdono niente`() {
        val base = empty()
        val phone = base.withTextClip(text("a", "dal telefono"), now = 2_000L)
        val tablet = base.withTextClip(text("b", "dal tablet", at = 2_500L), now = 2_500L)

        val merged = mergeNotes(phone, tablet)

        assertEquals(listOf("a", "b"), merged.textClips.map { it.id.value })
        assertEquals(merged, mergeNotes(tablet, phone), "l'ordine degli operandi non conta")
    }

    @Test
    fun `una foto cancellata su un dispositivo non resuscita col merge`() {
        val base = empty().withPhotoClip(photo("p1"), now = 2_000L)
        val deleted = base.withPhotoClipDeleted(PhotoClipId("p1"), now = 3_000L)

        val merged = mergeNotes(base, deleted)

        assertTrue(merged.photoClips.single().isDeleted)
        assertFalse(merged.hasPhoto)
        assertEquals(merged, mergeNotes(deleted, base))
    }

    @Test
    fun `il merge di testi e foto è idempotente`() {
        val note = empty()
            .withTextClip(text("t1", "uno"), now = 2_000L)
            .withPhotoClip(photo("p1"), now = 2_100L)

        assertEquals(note, mergeNotes(note, note))
    }

    @Test
    fun `nell'esportazione il testo digitato viene prima e le foto si allegano`() {
        val note = empty()
            .withTextClip(text("t1", "Chiamare Marco"), now = 2_000L)
            .withPhotoClip(photo("p1"), now = 2_100L)

        val content = assertNotNull(NoteExport.prepare(note))

        assertEquals("Chiamare Marco", content.text)
        assertEquals(listOf("photos/p1.jpg"), content.photoPaths)
        assertFalse(content.hasInkImage)
        assertTrue(content.isComplete, "il testo digitato non aspetta nessun riconoscimento")
        assertTrue(content.markdown!!.contains("Digitata e con foto"), content.markdown)
    }

    @Test
    fun `una foto cancellata non si allega`() {
        val note = empty()
            .withPhotoClip(photo("p1"), now = 2_000L)
            .withTextClip(text("t1", "resta"), now = 2_100L)
            .withPhotoClipDeleted(PhotoClipId("p1"), now = 2_200L)

        assertEquals(emptyList(), NoteExport.prepare(note)!!.photoPaths)
    }

    @Test
    fun `una nota di sola foto si manda senza testo`() {
        val note = empty().withPhotoClip(photo("p1"), now = 2_000L)

        val content = assertNotNull(NoteExport.prepare(note))

        assertNull(content.text)
        assertEquals(1, content.photoPaths.size)
    }
}
