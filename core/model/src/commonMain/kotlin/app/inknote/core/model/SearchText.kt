package app.inknote.core.model

/**
 * Forma canonica del testo con cui si cerca.
 *
 * ## Cosa risolve, e cosa non era rotto
 *
 * Il `LIKE` di SQLite pareggia già maiuscole e minuscole **ASCII**, quindi "SPESA"
 * trovava "spesa" anche prima. Restavano tre problemi, e il terzo è il più grave:
 *
 * 1. **gli accenti** — "caffe" non trovava "caffè", e in italiano non è un caso
 *    marginale: chi cerca in fretta non mette gli accenti;
 * 2. **le maiuscole non ASCII** — "PERCHÉ" non trovava "perché", perché la È esce dal
 *    ripiegamento ASCII;
 * 3. **l'ordine delle parole** — "pane latte" non trovava "latte, pane", perché `LIKE`
 *    cerca una sottostringa contigua. Ma la gente digita parole, non sottostringhe: è
 *    il difetto che si incontra per primo.
 *
 * ## Come
 *
 * `lowercase()` di Kotlin è consapevole di Unicode e risolve il punto 2 da sé. Gli
 * accenti si togliono con una tabella esplicita (punto 1). La punteggiatura diventa
 * spazio, così il testo si riduce a parole separate e il punto 3 si risolve
 * confrontando insiemi di parole invece di sottostringhe.
 *
 * L'apostrofo è un separatore: in italiano "l'idraulico" va trovato cercando
 * "idraulico".
 *
 * ## Il numero di versione
 *
 * [VERSION] va alzato ogni volta che questa funzione cambia risultato. Le righe
 * indicizzate da una versione precedente si riconoscono e si ricalcolano: senza il
 * numero, migliorare la normalizzazione lascerebbe in archivio un indice misto, e
 * alcune note diventerebbero introvabili senza che nessuno capisca perché.
 */
object SearchText {

    const val VERSION: Int = 1

    /** Lettere accentate e loro corrispondenti senza segni. Le due stringhe vanno in parallelo. */
    private const val ACCENTED =
        "àáâãäåāăą" + "çćĉċč" + "èéêëēĕėęě" + "ìíîïıīĭį" + "ðđ" + "ñńň" +
            "òóôõöøōŏő" + "ŕř" + "śşš" + "ţť" + "ùúûüūŭůűų" + "ýÿ" + "žźż" + "ĝğ" + "ł"

    private const val FOLDED =
        "aaaaaaaaa" + "ccccc" + "eeeeeeeee" + "iiiiiiii" + "dd" + "nnn" +
            "ooooooooo" + "rr" + "sss" + "tt" + "uuuuuuuuu" + "yy" + "zzz" + "gg" + "l"

    /** Lettere che non si riducono a un carattere solo. */
    private val EXPANSIONS = mapOf('æ' to "ae", 'œ' to "oe", 'ß' to "ss", 'þ' to "th")

    /**
     * Riduce il testo alla forma con cui si confronta: minuscolo, senza accenti, con
     * le parole separate da un singolo spazio.
     *
     * @return `null` se dal testo non resta nulla di cercabile. Non la stringa vuota:
     *   in archivio la differenza fra "non c'è indice" e "indice vuoto" conta.
     */
    fun normalize(raw: String?): String? {
        if (raw == null) return null

        val builder = StringBuilder(raw.length)
        var separatorPending = false

        for (char in raw.lowercase()) {
            if (!char.isLetterOrDigit()) {
                // Lo spazio si aggiunge solo se poi arriva altro: così non restano
                // spazi in testa o in coda da ripulire dopo.
                separatorPending = builder.isNotEmpty()
                continue
            }
            if (separatorPending) {
                builder.append(' ')
                separatorPending = false
            }
            builder.append(fold(char))
        }

        return builder.toString().ifEmpty { null }
    }

    /**
     * Le parole da cercare, dalla più lunga alla più corta.
     *
     * L'ordine non è estetico: chi interroga l'archivio usa la prima per restringere,
     * e una parola lunga è quasi sempre più selettiva di una corta.
     */
    fun tokenize(query: String): List<String> {
        val normalized = normalize(query) ?: return emptyList()
        return normalized.split(' ')
            .filter { it.isNotEmpty() }
            .distinct()
            .sortedWith(compareByDescending<String> { it.length }.thenBy { it })
    }

    private fun fold(char: Char): String {
        EXPANSIONS[char]?.let { return it }
        val index = ACCENTED.indexOf(char)
        return if (index >= 0) FOLDED[index].toString() else char.toString()
    }

    /** Le due tabelle devono restare allineate: lo verifica un test. */
    internal val tablesAreAligned: Boolean get() = ACCENTED.length == FOLDED.length
}
