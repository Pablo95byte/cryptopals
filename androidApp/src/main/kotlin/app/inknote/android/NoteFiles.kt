package app.inknote.android

import android.content.Context
import android.net.Uri
import android.os.UserManager
import java.io.File

/**
 * Dove stanno i file delle note: foto e immagini da condividere (D38, D40).
 *
 * ## Due radici
 *
 * - **Area protetta dal dispositivo** (`dp`): disponibile anche prima del primo sblocco
 *   dopo un riavvio. Una foto scattata dal foglio sopra il blocco nasce qui, come il
 *   giornale (D24).
 * - **Area protetta dalle credenziali** (`ce`): cifrata con il codice dell'utente. È dove
 *   le foto devono vivere; ci arrivano quando la nota entra in archivio ([adopt]).
 *
 * Il modello conosce solo percorsi relativi, `photos/<id>.jpg`: quale radice lo
 * contenga è affare di questo file. Così lo spostamento non cambia nessun dato.
 */
object NoteFiles {

    const val PHOTOS_DIR = "photos"
    const val EXPORTS_DIR = "exports"
    const val AUTHORITY = "app.inknote.android.files"

    enum class Root(val segment: String) { DEVICE("dp"), CREDENTIAL("ce"), CACHE("cache") }

    fun rootDir(context: Context, root: Root): File = when (root) {
        Root.DEVICE -> (context.createDeviceProtectedStorageContext() ?: context).filesDir
        Root.CREDENTIAL -> context.filesDir
        Root.CACHE -> context.cacheDir
    }

    /** Il percorso relativo di una foto nuova. */
    fun newPhotoPath(photoId: String): String = "$PHOTOS_DIR/$photoId.jpg"

    /** Dove il foglio salva una foto appena scattata: sempre l'area del dispositivo. */
    fun captureFile(context: Context, relativePath: String): File =
        File(rootDir(context, Root.DEVICE), relativePath).also { it.parentFile?.mkdirs() }

    /**
     * Il file di una foto, ovunque sia. Prima l'area delle credenziali, poi quella del
     * dispositivo: una foto non ancora adottata sta ancora lì.
     */
    fun resolve(context: Context, relativePath: String): File? = locate(context, relativePath)?.second

    /** Come [resolve], dicendo anche in quale radice sta: serve per costruirne l'indirizzo. */
    fun locate(context: Context, relativePath: String): Pair<Root, File>? {
        if (!isSafe(relativePath)) return null
        for (root in listOf(Root.CREDENTIAL, Root.DEVICE)) {
            if (root == Root.CREDENTIAL && !isUnlocked(context)) continue
            val file = File(rootDir(context, root), relativePath)
            if (file.isFile && file.length() > 0) return root to file
        }
        return null
    }

    /**
     * Sposta le foto delle note appena entrate in archivio nell'area protetta dalle
     * credenziali. Da chiamare solo a telefono sbloccato, fuori dal thread
     * dell'interfaccia.
     */
    fun adopt(context: Context, relativePaths: List<String>) {
        if (!isUnlocked(context)) return
        for (path in relativePaths) {
            if (!isSafe(path)) continue
            val from = File(rootDir(context, Root.DEVICE), path)
            if (!from.isFile) continue
            val to = File(rootDir(context, Root.CREDENTIAL), path)
            to.parentFile?.mkdirs()
            // La rinomina fra due aree diverse può non riuscire: allora si copia, e il
            // vecchio si cancella solo dopo che il nuovo è intero.
            if (!from.renameTo(to)) {
                from.copyTo(to, overwrite = true)
                if (to.length() == from.length()) from.delete()
            }
        }
    }

    /** Cancella dal disco le foto di note eliminate davvero. */
    fun deleteAll(context: Context, relativePaths: List<String>) {
        for (path in relativePaths) {
            if (!isSafe(path)) continue
            for (root in listOf(Root.CREDENTIAL, Root.DEVICE)) File(rootDir(context, root), path).delete()
        }
    }

    /** L'indirizzo con cui un'altra app (fotocamera, destinazione di una condivisione) raggiunge un file. */
    fun uri(root: Root, relativePath: String): Uri =
        Uri.Builder().scheme("content").authority(AUTHORITY)
            .appendPath(root.segment)
            .appendEncodedPath(relativePath)
            .build()

    /**
     * Niente percorsi che escono dalla cartella: un `..` in un percorso che arriva da un
     * archivio o da un indirizzo sarebbe il modo di leggere file che non sono note.
     */
    fun isSafe(relativePath: String): Boolean =
        relativePath.isNotEmpty() &&
            !relativePath.startsWith("/") &&
            relativePath.split('/').none { it == ".." || it == "." || it.isEmpty() }

    fun isUnlocked(context: Context): Boolean =
        context.getSystemService(UserManager::class.java)?.isUserUnlocked ?: true
}
