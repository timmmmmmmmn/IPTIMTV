package nl.timvanbergen.iptimtv.data

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import nl.timvanbergen.iptimtv.MainActivity
import nl.timvanbergen.iptimtv.R

/**
 * "Alleen geluid": de speler blijft spelen als je de app wegdrukt of het scherm uitzet.
 * Deze dienst houdt de app daarvoor wakker en zet een melding neer met een stopknop.
 */
object AudioOnly {
    var on by mutableStateOf(false); private set
    /** De speler luistert hiernaar om te stoppen vanuit de melding. */
    var onStop: (() -> Unit)? = null

    fun start(context: Context, title: String) {
        on = true
        val i = Intent(context, AudioService::class.java).putExtra("title", title)
        if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(i) else context.startService(i)
    }

    fun update(context: Context, title: String) {
        if (on) context.startService(Intent(context, AudioService::class.java).putExtra("title", title))
    }

    fun stop(context: Context) {
        if (!on) return
        on = false
        context.stopService(Intent(context, AudioService::class.java))
    }
}

class AudioService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val title = intent?.getStringExtra("title") ?: "IPTIMTV"
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(NotificationChannel("audio", "Alleen geluid", NotificationManager.IMPORTANCE_LOW))
        }
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getBroadcast(this, 1, Intent(this, AudioStop::class.java), PendingIntent.FLAG_IMMUTABLE)
        val n = (if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, "audio") else @Suppress("DEPRECATION") Notification.Builder(this))
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText("Alleen geluid · IPTIMTV")
            .setContentIntent(open)
            .setOngoing(true)
            .addAction(Notification.Action.Builder(null, "Stoppen", stop).build())
            .build()
        if (Build.VERSION.SDK_INT >= 29) startForeground(3, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        else startForeground(3, n)
        return START_NOT_STICKY
    }
}

/** De stopknop in de melding. */
class AudioStop : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        AudioOnly.onStop?.invoke()
        AudioOnly.stop(context)
    }
}
