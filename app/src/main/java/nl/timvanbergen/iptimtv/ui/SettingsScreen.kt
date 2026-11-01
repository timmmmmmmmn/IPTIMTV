package nl.timvanbergen.iptimtv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.platform.LocalContext
import nl.timvanbergen.iptimtv.data.Cloud
import nl.timvanbergen.iptimtv.data.History
import nl.timvanbergen.iptimtv.data.Setting
import nl.timvanbergen.iptimtv.data.Settings
import nl.timvanbergen.iptimtv.data.Updater
import nl.timvanbergen.iptimtv.data.formatWatchTime
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nl.timvanbergen.iptimtv.data.Library
import nl.timvanbergen.iptimtv.data.Profile
import nl.timvanbergen.iptimtv.data.SourceType
import nl.timvanbergen.iptimtv.data.Xtream

@Composable
fun SettingsScreen() {
    val cat = Library.catalog
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = Pad, end = Pad, top = 8.dp, bottom = if (Mobile) 110.dp else 48.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { SectionTitle("Logins") }
        items(Library.profiles, key = { it.id }) { p -> ProfileLine(p) }
        item { Button("Login toevoegen", icon = Icons.Filled.Add, onClick = { Nav.open(Overlay.ProfileForm(null)) }) }

        item { SectionTitle("IPTIMTV-account") }
        item {
            if (Cloud.loggedIn) {
                InfoCard(Ic.Cloud, "Ingelogd als ${Cloud.username}",
                    Cloud.stats?.let { "Deze week ${formatWatchTime(it.week)} · in totaal ${formatWatchTime(it.total)} gekeken" }
                        ?: "Je kijkgeschiedenis wordt bewaard in je account")
                Spacer(Modifier.height(10.dp))
                Button("Uitloggen", onClick = { Cloud.signOut() })
            } else {
                InfoCard(Ic.Cloud, "Niet ingelogd", "Met een account zie je op elk apparaat waar je gebleven was en hoeveel je kijkt.")
                Spacer(Modifier.height(10.dp))
                Wrap {
                    Button("Inloggen", primary = true, onClick = { Nav.open(Overlay.Account(create = false)) })
                    Button("Account maken", onClick = { Nav.open(Overlay.Account(create = true)) })
                }
            }
        }

        item { SectionTitle("Gegevens") }
        item {
            T(
                "${cat.channels.size} kanalen · ${cat.movies.size} films · ${cat.shows.size} series" +
                    (if (Library.epgLoading) " · gids wordt bijgewerkt…" else ""),
                color = C.dim, size = 17.sp, maxLines = 2,
            )
            Library.error?.let { T(it, color = C.live, size = 15.sp, maxLines = 3) }
            Spacer(Modifier.height(12.dp))
            Wrap {
                Button("Alles opnieuw ophalen", icon = Icons.Filled.Refresh, onClick = { Library.refresh() })
                Button("Live TV-groepen ordenen", icon = Icons.Filled.Menu, onClick = { Nav.open(Overlay.CategoryOrder) })
            }
        }

        item { SectionTitle("App") }
        item {
            T("Versie ${Updater.currentVersion}" + (Updater.available?.let { " · nieuwe versie ${it.name} beschikbaar" } ?: ""), color = C.dim, size = 17.sp)
            Updater.status?.let { T(it, color = C.dim, size = 15.sp, maxLines = 2) }
            Spacer(Modifier.height(12.dp))
            Wrap {
                if (Updater.available != null) Button("Nu bijwerken", primary = true, icon = Ic.Download, onClick = { Nav.open(Overlay.Update) })
                else Button("Zoeken naar updates", icon = Icons.Filled.Refresh, onClick = { scope.launch { Updater.check(force = true) } })
                Button("Geavanceerd", icon = Ic.Tune, onClick = { Nav.open(Overlay.Advanced) })
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    T(text, Modifier.padding(top = 18.dp, bottom = 2.dp), size = 22.sp, weight = FontWeight.Bold)
}

@Composable
private fun InfoCard(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, text: String) {
    Row(
        Modifier.widthIn(max = 760.dp).fillMaxWidth().background(C.surface, Rounded).padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(48.dp).clip(CircleShape).background(C.accentDeep), contentAlignment = Alignment.Center) { Icon(icon, size = 26.dp) }
        Spacer(Modifier.width(18.dp))
        Column(Modifier.weight(1f)) {
            T(title, size = 19.sp, weight = FontWeight.Bold)
            T(text, size = 14.sp, color = C.dim, maxLines = 3)
        }
    }
}

/** Knoppen naast elkaar, en op de telefoon onder elkaar als ze niet passen. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun Wrap(content: @Composable () -> Unit) {
    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { content() }
}

/**
 * Geavanceerd: alle knoppen om aan te draaien, in categorieën.
 * Links (of bovenaan op de telefoon) de categorie, rechts de instellingen. OK = volgende keuze.
 */
@Composable
fun AdvancedScreen() {
    var section by remember { mutableIntStateOf(0) }
    val focus = rememberInitialFocus()
    val context = LocalContext.current
    val current = Settings.sections.getOrNull(section)

    @Composable
    fun settingsList(modifier: Modifier) {
        LazyColumn(modifier, verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(6.dp, 6.dp, 6.dp, 60.dp)) {
            if (current != null) {
                item { T(current.description, color = C.dim, modifier = Modifier.padding(bottom = 6.dp)) }
                items(current.settings, key = { it.key }) { s -> SettingLine(s) }
            } else {
                item { T("Opruimen en terugzetten", color = C.dim, modifier = Modifier.padding(bottom = 6.dp)) }
                item { ActionLine("Gids nu opnieuw ophalen", "Haalt de programmagids direct opnieuw op.") { Library.refreshGuide() } }
                item { ActionLine("Afbeeldingen-cache wissen", "Logo's en posters worden opnieuw gedownload.") {
                    coil3.SingletonImageLoader.get(context).let { it.memoryCache?.clear(); it.diskCache?.clear() }
                } }
                item { ActionLine("Kijkgeschiedenis wissen", "Leegt Verder kijken (ook in je account).") { History.clear(); Cloud.clearHistory() } }
                item { ActionLine("Alle instellingen terugzetten", "Alles in Geavanceerd weer zoals het was.") { Settings.resetAll() } }
            }
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = Pad, vertical = if (Mobile) 8.dp else 32.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                T("Geavanceerd", size = 28.sp, weight = FontWeight.Bold)
                T(if (Mobile) "Tik op een instelling om te wisselen" else "OK op een instelling wisselt naar de volgende keuze", color = C.dim, maxLines = 2)
            }
            if (!Mobile) Button("Klaar", primary = true, icon = Icons.Filled.Check, onClick = { Nav.close() })
        }
        Spacer(Modifier.height(16.dp))
        val names = Settings.sections.map { it.title } + "Opruimen"
        if (Mobile) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
                items(names.size) { i -> Button(names[i], selected = i == section, onClick = { section = i }) }
            }
            Spacer(Modifier.height(10.dp))
            settingsList(Modifier.fillMaxSize())
        } else {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                LazyColumn(Modifier.width(320.dp).fillMaxHeight().focusRestorer(), verticalArrangement = Arrangement.spacedBy(4.dp), contentPadding = PaddingValues(6.dp)) {
                    items(names.size) { i ->
                        CategoryItem(names[i], selected = i == section, onFocus = { section = i },
                            modifier = if (i == 0) Modifier.focusRequester(focus) else Modifier)
                    }
                }
                settingsList(Modifier.weight(1f).fillMaxHeight())
            }
        }
    }
}

