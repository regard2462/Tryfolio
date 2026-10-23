package de.troetenrepublik.app.plattform

import de.troetenrepublik.app.market.MarketApi
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.ConnectionSpec
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager
import kotlin.coroutines.resume

// Netzzugang nur fuer Broker, getrennt von allen anderen Abrufen der App. Nur HTTPS mit TLS 1.2 oder 1.3 und nur an
// die Hosts des verbundenen Brokers. Keine Weiterleitungen (sonst wanderten eigene Kopfzeilen mit), keine Cookies,
// kein Cache, keine stille Wiederholung nach Verbindungsfehlern (eine Order koennte doppelt ankommen).
// Einem lokalen Gateway mit eigenem Zertifikat (Interactive Brokers) wird nur mit dem SHA-256-Fingerabdruck vertraut,
// den der Nutzer bestaetigt hat.
// Gegenstueck fuer Windows und Linux unter demselben Namen; im Browser gibt es keine Broker.
object BrokerNetz {
    // Anfrage abgewiesen, weil Adresse oder Host nicht erlaubt sind (kein Netzverkehr).
    const val UNSICHER = -1

    private val basis: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .followRedirects(false)
            .followSslRedirects(false)
            .retryOnConnectionFailure(false)
            .connectionSpecs(listOf(ConnectionSpec.MODERN_TLS))
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .callTimeout(60, TimeUnit.SECONDS)
            .addNetworkInterceptor(de.troetenrepublik.app.market.Zertifikatssperre.pruefung)
            .build()
    }

    // Hoechstens max Bytes lesen; readNBytes gibt es auf Android erst ab Version 13.
    private fun lesen(s: java.io.InputStream, max: Int): ByteArray {
        val aus = java.io.ByteArrayOutputStream()
        val puffer = ByteArray(16 * 1024)
        while (aus.size() < max) {
            val n = s.read(puffer, 0, minOf(puffer.size, max - aus.size()))
            if (n < 0) break
            aus.write(puffer, 0, n)
        }
        return aus.toByteArray()
    }

    private val gepinnt = java.util.concurrent.ConcurrentHashMap<String, OkHttpClient>()

    private fun fingerabdruck(c: X509Certificate): String =
        MessageDigest.getInstance("SHA-256").digest(c.encoded).joinToString(":") { "%02X".format(it) }

    // Client, der nur das Zertifikat mit dem Fingerabdruck pin annimmt (selbstsigniertes Gateway).
    private fun clientMitPin(pin: String): OkHttpClient = gepinnt.getOrPut(pin) {
        val tm = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = throw CertificateException()
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
                val leaf = chain?.firstOrNull() ?: throw CertificateException("no certificate")
                if (!fingerabdruck(leaf).equals(pin, ignoreCase = true)) throw CertificateException("certificate changed")
            }
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        }
        val ssl = SSLContext.getInstance("TLS").apply { init(null, arrayOf(tm), null) }
        basis.newBuilder()
            .sslSocketFactory(ssl.socketFactory, tm)
            // Der Name im Zertifikat des Gateways passt selten zur Adresse; geprueft wird der Fingerabdruck
            .hostnameVerifier { _, _ -> true }
            .connectionSpecs(listOf(ConnectionSpec.MODERN_TLS, ConnectionSpec.COMPATIBLE_TLS))
            .build()
    }

    // Fingerabdruck des Zertifikats, das url gerade vorzeigt, damit der Nutzer ihn vor dem Vertrauen bestaetigt.
    // null ohne Verbindung.
    suspend fun zertifikat(url: String): String? = suspendCancellableCoroutine { k ->
        var gesehen: String? = null
        val tm = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = throw CertificateException()
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
                gesehen = chain?.firstOrNull()?.let { fingerabdruck(it) }
                // Nur ansehen, nicht verbinden
                throw CertificateException("inspect only")
            }
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        }
        val ssl = SSLContext.getInstance("TLS").apply { init(null, arrayOf(tm), null) }
        val c = basis.newBuilder().sslSocketFactory(ssl.socketFactory, tm).hostnameVerifier { _, _ -> true }
            .connectionSpecs(listOf(ConnectionSpec.MODERN_TLS, ConnectionSpec.COMPATIBLE_TLS)).build()
        val req = runCatching { Request.Builder().url(url).build() }.getOrNull()
        if (req == null) { k.resume(null); return@suspendCancellableCoroutine }
        val call = c.newCall(req)
        k.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { if (k.isActive) k.resume(gesehen) }
            override fun onResponse(call: Call, response: Response) { response.close(); if (k.isActive) k.resume(gesehen) }
        })
    }

    // Anfrage an einen Broker. hosts: erlaubte Hosts; pin: Fingerabdruck fuer ein Gateway mit eigenem Zertifikat.
    // Antwort mit Status und Text (auch bei Fehlern); Status UNSICHER, wenn die Adresse nicht erlaubt ist.
    suspend fun anfrage(
        url: String,
        hosts: Set<String>,
        kopf: Map<String, String> = emptyMap(),
        methode: String = "GET",
        json: String? = null,
        formular: List<Pair<String, String>>? = null,
        formText: String? = null,
        max: Int = 30_000_000,
        pin: String? = null,
    ): MarketApi.Antwort {
        val u = url.toHttpUrlOrNull() ?: return MarketApi.Antwort(UNSICHER, null)
        if (u.scheme != "https" || u.host.lowercase() !in hosts) return MarketApi.Antwort(UNSICHER, null)
        val koerper = when {
            formular != null -> FormBody.Builder().apply { formular.forEach { (k, v) -> add(k, v) } }.build()
            json != null -> json.toRequestBody("application/json; charset=utf-8".toMediaType())
            formText != null -> formText.toRequestBody("application/x-www-form-urlencoded".toMediaType())
            else -> null
        }
        val req = runCatching {
            Request.Builder().url(u)
                .header("Accept", "application/json")
                .header("User-Agent", "Tryfolio/5.9")
                .apply { kopf.forEach { (k, v) -> header(k, v) } }
                // Mit Koerper nie GET (OkHttp lehnt das ab); ohne Koerper brauchen POST, PUT und PATCH einen leeren
                .method(if (koerper != null && methode == "GET") "POST" else methode,
                    koerper ?: if (methode == "POST" || methode == "PUT" || methode == "PATCH") ByteArray(0).toRequestBody(null) else null)
                .build()
        }.getOrNull() ?: return MarketApi.Antwort(UNSICHER, null)
        val client = if (pin != null) clientMitPin(pin) else basis
        return suspendCancellableCoroutine { k ->
            val call = client.newCall(req)
            k.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) { if (k.isActive) k.resume(MarketApi.Antwort(0, null)) }
                override fun onResponse(call: Call, response: Response) {
                    val a = response.use { r ->
                        try {
                            val bytes = r.body?.byteStream()?.use { s -> lesen(s, max + 1) }
                            MarketApi.Antwort(r.code, bytes?.takeIf { it.size <= max }?.toString(Charsets.UTF_8), r.headers.toMultimap().mapKeys { it.key.lowercase() }.mapValues { it.value.joinToString(",") })
                        } catch (_: IOException) { MarketApi.Antwort(r.code, null) }
                    }
                    if (k.isActive) k.resume(a)
                }
            })
        }
    }
}
