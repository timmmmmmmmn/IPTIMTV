package nl.timvanbergen.iptimtv.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.cast.CastPlayer
import androidx.media3.cast.SessionAvailabilityListener
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.mediarouter.media.MediaRouteSelector
import androidx.mediarouter.media.MediaRouter
import com.google.android.gms.cast.CastMediaControlIntent
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastOptions as GmsCastOptions
import com.google.android.gms.cast.framework.OptionsProvider
import com.google.android.gms.cast.framework.SessionProvider
import nl.timvanbergen.iptimtv.BuildConfig

/** Casten gebruikt de standaard ontvanger van Google; die speelt HLS en mp4 af. */
class CastOptions : OptionsProvider {
    override fun getCastOptions(context: Context): GmsCastOptions =
        GmsCastOptions.Builder().setReceiverApplicationId(CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID).build()

    override fun getAdditionalSessionProviders(context: Context): List<SessionProvider>? = null
}

/** Wat er naar de Chromecast gaat. */
class CastItem(val url: String, val title: String, val subtitle: String?, val image: String?, val live: Boolean, val startAt: Long = 0)

/**
 * Casten naar een Chromecast of Google TV in huis (alleen in de telefoon-app).
 * Zoeken naar apparaten, verbinden, en daarna stuurt de telefoon alleen nog het adres van de stream door.
 */
@UnstableApi
object Cast {
    private var context: CastContext? = null
    private lateinit var router: MediaRouter
    private var castPlayer: CastPlayer? = null
    private var pending: CastItem? = null

    /** Gevonden apparaten (tijdens zoeken). */
    val devices = mutableStateListOf<MediaRouter.RouteInfo>()
    /** Naam van het apparaat waarmee we verbonden zijn, of null. */
    var device by mutableStateOf<String?>(null); private set
    var current by mutableStateOf<CastItem?>(null); private set
    var playing by mutableStateOf(false); private set
    var connecting by mutableStateOf(false); private set

    val available get() = context != null

    private val selector by lazy {
        MediaRouteSelector.Builder()
            .addControlCategory(CastMediaControlIntent.categoryForCast(CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID))
            .build()
    }

    private val callback = object : MediaRouter.Callback() {
        override fun onRouteAdded(router: MediaRouter, route: MediaRouter.RouteInfo) = refresh()
        override fun onRouteRemoved(router: MediaRouter, route: MediaRouter.RouteInfo) = refresh()
        override fun onRouteChanged(router: MediaRouter, route: MediaRouter.RouteInfo) = refresh()
    }

    fun init(app: Context) {
        if (!BuildConfig.MOBILE || context != null) return
        // Zonder Google Play-diensten (of op een emulator zonder) gewoon geen casten.
        val ctx = runCatching { CastContext.getSharedInstance(app) }.getOrNull() ?: return
        context = ctx
        router = MediaRouter.getInstance(app)
        castPlayer = CastPlayer(ctx).apply {
            setSessionAvailabilityListener(object : SessionAvailabilityListener {
                override fun onCastSessionAvailable() {
                    connecting = false
                    device = ctx.sessionManager.currentCastSession?.castDevice?.friendlyName ?: "Chromecast"
                    pending?.let { load(it) }
                    pending = null
                }

                override fun onCastSessionUnavailable() {
                    connecting = false
                    device = null
                    current = null
                    playing = false
                }
            })
            addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
            })
        }
        // Was er nog een cast-sessie van eerder? Dan die gebruiken.
        if (castPlayer?.isCastSessionAvailable == true) {
            device = ctx.sessionManager.currentCastSession?.castDevice?.friendlyName
        }
    }

    fun startSearch() {
        if (context == null) return
        router.addCallback(selector, callback, MediaRouter.CALLBACK_FLAG_PERFORM_ACTIVE_SCAN)
        refresh()
    }

    fun stopSearch() {
        if (context == null) return
        router.removeCallback(callback)
    }

    private fun refresh() {
        devices.clear()
        devices.addAll(router.routes.filter { !it.isDefault && it.isEnabled && it.matchesSelector(selector) })
    }

    /** Wat er op de Chromecast staat (bijv. de kanalen om doorheen te zappen). */
    var queue: List<CastItem> = emptyList(); private set
    private var index = 0

    /** Verbinden met een apparaat en daar meteen [item] afspelen (leeg adres = alleen verbinden). */
    fun connect(route: MediaRouter.RouteInfo, item: CastItem, list: List<CastItem> = listOf(item), at: Int = 0) {
        if (item.url.isNotEmpty()) { pending = item; queue = list; index = at }
        connecting = true
        router.selectRoute(route)
    }

    /** Iets anders afspelen op het verbonden apparaat (bijv. ander kanaal). */
    fun load(item: CastItem) {
        val p = castPlayer ?: return
        current = item
        // Live: de Chromecast kan geen losse .ts-stream, wel HLS.
        val url = if (item.live) item.url.replace(Regex("""(/live/.+)\.ts$"""), "$1.m3u8") else item.url
        val mime = when {
            url.contains(".m3u8") -> MimeTypes.APPLICATION_M3U8
            url.endsWith(".mkv") -> MimeTypes.VIDEO_MATROSKA
            url.endsWith(".ts") -> MimeTypes.VIDEO_MP2T
            url.endsWith(".webm") -> MimeTypes.VIDEO_WEBM
            else -> MimeTypes.VIDEO_MP4
        }
        val meta = MediaMetadata.Builder().setTitle(item.title).setSubtitle(item.subtitle)
            .apply { item.image?.let { setArtworkUri(android.net.Uri.parse(it)) } }.build()
        p.setMediaItem(MediaItem.Builder().setUri(url).setMimeType(mime).setMediaMetadata(meta).build(), item.startAt)
        p.prepare()
        p.play()
    }

    /** Een lijst afspelen op het verbonden apparaat. */
    fun play(list: List<CastItem>, at: Int) {
        queue = list; index = at
        list.getOrNull(at)?.let { load(it) }
    }

    /** Volgend/vorig kanaal op de Chromecast. */
    fun skip(delta: Int) {
        if (queue.isEmpty()) return
        index = (index + delta + queue.size) % queue.size
        load(queue[index])
    }

    fun togglePause() {
        val p = castPlayer ?: return
        if (p.isPlaying) p.pause() else p.play()
    }

    /** Hoe ver de film op de Chromecast is (om daarna op de telefoon verder te kijken). */
    val position get() = castPlayer?.currentPosition ?: 0L

    fun disconnect() {
        context?.sessionManager?.endCurrentSession(true)
        current = null
        device = null
        playing = false
    }
}
