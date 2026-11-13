package de.troetenrepublik.app.market

import de.troetenrepublik.app.engine.Quellenbuendel
import de.troetenrepublik.app.engine.Quellenbuendel.Fehler
import okhttp3.Authenticator
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Connection
import okhttp3.Dns
import okhttp3.EventListener
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Timeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import java.net.ProxySelector
import java.net.Proxy
import java.net.SocketAddress
import org.junit.Test
import java.io.IOException
import java.util.concurrent.TimeUnit

// Fassung 5.92: der Abfang in MarketApi mit Ersatzhosts, Pause nach 429, Abschalter, Umzug und Zaehlern.
class Fassung592QuellenAbfangTest {

    private class Anruf(private val r: Request) : Call {
        override fun request() = r
        override fun execute(): Response = throw UnsupportedOperationException()
        override fun enqueue(responseCallback: Callback) = Unit
        override fun cancel() = Unit
        override fun isExecuted() = true
        override fun isCanceled() = false
        override fun timeout(): Timeout = Timeout.NONE
        override fun clone(): Call = this
        // [0.71] Neu in OkHttp 5
        override fun addEventListener(eventListener: EventListener) = Unit
        override fun <T> tag(type: Class<out T>): T? = null
        override fun <T : Any> tag(type: kotlin.reflect.KClass<T>): T? = null
        override fun <T : Any> tag(type: kotlin.reflect.KClass<T>, computeIfAbsent: () -> T): T = computeIfAbsent()
        override fun <T : Any> tag(type: Class<T>, computeIfAbsent: () -> T): T = computeIfAbsent()
    }

    // Kette ohne Netz: antwort bestimmt je Anfrage Status und Inhalt (null wirft wie ohne Verbindung).
    private class Kette(private val anfrage: Request, private val antwort: (Request) -> Pair<Int, String>?) : Interceptor.Chain {
        val gesehen = mutableListOf<Request>()
        override fun request() = anfrage
        override fun proceed(request: Request): Response {
            gesehen += request
            val (code, text) = antwort(request) ?: throw IOException("kein Netz")
            val b = Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(code).message("x").body(text.toResponseBody())
            if (code == 429) b.header("Retry-After", "120")
            return b.build()
        }
        override fun connection(): Connection? = null
        override fun call(): Call = Anruf(anfrage)
        override fun connectTimeoutMillis() = 0
        override fun withConnectTimeout(timeout: Int, unit: TimeUnit) = this
        override fun readTimeoutMillis() = 0
        override fun withReadTimeout(timeout: Int, unit: TimeUnit) = this
        override fun writeTimeoutMillis() = 0
        override fun withWriteTimeout(timeout: Int, unit: TimeUnit) = this
        // [0.71] Neu in OkHttp 5
        override fun withDns(dns: Dns) = this
        override fun withSocketFactory(socketFactory: javax.net.SocketFactory) = this
        override fun withRetryOnConnectionFailure(retryOnConnectionFailure: Boolean) = this
        override fun withAuthenticator(authenticator: Authenticator) = this
        override fun withCookieJar(cookieJar: okhttp3.CookieJar) = this
        override fun withCache(cache: okhttp3.Cache?) = this
        override fun withProxy(proxy: java.net.Proxy?) = this
        override fun withProxySelector(proxySelector: java.net.ProxySelector) = this
        override fun withProxyAuthenticator(proxyAuthenticator: Authenticator) = this
        override fun withSslSocketFactory(sslSocketFactory: javax.net.ssl.SSLSocketFactory?, x509TrustManager: javax.net.ssl.X509TrustManager?) = this
        override fun withHostnameVerifier(hostnameVerifier: javax.net.ssl.HostnameVerifier) = this
        override fun withCertificatePinner(certificatePinner: okhttp3.CertificatePinner) = this
        override fun withConnectionPool(connectionPool: okhttp3.ConnectionPool) = this
        override val followSslRedirects: Boolean get() = true
        override val followRedirects: Boolean get() = true
        override val dns: Dns get() = Dns.SYSTEM
        override val socketFactory: javax.net.SocketFactory get() = javax.net.SocketFactory.getDefault()
        override val retryOnConnectionFailure: Boolean get() = true
        override val authenticator: Authenticator get() = Authenticator.NONE
        override val cookieJar: okhttp3.CookieJar get() = okhttp3.CookieJar.NO_COOKIES
        override val cache: okhttp3.Cache? get() = null
        override val proxy: java.net.Proxy? get() = null
        override val proxySelector: java.net.ProxySelector get() = ohneProxy()
        override val proxyAuthenticator: Authenticator get() = Authenticator.NONE
        override val sslSocketFactoryOrNull: javax.net.ssl.SSLSocketFactory? get() = null
        override val x509TrustManagerOrNull: javax.net.ssl.X509TrustManager? get() = null
        override val hostnameVerifier: javax.net.ssl.HostnameVerifier get() = javax.net.ssl.HttpsURLConnection.getDefaultHostnameVerifier()
        override val certificatePinner: okhttp3.CertificatePinner get() = okhttp3.CertificatePinner.DEFAULT
        override val connectionPool: okhttp3.ConnectionPool get() = okhttp3.ConnectionPool()
        override val eventListener: EventListener get() = EventListener.NONE
    }

