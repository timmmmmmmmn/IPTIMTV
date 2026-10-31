package nl.timvanbergen.iptimtv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import nl.timvanbergen.iptimtv.data.Channel
import nl.timvanbergen.iptimtv.data.Library

object LiveState {
    var category by mutableStateOf<String?>(null)
}

@Composable
fun LiveScreen() {
    val categories = Library.liveCategories()
    if (categories.isEmpty()) {
        EmptyState("live kanalen"); return
    }
    val current = categories.firstOrNull { it.id == LiveState.category } ?: categories.first()
    val channels = Library.catalog.channelsByCategory[current.id].orEmpty()

    Row(Modifier.fillMaxSize().padding(start = 42.dp, end = 48.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        LazyColumn(
            Modifier.width(330.dp).fillMaxHeight().focusRestorer(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            contentPadding = PaddingValues(6.dp, 6.dp, 6.dp, 40.dp),
        ) {
            item(key = "order") {
                Button("Volgorde aanpassen", icon = Icons.Filled.Menu, onClick = { Nav.open(Overlay.CategoryOrder) })
                Spacer(Modifier.height(10.dp))
            }
            itemsIndexed(categories, key = { _, c -> c.id }) { _, c ->
                CategoryItem(
                    c.name,
                    selected = c.id == current.id,
                    badge = Library.catalog.channelsByCategory[c.id]?.size?.toString(),
                    onFocus = { LiveState.category = c.id },
                )
            }
        }
        LazyColumn(
            Modifier.weight(1f).fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(6.dp, 6.dp, 6.dp, 40.dp),
        ) {
            itemsIndexed(channels, key = { _, c -> c.id }) { i, ch ->
                ChannelLine(i + 1, ch) { Nav.playChannels(channels, ch) }
            }
        }
    }
}

@Composable
fun ChannelLine(number: Int?, ch: Channel, onClick: () -> Unit) {
    val programs = rememberPrograms(ch.epgId, 2)
    val now = programs.firstOrNull()?.takeIf { it.isLive() }
    val next = programs.getOrNull(if (now != null) 1 else 0)
    Focusable(
        onClick = onClick,
        onLongClick = { Nav.open(Overlay.ChannelMenu(ch)) },
        modifier = Modifier.fillMaxWidth().height(84.dp),
        scale = 1.02f,
    ) {
        Row(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            if (number != null) T("$number", Modifier.width(44.dp), color = C.dim, size = 16.sp)
            Logo(ch.logo, ch.name, Modifier.size(104.dp, 60.dp))
            Spacer(Modifier.width(18.dp))
            Column(Modifier.weight(1f)) {
                T(ch.name, size = 18.sp, weight = FontWeight.Bold)
                if (now != null) {
                    T("${clock(now.start)}  ${now.title}", size = 15.sp, color = C.text.copy(alpha = 0.85f))
                    Spacer(Modifier.height(5.dp))
                    ProgressBar(now.progress(), Modifier.width(220.dp))
                }
            }
            if (next != null) Column(Modifier.width(300.dp).padding(start = 16.dp)) {
                T("Straks ${clock(next.start)}", size = 13.sp, color = C.dim)
                T(next.title, size = 15.sp, color = C.dim)
            }
        }
    }
}

/**
 * Providergroepen ordenen en verbergen. OK pakt een groep op, met omhoog/omlaag
 * schuif je hem, OK zet hem neer. Rechts staat de knop om te verbergen.
 */
@Composable
fun CategoryOrderScreen() {
    val order = remember { mutableStateListOf<String>().apply { addAll(Library.liveCategories(includeHidden = true).map { it.id }) } }
    val names = remember { Library.catalog.liveCategories.associate { it.id to it.name } }
    var moving by rememberSaveable { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()
    val focus = rememberInitialFocus()
    val movingFocus = remember { FocusRequester() }

    LaunchedEffect(order.toList(), moving) {
        val i = order.indexOf(moving)
        if (i >= 0) {
            runCatching { movingFocus.requestFocus() }
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 32.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                T("Live TV-groepen ordenen", size = 28.sp, weight = FontWeight.Bold)
                T(
                    if (moving != null) "Schuif met omhoog/omlaag, druk OK om neer te zetten"
                    else "Druk OK op een groep om hem op te pakken · rechts: tonen of verbergen",
                    color = if (moving != null) C.accent else C.dim,
                )
            }
            Button("Klaar", primary = true, icon = Icons.Filled.Check, modifier = Modifier.focusRequester(focus), onClick = {
                Library.setLiveOrder(order.toList()); Nav.close()
            })
        }
        Spacer(Modifier.height(20.dp))
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(6.dp),
        ) {
            itemsIndexed(order, key = { _, id -> id }) { index, id ->
                val isMoving = moving == id
                val hidden = id in Library.hiddenCategories
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Focusable(
                        onClick = {
                            if (isMoving) {
                                moving = null; Library.setLiveOrder(order.toList())
                            } else moving = id
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp)
                            .then(if (isMoving) Modifier.focusRequester(movingFocus) else Modifier)
                            .onPreviewKeyEvent { e ->
                                if (!isMoving || e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent isMoving &&
                                    (e.key == Key.DirectionUp || e.key == Key.DirectionDown || e.key == Key.DirectionLeft || e.key == Key.DirectionRight)
                                val to = when (e.key) {
                                    Key.DirectionUp -> index - 1
                                    Key.DirectionDown -> index + 1
                                    Key.DirectionLeft, Key.DirectionRight -> return@onPreviewKeyEvent true
                                    else -> return@onPreviewKeyEvent false
                                }
                                if (to in order.indices) order.add(to, order.removeAt(index))
                                true
                            },
                        color = if (isMoving) C.accent else C.surface,
                        focusedColor = if (isMoving) C.accent else C.surfaceHi,
                        scale = if (isMoving) 1.04f else 1.02f,
                    ) {
                        Row(Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                            T("${index + 1}", Modifier.width(48.dp), color = if (isMoving) Color.White else C.dim)
                            Icon(Icons.Filled.Menu, if (isMoving) Color.White else C.dim, 22.dp)
                            Spacer(Modifier.width(14.dp))
                            T(names[id].orEmpty(), size = 18.sp, weight = FontWeight.Medium, color = if (hidden) C.dim else C.text, modifier = Modifier.weight(1f))
                            if (hidden) T("verborgen", size = 14.sp, color = C.dim)
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Focusable(
                        onClick = { Library.toggleHidden(id) },
                        modifier = Modifier.size(width = 150.dp, height = 56.dp),
                        scale = 1.04f,
                    ) {
                        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                            Icon(if (hidden) Icons.Filled.Close else Icons.Filled.Check, if (hidden) C.dim else C.good, 20.dp)
                            Spacer(Modifier.width(8.dp))
                            T(if (hidden) "Verborgen" else "Zichtbaar", size = 15.sp, color = if (hidden) C.dim else C.text)
                        }
                    }
                }
            }
        }
    }
}
