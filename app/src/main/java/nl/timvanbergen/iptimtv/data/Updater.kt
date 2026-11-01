package nl.timvanbergen.iptimtv.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nl.timvanbergen.iptimtv.BuildConfig
import okhttp3.Request
import org.json.JSONObject
import java.io.File

class Release(val version: Int, val name: String, val notes: String, val apkUrl: String)

/** Kijkt op GitHub of er een nieuwere versie is en installeert die. */
object Updater {
    private const val LATEST = "https://api.github.com/repos/timmmmmmmmn/IPTIMTV/releases/latest"

    var available by mutableStateOf<Release?>(null); private set
    var downloading by mutableStateOf<Float?>(null); private set
    var status by mutableStateOf<String?>(null)
    private var lastCheck = 0L
    /** Gezet als we de gebruiker naar de toestemming sturen; daarna gaat de update vanzelf verder. */
    var resumeAfterPermission = false

    val currentVersion get() = BuildConfig.VERSION_NAME

    /** Geeft de nieuwe release terug, of null als je al de nieuwste hebt. */
    suspend fun check(force: Boolean = false): Release? = withContext(Dispatchers.IO) {
        if (!force && System.currentTimeMillis() - lastCheck < 3 * 3600_000) return@withContext available
        lastCheck = System.currentTimeMillis()
        try {
            val req = Request.Builder().url(LATEST).header("Accept", "application/vnd.github+json").build()
            val o = Net.client.newCall(req).execute().use { JSONObject(it.body!!.string()) }
            val tag = o.optString("tag_name")
            val version = tag.substringAfterLast('.').toIntOrNull() ?: return@withContext null
            val assets = o.optJSONArray("assets")
            var url: String? = null
            for (i in 0 until (assets?.length() ?: 0)) {
                val a = assets!!.getJSONObject(i)
                if (a.optString("name") == BuildConfig.APK_NAME) url = a.optString("browser_download_url")
            }
            val release = if (version > BuildConfig.VERSION_CODE && url != null)
                Release(version, o.optString("name", tag), o.optString("body").lines().filterNot { it.startsWith("Co-Authored-By") }.joinToString("\n").trim(), url) else null
            withContext(Dispatchers.Main) {
                available = release
                status = if (release == null) "Je hebt de nieuwste versie (${BuildConfig.VERSION_NAME})" else null
            }
            release
        } catch (e: Exception) {
            withContext(Dispatchers.Main) { status = "Kon niet controleren op updates" }
            null
        }
    }

    /** Downloadt de APK en opent het installatiescherm van Android. */
    suspend fun install(context: Context) {
        val release = available ?: return
        // Eerst toestemming om apps te installeren (eenmalig per app).
        if (Build.VERSION.SDK_INT >= 26 && !context.packageManager.canRequestPackageInstalls()) {
            status = "Geef IPTIMTV toestemming om updates te installeren"
            resumeAfterPermission = true
            val intent = Intent(AndroidSettings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(intent) }
            return
        }
        val file = File(context.cacheDir, "updates/update.apk").apply { parentFile?.mkdirs() }
        try {
            downloading = 0f
            withContext(Dispatchers.IO) {
                Net.client.newCall(Request.Builder().url(release.apkUrl).build()).execute().use { r ->
                    if (!r.isSuccessful) error("download ${r.code}")
                    val body = r.body!!
                    val total = body.contentLength().takeIf { it > 0 } ?: 1L
                    var done = 0L
                    body.byteStream().use { input ->
                        file.outputStream().use { out ->
                            val buf = ByteArray(64 * 1024)
                            while (true) {
                                val n = input.read(buf)
                                if (n < 0) break
                                out.write(buf, 0, n)
                                done += n
                                withContext(Dispatchers.Main) { downloading = (done.toFloat() / total).coerceIn(0f, 1f) }
                            }
                        }
                    }
                }
            }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
            context.startActivity(
                Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            status = null
        } catch (e: Exception) {
            status = "Downloaden mislukt, probeer het later opnieuw"
        } finally {
            downloading = null
        }
    }
}
