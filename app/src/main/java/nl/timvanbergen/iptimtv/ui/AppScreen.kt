package nl.timvanbergen.iptimtv.ui

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import nl.timvanbergen.iptimtv.data.Channel
import nl.timvanbergen.iptimtv.data.Library
import nl.timvanbergen.iptimtv.data.Movie
import nl.timvanbergen.iptimtv.data.Profile
import nl.timvanbergen.iptimtv.data.Show

enum class Tab(val label: String, val icon: ImageVector? = null) {
    HOME("Start"), LIVE("Live TV"), MOVIES("Films"), SERIES("Series"),
    SEARCH("Zoeken", Icons.Filled.Search), SETTINGS("Instellingen", Icons.Filled.Settings),
}

/** Schermen die over het hoofdscherm heen komen. */
sealed interface Overlay {
    class Picker(val title: String, val initial: List<String>, val onDone: (List<String>) -> Unit) : Overlay
    class GroupEdit(val groupId: String) : Overlay
    class ChannelMenu(val channel: Channel) : Overlay
    class Guide(val channel: Channel) : Overlay
    class TextInput(val title: String, val initial: String, val onDone: (String) -> Unit) : Overlay
    class MovieInfo(val movie: Movie) : Overlay
    class ShowInfo(val show: Show) : Overlay
    class ProfileForm(val profile: Profile?) : Overlay
    class ProfileMenu(val profile: Profile) : Overlay
    object CategoryOrder : Overlay
}

data class PlayItem(
    val title: String,
    val url: String,
    val logo: String? = null,
    val epgId: String? = null,
    val subtitle: String? = null,
    val channelId: String? = null,
)

class Playback(val items: List<PlayItem>, val index: Int, val live: Boolean, val startAt: Long = 0)

object Nav {
    var tab by mutableStateOf(Tab.HOME)
    /**
     * Waar bij het sluiten van een scherm geeft Compose de focus eerst kort aan de Start-tab.
     * Zolang we de focus zelf terugzetten, mag dat de tab niet laten wisselen.
     */
    var restoring = false
    val overlays = mutableStateListOf<Overlay>()
    var play by mutableStateOf<Playback?>(null)
    var playerKeys: ((KeyEvent) -> Boolean)? = null
    /** Telt op bij elke reset, zodat het startscherm weer de focus pakt. */
    var resets by mutableStateOf(0)
    /** Alleen bij het openen van de app springt de focus naar het eerste kanaal. */
    var focusHome = true

    /** Het element dat als laatste focus had; zo komt de focus na het sluiten van een scherm terug. */
    var lastFocused: FocusRequester? = null
    private val focusStack = mutableListOf<FocusRequester?>()
    var pendingFocus: FocusRequester? = null

    fun open(o: Overlay) {
        // Werd er net een scherm gesloten (bijv. naam invullen → kanalen kiezen), dan geldt diens terugkeerplek.
        focusStack += pendingFocus ?: lastFocused
        pendingFocus = null
        overlays += o
    }

    fun close() {
        if (overlays.isEmpty()) return
        overlays.removeAt(overlays.lastIndex)
        pendingFocus = focusStack.removeLastOrNull()
        restoring = true
    }

    fun playChannels(list: List<Channel>, channel: Channel) {
        val i = list.indexOfFirst { it.id == channel.id }.coerceAtLeast(0)
        Library.setLastChannel(channel.id)
        play = Playback(list.map { it.toPlayItem() }, i, live = true)
    }

    fun reset() {
        play = null
        overlays.clear()
        focusStack.clear()
        pendingFocus = null
        tab = Tab.HOME
        focusHome = true
        resets++
    }
}

fun Channel.toPlayItem() = PlayItem(name, url, logo, epgId, channelId = id)

/** Alles is ontworpen voor een scherm van 1440 breed; zo ziet het er op elke tv hetzelfde uit. */
private const val DESIGN_WIDTH = 1440f

