package co.diego.monitorcarros

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.InputType
import android.widget.LinearLayout
import android.widget.Toast
import org.json.JSONObject
import java.net.URLEncoder
import java.util.Calendar

/** Mensaje al vendedor: siempre lo envía el usuario, con un toque. */
object Mensajes {
    const val PLANTILLA = "{saludo}, ¿cómo está? Vi su {carro} publicado en {fuente}{precio}. " +
        "Estoy interesado y puedo concretar rápido. ¿Sigue disponible? ¿Cuándo lo puedo ver?"

    fun saludo(): String {
        val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return when {
            h < 12 -> "Buenos días"
            h < 19 -> "Buenas tardes"
            else -> "Buenas noches"
        }
    }

    fun armar(plantilla: String, o: JSONObject): String {
        val p = o.optString("p")
        return plantilla.ifBlank { PLANTILLA }
            .replace("{saludo}", saludo())
            .replace("{carro}", o.optString("t").ifBlank { "vehículo" })
            .replace("{fuente}", o.optString("f").ifBlank { "TuCarro" })
            .replace("{precio}", if (p.isNotBlank()) " por $ $p" else "")
    }

    private fun numero(n: String): String {
        val d = n.filter { it.isDigit() }
        return if (d.length == 10 && d.startsWith("3")) "57$d" else d
    }

    /** abrirPublicacion: lo que hace la app para mostrar el carro (dentro de Jerocar). */
    fun dialogo(ctx: Context, plantilla: String, o: JSONObject, abrirPublicacion: () -> Unit) {
        val col = LinearLayout(ctx)
        col.orientation = LinearLayout.VERTICAL
        val pad = Estilo.dp(ctx, 18f)
        col.setPadding(pad, Estilo.dp(ctx, 8f), pad, 0)
        val texto = Estilo.campo(ctx, armar(plantilla, o), false)
        texto.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        texto.minLines = 4
        col.addView(texto)
        col.addView(Estilo.espacio(ctx, 10f))
        val tel = Estilo.campo(ctx, "", true)
        tel.inputType = InputType.TYPE_CLASS_PHONE
        tel.hint = "Celular del vendedor (opcional)"
        col.addView(tel)

        AlertDialog.Builder(ctx)
            .setTitle("💬 Escribir al vendedor")
            .setView(col)
            .setPositiveButton("WhatsApp") { _, _ ->
                val msg = texto.text.toString()
                val n = numero(tel.text.toString())
                try {
                    val i = if (n.length >= 11) {
                        Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$n?text=" + URLEncoder.encode(msg, "UTF-8")))
                    } else {
                        Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, msg).setPackage("com.whatsapp")
                    }
                    ctx.startActivity(i)
                } catch (e: Exception) {
                    copiar(ctx, msg)
                    Toast.makeText(ctx, "No encontré WhatsApp. Copié el mensaje.", Toast.LENGTH_LONG).show()
                }
            }
            .setNeutralButton("Copiar y abrir publicación") { _, _ ->
                copiar(ctx, texto.text.toString())
                Toast.makeText(ctx, "Mensaje copiado. Péguelo en el chat del vendedor.", Toast.LENGTH_LONG).show()
                abrirPublicacion()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    fun copiar(ctx: Context, t: String) {
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("Mensaje Jerocar", t))
    }
}
