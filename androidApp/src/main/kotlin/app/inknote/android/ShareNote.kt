package app.inknote.android

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import app.inknote.core.model.ExportTarget
import app.inknote.core.model.Note
import app.inknote.core.model.NoteExport
import app.inknote.core.model.NoteId
import java.io.File
import java.text.DateFormat
import java.util.Date

/**
 * Manda una nota fuori, col foglio di condivisione del sistema (D31, fase 1).
 *
 * Una sola strada per tutte le destinazioni: Keep, Notion, Obsidian, la mail, i
 * messaggi, e le app che ancora non esistono. Si manda il testo — quello digitato e, più
 * avanti, quello riconosciuto — **con l'immagine dell'inchiostro e le foto allegate**.
 */
object ShareNote {

    fun share(activity: Activity, note: Note) {
        val app = activity.applicationContext
        Thread({
            val dateLabel = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(note.createdAt))
            // La firma è l'anello di crescita (D68): chi riceve la nota scopre da dove viene.
            val content = NoteExport.prepare(note, dateLabel, app.getString(R.string.share_signature))
            if (content == null) {
                activity.runOnUiThread { Toast.makeText(app, R.string.share_nothing, Toast.LENGTH_SHORT).show() }
                return@Thread
            }

            val uris = ArrayList<Uri>()
            if (content.hasInkImage) {
                val relative = "${NoteFiles.EXPORTS_DIR}/${content.fileBaseName}.png"
                val file = File(NoteFiles.rootDir(app, NoteFiles.Root.CACHE), relative)
                if (NoteRenderer.exportPng(note, file)) uris += NoteFiles.uri(NoteFiles.Root.CACHE, relative)
            }
            for (path in content.photoPaths) {
                val (root, _) = NoteFiles.locate(app, path) ?: continue
                uris += NoteFiles.uri(root, path)
            }

            val send = when {
                uris.isEmpty() -> Intent(Intent.ACTION_SEND).setType("text/plain")
                uris.size == 1 -> Intent(Intent.ACTION_SEND).setType("image/*")
                    .putExtra(Intent.EXTRA_STREAM, uris.single())
                else -> Intent(Intent.ACTION_SEND_MULTIPLE).setType("image/*")
                    .putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            }
            content.text?.let { send.putExtra(Intent.EXTRA_TEXT, it) }
            if (uris.isNotEmpty()) {
                // Il permesso di lettura viaggia con la ClipData: senza, alcune app di
                // destinazione ricevono l'indirizzo e non riescono ad aprirlo.
                val clip = ClipData.newRawUri(null, uris.first())
                for (uri in uris.drop(1)) clip.addItem(ClipData.Item(uri))
                send.clipData = clip
                send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            // Si registra l'invio solo quando l'utente sceglie davvero una destinazione:
            // aprire il foglio e chiuderlo non è mandare (D31, invariante 18).
            val callback = PendingIntent.getBroadcast(
                app,
                note.id.value.hashCode(),
                Intent(app, ShareResultReceiver::class.java).putExtra(ShareResultReceiver.EXTRA_NOTE_ID, note.id.value),
                PendingIntent.FLAG_UPDATE_CURRENT or
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0,
            )
            val chooser = Intent.createChooser(send, app.getString(R.string.share_title), callback.intentSender)
            activity.runOnUiThread { activity.startActivity(chooser) }
        }, "inknote-condivisione").start()
    }
}

/** La ricevuta del foglio di condivisione: l'utente ha scelto una destinazione. */
class ShareResultReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val noteId = intent.getStringExtra(EXTRA_NOTE_ID) ?: return
        val pending = goAsync()
        Archive.run(context, { store ->
            val note = store.note(NoteId(noteId)) ?: return@run
            store.save(note.withExport(ExportTarget.SystemShare, System.currentTimeMillis()))
        }, { pending.finish() })
    }

    companion object {
        const val EXTRA_NOTE_ID = "app.inknote.android.NOTE_ID"
    }
}
