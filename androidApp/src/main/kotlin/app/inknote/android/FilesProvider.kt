package app.inknote.android

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import java.io.File
import java.io.FileNotFoundException

/**
 * Consegna i file delle note ad altre app: alla destinazione di una condivisione, per
 * leggerli (D40).
 *
 * ## Perché non `FileProvider`, e perché un processo a parte
 *
 * `FileProvider` è AndroidX. Ma il punto vero è un altro: **ogni `ContentProvider` del
 * processo principale viene creato all'avvio del processo, prima della nostra
 * Activity** — è la leva 2 di D32 e l'invariante 21. Questo sta nel processo `:files`
 * (vedi il manifest), che nasce solo quando un'altra app chiede un file. Il processo
 * della cattura non lo vede mai.
 *
 * ## Cosa si può fare
 *
 * Solo leggere: foto e immagini esportate, per le app a cui mandiamo una nota. Da D45 la
 * fotocamera è dentro il foglio e scrive da sé: nessuno ha più bisogno di scrivere da qui,
 * e una porta che non serve è una porta da chiudere. Il provider non è esportato: ci si
 * arriva solo con un permesso concesso da noi, indirizzo per indirizzo.
 */
class FilesProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val (_, file) = resolve(uri)
        if (mode.contains('w')) throw SecurityException("sola lettura: $uri")
        if (!file.isFile) throw FileNotFoundException(uri.toString())
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun getType(uri: Uri): String = when (uri.lastPathSegment?.substringAfterLast('.')) {
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "md" -> "text/markdown"
        else -> "application/octet-stream"
    }

    /** Nome e dimensione: le app di destinazione li chiedono per mostrare l'allegato. */
    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor {
        val (_, file) = resolve(uri)
        val columns = projection ?: arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        val cursor = MatrixCursor(columns)
        cursor.addRow(
            columns.map<String, Any?> { column ->
                when (column) {
                    OpenableColumns.DISPLAY_NAME -> file.name
                    OpenableColumns.SIZE -> file.length()
                    else -> null
                }
            }.toTypedArray(),
        )
        return cursor
    }

    private fun resolve(uri: Uri): Pair<NoteFiles.Root, File> {
        val context = context ?: throw FileNotFoundException("provider non pronto")
        val segments = uri.pathSegments
        if (segments.size < 2) throw FileNotFoundException(uri.toString())
        val root = NoteFiles.Root.entries.firstOrNull { it.segment == segments.first() }
            ?: throw FileNotFoundException(uri.toString())
        val relative = segments.drop(1).joinToString("/")
        if (!NoteFiles.isSafe(relative)) throw SecurityException("percorso non valido: $uri")

        val base = NoteFiles.rootDir(context, root).canonicalFile
        val file = File(base, relative).canonicalFile
        // Seconda difesa, dopo isSafe: il file risolto deve stare davvero dentro la radice.
        if (!file.path.startsWith(base.path + File.separator)) throw SecurityException("fuori radice: $uri")
        return root to file
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = throw UnsupportedOperationException()
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int =
        throw UnsupportedOperationException()
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int =
        throw UnsupportedOperationException()
}
