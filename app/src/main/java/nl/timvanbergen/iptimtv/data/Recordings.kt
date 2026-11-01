package nl.timvanbergen.iptimtv.data

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.os.PowerManager
import android.provider.MediaStore
import androidx.compose.runtime.mutableStateListOf
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class RecState { SCHEDULED, RECORDING, DONE, FAILED, CANCELLED }

data class Recording(
    val id: String,
    val title: String,
    val channel: String,
    val logo: String?,
    val url: String,
    val start: Long,
    val end: Long,
    val state: RecState = RecState.SCHEDULED,
    val uri: String? = null,
    val bytes: Long = 0,
) {
    fun toJson(): JSONObject = JSONObject().put("id", id).put("title", title).put("channel", channel).put("logo", logo)
        .put("url", url).put("start", start).put("end", end).put("state", state.name).put("uri", uri).put("bytes", bytes)

    companion object {
        fun fromJson(o: JSONObject) = Recording(
            o.getString("id"), o.optString("title"), o.optString("channel"),
            o.optString("logo").takeIf { it.isNotBlank() && it != "null" }, o.optString("url"),
            o.optLong("start"), o.optLong("end"),
            runCatching { RecState.valueOf(o.optString("state")) }.getOrDefault(RecState.FAILED),
            o.optString("uri").takeIf { it.isNotBlank() && it != "null" }, o.optLong("bytes"),
        )
    }
}

/** Alle opnames: gepland, bezig en klaar. Bestanden komen in Download/IPTIMTV. */
object Recordings {
    private lateinit var app: Context
    private lateinit var prefs: SharedPreferences
    val items = mutableStateListOf<Recording>()

    fun init(context: Context) {
        app = context.applicationContext
        prefs = app.getSharedPreferences("recordings", Context.MODE_PRIVATE)
        runCatching { JSONArray(prefs.getString("list", "[]")) }.getOrNull()?.let { a ->
            for (i in 0 until a.length()) items += Recording.fromJson(a.getJSONObject(i))
        }
        // Opnames die bezig waren toen de app stopte: afgelopen = klaar, nog bezig = opnieuw starten.
        val now = System.currentTimeMillis()
        items.toList().forEach {
            when {
                it.state == RecState.RECORDING && it.end < now -> {
                    it.uri?.let { u -> unpend(Uri.parse(u)) }
                    update(it.copy(state = if (it.bytes > 0) RecState.DONE else RecState.FAILED))
                }
                it.state == RecState.RECORDING -> update(it.copy(state = RecState.SCHEDULED))
                it.state == RecState.SCHEDULED && it.end < now -> update(it.copy(state = RecState.FAILED))
            }
        }
        // Wekkers opnieuw zetten (na herstart van het apparaat zijn ze weg).
        items.filter { it.state == RecState.SCHEDULED || it.state == RecState.RECORDING }.forEach { arm(it) }
    }

    /** Bestand dat half geschreven was zichtbaar maken (het is wel bruikbaar). */
    private fun unpend(uri: Uri) {
        if (Build.VERSION.SDK_INT >= 29 && uri.scheme == "content") runCatching {
            app.contentResolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
        }
    }

    private fun save() = prefs.edit().putString("list", JSONArray(items.map { it.toJson() }).toString()).apply()

    fun get(id: String) = items.firstOrNull { it.id == id }

    @Synchronized
    fun update(r: Recording) {
        val i = items.indexOfFirst { it.id == r.id }
        if (i >= 0) items[i] = r else items.add(0, r)
        save()
    }

    /** Opname plannen; begint die nu al, dan start hij meteen. */
    fun schedule(title: String, channel: Channel, start: Long, end: Long): Recording {
        val r = Recording(
            id = System.currentTimeMillis().toString(36),
            title = title, channel = channel.name, logo = channel.logo,
            url = channel.url, start = start, end = end,
        )
        update(r)
        arm(r)
        return r
    }

    fun isRecording(channelUrl: String) = items.any { it.url == channelUrl && it.state == RecState.RECORDING }
    fun activeFor(channelUrl: String) = items.firstOrNull { it.url == channelUrl && (it.state == RecState.RECORDING || it.state == RecState.SCHEDULED) && it.start <= System.currentTimeMillis() + 60_000 }

