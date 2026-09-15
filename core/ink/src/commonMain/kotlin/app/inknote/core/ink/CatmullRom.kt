package app.inknote.core.ink

import app.inknote.core.model.InkPoint
import app.inknote.core.model.distanceTo
import kotlin.math.ceil
import kotlin.math.pow

/**
 * Trasforma i campioni salvati in una curva levigata, ricampionata a passo
 * costante.
 *
 * Usiamo una spline di Catmull-Rom in parametrizzazione **centripeta** (alpha 0.5)
 * e non uniforme: quella uniforme, sui campioni molto ravvicinati che arrivano da
 * un digitizer, produce cuspidi e cappi: sullo schermo si vedono come piccoli
 * uncini sulle curve strette della scrittura corsiva.
 *
 * La curva passa per tutti i campioni originali, quindi il tratto resta fedele a
 * quello che l'utente ha scritto.
 */
object CatmullRom {

    private const val ALPHA = 0.5f

    /**
     * @param spacing distanza fra i punti in uscita, in unità di canvas. Più
     *   piccolo è, più liscio è il contorno e più punti deve disegnare il renderer
     *   nativo: nel widget si usa un valore più grande.
     */
    fun resample(points: List<InkPoint>, spacing: Float): List<InkPoint> {
        require(spacing > 0f) { "spacing deve essere positivo: $spacing" }
        if (points.size < 2) return points.toList()

        val out = ArrayList<InkPoint>((points.pathLengthOrZero() / spacing).toInt().coerceIn(points.size, 8192))
        out += points.first()

        for (i in 0 until points.size - 1) {
            val p1 = points[i]
            val p2 = points[i + 1]
            // Agli estremi non esiste un punto di controllo: lo specchiamo, così la
            // curva entra e esce con la stessa direzione della spezzata.
            val p0 = if (i > 0) points[i - 1] else mirror(p1, p2)
            val p3 = if (i + 2 < points.size) points[i + 2] else mirror(p2, p1)

            val chord = p1.distanceTo(p2)
            if (chord == 0f) continue

            val steps = ceil(chord / spacing).toInt().coerceAtLeast(1)
            for (step in 1..steps) {
                val u = step.toFloat() / steps
                out += interpolate(p0, p1, p2, p3, u)
            }
        }

        return out
    }

    private fun mirror(from: InkPoint, toward: InkPoint): InkPoint = from.copy(
        x = from.x * 2f - toward.x,
        y = from.y * 2f - toward.y,
    )

    /**
     * Valuta la spline fra [p1] e [p2] con lo schema piramidale di Barry-Goldman,
     * che è la forma stabile della Catmull-Rom non uniforme.
     *
     * @param u posizione normalizzata 0..1 nel segmento.
     */
    private fun interpolate(p0: InkPoint, p1: InkPoint, p2: InkPoint, p3: InkPoint, u: Float): InkPoint {
        val t0 = 0f
        val t1 = t0 + knot(p0, p1)
        val t2 = t1 + knot(p1, p2)
        val t3 = t2 + knot(p2, p3)

        // Campioni duplicati o coincidenti annullerebbero un denominatore: in quel
        // caso la spline non è definita e l'interpolazione lineare è la risposta
        // giusta, non un ripiego.
        if (t1 <= t0 || t2 <= t1 || t3 <= t2) return lerp(p1, p2, u)

        val t = t1 + u * (t2 - t1)

        val a1x = lerpValue(p0.x, p1.x, t0, t1, t)
        val a1y = lerpValue(p0.y, p1.y, t0, t1, t)
        val a2x = lerpValue(p1.x, p2.x, t1, t2, t)
        val a2y = lerpValue(p1.y, p2.y, t1, t2, t)
        val a3x = lerpValue(p2.x, p3.x, t2, t3, t)
        val a3y = lerpValue(p2.y, p3.y, t2, t3, t)

        val b1x = lerpValue(a1x, a2x, t0, t2, t)
        val b1y = lerpValue(a1y, a2y, t0, t2, t)
        val b2x = lerpValue(a2x, a3x, t1, t3, t)
        val b2y = lerpValue(a2y, a3y, t1, t3, t)

        return InkPoint(
            x = lerpValue(b1x, b2x, t1, t2, t),
            y = lerpValue(b1y, b2y, t1, t2, t),
            // Pressione e tempo non seguono la curva: sono grandezze del campione,
            // e interpolarle linearmente fra i due estremi è sia corretto sia stabile.
            pressure = if (p1.hasPressure && p2.hasPressure) {
                p1.pressure + (p2.pressure - p1.pressure) * u
            } else {
                InkPoint.NO_PRESSURE
            },
            tMs = (p1.tMs + (p2.tMs - p1.tMs) * u).toInt(),
        )
    }

    private fun knot(a: InkPoint, b: InkPoint): Float = a.distanceTo(b).pow(ALPHA)

    private fun lerp(a: InkPoint, b: InkPoint, u: Float) = InkPoint(
        x = a.x + (b.x - a.x) * u,
        y = a.y + (b.y - a.y) * u,
        pressure = if (a.hasPressure && b.hasPressure) a.pressure + (b.pressure - a.pressure) * u else InkPoint.NO_PRESSURE,
        tMs = (a.tMs + (b.tMs - a.tMs) * u).toInt(),
    )

    private fun lerpValue(from: Float, to: Float, fromT: Float, toT: Float, at: Float): Float {
        val span = toT - fromT
        if (span == 0f) return from
        val w = (at - fromT) / span
        return from + (to - from) * w
    }

    private fun List<InkPoint>.pathLengthOrZero(): Float {
        var total = 0f
        for (i in 1 until size) total += this[i - 1].distanceTo(this[i])
        return total
    }
}
