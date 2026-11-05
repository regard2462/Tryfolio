package okhttp3

import de.troetenrepublik.app.ios.alsBytes
import de.troetenrepublik.app.ios.alsNSData
import de.troetenrepublik.app.ios.jetztMs
import java.io.IOException
import java.io.InterruptedIOException
import java.net.SocketTimeoutException
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.NSHTTPCookie
import platform.Foundation.NSHTTPCookieAcceptPolicy
import platform.Foundation.NSHTTPURLResponse
import platform.Foundation.NSMutableURLRequest
import platform.Foundation.NSURL
import platform.Foundation.NSURLErrorCancelled
import platform.Foundation.NSURLErrorTimedOut
import platform.Foundation.NSURLRequest
import platform.Foundation.NSURLRequestReloadIgnoringLocalCacheData
import platform.Foundation.NSURLResponse
import platform.Foundation.NSURLSession
import platform.Foundation.NSURLSessionConfiguration
import platform.Foundation.NSURLSessionTask
import platform.Foundation.NSURLSessionTaskDelegateProtocol
import platform.Foundation.addValue
import platform.Foundation.dataTaskWithRequest
import platform.Foundation.setHTTPBody
import platform.Foundation.setHTTPMethod
import platform.Foundation.setHTTPShouldHandleCookies
import platform.Foundation.setValue
import platform.Foundation.timeIntervalSince1970
import platform.darwin.DISPATCH_TIME_FOREVER
import platform.darwin.DISPATCH_TIME_NOW
import platform.darwin.NSObject
import platform.darwin.dispatch_semaphore_create
import platform.darwin.dispatch_semaphore_signal
import platform.darwin.dispatch_semaphore_wait
import platform.darwin.dispatch_time

/**
 * Der eigentliche Abruf ueber NSURLSession, synchron fuer Call.execute: Der Thread wartet, bis die Antwort ganz da ist.
 * Ohne Cookie-Speicher und ohne Zwischenspeicher des Systems; Cookies verwaltet der CookieJar der App.
 */
internal object Netzwerk {
    private fun konfiguration(): NSURLSessionConfiguration = NSURLSessionConfiguration.ephemeralSessionConfiguration.apply {
        HTTPShouldSetCookies = false
        HTTPCookieAcceptPolicy = NSHTTPCookieAcceptPolicy.NSHTTPCookieAcceptPolicyNever
        HTTPCookieStorage = null
        URLCache = null
        requestCachePolicy = NSURLRequestReloadIgnoringLocalCacheData
    }

    private val mitWeiterleitung: NSURLSession by lazy { NSURLSession.sessionWithConfiguration(konfiguration()) }

    private val ohneWeiterleitung: NSURLSession by lazy {
        NSURLSession.sessionWithConfiguration(konfiguration(), delegate = OhneWeiterleitung(), delegateQueue = null)
    }

    // Weiterleitungen nicht folgen: Die Antwort mit 3xx geht an die App wie bei OkHttp mit followRedirects(false)
    private class OhneWeiterleitung : NSObject(), NSURLSessionTaskDelegateProtocol {
        override fun URLSession(
            session: NSURLSession,
            task: NSURLSessionTask,
            willPerformHTTPRedirection: NSHTTPURLResponse,
            newRequest: NSURLRequest,
            completionHandler: (NSURLRequest?) -> Unit,
        ) {
            completionHandler(null)
        }
    }

    private class Ergebnis {
        var daten: ByteArray? = null
        var antwort: NSURLResponse? = null
        var fehler: NSError? = null
    }

