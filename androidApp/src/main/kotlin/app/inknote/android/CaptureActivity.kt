package app.inknote.android

import android.app.Activity
import android.app.KeyguardManager
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import android.provider.MediaStore
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
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
import app.inknote.core.model.NoteId
import app.inknote.core.model.PhotoClip
import app.inknote.core.model.PhotoClipId

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
    private lateinit var textField: EditText
    private lateinit var photoStrip: LinearLayout

    /**
     * La foto in attesa della fotocamera. Finché è qui il foglio **non** si chiude quando
     * esce dallo schermo: a coprirlo è la fotocamera che abbiamo aperto noi (D34, D38).
     */
    private var pendingPhoto: PhotoClip? = null
    private val commitTextTask = Runnable { session.commitText(textField.text.toString()) }

    /** Uno solo: `removeCallbacks` toglie solo ciò che è stato messo in coda dallo stesso Handler. */
    private val mainHandler = Handler(Looper.getMainLooper())

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
        //
        // "Primo" vuol dire prima Activity del processo, non primo foglio: se il processo
        // l'ha avviato l'archivio, il foglio lo trova già caldo.
        val firstInProcess = !ProcessState.anyActivityCreated
        ProcessState.anyActivityCreated = true
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
        @Suppress("DEPRECATION") // la sostituta esiste solo da Android 14; questa funziona ovunque
        overridePendingTransition(0, 0)

        journalSink = AndroidInkJournalSink.open(this)
        failuresAtOpen = AndroidInkJournalSink.failures.get()
        val wallClock = Clock { System.currentTimeMillis() }
        // Se il sistema ha ucciso il processo mentre la fotocamera era aperta, il foglio
        // rinasce qui: con lo stesso id, i pezzi nuovi finiscono nella stessa nota di
        // quelli già nel giornale, invece di spezzarla in due.
        val restoredId = savedInstanceState?.getString(STATE_NOTE_ID)
        session = CaptureSession(
            canvas = screenCanvasSize(),
            journal = InkJournal(journalSink),
            clock = wallClock,
            noteId = restoredId?.let(::NoteId) ?: NoteId.random(),
            createdAt = savedInstanceState?.getLong(STATE_CREATED_AT) ?: wallClock.nowMillis(),
        )
        pendingPhoto = savedInstanceState?.let { state ->
            val id = state.getString(STATE_PHOTO_ID) ?: return@let null
            PhotoClip(PhotoClipId(id), state.getLong(STATE_PHOTO_AT), state.getString(STATE_PHOTO_PATH) ?: return@let null)
        }

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
        // sicuro: il salvataggio non dipende dalla conferma (D5, D20). Lo stesso vale
        // per il testo a metà.
        inkView.commitIfDrawing()
        commitTextNow()
        super.onPause()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_NOTE_ID, session.noteId.value)
        outState.putLong(STATE_CREATED_AT, session.createdAt)
        pendingPhoto?.let {
            outState.putString(STATE_PHOTO_ID, it.id.value)
            outState.putLong(STATE_PHOTO_AT, it.takenAt)
            outState.putString(STATE_PHOTO_PATH, it.path)
        }
    }

    override fun onStop() {
        super.onStop()
        // Il sistema può uccidere un processo in secondo piano quando vuole: le
        // scritture in coda vanno su disco adesso, non "fra poco".
        journalSink.awaitWrites()
        // Il foglio è uscito dallo schermo, e con lui la nota (D34). Il manifest
        // dichiara i cambi di configurazione, quindi ruotare il telefono non arriva
        // qui e non spezza la nota in due.
        //
        // L'unica eccezione è la fotocamera aperta da qui: il foglio è coperto, non
        // abbandonato, e la foto deve tornare nella nota.
        if (!isChangingConfigurations && pendingPhoto == null) finish()
    }

    /** Il testo digitato va nel giornale adesso, non fra un momento (D5, D38). */
    private fun commitTextNow() {
        if (!::textField.isInitialized) return
        mainHandler.removeCallbacks(commitTextTask)
        session.commitText(textField.text.toString())
    }

    private fun openKeyboard() {
        textField.visibility = View.VISIBLE
        textField.requestFocus()
        getSystemService(InputMethodManager::class.java)?.showSoftInput(textField, InputMethodManager.SHOW_IMPLICIT)
    }

    /**
     * Apre la fotocamera del sistema, che scrive la foto direttamente nei nostri file (D38).
     *
     * A telefono bloccato si usa la variante sicura: la fotocamera si apre sopra il blocco
     * senza chiedere il codice, come il foglio, e senza dare accesso alla galleria.
     */
    private fun takePhoto() {
        val id = PhotoClipId.random()
        val path = NoteFiles.newPhotoPath(id.value)
        val uri = NoteFiles.uri(NoteFiles.Root.DEVICE, path)
        val locked = getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == true
        val intent = Intent(if (locked) MediaStore.ACTION_IMAGE_CAPTURE_SECURE else MediaStore.ACTION_IMAGE_CAPTURE)
            .putExtra(MediaStore.EXTRA_OUTPUT, uri)
            .addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        intent.clipData = ClipData.newRawUri(null, uri)

        // Nel giornale **prima** di aprire la fotocamera: mentre è aperta il sistema può
        // uccidere il nostro processo, e la foto deve comunque ritrovare la sua nota.
        val clip = session.addPhoto(path, id)
        pendingPhoto = clip
        try {
            startActivityForResult(intent, REQUEST_PHOTO)
        } catch (e: ActivityNotFoundException) {
            pendingPhoto = null
            session.discardPhoto(clip)
            Toast.makeText(this, R.string.no_camera, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_PHOTO) return
        val clip = pendingPhoto ?: return
        pendingPhoto = null
        if (resultCode == RESULT_OK) showPhoto(clip) else session.discardPhoto(clip)
    }

    /** Una miniatura sul foglio: la foto c'è, ed è in questa nota. Letta fuori dal thread dell'interfaccia. */
    private fun showPhoto(clip: PhotoClip) {
        val size = dp(56)
        val view = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
        photoStrip.addView(view, LinearLayout.LayoutParams(size, size).apply { rightMargin = dp(8) })
        val app = applicationContext
        Thread {
            val bitmap = NoteFiles.resolve(app, clip.path)?.let { NoteRenderer.decodeThumbnail(it, size) }
            view.post { view.setImageBitmap(bitmap) }
        }.start()
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

        // Il campo di testo, nascosto finché non si tocca la tastiera (D38).
        textField = EditText(this).apply {
            setHint(R.string.text_hint)
            textSize = 20f
            setTextColor(InkPalette.INK)
            setBackgroundColor(0xF2FFFFFF.toInt())
            setPadding(dp(20), dp(16), dp(20), dp(16))
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            minLines = 3
            visibility = View.GONE
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
        root.addView(textField, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT,
        ).apply { gravity = Gravity.TOP })

        // Tastiera e fotocamera, piccole e tenui (D38). Non sono decisioni da prendere:
        // chi vuole scrivere a mano scrive e basta, e le icone non gli costano niente.
        val tools = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        tools.addView(toolButton(R.drawable.ic_keyboard, R.string.keyboard) { openKeyboard() })
        tools.addView(toolButton(R.drawable.ic_camera, R.string.camera) { takePhoto() })
        root.addView(tools, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.START
            leftMargin = dp(8)
            bottomMargin = dp(28)
        })

        photoStrip = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        root.addView(photoStrip, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.START
            leftMargin = dp(20)
            bottomMargin = dp(92)
        })

        // L'uscita (D5, D21). Non salva: è già tutto nel giornale.
        root.addView(
            Button(this).apply {
                text = getString(R.string.ok)
                setOnClickListener {
                    commitTextNow()
                    finish()
                }
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

    private fun toolButton(icon: Int, label: Int, onClick: () -> Unit) = ImageButton(this).apply {
        setImageResource(icon)
        setColorFilter(InkPalette.MUTED)
        background = null
        contentDescription = getString(label)
        // 48 dp: il bersaglio minimo per un pollice, anche se l'icona è piccola (D16).
        minimumWidth = dp(48)
        minimumHeight = dp(48)
        setPadding(dp(12), dp(12), dp(12), dp(12))
        setOnClickListener { onClick() }
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

        const val REQUEST_PHOTO = 1
        const val TEXT_COMMIT_DELAY_MS = 1_200L
        const val STATE_NOTE_ID = "note_id"
        const val STATE_CREATED_AT = "created_at"
        const val STATE_PHOTO_ID = "photo_id"
        const val STATE_PHOTO_AT = "photo_at"
        const val STATE_PHOTO_PATH = "photo_path"

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
