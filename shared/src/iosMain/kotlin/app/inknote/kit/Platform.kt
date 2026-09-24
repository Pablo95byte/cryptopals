package app.inknote.kit

import app.cash.sqldelight.driver.native.NativeSqliteDriver
import app.inknote.core.model.Clock
import app.inknote.core.store.InkNoteStore
import app.inknote.core.store.NoteStore
import platform.Foundation.NSDate
import platform.Foundation.timeIntervalSince1970

/**
 * Il pezzo di iOS che deve stare in Kotlin: l'orologio di parete e l'apertura
 * dell'archivio. Il resto della piattaforma — file, fotocamera, interfaccia — sta in
 * Swift, dove le API di Apple sono di casa.
 */
object IosPlatform {

    /** L'ora di parete, per i timestamp delle note: devono avere senso fra dispositivi. */
    val wallClock: Clock = Clock { (NSDate().timeIntervalSince1970 * 1000.0).toLong() }

    /**
     * Apre l'archivio nel contenitore dell'app, migrazioni comprese.
     *
     * Il driver lo costruisce la piattaforma, come dice D14: qui, perché su iOS il
     * driver è Kotlin anche lui.
     */
    fun openStore(name: String = "inknote.db"): NoteStore =
        InkNoteStore.open(NativeSqliteDriver(InkNoteStore.schema, name))
}
