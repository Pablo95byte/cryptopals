package app.inknote.core.capture

/**
 * Il pezzo di giornale che dipende dalla piattaforma: aggiungere byte in coda a un
 * file e rileggerli.
 *
 * È volutamente minuscolo — tre operazioni — perché tutto il resto della logica sta
 * in [InkJournal] e va verificato senza un dispositivo. L'implementazione vera è
 * qualche riga per parte: su Android un `FileOutputStream` in append sulla cache,
 * su iOS un `FileHandle`.
 */
interface InkJournalSink {

    /**
     * Aggiunge un record in coda.
     *
     * **Deve forzare la scrittura su disco prima di ritornare.** Un giornale che
     * resta nei buffer del sistema operativo non protegge da niente: il caso da cui
     * ci stiamo difendendo è proprio che il processo muoia subito dopo.
     */
    fun append(record: ByteArray)

    /** Tutti i byte del giornale, anche se l'ultimo record è troncato. */
    fun readAll(): ByteArray

    /** Svuota il giornale. Da chiamare solo dopo che i record sono in archivio. */
    fun clear()
}

/**
 * Giornale in memoria, per i test e per le anteprime dell'interfaccia.
 *
 * Non va usato in produzione: sparisce col processo, che è esattamente ciò contro
 * cui serve il giornale.
 */
class InMemoryInkJournalSink(initial: ByteArray = ByteArray(0)) : InkJournalSink {

    private var bytes: ByteArray = initial

    override fun append(record: ByteArray) {
        bytes = bytes + record
    }

    override fun readAll(): ByteArray = bytes.copyOf()

    override fun clear() {
        bytes = ByteArray(0)
    }

    /** Simula un processo morto a metà scrittura: taglia gli ultimi [bytesLost] byte. */
    fun truncate(bytesLost: Int) {
        bytes = bytes.copyOf((bytes.size - bytesLost).coerceAtLeast(0))
    }

    /** Simula byte illeggibili in coda (settore danneggiato, scrittura parziale). */
    fun corruptLastByte() {
        if (bytes.isNotEmpty()) bytes[bytes.size - 1] = (bytes[bytes.size - 1] + 1).toByte()
    }
}
