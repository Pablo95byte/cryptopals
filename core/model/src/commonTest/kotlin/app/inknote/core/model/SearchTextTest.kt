package app.inknote.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SearchTextTest {

    @Test
    fun `le due tabelle degli accenti sono allineate`() {
        // Se si aggiunge una lettera a una sola delle due, la normalizzazione
        // sballerebbe silenziosamente su tutte le lettere successive.
        assertTrue(SearchText.tablesAreAligned)
    }

    @Test
    fun `gli accenti spariscono`() {
        assertEquals("caffe", SearchText.normalize("caffè"))
        assertEquals("perche", SearchText.normalize("perché"))
        assertEquals("piu cosi", SearchText.normalize("più così"))
    }

    @Test
    fun `le maiuscole non ASCII vengono pareggiate`() {
        assertEquals("perche", SearchText.normalize("PERCHÉ"))
        assertEquals("caffe", SearchText.normalize("CAFFÈ"))
    }

    @Test
    fun `la punteggiatura diventa separazione fra parole`() {
        assertEquals("latte pane caffe", SearchText.normalize("Latte, pane, caffè!"))
        assertEquals("via manzoni 14", SearchText.normalize("via Manzoni, 14"))
    }

    @Test
    fun `l'apostrofo separa, perche l'elisione non deve nascondere la parola`() {
        assertEquals("l idraulico", SearchText.normalize("l'idraulico"))
        assertEquals("l idraulico", SearchText.normalize("l’idraulico"), "anche l'apostrofo tipografico")
    }

    @Test
    fun `gli spazi si riducono a uno e non restano ai bordi`() {
        assertEquals("due parole", SearchText.normalize("  due   \n parole  "))
    }

    @Test
    fun `le cifre restano, perche targhe e numeri si cercano`() {
        assertEquals("ab 421 xy", SearchText.normalize("AB 421 XY"))
        assertEquals("ospiti2026", SearchText.normalize("ospiti2026"))
    }

    @Test
    fun `un testo senza nulla di cercabile diventa null e non stringa vuota`() {
        assertNull(SearchText.normalize(null))
        assertNull(SearchText.normalize(""))
        assertNull(SearchText.normalize("   "))
        assertNull(SearchText.normalize("!?... ---"))
    }

    @Test
    fun `le lettere che valgono due caratteri si espandono`() {
        assertEquals("strasse", SearchText.normalize("straße"))
        assertEquals("caesar", SearchText.normalize("cæsar"))
    }

    @Test
    fun `le parole da cercare escono dalla più lunga`() {
        assertEquals(listOf("caffe", "pane", "e"), SearchText.tokenize("e pane caffè"))
    }

    @Test
    fun `le parole ripetute si contano una volta`() {
        assertEquals(listOf("pane"), SearchText.tokenize("pane PANE pane"))
    }

    @Test
    fun `una ricerca senza parole non produce parole`() {
        assertTrue(SearchText.tokenize("").isEmpty())
        assertTrue(SearchText.tokenize("   ").isEmpty())
        assertTrue(SearchText.tokenize("!!!").isEmpty())
    }

    @Test
    fun `la normalizzazione è idempotente`() {
        val once = SearchText.normalize("L'idraulico, perché? Caffè!")!!

        assertEquals(once, SearchText.normalize(once))
    }
}
