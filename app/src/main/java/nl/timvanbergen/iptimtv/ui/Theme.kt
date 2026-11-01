package nl.timvanbergen.iptimtv.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import nl.timvanbergen.iptimtv.data.Settings
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Kleuren: donker met een vleugje groen/turquoise uit het logo. */
object C {
    val bg = Color(0xFF08110F)
    val panel = Color(0xFF0C1815)
    val surface = Color(0xFF12201C)
    val surfaceHi = Color(0xFF1B2F29)
    val accent = Color(0xFF1FB58F)
    val accentDeep = Color(0xFF0F8A4A)
    val text = Color(0xFFF1F6F4)
    val dim = Color(0xFF8DA19B)
    val live = Color(0xFFFF4D5E)
    val good = Color(0xFF3DDC97)
}

/** True in de telefoon-app: andere layouts, touch in plaats van afstandsbediening. */
val Mobile = nl.timvanbergen.iptimtv.BuildConfig.MOBILE

/** Zijmarge van schermen: ruim op de tv, smal op de telefoon. */
val Pad = if (Mobile) 16.dp else 48.dp

val Rounded = RoundedCornerShape(14.dp)

@Composable
fun T(
    text: String,
    modifier: Modifier = Modifier,
    size: TextUnit = 16.sp,
    color: Color = C.text,
    weight: FontWeight = FontWeight.Normal,
    maxLines: Int = 1,
    align: TextAlign = TextAlign.Start,
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = TextStyle(color = color, fontSize = size, fontWeight = weight, textAlign = align, lineHeight = size * 1.3f),
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

/**
 * De basis van alles wat je met de afstandsbediening kunt aanwijzen:
 * licht op en groeit een beetje als hij focus heeft. Lang indrukken = [onLongClick].
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Focusable(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = Rounded,
    color: Color = C.surface,
    focusedColor: Color = C.surfaceHi,
    scale: Float = 1.06f,
    border: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    content: @Composable BoxScope.(focused: Boolean) -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val s by animateFloatAsState(if (focused && Settings.animations.on && !Mobile) scale else 1f, label = "scale")
    val self = remember { FocusRequester() }
    val pressed = remember { mutableStateOf(false) }
    Box(
        modifier
            .focusRequester(self)
            .onFocusChanged { if (it.isFocused) Nav.lastFocused = self }
            .graphicsLayer { scaleX = s; scaleY = s }
            .clip(shape)
            .background(if (focused) focusedColor else color)
            .then(if (focused && border) Modifier.border(3.dp, Color.White, shape) else Modifier)
            .onPreviewKeyEvent {
                // Alleen reageren op het loslaten van OK als het indrukken ook hier begon. Anders zou het
                // loslaten na "lang indrukken" meteen het eerste item van het menu dat net openging uitvoeren.
                val ok = it.key == Key.DirectionCenter || it.key == Key.Enter || it.key == Key.NumPadEnter
                when {
                    ok && it.type == KeyEventType.KeyDown -> { if (it.nativeKeyEvent.repeatCount == 0) pressed.value = true; false }
                    ok && it.type == KeyEventType.KeyUp && !pressed.value -> true
                    ok && it.type == KeyEventType.KeyUp -> { pressed.value = false; false }
                    else -> false
                }
            }
            .onKeyEvent {
                // De "menu"-knop op afstandsbedieningen die er een hebben doet hetzelfde als lang drukken.
                if (onLongClick != null && it.key == Key.Menu && it.type == KeyEventType.KeyUp) {
                    onLongClick(); true
                } else false
            }
            .combinedClickable(
                interactionSource = source,
                indication = null,
                onLongClick = onLongClick,
                onClick = onClick,
            ),
        content = { content(focused) },
    )
}

@Composable
fun Button(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    primary: Boolean = false,
    selected: Boolean = false,
) {
    Focusable(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        shape = RoundedCornerShape(24.dp),
        color = when {
            primary -> C.accent
            selected -> C.surfaceHi
            else -> C.surface
        },
        focusedColor = Color.White,
        border = false,
        scale = 1.05f,
    ) { focused ->
        val fg = if (focused) Color.Black else C.text
        Row(
            Modifier.padding(horizontal = 22.dp).fillMaxHeight(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (icon != null) Icon(icon, fg, 22.dp)
            T(text, color = fg, weight = FontWeight.Medium, size = 17.sp)
        }
    }
}

@Composable
fun Icon(icon: ImageVector, tint: Color = C.text, size: Dp = 24.dp, modifier: Modifier = Modifier) {
    Image(icon, contentDescription = null, modifier = modifier.size(size), colorFilter = ColorFilter.tint(tint))
}

/** Kanaallogo op een donker vlak; zonder logo de eerste letters van de naam. */
@Composable
fun Logo(url: String?, name: String, modifier: Modifier = Modifier) {
    Box(modifier.clip(RoundedCornerShape(10.dp)).background(Color(0xFF0A1714)), contentAlignment = Alignment.Center) {
        T(initials(name), size = 20.sp, weight = FontWeight.Bold, color = C.dim)
        if (url != null && Settings.showLogos.on) AsyncImage(
            model = url,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.matchParentSize().padding(8.dp).background(Color(0xFF0A1714)),
        )
    }
}

@Composable
fun Poster(url: String?, name: String, modifier: Modifier = Modifier) {
    Box(modifier.background(C.surface), contentAlignment = Alignment.Center) {
        T(name, Modifier.padding(10.dp), size = 15.sp, color = C.dim, maxLines = 4, align = TextAlign.Center)
        if (url != null) AsyncImage(
            model = url,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize(),
        )
    }
}

fun initials(name: String): String =
    name.replace(Regex("""^\W*[A-Za-z]{2,3}\s*[|:]\s*"""), "").split(' ', '-', '|', ':').filter { it.isNotBlank() && it[0].isLetterOrDigit() }.take(2)
        .joinToString("") { it.take(1).uppercase() }

@Composable
fun ProgressBar(progress: Float, modifier: Modifier = Modifier, color: Color = C.accent) {
    Box(modifier.height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.15f))) {
        Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).height(4.dp).background(color))
    }
}

