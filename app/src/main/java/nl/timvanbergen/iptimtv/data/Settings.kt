package nl.timvanbergen.iptimtv.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class Choice(val value: String, val label: String)

/** Eén instelling uit het menu Geavanceerd: een sleutel, uitleg en een vaste lijst keuzes. */
class Setting(
    val key: String,
    val title: String,
    val description: String,
    val choices: List<Choice>,
    val default: String,
) {
    var value by mutableStateOf(default)
        internal set

    val label get() = choices.firstOrNull { it.value == value }?.label ?: value
    val on get() = value == "1"
    val int get() = value.toIntOrNull() ?: default.toIntOrNull() ?: 0
    val float get() = value.toFloatOrNull() ?: default.toFloat()

    /** Naar de volgende keuze (OK op de afstandsbediening). */
    fun next() = Settings.set(this, choices[(choices.indexOfFirst { it.value == value } + 1) % choices.size].value)
}

class Section(val title: String, val description: String, val settings: List<Setting>)

private fun toggle(key: String, title: String, description: String, default: Boolean) =
    Setting(key, title, description, listOf(Choice("1", "Aan"), Choice("0", "Uit")), if (default) "1" else "0")

private fun pick(key: String, title: String, description: String, default: String, vararg choices: Pair<String, String>) =
    Setting(key, title, description, choices.map { Choice(it.first, it.second) }, default)

private fun hours(vararg h: Int) = h.map { "$it" to (if (it < 24) "$it uur" else "${it / 24} dag" + if (it >= 48) "en" else "") }.toTypedArray()

object Settings {
    private lateinit var prefs: SharedPreferences

    // ---------- Weergave ----------
    val uiScale = pick("ui_scale", "Grootte van alles", "Maakt tekst, tegels en knoppen groter of kleiner.", "1.0",
        "0.85" to "Klein", "1.0" to "Normaal", "1.15" to "Groot", "1.3" to "Extra groot")
    val animations = toggle("animations", "Animaties", "Tegels groeien een beetje als je ze aanwijst.", true)
    val showClock = toggle("show_clock", "Klok rechtsboven", "De tijd bovenin het scherm.", true)
    val showNumbers = toggle("show_numbers", "Kanaalnummers", "Nummers voor de kanalen in lijsten.", true)
    val showEpgInLists = toggle("show_epg_lists", "Nu en straks in lijsten", "Wat er nu en straks op is bij elk kanaal.", true)
    val showLogos = toggle("show_logos", "Zenderlogo's", "Uit = alleen letters, iets sneller op trage tv's.", true)
    val showRatings = toggle("show_ratings", "Beoordelingen op posters", "Het sterretje met cijfer op films en series.", true)
    val tabOnFocus = pick("tab_on_focus", "Tabbladen openen", "Wanneer een tab bovenin opent.", "focus",
        "focus" to "Bij aanwijzen", "click" to "Pas na OK")

    // ---------- Startscherm ----------
    val showContinue = toggle("show_continue", "Verder kijken", "Rij met films en series die je nog niet af hebt.", true)
    val showRecordings = toggle("show_recordings", "Opnames", "Rij met je opnames op het startscherm.", true)
    val showStats = toggle("show_stats", "Kijktijd tonen", "Hoeveel je deze week hebt gekeken (met account).", true)
    val startup = pick("startup", "Bij openen van de app", "Wat er gebeurt als de app start.", "home",
        "home" to "Startscherm", "last" to "Laatste kanaal afspelen")

    // ---------- Speler ----------
    val okLive = pick("ok_live", "OK-knop bij live tv", "Wat OK doet tijdens het kijken. Het andere zit onder lang OK.", "list",
        "list" to "Kanalenlijst", "menu" to "Pauzemenu")
    val zapDirection = pick("zap_dir", "Pijltje omhoog", "Welke kant je op zapt met omhoog.", "next",
        "next" to "Volgend kanaal", "prev" to "Vorig kanaal")
    val zapWrap = toggle("zap_wrap", "Rondgaan in de lijst", "Na het laatste kanaal weer naar het eerste.", true)
    val infoTime = pick("info_time", "Info-balk zichtbaar", "Hoe lang de balk met programma-info blijft staan.", "5",
        "3" to "3 seconden", "5" to "5 seconden", "8" to "8 seconden", "0" to "Tot je hem wegdrukt")
    val resize = pick("resize", "Beeld", "Hoe het beeld het scherm vult.", "fit",
        "fit" to "Passend", "fill" to "Uitrekken", "zoom" to "Inzoomen")
    val seekBack = pick("seek_back", "Terugspoelen per druk", "Bij films en series.", "10",
        "5" to "5 sec", "10" to "10 sec", "15" to "15 sec", "30" to "30 sec")
    val seekForward = pick("seek_fwd", "Vooruitspoelen per druk", "Bij films en series.", "30",
        "10" to "10 sec", "15" to "15 sec", "30" to "30 sec", "60" to "1 minuut")
    val autoNext = toggle("auto_next", "Volgende aflevering", "Na een aflevering meteen de volgende afspelen.", true)
    val resumeMode = pick("resume", "Films verder kijken", "Wat er gebeurt als je een half bekeken film opent.", "ask",
        "ask" to "Vragen", "auto" to "Meteen verder", "start" to "Altijd vanaf begin")
    val playerClock = toggle("player_clock", "Klok tijdens kijken", "De tijd in de info-balk.", true)

