package co.diego.monitorcarros

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Guarda la configuración, los carros ya vistos y el historial de avisos. */
class Store(ctx: Context) {
    private val p = ctx.applicationContext.getSharedPreferences("monitor", Context.MODE_PRIVATE)

    private fun s(k: String, d: String = ""): String = p.getString(k, d) ?: d
    private fun put(k: String, v: String) {
        p.edit().putString(k, v).apply()
    }

    var clientId: String
        get() = s("clientId")
        set(v) = put("clientId", v)

    var clientSecret: String
        get() = s("clientSecret")
        set(v) = put("clientSecret", v)

    var departamento: String
        get() = s("departamento", "Santander")
        set(v) = put("departamento", v)

    var ciudades: String
        get() = s("ciudades")
        set(v) = put("ciudades", v)

    var precioMin: Long
        get() = p.getLong("precioMin", 30_000_000L)
        set(v) {
            p.edit().putLong("precioMin", v).apply()
        }

    var precioMax: Long
        get() = p.getLong("precioMax", 90_000_000L)
        set(v) {
            p.edit().putLong("precioMax", v).apply()
        }

    var intervalo: Int
        get() = p.getInt("intervalo", 60)
        set(v) {
            p.edit().putInt("intervalo", v).apply()
        }

    var token: String
        get() = s("token")
        set(v) = put("token", v)

    var tokenVence: Long
        get() = p.getLong("tokenVence", 0L)
        set(v) {
            p.edit().putLong("tokenVence", v).apply()
        }

    var estadoId: String
        get() = s("estadoId")
        set(v) = put("estadoId", v)

    var ultimoEstado: String
        get() = s("ultimoEstado")
        set(v) = put("ultimoEstado", v)

    fun vistos(): MutableMap<String, Long> {
        val m = HashMap<String, Long>()
        val o = try {
            JSONObject(s("vistos", "{}"))
        } catch (e: Exception) {
            JSONObject()
        }
        val it = o.keys()
        while (it.hasNext()) {
            val k = it.next()
            m[k] = o.optLong(k)
        }
        return m
    }

    fun guardarVistos(m: Map<String, Long>) {
        val limite = System.currentTimeMillis() - 60L * 24 * 3600 * 1000
        val o = JSONObject()
        for ((k, v) in m) if (v >= limite) o.put(k, v)
        p.edit().putString("vistos", o.toString()).commit()
    }

    var baseHecha: Boolean
        get() = p.getBoolean("baseHecha", false)
        set(v) {
            p.edit().putBoolean("baseHecha", v).commit()
        }

    fun reiniciarVistos() {
        p.edit().remove("vistos").putBoolean("baseHecha", false).commit()
    }

    fun historial(): List<String> {
        val a = try {
            JSONArray(s("historial", "[]"))
        } catch (e: Exception) {
            JSONArray()
        }
        return (0 until a.length()).map { a.getString(it) }
    }

    @Synchronized
    fun agregarHistorial(linea: String) {
        val lista = (listOf(linea) + historial()).take(100)
        p.edit().putString("historial", JSONArray(lista).toString()).commit()
    }

    fun borrarHistorial() = put("historial", "[]")
}
