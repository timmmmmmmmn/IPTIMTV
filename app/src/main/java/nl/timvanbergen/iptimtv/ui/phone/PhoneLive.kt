package nl.timvanbergen.iptimtv.ui.phone

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import nl.timvanbergen.iptimtv.data.Library
import nl.timvanbergen.iptimtv.ui.Nav
import nl.timvanbergen.iptimtv.ui.Overlay
import nl.timvanbergen.iptimtv.ui.Tab

private const val FAVORITES = "__fav"

@Composable
fun PhoneLive() {
    val cats = Library.liveCategories()
    val hasFav = Library.favorites.isNotEmpty()
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    val current = selected?.takeIf { it == FAVORITES && hasFav || cats.any { c -> c.id == it } }
        ?: if (hasFav) FAVORITES else cats.firstOrNull()?.id
    val channels = if (current == FAVORITES) Library.channels(Library.favorites) else Library.catalog.channelsByCategory[current].orEmpty()
    val list = rememberLazyListState()
    LaunchedEffect(current) { list.scrollToItem(0) }

    Column(Modifier.fillMaxSize()) {
        PhoneTopBar("Live TV") {
            IconButton(onClick = { Nav.tab = Tab.SEARCH }) { Icon(Icons.Filled.Search, "Zoeken") }
        }
        if (cats.isEmpty()) {
            EmptyState(if (Library.loading != null) "Kanalen laden…" else "Geen kanalen", Library.error)
            return
        }
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (hasFav) item {
                FilterChip(
                    selected = current == FAVORITES, onClick = { selected = FAVORITES },
                    label = { Text("Mijn kanalen") },
                    leadingIcon = { Icon(Icons.Filled.Star, null, Modifier.size(FilterChipDefaults.IconSize)) },
                )
            }
            items(cats, key = { it.id }) { c ->
                FilterChip(selected = current == c.id, onClick = { selected = c.id }, label = { Text(c.name, maxLines = 1) })
            }
            item {
                AssistChip(onClick = { Nav.open(Overlay.CategoryOrder) }, label = { Text("Volgorde") })
            }
        }
        Spacer(Modifier.height(4.dp))
        LazyColumn(state = list, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
            itemsIndexed(channels, key = { _, ch -> ch.id }) { i, ch ->
                val fav = ch.id in Library.favorites
                ChannelRow(
                    ch, i + 1,
                    trailing = {
                        IconButton(onClick = { Library.toggleFavorite(ch.id) }) {
                            Icon(Icons.Filled.Star, if (fav) "Uit Mijn kanalen" else "In Mijn kanalen",
                                tint = if (fav) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outline)
                        }
                    },
                    onLongClick = { ChannelSheet.channel = ch },
                ) { Nav.playChannels(channels, ch) }
            }
        }
    }
    ChannelSheetHost()
}
