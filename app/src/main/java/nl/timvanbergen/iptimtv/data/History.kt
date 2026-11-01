package nl.timvanbergen.iptimtv.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateListOf
import org.json.JSONArray
import org.json.JSONObject

/**
 * Wat je gekeken hebt en waar je was. Lokaal bewaard en (met account) ook online,
 * zodat "Verder kijken" op elk apparaat klopt.
 *
 * Sleutels: "movie:<id>.<ext>", "episode:<id>.<ext>" (parent = serie-id), "live:<kanaal-id>",
 * "rec:<opname-id>" en "m3u:<hash>". Er staan nooit stream-adressen in.
 */
data class WatchItem(
    val key: String,
    val kind: String,
    val title: String,
    val subtitle: String? = null,
    val image: String? = null,
    val parent: String? = null,
    val position: Long = 0,
    val duration: Long = 0,
    val updated: Long = System.currentTimeMillis(),
) {
    /** Half bekeken: minstens een minuut gezien en nog niet bij de aftiteling. */
    val unfinished get() = position > 60_000 && (duration <= 0 || position < duration - 3 * 60_000)
    val progress get() = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f

    fun toJson(): JSONObject = JSONObject()
        .put("key", key).put("kind", kind).put("title", title).put("subtitle", subtitle).put("image", image)
        .put("parent", parent).put("position", position).put("duration", duration).put("updated", updated)

    companion object {
        fun fromJson(o: JSONObject) = WatchItem(
            key = o.getString("key"),
            kind = o.optString("kind"),
            title = o.optString("title"),
            subtitle = o.optString("subtitle").takeIf { it.isNotBlank() && it != "null" },
            image = o.optString("image").takeIf { it.isNotBlank() && it != "null" },
            parent = o.optString("parent").takeIf { it.isNotBlank() && it != "null" },
            position = o.optLong("position"),
            duration = o.optLong("duration"),
            updated = o.optLong("updated"),
        )
    }
}

object History {
    private lateinit var prefs: SharedPreferences
    private var profileId: String? = null
    val items = mutableStateListOf<WatchItem>()

    fun init(context: Context) {
        prefs = context.getSharedPreferences("history", Context.MODE_PRIVATE)
    }

    /** Elke login heeft zijn eigen geschiedenis (de id's verschillen per provider). */
    fun load(profile: Profile?) {
        profileId = profile?.id
        items.clear()
        val raw = profileId?.let { prefs.getString(it, null) } ?: return
        runCatching { JSONArray(raw) }.getOrNull()?.let { a ->
            for (i in 0 until a.length()) items += WatchItem.fromJson(a.getJSONObject(i))
        }
    }

    private fun save() {
        val id = profileId ?: return
        prefs.edit().putString(id, JSONArray(items.take(150).map { it.toJson() }).toString()).apply()
    }

    fun get(key: String) = items.firstOrNull { it.key == key }

    fun update(item: WatchItem) {
        items.removeAll { it.key == item.key }
        items.add(0, item)
        save()
    }

    fun remove(key: String) {
        items.removeAll { it.key == key }
        save()
        Cloud.forget(key)
    }

    fun clear() {
        items.clear()
        save()
    }

    /** Nieuwere online gegevens (van een ander apparaat) overnemen. */
    fun merge(remote: List<WatchItem>) {
        var changed = false
        remote.forEach { r ->
            val local = get(r.key)
            if (local == null || r.updated > local.updated) {
                items.removeAll { it.key == r.key }
                items += r
                changed = true
            }
        }
        if (changed) {
            val sorted = items.sortedByDescending { it.updated }
            items.clear(); items.addAll(sorted)
            save()
        }
    }

    val continueWatching get() = items.filter { (it.kind == "movie" || it.kind == "episode") && it.unfinished }
}
