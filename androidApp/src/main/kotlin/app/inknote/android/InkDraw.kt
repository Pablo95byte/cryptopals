package app.inknote.android

import android.graphics.Paint
import android.graphics.Path
import app.inknote.core.geometry.Outline
import app.inknote.core.geometry.RenderQuality
import app.inknote.core.geometry.StrokeGeometry
import app.inknote.core.model.Pen
import app.inknote.core.model.PenKind
import app.inknote.core.model.Stroke

/** Colori della direzione visiva (D16). */
object InkPalette {
    const val PAPER: Int = 0xFFFBF8F1.toInt()
    const val INK: Int = 0xFF1F2430.toInt()
    const val MUTED: Int = 0xFF8B8374.toInt()

    /** Il foglio di notte (D52): gli stessi valori di `sheet_paper` e `sheet_ink` in values-night. */
    const val NIGHT_PAPER: Int = 0xFF1B1A17.toInt()
    const val NIGHT_INK: Int = 0xFFECE5D6.toInt()
}

/**
 * Dal contorno calcolato dal core al disegno su `Canvas`.
 *
 * Il riempimento di un poligono è tutto ciò che questo lato deve fare: levigatura,
 * spessore e punte arrivano già risolti da `StrokeGeometry`, identici a quelli che
 * disegnerà iOS e disegneranno i widget (D6).
 */
object InkDraw {

    fun path(stroke: Stroke, scale: Float, quality: RenderQuality = RenderQuality.SCREEN): Path =
        StrokeGeometry.outline(stroke, quality).toPath(scale)

    fun Outline.toPath(scale: Float, offsetX: Float = 0f, offsetY: Float = 0f): Path {
        val path = Path()
        if (pointCount == 0) return path
        path.moveTo(x(0) * scale + offsetX, y(0) * scale + offsetY)
        for (index in 1 until pointCount) {
            path.lineTo(x(index) * scale + offsetX, y(index) * scale + offsetY)
        }
        path.close()
        return path
    }

    /**
     * @param night il foglio di notte (D52): l'inchiostro della casa diventa chiaro, gli altri
     *   colori restano i loro. Cambia solo il disegno, mai il tratto salvato (D7).
     */
    fun paint(pen: Pen, night: Boolean = false): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = if (night && pen.color == InkPalette.INK) InkPalette.NIGHT_INK else pen.color
        // L'evidenziatore è semitrasparente e va disegnato sotto: l'ordine lo decide
        // già `orderStrokes` nel core, qui serve solo l'alfa.
        if (pen.kind == PenKind.HIGHLIGHTER) alpha = 96
    }

}
