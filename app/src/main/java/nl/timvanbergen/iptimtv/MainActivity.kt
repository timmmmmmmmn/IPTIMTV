package nl.timvanbergen.iptimtv

import android.app.Application
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import nl.timvanbergen.iptimtv.data.Library
import nl.timvanbergen.iptimtv.ui.IptimApp
import nl.timvanbergen.iptimtv.ui.Nav

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Library.init(this)
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        setContent { IptimApp(onExit = { finish() }) }
    }

    /** Tijdens het kijken gaan alle toetsen rechtstreeks naar de speler. */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        Nav.playerKeys?.let { if (it(event)) return true }
        return super.dispatchKeyEvent(event)
    }

    override fun onStop() {
        super.onStop()
        // Wie de app later weer opent, begint altijd op het startscherm.
        Nav.reset()
    }
}
