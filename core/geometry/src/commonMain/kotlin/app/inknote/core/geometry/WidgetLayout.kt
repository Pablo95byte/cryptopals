package app.inknote.core.geometry

import app.inknote.core.model.Note
import app.inknote.core.model.NoteId

/** Formato del widget, come lo sceglie l'utente quando lo mette in home. */
enum class WidgetSize { SMALL, MEDIUM, LARGE }

/**
 * Cosa va disegnato in una casella del widget.
 *
 * Sono due casi e non uno perché una nota vocale non ha inchiostro da mostrare, e
 * lasciarla fuori dal widget vorrebbe dire che metà della cattura (D18) non si vede in
 * home.
 */
sealed interface WidgetCellContent {

    /**
     * Inchiostro, inquadrato su [source] e ingrandito di [scale].
     *
     * Quando una nota ha sia inchiostro sia voce vince l'inchiostro: la calligrafia è
     * l'identità del prodotto (D1), e in un riquadro di pochi centimetri è anche quella
     * che si riconosce a colpo d'occhio.
     */
    data class Ink(
        val source: Bounds,
        val scale: Float,
        val quality: RenderQuality,
    ) : WidgetCellContent

    /** Una nota vocale. [transcript] è `null` se il riconoscimento non è ancora arrivato. */
    data class Voice(
        val durationMs: Int,
        val transcript: String?,
    ) : WidgetCellContent
}

/** Una nota e il rettangolo in cui va disegnata, in unità logiche del widget. */
data class WidgetCell(
    val noteId: NoteId,
    val rect: Bounds,
    val content: WidgetCellContent,
)

/**
 * Come riempire un widget.
 *
 * @param captureTarget l'area che, toccata, apre il foglio. È **sempre** presente: è la
 *   ragione per cui il widget esiste.
 * @param wholeWidgetCaptures `true` quando tutto il widget scrive, e non solo
 *   [captureTarget]. Nel formato piccolo un pulsante ruberebbe spazio alla nota e
 *   costringerebbe a centrare il dito; e con il widget vuoto, un piccolo `+` in un
 *   riquadro altrimenti inutile è una porta sprecata.
 * @param hiddenNoteCount quante note c'erano e non sono entrate, perché mostrarle
 *   avrebbe voluto dire renderle illeggibili.
 */
data class WidgetLayout(
    val size: WidgetSize,
    val cells: List<WidgetCell>,
    val captureTarget: Bounds,
    val wholeWidgetCaptures: Boolean,
    val hiddenNoteCount: Int,
) {
    val isEmpty: Boolean get() = cells.isEmpty()
}

/**
 * Misure dell'inquadratura, in unità logiche.
 *
 * @param minCellSide lato minimo di una casella. Sotto questa misura una nota non si
 *   legge, e una nota che non si legge nel widget non serve a niente.
 * @param maxScale quanto si può ingrandire l'inchiostro. Serve: due parole ritagliate
 *   strette e portate a riempire una casella grande sembrano un manifesto, non una nota.
 * @param captureTargetSide lato dell'area di cattura. Mai sotto 44: è il minimo perché
 *   un dito la prenda al primo colpo.
 */
data class WidgetLayoutConfig(
    val padding: Float = 12f,
    val gap: Float = 10f,
    val minCellSide: Float = 64f,
    val maxScale: Float = 2.5f,
    val inkMargin: Float = 4f,
    val captureTargetSide: Float = 44f,
) {
    init {
        require(captureTargetSide >= 44f) { "area di cattura sotto i 44 punti: $captureTargetSide" }
        require(minCellSide > 0f && maxScale >= 1f && gap >= 0f && padding >= 0f)
    }

    companion object {
        val Default = WidgetLayoutConfig()
    }
}

