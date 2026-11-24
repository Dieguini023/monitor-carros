package co.diego.monitorcarros

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.text.InputType
import android.text.util.Linkify
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class AjustesActivity : AppCompatActivity() {
    private lateinit var store: Store
    private lateinit var col: LinearLayout
    private lateinit var estado: TextView
    private lateinit var permisos: TextView
    private lateinit var historial: TextView
    private var d = 1f

    private fun texto(t: String, tam: Float = 15f, negrita: Boolean = false): TextView {
        val titulo = tam >= 17f
        val v = Estilo.texto(this, t, tam, if (titulo || negrita) Estilo.TEXTO else Estilo.GRIS, negrita)
        v.setPadding(0, ((if (titulo) 20 else 10) * d).toInt(), 0, (6 * d).toInt())
        col.addView(v)
        return v
    }

    private fun campo(etiqueta: String, valor: String, numero: Boolean = false): EditText {
        texto(etiqueta, 13f)
        val e = Estilo.campo(this, valor, numero)
        col.addView(e, LinearLayout.LayoutParams(-1, -2))
        return e
    }

    private fun boton(t: String, accion: () -> Unit): Button {
        val principal = t == "Guardar"
        val b = Estilo.boton(this, t, principal, accion)
        val p = LinearLayout.LayoutParams(-1, -2)
        p.setMargins(0, (6 * d).toInt(), 0, (6 * d).toInt())
        col.addView(b, p)
        return b
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = Store(this)
        Notifier.crearCanal(this)
        d = resources.displayMetrics.density
        window.statusBarColor = Estilo.FONDO
        window.navigationBarColor = Estilo.FONDO
        col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setBackgroundColor(Estilo.FONDO)
        val pad = (16 * d).toInt()
        col.setPadding(pad, pad, pad, pad)

        val atras = texto("‹  Ajustes", 24f, true)
        atras.setOnClickListener { finish() }
        estado = texto("", 14f)
        permisos = texto("", 13f)

        texto("Zona y precio", 17f, true)
        val etId = campo("App ID de Mercado Libre (opcional: vacío = leer TuCarro web)", store.clientId)
        val etSecret = campo("Secret Key (opcional)", store.clientSecret)
        val etDepto = campo("Departamento", store.departamento)
        val etCiudades = campo("Ciudades (separadas por coma; la primera se usa para Marketplace)", store.ciudades)
        val etMin = campo("Precio mínimo", store.precioMin.toString(), true)
        val etMax = campo("Precio máximo", store.precioMax.toString(), true)
        val etIntervalo = campo("Recordatorio / revisión automática cada (minutos; 0 = apagado)", store.intervalo.toString(), true)

        val etDesde = campo("Recordatorio desde la hora (0-23)", store.horaInicio.toString(), true)
        val etHasta = campo("Recordatorio hasta la hora (1-24)", store.horaFin.toString(), true)

        boton("Guardar") {
            store.horaInicio = (etDesde.text.toString().toIntOrNull() ?: 6).coerceIn(0, 23)
            store.horaFin = (etHasta.text.toString().toIntOrNull() ?: 21).coerceIn(1, 24)
            val nuevoDepto = etDepto.text.toString().trim().ifBlank { "Santander" }
            val nuevoMin = etMin.text.toString().toLongOrNull() ?: 30_000_000L
            val nuevoMax = etMax.text.toString().toLongOrNull() ?: 90_000_000L
            val cambioBusqueda = !nuevoDepto.equals(store.departamento, ignoreCase = true) ||
                nuevoMin != store.precioMin || nuevoMax != store.precioMax
            if (!nuevoDepto.equals(store.departamento, ignoreCase = true)) store.estadoId = ""
            store.clientId = etId.text.toString().trim()
            store.clientSecret = etSecret.text.toString().trim()
            store.departamento = nuevoDepto
            store.ciudades = etCiudades.text.toString().trim()
            store.precioMin = nuevoMin
            store.precioMax = nuevoMax
            val iv = etIntervalo.text.toString().toIntOrNull() ?: 0
            store.intervalo = if (iv <= 0) 0 else iv.coerceAtLeast(15)
            store.token = ""
            if (cambioBusqueda) store.reiniciarVistos()
            programar()
            Toast.makeText(this, if (store.intervalo == 0) "Guardado. Recordatorio apagado." else "Guardado. Recordatorio cada ${store.intervalo} min", Toast.LENGTH_LONG).show()
            refrescar()
        }

        boton("Probar recordatorio ahora") {
            estado.text = "Revisando… (puede tardar un minuto)"
            Thread {
                val r = try {
                    Revision.ejecutar(applicationContext)
                } catch (e: Exception) {
                    val m = "Error: ${e.message}"
                    store.ultimoEstado = m
                    m
                }
                runOnUiThread {
                    Toast.makeText(this, r, Toast.LENGTH_LONG).show()
                    refrescar()
                }
            }.start()
        }

        boton("Enviar diagnóstico a Claude") {
            val t = TuCarro.diagnostico(applicationContext)
            val i = Intent(Intent.ACTION_SEND)
            i.type = "text/plain"
            i.putExtra(Intent.EXTRA_TEXT, "Diagnóstico Jerocar\n" + store.ultimoEstado + "\n" + t)
            startActivity(Intent.createChooser(i, "Enviar diagnóstico"))
        }

        texto("Permisos", 17f, true)
        boton("Permitir notificaciones") {
            if (Build.VERSION.SDK_INT >= 33) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
            } else {
                Toast.makeText(this, "Ya están permitidas en esta versión de Android", Toast.LENGTH_SHORT).show()
            }
        }
        boton("Que Android no la apague (batería)") {
            try {
                startActivity(
                    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")),
                )
            } catch (e: Exception) {
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }
        }

        texto("Historial de avisos", 17f, true)
        boton("Borrar historial") {
            store.borrarHistorial()
            refrescar()
        }
        historial = texto("", 13f)
        historial.autoLinkMask = Linkify.WEB_URLS

        val scroll = ScrollView(this)
        scroll.setBackgroundColor(Estilo.FONDO)
        scroll.isFillViewport = true
        scroll.addView(col)
        setContentView(scroll)
    }

    override fun onResume() {
        super.onResume()
        refrescar()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        refrescar()
    }

    private fun programar() {
        Programador.programar(this)
    }

    private fun si(b: Boolean) = if (b) "✅" else "❌"

    private fun refrescar() {
        estado.text = "Última revisión: " + store.ultimoEstado.ifBlank { "todavía ninguna" }
        val notif = Build.VERSION.SDK_INT < 33 ||
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        val bateria = (getSystemService(POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(packageName)
        permisos.text = "Notificaciones ${si(notif)}   Batería ${si(bateria)}"
        historial.text = store.historial().joinToString("\n\n").ifBlank { "Sin avisos todavía." }
    }
}
