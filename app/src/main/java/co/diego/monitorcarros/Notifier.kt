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
    private const val CANAL_ESTADO = "estado_revision"

    fun crearCanal(ctx: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            val c = NotificationChannel(CANAL, "Carros nuevos", NotificationManager.IMPORTANCE_HIGH)
            c.enableVibration(true)
            c.vibrationPattern = longArrayOf(0, 500, 300, 500)
            val nm = ctx.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(c)
            val e = NotificationChannel(CANAL_ESTADO, "Estado de revisión", NotificationManager.IMPORTANCE_LOW)
            e.setSound(null, null)
            e.enableVibration(false)
            nm.createNotificationChannel(e)
        }
    }

    @SuppressLint("MissingPermission")
    fun recordatorio(ctx: Context) {
        crearCanal(ctx)
        if (Build.VERSION.SDK_INT >= 33 &&
            ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val revisarAqui = PendingIntent.getForegroundService(
            ctx, 504, Intent(ctx, ServicioRevision::class.java), flags,
        )
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
            .setContentText("Toque 🔍 Revisar: reviso sin abrir la app y le aviso solo si hay algo nuevo")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(revisarAqui)
            .addAction(0, "🔍 Revisar", revisarAqui)
            .addAction(0, "Abrir Jerocar", abrirApp(0, 503, true))
            .build()
        NotificationManagerCompat.from(ctx).notify(500, n)
    }

    fun estado(ctx: Context, titulo: String, texto: String): android.app.Notification {
        crearCanal(ctx)
        return NotificationCompat.Builder(ctx, CANAL_ESTADO)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle(titulo)
            .setContentText(texto)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSilent(true)
            .build()
    }

    @SuppressLint("MissingPermission")
    fun resultado(ctx: Context, titulo: String, texto: String, importante: Boolean, alTocar: PendingIntent) {
        crearCanal(ctx)
        if (Build.VERSION.SDK_INT >= 33 &&
            ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val b = NotificationCompat.Builder(ctx, if (importante) CANAL else CANAL_ESTADO)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle(titulo)
            .setContentText(texto)
            .setStyle(NotificationCompat.BigTextStyle().bigText(texto))
            .setPriority(if (importante) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .setContentIntent(alTocar)
        if (!importante) {
            b.setSilent(true)
            b.setTimeoutAfter(90_000)
        }
        NotificationManagerCompat.from(ctx).notify(601, b.build())
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
