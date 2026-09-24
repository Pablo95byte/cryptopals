package app.inknote.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val MIN = 60_000L
private const val HOUR = 60 * MIN
private const val DAY = 24 * HOUR

/** Italia d'estate: UTC+2. */
private const val OFFSET = 2 * HOUR

/** Il giorno dal 1970 di una data civile. */
private fun day(y: Int, m: Int, d: Int) = DateHints.civilToDay(y, m, d)

/** Un istante locale, in UTC. */
private fun at(y: Int, m: Int, d: Int, h: Int, min: Int = 0) = day(y, m, d) * DAY + h * HOUR + min * MIN - OFFSET

/** Giovedì 24 settembre 2026, mezzogiorno in Italia. */
private val NOW = at(2026, 9, 24, 12)

class DateHintsTest {

    private fun find(text: String) = DateHints.find(text, NOW, OFFSET)

    @Test
    fun `domani alle 9`() {
        assertEquals(DateHint(at(2026, 9, 25, 9), hasTime = true), find("Chiamare l'idraulico domani alle 9"))
    }

    @Test
    fun `un giorno della settimana senza ora vale alle 9 e lo dice`() {
        assertEquals(DateHint(at(2026, 9, 28, 9), hasTime = false), find("portare la bici lunedì"))
    }

    @Test
    fun `il giorno di oggi nominato vuol dire la settimana prossima`() {
        assertEquals(at(2026, 10, 1, 9), find("giovedi riunione")!!.atMillis)
    }

    @Test
    fun `in inglese, col pomeriggio`() {
        assertEquals(DateHint(at(2026, 9, 25, 15), hasTime = true), find("Call Anna tomorrow at 3pm"))
    }

    @Test
    fun `data numerica con l'ora`() {
        assertEquals(DateHint(at(2026, 10, 12, 15, 30), hasTime = true), find("riunione 12/10 ore 15:30"))
    }

    @Test
    fun `data con il mese scritto`() {
        assertEquals(DateHint(at(2026, 10, 12, 9), hasTime = false), find("compleanno di Giulia 12 ottobre"))
    }

    @Test
    fun `una data già passata quest'anno è dell'anno prossimo`() {
        assertEquals(at(2027, 1, 3, 9), find("3 gennaio dentista")!!.atMillis)
    }

    @Test
    fun `solo l'ora, ancora da venire, è oggi`() {
        assertEquals(at(2026, 9, 24, 18), find("aperitivo alle 18")!!.atMillis)
    }

    @Test
    fun `solo l'ora, già passata, è domani`() {
        assertEquals(at(2026, 9, 25, 8), find("sveglia alle 8")!!.atMillis)
    }

    @Test
    fun `l'ora col punto`() {
        assertEquals(at(2026, 9, 24, 21, 15), find("partita alle 21.15")!!.atMillis)
    }

    @Test
    fun `un numero qualunque non è una data`() {
        assertNull(find("comprare 2 litri di latte e 12 uova"))
    }

    @Test
    fun `una data impossibile non è una data`() {
        assertNull(find("scaffale 12/13"))
    }

    @Test
    fun `nessun testo, nessuna data`() {
        assertNull(DateHints.find(null, NOW, OFFSET))
    }
}

class ResurfaceTest {

    private fun note(id: String, created: Long, text: String = "idea $id") =
        Note.empty(CanvasSize(360f, 640f), now = created, id = NoteId(id))
            .withTextClip(TextClip(TextClipId("t$id"), created, text), now = created)

    @Test
    fun `una nota di una settimana fa oggi vince su quelle più vecchie`() {
        val weekAgo = note("settimana", NOW - 7 * DAY)
        val older = (1..20).map { note("vecchia$it", NOW - (40 + it) * DAY) }

        val picked = assertNotNull(Resurface.pick(older + weekAgo, NOW, OFFSET))

        assertEquals("settimana", picked.note.id.value)
        assertEquals(7, picked.daysAgo)
        assertTrue(picked.isAnniversary)
    }

    @Test
    fun `un anno fa oggi vince su una settimana fa`() {
        val picked = Resurface.pick(
            listOf(note("settimana", NOW - 7 * DAY), note("anno", NOW - 365 * DAY)),
            NOW,
            OFFSET,
        )

        assertEquals("anno", picked!!.note.id.value)
    }

    @Test
    fun `nello stesso giorno la scelta non cambia`() {
        val notes = (1..30).map { note("n$it", NOW - (10 + it * 3) * DAY) }

        val morning = Resurface.pick(notes, at(2026, 9, 24, 7), OFFSET)
        val evening = Resurface.pick(notes, at(2026, 9, 24, 23), OFFSET)

        assertEquals(morning!!.note.id, evening!!.note.id)
    }

    @Test
    fun `le note di questa settimana non riemergono`() {
        assertNull(Resurface.pick(listOf(note("fresca", NOW - 3 * DAY)), NOW, OFFSET))
    }

    @Test
    fun `note cancellate e vuote non riemergono`() {
        val deleted = note("cancellata", NOW - 30 * DAY).copy(deletedAt = NOW - DAY)
        val empty = Note.empty(CanvasSize(360f, 640f), now = NOW - 30 * DAY, id = NoteId("vuota"))

        assertNull(Resurface.pick(listOf(deleted, empty), NOW, OFFSET))
    }
}

class SortingTest {

    private val note = Note.empty(CanvasSize(360f, 640f), now = 1_000L, id = NoteId("n"))

    @Test
    fun `smistare non fa salire la revisione`() {
        val sorted = note.withSorted(5_000L)

        assertEquals(note.revision, sorted.revision)
        assertTrue(sorted.isSorted)
    }

    @Test
    fun `una nota mandata fuori è già smistata`() {
        assertTrue(note.withExport(ExportTarget.SystemShare, 2_000L).isSorted)
        assertFalse(note.isSorted)
    }

    @Test
    fun `smistata su un dispositivo è smistata, e vale il primo istante`() {
        val a = note.withSorted(5_000L)
        val b = note.withSorted(3_000L)

        assertEquals(3_000L, mergeNotes(a, b).sortedAt)
        assertEquals(mergeNotes(a, b), mergeNotes(b, a))
        assertEquals(3_000L, mergeNotes(note, b).sortedAt)
    }
}
