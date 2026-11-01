package nl.timvanbergen.iptimtv.ui.phone

import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn as AOptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import nl.timvanbergen.iptimtv.data.Cast
import nl.timvanbergen.iptimtv.data.CastItem
import nl.timvanbergen.iptimtv.data.Channel
import nl.timvanbergen.iptimtv.data.Library
import nl.timvanbergen.iptimtv.ui.AutoPlayLast
import nl.timvanbergen.iptimtv.ui.Ic
import nl.timvanbergen.iptimtv.ui.Nav
import nl.timvanbergen.iptimtv.ui.Overlay
import nl.timvanbergen.iptimtv.ui.Playback
import nl.timvanbergen.iptimtv.ui.Tab
import nl.timvanbergen.iptimtv.ui.TabContent

private class Dest(val tab: Tab, val label: String, val icon: ImageVector)

private val dests = listOf(
    Dest(Tab.HOME, "Start", Icons.Filled.Home),
    Dest(Tab.LIVE, "Live TV", Ic.Tv),
    Dest(Tab.SPORT, "Sport", Ic.Ball),
    Dest(Tab.MOVIES, "Films", Ic.Movie),
    Dest(Tab.SEARCH, "Zoeken", Icons.Filled.Search),
)

/** De telefoon-app: een eigen opzet met onderbalk, in plaats van de tv-schermen verkleind. */
@AOptIn(UnstableApi::class)
@Composable
fun PhoneMain(onExit: () -> Unit) {
    AutoPlayLast()
    BackHandler(enabled = Nav.overlays.isEmpty() && Nav.play == null) {
        if (Nav.tab != Tab.HOME) Nav.tab = Tab.HOME else onExit()
    }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            Column {
                if (Cast.device != null) CastBar()
                NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                    dests.forEach { d ->
                        val selected = Nav.tab == d.tab || (d.tab == Tab.MOVIES && Nav.tab == Tab.SERIES)
                        NavigationBarItem(
                            selected = selected,
                            onClick = { Nav.tab = d.tab },
                            icon = { Icon(d.icon, null) },
                            label = { Text(d.label, maxLines = 1) },
                        )
                    }
                }
            }
        },
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad).consumeWindowInsets(pad)) {
            when (Nav.tab) {
                Tab.HOME -> PhoneHome()
                Tab.LIVE -> PhoneLive()
                Tab.SPORT -> PhoneSport()
                Tab.MOVIES, Tab.SERIES -> PhoneVod()
                Tab.SEARCH -> PhoneSearch()
                Tab.SETTINGS -> PhoneSettings()
            }
        }
    }
    CastSheetHost()
}

/** Bovenbalk van een tab, met casten en je account rechts. */
@kotlin.OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhoneTopBar(title: String, actions: @Composable RowScope.() -> Unit = {}) {
    TopAppBar(
        title = { Text(title, style = MaterialTheme.typography.headlineSmall) },
        actions = {
            actions()
            CastIconButton()
            IconButton(onClick = { Nav.tab = Tab.SETTINGS }) { Icon(Ic.Account, "Instellingen") }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
    )
}

@kotlin.OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PhoneSettings() {
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Instellingen", style = MaterialTheme.typography.headlineSmall) },
            navigationIcon = { IconButton(onClick = { Nav.tab = Tab.HOME }) { Icon(Icons.Filled.ArrowBack, "Terug") } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
        )
        Box(Modifier.weight(1f)) { TabContent(Tab.SETTINGS) }
    }
}

// ---------- casten ----------

object CastUi {
    /** Wat er na het kiezen van een apparaat moet gaan spelen (null = alleen verbinden). */
    var sheetFor by mutableStateOf<List<CastItem>?>(null)
    var sheetIndex = 0
    var open by mutableStateOf(false)

    fun choose(items: List<CastItem>? = null, index: Int = 0) {
        sheetFor = items; sheetIndex = index; open = true
    }
}

/** Afspelen naar de Chromecast sturen in plaats van op de telefoon. */
fun Playback.toCast() = items.map { CastItem(it.url, it.title, it.subtitle, it.image ?: it.logo, live) }

@AOptIn(UnstableApi::class)
@Composable
fun CastIconButton() {
    if (!Cast.available) return
    IconButton(onClick = { if (Cast.device == null) CastUi.choose() else CastUi.open = true }) {
        Icon(if (Cast.device != null) Ic.CastOn else Ic.Cast, "Casten", tint = if (Cast.device != null) MaterialTheme.colorScheme.primary else LocalContentColor.current)
    }
}

