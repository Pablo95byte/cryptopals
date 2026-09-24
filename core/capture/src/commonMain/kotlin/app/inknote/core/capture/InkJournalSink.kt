package app.inknote.core.capture

/**
 * Il pezzo di giornale che dipende dalla piattaforma: aggiungere byte in coda a un
 * file e rileggerli.
 *
 * È volutamente minuscolo — tre operazioni — perché tutto il resto della logica sta
 * in [InkJournal] e va verificato senza un dispositivo. L'implementazione vera è
 * qualche riga per parte: su Android un `FileOutputStream` in append nei file
 * dell'app (D24), su iOS un `FileHandle`.
 *
 * **Le tre operazioni devono escludersi a vicenda.** Una lettura che vede un record
 * scritto a metà da un'altra parte dell'app lo scambierebbe per un record rotto, e lo
 * svuotamento successivo lo cancellerebbe. Su Android basta un solo thread che fa
 * tutto; su iOS, dove widget e app sono processi diversi, serve un lock sul file.
 */
interface InkJournalSink {

    /**
     * Aggiunge un record in coda.
     *
     * **Deve forzare la scrittura su disco**, non lasciarla nei buffer del sistema
     * operativo: il caso da cui ci stiamo difendendo è proprio che il processo muoia
     * subito dopo. Può farlo prima di ritornare, oppure su un thread suo — purché
     * **in ordine** e con un modo di aspettare la fine delle scritture prima che il
     * processo possa essere ucciso. Su Android è la seconda strada, perché la prima
     * metteva l'attesa della memoria flash fra una parola e l'altra (D35).
     */
    fun append(record: ByteArray)

    /** Tutti i byte del giornale, anche se l'ultimo record è troncato. */
    fun readAll(): ByteArray

    /**
     * Toglie i primi [byteCount] byte e **conserva tutto quello che viene dopo**.
     *
     * Non esiste uno svuotamento totale, di proposito: fra la lettura e lo
     * svuotamento l'archivio impiega il suo tempo, e intanto la cattura può aver
     * aggiunto un tratto. Cancellare il file intero porterebbe via quel tratto, che in
     * archivio non c'è (invariante 13).
     */
    fun discardPrefix(byteCount: Int)
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

    override fun discardPrefix(byteCount: Int) {
        bytes = bytes.copyOfRange(byteCount.coerceIn(0, bytes.size), bytes.size)
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
