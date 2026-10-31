package nl.timvanbergen.iptimtv.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import nl.timvanbergen.iptimtv.data.Channel
import nl.timvanbergen.iptimtv.data.Library
import nl.timvanbergen.iptimtv.data.Movie
import nl.timvanbergen.iptimtv.data.Program
import nl.timvanbergen.iptimtv.data.Show

object SearchState {
    var query by mutableStateOf("")
}

private class Results(
    val channels: List<Channel> = emptyList(),
    val programs: List<Pair<Program, Channel>> = emptyList(),
    val movies: List<Movie> = emptyList(),
    val shows: List<Show> = emptyList(),
) {
    val isEmpty get() = channels.isEmpty() && programs.isEmpty() && movies.isEmpty() && shows.isEmpty()
}

@Composable
fun SearchScreen() {
    val query = SearchState.query.trim()
    val results by produceState(Results(), query, Library.catalog, Library.epgVersion) {
        if (query.length < 2) {
            value = Results(); return@produceState
        }
        delay(250) // wacht tot er even niet getypt wordt
        value = withContext(Dispatchers.Default) {
            val cat = Library.catalog
            val byEpg = cat.channels.filter { it.epgId != null }.groupBy { it.epgId!!.lowercase() }
            val programs = runCatching { Library.epg?.search(query).orEmpty() }.getOrDefault(emptyList())
                .mapNotNull { p -> byEpg[p.channelKey]?.firstOrNull()?.let { p to it } }
            Results(
                channels = cat.channels.filter { it.name.contains(query, true) }.take(25),
                programs = programs,
                movies = cat.movies.filter { it.name.contains(query, true) }.take(60),
                shows = cat.shows.filter { it.name.contains(query, true) }.take(60),
            )
        }
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "field") {
            Column(Modifier.padding(horizontal = 48.dp, vertical = 8.dp)) {
                TvTextField(
                    SearchState.query,
                    { SearchState.query = it },
                    "Zoek een kanaal, programma, film of serie…",
                    Modifier.width(720.dp),
                    imeAction = ImeAction.Search,
                )
                if (query.length >= 2 && results.isEmpty) {
                    Spacer(Modifier.height(20.dp))
                    T("Niets gevonden voor \"$query\"", color = C.dim, size = 18.sp)
                }
            }
        }
        if (results.channels.isNotEmpty()) {
            item(key = "chTitle") { SectionTitle("Kanalen") }
            items(results.channels, key = { "c" + it.id }) { ch ->
                Column(Modifier.padding(horizontal = 48.dp)) {
                    ChannelLine(null, ch) { Nav.open(Overlay.Guide(ch)) }
                }
            }
        }
        if (results.programs.isNotEmpty()) {
            item(key = "epgTitle") { SectionTitle("In de gids") }
            items(results.programs.size, key = { "p$it" }) { i ->
                val (p, ch) = results.programs[i]
                ProgramLine(p, ch)
            }
        }
        if (results.movies.isNotEmpty()) item(key = "mov") {
            Section("Films") {
                LazyRow(contentPadding = PaddingValues(horizontal = 48.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    items(results.movies, key = { it.id }) { m -> PosterTile(m.name, m.poster, m.rating, width = 160) { Nav.open(Overlay.MovieInfo(m)) } }
                }
            }
        }
        if (results.shows.isNotEmpty()) item(key = "ser") {
            Section("Series") {
                LazyRow(contentPadding = PaddingValues(horizontal = 48.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    items(results.shows, key = { it.id }) { s -> PosterTile(s.name, s.poster, s.rating, width = 160) { Nav.open(Overlay.ShowInfo(s)) } }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    T(title, Modifier.padding(start = 48.dp, top = 14.dp), size = 22.sp, weight = FontWeight.Bold)
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column {
        SectionTitle(title)
        content()
    }
}

@Composable
private fun ProgramLine(p: Program, ch: Channel) {
    val live = p.isLive()
    Focusable(
        onClick = { Nav.playChannels(listOf(ch), ch) },
        modifier = Modifier.padding(horizontal = 48.dp).fillMaxWidth().height(78.dp),
        scale = 1.02f,
    ) {
        Row(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            T(
                whenText(p.start, p.stop),
                Modifier.width(110.dp),
                size = 17.sp,
                weight = FontWeight.Bold,
                color = if (live) C.live else C.text,
            )
            Logo(ch.logo, ch.name, Modifier.size(90.dp, 52.dp))
            Spacer(Modifier.width(18.dp))
            Column(Modifier.weight(1f)) {
                T(p.title, size = 18.sp, weight = FontWeight.Bold)
                T("${ch.name} · ${clock(p.start)} – ${clock(p.stop)}", size = 14.sp, color = C.dim)
            }
            if (live) ProgressBar(p.progress(), Modifier.width(160.dp))
        }
    }
}

/** De gids van één kanaal: wat er nu en de komende twee dagen op is. */
@Composable
fun ChannelGuide(ch: Channel) {
    val focus = rememberInitialFocus()
    val programs by produceState<List<Program>?>(null, ch.epgId, Library.epgVersion) {
        value = withContext(Dispatchers.IO) { runCatching { Library.epg?.upcoming(ch.epgId, 120).orEmpty() }.getOrDefault(emptyList()) }
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 56.dp, vertical = 36.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Logo(ch.logo, ch.name, Modifier.size(150.dp, 86.dp))
            Spacer(Modifier.width(24.dp))
            Column(Modifier.weight(1f)) {
                T(ch.name, size = 32.sp, weight = FontWeight.Black)
                T("Programmagids", color = C.dim)
            }
            Button("Nu kijken", icon = androidx.compose.material.icons.Icons.Filled.PlayArrow, primary = true,
                modifier = Modifier.focusRequester(focus), onClick = { Nav.close(); Nav.playChannels(listOf(ch), ch) })
            Spacer(Modifier.width(12.dp))
            Button(if (ch.id in Library.favorites) "In Mijn kanalen" else "Naar Mijn kanalen",
                icon = androidx.compose.material.icons.Icons.Filled.Star,
                selected = ch.id in Library.favorites,
                onClick = { Library.toggleFavorite(ch.id) })
        }
        Spacer(Modifier.height(24.dp))
        when {
            programs == null -> T("Gids laden…", color = C.dim)
            programs!!.isEmpty() -> T("Geen gidsgegevens voor dit kanaal.", color = C.dim, size = 18.sp)
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(4.dp, 4.dp, 4.dp, 40.dp)) {
                items(programs!!.size) { i ->
                    val p = programs!![i]
                    val live = p.isLive()
                    Focusable(
                        onClick = { Nav.close(); Nav.playChannels(listOf(ch), ch) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp),
                        scale = 1.02f,
                    ) {
                        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            T(whenText(p.start, p.stop), Modifier.width(120.dp), size = 17.sp, weight = FontWeight.Bold, color = if (live) C.live else C.text)
                            Column(Modifier.weight(1f)) {
                                T(p.title, size = 18.sp, weight = FontWeight.Bold)
                                T("${clock(p.start)} – ${clock(p.stop)}" + (p.description?.let { "  ·  $it" } ?: ""), size = 14.sp, color = C.dim, maxLines = 2)
                            }
                            if (live) ProgressBar(p.progress(), Modifier.width(160.dp).padding(start = 16.dp))
                        }
                    }
                }
            }
        }
    }
}