@Composable
private fun SettingLine(s: Setting) {
    Focusable(onClick = { s.next() }, modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp), scale = 1.02f) { focused ->
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                T(s.title, size = 18.sp, weight = FontWeight.Bold)
                T(s.description, size = 14.sp, color = C.dim, maxLines = 3)
            }
            Spacer(Modifier.width(16.dp))
            val onOff = s.choices.size == 2 && s.choices.any { it.value == "1" }
            if (onOff) Switch(s.on)
            else T(s.label, size = 16.sp, weight = FontWeight.Bold, color = if (focused) C.text else C.accent,
                modifier = Modifier.background(C.bg.copy(alpha = 0.5f), RoundedCornerShape(10.dp)).padding(horizontal = 12.dp, vertical = 6.dp))
        }
    }
}

@Composable
private fun Switch(on: Boolean) {
    Box(
        Modifier.size(52.dp, 30.dp).clip(CircleShape).background(if (on) C.accent else C.surfaceHi).padding(4.dp),
        contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart,
    ) { Box(Modifier.size(22.dp).clip(CircleShape).background(androidx.compose.ui.graphics.Color.White)) }
}

@Composable
private fun ActionLine(title: String, text: String, action: () -> Unit) {
    var done by remember { mutableStateOf(false) }
    Focusable(onClick = { action(); done = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp), scale = 1.02f) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                T(title, size = 18.sp, weight = FontWeight.Bold)
                T(text, size = 14.sp, color = C.dim, maxLines = 2)
            }
            if (done) T("Gedaan", color = C.good, weight = FontWeight.Bold)
        }
    }
}

