package nl.timvanbergen.iptimtv.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream

/**
 * Zet de kanalen-, film- en serielijsten (zonder wachtwoorden) in je account, zodat de website ze kan tonen.
 * Nodig omdat veel providers verzoeken vanaf servers blokkeren; de app haalt ze vanaf je eigen internet op.
 */
object CatalogShare {
    private const val BASE = "https://iptv.timvanbergen.nl/api/catalog/"
    private lateinit var prefs: SharedPreferences
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun init(context: Context) {
        prefs = context.getSharedPreferences("catalog_share", Context.MODE_PRIVATE)
    }

    /** Uploaden als het een dag geleden is (of [force] na verversen). */
    fun maybeUpload(p: Profile?, c: Catalog, force: Boolean = false) {
        if (p == null || c.isEmpty || !Cloud.loggedIn || !::prefs.isInitialized) return
        val last = prefs.getLong(p.id, 0)
        if (!force && System.currentTimeMillis() - last < 24 * 3600_000L) return
        scope.launch {
            val m3u = p.type == SourceType.M3U
            val o = JSONObject().put("v", 1).put("type", p.type.name)
                .put("liveCats", JSONArray(c.liveCategories.map { JSONArray().put(it.id).put(it.name) }))
                .put("live", JSONArray(c.channels.map {
                    JSONArray().put(it.id).put(it.name).put(it.logo ?: "").put(it.categoryId).put(it.archiveDays).put(it.epgId ?: "")
                        .put(if (m3u) it.url else "")
                }))
                .put("movieCats", JSONArray(c.movieCategories.map { JSONArray().put(it.id).put(it.name) }))
                .put("movies", JSONArray(c.movies.map {
                    JSONArray().put(it.id).put(it.name).put(it.poster ?: "").put(it.categoryId).put(it.rating ?: "").put(it.added)
                        .put(if (m3u) it.url else it.url.substringAfterLast('.', "mp4"))
                }))
                .put("showCats", JSONArray(c.showCategories.map { JSONArray().put(it.id).put(it.name) }))
                .put("shows", JSONArray(c.shows.map {
                    JSONArray().put(it.id).put(it.name).put(it.poster ?: "").put(it.categoryId).put(it.rating ?: "").put((it.plot ?: "").take(300)).put(it.updated)
                }))
            if (put(p.id, o.toString())) prefs.edit().putLong(p.id, System.currentTimeMillis()).apply()
        }
    }

    /** Afleveringen van een serie die je in de app opent, ook voor de website. */
    fun uploadSeries(p: Profile?, seriesId: String, episodes: List<Episode>) {
        if (p == null || episodes.isEmpty() || !Cloud.loggedIn) return
        scope.launch {
            val a = JSONArray(episodes.map {
                JSONArray().put(it.id).put(it.season).put(it.number).put(it.title).put(it.url.substringAfterLast('.', "mp4"))
                    .put(it.image ?: "").put(it.duration ?: "")
            })
            put("${p.id}/series/$seriesId", a.toString())
        }
    }

    private fun put(path: String, json: String): Boolean = runCatching {
        val bytes = ByteArrayOutputStream().also { out -> GZIPOutputStream(out).use { it.write(json.toByteArray()) } }.toByteArray()
        val req = Request.Builder().url(BASE + path).header("Authorization", "Bearer " + Cloud.token())
            .put(bytes.toRequestBody("application/gzip".toMediaType())).build()
        Net.client.newCall(req).execute().use { it.isSuccessful }
    }.getOrDefault(false)
}
