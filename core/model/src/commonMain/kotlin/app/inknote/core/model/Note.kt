package app.inknote.core.model

/**
 * Dimensione logica del foglio su cui sono espresse le coordinate dei tratti.
 *
 * I tratti non sono salvati in pixel dello schermo: sono salvati in queste unità,
 * e ogni renderer scala di `target / canvas`. Senza questo, la stessa nota
 * risulterebbe di dimensioni diverse sul telefono, sul widget piccolo e sul
 * widget grande, e su un futuro iPad non sarebbe rileggibile.
 */
data class CanvasSize(val width: Float, val height: Float) {
    init {
        require(width > 0f && height > 0f) { "canvas non valido: ${width}x$height" }
    }
}

/**
 * Una nota scritta a mano.
 *
 * ## Campi pensati per il sync
 *
 * La v1 è tutta locale, ma i campi che servono al sync sono qui dal primo giorno:
 * [updatedAt], [revision] e [deletedAt]. Aggiungerli dopo avrebbe voluto dire una
 * migrazione sui dati degli utenti già installati, che è il modo più rapido di
 * perdere note altrui e prendersi una stella.
 *
 * @param revision contatore monotono incrementato a ogni modifica locale. Serve a
 *   capire se [recognizedText] è ancora valido e a ordinare le versioni quando i
 *   timestamp di due dispositivi non sono allineati.
 * @param deletedAt tombstone. Non cancelliamo le righe: una cancellazione deve
 *   poter viaggiare, altrimenti al primo sync la nota cancellata torna indietro.
 * @param voiceClips le registrazioni vocali della nota. Una lista e non un campo
 *   singolo: due dispositivi che registrano offline sulla stessa nota si fondono per
 *   unione, come i tratti, invece che sovrascriversi (D8, D25).
 * @param exports dove questa nota è già stata mandata, per non duplicarla al secondo
 *   invio (D31).
 * @param sortedAt quando la nota è stata smistata — tenuta, o mandata fuori — nello
 *   smistamento a carte (D52). `null` finché aspetta di essere smistata.
 * @param textClips il testo digitato con la tastiera, e [photoClips] le foto (D38).
 *   Liste di pezzi immutabili per la stessa ragione di [voiceClips].
 * @param recognizedText esito dell'OCR **sull'inchiostro**, `null` se non ancora
 *   calcolato. La trascrizione del parlato sta su ciascun [VoiceClip].
 * @param recognizedFromRevision revisione su cui l'OCR è stato calcolato: se è
 *   diversa da [revision], il testo è vecchio e va ricalcolato.
 */
