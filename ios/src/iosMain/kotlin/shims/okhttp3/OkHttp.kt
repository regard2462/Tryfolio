package okhttp3

import java.io.ByteArrayInputStream
import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.nio.charset.Charset
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.launch

/*
 * Ersatz fuer OkHttp auf iOS mit den Teilen, die der gemeinsame Code nutzt (MarketApi, Quellen, OhneAdresse,
 * Zertifikatssperre). Ablauf wie in OkHttp: Anwendungs-Interceptoren, dann Cookies aus dem CookieJar, dann
 * Netzwerk-Interceptoren, dann der Abruf ueber NSURLSession (Netzwerk.kt). Antworten liegen ganz im Speicher.
 */

object OkHttp {
    const val VERSION = "4.12.0"
}

class OkHttpClient private constructor(b: Builder) {
    constructor() : this(Builder())

    val connectTimeoutMillis: Int = b.connect
    val readTimeoutMillis: Int = b.read
    val callTimeoutMillis: Int = b.call
    val followRedirects: Boolean = b.redirects
    val cookieJar: CookieJar = b.jar
    val interceptors: List<Interceptor> = b.anwendung.toList()
    val networkInterceptors: List<Interceptor> = b.netz.toList()

    fun newCall(request: Request): Call = EchterAufruf(this, request)

    fun newBuilder(): Builder = Builder().also {
        it.connect = connectTimeoutMillis; it.read = readTimeoutMillis; it.call = callTimeoutMillis
        it.redirects = followRedirects; it.jar = cookieJar
        it.anwendung += interceptors; it.netz += networkInterceptors
    }

    class Builder {
        internal var connect = 10_000
        internal var read = 10_000
        internal var call = 0
        internal var redirects = true
        internal var jar: CookieJar = CookieJar.NO_COOKIES
        internal val anwendung = ArrayList<Interceptor>()
        internal val netz = ArrayList<Interceptor>()

        fun connectTimeout(timeout: Long, unit: TimeUnit) = apply { connect = unit.toMillis(timeout).toInt() }
        fun readTimeout(timeout: Long, unit: TimeUnit) = apply { read = unit.toMillis(timeout).toInt() }
        fun writeTimeout(timeout: Long, unit: TimeUnit) = apply {}
        fun callTimeout(timeout: Long, unit: TimeUnit) = apply { call = unit.toMillis(timeout).toInt() }
        fun followRedirects(followRedirects: Boolean) = apply { redirects = followRedirects }
        fun followSslRedirects(followProtocolRedirects: Boolean) = apply {}
        fun retryOnConnectionFailure(retryOnConnectionFailure: Boolean) = apply {}
        fun cookieJar(cookieJar: CookieJar) = apply { jar = cookieJar }
        fun addInterceptor(interceptor: Interceptor) = apply { anwendung += interceptor }
        fun addNetworkInterceptor(interceptor: Interceptor) = apply { netz += interceptor }
        // TLS 1.2 und 1.3 erzwingt iOS selbst (App Transport Security)
        fun connectionSpecs(connectionSpecs: List<ConnectionSpec>) = apply {}
        fun cache(cache: Cache?) = apply {}
        fun build(): OkHttpClient = OkHttpClient(this)
    }
}

class ConnectionSpec private constructor(val name: String) {
    companion object {
        val MODERN_TLS = ConnectionSpec("MODERN_TLS")
        val COMPATIBLE_TLS = ConnectionSpec("COMPATIBLE_TLS")
    }
}

/** Ohne Wirkung auf iOS; die App haelt keinen eigenen Zwischenspeicher fuer Abrufe. */
class Cache(val directory: java.io.File, val maxSize: Long)

interface Callback {
    fun onFailure(call: Call, e: IOException)
    fun onResponse(call: Call, response: Response)
}

interface Call {
    fun request(): Request
    fun execute(): Response
    fun enqueue(responseCallback: Callback)
    fun cancel()
    fun isExecuted(): Boolean
    fun isCanceled(): Boolean
}