/** Inloggen of account maken op iptv.timvanbergen.nl. */
@Composable
fun AccountForm(create: Boolean) {
    var name by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val focus = rememberInitialFocus()
    DialogFrame(if (create) "Account maken" else "Inloggen bij IPTIMTV") {
        T(if (create) "Kies een gebruikersnaam en wachtwoord. Je IPTV-gegevens worden niet gedeeld." else "Met je IPTIMTV-account (niet je IPTV-login).",
            color = C.dim, maxLines = 3)
        TvTextField(name, { name = it }, "Gebruikersnaam", Modifier.focusRequester(focus), keyboardType = KeyboardType.Password)
        TvTextField(pass, { pass = it }, "Wachtwoord", password = true)
        message?.let { T(it, color = C.live, maxLines = 3) }
        Wrap {
            Button(if (busy) "Bezig…" else if (create) "Account maken" else "Inloggen", primary = true, icon = Icons.Filled.Check, onClick = {
                if (busy) return@Button
                busy = true; message = null
                scope.launch {
                    val err = Cloud.signIn(name, pass, create)
                    busy = false
                    if (err == null) Nav.close() else message = err
                }
            })
            Button("Annuleren", onClick = { Nav.close() })
        }
    }
}

/** Melding dat er een nieuwe versie op GitHub staat. */
@Composable
fun UpdateDialog() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focus = rememberInitialFocus()
    val r = Updater.available
    DialogFrame(if (r != null) "Nieuwe versie: ${r.name}" else "Updates") {
        if (r == null) {
            T(Updater.status ?: "Je hebt de nieuwste versie.", color = C.dim)
            Button("Sluiten", onClick = { Nav.close() }, modifier = Modifier.focusRequester(focus))
            return@DialogFrame
        }
        T("Je hebt nu versie ${Updater.currentVersion}.", color = C.dim)
        if (r.notes.isNotBlank()) T(r.notes, maxLines = 6, color = C.text.copy(alpha = 0.85f))
        Updater.downloading?.let {
            T("Downloaden… ${(it * 100).toInt()}%", color = C.accent)
            ProgressBar(it, Modifier.fillMaxWidth())
        }
        Updater.status?.let { T(it, color = C.dim, maxLines = 3) }
        Wrap {
            Button("Nu bijwerken", primary = true, icon = Ic.Download, modifier = Modifier.focusRequester(focus), onClick = {
                if (Updater.downloading == null) scope.launch { Updater.install(context) }
            })
            Button("Later", onClick = {
                context.getSharedPreferences("iptimtv", 0).edit().putInt("update_dismissed", r.version).apply()
                Nav.close()
            })
        }
    }
}

@Composable
private fun ProfileLine(p: Profile) {
    val active = Library.active?.id == p.id
    Focusable(
        onClick = { Nav.open(Overlay.ProfileMenu(p)) },
        modifier = Modifier.widthIn(max = 760.dp).fillMaxWidth().height(84.dp),
        scale = 1.02f,
    ) {
        Row(Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(48.dp).clip(CircleShape).background(if (active) C.accent else C.surfaceHi),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.Person, size = 26.dp) }
            Spacer(Modifier.width(18.dp))
            Column(Modifier.weight(1f)) {
                T(p.name, size = 19.sp, weight = FontWeight.Bold)
                T(
                    when (p.type) {
                        SourceType.XTREAM -> "Xtream · ${p.server.removePrefix("http://").removePrefix("https://")} · ${p.username}"
                        SourceType.M3U -> "M3U-playlist"
                    },
                    size = 14.sp, color = C.dim,
                )
            }
            if (active) T("In gebruik", color = C.good, weight = FontWeight.Bold)
        }
    }
}

@Composable
fun ProfileMenu(p: Profile) {
    val focus = rememberInitialFocus()
    val active = Library.active?.id == p.id
    DialogFrame(p.name) {
        if (!active) MenuItem("Deze login gebruiken", Icons.Filled.Check, Modifier.focusRequester(focus)) {
            Nav.close(); Library.activate(p); Nav.tab = Tab.HOME
        }
        MenuItem("Wijzigen", Icons.Filled.Edit, if (active) Modifier.focusRequester(focus) else Modifier) {
            Nav.close(); Nav.open(Overlay.ProfileForm(p))
        }
        MenuItem("Verwijderen", Icons.Filled.Delete) {
            Nav.close(); Library.deleteProfile(p)
        }
    }
}

