package nl.timvanbergen.iptimtv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nl.timvanbergen.iptimtv.data.Category
import nl.timvanbergen.iptimtv.data.Episode
import nl.timvanbergen.iptimtv.data.Library
import nl.timvanbergen.iptimtv.data.Movie
import nl.timvanbergen.iptimtv.data.Show
import nl.timvanbergen.iptimtv.data.SourceType
import nl.timvanbergen.iptimtv.data.Xtream

object VodState {
    var movieCategory by mutableStateOf<String?>(null)
    var showCategory by mutableStateOf<String?>(null)
}

@Composable
fun MoviesScreen() {
    val cats = Library.catalog.movieCategoriesWithNew
    if (cats.isEmpty()) {
        EmptyState("films"); return
    }
    val current = cats.firstOrNull { it.id == VodState.movieCategory } ?: cats.first()
    val movies = Library.catalog.moviesByCategory[current.id].orEmpty()
    CategoryGrid(cats, current, { VodState.movieCategory = it }) {
        items(movies, key = { it.id }) { m ->
            PosterTile(m.name, m.poster, m.rating) { Nav.open(Overlay.MovieInfo(m)) }
        }
    }
}

@Composable
fun SeriesScreen() {
    val cats = Library.catalog.showCategoriesWithNew
    if (cats.isEmpty()) {
        EmptyState("series"); return
    }
    val current = cats.firstOrNull { it.id == VodState.showCategory } ?: cats.first()
    val shows = Library.catalog.showsByCategory[current.id].orEmpty()
    CategoryGrid(cats, current, { VodState.showCategory = it }) {
        items(shows, key = { it.id }) { s ->
            PosterTile(s.name, s.poster, s.rating) { Nav.open(Overlay.ShowInfo(s)) }
        }
    }
}

@Composable
private fun CategoryGrid(
    cats: List<Category>,
    current: Category,
    onSelect: (String) -> Unit,
    grid: androidx.compose.foundation.lazy.grid.LazyGridScope.() -> Unit,
) {
    Row(Modifier.fillMaxSize().padding(start = 42.dp, end = 36.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        LazyColumn(
            Modifier.width(330.dp).fillMaxHeight().focusRestorer(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            contentPadding = PaddingValues(6.dp, 6.dp, 6.dp, 40.dp),
        ) {
            items(cats, key = { it.id }) { c ->
                CategoryItem(c.name, selected = c.id == current.id, onFocus = { onSelect(c.id) })
            }
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(168.dp),
            modifier = Modifier.weight(1f).fillMaxHeight(),
            contentPadding = PaddingValues(12.dp, 12.dp, 12.dp, 40.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
            content = grid,
        )
    }
}

@Composable
fun PosterTile(name: String, poster: String?, rating: String?, modifier: Modifier = Modifier, width: Int? = null, onClick: () -> Unit) {
    Column(if (width != null) modifier.width(width.dp) else modifier) {
        Focusable(onClick = onClick, modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f), scale = 1.07f) {
            Poster(poster, name, Modifier.fillMaxSize())
            if (rating != null) Row(
                Modifier.align(Alignment.TopEnd).padding(8.dp)
                    .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 7.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Star, Color(0xFFFFC94D), 14.dp)
                Spacer(Modifier.width(3.dp))
                T(rating.take(3), size = 13.sp, weight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(8.dp))
        T(name, size = 15.sp, color = C.text.copy(alpha = 0.9f), maxLines = 2)
    }
}

/** Grote achtergrond met poster links en tekst rechts; gedeeld door film- en serieschermen. */
@Composable
private fun InfoLayout(poster: String?, title: String, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        Poster(poster, "", Modifier.fillMaxSize())
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(listOf(C.bg, C.bg.copy(alpha = 0.96f), C.bg.copy(alpha = 0.75f))),
            ),
        )
        Row(Modifier.fillMaxSize().padding(56.dp), horizontalArrangement = Arrangement.spacedBy(48.dp)) {
            Poster(poster, title, Modifier.width(300.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(18.dp)))
            Column(Modifier.weight(1f)) { content() }
        }
    }
}

