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
        get() = p.getInt("intervalo", 0)
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

    fun vistos(clave: String = "vistos"): MutableMap<String, Long> {
        val m = HashMap<String, Long>()
        val o = try {
            JSONObject(s(clave, "{}"))
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

    fun guardarVistos(m: Map<String, Long>, clave: String = "vistos") {
        val limite = System.currentTimeMillis() - 60L * 24 * 3600 * 1000
        val o = JSONObject()
        for ((k, v) in m) if (v >= limite) o.put(k, v)
        p.edit().putString(clave, o.toString()).commit()
    }

    /** Guarda los datos de cada carro la primera vez que se ve (con la hora de detección "d"). */
    @Synchronized
    fun registrarFichas(arr: JSONArray) {
        val todas = try {
            JSONObject(s("fichas", "{}"))
        } catch (e: Exception) {
            JSONObject()
        }
        val ahora = System.currentTimeMillis()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val id = o.optString("id")
            if (id.isBlank()) continue
            if (todas.has(id)) {
                // completar datos nuevos (fuente/vendedor) sin cambiar la hora de detección
                val ex = todas.getJSONObject(id)
                if (!ex.has("s") && o.has("s")) ex.put("s", o.optString("s"))
                if (!ex.has("f") && o.has("f")) ex.put("f", o.optString("f"))
                continue
            }
            val f = JSONObject(o.toString())
            f.put("d", ahora)
            todas.put(id, f)
        }
        // conservar las 400 más recientes y borrar las de más de 30 días
        val limite = ahora - 30L * 24 * 3600 * 1000
        val lista = mutableListOf<JSONObject>()
        val it = todas.keys()
        while (it.hasNext()) lista.add(todas.getJSONObject(it.next()))
        lista.removeAll { it.optLong("d") < limite }
        lista.sortByDescending { it.optLong("d") }
        val final = JSONObject()
        lista.take(400).forEach { final.put(it.optString("id"), it) }
        p.edit().putString("fichas", final.toString()).commit()
    }

    fun existeFicha(id: String): Boolean = try {
        JSONObject(s("fichas", "{}")).has(id)
    } catch (e: Exception) {
        false
    }

    @Synchronized
    fun quitarFicha(id: String) {
        val todas = try {
            JSONObject(s("fichas", "{}"))
        } catch (e: Exception) {
            return
        }
        todas.remove(id)
        p.edit().putString("fichas", todas.toString()).commit()
    }

    fun fichas(): List<JSONObject> {
        val todas = try {
            JSONObject(s("fichas", "{}"))
        } catch (e: Exception) {
            JSONObject()
        }
        val lista = mutableListOf<JSONObject>()
        val it = todas.keys()
        while (it.hasNext()) lista.add(todas.getJSONObject(it.next()))
        return lista
    }

    var plantilla: String
        get() = s("plantilla", Mensajes.PLANTILLA).let { if (it == Mensajes.PLANTILLA_VIEJA) Mensajes.PLANTILLA else it }
        set(v) = put("plantilla", v)

    var ultimosNuevos: String
        get() = s("ultimosNuevos", "[]")
        set(v) = put("ultimosNuevos", v)

    var ultimaRevision: String
        get() = s("ultimaRevision")
        set(v) = put("ultimaRevision", v)

    var horaInicio: Int
        get() = p.getInt("horaInicio", 6)
        set(v) {
            p.edit().putInt("horaInicio", v).apply()
        }

    var horaFin: Int
        get() = p.getInt("horaFin", 21)
        set(v) {
            p.edit().putInt("horaFin", v).apply()
        }

    var migrado06: Boolean
        get() = p.getBoolean("migrado06", false)
        set(v) {
            p.edit().putBoolean("migrado06", v).commit()
        }

    var migrado05: Boolean
        get() = p.getBoolean("migrado05", false)
        set(v) {
            p.edit().putBoolean("migrado05", v).commit()
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
