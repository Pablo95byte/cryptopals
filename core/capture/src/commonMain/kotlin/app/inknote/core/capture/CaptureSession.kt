package app.inknote.core.capture

import app.inknote.core.ink.InkConfig
import app.inknote.core.ink.StrokeBuilder
import app.inknote.core.model.CanvasSize
import app.inknote.core.model.Clock
import app.inknote.core.model.Note
import app.inknote.core.model.NoteId
import app.inknote.core.model.Pen
import app.inknote.core.model.Stroke
import app.inknote.core.model.StrokeId
import app.inknote.core.model.orderStrokes
import app.inknote.core.model.PhotoClip
import app.inknote.core.model.PhotoClipId
import app.inknote.core.model.TextClip
import app.inknote.core.model.TextClipId
import app.inknote.core.model.orderPhotoClips
import app.inknote.core.model.orderTextClips
import app.inknote.core.model.VoiceClip
import app.inknote.core.model.VoiceClipId
import app.inknote.core.model.orderVoiceClips

/**
 * Una sessione di scrittura: dall'apertura del foglio alla conferma.
 *
 * Non conosce il database, non conosce la UI e non ha dipendenze da iniettare. È
 * quello che serve perché la superficie di cattura possa accettare il primo tocco
 * al primo fotogramma (decisioni D19 e D20).
 *
 * Ogni tratto chiuso viene consegnato al giornale prima di tornare al chiamante:
 * quando [endStroke] ritorna, quell'inchiostro è su disco oppure in coda per
 * andarci, a seconda di come la piattaforma scrive (D35).
 */
