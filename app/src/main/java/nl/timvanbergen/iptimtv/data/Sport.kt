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

/**
 * Een competitie; [country] is het land waar hij speelt (null = Europees, dan telt Nederland als eigen land).
 * [words]: hoe de gids hem noemt; [broadcasters]: zenders die hem meestal uitzenden.
 */
class League(val id: String, val name: String, val country: String?, val words: List<String>, val broadcasters: List<String>)

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
    private val uefa = listOf("ziggo sport", "rtl 7", "tnt sports", "amazon prime", "prime video", "sky sport", "sky sports", "canal+",
        "movistar", "dazn", "rmc sport", "bein", "cbs sports", "paramount", "sport tv", "veronica")
    val leagues = listOf(
        League("ned.1", "Eredivisie", "NL", listOf("eredivisie"), listOf("espn")),
        League("ned.2", "Keuken Kampioen Divisie", "NL", listOf("keuken kampioen", "kkd", "eerste divisie"), listOf("espn")),
        League("ned.cup", "KNVB Beker", "NL", listOf("knvb beker", "knvb-beker", "toto knvb"), listOf("espn", "npo 1")),
        League("uefa.champions", "Champions League", null, listOf("champions league", "uefa champions", "ucl"), uefa),
        League("uefa.europa", "Europa League", null, listOf("europa league", "uel"), uefa),
        League("uefa.europa.conf", "Conference League", null, listOf("conference league", "uecl"), uefa),
        League("eng.1", "Premier League", "UK", listOf("premier league", "epl", "barclays"),
            listOf("viaplay", "sky sports", "tnt sports", "amazon prime", "prime video", "canal+", "nbc", "peacock", "usa network",
                "dazn", "bein", "supersport", "optus", "premier league")),
        League("esp.1", "La Liga", "ES", listOf("la liga", "laliga"),
            listOf("ziggo sport", "movistar", "dazn", "laliga tv", "premier sports", "espn", "bein")),
        League("ita.1", "Serie A", "IT", listOf("serie a"), listOf("ziggo sport", "dazn", "sky sport", "tnt sports", "cbs sports", "paramount", "bein")),
        League("ger.1", "Bundesliga", "DE", listOf("bundesliga"), listOf("viaplay", "sky sport", "sky sports", "dazn", "espn", "sport1")),
        League("fra.1", "Ligue 1", "FR", listOf("ligue 1"), listOf("ligue 1+", "dazn", "bein", "canal+", "espn")),
        League("por.1", "Liga Portugal", "PT", listOf("liga portugal", "primeira liga", "liga betclic"), listOf("sport tv", "btv", "benfica tv", "dazn", "eleven")),
        League("eng.fa", "FA Cup", "UK", listOf("fa cup"), listOf("bbc one", "bbc", "itv", "espn", "tnt sports")),
        League("uefa.nations", "Nations League", null, listOf("nations league"),
            listOf("npo 1", "npo", "sky sports", "itv", "bbc", "das erste", "zdf", "rtl", "tf1", "rai", "la 1", "rtp")),
    )

    // Algemene voetbalwoorden in de gids (sterker als er ook "live" bij staat).
    private val footballWords = listOf("voetbal", "football", "soccer", "futbol", "calcio", "fussball", "fußball", "live")

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

    /** Komt een van de namen als heel woord voor? ("az" wel in "AZ - Ajax", niet in "Lazio"); één regex voor de hele lijst. */
    private fun words(names: List<String>) =
        Regex("(^|[^a-z0-9])(" + names.joinToString("|") { Regex.escape(it) } + ")($|[^a-z0-9])")

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
        "MX" to listOf("mx", "mex", "mexico"),
        "BR" to listOf("br", "bra", "brasil", "brazil"),
        "CA" to listOf("ca", "can", "canada"),
        "LATAM" to listOf("latam", "latino"),
        "ARAB" to listOf("ara", "arab", "arabic", "mena"),
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
     * Zoekt kanalen die de wedstrijd uitzenden, van zeker naar waarschijnlijk:
     * beide clubs in de gids rond de aftrap (100), een eventkanaal met beide clubs (90), één club in de gids (75),
     * de competitie in de gids (55), voetbal in de gids op een zender van de competitie (45), en zenders die de competitie
     * meestal uitzenden (30). Binnen elke zekerheid eerst het land van de competitie, dan Nederland/Engeland, dan de rest.
     */
    suspend fun channelsFor(m: Match, catalog: Catalog, epg: Epg?): List<SportHit> = withContext(Dispatchers.Default) {
        val homeRe = words(names(m.home))
        val awayRe = words(names(m.away))
        val leagueRe = words(m.league.words)
        val footballRe = words(footballWords)
        val broadcastRe = words(m.league.broadcasters)
        val league = m.league
        val home = league.country ?: "NL"
        val hits = HashMap<String, SportHit>()
        val countries = HashMap<String, String?>()
        fun country(ch: Channel) = countries.getOrPut(ch.id) { countryOf(ch, catalog) }
        fun add(ch: Channel, reason: String, score: Int, p: Program?) {
            val old = hits[ch.id]
            if (old == null || score > old.score) hits[ch.id] = SportHit(ch, reason, score, p, country(ch))
        }
        fun broadcaster(ch: Channel) = broadcastRe.containsMatchIn(plain(ch.name))

        if (epg != null) {
            val programs = runCatching {
                epg.findInWindow(names(m.home) + names(m.away) + league.words + footballWords, m.start - 60 * 60_000, m.start + 130 * 60_000, limit = 2000)
            }.getOrDefault(emptyList())
            val byEpg = catalog.channels.filter { it.epgId != null }.groupBy { it.epgId!!.lowercase() }
            programs.forEach { p ->
                // Rond de aftrap begonnen, of loopt over de aftrap heen (geen samenvatting later op de dag).
                val onTime = p.start in (m.start - 60 * 60_000)..(m.start + 20 * 60_000) ||
                    (p.start <= m.start && p.stop >= m.start + 30 * 60_000)
                if (!onTime) return@forEach
                val chs = byEpg[p.channelKey] ?: return@forEach
                val t = plain(p.title + " " + (p.description ?: ""))
                val h = homeRe.containsMatchIn(t)
                val a = awayRe.containsMatchIn(t)
                val what = "${p.title} (${clock(p.start)})"
                chs.forEach { ch ->
                    when {
                        h && a -> add(ch, "In de gids: $what", 100, p)
                        h || a -> add(ch, "In de gids: $what", 75, p)
                        leagueRe.containsMatchIn(t) -> add(ch, "${league.name} in de gids: $what", 55, p)
                        footballRe.containsMatchIn(t) && broadcaster(ch) -> add(ch, "Voetbal in de gids: $what", 45, p)
                    }
                }
            }
        }
        catalog.channels.forEach { ch ->
            val n = plain(ch.name)
            if (homeRe.containsMatchIn(n) && awayRe.containsMatchIn(n)) add(ch, "Kanaal speciaal voor deze wedstrijd", 90, null)
            else if (broadcaster(ch) && country(ch).let { it == null || it == home || it == "NL" || it == "UK" || it == league.country }) {
                add(ch, "Zendt de ${league.name} meestal uit", 30, null)
            }
        }

        fun tier(c: String?) = when (c) {
            home -> 0
            "NL", "UK" -> 1
            else -> 2
        }
        fun level(s: Int) = when {
            s >= 75 -> 0
            s >= 45 -> 1
            else -> 2
        }
        hits.values.sortedWith(compareBy<SportHit>({ level(it.score) }, { tier(it.country) }, { -it.score }, { it.channel.name })).take(60)
    }

    private fun clock(ms: Long) = java.text.SimpleDateFormat("HH:mm", java.util.Locale("nl")).format(java.util.Date(ms))
}