    private fun get(url: String) = Request.Builder().url(url).build()

    private fun lauf(r: Request, antwort: (Request) -> Pair<Int, String>?): Pair<Kette, Response?> {
        val k = Kette(r, antwort)
        val a = try { Quellen.abfang.intercept(k) } catch (_: IOException) { null }
        return k to a
    }

    @Before fun vorher() = Quellen.fuerTest(null)
    @After fun nachher() = Quellen.fuerTest(null)

    @Test fun ersatzhostNachServerfehlerUndDanachZuerst() {
        val (k, a) = lauf(get("https://query1.finance.yahoo.com/v8/finance/chart/AAPL?range=5d")) { r ->
            if (r.url.host == "query1.finance.yahoo.com") 503 to "" else 200 to "{}"
        }
        assertEquals(200, a!!.code)
        assertEquals(listOf("query1.finance.yahoo.com", "query2.finance.yahoo.com"), k.gesehen.map { it.url.host })
        // Pfad und Abfrage bleiben
        assertEquals("/v8/finance/chart/AAPL", k.gesehen[1].url.encodedPath)
        assertEquals("5d", k.gesehen[1].url.queryParameter("range"))
        val g = Quellen.gesundheit.value["yahoo"]!!
        assertEquals(1, g.erfolge)
        assertEquals(1, g.fehler)
        assertEquals(0, g.inFolge)
        assertEquals("query2.finance.yahoo.com", g.umweg)
        // Der funktionierende Ersatz wird beim naechsten Mal zuerst gefragt
        val (k2, _) = lauf(get("https://query1.finance.yahoo.com/v7/finance/quote?symbols=X")) { 200 to "{}" }
        assertEquals(listOf("query2.finance.yahoo.com"), k2.gesehen.map { it.url.host })
    }

    @Test fun ohneVerbindungErsatzUndZeitfehler() {
        val (k, a) = lauf(get("https://query2.finance.yahoo.com/v1/x")) { null }
        assertNull(a)
        assertEquals(2, k.gesehen.size)
        val g = Quellen.gesundheit.value["yahoo"]!!
        assertEquals(2, g.inFolge)
        assertEquals(Fehler.NETZ, g.art)
    }

    @Test fun sperreWirdGeachtet() {
        val (k, a) = lauf(get("https://www.sec.gov/cgi-bin/browse-edgar")) { 429 to "" }
        assertEquals(429, a!!.code)
        assertEquals(1, k.gesehen.size)
        assertEquals(Fehler.GESPERRT, Quellen.gesundheit.value["sec"]!!.art)
        assertEquals(429, Quellen.gesundheit.value["sec"]!!.code)
        // Waehrend der Pause geht keine Anfrage hinaus
        val (k2, a2) = lauf(get("https://www.sec.gov/cgi-bin/browse-edgar")) { 200 to "ok" }
        assertNull(a2)
        assertTrue(k2.gesehen.isEmpty())
        // Andere Quellen laufen weiter
        val (_, a3) = lauf(get("https://data-api.ecb.europa.eu/service/data")) { 200 to "ok" }
        assertEquals(200, a3!!.code)
    }

