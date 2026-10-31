package nl.timvanbergen.iptimtv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import nl.timvanbergen.iptimtv.data.Channel
import nl.timvanbergen.iptimtv.data.Library

/** Een kader midden in beeld. */
@Composable
fun DialogFrame(title: String, width: Int = 620, content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            Modifier
                .width(width.dp)
                .background(C.panel, RoundedCornerShape(22.dp))
                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(22.dp))
                .padding(36.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            T(title, size = 26.sp, weight = FontWeight.Bold, maxLines = 2)
            Spacer(Modifier.height(4.dp))
            content()
        }
    }
}

/** Een regel in een menu: groot, makkelijk te raken. */
@Composable
fun MenuItem(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector? = null, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Focusable(onClick = onClick, modifier = modifier.fillMaxWidth().height(56.dp), scale = 1.02f, focusedColor = Color.White, border = false) { focused ->
        Row(
            Modifier.fillMaxSize().padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            val fg = if (focused) Color.Black else C.text
            if (icon != null) Icon(icon, fg, 24.dp)
            T(text, color = fg, size = 18.sp, weight = FontWeight.Medium)
        }
    }
}

/**
 * Tekstveld voor de afstandsbediening: het is een gewone knop tot je op OK drukt.
 * Pas dan verschijnt het toetsenbord, zodat het niet de pijltjes vangt bij het langslopen.
 */
@Composable
fun TvTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    password: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Done,
    onDone: () -> Unit = {},
) {
    var editing by remember { mutableStateOf(false) }
    var hadFocus by remember { mutableStateOf(false) }
    var returnFocus by remember { mutableStateOf(false) }
    var edit by remember { mutableStateOf(TextFieldValue(value)) }
    val field = remember { FocusRequester() }
    val button = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val shape = RoundedCornerShape(12.dp)

    fun finish(done: Boolean) {
        keyboard?.hide()
        returnFocus = true
        editing = false
        if (done) onDone()
    }

    Box(modifier.fillMaxWidth().height(56.dp)) {
        if (editing) {
            LaunchedEffect(Unit) {
                edit = TextFieldValue(value, TextRange(value.length)) // cursor achteraan
                runCatching { field.requestFocus() }
                keyboard?.show()
            }
            BasicTextField(
                value = edit,
                onValueChange = { edit = it; onValueChange(it.text) },
                singleLine = true,
                textStyle = TextStyle(color = C.text, fontSize = 19.sp),
                cursorBrush = SolidColor(C.accent),
                visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (password) KeyboardType.Password else keyboardType,
                    imeAction = imeAction,
                    autoCorrectEnabled = false,
                ),
                keyboardActions = KeyboardActions(onAny = { finish(true) }),
                modifier = Modifier
                    .fillMaxSize()
                    .focusRequester(field)
                    .onFocusChanged {
                        if (it.isFocused) hadFocus = true
                        else if (hadFocus) { hadFocus = false; editing = false }
                    },
                decorationBox = { inner ->
                    Box(
                        Modifier.fillMaxSize().background(C.surfaceHi, shape).border(3.dp, C.accent, shape).padding(horizontal = 18.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (value.isEmpty()) T(placeholder, color = C.dim, size = 19.sp)
                        inner()
                    }
                },
            )
        } else {
            LaunchedEffect(Unit) {
                if (returnFocus) {
                    returnFocus = false
                    runCatching { button.requestFocus() }
                }
            }
            Focusable(
                onClick = { editing = true },
                modifier = Modifier.fillMaxSize().focusRequester(button),
                shape = shape,
                scale = 1.02f,
            ) {
                Box(Modifier.fillMaxSize().padding(horizontal = 18.dp), contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) T(placeholder, color = C.dim, size = 19.sp)
                    else T(if (password) "•".repeat(value.length) else value, size = 19.sp)
                }
            }
        }
    }
}