class CaptureSession(
    val canvas: CanvasSize,
    private val journal: InkJournal,
    private val clock: Clock,
    val noteId: NoteId = NoteId.random(),
    private val config: InkConfig = InkConfig.Default,
    val createdAt: Long = clock.nowMillis(),
) {

    private val strokes = ArrayList<Stroke>()
    private val textClips = ArrayList<TextClip>()
    private val photoClips = ArrayList<PhotoClip>()
    private val voiceClips = ArrayList<VoiceClip>()

    /** Il testo digitato in questo foglio, nella sua ultima versione salvata. */
    private var currentText: TextClip? = null
    private var active: StrokeBuilder? = null

    /**
     * Quante volte la scrittura sul giornale è fallita.
     *
     * Non è una statistica: se è maggiore di zero l'interfaccia deve avvisare, perché
     * il disco pieno è l'unico caso in cui la promessa "al sicuro dal primo tratto"
     * non vale. L'inchiostro resta comunque in memoria — vedi [endStroke].
     */
    var journalFailures: Int = 0
        private set

    val strokeCount: Int get() = strokes.size

    val isEmpty: Boolean get() = strokes.isEmpty() && currentText == null &&
        photoClips.none { !it.isDeleted } && voiceClips.none { !it.isDeleted }

    val isDrawing: Boolean get() = active != null

    /**
     * Inizia un tratto.
     *
     * @return `false` se un tratto è già in corso. Un secondo contatto mentre si
     *   scrive è il palmo della mano o un dito appoggiato, non un secondo tratto
     *   voluto: va ignorato, non disegnato.
     */
    fun beginStroke(pen: Pen, id: StrokeId = StrokeId.random()): Boolean {
        if (active != null) return false
        active = StrokeBuilder(pen = pen, startedAt = clock.nowMillis(), config = config, id = id)
        return true
    }

    /** Registra un campione nel tratto in corso. `false` se non c'è un tratto aperto. */
    fun addSample(x: Float, y: Float, pressure: Float, tMs: Int): Boolean =
        active?.add(x = x, y = y, pressure = pressure, tMs = tMs) ?: false

    /**
     * Chiude il tratto in corso e lo mette al sicuro nel giornale.
     *
     * @return il tratto, oppure `null` se non c'era nulla da chiudere.
     */
    fun endStroke(): Stroke? {
        val builder = active ?: return null
        active = null

        val stroke = builder.build() ?: return null
        strokes += stroke

        // Se il giornale non accetta la scrittura — disco pieno, permessi — il tratto
        // resta comunque in memoria. Far sparire dallo schermo l'inchiostro appena
        // disegnato sarebbe il comportamento peggiore possibile: l'utente perde
        // fiducia nell'app e non saprà mai perché.
        runCatching {
            journal.record(
                JournalRecord(
                    noteId = noteId,
                    noteCreatedAt = createdAt,
                    canvas = canvas,
                    stroke = stroke,
                ),
            )
        }.onFailure { journalFailures++ }

        return stroke
    }

    /**
     * Mette al sicuro il testo digitato così com'è adesso (D38).
     *
     * Si chiama quando la tastiera si chiude, quando il foglio perde il fuoco, e dopo una
     * breve pausa nella digitazione. Se il testo è cambiato dall'ultima volta, la
     * versione vecchia va nel giornale col tombstone e la nuova come pezzo nuovo: il
     * testo resta immutabile come un tratto, e il recupero li fonde per id.
     *
     * @return il pezzo di testo corrente, o `null` se il campo è vuoto.
     */
    fun commitText(text: String): TextClip? {
        val previous = currentText
        if (previous != null && previous.text == text) return previous

        val now = clock.nowMillis()
        if (previous != null) {
            val deleted = previous.copy(deletedAt = now)
            textClips[textClips.indexOfFirst { it.id == previous.id }] = deleted
            journalItem(JournalItem.Text(deleted))
        }

        if (text.isBlank()) {
            currentText = null
            return null
        }
        val clip = TextClip(id = TextClipId.random(), writtenAt = now, text = text)
        textClips += clip
        currentText = clip
        journalItem(JournalItem.Text(clip))
        return clip
    }

    /**
     * Aggiunge una foto già salvata su disco (D38).
     *
     * @param path percorso relativo del file, come lo leggerà l'archivio.
     */
    fun addPhoto(path: String, id: PhotoClipId = PhotoClipId.random()): PhotoClip {
        val clip = PhotoClip(id = id, takenAt = clock.nowMillis(), path = path)
        photoClips += clip
        journalItem(JournalItem.Photo(clip))
        return clip
    }

    /**
     * Toglie una foto: la fotocamera è stata chiusa senza scattare.
     *
     * La foto entra nel giornale **prima** di aprire la fotocamera, perché mentre la
     * fotocamera è aperta il sistema può uccidere il nostro processo; se poi lo scatto non
     * arriva, qui se ne scrive il tombstone. Prende la foto intera e non l'id: dopo la
     * morte del processo la sessione nuova non la conosce, ma il giornale sì.
     */
    fun discardPhoto(clip: PhotoClip) {
        val deleted = clip.copy(deletedAt = clock.nowMillis())
        val index = photoClips.indexOfFirst { it.id == clip.id }
        if (index >= 0) photoClips[index] = deleted
        journalItem(JournalItem.Photo(deleted))
    }

    /**
     * Aggiunge una registrazione finita, già salvata su disco (D63).
     *
     * Entra nel giornale a registrazione chiusa, non all'inizio: un file audio
     * interrotto a metà non si riascolta, quindi non c'è niente da proteggere prima
     * (D25). La trascrizione arriva dopo, nell'archivio, fuori dal foglio.
     *
     * @param path percorso relativo del file, come lo leggerà l'archivio.
     */
    fun addVoice(
        path: String,
        durationMs: Int,
        recordedAt: Long = clock.nowMillis() - durationMs,
        id: VoiceClipId = VoiceClipId.random(),
    ): VoiceClip {
        val clip = VoiceClip(id = id, recordedAt = recordedAt, durationMs = durationMs.coerceAtLeast(0), audioPath = path)
        voiceClips += clip
        journalItem(JournalItem.Voice(clip))
        return clip
    }

    private fun journalItem(item: JournalItem) {
        runCatching {
            journal.record(JournalRecord(noteId, createdAt, canvas, item))
        }.onFailure { journalFailures++ }
    }

    /** Butta via il tratto in corso: gesto annullato, tocco accidentale. */
    fun cancelStroke() {
        active = null
    }

    /** La nota così com'è adesso, nell'ordine di disegno canonico. */
    fun note(): Note {
        val ordered = orderStrokes(strokes)
        val latest = listOfNotNull(
            ordered.maxOfOrNull { it.createdAt },
            textClips.maxOfOrNull { it.deletedAt ?: it.writtenAt },
            photoClips.maxOfOrNull { it.takenAt },
            voiceClips.maxOfOrNull { it.recordedAt },
        ).maxOrNull()
        return Note(
            id = noteId,
            canvas = canvas,
            strokes = ordered,
            voiceClips = orderVoiceClips(voiceClips),
            textClips = orderTextClips(textClips),
            photoClips = orderPhotoClips(photoClips),
            createdAt = createdAt,
            updatedAt = latest ?: createdAt,
            revision = ordered.size + textClips.size + photoClips.size + voiceClips.size + 1L,
        )
    }
}
