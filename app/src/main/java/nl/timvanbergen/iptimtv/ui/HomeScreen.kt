package nl.timvanbergen.iptimtv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import nl.timvanbergen.iptimtv.data.Channel
import nl.timvanbergen.iptimtv.data.Cloud
import nl.timvanbergen.iptimtv.data.History
import nl.timvanbergen.iptimtv.data.Library
import nl.timvanbergen.iptimtv.data.Program
import nl.timvanbergen.iptimtv.data.RecState
import nl.timvanbergen.iptimtv.data.Recording
import nl.timvanbergen.iptimtv.data.Recordings
import nl.timvanbergen.iptimtv.data.Settings
import nl.timvanbergen.iptimtv.data.WatchItem
import nl.timvanbergen.iptimtv.data.formatWatchTime

private val TileWidth = if (Mobile) 156.dp else 230.dp

@Composable
fun HomeScreen() {
    val favorites = Library.channels(Library.favorites)
    val firstTile = remember { FocusRequester() }
    val continueItems = if (Settings.showContinue.on) History.continueWatching.take(20) else emptyList()
    val recordings = if (Settings.showRecordings.on) Recordings.finished else emptyList()

    // Bij het openen van de app staat de focus meteen op het eerste kanaal: één keer OK en je kijkt.
    LaunchedEffect(favorites.isNotEmpty(), Nav.resets) {
        if (favorites.isNotEmpty() && Nav.focusHome && !Mobile) {
            delay(120)
            if (runCatching { firstTile.requestFocus() }.getOrDefault(false)) Nav.focusHome = false
        }
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = if (Mobile) 110.dp else 48.dp),
        verticalArrangement = Arrangement.spacedBy(if (Mobile) 4.dp else 8.dp),
    ) {
        if (Library.catalog.channels.isEmpty() && (Library.loading != null || Library.error != null)) {
            item { EmptyState("kanalen") }
        }
        item(key = "fav") {
            ChannelRow(
                title = "Mijn kanalen",
                channels = favorites,
                firstRequester = firstTile,
                trailing = {
                    ActionTile(if (favorites.isEmpty()) "Kanalen toevoegen" else "Kanalen kiezen", Icons.Filled.Add) {
                        Nav.open(Overlay.Picker("Mijn kanalen", Library.favorites.toList()) { Library.setFavorites(it) })
                    }
                },
            )
        }
        if (continueItems.isNotEmpty()) item(key = "continue") {
            Column {
                RowTitle("Verder kijken", stats())
                LazyRow(contentPadding = PaddingValues(horizontal = Pad, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    items(continueItems, key = { it.key }) { w -> WatchTile(w) }
                }
            }
        }
        items(Library.groups, key = { it.id }) { g ->
            ChannelRow(
                title = g.name,
                channels = Library.channels(g.channelIds),
                trailing = {
                    ActionTile("Groep bewerken", Icons.Filled.Edit) { Nav.open(Overlay.GroupEdit(g.id)) }
                },
            )
        }
        if (recordings.isNotEmpty()) item(key = "rec") {
            Column {
                RowTitle("Opnames", "Bewaard in Download/IPTIMTV")
                LazyRow(contentPadding = PaddingValues(horizontal = Pad, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    items(recordings, key = { it.id }) { r -> RecordingTile(r) }
                }
            }
        }
        item(key = "new") {
            Row(Modifier.padding(horizontal = Pad, vertical = 16.dp)) {
                Button("Nieuwe groep maken", icon = Icons.Filled.Add, onClick = { createGroup() })
            }
        }
    }
}

private fun stats(): String? {
    if (!Settings.showStats.on) return null
    val s = Cloud.stats ?: return null
    return "Deze week ${formatWatchTime(s.week)} gekeken · vandaag ${formatWatchTime(s.today)}"
}

@Composable
private fun RowTitle(title: String, subtitle: String? = null) {
    Row(Modifier.padding(start = Pad, end = Pad, top = 12.dp, bottom = 6.dp), verticalAlignment = Alignment.Bottom) {
        T(title, size = 22.sp, weight = FontWeight.Bold)
        if (subtitle != null) {
            Spacer(Modifier.width(14.dp))
            T(subtitle, size = 14.sp, color = C.dim, modifier = Modifier.weight(1f, fill = false))
        }
    }
}

/** Vraagt een naam, maakt de groep en opent meteen de kanaalkiezer. */
fun createGroup(withChannel: String? = null) {
    Nav.open(Overlay.TextInput("Naam van de nieuwe groep", "") { name ->
        val g = Library.addGroup(name)
        if (withChannel != null) {
            Library.updateGroup(g.copy(channelIds = listOf(withChannel)))
        } else {
            Nav.open(Overlay.Picker(g.name, emptyList()) { ids ->
                Library.groups.firstOrNull { it.id == g.id }?.let { Library.updateGroup(it.copy(channelIds = ids)) }
            })
        }
    })
}

@Composable
private fun ChannelRow(
    title: String,
    channels: List<Channel>,
    firstRequester: FocusRequester? = null,
    trailing: @Composable () -> Unit,
) {
    Column {
        RowTitle(title)
        LazyRow(
            contentPadding = PaddingValues(horizontal = Pad, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(if (Mobile) 12.dp else 18.dp),
        ) {
            items(channels.size, key = { channels[it].id }) { i ->
                val ch = channels[i]
                ChannelTile(
                    ch,
                    modifier = if (i == 0 && firstRequester != null) Modifier.focusRequester(firstRequester) else Modifier,
                ) { Nav.playChannels(channels, ch) }
            }
            item(key = "trailing") { trailing() }
        }
    }
}

@Composable
fun ChannelTile(ch: Channel, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val now = if (Settings.showEpgInLists.on) rememberPrograms(ch.epgId, 1).firstOrNull()?.takeIf { it.isLive() } else null
    Focusable(
        onClick = onClick,
        onLongClick = { Nav.open(Overlay.ChannelMenu(ch)) },
        modifier = modifier.width(TileWidth),
    ) {
        Column {
            Logo(ch.logo, ch.name, Modifier.fillMaxWidth().height(if (Mobile) 84.dp else 116.dp).padding(if (Mobile) 6.dp else 8.dp))
            Column(Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp)) {
                T(ch.name, weight = FontWeight.Bold, size = if (Mobile) 14.sp else 16.sp)
                Spacer(Modifier.height(2.dp))
                T(now?.title ?: " ", size = if (Mobile) 12.sp else 14.sp, color = C.dim)
                Spacer(Modifier.height(8.dp))
                ProgressBar(now?.progress() ?: 0f, Modifier.fillMaxWidth())
            }
        }
    }
}

/** Een film of aflevering die je nog niet af hebt. */
@Composable
private fun WatchTile(w: WatchItem) {
    Focusable(
        onClick = { Nav.playWatch(w) },
        onLongClick = { Nav.open(Overlay.WatchMenu(w)) },
        modifier = Modifier.width(if (Mobile) 220.dp else 320.dp),
    ) {
        Column {
            Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
                Poster(w.image, "", Modifier.fillMaxSize())
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f)))))
                Box(
                    Modifier.align(Alignment.Center).size(48.dp).clip(CircleShape).background(C.accent.copy(alpha = 0.9f)),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Filled.PlayArrow, Color.White, 30.dp) }
                T("nog ${duration((w.duration - w.position).coerceAtLeast(0))}", Modifier.align(Alignment.BottomEnd).padding(10.dp), size = 13.sp, weight = FontWeight.Bold)
            }
            ProgressBar(w.progress, Modifier.fillMaxWidth())
            Column(Modifier.padding(12.dp)) {
                T(w.title, weight = FontWeight.Bold, size = 16.sp)
                T(w.subtitle ?: if (w.kind == "movie") "Film" else "Aflevering", size = 13.sp, color = C.dim)
            }
        }
    }
}