fun interface Interceptor {
    fun intercept(chain: Chain): Response

    interface Chain {
        fun request(): Request
        fun proceed(request: Request): Response
        fun connection(): Connection?
        fun call(): Call
        fun connectTimeoutMillis(): Int
        fun readTimeoutMillis(): Int
        fun writeTimeoutMillis(): Int
        fun withConnectTimeout(timeout: Int, unit: TimeUnit): Chain
        fun withReadTimeout(timeout: Int, unit: TimeUnit): Chain
        fun withWriteTimeout(timeout: Int, unit: TimeUnit): Chain
    }
}

/** Auf iOS gibt NSURLSession die Zertifikatskette nicht an die Interceptoren weiter; connection() ist null. */
interface Connection {
    fun handshake(): Handshake?
}

class Handshake(val peerCertificates: List<java.security.cert.Certificate>)

class Headers private constructor(private val paare: List<Pair<String, String>>) : Iterable<Pair<String, String>> {
    val size: Int get() = paare.size
    fun name(index: Int): String = paare[index].first
    fun value(index: Int): String = paare[index].second
    operator fun get(name: String): String? = paare.lastOrNull { it.first.equals(name, true) }?.second
    fun values(name: String): List<String> = paare.filter { it.first.equals(name, true) }.map { it.second }
    fun names(): Set<String> = paare.mapTo(LinkedHashSet()) { it.first }
    fun toMultimap(): Map<String, List<String>> {
        val m = LinkedHashMap<String, MutableList<String>>()
        for ((k, v) in paare) m.getOrPut(k.lowercase()) { ArrayList() } += v
        return m
    }
    fun newBuilder(): Builder = Builder().also { it.paare += paare }
    override fun iterator(): Iterator<Pair<String, String>> = paare.iterator()
    override fun toString(): String = paare.joinToString("\n") { "${it.first}: ${it.second}" }

    class Builder {
        internal val paare = ArrayList<Pair<String, String>>()
        fun add(name: String, value: String) = apply { paare += name to value }
        fun set(name: String, value: String) = apply { removeAll(name); add(name, value) }
        fun removeAll(name: String) = apply { paare.removeAll { it.first.equals(name, true) } }
        operator fun get(name: String): String? = paare.lastOrNull { it.first.equals(name, true) }?.second
        fun build(): Headers = Headers(paare.toList())
    }

    companion object {
        fun headersOf(vararg namesAndValues: String): Headers =
            Headers(namesAndValues.toList().chunked(2).map { it[0] to it[1] })
    }
}

class MediaType private constructor(private val text: String, val type: String, val subtype: String, private val zeichensatz: String?) {
    fun charset(defaultValue: Charset? = null): Charset? =
        zeichensatz?.let { runCatching { Charset.forName(it) }.getOrNull() } ?: defaultValue

    override fun toString(): String = text
    override fun equals(other: Any?): Boolean = other is MediaType && other.text == text
    override fun hashCode(): Int = text.hashCode()

    companion object {
        fun String.toMediaType(): MediaType = toMediaTypeOrNull() ?: throw IllegalArgumentException("No subtype found for: \"$this\"")

        fun String.toMediaTypeOrNull(): MediaType? {
            val teile = split(';').map { it.trim() }
            val art = teile.firstOrNull()?.split('/') ?: return null
            if (art.size != 2 || art[0].isBlank() || art[1].isBlank()) return null
            val cs = teile.drop(1).firstOrNull { it.startsWith("charset=", true) }?.substringAfter('=')?.trim('"', ' ')
            return MediaType(this, art[0].lowercase(), art[1].lowercase(), cs)
        }

        fun get(string: String): MediaType = string.toMediaType()
        fun parse(string: String): MediaType? = string.toMediaTypeOrNull()
    }
}

abstract class RequestBody {
    abstract fun contentType(): MediaType?
    open fun contentLength(): Long = bytes().size.toLong()
    internal abstract fun bytes(): ByteArray

    companion object {
        fun String.toRequestBody(contentType: MediaType? = null): RequestBody = encodeToByteArray().toRequestBody(contentType)

        fun ByteArray.toRequestBody(contentType: MediaType? = null, offset: Int = 0, byteCount: Int = size): RequestBody {
            val inhalt = copyOfRange(offset, offset + byteCount)
            return object : RequestBody() {
                override fun contentType(): MediaType? = contentType
                override fun bytes(): ByteArray = inhalt
            }
        }
    }
}

