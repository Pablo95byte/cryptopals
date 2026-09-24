package app.inknote.android

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import app.inknote.core.capture.CaptureMilestone
import app.inknote.core.capture.CaptureSession
import app.inknote.core.capture.FrictionTrace
import app.inknote.core.capture.FrictionVerdict
import app.inknote.core.capture.InkJournal
import app.inknote.core.capture.StartKind
import app.inknote.core.model.CanvasSize
import app.inknote.core.model.Clock
import app.inknote.core.model.PhotoClipId
import java.io.FileOutputStream

/**
 * Il foglio.
 *
 * Un'Activity di piattaforma, nessuna libreria, nessun database, nessuna iniezione di
 * dipendenze, nessuna animazione di apertura (D19, D20). È anche la prova di velocità:
 * nelle build di debug il misuratore dice quanto ci ha messo.
 *
 * La stessa Activity serve tutti gli ingressi: aperta dal launcher o dal widget si
 * sovrappone alla home, aperta dal riquadro delle impostazioni rapide a telefono
 * bloccato compare sopra il blocco, perché `showWhenLocked` sta nel manifest (D17).
 * Il foglio è cieco in tutti i casi: non mostra nessuna nota già scritta.
 *
 * **Un foglio vive finché è sullo schermo** (D34). Quando esce — tasto home, schermo
 * spento, una chiamata — la nota è chiusa, e l'Activity con lei.
 *
 * ## Il disegno (D46)
 *
 * Il foglio è tutto lo schermo, anche sotto le barre di sistema. I comandi galleggiano
 * in basso, dove arriva il pollice: a sinistra una pillola tenue con tastiera e
 * fotocamera, a destra "Fatto". Nient'altro, e niente in alto: in alto si scrive.
 */
class CaptureActivity : Activity() {

    private lateinit var trace: FrictionTrace
    private lateinit var session: CaptureSession
    private lateinit var journalSink: AndroidInkJournalSink
    private lateinit var inkView: InkCanvasView
    private lateinit var warning: TextView
    private lateinit var textCard: LinearLayout
    private lateinit var textField: EditText
    private lateinit var photoStrip: LinearLayout
    private lateinit var root: FrameLayout
    private var meter: TextView? = null
    private var camera: InlineCamera? = null

    /** Il dialogo del permesso copre il foglio senza abbandonarlo: non va chiuso. */
    private var awaitingPermission = false

    /** Il contatore è del processo: conta solo quello che è fallito da quando questo foglio è aperto. */
    private var failuresAtOpen = 0

    /** Uno solo: `removeCallbacks` toglie solo ciò che è stato messo in coda dallo stesso Handler. */
    private val mainHandler = Handler(Looper.getMainLooper())
    private val commitTextTask = Runnable { session.commitText(textField.text.toString()) }

    /**
     * Il carattere dei comandi del foglio è quello di sistema, non Instrument Sans:
     * leggere un font dai file costa millisecondi sul percorso che misuriamo (D19).
     */
    private val controlsFont: Typeface by lazy { Typeface.create("sans-serif-medium", Typeface.NORMAL) }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Due orologi, ognuno corretto per il suo scopo. Le durate si misurano su un
        // orologio monotono, che non salta se cambia l'ora di sistema; i timestamp
        // delle note sono ora di parete, perché devono avere senso fra dispositivi.
        val processStart = Process.getStartElapsedRealtime()
        val sinceProcessStart = SystemClock.elapsedRealtime() - processStart
        // A freddo il processo è nato per questo foglio; a caldo era già vivo (D32).
        // "Primo" vuol dire prima Activity del processo: se il processo l'ha avviato
        // l'archivio, il foglio lo trova già caldo.
        val firstInProcess = !ProcessState.anyActivityCreated
        ProcessState.anyActivityCreated = true
        val startKind = if (firstInProcess && sinceProcessStart in 0..MAX_COLD_START_MS) {
            StartKind.COLD
        } else {
            StartKind.WARM
        }

