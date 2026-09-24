package app.inknote.core.model

import kotlin.jvm.JvmInline
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Identificatore di una nota.
 *
 * È un UUID generato sul dispositivo e mai riassegnato. Non usiamo un intero
 * auto-incrementale del database: con un id locale il sync futuro non potrebbe
 * distinguere le note create offline su due dispositivi diversi, e aggiungerlo
 * dopo significherebbe migrare i dati degli utenti già installati.
 */
@JvmInline
value class NoteId(val value: String) {
    companion object {
        @OptIn(ExperimentalUuidApi::class)
        fun random(): NoteId = NoteId(Uuid.random().toString())
    }
}

/**
 * Identificatore di un singolo tratto.
 *
 * Ogni tratto ha un id proprio perché i tratti sono l'unità di sincronizzazione:
 * vedi [mergeNotes].
 */
@JvmInline
value class StrokeId(val value: String) {
    companion object {
        @OptIn(ExperimentalUuidApi::class)
        fun random(): StrokeId = StrokeId(Uuid.random().toString())
    }
}

/**
 * Sorgente del tempo, iniettata invece di leggere l'orologio di sistema dal core.
 *
 * Serve a due cose: tenere il core privo di dipendenze di piattaforma e rendere
 * i test deterministici (i timestamp sono la base del merge, quindi devono essere
 * controllabili nei test).
 */
fun interface Clock {
    fun nowMillis(): Long
}
