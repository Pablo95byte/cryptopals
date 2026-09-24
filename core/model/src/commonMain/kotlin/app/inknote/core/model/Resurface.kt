package app.inknote.core.model

/**
 * Una nota tornata a galla, e quanti giorni fa era stata scritta.
 *
 * @param isAnniversary `true` se è esattamente una settimana, un mese, tre mesi o un anno
 *   fa: l'interfaccia lo dice con le parole ("un anno fa oggi"), che toccano più di un numero.
 */
data class Resurfaced(val note: Note, val daysAgo: Int, val isAnniversary: Boolean)

/**
 * La riemersione (D51, D52): un'idea non persa ma mai più riletta è persa lo stesso.
 *
 * Una nota vecchia al giorno, in cima all'archivio, **senza notifiche**. Prima gli
 * anniversari — una settimana, un mese, tre mesi, un anno fa oggi — perché ritrovare
 * un'idea nel suo stesso giorno è ciò che fa venire voglia di riprenderla. Se non ce ne
 * sono, una nota qualunque più vecchia di una settimana.
 *
 * **Stabile nella giornata**: la stessa scelta a ogni apertura dello stesso giorno, altrimenti
 * la nota cambierebbe sotto il pollice e non sembrerebbe un pensiero ma una slot machine.
 * Nessun campo in archivio: la scelta è una funzione del giorno e delle note.
 */
object Resurface {

    val ANNIVERSARY_DAYS: List<Int> = listOf(365, 90, 30, 7)
    const val MIN_AGE_DAYS: Int = 7
    private const val DAY_MS = 86_400_000L

    /**
     * @param utcOffsetMillis lo scarto del fuso dell'utente adesso: "oggi" è un fatto locale,
     *   e il core non conosce i fusi (invariante 5).
     */
    fun pick(candidates: List<Note>, nowMillis: Long, utcOffsetMillis: Long): Resurfaced? {
        val today = dayOf(nowMillis, utcOffsetMillis)
        val eligible = candidates
            .filter { !it.isDeleted && !it.isEmpty }
            .map { it to (today - dayOf(it.createdAt, utcOffsetMillis)).toInt() }
            .filter { (_, age) -> age >= MIN_AGE_DAYS }
            .sortedBy { (note, _) -> note.id.value }
        if (eligible.isEmpty()) return null

        for (days in ANNIVERSARY_DAYS) {
            val hits = eligible.filter { (_, age) -> age == days }
            if (hits.isNotEmpty()) {
                val (note, age) = hits[stableIndex(today, hits.size)]
                return Resurfaced(note, age, isAnniversary = true)
            }
        }
        val (note, age) = eligible[stableIndex(today, eligible.size)]
        return Resurfaced(note, age, isAnniversary = false)
    }

    /** Il giorno locale di un istante, contato dal 1° gennaio 1970. */
    fun dayOf(millis: Long, utcOffsetMillis: Long): Long = (millis + utcOffsetMillis).floorDiv(DAY_MS)

    /** Un indice che cambia ogni giorno ma è lo stesso per tutto il giorno. */
    private fun stableIndex(day: Long, size: Int): Int {
        var x = day * 0x9E3779B97F4A7C15uL.toLong()
        x = x xor (x ushr 31)
        return (x).mod(size.toLong()).toInt()
    }
}
