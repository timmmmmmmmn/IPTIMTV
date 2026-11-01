package nl.timvanbergen.iptimtv.data

import android.util.JsonReader
import android.util.JsonToken
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream

const val USER_AGENT = "IPTIMTV/1.0"

object Net {
    private var cached: Pair<String, OkHttpClient>? = null

    /** De verbinding volgt de instellingen (identiteit en wachttijd) uit Geavanceerd. */
    val client: OkHttpClient
        get() {
            val ua = Settings.userAgent.value
            val timeout = Settings.timeout.int.toLong()
            val key = "$ua|$timeout"
            cached?.let { if (it.first == key) return it.second }
            val c = OkHttpClient.Builder()
                .connectTimeout(timeout, TimeUnit.SECONDS)
                .readTimeout(timeout * 6, TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .addInterceptor { chain ->
                    val r = chain.request()
                    // Eigen server en GitHub altijd als IPTIMTV; de provider krijgt de gekozen identiteit.
                    val agent = if (r.url.host.endsWith("timvanbergen.nl") || r.url.host.endsWith("github.com")) USER_AGENT else ua
                    chain.proceed(r.newBuilder().header("User-Agent", agent).build())
                }
                .build()
            cached = key to c
            return c
        }

    fun get(url: String): String = open(url).use { it.readBytes().toString(Charsets.UTF_8) }

    /** Opent een url als stream; gzip wordt automatisch uitgepakt. */
    fun open(url: String): InputStream {
        val response = client.newCall(Request.Builder().url(url).build()).execute()
        if (!response.isSuccessful) {
            response.close()
            throw IOException("Server antwoordde ${response.code}")
        }
        val stream = BufferedInputStream(response.body!!.byteStream(), 64 * 1024)
        stream.mark(2)
        val gzip = stream.read() == 0x1f && stream.read() == 0x8b
        stream.reset()
        return if (gzip) BufferedInputStream(GZIPInputStream(stream), 64 * 1024) else stream
    }

    /** Downloadt naar een tijdelijk bestand en zet het pas op zijn plek als alles binnen is. */
    fun download(url: String, target: File) {
        val tmp = File(target.parentFile, target.name + ".tmp")
        open(url).use { input -> tmp.outputStream().use { input.copyTo(it, 64 * 1024) } }
        if (!tmp.renameTo(target)) {
            target.delete(); tmp.renameTo(target)
        }
    }
}

private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

/** Leest een JSON-array van objecten één voor één in, zodat ook enorme lijsten weinig geheugen kosten. */
fun forEachJsonObject(file: File, block: (Map<String, String>) -> Unit) {
    if (!file.exists()) return
    JsonReader(file.bufferedReader(Charsets.UTF_8)).use { r ->
        r.isLenient = true
        if (r.peek() != JsonToken.BEGIN_ARRAY) return
        r.beginArray()
        while (r.hasNext()) {
            if (r.peek() != JsonToken.BEGIN_OBJECT) {
                r.skipValue(); continue
            }
            val m = HashMap<String, String>(24)
            r.beginObject()
            while (r.hasNext()) {
                val key = r.nextName()
                when (r.peek()) {
                    JsonToken.STRING, JsonToken.NUMBER -> m[key] = r.nextString()
                    JsonToken.BOOLEAN -> m[key] = r.nextBoolean().toString()
                    JsonToken.NULL -> r.nextNull()
                    else -> r.skipValue()
                }
            }
            r.endObject()
            block(m)
        }
    }
}

object Xtream {
    /** Maakt van wat iemand intypt ("host:8080", "http://host/player_api.php") een nette basis-url. */
    fun normalizeServer(input: String): String {
        var s = input.trim().trimEnd('/')
        if (!s.startsWith("http://") && !s.startsWith("https://")) s = "http://$s"
        for (suffix in listOf("/player_api.php", "/get.php", "/c")) if (s.endsWith(suffix)) s = s.removeSuffix(suffix)
        return s.trimEnd('/')
    }

    fun api(p: Profile, action: String? = null, extra: String = ""): String =
        "${p.server}/player_api.php?username=${enc(p.username)}&password=${enc(p.password)}" +
            (if (action != null) "&action=$action" else "") + extra

    fun epgUrl(p: Profile) = p.epgUrl.ifBlank { "${p.server}/xmltv.php?username=${enc(p.username)}&password=${enc(p.password)}" }

