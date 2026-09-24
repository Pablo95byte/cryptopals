package app.inknote.core.geometry

import app.inknote.core.model.Note

/**
 * Una nota inquadrata in un riquadro: cosa mostrare, quanto ingrandire, con che dettaglio.
 *
 * @param source la parte di foglio da mostrare — l'inchiostro, non tutto il foglio.
 * @param scale fattore di ingrandimento da applicare, già limitato.
 */
data class NoteFrame(
    val source: Bounds,
    val scale: Float,
    val quality: RenderQuality,
)

/**
 * @param maxScale quanto si può ingrandire l'inchiostro. Serve: due parole ritagliate
 *   strette e portate a riempire un riquadro grande sembrano un manifesto, non una nota.
 */
data class NoteFramingConfig(
    val inkMargin: Float = 4f,
    val maxScale: Float = 2.5f,
) {
    init {
        require(maxScale >= 1f && inkMargin >= 0f)
    }

    companion object {
        val Default = NoteFramingConfig()
    }
}

/**
 * Inquadra una nota in un riquadro.
 *
 * Serve all'elenco delle note e alle anteprime **dentro l'app**. Non serve ai widget:
 * il widget non mostra note, è un foglio bianco e nient'altro (D30).
 *
 * Questo file è quello che resta di `WidgetFraming`, cancellato con D30. Le due regole
 * sopravvissute sono quelle che valgono in qualunque riquadro, non solo in un widget:
 * si inquadra l'inchiostro e non il foglio, e l'ingrandimento ha un tetto.
 */
object NoteFraming {

    /**
     * @return `null` se la nota non ha inchiostro da mostrare — una nota di sola voce.
     *   Chi chiama mostra la trascrizione al suo posto.
     */
    fun fit(note: Note, into: Bounds, config: NoteFramingConfig = NoteFramingConfig.Default): NoteFrame? {
        if (!note.hasInk || into.width <= 0f || into.height <= 0f) return null
        val ink = Bounds.of(note)?.inflate(config.inkMargin) ?: return null
        if (ink.width <= 0f || ink.height <= 0f) return null

        val fit = minOf(into.width / ink.width, into.height / ink.height)
        return NoteFrame(
            source = ink,
            scale = fit.coerceAtMost(config.maxScale),
            quality = qualityFor(into),
        )
    }

    /**
     * Il dettaglio lo decide il riquadro, non chi disegna: è il riquadro a stabilire
     * quanti punti di contorno finiscono in memoria (D11).
     */
    fun qualityFor(box: Bounds): RenderQuality {
        val side = minOf(box.width, box.height)
        return when {
            side >= 200f -> RenderQuality.SCREEN
            side >= 110f -> RenderQuality.WIDGET
            else -> RenderQuality.THUMBNAIL
        }
    }
}
