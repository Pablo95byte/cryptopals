package app.inknote.core.model

/**
 * Tipo di punta. Determina come [app.inknote.core.ink.WidthProfile] calcola lo
 * spessore e come il renderer nativo compone il colore.
 */
enum class PenKind {
    /** Spessore variabile con pressione/velocità, opaco. La punta predefinita. */
    BALLPOINT,

    /** Più spesso e meno reattivo alla pressione. */
    MARKER,

    /** Spessore costante e colore semitrasparente: va disegnato sotto gli altri tratti. */
    HIGHLIGHTER,
}

/**
 * Stile di un tratto.
 *
 * @param color colore ARGB compattato (0xAARRGGBB) come [Int]. Teniamo un intero
 *   e non un tipo colore di piattaforma perché il modello viaggia identico su
 *   iOS, Android e widget; la conversione la fa ogni renderer.
 * @param baseWidth spessore massimo in unità logiche del canvas (vedi [CanvasSize]).
 */
data class Pen(
    val color: Int,
    val kind: PenKind = PenKind.BALLPOINT,
    val baseWidth: Float = 3f,
) {
    init {
        require(baseWidth > 0f) { "baseWidth deve essere positivo: $baseWidth" }
    }
}
