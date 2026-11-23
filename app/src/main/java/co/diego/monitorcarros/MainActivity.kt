package co.diego.monitorcarros

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.work.WorkManager
import org.json.JSONArray
import org.json.JSONTokener
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Pantalla principal: ver TuCarro y Marketplace DENTRO de la app.
 * Es usted quien navega (como en Chrome); la app solo marca lo nuevo
 * desde su última revisión en las páginas de TuCarro.
 */
class MainActivity : AppCompatActivity() {
    private lateinit var store: Store
    private lateinit var web: WebView
    private lateinit var banner: TextView
    private val tabs = mutableListOf<Button>()
    private var pestana = 0
    private var foto: Map<String, Long> = emptyMap()
    private val handler = Handler(Looper.getMainLooper())

    private val jsLista = """
        (function(){
          var out=[], seen={};
          var as=document.querySelectorAll('a[href*="MCO"]');
          for (var i=0;i<as.length;i++){
            var a=as[i]; var m=a.href.match(/MCO-?(\d{6,})/); if(!m) continue;
            var id='MCO'+m[1]; if(seen[id]) continue;
            var card=a.closest('li')||a.closest('.poly-card')||a.parentElement;
            var t=(a.textContent||'').trim();
            if(t.length<4 && card){ var h=card.querySelector('h2,h3,.poly-component__title'); t=h?h.textContent.trim():''; }
            if(t.length<4) continue;
            var p=card?card.querySelector('.andes-money-amount__fraction'):null;
            seen[id]=1; out.push({id:id,t:t.substring(0,90),p:p?p.textContent.trim():'',u:a.href.split('#')[0]});
          }
          return JSON.stringify(out);
        })()
    """.trimIndent()

    private fun jsResaltar(ids: List<String>): String = """
        (function(ids){
          var set={}; ids.forEach(function(x){set[x]=1;});
          var as=document.querySelectorAll('a[href*="MCO"]');
          for (var i=0;i<as.length;i++){
            var m=as[i].href.match(/MCO-?(\d{6,})/); if(!m||!set['MCO'+m[1]]) continue;
            var c=as[i].closest('li')||as[i].closest('.poly-card')||as[i].parentElement;
            if(!c||c.getAttribute('data-nuevo')) continue;
            c.setAttribute('data-nuevo','1');
            c.style.outline='4px solid #ff9800'; c.style.borderRadius='8px';
            var b=document.createElement('div'); b.textContent='🆕 NUEVO';
            b.style.cssText='background:#ff9800;color:#fff;font-weight:bold;padding:4px 8px;display:inline-block;border-radius:6px;margin:4px';
            c.insertBefore(b,c.firstChild);
          }
        })(${JSONArray(ids)})
    """.trimIndent()

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = Store(this)
        Notifier.crearCanal(this)

        // Al pasar a la v0.5 se apaga el recordatorio cada 15 min (se puede volver a activar en Ajustes)
        if (!store.migrado05) {
            store.intervalo = 0
            WorkManager.getInstance(this).cancelUniqueWork("monitor")
            store.migrado05 = true
        }

        val d = resources.displayMetrics.density
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL

