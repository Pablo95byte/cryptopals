package app.inknote.android

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.text.format.DateUtils
import android.util.LruCache
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.AbsListView
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import app.inknote.core.model.Note

/**
 * L'archivio: tutte le note, la ricerca, e il pulsante per scriverne una nuova (D39).
 *
 * È ciò che si apre toccando l'icona dell'app — "se apro l'app le ho tutte", dalla
 * richiesta iniziale. Il widget, il riquadro rapido e "Scrivi" aprono invece il foglio:
 * dalla home si aggiunge, non si rilegge (D30).
 *
 * ## Il disegno (D46)
 *
 * Bigliettini di carta su una scrivania: una griglia che mette tante colonne quante ne
 * stanno — due su un telefono, tre o quattro su un tablet o in orizzontale. Titolo grande,
 * ricerca a pillola, e un solo pulsante pieno: "Scrivi". Di notte la scrivania si scurisce
 * e i bigliettini restano carta, perché l'inchiostro è scuro.
 */
class ArchiveActivity : Activity() {

    private val adapter by lazy { NotesAdapter(this) }
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var search: EditText
    private lateinit var clearSearch: View
    private lateinit var subtitle: TextView
    private lateinit var empty: View
    private lateinit var emptyTitle: TextView
    private lateinit var emptyBody: TextView
    private val reloadTask = Runnable { reload() }