@Composable
fun TextInputDialog(o: Overlay.TextInput) {
    var text by remember { mutableStateOf(o.initial) }
    val focus = rememberInitialFocus()
    val done = {
        Nav.close()
        o.onDone(text)
    }
    DialogFrame(o.title) {
        TvTextField(text, { text = it }, "Bijvoorbeeld: Ziggo Sport", Modifier.focusRequester(focus), imeAction = ImeAction.Done, onDone = done)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button("Opslaan", onClick = done, primary = true, icon = Icons.Filled.Check)
            Button("Annuleren", onClick = { Nav.close() })
        }
    }
}

/** Lang drukken op een kanaal: naar Mijn kanalen of een groep. */
@Composable
fun ChannelMenu(ch: Channel) {
    val focus = rememberInitialFocus()
    val fav = ch.id in Library.favorites
    DialogFrame(ch.name) {
        MenuItem(if (fav) "Verwijderen uit Mijn kanalen" else "Toevoegen aan Mijn kanalen", Icons.Filled.Star, Modifier.focusRequester(focus)) {
            Library.toggleFavorite(ch.id); Nav.close()
        }
        MenuItem("Programmagids", Icons.Filled.DateRange) { Nav.close(); Nav.open(Overlay.Guide(ch)) }
        Library.groups.forEach { g ->
            val inGroup = ch.id in g.channelIds
            MenuItem(if (inGroup) "Verwijderen uit ${g.name}" else "Toevoegen aan ${g.name}", if (inGroup) Icons.Filled.Delete else Icons.Filled.Add) {
                Library.updateGroup(g.copy(channelIds = if (inGroup) g.channelIds - ch.id else g.channelIds + ch.id))
                Nav.close()
            }
        }
        MenuItem("Nieuwe groep met dit kanaal", Icons.Filled.Add) {
            Nav.close(); createGroup(withChannel = ch.id)
        }
    }
}

@Composable
fun GroupEditor(groupId: String) {
    val g = Library.groups.firstOrNull { it.id == groupId } ?: run { Nav.close(); return }
    val focus = rememberInitialFocus()
    DialogFrame("Groep: ${g.name}") {
        T("${g.channelIds.size} kanalen", color = C.dim)
        MenuItem("Kanalen kiezen", Icons.Filled.Check, Modifier.focusRequester(focus)) {
            Nav.open(Overlay.Picker(g.name, g.channelIds) { ids ->
                Library.groups.firstOrNull { it.id == groupId }?.let { Library.updateGroup(it.copy(channelIds = ids)) }
            })
        }
        MenuItem("Naam wijzigen", Icons.Filled.Edit) {
            Nav.open(Overlay.TextInput("Nieuwe naam", g.name) { name ->
                Library.groups.firstOrNull { it.id == groupId }?.let { Library.updateGroup(it.copy(name = name.trim().ifBlank { it.name })) }
            })
        }
        MenuItem("Groep omhoog", Icons.Filled.KeyboardArrowUp) { Library.moveGroup(groupId, -1) }
        MenuItem("Groep omlaag", Icons.Filled.KeyboardArrowDown) { Library.moveGroup(groupId, 1) }
        MenuItem("Groep verwijderen", Icons.Filled.Delete) { Library.deleteGroup(groupId); Nav.close() }
    }
}

/**
 * Kanalen in bulk kiezen: links de groepen van de provider, rechts de kanalen.
 * "Alles in deze groep" zet in één keer een hele providergroep aan.
 */
