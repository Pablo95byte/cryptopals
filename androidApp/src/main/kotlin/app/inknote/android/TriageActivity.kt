package app.inknote.android

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.app.Activity
import android.graphics.Bitmap
import android.os.Bundle
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import app.inknote.core.model.Note
import kotlin.math.abs
import kotlin.math.min

/**
 * Lo smistamento a carte (D51, D52, D64): le note nuove una alla volta. A destra "manda",
 * a sinistra "tieni", in basso "butta"; gli stessi tre gesti come pulsanti, per chi non
 * trascina e per TalkBack.
 *
 * "Cattura adesso, smista quando hai un minuto" (D31). La coda la decide l'archivio
 * (`notesToSort`), non questa schermata: una nota esce dalla coda quando è tenuta,
 * mandata o buttata. Se la condivisione si chiude senza destinazione, la nota non risulta
 * mandata (invariante 18) e alla prossima apertura è ancora qui.
 */
class TriageActivity : Activity() {

    private val queue = ArrayDeque<Note>()
    private lateinit var title: TextView
    private lateinit var stage: FrameLayout
    private lateinit var actions: View
    private lateinit var empty: View
    private var card: View? = null
    private var hint: TextView? = null
    private var busy = false

    override fun onCreate(savedInstanceState: Bundle?) {
        ProcessState.anyActivityCreated = true
        super.onCreate(savedInstanceState)
        Ui.edgeToEdge(this)
        setContentView(buildLayout())
        Archive.run(this, { store -> store.notesToSort(limit = 100) }) { notes ->
            queue.clear()
            queue.addAll(notes)
            showCurrent()
        }
    }

