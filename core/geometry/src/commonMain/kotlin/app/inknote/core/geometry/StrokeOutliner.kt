package app.inknote.core.geometry

import app.inknote.core.model.InkPoint
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Trasforma una polilinea con spessore variabile nel contorno chiuso che la
 * rappresenta.
 *
 * Il contorno è costruito percorrendo la linea su un lato, girando attorno alla
 * punta finale, tornando sull'altro lato e chiudendo con la punta iniziale. Le
 * punte sono arrotondate perché una penna lascia una traccia tonda, non tagliata.
 */
object StrokeOutliner {

    private const val EPSILON = 1e-4f
    private const val PI = 3.1415927f

    /**
     * @param points punti della linea, già ricampionati.
     * @param widths spessore in corrispondenza di ciascun punto; stessa lunghezza
     *   di [points].
     * @param capSegments segmenti con cui approssimare le punte arrotondate. Meno
     *   segmenti, meno punti da disegnare: nel widget conviene abbassarlo.
     */
    fun outline(points: List<InkPoint>, widths: FloatArray, capSegments: Int = 8): Outline {
        require(points.size == widths.size) {
            "un punto un raggio: ${points.size} punti e ${widths.size} spessori"
        }
        if (points.isEmpty()) return Outline.Empty

        val segments = capSegments.coerceAtLeast(2)
        if (points.size == 1 || isStationary(points)) {
            return dot(points.first(), widths.max() / 2f, segments)
        }

        val count = points.size
        val tangentsX = FloatArray(count)
        val tangentsY = FloatArray(count)
        var lastX = 1f
        var lastY = 0f

        for (i in 0 until count) {
            val previous = points[if (i > 0) i - 1 else 0]
            val next = points[if (i < count - 1) i + 1 else count - 1]
            var tx = next.x - previous.x
            var ty = next.y - previous.y
            val length = sqrt(tx * tx + ty * ty)
            if (length < EPSILON) {
                // Campioni coincidenti: non definiscono una direzione. Proseguire con
                // l'ultima valida evita un contorno attorcigliato nel punto morto.
                tx = lastX
                ty = lastY
            } else {
                tx /= length
                ty /= length
                lastX = tx
                lastY = ty
            }
            tangentsX[i] = tx
            tangentsY[i] = ty
        }

        // Un lato, le due punte e l'altro lato: la dimensione è nota in partenza.
        val result = FloatArray((count * 2 + (segments - 1) * 2) * 2)
        var cursor = 0

        // Lato sinistro, in avanti.
        for (i in 0 until count) {
            val radius = widths[i] / 2f
            result[cursor++] = points[i].x - tangentsY[i] * radius
            result[cursor++] = points[i].y + tangentsX[i] * radius
        }

        // Punta finale: dalla normale sinistra a quella destra, passando per la tangente.
        cursor = appendCap(
            result, cursor,
            center = points[count - 1],
            radius = widths[count - 1] / 2f,
            normalX = -tangentsY[count - 1], normalY = tangentsX[count - 1],
            tangentX = tangentsX[count - 1], tangentY = tangentsY[count - 1],
            segments = segments,
        )

        // Lato destro, all'indietro.
        for (i in count - 1 downTo 0) {
            val radius = widths[i] / 2f
            result[cursor++] = points[i].x + tangentsY[i] * radius
            result[cursor++] = points[i].y - tangentsX[i] * radius
        }

        // Punta iniziale: chiude il contorno.
        cursor = appendCap(
            result, cursor,
            center = points[0],
            radius = widths[0] / 2f,
            normalX = tangentsY[0], normalY = -tangentsX[0],
            tangentX = -tangentsX[0], tangentY = -tangentsY[0],
            segments = segments,
        )

        return Outline(if (cursor == result.size) result else result.copyOf(cursor))
    }

    /** Punto singolo: una penna appoggiata e sollevata lascia un tondo. */
    private fun dot(center: InkPoint, radius: Float, segments: Int): Outline {
        val steps = (segments * 2).coerceAtLeast(6)
        val result = FloatArray(steps * 2)
        for (i in 0 until steps) {
            val angle = 2f * PI * i / steps
            result[i * 2] = center.x + cos(angle) * radius
            result[i * 2 + 1] = center.y + sin(angle) * radius
        }
        return Outline(result)
    }

    /**
     * Mezzo giro attorno a [center], da `normal` a `-normal` passando per `tangent`.
     * Gli estremi non vengono ripetuti: ci sono già come ultimo punto del lato.
     */
    private fun appendCap(
        target: FloatArray,
        start: Int,
        center: InkPoint,
        radius: Float,
        normalX: Float,
        normalY: Float,
        tangentX: Float,
        tangentY: Float,
        segments: Int,
    ): Int {
        var cursor = start
        for (step in 1 until segments) {
            val angle = PI * step / segments
            val c = cos(angle)
            val s = sin(angle)
            target[cursor++] = center.x + (normalX * c + tangentX * s) * radius
            target[cursor++] = center.y + (normalY * c + tangentY * s) * radius
        }
        return cursor
    }

    private fun isStationary(points: List<InkPoint>): Boolean {
        val first = points.first()
        for (i in 1 until points.size) {
            val dx = points[i].x - first.x
            val dy = points[i].y - first.y
            if (dx * dx + dy * dy > EPSILON) return false
        }
        return true
    }
}