class FormBody private constructor(private val namen: List<String>, private val werte: List<String>) : RequestBody() {
    val size: Int get() = namen.size
    fun name(index: Int): String = namen[index]
    fun value(index: Int): String = werte[index]

    override fun contentType(): MediaType? = TYP
    override fun bytes(): ByteArray = namen.indices.joinToString("&") { kodieren(namen[it]) + "=" + kodieren(werte[it]) }.encodeToByteArray()

    class Builder {
        private val namen = ArrayList<String>()
        private val werte = ArrayList<String>()
        fun add(name: String, value: String) = apply { namen += name; werte += value }
        fun addEncoded(name: String, value: String) = apply { namen += name; werte += value }
        fun build(): FormBody = FormBody(namen.toList(), werte.toList())
    }

    private companion object {
        val TYP = MediaType.run { "application/x-www-form-urlencoded".toMediaType() }

        // Wie OkHttp: alles ausser Buchstaben, Ziffern und - . _ * prozentkodiert (Leerzeichen als %20)
        fun kodieren(s: String): String {
            val sb = StringBuilder()
            for (b in s.encodeToByteArray()) {
                val c = b.toInt() and 0xFF
                val ch = c.toChar()
                if (ch in 'A'..'Z' || ch in 'a'..'z' || ch in '0'..'9' || ch == '-' || ch == '.' || ch == '_' || ch == '*') sb.append(ch)
                else sb.append('%').append("0123456789ABCDEF"[c shr 4]).append("0123456789ABCDEF"[c and 15])
            }
            return sb.toString()
        }
    }
}

class Request private constructor(
    val url: HttpUrl,
    val method: String,
    val headers: Headers,
    val body: RequestBody?,
) {
    fun header(name: String): String? = headers[name]
    fun headers(name: String): List<String> = headers.values(name)
    fun newBuilder(): Builder = Builder().also { it.adresse = url; it.methode = method; it.kopf = headers.newBuilder(); it.koerper = body }
    override fun toString(): String = "Request{method=$method, url=$url}"

    class Builder {
        internal var adresse: HttpUrl? = null
        internal var methode = "GET"
        internal var kopf = Headers.Builder()
        internal var koerper: RequestBody? = null

        fun url(url: String): Builder = url(HttpUrl.run { url.toHttpUrl() })
        fun url(url: HttpUrl) = apply { adresse = url }
        fun header(name: String, value: String) = apply { kopf.set(name, value) }
        fun addHeader(name: String, value: String) = apply { kopf.add(name, value) }
        fun removeHeader(name: String) = apply { kopf.removeAll(name) }
        fun headers(headers: Headers) = apply { kopf = headers.newBuilder() }
        fun get() = method("GET", null)
        fun head() = method("HEAD", null)
        fun post(body: RequestBody) = method("POST", body)
        fun put(body: RequestBody) = method("PUT", body)
        fun patch(body: RequestBody) = method("PATCH", body)
        fun delete(body: RequestBody? = null) = method("DELETE", body)

        fun method(method: String, body: RequestBody?) = apply {
            require(method.isNotEmpty()) { "method.isEmpty() == true" }
            if (body != null) require(method != "GET" && method != "HEAD") { "method $method must not have a request body." }
            if (body == null) require(method != "POST" && method != "PUT" && method != "PATCH") { "method $method must have a request body." }
            methode = method
            koerper = body
        }

        fun build(): Request = Request(adresse ?: throw IllegalStateException("url == null"), methode, kopf.build(), koerper)
    }
}

class ResponseBody internal constructor(private val inhalt: ByteArray, private val typ: MediaType?) : Closeable {
    fun contentType(): MediaType? = typ
    fun contentLength(): Long = inhalt.size.toLong()
    fun byteStream(): InputStream = ByteArrayInputStream(inhalt)
    fun bytes(): ByteArray = inhalt
    fun string(): String = de.troetenrepublik.app.ios.jvm.dekodieren(inhalt, typ?.charset() ?: Charset.forName("UTF-8"))
    override fun close() {}
}

