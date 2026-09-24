package app.inknote.android

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.view.MotionEvent
import android.view.View
import app.inknote.android.InkDraw.toPath
import app.inknote.core.capture.CaptureSession
import app.inknote.core.geometry.RenderQuality
import app.inknote.core.geometry.StrokeGeometry
import app.inknote.core.model.InkPoint
import app.inknote.core.model.Pen
import app.inknote.core.model.PenKind
import app.inknote.core.model.Stroke
import app.inknote.core.model.StrokeId

/**
 * La superficie di scrittura.
 *
 * Una `View` normale, non Compose, e senza niente da iniettare: deve poter accettare
 * il primo tocco al primo fotogramma (D20). Tutto quello che fa è convertire i tocchi
 * in campioni e riempire i contorni che il core calcola.
 */
class InkCanvasView(context: Context) : View(context) {

    /** La punta in uso. Nella prova di velocità è una sola. */
    var pen: Pen = Pen(color = InkPalette.INK, kind = PenKind.BALLPOINT, baseWidth = 3.2f)

    /**
     * Scattano una volta sola: sono le tappe della misura (D19, D32).
     *
     * Vengono invocate **in modo sincrono**, nell'istante esatto della tappa. Chi le
     * riceve deve limitarsi a registrare il tempo: aggiornare l'interfaccia da qui
     * significa toccare il layout durante un disegno.
     *
     * [onTouch] porta l'istante del tocco secondo l'hardware (`MotionEvent.eventTime`,
     * orologio `uptimeMillis`), non quello in cui l'evento ci arriva: la latenza del
     * tratto si misura da lì (D36).
     */
    var onFirstFrame: (() -> Unit)? = null
    var onTouch: ((eventUptimeMillis: Long) -> Unit)? = null
    var onInkAccepted: (() -> Unit)? = null
    var onInkDrawn: (() -> Unit)? = null

    /** Scatta a ogni tratto chiuso, dopo che è stato consegnato al giornale. */
    var onStrokeCommitted: (() -> Unit)? = null

    private var session: CaptureSession? = null

    private val committed = ArrayList<PaintedStroke>()
    private val liveSamples = ArrayList<InkPoint>()
    private var liveStartedAtEventTime = 0L
    private var reportedFirstFrame = false
    private var reportedTouch = false

    /**
     * Il dito che sta scrivendo, per id e non per posizione nell'evento.
     *
     * L'indice 0 di un `MotionEvent` non è "il primo dito": quando il primo si alza,
     * l'indice 0 passa al dito che resta. Leggere sempre l'indice 0 faceva saltare il
     * tratto da un dito all'altro, disegnando una retta fra i due — trovato sul primo
     * telefono vero, appoggiando due dita.
     */
    private var activePointerId = MotionEvent.INVALID_POINTER_ID
    private var reportedInkAccepted = false
    private var reportedInkDrawn = false

    private val rule = InkDraw.rulePaint()

    /**
     * Il pennello del tratto in corso, creato una volta sola: `onDraw` gira a ogni
     * fotogramma mentre si scrive, e allocare lì fa lavorare il garbage collector
     * proprio mentre il dito si muove.
     */
    private var livePaint: Paint = InkDraw.paint(pen)
    private var livePaintPen: Pen = pen

    /** Unità logiche per pixel: i tratti sono salvati in dp, non in pixel (D10). */
    private val scale: Float get() = resources.displayMetrics.density

    fun attach(session: CaptureSession) {
        this.session = session
        committed.clear()
        for (stroke in session.note().strokes) committed += paint(stroke)
        invalidate()
    }

    /** Chiude il tratto in corso, se ce n'è uno. Da chiamare quando l'app perde il fuoco. */
    fun commitIfDrawing() {
        val session = session ?: return
        if (session.isDrawing) commit(session)
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(InkPalette.PAPER)
        drawRules(canvas)

        for (painted in committed) canvas.drawPath(painted.path, painted.paint)

        if (liveSamples.isNotEmpty() && !reportedInkDrawn) {
            reportedInkDrawn = true
            onInkDrawn?.invoke()
        }

        if (liveSamples.isNotEmpty()) {
            val live = Stroke(
                id = LIVE_STROKE_ID,
                pen = pen,
                points = ArrayList(liveSamples),
                createdAt = 0L,
            )
            if (livePaintPen != pen) {
                livePaint = InkDraw.paint(pen)
                livePaintPen = pen
            }
            canvas.drawPath(StrokeGeometry.outline(live, RenderQuality.SCREEN).toPath(scale), livePaint)
        }

        if (!reportedFirstFrame) {
            reportedFirstFrame = true
            // Sincrona: marca la tappa nell'istante giusto. Chi la riceve non deve
            // toccare il layout, perché siamo dentro un disegno.
            onFirstFrame?.invoke()
        }
    }

