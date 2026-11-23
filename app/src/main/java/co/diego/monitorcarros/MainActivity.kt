package co.diego.monitorcarros

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.work.WorkManager
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Pantalla principal. Usted navega TuCarro y Marketplace dentro de la app.
 * Con "🔍 Revisar" (botón o notificación) la app carga TuCarro hoy en su
 * navegador interno y le muestra solo una lista de lo nuevo desde su última revisión.
 */
class MainActivity : AppCompatActivity() {
    companion object {
        val JS_LISTA = """
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
                var img=card?card.querySelector('img'):null; var src='';
                if(img){ src=img.getAttribute('data-src')||img.currentSrc||img.src||''; if(src.indexOf('data:')===0){ src=img.getAttribute('data-src')||''; } }
                var at=card?Array.prototype.map.call(card.querySelectorAll('[class*="attributes"] li, [class*="attributes-list__item"], [class*="attributes_list__item"]'),function(x){return x.textContent.trim();}).filter(function(x){return x.length>0&&x.length<30;}):[];
                var loc=card?card.querySelector('[class*="location"]'):null;
                seen[id]=1;
                out.push({id:id,t:t.substring(0,90),p:p?p.textContent.trim():'',u:a.href.split('#')[0],
                          img:src,a:at.slice(0,3).join(' · '),l:loc?loc.textContent.trim():''});
              }
              return JSON.stringify(out);
            })()
    """.trimIndent()
    }

    private lateinit var store: Store
    private lateinit var web: WebView
    private lateinit var banner: TextView
    private lateinit var panel: ScrollView
    private lateinit var lista: LinearLayout
    private val tabs = mutableListOf<Button>()
    private var pestana = 0
    private var foto: Map<String, Long> = emptyMap()
    private var modoRevision = false
    private val handler = Handler(Looper.getMainLooper())
    private val reloj = Handler(Looper.getMainLooper())
    private var d = 1f



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

