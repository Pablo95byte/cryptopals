package app.inknote.core.ink

import app.inknote.core.model.InkPoint
import app.inknote.core.model.Pen
import app.inknote.core.model.Stroke
import app.inknote.core.model.StrokeId
import app.inknote.core.model.distanceTo

/**
 * Accumula i campioni di una passata di penna, dal tocco al rilascio, e produce
 * uno [Stroke].
 *
 * Quello che salviamo sono i campioni d'ingresso ripuliti, non la curva già
 * levigata. La curva si ricalcola al disegno ([StrokeGeometry]): così la stessa
 * nota si può rendere alla qualità che serve — piena sullo schermo, ridotta nel
 * widget — e possiamo migliorare il motore d'inchiostro in una versione futura
 * senza che le note vecchie restino brutte.
 */
class StrokeBuilder(
    private val pen: Pen,
    private val startedAt: Long,
    private val config: InkConfig = InkConfig.Default,
    private val id: StrokeId = StrokeId.random(),
) {
    private val samples = ArrayList<InkPoint>(64)

    /**
     * Ultimo campione scartato perché troppo vicino al precedente. Va tenuto da
     * parte: se l'utente stacca il dito subito dopo, quello è il punto finale
     * effettivo del tratto e perderlo accorcia il tratto in modo visibile.
     */
    private var deferred: InkPoint? = null

    val pointCount: Int get() = samples.size + if (deferred != null) 1 else 0

    val isEmpty: Boolean get() = pointCount == 0

    /**
     * Registra un campione. Ritorna `true` se è stato accettato nella spezzata,
     * `false` se è stato trattenuto come rumore.
     */
    fun add(x: Float, y: Float, pressure: Float = InkPoint.NO_PRESSURE, tMs: Int = 0): Boolean {
        val point = InkPoint(x = x, y = y, pressure = pressure, tMs = tMs)
        val last = samples.lastOrNull()
        if (last == null) {
            samples += point
            return true
        }

        val movedEnough = last.distanceTo(point) >= config.minPointDistance
        val dwelled = tMs - last.tMs >= config.maxDwellIntervalMs
        return if (movedEnough || dwelled) {
            samples += point
            deferred = null
            true
        } else {
            deferred = point
            false
        }
    }

    /** Chiude il tratto. Ritorna `null` se non è stato registrato alcun campione. */
    fun build(): Stroke? {
        if (isEmpty) return null
        deferred?.let {
            samples += it
            deferred = null
        }
        return Stroke(
            id = id,
            pen = pen,
            points = StrokeSimplifier.simplify(samples, config.simplifyTolerance),
            createdAt = startedAt,
        )
    }
}
