package nl.timvanbergen.iptimtv.ui

import android.content.Context
import android.view.KeyEvent
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import nl.timvanbergen.iptimtv.data.Library
import nl.timvanbergen.iptimtv.data.Net

@OptIn(UnstableApi::class)
private fun buildPlayer(context: Context): ExoPlayer {
    val http = OkHttpDataSource.Factory(Net.client)
    val extractors = DefaultExtractorsFactory().setTsExtractorFlags(
        DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES or DefaultTsPayloadReaderFactory.FLAG_DETECT_ACCESS_UNITS,
    )
    // Kleine buffer vooraf: zappen moet snel gaan.
    val load = DefaultLoadControl.Builder().setBufferDurationsMs(15_000, 50_000, 1_000, 2_500).build()
    return ExoPlayer.Builder(context, DefaultRenderersFactory(context).setEnableDecoderFallback(true))
        .setMediaSourceFactory(DefaultMediaSourceFactory(DefaultDataSource.Factory(context, http), extractors))
        .setLoadControl(load)
        .build()
        .apply {
            trackSelectionParameters = trackSelectionParameters.buildUpon()
                .setPreferredAudioLanguage("nl")
                .setPreferredTextLanguage("nl")
                .build()
        }
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

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(pb: Playback) {
    val context = LocalContext.current
    val player = remember { buildPlayer(context) }
    val items = pb.items
    var index by remember { mutableIntStateOf(pb.index) }
    val item = items[index]

    var buffering by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var playing by remember { mutableStateOf(true) }
    var position by remember { mutableLongStateOf(0L) }
    var length by remember { mutableLongStateOf(0L) }
    var retries by remember { mutableIntStateOf(0) }
    var triedHls by remember { mutableStateOf(false) }

    var overlayTick by remember { mutableIntStateOf(0) }
    var overlay by remember { mutableStateOf(true) }
    var panel by remember { mutableStateOf(false) }
    var panelIndex by remember { mutableIntStateOf(index) }
    var panelTick by remember { mutableIntStateOf(0) }

    fun showOverlay() { overlayTick++ }
    fun saveProgress() {
        if (!pb.live) Library.saveResume(items[index].url, player.currentPosition, player.duration.coerceAtLeast(0))
    }
    fun go(to: Int) {
        if (items.isEmpty()) return
        saveProgress()
        index = ((to % items.size) + items.size) % items.size
        items[index].channelId?.let { Library.setLastChannel(it) }
    }
    fun close() {
        saveProgress()
        Nav.play = null
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                buffering = state == Player.STATE_BUFFERING
                if (state == Player.STATE_READY) {
                    error = null; retries = 0
                }
                if (state == Player.STATE_ENDED) {
                    if (!pb.live && index < items.lastIndex) {
                        Library.saveResume(items[index].url, 0, 0)
                        index++
                    } else if (!pb.live) {
                        Library.saveResume(items[index].url, 0, 0)
                        Nav.play = null
                    } else {
                        player.seekToDefaultPosition(); player.prepare()
                    }
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }

            override fun onPlayerError(e: PlaybackException) {
                if (e.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
                    player.seekToDefaultPosition(); player.prepare(); return
                }
                // Sommige providers geven op een .ts-adres eigenlijk HLS terug: dan het andere formaat proberen.
                val url = items[index].url
                if (!triedHls && url.endsWith(".ts") && e.errorCode in PARSE_ERRORS) {
                    triedHls = true
                    player.setMediaItem(mediaItem(url.removeSuffix(".ts") + ".m3u8"))
                    player.prepare()
                    return
                }
                if (retries < 3) {
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
        player.setMediaItem(mediaItem(items[index].url))
        player.prepare()
        if (index == pb.index && pb.startAt > 0) player.seekTo(pb.startAt)
        player.playWhenReady = true
        showOverlay()
    }

    LaunchedEffect(overlayTick) {
        overlay = true
        delay(if (pb.live) 5_000 else 4_000)
        overlay = !playing && !pb.live
    }
    LaunchedEffect(panel, panelTick) {
        if (panel) {
            delay(12_000); panel = false
        }
    }
    // Positie bijhouden voor de voortgangsbalk van films/series, en af en toe opslaan.
    LaunchedEffect(pb.live) {
        if (pb.live) return@LaunchedEffect
        var n = 0
        while (true) {
            position = player.currentPosition
            length = player.duration.coerceAtLeast(0)
            if (++n % 30 == 0 && player.isPlaying) saveProgress()
            delay(500)
        }
    }

    val keys = rememberUpdatedState { e: KeyEvent ->
        val down = e.action == KeyEvent.ACTION_DOWN
        when (e.keyCode) {
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_CHANNEL_UP, KeyEvent.KEYCODE_CHANNEL_DOWN -> {
                if (down) {
                    val up = e.keyCode == KeyEvent.KEYCODE_DPAD_UP || e.keyCode == KeyEvent.KEYCODE_CHANNEL_UP
                    when {
                        panel -> {
                            panelIndex = (panelIndex + if (up) -1 else 1).coerceIn(0, items.lastIndex); panelTick++
                        }
                        pb.live -> go(index + if (up) 1 else -1)
                        else -> showOverlay()
                    }
                }
                true
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                if (down && e.repeatCount == 0) when {
                    panel -> {
                        panel = false; if (panelIndex != index) go(panelIndex)
                    }
                    pb.live -> {
                        panelIndex = index; panel = true; panelTick++
                    }
                    else -> {
                        if (player.isPlaying) player.pause() else player.play()
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
                        panel -> if (back) panel = false
                        pb.live -> showOverlay()
                        else -> {
                            val step = when {
                                e.repeatCount > 20 -> 120_000L
                                e.repeatCount > 5 -> 60_000L
                                back -> 10_000L
                                else -> 30_000L
                            }
                            val target = (player.currentPosition + if (back) -step else step)
                                .coerceIn(0, player.duration.takeIf { it > 0 } ?: Long.MAX_VALUE)
                            player.seekTo(target)
                            position = target
                            showOverlay()
                        }
                    }
                }
                true
            }
            KeyEvent.KEYCODE_MEDIA_NEXT -> { if (down) go(index + 1); true }
            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> { if (down) go(index - 1); true }
            KeyEvent.KEYCODE_INFO, KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_GUIDE -> { if (down) showOverlay(); true }
            else -> false // volume, home e.d. gewoon doorlaten
        }
    }
    // Terug komt op nieuwe Android-versies niet als toets binnen, maar via de back-dispatcher.
    BackHandler {
        when {
            panel -> panel = false
            overlay && pb.live -> overlay = false
            else -> close()
        }
    }
    DisposableEffect(Unit) {
        Nav.playerKeys = { keys.value(it) }
        onDispose { Nav.playerKeys = null }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    keepScreenOn = true
                    isFocusable = false
                    isFocusableInTouchMode = false
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                    setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                    this.player = player
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        if (buffering && error == null) Spinner(Modifier.align(Alignment.Center))
        error?.let {
            Column(
                Modifier.align(Alignment.Center).background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(18.dp)).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                T(it, size = 20.sp, weight = FontWeight.Bold, maxLines = 3)
                Spacer(Modifier.height(6.dp))
                T(if (pb.live) "Kies een ander kanaal met omhoog/omlaag of druk op Terug" else "Druk op Terug", color = C.dim)
            }
        }

        AnimatedVisibility(overlay && !panel, Modifier.align(Alignment.BottomCenter), enter = fadeIn(), exit = fadeOut()) {
            if (pb.live) LiveInfo(item, index + 1) else VodInfo(item, position, length, playing)
        }

        AnimatedVisibility(
            panel,
            Modifier.align(Alignment.CenterStart),
            enter = slideInHorizontally { -it } + fadeIn(),
            exit = slideOutHorizontally { -it } + fadeOut(),
        ) { ChannelPanel(items, index, panelIndex) }
    }
}

@Composable
private fun LiveInfo(item: PlayItem, number: Int) {
    val programs = rememberPrograms(item.epgId, 2)
    val now = programs.firstOrNull()?.takeIf { it.isLive() }
    val next = programs.getOrNull(if (now != null) 1 else 0)
    val time by produceState(System.currentTimeMillis()) { while (true) { value = System.currentTimeMillis(); delay(10_000) } }
    Box(
        Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.92f))))
            .padding(start = 56.dp, end = 56.dp, top = 80.dp, bottom = 40.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Logo(item.logo, item.title, Modifier.size(170.dp, 100.dp))
            Spacer(Modifier.width(28.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    T("$number", size = 22.sp, color = C.accent, weight = FontWeight.Black)
                    Spacer(Modifier.width(14.dp))
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
            T(clock(time), size = 30.sp, weight = FontWeight.Bold)
        }
    }
}

