package nl.timvanbergen.iptimtv.ui

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.view.KeyEvent
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C as MC
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioCapabilities
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import androidx.media3.ui.SubtitleView
import kotlinx.coroutines.delay
import nl.timvanbergen.iptimtv.data.Cloud
import nl.timvanbergen.iptimtv.data.History
import nl.timvanbergen.iptimtv.data.Library
import nl.timvanbergen.iptimtv.data.Net
import nl.timvanbergen.iptimtv.data.Recordings
import nl.timvanbergen.iptimtv.data.Settings
import nl.timvanbergen.iptimtv.data.SourceType
import nl.timvanbergen.iptimtv.data.WatchItem
import nl.timvanbergen.iptimtv.data.Xtream

/** De speler, opgebouwd uit de instellingen van Geavanceerd → Buffer en decoder / Geluid. */
@OptIn(UnstableApi::class)
private fun buildPlayer(context: Context): ExoPlayer {
    val http = OkHttpDataSource.Factory(Net.client)
    // Standaard de gewone TS-verwerking; de "compatibele" modus alleen voor rare streams zonder sleutelframes.
    val extractors = DefaultExtractorsFactory().apply {
        if (Settings.tsCompat.on) setTsExtractorFlags(
            DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES or DefaultTsPayloadReaderFactory.FLAG_DETECT_ACCESS_UNITS,
        )
    }
    val start = Settings.startBuffer.int
    val rebuffer = Settings.rebuffer.int
    val max = Settings.maxBuffer.int
    val min = maxOf(minOf(15_000, max), start, rebuffer)
    val load = DefaultLoadControl.Builder().setBufferDurationsMs(min, maxOf(max, min), start, rebuffer).build()

    val renderers = object : DefaultRenderersFactory(context) {
        override fun buildAudioSink(context: Context, enableFloatOutput: Boolean, enableAudioTrackPlaybackParams: Boolean): AudioSink? =
            if (Settings.softwareAudio.on) {
                // Altijd zelf omzetten naar stereo PCM, geen Dolby-doorgifte naar de soundbar.
                @Suppress("DEPRECATION")
                DefaultAudioSink.Builder(context).setAudioCapabilities(AudioCapabilities.DEFAULT_AUDIO_CAPABILITIES).build()
            } else super.buildAudioSink(context, enableFloatOutput, enableAudioTrackPlaybackParams)
    }.setEnableDecoderFallback(Settings.decoderFallback.on).apply {
        // Alleen in de emulator: de nagebootste hardware-decoder werkt daar niet, de software-decoder wel.
        if (android.os.Build.FINGERPRINT.contains("generic") || android.os.Build.HARDWARE.contains("ranchu")) {
            setMediaCodecSelector { mime, secure, tunneling ->
                androidx.media3.exoplayer.mediacodec.MediaCodecUtil.getDecoderInfos(mime, secure, tunneling)
                    .sortedBy { if (it.name.startsWith("c2.android")) 0 else 1 }
            }
        }
    }

    val selector = DefaultTrackSelector(context).apply {
        setParameters(
            buildUponParameters()
                .setTunnelingEnabled(Settings.tunneling.on)
                .setPreferredAudioLanguage(Settings.audioLang.value.ifBlank { null })
                .setPreferredTextLanguage(Settings.subLang.value.ifBlank { null })
                .setTrackTypeDisabled(MC.TRACK_TYPE_TEXT, Settings.subLang.value.isBlank())
                .setSelectUndeterminedTextLanguage(false),
        )
    }
    return ExoPlayer.Builder(context, renderers)
        .setMediaSourceFactory(DefaultMediaSourceFactory(DefaultDataSource.Factory(context, http), extractors))
        .setLoadControl(load)
        .setTrackSelector(selector)
        .build()
}

private val PARSE_ERRORS = setOf(
    PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
    PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
    PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
)

private fun mediaItem(url: String): MediaItem {
    val b = MediaItem.Builder().setUri(url)
    if (url.contains(".m3u8", ignoreCase = true)) b.setMimeType(MimeTypes.APPLICATION_M3U8)
    return b.build()
}

private val resizeModes = listOf("fit" to "Passend", "fill" to "Uitrekken", "zoom" to "Inzoomen")

@OptIn(UnstableApi::class)
private fun resizeMode(v: String) = when (v) {
    "fill" -> AspectRatioFrameLayout.RESIZE_MODE_FILL
    "zoom" -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
    else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
}