/**
 * Decide come riempire un widget: quante note ci stanno, dove, e con che ritaglio.
 *
 * ## Le regole, e da dove vengono
 *
 * La missione è annotare un'idea **senza sforzo**. Da lì escono tutte:
 *
 * - **La leggibilità viene prima della quantità.** Il numero di note mostrate non lo
 *   decide il formato ma lo spazio: se la griglia preferita produrrebbe caselle troppo
 *   piccole, si mostrano meno note più grandi. Un widget con sei francobolli
 *   indistinguibili costringe ad aprire l'app, che è il contrario del punto.
 * - **Si inquadra l'inchiostro, non il foglio.** Tre parole scritte in alto a sinistra
 *   di un foglio grande, mostrate senza ritaglio, sono invisibili.
 * - **L'area di cattura c'è sempre.** Nel formato piccolo, e con il widget vuoto, è
 *   tutto il widget: nessun bersaglio da centrare.
 * - **La qualità la decide la casella, non il formato.** Un widget grande con quattro
 *   note ha caselle piccole quanto quelle di un medio con due (D11).
 * - **La prima nota va nella prima casella.** Chi chiama passa le note nell'ordine in
 *   cui vuole vederle; qui non si riordina niente, e a parità di ingresso l'uscita è
 *   sempre la stessa.
 *
 * ## Perché sta in `core:geometry`
 *
 * Inquadrare è geometria, e questo modulo ha già `Bounds` e `RenderQuality`. **Quali**
 * note mostrare non si decide qui: arrivano già scelte e ordinate da chi chiama
 * (archivio più configurazione del widget), così la politica di prodotto resta fuori
 * dalla matematica.
 */
object WidgetFraming {

    /**
     * @param notes le note candidate, nell'ordine in cui si vogliono vedere.
     * @param widget la misura del widget in unità logiche.
     */
    fun layout(
        notes: List<Note>,
        size: WidgetSize,
        widget: Bounds,
        config: WidgetLayoutConfig = WidgetLayoutConfig.Default,
    ): WidgetLayout {
        val showable = notes.filter { !it.isDeleted && !it.isEmpty }

        val usable = Bounds(
            minX = widget.minX + config.padding,
            minY = widget.minY + config.padding,
            maxX = widget.maxX - config.padding,
            maxY = widget.maxY - config.padding,
        )

        // Nessuna nota da mostrare: il widget serve solo ad aprire il foglio, e allora
        // tutto il widget lo apre.
        if (showable.isEmpty() || usable.width <= 0f || usable.height <= 0f) {
            return WidgetLayout(
                size = size,
                cells = emptyList(),
                captureTarget = widget,
                wholeWidgetCaptures = true,
                hiddenNoteCount = 0,
            )
        }

        // Nel formato piccolo il pulsante ruberebbe spazio alla nota: si tocca tutto.
        val wholeWidgetCaptures = size == WidgetSize.SMALL

        // Dove mettere l'area di cattura: sul lato lungo, dove costa meno.
        //
        // Su un widget basso e largo — il formato medio — una striscia in alto si
        // mangerebbe più spazio del contenuto: 44 punti su 131 di altezza utile
        // lascerebbero alle note meno di quanto prende il pulsante. Di lato, invece,
        // costa 44 punti su 305 di larghezza. Non è simmetria per gusto: è la stessa
        // regola della leggibilità, applicata al pulsante.
        val sideColumn = !wholeWidgetCaptures && usable.width > usable.height * LANDSCAPE_RATIO

        val gridArea = when {
            wholeWidgetCaptures -> usable
            sideColumn -> Bounds(
                usable.minX,
                usable.minY,
                usable.maxX - config.captureTargetSide - config.gap,
                usable.maxY,
            )
            else -> Bounds(
                usable.minX,
                usable.minY + config.captureTargetSide + config.gap,
                usable.maxX,
                usable.maxY,
            )
        }

        val captureTarget = when {
            wholeWidgetCaptures -> widget
            // In colonna il pulsante sta a metà altezza: è dove arriva il pollice.
            sideColumn -> Bounds(
                minX = usable.maxX - config.captureTargetSide,
                minY = (usable.minY + usable.maxY - config.captureTargetSide) / 2f,
                maxX = usable.maxX,
                maxY = (usable.minY + usable.maxY + config.captureTargetSide) / 2f,
            )
            else -> Bounds(
                minX = usable.maxX - config.captureTargetSide,
                minY = usable.minY,
                maxX = usable.maxX,
                maxY = usable.minY + config.captureTargetSide,
            )
        }

        val grid = chooseGrid(size, gridArea, showable.size, config)

        if (grid == null) {
            // Nemmeno una casella leggibile ci sta: meglio un widget che apre soltanto
            // il foglio, che uno con una nota illeggibile dentro.
            return WidgetLayout(
                size = size,
                cells = emptyList(),
                captureTarget = if (wholeWidgetCaptures) widget else captureTarget,
                wholeWidgetCaptures = true,
                hiddenNoteCount = showable.size,
            )
        }

        val cells = ArrayList<WidgetCell>(grid.capacity)
        val cellWidth = (gridArea.width - config.gap * (grid.columns - 1)) / grid.columns
        val cellHeight = (gridArea.height - config.gap * (grid.rows - 1)) / grid.rows

        for ((index, note) in showable.take(grid.capacity).withIndex()) {
            val row = index / grid.columns
            val column = index % grid.columns
            val rect = Bounds(
                minX = gridArea.minX + column * (cellWidth + config.gap),
                minY = gridArea.minY + row * (cellHeight + config.gap),
                maxX = gridArea.minX + column * (cellWidth + config.gap) + cellWidth,
                maxY = gridArea.minY + row * (cellHeight + config.gap) + cellHeight,
            )
            cells += WidgetCell(noteId = note.id, rect = rect, content = contentOf(note, rect, config))
        }

        return WidgetLayout(
            size = size,
            cells = cells,
            captureTarget = captureTarget,
            wholeWidgetCaptures = wholeWidgetCaptures,
            hiddenNoteCount = showable.size - cells.size,
        )
    }

