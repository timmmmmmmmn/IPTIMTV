package nl.timvanbergen.iptimtv.ui.phone

import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import nl.timvanbergen.iptimtv.data.*
import nl.timvanbergen.iptimtv.ui.Nav
import nl.timvanbergen.iptimtv.ui.SportState
import nl.timvanbergen.iptimtv.ui.clock
import nl.timvanbergen.iptimtv.ui.countryName
import nl.timvanbergen.iptimtv.ui.Overlay

@Composable
fun PhoneSport() {
    LoadMatches()
    val all = SportState.matches
    val leagues = all.orEmpty().map { it.league }.distinct()
    val shown = all.orEmpty().filter { SportState.league == null || it.league.id == SportState.league }

    Column(Modifier.fillMaxSize()) {
        PhoneTopBar("Sport")
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(listOf(-1 to "Gisteren", 0 to "Vandaag", 1 to "Morgen")) { (d, label) ->
                FilterChip(selected = SportState.day == d, onClick = {
                    if (SportState.day != d) { SportState.day = d; SportState.matches = null; SportState.league = null }
                }, label = { Text(label) })
            }
        }
        if (leagues.size > 1) LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item { FilterChip(selected = SportState.league == null, onClick = { SportState.league = null }, label = { Text("Alles") }) }
            items(leagues, key = { it.id }) { l ->
                FilterChip(selected = SportState.league == l.id, onClick = { SportState.league = l.id }, label = { Text(l.name) })
            }
        }
        when {
            all == null -> Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            all.isEmpty() -> EmptyState("Geen wedstrijden", "Er zijn op deze dag geen wedstrijden in de grote competities.", "Opnieuw laden") {
                SportState.matches = null; SportState.loadedAt = 0
            }
            else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                shown.groupBy { it.league }.forEach { (league, matches) ->
                    item(key = "h" + league.id) {
                        Text(league.name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 4.dp, top = 18.dp, bottom = 8.dp))
                    }
                    item(key = "c" + league.id) {
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                            matches.forEachIndexed { i, m ->
                                if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                MatchRow(m)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MatchRow(m: Match) {
    Row(
        Modifier.fillMaxWidth().clickable { Nav.open(Overlay.MatchInfo(m)) }.padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.width(58.dp)) {
            when (m.state) {
                "in" -> { LivePill(); Text(m.detail, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1) }
                "post" -> Text("Einde", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                else -> Text(clock(m.start), style = MaterialTheme.typography.titleMedium)
            }
        }
        Column(Modifier.weight(1f)) {
            listOf(m.home, m.away).forEach { t ->
                Row(Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(20.dp)) { if (t.logo != null) AsyncImage(t.logo, null, Modifier.fillMaxSize()) }
                    Spacer(Modifier.width(10.dp))
                    Text(t.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (m.state != "pre") Text(t.score ?: "0", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
        if (Reminders.has(m)) Icon(Icons.Filled.Notifications, "Herinnering", Modifier.padding(start = 10.dp).size(18.dp), tint = MaterialTheme.colorScheme.primary)
    }
}

/** Een wedstrijd: groot de stand, een herinnering, en de zenders waarop hij is. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhoneMatch(m: Match) {
    val context = LocalContext.current
    val hits by produceState<List<SportHit>?>(null, m) { value = Sport.channelsFor(m, Library.catalog, Library.epg) }
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(m.league.name) },
            navigationIcon = { IconButton(onClick = { Nav.close() }) { Icon(Icons.Filled.ArrowBack, "Terug") } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
        )
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    BigTeam(m.home, Modifier.weight(1f))
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(if (m.state == "pre") clock(m.start) else "${m.home.score ?: 0} - ${m.away.score ?: 0}",
                            style = MaterialTheme.typography.headlineLarge)
                        when (m.state) {
                            "in" -> { Spacer(Modifier.height(4.dp)); LivePill(m.detail) }
                            "post" -> Text("Afgelopen", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    BigTeam(m.away, Modifier.weight(1f))
                }
                if (m.state == "pre" && m.start > System.currentTimeMillis()) {
                    val on = Reminders.has(m)
                    FilledTonalButton(
                        onClick = {
                            if (!on && Build.VERSION.SDK_INT >= 33) (context as? android.app.Activity)?.requestPermissions(
                                arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 7,
                            )
                            Reminders.toggle(m)
                        },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    ) {
                        Icon(Icons.Filled.Notifications, null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (on) "Herinnering staat aan" else "Herinner me 5 min van tevoren")
                    }
                }
                Text("Hier is hij te zien", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 20.dp, top = 24.dp, bottom = 6.dp))
            }
            val list = hits
            when {
                list == null -> item { Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
                list.isEmpty() -> item { EmptyState("Geen zender gevonden", "Geen kanaal gevonden dat deze wedstrijd uitzendt.") }
                else -> items(list, key = { it.channel.id }) { h ->
                    ListItem(
                        headlineContent = { Text(h.channel.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        supportingContent = { Text(h.reason, color = MaterialTheme.colorScheme.primary, maxLines = 2) },
                        leadingContent = { ChannelLogo(h.channel.logo, h.channel.name, Modifier.size(56.dp, 40.dp)) },
                        trailingContent = h.country?.let { c -> { AssistChip(onClick = {}, label = { Text(countryName(c)) }) } },
                        modifier = Modifier.clickable { Nav.close(); Nav.playChannels(list.map { it.channel }, h.channel) },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
                    )
                }
            }
        }
    }
}

@Composable
private fun BigTeam(t: Team, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(64.dp)) { if (t.logo != null) AsyncImage(t.logo, null, Modifier.fillMaxSize()) }
        Spacer(Modifier.height(8.dp))
        Text(t.name, style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center, maxLines = 2, fontSize = 15.sp)
    }
}