@Composable
fun WelcomeScreen() {
    Column(Modifier.fillMaxSize().padding(top = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Brand(if (Mobile) 64 else 90)
        Spacer(Modifier.height(6.dp))
        T("Welkom! Voeg je eerste IPTV-login toe.", color = C.dim, size = 18.sp)
        ProfileFormContent(null, standalone = true)
    }
}

@Composable
fun ProfileForm(p: Profile?) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        ProfileFormContent(p, standalone = false)
    }
}

/** Formulier voor een Xtream- of M3U-login. Bij Xtream wordt de login eerst gecontroleerd. */
@Composable
private fun ProfileFormContent(p: Profile?, standalone: Boolean) {
    var type by remember { mutableStateOf(p?.type ?: SourceType.XTREAM) }
    var name by remember { mutableStateOf(p?.name ?: "") }
    var server by remember { mutableStateOf(p?.server ?: "") }
    var user by remember { mutableStateOf(p?.username ?: "") }
    var pass by remember { mutableStateOf(p?.password ?: "") }
    var m3u by remember { mutableStateOf(p?.m3uUrl ?: "") }
    var epg by remember { mutableStateOf(p?.epgUrl ?: "") }
    var format by remember { mutableStateOf(p?.liveFormat ?: "ts") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val focus = rememberInitialFocus()

    fun save() {
        if (busy) return
        val profile = Profile(
            id = p?.id ?: Library.newId(),
            name = name.trim().ifBlank { if (type == SourceType.XTREAM) "IPTV" else "Playlist" },
            type = type,
            server = if (type == SourceType.XTREAM) Xtream.normalizeServer(server) else "",
            username = user.trim(),
            password = pass.trim(),
            m3uUrl = m3u.trim(),
            epgUrl = epg.trim(),
            liveFormat = format,
        )
        when {
            type == SourceType.XTREAM && (server.isBlank() || user.isBlank() || pass.isBlank()) -> {
                message = "Vul server, gebruikersnaam en wachtwoord in"; return
            }
            type == SourceType.M3U && !m3u.trim().startsWith("http") -> {
                message = "Vul een geldige playlist-url in (begint met http)"; return
            }
        }
        busy = true
        message = "Controleren…"
        scope.launch {
            val error = if (type == SourceType.XTREAM) withContext(Dispatchers.IO) { Xtream.checkLogin(profile) } else null
            busy = false
            if (error != null) {
                message = error
            } else {
                message = null
                if (!standalone) Nav.close()
                Library.saveProfile(profile)
                Nav.tab = Tab.HOME
            }
        }
    }

    LazyColumn(
        Modifier.widthIn(max = 700.dp).fillMaxWidth().padding(top = 24.dp, start = if (Mobile) 16.dp else 0.dp, end = if (Mobile) 16.dp else 0.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(6.dp, 6.dp, 6.dp, 60.dp),
    ) {
        item {
            if (!standalone) T(if (p == null) "Login toevoegen" else "Login wijzigen", size = 28.sp, weight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Wrap {
                Button("Xtream (server + login)", selected = type == SourceType.XTREAM, modifier = Modifier.focusRequester(focus), onClick = { type = SourceType.XTREAM })
                Button("M3U-link", selected = type == SourceType.M3U, onClick = { type = SourceType.M3U })
            }
        }
        item { TvTextField(name, { name = it }, "Naam, bijvoorbeeld: Thuis") }
        if (type == SourceType.XTREAM) {
            item { TvTextField(server, { server = it }, "Server, bijvoorbeeld: http://server.com:8080", keyboardType = KeyboardType.Uri) }
            item { TvTextField(user, { user = it }, "Gebruikersnaam", keyboardType = KeyboardType.Password) }
            item { TvTextField(pass, { pass = it }, "Wachtwoord", password = true) }
        } else {
            item { TvTextField(m3u, { m3u = it }, "Playlist-url (http://…/get.php?…)", keyboardType = KeyboardType.Uri) }
        }
        item { TvTextField(epg, { epg = it }, "Gids-url (optioneel, XMLTV)", keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done) }
        if (type == SourceType.XTREAM) item {
            Wrap {
                T("Live formaat", color = C.dim, modifier = Modifier.width(130.dp).padding(top = 12.dp))
                Button("MPEG-TS (aanbevolen)", selected = format == "ts", onClick = { format = "ts" })
                Button("HLS", selected = format == "m3u8", onClick = { format = "m3u8" })
            }
        }
        item {
            message?.let {
                T(it, color = if (busy) C.dim else C.live, size = 16.sp, maxLines = 3)
                Spacer(Modifier.height(6.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(if (busy) "Bezig…" else "Opslaan", primary = true, icon = Icons.Filled.Check, onClick = { save() })
                if (!standalone) Button("Annuleren", onClick = { Nav.close() })
            }
        }
    }
}
