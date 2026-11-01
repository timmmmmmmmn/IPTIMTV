package nl.timvanbergen.iptimtv.ui.phone

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import nl.timvanbergen.iptimtv.data.Channel
import nl.timvanbergen.iptimtv.data.Settings
import nl.timvanbergen.iptimtv.ui.C
import nl.timvanbergen.iptimtv.ui.initials
import nl.timvanbergen.iptimtv.ui.rememberPrograms

/** De kleuren van IPTIMTV als Material 3-thema: zo voelt de telefoon-app als een gewone Android-app. */
private val scheme = darkColorScheme(
    primary = C.accent,
    onPrimary = Color(0xFF00281D),
    primaryContainer = Color(0xFF0D4536),
    onPrimaryContainer = Color(0xFFB3F2DC),
    secondary = Color(0xFF8FD3BD),
    onSecondary = Color(0xFF00382A),
    secondaryContainer = Color(0xFF1A3A31),
    onSecondaryContainer = Color(0xFFC9EBDF),
    tertiary = Color(0xFFFFC94D),
    background = C.bg,
    onBackground = C.text,
    surface = C.bg,
    onSurface = C.text,
    surfaceVariant = C.surface,
    onSurfaceVariant = C.dim,
    surfaceContainerLowest = Color(0xFF050C0A),
    surfaceContainerLow = C.panel,
    surfaceContainer = Color(0xFF0F1C18),
    surfaceContainerHigh = C.surface,
    surfaceContainerHighest = C.surfaceHi,
    outline = Color(0xFF3B5049),
    outlineVariant = Color(0xFF213530),
    error = C.live,
)

@Composable
fun PhoneTheme(content: @Composable () -> Unit) {
    val base = Typography()
    MaterialTheme(
        colorScheme = scheme,
        typography = base.copy(
            headlineLarge = base.headlineLarge.copy(fontWeight = FontWeight.Black),
            headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.Bold),
            headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.Bold),
            titleLarge = base.titleLarge.copy(fontWeight = FontWeight.Bold),
            titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        ),
    ) {
        // Ook schermen buiten het hoofdscherm (pagina's, menu's) krijgen zo lichte tekst.
        CompositionLocalProvider(LocalContentColor provides C.text, content = content)
    }
}

@Composable
fun SectionHeader(title: String, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 22.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        if (action != null && onAction != null) TextButton(onClick = onAction) { Text(action) }
    }
}

/** Zenderlogo in een rond vlak; zonder logo de beginletters. */
@Composable
fun ChannelLogo(url: String?, name: String, modifier: Modifier = Modifier) {
    Box(modifier.clip(RoundedCornerShape(12.dp)).background(Color(0xFF0B1714)), contentAlignment = Alignment.Center) {
        Text(initials(name), color = C.dim, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        if (url != null && Settings.showLogos.on) {
            AsyncImage(url, null, contentScale = ContentScale.Fit, modifier = Modifier.matchParentSize().background(Color(0xFF0B1714)).padding(6.dp))
        }
    }
}

@Composable
fun PosterImage(url: String?, name: String, modifier: Modifier = Modifier) {
    Box(modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
        Text(name, color = C.dim, style = MaterialTheme.typography.labelMedium, maxLines = 4, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(8.dp))
        if (url != null) AsyncImage(url, null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
    }
}

@Composable
fun Progress(fraction: Float, modifier: Modifier = Modifier) {
    LinearProgressIndicator(
        progress = { fraction.coerceIn(0f, 1f) },
        modifier = modifier.height(3.dp).clip(CircleShape),
        trackColor = Color.White.copy(alpha = 0.12f),
        drawStopIndicator = {},
        gapSize = 0.dp,
    )
}

/** Een kanaal in een lijst: logo, naam, wat er nu op is met voortgang, en wat straks. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChannelRow(ch: Channel, number: Int?, trailing: @Composable (() -> Unit)? = null, onLongClick: () -> Unit, onClick: () -> Unit) {
    val programs = if (Settings.showEpgInLists.on) rememberPrograms(ch.epgId, 2) else emptyList()
    val now = programs.firstOrNull()?.takeIf { it.isLive() }
    val next = programs.firstOrNull { it != now }
    Row(
        Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLongClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ChannelLogo(ch.logo, ch.name, Modifier.size(64.dp, 44.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (number != null && Settings.showNumbers.on) {
                    Text("$number", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.width(8.dp))
                }
                Text(ch.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (now != null) {
                Text(now.title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(5.dp))
                Progress(now.progress(), Modifier.fillMaxWidth(0.7f))
            } else if (next != null) {
                Text("${nl.timvanbergen.iptimtv.ui.clock(next.start)} ${next.title}", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        trailing?.invoke()
    }
}

/** Kanaal als kaart (voor rijen op het startscherm). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChannelCard(ch: Channel, onLongClick: () -> Unit, onClick: () -> Unit) {
    val now = (if (Settings.showEpgInLists.on) rememberPrograms(ch.epgId, 1) else emptyList()).firstOrNull()?.takeIf { it.isLive() }
    Card(
        Modifier.width(156.dp).clip(CardDefaults.shape).combinedClickable(onClick = onClick, onLongClick = onLongClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        ChannelLogo(ch.logo, ch.name, Modifier.fillMaxWidth().height(84.dp).padding(8.dp))
        Column(Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp)) {
            Text(ch.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(now?.title ?: " ", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(6.dp))
            Progress(now?.progress() ?: 0f, Modifier.fillMaxWidth())
        }
    }
}

/** Een rood "LIVE"-labeltje. */
@Composable
fun LivePill(text: String = "LIVE") {
    Text(
        text, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Black,
        modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(C.live).padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

@Composable
fun EmptyState(title: String, text: String? = null, action: String? = null, onAction: (() -> Unit)? = null) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        if (text != null) {
            Spacer(Modifier.height(6.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
        if (action != null && onAction != null) {
            Spacer(Modifier.height(16.dp))
            FilledTonalButton(onClick = onAction) { Text(action) }
        }
    }
}