private val hhmm = SimpleDateFormat("HH:mm", Locale("nl", "NL"))
private val dayHhmm = SimpleDateFormat("EEE HH:mm", Locale("nl", "NL"))

fun clock(ms: Long): String = hhmm.format(Date(ms))

fun whenText(start: Long, stop: Long, now: Long = System.currentTimeMillis()): String = when {
    now in start until stop -> "Nu"
    Date(start).let { java.util.Calendar.getInstance().apply { time = it } }.get(java.util.Calendar.DAY_OF_YEAR) ==
        java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_YEAR) -> clock(start)
    else -> dayHhmm.format(Date(start))
}

fun duration(ms: Long): String {
    val s = ms / 1000
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
}

/** Een paar eigen iconen (Material-vormen) die niet in de basisset zitten. */
object Ic {
    private fun icon(name: String, path: String) = androidx.compose.ui.graphics.vector.ImageVector.Builder(
        name, 24.dp, 24.dp, 24f, 24f,
    ).addPath(androidx.compose.ui.graphics.vector.addPathNodes(path), fill = androidx.compose.ui.graphics.SolidColor(Color.Black)).build()

    val Tv = icon("tv", "M21,3H3c-1.1,0 -2,0.9 -2,2v12c0,1.1 0.9,2 2,2h5v2h8v-2h5c1.1,0 1.99,-0.9 1.99,-2L23,5c0,-1.1 -0.9,-2 -2,-2zM21,17H3V5h18v12z")
    val Movie = icon("movie", "M18,4l2,4h-3l-2,-4h-2l2,4h-3l-2,-4H8l2,4H7L5,4H4c-1.1,0 -1.99,0.9 -1.99,2L2,18c0,1.1 0.9,2 2,2h16c1.1,0 2,-0.9 2,-2V4h-4z")
    val Series = icon("series", "M4,6H2v14c0,1.1 0.9,2 2,2h14v-2H4V6zM20,2H8c-1.1,0 -2,0.9 -2,2v12c0,1.1 0.9,2 2,2h12c1.1,0 2,-0.9 2,-2V4c0,-1.1 -0.9,-2 -2,-2zM12,14.5v-9l6,4.5 -6,4.5z")
    val Ball = icon("ball", "M12,2C6.48,2 2,6.48 2,12s4.48,10 10,10 10,-4.48 10,-10S17.52,2 12,2zM13,5.3l1.35,-0.95c1.82,0.56 3.37,1.76 4.38,3.34l-0.39,1.34 -1.35,0.46L13,6.7V5.3zM9.65,4.35L11,5.3v1.4L7.01,9.49l-1.35,-0.46 -0.39,-1.34c1.01,-1.57 2.56,-2.77 4.38,-3.34zM7.08,17.11l-1.14,0.1C4.73,15.81 4,13.99 4,12c0,-0.12 0.01,-0.23 0.02,-0.35l1,-0.73 1.38,0.48 1.46,4.34 -0.78,1.37zM14.5,19.59c-0.79,0.26 -1.63,0.41 -2.5,0.41s-1.71,-0.15 -2.5,-0.41l-0.69,-1.49 0.64,-1.1h5.11l0.64,1.11 -0.7,1.48zM14.27,15L9.73,15l-1.35,-4.02L12,8.44l3.63,2.54L14.27,15zM18.06,17.21l-1.14,-0.1 -0.79,-1.37 1.46,-4.34 1.39,-0.47 1,0.73c0.01,0.11 0.02,0.22 0.02,0.34 0,1.99 -0.73,3.81 -1.94,5.21z")
    val Record = icon("record", "M12,4a8,8 0 1,0 0.01,0z")
    val Pause = icon("pause", "M6,19h4V5H6v14zM14,5v14h4V5h-4z")
    val Restart = icon("restart", "M13,3c-4.97,0 -9,4.03 -9,9H1l3.89,3.89 0.07,0.14L9,12H6c0,-3.87 3.13,-7 7,-7s7,3.13 7,7 -3.13,7 -7,7c-1.93,0 -3.68,-0.79 -4.94,-2.06l-1.42,1.42C8.27,19.99 10.51,21 13,21c4.97,0 9,-4.03 9,-9s-4.03,-9 -9,-9z")
    val Audio = icon("audio", "M3,9v6h4l5,5V4L7,9H3zM16.5,12c0,-1.77 -1.02,-3.29 -2.5,-4.03v8.05c1.48,-0.73 2.5,-2.25 2.5,-4.02zM14,3.23v2.06c2.89,0.86 5,3.54 5,6.71s-2.11,5.85 -5,6.71v2.06c4.01,-0.91 7,-4.49 7,-8.77s-2.99,-7.86 -7,-8.77z")
    val Subtitles = icon("subtitles", "M20,4H4c-1.1,0 -2,0.9 -2,2v12c0,1.1 0.9,2 2,2h16c1.1,0 2,-0.9 2,-2V6c0,-1.1 -0.9,-2 -2,-2zM4,12h4v2H4v-2zM14,18H4v-2h10v2zM20,18h-4v-2h4v2zM20,14H10v-2h10v2z")
    val Aspect = icon("aspect", "M19,12h-2v3h-3v2h5v-5zM7,9h3V7H5v5h2V9zM21,3H3c-1.1,0 -2,0.9 -2,2v14c0,1.1 0.9,2 2,2h18c1.1,0 2,-0.9 2,-2V5c0,-1.1 -0.9,-2 -2,-2zM21,19.01H3V4.99h18v14.02z")
    val SkipNext = icon("next", "M6,18l8.5,-6L6,6v12zM16,6v12h2V6h-2z")
    val SkipPrev = icon("prev", "M6,6h2v12H6zM9.5,12l8.5,6V6z")
    val Forward = icon("fwd", "M4,18l8.5,-6L4,6v12zM13,6v12l8.5,-6L13,6z")
    val Rewind = icon("rew", "M11,18V6l-8.5,6 8.5,6zM11.5,12l8.5,6V6l-8.5,6z")
    val Tune = icon("tune", "M3,17v2h6v-2H3zM3,5v2h10V5H3zM13,21v-2h8v-2h-8v-2h-2v6h2zM7,9v2H3v2h4v2h2V9H7zM21,13v-2H11v2h10zM15,9h2V7h4V5h-4V3h-2v6z")
    val Cloud = icon("cloud", "M19.35,10.04C18.67,6.59 15.64,4 12,4 9.11,4 6.6,5.64 5.35,8.04 2.34,8.36 0,10.91 0,14c0,3.31 2.69,6 6,6h13c2.76,0 5,-2.24 5,-5 0,-2.64 -2.05,-4.78 -4.65,-4.96z")
    val Download = icon("download", "M19,9h-4V3H9v6H5l7,7 7,-7zM5,18v2h14v-2H5z")
}