        val fila1 = LinearLayout(this)
        fila1.orientation = LinearLayout.HORIZONTAL
        listOf("TuCarro hoy", "TuCarro todo", "Marketplace").forEachIndexed { i, t ->
            val b = Button(this)
            b.text = t
            b.textSize = 12f
            b.isAllCaps = false
            b.setOnClickListener { abrir(i) }
            fila1.addView(b, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            tabs.add(b)
        }
        root.addView(fila1)

        val fila2 = LinearLayout(this)
        fila2.orientation = LinearLayout.HORIZONTAL
        fun chico(t: String, accion: () -> Unit) {
            val b = Button(this)
            b.text = t
            b.textSize = 12f
            b.isAllCaps = false
            b.setOnClickListener { accion() }
            fila2.addView(b, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
        chico("◀ Atrás") { if (web.canGoBack()) web.goBack() }
        chico("⟳ Actualizar") {
            tomarFoto()
            banner.visibility = View.GONE
            web.reload()
        }
        chico("⚙ Ajustes") { startActivity(Intent(this, AjustesActivity::class.java)) }
        root.addView(fila2)

        banner = TextView(this)
        banner.textSize = 15f
        banner.setTypeface(banner.typeface, Typeface.BOLD)
        banner.setPadding((12 * d).toInt(), (8 * d).toInt(), (12 * d).toInt(), (8 * d).toInt())
        banner.setBackgroundColor(Color.parseColor("#FFF3CD"))
        banner.setTextColor(Color.parseColor("#5C4400"))
        banner.visibility = View.GONE
        root.addView(banner)

        web = WebView(this)
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.settings.loadWithOverviewMode = true
        web.settings.useWideViewPort = true
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true)
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val u = request.url
                if (u.scheme == "http" || u.scheme == "https") return false
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, u))
                } catch (e: Exception) {
                    // esquema que no se puede abrir: se ignora
                }
                return true
            }

            override fun onPageFinished(view: WebView, url: String) {
                if (url.contains("tucarro") || url.contains("mercadolibre")) {
                    handler.removeCallbacksAndMessages(null)
                    handler.postDelayed({ analizar() }, 1500)
                }
            }
        }
        root.addView(web, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (web.canGoBack()) {
                    web.goBack()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })

        if (savedInstanceState == null) abrir(0) else web.restoreState(savedInstanceState)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        web.saveState(outState)
    }

    private fun url(i: Int): String = when (i) {
        0 -> TuCarro.url(store, 1)
        1 -> TuCarro.urlTodos(store)
        else -> TuCarro.marketplace(store)
    }

    private fun clave(): String = "vistosWeb$pestana"

    private fun tomarFoto() {
        foto = HashMap(store.vistos(clave()))
    }

    private fun abrir(i: Int) {
        pestana = i
        tomarFoto()
        tabs.forEachIndexed { j, b -> b.setTypeface(null, if (j == i) Typeface.BOLD else Typeface.NORMAL) }
        banner.visibility = View.GONE
        web.loadUrl(url(i))
    }

    private fun mostrar(t: String) {
        banner.text = t
        banner.visibility = View.VISIBLE
    }

    private fun analizar() {
        web.evaluateJavascript(jsLista) { res ->
            val lista = try {
                JSONArray(JSONTokener(res).nextValue() as String)
            } catch (e: Exception) {
                JSONArray()
            }
            if (lista.length() == 0) return@evaluateJavascript

            val vistos = store.vistos(clave())
            val primera = foto.isEmpty()
            val ahora = System.currentTimeMillis()
            val nuevos = mutableListOf<String>()
            val hora = SimpleDateFormat("dd/MM hh:mm a", Locale("es", "CO")).format(Date())
            for (i in 0 until lista.length()) {
                val o = lista.getJSONObject(i)
                val id = o.getString("id")
                if (!foto.containsKey(id)) {
                    nuevos.add(id)
                    if (!vistos.containsKey(id) && !primera) {
                        val precio = o.optString("p")
                        store.agregarHistorial(
                            "$hora · 🆕 ${o.optString("t")}" +
                                (if (precio.isNotBlank()) " · $$precio" else "") + "\n" + o.optString("u"),
                        )
                    }
                }
                if (!vistos.containsKey(id)) vistos[id] = ahora
            }
            store.guardarVistos(vistos, clave())

            when {
                primera -> mostrar("Primera revisión: guardé ${lista.length()} carros. Desde ahora le marco los nuevos.")
                nuevos.isEmpty() -> mostrar("Sin carros nuevos desde su última revisión (${lista.length()} en pantalla)")
                else -> {
                    mostrar("🆕 ${nuevos.size} nuevo(s) desde su última revisión (marcados en naranja)")
                    web.evaluateJavascript(jsResaltar(nuevos), null)
                }
            }
        }
    }
}