    // ---------- Geluid en ondertitels ----------
    val audioLang = pick("audio_lang", "Voorkeurstaal geluid", "Als een film meerdere talen heeft.", "nl",
        "nl" to "Nederlands", "en" to "Engels", "de" to "Duits", "fr" to "Frans", "" to "Zoals de zender")
    val subLang = pick("sub_lang", "Ondertitels", "Ondertitels automatisch aanzetten.", "",
        "" to "Uit", "nl" to "Nederlands", "en" to "Engels")
    val subSize = pick("sub_size", "Grootte ondertitels", "Hoe groot de ondertitels zijn.", "1.0",
        "0.8" to "Klein", "1.0" to "Normaal", "1.3" to "Groot", "1.6" to "Extra groot")
    val subBackground = toggle("sub_bg", "Ondertitels met achtergrond", "Zwart vlak achter de tekst, beter leesbaar.", true)

    // ---------- Buffer en decoder ----------
    val startBuffer = pick("start_buffer", "Beginnen na", "Hoeveel beeld er binnen moet zijn voor het begint. Korter = sneller zappen.", "1000",
        "500" to "0,5 sec (snelst)", "1000" to "1 sec", "2500" to "2,5 sec", "5000" to "5 sec (stabielst)")
    val maxBuffer = pick("max_buffer", "Buffer maximaal", "Hoeveel er vooruit geladen wordt. Meer = minder haperen.", "50000",
        "15000" to "15 sec", "30000" to "30 sec", "50000" to "50 sec", "120000" to "2 minuten")
    val rebuffer = pick("rebuffer", "Na haperen verder na", "Hoeveel er binnen moet zijn voor het na een hapering verdergaat.", "2500",
        "1000" to "1 sec", "2500" to "2,5 sec", "5000" to "5 sec", "10000" to "10 sec")
    val retries = pick("retries", "Opnieuw proberen bij fouten", "Zo vaak probeert de speler het opnieuw.", "3",
        "0" to "Niet", "3" to "3 keer", "5" to "5 keer", "10" to "10 keer")
    val hlsFallback = toggle("hls_fallback", "Automatisch HLS proberen", "Als een kanaal niet wil, het andere formaat proberen.", true)
    val decoderFallback = toggle("decoder_fallback", "Andere decoder bij fouten", "Probeert een andere video-decoder als de eerste het niet kan.", true)
    val tunneling = toggle("tunneling", "Getunneld afspelen", "Beter beeld/geluid-synchroon op sommige tv's. Uitzetten als het beeld zwart blijft.", false)
    val softwareAudio = toggle("sw_audio", "Geluid zelf omzetten", "Aan als je soundbar geen Dolby snapt; dan wordt het stereo.", false)

