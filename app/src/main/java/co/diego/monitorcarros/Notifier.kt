package co.diego.monitorcarros

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

object Notifier {
    private const val CANAL = "carros_nuevos"

    fun crearCanal(ctx: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            val c = NotificationChannel(CANAL, "Carros nuevos", NotificationManager.IMPORTANCE_HIGH)
            c.enableVibration(true)
            c.vibrationPattern = longArrayOf(0, 500, 300, 500)
            ctx.getSystemService(NotificationManager::class.java).createNotificationChannel(c)
        }
    }

    @SuppressLint("MissingPermission")
    fun recordatorio(ctx: Context) {
        crearCanal(ctx)
        if (Build.VERSION.SDK_INT >= 33 &&
            ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        fun abrirApp(pestana: Int, codigo: Int, revisar: Boolean = false): PendingIntent {
            val i = Intent(ctx, MainActivity::class.java)
            i.putExtra("pestana", pestana)
            i.putExtra("revisar", revisar)
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            return PendingIntent.getActivity(ctx, codigo, i, flags)
        }
        val n = NotificationCompat.Builder(ctx, CANAL)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle("🚗 ¿Carros nuevos en TuCarro?")
            .setContentText("Toque 🔍 Revisar: le muestro solo lo nuevo")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(abrirApp(0, 501, true))
            .addAction(0, "🔍 Revisar", abrirApp(0, 503, true))
            .addAction(0, "Ver Marketplace", abrirApp(2, 502))
            .build()
        NotificationManagerCompat.from(ctx).notify(500, n)
    }

    @SuppressLint("MissingPermission")
    fun mostrar(
        ctx: Context,
        id: Int,
        titulo: String,
        texto: String,
        enlace: String?,
        original: PendingIntent? = null,
    ) {
        crearCanal(ctx)
        if (Build.VERSION.SDK_INT >= 33 &&
            ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val alTocar: PendingIntent = when {
            original != null -> original
            !enlace.isNullOrBlank() ->
                PendingIntent.getActivity(ctx, id, Intent(Intent.ACTION_VIEW, Uri.parse(enlace)), flags)
            else ->
                PendingIntent.getActivity(ctx, 0, Intent(ctx, MainActivity::class.java), flags)
        }

        val n = NotificationCompat.Builder(ctx, CANAL)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle(titulo)
            .setContentText(texto)
            .setStyle(NotificationCompat.BigTextStyle().bigText(texto))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setContentIntent(alTocar)
            .build()
        NotificationManagerCompat.from(ctx).notify(id, n)
    }
}