/** Een regel in het pauzemenu. */
private class MenuEntry(val label: String, val icon: ImageVector, val value: String? = null, val action: () -> Unit)

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(pb: Playback) {
    val context = LocalContext.current
    val player = remember { buildPlayer(context) }
    val items = pb.items
    var index by remember { mutableIntStateOf(pb.index) }
    val item = items[index]
    val channel = item.channelId?.let { Library.catalog.channelById[it] }

    var buffering by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var playing by remember { mutableStateOf(true) }
    var position by remember { mutableLongStateOf(0L) }
    var length by remember { mutableLongStateOf(0L) }
    var retries by remember { mutableIntStateOf(0) }
    var triedHls by remember { mutableStateOf(false) }
    var resize by remember { mutableStateOf(Settings.resize.value) }
    var tracksVersion by remember { mutableIntStateOf(0) }

    var overlayTick by remember { mutableIntStateOf(0) }
    var overlay by remember { mutableStateOf(true) }
    var panel by remember { mutableStateOf(false) }
    var panelIndex by remember { mutableIntStateOf(index) }
    var panelTick by remember { mutableIntStateOf(0) }
    var menu by remember { mutableStateOf(false) }
    var menuPage by remember { mutableStateOf("main") }
    var menuIndex by remember { mutableIntStateOf(0) }
    var menuTick by remember { mutableIntStateOf(0) }
    var toast by remember { mutableStateOf<String?>(null) }
    var okLong by remember { mutableStateOf(false) }
    // Telefoon: bediening zichtbaar, en welke keuzelijst (geluid/ondertitels/opnemen) open is.
    var controls by remember { mutableStateOf(true) }
    var controlsTick by remember { mutableIntStateOf(0) }
    var sheet by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(controls, controlsTick, playing, sheet) {
        if (controls && playing && sheet == null) { delay(4000); controls = false }
    }

    // Op de telefoon: liggend kijken.
    if (Mobile) DisposableEffect(Unit) {
        val activity = context as? Activity
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        val bars = activity?.window?.let { androidx.core.view.WindowCompat.getInsetsController(it, it.decorView) }
        bars?.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        bars?.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            bars?.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        }
    }

    // ---------- kijkgeschiedenis en kijktijd ----------
    var watched by remember { mutableLongStateOf(0L) }
    fun flush(finished: Boolean = false) {
        val it = items.getOrNull(index) ?: return
        val key = it.key ?: return
        val dur = player.duration.takeIf { d -> d > 0 } ?: 0L
        val w = WatchItem(
            key = key, kind = it.kind, title = it.title, subtitle = it.subtitle, image = it.image ?: it.logo, parent = it.parent,
            position = if (finished) dur else if (it.kind == "live") 0 else player.currentPosition, duration = dur,
        )
        if (it.kind != "live") History.update(w)
        if (watched > 0 || it.kind != "live") Cloud.progress(w, watched)
        watched = 0
    }
    LaunchedEffect(Unit) {
        var n = 0
        while (true) {
            delay(1000)
            if (player.isPlaying) watched++
            if (++n % 30 == 0) flush()
        }
    }

    fun showOverlay() { overlayTick++ }
    fun say(text: String) { toast = text }
    fun go(to: Int) {
        if (items.isEmpty()) return
        flush()
        val next = if (Settings.zapWrap.on) ((to % items.size) + items.size) % items.size else to.coerceIn(0, items.lastIndex)
        if (next == index) return
        index = next
        items[index].channelId?.let { Library.setLastChannel(it) }
    }
    fun close() {
        flush()
        Nav.play = null
    }
    fun seekBy(ms: Long) {
        val target = (player.currentPosition + ms).coerceIn(0, player.duration.takeIf { it > 0 } ?: Long.MAX_VALUE)
        player.seekTo(target)
        position = target
        showOverlay()
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                buffering = state == Player.STATE_BUFFERING
                if (state == Player.STATE_READY) {
                    error = null; retries = 0
                }
                if (state == Player.STATE_ENDED) {
                    if (!pb.live) {
                        flush(finished = true)
                        if (index < items.lastIndex && Settings.autoNext.on) index++ else Nav.play = null
                    } else {
                        player.seekToDefaultPosition(); player.prepare()
                    }
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
            override fun onTracksChanged(tracks: androidx.media3.common.Tracks) { tracksVersion++ }

            override fun onPlayerError(e: PlaybackException) {
                if (e.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
                    player.seekToDefaultPosition(); player.prepare(); return
                }
                // Sommige providers geven op een .ts-adres eigenlijk HLS terug: dan het andere formaat proberen.
                val url = items[index].url
                if (Settings.hlsFallback.on && !triedHls && url.endsWith(".ts") && e.errorCode in PARSE_ERRORS) {
                    triedHls = true
                    player.setMediaItem(mediaItem(url.removeSuffix(".ts") + ".m3u8"))
                    player.prepare()
                    return
                }
                if (retries < Settings.retries.int) {
                    retries++
                    player.prepare()
                    if (pb.live) player.seekToDefaultPosition()
                } else {
                    error = "Dit kan nu niet worden afgespeeld (${e.errorCodeName.removePrefix("ERROR_CODE_").lowercase().replace('_', ' ')})"
                    buffering = false
                }
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    LaunchedEffect(index) {
        error = null
        retries = 0
        triedHls = false
        showOverlay()
        if (player.playbackState != Player.STATE_IDLE) {
            // Oude stream eerst helemaal dicht (verbinding én decoder): veel providers staan maar één
            // verbinding tegelijk toe, en sommige tv-chips bevriezen als de decoder hergebruikt wordt.
            player.stop()
            player.clearMediaItems()
            delay(if (pb.live) 250 else 50)
        }
        player.setMediaItem(mediaItem(items[index].url))
        player.prepare()
        if (index == pb.index && pb.startAt > 0) player.seekTo(pb.startAt)
        player.playWhenReady = true
    }

    // Waakhond voor live tv: staat het beeld te lang stil, dan zelf opnieuw verbinden.
    var reconnects by remember { mutableIntStateOf(0) }
    LaunchedEffect(index, reconnects) {
        if (!pb.live) return@LaunchedEffect
        delay(8000) // eerst rustig laten opstarten
        var last = -1L
        var stuck = 0
        while (true) {
            delay(1000)
            val pos = player.currentPosition
            val waiting = player.playWhenReady && error == null &&
                (player.playbackState == Player.STATE_BUFFERING || (player.playbackState == Player.STATE_READY && pos == last))
            last = pos
            stuck = if (waiting) stuck + 1 else 0
            if (stuck >= 5) {
                say("Verbinding opnieuw opgezet")
                player.stop()
                delay(300)
                player.setMediaItem(mediaItem(items[index].url))
                player.prepare()
                player.playWhenReady = true
                reconnects++
                return@LaunchedEffect
            }
        }
    }

    LaunchedEffect(overlayTick) {
        overlay = true
        val secs = Settings.infoTime.int
        if (secs == 0 && pb.live) return@LaunchedEffect
        delay(if (secs == 0) 4000L else secs * 1000L)
        overlay = !playing && !pb.live
    }
    LaunchedEffect(panel, panelTick) {
        if (panel) {
            delay(12_000); panel = false
        }
    }
    LaunchedEffect(menu, menuTick) {
        if (menu && pb.live) {
            delay(20_000); menu = false
        }
    }
    LaunchedEffect(toast) {
        if (toast != null) {
            delay(3000); toast = null
        }
    }
    // Positie bijhouden voor de voortgangsbalk van films/series.
    LaunchedEffect(pb.live) {
        if (pb.live) return@LaunchedEffect
        while (true) {
            position = player.currentPosition
            length = player.duration.coerceAtLeast(0)
            delay(500)
        }
    }

    // ---------- geluid en ondertitels ----------
    fun trackChoices(type: Int): List<Pair<String, () -> Unit>> {
        val out = ArrayList<Pair<String, () -> Unit>>()
        player.currentTracks.groups.filter { it.type == type }.forEach { g ->
            for (i in 0 until g.length) {
                val f = g.getTrackFormat(i)
                val name = f.label ?: f.language?.let { java.util.Locale(it).getDisplayLanguage(java.util.Locale("nl")).replaceFirstChar { c -> c.uppercase() } } ?: "Spoor ${out.size + 1}"
                out += name to {
                    player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                        .setTrackTypeDisabled(type, false)
                        .setOverrideForType(TrackSelectionOverride(g.mediaTrackGroup, i))
                        .build()
                }
            }
        }
        return out
    }
    fun currentTrack(type: Int): String? {
        player.currentTracks.groups.filter { it.type == type }.forEach { g ->
            for (i in 0 until g.length) if (g.isTrackSelected(i)) {
                val f = g.getTrackFormat(i)
                return f.label ?: f.language?.let { java.util.Locale(it).getDisplayLanguage(java.util.Locale("nl")).replaceFirstChar { c -> c.uppercase() } } ?: "Spoor ${i + 1}"
            }
        }
        return null
    }
    fun cycleAudio() {
        val choices = trackChoices(MC.TRACK_TYPE_AUDIO)
        if (choices.size < 2) { say("Er is maar één geluidsspoor"); return }
        val cur = choices.indexOfFirst { it.first == currentTrack(MC.TRACK_TYPE_AUDIO) }
        val next = choices[(cur + 1) % choices.size]
        next.second(); say("Geluid: ${next.first}")
    }
    fun cycleSubs() {
        val choices = trackChoices(MC.TRACK_TYPE_TEXT)
        if (choices.isEmpty()) { say("Geen ondertitels beschikbaar"); return }
        val off = player.trackSelectionParameters.disabledTrackTypes.contains(MC.TRACK_TYPE_TEXT) || currentTrack(MC.TRACK_TYPE_TEXT) == null
        val cur = if (off) -1 else choices.indexOfFirst { it.first == currentTrack(MC.TRACK_TYPE_TEXT) }
        if (cur == choices.lastIndex) {
            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().setTrackTypeDisabled(MC.TRACK_TYPE_TEXT, true).build()
            say("Ondertitels uit")
        } else {
            val next = choices[cur + 1]
            next.second(); say("Ondertitels: ${next.first}")
        }
    }

    // ---------- het pauzemenu ----------
    val nowProgram = rememberPrograms(item.epgId, 1).firstOrNull()?.takeIf { it.isLive() }
    @Suppress("UNUSED_EXPRESSION") tracksVersion
    val recording = channel?.let { Recordings.activeFor(it.url) }
    val entries: List<MenuEntry> = when (menuPage) {
        "record" -> buildList {
            val now = System.currentTimeMillis()
            if (nowProgram != null) add(MenuEntry("Tot einde programma", Ic.Record, "tot ${clock(nowProgram.stop)}") {
                channel?.let { Recordings.schedule(nowProgram.title, it, now, nowProgram.stop + Settings.recordPost.int * 60_000L) }
                say("Opname gestart"); menu = false
            })
            listOf(30, 60, 120).forEach { m ->
                add(MenuEntry(if (m < 60) "$m minuten" else "${m / 60} uur", Ic.Record) {
                    channel?.let { Recordings.schedule(nowProgram?.title ?: it.name, it, now, now + m * 60_000L) }
                    say("Opname gestart"); menu = false
                })
            }
            add(MenuEntry("Terug", Icons.Filled.Close) { menuPage = "main"; menuIndex = 0 })
        }
        else -> buildList {
            if (pb.live) {
                add(MenuEntry("Kanalenlijst", Icons.Filled.List) { menu = false; panelIndex = index; panel = true; panelTick++ })
                if (Mobile) {
                    add(MenuEntry("Volgend kanaal", Ic.SkipNext) { go(index + 1) })
                    add(MenuEntry("Vorig kanaal", Ic.SkipPrev) { go(index - 1) })
                }
                val p = Library.active
                if (channel != null && channel.archiveDays > 0 && nowProgram != null && p?.type == SourceType.XTREAM) {
                    add(MenuEntry("Vanaf begin kijken", Ic.Restart, nowProgram.title) {
                        flush()
                        Nav.play = Playback(listOf(PlayItem(nowProgram.title, Xtream.catchupUrl(p, channel, nowProgram.start, nowProgram.stop),
                            channel.logo, subtitle = channel.name, key = "live:${channel.id}", kind = "live", image = channel.logo)), 0, live = false)
                    })
                }
                if (channel != null) {
                    if (recording != null) add(MenuEntry("Opname stoppen", Ic.Record, recording.title) {
                        Recordings.stop(recording); say("Opname gestopt"); menu = false
                    }) else add(MenuEntry("Opnemen", Ic.Record) {
                        if (Settings.recordDefault.value == "program" || nowProgram == null) { menuPage = "record"; menuIndex = 0 }
                        else {
                            val now = System.currentTimeMillis()
                            Recordings.schedule(nowProgram.title, channel, now, now + Settings.recordDefault.int * 60_000L)
                            say("Opname gestart"); menu = false
                        }
                    })
                    add(MenuEntry("Programmagids", Icons.Filled.DateRange) { close(); Nav.open(Overlay.Guide(channel)) })
                }
            } else {
                add(MenuEntry(if (playing) "Pauzeren" else "Doorgaan", if (playing) Ic.Pause else Icons.Filled.PlayArrow) {
                    if (player.isPlaying) player.pause() else { player.play(); if (!Mobile) menu = false }
                })
                if (Mobile) {
                    add(MenuEntry("${Settings.seekBack.int} sec terug", Ic.Rewind) { seekBy(-Settings.seekBack.int * 1000L) })
                    add(MenuEntry("${Settings.seekForward.int} sec vooruit", Ic.Forward) { seekBy(Settings.seekForward.int * 1000L) })
                }
                if (index < items.lastIndex) add(MenuEntry("Volgende aflevering", Ic.SkipNext, items[index + 1].title) { go(index + 1); menu = false })
                add(MenuEntry("Vanaf het begin", Ic.Restart) { player.seekTo(0); player.play(); menu = false })
            }
            add(MenuEntry("Geluid", Ic.Audio, currentTrack(MC.TRACK_TYPE_AUDIO) ?: "Standaard") { cycleAudio() })
            add(MenuEntry("Ondertitels", Ic.Subtitles,
                if (player.trackSelectionParameters.disabledTrackTypes.contains(MC.TRACK_TYPE_TEXT)) "Uit" else currentTrack(MC.TRACK_TYPE_TEXT) ?: "Uit") { cycleSubs() })
            add(MenuEntry("Beeld", Ic.Aspect, resizeModes.first { it.first == resize }.second) {
                resize = resizeModes[(resizeModes.indexOfFirst { it.first == resize } + 1) % resizeModes.size].first
            })
            add(MenuEntry("Stoppen met kijken", Icons.Filled.Close) { close() })
        }
    }
    fun openMenu() {
        menuPage = "main"; menuIndex = 0; menu = true; panel = false; menuTick++
    }
    val restartEntry = entries.firstOrNull { it.icon == Ic.Restart && pb.live }

    /** Opname-keuzes (voor het telefoonmenu). */
    fun recordOptions(): List<Pair<String, () -> Unit>> {
        val ch = channel ?: return emptyList()
        val now = System.currentTimeMillis()
        return buildList {
            if (nowProgram != null) add("Tot einde programma (${clock(nowProgram.stop)})" to {
                Recordings.schedule(nowProgram.title, ch, now, nowProgram.stop + Settings.recordPost.int * 60_000L); say("Opname gestart")
            })
            listOf(30, 60, 120).forEach { m ->
                add((if (m < 60) "$m minuten" else "${m / 60} uur") to {
                    Recordings.schedule(nowProgram?.title ?: ch.name, ch, now, now + m * 60_000L); say("Opname gestart")
                })
            }
        }
    }

    // ---------- afstandsbediening ----------
    val keys = rememberUpdatedState { e: KeyEvent ->
        val down = e.action == KeyEvent.ACTION_DOWN
        val up = e.action == KeyEvent.ACTION_UP
        when (e.keyCode) {
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_CHANNEL_UP, KeyEvent.KEYCODE_CHANNEL_DOWN -> {
                if (down) {
                    val isUp = e.keyCode == KeyEvent.KEYCODE_DPAD_UP || e.keyCode == KeyEvent.KEYCODE_CHANNEL_UP
                    when {
                        menu -> { menuIndex = (menuIndex + if (isUp) -1 else 1).coerceIn(0, entries.lastIndex); menuTick++ }
                        panel -> { panelIndex = (panelIndex + if (isUp) -1 else 1).coerceIn(0, items.lastIndex); panelTick++ }
                        pb.live -> {
                            val forward = if (Settings.zapDirection.value == "next") isUp else !isUp
                            go(index + if (forward) 1 else -1)
                        }
                        else -> showOverlay()
                    }
                }
                true
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                // Lang indrukken opent het andere van de twee (lijst of menu); kort indrukken pas bij loslaten.
                if (down && e.repeatCount == 0) okLong = false
                if (down && e.repeatCount > 0 && !okLong) {
                    okLong = true
                    if (!menu && !panel) {
                        if (!pb.live || Settings.okLive.value == "list") openMenu() else { panelIndex = index; panel = true; panelTick++ }
                    }
                }
                if (up && !e.isCanceled && !okLong) when {
                    menu -> entries.getOrNull(menuIndex)?.action?.invoke()
                    panel -> { panel = false; if (panelIndex != index) go(panelIndex) }
                    pb.live -> if (Settings.okLive.value == "list") { panelIndex = index; panel = true; panelTick++ } else openMenu()
                    else -> {
                        if (player.isPlaying) { player.pause(); openMenu() } else { player.play(); menu = false }
                        showOverlay()
                    }
                }
                true
            }
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                if (down && e.repeatCount == 0) {
                    if (player.isPlaying) player.pause() else player.play()
                    showOverlay()
                }
                true
            }
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_MEDIA_REWIND, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                if (down) {
                    val back = e.keyCode == KeyEvent.KEYCODE_DPAD_LEFT || e.keyCode == KeyEvent.KEYCODE_MEDIA_REWIND
                    when {
                        menu -> if (back) menu = false else entries.getOrNull(menuIndex)?.action?.invoke()
                        panel -> if (back) panel = false
                        pb.live -> showOverlay()
                        else -> {
                            val step = when {
                                e.repeatCount > 20 -> 120_000L
                                e.repeatCount > 5 -> 60_000L
                                back -> Settings.seekBack.int * 1000L
                                else -> Settings.seekForward.int * 1000L
                            }
                            seekBy(if (back) -step else step)
                        }
                    }
                }
                true
            }
            KeyEvent.KEYCODE_MEDIA_NEXT -> { if (down) go(index + 1); true }
            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> { if (down) go(index - 1); true }
            KeyEvent.KEYCODE_MEDIA_RECORD -> { if (down && pb.live) { openMenu(); menuPage = "record" }; true }
            KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_INFO, KeyEvent.KEYCODE_GUIDE -> { if (down) { if (menu) menu = false else openMenu() }; true }
            else -> false // volume, home e.d. gewoon doorlaten
        }
    }
    // Terug komt op nieuwe Android-versies niet als toets binnen, maar via de back-dispatcher.
    BackHandler {
        when {
            sheet != null -> sheet = null
            menu && menuPage != "main" -> { menuPage = "main"; menuIndex = 0 }
            menu -> { menu = false; if (!pb.live && !player.isPlaying) player.play() }
            panel -> panel = false
            overlay && pb.live && !Mobile -> overlay = false
            else -> close()
        }
    }
    DisposableEffect(Unit) {
        Nav.playerKeys = { keys.value(it) }
        onDispose { Nav.playerKeys = null }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(onTap = {
                    when {
                        panel -> panel = false
                        Mobile && sheet != null -> sheet = null
                        Mobile -> { controls = !controls; controlsTick++ }
                        menu -> menu = false
                        else -> { openMenu(); showOverlay() }
                    }
                })
            },
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    useController = false
                    keepScreenOn = true
                    isFocusable = false
                    isFocusableInTouchMode = false
                    isClickable = false
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                    setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                    subtitleView?.apply {
                        setFractionalTextSize(SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * Settings.subSize.float)
                        setStyle(
                            CaptionStyleCompat(
                                android.graphics.Color.WHITE,
                                if (Settings.subBackground.on) 0xAA000000.toInt() else android.graphics.Color.TRANSPARENT,
                                android.graphics.Color.TRANSPARENT,
                                if (Settings.subBackground.on) CaptionStyleCompat.EDGE_TYPE_NONE else CaptionStyleCompat.EDGE_TYPE_OUTLINE,
                                android.graphics.Color.BLACK, null,
                            ),
                        )
                    }
                    this.player = player
                }
            },
            update = { it.resizeMode = resizeMode(resize) },
            modifier = Modifier.fillMaxSize(),
        )

        if (buffering && error == null) Spinner(Modifier.align(Alignment.Center))
        error?.let {
            Column(
                Modifier.align(Alignment.Center).padding(24.dp).background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(18.dp)).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                T(it, size = 20.sp, weight = FontWeight.Bold, maxLines = 3)
                Spacer(Modifier.height(6.dp))
                T(if (pb.live) "Kies een ander kanaal of druk op Terug" else "Druk op Terug", color = C.dim)
            }
        }
        recording?.let {
            Row(
                Modifier.align(Alignment.TopEnd).padding(24.dp).background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Ic.Record, C.live, 16.dp); Spacer(Modifier.width(8.dp)); T("Opname bezig", size = 14.sp)
            }
        }

        if (Mobile) {
            MobileControls(
                visible = controls && !panel && sheet == null,
                item = item, number = index + 1, live = pb.live, playing = playing, position = position, length = length,
                recording = recording != null, canRestart = restartEntry != null,
                onClose = { close() },
                onChannels = { controls = false; panelIndex = index; panel = true; panelTick++ },
                onRecord = { sheet = "record" },
                onAudio = { sheet = "audio" },
                onSubs = { sheet = "subs" },
                onAspect = { resize = resizeModes[(resizeModes.indexOfFirst { it.first == resize } + 1) % resizeModes.size].first; say("Beeld: ${resizeModes.first { it.first == resize }.second}") },
                onRestart = { restartEntry?.action?.invoke() },
                onPrev = { if (pb.live) go(index - 1) else seekBy(-Settings.seekBack.int * 1000L) },
                onNext = { if (pb.live) go(index + 1) else seekBy(Settings.seekForward.int * 1000L) },
                onPlayPause = { if (player.isPlaying) player.pause() else player.play() },
                onSeek = { f -> if (length > 0) { player.seekTo((length * f).toLong()); position = (length * f).toLong() } },
                onTouch = { controlsTick++ },
            )
            sheet?.let { which ->
                val options: List<Triple<String, Boolean, () -> Unit>> = when (which) {
                    "audio" -> trackChoices(MC.TRACK_TYPE_AUDIO).map { (name, act) -> Triple(name, name == currentTrack(MC.TRACK_TYPE_AUDIO), act) }
                    "subs" -> {
                        val off = player.trackSelectionParameters.disabledTrackTypes.contains(MC.TRACK_TYPE_TEXT)
                        listOf(Triple("Uit", off, {
                            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().setTrackTypeDisabled(MC.TRACK_TYPE_TEXT, true).build()
                        })) + trackChoices(MC.TRACK_TYPE_TEXT).map { (name, act) -> Triple(name, !off && name == currentTrack(MC.TRACK_TYPE_TEXT), act) }
                    }
                    else -> recordOptions().map { (label, act) -> Triple(label, false, act) }
                }
                OptionSheet(
                    title = when (which) { "audio" -> "Geluid"; "subs" -> "Ondertitels"; else -> if (recording != null) "Opname" else "Opnemen" },
                    options = if (which == "record" && recording != null) listOf(Triple("Opname stoppen", false) { Recordings.stop(recording); say("Opname gestopt") }) else options,
                    empty = if (which == "audio") "Er is maar één geluidsspoor" else "Niets te kiezen",
                    modifier = Modifier.align(Alignment.BottomCenter),
                    onPick = { it(); sheet = null },
                    onClose = { sheet = null },
                )
            }
        } else {
        AnimatedVisibility((overlay || menu) && !panel, Modifier.align(Alignment.BottomCenter), enter = fadeIn(), exit = fadeOut()) {
            if (pb.live) LiveInfo(item, index + 1, showClock = !menu) else VodInfo(item, position, length, playing)
        }
        AnimatedVisibility(
            menu,
            Modifier.align(Alignment.CenterEnd),
            enter = slideInHorizontally { it } + fadeIn(),
            exit = slideOutHorizontally { it } + fadeOut(),
        ) { PauseMenu(if (menuPage == "record") "Opnemen" else if (pb.live) item.title else "Gepauzeerd", entries, menuIndex) }
        }

        AnimatedVisibility(
            panel,
            Modifier.align(Alignment.CenterStart),
            enter = slideInHorizontally { -it } + fadeIn(),
            exit = slideOutHorizontally { -it } + fadeOut(),
        ) { ChannelPanel(items, index, panelIndex) { panel = false; if (it != index) go(it) } }

        if (Settings.techInfo.on) TechInfo(player, Modifier.align(Alignment.TopStart).padding(16.dp))

        toast?.let {
            T(it, Modifier.align(Alignment.TopCenter).padding(top = 32.dp).background(C.accentDeep, RoundedCornerShape(20.dp))
                .padding(horizontal = 20.dp, vertical = 10.dp), size = 17.sp, weight = FontWeight.Bold)
        }
    }
}

