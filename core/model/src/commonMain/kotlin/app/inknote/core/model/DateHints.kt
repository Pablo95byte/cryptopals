package app.inknote.core.model

/**
 * Un appuntamento trovato nel testo di una nota: "domani alle 9", "lunedì", "12/10 ore 15".
 *
 * @param hasTime `false` se il testo diceva il giorno ma non l'ora: allora [atMillis] è alle
 *   9 del mattino, e l'interfaccia lo lascia correggere.
 */
data class DateHint(val atMillis: Long, val hasTime: Boolean)

/**
 * Le date scritte in una nota, riconosciute (D51, D52).
 *
 * Non agisce da solo e **non compare mai sul foglio**: nell'archivio, sotto la nota, diventa
 * un suggerimento — "Ricordamelo: domani, 9:00" — che l'utente accetta con un tocco o ignora.
 * Proporlo durante la scrittura sarebbe chiedere una decisione a chi non ne vuole prendere
 * (D21).
 *
 * Italiano e inglese; si cerca la **prima** menzione. Lavora sul testo — oggi quello digitato,
 * domani quello riconosciuto dalla scrittura (D2). Il fuso arriva da fuori come scarto fisso:
 * un cambio d'ora fra adesso e la data trovata sposta il suggerimento di un'ora, e per un
 * promemoria che l'utente conferma è accettabile.
 */
object DateHints {

    private const val MINUTE = 60_000L
    private const val HOUR = 60 * MINUTE
    private const val DAY = 24 * HOUR
    private const val DEFAULT_HOUR = 9

    private val weekdays = listOf(
        listOf("luned[iì]", "monday", "mon"),
        listOf("marted[iì]", "tuesday", "tue"),
        listOf("mercoled[iì]", "wednesday", "wed"),
        listOf("gioved[iì]", "thursday", "thu"),
        listOf("venerd[iì]", "friday", "fri"),
        listOf("sabato", "saturday", "sat"),
        listOf("domenica", "sunday", "sun"),
    )

    private val months = listOf(
        listOf("gennaio", "gen", "january", "jan"),
        listOf("febbraio", "feb", "february"),
        listOf("marzo", "mar", "march"),
        listOf("aprile", "apr", "april"),
        listOf("maggio", "mag", "may"),
        listOf("giugno", "giu", "june", "jun"),
        listOf("luglio", "lug", "july", "jul"),
        listOf("agosto", "ago", "august", "aug"),
        listOf("settembre", "set", "september", "sep", "sept"),
        listOf("ottobre", "ott", "october", "oct"),
        listOf("novembre", "nov", "november"),
        listOf("dicembre", "dic", "december", "dec"),
    )

    private val relative = Regex("""\b(oggi|today|stasera|tonight|domani|tomorrow|dopodomani)\b""")
    // Confini di parola che conoscono le lettere accentate: `\b` da Java 19 guarda solo
    // l'ASCII, e "lunedì" non finirebbe mai su un confine. Trovato da un test.
    private const val START = """(?<![\p{L}\d])"""
    private const val END = """(?![\p{L}\d])"""

    private val weekday = Regex(START + "(" + weekdays.flatten().joinToString("|") + ")" + END)
    private val numericDate = Regex("""\b(\d{1,2})[/.-](\d{1,2})(?:[/.-](\d{2,4}))?\b""")
    private val namedDate = Regex("""\b(\d{1,2})\s+(""" + months.flatten().joinToString("|") + """)\b(?:\s+(\d{4}))?""")
    private val keywordTime = Regex("""\b(?:alle|ore|h|at)\s*(\d{1,2})(?:[:.](\d{2}))?\s*(am|pm)?\b""")
    private val clockTime = Regex("""\b(\d{1,2}):(\d{2})\s*(am|pm)?\b""")
    private val meridiemTime = Regex("""\b(\d{1,2})\s*(am|pm)\b""")

    fun find(text: String?, nowMillis: Long, utcOffsetMillis: Long): DateHint? {
        if (text.isNullOrBlank()) return null
        val lower = text.lowercase()
        val today = (nowMillis + utcOffsetMillis).floorDiv(DAY)
        val nowMinuteOfDay = ((nowMillis + utcOffsetMillis).mod(DAY) / MINUTE).toInt()

        val time = findTime(lower)
        val day: Long? = findDay(lower, today)

        return when {
            day != null -> {
                val minutes = time ?: (DEFAULT_HOUR * 60)
                DateHint(toUtc(day, minutes, utcOffsetMillis), hasTime = time != null)
            }
            time != null -> {
                // Solo l'ora: oggi se deve ancora venire, altrimenti domani.
                val targetDay = if (time > nowMinuteOfDay) today else today + 1
                DateHint(toUtc(targetDay, time, utcOffsetMillis), hasTime = true)
            }
            else -> null
        }
    }

