package nl.timvanbergen.iptimtv.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.LruCache
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 * Programmagids: de XMLTV van de provider wordt in een SQLite-database gezet,
 * zodat "nu / straks" en zoeken op programmatitel snel gaan.
 */
class Epg(context: Context, profileId: String) :
    SQLiteOpenHelper(context, "epg_$profileId.db", null, 1) {

    init {
        // Tijdens het inlezen van een nieuwe gids blijft de oude gewoon leesbaar.
        setWriteAheadLoggingEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE prog (ch TEXT NOT NULL, start INTEGER NOT NULL, stop INTEGER NOT NULL, title TEXT NOT NULL, descr TEXT)")
        db.execSQL("CREATE INDEX prog_ch ON prog (ch, stop)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    private val cache = LruCache<String, Pair<Long, List<Program>>>(2000)

    /** Huidig en volgend programma (max [count]) voor een epg-kanaal. */
    fun upcoming(epgId: String?, count: Int = 2): List<Program> {
        if (epgId.isNullOrBlank()) return emptyList()
        val key = epgId.lowercase()
        val now = System.currentTimeMillis()
        cache.get(key)?.let { (until, list) -> if (now < until && list.size >= count) return list.take(count) }
        val out = ArrayList<Program>()
        readableDatabase.rawQuery(
            "SELECT start, stop, title, descr FROM prog WHERE ch = ? AND stop > ? ORDER BY start LIMIT ?",
            arrayOf(key, now.toString(), maxOf(count, 2).toString()),
        ).use { c ->
            while (c.moveToNext()) out += Program(key, c.getLong(0), c.getLong(1), c.getString(2), c.getString(3))
        }
        // Geldig tot het eerste programma afloopt (of een minuut als er niets is).
        cache.put(key, (out.firstOrNull()?.stop ?: (now + 60_000)) to out)
        return out.take(count)
    }

    fun search(query: String, limit: Int = 60): List<Program> {
        val out = ArrayList<Program>()
        val like = "%" + query.replace("%", "").replace("_", "") + "%"
        readableDatabase.rawQuery(
            "SELECT ch, start, stop, title, descr FROM prog WHERE stop > ? AND title LIKE ? ORDER BY start LIMIT ?",
            arrayOf(System.currentTimeMillis().toString(), like, limit.toString()),
        ).use { c ->
            while (c.moveToNext()) out += Program(c.getString(0), c.getLong(1), c.getLong(2), c.getString(3), c.getString(4))
        }
        return out
    }

    fun isEmpty(): Boolean = readableDatabase.rawQuery("SELECT 1 FROM prog LIMIT 1", null).use { !it.moveToFirst() }

    /**
     * Leest een XMLTV-stream in. Alleen kanalen die we kennen en programma's van
     * twee uur geleden tot twee dagen vooruit worden bewaard.
     */
    fun import(input: InputStream, wanted: Set<String>) {
        val now = System.currentTimeMillis()
        val from = now - 2 * 3600_000
        val until = now + 48 * 3600_000
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("prog", null, null)
            val insert = db.compileStatement("INSERT INTO prog (ch, start, stop, title, descr) VALUES (?, ?, ?, ?, ?)")
            val parser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(input, null)
            var ch: String? = null
            var start = 0L
            var stop = 0L
            var title: String? = null
            var desc: String? = null
            var keep = false
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> when (parser.name) {
                        "programme" -> {
                            ch = parser.getAttributeValue(null, "channel")?.lowercase()
                            start = parseTime(parser.getAttributeValue(null, "start"))
                            stop = parseTime(parser.getAttributeValue(null, "stop"))
                            keep = ch != null && ch in wanted && stop > from && start < until
                            title = null; desc = null
                        }
                        "title" -> if (keep && title == null) title = parser.nextText()
                        "desc" -> if (keep && desc == null) desc = parser.nextText()
                    }
                    XmlPullParser.END_TAG -> if (parser.name == "programme" && keep && title != null) {
                        insert.bindString(1, ch)
                        insert.bindLong(2, start)
                        insert.bindLong(3, stop)
                        insert.bindString(4, title!!.trim())
                        if (desc != null) insert.bindString(5, desc!!.trim()) else insert.bindNull(5)
                        insert.executeInsert()
                        keep = false
                    }
                }
                event = parser.next()
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
            cache.evictAll()
        }
    }

    companion object {
        /** XMLTV-tijd: "20261031203000 +0100" (de tijdzone mag ontbreken, dan is het UTC). */
        fun parseTime(s: String?): Long {
            if (s == null || s.length < 12) return 0
            return try {
                val dt = LocalDateTime.of(
                    s.substring(0, 4).toInt(), s.substring(4, 6).toInt(), s.substring(6, 8).toInt(),
                    s.substring(8, 10).toInt(), s.substring(10, 12).toInt(),
                    if (s.length >= 14 && s[12].isDigit()) s.substring(12, 14).toInt() else 0,
                )
                val tz = s.substringAfter(' ', "").trim()
                val offset = if (tz.length >= 5 && (tz[0] == '+' || tz[0] == '-')) {
                    val sign = if (tz[0] == '-') -1 else 1
                    ZoneOffset.ofHoursMinutes(sign * tz.substring(1, 3).toInt(), sign * tz.substring(3, 5).toInt())
                } else ZoneOffset.UTC
                dt.toEpochSecond(offset) * 1000
            } catch (_: Exception) {
                0
            }
        }
    }
}