@Composable
private fun VodInfo(item: PlayItem, position: Long, length: Long, playing: Boolean) {
    Box(
        Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f))))
            .padding(start = 56.dp, end = 56.dp, top = 80.dp, bottom = 40.dp),
    ) {
        Column {
            if (item.subtitle != null) T(item.subtitle, size = 16.sp, color = C.dim)
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!playing) {
                    Icon(Icons.Filled.PlayArrow, C.text, 34.dp)
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
            Spacer(Modifier.height(4.dp))
            T("OK = pauze · ◀ ▶ = terug/vooruit spoelen", size = 13.sp, color = C.dim.copy(alpha = 0.7f))
        }
    }
}

@Composable
private fun ChannelPanel(items: List<PlayItem>, current: Int, highlighted: Int) {
    val state = rememberLazyListState(initialFirstVisibleItemIndex = (highlighted - 4).coerceAtLeast(0))
    LaunchedEffect(highlighted) {
        val visible = state.layoutInfo.visibleItemsInfo
        val first = visible.firstOrNull()?.index ?: 0
        val last = visible.lastOrNull()?.index ?: 0
        if (highlighted <= first || highlighted >= last) state.animateScrollToItem((highlighted - 4).coerceAtLeast(0))
    }
    Column(
        Modifier.width(560.dp).fillMaxHeight()
            .background(Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.95f), Color.Black.copy(alpha = 0.8f))))
            .padding(top = 36.dp, start = 28.dp, end = 20.dp),
    ) {
        T("Kanalen", size = 24.sp, weight = FontWeight.Black)
        T("Omhoog/omlaag om te kiezen · OK om te kijken", size = 14.sp, color = C.dim)
        Spacer(Modifier.height(14.dp))
        LazyColumn(state = state, verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(bottom = 40.dp)) {
            items(items.size) { i ->
                val it = items[i]
                val programs = rememberPrograms(it.epgId, 1)
                val now = programs.firstOrNull()?.takeIf { p -> p.isLive() }
                val hl = i == highlighted
                Row(
                    Modifier.fillMaxWidth().height(68.dp)
                        .background(if (hl) Color.White else Color.Transparent, RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    T("${i + 1}", Modifier.width(40.dp), color = if (hl) Color.Black else if (i == current) C.accent else C.dim, weight = FontWeight.Bold)
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