    private fun contentOf(note: Note, cell: Bounds, config: WidgetLayoutConfig): WidgetCellContent {
        // L'inchiostro vince sulla voce: è quello che si riconosce a colpo d'occhio (D1).
        val ink = if (note.hasInk) Bounds.of(note)?.inflate(config.inkMargin) else null
        if (ink != null && ink.width > 0f && ink.height > 0f) {
            val fit = minOf(cell.width / ink.width, cell.height / ink.height)
            return WidgetCellContent.Ink(
                source = ink,
                scale = fit.coerceAtMost(config.maxScale),
                quality = qualityFor(cell),
            )
        }

        val clip = note.visibleVoiceClips.first()
        return WidgetCellContent.Voice(durationMs = clip.durationMs, transcript = clip.transcript)
    }

    /**
     * Il dettaglio lo decide la casella, non il formato del widget: è la casella a
     * stabilire quanti punti di contorno finiscono in memoria (D11).
     */
    private fun qualityFor(cell: Bounds): RenderQuality {
        val side = minOf(cell.width, cell.height)
        return when {
            side >= 200f -> RenderQuality.SCREEN
            side >= 110f -> RenderQuality.WIDGET
            else -> RenderQuality.THUMBNAIL
        }
    }

    /**
     * Sceglie la griglia più ricca che produce caselle ancora leggibili, senza
     * superare il numero di note disponibili.
     *
     * @return `null` se nemmeno una casella sta dentro il minimo.
     */
    private fun chooseGrid(
        size: WidgetSize,
        area: Bounds,
        noteCount: Int,
        config: WidgetLayoutConfig,
    ): Grid? = candidates(size)
        .filter { it.capacity <= noteCount || it.capacity == 1 }
        .firstOrNull { grid ->
            val cellWidth = (area.width - config.gap * (grid.columns - 1)) / grid.columns
            val cellHeight = (area.height - config.gap * (grid.rows - 1)) / grid.rows
            cellWidth >= config.minCellSide && cellHeight >= config.minCellSide
        }

    /** Dalla griglia più ricca alla più povera: si prende la prima che sta dentro. */
    private fun candidates(size: WidgetSize): List<Grid> = when (size) {
        WidgetSize.SMALL -> listOf(Grid(rows = 1, columns = 1))
        WidgetSize.MEDIUM -> listOf(Grid(1, 3), Grid(1, 2), Grid(1, 1))
        WidgetSize.LARGE -> listOf(Grid(2, 2), Grid(1, 2), Grid(1, 1))
    }

    private data class Grid(val rows: Int, val columns: Int) {
        val capacity: Int get() = rows * columns
    }

    /**
     * Oltre questo rapporto fra larghezza e altezza il widget è "basso e largo", e
     * l'area di cattura conviene di lato.
     */
    private const val LANDSCAPE_RATIO = 1.3f
}
