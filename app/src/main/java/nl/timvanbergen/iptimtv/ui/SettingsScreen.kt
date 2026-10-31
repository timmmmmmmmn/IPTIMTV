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
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 48.dp, end = 48.dp, top = 8.dp, bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { T("Logins", size = 22.sp, weight = FontWeight.Bold) }
        items(Library.profiles, key = { it.id }) { p -> ProfileLine(p) }
        item {
            Spacer(Modifier.height(4.dp))
            Button("Login toevoegen", icon = Icons.Filled.Add, onClick = { Nav.open(Overlay.ProfileForm(null)) })
        }
        item {
            Spacer(Modifier.height(24.dp))
            T("Gegevens", size = 22.sp, weight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            T(
                "${cat.channels.size} kanalen · ${cat.movies.size} films · ${cat.shows.size} series" +
                    (if (Library.epgLoading) " · gids wordt bijgewerkt…" else ""),
                color = C.dim, size = 17.sp,
            )
            Library.error?.let { T(it, color = C.live, size = 15.sp, maxLines = 3) }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button("Alles opnieuw ophalen", icon = Icons.Filled.Refresh, onClick = { Library.refresh() })
                Button("Live TV-groepen ordenen", icon = Icons.Filled.Menu, onClick = { Nav.open(Overlay.CategoryOrder) })
            }
        }
    }
}

@Composable
private fun ProfileLine(p: Profile) {
    val active = Library.active?.id == p.id
    Focusable(
        onClick = { Nav.open(Overlay.ProfileMenu(p)) },
        modifier = Modifier.width(760.dp).height(84.dp),
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
        Brand(44)
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
        Modifier.width(700.dp).padding(top = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(6.dp, 6.dp, 6.dp, 60.dp),
    ) {
        item {
            if (!standalone) T(if (p == null) "Login toevoegen" else "Login wijzigen", size = 28.sp, weight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
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
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                T("Live formaat", color = C.dim, modifier = Modifier.width(130.dp))
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
