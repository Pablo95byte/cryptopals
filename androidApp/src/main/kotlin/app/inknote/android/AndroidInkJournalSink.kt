package app.inknote.android

import android.content.Context
import app.inknote.core.capture.InkJournalSink
import java.io.File
import java.io.FileOutputStream

/**
 * Il giornale dell'inchiostro su disco.
 *
 * Tutta la logica sta in `core:capture` e ha i suoi test; qui c'è solo la scrittura
 * vera, che è la parte che dipende dalla piattaforma.
 */
class AndroidInkJournalSink(private val file: File) : InkJournalSink {

    override fun append(record: ByteArray) {
        file.parentFile?.mkdirs()
        FileOutputStream(file, /* append = */ true).use { out ->
            out.write(record)
            out.flush()
            // Questa riga è il meccanismo. Senza `sync` i byte restano nei buffer del
            // sistema, e un processo ucciso subito dopo li porta via: il giornale non
            // proteggerebbe da niente, che è esattamente il caso per cui esiste (D20).
            out.fd.sync()
        }
    }

    override fun readAll(): ByteArray = if (file.isFile) file.readBytes() else ByteArray(0)

    override fun clear() {
        file.delete()
    }

    companion object {

        /**
         * Apre il giornale nell'area protetta dal **dispositivo**, non dalle credenziali.
         *
         * È l'unica area disponibile prima del primo sblocco dopo un riavvio, ed è ciò
         * che permette alla cattura sopra la schermata di blocco di salvare anche in
         * quel caso (D17, dove il problema era annotato come caveat aperto).
         *
         * **Compromesso accettato:** qui la cifratura è legata al dispositivo e non
         * alla credenziale dell'utente, quindi è più debole di quella dell'archivio.
         * Il giornale è però transitorio — vive fino al primo assorbimento in
         * archivio — mentre le note vere stanno nell'area protetta dalle credenziali.
         */
        fun open(context: Context): AndroidInkJournalSink {
            val storage = context.createDeviceProtectedStorageContext() ?: context
            return AndroidInkJournalSink(File(storage.filesDir, "ink-journal.bin"))
        }
    }
}