    @Test fun nichtGefundenZaehltNicht() {
        val (_, a) = lauf(get("https://query1.finance.yahoo.com/v10/finance/quoteSummary/UNBEKANNT")) { 404 to "" }
        assertEquals(404, a!!.code)
        assertNull(Quellen.gesundheit.value["yahoo"])
    }

    @Test fun leereAntwortZaehltAlsFehler() {
        val (k, a) = lauf(get("https://data-api.ecb.europa.eu/service/data")) { 200 to "" }
        assertEquals(200, a!!.code)
        assertEquals(1, k.gesehen.size)
        assertEquals(Fehler.LEER, Quellen.gesundheit.value["ezb"]!!.art)
    }

    @Test fun fremdeHostsUnveraendert() {
        val r = Request.Builder().url("https://api.kraken.com/0/private/Balance").header("API-Key", "k").post("a=b".toRequestBody()).build()
        val (k, a) = lauf(r) { 503 to "" }
        assertEquals(503, a!!.code)
        assertEquals(1, k.gesehen.size)
        assertTrue(k.gesehen.single() === r)
        assertTrue(Quellen.gesundheit.value.isEmpty())
    }

    @Test fun postOhneErsatz() {
        val r = Request.Builder().url("https://query1.finance.yahoo.com/v1/x").post("a=b".toRequestBody()).build()
        val (k, a) = lauf(r) { 503 to "" }
        assertEquals(503, a!!.code)
        assertEquals(1, k.gesehen.size)
    }

    @Test fun abschalterUmzugUndKopfzeilen() {
        val b = Quellenbuendel.lesen(
            "{\"version\":2,\"sources\":{" +
                "\"sec\":{\"off\":true}," +
                "\"yahoo\":{\"moves\":{\"query1.finance.yahoo.com\":\"query3.finance.yahoo.com\"},\"paths\":{\"/v7/finance/quote\":\"/v8/finance/quote\"}," +
                "\"fallbacks\":{},\"headers\":{\"User-Agent\":\"Mozilla/5.0 Neu\",\"X-Test\":\"1\"}}}}",
        )!!
        Quellen.fuerTest(b)
        val (k, a) = lauf(get("https://www.sec.gov/x")) { 200 to "ok" }
        assertNull(a)
        assertTrue(k.gesehen.isEmpty())
        assertEquals(Fehler.AUS, Quellen.gesundheit.value["sec"]!!.art)

        val r = Request.Builder().url("https://query1.finance.yahoo.com/v7/finance/quote?symbols=A").header("X-Test", "eigen").build()
        val (k2, a2) = lauf(r) { 200 to "{}" }
        assertEquals(200, a2!!.code)
        val raus = k2.gesehen.single()
        assertEquals("query3.finance.yahoo.com", raus.url.host)
        assertEquals("/v8/finance/quote", raus.url.encodedPath)
        assertEquals("A", raus.url.queryParameter("symbols"))
        assertEquals("Mozilla/5.0 Neu", raus.header("User-Agent"))
        // Was der Abruf selbst setzt, gewinnt
        assertEquals("eigen", raus.header("X-Test"))
        // Felder fuer Leser: aus dem Buendel oder Standard
        assertEquals(listOf("a"), Quellen.felder("yahoo", "unbekannt", listOf("a")))
        assertTrue("trailingPE" in Quellen.felder("yahoo", "kennzahlen", emptyList()))
    }

    @Test fun unerwarteteAntwortWirftUndZaehlt() {
        try {
            Quellen.unerwartet("yahoo", "quoteResponse fehlt")
            fail()
        } catch (e: UnerwarteteAntwort) {
            assertEquals("yahoo", e.quelle)
        }
        assertEquals(Fehler.FORM, Quellen.gesundheit.value["yahoo"]!!.art)
    }
}

// ProxySelector ohne Vermittler (getDefault ist auf manchen Laufzeiten null)
private fun ohneProxy(): ProxySelector = object : ProxySelector() {
    override fun select(uri: java.net.URI?) = listOf(Proxy.NO_PROXY)
    override fun connectFailed(uri: java.net.URI?, sa: SocketAddress?, ioe: IOException?) = Unit
}
