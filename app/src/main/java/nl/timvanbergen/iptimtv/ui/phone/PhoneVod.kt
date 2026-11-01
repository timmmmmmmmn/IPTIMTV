package nl.timvanbergen.iptimtv.ui.phone

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.material3.Tab as M3Tab
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nl.timvanbergen.iptimtv.data.*
import nl.timvanbergen.iptimtv.ui.Nav
import nl.timvanbergen.iptimtv.ui.Overlay
import nl.timvanbergen.iptimtv.ui.PlayItem
import nl.timvanbergen.iptimtv.ui.Playback
import nl.timvanbergen.iptimtv.ui.Tab
import nl.timvanbergen.iptimtv.ui.duration
import nl.timvanbergen.iptimtv.ui.watchKey

@Composable
fun PhoneVod() {
    val movies = Nav.tab != Tab.SERIES
    val cat = Library.catalog
    val cats = if (movies) cat.movieCategoriesWithNew else cat.showCategoriesWithNew
    var selected by rememberSaveable(movies) { mutableStateOf<String?>(null) }
    val current = selected?.takeIf { s -> cats.any { it.id == s } } ?: cats.firstOrNull()?.id
    val grid = rememberLazyGridState()
    LaunchedEffect(current, movies) { grid.scrollToItem(0) }

    Column(Modifier.fillMaxSize()) {
        PhoneTopBar("Films & series")
        PrimaryTabRow(selectedTabIndex = if (movies) 0 else 1, containerColor = MaterialTheme.colorScheme.background) {
            val dim = MaterialTheme.colorScheme.onSurfaceVariant
            M3Tab(selected = movies, onClick = { Nav.tab = Tab.MOVIES }, text = { Text("Films (${cat.movies.size})") }, unselectedContentColor = dim)
            M3Tab(selected = !movies, onClick = { Nav.tab = Tab.SERIES }, text = { Text("Series (${cat.shows.size})") }, unselectedContentColor = dim)
        }
        if (cats.isEmpty()) {
            EmptyState(if (Library.loading != null) "Laden…" else if (movies) "Geen films" else "Geen series")
            return
        }
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(cats, key = { it.id }) { c ->
                FilterChip(selected = current == c.id, onClick = { selected = c.id }, label = { Text(c.name, maxLines = 1) })
            }
        }
        LazyVerticalGrid(
            GridCells.Adaptive(108.dp), state = grid, modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (movies) items(cat.moviesByCategory[current].orEmpty(), key = { it.id }) { m ->
                PosterCard(m.poster, m.name, m.rating) { Nav.open(Overlay.MovieInfo(m)) }
            } else items(cat.showsByCategory[current].orEmpty(), key = { it.id }) { s ->
                PosterCard(s.poster, s.name, s.rating) { Nav.open(Overlay.ShowInfo(s)) }
            }
        }
    }
}

@Composable
fun PosterCard(poster: String?, name: String, rating: String?, onClick: () -> Unit) {
    Column(Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick)) {
        Box {
            PosterImage(poster, name, Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(RoundedCornerShape(12.dp)))
            val r = rating?.toFloatOrNull()
            if (r != null && r > 0 && Settings.showRatings.on) Row(
                Modifier.align(Alignment.TopEnd).padding(6.dp).clip(RoundedCornerShape(8.dp)).background(Color.Black.copy(alpha = 0.7f))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Star, null, Modifier.size(12.dp), tint = MaterialTheme.colorScheme.tertiary)
                Spacer(Modifier.width(3.dp))
                Text(if (r >= 10) "${r.toInt()}" else "%.1f".format(r), style = MaterialTheme.typography.labelSmall, color = Color.White)
            }
        }
        Text(name, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp, start = 2.dp, end = 2.dp))
    }
}

/** Kop van een film of serie: de poster vervaagd op de achtergrond, scherp ervoor. */
@Composable
private fun DetailHeader(poster: String?, title: String, rating: String?, extra: String?) {
    Box(Modifier.fillMaxWidth().height(300.dp)) {
        if (poster != null) AsyncImage(
            poster, null, contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize().then(if (Build.VERSION.SDK_INT >= 31) Modifier.blur(28.dp) else Modifier),
            alpha = 0.55f,
        )
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color.Transparent, MaterialTheme.colorScheme.background))))
        Row(Modifier.align(Alignment.BottomStart).padding(horizontal = 20.dp), verticalAlignment = Alignment.Bottom) {
            PosterImage(poster, title, Modifier.width(118.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(12.dp)))
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f).padding(bottom = 6.dp)) {
                Text(title, style = MaterialTheme.typography.headlineSmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
                val r = rating?.toFloatOrNull()
                if (r != null && r > 0) Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Star, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.tertiary)
                    Spacer(Modifier.width(4.dp))
                    Text(if (r >= 10) "${r.toInt()}" else "%.1f".format(r), style = MaterialTheme.typography.titleSmall)
                }
                if (extra != null) Text(extra, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp))
            }
        }
        FilledTonalIconButton(onClick = { Nav.close() }, modifier = Modifier.statusBarsPadding().padding(8.dp)) {
            Icon(Icons.Filled.ArrowBack, "Terug")
        }
    }
}

