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
import android.view.ViewOutlineProvider
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.work.WorkManager
import co.diego.monitorcarros.Estilo.dp
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Jerocar – pantalla principal.
 * Inicio (tablero) · Detectados (lista ordenable) · TuCarro y Marketplace dentro de la app.
 * Las revisiones siempre salen de un toque del usuario.
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
                var sel=card?card.querySelector('[class*="seller"], [class*="official-store"], [class*="store-name"]'):null;
                var st=sel?sel.textContent.replace(/^\s*(por|by)\s+/i,'').trim():'';
                if(st.length>40) st=st.substring(0,40);
                var f=location.hostname.indexOf('tucarro')>=0?'TuCarro':'Mercado Libre';
                seen[id]=1;
                out.push({id:id,t:t.substring(0,90),p:p?p.textContent.trim():'',u:a.href.split('#')[0],
                          img:src,a:at.slice(0,3).join(' · '),l:loc?loc.textContent.trim():'',s:st,f:f});
              }
              return JSON.stringify(out);
            })()
        """.trimIndent()

        private const val V_INICIO = 0
        private const val V_LISTA = 1
        private const val V_WEB = 2
    }

    private lateinit var store: Store
    private lateinit var web: WebView
    private lateinit var banner: TextView
    private lateinit var capaInicio: ScrollView
    private lateinit var inicio: LinearLayout
    private lateinit var capaLista: LinearLayout
    private lateinit var tituloLista: TextView
    private lateinit var listaScroll: ScrollView
    private lateinit var lista: LinearLayout
    private lateinit var capaWeb: LinearLayout
    private lateinit var tituloWeb: TextView
    private lateinit var segmento: LinearLayout
    private val navItems = mutableListOf<LinearLayout>()

    private var vista = V_INICIO
    private var pestana = 0
    private var foto: Map<String, Long> = emptyMap()
    private var modoRevision = false
    private val handler = Handler(Looper.getMainLooper())
    private val reloj = Handler(Looper.getMainLooper())

    private fun jsResaltar(ids: List<String>): String = """
        (function(ids){
          var set={}; ids.forEach(function(x){set[x]=1;});
          var as=document.querySelectorAll('a[href*="MCO"]');
          for (var i=0;i<as.length;i++){
            var m=as[i].href.match(/MCO-?(\d{6,})/); if(!m||!set['MCO'+m[1]]) continue;
            var c=as[i].closest('li')||as[i].closest('.poly-card')||as[i].parentElement;
            if(!c||c.getAttribute('data-nuevo')) continue;
            c.setAttribute('data-nuevo','1');
            c.style.outline='4px solid #ff8a00'; c.style.borderRadius='10px';
            var b=document.createElement('div'); b.textContent='🆕 NUEVO';
            b.style.cssText='background:#ff8a00;color:#fff;font-weight:bold;padding:4px 10px;display:inline-block;border-radius:8px;margin:4px';
            c.insertBefore(b,c.firstChild);
          }
        })(${JSONArray(ids)})
    """.trimIndent()

    private fun d(v: Float) = dp(this, v)

    // ------------------------------------------------------------------ UI

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = Store(this)
        Notifier.crearCanal(this)
        window.statusBarColor = Estilo.FONDO
        window.navigationBarColor = Estilo.TARJETA

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

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(Estilo.FONDO)

        val marco = FrameLayout(this)
        root.addView(marco, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(barraInferior())

        // ---- capa web
        capaWeb = LinearLayout(this)
        capaWeb.orientation = LinearLayout.VERTICAL
        capaWeb.setBackgroundColor(Estilo.FONDO)
        capaWeb.addView(barraWeb())
        banner = Estilo.texto(this, "", 14f, Color.parseColor("#0B1020"), true)
        banner.setPadding(d(14f), d(10f), d(14f), d(10f))
        banner.background = Estilo.degradado(Estilo.NARANJA, Color.parseColor("#FFC14D"), 0f, this)
        banner.visibility = View.GONE
        capaWeb.addView(banner)
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
                    // se ignora
                }
                return true
            }

            override fun onPageFinished(view: WebView, url: String) {
                if (url.contains("carros.tucarro.com.co") || url.contains("carros.mercadolibre.com.co")) {
                    handler.removeCallbacksAndMessages(null)
                    handler.postDelayed({ analizar() }, 1800)
                }
            }
        }
        capaWeb.addView(web, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        marco.addView(capaWeb, FrameLayout.LayoutParams(-1, -1))

        // ---- capa lista
        capaLista = LinearLayout(this)
        capaLista.orientation = LinearLayout.VERTICAL
        capaLista.setBackgroundColor(Estilo.FONDO)
        val cab = LinearLayout(this)
        cab.orientation = LinearLayout.HORIZONTAL
        cab.gravity = Gravity.CENTER_VERTICAL
        cab.setPadding(d(16f), d(14f), d(16f), d(6f))
        tituloLista = Estilo.texto(this, "", 20f, Estilo.TEXTO, true)
        cab.addView(tituloLista, LinearLayout.LayoutParams(0, -2, 1f))
        capaLista.addView(cab)
        listaScroll = ScrollView(this)
        lista = LinearLayout(this)
        lista.orientation = LinearLayout.VERTICAL
        lista.setPadding(d(14f), d(4f), d(14f), d(20f))
        listaScroll.addView(lista)
        capaLista.addView(listaScroll, LinearLayout.LayoutParams(-1, 0, 1f))
        marco.addView(capaLista, FrameLayout.LayoutParams(-1, -1))

        // ---- capa inicio
        capaInicio = ScrollView(this)
        capaInicio.setBackgroundColor(Estilo.FONDO)
        inicio = LinearLayout(this)
        inicio.orientation = LinearLayout.VERTICAL
        inicio.setPadding(d(16f), d(16f), d(16f), d(24f))
        capaInicio.addView(inicio)
        marco.addView(capaInicio, FrameLayout.LayoutParams(-1, -1))

        setContentView(root)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    vista == V_WEB && web.canGoBack() -> web.goBack()
                    vista != V_INICIO -> mostrarInicio()
                    else -> {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                    }
                }
            }
        })

        when {
            intent.getBooleanExtra("mostrarNuevos", false) -> mostrarGuardados()
            intent.getBooleanExtra("revisar", false) -> revisar()
            intent.hasExtra("pestana") && intent.getIntExtra("pestana", -1) >= 0 -> abrir(intent.getIntExtra("pestana", 0))
            else -> mostrarInicio()
        }
    }

    override fun onResume() {
        super.onResume()
        if (vista == V_INICIO) construirInicio()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        when {
            intent.getBooleanExtra("mostrarNuevos", false) -> mostrarGuardados()
            intent.getBooleanExtra("revisar", false) -> revisar()
            intent.getIntExtra("pestana", -1) >= 0 -> abrir(intent.getIntExtra("pestana", 0))
        }
    }

    private fun ponerVista(v: Int, nav: Int) {
        vista = v
        capaInicio.visibility = if (v == V_INICIO) View.VISIBLE else View.GONE
        capaLista.visibility = if (v == V_LISTA) View.VISIBLE else View.GONE
        // la web queda INVISIBLE (no GONE) para que pueda cargar mientras se revisa
        capaWeb.visibility = if (v == V_WEB) View.VISIBLE else View.INVISIBLE
        navItems.forEachIndexed { i, item ->
            val activo = i == nav
            val ico = item.getChildAt(0) as TextView
            val lab = item.getChildAt(1) as TextView
            lab.setTextColor(if (activo) Estilo.CIAN else Estilo.GRIS)
            lab.setTypeface(Typeface.DEFAULT, if (activo) Typeface.BOLD else Typeface.NORMAL)
            ico.alpha = if (activo) 1f else 0.55f
        }
    }

    private fun barraInferior(): View {
        val barra = LinearLayout(this)
        barra.orientation = LinearLayout.HORIZONTAL
        barra.setBackgroundColor(Estilo.TARJETA)
        barra.setPadding(0, d(6f), 0, d(6f))
        val items = listOf("🏠" to "Inicio", "📋" to "Detectados", "🚗" to "TuCarro", "🛒" to "Marketplace")
        items.forEachIndexed { i, (ico, lab) ->
            val item = LinearLayout(this)
            item.orientation = LinearLayout.VERTICAL
            item.gravity = Gravity.CENTER
            item.addView(Estilo.texto(this, ico, 20f).also { it.gravity = Gravity.CENTER })
            item.addView(Estilo.texto(this, lab, 11f, Estilo.GRIS).also { it.gravity = Gravity.CENTER })
            item.setOnClickListener {
                when (i) {
                    0 -> mostrarInicio()
                    1 -> mostrarDetectados(0)
                    2 -> abrir(0)
                    else -> abrir(2)
                }
            }
            barra.addView(item, LinearLayout.LayoutParams(0, -2, 1f))
            navItems.add(item)
        }
        return barra
    }

    private fun barraWeb(): View {
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(d(12f), d(10f), d(12f), d(8f))
        val fila = LinearLayout(this)
        fila.orientation = LinearLayout.HORIZONTAL
        fila.gravity = Gravity.CENTER_VERTICAL
        val atras = Estilo.texto(this, "‹", 30f, Estilo.TEXTO, true)
        atras.setPadding(d(6f), 0, d(14f), 0)
        atras.setOnClickListener { if (web.canGoBack()) web.goBack() else mostrarInicio() }
        fila.addView(atras)
        tituloWeb = Estilo.texto(this, "", 18f, Estilo.TEXTO, true)
        fila.addView(tituloWeb, LinearLayout.LayoutParams(0, -2, 1f))
        val rec = Estilo.texto(this, "⟳", 24f, Estilo.CIAN, true)
        rec.setPadding(d(12f), 0, d(6f), 0)
        rec.setOnClickListener {
            tomarFoto()
            banner.visibility = View.GONE
            web.reload()
        }
        fila.addView(rec)
        col.addView(fila)
        segmento = LinearLayout(this)
        segmento.orientation = LinearLayout.HORIZONTAL
        segmento.setPadding(0, d(8f), 0, 0)
        col.addView(segmento)
        return col
    }

    private fun pintarSegmento() {
        segmento.removeAllViews()
        if (pestana == 2) {
            segmento.visibility = View.GONE
            return
        }
        segmento.visibility = View.VISIBLE
        listOf("Publicados hoy", "Todos").forEachIndexed { i, t ->
            val c = Estilo.chip(this, t, pestana == i) { abrir(i) }
            val p = LinearLayout.LayoutParams(-2, -2)
            p.setMargins(0, 0, d(8f), 0)
            segmento.addView(c, p)
        }
    }

    // ------------------------------------------------------------------ INICIO

    private fun mostrarInicio() {
        modoRevision = false
        construirInicio()
        ponerVista(V_INICIO, 0)
    }

    private fun construirInicio() {
        inicio.removeAllViews()
        val todas = store.fichas()
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0)
        val hoy = todas.count { it.optLong("d") >= cal.timeInMillis }

        // encabezado con logo
        val cab = LinearLayout(this)
        cab.orientation = LinearLayout.HORIZONTAL
        cab.gravity = Gravity.CENTER_VERTICAL
        val logo = ImageView(this)
        logo.setImageResource(R.drawable.logo_jerocar)
        cab.addView(logo, LinearLayout.LayoutParams(d(54f), d(54f)))
        val tit = LinearLayout(this)
        tit.orientation = LinearLayout.VERTICAL
        tit.setPadding(d(12f), 0, 0, 0)
        tit.addView(Estilo.texto(this, "Jerocar", 26f, Estilo.TEXTO, true))
        tit.addView(Estilo.texto(this, "Radar de compraventa de vehículos", 13f, Estilo.GRIS))
        cab.addView(tit, LinearLayout.LayoutParams(0, -2, 1f))
        val aj = Estilo.texto(this, "⚙", 24f, Estilo.GRIS)
        aj.setPadding(d(10f), d(6f), d(4f), d(6f))
        aj.setOnClickListener { startActivity(Intent(this, AjustesActivity::class.java)) }
        cab.addView(aj)
        inicio.addView(cab)
        inicio.addView(Estilo.espacio(this, 18f))

        // tarjeta de estado (degradado)
        val hero = LinearLayout(this)
        hero.orientation = LinearLayout.VERTICAL
        hero.setPadding(d(18f), d(18f), d(18f), d(18f))
        hero.background = Estilo.degradado(Color.parseColor("#1D4ED8"), Color.parseColor("#7C3AED"), 20f, this)
        hero.addView(Estilo.texto(this, "ZONA ACTIVA", 11f, Color.parseColor("#C7D2FE"), true))
        hero.addView(Estilo.texto(this, "${store.departamento} · ${Revision.pesos(store.precioMin)} – ${Revision.pesos(store.precioMax)}", 16f, Color.WHITE, true))
        hero.addView(Estilo.espacio(this, 14f))
        val stats = LinearLayout(this)
        stats.orientation = LinearLayout.HORIZONTAL
        fun stat(valor: String, etiqueta: String) {
            val c = LinearLayout(this)
            c.orientation = LinearLayout.VERTICAL
            c.addView(Estilo.texto(this, valor, 24f, Color.WHITE, true))
            c.addView(Estilo.texto(this, etiqueta, 12f, Color.parseColor("#C7D2FE")))
            stats.addView(c, LinearLayout.LayoutParams(0, -2, 1f))
        }
        stat("$hoy", "detectados hoy")
        stat("${todas.size}", "en seguimiento")
        stat(store.ultimaRevision.ifBlank { "—" }, "última revisión")
        hero.addView(stats)
        inicio.addView(hero)
        inicio.addView(Estilo.espacio(this, 16f))

        // botón principal
        inicio.addView(Estilo.boton(this, "🔍  Revisar ahora", true) { revisar() }, LinearLayout.LayoutParams(-1, -2))
        val rec = if (store.intervalo > 0) "Recordatorio cada ${store.intervalo} min · ${store.horaInicio}:00 a ${store.horaFin}:00" else "Recordatorio apagado"
        inicio.addView(Estilo.texto(this, rec, 12f, Estilo.GRIS).also { it.gravity = Gravity.CENTER; it.setPadding(0, d(8f), 0, 0) }, LinearLayout.LayoutParams(-1, -2))
        inicio.addView(Estilo.espacio(this, 22f))

        // últimos detectados
        val filaT = LinearLayout(this)
        filaT.orientation = LinearLayout.HORIZONTAL
        filaT.gravity = Gravity.CENTER_VERTICAL
        filaT.addView(Estilo.texto(this, "Últimos detectados", 18f, Estilo.TEXTO, true), LinearLayout.LayoutParams(0, -2, 1f))
        val ver = Estilo.texto(this, "Ver todos ›", 14f, Estilo.CIAN, true)
        ver.setOnClickListener { mostrarDetectados(0) }
        filaT.addView(ver)
        inicio.addView(filaT)
        inicio.addView(Estilo.espacio(this, 8f))
        val ultimos = todas.sortedByDescending { it.optLong("d") }.take(3)
        if (ultimos.isEmpty()) {
            inicio.addView(vacio("Todavía no hay carros detectados.\nToque “Revisar ahora” para empezar."))
        } else {
            ultimos.forEach { inicio.addView(tarjeta(it)) }
        }
        inicio.addView(Estilo.espacio(this, 18f))

        // fuentes
        inicio.addView(Estilo.texto(this, "Fuentes", 18f, Estilo.TEXTO, true))
        inicio.addView(Estilo.espacio(this, 8f))
        val f1 = LinearLayout(this)
        f1.orientation = LinearLayout.HORIZONTAL
        f1.addView(mosaico("🚗", "TuCarro hoy", "Publicados hoy") { abrir(0) }, mosaicoLp(true))
        f1.addView(mosaico("📚", "TuCarro todo", "Todo el inventario") { abrir(1) }, mosaicoLp(false))
        inicio.addView(f1)
        val f2 = LinearLayout(this)
        f2.orientation = LinearLayout.HORIZONTAL
        f2.addView(mosaico("🛒", "Marketplace", "Vehículos recientes") { abrir(2) }, mosaicoLp(true))
        f2.addView(mosaico("⚙", "Ajustes", "Zona, precio y avisos") { startActivity(Intent(this, AjustesActivity::class.java)) }, mosaicoLp(false))
        inicio.addView(f2)
    }

    private fun mosaicoLp(izq: Boolean): LinearLayout.LayoutParams {
        val p = LinearLayout.LayoutParams(0, -2, 1f)
        p.setMargins(if (izq) 0 else d(5f), d(5f), if (izq) d(5f) else 0, d(5f))
        return p
    }

    private fun mosaico(ico: String, t: String, sub: String, accion: () -> Unit): View {
        val c = LinearLayout(this)
        c.orientation = LinearLayout.VERTICAL
        c.setPadding(d(14f), d(14f), d(14f), d(14f))
        c.background = Estilo.fondo(Estilo.TARJETA, 16f, this, Estilo.BORDE)
        c.addView(Estilo.texto(this, ico, 24f))
        c.addView(Estilo.espacio(this, 6f))
        c.addView(Estilo.texto(this, t, 15f, Estilo.TEXTO, true))
        c.addView(Estilo.texto(this, sub, 12f, Estilo.GRIS))
        c.setOnClickListener { accion() }
        return c
    }

    private fun vacio(t: String): View {
        val v = Estilo.texto(this, t, 14f, Estilo.GRIS)
        v.gravity = Gravity.CENTER
        v.setPadding(d(16f), d(22f), d(16f), d(22f))
        v.background = Estilo.fondo(Estilo.TARJETA, 16f, this, Estilo.BORDE)
        return v
    }

    // ------------------------------------------------------------------ WEB

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
        modoRevision = false
        pestana = i
        tomarFoto()
        tituloWeb.text = if (i == 2) "Marketplace" else "TuCarro · ${store.departamento}"
        pintarSegmento()
        banner.visibility = View.GONE
        ponerVista(V_WEB, if (i == 2) 3 else 2)
        web.loadUrl(url(i))
    }

    // ------------------------------------------------------------------ LISTAS

    private fun prepararLista(titulo: String, nav: Int) {
        tituloLista.text = titulo
        lista.removeAllViews()
        listaScroll.scrollTo(0, 0)
        ponerVista(V_LISTA, nav)
    }

    private fun revisar() {
        modoRevision = true
        pestana = 0
        tomarFoto()
        prepararLista("Revisando…", 0)
        val cargando = LinearLayout(this)
        cargando.orientation = LinearLayout.VERTICAL
        cargando.gravity = Gravity.CENTER
        cargando.setPadding(0, d(60f), 0, d(60f))
        cargando.addView(ProgressBar(this))
        cargando.addView(Estilo.texto(this, "Consultando TuCarro · ${store.departamento}, publicados hoy", 14f, Estilo.GRIS).also { it.gravity = Gravity.CENTER; it.setPadding(0, d(14f), 0, 0) })
        lista.addView(cargando)
        tituloWeb.text = "TuCarro · ${store.departamento}"
        pintarSegmento()
        web.loadUrl(url(0))
        reloj.removeCallbacksAndMessages(null)
        reloj.postDelayed({
            if (modoRevision) {
                modoRevision = false
                prepararLista("No pude revisar", 0)
                lista.addView(vacio("TuCarro no respondió esta vez.\nPuede que pida verificar que es una persona."))
                lista.addView(Estilo.espacio(this, 12f))
                lista.addView(Estilo.boton(this, "Abrir TuCarro") { abrir(0) })
            }
        }, 25_000)
    }

    private fun precioNum(o: JSONObject): Long =
        o.optString("p").replace(".", "").replace(",", "").trim().toLongOrNull() ?: Long.MAX_VALUE

    /** orden: 0 = más recientes, 1 = menor precio, 2 = mayor precio */
    private fun mostrarDetectados(orden: Int) {
        modoRevision = false
        val todas = store.fichas()
        val ordenadas = when (orden) {
            1 -> todas.sortedBy { precioNum(it) }
            2 -> todas.sortedByDescending { if (precioNum(it) == Long.MAX_VALUE) -1L else precioNum(it) }
            else -> todas.sortedByDescending { it.optLong("d") }
        }
        prepararLista("Detectados (${todas.size})", 1)
        val chips = LinearLayout(this)
        chips.orientation = LinearLayout.HORIZONTAL
        listOf("Recientes", "Menor precio", "Mayor precio").forEachIndexed { i, t ->
            val p = LinearLayout.LayoutParams(-2, -2)
            p.setMargins(0, 0, d(8f), 0)
            chips.addView(Estilo.chip(this, t, i == orden) { mostrarDetectados(i) }, p)
        }
        val hs = HorizontalScrollView(this)
        hs.isHorizontalScrollBarEnabled = false
        hs.addView(chips)
        lista.addView(hs)
        lista.addView(Estilo.espacio(this, 10f))
        if (todas.isEmpty()) lista.addView(vacio("Todavía no hay carros detectados.\nToque “Revisar ahora” en Inicio."))
        ordenadas.take(150).forEach { lista.addView(tarjeta(it)) }
    }

    private fun mostrarGuardados() {
        modoRevision = false
        pestana = 0
        val arr = try {
            JSONArray(store.ultimosNuevos)
        } catch (e: Exception) {
            JSONArray()
        }
        prepararLista("🆕 ${arr.length()} nuevo(s)", 0)
        lista.addView(Estilo.texto(this, "Revisado a las ${store.ultimaRevision}", 13f, Estilo.GRIS))
        lista.addView(Estilo.espacio(this, 8f))
        for (i in 0 until arr.length()) lista.addView(tarjeta(arr.getJSONObject(i), true))
        lista.addView(Estilo.espacio(this, 8f))
        lista.addView(Estilo.boton(this, "Ver TuCarro hoy completo") { abrir(0) })
    }

    private fun tarjeta(o: JSONObject, nuevo: Boolean = false): View {
        val card = LinearLayout(this)
        card.orientation = LinearLayout.HORIZONTAL
        card.gravity = Gravity.CENTER_VERTICAL
        card.setPadding(d(10f), d(10f), d(12f), d(10f))
        card.background = Estilo.fondo(Estilo.TARJETA, 16f, this, if (nuevo) Estilo.NARANJA else Estilo.BORDE)

        val img = ImageView(this)
        img.scaleType = ImageView.ScaleType.CENTER_CROP
        img.background = Estilo.fondo(Estilo.TARJETA2, 12f, this)
        img.outlineProvider = ViewOutlineProvider.BACKGROUND
        img.clipToOutline = true
        card.addView(img, LinearLayout.LayoutParams(d(104f), d(80f)))
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
        col.setPadding(d(12f), 0, 0, 0)
        val etiquetas = LinearLayout(this)
        etiquetas.orientation = LinearLayout.HORIZONTAL
        fun etiqueta(t: String, fondo: Int, color: Int) {
            val b = Estilo.texto(this, t, 10f, color, true)
            b.setPadding(d(7f), d(2f), d(7f), d(2f))
            b.background = Estilo.fondo(fondo, 7f, this)
            b.maxLines = 1
            val p = LinearLayout.LayoutParams(-2, -2)
            p.setMargins(0, 0, d(5f), d(4f))
            etiquetas.addView(b, p)
        }
        if (nuevo) etiqueta("NUEVO", Estilo.NARANJA, Color.WHITE)
        etiqueta(o.optString("f").ifBlank { "TuCarro" }, Color.parseColor("#FFE600"), Color.parseColor("#1A1A1A"))
        val vendedor = o.optString("s")
        if (vendedor.isNotBlank()) {
            etiqueta("🏪 $vendedor", Estilo.TARJETA2, Estilo.GRIS)
        } else if (o.has("s")) {
            etiqueta("👤 Particular", Color.parseColor("#123B2C"), Estilo.VERDE)
        }
        col.addView(etiquetas)
        val t = Estilo.texto(this, o.optString("t"), 15f, Estilo.TEXTO, true)
        t.maxLines = 2
        col.addView(t)
        val precio = o.optString("p")
        if (precio.isNotBlank()) col.addView(Estilo.texto(this, "$ $precio", 17f, Estilo.VERDE, true))
        val extra = listOf(o.optString("a"), o.optString("l")).filter { it.isNotBlank() }.joinToString("\n")
        if (extra.isNotBlank()) col.addView(Estilo.texto(this, extra, 12f, Estilo.GRIS).also { it.maxLines = 3 })
        if (o.has("d")) {
            val det = SimpleDateFormat("dd/MM hh:mm a", Locale("es", "CO")).format(Date(o.optLong("d")))
            col.addView(Estilo.texto(this, "● Detectado $det", 11f, Estilo.CIAN))
        }
        card.addView(col, LinearLayout.LayoutParams(0, -2, 1f))

        val u = o.optString("u")
        card.setOnClickListener {
            modoRevision = false
            pestana = 0
            tituloWeb.text = "Publicación"
            segmento.visibility = View.GONE
            banner.visibility = View.GONE
            ponerVista(V_WEB, 2)
            web.loadUrl(u)
        }
        val cont = LinearLayout(this)
        cont.orientation = LinearLayout.VERTICAL
        cont.setPadding(0, d(5f), 0, d(5f))
        cont.addView(card)
        return cont
    }

    private fun mostrarBanner(t: String) {
        banner.text = t
        banner.visibility = View.VISIBLE
    }

    // ------------------------------------------------------------------ ANÁLISIS

    private fun analizar() {
        web.evaluateJavascript(JS_LISTA) { res ->
            val arr = try {
                JSONArray(JSONTokener(res).nextValue() as String)
            } catch (e: Exception) {
                JSONArray()
            }
            if (arr.length() == 0) return@evaluateJavascript
            if (!web.url.orEmpty().let { it.contains("carros.tucarro.com.co") || it.contains("carros.mercadolibre.com.co") }) return@evaluateJavascript

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
            store.registrarFichas(arr)
            if (pestana == 0) store.ultimaRevision = hora

            val desde = if (anterior.isNotBlank()) " desde las $anterior" else ""
            if (modoRevision) {
                modoRevision = false
                reloj.removeCallbacksAndMessages(null)
                when {
                    primera -> {
                        prepararLista("✅ Primera revisión", 0)
                        lista.addView(vacio("Guardé ${arr.length()} carros publicados hoy.\nDesde la próxima vez le muestro solo los nuevos."))
                    }
                    nuevos.isEmpty() -> {
                        prepararLista("Nada nuevo", 0)
                        lista.addView(vacio("Sin carros nuevos$desde.\n${arr.length()} publicados hoy, todos ya vistos."))
                    }
                    else -> {
                        prepararLista("🆕 ${nuevos.size} nuevo(s)$desde", 0)
                        nuevos.forEach { lista.addView(tarjeta(it, true)) }
                        web.evaluateJavascript(jsResaltar(nuevos.map { it.getString("id") }), null)
                    }
                }
                lista.addView(Estilo.espacio(this, 10f))
                lista.addView(Estilo.boton(this, "Ver TuCarro hoy completo") { ponerVista(V_WEB, 2) })
            } else if (vista == V_WEB) {
                when {
                    primera -> mostrarBanner("Primera revisión: guardé ${arr.length()} carros. Desde ahora le marco los nuevos.")
                    nuevos.isEmpty() -> mostrarBanner("Sin carros nuevos$desde · ${arr.length()} en pantalla")
                    else -> {
                        mostrarBanner("🆕 ${nuevos.size} nuevo(s)$desde · marcados en naranja")
                        web.evaluateJavascript(jsResaltar(nuevos.map { it.getString("id") }), null)
                    }
                }
            }
        }
    }
}
