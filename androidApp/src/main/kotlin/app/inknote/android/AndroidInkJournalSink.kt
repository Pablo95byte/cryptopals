package app.inknote.android

import android.content.Context
import app.inknote.core.capture.InkJournalSink
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Il giornale dell'inchiostro su disco.
 *
 * Tutta la logica sta in `core:capture` e ha i suoi test; qui c'è solo la scrittura
 * vera, che è la parte che dipende dalla piattaforma.
 *
 * ## Un thread solo, e non è quello dell'interfaccia
 *
 * `fd.sync()` aspetta che la memoria flash confermi la scrittura: pochi millisecondi
 * su un telefono buono, decine su uno economico, e ogni tanto di più. Fatto dentro il
 * sollevamento del dito, quel tempo cade fra un tratto e il successivo, cioè mentre
 * l'utente sta scrivendo: la parola dopo parte in ritardo (D35).
 *
 * Quindi ogni operazione passa da **un unico thread di scrittura**, condiviso da tutto
 * il processo. Essere uno solo è anche ciò che rende le tre operazioni esclusive fra
 * loro, come chiede [InkJournalSink]: una lettura non può vedere un record a metà.
 *
 * **Cosa costa:** fra la fine di un tratto e il suo arrivo su disco passano pochi
 * millisecondi invece di zero. [awaitWrites] li chiude quando il foglio esce dallo
 * schermo, che è il momento in cui il sistema può decidere di uccidere il processo.
 */
class AndroidInkJournalSink private constructor(private val resolveFile: () -> File) : InkJournalSink {

    /** Risolto sul thread di scrittura: anche trovare la cartella è disco. */
    private val file: File by lazy { resolveFile().also { it.parentFile?.mkdirs() } }

    override fun append(record: ByteArray) {
        // Il chiamante può riusare il suo array: se ne tiene una copia.
        val bytes = record.copyOf()
        writer.execute {
            try {
                FileOutputStream(file, /* append = */ true).use { out ->
                    out.write(bytes)
                    out.flush()
                    // Questa riga è il meccanismo. Senza `sync` i byte restano nei
                    // buffer del sistema, e un processo ucciso subito dopo li porta
                    // via: il giornale non proteggerebbe da niente (D20).
                    out.fd.sync()
                }
            } catch (e: Exception) {
                failures.incrementAndGet()
            }
        }
    }

    override fun readAll(): ByteArray =
        writer.submit(Callable { if (file.isFile) file.readBytes() else ByteArray(0) }).get()

    override fun discardPrefix(byteCount: Int) {
        writer.submit(Callable { discardOnWriter(byteCount) }).get()
    }

    private fun discardOnWriter(byteCount: Int) {
        if (!file.isFile) return
        val bytes = file.readBytes()
        if (byteCount >= bytes.size) {
            file.delete()
            return
        }
        val from = byteCount.coerceAtLeast(0)
        // Si scrive il resto in un file nuovo e lo si mette al posto del vecchio: la
        // rinomina è atomica, quindi un processo ucciso a metà lascia il giornale
        // vecchio intero, mai uno dimezzato.
        val next = File(file.parentFile, file.name + ".next")
        FileOutputStream(next).use { out ->
            out.write(bytes, from, bytes.size - from)
            out.flush()
            out.fd.sync()
        }
        check(next.renameTo(file)) { "giornale non sostituito" }
    }

    /**
     * Aspetta che le scritture in coda siano su disco, per al massimo [timeoutMs].
     *
     * Da chiamare quando il foglio esce dallo schermo. Il tetto c'è perché siamo sul
     * thread dell'interfaccia: meglio un tratto a rischio in un caso patologico che
     * un'app che non risponde.
     */
    fun awaitWrites(timeoutMs: Long = 500) {
        runCatching { writer.submit(Runnable {}).get(timeoutMs, TimeUnit.MILLISECONDS) }
    }

    companion object {

        /**
         * Le scritture fallite, per tutto il processo.
         *
         * Se è maggiore di zero il foglio deve dirlo: è l'unico caso — disco pieno —
         * in cui "al sicuro dal primo tratto" non vale. Prima questo numero lo teneva
         * `CaptureSession.journalFailures`, ma con la scrittura su un altro thread
         * l'errore non risale più al chiamante.
         */
        val failures = AtomicInteger(0)

        private val writer: ExecutorService = Executors.newSingleThreadExecutor { task ->
            Thread(task, "inknote-giornale").apply { isDaemon = true }
        }

        /**
         * Apre il giornale nell'area protetta dal **dispositivo**, non dalle credenziali.
         *
         * È l'unica area disponibile prima del primo sblocco dopo un riavvio, ed è ciò
         * che permette alla cattura sopra la schermata di blocco di salvare anche in
         * quel caso (D17, D24).
         *
         * **Compromesso accettato:** qui la cifratura è legata al dispositivo e non
         * alla credenziale dell'utente, quindi è più debole di quella dell'archivio.
         * Il giornale è però transitorio — vive fino al primo assorbimento in
         * archivio — mentre le note vere stanno nell'area protetta dalle credenziali.
         *
         * Non tocca il disco: la cartella si trova al primo uso, sul thread di
         * scrittura. Aprire il giornale non costa niente al primo fotogramma.
         */
        fun open(context: Context): AndroidInkJournalSink {
            val app = context.applicationContext ?: context
            return AndroidInkJournalSink {
                val storage = app.createDeviceProtectedStorageContext() ?: app
                File(storage.filesDir, "ink-journal.bin")
            }
        }
    }
}