/** Ronde knop met icoon voor de telefoonbediening. */
@Composable
private fun RoundButton(icon: ImageVector, size: Int, onClick: () -> Unit, tint: Color = Color.White, bg: Color = Color.Black.copy(alpha = 0.35f)) {
    Box(
        Modifier.size(size.dp).clip(CircleShape).background(bg).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, tint, (size * 0.5f).dp) }
}

/**
 * Bediening op de telefoon: bovenin terug, titel en snelle instellingen; in het midden grote knoppen;
 * onderin de tijdbalk (films) of het programma (live).
 */
@Composable
private fun MobileControls(
    visible: Boolean, item: PlayItem, number: Int, live: Boolean, playing: Boolean, position: Long, length: Long,
    recording: Boolean, canRestart: Boolean,
    onClose: () -> Unit, onChannels: () -> Unit, onRecord: () -> Unit, onAudio: () -> Unit, onSubs: () -> Unit,
    onAspect: () -> Unit, onRestart: () -> Unit, onPrev: () -> Unit, onNext: () -> Unit, onPlayPause: () -> Unit,
    onSeek: (Float) -> Unit, onTouch: () -> Unit,
) {
    AnimatedVisibility(visible, enter = fadeIn(), exit = fadeOut()) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f))) {
            // Boven: terug, titel, snelle instellingen
            Row(
                Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.8f), Color.Transparent)))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RoundButton(Icons.Filled.ArrowBack, 44, onClose)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    T(if (live) "$number  ${item.title}" else item.title, size = 18.sp, weight = FontWeight.Bold)
                    item.subtitle?.let { T(it, size = 13.sp, color = C.dim) }
                }
                if (live) {
                    QuickButton(Icons.Filled.List, "Kanalen") { onTouch(); onChannels() }
                    if (canRestart) QuickButton(Ic.Restart, "Begin") { onTouch(); onRestart() }
                    QuickButton(Ic.Record, if (recording) "Opname" else "Opnemen", tint = if (recording) C.live else Color.White) { onTouch(); onRecord() }
                }
                QuickButton(Ic.Audio, "Geluid") { onTouch(); onAudio() }
                QuickButton(Ic.Subtitles, "Ondertitels") { onTouch(); onSubs() }
                QuickButton(Ic.Aspect, "Beeld") { onTouch(); onAspect() }
            }
            // Midden: grote knoppen
            Row(Modifier.align(Alignment.Center), horizontalArrangement = Arrangement.spacedBy(40.dp), verticalAlignment = Alignment.CenterVertically) {
                RoundButton(if (live) Ic.SkipPrev else Ic.Rewind, 60, { onTouch(); onPrev() })
                if (!live) RoundButton(if (playing) Ic.Pause else Icons.Filled.PlayArrow, 80, { onTouch(); onPlayPause() }, bg = C.accent.copy(alpha = 0.9f))
                RoundButton(if (live) Ic.SkipNext else Ic.Forward, 60, { onTouch(); onNext() })
            }
            // Onder: tijdbalk of programma
            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))))
                    .padding(horizontal = 24.dp, vertical = 16.dp),
            ) {
                if (live) {
                    val programs = rememberPrograms(item.epgId, 2)
                    val now = programs.firstOrNull()?.takeIf { it.isLive() }
                    if (now != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            T("NU", size = 13.sp, color = C.live, weight = FontWeight.Black)
                            Spacer(Modifier.width(8.dp))
                            T(now.title, size = 16.sp, weight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            T("${clock(now.start)} – ${clock(now.stop)}", size = 13.sp, color = C.dim)
                        }
                        Spacer(Modifier.height(8.dp))
                        ProgressBar(now.progress(), Modifier.fillMaxWidth())
                    }
                } else {
                    SeekBar(if (length > 0) position.toFloat() / length else 0f, onSeek, onTouch)
                    Spacer(Modifier.height(6.dp))
                    Row {
                        T(duration(position), size = 13.sp, color = C.dim, modifier = Modifier.weight(1f))
                        if (length > 0) T("-" + duration(length - position), size = 13.sp, color = C.dim)
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickButton(icon: ImageVector, label: String, tint: Color = Color.White, onClick: () -> Unit) {
    Column(
        Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, tint, 24.dp)
        T(label, size = 10.sp, color = C.dim)
    }
}

/** Tijdbalk waar je op kunt tikken of langs kunt slepen. */
@Composable
private fun SeekBar(progress: Float, onSeek: (Float) -> Unit, onTouch: () -> Unit) {
    var drag by remember { mutableStateOf<Float?>(null) }
    val shown = drag ?: progress
    Box(
        Modifier.fillMaxWidth().height(28.dp)
            .pointerInput(Unit) {
                detectTapGestures { o -> onTouch(); onSeek((o.x / size.width).coerceIn(0f, 1f)) }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { o -> drag = (o.x / size.width).coerceIn(0f, 1f) },
                    onDragEnd = { drag?.let(onSeek); drag = null },
                    onDragCancel = { drag = null },
                ) { change, _ -> onTouch(); drag = (change.position.x / size.width).coerceIn(0f, 1f) }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)).background(Color.White.copy(alpha = 0.25f)))
        Box(Modifier.fillMaxWidth(shown).height(5.dp).clip(RoundedCornerShape(3.dp)).background(C.accent))
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
            Box(Modifier.padding(start = (maxWidth - 16.dp) * shown).size(16.dp).clip(CircleShape).background(Color.White))
        }
    }
}