    /** Controleert de login; geeft een foutmelding terug of null als het goed is. */
    fun checkLogin(p: Profile): String? = try {
        val o = JSONObject(Net.get(api(p)))
        val info = o.optJSONObject("user_info")
        when {
            info == null -> "Server gaf geen geldig antwoord"
            info.optString("auth") != "1" -> "Gebruikersnaam of wachtwoord klopt niet"
            info.optString("status").let { it.isNotBlank() && !it.equals("Active", true) } ->
                "Abonnement is niet actief (${info.optString("status")})"
            else -> null
        }
    } catch (e: Exception) {
        "Kan server niet bereiken: ${e.message ?: e.javaClass.simpleName}"
    }

    val files = listOf(
        "get_live_categories", "get_live_streams",
        "get_vod_categories", "get_vod_streams",
        "get_series_categories", "get_series",
    )

    fun parse(p: Profile, dir: File): Catalog {
        fun cats(name: String): List<Category> {
            val out = ArrayList<Category>()
            forEachJsonObject(File(dir, "$name.json")) { m ->
                val id = m["category_id"] ?: return@forEachJsonObject
                out += Category(id, m["category_name"].orEmpty().trim())
            }
            return out
        }
        val live = cats("get_live_categories")
        val channels = ArrayList<Channel>(8192)
        forEachJsonObject(File(dir, "get_live_streams.json")) { m ->
            val id = m["stream_id"] ?: return@forEachJsonObject
            channels += Channel(
                id = id,
                name = m["name"].orEmpty().trim(),
                logo = m["stream_icon"]?.takeIf { it.isNotBlank() },
                categoryId = m["category_id"].orEmpty(),
                url = "${p.server}/live/${p.username}/${p.password}/$id.${p.liveFormat}",
                epgId = m["epg_channel_id"]?.takeIf { it.isNotBlank() },
                archiveDays = if (m["tv_archive"] == "1") m["tv_archive_duration"]?.toIntOrNull() ?: 1 else 0,
            )
        }
        val movieCats = cats("get_vod_categories")
        val movies = ArrayList<Movie>(8192)
        forEachJsonObject(File(dir, "get_vod_streams.json")) { m ->
            val id = m["stream_id"] ?: return@forEachJsonObject
            val ext = m["container_extension"]?.takeIf { it.isNotBlank() } ?: "mp4"
            movies += Movie(
                id = id,
                name = m["name"].orEmpty().trim(),
                poster = m["stream_icon"]?.takeIf { it.isNotBlank() },
                categoryId = m["category_id"].orEmpty(),
                url = "${p.server}/movie/${p.username}/${p.password}/$id.$ext",
                rating = m["rating"]?.takeIf { it.isNotBlank() && it != "0" },
                added = m["added"]?.toLongOrNull() ?: 0,
            )
        }
        val showCats = cats("get_series_categories")
        val shows = ArrayList<Show>(4096)
        forEachJsonObject(File(dir, "get_series.json")) { m ->
            val id = m["series_id"] ?: return@forEachJsonObject
            shows += Show(
                id = id,
                name = m["name"].orEmpty().trim(),
                poster = m["cover"]?.takeIf { it.isNotBlank() },
                categoryId = m["category_id"].orEmpty(),
                plot = m["plot"]?.takeIf { it.isNotBlank() },
                rating = m["rating"]?.takeIf { it.isNotBlank() && it != "0" },
                updated = m["last_modified"]?.toLongOrNull() ?: 0,
            )
        }
        return Catalog(live, channels, movieCats, movies, showCats, shows)
    }

    fun episodes(p: Profile, seriesId: String): List<Episode> {
        val o = JSONObject(Net.get(api(p, "get_series_info", "&series_id=$seriesId")))
        val out = ArrayList<Episode>()
        fun add(e: JSONObject, seasonKey: Int) {
            val id = e.optString("id").ifBlank { return }
            val ext = e.optString("container_extension").ifBlank { "mp4" }
            val info = e.optJSONObject("info")
            out += Episode(
                id = id,
                season = e.optString("season").toIntOrNull() ?: seasonKey,
                number = e.optString("episode_num").toIntOrNull() ?: 0,
                title = e.optString("title").trim(),
                url = "${p.server}/series/${p.username}/${p.password}/$id.$ext",
                plot = info?.optString("plot")?.takeIf { it.isNotBlank() },
                image = info?.optString("movie_image")?.takeIf { it.isNotBlank() },
                duration = info?.optString("duration")?.takeIf { it.isNotBlank() },
            )
        }
        when (val eps = o.opt("episodes")) {
            is JSONObject -> eps.keys().forEach { key ->
                val list = eps.optJSONArray(key) ?: return@forEach
                for (i in 0 until list.length()) list.optJSONObject(i)?.let { add(it, key.toIntOrNull() ?: 0) }
            }
            is JSONArray -> for (i in 0 until eps.length()) {
                when (val item = eps.opt(i)) {
                    is JSONArray -> for (j in 0 until item.length()) item.optJSONObject(j)?.let { add(it, i + 1) }
                    is JSONObject -> add(item, 0)
                }
            }
        }
        return out.sortedWith(compareBy({ it.season }, { it.number }))
    }