    // ---------- Netwerk ----------
    val userAgent = pick("user_agent", "Identiteit naar de server", "Sommige providers blokkeren onbekende apps.", "IPTIMTV/1.0",
        "IPTIMTV/1.0" to "IPTIMTV", "VLC/3.0.20 LibVLC/3.0.20" to "VLC", "Kodi/21.0" to "Kodi",
        "TiviMate/5.0" to "TiviMate", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/124.0 Safari/537.36" to "Chrome")
    val timeout = pick("timeout", "Wachten op de server", "Hoe lang de app wacht voor hij opgeeft.", "15",
        "10" to "10 sec", "15" to "15 sec", "30" to "30 sec", "60" to "1 minuut")
    val catalogRefresh = pick("catalog_refresh", "Kanalen en films verversen", "Hoe vaak de lijsten opnieuw worden opgehaald.", "12", *hours(6, 12, 24, 72))

    // ---------- Gids ----------
    val epgRefresh = pick("epg_refresh", "Gids verversen", "Hoe vaak de programmagids wordt opgehaald.", "12", *hours(6, 12, 24))
    val epgAhead = pick("epg_ahead", "Gids vooruit", "Hoeveel dagen vooruit de gids bewaard wordt.", "2",
        "1" to "1 dag", "2" to "2 dagen", "3" to "3 dagen", "7" to "Een week")
    val epgBack = pick("epg_back", "Gids terug", "Hoeveel dagen terug, voor terugkijken.", "2",
        "0" to "Alleen vandaag", "1" to "1 dag", "2" to "2 dagen", "3" to "3 dagen", "7" to "Een week")
    val epgShift = pick("epg_shift", "Tijdcorrectie gids", "Als de gids een uur verschoven loopt.", "0",
        "-2" to "-2 uur", "-1" to "-1 uur", "0" to "Geen", "1" to "+1 uur", "2" to "+2 uur")
    val catchupShift = pick("catchup_shift", "Tijdcorrectie terugkijken", "Als terugkijken op het verkeerde moment begint.", "0",
        "-2" to "-2 uur", "-1" to "-1 uur", "0" to "Geen", "1" to "+1 uur", "2" to "+2 uur")

    // ---------- Opnemen ----------
    val recordPre = pick("rec_pre", "Eerder beginnen", "Opname begint zoveel eerder dan het programma.", "1",
        "0" to "Niet", "1" to "1 minuut", "2" to "2 minuten", "5" to "5 minuten")
    val recordPost = pick("rec_post", "Later stoppen", "Opname loopt zoveel door na het programma.", "5",
        "0" to "Niet", "2" to "2 minuten", "5" to "5 minuten", "10" to "10 minuten", "15" to "15 minuten")
    val recordDefault = pick("rec_default", "Nu opnemen duurt", "Als je tijdens het kijken op opnemen drukt.", "program",
        "program" to "Tot einde programma", "30" to "30 minuten", "60" to "1 uur", "120" to "2 uur")

    // ---------- Updates en account ----------
    val autoUpdate = toggle("auto_update", "Automatisch op updates letten", "Meldt een nieuwe versie zodra die op GitHub staat.", true)
    val syncHistory = toggle("sync_history", "Kijkgeschiedenis bewaren in account", "Zo zie je op elk apparaat waar je was.", true)
    val countLive = toggle("count_live", "Live tv meetellen", "Ook live kijken telt mee voor je kijktijd.", true)

    val sections = listOf(
        Section("Weergave", "Grootte, animaties en wat je ziet", listOf(uiScale, animations, showClock, showNumbers, showEpgInLists, showLogos, showRatings, tabOnFocus)),
        Section("Startscherm", "Wat er op Start staat", listOf(showContinue, showRecordings, showStats, startup)),
        Section("Speler", "Bediening tijdens het kijken", listOf(okLive, zapDirection, zapWrap, infoTime, resize, seekBack, seekForward, autoNext, resumeMode, playerClock)),
        Section("Geluid en ondertitels", "Talen en leesbaarheid", listOf(audioLang, subLang, subSize, subBackground)),
        Section("Buffer en decoder", "Sneller zappen of minder haperen", listOf(startBuffer, maxBuffer, rebuffer, retries, hlsFallback, decoderFallback, tunneling, softwareAudio)),
        Section("Netwerk", "Verbinding met je provider", listOf(userAgent, timeout, catalogRefresh)),
        Section("Gids en terugkijken", "Programmagids en tijden", listOf(epgRefresh, epgAhead, epgBack, epgShift, catchupShift)),
        Section("Opnemen", "Hoe opnames worden gemaakt", listOf(recordPre, recordPost, recordDefault)),
        Section("Account en updates", "Synchroniseren en nieuwe versies", listOf(syncHistory, countLive, autoUpdate)),
    )

    private val all get() = sections.flatMap { it.settings }

    fun init(context: Context) {
        prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        all.forEach { it.value = prefs.getString(it.key, it.default) ?: it.default }
    }

    fun set(s: Setting, value: String) {
        s.value = value
        prefs.edit().putString(s.key, value).apply()
    }

    fun resetAll() {
        prefs.edit().clear().apply()
        all.forEach { it.value = it.default }
    }
}