@AOptIn(UnstableApi::class)
@kotlin.OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CastSheetHost() {
    if (!CastUi.open) return
    DisposableEffect(Unit) {
        Cast.startSearch()
        onDispose { Cast.stopSearch() }
    }
    ModalBottomSheet(onDismissRequest = { CastUi.open = false }) {
        Column(Modifier.padding(bottom = 24.dp)) {
            Text(if (Cast.device != null) "Verbonden met ${Cast.device}" else "Casten naar", style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
            if (Cast.device != null) {
                Cast.current?.let { Text(it.title, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 24.dp)) }
                Spacer(Modifier.height(16.dp))
                FilledTonalButton(onClick = { Cast.disconnect(); CastUi.open = false }, modifier = Modifier.padding(horizontal = 24.dp)) {
                    Text("Stoppen met casten")
                }
            } else {
                if (Cast.devices.isEmpty()) {
                    Row(Modifier.padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(16.dp))
                        Text("Zoeken naar Chromecast en Google TV in je netwerk…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Cast.devices.forEach { r ->
                    ListItem(
                        headlineContent = { Text(r.name) },
                        supportingContent = r.description?.takeIf { it.isNotBlank() }?.let { { Text(it) } },
                        leadingContent = { Icon(Ic.Tv, null, tint = MaterialTheme.colorScheme.primary) },
                        trailingContent = if (Cast.connecting) ({ CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) }) else null,
                        modifier = Modifier.clickable {
                            val items = CastUi.sheetFor
                            val first = items?.getOrNull(CastUi.sheetIndex)
                            Cast.connect(r, first ?: CastItem("", "", null, null, false), items.orEmpty(), CastUi.sheetIndex)
                            if (items != null) Nav.play = null
                            CastUi.open = false
                        },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    )
                }
                Text("Je telefoon en de Chromecast moeten op hetzelfde wifi-netwerk zitten.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp))
            }
        }
    }
}

/** Balkje boven de tabs zolang er gecast wordt. */
@AOptIn(UnstableApi::class)
@Composable
private fun CastBar() {
    val item = Cast.current
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp).clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.primaryContainer).clickable { CastUi.open = true }
            .padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Ic.CastOn, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(item?.title ?: "Verbonden", style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onPrimaryContainer)
            Text("Op ${Cast.device}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
        }
        if (item?.live == true && Cast.queue.size > 1) {
            IconButton(onClick = { Cast.skip(-1) }) { Icon(Ic.SkipPrev, "Vorig kanaal") }
            IconButton(onClick = { Cast.skip(1) }) { Icon(Ic.SkipNext, "Volgend kanaal") }
        }
        if (item != null) IconButton(onClick = { Cast.togglePause() }) { Icon(if (Cast.playing) Ic.Pause else Icons.Filled.PlayArrow, "Pauze") }
        IconButton(onClick = { Cast.disconnect() }) { Icon(Icons.Filled.Close, "Stoppen met casten") }
    }
}

// ---------- kanaalmenu (lang indrukken) ----------

object ChannelSheet {
    var channel by mutableStateOf<Channel?>(null)
}

@kotlin.OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelSheetHost() {
    val ch = ChannelSheet.channel ?: return
    val fav = ch.id in Library.favorites
    ModalBottomSheet(onDismissRequest = { ChannelSheet.channel = null }) {
        Row(Modifier.padding(horizontal = 24.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            ChannelLogo(ch.logo, ch.name, Modifier.size(64.dp, 44.dp))
            Spacer(Modifier.width(14.dp))
            Text(ch.name, style = MaterialTheme.typography.titleLarge)
        }
        Spacer(Modifier.height(8.dp))
        val itemColors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
        ListItem(
            headlineContent = { Text(if (fav) "Uit Mijn kanalen halen" else "Toevoegen aan Mijn kanalen") },
            leadingContent = { Icon(Icons.Filled.Star, null, tint = if (fav) MaterialTheme.colorScheme.tertiary else LocalContentColor.current) },
            modifier = Modifier.clickable { Library.toggleFavorite(ch.id); ChannelSheet.channel = null },
            colors = itemColors,
        )
        ListItem(
            headlineContent = { Text("Programmagids") },
            leadingContent = { Icon(Icons.Filled.DateRange, null) },
            modifier = Modifier.clickable { ChannelSheet.channel = null; Nav.open(Overlay.Guide(ch)) },
            colors = itemColors,
        )
        ListItem(
            headlineContent = { Text("Groepen en meer") },
            leadingContent = { Icon(Ic.More, null) },
            modifier = Modifier.clickable { ChannelSheet.channel = null; Nav.open(Overlay.ChannelMenu(ch)) },
            colors = itemColors,
        )
        Spacer(Modifier.height(24.dp))
    }
}
