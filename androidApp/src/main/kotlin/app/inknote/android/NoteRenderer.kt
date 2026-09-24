package app.inknote.android

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.view.View
import app.inknote.android.InkDraw.toPath
import app.inknote.core.geometry.Bounds
import app.inknote.core.geometry.NoteFraming
import app.inknote.core.geometry.RenderQuality
import app.inknote.core.geometry.StrokeGeometry
import app.inknote.core.model.Note
import java.io.File
import java.io.FileOutputStream

/**
 * Disegna l'inchiostro di una nota dentro un riquadro, inquadrando l'inchiostro e non il
 * foglio (`NoteFraming`, D29 per la parte che vale ancora).
 *
 * Tutta la calligrafia arriva dal core: qui si riempiono contorni, come nel foglio (D6).
 */
object NoteRenderer {

    /**
     * @param density pixel per unità logica: quella dello schermo per le viste, un valore
     *   scelto da noi per l'immagine da condividere.
     */
    fun drawInk(canvas: Canvas, note: Note, widthPx: Int, heightPx: Int, density: Float) {
        val box = Bounds(0f, 0f, widthPx / density, heightPx / density)
        val frame = NoteFraming.fit(note, box) ?: return
        val pixelScale = frame.scale * density
        val offsetX = (widthPx - frame.source.width * pixelScale) / 2f - frame.source.minX * pixelScale
        val offsetY = (heightPx - frame.source.height * pixelScale) / 2f - frame.source.minY * pixelScale
        for (outlined in StrokeGeometry.outlines(note, frame.quality)) {
            canvas.drawPath(outlined.outline.toPath(pixelScale, offsetX, offsetY), InkDraw.paint(outlined.pen))
        }
    }

    /**
     * L'immagine dell'inchiostro da allegare a una condivisione (D31): l'originale, che
     * non sbaglia parole come il riconoscimento.
     *
     * Tre pixel per unità logica, e mai oltre 2048 per lato: abbastanza nitida per uno
     * schermo grande, abbastanza leggera per un messaggio.
     */
    fun exportPng(note: Note, file: File): Boolean {
        val ink = Bounds.of(note)?.inflate(EXPORT_MARGIN) ?: return false
        val density = minOf(EXPORT_DENSITY, MAX_EXPORT_SIDE / maxOf(ink.width, ink.height))
        val width = (ink.width * density).toInt().coerceAtLeast(1)
        val height = (ink.height * density).toInt().coerceAtLeast(1)

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(InkPalette.PAPER)
        val offsetX = -ink.minX * density
        val offsetY = -ink.minY * density
        for (outlined in StrokeGeometry.outlines(note, RenderQuality.SCREEN)) {
            canvas.drawPath(outlined.outline.toPath(density, offsetX, offsetY), InkDraw.paint(outlined.pen))
        }

        file.parentFile?.mkdirs()
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        return true
    }

    /**
     * Una foto ridotta per un riquadro di [targetPx]: una foto da 12 megapixel letta intera
     * sono 48 MB di memoria per una miniatura.
     */
    fun decodeThumbnail(file: File, targetPx: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (minOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= targetPx) sample *= 2
        return BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    private const val EXPORT_DENSITY = 3f
    private const val MAX_EXPORT_SIDE = 2048f
    private const val EXPORT_MARGIN = 12f
}

/** L'inchiostro di una nota, inquadrato nel suo riquadro: per l'elenco e per la nota aperta. */
class InkPreviewView(context: Context) : View(context) {

    var note: Note? = null
        set(value) {
            field = value
            invalidate()
        }

    override fun onDraw(canvas: Canvas) {
        val note = note ?: return
        NoteRenderer.drawInk(canvas, note, width, height, resources.displayMetrics.density)
    }
}
