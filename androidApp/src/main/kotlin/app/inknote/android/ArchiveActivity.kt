package app.inknote.android

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.util.LruCache
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import app.inknote.core.model.Note
import java.text.DateFormat
import java.util.Date

/**
 * L'archivio: tutte le note, la ricerca, e il pulsante per scriverne una nuova (D39).
 *
 * È ciò che si apre toccando l'icona dell'app — "se apro l'app le ho tutte", dalla
 * richiesta iniziale. Il widget, il riquadro rapido e la scorciatoia aprono invece il
 * foglio: dalla home si aggiunge, non si rilegge (D30).
 *
 * View di piattaforma e non Compose (D39): Compose porta con sé librerie che si
 * inizializzano con un `ContentProvider`, e nello stesso processo della cattura quel
 * costo lo pagherebbe anche il foglio (invariante 21).
 */
class ArchiveActivity : Activity() {

    private val adapter by lazy { NotesAdapter(this) }
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var search: EditText
    private lateinit var empty: TextView
    private val reloadTask = Runnable { reload() }

    override fun onCreate(savedInstanceState: Bundle?) {
        ProcessState.anyActivityCreated = true
        super.onCreate(savedInstanceState)
        setContentView(buildLayout())
    }

    override fun onResume() {
        super.onResume()
        // Quello che il foglio ha scritto nel giornale entra in archivio adesso, quando
        // nessuno sta aspettando di scrivere (D20, D22).
        Archive.ingest(this) { result ->
            if (result.hadTornTail) Toast.makeText(this, R.string.torn_tail_notice, Toast.LENGTH_LONG).show()
            reload()
        }
    }

    private fun reload() {
        val term = search.text.toString()
        Archive.run(this, { store ->
            if (term.isBlank()) store.recentNotes(limit = 500) else store.search(term, limit = 200)
        }) { notes ->
            adapter.submit(notes)
            empty.visibility = if (notes.isEmpty()) View.VISIBLE else View.GONE
            empty.setText(if (term.isBlank()) R.string.archive_empty else R.string.search_empty)
        }
    }

    private fun buildLayout(): View {
        val root = FrameLayout(this).apply {
            setBackgroundColor(InkPalette.PAPER)
            // Da Android 15 le app disegnano sotto le barre di sistema: il contenuto va
            // spostato dentro, o il titolo finisce sotto l'orologio.
            fitsSystemWindows = true
        }
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), 0)
        }

        column.addView(TextView(this).apply {
            setText(R.string.archive_title)
            textSize = 28f
            setTextColor(InkPalette.INK)
            setPadding(dp(4), dp(8), 0, dp(12))
        })

        search = EditText(this).apply {
            setHint(R.string.search_hint)
            isSingleLine = true
            textSize = 16f
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                override fun afterTextChanged(s: Editable?) {
                    // Una ricerca a ogni tasto, ma non a ogni tasto di una parola scritta
                    // in fretta: si aspetta che la mano si fermi un attimo.
                    handler.removeCallbacks(reloadTask)
                    handler.postDelayed(reloadTask, SEARCH_DELAY_MS)
                }
            })
        }
        column.addView(search)

        val list = ListView(this).apply {
            adapter = this@ArchiveActivity.adapter
            divider = null
            clipToPadding = false
            setPadding(0, dp(8), 0, dp(96))
            setOnItemClickListener { _, _, position, _ ->
                val note = this@ArchiveActivity.adapter.getItem(position)
                startActivity(Intent(this@ArchiveActivity, NoteActivity::class.java).putExtra(NoteActivity.EXTRA_NOTE_ID, note.id.value))
            }
        }
        column.addView(list, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(column)

        empty = TextView(this).apply {
            setText(R.string.archive_empty)
            setTextColor(InkPalette.MUTED)
            textSize = 16f
            gravity = Gravity.CENTER
            setPadding(dp(32), 0, dp(32), 0)
            visibility = View.GONE
        }
        root.addView(empty, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER,
        ))

        // Il foglio, a un tocco anche da qui.
        root.addView(Button(this).apply {
            text = "+"
            textSize = 28f
            contentDescription = getString(R.string.new_note)
            setOnClickListener { startActivity(Intent(this@ArchiveActivity, CaptureActivity::class.java)) }
        }, FrameLayout.LayoutParams(dp(72), dp(72), Gravity.BOTTOM or Gravity.END).apply {
            rightMargin = dp(20)
            bottomMargin = dp(24)
        })

        return root
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private companion object {
        const val SEARCH_DELAY_MS = 200L
    }
}

