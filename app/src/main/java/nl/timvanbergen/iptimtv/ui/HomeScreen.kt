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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import nl.timvanbergen.iptimtv.data.Channel
import nl.timvanbergen.iptimtv.data.Library
import nl.timvanbergen.iptimtv.data.Program

private val TileWidth = 230.dp

@Composable
fun HomeScreen() {
    val favorites = Library.channels(Library.favorites)
    val firstTile = remember { FocusRequester() }

    // Bij het openen van de app staat de focus meteen op het eerste kanaal: één keer OK en je kijkt.
    LaunchedEffect(favorites.isNotEmpty(), Nav.resets) {
        if (favorites.isNotEmpty() && Nav.focusHome) {
            delay(120)
            if (runCatching { firstTile.requestFocus() }.getOrDefault(false)) Nav.focusHome = false
        }
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
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
                    ActionTile(
                        if (favorites.isEmpty()) "Kanalen toevoegen" else "Kanalen kiezen",
                        Icons.Filled.Add,
                    ) {
                        Nav.open(Overlay.Picker("Mijn kanalen", Library.favorites.toList()) { Library.setFavorites(it) })
                    }
                },
            )
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
        item(key = "new") {
            Row(Modifier.padding(horizontal = 48.dp, vertical = 16.dp)) {
                Button("Nieuwe groep maken", icon = Icons.Filled.Add, onClick = { createGroup() })
            }
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
        T(title, Modifier.padding(start = 48.dp, top = 12.dp, bottom = 10.dp), size = 22.sp, weight = FontWeight.Bold)
        LazyRow(
            contentPadding = PaddingValues(horizontal = 48.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
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
    val now = rememberPrograms(ch.epgId, 1).firstOrNull()?.takeIf { it.isLive() }
    Focusable(
        onClick = onClick,
        onLongClick = { Nav.open(Overlay.ChannelMenu(ch)) },
        modifier = modifier.width(TileWidth),
    ) {
        Column {
            Logo(ch.logo, ch.name, Modifier.fillMaxWidth().height(116.dp).padding(8.dp))
            Column(Modifier.padding(start = 14.dp, end = 14.dp, bottom = 12.dp)) {
                T(ch.name, weight = FontWeight.Bold, size = 16.sp)
                Spacer(Modifier.height(2.dp))
                T(now?.title ?: " ", size = 14.sp, color = C.dim)
                Spacer(Modifier.height(8.dp))
                ProgressBar(now?.progress() ?: 0f, Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun ActionTile(label: String, icon: ImageVector, onClick: () -> Unit) {
    Focusable(onClick = onClick, modifier = Modifier.width(170.dp).height(184.dp), color = C.panel) { focused ->
        Column(
            Modifier.fillMaxSize().padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                Modifier.size(56.dp).clip(CircleShape).background(if (focused) C.accent else C.surfaceHi),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, size = 28.dp) }
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