    override fun onCreate(savedInstanceState: Bundle?) {
        ProcessState.anyActivityCreated = true
        super.onCreate(savedInstanceState)
        Ui.edgeToEdge(this)
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
            val notes = if (term.isBlank()) store.recentNotes(limit = 500) else store.search(term, limit = 200)
            notes to store.liveNoteCount()
        }) { (notes, total) ->
            adapter.submit(notes)
            subtitle.text = resources.getQuantityString(R.plurals.note_count, total.toInt(), total.toInt())
            subtitle.visibility = if (total > 0) View.VISIBLE else View.INVISIBLE
            val searching = term.isNotBlank()
            empty.visibility = if (notes.isEmpty()) View.VISIBLE else View.GONE
            emptyTitle.setText(if (searching) R.string.search_empty else R.string.archive_empty_title)
            emptyBody.visibility = if (searching) View.GONE else View.VISIBLE
        }
    }

    private fun buildLayout(): View {
        val root = FrameLayout(this).apply { setBackgroundColor(getColor(R.color.desk)) }

        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        Ui.padForSystemBars(column, top = true, bottom = false)
        column.addView(buildHeader(), Ui.matchWidth())

        val grid = GridView(this).apply {
            adapter = this@ArchiveActivity.adapter
            numColumns = GridView.AUTO_FIT
            columnWidth = dp(158)
            stretchMode = GridView.STRETCH_COLUMN_WIDTH
            horizontalSpacing = dp(12)
            verticalSpacing = dp(12)
            // Spazio in fondo per il pulsante "Scrivi", che galleggia sopra l'elenco.
            setPadding(dp(16), dp(8), dp(16), dp(112))
            clipToPadding = false
            selector = ColorDrawable(Color.TRANSPARENT)
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            setOnItemClickListener { _, _, position, _ ->
                val note = this@ArchiveActivity.adapter.getItem(position)
                startActivity(Intent(this@ArchiveActivity, NoteActivity::class.java).putExtra(NoteActivity.EXTRA_NOTE_ID, note.id.value))
            }
            // Tenendo premuto: mandare o eliminare senza aprire la nota (D59).
            setOnItemLongClickListener { _, view, position, _ ->
                showNoteMenu(view, this@ArchiveActivity.adapter.getItem(position))
                true
            }
            // Scorrendo, la tastiera della ricerca si chiude: si stava guardando, non scrivendo.
            setOnScrollListener(object : AbsListView.OnScrollListener {
                override fun onScrollStateChanged(view: AbsListView, state: Int) {
                    if (state == AbsListView.OnScrollListener.SCROLL_STATE_TOUCH_SCROLL) hideKeyboard()
                }
                override fun onScroll(view: AbsListView, first: Int, visible: Int, total: Int) = Unit
            })
        }
        Ui.padForSystemBars(grid, top = false, bottom = true, sides = false)
        column.addView(grid, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(column)

        empty = buildEmptyState()
        root.addView(empty, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER))

        // Un solo pulsante pieno nella schermata: scrivere.
        val write = Ui.pill(this, getString(R.string.capture_label), R.drawable.ic_pen, primary = true) {
            startActivity(Intent(this, CaptureActivity::class.java))
        }
        val fabHolder = FrameLayout(this).apply { setPadding(0, 0, dp(20), dp(24)) }
        Ui.padForSystemBars(fabHolder, top = false, bottom = true)
        fabHolder.addView(write, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(56)))
        root.addView(fabHolder, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.END))

        return root
    }

    private fun buildHeader(): View {
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(8))
        }
        header.addView(Ui.text(this, Ui.TITLE, Ui.BOLD, getColor(R.color.text_primary)).apply {
            setText(R.string.archive_title)
            letterSpacing = -0.02f
        })
        subtitle = Ui.text(this, Ui.CAPTION + 1, Ui.MEDIUM, getColor(R.color.text_secondary)).apply {
            visibility = View.INVISIBLE
            setPadding(dp(2), dp(2), 0, dp(14))
        }
        header.addView(subtitle)

        // La ricerca: una pillola, non una riga sottolineata.
        val secondary = getColor(R.color.text_secondary)
        val icon = ImageView(this).apply {
            setImageResource(R.drawable.ic_search)
            setColorFilter(secondary)
        }
        search = EditText(this).apply {
            setHint(R.string.search_hint)
            isSingleLine = true
            textSize = Ui.BODY
            typeface = Ui.typeface(context, Ui.REGULAR)
            setTextColor(getColor(R.color.text_primary))
            setHintTextColor(secondary)
            background = null
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            setPadding(dp(10), 0, dp(4), 0)
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                override fun afterTextChanged(s: Editable?) {
                    clearSearch.visibility = if (s.isNullOrEmpty()) View.GONE else View.VISIBLE
                    // Una ricerca quando la mano si ferma, non a ogni tasto.
                    handler.removeCallbacks(reloadTask)
                    handler.postDelayed(reloadTask, SEARCH_DELAY_MS)
                }
            })
        }
        clearSearch = Ui.iconButton(this, R.drawable.ic_close, getString(R.string.cancel), secondary) {
            search.setText("")
            hideKeyboard()
        }.apply { visibility = View.GONE }

        val pill = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), 0, dp(4), 0)
            background = Ui.rounded(getColor(R.color.chip), dp(26).toFloat())
            addView(icon, LinearLayout.LayoutParams(dp(20), dp(20)))
            addView(search, LinearLayout.LayoutParams(0, dp(52), 1f))
            addView(clearSearch)
        }
        header.addView(pill, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)))
        return header
    }

    private fun buildEmptyState(): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        setPadding(dp(40), dp(80), dp(40), 0)
        visibility = View.GONE
        addView(ImageView(context).apply {
            setImageResource(R.drawable.ic_scribble)
            setColorFilter(getColor(R.color.text_secondary))
            alpha = 0.6f
        }, LinearLayout.LayoutParams(dp(72), dp(72)).apply { bottomMargin = dp(16) })
        emptyTitle = Ui.text(context, Ui.HEADLINE, Ui.SEMIBOLD, getColor(R.color.text_primary)).apply {
            gravity = Gravity.CENTER
            setText(R.string.archive_empty_title)
        }
        addView(emptyTitle)
        emptyBody = Ui.text(context, Ui.BODY - 1, Ui.REGULAR, getColor(R.color.text_secondary)).apply {
            gravity = Gravity.CENTER
            setText(R.string.archive_empty_body)
            setPadding(0, dp(8), 0, 0)
        }
        addView(emptyBody)
    }

    private fun showNoteMenu(anchor: View, note: Note) {
        anchor.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        PopupMenu(this, anchor).apply {
            menu.add(0, MENU_SHARE, 0, R.string.share)
            menu.add(0, MENU_DELETE, 1, R.string.delete)
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    MENU_SHARE -> ShareNote.share(this@ArchiveActivity, note)
                    MENU_DELETE -> confirmDelete(note)
                }
                true
            }
        }.show()
    }

    private fun confirmDelete(note: Note) {
        AlertDialog.Builder(this)
            .setMessage(R.string.delete_confirm)
            .setPositiveButton(R.string.delete) { _, _ ->
                // Un tombstone, non una cancellazione (D8, D26).
                Archive.run(this, { it.markDeleted(note.id, System.currentTimeMillis()) }) { reload() }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun hideKeyboard() {
        getSystemService(android.view.inputmethod.InputMethodManager::class.java)?.hideSoftInputFromWindow(search.windowToken, 0)
        search.clearFocus()
    }

    private fun dp(value: Int): Int = Ui.dp(this, value.toFloat())

    private companion object {
        const val SEARCH_DELAY_MS = 200L
        const val MENU_SHARE = 1
        const val MENU_DELETE = 2
    }
}

/**
 * Un bigliettino per nota. Tre forme, secondo cosa c'è dentro: l'inchiostro inquadrato,
 * la foto a tutta larghezza, o il testo digitato. Sotto, una riga e la data.
 */
private class NotesAdapter(private val context: Context) : BaseAdapter() {

    private var notes: List<Note> = emptyList()
    private val density = context.resources.displayMetrics.density

    /** Miniature delle foto già lette, per non rileggerle a ogni scorrimento. */
    private val thumbnails = LruCache<String, Bitmap>(48)

    fun submit(newNotes: List<Note>) {
        notes = newNotes
        notifyDataSetChanged()
    }

    override fun getCount(): Int = notes.size
    override fun getItem(position: Int): Note = notes[position]
    override fun getItemId(position: Int): Long = notes[position].id.value.hashCode().toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val holder = (convertView?.tag as? Holder) ?: Holder(context)
        val note = notes[position]
        val firstPhoto = note.visiblePhotoClips.firstOrNull()

        // Cosa va nella parte grande del bigliettino: prima l'inchiostro, che è ciò che
        // si riconosce a colpo d'occhio (D1), poi la foto, poi il testo.
        holder.ink.visibility = View.GONE
        holder.photo.visibility = View.GONE
        holder.body.visibility = View.GONE
        holder.photo.setImageDrawable(null)
        holder.photo.tag = null
        when {
            note.hasInk -> {
                holder.ink.note = note
                holder.ink.visibility = View.VISIBLE
            }
            firstPhoto != null -> {
                holder.photo.visibility = View.VISIBLE
                loadThumbnail(holder.photo, firstPhoto.path)
            }
            else -> {
                holder.body.text = snippetOf(note) ?: ""
                holder.body.visibility = View.VISIBLE
            }
        }

        // La riga sotto: il testo, se la parte grande non lo mostra già.
        val line = if (holder.body.visibility == View.VISIBLE) null else snippetOf(note)
        holder.caption.text = line ?: ""
        holder.caption.visibility = if (line == null) View.GONE else View.VISIBLE

        holder.date.text = DateUtils.getRelativeTimeSpanString(
            note.updatedAt, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE,
        )
        val photos = note.visiblePhotoClips.size
        holder.photoBadge.visibility = if (photos > 0 && !(holder.photo.visibility == View.VISIBLE && photos == 1)) View.VISIBLE else View.GONE
        holder.photoCount.text = if (photos > 1) photos.toString() else ""
        return holder.card
    }

    private fun snippetOf(note: Note): String? =
        note.typedText ?: note.recognizedText ?: note.visibleVoiceClips.firstNotNullOfOrNull { it.transcript }

    private fun loadThumbnail(view: ImageView, path: String) {
        view.tag = path
        thumbnails.get(path)?.let { view.setImageBitmap(it); return }
        val app = context.applicationContext
        val target = (180 * density).toInt()
        Thread {
            val bitmap = NoteFiles.resolve(app, path)?.let { NoteRenderer.decodeThumbnail(it, target) }
            view.post {
                if (bitmap != null) thumbnails.put(path, bitmap)
                // La vista può essere già stata riusata per un'altra nota mentre si leggeva.
                if (view.tag == path) view.setImageBitmap(bitmap)
            }
        }.start()
    }

    private class Holder(private val context: Context) {
        private fun dp(value: Float) = Ui.dp(context, value)

        val ink = InkPreviewView(context).apply { setPadding(dp(12f), dp(12f), dp(12f), dp(4f)) }
        val photo = ImageView(context).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
        val body = Ui.text(context, 15f, Ui.MEDIUM, context.getColor(R.color.on_paper), maxLines = 7).apply {
            setPadding(dp(16f), dp(16f), dp(16f), 0)
        }
        val caption = Ui.text(context, 13.5f, Ui.MEDIUM, context.getColor(R.color.on_paper), maxLines = 1)
        val date = Ui.text(context, Ui.CAPTION, Ui.REGULAR, context.getColor(R.color.on_paper_muted), maxLines = 1)
        val photoCount = Ui.text(context, Ui.CAPTION, Ui.MEDIUM, context.getColor(R.color.on_paper_muted))
        val photoBadge = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(ImageView(context).apply {
                setImageResource(R.drawable.ic_camera)
                setColorFilter(context.getColor(R.color.on_paper_muted))
            }, LinearLayout.LayoutParams(dp(15f), dp(15f)).apply { rightMargin = dp(3f) })
            addView(photoCount)
        }
        val card: View

        init {
            val preview = FrameLayout(context).apply {
                addView(ink, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                addView(photo, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                addView(body, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            }
            val footer = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(14f), dp(8f), dp(14f), dp(12f))
                addView(caption)
                addView(LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    addView(date, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                    addView(photoBadge)
                })
            }
            card = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                Ui.card(this, context)
                foreground = Ui.pressable(context, null, dp(Ui.RADIUS_CARD).toFloat())
                addView(preview, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
                addView(footer)
                layoutParams = AbsListView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(208f))
                tag = this@Holder
            }
        }
    }
}
