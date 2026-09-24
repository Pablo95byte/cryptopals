package app.inknote.core.model

import kotlin.jvm.JvmInline

/**
 * Dove una nota è stata mandata.
 *
 * È una stringa e non un enum di proposito: le destinazioni si aggiungono senza
 * toccare il modello, e una destinazione arrivata da una versione più nuova dell'app
 * si rilegge invece di far esplodere la lettura dell'archivio.
 */
@JvmInline
value class ExportTarget(val value: String) {
    companion object {
        /** Il foglio di condivisione del sistema: da lì la nota va dove vuole l'utente. */
        val SystemShare = ExportTarget("share")

        /** Un file Markdown, per i sistemi a file (Obsidian e simili). */
        val Markdown = ExportTarget("markdown")

        val Notion = ExportTarget("notion")
    }
}

/**
 * Il fatto che una nota è stata mandata da qualche parte.
 *
 * Serve a non duplicare: senza questa traccia, un secondo invio della stessa nota
 * creerebbe una seconda pagina in Notion, e l'utente si ritroverebbe l'archivio altrui
 * pieno di doppioni per colpa nostra.
 *
 * @param revision la revisione della nota che è stata mandata. Se la nota è cresciuta
 *   da allora, l'invio è vecchio e ha senso rimandarla: vedi [Note.needsResendTo].
 */
data class ExportRecord(
    val target: ExportTarget,
    val sentAt: Long,
    val revision: Long,
)

/** Ordine deterministico, come per tratti e registrazioni. */
fun orderExports(records: List<ExportRecord>): List<ExportRecord> =
    records.sortedBy { it.target.value }
