package nl.timvanbergen.iptimtv.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.text.Normalizer
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Een competitie; [country] is het land waar hij speelt (null = Europees, dan telt Nederland als eigen land). */
class League(val id: String, val name: String, val country: String?)

class Team(val name: String, val short: String, val logo: String?, val score: String?)

class Match(
    val league: League,
    val home: Team,
    val away: Team,
    val start: Long,
    /** "pre", "in" of "post" */
    val state: String,
    val detail: String,
) {
    val live get() = state == "in"
}

/** Een kanaal waarop een wedstrijd (waarschijnlijk) te zien is, met de reden waarom. */
class SportHit(val channel: Channel, val reason: String, val score: Int, val program: Program?, val country: String? = null)

/** Wedstrijden van vandaag (via de openbare ESPN-uitslagen) en zoeken naar zenders die ze uitzenden. */
object Sport {
    val leagues = listOf(
        League("ned.1", "Eredivisie", "NL"),
        League("ned.2", "Keuken Kampioen Divisie", "NL"),
        League("ned.cup", "KNVB Beker", "NL"),
        League("uefa.champions", "Champions League", null),
        League("uefa.europa", "Europa League", null),
        League("uefa.europa.conf", "Conference League", null),
        League("eng.1", "Premier League", "UK"),
        League("esp.1", "La Liga", "ES"),
        League("ita.1", "Serie A", "IT"),
        League("ger.1", "Bundesliga", "DE"),
        League("fra.1", "Ligue 1", "FR"),
        League("por.1", "Liga Portugal", "PT"),
        League("eng.fa", "FA Cup", "UK"),
        League("uefa.nations", "Nations League", null),
    )

    suspend fun today(date: LocalDate = LocalDate.now()): List<Match> = withContext(Dispatchers.IO) {
        val day = date.format(DateTimeFormatter.BASIC_ISO_DATE)
        coroutineScope {
            leagues.map { l ->
                async {
                    runCatching {
                        val o = JSONObject(Net.get("https://site.api.espn.com/apis/site/v2/sports/soccer/${l.id}/scoreboard?dates=$day"))
                        val events = o.optJSONArray("events") ?: return@runCatching emptyList()
                        List(events.length()) { i ->
                            val e = events.getJSONObject(i)
                            val c = e.getJSONArray("competitions").getJSONObject(0)
                            val teams = c.getJSONArray("competitors")
                            fun team(side: String): Team {
                                for (j in 0 until teams.length()) {
                                    val t = teams.getJSONObject(j)
                                    if (t.optString("homeAway") == side) {
                                        val tt = t.getJSONObject("team")
                                        return Team(tt.optString("displayName"), tt.optString("shortDisplayName"),
                                            tt.optString("logo").takeIf { it.isNotBlank() }, t.optString("score").takeIf { it.isNotBlank() })
                                    }
                                }
                                return Team("?", "?", null, null)
                            }
                            val status = e.getJSONObject("status").getJSONObject("type")
                            Match(l, team("home"), team("away"), Instant.parse(e.getString("date").let { if (it.length == 17) it.dropLast(1) + ":00Z" else it }).toEpochMilli(),
                                status.optString("state"), status.optString("shortDetail"))
                        }
                    }.getOrDefault(emptyList())
                }
            }.awaitAll().flatten().sortedWith(compareBy({ leagues.indexOf(it.league) }, { it.start }))
        }
    }

    private fun plain(s: String) = Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{M}"), "").lowercase()

    private val noise = setOf("fc", "afc", "sc", "cf", "sv", "as", "ss", "ac", "fk", "sk", "bv", "vv", "cd", "rc", "rcd", "ud", "sd", "1.", "calcio", "club", "de")

    // Eerste woorden die bij meerdere clubs horen; daar zoeken we niet los op.
    private val ambiguous = setOf("manchester", "real", "athletic", "atletico", "inter", "sporting", "borussia", "olympique",
        "racing", "dynamo", "union", "young", "royal", "stade", "west", "sheffield", "nottingham", "queens")

    /** Namen waarop we een club zoeken: volledige naam, korte naam, de kern zonder "FC" e.d. en het eerste kernwoord. */
    fun names(t: Team): List<String> {
        val words = plain(t.name).split(' ', '-').filter { it.isNotBlank() && it !in noise }
        val core = words.joinToString(" ")
        val first = words.firstOrNull()?.takeIf { it.length >= 5 && it !in ambiguous && words.size > 1 }
        return listOfNotNull(plain(t.name), plain(t.short), core, first).filter { it.length >= 2 }.distinct()
    }

    /** Komt een van de namen als heel woord voor in de tekst? ("az" wel in "AZ - Ajax", niet in "Lazio") */
    private fun mentions(text: String, names: List<String>) = names.any { n ->
        Regex("(^|[^a-z0-9])" + Regex.escape(n) + "($|[^a-z0-9])").containsMatchIn(text)
    }