@Composable
fun MovieInfo(m: Movie) {
    val focus = rememberInitialFocus()
    val resume = remember { Library.resumePosition(m.url) }
    val plot by produceState<String?>(null, m.id) {
        val p = Library.active
        if (p?.type == SourceType.XTREAM) value = withContext(Dispatchers.IO) { Xtream.moviePlot(p, m.id) }
    }
    val item = PlayItem(m.name, m.url, m.poster)
    InfoLayout(m.poster, m.name) {
        T(m.name, size = 40.sp, weight = FontWeight.Black, maxLines = 2)
        Spacer(Modifier.height(12.dp))
        if (m.rating != null) Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Star, Color(0xFFFFC94D), 20.dp)
            Spacer(Modifier.width(6.dp))
            T(m.rating, size = 18.sp, weight = FontWeight.Bold)
        }
        Spacer(Modifier.height(20.dp))
        T(plot ?: "", size = 18.sp, color = C.text.copy(alpha = 0.85f), maxLines = 7, modifier = Modifier.width(760.dp))
        Spacer(Modifier.height(32.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            if (resume > 0) {
                Button("Verder kijken (${duration(resume)})", icon = Icons.Filled.PlayArrow, primary = true, modifier = Modifier.focusRequester(focus), onClick = {
                    Nav.play = Playback(listOf(item), 0, live = false, startAt = resume)
                })
                Button("Vanaf het begin", icon = Icons.Filled.Refresh, onClick = {
                    Nav.play = Playback(listOf(item), 0, live = false)
                })
            } else {
                Button("Afspelen", icon = Icons.Filled.PlayArrow, primary = true, modifier = Modifier.focusRequester(focus), onClick = {
                    Nav.play = Playback(listOf(item), 0, live = false)
                })
            }
        }
    }
}

@Composable
fun ShowInfo(s: Show) {
    val focus = rememberInitialFocus()
    var season by remember { mutableStateOf<Int?>(null) }
    val episodes by produceState<List<Episode>?>(null, s.id) {
        val p = Library.active ?: return@produceState
        value = withContext(Dispatchers.IO) { runCatching { Xtream.episodes(p, s.id) }.getOrDefault(emptyList()) }
    }
    val seasons = episodes.orEmpty().map { it.season }.distinct()
    // De afleveringen komen pas na het laden binnen; dan pas kan de focus op het eerste seizoen.
    LaunchedEffect(episodes != null) {
        if (episodes != null) {
            kotlinx.coroutines.delay(50)
            runCatching { focus.requestFocus() }
        }
    }
    val currentSeason = season ?: seasons.firstOrNull()
    val shown = episodes.orEmpty().filter { it.season == currentSeason }

    InfoLayout(s.poster, s.name) {
        T(s.name, size = 36.sp, weight = FontWeight.Black, maxLines = 2)
        if (s.plot != null) {
            Spacer(Modifier.height(10.dp))
            T(s.plot, size = 16.sp, color = C.text.copy(alpha = 0.8f), maxLines = 3, modifier = Modifier.width(820.dp))
        }
        Spacer(Modifier.height(20.dp))
        when {
            episodes == null -> T("Afleveringen laden…", color = C.dim)
            episodes!!.isEmpty() -> T("Geen afleveringen gevonden", color = C.dim)
            else -> {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(4.dp)) {
                    items(seasons) { n ->
                        Button(
                            if (n > 0) "Seizoen $n" else "Afleveringen",
                            selected = n == currentSeason,
                            modifier = if (n == seasons.first()) Modifier.focusRequester(focus) else Modifier,
                            onClick = { season = n },
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(4.dp, 4.dp, 4.dp, 40.dp)) {
                    items(shown, key = { it.id }) { e ->
                        EpisodeLine(e) {
                            val items = shown.map { PlayItem(it.title.ifBlank { "Aflevering ${it.number}" }, it.url, s.poster, subtitle = s.name) }
                            Nav.play = Playback(items, shown.indexOf(e), live = false, startAt = Library.resumePosition(e.url))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EpisodeLine(e: Episode, onClick: () -> Unit) {
    val resume = remember(e.url, Nav.play) { Library.resumePosition(e.url) }
    Focusable(onClick = onClick, modifier = Modifier.fillMaxWidth().height(86.dp), scale = 1.02f) {
        Row(Modifier.fillMaxSize().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Poster(e.image, "${e.number}", Modifier.size(118.dp, 66.dp).clip(RoundedCornerShape(8.dp)))
            Spacer(Modifier.width(18.dp))
            Column(Modifier.weight(1f)) {
                T("${e.number}. ${e.title.ifBlank { "Aflevering ${e.number}" }}", size = 18.sp, weight = FontWeight.Bold)
                if (e.plot != null) T(e.plot, size = 14.sp, color = C.dim)
                if (resume > 0) T("Verder vanaf ${duration(resume)}", size = 13.sp, color = C.accent)
            }
            if (e.duration != null) T(e.duration, size = 14.sp, color = C.dim, modifier = Modifier.padding(start = 12.dp))
        }
    }
}
