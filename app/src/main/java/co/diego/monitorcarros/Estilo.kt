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
    val FONDO = Color.parseColor("#0B1020")
    val TARJETA = Color.parseColor("#161C30")
    val TARJETA2 = Color.parseColor("#1E2540")
    val BORDE = Color.parseColor("#2A3252")
    val TEXTO = Color.parseColor("#F1F4FF")
    val GRIS = Color.parseColor("#9AA3C0")
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
        if (negrita) v.setTypeface(Typeface.DEFAULT, Typeface.BOLD)
        return v
    }

    /** Botón con fondo redondeado. principal = degradado naranja. */
    fun boton(ctx: Context, t: String, principal: Boolean = false, accion: () -> Unit): Button {
        val b = Button(ctx)
        b.text = t
        b.isAllCaps = false
        b.textSize = if (principal) 17f else 14f
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD)
        b.setTextColor(if (principal) Color.WHITE else TEXTO)
        b.background = if (principal) {
            degradado(Color.parseColor("#FF8A00"), Color.parseColor("#FF4D6D"), 16f, ctx)
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
        val v = texto(ctx, t, 13f, if (activo) Color.parseColor("#0B1020") else TEXTO, activo)
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
