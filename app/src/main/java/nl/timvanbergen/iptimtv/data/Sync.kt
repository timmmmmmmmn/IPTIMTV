package nl.timvanbergen.iptimtv.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/**
 * Logins, favorieten, groepen en volgorde via je IPTIMTV-account op al je apparaten (en de website).
 * Per onderdeel wint de nieuwste wijziging.
 */
object Sync {
    private const val BASE = "https://iptv.timvanbergen.nl/api/sync"
    private lateinit var prefs: SharedPreferences
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val pending = mutableMapOf<String, Job>()
    private var applying = false

    fun init(context: Context) {
        prefs = context.getSharedPreferences("sync", Context.MODE_PRIVATE)
    }

    private fun stamp(key: String) = prefs.getLong(key, 0)

    /** Er is lokaal iets veranderd: even wachten (meerdere wijzigingen tegelijk) en dan versturen. */
    fun changed(key: String, value: Any) {
        if (applying || !::prefs.isInitialized) return
        prefs.edit().putLong(key, System.currentTimeMillis()).apply()
        if (!Cloud.loggedIn) return
        pending[key]?.cancel()
        pending[key] = scope.launch {
            delay(1500)
            push(key, value, stamp(key))
        }
    }

    private fun push(key: String, value: Any, updated: Long) {
        runCatching {
            val body = JSONObject().put("key", key).put("value", value).put("updated", updated).toString()
            val req = Request.Builder().url(BASE).header("Authorization", "Bearer " + Cloud.token())
                .put(body.toRequestBody("application/json".toMediaType())).build()
            Net.client.newCall(req).execute().close()
        }
    }

    /** Ophalen en samenvoegen; daarna wat hier nieuwer is terugsturen. */
    suspend fun pull() {
        if (!Cloud.loggedIn) return
        val remote = withContext(Dispatchers.IO) {
            runCatching {
                val req = Request.Builder().url(BASE).header("Authorization", "Bearer " + Cloud.token()).build()
                Net.client.newCall(req).execute().use { r ->
                    if (!r.isSuccessful) return@runCatching null
                    JSONObject(r.body!!.string()).optJSONArray("items")
                }
            }.getOrNull()
        } ?: return
        val seen = HashSet<String>()
        applying = true
        try {
            for (i in 0 until remote.length()) {
                val it = remote.getJSONObject(i)
                val key = it.getString("key")
                val updated = it.optLong("updated")
                seen += key
                if (updated <= stamp(key)) continue
                when {
                    // Eerste keer synchroniseren met al eigen logins op dit apparaat: samenvoegen, niets kwijtraken.
                    key == "profiles" && stamp(key) == 0L && Library.profiles.isNotEmpty() -> {
                        val merged = it.optJSONArray("value") ?: JSONArray()
                        val ids = (0 until merged.length()).map { i -> merged.getJSONObject(i).optString("id") }.toSet()
                        Library.profiles.filter { p -> p.id !in ids }.forEach { p -> merged.put(p.toJson()) }
                        Library.applyProfiles(merged)
                        prefs.edit().putLong(key, System.currentTimeMillis()).apply()
                        continue
                    }
                    key == "profiles" -> Library.applyProfiles(it.optJSONArray("value") ?: JSONArray())
                    key.startsWith("user:") -> Library.applyUserData(key.removePrefix("user:"), it.optJSONObject("value") ?: JSONObject())
                }
                prefs.edit().putLong(key, updated).apply()
            }
        } finally {
            applying = false
        }
        // Wat hier nieuwer is (of online nog niet bestaat) versturen.
        val local = buildList {
            if (Library.profiles.isNotEmpty()) add("profiles" to Library.profilesJson())
            Library.profiles.forEach { p -> add("user:${p.id}" to Library.userDataJson(p.id)) }
        }
        val remoteStamps = (0 until remote.length()).associate { remote.getJSONObject(it).let { o -> o.getString("key") to o.optLong("updated") } }
        withContext(Dispatchers.IO) {
            local.forEach { (key, value) ->
                val mine = stamp(key).takeIf { it > 0 } ?: System.currentTimeMillis().also { t -> prefs.edit().putLong(key, t).apply() }
                if (key !in seen || mine > (remoteStamps[key] ?: 0)) push(key, value, mine)
            }
        }
    }
}
