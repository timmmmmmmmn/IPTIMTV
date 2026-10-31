package nl.timvanbergen.iptimtv.data

import org.json.JSONArray
import org.json.JSONObject

enum class SourceType { XTREAM, M3U }

/** Eén IPTV-login. Bij Xtream zijn server/gebruiker/wachtwoord gevuld, bij M3U de playlist-url. */
data class Profile(
    val id: String,
    val name: String,
    val type: SourceType,
    val server: String = "",
    val username: String = "",
    val password: String = "",
    val m3uUrl: String = "",
    val epgUrl: String = "",
    /** "ts" of "m3u8": het formaat waarin live kanalen bij Xtream worden opgevraagd. */
    val liveFormat: String = "ts",
) {
    fun toJson() = JSONObject()
        .put("id", id).put("name", name).put("type", type.name)
        .put("server", server).put("username", username).put("password", password)
        .put("m3uUrl", m3uUrl).put("epgUrl", epgUrl).put("liveFormat", liveFormat)

    companion object {
        fun fromJson(o: JSONObject) = Profile(
            id = o.getString("id"),
            name = o.optString("name"),
            type = runCatching { SourceType.valueOf(o.optString("type")) }.getOrDefault(SourceType.XTREAM),
            server = o.optString("server"),
            username = o.optString("username"),
            password = o.optString("password"),
            m3uUrl = o.optString("m3uUrl"),
            epgUrl = o.optString("epgUrl"),
            liveFormat = o.optString("liveFormat", "ts").ifBlank { "ts" },
        )
    }
}

data class Category(val id: String, val name: String)

data class Channel(
    val id: String,
    val name: String,
    val logo: String?,
    val categoryId: String,
    val url: String,
    val epgId: String?,
)

data class Movie(
    val id: String,
    val name: String,
    val poster: String?,
    val categoryId: String,
    val url: String,
    val rating: String?,
    val added: Long,
)

data class Show(
    val id: String,
    val name: String,
    val poster: String?,
    val categoryId: String,
    val plot: String?,
    val rating: String?,
    val updated: Long,
)

data class Episode(
    val id: String,
    val season: Int,
    val number: Int,
    val title: String,
    val url: String,
    val plot: String?,
    val image: String?,
    val duration: String?,
)

data class Program(val channelKey: String, val start: Long, val stop: Long, val title: String, val description: String?) {
    fun isLive(now: Long = System.currentTimeMillis()) = now in start until stop
    fun progress(now: Long = System.currentTimeMillis()) =
        if (stop <= start) 0f else ((now - start).toFloat() / (stop - start)).coerceIn(0f, 1f)
}

/** Een groep kanalen die de gebruiker zelf op het startscherm heeft gezet. */
data class UserGroup(val id: String, val name: String, val channelIds: List<String>) {
    fun toJson() = JSONObject().put("id", id).put("name", name).put("channels", JSONArray(channelIds))

    companion object {
        fun fromJson(o: JSONObject) = UserGroup(
            id = o.getString("id"),
            name = o.optString("name"),
            channelIds = o.optJSONArray("channels").strings(),
        )
    }
}

fun JSONArray?.strings(): List<String> =
    if (this == null) emptyList() else List(length()) { optString(it) }

const val NEW_CATEGORY = "__nieuw"

class Catalog(
    val liveCategories: List<Category> = emptyList(),
    val channels: List<Channel> = emptyList(),
    val movieCategories: List<Category> = emptyList(),
    val movies: List<Movie> = emptyList(),
    val showCategories: List<Category> = emptyList(),
    val shows: List<Show> = emptyList(),
) {
    val channelById: Map<String, Channel> by lazy { channels.associateBy { it.id } }
    val channelsByCategory: Map<String, List<Channel>> by lazy { channels.groupBy { it.categoryId } }
    val moviesByCategory: Map<String, List<Movie>> by lazy {
        movies.groupBy { it.categoryId } + (NEW_CATEGORY to movies.sortedByDescending { it.added }.take(200))
    }
    val showsByCategory: Map<String, List<Show>> by lazy {
        shows.groupBy { it.categoryId } + (NEW_CATEGORY to shows.sortedByDescending { it.updated }.take(200))
    }
    /** Film- en seriecategorieën met bovenaan "Nieuw toegevoegd" (alleen als er iets in zit). */
    val movieCategoriesWithNew: List<Category> by lazy {
        if (movies.isEmpty()) movieCategories else listOf(Category(NEW_CATEGORY, "Nieuw toegevoegd")) + movieCategories
    }
    val showCategoriesWithNew: List<Category> by lazy {
        if (shows.isEmpty()) showCategories else listOf(Category(NEW_CATEGORY, "Nieuw toegevoegd")) + showCategories
    }
    val isEmpty get() = channels.isEmpty() && movies.isEmpty() && shows.isEmpty()
}
