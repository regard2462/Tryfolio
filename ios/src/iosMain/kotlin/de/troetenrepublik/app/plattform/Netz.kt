package de.troetenrepublik.app.plattform

import androidx.compose.ui.platform.UriHandler
import de.troetenrepublik.app.i18n.I18n
import okhttp3.OkHttpClient
import okhttp3.Request
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import java.util.concurrent.TimeUnit

// Netzzugriffe der iOS-Teile (Logos, Artikeltext, Downloads). Kurse und News laufen wie ueberall ueber MarketApi.
internal object Netz {
    // BROWSER: Kennung fuer Artikeltexte und Downloads aus dem Browser. Logos und andere Abrufe der App melden sich mit
    // Ersatzabrufe.KENNUNG; Yahoo, CNN Fear & Greed und Nasdaq nutzen MarketApi.BROWSER_UA. Keine Angaben zum Nutzer.
    val BROWSER: String get() = de.troetenrepublik.app.market.Browserkennung.ios()

    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
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
            r.body?.bytes()?.takeIf { it.size <= max }
        }
    }.getOrNull()
}

// Adressen in Safari oeffnen.
object Browser : UriHandler {
    val zuletzt = mutableListOf<String>()

    fun oeffnen(adresse: String): Boolean {
        if (!adresse.startsWith("http://", true) && !adresse.startsWith("https://", true)) return false
        val url = NSURL.URLWithString(adresse) ?: return false
        zuletzt += adresse
        UIApplication.sharedApplication.openURL(url, options = emptyMap<Any?, Any?>(), completionHandler = null)
        return true
    }

    override fun openUri(uri: String) {
        if (!oeffnen(uri)) throw IllegalStateException("Browser nicht verfuegbar")
    }
}