        trace = FrictionTrace(Clock { SystemClock.elapsedRealtime() }, startKind)
        // A caldo il momento del tocco non si vede da qui: si parte da `onCreate`, e il
        // numero non conta i millisecondi che il sistema spende prima (D32).
        trace.markAt(
            CaptureMilestone.INTENT,
            atMillis = if (startKind == StartKind.COLD) processStart else SystemClock.elapsedRealtime(),
        )

        super.onCreate(savedInstanceState)
        @Suppress("DEPRECATION") // la sostituta esiste solo da Android 14; questa funziona ovunque
        overridePendingTransition(0, 0)
        // Il foglio arriva sotto le barre di sistema: è carta fino al bordo. Barre con
        // icone scure, perché la carta è chiara anche di notte.
        Ui.edgeToEdge(this, lightBars = true)

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
                // L'evento porta l'istante dell'hardware sull'orologio `uptimeMillis`; la
                // misura sta su `elapsedRealtime`. Da svegli la differenza è costante.
                val offset = SystemClock.elapsedRealtime() - SystemClock.uptimeMillis()
                trace.markAt(CaptureMilestone.TOUCH, atMillis = eventUptime + offset)
            }
            onInkAccepted = {
                trace.mark(CaptureMilestone.INK_ACCEPTED)
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
        // sicuro: il salvataggio non dipende dalla conferma (D5, D20). Lo stesso per il
        // testo a metà.
        inkView.commitIfDrawing()
        commitTextNow()
        // La fotocamera si libera sempre quando il foglio non è in primo piano: la
        // tiene un'app sola alla volta.
        closeCamera()
        super.onPause()
    }

    override fun onStop() {
        super.onStop()
        // Il sistema può uccidere un processo in secondo piano quando vuole: le
        // scritture in coda vanno su disco adesso, non "fra poco".
        journalSink.awaitWrites()
        // Il foglio è uscito dallo schermo, e con lui la nota (D34). Ruotare il telefono
        // non arriva qui (il manifest dichiara i cambi di configurazione), e il dialogo
        // del permesso della fotocamera copre il foglio senza abbandonarlo.
        if (!isChangingConfigurations && !awaitingPermission) finish()
    }

    private fun buildLayout(): View {
        root = FrameLayout(this)
        root.addView(inkView, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        // I comandi stanno in uno strato sopra il foglio, spostato dentro le barre di
        // sistema. Lo strato non prende i tocchi: quelli fuori dai comandi arrivano al
        // foglio sotto.
        val overlay = FrameLayout(this)
        val gap = dp(12)
        overlay.setPadding(gap, gap, gap, dp(16))
        Ui.padForSystemBars(overlay, top = true, bottom = true)

        overlay.addView(buildTop(), FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP,
        ))
        overlay.addView(buildBottom(), FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM,
        ))
        root.addView(overlay, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        return root
    }

    /** In alto solo ciò che l'utente ha chiesto: il testo digitato, e l'avviso se il disco è pieno. */
    private fun buildTop(): View {
        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        // Invisibile finché va tutto bene, cioè quasi sempre: se il disco è pieno
        // l'utente deve saperlo adesso e non quando cercherà la nota (D5).
        warning = TextView(this).apply {
            text = getString(R.string.journal_failed)
            setTextColor(Color.WHITE)
            typeface = controlsFont
            textSize = 14f
            setPadding(dp(16), dp(12), dp(16), dp(12))
            background = Ui.rounded(getColor(R.color.danger), dp(16).toFloat())
            visibility = View.GONE
        }
        column.addView(warning, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = dp(8)
        })

        // Il testo digitato (D38): una scheda in alto, sotto la barra di stato, che non
        // copre niente e non è coperta da niente.
        textField = EditText(this).apply {
            setHint(R.string.text_hint)
            textSize = 18f
            setTextColor(getColor(R.color.on_paper))
            setHintTextColor(getColor(R.color.on_paper_muted))
            background = null
            setPadding(dp(4), dp(4), dp(4), dp(4))
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            minLines = 2
            maxLines = 7
            isVerticalScrollBarEnabled = true
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                override fun afterTextChanged(s: Editable?) {
                    // Nel giornale dopo una breve pausa, non a ogni tasto: ogni versione
                    // del testo è un pezzo nuovo, e a ogni tasto sarebbero centinaia.
                    mainHandler.removeCallbacks(commitTextTask)
                    mainHandler.postDelayed(commitTextTask, TEXT_COMMIT_DELAY_MS)
                }
            })
        }
        val hide = Ui.iconButton(this, R.drawable.ic_close, getString(R.string.cancel), getColor(R.color.on_paper_muted)) {
            closeKeyboard()
        }
        textCard = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.TOP
            setPadding(dp(14), dp(10), dp(4), dp(10))
            Ui.card(this, context)
            visibility = View.GONE
            addView(textField, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(hide)
        }
        column.addView(textCard, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        return column
    }

    /** In basso, dove arriva il pollice: le miniature delle foto, poi i comandi. */
    private fun buildBottom(): View {
        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        if (isDebuggable()) column.addView(debugMeter(), LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT,
        ).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            bottomMargin = dp(8)
        })

        photoStrip = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        column.addView(HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(photoStrip)
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = dp(10)
        })

        // Tastiera e fotocamera, tenui (D38): non sono decisioni da prendere, chi vuole
        // scrivere a mano scrive e basta.
        // Direttamente sulla carta: niente contenitore e niente ombra. Una pillola con
        // l'ombra dietro faceva sembrare le icone un adesivo appiccicato sul foglio (D46).
        val muted = getColor(R.color.on_paper_muted)
        val tools = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(Ui.iconButton(this@CaptureActivity, R.drawable.ic_keyboard, getString(R.string.keyboard), muted) { openKeyboard() })
            addView(Ui.iconButton(this@CaptureActivity, R.drawable.ic_camera, getString(R.string.camera), muted) { takePhoto() })
        }

        // L'uscita (D5, D21). Non salva: è già tutto nel giornale.
        val done = Ui.pill(this, getString(R.string.done), R.drawable.ic_check, primary = true, font = controlsFont) {
            commitTextNow()
            finish()
        }.apply {
            // Colori fissi: il foglio è carta anche di notte, e il pulsante resta inchiostro.
            background = Ui.pressable(context, Ui.rounded(getColor(R.color.on_paper), dp(28).toFloat()), dp(28).toFloat())
            elevation = 0f
            setTextColor(getColor(R.color.paper))
            compoundDrawablesRelative[0]?.setTint(getColor(R.color.paper))
        }

        column.addView(Ui.row(this, tools, Ui.spacer(this), done))
        return column
    }

    /**
     * Il misuratore esiste **solo** nelle build di debug: all'apertura il foglio deve
     * essere nudo (D21). Sta in basso, piccolo, e un tocco lo nasconde; tenerlo premuto
     * apre il giornale.
     */
    private fun debugMeter(): View {
        val view = TextView(this).apply {
            textSize = 11f
            typeface = controlsFont
            setTextColor(getColor(R.color.on_paper_muted))
            text = "attrito: in misura…"
            setPadding(dp(12), dp(6), dp(12), dp(6))
            background = Ui.rounded(0xE6FFFDF8.toInt(), dp(14).toFloat())
            setOnClickListener { visibility = View.GONE }
            setOnLongClickListener {
                startActivity(Intent(this@CaptureActivity, RecoveredNotesActivity::class.java))
                true
            }
        }
        meter = view
        return view
    }

    private fun showMeasurement() {
        val meter = meter ?: return
        meter.text = trace.report()
        meter.setTextColor(
            // Rosso se una delle due sfora: il foglio lento ad aprirsi o il tratto che
            // resta indietro rispetto al dito (D36).
            if (trace.verdict() == FrictionVerdict.OVER_BUDGET || trace.touchVerdict() == FrictionVerdict.OVER_BUDGET) {
                getColor(R.color.danger)
            } else {
                Color.parseColor("#2F6B3A")
            },
        )
    }

    // --- Testo (D38) ---

    /** Il testo digitato va nel giornale adesso, non fra un momento (D5, D38). */
    private fun commitTextNow() {
        if (!::textField.isInitialized) return
        mainHandler.removeCallbacks(commitTextTask)
        session.commitText(textField.text.toString())
    }

    private fun openKeyboard() {
        textCard.visibility = View.VISIBLE
        textField.requestFocus()
        getSystemService(InputMethodManager::class.java)?.showSoftInput(textField, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun closeKeyboard() {
        getSystemService(InputMethodManager::class.java)?.hideSoftInputFromWindow(textField.windowToken, 0)
        textField.clearFocus()
        commitTextNow()
        // La scheda resta se c'è del testo: è parte della nota, e deve vedersi.
        if (textField.text.isBlank()) textCard.visibility = View.GONE
    }

    // --- Foto (D38, D45) ---

    private fun takePhoto() {
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            awaitingPermission = true
            requestPermissions(arrayOf(Manifest.permission.CAMERA), REQUEST_CAMERA)
            return
        }
        openCamera()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQUEST_CAMERA) return
        awaitingPermission = false
        if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            openCamera()
        } else {
            Toast.makeText(this, R.string.camera_denied, Toast.LENGTH_LONG).show()
        }
    }

    private fun openCamera() {
        if (camera != null) return
        getSystemService(InputMethodManager::class.java)?.hideSoftInputFromWindow(root.windowToken, 0)
        val inline = InlineCamera(
            activity = this,
            onPhoto = { bytes -> onPhotoTaken(bytes) },
            onClose = { closeCamera() },
            onFailure = {
                closeCamera()
                Toast.makeText(this, R.string.no_camera, Toast.LENGTH_SHORT).show()
            },
        )
        camera = inline
        root.addView(inline.view, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        inline.start()
    }

    private fun closeCamera() {
        val inline = camera ?: return
        camera = null
        inline.stop()
        root.removeView(inline.view)
    }

    /**
     * La foto entra nella nota subito, e il file si scrive su un altro thread (invariante
     * 20). Se il processo morisse fra le due cose, l'archivio troverebbe una foto senza
     * file e semplicemente non la mostrerebbe.
     */
    private fun onPhotoTaken(bytes: ByteArray) {
        closeCamera()
        val id = PhotoClipId.random()
        val path = NoteFiles.newPhotoPath(id.value)
        session.addPhoto(path, id)

        val size = dp(64)
        val radius = dp(Ui.RADIUS_THUMB.toInt()).toFloat()
        val thumbnail = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = Ui.rounded(getColor(R.color.card), radius)
            Ui.clipRounded(this, radius)
        }
        photoStrip.addView(thumbnail, LinearLayout.LayoutParams(size, size).apply { rightMargin = dp(8) })

        val app = applicationContext
        Thread({
            val written = runCatching {
                FileOutputStream(NoteFiles.captureFile(app, path)).use { out ->
                    out.write(bytes)
                    out.flush()
                    out.fd.sync()
                }
            }.isSuccess
            val bitmap = if (written) NoteFiles.resolve(app, path)?.let { NoteRenderer.decodeThumbnail(it, size) } else null
            runOnUiThread {
                if (bitmap != null) {
                    thumbnail.setImageBitmap(bitmap)
                } else {
                    photoStrip.removeView(thumbnail)
                    Toast.makeText(app, R.string.journal_failed, Toast.LENGTH_LONG).show()
                }
            }
        }, "inknote-foto").start()
    }

    // --- Varie ---

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
        const val REQUEST_CAMERA = 1
        const val TEXT_COMMIT_DELAY_MS = 1_200L

        /**
         * Soglia che distingue un'apertura a freddo da una a caldo: se dall'avvio del
         * processo è passato meno di questo, il processo è nato per questa apertura (D32).
         */
        const val MAX_COLD_START_MS = 10_000L

        /** Il tempo di una scrittura su disco lenta, con margine. */
        const val WARNING_CHECK_DELAY_MS = 300L
    }
}