    // Landcodes zoals providers ze voor kanalen en groepen zetten ("NL | ESPN 1", "UK: Sky Sports", "[AR] ...").
    private val countryWords = mapOf(
        "NL" to listOf("nl", "nld", "netherlands", "nederland", "holland", "dutch"),
        "BE" to listOf("be", "bel", "belgium", "belgie", "vlaanderen", "flemish"),
        "UK" to listOf("uk", "gb", "en", "eng", "united kingdom", "england", "english"),
        "DE" to listOf("de", "ger", "germany", "deutschland"),
        "ES" to listOf("es", "esp", "spain", "espana"),
        "IT" to listOf("it", "ita", "italy", "italia"),
        "FR" to listOf("fr", "fra", "france"),
        "PT" to listOf("pt", "por", "portugal"),
        "US" to listOf("us", "usa"),
        "AR" to listOf("ar", "arg", "argentina"),
        "TR" to listOf("tr", "tur", "turkey", "turkiye"),
        "AU" to listOf("au", "aus", "australia"),
    )
    // Merken die maar in één land zitten.
    private val brands = mapOf(
        "NL" to listOf("ziggo", "npo", "rtl 4", "rtl 5", "rtl 7", "rtl 8", "sbs", "veronica", "espn nl", "viaplay nl"),
        "UK" to listOf("sky sports", "bbc", "itv", "tnt sports", "amazon prime uk"),
        "DE" to listOf("sky sport de", "das erste", "zdf", "sportschau"),
    )

    /** Het land van een kanaal, uit de kanaalnaam of de providergroep. */
    fun countryOf(ch: Channel, catalog: Catalog): String? {
        val group = catalog.liveCategories.firstOrNull { it.id == ch.categoryId }?.name.orEmpty()
        for (text in listOf(ch.name, group)) {
            val t = plain(text)
            // Voorvoegsel zoals "nl |", "uk:", "[de]", "nl-"
            Regex("""^\W*([a-z]{2,3})\s*[|:\]\-/]""").find(t)?.groupValues?.get(1)?.let { code ->
                countryWords.entries.firstOrNull { code in it.value }?.let { return it.key }
            }
            countryWords.entries.firstOrNull { (_, words) -> words.any { w -> w.length > 3 && w in t } }?.let { return it.key }
        }
        val n = plain(ch.name)
        return brands.entries.firstOrNull { (_, b) -> b.any { it in n } }?.key
    }

    /**
     * Zoekt kanalen die de wedstrijd (vrijwel zeker) uitzenden:
     * een programma in de gids rond de aftrap met beide clubs in de titel, of een eventkanaal met beide clubs in de naam.
     * Volgorde: kanalen uit het land van de competitie, dan Nederland/Engeland, dan de rest.
     */
    suspend fun channelsFor(m: Match, catalog: Catalog, epg: Epg?): List<SportHit> = withContext(Dispatchers.Default) {
        val homeNames = names(m.home)
        val awayNames = names(m.away)
        val hits = HashMap<String, SportHit>()
        fun add(ch: Channel, reason: String, score: Int, p: Program?) {
            val old = hits[ch.id]
            if (old == null || score > old.score) hits[ch.id] = SportHit(ch, reason, score, p, countryOf(ch, catalog))
        }

        if (epg != null) {
            val programs = runCatching {
                epg.findInWindow(homeNames + awayNames, m.start - 30 * 60_000, m.start + 130 * 60_000)
            }.getOrDefault(emptyList())
            val byEpg = catalog.channels.filter { it.epgId != null }.groupBy { it.epgId!!.lowercase() }
            programs.forEach { p ->
                val t = plain(p.title + " " + (p.description ?: ""))
                val both = mentions(t, homeNames) && mentions(t, awayNames)
                // Rond de aftrap begonnen (live-uitzending, geen samenvatting later op de dag).
                val onTime = p.start in (m.start - 45 * 60_000)..(m.start + 20 * 60_000)
                if (both && onTime) byEpg[p.channelKey]?.forEach { add(it, "In de gids: ${p.title} (${clock(p.start)})", 100, p) }
            }
        }
        catalog.channels.forEach { ch ->
            val n = plain(ch.name)
            if (mentions(n, homeNames) && mentions(n, awayNames)) add(ch, "Kanaal speciaal voor deze wedstrijd", 90, null)
        }

        val home = m.league.country ?: "NL"
        fun tier(c: String?) = when (c) {
            home -> 0
            "NL", "UK" -> 1
            else -> 2
        }
        hits.values.sortedWith(compareBy<SportHit>({ tier(it.country) }, { -it.score }, { it.channel.name })).take(60)
    }

    private fun clock(ms: Long) = java.text.SimpleDateFormat("HH:mm", java.util.Locale("nl")).format(java.util.Date(ms))
}