/** Una riga per nota: l'anteprima a sinistra, le prime parole e la data a destra. */
private class NotesAdapter(private val context: Context) : BaseAdapter() {

    private var notes: List<Note> = emptyList()
    private val dates = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
    private val density = context.resources.displayMetrics.density

    /** Miniature delle foto già lette, per non rileggerle a ogni scorrimento. */
    private val thumbnails = LruCache<String, android.graphics.Bitmap>(40)

    fun submit(newNotes: List<Note>) {
        notes = newNotes
        notifyDataSetChanged()
    }

    override fun getCount(): Int = notes.size
    override fun getItem(position: Int): Note = notes[position]
    override fun getItemId(position: Int): Long = notes[position].id.value.hashCode().toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val holder = (convertView?.tag as? Holder) ?: Holder(context, density)
        val note = notes[position]

        holder.ink.note = if (note.hasInk) note else null
        holder.ink.visibility = if (note.hasInk) View.VISIBLE else View.GONE

        val firstPhoto = note.visiblePhotoClips.firstOrNull()
        holder.photo.setImageDrawable(null)
        holder.photo.visibility = if (!note.hasInk && firstPhoto != null) View.VISIBLE else View.GONE
        if (!note.hasInk && firstPhoto != null) loadThumbnail(holder.photo, firstPhoto.path)

        holder.snippet.text = snippetOf(note)
        holder.date.text = dates.format(Date(note.updatedAt))
        return holder.row
    }

    private fun snippetOf(note: Note): String =
        note.typedText
            ?: note.recognizedText
            ?: note.visibleVoiceClips.firstNotNullOfOrNull { it.transcript }
            ?: context.getString(if (note.hasInk) R.string.handwritten_note else R.string.photo_note)

    private fun loadThumbnail(view: ImageView, path: String) {
        view.tag = path
        thumbnails.get(path)?.let { view.setImageBitmap(it); return }
        val app = context.applicationContext
        Thread {
            val bitmap = NoteFiles.resolve(app, path)?.let { NoteRenderer.decodeThumbnail(it, (96 * density).toInt()) }
            view.post {
                if (bitmap != null) thumbnails.put(path, bitmap)
                // La riga può essere già stata riusata per un'altra nota mentre si leggeva.
                if (view.tag == path) view.setImageBitmap(bitmap)
            }
        }.start()
    }

    private class Holder(context: Context, density: Float) {
        val ink = InkPreviewView(context)
        val photo = ImageView(context).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
        val snippet = TextView(context).apply {
            textSize = 16f
            setTextColor(InkPalette.INK)
            maxLines = 2
        }
        val date = TextView(context).apply {
            textSize = 12f
            setTextColor(InkPalette.MUTED)
        }
        val row: View

        init {
            fun dp(v: Int) = (v * density).toInt()
            val preview = FrameLayout(context).apply {
                setBackgroundColor(Color.WHITE)
                addView(ink, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                addView(photo, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            }
            val text = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(14), 0, 0, 0)
                gravity = Gravity.CENTER_VERTICAL
                addView(snippet)
                addView(date)
            }
            row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, dp(6), 0, dp(6))
                addView(preview, LinearLayout.LayoutParams(dp(120), dp(84)))
                addView(text, LinearLayout.LayoutParams(0, dp(84), 1f))
                tag = this@Holder
            }
        }
    }
}