/** Keuzelijst die van onderen omhoog komt (geluid, ondertitels, opnemen). */
@Composable
private fun OptionSheet(
    title: String, options: List<Triple<String, Boolean, () -> Unit>>, empty: String, modifier: Modifier,
    onPick: (() -> Unit) -> Unit, onClose: () -> Unit,
) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 80.dp)
            .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
            .background(C.panel.copy(alpha = 0.97f))
            .pointerInput(Unit) { detectTapGestures { } }
            .padding(horizontal = 20.dp, vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            T(title, size = 20.sp, weight = FontWeight.Bold, modifier = Modifier.weight(1f))
            RoundButton(Icons.Filled.Close, 36, onClose, bg = C.surfaceHi)
        }
        Spacer(Modifier.height(8.dp))
        if (options.isEmpty()) T(empty, color = C.dim, modifier = Modifier.padding(vertical = 12.dp))
        LazyColumn(Modifier.heightIn(max = 220.dp)) {
            items(options.size) { i ->
                val (label, selected, act) = options[i]
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onPick(act) }.padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    T(label, size = 16.sp, weight = if (selected) FontWeight.Bold else FontWeight.Normal, color = if (selected) C.accent else C.text, modifier = Modifier.weight(1f))
                    if (selected) Icon(Icons.Filled.Check, C.accent, 20.dp)
                }
            }
        }
    }
}

