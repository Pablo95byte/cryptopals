package app.inknote.android

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import app.inknote.core.model.Note
import app.inknote.core.model.NoteId
import java.text.DateFormat
import java.util.Date

/**
 * Una nota aperta: l'inchiostro in grande, il testo, le foto, e le due cose che si fanno
 * con una nota dopo averla scritta — mandarla dove si tengono le note (D31) o buttarla.
 */
class NoteActivity : Activity() {

    private lateinit var column: LinearLayout
    private var note: Note? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        ProcessState.anyActivityCreated = true
        super.onCreate(savedInstanceState)

        column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(32))
        }
        setContentView(ScrollView(this).apply {
            setBackgroundColor(InkPalette.PAPER)
            fitsSystemWindows = true
            addView(column)
        })

        val id = NoteId(intent.getStringExtra(EXTRA_NOTE_ID) ?: return finish())
        Archive.run(this, { it.note(id) }) { loaded ->
            if (loaded == null || loaded.isDeleted) finish() else show(loaded)
        }
    }

    private fun show(note: Note) {
        this.note = note
        column.removeAllViews()

        column.addView(TextView(this).apply {
            text = DateFormat.getDateTimeInstance(DateFormat.FULL, DateFormat.SHORT).format(Date(note.createdAt))
            setTextColor(InkPalette.MUTED)
            textSize = 13f
            setPadding(0, 0, 0, dp(12))
        })

        if (note.hasInk) {
            column.addView(InkPreviewView(this).apply {
                this.note = note
                setBackgroundColor(0xFFFFFFFF.toInt())
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(320)).apply { bottomMargin = dp(16) })
        }

        note.typedText?.let { text ->
            column.addView(TextView(this).apply {
                this.text = text
                textSize = 18f
                setTextColor(InkPalette.INK)
                setTextIsSelectable(true)
                setPadding(0, 0, 0, dp(16))
            })
        }

        for (clip in note.visibleVoiceClips) {
            clip.transcript?.let { transcript ->
                column.addView(TextView(this).apply {
                    text = "“$transcript”"
                    textSize = 16f
                    setTextColor(InkPalette.INK)
                    setPadding(0, 0, 0, dp(16))
                })
            }
        }

        val width = resources.displayMetrics.widthPixels
        for (photo in note.visiblePhotoClips) {
            val view = ImageView(this).apply { adjustViewBounds = true }
            column.addView(view, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(16)
            })
            val app = applicationContext
            Thread {
                val bitmap = NoteFiles.resolve(app, photo.path)?.let { NoteRenderer.decodeThumbnail(it, width) }
                view.post { if (bitmap != null) view.setImageBitmap(bitmap) else view.visibility = View.GONE }
            }.start()
        }

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(8), 0, 0)
        }
        actions.addView(Button(this).apply {
            setText(R.string.share)
            setOnClickListener { ShareNote.share(this@NoteActivity, note) }
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        actions.addView(Button(this).apply {
            setText(R.string.delete)
            setOnClickListener { confirmDelete(note) }
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        column.addView(actions)
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

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        const val EXTRA_NOTE_ID = "app.inknote.android.NOTE_ID"
    }
}
