package nl.timvanbergen.iptimtv.ui

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import nl.timvanbergen.iptimtv.R
import nl.timvanbergen.iptimtv.data.Channel
import nl.timvanbergen.iptimtv.data.Cloud
import nl.timvanbergen.iptimtv.data.Library
import nl.timvanbergen.iptimtv.data.Match
import nl.timvanbergen.iptimtv.data.Movie
import nl.timvanbergen.iptimtv.data.Profile
import nl.timvanbergen.iptimtv.data.Program
import nl.timvanbergen.iptimtv.data.Recording
import nl.timvanbergen.iptimtv.data.Reminder
import nl.timvanbergen.iptimtv.data.Reminders
import nl.timvanbergen.iptimtv.data.Sport
import nl.timvanbergen.iptimtv.data.Settings
import nl.timvanbergen.iptimtv.data.Show
import nl.timvanbergen.iptimtv.data.SourceType
import nl.timvanbergen.iptimtv.data.Updater
import nl.timvanbergen.iptimtv.data.WatchItem

enum class Tab(val label: String, val icon: ImageVector? = null) {
    HOME("Start", Icons.Filled.Home), LIVE("Live TV", Ic.Tv), SPORT("Sport", Ic.Ball),
    MOVIES("Films", Ic.Movie), SERIES("Series", Ic.Series),
    SEARCH("Zoeken", Icons.Filled.Search), SETTINGS("Instellingen", Icons.Filled.Settings),
}

/** Schermen die over het hoofdscherm heen komen. */
sealed interface Overlay {
    class Picker(val title: String, val initial: List<String>, val onDone: (List<String>) -> Unit) : Overlay
    class GroupEdit(val groupId: String) : Overlay
    class ChannelMenu(val channel: Channel) : Overlay
    class Guide(val channel: Channel) : Overlay
    class ProgramMenu(val channel: Channel, val program: Program) : Overlay
    class TextInput(val title: String, val initial: String, val onDone: (String) -> Unit) : Overlay
    class MovieInfo(val movie: Movie) : Overlay
    class ShowInfo(val show: Show) : Overlay
    class ProfileForm(val profile: Profile?) : Overlay
    class ProfileMenu(val profile: Profile) : Overlay
    class Account(val create: Boolean) : Overlay
    class MatchInfo(val match: Match) : Overlay
    class RecordingMenu(val recording: Recording) : Overlay
    class WatchMenu(val item: WatchItem) : Overlay
    object CategoryOrder : Overlay
    object Advanced : Overlay
    object Update : Overlay
}

data class PlayItem(
    val title: String,
    val url: String,
    val logo: String? = null,
    val epgId: String? = null,
    val subtitle: String? = null,
    val channelId: String? = null,
    /** Voor kijkgeschiedenis: sleutel, soort en (bij afleveringen) de serie. */
    val key: String? = null,
    val kind: String = "live",
    val parent: String? = null,
    val image: String? = null,
)

class Playback(val items: List<PlayItem>, val index: Int, val live: Boolean, val startAt: Long = 0)

