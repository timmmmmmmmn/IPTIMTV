package nl.timvanbergen.iptimtv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import nl.timvanbergen.iptimtv.data.Library
import nl.timvanbergen.iptimtv.data.Match
import nl.timvanbergen.iptimtv.data.Sport
import nl.timvanbergen.iptimtv.data.SportHit
import nl.timvanbergen.iptimtv.data.Team
import java.time.LocalDate

object SportState {
    var matches by mutableStateOf<List<Match>?>(null)
    var loadedAt = 0L
    var day by mutableIntStateOf(0)
    var league by mutableStateOf<String?>(null)
}

@Composable
fun SportScreen() {
    // Elke paar minuten verversen, zodat de standen bij live wedstrijden kloppen.
    LaunchedEffect(SportState.day) {
        while (true) {
            if (SportState.matches == null || System.currentTimeMillis() - SportState.loadedAt > 120_000) {
                SportState.matches = Sport.today(LocalDate.now().plusDays(SportState.day.toLong()))
                SportState.loadedAt = System.currentTimeMillis()
            }
            delay(60_000)
        }
    }
    val all = SportState.matches
    val leagues = all.orEmpty().map { it.league }.distinct()
    val shown = all.orEmpty().filter { SportState.league == null || it.league.id == SportState.league }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = if (Mobile) 110.dp else 48.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "days") {
            LazyRow(contentPadding = PaddingValues(horizontal = Pad, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(listOf(0 to "Vandaag", 1 to "Morgen", -1 to "Gisteren")) { (d, label) ->
                    Button(label, selected = SportState.day == d, onClick = {
                        if (SportState.day != d) { SportState.day = d; SportState.matches = null; SportState.league = null }
                    })
                }
                if (leagues.size > 1) {
                    item { Spacer(Modifier.width(18.dp)) }
                    item { Button("Alles", selected = SportState.league == null, onClick = { SportState.league = null }) }
                    items(leagues) { l -> Button(l.name, selected = SportState.league == l.id, onClick = { SportState.league = l.id }) }
                }
            }
        }
        when {
            all == null -> item { Message("Wedstrijden laden…") }
            all.isEmpty() -> item {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Message("Geen wedstrijden", "Er zijn op deze dag geen wedstrijden in de grote competities.")
                    Button("Opnieuw laden", icon = Icons.Filled.Refresh, onClick = { SportState.matches = null; SportState.loadedAt = 0 })
                }
            }
            else -> {
                var last: String? = null
                shown.forEach { m ->
                    if (m.league.id != last) {
                        last = m.league.id
                        item(key = "h" + m.league.id) {
                            T(m.league.name, Modifier.padding(start = Pad, top = 14.dp, bottom = 4.dp), size = 20.sp, weight = FontWeight.Bold, color = C.accent)
                        }
                    }
                    item(key = m.league.id + m.home.name + m.start) { MatchLine(m) }
                }
            }
        }
    }
}

@Composable
private fun MatchLine(m: Match) {
    Focusable(
        onClick = { Nav.open(Overlay.MatchInfo(m)) },
        modifier = Modifier.padding(horizontal = Pad).fillMaxWidth().height(if (Mobile) 72.dp else 84.dp),
        scale = 1.02f,
    ) {
        Row(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.width(if (Mobile) 56.dp else 110.dp)) {
                when (m.state) {
                    "in" -> { T("LIVE", size = 14.sp, weight = FontWeight.Black, color = C.live); T(m.detail, size = 13.sp, color = C.dim) }
                    "post" -> T("Afgelopen", size = 14.sp, color = C.dim)
                    else -> T(clock(m.start), size = if (Mobile) 16.sp else 20.sp, weight = FontWeight.Bold)
                }
            }
            TeamSide(m.home, Modifier.weight(1f), alignEnd = true)
            Box(Modifier.width(if (Mobile) 64.dp else 96.dp), contentAlignment = Alignment.Center) {
                T(if (m.state == "pre") "–" else "${m.home.score ?: 0} - ${m.away.score ?: 0}", size = if (Mobile) 18.sp else 24.sp, weight = FontWeight.Black)
            }
            TeamSide(m.away, Modifier.weight(1f), alignEnd = false)
        }
    }
}

