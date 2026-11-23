package co.diego.monitorcarros

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class Carro(
    val id: String,
    val titulo: String,
    val precio: Long,
    val enlace: String,
    val anio: String?,
    val km: String?,
    val ciudad: String,
    val barrio: String,
    val tienda: Boolean,
)

/** Consulta oficial a Mercado Libre Colombia (TuCarro usa la misma base). */
object MercadoLibre {
    private const val API = "https://api.mercadolibre.com"
    private const val SITE = "MCO"
    private const val CATEGORIA = "MCO1744" // Carros y Camionetas
    private const val TRAMO = 10_000_000L    // busca por tramos de precio (límite de 1.000 resultados)

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")

    private fun pedir(url: String, token: String?, cuerpo: String? = null): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 20_000
        c.readTimeout = 30_000
        c.setRequestProperty("Accept", "application/json")
        c.setRequestProperty("User-Agent", "MonitorCarrosSantander/1.0")
        if (token != null) c.setRequestProperty("Authorization", "Bearer $token")
        if (cuerpo != null) {
            c.requestMethod = "POST"
            c.doOutput = true
            c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            c.outputStream.use { it.write(cuerpo.toByteArray()) }
        }
        val codigo = c.responseCode
        val stream = if (codigo in 200..299) c.inputStream else c.errorStream
        val texto = stream?.bufferedReader()?.use { it.readText() } ?: ""
        c.disconnect()
        if (codigo !in 200..299) {
            throw RuntimeException("Mercado Libre respondió $codigo: ${texto.take(200)}")
        }
        return texto
    }

    private fun token(store: Store): String? {
        if (store.clientId.isBlank() || store.clientSecret.isBlank()) return null
        if (store.token.isNotBlank() && System.currentTimeMillis() < store.tokenVence) return store.token
        val cuerpo = "grant_type=client_credentials" +
            "&client_id=${enc(store.clientId.trim())}" +
            "&client_secret=${enc(store.clientSecret.trim())}"
        val r = JSONObject(pedir("$API/oauth/token", null, cuerpo))
        store.token = r.getString("access_token")
        store.tokenVence = System.currentTimeMillis() + (r.optLong("expires_in", 21600L) - 300L) * 1000L
        return store.token
    }

    private fun buscar(params: Map<String, String>, token: String?): JSONObject {
        val q = params.entries.joinToString("&") { "${it.key}=${enc(it.value)}" }
        return JSONObject(pedir("$API/sites/$SITE/search?$q", token))
    }

    private fun estadoId(store: Store, token: String?): String {
        if (store.estadoId.isNotBlank()) return store.estadoId
        val r = buscar(mapOf("category" to CATEGORIA, "limit" to "1"), token)
        for (lista in listOf(r.optJSONArray("available_filters"), r.optJSONArray("filters"))) {
            if (lista == null) continue
            for (i in 0 until lista.length()) {
                val f = lista.getJSONObject(i)
                if (f.optString("id") != "state") continue
                val vals = f.optJSONArray("values") ?: continue
                for (j in 0 until vals.length()) {
                    val v = vals.getJSONObject(j)
                    if (v.optString("name").trim().equals(store.departamento.trim(), ignoreCase = true)) {
                        store.estadoId = v.getString("id")
                        return store.estadoId
                    }
                }
            }
        }
        throw RuntimeException("No encontré el departamento '${store.departamento}' en Mercado Libre")
    }

    private fun atributo(o: JSONObject, id: String): String? {
        val a = o.optJSONArray("attributes") ?: return null
        for (i in 0 until a.length()) {
            val x = a.getJSONObject(i)
            if (x.optString("id") == id) {
                val v = x.optString("value_name")
                return if (v.isBlank() || v == "null") null else v
            }
        }
        return null
    }

    private fun aCarro(o: JSONObject): Carro {
        val loc = o.optJSONObject("location")
        val adr = o.optJSONObject("address")
        val ciudadLoc = loc?.optJSONObject("city")?.optString("name") ?: ""
        val ciudad = if (ciudadLoc.isNotBlank()) ciudadLoc else adr?.optString("city_name") ?: ""
        val barrio = loc?.optJSONObject("neighborhood")?.optString("name") ?: ""
        return Carro(
            id = o.getString("id"),
            titulo = o.optString("title"),
            precio = o.optDouble("price", 0.0).toLong(),
            enlace = o.optString("permalink"),
            anio = atributo(o, "VEHICLE_YEAR"),
            km = atributo(o, "KILOMETERS"),
            ciudad = ciudad,
            barrio = barrio,
            tienda = !o.isNull("official_store_id"),
        )
    }

    fun buscarTodo(store: Store): List<Carro> {
        val tok = token(store)
        val estado = estadoId(store, tok)
        val mapa = LinkedHashMap<String, Carro>()
        var p = store.precioMin
        while (p <= store.precioMax) {
            val tope = minOf(p + TRAMO - 1, store.precioMax)
            var offset = 0
            while (true) {
                val r = buscar(
                    mapOf(
                        "category" to CATEGORIA,
                        "state" to estado,
                        "price" to "$p-$tope",
                        "limit" to "50",
                        "offset" to offset.toString(),
                    ),
                    tok,
                )
                val res = r.optJSONArray("results") ?: JSONArray()
                for (i in 0 until res.length()) {
                    val c = aCarro(res.getJSONObject(i))
                    mapa[c.id] = c
                }
                val total = r.optJSONObject("paging")?.optInt("total") ?: 0
                offset += 50
                if (res.length() == 0 || offset >= total || offset >= 1000) break
                Thread.sleep(400)
            }
            p = tope + 1
        }
        return mapa.values.toList()
    }
}
