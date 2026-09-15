package app.inknote.core.model

/**
 * Formato binario dei campioni di un tratto.
 *
 * ## Perché un formato nostro e non JSON
 *
 * Una riga di scrittura sono centinaia di campioni, e una nota ne ha molte:
 * in JSON diventano decine di kilobyte di testo per nota, che vanno riletti e
 * riparsati ogni volta che il widget si aggiorna. Qui un campione occupa
 * [BYTES_PER_POINT] byte fissi e la decodifica è una scansione lineare.
 *
 * ## Il byte di versione
 *
 * Il primo byte è la versione del formato. Non è un lusso: è ciò che permette di
 * aggiungere un campo al campione (inclinazione del pennino, per esempio) in una
 * versione futura dell'app **continuando a leggere le note già salvate**. Un
 * formato senza versione obbliga a indovinare, e su dati che sono le note degli
 * utenti non si indovina.
 */
object StrokePointCodec {

    /** Versione scritta dalle nuove codifiche. Le vecchie restano leggibili. */
    const val VERSION: Int = 1

    /** `x`, `y` e `pressure` come float a 32 bit, `tMs` come interi a 32 bit. */
    const val BYTES_PER_POINT: Int = 16

    private const val HEADER_BYTES = 1

    fun encode(points: List<InkPoint>): ByteArray {
        val bytes = ByteArray(HEADER_BYTES + points.size * BYTES_PER_POINT)
        bytes[0] = VERSION.toByte()
        var offset = HEADER_BYTES
        for (point in points) {
            offset = writeInt(bytes, offset, point.x.toRawBits())
            offset = writeInt(bytes, offset, point.y.toRawBits())
            offset = writeInt(bytes, offset, point.pressure.toRawBits())
            offset = writeInt(bytes, offset, point.tMs)
        }
        return bytes
    }

    /**
     * @throws IllegalArgumentException se i byte non sono un tratto leggibile. È
     *   volutamente un errore e non una lista vuota: un archivio corrotto che si
     *   legge come "nota senza tratti" cancellerebbe silenziosamente la nota
     *   dell'utente al primo salvataggio successivo.
     */
    fun decode(bytes: ByteArray): List<InkPoint> {
        if (bytes.isEmpty()) return emptyList()

        val version = bytes[0].toInt()
        require(version == VERSION) {
            "versione del formato non riconosciuta: $version (questa build legge fino a $VERSION)"
        }

        val payload = bytes.size - HEADER_BYTES
        require(payload % BYTES_PER_POINT == 0) {
            "tratto troncato: $payload byte non sono un multiplo di $BYTES_PER_POINT"
        }

        val count = payload / BYTES_PER_POINT
        val points = ArrayList<InkPoint>(count)
        var offset = HEADER_BYTES
        repeat(count) {
            val x = Float.fromBits(readInt(bytes, offset))
            val y = Float.fromBits(readInt(bytes, offset + 4))
            val pressure = Float.fromBits(readInt(bytes, offset + 8))
            val tMs = readInt(bytes, offset + 12)
            offset += BYTES_PER_POINT
            points += InkPoint(x = x, y = y, pressure = pressure, tMs = tMs)
        }
        return points
    }

    private fun writeInt(target: ByteArray, offset: Int, value: Int): Int {
        target[offset] = (value ushr 24).toByte()
        target[offset + 1] = (value ushr 16).toByte()
        target[offset + 2] = (value ushr 8).toByte()
        target[offset + 3] = value.toByte()
        return offset + 4
    }

    private fun readInt(source: ByteArray, offset: Int): Int =
        (source[offset].toInt() and 0xFF shl 24) or
            (source[offset + 1].toInt() and 0xFF shl 16) or
            (source[offset + 2].toInt() and 0xFF shl 8) or
            (source[offset + 3].toInt() and 0xFF)
}
