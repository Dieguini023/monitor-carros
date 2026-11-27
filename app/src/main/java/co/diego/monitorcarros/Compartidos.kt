package co.diego.monitorcarros

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject

/** Arma la ficha de un carro guardado a mano (Marketplace u otro enlace). */
object Compartidos {
    fun limpiarTitulo(t: String): String = t
        .replace(Regex("\\s*[|·-]\\s*Facebook.*$", RegexOption.IGNORE_CASE), "")
        .replace(Regex("^Marketplace\\s*[-–·:]\\s*", RegexOption.IGNORE_CASE), "")
        .replace(Regex("\\s+"), " ")
        .trim()
        .take(90)

    fun ficha(id: String, titulo: String, precio: String, url: String, img: String, detalle: String): JSONObject {
        val o = JSONObject()
        o.put("id", id)
        o.put("t", limpiarTitulo(titulo).ifBlank { "Vehículo en Marketplace" })
        o.put("p", precio)
        o.put("u", url)
        o.put("img", img)
        o.put("a", detalle)
        o.put("l", "")
        o.put("f", if (url.contains("facebook.com")) "Marketplace" else if (url.contains("tucarro")) "TuCarro" else "Enlace")
        return o
    }
}

/**
 * Recibe "Compartir → Jerocar" desde Facebook (o cualquier app) y guarda el enlace en Detectados.
 * No abre ninguna pantalla: solo muestra un aviso corto.
 */
class CompartirActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val texto = intent?.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
        val asunto = intent?.getStringExtra(Intent.EXTRA_SUBJECT).orEmpty()
        val url = Regex("https?://\\S+").find(texto)?.value?.trimEnd('.', ',', ')')
        if (url == null) {
            Toast.makeText(this, "No encontré un enlace para guardar", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        val fbId = Regex("/marketplace/item/(\\d+)").find(url)?.groupValues?.get(1)
        val id = if (fbId != null) "FB$fbId" else "LINK" + url.hashCode().toString().replace("-", "")
        val precio = Regex("\\$\\s?(\\d{1,3}(?:[.\\s,]\\d{3})+)").find(texto)?.groupValues?.get(1)
            ?.replace(Regex("[\\s,]"), ".").orEmpty()
        val titulo = listOf(asunto, texto.replace(url, "")).firstOrNull { it.isNotBlank() }.orEmpty()
        val limpio = if (fbId != null) "https://www.facebook.com/marketplace/item/$fbId/" else url
        val store = Store(this)
        if (store.existeFicha(id)) {
            Toast.makeText(this, "Ya estaba en Jerocar › Detectados", Toast.LENGTH_SHORT).show()
        } else {
            store.registrarFichas(JSONArray().put(Compartidos.ficha(id, titulo, precio, limpio, "", "")))
            Toast.makeText(this, "⭐ Guardado en Jerocar › Detectados", Toast.LENGTH_SHORT).show()
        }
        finish()
    }
}