    fun senden(r: Request, aufruf: EchterAufruf, verbinden: Int, lesen: Int, gesamt: Int, weiterleiten: Boolean): Response {
        val adresse = NSURL.URLWithString(r.url.toString()) ?: throw IOException("invalid url")
        val anfrage = NSMutableURLRequest.requestWithURL(adresse)
        anfrage.setHTTPMethod(r.method)
        anfrage.setHTTPShouldHandleCookies(false)
        // NSURLSession kennt nur eine Wartezeit ohne Daten; sie gilt fuer Verbinden und Lesen
        anfrage.setTimeoutInterval(maxOf(verbinden, lesen).let { if (it <= 0) 60.0 else it / 1000.0 })
        for ((k, v) in r.headers) anfrage.addValue(v, forHTTPHeaderField = k)
        r.body?.let { koerper ->
            anfrage.setHTTPBody(koerper.bytes().alsNSData())
            if (r.header("Content-Type") == null) koerper.contentType()?.let { anfrage.setValue(it.toString(), forHTTPHeaderField = "Content-Type") }
        }
        val signal = dispatch_semaphore_create(0)
        val e = Ergebnis()
        val sitzung = if (weiterleiten) mitWeiterleitung else ohneWeiterleitung
        val aufgabe = sitzung.dataTaskWithRequest(anfrage) { daten: NSData?, antwort: NSURLResponse?, fehler: NSError? ->
            e.daten = daten?.alsBytes()
            e.antwort = antwort
            e.fehler = fehler
            dispatch_semaphore_signal(signal)
        }
        aufruf.laufend = { aufgabe.cancel() }
        if (aufruf.abgebrochen) throw IOException("Canceled")
        aufgabe.resume()
        val frist = if (gesamt > 0) dispatch_time(DISPATCH_TIME_NOW, gesamt * 1_000_000L) else DISPATCH_TIME_FOREVER
        if (dispatch_semaphore_wait(signal, frist) != 0L) {
            aufgabe.cancel()
            aufruf.laufend = null
            throw InterruptedIOException("timeout")
        }
        aufruf.laufend = null
        e.fehler?.let { f ->
            throw when (f.code) {
                NSURLErrorTimedOut -> SocketTimeoutException(f.localizedDescription)
                NSURLErrorCancelled -> IOException("Canceled")
                else -> IOException(f.localizedDescription)
            }
        }
        val http = e.antwort as? NSHTTPURLResponse ?: throw IOException("no http response")
        val kopf = kopfzeilen(http)
        val inhalt = e.daten ?: ByteArray(0)
        val typ = kopf["Content-Type"]?.let { MediaType.run { it.toMediaTypeOrNull() } }
        val nachricht = NSHTTPURLResponse.localizedStringForStatusCode(http.statusCode)
        return Response(r, http.statusCode.toInt(), nachricht, kopf, ResponseBody(inhalt, typ))
    }

    // Kopfzeilen der Antwort. Mehrere Set-Cookie fasst iOS mit Kommas zusammen; NSHTTPCookie trennt sie wieder.
    private fun kopfzeilen(h: NSHTTPURLResponse): Headers {
        val b = Headers.Builder()
        var kekse = false
        for ((k, v) in h.allHeaderFields) {
            val name = k as? String ?: continue
            val wert = v as? String ?: v?.toString() ?: continue
            if (name.equals("Set-Cookie", true)) { kekse = true; continue }
            b.add(name, wert)
        }
        if (kekse) {
            val url = h.URL
            if (url != null) {
                @Suppress("UNCHECKED_CAST")
                val alle = NSHTTPCookie.cookiesWithResponseHeaderFields(h.allHeaderFields as Map<Any?, *>, forURL = url)
                for (c in alle) {
                    val k = c as? NSHTTPCookie ?: continue
                    b.add("Set-Cookie", setCookie(k))
                }
            }
        }
        return b.build()
    }

    private fun setCookie(c: NSHTTPCookie): String = buildString {
        append(c.name).append('=').append(c.value)
        append("; Path=").append(c.path)
        // Domain mit fuehrendem Punkt gilt auch fuer Unterdomains; ohne Punkt nur fuer den Host
        if (c.domain.startsWith(".")) append("; Domain=").append(c.domain.removePrefix("."))
        c.expiresDate?.let { d -> append("; Max-Age=").append(((d.timeIntervalSince1970 * 1000.0).toLong() - jetztMs()) / 1000) }
        if (c.isSecure()) append("; Secure")
        if (c.isHTTPOnly()) append("; HttpOnly")
    }
}
