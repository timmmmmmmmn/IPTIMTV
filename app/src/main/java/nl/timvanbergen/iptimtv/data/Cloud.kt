package nl.timvanbergen.iptimtv.data

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.time.LocalDate
import java.util.TimeZone

class WatchStats(val today: Long, val week: Long, val total: Long)

/** Account op iptv.timvanbergen.nl: inloggen, kijkgeschiedenis en kijktijd. */
object Cloud {
    private const val BASE = "https://iptv.timvanbergen.nl/api"
    private lateinit var prefs: SharedPreferences
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    var username by mutableStateOf<String?>(null); private set
    var stats by mutableStateOf<WatchStats?>(null); private set
    private var token: String? = null

    val loggedIn get() = username != null && token != null
    fun token() = token.orEmpty()

    fun init(context: Context) {
        prefs = context.getSharedPreferences("cloud", Context.MODE_PRIVATE)
        token = prefs.getString("token", null)
        username = prefs.getString("username", null)
    }

    private fun call(method: String, path: String, body: JSONObject? = null): Pair<Int, JSONObject> {
        val b = Request.Builder().url("$BASE/$path")
        token?.let { b.header("Authorization", "Bearer $it") }
        val rb = body?.toString()?.toRequestBody("application/json".toMediaType())
        b.method(method, rb ?: if (method == "POST") "{}".toRequestBody("application/json".toMediaType()) else null)
        Net.client.newCall(b.build()).execute().use { r ->
            val text = r.body?.string().orEmpty()
            return r.code to (runCatching { JSONObject(text) }.getOrNull() ?: JSONObject())
        }
    }

    /** Inloggen of account maken; geeft een foutmelding terug of null als het gelukt is. */
    suspend fun signIn(name: String, password: String, create: Boolean): String? = withContext(Dispatchers.IO) {
        try {
            val (code, o) = call("POST", if (create) "register" else "login", JSONObject()
                .put("username", name.trim()).put("password", password).put("device", Build.MODEL))
            if (code != 200) return@withContext o.optString("error", "Er ging iets mis ($code)")
            token = o.getString("token")
            withContext(Dispatchers.Main) { username = o.optString("username") }
            prefs.edit().putString("token", token).putString("username", o.optString("username")).apply()
            refresh()
            null
        } catch (e: Exception) {
            "Geen verbinding met de server"
        }
    }

    fun signOut() {
        val t = token
        token = null
        username = null
        stats = null
        prefs.edit().clear().apply()
        if (t != null) scope.launch {
            runCatching {
                Net.client.newCall(Request.Builder().url("$BASE/logout").header("Authorization", "Bearer $t")
                    .post("{}".toRequestBody("application/json".toMediaType())).build()).execute().close()
            }
        }
    }

    /** Statistieken en geschiedenis ophalen (bij openen van de app en na inloggen). */
    suspend fun refresh() {
        if (!loggedIn) return
        withContext(Dispatchers.Main) { Sync.pull() }
        withContext(Dispatchers.IO) {
            runCatching {
                val tz = -TimeZone.getDefault().getOffset(System.currentTimeMillis()) / 60_000
                val (code, me) = call("GET", "me?tz=$tz")
                if (code == 401) {
                    withContext(Dispatchers.Main) { signOut() }
                    return@withContext
                }
                me.optJSONObject("stats")?.let { s ->
                    val st = WatchStats(s.optLong("today"), s.optLong("week"), s.optLong("total"))
                    withContext(Dispatchers.Main) { stats = st }
                }
                if (Settings.syncHistory.on) {
                    val (_, h) = call("GET", "history?limit=80")
                    val a = h.optJSONArray("items") ?: return@runCatching
                    val list = List(a.length()) { WatchItem.fromJson(a.getJSONObject(it)) }
                    withContext(Dispatchers.Main) { History.merge(list) }
                }
            }
        }
    }

    /** Voortgang en kijktijd doorgeven; gaat op de achtergrond en mag mislukken. */
    fun progress(item: WatchItem, watchedSeconds: Long) {
        if (!loggedIn || !Settings.syncHistory.on) return
        if (item.kind == "live" && !Settings.countLive.on) return
        scope.launch {
            runCatching {
                call("POST", "progress", item.toJson().put("watched", watchedSeconds).put("day", LocalDate.now().toString()))
            }
        }
    }

    fun forget(key: String) {
        if (!loggedIn) return
        scope.launch { runCatching { call("DELETE", "history/item?key=" + java.net.URLEncoder.encode(key, "UTF-8")) } }
    }

    fun clearHistory() {
        if (!loggedIn) return
        scope.launch { runCatching { call("DELETE", "history") } }
    }
}

fun formatWatchTime(seconds: Long): String {
    val m = seconds / 60
    return when {
        m < 1 -> "nog niets"
        m < 60 -> "$m min"
        else -> "${m / 60} u ${m % 60} min"
    }
}
