package co.diego.monitorcarros

import android.content.Context
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.text.Normalizer

/**
 * Lee la página pública de TuCarro "Publicados hoy" (la misma que se ve en Chrome)
 * para el departamento y rango de precio elegidos. No usa ninguna cuenta.
 */
object TuCarro {
    private const val UA =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Mobile Safari/537.36"

    var ultimaUrl = ""
        private set

    private fun slug(s: String): String =
        Normalizer.normalize(s.trim().lowercase(), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}"), "")
            .replace(Regex("\\s+"), "-")

    fun marketplace(store: Store): String {
        val ciudad = store.ciudades.split(",").map { it.trim() }.firstOrNull { it.isNotEmpty() } ?: "bucaramanga"
        return "https://www.facebook.com/marketplace/${slug(ciudad)}/vehicles" +
            "?minPrice=${store.precioMin}&maxPrice=${store.precioMax}&daysSinceListed=1&sortBy=creation_time_descend"
    }

    fun urlTodos(store: Store): String =
        "https://carros.tucarro.com.co/${slug(store.departamento)}/_PriceRange_${store.precioMin}COP-${store.precioMax}COP"

    fun url(store: Store, desde: Int): String {
        val pagina = if (desde > 1) "_Desde_$desde" else ""
        return "https://carros.tucarro.com.co/${slug(store.departamento)}/" +
            "${pagina}_PriceRange_${store.precioMin}COP-${store.precioMax}COP_PublishedToday_YES"
    }

    private fun descargar(u: String): String {
        val c = URL(u).openConnection() as HttpURLConnection
        c.connectTimeout = 20_000
        c.readTimeout = 30_000
        c.instanceFollowRedirects = true
        c.setRequestProperty("User-Agent", UA)
        c.setRequestProperty("Accept", "text/html,application/xhtml+xml")
        c.setRequestProperty("Accept-Language", "es-CO,es;q=0.9")
        val codigo = c.responseCode
        val stream = if (codigo in 200..299) c.inputStream else c.errorStream
        val texto = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
        c.disconnect()
        if (codigo !in 200..299) throw RuntimeException("TuCarro respondió $codigo")
        return texto
    }

    private fun limpiar(s: String): String = s
        .replace("&amp;", "&").replace("&quot;", "\"").replace("&#x27;", "'").replace("&#39;", "'")
        .replace("&lt;", "<").replace("&gt;", ">").replace("&nbsp;", " ")
        .replace(Regex("\\s+"), " ").trim()

    private val reLink = Regex("href=\"(https://[^\"]*?MCO-?(\\d{6,})[^\"]*)\"")
    private val reTitulo = listOf(
        Regex("poly-component__title[^>]*>([^<]{4,})<"),
        Regex("ui-search-item__title[^>]*>([^<]{4,})<"),
        Regex("<h[23][^>]*>([^<]{4,})</h[23]>"),
        Regex("title=\"([^\"]{4,})\""),
    )
    private val rePrecio = Regex("andes-money-amount__fraction[^>]*>([\\d.]+)<")
    private val reAtributo = Regex("attributes?[-_]list__item[^>]*>([^<]+)<")
    private val reLugar = listOf(
        Regex("poly-component__location[^>]*>([^<]+)<"),
        Regex("ui-search-item__location[^>]*>([^<]+)<"),
    )

    fun leer(html: String): List<Carro> {
        var bloques = html.split(Regex("<li[^>]*ui-search-layout__item")).drop(1)
        if (bloques.isEmpty()) bloques = html.split(Regex("class=\"[^\"]*poly-card[\\s\"]")).drop(1)
        val mapa = LinkedHashMap<String, Carro>()
        for (b in bloques) {
            val link = reLink.find(b) ?: continue
            val id = "MCO" + link.groupValues[2]
            if (mapa.containsKey(id)) continue
            val enlace = link.groupValues[1].substringBefore("#").substringBefore("?")
            val titulo = reTitulo.firstNotNullOfOrNull { it.find(b)?.groupValues?.get(1) }?.let { limpiar(it) }
                ?: "Vehículo"
            val precio = rePrecio.find(b)?.groupValues?.get(1)?.replace(".", "")?.toLongOrNull() ?: 0L
            val attrs = reAtributo.findAll(b).map { limpiar(it.groupValues[1]) }.toList()
            val anio = attrs.firstOrNull { it.matches(Regex("(19|20)\\d{2}")) }
            val km = attrs.firstOrNull { it.contains("km", ignoreCase = true) }
            val lugar = reLugar.firstNotNullOfOrNull { it.find(b)?.groupValues?.get(1) }?.let { limpiar(it) } ?: ""
            val ciudad = lugar.substringAfterLast(" - ").trim()
            val barrio = if (lugar.contains(" - ")) lugar.substringBeforeLast(" - ").trim() else ""
            mapa[id] = Carro(id, titulo, precio, enlace, anio, km, ciudad, barrio, false)
        }
        return mapa.values.toList()
    }

    fun buscar(ctx: Context, store: Store): List<Carro> {
        val todos = LinkedHashMap<String, Carro>()
        var desde = 1
        for (pagina in 0 until 4) {
            val u = url(store, desde)
            if (pagina == 0) ultimaUrl = u
            val html = descargar(u)
            if (pagina == 0) {
                try {
                    File(ctx.filesDir, "ultima.html").writeText(html)
                } catch (e: Exception) {
                    // sin importancia
                }
            }
            val lista = leer(html)
            val antes = todos.size
            lista.forEach { todos[it.id] = it }
            if (lista.size < 40 || todos.size == antes) break
            desde += 48
            Thread.sleep(1500)
        }
        return todos.values.toList()
    }

    fun diagnostico(ctx: Context): String {
        val html = try {
            File(ctx.filesDir, "ultima.html").readText()
        } catch (e: Exception) {
            ""
        }
        if (html.isEmpty()) return "Sin página guardada todavía. Toque REVISAR AHORA primero."
        val i = listOf("ui-search-layout__item", "poly-card", "MCO-")
            .map { html.indexOf(it) }.filter { it >= 0 }.minOrNull() ?: 0
        val inicio = maxOf(0, i - 300)
        return "URL: $ultimaUrl\nTamaño: ${html.length}\nCarros leídos: ${leer(html).size}\n\n" +
            html.substring(inicio, minOf(html.length, inicio + 3500))
    }
}
