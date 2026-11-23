package co.diego.monitorcarros

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Se ejecuta SOLO cuando usted toca "🔍 Revisar" en la notificación.
 * Carga una vez TuCarro hoy en el navegador interno, sin abrir la app,
 * y le avisa únicamente si hay carros nuevos (o si no pudo revisar).
 */
class ServicioRevision : Service() {
    private var web: WebView? = null
    private val h = Handler(Looper.getMainLooper())
    private var terminado = true
    private var analizar: Runnable? = null

    override fun onBind(intent: Intent?): IBinder? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val n = Notifier.estado(this, "🔍 Revisando TuCarro…", "Le aviso solo si hay algo nuevo")
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(600, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(600, n)
        }
        NotificationManagerCompat.from(this).cancel(500)
        NotificationManagerCompat.from(this).cancel(601)
        if (!terminado) return START_NOT_STICKY
        terminado = false

        val store = Store(this)
        val foto = HashMap(store.vistos("vistosWeb0"))

        val w = WebView(this)
        w.settings.javaScriptEnabled = true
        w.settings.domStorageEnabled = true
        w.settings.loadWithOverviewMode = true
        w.settings.useWideViewPort = true
        CookieManager.getInstance().setAcceptCookie(true)
        w.measure(
            View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY),
        )
        w.layout(0, 0, 1080, 1920)
        val r = Runnable {
            w.evaluateJavascript(MainActivity.JS_LISTA) { res -> procesar(res, foto, store) }
        }
        analizar = r
        w.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val s = request.url.scheme
                return !(s == "http" || s == "https")
            }

            override fun onPageFinished(view: WebView, url: String) {
                if (url.contains("tucarro") || url.contains("mercadolibre")) {
                    h.removeCallbacks(r)
                    h.postDelayed(r, 2000)
                }
            }
        }
        web = w

        h.postDelayed({
            if (!terminado) {
                Notifier.resultado(
                    this, "No pude revisar TuCarro esta vez",
                    "Puede que la página pida verificar que es una persona. Toque para abrir Jerocar.",
                    false, abrirApp(false),
                )
                terminar()
            }
        }, 30_000)

        w.loadUrl(TuCarro.url(store, 1))
        return START_NOT_STICKY
    }

    private fun abrirApp(mostrarNuevos: Boolean): PendingIntent {
        val i = Intent(this, MainActivity::class.java)
        if (mostrarNuevos) i.putExtra("mostrarNuevos", true) else i.putExtra("revisar", true)
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(
            this, if (mostrarNuevos) 610 else 611, i,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun procesar(res: String?, foto: Map<String, Long>, store: Store) {
        if (terminado) return
        val arr = try {
            JSONArray(JSONTokener(res).nextValue() as String)
        } catch (e: Exception) {
            JSONArray()
        }
        if (arr.length() == 0) return // se espera otra carga o el tiempo límite

        val hora = SimpleDateFormat("hh:mm a", Locale("es", "CO")).format(Date())
        val anterior = store.ultimaRevision
        val vistos = store.vistos("vistosWeb0")
        val ahora = System.currentTimeMillis()
        val nuevos = JSONArray()
        for (i in 0 until arr.length()) {
            val o: JSONObject = arr.getJSONObject(i)
            val id = o.getString("id")
            if (!foto.containsKey(id)) {
                nuevos.put(o)
                if (foto.isNotEmpty()) {
                    val p = o.optString("p")
                    store.agregarHistorial(
                        "${SimpleDateFormat("dd/MM hh:mm a", Locale("es", "CO")).format(Date())} · 🆕 ${o.optString("t")}" +
                            (if (p.isNotBlank()) " · $$p" else "") + "\n" + o.optString("u"),
                    )
                }
            }
            if (!vistos.containsKey(id)) vistos[id] = ahora
        }
        store.guardarVistos(vistos, "vistosWeb0")
        store.ultimaRevision = hora
        val desde = if (anterior.isNotBlank()) " desde las $anterior" else ""

        when {
            foto.isEmpty() -> Notifier.resultado(
                this, "✅ Primera revisión lista",
                "Guardé ${arr.length()} carros publicados hoy. Desde la próxima le aviso solo lo nuevo.",
                false, abrirApp(false),
            )
            nuevos.length() == 0 -> Notifier.resultado(
                this, "Nada nuevo$desde", "Revisado a las $hora · ${arr.length()} carros hoy, todos ya vistos.",
                false, abrirApp(false),
            )
            else -> {
                store.ultimosNuevos = nuevos.toString()
                val nombres = (0 until nuevos.length()).joinToString("\n") {
                    val o = nuevos.getJSONObject(it)
                    val p = o.optString("p")
                    "• ${o.optString("t")}" + (if (p.isNotBlank()) " – $$p" else "")
                }
                Notifier.resultado(
                    this, "🆕 ${nuevos.length()} carro(s) nuevo(s) en TuCarro", nombres, true, abrirApp(true),
                )
            }
        }
        terminar()
    }

    private fun terminar() {
        if (terminado) return
        terminado = true
        h.removeCallbacksAndMessages(null)
        web?.stopLoading()
        web?.destroy()
        web = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        terminado = true
        h.removeCallbacksAndMessages(null)
        web?.destroy()
        web = null
        super.onDestroy()
    }
}