    private fun arm(r: Recording) {
        if (r.start <= System.currentTimeMillis()) {
            ContextCompat.startForegroundService(app, RecordService.intent(app, r.id))
            return
        }
        val am = app.getSystemService(AlarmManager::class.java)
        val pi = alarmIntent(r.id)
        if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, r.start, pi)
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, r.start, pi)
        }
    }

    private fun alarmIntent(id: String) = PendingIntent.getBroadcast(
        app, id.hashCode(), Intent(app, RecordAlarm::class.java).putExtra("id", id),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /** Stoppen (bezig) of annuleren (gepland). */
    fun stop(r: Recording) {
        app.getSystemService(AlarmManager::class.java).cancel(alarmIntent(r.id))
        if (r.state == RecState.RECORDING) {
            update(r.copy(end = System.currentTimeMillis()))
        } else if (r.state == RecState.SCHEDULED) {
            update(r.copy(state = RecState.CANCELLED))
        }
    }

    fun delete(r: Recording) {
        stop(r)
        r.uri?.let { u ->
            runCatching {
                val uri = Uri.parse(u)
                if (uri.scheme == "file") File(uri.path!!).delete() else app.contentResolver.delete(uri, null, null)
            }
        }
        items.removeAll { it.id == r.id }
        save()
    }

    val finished get() = items.filter { it.state == RecState.DONE || it.state == RecState.RECORDING }
}

class RecordAlarm : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra("id") ?: return
        ContextCompat.startForegroundService(context, RecordService.intent(context, id))
    }
}