    /**
     * Terugkijken: Xtream biedt dit via /timeshift/ voor kanalen met tv_archive.
     * De starttijd gaat in de tijdzone van het apparaat, eventueel gecorrigeerd in Geavanceerd.
     */
    fun catchupUrl(p: Profile, channel: Channel, start: Long, stop: Long): String {
        val minutes = ((stop - start) / 60_000).coerceAtLeast(1)
        val fmt = java.text.SimpleDateFormat("yyyy-MM-dd:HH-mm", java.util.Locale.US)
        val at = fmt.format(java.util.Date(start + Settings.catchupShift.int * 3600_000L))
        return "${p.server}/timeshift/${p.username}/${p.password}/$minutes/$at/${channel.id}.ts"
    }

    fun moviePlot(p: Profile, movieId: String): String? = runCatching {
        JSONObject(Net.get(api(p, "get_vod_info", "&vod_id=$movieId")))
            .optJSONObject("info")?.optString("plot")?.takeIf { it.isNotBlank() }
    }.getOrNull()
}

object M3u {
    private val attr = Regex("""([\w-]+)="([^"]*)"""")

    class Result(val catalog: Catalog, val epgUrl: String?)

    fun parse(file: File): Result {
        val liveCats = LinkedHashMap<String, Category>()
        val movieCats = LinkedHashMap<String, Category>()
        val channels = ArrayList<Channel>(8192)
        val movies = ArrayList<Movie>(4096)
        var epgUrl: String? = null
        var attrs: Map<String, String> = emptyMap()
        var title = ""
        var index = 0L
        if (!file.exists()) return Result(Catalog(), null)
        file.bufferedReader(Charsets.UTF_8).useLines { lines ->
            for (raw in lines) {
                val line = raw.trim()
                when {
                    line.startsWith("#EXTM3U") -> {
                        val a = attr.findAll(line).associate { it.groupValues[1] to it.groupValues[2] }
                        epgUrl = (a["url-tvg"] ?: a["x-tvg-url"])?.split(',')?.firstOrNull()?.trim()
                    }
                    line.startsWith("#EXTINF") -> {
                        attrs = attr.findAll(line).associate { it.groupValues[1] to it.groupValues[2] }
                        title = line.substringAfterLast("\",", line.substringAfter(',')).trim()
                        if (title.isEmpty()) title = attrs["tvg-name"].orEmpty()
                    }
                    line.startsWith("#EXTGRP:") && attrs["group-title"] == null ->
                        attrs = attrs + ("group-title" to line.removePrefix("#EXTGRP:").trim())
                    line.isEmpty() || line.startsWith("#") -> Unit
                    else -> {
                        val group = attrs["group-title"]?.takeIf { it.isNotBlank() } ?: "Overig"
                        val logo = attrs["tvg-logo"]?.takeIf { it.isNotBlank() }
                        val isVod = line.contains("/movie/") || line.contains("/series/") ||
                            Regex("""\.(mp4|mkv|avi)(\?|$)""", RegexOption.IGNORE_CASE).containsMatchIn(line)
                        if (isVod) {
                            movieCats.getOrPut(group) { Category(group, group) }
                            movies += Movie(line, title, logo, group, line, null, index++)
                        } else {
                            liveCats.getOrPut(group) { Category(group, group) }
                            channels += Channel(line, title, logo, group, line, attrs["tvg-id"]?.takeIf { it.isNotBlank() } ?: title)
                        }
                        attrs = emptyMap(); title = ""
                    }
                }
            }
        }
        return Result(Catalog(liveCats.values.toList(), channels, movieCats.values.toList(), movies), epgUrl)
    }
}