    private fun drawRules(canvas: Canvas) {
        val spacing = 36f * scale
        var y = spacing * 2.6f
        while (y < height) {
            canvas.drawLine(0f, y, width.toFloat(), y, rule)
            y += spacing
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val session = session ?: return false

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (!session.beginStroke(pen)) return true
                activePointerId = event.getPointerId(0)
                if (!reportedTouch) {
                    reportedTouch = true
                    onTouch?.invoke(event.eventTime)
                }
                liveSamples.clear()
                liveStartedAtEventTime = event.eventTime
                addSample(session, event, 0, event.getX(0), event.getY(0), event.getPressure(0), event.eventTime)
                invalidate()
                return true
            }

            // Un secondo dito mentre si scrive è il palmo, o una mano che regge il
            // telefono: non apre un tratto e non sposta quello in corso.
            MotionEvent.ACTION_POINTER_DOWN -> return true

            MotionEvent.ACTION_MOVE -> {
                val index = event.findPointerIndex(activePointerId)
                if (index < 0) return true
                // I campioni storici sono quelli che il digitizer ha raccolto fra due
                // consegne: ignorarli vuol dire buttare via metà della risoluzione del
                // tratto su uno schermo a 120 Hz.
                for (position in 0 until event.historySize) {
                    addSample(
                        session = session,
                        event = event,
                        pointerIndex = index,
                        x = event.getHistoricalX(index, position),
                        y = event.getHistoricalY(index, position),
                        pressure = event.getHistoricalPressure(index, position),
                        eventTime = event.getHistoricalEventTime(position),
                    )
                }
                addSample(session, event, index, event.getX(index), event.getY(index), event.getPressure(index), event.eventTime)
                invalidate()
                return true
            }

            MotionEvent.ACTION_POINTER_UP -> {
                // Si è alzato il dito che scriveva mentre un altro resta sul vetro: il
                // tratto finisce qui. Continuarlo con l'altro dito è la retta di prima.
                if (event.getPointerId(event.actionIndex) == activePointerId) {
                    activePointerId = MotionEvent.INVALID_POINTER_ID
                    commit(session)
                }
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                activePointerId = MotionEvent.INVALID_POINTER_ID
                commit(session)
                return true
            }
        }

        return super.onTouchEvent(event)
    }

    private fun addSample(
        session: CaptureSession,
        event: MotionEvent,
        pointerIndex: Int,
        x: Float,
        y: Float,
        pressure: Float,
        eventTime: Long,
    ) {
        // La pressione si legge solo dal pennino. Sul dito Android riporta 1.0 fisso,
        // che non è pressione: dichiararla assente fa calcolare lo spessore dalla
        // velocità, ed è la differenza fra una nota scritta a mano e un tubo di
        // spessore costante (invariante 6).
        val effectivePressure = if (event.getToolType(pointerIndex) == MotionEvent.TOOL_TYPE_STYLUS) {
            pressure
        } else {
            InkPoint.NO_PRESSURE
        }
        val sample = InkPoint(
            x = x / scale,
            y = y / scale,
            pressure = effectivePressure,
            tMs = (eventTime - liveStartedAtEventTime).toInt(),
        )

        // La sessione filtra il rumore del digitizer: si disegna quello che verrà
        // salvato, non quello che è arrivato.
        if (!session.addSample(x = sample.x, y = sample.y, pressure = sample.pressure, tMs = sample.tMs)) {
            return
        }
        liveSamples += sample

        if (!reportedInkAccepted) {
            reportedInkAccepted = true
            // Sincrona di proposito: la richiamata marca solo la tappa, e rimandarla al
            // giro successivo del loop aggiungerebbe millisecondi alla misura falsandola.
            onInkAccepted?.invoke()
        }
    }

    private fun commit(session: CaptureSession) {
        // Quando questa chiamata ritorna il tratto è consegnato al giornale, che lo
        // porta su disco entro pochi millisecondi su un altro thread: nessuna
        // conferma serve (D20, D35).
        val stroke = session.endStroke()
        liveSamples.clear()
        if (stroke != null) committed += paint(stroke)
        invalidate()
        if (stroke != null) onStrokeCommitted?.invoke()
    }

    private fun paint(stroke: Stroke) = PaintedStroke(
        path = InkDraw.path(stroke, scale),
        paint = InkDraw.paint(stroke.pen),
    )

    private class PaintedStroke(val path: Path, val paint: Paint)

    private companion object {
        val LIVE_STROKE_ID = StrokeId("in-corso")
    }
}
