package app.inknote.android

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import app.inknote.core.model.Note
import app.inknote.core.model.NoteId
import java.text.DateFormat
import java.util.Date

/**
 * Una nota aperta: l'inchiostro in grande, il testo, le foto, e le due cose che si fanno
 * con una nota dopo averla scritta — mandarla dove si tengono le note (D31) o buttarla.
 *
 * ## Il disegno (D46)
 *
 * Un foglio di carta al centro della scrivania, largo al massimo quanto si legge bene
 * anche su un tablet. In alto, indietro ed elimina; in basso, galleggiante, l'azione che
 * conta: "Manda a…".
 */
class NoteActivity : Activity() {

    private lateinit var column: LinearLayout
    private lateinit var sendButton: View

    override fun onCreate(savedInstanceState: Bundle?) {
        ProcessState.anyActivityCreated = true
        super.onCreate(savedInstanceState)
        Ui.edgeToEdge(this)

        val root = FrameLayout(this).apply { setBackgroundColor(getColor(R.color.desk)) }

        column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(4), dp(20), dp(120))
        }
        val scroll = ScrollView(this).apply {
            clipToPadding = false
            isVerticalScrollBarEnabled = false
            // Il foglio non si allarga oltre quanto si legge comodamente: su un tablet
            // resta una colonna al centro, non una riga lunga un metro.
            addView(FrameLayout(context).apply {
                val width = if (resources.displayMetrics.widthPixels <= dp(MAX_CONTENT_DP)) {
                    ViewGroup.LayoutParams.MATCH_PARENT
                } else {
                    dp(MAX_CONTENT_DP)
                }
                addView(column, FrameLayout.LayoutParams(width, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL))
            })
        }
        Ui.padForSystemBars(scroll, top = true, bottom = true)
        root.addView(scroll)

        sendButton = Ui.pill(this, getString(R.string.share), R.drawable.ic_share, primary = true) {}
        val sendHolder = FrameLayout(this).apply { setPadding(0, 0, 0, dp(24)) }
        Ui.padForSystemBars(sendHolder, top = false, bottom = true)
        sendHolder.addView(sendButton, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(56)))
        sendButton.visibility = View.INVISIBLE
        root.addView(sendHolder, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL))

        setContentView(root)

        val id = NoteId(intent.getStringExtra(EXTRA_NOTE_ID) ?: return finish())
        Archive.run(this, { it.note(id) }) { loaded ->
            if (loaded == null || loaded.isDeleted) finish() else show(loaded)
        }
    }

    private fun show(note: Note) {
        column.removeAllViews()
        val primary = getColor(R.color.text_primary)

        // La barra in alto: indietro, e in fondo elimina. Niente titolo: il titolo è la nota.
        column.addView(Ui.row(
            this,
            Ui.iconButton(this, R.drawable.ic_back, getString(R.string.back), primary) { finish() },
            Ui.spacer(this),
            Ui.iconButton(this, R.drawable.ic_delete, getString(R.string.delete), primary) { confirmDelete(note) },
        ), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            // Le icone allineate al bordo del foglio, non al bordo del loro bersaglio.
            leftMargin = -dp(12)
            rightMargin = -dp(12)
        })

        column.addView(Ui.text(this, Ui.CAPTION + 0.5f, Ui.SEMIBOLD, getColor(R.color.text_secondary)).apply {
            text = DateFormat.getDateTimeInstance(DateFormat.FULL, DateFormat.SHORT).format(Date(note.createdAt))
            setPadding(dp(4), dp(8), 0, dp(14))
        })

        val width = contentWidth() - dp(40)
        if (note.hasInk) {
            val height = NoteRenderer.inkHeightFor(note, width, dp(180), dp(560))
            column.addView(FrameLayout(this).apply {
                Ui.card(this, context, elevationDp = 2f)
                addView(InkPreviewView(context).apply {
                    this.note = note
                    setPadding(dp(16), dp(16), dp(16), dp(16))
                }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height).apply { bottomMargin = dp(14) })
        }

        note.typedText?.let { text ->
            column.addView(Ui.text(this, 18f, Ui.REGULAR, getColor(R.color.on_paper)).apply {
                this.text = text
                setTextIsSelectable(true)
                setPadding(dp(20), dp(18), dp(20), dp(18))
                Ui.card(this, context, elevationDp = 2f)
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(14)
            })
        }

        for (clip in note.visibleVoiceClips) {
            clip.transcript?.let { transcript ->
                column.addView(Ui.text(this, Ui.BODY, Ui.REGULAR, getColor(R.color.text_secondary)).apply {
                    text = "“$transcript”"
                    setPadding(dp(4), 0, dp(4), dp(14))
                })
            }
        }

        for (photo in note.visiblePhotoClips) {
            val view = ImageView(this).apply {
                adjustViewBounds = true
                scaleType = ImageView.ScaleType.FIT_CENTER
                Ui.clipRounded(this, dp(Ui.RADIUS_CARD.toInt()).toFloat())
                elevation = dp(2).toFloat()
            }
            column.addView(view, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(14)
            })
            val app = applicationContext
            Thread {
                val bitmap = NoteFiles.resolve(app, photo.path)?.let { NoteRenderer.decodeThumbnail(it, width) }
                view.post { if (bitmap != null) view.setImageBitmap(bitmap) else view.visibility = View.GONE }
            }.start()
        }

        sendButton.setOnClickListener { ShareNote.share(this, note) }
        sendButton.visibility = View.VISIBLE
    }

    private fun confirmDelete(note: Note) {
        AlertDialog.Builder(this)
            .setMessage(R.string.delete_confirm)
            .setPositiveButton(R.string.delete) { _, _ ->
                // Un tombstone, non una cancellazione: la nota resta in archivio trenta
                // giorni, e il sync futuro saprà che è stata cancellata (D8, D26).
                Archive.run(this, { it.markDeleted(note.id, System.currentTimeMillis()) }) { finish() }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    /** Larghezza della colonna: tutto lo schermo sul telefono, al massimo 680 dp altrove. */
    private fun contentWidth(): Int = minOf(resources.displayMetrics.widthPixels, dp(MAX_CONTENT_DP))

    private fun dp(value: Int): Int = Ui.dp(this, value.toFloat())

    companion object {
        const val EXTRA_NOTE_ID = "app.inknote.android.NOTE_ID"
        private const val MAX_CONTENT_DP = 680
    }
}
