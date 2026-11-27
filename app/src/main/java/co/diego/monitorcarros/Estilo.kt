package co.diego.monitorcarros

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

/** Colores y piezas visuales comunes de Jerocar (tema oscuro). */
object Estilo {
    val FONDO = Color.parseColor("#0A0B10")
    val TARJETA = Color.parseColor("#14161E")
    val TARJETA2 = Color.parseColor("#1C1F2A")
    val BORDE = Color.parseColor("#262A36")
    val TEXTO = Color.parseColor("#F4F5F8")
    val GRIS = Color.parseColor("#8B90A0")
    val MEDIA: Typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    val FINA: Typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
    val CIAN = Color.parseColor("#22D3EE")
    val VIOLETA = Color.parseColor("#A855F7")
    val NARANJA = Color.parseColor("#FF8A00")
    val VERDE = Color.parseColor("#34D399")

    fun dp(ctx: Context, v: Float): Int = (v * ctx.resources.displayMetrics.density).toInt()

    fun fondo(color: Int, radio: Float, ctx: Context, borde: Int? = null): GradientDrawable {
        val g = GradientDrawable()
        g.setColor(color)
        g.cornerRadius = dp(ctx, radio).toFloat()
        if (borde != null) g.setStroke(dp(ctx, 1f).coerceAtLeast(1), borde)
        return g
    }

    fun degradado(c1: Int, c2: Int, radio: Float, ctx: Context): GradientDrawable {
        val g = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(c1, c2))
        g.cornerRadius = dp(ctx, radio).toFloat()
        return g
    }

    fun texto(ctx: Context, t: String, tam: Float, color: Int = TEXTO, negrita: Boolean = false): TextView {
        val v = TextView(ctx)
        v.text = t
        v.textSize = tam
        v.setTextColor(color)
        v.typeface = if (negrita) MEDIA else Typeface.create("sans-serif", Typeface.NORMAL)
        v.letterSpacing = if (tam >= 20f) -0.01f else 0.01f
        return v
    }

    fun icono(ctx: Context, res: Int, color: Int, tam: Float = 24f): android.widget.ImageView {
        val i = android.widget.ImageView(ctx)
        i.setImageResource(res)
        i.setColorFilter(color)
        i.layoutParams = LinearLayout.LayoutParams(dp(ctx, tam), dp(ctx, tam))
        return i
    }

    fun insignia(ctx: Context, t: String, fondoColor: Int, colorTexto: Int): TextView {
        val b = TextView(ctx)
        b.text = t
        b.textSize = 8.5f
        b.typeface = Typeface.DEFAULT_BOLD
        b.setTextColor(colorTexto)
        b.gravity = Gravity.CENTER
        b.setPadding(dp(ctx, 3f), 0, dp(ctx, 3f), 0)
        b.background = fondo(fondoColor, 4f, ctx)
        return b
    }

    /** Botón con fondo redondeado. principal = degradado naranja. */
    fun boton(ctx: Context, t: String, principal: Boolean = false, accion: () -> Unit): Button {
        val b = Button(ctx)
        b.text = t
        b.isAllCaps = false
        b.textSize = if (principal) 17f else 14f
        b.typeface = MEDIA
        b.letterSpacing = 0.02f
        b.setTextColor(if (principal) Color.parseColor("#0A0B10") else TEXTO)
        b.background = if (principal) {
            degradado(Color.parseColor("#22D3EE"), Color.parseColor("#818CF8"), 14f, ctx)
        } else {
            fondo(TARJETA2, 12f, ctx, BORDE)
        }
        b.stateListAnimator = null
        b.minHeight = dp(ctx, if (principal) 58f else 44f)
        b.setPadding(dp(ctx, 12f), 0, dp(ctx, 12f), 0)
        b.setOnClickListener { accion() }
        return b
    }

    fun chip(ctx: Context, t: String, activo: Boolean, accion: () -> Unit): TextView {
        val v = texto(ctx, t, 13f, if (activo) Color.parseColor("#0A0B10") else TEXTO, activo)
        v.gravity = Gravity.CENTER
        v.setPadding(dp(ctx, 14f), dp(ctx, 8f), dp(ctx, 14f), dp(ctx, 8f))
        v.background = if (activo) degradado(CIAN, VIOLETA, 20f, ctx) else fondo(TARJETA2, 20f, ctx, BORDE)
        v.setOnClickListener { accion() }
        return v
    }

    fun campo(ctx: Context, valor: String, numero: Boolean): EditText {
        val e = EditText(ctx)
        e.setText(valor)
        e.setTextColor(TEXTO)
        e.setHintTextColor(GRIS)
        e.textSize = 15f
        e.inputType = if (numero) android.text.InputType.TYPE_CLASS_NUMBER else android.text.InputType.TYPE_CLASS_TEXT
        e.background = fondo(TARJETA2, 12f, ctx, BORDE)
        e.setPadding(dp(ctx, 14f), dp(ctx, 12f), dp(ctx, 14f), dp(ctx, 12f))
        return e
    }

    fun espacio(ctx: Context, alto: Float): View {
        val v = View(ctx)
        v.layoutParams = LinearLayout.LayoutParams(1, dp(ctx, alto))
        return v
    }

    fun lp(ancho: Int, alto: Int, peso: Float = 0f, margen: Int = 0): LinearLayout.LayoutParams {
        val p = LinearLayout.LayoutParams(ancho, alto, peso)
        p.setMargins(margen, margen, margen, margen)
        return p
    }
}
