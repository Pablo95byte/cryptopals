package app.inknote.core.model

import kotlin.math.sqrt

/** Distanza euclidea fra due campioni, nello spazio logico del canvas. */
fun InkPoint.distanceTo(other: InkPoint): Float {
    val dx = other.x - x
    val dy = other.y - y
    return sqrt(dx * dx + dy * dy)
}

/** Lunghezza della spezzata che passa per tutti i campioni. */
fun List<InkPoint>.pathLength(): Float {
    var total = 0f
    for (i in 1 until size) total += this[i - 1].distanceTo(this[i])
    return total
}
