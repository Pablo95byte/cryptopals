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
 * La stessa Activity serve tutti gli ingressi: aperta dal launcher o dal widget si
 * sovrappone alla home, aperta dal riquadro delle impostazioni rapide a telefono
 * bloccato compare sopra il blocco, perché `showWhenLocked` sta nel manifest (D17).
 * Il foglio è cieco in tutti i casi: non mostra nessuna nota già scritta.
 *
 * **Un foglio vive finché è sullo schermo** (D34). Quando esce — tasto home, schermo
 * spento, una chiamata — la nota è chiusa, e l'Activity con lei. Altrimenti il foglio
 * con la nota di prima riapparirebbe sopra il blocco alla prima accensione, leggibile
 * da chiunque, e il tocco successivo sul widget non troverebbe un foglio bianco.
 */
class CaptureActivity : Activity() {

    private lateinit var trace: FrictionTrace
    private lateinit var session: CaptureSession
    private lateinit var journalSink: AndroidInkJournalSink
    private lateinit var inkView: InkCanvasView
    private lateinit var warning: TextView
    private var meter: TextView? = null

    /** Il contatore è del processo: conta solo quello che è fallito da quando questo foglio è aperto. */
    private var failuresAtOpen = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        // Due orologi, ognuno corretto per il suo scopo. Le durate si misurano su un
        // orologio monotono, che non salta se cambia l'ora di sistema; i timestamp
        // delle note sono ora di parete, perché devono avere senso fra dispositivi.
        val processStart = Process.getStartElapsedRealtime()
        val sinceProcessStart = SystemClock.elapsedRealtime() - processStart
        // A freddo il processo è nato per questo foglio; a caldo era già vivo. Sono due
        // fenomeni fisici diversi e hanno due tetti diversi (D32).
        //
        // Il tempo dall'avvio del processo da solo non basta: un secondo foglio aperto
        // pochi secondi dopo il primo trova il processo giovane, e verrebbe misurato
        // dall'avvio del processo di prima — migliaia di millisecondi, in rosso, falsi.
        // È freddo solo il **primo** foglio del processo, e solo se il processo è nato
        // da poco: un processo avviato ore prima dal widget non lo è.
        val firstInProcess = !sheetCreatedInThisProcess
        sheetCreatedInThisProcess = true
        val startKind = if (firstInProcess && sinceProcessStart in 0..MAX_COLD_START_MS) {
            StartKind.COLD
        } else {
            StartKind.WARM
        }

        trace = FrictionTrace(Clock { SystemClock.elapsedRealtime() }, startKind)
        // A caldo il momento del tocco non si vede da qui: si parte da `onCreate`, e
        // il numero non conta i millisecondi che il sistema spende prima di chiamarci.
        // È ottimista, e lo dice la guida: la misura esterna è `am start -W`.
        trace.markAt(
            CaptureMilestone.INTENT,
            atMillis = if (startKind == StartKind.COLD) processStart else SystemClock.elapsedRealtime(),
        )

        super.onCreate(savedInstanceState)
        // Per sicurezza, oltre al tema: nessuna transizione da aspettare.
        overridePendingTransition(0, 0)

        journalSink = AndroidInkJournalSink.open(this)
        failuresAtOpen = AndroidInkJournalSink.failures.get()
        session = CaptureSession(
            canvas = screenCanvasSize(),
            journal = InkJournal(journalSink),
            clock = Clock { System.currentTimeMillis() },
        )

        inkView = InkCanvasView(this).apply {
            onFirstFrame = { trace.mark(CaptureMilestone.FIRST_FRAME) }
            onTouch = { eventUptime ->
                // L'evento porta l'istante dell'hardware sull'orologio `uptimeMillis`;
                // la misura sta su `elapsedRealtime`. Da svegli la differenza fra i due
                // è costante, quindi basta spostarlo.
                val offset = SystemClock.elapsedRealtime() - SystemClock.uptimeMillis()
                trace.markAt(CaptureMilestone.TOUCH, atMillis = eventUptime + offset)
            }
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
            // La scrittura sul giornale avviene su un altro thread: l'eventuale errore
            // si guarda poco dopo, non nell'istante del sollevamento del dito.
            onStrokeCommitted = { postDelayed({ showJournalWarningIfNeeded() }, WARNING_CHECK_DELAY_MS) }
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

    override fun onStop() {
        super.onStop()
        // Il sistema può uccidere un processo in secondo piano quando vuole: le
        // scritture in coda vanno su disco adesso, non "fra poco".
        journalSink.awaitWrites()
        // Il foglio è uscito dallo schermo, e con lui la nota (D34). Il manifest
        // dichiara i cambi di configurazione, quindi ruotare il telefono non arriva
        // qui e non spezza la nota in due.
        if (!isChangingConfigurations) finish()
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

        // Invisibile finché va tutto bene, cioè quasi sempre: non è un comando e non
        // chiede niente, ma se il disco è pieno l'utente deve saperlo adesso e non
        // quando cercherà la nota (D5).
        warning = TextView(this).apply {
            text = getString(R.string.journal_failed)
            setTextColor(Color.parseColor("#B4402F"))
            textSize = 13f
            visibility = View.GONE
        }
        root.addView(
            warning,
            // In alto e a tutta larghezza: in basso finirebbe sotto il pulsante OK sugli
            // schermi stretti, e sotto il misuratore nelle build di debug.
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                leftMargin = dp(20)
                rightMargin = dp(20)
                topMargin = dp(80)
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
            // Rosso se una delle due sfora: il foglio lento ad aprirsi o il tratto che
            // resta indietro rispetto al dito (D36).
            if (trace.verdict() == FrictionVerdict.OVER_BUDGET ||
                trace.touchVerdict() == FrictionVerdict.OVER_BUDGET
            ) {
                Color.parseColor("#B4402F")
            } else {
                Color.parseColor("#2F6B3A")
            },
        )
    }

    private fun showJournalWarningIfNeeded() {
        val failed = session.journalFailures > 0 || AndroidInkJournalSink.failures.get() > failuresAtOpen
        if (failed) warning.visibility = View.VISIBLE
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

        /** Se in questo processo si è già aperto un foglio: il secondo non è mai freddo. */
        var sheetCreatedInThisProcess = false

        /**
         * Soglia che distingue un'apertura a freddo da una a caldo.
         *
         * Se dall'avvio del processo è passato meno di questo, il processo è nato per
         * questa apertura: è freddo, e il tetto è quello più largo. Oltre, il processo
         * era già vivo per altri motivi e il tetto è quello severo (D32).
         */
        const val MAX_COLD_START_MS = 10_000L

        /** Il tempo di una scrittura su disco lenta, con margine. */
        const val WARNING_CHECK_DELAY_MS = 300L
    }
}
