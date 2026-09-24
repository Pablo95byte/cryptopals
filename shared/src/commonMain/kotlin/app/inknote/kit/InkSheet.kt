package app.inknote.kit

import app.inknote.core.capture.CaptureSession
import app.inknote.core.capture.InkJournal
import app.inknote.core.capture.InkJournalSink
import app.inknote.core.geometry.Outline
import app.inknote.core.geometry.RenderQuality
import app.inknote.core.geometry.StrokeGeometry
import app.inknote.core.model.CanvasSize
import app.inknote.core.model.Clock
import app.inknote.core.model.InkPoint
import app.inknote.core.model.NoteId
import app.inknote.core.model.Pen
import app.inknote.core.model.PenKind
import app.inknote.core.model.Stroke
import app.inknote.core.model.StrokeId

/**
 * Il foglio visto da Swift (D48).
 *
 * È la stessa cattura di Android — `CaptureSession`, giornale, geometria — dietro
 * chiamate che un `UIView` sa fare senza conoscere il core: tocco, movimento,
 * sollevamento, e contorni da riempire. Così la calligrafia su iPhone è quella di
 * Android per costruzione (D6), e questa parte si prova qui, senza un Mac.
 *
 * Le coordinate sono punti di iOS, che valgono come i dp di Android: unità logiche del
 * foglio (D10).
 *
 * Tutto va chiamato dallo stesso thread, quello dell'interfaccia. Il giornale scrive su
 * disco da sé, su un thread suo (D35): è compito del sink che si passa.
 */
class InkSheet(
    canvasWidth: Float,
    canvasHeight: Float,
    journalSink: InkJournalSink,
    private val clock: Clock,
) {
    private val session = CaptureSession(
        canvas = CanvasSize(canvasWidth, canvasHeight),
        journal = InkJournal(journalSink),
        clock = clock,
    )

    private val shapes = ArrayList<InkShape>()
    private val live = ArrayList<InkPoint>()
    private var livePen: Pen = FINGER_PEN
    private var liveStartMs = 0L

    val noteId: String get() = session.noteId.value

    /** `true` se il foglio non ha niente: né tratti, né testo, né foto. */
    val isEmpty: Boolean get() = session.isEmpty

    /** Se è maggiore di zero, il disco non accetta scritture: il foglio deve dirlo (D5). */
    val journalFailures: Int get() = session.journalFailures

    /** I tratti chiusi, pronti da riempire, nell'ordine in cui disegnarli. */
    val committedShapes: List<InkShape> get() = shapes

    /**
     * Comincia un tratto.
     *
     * @param pencil `true` per l'Apple Pencil: punta sottile, e lo spessore segue la
     *   pressione. Col dito la punta è un pennarello e lo spessore segue la velocità (D42).
     * @param timestampMs l'istante del tocco secondo il sistema, in millisecondi.
     * @return `false` se un tratto è già aperto: un secondo dito è il palmo.
     */
    fun begin(pencil: Boolean, timestampMs: Long): Boolean {
        val pen = if (pencil) PENCIL_PEN else FINGER_PEN
        if (!session.beginStroke(pen)) return false
        livePen = pen
        live.clear()
        liveStartMs = timestampMs
        return true
    }

    /**
     * Un campione del tratto aperto.
     *
     * @param force pressione fra 0 e 1, oppure un valore negativo se non c'è: il dito su
     *   iPhone non ha pressione vera, e inventarla è ciò che l'invariante 6 vieta.
     * @return `true` se il campione è entrato nel tratto, `false` se era rumore.
     */
    fun add(x: Float, y: Float, force: Float, timestampMs: Long): Boolean {
        val pressure = if (force < 0f) InkPoint.NO_PRESSURE else force.coerceIn(0f, 1f)
        val tMs = (timestampMs - liveStartMs).toInt()
        if (!session.addSample(x = x, y = y, pressure = pressure, tMs = tMs)) return false
        live += InkPoint(x = x, y = y, pressure = pressure, tMs = tMs)
        return true
    }

    /**
     * Chiude il tratto e lo consegna al giornale.
     *
     * @return `true` se c'era un tratto da chiudere: allora [committedShapes] ne ha uno in più.
     */
    fun end(): Boolean {
        live.clear()
        val stroke = session.endStroke() ?: return false
        shapes += InkShape.of(stroke)
        return true
    }

    /** Il tratto in corso da disegnare adesso, o `null` se non ce n'è uno. */
    fun liveShape(): InkShape? {
        if (live.isEmpty()) return null
        return InkShape.of(Stroke(id = LIVE_ID, pen = livePen, points = ArrayList(live), createdAt = 0L))
    }

    /** Il testo digitato, così com'è adesso (D38). */
    fun commitText(text: String) {
        session.commitText(text)
    }

    /** Una foto già salvata su disco, col suo percorso relativo (D38). */
    fun addPhoto(relativePath: String) {
        session.addPhoto(relativePath)
    }

    /**
     * Una registrazione finita, già su disco (D63).
     *
     * @param relativePath percorso relativo del file audio.
     * @param durationMs quanto è durata: l'istante della registrazione è il suo inizio.
     */
    fun addVoice(relativePath: String, durationMs: Int) {
        session.addVoice(path = relativePath, durationMs = durationMs)
    }

    companion object {
        /** L'inchiostro della direzione visiva (D16), come su Android. */
        const val INK: Int = 0xFF1F2430.toInt()

        private val FINGER_PEN = Pen(color = INK, kind = PenKind.BALLPOINT, baseWidth = 4.8f)
        private val PENCIL_PEN = Pen(color = INK, kind = PenKind.BALLPOINT, baseWidth = 3.2f)
        private val LIVE_ID = StrokeId("in-corso")

        /** Un id di nota nuovo, per chi deve nominare una foto prima di scattarla. */
        fun newId(): String = NoteId.random().value
    }
}

/**
 * Un contorno da riempire: un poligono chiuso, già levigato e con le punte.
 *
 * Swift lo legge punto per punto con [x] e [y]: un `FloatArray` di Kotlin da Swift è un
 * oggetto, non un array, e copiarlo costerebbe più che leggerlo.
 */
class InkShape private constructor(
    private val outline: Outline,
    val color: Int,
    val alpha: Float,
    private val scale: Float,
    private val offsetX: Float,
    private val offsetY: Float,
) {

    val pointCount: Int get() = outline.pointCount

    fun x(index: Int): Float = outline.x(index) * scale + offsetX

    fun y(index: Int): Float = outline.y(index) * scale + offsetY

    companion object {
        internal fun of(
            stroke: Stroke,
            quality: RenderQuality = RenderQuality.SCREEN,
            scale: Float = 1f,
            offsetX: Float = 0f,
            offsetY: Float = 0f,
        ): InkShape = InkShape(
            outline = StrokeGeometry.outline(stroke, quality),
            color = stroke.pen.color,
            // L'evidenziatore è semitrasparente, come su Android.
            alpha = if (stroke.pen.kind == PenKind.HIGHLIGHTER) 96f / 255f else 1f,
            scale = scale,
            offsetX = offsetX,
            offsetY = offsetY,
        )
    }
}