@Composable
private fun TeamSide(t: Team, modifier: Modifier, alignEnd: Boolean) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = if (alignEnd) Arrangement.End else Arrangement.Start) {
        if (!alignEnd) TeamLogo(t)
        T(if (Mobile) t.short else t.name, size = if (Mobile) 15.sp else 19.sp, weight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 10.dp).weight(1f, fill = false), align = if (alignEnd) TextAlign.End else TextAlign.Start)
        if (alignEnd) TeamLogo(t)
    }
}

@Composable
private fun TeamLogo(t: Team) {
    Box(Modifier.size(if (Mobile) 28.dp else 40.dp)) {
        if (t.logo != null) AsyncImage(t.logo, null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
    }
}

/** Een wedstrijd: op de achtergrond zoeken we alle zenders die hem (waarschijnlijk) uitzenden. */
@Composable
fun MatchInfo(m: Match) {
    val focus = rememberInitialFocus()
    val hits by produceState<List<SportHit>?>(null, m) {
        value = Sport.channelsFor(m, Library.catalog, Library.epg)
    }
    Column(Modifier.fillMaxSize().padding(horizontal = Pad, vertical = if (Mobile) 12.dp else 32.dp)) {
        T(m.league.name, color = C.accent, weight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.widthIn(max = 1000.dp)) {
            TeamSide(m.home, Modifier.weight(1f), alignEnd = true)
            T(if (m.state == "pre") clock(m.start) else "${m.home.score ?: 0} - ${m.away.score ?: 0}",
                Modifier.padding(horizontal = 16.dp), size = if (Mobile) 22.sp else 34.sp, weight = FontWeight.Black)
            TeamSide(m.away, Modifier.weight(1f), alignEnd = false)
        }
        if (m.state == "in") T("Live · ${m.detail}", color = C.live, weight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
        Spacer(Modifier.height(20.dp))
        when {
            hits == null -> T("Zenders zoeken…", color = C.dim, size = 18.sp)
            hits!!.isEmpty() -> T("Geen zenders gevonden voor deze wedstrijd.", color = C.dim, size = 18.sp)
            else -> {
                T("Hier is hij (waarschijnlijk) te zien", size = 20.sp, weight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                val channels = hits!!.map { it.channel }
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(4.dp, 4.dp, 4.dp, 40.dp)) {
                    items(hits!!.size) { i ->
                        val h = hits!![i]
                        Focusable(
                            onClick = { Nav.close(); Nav.playChannels(channels, h.channel) },
                            onLongClick = { Nav.open(Overlay.ChannelMenu(h.channel)) },
                            modifier = Modifier.fillMaxWidth().height(if (Mobile) 64.dp else 76.dp)
                                .then(if (i == 0) Modifier.focusRequester(focus) else Modifier),
                            scale = 1.02f,
                        ) {
                            Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Logo(h.channel.logo, h.channel.name, Modifier.size(if (Mobile) 72.dp else 96.dp, if (Mobile) 42.dp else 54.dp))
                                Spacer(Modifier.width(16.dp))
                                Column(Modifier.weight(1f)) {
                                    T(h.channel.name, size = 18.sp, weight = FontWeight.Bold)
                                    T(h.reason, size = 14.sp, color = if (h.score >= 60) C.accent else C.dim)
                                }
                                if (h.score >= 90) T("Beste keus", size = 13.sp, weight = FontWeight.Bold, color = C.bg,
                                    modifier = Modifier.background(C.accent, RoundedCornerShape(10.dp)).padding(horizontal = 10.dp, vertical = 4.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}
