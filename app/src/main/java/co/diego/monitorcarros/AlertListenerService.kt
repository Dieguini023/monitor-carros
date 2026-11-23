package co.diego.monitorcarros

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Con su permiso, lee las notificaciones que le llegan de Facebook (alertas de
 * Marketplace), Mercado Libre y TuCarro. Si hablan de vehículos, las guarda en
 * el historial y le lanza un aviso fuerte propio. Al tocarlo se abre la app original.
 */
class AlertListenerService : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val pkg = sbn.packageName ?: return
        if (pkg == packageName) return
        val fuente = when {
            pkg == "com.facebook.katana" || pkg == "com.facebook.lite" -> "Facebook"
            pkg.contains("mercadolibre") -> "Mercado Libre"
            pkg.contains("tucarro") -> "TuCarro"
            else -> return
        }

        val ex = sbn.notification?.extras ?: return
        val titulo = ex.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val texto = (ex.getCharSequence(Notification.EXTRA_BIG_TEXT)
            ?: ex.getCharSequence(Notification.EXTRA_TEXT))?.toString() ?: ""
        val todo = "$titulo $texto".lowercase()
        if (PALABRAS.none { todo.contains(it) }) return

        val clave = "$pkg|$titulo|$texto"
        if (clave == ultimaClave) return
        ultimaClave = clave

        val hora = SimpleDateFormat("dd/MM hh:mm a", Locale("es", "CO")).format(Date())
        Store(this).agregarHistorial("$hora · [$fuente] $titulo\n$texto")
        Notifier.mostrar(
            this, clave.hashCode(), "🔔 $fuente: $titulo", texto, null,
            sbn.notification.contentIntent,
        )
    }

    companion object {
        @Volatile
        private var ultimaClave = ""

        private val PALABRAS = listOf(
            "marketplace", "carro", "camioneta", "vehículo", "vehiculo", "automóvil", "automovil",
            "auto ", "moto", "modelo 20", " km", "kilómetros", "kilometros",
            "renault", "chevrolet", "mazda", "kia", "toyota", "nissan", "hyundai", "ford",
            "volkswagen", "suzuki", "duster", "sandero", "logan", "spark", "sail", "picanto",
            "rio ", "tracker", "captur", "stepway", "swift", "fiat", "jeep", "mitsubishi",
            "honda", "peugeot", "bmw", "mercedes", "audi", "subaru", "byd", "jac",
        )
    }
}