/** Sleutel voor de kijkgeschiedenis; nooit het stream-adres zelf (daar zit je wachtwoord in). */
fun watchKey(kind: String, url: String): String =
    if (Library.active?.type == SourceType.XTREAM && "/$kind/".replace("episode", "series") in url) "$kind:" + url.substringAfterLast('/')
    else "m3u:" + Integer.toHexString(url.hashCode())

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
    var autoPlayed = false

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

    /** Een item uit "Verder kijken" afspelen (ook als het van een ander apparaat komt). */
    fun playWatch(w: WatchItem, fromStart: Boolean = false) {
        val p = Library.active ?: return
        val seg = w.key.substringAfter(':')
        val url = when {
            w.key.startsWith("movie:") && p.type == SourceType.XTREAM -> "${p.server}/movie/${p.username}/${p.password}/$seg"
            w.key.startsWith("episode:") && p.type == SourceType.XTREAM -> "${p.server}/series/${p.username}/${p.password}/$seg"
            else -> Library.catalog.movies.firstOrNull { watchKey("movie", it.url) == w.key }?.url
        } ?: return
        play = Playback(
            listOf(PlayItem(w.title, url, w.image, subtitle = w.subtitle, key = w.key, kind = w.kind, parent = w.parent, image = w.image)),
            0, live = false, startAt = if (fromStart) 0 else w.position,
        )
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

fun Channel.toPlayItem() = PlayItem(name, url, logo, epgId, channelId = id, key = "live:$id", kind = "live", image = logo)

/** Alles is ontworpen voor een tv van 1440 breed; zo ziet het er op elke tv hetzelfde uit. */
private const val DESIGN_WIDTH = 1440f

@Composable
fun IptimApp(onExit: () -> Unit) {
    val density = LocalDensity.current
    val scale = Settings.uiScale.float
    val d = if (Mobile) {
        Density(density.density * scale, density.fontScale)
    } else {
        val widthPx = LocalConfiguration.current.screenWidthDp * density.density
        Density(widthPx / (DESIGN_WIDTH / scale), 1f)
    }
    CompositionLocalProvider(LocalDensity provides d) {
        AppContent(onExit)
    }
}

@Composable
private fun AppContent(onExit: () -> Unit) {
    val context = LocalContext.current
    // Op de achtergrond: account bijwerken en naar updates luisteren.
    LaunchedEffect(Nav.resets) { Cloud.refresh() }
    LaunchedEffect(Unit) {
        while (true) {
            if (Settings.autoUpdate.on) {
                val r = Updater.check()
                val prefs = context.getSharedPreferences("iptimtv", 0)
                // Niet midden in het eerste inloggen of tijdens het kijken.
                while (Library.profiles.isEmpty() || Nav.play != null || Nav.overlays.isNotEmpty()) delay(5000)
                if (r != null && prefs.getInt("update_dismissed", 0) != r.version) {
                    Nav.open(Overlay.Update)
                }
            }
            delay(6 * 3600_000L)
        }
    }
    Box(Modifier.fillMaxSize().background(C.bg)) {
        when {
            Library.profiles.isEmpty() -> Box(if (Mobile) Modifier.systemBarsPadding() else Modifier) { WelcomeScreen() }
            Mobile -> MobileMain(onExit)
            else -> MainScreen(onExit)
        }
        Nav.overlays.lastOrNull()?.let { o -> key(o) { OverlayHost(o) } }
        Nav.play?.let { p -> key(p) { PlayerScreen(p) } }
        Reminders.banner?.let { r -> key(r) { ReminderBanner(r, Modifier.align(Alignment.TopCenter)) } }
    }
}

@Composable
private fun OverlayHost(o: Overlay) {
    BackHandler { Nav.close() }
    val focus = remember { FocusRequester() }
    var inside by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(20)
        val back = Nav.pendingFocus
        Nav.pendingFocus = null
        // Terug in dit scherm na het sluiten van een scherm erboven? Dan daar verder.
        if (back == null || !runCatching { back.requestFocus() }.getOrDefault(false)) {
            runCatching { focus.requestFocus() }
        }
        Nav.restoring = false
        // Zat er nog een tekstveld of iets anders vast aan de focus? Dan nog even doorproberen.
        repeat(10) {
            delay(150)
            if (!inside && !Mobile) runCatching { focus.requestFocus() }
        }
    }
    // Focus blijft binnen het bovenste scherm; het hoofdscherm eronder is onbereikbaar.
    Box(
        Modifier
            .fillMaxSize()
            .background(C.bg.copy(alpha = if (o.isDialog) 0.88f else 1f))
            .then(if (Mobile) Modifier.systemBarsPadding() else Modifier)
            .onFocusChanged { inside = it.hasFocus }
            .focusRequester(focus)
            .focusProperties { onExit = { cancelFocusChange() } }
            .focusGroup(),
    ) {
        when (o) {
            is Overlay.Picker -> ChannelPicker(o)
            is Overlay.GroupEdit -> GroupEditor(o.groupId)
            is Overlay.ChannelMenu -> ChannelMenu(o.channel)
            is Overlay.Guide -> ChannelGuide(o.channel)
            is Overlay.ProgramMenu -> ProgramMenu(o.channel, o.program)
            is Overlay.TextInput -> TextInputDialog(o)
            is Overlay.MovieInfo -> MovieInfo(o.movie)
            is Overlay.ShowInfo -> ShowInfo(o.show)
            is Overlay.ProfileForm -> ProfileForm(o.profile)
            is Overlay.ProfileMenu -> ProfileMenu(o.profile)
            is Overlay.Account -> AccountForm(o.create)
            is Overlay.MatchInfo -> MatchInfo(o.match)
            is Overlay.RecordingMenu -> RecordingMenu(o.recording)
            is Overlay.WatchMenu -> WatchMenu(o.item)
            Overlay.CategoryOrder -> CategoryOrderScreen()
            Overlay.Advanced -> AdvancedScreen()
            Overlay.Update -> UpdateDialog()
        }
    }
}

private val Overlay.isDialog get() =
    this is Overlay.ChannelMenu || this is Overlay.TextInput || this is Overlay.GroupEdit || this is Overlay.ProfileMenu ||
        this is Overlay.ProgramMenu || this is Overlay.RecordingMenu || this is Overlay.WatchMenu || this is Overlay.Update ||
        this is Overlay.Account

