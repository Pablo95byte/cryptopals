package app.inknote.android

import android.app.Activity
import android.content.pm.ApplicationInfo
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Process
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import app.inknote.core.capture.CaptureMilestone
import app.inknote.core.capture.CaptureSession
import app.inknote.core.capture.FrictionTrace
import app.inknote.core.capture.FrictionVerdict
import app.inknote.core.capture.StartKind
import app.inknote.core.capture.InkJournal
import app.inknote.core.model.CanvasSize
import app.inknote.core.model.Clock

/**
 * Il foglio.
 *
 * Questa è la **prova di velocità** di D19: un'Activity di piattaforma, nessuna
 * libreria, nessun database, nessuna iniezione di dipendenze, nessuna animazione di
 * apertura. Serve a misurare il pavimento dell'attrito su un telefono vero, e allo
 * stesso tempo è la base su cui crescerà la cattura definitiva — non codice da
 * buttare.
 *
 * La stessa Activity serve i due ingressi previsti: aperta dal launcher o dal widget
 * si sovrappone alla home, aperta a telefono bloccato compare sopra il blocco, perché
 * `showWhenLocked` sta nel manifest (D17). Il foglio è cieco in entrambi i casi:
 * non mostra nessuna nota già scritta.
 */
class CaptureActivity : Activity() {

    private lateinit var trace: FrictionTrace
    private lateinit var session: CaptureSession
    private lateinit var inkView: InkCanvasView
    private var meter: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        // Due orologi, ognuno corretto per il suo scopo. Le durate si misurano su un
        // orologio monotono, che non salta se cambia l'ora di sistema; i timestamp
        // delle note sono ora di parete, perché devono avere senso fra dispositivi.
        val processStart = Process.getStartElapsedRealtime()
        val sinceProcessStart = SystemClock.elapsedRealtime() - processStart
        // A freddo il processo è appena nato; a caldo era già vivo. Sono due fenomeni
        // fisici diversi e hanno due tetti diversi (D32).
        val startKind = if (sinceProcessStart in 0..MAX_COLD_START_MS) StartKind.COLD else StartKind.WARM

        trace = FrictionTrace(Clock { SystemClock.elapsedRealtime() }, startKind)
        trace.markAt(
            CaptureMilestone.INTENT,
            atMillis = if (startKind == StartKind.COLD) processStart else SystemClock.elapsedRealtime(),
        )

        super.onCreate(savedInstanceState)
        // Per sicurezza, oltre al tema: nessuna transizione da aspettare.
        overridePendingTransition(0, 0)

        val journal = InkJournal(AndroidInkJournalSink.open(this))
        session = CaptureSession(
            canvas = screenCanvasSize(),
            journal = journal,
            clock = Clock { System.currentTimeMillis() },
        )

        inkView = InkCanvasView(this).apply {
            onFirstFrame = { trace.mark(CaptureMilestone.FIRST_FRAME) }
            onInkAccepted = {
                trace.mark(CaptureMilestone.INK_ACCEPTED)
                // L'aggiornamento dell'interfaccia sì, al giro successivo: la tappa è
                // già stata marcata, e qui siamo dentro la gestione di un tocco.
                post { showMeasurement() }
            }
            onInkDrawn = {
                trace.mark(CaptureMilestone.INK_DRAWN)
                post { showMeasurement() }
            }
            attach(session)
        }

        setContentView(buildLayout())
        trace.mark(CaptureMilestone.SURFACE_READY)
    }

    override fun onPause() {
        // Se si esce col dito ancora sul vetro, quel tratto va chiuso e messo al
        // sicuro: il salvataggio non dipende dalla conferma (D5, D20).
        inkView.commitIfDrawing()
        super.onPause()
    }

    private fun buildLayout(): View {
        val root = FrameLayout(this)
        root.addView(
            inkView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )

        // L'unico comando visibile all'apertura (D21).
        root.addView(
            Button(this).apply {
                text = getString(R.string.ok)
                setOnClickListener { finish() }
            },
            FrameLayout.LayoutParams(dp(96), dp(56)).apply {
                gravity = Gravity.BOTTOM or Gravity.END
                rightMargin = dp(20)
                bottomMargin = dp(28)
            },
        )

        if (isDebuggable()) root.addView(debugOverlay(), debugOverlayParams())

        return root
    }

    /**
     * Il misuratore e la scorciatoia all'elenco esistono **solo** nelle build di
     * debug: all'apertura il foglio deve essere nudo (D21). Qui servono a leggere il
     * numero di D19 sul telefono senza collegare strumenti.
     */
    private fun debugOverlay(): View {
        val view = TextView(this).apply {
            setTextColor(InkPalette.MUTED)
            textSize = 11f
            text = "attrito: in misura…"
            setOnClickListener {
                startActivity(Intent(this@CaptureActivity, RecoveredNotesActivity::class.java))
            }
        }
        meter = view
        return view
    }

    private fun debugOverlayParams() = FrameLayout.LayoutParams(
        FrameLayout.LayoutParams.WRAP_CONTENT,
        FrameLayout.LayoutParams.WRAP_CONTENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        leftMargin = dp(20)
        topMargin = dp(52)
    }

    private fun showMeasurement() {
        val meter = meter ?: return
        meter.text = trace.report()
        meter.setTextColor(
            when (trace.verdict()) {
                FrictionVerdict.WITHIN_BUDGET -> Color.parseColor("#2F6B3A")
                else -> Color.parseColor("#B4402F")
            },
        )
    }

    /** Il foglio è grande quanto lo schermo, in unità logiche (D10). */
    private fun screenCanvasSize(): CanvasSize {
        val metrics = resources.displayMetrics
        return CanvasSize(
            width = metrics.widthPixels / metrics.density,
            height = metrics.heightPixels / metrics.density,
        )
    }

    private fun isDebuggable(): Boolean =
        (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private companion object {
        /**
         * Soglia che distingue un'apertura a freddo da una a caldo.
         *
         * Se dall'avvio del processo è passato meno di questo, il processo è nato per
         * questa apertura: è freddo, e il tetto è quello più largo. Oltre, il processo
         * era già vivo per altri motivi e il tetto è quello severo (D32).
         */
        const val MAX_COLD_START_MS = 10_000L
    }
}
