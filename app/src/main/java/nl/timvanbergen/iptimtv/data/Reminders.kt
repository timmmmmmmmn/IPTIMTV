package nl.timvanbergen.iptimtv.data

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject

/** Een wedstrijd waarvoor je een herinnering wilt. */
data class Reminder(val id: String, val title: String, val league: String, val start: Long) {
    fun toJson(): JSONObject = JSONObject().put("id", id).put("title", title).put("league", league).put("start", start)

    companion object {
        fun fromJson(o: JSONObject) = Reminder(o.getString("id"), o.optString("title"), o.optString("league"), o.optLong("start"))
    }
}

/** Herinneringen: 5 minuten voor de aftrap een melding en (in de app) een banner van 30 seconden. */
object Reminders {
    const val BEFORE = 5 * 60_000L
    private lateinit var app: Context
    private lateinit var prefs: SharedPreferences
    val items = mutableStateListOf<Reminder>()

    /** De banner die nu in beeld staat (ook tijdens het kijken). */
    var banner by mutableStateOf<Reminder?>(null)

    fun init(context: Context) {
        app = context.applicationContext
        prefs = app.getSharedPreferences("reminders", Context.MODE_PRIVATE)
        runCatching { JSONArray(prefs.getString("list", "[]")) }.getOrNull()?.let { a ->
            for (i in 0 until a.length()) items += Reminder.fromJson(a.getJSONObject(i))
        }
        // Oude herinneringen opruimen en de rest opnieuw zetten (na een herstart zijn wekkers weg).
        items.removeAll { it.start < System.currentTimeMillis() - 3 * 3600_000L }
        save()
        items.filter { it.start - BEFORE > System.currentTimeMillis() }.forEach { arm(it) }
    }

    fun idFor(m: Match) = "${m.league.id}|${m.home.name}|${m.away.name}|${m.start}"
    fun has(m: Match) = items.any { it.id == idFor(m) }

    fun toggle(m: Match) {
        val id = idFor(m)
        val existing = items.firstOrNull { it.id == id }
        if (existing != null) {
            items.remove(existing)
            app.getSystemService(AlarmManager::class.java).cancel(pending(existing))
        } else {
            val r = Reminder(id, "${m.home.name} – ${m.away.name}", m.league.name, m.start)
            items += r
            arm(r)
        }
        save()
    }

    private fun save() = prefs.edit().putString("list", JSONArray(items.map { it.toJson() }).toString()).apply()

    private fun pending(r: Reminder) = PendingIntent.getBroadcast(
        app, r.id.hashCode(), Intent(app, ReminderAlarm::class.java).putExtra("id", r.id),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun arm(r: Reminder) {
        val at = maxOf(r.start - BEFORE, System.currentTimeMillis() + 1000)
        val am = app.getSystemService(AlarmManager::class.java)
        if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending(r))
        else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending(r))
    }

    /** Wordt door de wekker aangeroepen. */
    fun fire(context: Context, id: String) {
        val r = items.firstOrNull { it.id == id } ?: return
        banner = r
        val nm = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(NotificationChannel("matches", "Wedstrijden", NotificationManager.IMPORTANCE_HIGH))
        }
        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, nl.timvanbergen.iptimtv.MainActivity::class.java).putExtra("open", "sport")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = (if (Build.VERSION.SDK_INT >= 26) Notification.Builder(context, "matches") else @Suppress("DEPRECATION") Notification.Builder(context))
            .setContentTitle("${r.title} begint over 5 minuten")
            .setContentText(r.league)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { nm.notify(r.id.hashCode(), n) }
    }
}

class ReminderAlarm : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Reminders.fire(context, intent.getStringExtra("id") ?: return)
    }
}