@Composable
private fun RecordingTile(r: Recording) {
    Focusable(
        onClick = {
            if (r.uri != null && r.state == RecState.DONE) {
                Nav.play = Playback(listOf(PlayItem(r.title, r.uri, r.logo, subtitle = r.channel, key = "rec:${r.id}", kind = "recording", image = r.logo)), 0, live = false)
            } else Nav.open(Overlay.RecordingMenu(r))
        },
        onLongClick = { Nav.open(Overlay.RecordingMenu(r)) },
        modifier = Modifier.width(TileWidth),
    ) {
        Column {
            Box {
                Logo(r.logo, r.channel, Modifier.fillMaxWidth().height(if (Mobile) 84.dp else 116.dp).padding(8.dp))
                if (r.state == RecState.RECORDING) Row(
                    Modifier.align(Alignment.TopEnd).padding(12.dp).background(C.live, RoundedCornerShape(10.dp)).padding(horizontal = 8.dp, vertical = 2.dp),
                ) { T("OPNAME", size = 11.sp, weight = FontWeight.Black) }
            }
            Column(Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp)) {
                T(r.title, weight = FontWeight.Bold, size = 15.sp)
                T("${r.channel} · ${whenText(r.start, r.end)}", size = 13.sp, color = C.dim)
            }
        }
    }
}

@Composable
private fun ActionTile(label: String, icon: ImageVector, onClick: () -> Unit) {
    Focusable(onClick = onClick, modifier = Modifier.width(if (Mobile) 120.dp else 170.dp).height(if (Mobile) 146.dp else 184.dp), color = C.panel) { focused ->
        Column(
            Modifier.fillMaxSize().padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                Modifier.size(52.dp).clip(CircleShape).background(if (focused) C.accent else C.surfaceHi),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, size = 26.dp) }
            Spacer(Modifier.height(12.dp))
            T(label, size = 15.sp, color = if (focused) C.text else C.dim, maxLines = 2, align = TextAlign.Center)
        }
    }
}

/** Nu en straks op een kanaal; ververst zichzelf elke minuut. */
@Composable
fun rememberPrograms(epgId: String?, count: Int = 2): List<Program> {
    val epg = Library.epg
    val version = Library.epgVersion
    var list by remember(epgId, epg, version) { mutableStateOf(emptyList<Program>()) }
    LaunchedEffect(epgId, epg, version) {
        if (epg == null || epgId == null) return@LaunchedEffect
        while (true) {
            list = withContext(Dispatchers.IO) { runCatching { epg.upcoming(epgId, count) }.getOrDefault(emptyList()) }
            delay(60_000)
        }
    }
    return list
}
