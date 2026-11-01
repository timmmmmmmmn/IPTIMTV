package nl.timvanbergen.iptimtv.ui.phone

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import nl.timvanbergen.iptimtv.data.*
import nl.timvanbergen.iptimtv.ui.Nav
import nl.timvanbergen.iptimtv.ui.Overlay
import nl.timvanbergen.iptimtv.ui.SearchState
import nl.timvanbergen.iptimtv.ui.whenText

private class Found(
    val channels: List<Channel> = emptyList(),
    val programs: List<Pair<Program, Channel>> = emptyList(),
    val movies: List<Movie> = emptyList(),
    val shows: List<Show> = emptyList(),
) {
    val isEmpty get() = channels.isEmpty() && programs.isEmpty() && movies.isEmpty() && shows.isEmpty()
}

@Composable
fun PhoneSearch() {
    val query = SearchState.query.trim()
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    LaunchedEffect(Unit) { if (query.isEmpty()) runCatching { focus.requestFocus() } }
    val found by produceState(Found(), query, Library.catalog, Library.epgVersion) {
        if (query.length < 2) { value = Found(); return@produceState }
        delay(250)
        value = withContext(Dispatchers.Default) {
            val cat = Library.catalog
            val byEpg = cat.channels.filter { it.epgId != null }.groupBy { it.epgId!!.lowercase() }
            Found(
                channels = cat.channels.filter { it.name.contains(query, true) }.take(25),
                programs = runCatching { Library.epg?.search(query).orEmpty() }.getOrDefault(emptyList())
                    .mapNotNull { p -> byEpg[p.channelKey]?.firstOrNull()?.let { p to it } },
                movies = cat.movies.filter { it.name.contains(query, true) }.take(60),
                shows = cat.shows.filter { it.name.contains(query, true) }.take(60),
            )
        }
    }

    Column(Modifier.fillMaxSize()) {
        PhoneTopBar("Zoeken")
        OutlinedTextField(
            value = SearchState.query,
            onValueChange = { SearchState.query = it },
            placeholder = { Text("Kanalen, programma's, films, series") },
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            trailingIcon = if (SearchState.query.isNotEmpty()) ({
                IconButton(onClick = { SearchState.query = "" }) { Icon(Icons.Filled.Close, "Wissen") }
            }) else null,
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { focusManager.clearFocus() }),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).focusRequester(focus),
        )
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
            if (query.length >= 2 && found.isEmpty) item { EmptyState("Niets gevonden", "Geen resultaten voor \"$query\".") }
            if (query.length < 2) item { EmptyState("Waar ben je naar op zoek?", "Zoek op een kanaal, een programma in de gids, een film of een serie.") }
            if (found.channels.isNotEmpty()) {
                item { SectionHeader("Kanalen") }
                items(found.channels, key = { "c" + it.id }) { ch ->
                    ChannelRow(ch, null, onLongClick = { ChannelSheet.channel = ch }) { Nav.playChannels(found.channels, ch) }
                }
            }
            if (found.movies.isNotEmpty()) item {
                SectionHeader("Films")
                PosterRow(found.movies.map { Triple(it.poster, it.name, it.rating) }) { Nav.open(Overlay.MovieInfo(found.movies[it])) }
            }
            if (found.shows.isNotEmpty()) item {
                SectionHeader("Series")
                PosterRow(found.shows.map { Triple(it.poster, it.name, it.rating) }) { Nav.open(Overlay.ShowInfo(found.shows[it])) }
            }
            if (found.programs.isNotEmpty()) {
                item { SectionHeader("In de gids") }
                items(found.programs, key = { "p" + it.first.channelKey + it.first.start }) { (p, ch) ->
                    ListItem(
                        headlineContent = { Text(p.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        supportingContent = { Text("${ch.name} · ${whenText(p.start, p.stop)}") },
                        leadingContent = { ChannelLogo(ch.logo, ch.name, Modifier.size(56.dp, 40.dp)) },
                        trailingContent = if (p.isLive()) ({ LivePill("NU") }) else null,
                        modifier = Modifier.clickable {
                            if (p.isLive()) Nav.playChannels(listOf(ch), ch) else Nav.open(Overlay.ProgramMenu(ch, p))
                        },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
                    )
                }
            }
        }
    }
    ChannelSheetHost()
}

@Composable
private fun PosterRow(items: List<Triple<String?, String, String?>>, onClick: (Int) -> Unit) {
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(items.size) { i ->
            Box(Modifier.width(112.dp)) { PosterCard(items[i].first, items[i].second, items[i].third) { onClick(i) } }
        }
    }
}