    private fun findDay(text: String, today: Long): Long? {
        val candidates = mutableListOf<Pair<Int, Long>>() // posizione nel testo, giorno

        relative.find(text)?.let { match ->
            val offset = when (match.value) {
                "oggi", "today", "stasera", "tonight" -> 0
                "domani", "tomorrow" -> 1
                else -> 2
            }
            candidates += match.range.first to today + offset
        }

        weekday.find(text)?.let { match ->
            val index = weekdays.indexOfFirst { names -> names.any { Regex(it).matches(match.value) } }
            if (index >= 0) {
                // Il prossimo, mai oggi: "lunedì" detto di lunedì vuol dire fra una settimana.
                val todayIndex = (today + 3).mod(7L).toInt() // 1970-01-01 era giovedì
                var ahead = index - todayIndex
                if (ahead <= 0) ahead += 7
                candidates += match.range.first to today + ahead
            }
        }

        numericDate.find(text)?.let { match ->
            val (d, m, y) = match.destructured
            dateOf(d.toInt(), m.toInt(), y.takeIf { it.isNotEmpty() }?.toInt(), today)?.let {
                candidates += match.range.first to it
            }
        }

        namedDate.find(text)?.let { match ->
            val (d, name, y) = match.destructured
            val month = months.indexOfFirst { name in it } + 1
            if (month > 0) {
                dateOf(d.toInt(), month, y.takeIf { it.isNotEmpty() }?.toInt(), today)?.let {
                    candidates += match.range.first to it
                }
            }
        }

        return candidates.minByOrNull { it.first }?.second
    }

    /** I minuti dall'inizio del giorno, o `null` se il testo non dice un'ora. */
    private fun findTime(text: String): Int? {
        val matches = listOfNotNull(
            keywordTime.find(text)?.let { it.range.first to it },
            clockTime.find(text)?.let { it.range.first to it },
            meridiemTime.find(text)?.let { it.range.first to it },
        )
        val match = matches.minByOrNull { it.first }?.second ?: return null
        val groups = match.groupValues
        var hour = groups[1].toInt()
        val minute = groups.getOrNull(2)?.takeIf { it.isNotEmpty() && it.all(Char::isDigit) }?.toInt() ?: 0
        val meridiem = groups.lastOrNull { it == "am" || it == "pm" }
        if (meridiem == "pm" && hour < 12) hour += 12
        if (meridiem == "am" && hour == 12) hour = 0
        if (hour !in 0..23 || minute !in 0..59) return null
        return hour * 60 + minute
    }

    /** Giorno e mese senza anno vogliono dire la prossima volta che capitano. */
    private fun dateOf(day: Int, month: Int, year: Int?, today: Long): Long? {
        if (month !in 1..12 || day !in 1..31) return null
        val fullYear = year?.let { if (it < 100) 2000 + it else it }
        if (fullYear != null) return civilToDay(fullYear, month, day).takeIf { validDay(fullYear, month, day) }
        val thisYear = dayToYear(today)
        for (candidate in thisYear..thisYear + 1) {
            if (!validDay(candidate, month, day)) continue
            val epochDay = civilToDay(candidate, month, day)
            if (epochDay >= today) return epochDay
        }
        return null
    }

    private fun toUtc(epochDay: Long, minuteOfDay: Int, utcOffsetMillis: Long): Long =
        epochDay * DAY + minuteOfDay * MINUTE - utcOffsetMillis

    private fun validDay(year: Int, month: Int, day: Int): Boolean {
        val lengths = intArrayOf(31, if (isLeap(year)) 29 else 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
        return day <= lengths[month - 1]
    }

    private fun isLeap(year: Int) = (year % 4 == 0 && year % 100 != 0) || year % 400 == 0

    /** Da data civile a giorno dal 1970 (l'algoritmo di Howard Hinnant). */
    internal fun civilToDay(year: Int, month: Int, day: Int): Long {
        val y = (if (month <= 2) year - 1 else year).toLong()
        val era = (y).floorDiv(400L)
        val yoe = y - era * 400
        val mp = (month + 9) % 12
        val doy = (153 * mp + 2) / 5 + day - 1
        val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
        return era * 146097 + doe - 719468
    }

    private fun dayToYear(epochDay: Long): Int {
        val z = epochDay + 719468
        val era = (z).floorDiv(146097L)
        val doe = z - era * 146097
        val yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365
        val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
        val mp = (5 * doy + 2) / 153
        val month = if (mp < 10) mp + 3 else mp - 9
        return (yoe + era * 400 + if (month <= 2) 1 else 0).toInt()
    }
}