@Composable
fun IptimApp(onExit: () -> Unit) {
    val density = LocalDensity.current
    val widthPx = LocalConfiguration.current.screenWidthDp * density.density
    CompositionLocalProvider(LocalDensity provides Density(widthPx / DESIGN_WIDTH, 1f)) {
        AppContent(onExit)
    }
}

@Composable
private fun AppContent(onExit: () -> Unit) {
    Box(Modifier.fillMaxSize().background(C.bg)) {
        if (Library.profiles.isEmpty()) {
            WelcomeScreen()
        } else {
            MainScreen(onExit)
        }
        Nav.overlays.lastOrNull()?.let { o -> key(o) { OverlayHost(o) } }
        Nav.play?.let { p -> key(p) { PlayerScreen(p) } }
    }
}

@Composable
private fun OverlayHost(o: Overlay) {
    BackHandler { Nav.close() }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(20)
        val back = Nav.pendingFocus
        Nav.pendingFocus = null
        // Terug in dit scherm na het sluiten van een scherm erboven? Dan daar verder.
        if (back == null || !runCatching { back.requestFocus() }.getOrDefault(false)) {
            runCatching { focus.requestFocus() }
        }
        Nav.restoring = false
    }
    // Focus blijft binnen het bovenste scherm; het hoofdscherm eronder is onbereikbaar.
    Box(
        Modifier
            .fillMaxSize()
            .background(C.bg.copy(alpha = if (o.isDialog) 0.85f else 1f))
            .focusRequester(focus)
            .focusProperties { onExit = { cancelFocusChange() } }
            .focusGroup(),
    ) {
        when (o) {
            is Overlay.Picker -> ChannelPicker(o)
            is Overlay.GroupEdit -> GroupEditor(o.groupId)
            is Overlay.ChannelMenu -> ChannelMenu(o.channel)
            is Overlay.Guide -> ChannelGuide(o.channel)
            is Overlay.TextInput -> TextInputDialog(o)
            is Overlay.MovieInfo -> MovieInfo(o.movie)
            is Overlay.ShowInfo -> ShowInfo(o.show)
            is Overlay.ProfileForm -> ProfileForm(o.profile)
            is Overlay.ProfileMenu -> ProfileMenu(o.profile)
            Overlay.CategoryOrder -> CategoryOrderScreen()
        }
    }
}

private val Overlay.isDialog get() =
    this is Overlay.ChannelMenu || this is Overlay.TextInput || this is Overlay.GroupEdit || this is Overlay.ProfileMenu

@Composable
private fun MainScreen(onExit: () -> Unit) {
    val tabFocus = remember { Tab.entries.associateWith { FocusRequester() } }
    val content = remember { FocusRequester() }
    var tabsFocused by remember { mutableStateOf(false) }
    val overlayOpen = Nav.overlays.isNotEmpty() || Nav.play != null

    BackHandler(enabled = !overlayOpen) {
        when {
            !tabsFocused -> tabFocus.getValue(Nav.tab).requestFocus()
            Nav.tab != Tab.HOME -> tabFocus.getValue(Tab.HOME).requestFocus()
            else -> onExit()
        }
    }

    // Na het sluiten van een scherm erboven: terug naar waar je was.
    LaunchedEffect(overlayOpen) {
        if (!overlayOpen) {
            delay(30)
            val back = Nav.pendingFocus
            Nav.pendingFocus = null
            val ok = back != null && runCatching { back.requestFocus() }.getOrDefault(false)
            if (!ok && !runCatching { content.requestFocus() }.getOrDefault(false)) {
                runCatching { tabFocus.getValue(Nav.tab).requestFocus() }
            }
            delay(30)
            Nav.restoring = false
        }
    }
    LaunchedEffect(Nav.resets) {
        delay(30)
        runCatching { tabFocus.getValue(Tab.HOME).requestFocus() }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 48.dp, end = 48.dp, top = 28.dp, bottom = 12.dp)
                .onFocusChanged { tabsFocused = it.hasFocus }
                .focusGroup(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Brand()
            Spacer(Modifier.width(40.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                Tab.entries.forEach { t -> TabButton(t, tabFocus.getValue(t)) }
            }
            StatusArea()
        }
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .focusRequester(content)
                .focusRestorer()
                .focusGroup(),
        ) {
            when (Nav.tab) {
                Tab.HOME -> HomeScreen()
                Tab.LIVE -> LiveScreen()
                Tab.MOVIES -> MoviesScreen()
                Tab.SERIES -> SeriesScreen()
                Tab.SEARCH -> SearchScreen()
                Tab.SETTINGS -> SettingsScreen()
            }
        }
    }
}

