package app.inknote.core.model

import kotlin.jvm.JvmInline
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@JvmInline
value class VoiceClipId(val value: String) {
    companion object {
        @OptIn(ExperimentalUuidApi::class)
        fun random(): VoiceClipId = VoiceClipId(Uuid.random().toString())
    }
}

/**
 * Una registrazione vocale che appartiene a una nota.
 *
 * ## L'audio si conserva, non solo la trascrizione
 *
 * Per una nota vocale **l'audio è la nota**, e la trascrizione è solo l'indice che
 * serve a ritrovarla: è lo stesso rapporto che c'è fra l'inchiostro e l'OCR (D1, D2).
 * Il riconoscimento del parlato sbaglia una parola ogni tanto, e se quella parola era
 * un numero di telefono o un indirizzo la nota, senza l'audio, è persa. Tenere
 * l'originale nell'un caso e buttarlo nell'altro sarebbe incoerente.
 *
 * ## La trascrizione non diventa mai vecchia
 *
 * Una registrazione, una volta fatta, non cambia più — come un tratto (D8). Quindi la
 * sua trascrizione, una volta calcolata, è definitiva, e qui non serve il campo di
 * "revisione a cui si riferisce" che invece serve per l'inchiostro: lì la nota si può
 * continuare a scrivere, e il testo riconosciuto invecchia.
 *
 * @param transcript `null` finché il riconoscimento non è stato fatto.
 * @param audioPath percorso del file audio, `null` se l'audio non è (più) disponibile:
 *   può succedere dopo un ripristino da backup che non ha portato i file.
 */
data class VoiceClip(
    val id: VoiceClipId,
    val recordedAt: Long,
    val durationMs: Int,
    val transcript: String? = null,
    val audioPath: String? = null,
    val deletedAt: Long? = null,
) {
    init {
        require(durationMs >= 0) { "durata negativa: $durationMs" }
    }

    val isDeleted: Boolean get() = deletedAt != null

    /** `true` se il parlato non è ancora stato riconosciuto. */
    val needsTranscription: Boolean get() = transcript == null

    /** `true` se la registrazione si può ancora riascoltare. */
    val hasAudio: Boolean get() = audioPath != null
}

/**
 * Ordine deterministico delle registrazioni di una nota.
 *
 * Stessa ragione dell'ordine dei tratti: due dispositivi che hanno fuso la stessa nota
 * devono presentarla identica, e l'ordine di arrivo non è una funzione dei dati.
 */
fun orderVoiceClips(clips: List<VoiceClip>): List<VoiceClip> =
    clips.sortedWith(compareBy({ it.recordedAt }, { it.id.value }))