@Composable
fun ChannelPicker(o: Overlay.Picker) {
    val selected = remember { mutableStateListOf<String>().apply { addAll(o.initial) } }
    val selectedSet by remember { androidx.compose.runtime.derivedStateOf { selected.toHashSet() } }
    val categories = remember { Library.liveCategories(includeHidden = true) }
    var category by remember { mutableStateOf(categories.firstOrNull()?.id) }
    var query by remember { mutableStateOf("") }
    val focus = rememberInitialFocus()

    val channels = remember(category, query) {
        if (query.isNotBlank()) {
            Library.catalog.channels.filter { it.name.contains(query.trim(), ignoreCase = true) }.take(300)
        } else Library.catalog.channelsByCategory[category].orEmpty()
    }
    val allOn = channels.isNotEmpty() && channels.all { it.id in selectedSet }

    Column(Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 32.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                T("Kanalen kiezen voor ${o.title}", size = 28.sp, weight = FontWeight.Bold)
                T("${selected.size} geselecteerd · OK zet een kanaal aan of uit", color = C.dim)
            }
            Button("Opslaan", primary = true, icon = Icons.Filled.Check, onClick = {
                Nav.close(); o.onDone(selected.toList())
            })
            Spacer(Modifier.width(12.dp))
            Button("Annuleren", onClick = { Nav.close() })
        }
        Spacer(Modifier.height(20.dp))
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            LazyColumn(
                Modifier.width(320.dp).fillMaxHeight().focusRestorer(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(vertical = 6.dp, horizontal = 6.dp),
            ) {
                item {
                    TvTextField(query, { query = it }, "Zoek een kanaal…", imeAction = ImeAction.Search)
                    Spacer(Modifier.height(8.dp))
                }
                items(categories, key = { it.id }) { c ->
                    val count = Library.catalog.channelsByCategory[c.id].orEmpty().count { it.id in selectedSet }
                    CategoryItem(
                        c.name,
                        selected = query.isBlank() && c.id == category,
                        badge = if (count > 0) "$count" else null,
                        modifier = if (c.id == categories.first().id) Modifier.focusRequester(focus) else Modifier,
                        onFocus = { query = ""; category = c.id },
                    )
                }
            }
            LazyColumn(
                Modifier.weight(1f).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(vertical = 6.dp, horizontal = 6.dp),
            ) {
                if (channels.isNotEmpty()) item(key = "all") {
                    MenuItem(if (allOn) "Alles in deze lijst uitzetten" else "Alles in deze lijst aanzetten (${channels.size})", Icons.Filled.Check) {
                        if (allOn) selected.removeAll(channels.map { it.id }.toSet())
                        else channels.forEach { if (it.id !in selectedSet) selected += it.id }
                    }
                }
                items(channels, key = { it.id }) { ch ->
                    val on = ch.id in selectedSet
                    Focusable(
                        onClick = { if (on) selected.remove(ch.id) else selected += ch.id },
                        modifier = Modifier.fillMaxWidth().height(64.dp),
                        scale = 1.02f,
                    ) {
                        Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(on)
                            Spacer(Modifier.width(16.dp))
                            Logo(ch.logo, ch.name, Modifier.size(72.dp, 44.dp))
                            Spacer(Modifier.width(16.dp))
                            T(ch.name, size = 18.sp, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun Checkbox(on: Boolean) {
    Box(
        Modifier
            .size(28.dp)
            .background(if (on) C.accent else Color.Transparent, RoundedCornerShape(7.dp))
            .border(2.dp, if (on) C.accent else C.dim, RoundedCornerShape(7.dp)),
        contentAlignment = Alignment.Center,
    ) { if (on) Icon(Icons.Filled.Check, Color.White, 20.dp) }
}

/** Een groep in de linkerkolom; wordt gekozen zodra je erop staat. */
@Composable
fun CategoryItem(
    name: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    badge: String? = null,
    onFocus: () -> Unit,
    onClick: () -> Unit = onFocus,
) {
    Focusable(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(52.dp).onFocusChanged { if (it.isFocused) onFocus() },
        color = if (selected) C.surfaceHi else Color.Transparent,
        focusedColor = Color.White,
        border = false,
        scale = 1.03f,
    ) { focused ->
        Row(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            if (selected && !focused) Box(Modifier.size(4.dp, 22.dp).background(C.accent, RoundedCornerShape(2.dp)))
            if (selected && !focused) Spacer(Modifier.width(10.dp))
            T(name, color = if (focused) Color.Black else if (selected) C.text else C.dim, size = 17.sp, weight = if (selected) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.weight(1f))
            if (badge != null) T(badge, color = if (focused) Color.Black else C.accent, weight = FontWeight.Bold)
        }
    }
}