/** Technische info voor wie wil weten wat er gebeurt (aan te zetten in Geavanceerd). */
@OptIn(UnstableApi::class)
@Composable
private fun TechInfo(player: ExoPlayer, modifier: Modifier) {
    val text by produceState("") {
        while (true) {
            val v = player.videoFormat
            val a = player.audioFormat
            val state = when (player.playbackState) {
                Player.STATE_BUFFERING -> "laden"; Player.STATE_READY -> if (player.isPlaying) "speelt" else "klaar"
                Player.STATE_ENDED -> "einde"; else -> "stil"
            }
            val counters = player.videoDecoderCounters
            value = buildString {
                append("Status: $state · buffer ${(player.totalBufferedDuration / 100) / 10.0} s\n")
                if (v != null) append("Beeld: ${v.sampleMimeType?.substringAfter('/')} ${v.width}x${v.height} ${if (v.frameRate > 0) "%.0f fps".format(v.frameRate) else ""}\n")
                if (a != null) append("Geluid: ${a.sampleMimeType?.substringAfter('/')} ${a.channelCount} kan. ${a.sampleRate} Hz\n")
                if (counters != null) append("Frames: ${counters.renderedOutputBufferCount} getoond · ${counters.droppedBufferCount} overgeslagen")
            }
            delay(1000)
        }
    }
    T(text, modifier.background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(10.dp)).padding(12.dp), size = 13.sp, maxLines = 5)
}

