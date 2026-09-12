package app.inknote.core.geometry

import app.inknote.core.ink.CatmullRom
import app.inknote.core.ink.InkConfig
import app.inknote.core.ink.WidthProfile
import app.inknote.core.model.Note
import app.inknote.core.model.Pen
import app.inknote.core.model.Stroke
import app.inknote.core.model.StrokeId

/**
 * Livello di dettaglio con cui si costruisce la geometria.
 *
 * Non è una rifinitura: è il vincolo dei widget. Su iOS l'estensione widget ha un
 * tetto di memoria basso e viene terminata se lo supera, su Android il bitmap del
 * widget passa per una transazione Binder con un limite di dimensione. Lo stesso
 * dettaglio che serve a schermo, nel widget, fa sparire la nota.
 */
enum class RenderQuality(val spacing: Float, val capSegments: Int) {
    /** Schermo, inchiostro sotto il dito: il contorno deve essere liscio. */
    SCREEN(spacing = 1.5f, capSegments = 8),

    /** Widget in home: lo stesso tratto, meno punti. */
    WIDGET(spacing = 2.5f, capSegments = 5),

    /** Anteprima in elenco: qui conta solo la forma generale. */
    THUMBNAIL(spacing = 4f, capSegments = 3),
}

/** Un tratto pronto da disegnare: il contorno da riempire e con quale penna. */
data class StrokeOutline(
    val strokeId: StrokeId,
    val pen: Pen,
    val outline: Outline,
)

/**
 * Il punto di ingresso per i renderer nativi.
 *
 * È l'unica API che SwiftUI, Compose, WidgetKit e Glance devono conoscere: danno
 * una nota, ricevono contorni da riempire. Tutta la calligrafia — levigatura,
 * spessore, punte — sta da questo lato del confine, quindi è identica su tutte e
 * quattro le superfici.
 */
object StrokeGeometry {

    fun outline(
        stroke: Stroke,
        quality: RenderQuality = RenderQuality.SCREEN,
        config: InkConfig = InkConfig.Default,
    ): Outline {
        if (stroke.points.isEmpty()) return Outline.Empty
        val curve = CatmullRom.resample(stroke.points, quality.spacing)
        val widths = WidthProfile.compute(curve, stroke.pen, config)
        return StrokeOutliner.outline(curve, widths, quality.capSegments)
    }

    /**
     * I contorni di una nota, nell'ordine di disegno. I tratti cancellati non
     * compaiono, ma restano nel modello: vedi [app.inknote.core.model.mergeNotes].
     */
    fun outlines(
        note: Note,
        quality: RenderQuality = RenderQuality.SCREEN,
        config: InkConfig = InkConfig.Default,
    ): List<StrokeOutline> = note.visibleStrokes.map { stroke ->
        StrokeOutline(
            strokeId = stroke.id,
            pen = stroke.pen,
            outline = outline(stroke, quality, config),
        )
    }
}
