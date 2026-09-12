package app.inknote.android

import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import app.inknote.android.InkDraw.toPath
import app.inknote.core.capture.InkJournal
import app.inknote.core.capture.RecoveredNote
import app.inknote.core.geometry.Bounds
import app.inknote.core.geometry.RenderQuality
import app.inknote.core.geometry.StrokeGeometry
import app.inknote.core.model.Note

/**
 * Le note che stanno nel giornale, ricostruite.
 *
 * Schermata di servizio, non di prodotto: serve a verificare sul telefono che
 * l'inchiostro sopravviva alla morte del processo. In questa fetta il giornale non
 * viene ancora assorbito nell'archivio — `JournalIngest` esiste ed è testato, ma
 * collegarlo richiede il driver SQLite di Android, che è il passo successivo. Per ora
 * chiudere l'app, riaprirla e ritrovare qui i tratti è esattamente la prova che
 * serve.
 */
class RecoveredNotesActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val journal = InkJournal(AndroidInkJournalSink.open(this))
        val recovered = journal.recover()

        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(28), dp(20), dp(28))
            setBackgroundColor(InkPalette.PAPER)
        }

        column.addView(header(recovered))

        for (entry in recovered) {
            column.addView(caption(entry))
            column.addView(
                NotePreview(this, entry.note),
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(180)).apply {
                    bottomMargin = dp(20)
                },
            )
        }

        column.addView(
            Button(this).apply {
                text = getString(R.string.clear_journal)
                setOnClickListener {
                    journal.clear()
                    recreate()
                }
            },
        )

        setContentView(
            ScrollView(this).apply {
                setBackgroundColor(InkPalette.PAPER)
                addView(column)
            },
        )
    }

    private fun header(recovered: List<RecoveredNote>) = TextView(this).apply {
        val strokes = recovered.sumOf { it.note.strokes.size }
        val torn = recovered.any { it.hadTornTail }
        text = buildString {
            append("${recovered.size} note nel giornale, $strokes tratti")
            // Un tratto perso si dice, non si nasconde (D22).
            if (torn) append("\nla coda del giornale era troncata: un tratto si è perso")
        }
        setTextColor(if (torn) 0xFFB4402F.toInt() else InkPalette.INK)
        textSize = 15f
        setPadding(0, 0, 0, dp(20))
    }

    private fun caption(entry: RecoveredNote) = TextView(this).apply {
        text = "${entry.note.id.value.take(8)} · ${entry.note.strokes.size} tratti"
        setTextColor(InkPalette.MUTED)
        textSize = 11f
        setPadding(0, 0, 0, dp(6))
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}

/**
 * Anteprima di una nota, inquadrata sull'inchiostro e non sul foglio intero: tre
 * parole scritte in alto a sinistra, mostrate senza ritaglio, sarebbero illeggibili.
 * È lo stesso ragionamento che vale per i widget.
 */
private class NotePreview(context: Context, private val note: Note) : View(context) {

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(0xFFFFFDF6.toInt())

        val bounds = Bounds.of(note)?.inflate(4f) ?: return
        if (bounds.width <= 0f || bounds.height <= 0f) return

        val scale = minOf(width / bounds.width, height / bounds.height)
        val offsetX = (width - bounds.width * scale) / 2f - bounds.minX * scale
        val offsetY = (height - bounds.height * scale) / 2f - bounds.minY * scale

        for (outlined in StrokeGeometry.outlines(note, RenderQuality.THUMBNAIL)) {
            canvas.drawPath(
                outlined.outline.toPath(scale, offsetX, offsetY),
                InkDraw.paint(outlined.pen),
            )
        }
    }
}