@Composable
private fun PauseMenu(title: String, entries: List<MenuEntry>, highlighted: Int) {
    Column(
        Modifier.width(if (Mobile) 320.dp else 460.dp).fillMaxHeight()
            .background(Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.75f), Color.Black.copy(alpha = 0.95f))))
            .padding(top = 32.dp, start = 24.dp, end = 24.dp),
    ) {
        T(title, size = 24.sp, weight = FontWeight.Black)
        if (!Mobile) T("Omhoog/omlaag kiezen · OK doen · Terug sluiten", size = 13.sp, color = C.dim)
        Spacer(Modifier.height(16.dp))
        val state = rememberLazyListState()
        LaunchedEffect(highlighted) { if (highlighted >= 5) state.animateScrollToItem(highlighted - 4) }
        LazyColumn(state = state, verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(bottom = 32.dp)) {
            items(entries.size) { i ->
                val e = entries[i]
                val hl = i == highlighted && !Mobile
                Row(
                    Modifier.fillMaxWidth().height(if (Mobile) 52.dp else 60.dp)
                        .background(if (hl) Color.White else Color.White.copy(alpha = 0.06f), RoundedCornerShape(14.dp))
                        .clickable { e.action() }
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(e.icon, if (hl) Color.Black else C.accent, 24.dp)
                    Spacer(Modifier.width(14.dp))
                    T(e.label, size = 18.sp, weight = FontWeight.Bold, color = if (hl) Color.Black else C.text, modifier = Modifier.weight(1f))
                    if (e.value != null) T(e.value, size = 15.sp, color = if (hl) Color.DarkGray else C.dim, modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}

@Composable
private fun LiveInfo(item: PlayItem, number: Int, showClock: Boolean) {
    val programs = rememberPrograms(item.epgId, 2)
    val now = programs.firstOrNull()?.takeIf { it.isLive() }
    val next = programs.getOrNull(if (now != null) 1 else 0)
    val time by produceState(System.currentTimeMillis()) { while (true) { value = System.currentTimeMillis(); delay(10_000) } }
    Box(
        Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.92f))))
            .padding(start = Pad + 8.dp, end = Pad + 8.dp, top = 80.dp, bottom = if (Mobile) 20.dp else 40.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Logo(item.logo, item.title, Modifier.size(if (Mobile) 110.dp else 170.dp, if (Mobile) 64.dp else 100.dp))
            Spacer(Modifier.width(if (Mobile) 16.dp else 28.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (Settings.showNumbers.on) {
                        T("$number", size = 22.sp, color = C.accent, weight = FontWeight.Black)
                        Spacer(Modifier.width(14.dp))
                    }
                    T(item.title, size = 28.sp, weight = FontWeight.Black)
                }
                Spacer(Modifier.height(6.dp))
                if (now != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        T("NU", size = 14.sp, color = C.live, weight = FontWeight.Black)
                        Spacer(Modifier.width(10.dp))
                        T(now.title, size = 20.sp, weight = FontWeight.Bold, modifier = Modifier.weight(1f, fill = false))
                        Spacer(Modifier.width(12.dp))
                        T("${clock(now.start)} – ${clock(now.stop)}", size = 16.sp, color = C.dim)
                    }
                    Spacer(Modifier.height(8.dp))
                    ProgressBar(now.progress(), Modifier.width(520.dp))
                } else T("Geen gidsinformatie", color = C.dim)
                if (next != null) {
                    Spacer(Modifier.height(8.dp))
                    T("Straks ${clock(next.start)}  ${next.title}", size = 16.sp, color = C.dim)
                }
            }
            if (Settings.playerClock.on && showClock) T(clock(time), size = 30.sp, weight = FontWeight.Bold)
        }
    }
}

