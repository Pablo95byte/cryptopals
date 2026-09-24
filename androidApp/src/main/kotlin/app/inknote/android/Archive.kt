package app.inknote.android

import android.content.Context
import android.os.Handler
import android.os.Looper
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import app.inknote.core.capture.InkJournal
import app.inknote.core.store.IngestResult
import app.inknote.core.store.InkNoteStore
import app.inknote.core.store.JournalIngest
import app.inknote.core.store.NoteStore
import java.util.concurrent.Executors

/**
 * L'archivio delle note su Android (D39).
 *
 * **La cattura non lo tocca mai** (D20, invariante 10): il foglio scrive nel giornale, e
 * l'archivio assorbe il giornale quando l'app si apre. Questo oggetto lo usano solo
 * l'archivio, la nota aperta e la ricevuta della condivisione.
 *
 * Tutto il lavoro sul database passa da un thread solo, suo. L'API dell'archivio è
 * sincrona (D14): chi chiama decide il thread, e qui il thread è sempre questo.
 */
object Archive {

    private val worker = Executors.newSingleThreadExecutor { task ->
        Thread(task, "inknote-archivio").apply { isDaemon = true }
    }
    private val main = Handler(Looper.getMainLooper())

    @Volatile
    private var store: NoteStore? = null

    /** Da chiamare solo dal thread dell'archivio. */
    private fun storeOn(context: Context): NoteStore = store ?: synchronized(this) {
        store ?: InkNoteStore.open(
            AndroidSqliteDriver(InkNoteStore.schema, context.applicationContext, "inknote.db"),
        ).also { store = it }
    }

    /** Esegue [work] sul thread dell'archivio e consegna il risultato sul thread dell'interfaccia. */
    fun <T> run(context: Context, work: (NoteStore) -> T, then: (T) -> Unit = {}) {
        val app = context.applicationContext
        worker.execute {
            val result = work(storeOn(app))
            main.post { then(result) }
        }
    }

    /**
     * Porta in archivio quello che il foglio ha scritto nel giornale, e fa la
     * manutenzione che non deve mai stare sul percorso di cattura.
     *
     * L'ordine è quello di D22: prima si salva, poi si svuota il giornale. Poi le foto
     * delle note assorbite passano nell'area protetta dalle credenziali.
     */
    fun ingest(context: Context, then: (IngestResult) -> Unit = {}) {
        val app = context.applicationContext
        run(app, { store ->
            val journal = InkJournal(AndroidInkJournalSink.open(app))
            val recovered = journal.recover().notes
            val result = JournalIngest(store).ingest(journal)
            NoteFiles.adopt(app, recovered.flatMap { note -> note.photoClips.map { it.path } })

            // L'indice di ricerca calcolato da una normalizzazione vecchia (D27).
            store.reindexSearch()
            // Le note cestinate da più di trenta giorni se ne vanno davvero, con le foto.
            val before = System.currentTimeMillis() - TRASH_RETENTION_MS
            NoteFiles.deleteAll(app, store.purgeDeleted(before))
            result
        }, then)
    }

    /** Un mese: abbastanza per accorgersi di aver cancellato la nota sbagliata. */
    private const val TRASH_RETENTION_MS = 30L * 24 * 60 * 60 * 1000
}
