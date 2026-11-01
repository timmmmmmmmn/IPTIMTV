package nl.timvanbergen.iptimtv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import nl.timvanbergen.iptimtv.data.Library

/** De tabs in de zwevende balk onderin; instellingen zitten achter het tandwiel bovenin. */
private val mobileTabs = listOf(Tab.HOME, Tab.LIVE, Tab.SPORT, Tab.MOVIES, Tab.SERIES, Tab.SEARCH)

@Composable
fun MobileMain(onExit: () -> Unit) {
    AutoPlayLast()
    BackHandler(enabled = Nav.overlays.isEmpty() && Nav.play == null) {
        if (Nav.tab != Tab.HOME) Nav.tab = Tab.HOME else onExit()
    }
    Box(Modifier.fillMaxSize().background(C.bg)) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().padding(start = 12.dp, end = 8.dp, top = 8.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Brand(34)
                Spacer(Modifier.weight(1f))
                Library.loading?.let { T(it, size = 12.sp, color = C.dim, modifier = Modifier.padding(end = 8.dp)) }
                Box(
                    Modifier.size(44.dp).clip(CircleShape)
                        .background(if (Nav.tab == Tab.SETTINGS) C.accent else C.surface)
                        .clickable { Nav.tab = if (Nav.tab == Tab.SETTINGS) Tab.HOME else Tab.SETTINGS },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Filled.Settings, C.text, 22.dp) }
            }
            Box(Modifier.weight(1f).fillMaxWidth()) { TabContent(Nav.tab) }
        }
        FloatingNavBar(Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun FloatingNavBar(modifier: Modifier) {
    Row(
        modifier
            .navigationBarsPadding()
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .shadow(18.dp, RoundedCornerShape(30.dp))
            .clip(RoundedCornerShape(30.dp))
            .background(Brush.horizontalGradient(listOf(C.surface.copy(alpha = 0.97f), C.panel.copy(alpha = 0.97f))))
            .border(1.dp, C.accent.copy(alpha = 0.18f), RoundedCornerShape(30.dp))
            .padding(horizontal = 6.dp, vertical = 6.dp)
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        mobileTabs.forEach { t -> NavItem(t, Modifier.weight(1f)) }
    }
}

@Composable
private fun NavItem(t: Tab, modifier: Modifier) {
    val selected = Nav.tab == t
    val bg by animateColorAsState(if (selected) C.accent.copy(alpha = 0.18f) else Color.Transparent, label = "nav")
    Column(
        modifier
            .clip(RoundedCornerShape(24.dp))
            .background(bg)
            .clickable(remember { MutableInteractionSource() }, indication = null) { Nav.tab = t }
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(t.icon!!, if (selected) C.accent else C.dim, 22.dp)
        Spacer(Modifier.height(2.dp))
        T(if (t == Tab.LIVE) "Live" else t.label, size = 11.sp, color = if (selected) C.text else C.dim,
            weight = if (selected) FontWeight.Bold else FontWeight.Medium)
    }
}
