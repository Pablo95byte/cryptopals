package app.inknote.core.geometry

import app.inknote.core.model.Note
import app.inknote.core.model.Stroke

/**
 * Rettangolo che contiene dell'inchiostro.
 *
 * Serve al widget: una nota di tre parole scritta in alto a sinistra, mostrata
 * senza ritaglio in un widget piccolo, diventa illeggibile. Il widget inquadra
 * questo rettangolo, non tutto il foglio.
 */
data class Bounds(
    val minX: Float,
    val minY: Float,
    val maxX: Float,
    val maxY: Float,
) {
    val width: Float get() = maxX - minX
    val height: Float get() = maxY - minY

    /** Allarga il rettangolo di [margin] su ogni lato. */
    fun inflate(margin: Float): Bounds = Bounds(minX - margin, minY - margin, maxX + margin, maxY + margin)

    companion object {

        /** Rettangolo dell'inchiostro visibile della nota, `null` se non c'è inchiostro. */
        fun of(note: Note): Bounds? = ofStrokes(note.visibleStrokes)

        /** Rettangolo di un singolo tratto, `null` se privo di campioni. */
        fun of(stroke: Stroke): Bounds? = ofStrokes(listOf(stroke))

        /**
         * Tiene conto dello spessore della penna e non solo della posizione dei
         * campioni: la linea ha un corpo, e un ritaglio sulla sola posizione
         * taglierebbe via metà del tratto di bordo.
         */
        fun ofStrokes(strokes: List<Stroke>): Bounds? {
            var minX = Float.MAX_VALUE
            var minY = Float.MAX_VALUE
            var maxX = -Float.MAX_VALUE
            var maxY = -Float.MAX_VALUE
            var found = false

            for (stroke in strokes) {
                val halfWidth = stroke.pen.baseWidth / 2f
                for (point in stroke.points) {
                    found = true
                    if (point.x - halfWidth < minX) minX = point.x - halfWidth
                    if (point.y - halfWidth < minY) minY = point.y - halfWidth
                    if (point.x + halfWidth > maxX) maxX = point.x + halfWidth
                    if (point.y + halfWidth > maxY) maxY = point.y + halfWidth
                }
            }

            return if (found) Bounds(minX, minY, maxX, maxY) else null
        }
    }
}
