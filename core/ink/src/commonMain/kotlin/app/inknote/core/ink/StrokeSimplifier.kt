package app.inknote.core.ink

import app.inknote.core.model.InkPoint
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Riduce il numero di campioni salvati scartando quelli che non cambiano la forma
 * del tratto, con l'algoritmo di Ramer-Douglas-Peucker.
 *
 * Non è un'ottimizzazione prematura: una nota di una riga scritta a 120 Hz sono
 * qualche migliaio di campioni, e i widget hanno un tetto di memoria basso su
 * entrambe le piattaforme. Scartiamo i campioni ridondanti alla fonte, una volta,
 * invece di pagarli a ogni disegno.
 */
object StrokeSimplifier {

    fun simplify(points: List<InkPoint>, tolerance: Float): List<InkPoint> {
        if (points.size <= 2 || tolerance <= 0f) return points.toList()

        val keep = BooleanArray(points.size)
        keep[0] = true
        keep[points.size - 1] = true

        // Iterativo e non ricorsivo: su tratti lunghi la ricorsione può arrivare a
        // migliaia di livelli, e sullo stack di un thread UI non è una scommessa
        // da fare.
        val pending = ArrayDeque<IntArray>()
        pending.addLast(intArrayOf(0, points.size - 1))

        while (pending.isNotEmpty()) {
            val (first, last) = pending.removeLast()
            if (last <= first + 1) continue

            var farthest = -1
            var maxDistance = 0f
            for (i in first + 1 until last) {
                val distance = perpendicularDistance(points[i], points[first], points[last])
                if (distance > maxDistance) {
                    maxDistance = distance
                    farthest = i
                }
            }

            if (farthest > 0 && maxDistance > tolerance) {
                keep[farthest] = true
                pending.addLast(intArrayOf(first, farthest))
                pending.addLast(intArrayOf(farthest, last))
            }
        }

        return points.filterIndexed { index, _ -> keep[index] }
    }

    /** Distanza del punto dal segmento, non dalla retta: i segmenti sono finiti. */
    private fun perpendicularDistance(point: InkPoint, start: InkPoint, end: InkPoint): Float {
        val dx = end.x - start.x
        val dy = end.y - start.y
        val lengthSquared = dx * dx + dy * dy

        if (lengthSquared == 0f) {
            val px = point.x - start.x
            val py = point.y - start.y
            return sqrt(px * px + py * py)
        }

        var t = ((point.x - start.x) * dx + (point.y - start.y) * dy) / lengthSquared
        t = t.coerceIn(0f, 1f)
        val projX = start.x + t * dx
        val projY = start.y + t * dy
        val ex = point.x - projX
        val ey = point.y - projY
        return if (ex == 0f) abs(ey) else sqrt(ex * ex + ey * ey)
    }
}