/** Schrijft de stream rechtstreeks weg. Meerdere opnames tegelijk kan. */
class RecordService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs = mutableMapOf<String, Job>()
    private var wake: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startInForeground("Opname wordt voorbereid")
        if (wake == null) {
            wake = getSystemService(PowerManager::class.java)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "IPTIMTV:record").apply { acquire(12 * 3600_000L) }
        }
        val id = intent?.getStringExtra("id")
        val r = id?.let { Recordings.get(it) }
        if (r != null && r.state == RecState.SCHEDULED && !jobs.containsKey(r.id)) {
            jobs[r.id] = scope.launch { record(r) }
        }
        if (jobs.isEmpty()) stopSelfSafely()
        return START_NOT_STICKY
    }

    private fun startInForeground(text: String) {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(NotificationChannel("rec", "Opnames", NotificationManager.IMPORTANCE_LOW))
        }
        val n = (if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, "rec") else @Suppress("DEPRECATION") Notification.Builder(this))
            .setContentTitle("IPTIMTV neemt op")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.presence_video_online)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 29) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC) else startForeground(1, n)
    }

    private suspend fun record(start: Recording) {
        var r = start.copy(state = RecState.RECORDING)
        Recordings.update(r)
        withContext(Dispatchers.Main) { startInForeground(r.title) }
        var out: OutputStream? = null
        var pendingUri: Uri? = null
        var written = 0L
        var saved = 0L
        // Teller bijhouden en af en toe opslaan, zodat een onderbroken opname niet als leeg telt.
        val count: (Int) -> Unit = { n ->
            written += n
            if (written - saved > 4_000_000) {
                saved = written
                Recordings.get(r.id)?.let { Recordings.update(it.copy(bytes = written)) }
            }
        }
        val endNow = { Recordings.get(r.id)?.end ?: r.end }
        try {
            val (stream, uri) = openOutput(fileName(r))
            out = stream
            pendingUri = uri
            r = r.copy(uri = uri.toString())
            Recordings.update(r)
            val seen = HashSet<String>()
            while (scope.isActive && System.currentTimeMillis() < endNow()) {
                try {
                    if (r.url.contains(".m3u8", true)) copyHls(r.url, stream, endNow, seen, count) else copyTs(r.url, stream, endNow, seen, count)
                } catch (e: Exception) {
                    delay(2000) // verbinding weg: even wachten en opnieuw
                }
            }
            stream.close(); out = null
            finishPending(uri)
            Recordings.update((Recordings.get(r.id) ?: r).copy(state = if (written > 0) RecState.DONE else RecState.FAILED, bytes = written))
        } catch (e: Exception) {
            runCatching { out?.close() }
            pendingUri?.let { finishPending(it) }
            Recordings.update((Recordings.get(r.id) ?: r).copy(state = if (written > 0) RecState.DONE else RecState.FAILED, bytes = written))
        } finally {
            withContext(Dispatchers.Main) {
                jobs.remove(start.id)
                if (jobs.isEmpty()) stopSelfSafely()
            }
        }
    }

    /** Gewone (Xtream) live-stream: bytes doorschrijven tot de eindtijd. Blijkt het HLS, dan via [copyHls]. */
    private suspend fun copyTs(url: String, out: OutputStream, end: () -> Long, seen: MutableSet<String>, count: (Int) -> Unit) {
        val response = Net.client.newCall(Request.Builder().url(recordUrl(url)).build()).execute()
        val finalUrl = response.request.url.toString()
        if (response.header("Content-Type").orEmpty().contains("mpegurl", true) || finalUrl.contains(".m3u8", true)) {
            response.close()
            return copyHls(finalUrl, out, end, seen, count)
        }
        response.use { r ->
            val input = r.body?.byteStream() ?: return
            val buf = ByteArray(128 * 1024)
            while (System.currentTimeMillis() < end() && scope.isActive) {
                val read = input.read(buf)
                if (read < 0) break
                out.write(buf, 0, read)
                count(read)
            }
        }
    }

    /** HLS: de playlist steeds opnieuw lezen en nieuwe stukjes achter elkaar zetten. */
    private suspend fun copyHls(url: String, out: OutputStream, end: () -> Long, seen: MutableSet<String>, count: (Int) -> Unit) {
        var playlistUrl = url
        while (System.currentTimeMillis() < end() && scope.isActive) {
            val text = Net.get(playlistUrl)
            val base = playlistUrl.substringBeforeLast('/') + "/"
            val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
            if (lines.any { it.startsWith("#EXT-X-STREAM-INF") }) {
                // Hoofdlijst: de eerste variant nemen.
                val variant = lines.dropWhile { !it.startsWith("#EXT-X-STREAM-INF") }.drop(1).firstOrNull { !it.startsWith("#") } ?: return
                playlistUrl = if (variant.startsWith("http")) variant else base + variant
                continue
            }
            val target = lines.firstOrNull { it.startsWith("#EXT-X-TARGETDURATION:") }?.substringAfter(':')?.toIntOrNull() ?: 4
            for (seg in lines.filter { !it.startsWith("#") }) {
                if (System.currentTimeMillis() >= end()) break
                val segUrl = if (seg.startsWith("http")) seg else base + seg
                if (!seen.add(segUrl)) continue
                Net.client.newCall(Request.Builder().url(segUrl).build()).execute().use { r ->
                    r.body?.byteStream()?.use { input ->
                        val buf = ByteArray(128 * 1024)
                        while (true) {
                            val read = input.read(buf)
                            if (read < 0) break
                            out.write(buf, 0, read)
                            count(read)
                        }
                    }
                }
            }
            // Kort wachten, maar wel snel reageren op stoppen.
            repeat(target) { if (System.currentTimeMillis() < end()) delay(500) }
        }
    }

    /** Bij Xtream nemen we altijd de .ts-variant op, ook als je kijkt via HLS. */
    private fun recordUrl(url: String) = if (url.contains("/live/") && url.endsWith(".m3u8")) url.removeSuffix(".m3u8") + ".ts" else url

    private fun fileName(r: Recording): String {
        val date = SimpleDateFormat("yyyy-MM-dd HH.mm", Locale.US).format(Date(r.start))
        val clean = "${r.channel} - ${r.title} - $date".replace(Regex("""[\\/:*?"<>|]"""), " ").trim()
        return "$clean.ts"
    }

    private fun openOutput(name: String): Pair<OutputStream, Uri> {
        if (Build.VERSION.SDK_INT >= 29) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, "video/mp2t")
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/IPTIMTV")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: error("geen opslag")
            return contentResolver.openOutputStream(uri)!! to uri
        }
        @Suppress("DEPRECATION")
        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "IPTIMTV").apply { mkdirs() }
        val file = File(dir, name)
        return file.outputStream() to Uri.fromFile(file)
    }

    private fun finishPending(uri: Uri) {
        if (Build.VERSION.SDK_INT >= 29 && uri.scheme == "content") {
            runCatching { contentResolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null) }
        }
    }

    private fun stopSelfSafely() {
        wake?.let { if (it.isHeld) it.release() }
        wake = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        wake?.let { if (it.isHeld) it.release() }
        super.onDestroy()
    }

    companion object {
        fun intent(context: Context, id: String) = Intent(context, RecordService::class.java).putExtra("id", id)
    }
}
