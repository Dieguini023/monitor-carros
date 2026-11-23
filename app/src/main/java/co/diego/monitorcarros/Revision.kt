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
        val carros = MercadoLibre.buscarTodo(store)
        val vistos = store.vistos()
        val ahora = System.currentTimeMillis()

        if (vistos.isEmpty()) {
            carros.forEach { vistos[it.id] = ahora }
            store.guardarVistos(vistos)
            val msg = "Base inicial: ${carros.size} carros en ${store.departamento} entre " +
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
                append(pesos(c.precio))
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
        val msg = "${carros.size} carros activos, ${filtrados.size} nuevos"
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
