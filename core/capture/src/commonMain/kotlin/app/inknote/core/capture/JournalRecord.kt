package app.inknote.core.capture

import app.inknote.core.model.CanvasSize
import app.inknote.core.model.NoteId
import app.inknote.core.model.Pen
import app.inknote.core.model.PenKind
import app.inknote.core.model.Stroke
import app.inknote.core.model.StrokeId
import app.inknote.core.model.StrokePointCodec

/**
 * Un tratto e il contesto minimo per ricostruire la nota che lo contiene.
 *
 * Ogni record è **autosufficiente**: porta con sé l'id della nota, il suo istante di
 * creazione e la dimensione del foglio. Sono una ventina di byte ridondanti per
 * tratto, e servono perché un giornale deve essere recuperabile da solo, anche
 * quando la nota non è mai arrivata al database — che è proprio il caso per cui
 * esiste.
 */
data class JournalRecord(
    val noteId: NoteId,
    val noteCreatedAt: Long,
    val canvas: CanvasSize,
    val stroke: Stroke,
)

/**
 * Formato su disco del giornale.
 *
 * ## Come sopravvive a un processo ucciso a metà scrittura
 *
 * Ogni record è `versione | lunghezza | checksum | contenuto`. Chi legge si ferma al
 * primo record che non torna — header incompleto, lunghezza oltre i byte
 * disponibili, checksum sbagliato — e **tiene tutti quelli precedenti**. Un tratto
 * scritto a metà costa quel tratto, non il giornale.
 *
 * Senza lunghezza e checksum non si distingue "file finito" da "scrittura
 * interrotta", e l'unica alternativa sarebbe scartare tutto al primo dubbio.
 */
internal object JournalCodec {

    const val VERSION: Int = 1
    private const val HEADER_BYTES = 9 // versione (1) + lunghezza (4) + checksum (4)

    fun encode(record: JournalRecord): ByteArray {
        val payload = ByteWriter()
        payload.putString(record.noteId.value)
        payload.putLong(record.noteCreatedAt)
        payload.putFloat(record.canvas.width)
        payload.putFloat(record.canvas.height)
        payload.putString(record.stroke.id.value)
        payload.putInt(record.stroke.pen.color)
        payload.putString(record.stroke.pen.kind.name)
        payload.putFloat(record.stroke.pen.baseWidth)
        payload.putLong(record.stroke.createdAt)
        payload.putBytes(StrokePointCodec.encode(record.stroke.points))
        val body = payload.toByteArray()

        val out = ByteWriter()
        out.putByte(VERSION)
        out.putInt(body.size)
        out.putInt(checksum(body))
        out.putRaw(body)
        return out.toByteArray()
    }

    /**
     * Legge tutti i record leggibili, in ordine.
     *
     * ## Un record rotto non nasconde quelli dopo
     *
     * Il caso vero non è solo la coda troncata. Il processo muore a metà di un
     * tratto, e all'apertura successiva la nuova sessione **scrive in coda, dopo i
     * byte rotti**. Fermarsi al primo record illeggibile renderebbe invisibili tutti i
     * tratti scritti dopo — e l'assorbimento, svuotando il giornale, li cancellerebbe.
     * Quindi davanti a un record che non torna si avanza di un byte e si cerca il
     * prossimo record valido: lunghezza plausibile **e** checksum giusto sul contenuto,
     * che per caso capita una volta su quattro miliardi.
     *
     * ## Un record di una versione futura non si consuma
     *
     * Un record con la cornice integra ma una versione che non conosciamo non è
     * spazzatura: l'ha scritto un'app più nuova. La lettura si ferma lì e
     * [JournalReadResult.consumedBytes] non lo include, così nessuno svuotamento può
     * cancellarlo (D13: una versione sconosciuta è un errore esplicito, non una
     * lettura approssimativa).
     */
    fun decodeAll(bytes: ByteArray): JournalReadResult {
        val records = ArrayList<JournalRecord>()
        var offset = 0
        var discarded = 0

        while (offset < bytes.size) {
            when (val frame = frameAt(bytes, offset)) {
                is Frame.Record -> {
                    records += frame.record
                    offset += frame.size
                }
                is Frame.Future -> return JournalReadResult(records, discarded, consumedBytes = offset)
                Frame.Damaged -> {
                    offset++
                    discarded++
                }
            }
        }

        return JournalReadResult(records, discarded, consumedBytes = bytes.size)
    }

    private fun frameAt(bytes: ByteArray, offset: Int): Frame {
        if (bytes.size - offset < HEADER_BYTES) return Frame.Damaged

        val version = bytes[offset].toInt()
        val reader = ByteReader(bytes, offset + 1)
        val length = reader.int()
        val expectedChecksum = reader.int()
        if (length < 0 || length > bytes.size - offset - HEADER_BYTES) return Frame.Damaged

        val body = bytes.copyOfRange(offset + HEADER_BYTES, offset + HEADER_BYTES + length)
        if (checksum(body) != expectedChecksum) return Frame.Damaged

        if (version > VERSION) return Frame.Future
        if (version != VERSION) return Frame.Damaged

        val record = runCatching { decodeBody(body) }.getOrNull() ?: return Frame.Damaged
        return Frame.Record(record, size = HEADER_BYTES + length)
    }