    private fun boton(t: String, fila: LinearLayout, accion: () -> Unit): Button {
        val b = Button(this)
        b.text = t
        b.textSize = 12f
        b.isAllCaps = false
        b.setOnClickListener { accion() }
        fila.addView(b, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        return b
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = Store(this)
        Notifier.crearCanal(this)

        if (!store.migrado05) {
            store.intervalo = 0
            WorkManager.getInstance(this).cancelUniqueWork("monitor")
            store.migrado05 = true
        }
        if (!store.migrado06) {
            store.intervalo = 30
            Programador.programar(this)
            store.migrado06 = true
        }

        d = resources.displayMetrics.density
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL

        val fila1 = LinearLayout(this)
        fila1.orientation = LinearLayout.HORIZONTAL
        listOf("TuCarro hoy", "TuCarro todo", "Marketplace").forEachIndexed { i, t ->
            tabs.add(boton(t, fila1) { abrir(i) })
        }
        root.addView(fila1)

        val fila2 = LinearLayout(this)
        fila2.orientation = LinearLayout.HORIZONTAL
        boton("◀ Atrás", fila2) {
            if (panel.visibility == View.VISIBLE) {
                panel.visibility = View.GONE
            } else if (web.canGoBack()) {
                web.goBack()
            }
        }
        val bRevisar = boton("🔍 Revisar", fila2) { revisar() }
        bRevisar.setTypeface(null, Typeface.BOLD)
        boton("⚙ Ajustes", fila2) { startActivity(Intent(this, AjustesActivity::class.java)) }
        root.addView(fila2)

        banner = TextView(this)
        banner.textSize = 15f
        banner.setTypeface(banner.typeface, Typeface.BOLD)
        banner.setPadding((12 * d).toInt(), (8 * d).toInt(), (12 * d).toInt(), (8 * d).toInt())
        banner.setBackgroundColor(Color.parseColor("#FFF3CD"))
        banner.setTextColor(Color.parseColor("#5C4400"))
        banner.visibility = View.GONE
        root.addView(banner)

        val marco = FrameLayout(this)

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
                    handler.postDelayed({ analizar() }, 1800)
                }
            }
        }
        marco.addView(web, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        panel = ScrollView(this)
        panel.setBackgroundColor(Color.WHITE)
        lista = LinearLayout(this)
        lista.orientation = LinearLayout.VERTICAL
        val pad = (12 * d).toInt()
        lista.setPadding(pad, pad, pad, pad)
        panel.addView(lista)
        panel.visibility = View.GONE
        marco.addView(panel, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        root.addView(marco, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    panel.visibility == View.VISIBLE -> panel.visibility = View.GONE
                    web.canGoBack() -> web.goBack()
                    else -> {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                    }
                }
            }
        })

        if (savedInstanceState == null) {
            when {
                intent.getBooleanExtra("mostrarNuevos", false) -> mostrarGuardados()
                intent.getBooleanExtra("revisar", false) -> revisar()
                else -> abrir(intent.getIntExtra("pestana", 0))
            }
        } else {
            web.restoreState(savedInstanceState)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        web.saveState(outState)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra("mostrarNuevos", false)) {
            mostrarGuardados()
        } else if (intent.getBooleanExtra("revisar", false)) {
            revisar()
        } else {
            val p = intent.getIntExtra("pestana", -1)
            if (p >= 0) abrir(p)
        }
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

    private fun marcarPestana(i: Int) {
        tabs.forEachIndexed { j, b -> b.setTypeface(null, if (j == i) Typeface.BOLD else Typeface.NORMAL) }
    }

    private fun abrir(i: Int) {
        modoRevision = false
        pestana = i
        tomarFoto()
        marcarPestana(i)
        panel.visibility = View.GONE
        banner.visibility = View.GONE
        web.loadUrl(url(i))
    }

    private fun revisar() {
        modoRevision = true
        pestana = 0
        tomarFoto()
        marcarPestana(0)
        lista.removeAllViews()
        lista.addView(texto("🔍 Revisando TuCarro (Santander, hoy)…", 17f, true))
        panel.visibility = View.VISIBLE
        banner.visibility = View.GONE
        web.loadUrl(url(0))
        // Si en 25 s no se pudo leer nada, se avisa
        reloj.removeCallbacksAndMessages(null)
        reloj.postDelayed({
            if (modoRevision) {
                modoRevision = false
                lista.removeAllViews()
                lista.addView(texto("No pude leer TuCarro esta vez.", 17f, true))
                lista.addView(texto("Puede que la página pida verificar que es una persona. Toque \"Ver página\" y revise.", 14f, false))
                lista.addView(botonLista("Ver página") { panel.visibility = View.GONE })
            }
        }, 25_000)
    }

    private fun mostrarGuardados() {
        modoRevision = false
        pestana = 0
        marcarPestana(0)
        val arr = try {
            JSONArray(store.ultimosNuevos)
        } catch (e: Exception) {
            JSONArray()
        }
        lista.removeAllViews()
        lista.addView(texto("🆕 ${arr.length()} carro(s) nuevo(s) · revisado a las ${store.ultimaRevision}", 18f, true))
        lista.addView(texto("Toque uno para ver la publicación completa.", 13f, false))
        for (i in 0 until arr.length()) lista.addView(tarjeta(arr.getJSONObject(i)))
        lista.addView(botonLista("Ver página completa de TuCarro hoy") { abrir(0) })
        banner.visibility = View.GONE
        panel.visibility = View.VISIBLE
    }

    private fun texto(t: String, tam: Float, negrita: Boolean): TextView {
        val v = TextView(this)
        v.text = t
        v.textSize = tam
        v.setTextColor(Color.parseColor("#222222"))
        if (negrita) v.setTypeface(null, Typeface.BOLD)
        v.setPadding(0, (6 * d).toInt(), 0, (6 * d).toInt())
        return v
    }

    private fun botonLista(t: String, accion: () -> Unit): Button {
        val b = Button(this)
        b.text = t
        b.isAllCaps = false
        b.setOnClickListener { accion() }
        return b
    }

    private fun tarjeta(o: JSONObject): View {
        val fila = LinearLayout(this)
        fila.orientation = LinearLayout.HORIZONTAL
        fila.gravity = Gravity.CENTER_VERTICAL
        fila.setPadding(0, (8 * d).toInt(), 0, (8 * d).toInt())
        fila.setBackgroundColor(Color.parseColor("#FFF8EC"))

        val img = ImageView(this)
        img.scaleType = ImageView.ScaleType.CENTER_CROP
        img.setBackgroundColor(Color.parseColor("#EEEEEE"))
        fila.addView(img, LinearLayout.LayoutParams((110 * d).toInt(), (82 * d).toInt()))
        val src = o.optString("img")
        if (src.startsWith("http")) {
            Thread {
                try {
                    val bmp = URL(src).openStream().use { BitmapFactory.decodeStream(it) }
                    runOnUiThread { img.setImageBitmap(bmp) }
                } catch (e: Exception) {
                    // sin foto
                }
            }.start()
        }

        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding((10 * d).toInt(), 0, 0, 0)
        val titulo = TextView(this)
        titulo.text = o.optString("t")
        titulo.textSize = 15f
        titulo.setTypeface(null, Typeface.BOLD)
        titulo.setTextColor(Color.parseColor("#222222"))
        col.addView(titulo)
        val precio = o.optString("p")
        if (precio.isNotBlank()) {
            val p = TextView(this)
            p.text = "$ $precio"
            p.textSize = 16f
            p.setTextColor(Color.parseColor("#0A7A2F"))
            p.setTypeface(null, Typeface.BOLD)
            col.addView(p)
        }
        val extra = listOf(o.optString("a"), o.optString("l")).filter { it.isNotBlank() }.joinToString("\n")
        if (extra.isNotBlank()) {
            val e = TextView(this)
            e.text = extra
            e.textSize = 13f
            e.setTextColor(Color.parseColor("#555555"))
            col.addView(e)
        }
        fila.addView(col, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        val u = o.optString("u")
        fila.setOnClickListener {
            panel.visibility = View.GONE
            modoRevision = false
            web.loadUrl(u)
        }
        val cont = LinearLayout(this)
        cont.orientation = LinearLayout.VERTICAL
        cont.addView(fila)
        val sep = View(this)
        sep.setBackgroundColor(Color.parseColor("#DDDDDD"))
        cont.addView(sep, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (1 * d).toInt().coerceAtLeast(1)))
        return cont
    }

    private fun mostrarBanner(t: String) {
        banner.text = t
        banner.visibility = View.VISIBLE
    }

    private fun analizar() {
        web.evaluateJavascript(JS_LISTA) { res ->
            val arr = try {
                JSONArray(JSONTokener(res).nextValue() as String)
            } catch (e: Exception) {
                JSONArray()
            }
            if (arr.length() == 0) return@evaluateJavascript

            val hora = SimpleDateFormat("hh:mm a", Locale("es", "CO")).format(Date())
            val anterior = store.ultimaRevision
            val vistos = store.vistos(clave())
            val primera = foto.isEmpty()
            val ahora = System.currentTimeMillis()
            val nuevos = mutableListOf<JSONObject>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val id = o.getString("id")
                if (!foto.containsKey(id)) {
                    nuevos.add(o)
                    if (!vistos.containsKey(id) && !primera) {
                        val p = o.optString("p")
                        store.agregarHistorial(
                            "${SimpleDateFormat("dd/MM hh:mm a", Locale("es", "CO")).format(Date())} · 🆕 ${o.optString("t")}" +
                                (if (p.isNotBlank()) " · $$p" else "") + "\n" + o.optString("u"),
                        )
                    }
                }
                if (!vistos.containsKey(id)) vistos[id] = ahora
            }
            store.guardarVistos(vistos, clave())
            if (pestana == 0) store.ultimaRevision = hora

            val desde = if (anterior.isNotBlank()) " desde las $anterior" else ""
            if (modoRevision) {
                modoRevision = false
                lista.removeAllViews()
                when {
                    primera -> {
                        lista.addView(texto("✅ Primera revisión", 18f, true))
                        lista.addView(texto("Guardé ${arr.length()} carros publicados hoy. Desde la próxima vez le muestro aquí solo los nuevos.", 15f, false))
                    }
                    nuevos.isEmpty() -> {
                        lista.addView(texto("Nada nuevo$desde", 18f, true))
                        lista.addView(texto("Hay ${arr.length()} carros publicados hoy en Santander, todos ya vistos.", 15f, false))
                    }
                    else -> {
                        lista.addView(texto("🆕 ${nuevos.size} carro(s) nuevo(s)$desde", 18f, true))
                        lista.addView(texto("Toque uno para ver la publicación completa.", 13f, false))
                        nuevos.forEach { lista.addView(tarjeta(it)) }
                    }
                }
                lista.addView(botonLista("Ver página completa de TuCarro hoy") { panel.visibility = View.GONE })
                panel.visibility = View.VISIBLE
                if (nuevos.isNotEmpty() && !primera) web.evaluateJavascript(jsResaltar(nuevos.map { it.getString("id") }), null)
            } else {
                when {
                    primera -> mostrarBanner("Primera revisión: guardé ${arr.length()} carros. Desde ahora le marco los nuevos.")
                    nuevos.isEmpty() -> mostrarBanner("Sin carros nuevos$desde (${arr.length()} en pantalla)")
                    else -> {
                        mostrarBanner("🆕 ${nuevos.size} nuevo(s)$desde (marcados en naranja)")
                        web.evaluateJavascript(jsResaltar(nuevos.map { it.getString("id") }), null)
                    }
                }
            }
        }
    }
}