class Response internal constructor(
    val request: Request,
    val code: Int,
    val message: String,
    val headers: Headers,
    val body: ResponseBody?,
) : Closeable {
    val isSuccessful: Boolean get() = code in 200..299
    val isRedirect: Boolean get() = code in 300..399
    fun header(name: String, defaultValue: String? = null): String? = headers[name] ?: defaultValue
    fun headers(name: String): List<String> = headers.values(name)
    fun code(): Int = code
    override fun close() {}
    override fun toString(): String = "Response{code=$code, url=${request.url}}"
}

interface CookieJar {
    fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>)
    fun loadForRequest(url: HttpUrl): List<Cookie>

    companion object {
        val NO_COOKIES: CookieJar = object : CookieJar {
            override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {}
            override fun loadForRequest(url: HttpUrl): List<Cookie> = emptyList()
        }
    }
}

/** Ein Aufruf: Interceptoren, Cookies, Abruf. Abbrechen beendet einen laufenden Abruf. */
internal class EchterAufruf(private val client: OkHttpClient, private val anfrage: Request) : Call {
    private var ausgefuehrt = false
    internal var abgebrochen = false
        private set
    internal var laufend: (() -> Unit)? = null

    override fun request(): Request = anfrage
    override fun isExecuted(): Boolean = ausgefuehrt
    override fun isCanceled(): Boolean = abgebrochen

    override fun cancel() {
        abgebrochen = true
        laufend?.invoke()
    }

    override fun execute(): Response {
        check(!ausgefuehrt) { "Already Executed" }
        ausgefuehrt = true
        return Kette(0, anfrage, client.connectTimeoutMillis, client.readTimeoutMillis).proceed(anfrage)
    }

    override fun enqueue(responseCallback: Callback) {
        @OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
        kotlinx.coroutines.GlobalScope.launch(de.troetenrepublik.app.ios.ioDispatcher) {
            val r = try {
                execute()
            } catch (e: IOException) {
                responseCallback.onFailure(this@EchterAufruf, e)
                return@launch
            } catch (e: Exception) {
                responseCallback.onFailure(this@EchterAufruf, IOException(e.message, e))
                return@launch
            }
            responseCallback.onResponse(this@EchterAufruf, r)
        }
    }

    // Erst die Anwendungs-Interceptoren, dann die Netzwerk-Interceptoren, dazwischen die Cookies
    private inner class Kette(private val stufe: Int, private val anfrage: Request, private val verbinden: Int, private val lesen: Int) : Interceptor.Chain {
        override fun request(): Request = anfrage
        override fun connection(): Connection? = null
        override fun call(): Call = this@EchterAufruf
        override fun connectTimeoutMillis(): Int = verbinden
        override fun readTimeoutMillis(): Int = lesen
        override fun writeTimeoutMillis(): Int = lesen
        override fun withConnectTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain = Kette(stufe, anfrage, unit.toMillis(timeout.toLong()).toInt(), lesen)
        override fun withReadTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain = Kette(stufe, anfrage, verbinden, unit.toMillis(timeout.toLong()).toInt())
        override fun withWriteTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain = this

        override fun proceed(request: Request): Response {
            if (abgebrochen) throw IOException("Canceled")
            val anwendung = client.interceptors
            val netz = client.networkInterceptors
            return when {
                stufe < anwendung.size -> anwendung[stufe].intercept(Kette(stufe + 1, request, verbinden, lesen))
                stufe == anwendung.size -> {
                    // Cookies wie die BridgeInterceptor-Stufe von OkHttp
                    val kekse = client.cookieJar.loadForRequest(request.url)
                    val mit = if (kekse.isEmpty() || request.header("Cookie") != null) request
                    else request.newBuilder().header("Cookie", kekse.joinToString("; ") { "${it.name}=${it.value}" }).build()
                    val antwort = Kette(stufe + 1, mit, verbinden, lesen).proceed(mit)
                    val neu = Cookie.parseAll(request.url, antwort.headers)
                    if (neu.isNotEmpty()) client.cookieJar.saveFromResponse(request.url, neu)
                    antwort
                }
                stufe - anwendung.size - 1 < netz.size -> netz[stufe - anwendung.size - 1].intercept(Kette(stufe + 1, request, verbinden, lesen))
                else -> Netzwerk.senden(request, this@EchterAufruf, verbinden, lesen, client.callTimeoutMillis, client.followRedirects)
            }
        }
    }
}
