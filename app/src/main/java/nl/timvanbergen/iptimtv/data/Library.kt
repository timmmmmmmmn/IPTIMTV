package nl.timvanbergen.iptimtv.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * Alles wat de app weet: de logins, de kanalen/films/series van de actieve login,
 * en wat de gebruiker zelf heeft ingesteld (favorieten, eigen groepen, volgorde).
 */
object Library {
    private lateinit var app: Context
    private lateinit var prefs: SharedPreferences
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var loadJob: Job? = null

    val profiles = mutableStateListOf<Profile>()
    var active by mutableStateOf<Profile?>(null); private set
    var catalog by mutableStateOf(Catalog()); private set
    var epg by mutableStateOf<Epg?>(null); private set
    /** Wordt opgehoogd zodra de gids opnieuw is ingelezen, zodat schermen opnieuw kijken. */
    var epgVersion by mutableStateOf(0); private set

    var loading by mutableStateOf<String?>(null); private set
    var epgLoading by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null)

    val favorites = mutableStateListOf<String>()
    val groups = mutableStateListOf<UserGroup>()
    private val liveOrder = mutableStateListOf<String>()
    val hiddenCategories = mutableStateListOf<String>()


    fun init(context: Context) {
        if (::app.isInitialized) return
        app = context.applicationContext
        prefs = app.getSharedPreferences("iptimtv", Context.MODE_PRIVATE)
        profiles.addAll(JSONArray(prefs.getString("profiles", "[]")).let { a ->
            List(a.length()) { Profile.fromJson(a.getJSONObject(it)) }
        })
        val activeId = prefs.getString("active", null)
        (profiles.firstOrNull { it.id == activeId } ?: profiles.firstOrNull())?.let { activate(it) }
    }

    // ---------- logins ----------

    fun newId(): String = UUID.randomUUID().toString().take(8)

    fun saveProfile(p: Profile) {
        val i = profiles.indexOfFirst { it.id == p.id }
        if (i >= 0) profiles[i] = p else profiles += p
        persistProfiles()
        // Een nieuwe login gaat meteen in gebruik; een gewijzigde alleen als hij al actief was.
        if (i < 0 || active?.id == p.id) {
            dir(p).listFiles()?.forEach { if (it.isFile) it.delete() }
            activate(p, forceRefresh = true)
        }
    }

    fun deleteProfile(p: Profile) {
        profiles.removeAll { it.id == p.id }
        persistProfiles()
        dir(p).deleteRecursively()
        app.deleteDatabase("epg_${p.id}.db")
        app.getSharedPreferences("user_${p.id}", Context.MODE_PRIVATE).edit().clear().apply()
        if (active?.id == p.id) {
            active = null
            catalog = Catalog()
            epg = null
            profiles.firstOrNull()?.let { activate(it) }
        }
    }

    private fun persistProfiles(fromSync: Boolean = false) {
        prefs.edit().putString("profiles", JSONArray(profiles.map { it.toJson() }).toString()).apply()
        if (!fromSync) Sync.changed("profiles", JSONArray(profiles.map { it.toJson() }))
    }

    // ---------- synchronisatie met je IPTIMTV-account ----------

    fun profilesJson() = JSONArray(profiles.map { it.toJson() })

    /** Logins uit je account overnemen (van een ander apparaat of de website). */
    fun applyProfiles(a: JSONArray) {
        val list = List(a.length()) { Profile.fromJson(a.getJSONObject(it)) }
        val before = profiles.toList()
        profiles.clear(); profiles.addAll(list)
        persistProfiles(fromSync = true)
        val current = active
        when {
            current == null || list.none { it.id == current.id } -> list.firstOrNull()?.let { activate(it) } ?: run {
                active = null; catalog = Catalog()
            }
            // Gegevens van de actieve login gewijzigd (bijv. nieuw wachtwoord): opnieuw laden.
            before.firstOrNull { it.id == current.id } != list.first { it.id == current.id } -> activate(list.first { it.id == current.id }, forceRefresh = true)
        }
    }

    fun userDataJson(profileId: String): JSONObject {
        val u = app.getSharedPreferences("user_$profileId", Context.MODE_PRIVATE)
        return JSONObject()
            .put("favorites", JSONArray(u.getString("favorites", "[]")))
            .put("groups", JSONArray(u.getString("groups", "[]")))
            .put("liveOrder", JSONArray(u.getString("liveOrder", "[]")))
            .put("hidden", JSONArray(u.getString("hidden", "[]")))
    }

    fun applyUserData(profileId: String, o: JSONObject) {
        app.getSharedPreferences("user_$profileId", Context.MODE_PRIVATE).edit()
            .putString("favorites", (o.optJSONArray("favorites") ?: JSONArray()).toString())
            .putString("groups", (o.optJSONArray("groups") ?: JSONArray()).toString())
            .putString("liveOrder", (o.optJSONArray("liveOrder") ?: JSONArray()).toString())
            .putString("hidden", (o.optJSONArray("hidden") ?: JSONArray()).toString())
            .apply()
        active?.takeIf { it.id == profileId }?.let { loadUserData(it) }
    }

    fun activate(p: Profile, forceRefresh: Boolean = false) {
        active = p
        prefs.edit().putString("active", p.id).apply()
        loadUserData(p)
        History.load(p)
        epg?.close()
        epg = Epg(app, p.id)
        catalog = Catalog()
        error = null
        loadJob?.cancel()
        loadJob = scope.launch {
            val dir = dir(p)
            val hasCache = dir.listFiles()?.any { it.isFile && !it.name.endsWith(".tmp") } == true
            if (hasCache && !forceRefresh) {
                loading = "Laden…"
                catalog = withContext(Dispatchers.IO) { runCatching { parse(p) }.getOrDefault(Catalog()) }
                loading = null
            }
            val stale = System.currentTimeMillis() - prefs.getLong("updated_${p.id}", 0) > Settings.catalogRefresh.int * 3600_000L
            if (forceRefresh || catalog.isEmpty || stale) refreshNow(p, showProgress = catalog.isEmpty)
            val epgStale = System.currentTimeMillis() - prefs.getLong("epg_${p.id}", 0) > Settings.epgRefresh.int * 3600_000L
            if (forceRefresh || epgStale || withContext(Dispatchers.IO) { epg?.isEmpty() == true }) refreshEpg(p)
        }
    }

    /** Alleen de gids opnieuw ophalen (vanuit Geavanceerd). */
    fun refreshGuide() {
        val p = active ?: return
        scope.launch { refreshEpg(p) }
    }

    /** Handmatig alles opnieuw ophalen (vanuit Instellingen). */
    fun refresh() {
        val p = active ?: return
        loadJob?.cancel()
        loadJob = scope.launch {
            refreshNow(p, showProgress = true)
            refreshEpg(p)
        }
    }

    private suspend fun refreshNow(p: Profile, showProgress: Boolean) {
        if (showProgress) loading = "Kanalen en films ophalen…"
        try {
            withContext(Dispatchers.IO) {
                val dir = dir(p)
                when (p.type) {
                    SourceType.XTREAM -> coroutineScope {
                        Xtream.files.map { name ->
                            async { Net.download(Xtream.api(p, name), File(dir, "$name.json")) }
                        }.awaitAll()
                    }
                    SourceType.M3U -> Net.download(p.m3uUrl, File(dir, "playlist.m3u"))
                }
            }
            if (showProgress) loading = "Lijsten verwerken…"
            catalog = withContext(Dispatchers.IO) { parse(p) }
            prefs.edit().putLong("updated_${p.id}", System.currentTimeMillis()).apply()
            error = null
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            error = "Kon gegevens niet ophalen: ${e.message ?: e.javaClass.simpleName}"
        } finally {
            loading = null
        }
    }

    private suspend fun refreshEpg(p: Profile) {
        val url = when (p.type) {
            SourceType.XTREAM -> Xtream.epgUrl(p)
            SourceType.M3U -> p.epgUrl.ifBlank { prefs.getString("m3uEpg_${p.id}", null) ?: return }
        }
        val wanted = catalog.channels.mapNotNullTo(HashSet()) { it.epgId?.lowercase() }
        if (wanted.isEmpty()) return
        epgLoading = true
        try {
            withContext(Dispatchers.IO) { Net.open(url).use { epg?.import(it, wanted) } }
            prefs.edit().putLong("epg_${p.id}", System.currentTimeMillis()).apply()
            epgVersion++
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
            // Geen gids is geen ramp; de kanalen werken gewoon.
        } finally {
            epgLoading = false
        }
    }

    private fun parse(p: Profile): Catalog = when (p.type) {
        SourceType.XTREAM -> Xtream.parse(p, dir(p))
        SourceType.M3U -> M3u.parse(File(dir(p), "playlist.m3u")).also { r ->
            r.epgUrl?.let { prefs.edit().putString("m3uEpg_${p.id}", it).apply() }
        }.catalog
    }

    private fun dir(p: Profile) = File(app.filesDir, "profiles/${p.id}").apply { mkdirs() }

    // ---------- eigen instellingen per login ----------

    private fun userPrefs(p: Profile) = app.getSharedPreferences("user_${p.id}", Context.MODE_PRIVATE)

    private fun loadUserData(p: Profile) {
        val u = userPrefs(p)
        favorites.clear(); favorites.addAll(JSONArray(u.getString("favorites", "[]")).strings())
        groups.clear()
        JSONArray(u.getString("groups", "[]")).let { a ->
            for (i in 0 until a.length()) groups += UserGroup.fromJson(a.getJSONObject(i))
        }
        liveOrder.clear(); liveOrder.addAll(JSONArray(u.getString("liveOrder", "[]")).strings())
        hiddenCategories.clear(); hiddenCategories.addAll(JSONArray(u.getString("hidden", "[]")).strings())
    }

    private fun saveUserData() {
        val p = active ?: return
        userPrefs(p).edit()
            .putString("favorites", JSONArray(favorites.toList()).toString())
            .putString("groups", JSONArray(groups.map { it.toJson() }).toString())
            .putString("liveOrder", JSONArray(liveOrder.toList()).toString())
            .putString("hidden", JSONArray(hiddenCategories.toList()).toString())
            .apply()
        Sync.changed("user:${p.id}", userDataJson(p.id))
    }

    fun setFavorites(ids: List<String>) {
        favorites.clear(); favorites.addAll(ids); saveUserData()
    }

    fun toggleFavorite(id: String) {
        if (!favorites.remove(id)) favorites += id
        saveUserData()
    }

    fun addGroup(name: String): UserGroup {
        val g = UserGroup(newId(), name.trim().ifBlank { "Nieuwe groep" }, emptyList())
        groups += g; saveUserData()
        return g
    }

    fun updateGroup(g: UserGroup) {
        val i = groups.indexOfFirst { it.id == g.id }
        if (i >= 0) groups[i] = g
        saveUserData()
    }

    fun deleteGroup(id: String) {
        groups.removeAll { it.id == id }; saveUserData()
    }

    fun moveGroup(id: String, delta: Int) {
        val i = groups.indexOfFirst { it.id == id }
        val j = i + delta
        if (i < 0 || j !in groups.indices) return
        groups.add(j, groups.removeAt(i)); saveUserData()
    }

    /** Providergroepen in de volgorde die de gebruiker heeft gekozen (nieuwe groepen achteraan). */
    fun liveCategories(includeHidden: Boolean = false): List<Category> {
        val all = catalog.liveCategories
        val byId = all.associateBy { it.id }
        val ordered = liveOrder.mapNotNull { byId[it] }
        val seen = ordered.mapTo(HashSet()) { it.id }
        val result = ordered + all.filter { it.id !in seen }
        return if (includeHidden) result else result.filter { it.id !in hiddenCategories }
    }

    fun setLiveOrder(ids: List<String>) {
        liveOrder.clear(); liveOrder.addAll(ids); saveUserData()
    }

    fun toggleHidden(id: String) {
        if (!hiddenCategories.remove(id)) hiddenCategories += id
        saveUserData()
    }

    fun channels(ids: List<String>): List<Channel> = ids.mapNotNull { catalog.channelById[it] }

    fun lastChannel(): String? = active?.let { prefs.getString("last_${it.id}", null) }
    fun setLastChannel(id: String) {
        active?.let { prefs.edit().putString("last_${it.id}", id).apply() }
    }
}