data class Note(
    val id: NoteId,
    val canvas: CanvasSize,
    val strokes: List<Stroke>,
    val voiceClips: List<VoiceClip> = emptyList(),
    val exports: List<ExportRecord> = emptyList(),
    val textClips: List<TextClip> = emptyList(),
    val photoClips: List<PhotoClip> = emptyList(),
    val createdAt: Long,
    val updatedAt: Long,
    val revision: Long = 1L,
    val deletedAt: Long? = null,
    val recognizedText: String? = null,
    val recognizedFromRevision: Long? = null,
    val sortedAt: Long? = null,
) {
    val isDeleted: Boolean get() = deletedAt != null

    /** I tratti da disegnare, nell'ordine di disegno. */
    val visibleStrokes: List<Stroke> get() = strokes.filter { !it.isDeleted }

    /** Le registrazioni ancora presenti, in ordine di registrazione. */
    val visibleVoiceClips: List<VoiceClip> get() = voiceClips.filter { !it.isDeleted }

    val hasInk: Boolean get() = visibleStrokes.isNotEmpty()

    val hasVoice: Boolean get() = visibleVoiceClips.isNotEmpty()

    val visibleTextClips: List<TextClip> get() = textClips.filter { !it.isDeleted && it.text.isNotBlank() }

    val visiblePhotoClips: List<PhotoClip> get() = photoClips.filter { !it.isDeleted }

    val hasText: Boolean get() = visibleTextClips.isNotEmpty()

    val hasPhoto: Boolean get() = visiblePhotoClips.isNotEmpty()

    /** Il testo digitato, nell'ordine in cui è stato scritto. */
    val typedText: String? get() = visibleTextClips.joinToString("\n") { it.text.trim() }.ifEmpty { null }

    /** Una nota è vuota solo se non ha niente: né inchiostro, né voce, né testo, né foto. */
    val isEmpty: Boolean get() = !hasInk && !hasVoice && !hasText && !hasPhoto

    /**
     * `true` se l'inchiostro va (ri)riconosciuto.
     *
     * La condizione su [hasInk] non è una rifinitura: senza di essa una nota di sola
     * voce risulterebbe sempre da riconoscere, e la coda dell'OCR le girerebbe sopra
     * per sempre senza mai avere niente da leggere.
     */
    val needsRecognition: Boolean get() = hasInk && recognizedFromRevision != revision

    /** `true` se la nota non aspetta più lo smistamento: tenuta, o già mandata fuori (D52). */
    val isSorted: Boolean get() = sortedAt != null || exports.isNotEmpty()

    /**
     * Segna la nota come smistata. Come un invio, **non** fa salire la revisione:
     * smistare una nota non la modifica (D31, D52).
     */
    fun withSorted(now: Long): Note = if (sortedAt != null) this else copy(sortedAt = now)

    /** `true` se la nota è già stata mandata a questa destinazione. */
    fun wasSentTo(target: ExportTarget): Boolean = exports.any { it.target == target }

    /**
     * `true` se la nota è stata mandata, ma è cresciuta da allora.
     *
     * È la condizione che permette un "rimanda" sensato senza duplicare quelle già a
     * posto: se la revisione è la stessa, l'invio è ancora valido.
     */
    fun needsResendTo(target: ExportTarget): Boolean {
        val record = exports.firstOrNull { it.target == target } ?: return false
        // `<` e non `!=`: se l'invio registrato è a una revisione pari o superiore, la
        // destinazione ha già una versione aggiornata e rimandarla la duplicherebbe.
        return record.revision < revision
    }

    /** Registra un invio, o ne aggiorna uno precedente verso la stessa destinazione. */
    fun withExport(target: ExportTarget, now: Long): Note {
        val record = ExportRecord(target = target, sentAt = now, revision = revision)
        val others = exports.filterNot { it.target == target }
        // L'invio **non** avanza la revisione: mandare una nota non la modifica, e far
        // salire la revisione qui vorrebbe dire che ogni invio rende vecchi tutti gli
        // altri invii della stessa nota.
        return copy(exports = orderExports(others + record))
    }

    /** Le registrazioni ancora da trascrivere. */
    val voiceClipsNeedingTranscription: List<VoiceClip>
        get() = visibleVoiceClips.filter { it.needsTranscription }

    /**
     * Tutto il testo con cui questa nota si può ritrovare: il testo digitato, l'OCR
     * dell'inchiostro e le trascrizioni del parlato.
     */
    val searchableText: String
        get() = buildList {
            typedText?.let(::add)
            recognizedText?.let(::add)
            for (clip in visibleVoiceClips) clip.transcript?.let(::add)
        }.joinToString(" ")

    /** Aggiunge un pezzo di testo digitato. Uno vuoto non cambia la nota. */
    fun withTextClip(clip: TextClip, now: Long): Note {
        if (clip.text.isBlank()) return this
        return copy(textClips = orderTextClips(textClips + clip), updatedAt = now, revision = revision + 1)
    }

    /** Marca un testo come cancellato. Correggere un testo è questo più un [withTextClip]. */
    fun withTextClipDeleted(clipId: TextClipId, now: Long): Note {
        val index = textClips.indexOfFirst { it.id == clipId }
        if (index < 0 || textClips[index].isDeleted) return this
        return copy(
            textClips = textClips.toMutableList().also { it[index] = it[index].copy(deletedAt = now) },
            updatedAt = now,
            revision = revision + 1,
        )
    }

    fun withPhotoClip(clip: PhotoClip, now: Long): Note =
        copy(photoClips = orderPhotoClips(photoClips + clip), updatedAt = now, revision = revision + 1)

    fun withPhotoClipDeleted(clipId: PhotoClipId, now: Long): Note {
        val index = photoClips.indexOfFirst { it.id == clipId }
        if (index < 0 || photoClips[index].isDeleted) return this
        return copy(
            photoClips = photoClips.toMutableList().also { it[index] = it[index].copy(deletedAt = now) },
            updatedAt = now,
            revision = revision + 1,
        )
    }

    /** Aggiunge una registrazione producendo una nuova versione della nota. */
    fun withVoiceClip(clip: VoiceClip, now: Long): Note = copy(
        voiceClips = orderVoiceClips(voiceClips + clip),
        updatedAt = now,
        revision = revision + 1,
    )

    /** Marca una registrazione come cancellata senza rimuoverla. */
    fun withVoiceClipDeleted(clipId: VoiceClipId, now: Long): Note {
        val index = voiceClips.indexOfFirst { it.id == clipId }
        if (index < 0 || voiceClips[index].isDeleted) return this
        return copy(
            voiceClips = voiceClips.toMutableList().also { it[index] = it[index].copy(deletedAt = now) },
            updatedAt = now,
            revision = revision + 1,
        )
    }

    /** Aggiunge un tratto producendo una nuova versione della nota. */
    fun withStroke(stroke: Stroke, now: Long): Note = copy(
        strokes = orderStrokes(strokes + stroke),
        updatedAt = now,
        revision = revision + 1,
    )

    /** Marca un tratto come cancellato (gomma) senza rimuoverlo dalla lista. */
    fun withStrokeDeleted(strokeId: StrokeId, now: Long): Note {
        val index = strokes.indexOfFirst { it.id == strokeId }
        if (index < 0 || strokes[index].isDeleted) return this
        return copy(
            strokes = strokes.toMutableList().also { it[index] = it[index].copy(deletedAt = now) },
            updatedAt = now,
            revision = revision + 1,
        )
    }

    companion object {
        fun empty(canvas: CanvasSize, now: Long, id: NoteId = NoteId.random()): Note = Note(
            id = id,
            canvas = canvas,
            strokes = emptyList(),
            createdAt = now,
            updatedAt = now,
        )
    }
}

/**
 * Ordine di disegno canonico: prima gli evidenziatori, poi per istante di
 * creazione, e a parità per id.
 *
 * Deve essere una funzione totale e deterministica, non l'ordine di arrivo: due
 * dispositivi che hanno fuso la stessa nota devono disegnarla identica, e
 * l'evidenziatore va sotto l'inchiostro o lo copre.
 */
fun orderStrokes(strokes: List<Stroke>): List<Stroke> = strokes.sortedWith(
    compareBy({ if (it.pen.kind == PenKind.HIGHLIGHTER) 0 else 1 }, { it.createdAt }, { it.id.value }),
)
