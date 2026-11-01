package nl.timvanbergen.iptimtv.ui.phone

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import nl.timvanbergen.iptimtv.data.*
import nl.timvanbergen.iptimtv.ui.*
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.util.Calendar
import java.util.Date
import java.util.Locale

private fun greeting(): String = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
    in 6..11 -> "Goedemorgen"
    in 12..17 -> "Goedemiddag"
    in 18..23 -> "Goedenavond"
    else -> "Goedenacht"
}

/** Wedstrijden van vandaag ophalen (ook voor de "Nu live"-rij op Start). */
@Composable
fun LoadMatches() {
    LaunchedEffect(SportState.day) {
        while (true) {
            if (SportState.matches == null || System.currentTimeMillis() - SportState.loadedAt > 120_000) {
                SportState.matches = Sport.today(LocalDate.now().plusDays(SportState.day.toLong()))
                SportState.loadedAt = System.currentTimeMillis()
            }
            delay(60_000)
        }
    }
}

@Composable
fun PhoneHome() {
    LoadMatches()
    val cat = Library.catalog
    val favorites = Library.channels(Library.favorites)
    val continueItems = if (Settings.showContinue.on) History.continueWatching.take(20) else emptyList()
    val recordings = if (Settings.showRecordings.on) Recordings.finished else emptyList()
    val live = if (SportState.day == 0) SportState.matches.orEmpty().filter { it.live } else emptyList()

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item(key = "head") {
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 20.dp, end = 4.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(greeting(), style = MaterialTheme.typography.headlineLarge)
                    val sub = listOfNotNull(
                        SimpleDateFormat("EEEE d MMMM", Locale("nl", "NL")).format(Date()),
                        Library.active?.name?.takeIf { Library.profiles.size > 1 },
                    ).joinToString(" · ")
                    Text(sub, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                CastIconButton()
                IconButton(onClick = { Nav.tab = Tab.SETTINGS }) { Icon(Ic.Account, "Instellingen") }
            }
            Library.loading?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 20.dp, top = 6.dp))
            }
            Cloud.stats?.takeIf { Settings.showStats.on && it.week > 0 }?.let { s ->
                Card(
                    Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp).fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Ic.Tv, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text("${formatWatchTime(s.week)} deze week", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            Text("Vandaag ${formatWatchTime(s.today)}", style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
                        }
                    }
                }
            }
        }

        if (live.isNotEmpty()) {
            item(key = "live") {
                SectionHeader("Nu live", "Alle wedstrijden") { Nav.tab = Tab.SPORT }
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(live, key = { it.league.id + it.home.name }) { m -> LiveMatchCard(m) }
                }
            }
        }

        item(key = "fav") {
            SectionHeader("Mijn kanalen", if (favorites.isEmpty()) null else "Wijzigen") {
                Nav.open(Overlay.Picker("Mijn kanalen", Library.favorites.toList()) { Library.setFavorites(it) })
            }
            if (favorites.isEmpty()) {
                EmptyState("Nog geen kanalen", "Kies je vaste kanalen, dan staan ze hier altijd klaar.", "Kanalen kiezen") {
                    Nav.open(Overlay.Picker("Mijn kanalen", Library.favorites.toList()) { Library.setFavorites(it) })
                }
            } else {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(favorites, key = { it.id }) { ch ->
                        ChannelCard(ch, onLongClick = { ChannelSheet.channel = ch }) { Nav.playChannels(favorites, ch) }
                    }
                }
            }
        }

        if (continueItems.isNotEmpty()) item(key = "continue") {
            SectionHeader("Verder kijken")
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(continueItems, key = { it.key }) { w -> ContinueCard(w) }
            }
        }

        Library.groups.forEach { g ->
            item(key = "g" + g.id) {
                val chs = Library.channels(g.channelIds)
                SectionHeader(g.name, "Bewerken") { Nav.open(Overlay.GroupEdit(g.id)) }
                if (chs.isEmpty()) {
                    TextButton(onClick = {
                        Nav.open(Overlay.Picker(g.name, g.channelIds) { ids -> Library.updateGroup(g.copy(channelIds = ids)) })
                    }, modifier = Modifier.padding(start = 8.dp)) { Text("Kanalen toevoegen") }
                } else LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(chs, key = { it.id }) { ch ->
                        ChannelCard(ch, onLongClick = { ChannelSheet.channel = ch }) { Nav.playChannels(chs, ch) }
                    }
                }
            }
        }

        if (recordings.isNotEmpty()) item(key = "rec") {
            SectionHeader("Opnames")
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(recordings, key = { it.id }) { r -> RecordingCard(r) }
            }
        }

        if (!cat.isEmpty) item(key = "newgroup") {
            OutlinedButton(onClick = { createGroup() }, modifier = Modifier.padding(start = 16.dp, top = 24.dp)) {
                Icon(Icons.Filled.Add, null)
                Spacer(Modifier.width(8.dp))
                Text("Nieuwe groep maken")
            }
        }
    }
    ChannelSheetHost()
}

@Composable
private fun LiveMatchCard(m: Match) {
    Card(
        onClick = { Nav.open(Overlay.MatchInfo(m)) },
        modifier = Modifier.width(250.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LivePill()
                Spacer(Modifier.width(8.dp))
                Text("${m.league.name} · ${m.detail}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(10.dp))
            listOf(m.home, m.away).forEach { t ->
                Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(22.dp)) { if (t.logo != null) AsyncImage(t.logo, null, Modifier.fillMaxSize()) }
                    Spacer(Modifier.width(10.dp))
                    Text(t.short, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f), maxLines = 1)
                    Text(t.score ?: "0", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ContinueCard(w: WatchItem) {
    Card(
        Modifier.width(250.dp).clip(CardDefaults.shape).combinedClickable(onClick = { Nav.playWatch(w) }, onLongClick = { Nav.open(Overlay.WatchMenu(w)) }),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
            PosterImage(w.image, "", Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f)))))
            Box(Modifier.align(Alignment.Center).size(44.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.55f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.PlayArrow, null, tint = Color.White)
            }
            Text("nog ${duration((w.duration - w.position).coerceAtLeast(0))}", style = MaterialTheme.typography.labelMedium, color = Color.White,
                modifier = Modifier.align(Alignment.BottomEnd).padding(10.dp))
        }
        Progress(w.progress, Modifier.fillMaxWidth())
        Column(Modifier.padding(12.dp)) {
            Text(w.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(w.subtitle ?: if (w.kind == "movie") "Film" else "Aflevering", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecordingCard(r: Recording) {
    Card(
        Modifier.width(200.dp).clip(CardDefaults.shape).combinedClickable(
            onClick = {
                if (r.uri != null && r.state == RecState.DONE) {
                    Nav.play = Playback(listOf(PlayItem(r.title, r.uri, r.logo, subtitle = r.channel, key = "rec:${r.id}", kind = "recording", image = r.logo)), 0, live = false)
                } else Nav.open(Overlay.RecordingMenu(r))
            },
            onLongClick = { Nav.open(Overlay.RecordingMenu(r)) },
        ),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Box {
            ChannelLogo(r.logo, r.channel, Modifier.fillMaxWidth().height(84.dp).padding(8.dp))
            if (r.state == RecState.RECORDING) Box(Modifier.align(Alignment.TopEnd).padding(12.dp)) { LivePill("OPNAME") }
        }
        Column(Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp)) {
            Text(r.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${r.channel} · ${whenText(r.start, r.end)}", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}
