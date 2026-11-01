package nl.timvanbergen.iptimtv.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.LaunchedEffect
import nl.timvanbergen.iptimtv.data.RecState
import nl.timvanbergen.iptimtv.data.Recordings
import nl.timvanbergen.iptimtv.data.Settings
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
        contentPadding = PaddingValues(bottom = if (Mobile) 110.dp else 48.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "field") {
            Column(Modifier.padding(horizontal = Pad, vertical = 8.dp)) {
                TvTextField(
                    SearchState.query,
                    { SearchState.query = it },
                    "Zoek een kanaal, programma, film of serie…",
                    Modifier.widthIn(max = 720.dp).fillMaxWidth(),
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
                Column(Modifier.padding(horizontal = Pad)) {
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
                LazyRow(contentPadding = PaddingValues(horizontal = Pad, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    items(results.movies, key = { it.id }) { m -> PosterTile(m.name, m.poster, m.rating, width = 160) { Nav.open(Overlay.MovieInfo(m)) } }
                }
            }
        }
        if (results.shows.isNotEmpty()) item(key = "ser") {
            Section("Series") {
                LazyRow(contentPadding = PaddingValues(horizontal = Pad, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    items(results.shows, key = { it.id }) { s -> PosterTile(s.name, s.poster, s.rating, width = 160) { Nav.open(Overlay.ShowInfo(s)) } }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    T(title, Modifier.padding(start = Pad, top = 14.dp), size = 22.sp, weight = FontWeight.Bold)
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
        onClick = { if (live) Nav.playChannels(listOf(ch), ch) else Nav.open(Overlay.ProgramMenu(ch, p)) },
        onLongClick = { Nav.open(Overlay.ProgramMenu(ch, p)) },
        modifier = Modifier.padding(horizontal = Pad).fillMaxWidth().height(78.dp),
        scale = 1.02f,
    ) {
        Row(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            T(
                whenText(p.start, p.stop),
                Modifier.width(if (Mobile) 70.dp else 110.dp),
                size = 17.sp,
                weight = FontWeight.Bold,
                color = if (live) C.live else C.text,
            )
            if (!Mobile) {
                Logo(ch.logo, ch.name, Modifier.size(90.dp, 52.dp))
                Spacer(Modifier.width(18.dp))
            }
            Column(Modifier.weight(1f)) {
                T(p.title, size = 18.sp, weight = FontWeight.Bold)
                T("${ch.name} · ${clock(p.start)} – ${clock(p.stop)}", size = 14.sp, color = C.dim)
            }
            if (live && !Mobile) ProgressBar(p.progress(), Modifier.width(160.dp))
        }
    }
}

/** De gids van één kanaal: wat er nu en de komende twee dagen op is. */
@Composable
fun ChannelGuide(ch: Channel) {
    val focus = rememberInitialFocus()
    // Kanalen met terugkijken tonen ook wat er al geweest is.
    val from = System.currentTimeMillis() - ch.archiveDays.coerceAtMost(Settings.epgBack.int) * 86_400_000L
    val programs by produceState<List<Program>?>(null, ch.epgId, Library.epgVersion) {
        value = withContext(Dispatchers.IO) { runCatching { Library.epg?.schedule(ch.epgId, from, 400).orEmpty() }.getOrDefault(emptyList()) }
    }
    val nowIndex = programs?.indexOfFirst { it.stop > System.currentTimeMillis() }?.coerceAtLeast(0) ?: 0
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    LaunchedEffect(programs) { if (programs != null && nowIndex > 0) listState.scrollToItem(nowIndex) }
    Column(Modifier.fillMaxSize().padding(horizontal = Pad + if (Mobile) 0.dp else 8.dp, vertical = if (Mobile) 8.dp else 36.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Logo(ch.logo, ch.name, Modifier.size(if (Mobile) 90.dp else 150.dp, if (Mobile) 52.dp else 86.dp))
            Spacer(Modifier.width(if (Mobile) 14.dp else 24.dp))
            Column(Modifier.weight(1f)) {
                T(ch.name, size = if (Mobile) 22.sp else 32.sp, weight = FontWeight.Black)
                T(if (ch.archiveDays > 0) "Programmagids · terugkijken tot ${ch.archiveDays} dagen" else "Programmagids", color = C.dim)
            }
        }
        Spacer(Modifier.height(12.dp))
        Wrap {
            Button("Nu kijken", icon = androidx.compose.material.icons.Icons.Filled.PlayArrow, primary = true,
                modifier = Modifier.focusRequester(focus), onClick = { Nav.close(); Nav.playChannels(listOf(ch), ch) })
            Button(if (ch.id in Library.favorites) "In Mijn kanalen" else "Naar Mijn kanalen",
                icon = androidx.compose.material.icons.Icons.Filled.Star,
                selected = ch.id in Library.favorites,
                onClick = { Library.toggleFavorite(ch.id) })
        }
        Spacer(Modifier.height(24.dp))
        when {
            programs == null -> T("Gids laden…", color = C.dim)
            programs!!.isEmpty() -> T("Geen gidsgegevens voor dit kanaal.", color = C.dim, size = 18.sp)
            else -> LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(4.dp, 4.dp, 4.dp, 40.dp)) {
                items(programs!!.size) { i ->
                    val p = programs!![i]
                    val live = p.isLive()
                    val past = p.stop <= System.currentTimeMillis()
                    Focusable(
                        onClick = { if (live) { Nav.close(); Nav.playChannels(listOf(ch), ch) } else Nav.open(Overlay.ProgramMenu(ch, p)) },
                        onLongClick = { Nav.open(Overlay.ProgramMenu(ch, p)) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp),
                        scale = 1.02f,
                    ) {
                        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            T(whenText(p.start, p.stop), Modifier.width(if (Mobile) 76.dp else 120.dp), size = 17.sp, weight = FontWeight.Bold,
                                color = if (live) C.live else if (past) C.dim else C.text)
                            Column(Modifier.weight(1f)) {
                                T(p.title, size = 18.sp, weight = FontWeight.Bold)
                                T("${clock(p.start)} – ${clock(p.stop)}" + (p.description?.let { "  ·  $it" } ?: ""), size = 14.sp, color = C.dim, maxLines = 2)
                            }
                            if (live) ProgressBar(p.progress(), Modifier.width(if (Mobile) 60.dp else 160.dp).padding(start = 16.dp))
                            if (past && ch.archiveDays > 0) T("Terugkijken", size = 13.sp, color = C.accent, weight = FontWeight.Bold)
                            if (Recordings.items.any { r -> r.url == ch.url && r.start <= p.start + 60_000 && r.end >= p.stop - 60_000 && r.state != RecState.CANCELLED && r.state != RecState.FAILED })
                                Icon(Ic.Record, C.live, 18.dp, Modifier.padding(start = 10.dp))
                        }
                    }
                }
            }
        }
    }
}