    private fun buildLayout(): View {
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(getColor(R.color.desk))
        }
        Ui.padForSystemBars(column, top = true, bottom = true)

        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), dp(8), dp(8), dp(8))
        }
        title = Ui.text(this, Ui.LABEL + 1, Ui.SEMIBOLD, getColor(R.color.text_primary))
        bar.addView(title, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        bar.addView(Ui.iconButton(this, R.drawable.ic_close, getString(R.string.close), getColor(R.color.text_primary)) { finish() })
        column.addView(bar, Ui.matchWidth())

        stage = FrameLayout(this).apply { clipChildren = false }
        column.addView(stage, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        empty = buildEmptyState()
        stage.addView(empty, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER))

        actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(18), 0, dp(20))
            visibility = View.INVISIBLE
            addView(roundButton(R.drawable.ic_delete, R.string.delete, primary = false) { current()?.let(::delete) })
            addView(roundButton(R.drawable.ic_keep, R.string.keep, primary = false) { current()?.let(::keep) })
            addView(roundButton(R.drawable.ic_share, R.string.send, primary = true) { current()?.let(::send) })
        }
        column.addView(actions, Ui.matchWidth())
        return column
    }

    private fun buildEmptyState(): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        setPadding(dp(40), 0, dp(40), 0)
        visibility = View.GONE
        addView(ImageView(context).apply {
            setImageResource(R.drawable.ic_check)
            setColorFilter(getColor(R.color.text_secondary))
        }, LinearLayout.LayoutParams(dp(48), dp(48)).apply { bottomMargin = dp(12) })
        addView(Ui.text(context, Ui.HEADLINE, Ui.SEMIBOLD, getColor(R.color.text_primary)).apply {
            gravity = Gravity.CENTER
            setText(R.string.all_sorted)
        })
        addView(Ui.text(context, Ui.BODY - 1, Ui.REGULAR, getColor(R.color.text_secondary)).apply {
            gravity = Gravity.CENTER
            setText(R.string.all_sorted_body)
            setPadding(0, dp(8), 0, 0)
        })
    }

    private fun roundButton(icon: Int, label: Int, primary: Boolean, onClick: () -> Unit): View {
        val tint = if (primary) getColor(R.color.on_accent) else getColor(R.color.text_primary)
        val background = if (primary) getColor(R.color.accent) else getColor(R.color.chip)
        return Ui.iconButton(this, icon, getString(label), tint, onClick).apply {
            val size = dp(60)
            this.background = Ui.pressable(context, Ui.rounded(background, size / 2f), size / 2f)
            val pad = dp(18)
            setPadding(pad, pad, pad, pad)
            layoutParams = LinearLayout.LayoutParams(size, size).apply { marginStart = dp(14); marginEnd = dp(14) }
        }
    }

    private fun current(): Note? = if (busy) null else queue.firstOrNull()

    private fun showCurrent() {
        card?.let(stage::removeView)
        card = null
        val note = queue.firstOrNull()
        val count = queue.size
        title.text = if (count > 0) resources.getQuantityString(R.plurals.to_sort, count, count) else ""
        empty.visibility = if (note == null) View.VISIBLE else View.GONE
        actions.visibility = if (note == null) View.INVISIBLE else View.VISIBLE
        if (note == null) return

        val view = buildCard(note)
        val maxWidth = dp(520)
        val params = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT).apply {
            setMargins(dp(20), dp(12), dp(20), dp(4))
        }
        stage.addView(view, params)
        // Su un tablet la carta non diventa un lenzuolo.
        stage.post {
            val width = min(stage.width - dp(40), maxWidth)
            val height = min(stage.height - dp(16), dp(560))
            view.layoutParams = FrameLayout.LayoutParams(width, height, Gravity.CENTER)
        }
        view.setOnTouchListener(DragToSort(note))
        card = view
    }

    private fun buildCard(note: Note): View {
        val content = FrameLayout(this)
        val firstPhoto = note.visiblePhotoClips.firstOrNull()
        val caption = captionOf(note)
        when {
            note.hasInk -> content.addView(InkPreviewView(this).apply {
                this.note = note
                setPadding(dp(20), dp(20), dp(20), dp(8))
            })
            firstPhoto != null -> content.addView(ImageView(this).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                loadPhoto(this, firstPhoto.path)
            })
            else -> content.addView(Ui.text(this, 20f, Ui.MEDIUM, getColor(R.color.on_paper)).apply {
                text = caption ?: ""
                setPadding(dp(24), dp(24), dp(24), 0)
            })
        }
        val label = Ui.text(this, Ui.LABEL, Ui.SEMIBOLD, getColor(R.color.on_paper)).apply {
            background = Ui.rounded(getColor(R.color.chip), dp(18).toFloat())
            setPadding(dp(16), dp(8), dp(16), dp(8))
            visibility = View.GONE
        }
        hint = label
        content.addView(label, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply {
            topMargin = dp(16)
        })

        val footer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(18), dp(10), dp(18), dp(16))
            addView(Ui.text(context, Ui.CAPTION + 1, Ui.REGULAR, getColor(R.color.on_paper_muted), maxLines = 1).apply {
                text = android.text.format.DateUtils.getRelativeTimeSpanString(
                    note.updatedAt, System.currentTimeMillis(), android.text.format.DateUtils.MINUTE_IN_MILLIS,
                )
            })
            if ((note.hasInk || firstPhoto != null) && caption != null) {
                addView(Ui.text(context, Ui.CAPTION + 1, Ui.MEDIUM, getColor(R.color.on_paper), maxLines = 1).apply {
                    text = caption
                    ellipsize = android.text.TextUtils.TruncateAt.END
                }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(12) })
            }
        }

        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            Ui.card(this, context)
            addView(content, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
            addView(footer, Ui.matchWidth())
        }
    }

    private fun captionOf(note: Note): String? =
        note.typedText
            ?: note.recognizedText?.trim()?.ifEmpty { null }
            ?: note.visibleVoiceClips.firstNotNullOfOrNull { clip -> clip.transcript?.trim()?.ifEmpty { null } }

    private fun loadPhoto(view: ImageView, path: String) {
        val app = applicationContext
        val target = dp(520)
        Thread {
            val bitmap: Bitmap? = NoteFiles.resolve(app, path)?.let { NoteRenderer.decodeThumbnail(it, target) }
            view.post { view.setImageBitmap(bitmap) }
        }.start()
    }

    // --- Le tre azioni ---

    private fun keep(note: Note) {
        val now = System.currentTimeMillis()
        // Si rilegge la nota dall'archivio: la copia in mano può essere vecchia, e il
        // salvataggio unisce comunque (D14, D26).
        Archive.run(this, { store -> store.note(note.id)?.let { store.save(it.withSorted(now)) } })
        leave(note, toX = -1f, toY = 0f)
    }

    private fun delete(note: Note) {
        // Un tombstone, non una cancellazione (D8, D26).
        Archive.run(this, { store -> store.markDeleted(note.id, System.currentTimeMillis()) })
        leave(note, toX = 0f, toY = 1f)
    }

    private fun send(note: Note) {
        // L'invio si registra solo se l'utente sceglie una destinazione (invariante 18):
        // se chiude il foglio di condivisione, la nota torna in coda alla prossima apertura.
        ShareNote.share(this, note)
        leave(note, toX = 1f, toY = 0f)
    }

    /** La carta esce dallo schermo dalla parte del gesto, e arriva la prossima. */
    private fun leave(note: Note, toX: Float, toY: Float) {
        val view = card ?: return
        busy = true
        view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
        val distance = stage.width.coerceAtLeast(stage.height) * 1.2f
        view.animate()
            .translationX(toX * distance)
            .translationY(toY * distance)
            .rotation(toX * 18f)
            .alpha(0f)
            .setDuration(220)
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    view.animate().setListener(null)
                    if (queue.firstOrNull()?.id == note.id) queue.removeFirst()
                    busy = false
                    showCurrent()
                }
            })
            .start()
    }

    /** Trascinare la carta: la parola sopra dice cosa succederà lasciando andare. */
    private inner class DragToSort(private val note: Note) : View.OnTouchListener {
        private var downX = 0f
        private var downY = 0f

        override fun onTouch(view: View, event: MotionEvent): Boolean {
            if (busy) return false
            val threshold = dp(110).toFloat()
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    view.animate().cancel()
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downX
                    val dy = (event.rawY - downY).coerceAtLeast(-dp(40).toFloat())
                    view.translationX = dx
                    view.translationY = dy
                    view.rotation = dx / 22f / resources.displayMetrics.density
                    showHint(dx, dy, threshold)
                }
                MotionEvent.ACTION_UP -> {
                    val dx = event.rawX - downX
                    val dy = event.rawY - downY
                    hint?.visibility = View.GONE
                    when {
                        dy > threshold && abs(dy) > abs(dx) -> delete(note)
                        dx > threshold -> send(note)
                        dx < -threshold -> keep(note)
                        else -> view.animate().translationX(0f).translationY(0f).rotation(0f).setDuration(180).start()
                    }
                }
                MotionEvent.ACTION_CANCEL -> {
                    hint?.visibility = View.GONE
                    view.animate().translationX(0f).translationY(0f).rotation(0f).setDuration(180).start()
                }
            }
            return true
        }

        private fun showHint(dx: Float, dy: Float, threshold: Float) {
            val label = when {
                dy > threshold && abs(dy) > abs(dx) -> R.string.delete
                dx > threshold -> R.string.send
                dx < -threshold -> R.string.keep
                else -> null
            }
            hint?.apply {
                if (label == null) {
                    visibility = View.GONE
                } else {
                    setText(label)
                    visibility = View.VISIBLE
                }
            }
        }
    }

    private fun dp(value: Int): Int = Ui.dp(this, value.toFloat())
}