    private sealed interface Frame {
        class Record(val record: JournalRecord, val size: Int) : Frame
        data object Future : Frame
        data object Damaged : Frame
    }

    private fun decodeBody(body: ByteArray): JournalRecord {
        val reader = ByteReader(body, 0)
        val noteId = reader.string()
        val noteCreatedAt = reader.long()
        val canvasWidth = reader.float()
        val canvasHeight = reader.float()
        val strokeId = reader.string()
        val penColor = reader.int()
        val penKindName = reader.string()
        val penBaseWidth = reader.float()
        val strokeCreatedAt = reader.long()
        val points = reader.bytes()

        return JournalRecord(
            noteId = NoteId(noteId),
            noteCreatedAt = noteCreatedAt,
            canvas = CanvasSize(canvasWidth, canvasHeight),
            stroke = Stroke(
                id = StrokeId(strokeId),
                pen = Pen(
                    color = penColor,
                    // Come nell'archivio: una punta sconosciuta viene da una versione
                    // più nuova dell'app, e il tratto va recuperato comunque.
                    kind = PenKind.entries.firstOrNull { it.name == penKindName } ?: PenKind.BALLPOINT,
                    baseWidth = penBaseWidth,
                ),
                points = StrokePointCodec.decode(points),
                createdAt = strokeCreatedAt,
            ),
        )
    }

    /** FNV-1a a 32 bit: serve a scoprire una scrittura interrotta, non a firmare nulla. */
    private fun checksum(bytes: ByteArray): Int {
        var hash = -0x7EE3623B // 2166136261
        for (byte in bytes) {
            hash = hash xor (byte.toInt() and 0xFF)
            hash *= 16777619
        }
        return hash
    }
}

/**
 * Esito della rilettura di un giornale.
 *
 * @param discardedBytes byte illeggibili saltati, in coda o in mezzo.
 * @param consumedBytes fin dove il giornale è stato letto per intero: è quanto si può
 *   togliere dalla testa dopo aver messo in archivio i [records]. I byte oltre — un
 *   record di una versione futura, o tratti arrivati dopo la lettura — non si toccano.
 */
data class JournalReadResult(
    val records: List<JournalRecord>,
    val discardedBytes: Int,
    val consumedBytes: Int,
) {
    /** `true` se una parte del giornale era illeggibile: un tratto si è perso. */
    val hadTornTail: Boolean get() = discardedBytes > 0
}

private class ByteWriter {
    private var buffer = ByteArray(128)
    private var size = 0

    fun putByte(value: Int) {
        ensure(1)
        buffer[size++] = value.toByte()
    }

    fun putInt(value: Int) {
        ensure(4)
        buffer[size++] = (value ushr 24).toByte()
        buffer[size++] = (value ushr 16).toByte()
        buffer[size++] = (value ushr 8).toByte()
        buffer[size++] = value.toByte()
    }

    fun putLong(value: Long) {
        putInt((value ushr 32).toInt())
        putInt(value.toInt())
    }

    fun putFloat(value: Float) = putInt(value.toRawBits())

    fun putBytes(value: ByteArray) {
        putInt(value.size)
        putRaw(value)
    }

    fun putString(value: String) = putBytes(value.encodeToByteArray())

    fun putRaw(value: ByteArray) {
        ensure(value.size)
        value.copyInto(buffer, size)
        size += value.size
    }

    fun toByteArray(): ByteArray = buffer.copyOf(size)

    private fun ensure(extra: Int) {
        if (size + extra <= buffer.size) return
        var capacity = buffer.size * 2
        while (capacity < size + extra) capacity *= 2
        buffer = buffer.copyOf(capacity)
    }
}

private class ByteReader(private val source: ByteArray, private var offset: Int) {

    fun int(): Int {
        require(offset + 4 <= source.size) { "record troncato" }
        val value = (source[offset].toInt() and 0xFF shl 24) or
            (source[offset + 1].toInt() and 0xFF shl 16) or
            (source[offset + 2].toInt() and 0xFF shl 8) or
            (source[offset + 3].toInt() and 0xFF)
        offset += 4
        return value
    }

    fun long(): Long = (int().toLong() and 0xFFFFFFFFL shl 32) or (int().toLong() and 0xFFFFFFFFL)

    fun float(): Float = Float.fromBits(int())

    fun bytes(): ByteArray {
        val length = int()
        require(length >= 0 && offset + length <= source.size) { "blocco troncato: $length byte" }
        val value = source.copyOfRange(offset, offset + length)
        offset += length
        return value
    }

    fun string(): String = bytes().decodeToString()
}
