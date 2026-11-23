package co.diego.monitorcarros

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Una revisión completa: busca, compara con lo ya visto y avisa lo nuevo. */
object Revision {
    private const val MAX_AVISOS = 30

    fun pesos(v: Long): String = "$" + String.format(Locale.US, "%,d", v).replace(",", ".")

    @Synchronized
    fun ejecutar(ctx: Context): String {
        val store = Store(ctx)
        val hora = SimpleDateFormat("dd/MM hh:mm a", Locale("es", "CO")).format(Date())
        val usaApi = store.clientId.isNotBlank() && store.clientSecret.isNotBlank()
        if (!usaApi) {
            val h = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
            val msg = if (h in 6..21) {
                Notifier.recordatorio(ctx, TuCarro.url(store, 1), TuCarro.marketplace(store))
                "Recordatorio enviado (cada ${store.intervalo} min, de 6 a.m. a 10 p.m.)"
            } else {
                "Fuera de horario (6 a.m. – 10 p.m.): sin recordatorio"
            }
            store.ultimoEstado = "$hora · $msg"
            return msg
        }
        val carros = MercadoLibre.buscarTodo(store)
        val fuente = if (usaApi) "Mercado Libre (API)" else "TuCarro web"
        val vistos = store.vistos()
        val ahora = System.currentTimeMillis()

        if (!store.baseHecha) {
            if (carros.isEmpty() && !usaApi) {
                val msg = "Leí la página de TuCarro pero encontré 0 carros publicados hoy. " +
                    "Si persiste, toque ENVIAR DIAGNÓSTICO A CLAUDE."
                store.ultimoEstado = "$hora · $msg"
                return msg
            }
            carros.forEach { vistos[it.id] = ahora }
            store.guardarVistos(vistos)
            store.baseHecha = true
            val msg = "Base inicial ($fuente): ${carros.size} carros publicados hoy en ${store.departamento} entre " +
                "${pesos(store.precioMin)} y ${pesos(store.precioMax)}. Desde ahora aviso solo los nuevos."
            Notifier.mostrar(ctx, 1, "✅ Monitor activo", msg, null)
            store.ultimoEstado = "$hora · $msg"
            return msg
        }

        val ciudades = store.ciudades.split(",").map { it.trim().lowercase() }.filter { it.isNotEmpty() }
        val nuevos = carros.filter { it.id !in vistos }
        nuevos.forEach { vistos[it.id] = ahora }
        val filtrados = nuevos.filter { c ->
            ciudades.isEmpty() || ciudades.any { c.ciudad.lowercase().contains(it) }
        }

        for (c in filtrados.take(MAX_AVISOS)) {
            val texto = buildString {
                append(if (c.precio > 0) pesos(c.precio) else "Precio no visible")
                c.anio?.let { append(" · Modelo $it") }
                c.km?.let { append(" · $it") }
                val lugar = listOf(c.barrio, c.ciudad).filter { it.isNotBlank() }.joinToString(", ")
                if (lugar.isNotEmpty()) append("\n📍 $lugar")
                if (c.tienda) append("\n🏪 Concesionario")
            }
            Notifier.mostrar(ctx, c.id.hashCode(), "🚗 ${c.titulo}", texto, c.enlace)
            store.agregarHistorial("$hora · ${c.titulo}\n${texto.replace("\n", " · ")}\n${c.enlace}")
        }
        if (filtrados.size > MAX_AVISOS) {
            Notifier.mostrar(
                ctx, 2, "…y ${filtrados.size - MAX_AVISOS} carros nuevos más",
                "Ábralos desde Mercado Libre / TuCarro", null,
            )
        }
        store.guardarVistos(vistos)
        val msg = "$fuente: ${carros.size} carros hoy, ${filtrados.size} nuevos"
        store.ultimoEstado = "$hora · $msg"
        return msg
    }
}

/** Tarea en segundo plano que Android ejecuta cada X minutos. */
class MonitorWorker(ctx: Context, params: WorkerParameters) : Worker(ctx, params) {
    override fun doWork(): Result {
        try {
            Revision.ejecutar(applicationContext)
        } catch (e: Exception) {
            val hora = SimpleDateFormat("dd/MM hh:mm a", Locale("es", "CO")).format(Date())
            Store(applicationContext).ultimoEstado = "$hora · Error: ${e.message}"
        }
        return Result.success()
    }
}
