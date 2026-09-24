package app.inknote.android

/**
 * Quello che il processo sa di sé.
 *
 * Serve al misuratore (D32, D36): un'apertura è fredda solo se la **prima** Activity del
 * processo è il foglio. Se il processo l'ha avviato l'archivio, il primo foglio trova il
 * processo già caldo, e misurarlo dall'avvio del processo conterebbe il tempo passato
 * nell'archivio.
 */
object ProcessState {
    @Volatile
    var anyActivityCreated: Boolean = false
}
