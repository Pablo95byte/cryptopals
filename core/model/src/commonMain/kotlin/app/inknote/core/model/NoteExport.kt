package app.inknote.core.model

/**
 * Una nota pronta da mandare fuori.
 *
 * @param text il testo nudo, senza decorazioni: è quello che finisce nel foglio di
 *   condivisione, e da lì magari in un messaggio. `null` quando non c'è ancora niente
 *   da scrivere, perché il riconoscimento non è stato fatto.
 * @param markdown la stessa cosa con la struttura: le parti dettate citate, e in fondo
 *   da dove viene. Per i sistemi a file e per chi incolla in Notion.
 * @param fileBaseName nome del file, senza estensione.
 * @param isComplete `false` se il riconoscimento dell'inchiostro o di una registrazione
 *   non è ancora arrivato. L'interfaccia deve dirlo prima di mandare, non dopo.
 * @param hasInkImage `true` se c'è inchiostro da allegare come immagine. È l'originale:
 *   il testo può sbagliare una parola, l'immagine no.
 * @param photoPaths le foto da allegare, come percorsi relativi (D38).
 */
data class ExportContent(
    val text: String?,
    val markdown: String?,
    val fileBaseName: String,
    val isComplete: Boolean,
    val hasInkImage: Boolean,
    val photoPaths: List<String> = emptyList(),
)

/**
 * Prepara una nota per uscire da qui.
 *
 * ## Perché l'inchiostro diventa testo
 *
 * Keep, Notion e i loro pari accettano testo, non calligrafia. Mandare una nota scritta
 * a mano vuol dire quindi mandare il testo riconosciuto **con l'immagine
 * dell'inchiostro allegata**: il testo serve a ritrovarla e a leggerla dove è arrivata,
 * l'immagine è l'originale e non sbaglia parole (D31).
 *
 * ## Perché la data arriva da fuori
 *
 * Formattare una data richiede fuso orario e lingua dell'utente, che sono cose di
 * piattaforma. Il core non le conosce e non deve tirarsi dentro una libreria di date
 * per una riga in fondo a un file: chi chiama passa l'etichetta già scritta.
 */
object NoteExport {

    private const val NAME_MAX_WORDS = 6
    private const val NAME_MAX_LENGTH = 48

    /**
     * @param dateLabel data già formattata dalla piattaforma, per la riga finale.
     * @return `null` se non c'è niente da mandare: nota vuota o cestinata.
     */
    fun prepare(note: Note, dateLabel: String? = null): ExportContent? {
        if (note.isDeleted || note.isEmpty) return null

        val transcripts = note.visibleVoiceClips.mapNotNull { it.transcript }
        val pieces = buildList {
            // Il testo digitato prima: è l'unico che non passa da un riconoscimento, e
            // quindi l'unico sicuramente giusto.
            note.typedText?.let(::add)
            note.recognizedText?.let(::add)
            addAll(transcripts)
        }
        val text = pieces.joinToString("\n\n").ifEmpty { null }

        val pendingTranscription = note.visibleVoiceClips.any { it.needsTranscription }
        val isComplete = !note.needsRecognition && !pendingTranscription

        return ExportContent(
            text = text,
            markdown = markdown(note, transcripts, dateLabel, isComplete),
            fileBaseName = fileBaseName(note, text),
            isComplete = isComplete,
            hasInkImage = note.hasInk,
            photoPaths = note.visiblePhotoClips.map { it.path },
        )
    }

    private fun markdown(
        note: Note,
        transcripts: List<String>,
        dateLabel: String?,
        isComplete: Boolean,
    ): String? {
        val content = buildList {
            note.typedText?.let(::add)
            note.recognizedText?.let(::add)
            // Le parti dettate si citano: chi rilegge fra sei mesi deve sapere che quel
            // pezzo viene da una registrazione e non da ciò che ha scritto.
            for (transcript in transcripts) add(transcript.prependIndent("> "))
        }
        // Senza contenuto non si scrive un file: un Markdown con dentro solo una riga
        // orizzontale e un piè di pagina non serve a nessuno. Chi chiama manda solo
        // l'immagine dell'inchiostro.
        if (content.isEmpty()) return null

        val footer = buildList {
            add(originLabel(note))
            dateLabel?.let(::add)
        }.joinToString(" · ")

        return buildList {
            addAll(content)
            add("---")
            add(footer)
            if (!isComplete) {
                // Detto nel file, non solo nell'app: la nota vivrà altrove, e chi la
                // rilegge lì deve sapere che il testo non è stato verificato.
                add("*Il riconoscimento non era completo: controlla il testo con l'immagine.*")
            }
        }.joinToString("\n\n")
    }

    private fun originLabel(note: Note): String {
        val parts = buildList {
            if (note.hasInk) add("scritta a mano")
            if (note.hasText) add("digitata")
            if (note.hasVoice) add("dettata")
            if (note.hasPhoto) add("con foto")
        }
        return when (parts.size) {
            0 -> "Nota"
            1 -> parts.single()
            else -> parts.dropLast(1).joinToString(", ") + " e " + parts.last()
        }.replaceFirstChar { it.uppercase() }
    }

    /**
     * Nome del file ricavato dalle prime parole, come lo scriverebbe una persona.
     *
     * Riusa la normalizzazione della ricerca: accenti e punteggiatura vanno via, e il
     * risultato è già una sequenza di parole separate da spazi.
     */
    private fun fileBaseName(note: Note, text: String?): String {
        val slug = SearchText.normalize(text)
            ?.split(' ')
            ?.filter { it.isNotEmpty() }
            ?.take(NAME_MAX_WORDS)
            ?.joinToString("-")
            ?.take(NAME_MAX_LENGTH)
            ?.trimEnd('-')

        return if (slug.isNullOrEmpty()) "nota-${note.id.value.take(8)}" else slug
    }
}
