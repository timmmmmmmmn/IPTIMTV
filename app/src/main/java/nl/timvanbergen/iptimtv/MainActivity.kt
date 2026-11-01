package nl.timvanbergen.iptimtv

import android.app.Application
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import nl.timvanbergen.iptimtv.data.Cloud
import nl.timvanbergen.iptimtv.data.History
import nl.timvanbergen.iptimtv.data.Library
import nl.timvanbergen.iptimtv.data.Recordings
import nl.timvanbergen.iptimtv.data.Reminders
import android.content.Intent
import nl.timvanbergen.iptimtv.data.Settings
import nl.timvanbergen.iptimtv.data.Sync
import nl.timvanbergen.iptimtv.data.CatalogShare
import nl.timvanbergen.iptimtv.data.Updater
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import nl.timvanbergen.iptimtv.ui.IptimApp
import nl.timvanbergen.iptimtv.ui.Nav

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Settings.init(this)
        History.init(this)
        Cloud.init(this)
        Sync.init(this)
        CatalogShare.init(this)
        Recordings.init(this)
        Reminders.init(this)
        Library.init(this)
        initCast()
    }

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    private fun initCast() = nl.timvanbergen.iptimtv.data.Cast.init(this)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (BuildConfig.MOBILE) {
            enableEdgeToEdge()
            WindowCompat.getInsetsController(window, window.decorView).show(WindowInsetsCompat.Type.systemBars())
        } else {
            WindowCompat.getInsetsController(window, window.decorView).apply {
                hide(WindowInsetsCompat.Type.systemBars())
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }
        setContent { IptimApp(onExit = { finish() }) }
        handle(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    /** Getikt op een wedstrijdmelding: naar Sport. */
    private fun handle(intent: Intent?) {
        if (intent?.getStringExtra("open") == "sport") {
            Nav.play = null
            Nav.tab = nl.timvanbergen.iptimtv.ui.Tab.SPORT
        }
    }

    /** Tijdens het kijken gaan alle toetsen rechtstreeks naar de speler. */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        Nav.playerKeys?.let { if (it(event)) return true }
        return super.dispatchKeyEvent(event)
    }

    override fun onResume() {
        super.onResume()
        // Terug van het scherm "onbekende apps toestaan": de update meteen afmaken.
        if (Updater.resumeAfterPermission && (android.os.Build.VERSION.SDK_INT < 26 || packageManager.canRequestPackageInstalls())) {
            Updater.resumeAfterPermission = false
            lifecycleScope.launch { Updater.install(this@MainActivity) }
        }
    }

    override fun onStop() {
        super.onStop()
        if (BuildConfig.MOBILE) {
            // Telefoon: waar je was blijft staan; alleen de video stopt (behalve bij "alleen geluid").
            if (!nl.timvanbergen.iptimtv.data.AudioOnly.on && !isChangingConfigurations) Nav.play = null
        } else {
            // Wie de app later weer opent, begint altijd op het startscherm.
            Nav.reset()
        }
    }
}