@Composable
fun PhoneMovie(m: Movie) {
    val key = watchKey("movie", m.url)
    val resume = remember(Nav.play) { History.get(key)?.takeIf { it.unfinished }?.position ?: 0L }
    val plot by produceState<String?>(null, m.id) {
        val p = Library.active
        if (p?.type == SourceType.XTREAM) value = withContext(Dispatchers.IO) { runCatching { Xtream.moviePlot(p, m.id) }.getOrNull() }
    }
    val item = PlayItem(m.name, m.url, m.poster, key = key, kind = "movie", image = m.poster)
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
        item { DetailHeader(m.poster, m.name, m.rating, null) }
        item {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                Button(
                    onClick = { Nav.play = Playback(listOf(item), 0, live = false, startAt = if (Settings.resumeMode.value == "start") 0 else resume) },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) {
                    Icon(Icons.Filled.PlayArrow, null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (resume > 0) "Verder kijken (${duration(resume)})" else "Afspelen")
                }
                if (resume > 0) OutlinedButton(
                    onClick = { Nav.play = Playback(listOf(item), 0, live = false) },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(48.dp),
                ) {
                    Icon(Icons.Filled.Refresh, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Vanaf het begin")
                }
                Spacer(Modifier.height(20.dp))
                when (val p = plot) {
                    null -> if (Library.active?.type == SourceType.XTREAM) LinearProgressIndicator(Modifier.fillMaxWidth(0.4f))
                    else -> Text(p, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f))
                }
            }
        }
    }
}

@Composable
fun PhoneShow(s: Show) {
    var season by remember { mutableStateOf<Int?>(null) }
    val episodes by produceState<List<Episode>?>(null, s.id) {
        val p = Library.active ?: return@produceState
        value = withContext(Dispatchers.IO) { runCatching { Xtream.episodes(p, s.id) }.getOrDefault(emptyList()) }
        CatalogShare.uploadSeries(p, s.id, value.orEmpty())
    }
    val seasons = episodes.orEmpty().map { it.season }.distinct()
    val currentSeason = season ?: seasons.firstOrNull()
    val shown = episodes.orEmpty().filter { it.season == currentSeason }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
        item { DetailHeader(s.poster, s.name, s.rating, episodes?.let { "${seasons.size} seizoen${if (seasons.size == 1) "" else "en"} · ${it.size} afleveringen" }) }
        if (s.plot != null) item {
            Text(s.plot, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp), maxLines = 6, overflow = TextOverflow.Ellipsis)
        }
        when {
            episodes == null -> item { Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
            episodes!!.isEmpty() -> item { EmptyState("Geen afleveringen gevonden") }
            else -> {
                if (seasons.size > 1) item {
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(seasons) { n ->
                            FilterChip(selected = n == currentSeason, onClick = { season = n }, label = { Text(if (n > 0) "Seizoen $n" else "Afleveringen") })
                        }
                    }
                }
                items(shown, key = { it.id }) { e -> EpisodeRow(s, e, shown) }
            }
        }
    }
}

@Composable
private fun EpisodeRow(s: Show, e: Episode, list: List<Episode>) {
    val w = remember(e.url, Nav.play) { History.get(watchKey("episode", e.url)) }
    val resume = w?.takeIf { it.unfinished }?.position ?: 0L
    Row(
        Modifier.fillMaxWidth().clickable {
            val items = list.map {
                PlayItem(it.title.ifBlank { "Aflevering ${it.number}" }, it.url, s.poster,
                    subtitle = s.name + if (it.season > 0) " · S${it.season} A${it.number}" else "",
                    key = watchKey("episode", it.url), kind = "episode", parent = s.id, image = it.image ?: s.poster)
            }
            Nav.play = Playback(items, list.indexOf(e), live = false, startAt = if (Settings.resumeMode.value == "start") 0 else resume)
        }.padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(128.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(10.dp))) {
            PosterImage(e.image ?: s.poster, "${e.number}", Modifier.fillMaxSize())
            if (w != null && w.duration > 0) Progress(w.progress, Modifier.align(Alignment.BottomCenter).fillMaxWidth())
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text("${e.number}. ${e.title.ifBlank { "Aflevering ${e.number}" }}", style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val sub = listOfNotNull(e.duration, if (resume > 0) "verder vanaf ${duration(resume)}" else null).joinToString(" · ")
            if (sub.isNotEmpty()) Text(sub, style = MaterialTheme.typography.bodySmall, color = if (resume > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            if (e.plot != null) Text(e.plot, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}