@Composable
private fun VodInfo(item: PlayItem, position: Long, length: Long, playing: Boolean) {
    Box(
        Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f))))
            .padding(start = Pad + 8.dp, end = Pad + 8.dp, top = 80.dp, bottom = if (Mobile) 20.dp else 40.dp),
    ) {
        Column {
            if (item.subtitle != null) T(item.subtitle, size = 16.sp, color = C.dim)
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!playing) {
                    Icon(Ic.Pause, C.text, 30.dp)
                    Spacer(Modifier.width(8.dp))
                }
                T(item.title, size = 28.sp, weight = FontWeight.Black)
            }
            Spacer(Modifier.height(16.dp))
            ProgressBar(if (length > 0) position.toFloat() / length else 0f, Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            Row {
                T(duration(position), color = C.dim, modifier = Modifier.weight(1f))
                if (length > 0) T("-" + duration(length - position), color = C.dim)
            }
            if (!Mobile) {
                Spacer(Modifier.height(4.dp))
                T("OK = pauzemenu · ◀ ▶ = terug/vooruit spoelen", size = 13.sp, color = C.dim.copy(alpha = 0.7f))
            }
        }
    }
}

@Composable
private fun ChannelPanel(items: List<PlayItem>, current: Int, highlighted: Int, onPick: (Int) -> Unit) {
    val state = rememberLazyListState(initialFirstVisibleItemIndex = (highlighted - 4).coerceAtLeast(0))
    LaunchedEffect(highlighted) {
        val visible = state.layoutInfo.visibleItemsInfo
        val first = visible.firstOrNull()?.index ?: 0
        val last = visible.lastOrNull()?.index ?: 0
        if (highlighted <= first || highlighted >= last) state.animateScrollToItem((highlighted - 4).coerceAtLeast(0))
    }
    Column(
        Modifier.width(if (Mobile) 340.dp else 560.dp).fillMaxHeight()
            .background(Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.95f), Color.Black.copy(alpha = 0.8f))))
            .padding(top = 36.dp, start = 28.dp, end = 20.dp),
    ) {
        T("Kanalen", size = 24.sp, weight = FontWeight.Black)
        if (!Mobile) T("Omhoog/omlaag om te kiezen · OK om te kijken", size = 14.sp, color = C.dim)
        Spacer(Modifier.height(14.dp))
        LazyColumn(state = state, verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(bottom = 40.dp)) {
            items(items.size) { i ->
                val it = items[i]
                val programs = rememberPrograms(it.epgId, 1)
                val now = programs.firstOrNull()?.takeIf { p -> p.isLive() }
                val hl = i == highlighted && !Mobile
                Row(
                    Modifier.fillMaxWidth().height(68.dp)
                        .background(if (hl) Color.White else Color.Transparent, RoundedCornerShape(12.dp))
                        .clickable { onPick(i) }
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (Settings.showNumbers.on) T("${i + 1}", Modifier.width(40.dp), color = if (hl) Color.Black else if (i == current) C.accent else C.dim, weight = FontWeight.Bold)
                    Logo(it.logo, it.title, Modifier.size(80.dp, 46.dp))
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        T(it.title, size = 17.sp, weight = FontWeight.Bold, color = if (hl) Color.Black else C.text)
                        if (now != null) T(now.title, size = 14.sp, color = if (hl) Color.DarkGray else C.dim)
                    }
                    if (i == current) Box(Modifier.size(10.dp).background(C.live, RoundedCornerShape(5.dp)))
                }
            }
        }
    }
}

@Composable
private fun Spinner(modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "spin")
    val angle by t.animateFloat(0f, 360f, infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart), label = "angle")
    Canvas(modifier.size(64.dp)) {
        drawArc(
            color = Color.White.copy(alpha = 0.15f), startAngle = 0f, sweepAngle = 360f, useCenter = false,
            style = Stroke(6.dp.toPx()),
        )
        drawArc(
            color = C.accent, startAngle = angle, sweepAngle = 90f, useCenter = false,
            topLeft = Offset.Zero, size = Size(size.width, size.height),
            style = Stroke(6.dp.toPx(), cap = StrokeCap.Round),
        )
    }
}
