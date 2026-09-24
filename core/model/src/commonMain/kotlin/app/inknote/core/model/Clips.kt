package app.inknote.core.model

import kotlin.jvm.JvmInline
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@JvmInline
value class TextClipId(val value: String) {
    companion object {
        @OptIn(ExperimentalUuidApi::class)
        fun random(): TextClipId = TextClipId(Uuid.random().toString())
    }
}

@JvmInline
value class PhotoClipId(val value: String) {
    companion object {
        @OptIn(ExperimentalUuidApi::class)
        fun random(): PhotoClipId = PhotoClipId(Uuid.random().toString())
    }
}

/**
 * Un pezzo di testo digitato con la tastiera (D38).
 *
 * ## Immutabile, come un tratto
 *
 * Un testo scritto non si corregge sul posto: correggerlo vuol dire marcare questo come
 * cancellato e aggiungerne uno nuovo. Sembra scomodo e non lo è — dall'interfaccia
 * l'utente vede un campo che modifica — ma è ciò che permette di fondere due
 * dispositivi senza conflitti (D8). Un campo di testo unico, modificabile, sarebbe il
 * primo punto del modello in cui due dispositivi possono non essere d'accordo, e allora
 * uno dei due perde quello che ha scritto.
 */
data class TextClip(
    val id: TextClipId,
    val writtenAt: Long,
    val text: String,
    val deletedAt: Long? = null,
) {
    val isDeleted: Boolean get() = deletedAt != null
}

/**
 * Una foto scattata dal foglio (D38).
 *
 * @param path percorso **relativo** del file, per esempio `photos/<id>.jpg`. Relativo di
 *   proposito: su Android una foto scattata a telefono bloccato nasce nell'area protetta
 *   dal dispositivo e viene spostata nell'area protetta dalle credenziali quando entra in
 *   archivio (D24). Con un percorso assoluto lo spostamento cambierebbe il dato; così
 *   cambia solo la radice, che è affare della piattaforma.
 */
data class PhotoClip(
    val id: PhotoClipId,
    val takenAt: Long,
    val path: String,
    val deletedAt: Long? = null,
) {
    val isDeleted: Boolean get() = deletedAt != null
}

/** Ordine deterministico, per la stessa ragione di [orderStrokes]. */
fun orderTextClips(clips: List<TextClip>): List<TextClip> =
    clips.sortedWith(compareBy({ it.writtenAt }, { it.id.value }))

/** Ordine deterministico, per la stessa ragione di [orderStrokes]. */
fun orderPhotoClips(clips: List<PhotoClip>): List<PhotoClip> =
    clips.sortedWith(compareBy({ it.takenAt }, { it.id.value }))