/** Banner van een halve minuut: de wedstrijd begint zo, met de zender waarop hij te zien is. */
@Composable
private fun ReminderBanner(r: Reminder, modifier: Modifier) {
    val channel by produceState<String?>(null, r) {
        val m = SportState.matches?.firstOrNull { Reminders.idFor(it) == r.id }
        value = m?.let { Sport.channelsFor(it, Library.catalog, Library.epg).firstOrNull()?.channel?.name }
    }
    LaunchedEffect(r) {
        delay(30_000)
        if (Reminders.banner == r) Reminders.banner = null
    }
    Row(
        modifier.then(if (Mobile) Modifier.systemBarsPadding() else Modifier).padding(top = 20.dp, start = 16.dp, end = 16.dp)
            .background(C.accentDeep, RoundedCornerShape(20.dp))
            .border(1.dp, C.accent, RoundedCornerShape(20.dp))
            .padding(horizontal = 22.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Ic.Ball, C.text, 28.dp)
        Spacer(Modifier.width(14.dp))
        Column {
            T("${r.title} begint over 5 minuten", size = 19.sp, weight = FontWeight.Bold, maxLines = 2)
            T(r.league + (channel?.let { " · te zien op $it" } ?: ""), size = 14.sp, color = C.text.copy(alpha = 0.85f), maxLines = 2)
        }
    }
}

/** Optie "laatste kanaal afspelen bij openen". */
@Composable
fun AutoPlayLast() {
    val catalog = Library.catalog
    LaunchedEffect(catalog) {
        if (Nav.autoPlayed || catalog.channels.isEmpty()) return@LaunchedEffect
        Nav.autoPlayed = true
        if (Settings.startup.value != "last") return@LaunchedEffect
        val id = Library.lastChannel() ?: return@LaunchedEffect
        val ch = catalog.channelById[id] ?: return@LaunchedEffect
        val list = Library.channels(Library.favorites).takeIf { l -> l.any { it.id == id } } ?: catalog.channelsByCategory[ch.categoryId].orEmpty()
        Nav.playChannels(list, ch)
    }
}

@Composable
fun TabContent(tab: Tab) {
    when (tab) {
        Tab.HOME -> HomeScreen()
        Tab.LIVE -> LiveScreen()
        Tab.SPORT -> SportScreen()
        Tab.MOVIES -> MoviesScreen()
        Tab.SERIES -> SeriesScreen()
        Tab.SEARCH -> SearchScreen()
        Tab.SETTINGS -> SettingsScreen()
    }
}

@Composable
private fun MainScreen(onExit: () -> Unit) {
    val tabFocus = remember { Tab.entries.associateWith { FocusRequester() } }
    val content = remember { FocusRequester() }
    var tabsFocused by remember { mutableStateOf(false) }
    val overlayOpen = Nav.overlays.isNotEmpty() || Nav.play != null
    AutoPlayLast()

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
                .padding(start = 44.dp, end = 48.dp, top = 22.dp, bottom = 10.dp)
                .onFocusChanged { tabsFocused = it.hasFocus }
                .focusGroup(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Brand()
            Spacer(Modifier.width(28.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
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
            TabContent(Nav.tab)
        }
    }
}

/** Het logo linksboven. */
@Composable
fun Brand(height: Int = 46) {
    Image(
        painterResource(R.drawable.logo_inapp),
        contentDescription = "IPTIMTV",
        contentScale = ContentScale.Fit,
        modifier = Modifier.height(height.dp),
    )
}

@Composable
private fun TabButton(t: Tab, requester: FocusRequester) {
    val selected = Nav.tab == t
    var hasFocus by remember { mutableStateOf(false) }
    // Pas wisselen als de focus even blijft staan: als een veld of scherm verdwijnt, geeft
    // Compose de focus soms heel kort aan de eerste tab voordat wij hem terugzetten.
    LaunchedEffect(hasFocus) {
        if (hasFocus && Settings.tabOnFocus.value == "focus") {
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
        val iconOnly = t == Tab.SETTINGS
        Row(
            Modifier.height(44.dp).padding(horizontal = if (iconOnly) 12.dp else 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (t == Tab.SEARCH || t == Tab.SETTINGS || t == Tab.SPORT) Icon(t.icon!!, fg, 22.dp)
            if (!iconOnly) T(t.label, color = fg, size = 18.sp, weight = if (selected) FontWeight.Bold else FontWeight.Medium)
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
        if (Settings.showClock.on) T(clock(now), size = 40.sp, weight = FontWeight.Bold)
        val status = Library.loading ?: if (Library.epgLoading) "Gids bijwerken…" else Library.active?.name
        if (status != null) T(status, size = 14.sp, color = C.dim)
    }
}

/** Geeft een element focus zodra het scherm verschijnt. */
@Composable
fun rememberInitialFocus(): FocusRequester {
    val r = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(60)
        if (!Mobile) runCatching { r.requestFocus() }
    }
    return r
}

@Composable
fun Message(title: String, text: String? = null, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(if (Mobile) 24.dp else 64.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        T(title, size = 24.sp, weight = FontWeight.Bold, maxLines = 2)
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
