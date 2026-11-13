package de.troetenrepublik.app.plattform

import de.troetenrepublik.app.i18n.I18n
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

// Netzzugriffe der Desktop-Teile (Logos, Artikeltext). Kurse und News laufen wie auf Android ueber MarketApi.
internal object Netz {
    // BROWSER: Kennung fuer Artikeltexte und Downloads aus dem Browser. Alle anderen Abrufe nutzen MarketApi.BROWSER_UA
    // (Ersatzabrufe.KENNUNG ist dieselbe Chrome-Kennung). Keine Angaben zum Nutzer.
    val BROWSER: String get() = de.troetenrepublik.app.market.Browserkennung.windows()

    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .cache(Cache(File(Plattform.daten, "cache/netz"), 50L * 1024 * 1024))
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .followRedirects(true)
            .addNetworkInterceptor(de.troetenrepublik.app.market.OhneAdresse.sperre)
            .build()
    }

    // null bei Fehler, hoechstens max Bytes.
    fun bytes(url: String, max: Int = 4 * 1024 * 1024, kennung: String = de.troetenrepublik.app.market.Ersatzabrufe.KENNUNG): ByteArray? = runCatching {
        val req = Request.Builder().url(url)
            .header("User-Agent", kennung)
            .header("Accept-Language", if (I18n.lang.code == "de") "de-DE,de;q=0.9,en;q=0.6" else "en-GB,en;q=0.9")
            .build()
        client.newCall(req).execute().use { r ->
            if (!r.isSuccessful) return@use null
            val body = r.body ?: return@use null
            if (body.contentLength() > max) return@use null
            body.byteStream().use { s -> s.readNBytes(max + 1).takeIf { it.size <= max } }
        }
    }.getOrNull()
}
