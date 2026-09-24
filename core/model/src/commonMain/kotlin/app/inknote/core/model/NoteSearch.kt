package app.inknote.core.model

/**
 * Confronto e ordinamento dei risultati di ricerca.
 *
 * Sta nel modello, e non nell'archivio, per due ragioni: è logica pura e quindi
 * verificabile senza database, e le regole che decidono *quale nota viene prima* sono
 * una scelta di prodotto, non un dettaglio di SQL.
 *
 * ## Perché non tutto in SQL
 *
 * Chiedere a SQLite "tutte le parole, in qualsiasi ordine" vorrebbe dire una
 * condizione per parola, cioè una query di lunghezza variabile — oppure FTS5, che su
 * Android dipende dalla versione di SQLite del telefono e imporrebbe un minimo di
 * sistema per una comodità (la stessa ragione di D15). Conviene invece restringere in
 * SQL con la parola più selettiva e rifinire qui, su poche decine di candidate.
 */
object NoteSearch {

    /** `true` se la nota contiene **tutte** le parole cercate, in qualsiasi ordine. */
    fun matches(note: Note, tokens: List<String>): Boolean =
        matchesText(SearchText.normalize(note.searchableText), tokens)

    /**
     * Come [matches], ma su testo **già normalizzato**.
     *
     * È la forma che usa l'archivio: lì il testo normalizzato è già in una colonna, e
     * ricostruire la nota intera per confrontarla sarebbe due query per candidata.
     */
    fun matchesText(normalized: String?, tokens: List<String>): Boolean {
        if (tokens.isEmpty() || normalized == null) return false
        return tokens.all { normalized.contains(it) }
    }

    /**
     * Quanto bene la nota risponde alla ricerca.
     *
     * Una parola che comincia una parola del testo conta il doppio di una che capita in
     * mezzo: cercando "pane", "pane integrale" è una risposta migliore di
     * "accompanenare". Non è una raffinatezza da motore di ricerca, è la differenza fra
     * un elenco utile e un elenco in cui il risultato giusto sta terzo.
     */
    fun score(note: Note, tokens: List<String>): Int =
        scoreText(SearchText.normalize(note.searchableText), tokens)

    /** Come [score], ma su testo già normalizzato. */
    fun scoreText(normalized: String?, tokens: List<String>): Int {
        if (normalized == null) return 0
        var total = 0
        for (token in tokens) {
            val index = normalized.indexOf(token)
            if (index < 0) continue
            total += if (isWordStart(normalized, index)) 2 else 1
        }
        return total
    }

    /**
     * Ordina i risultati: prima chi risponde meglio, poi le note più recenti, e a
     * parità l'id — perché l'ordine dei risultati non deve cambiare fra due ricerche
     * identiche.
     */
    fun rank(notes: List<Note>, tokens: List<String>): List<Note> = notes.sortedWith(
        compareByDescending<Note> { score(it, tokens) }
            .thenByDescending { it.updatedAt }
            .thenBy { it.id.value },
    )

    /** Cerca fra note già in memoria. Utile per elenchi piccoli e per i test. */
    fun filter(notes: List<Note>, query: String): List<Note> {
        val tokens = SearchText.tokenize(query)
        if (tokens.isEmpty()) return emptyList()
        return rank(notes.filter { matches(it, tokens) }, tokens)
    }

    private fun isWordStart(haystack: String, index: Int): Boolean =
        index == 0 || haystack[index - 1] == ' '
}
