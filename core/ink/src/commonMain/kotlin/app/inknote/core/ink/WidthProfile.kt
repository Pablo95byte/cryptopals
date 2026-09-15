package app.inknote.core.ink

import app.inknote.core.model.InkPoint
import app.inknote.core.model.Pen
import app.inknote.core.model.PenKind
import app.inknote.core.model.distanceTo
import kotlin.math.pow

/**
 * Calcola lo spessore del tratto punto per punto.
 *
 * È la parte che fa sembrare l'inchiostro inchiostro. Due sorgenti, in ordine:
 *
 * 1. **la pressione**, quando il dispositivo la riporta (Apple Pencil, S Pen,
 *    schermi con force touch);
 * 2. **la velocità**, quando non c'è. Scrivendo col dito la pressione non arriva,
 *    e senza questo ripiego il tratto sarebbe un tubo di spessore costante: è la
 *    differenza fra "nota scritta a mano" e "scarabocchio fatto con l'app".
 *    Più veloce è il gesto, più sottile il tratto, come una penna vera che tocca
 *    meno carta.
 */
object WidthProfile {

    /**
     * @param points campioni già ricampionati da [CatmullRom]: lo spessore va
     *   calcolato sugli stessi punti che verranno disegnati.
     * @return spessori in unità di canvas, uno per punto.
     */
    fun compute(points: List<InkPoint>, pen: Pen, config: InkConfig = InkConfig.Default): FloatArray {
        if (points.isEmpty()) return FloatArray(0)

        // L'evidenziatore imita un pennarello a punta piatta: spessore costante, e
        // la trasparenza la applica il renderer.
        if (pen.kind == PenKind.HIGHLIGHTER) return FloatArray(points.size) { pen.baseWidth }

        val dynamicRange = when (pen.kind) {
            PenKind.MARKER -> 0.5f // il pennarello reagisce meno: più carico d'inchiostro
            else -> 1f
        }

        val factors = if (points.any { it.hasPressure }) {
            pressureFactors(points, config)
        } else {
            velocityFactors(points, config)
        }

        smoothInPlace(factors)
        taperInPlace(factors, config.taperSamples, config.minWidthFactor)

        return FloatArray(points.size) { i ->
            val compressed = 1f - (1f - factors[i]) * dynamicRange
            pen.baseWidth * compressed.coerceIn(config.minWidthFactor, 1f)
        }
    }

    private fun pressureFactors(points: List<InkPoint>, config: InkConfig): FloatArray =
        FloatArray(points.size) { i ->
            val point = points[i]
            // Un campione senza pressione in mezzo a campioni che ce l'hanno è un
            // buco del digitizer: meglio la pressione piena che un buco nel tratto.
            val pressure = if (point.hasPressure) point.pressure.coerceIn(0f, 1f) else 1f
            config.minWidthFactor + (1f - config.minWidthFactor) * pressure.pow(config.pressureGamma)
        }

    private fun velocityFactors(points: List<InkPoint>, config: InkConfig): FloatArray {
        val factors = FloatArray(points.size)
        for (i in points.indices) {
            val speed = when {
                points.size < 2 -> 0f
                i == 0 -> speedBetween(points[0], points[1])
                else -> speedBetween(points[i - 1], points[i])
            }
            val normalized = (speed / config.velocityReference).coerceIn(0f, 1f)
            factors[i] = 1f - config.velocityStrength * normalized
        }
        return factors
    }

    private fun speedBetween(from: InkPoint, to: InkPoint): Float {
        // I campioni ricampionati condividono spesso lo stesso millisecondo: un
        // delta di zero darebbe velocità infinita, quindi il minimo è 1 ms.
        val dt = (to.tMs - from.tMs).coerceAtLeast(1)
        return from.distanceTo(to) / dt
    }

    /**
     * Media mobile a tre campioni. Senza questo passaggio il rumore del digitizer
     * si vede come una variazione nervosa dello spessore lungo il tratto.
     */
    private fun smoothInPlace(factors: FloatArray) {
        if (factors.size < 3) return
        val source = factors.copyOf()
        for (i in 1 until factors.size - 1) {
            factors[i] = (source[i - 1] + source[i] + source[i + 1]) / 3f
        }
    }

    /**
     * Affila l'inizio e la fine del tratto. Una penna vera non appoggia a pieno
     * spessore: senza affilatura ogni tratto sembra tagliato di netto.
     */
    private fun taperInPlace(factors: FloatArray, samples: Int, minFactor: Float) {
        if (samples <= 0 || factors.isEmpty()) return
        val span = minOf(samples, factors.size / 2).coerceAtLeast(1)
        for (i in 0 until span) {
            val ramp = minFactor + (1f - minFactor) * ((i + 1).toFloat() / (span + 1))
            factors[i] *= ramp
            factors[factors.size - 1 - i] *= ramp
        }
    }
}
