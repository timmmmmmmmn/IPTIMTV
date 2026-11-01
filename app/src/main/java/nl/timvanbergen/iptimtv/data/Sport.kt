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

class League(val id: String, val name: String, val broadcasters: List<String>)

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
class SportHit(val channel: Channel, val reason: String, val score: Int, val program: Program?)

/** Wedstrijden van vandaag (via de openbare ESPN-uitslagen) en zoeken naar zenders die ze uitzenden. */
object Sport {
    val leagues = listOf(
        League("ned.1", "Eredivisie", listOf("ESPN")),
        League("ned.2", "Keuken Kampioen Divisie", listOf("ESPN")),
        League("ned.cup", "KNVB Beker", listOf("ESPN")),
        League("uefa.champions", "Champions League", listOf("Ziggo Sport")),
        League("uefa.europa", "Europa League", listOf("Ziggo Sport")),
        League("uefa.europa.conf", "Conference League", listOf("Ziggo Sport")),
        League("eng.1", "Premier League", listOf("Viaplay", "Sky Sports")),
        League("esp.1", "La Liga", listOf("Ziggo Sport", "DAZN")),
        League("ita.1", "Serie A", listOf("Ziggo Sport", "DAZN")),
        League("ger.1", "Bundesliga", listOf("Viaplay", "Sky Sport")),
        League("fra.1", "Ligue 1", listOf("Ziggo Sport", "DAZN")),
        League("por.1", "Liga Portugal", listOf("Ziggo Sport", "ESPN")),
        League("eng.fa", "FA Cup", listOf("ESPN", "Viaplay")),
        League("uefa.nations", "Nations League", listOf("NOS", "NPO")),
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

    /** Namen waarop we een club zoeken: volledige naam, korte naam en de kern zonder "FC" e.d. */
    fun names(t: Team): List<String> {
        val core = plain(t.name).split(' ', '-').filter { it.isNotBlank() && it !in noise }.joinToString(" ")
        return listOf(plain(t.name), plain(t.short), core).filter { it.length >= 3 }.distinct()
    }

    /** Zoekt kanalen voor een wedstrijd: gids (beste), kanaalnaam met clubnamen, en vaste zenders van de competitie. */
    suspend fun channelsFor(m: Match, catalog: Catalog, epg: Epg?): List<SportHit> = withContext(Dispatchers.Default) {
        val homeNames = names(m.home)
        val awayNames = names(m.away)
        val hits = HashMap<String, SportHit>()
        fun add(h: SportHit) {
            val old = hits[h.channel.id]
            if (old == null || h.score > old.score) hits[h.channel.id] = h
        }

        // 1. Programmagids: titels met een of beide clubs rond de aftrap.
        if (epg != null) {
            val programs = runCatching {
                epg.findInWindow(homeNames + awayNames, m.start - 45 * 60_000, m.start + 150 * 60_000)
            }.getOrDefault(emptyList())
            val byEpg = catalog.channels.filter { it.epgId != null }.groupBy { it.epgId!!.lowercase() }
            programs.forEach { p ->
                val t = plain(p.title + " " + (p.description ?: ""))
                val both = homeNames.any { it in t } && awayNames.any { it in t }
                byEpg[p.channelKey]?.forEach { ch ->
                    add(SportHit(ch, "In de gids: ${p.title}", if (both) 100 else 60, p))
                }
            }
        }

        // 2. Kanaalnamen met de clubs erin (eventkanalen zoals "ESPN Live: PEC Zwolle - ADO").
        catalog.channels.forEach { ch ->
            val n = plain(ch.name)
            val h = homeNames.any { it in n }
            val a = awayNames.any { it in n }
            if (h && a) add(SportHit(ch, "Kanaal voor deze wedstrijd", 90, null))
            else if (h || a) add(SportHit(ch, "Kanaal met ${if (h) m.home.name else m.away.name}", 40, null))
        }

        // 3. De zenders die deze competitie normaal uitzenden.
        val keys = m.league.broadcasters.map { plain(it) }
        catalog.channels.forEach { ch ->
            val n = plain(ch.name)
            if (keys.any { it in n }) add(SportHit(ch, "Zender van de ${m.league.name}", 10, null))
        }

        hits.values.sortedWith(compareByDescending<SportHit> { it.score }.thenBy { it.channel.name }).take(80)
    }
}
