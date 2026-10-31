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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object C {
    val bg = Color(0xFF0B0E14)
    val panel = Color(0xFF11151E)
    val surface = Color(0xFF171C27)
    val surfaceHi = Color(0xFF232A38)
    val accent = Color(0xFF3D8BFF)
    val text = Color(0xFFF2F4F8)
    val dim = Color(0xFF8E97A8)
    val live = Color(0xFFFF4D5E)
    val good = Color(0xFF3DDC97)
}

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
    val s by animateFloatAsState(if (focused) scale else 1f, label = "scale")
    val self = remember { FocusRequester() }
    Box(
        modifier
            .focusRequester(self)
            .onFocusChanged { if (it.isFocused) Nav.lastFocused = self }
            .graphicsLayer { scaleX = s; scaleY = s }
            .clip(shape)
            .background(if (focused) focusedColor else color)
            .then(if (focused && border) Modifier.border(3.dp, Color.White, shape) else Modifier)
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
    Box(modifier.clip(RoundedCornerShape(10.dp)).background(Color(0xFF0E121A)), contentAlignment = Alignment.Center) {
        T(initials(name), size = 20.sp, weight = FontWeight.Bold, color = C.dim)
        if (url != null) AsyncImage(
            model = url,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.matchParentSize().padding(8.dp).background(Color(0xFF0E121A)),
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
    name.split(' ', '-', '|', ':').filter { it.isNotBlank() && it[0].isLetterOrDigit() }.take(2)
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
