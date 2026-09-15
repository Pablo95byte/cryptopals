package app.inknote.core.ink

import app.inknote.core.model.InkPoint
import app.inknote.core.model.Pen
import app.inknote.core.model.PenKind

internal val BIRO = Pen(color = 0xFF101010.toInt(), kind = PenKind.BALLPOINT, baseWidth = 4f)

/** Retta orizzontale di [count] campioni distanti [step], con pressione opzionale. */
internal fun line(
    count: Int,
    step: Float = 2f,
    msPerSample: Int = 8,
    pressure: (Int) -> Float = { InkPoint.NO_PRESSURE },
): List<InkPoint> = List(count) { i ->
    InkPoint(x = i * step, y = 0f, pressure = pressure(i), tMs = i * msPerSample)
}
