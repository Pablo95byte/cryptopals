package app.inknote.android

import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Outline
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Il kit dell'interfaccia (D46): tipografia, superfici, pulsanti, margini sicuri.
 *
 * Esiste perché l'app non usa né Material né AppCompat — si inizializzano con un
 * `ContentProvider`, e la cattura ne pagherebbe il costo a ogni avvio (invariante 21).
 * Senza un kit, ogni schermata reinventerebbe raggi, ombre e colori, e l'app
 * sembrerebbe fatta a pezzi. Con il kit è una cosa sola.
 */
object Ui {

    // Scala tipografica: pochi gradini, ben distanziati.
    const val TITLE = 34f
    const val HEADLINE = 22f
    const val BODY = 16f
    const val LABEL = 15f
    const val CAPTION = 12.5f

    // Pesi dell'Instrument Sans, che è un font variabile.
    const val REGULAR = 400
    const val MEDIUM = 500
    const val SEMIBOLD = 600
    const val BOLD = 700

    // Raggi: le superfici grandi più tonde di quelle piccole.
    const val RADIUS_CARD = 22f
    const val RADIUS_PILL = 999f
    const val RADIUS_THUMB = 14f

    private val typefaces = HashMap<Int, Typeface>()

    fun dp(context: Context, value: Float): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, context.resources.displayMetrics).toInt()

    fun color(context: Context, id: Int): Int = context.getColor(id)

    /**
     * Instrument Sans (D16) al peso chiesto. Letto una volta per peso e tenuto: il foglio
     * non lo usa mai, quindi non costa niente all'avvio della cattura.
     */
    fun typeface(context: Context, weight: Int): Typeface = typefaces.getOrPut(weight) {
        runCatching {
            Typeface.Builder(context.assets, "fonts/InstrumentSans.ttf")
                .setFontVariationSettings("'wght' $weight")
                .build()
        }.getOrNull() ?: Typeface.create(Typeface.SANS_SERIF, if (weight >= SEMIBOLD) Typeface.BOLD else Typeface.NORMAL)
    }

    fun text(context: Context, size: Float, weight: Int, color: Int, maxLines: Int = Int.MAX_VALUE) =
        TextView(context).apply {
            textSize = size
            typeface = typeface(context, weight)
            setTextColor(color)
            if (maxLines != Int.MAX_VALUE) {
                this.maxLines = maxLines
                ellipsize = TextUtils.TruncateAt.END
            }
            // Un po' d'aria fra le righe: il testo fitto è la prima cosa che sa di vecchio.
            setLineSpacing(0f, 1.15f)
        }

    fun rounded(color: Int, radiusPx: Float, strokeColor: Int? = null, strokePx: Int = 0) =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radiusPx
            setColor(color)
            if (strokeColor != null && strokePx > 0) setStroke(strokePx, strokeColor)
        }

    /** Il riscontro al tocco, dentro la stessa forma della superficie. */
    fun pressable(context: Context, content: Drawable?, radiusPx: Float): Drawable =
        RippleDrawable(ColorStateList.valueOf(color(context, R.color.ripple)), content, rounded(0xFFFFFFFF.toInt(), radiusPx))

    /** Ritaglia la vista ai suoi angoli arrotondati: foto e anteprime non sbordano. */
    fun clipRounded(view: View, radiusPx: Float) {
        view.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(v: View, outline: Outline) {
                outline.setRoundRect(0, 0, v.width, v.height, radiusPx)
            }
        }
        view.clipToOutline = true
    }

    /**
     * Pulsante a pillola: icona e parola. [primary] è l'azione della schermata, piena;
     * le altre sono tenui.
     */
    /**
     * La goccia vermiglia (D66, D71): dentro l'app vuol dire solo "da smistare" (D72).
     * Non va usata per decorare, o smette di dire qualcosa.
     */
    fun drop(context: Context): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(color(context, R.color.spark))
    }

    fun pill(
        context: Context,
        label: String,
        icon: Int?,
        primary: Boolean,
        font: Typeface? = null,
        onClick: () -> Unit,
    ): TextView {
        val background = if (primary) color(context, R.color.accent) else color(context, R.color.chip)
        val foreground = if (primary) color(context, R.color.on_accent) else color(context, R.color.text_primary)
        return TextView(context).apply {
            text = label
            textSize = LABEL
            typeface = font ?: typeface(context, SEMIBOLD)
            setTextColor(foreground)
            gravity = Gravity.CENTER
            val horizontal = dp(context, 22f)
            setPadding(horizontal, 0, horizontal, 0)
            minHeight = dp(context, 52f)
            minWidth = dp(context, 52f)
            if (icon != null) {
                val drawable = context.getDrawable(icon)?.mutate()?.apply {
                    setTint(foreground)
                    val size = dp(context, 20f)
                    setBounds(0, 0, size, size)
                }
                setCompoundDrawablesRelative(drawable, null, null, null)
                compoundDrawablePadding = dp(context, 8f)
            }
            this.background = pressable(context, rounded(background, dp(context, RADIUS_PILL).toFloat()), dp(context, RADIUS_PILL).toFloat())
            // Piatto: il colore pieno basta a dire "questo è il pulsante". Le ombre sotto i
            // comandi li facevano sembrare adesivi appiccicati sopra (D46).
            stateListAnimator = null
            elevation = 0f
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
        }
    }

    /** Pulsante rotondo con sola icona. 48 dp: il bersaglio minimo per un pollice. */
    fun iconButton(context: Context, icon: Int, description: String, tint: Int, onClick: () -> Unit) =
        ImageButton(context).apply {
            setImageResource(icon)
            setColorFilter(tint)
            contentDescription = description
            val size = dp(context, 48f)
            minimumWidth = size
            minimumHeight = size
            val pad = dp(context, 12f)
            setPadding(pad, pad, pad, pad)
            background = pressable(context, null, size / 2f)
            // Nessuna ombra e nessun contorno: l'icona sta sulla superficie, non sopra.
            stateListAnimator = null
            elevation = 0f
            outlineProvider = null
            setOnClickListener { onClick() }
        }

    /** Superficie di una card: carta, angoli tondi, ombra leggera. */
    fun card(view: View, context: Context, radiusDp: Float = RADIUS_CARD, elevationDp: Float = 0f) {
        val radius = dp(context, radiusDp).toFloat()
        // Un filo di bordo al posto dell'ombra: la carta si stacca dalla scrivania senza
        // sembrare sollevata (D46).
        view.background = rounded(color(context, R.color.card), radius, color(context, R.color.outline), dp(context, 1f).coerceAtLeast(1))
        view.elevation = dp(context, elevationDp).toFloat()
        clipRounded(view, radius)
    }

    fun row(context: Context, vararg children: View, gravity: Int = Gravity.CENTER_VERTICAL) =
        LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            this.gravity = gravity
            for (child in children) addView(child)
        }

    fun spacer(context: Context) = View(context).apply {
        layoutParams = LinearLayout.LayoutParams(0, 1, 1f)
    }

    /**
     * Da bordo a bordo: il contenuto passa sotto le barre di sistema trasparenti. Prima
     * di Android 15 va chiesto; da Android 15 è obbligatorio, e senza queste righe il
     * titolo finirebbe sotto l'orologio.
     */
    @Suppress("DEPRECATION")
    fun edgeToEdge(activity: Activity, lightBars: Boolean = !isNight(activity)) {
        var flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        if (lightBars) flags = flags or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        activity.window.decorView.systemUiVisibility = flags
    }

    /**
     * Sposta dentro una vista di quanto occupano le barre di sistema, sopra il suo
     * margine interno di partenza. Si ricalcola quando le barre cambiano, per esempio
     * quando si apre la tastiera.
     */
    @Suppress("DEPRECATION")
    fun padForSystemBars(view: View, top: Boolean, bottom: Boolean, sides: Boolean = true) {
        val start = view.paddingLeft
        val topBase = view.paddingTop
        val end = view.paddingRight
        val bottomBase = view.paddingBottom
        view.setOnApplyWindowInsetsListener { v, insets ->
            v.setPadding(
                start + if (sides) insets.systemWindowInsetLeft else 0,
                topBase + if (top) insets.systemWindowInsetTop else 0,
                end + if (sides) insets.systemWindowInsetRight else 0,
                bottomBase + if (bottom) insets.systemWindowInsetBottom else 0,
            )
            insets
        }
        view.requestApplyInsets()
    }

    fun isNight(context: Context): Boolean =
        (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    fun matchWidth() = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
}
