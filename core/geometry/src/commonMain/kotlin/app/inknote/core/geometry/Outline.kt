package app.inknote.core.geometry

/**
 * Contorno chiuso di un tratto, pronto da riempire.
 *
 * ## Perché un contorno e non una polilinea con spessore
 *
 * Un tratto a spessore variabile non si può disegnare come linea: le API di
 * disegno accettano *uno* spessore per path. Lo trasformiamo quindi in un poligono
 * chiuso che il renderer nativo riempie — su iOS con Core Graphics, su Android con
 * Canvas, nei widget con le stesse API. Un solo calcolo condiviso, quattro
 * renderer che si limitano a riempire.
 *
 * @param points coppie `x, y` consecutive. È un [FloatArray] e non una lista di
 *   oggetti perché questo array attraversa il confine verso Swift e verso il
 *   canvas di Android a ogni frame: una lista di punti significherebbe migliaia di
 *   allocazioni per fotogramma.
 */
class Outline(val points: FloatArray) {

    val pointCount: Int get() = points.size / 2

    val isEmpty: Boolean get() = points.isEmpty()

    fun x(index: Int): Float = points[index * 2]

    fun y(index: Int): Float = points[index * 2 + 1]

    companion object {
        val Empty = Outline(FloatArray(0))
    }
}