@Composable
fun Brand(size: Int = 26) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        T("IPTIM", size = size.sp, weight = FontWeight.Black)
        T("TV", size = size.sp, weight = FontWeight.Black, color = C.accent)
    }
}

@Composable
private fun TabButton(t: Tab, requester: FocusRequester) {
    val selected = Nav.tab == t
    var hasFocus by remember { mutableStateOf(false) }
    // Pas wisselen als de focus even blijft staan: als een veld of scherm verdwijnt, geeft
    // Compose de focus soms heel kort aan de eerste tab voordat wij hem terugzetten.
    LaunchedEffect(hasFocus) {
        if (hasFocus) {
            delay(90)
            if (hasFocus && !Nav.restoring) Nav.tab = t
        }
    }
    Focusable(
        onClick = { Nav.tab = t },
        modifier = Modifier
            .focusRequester(requester)
            .onFocusChanged { hasFocus = it.isFocused },
        shape = RoundedCornerShape(22.dp),
        color = Color.Transparent,
        focusedColor = Color.White,
        border = false,
        scale = 1.04f,
    ) { focused ->
        val fg = when {
            focused -> Color.Black
            selected -> C.text
            else -> C.dim
        }
        Row(
            Modifier.height(44.dp).padding(horizontal = if (t.icon != null && t.label == "Instellingen") 12.dp else 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (t.icon != null) Icon(t.icon, fg, 22.dp)
            if (t != Tab.SETTINGS) T(t.label, color = fg, size = 18.sp, weight = if (selected) FontWeight.Bold else FontWeight.Medium)
        }
        if (selected && !focused) Box(
            Modifier.align(Alignment.BottomCenter).padding(bottom = 3.dp).size(18.dp, 3.dp)
                .clip(CircleShape).background(C.accent),
        )
    }
}

@Composable
private fun StatusArea() {
    val now by produceState(System.currentTimeMillis()) {
        while (true) { value = System.currentTimeMillis(); delay(10_000) }
    }
    Column(horizontalAlignment = Alignment.End) {
        T(clock(now), size = 22.sp, weight = FontWeight.Bold)
        val status = Library.loading ?: if (Library.epgLoading) "Gids bijwerken…" else Library.active?.name
        if (status != null) T(status, size = 13.sp, color = C.dim)
    }
}

/** Geeft een element focus zodra het scherm verschijnt. */
@Composable
fun rememberInitialFocus(): FocusRequester {
    val r = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(60)
        runCatching { r.requestFocus() }
    }
    return r
}

@Composable
fun Message(title: String, text: String? = null, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(64.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        T(title, size = 24.sp, weight = FontWeight.Bold)
        if (text != null) {
            Spacer(Modifier.height(8.dp))
            T(text, size = 17.sp, color = C.dim, maxLines = 4)
        }
    }
}

/** Toont laad- of foutstatus wanneer er (nog) niets is. */
@Composable
fun EmptyState(what: String) {
    when {
        Library.loading != null -> Message(Library.loading!!, "Even geduld, dit kan bij de eerste keer even duren.")
        Library.error != null -> Message("Er ging iets mis", Library.error)
        else -> Message("Geen $what gevonden", "Deze login heeft geen $what.")
    }
}
