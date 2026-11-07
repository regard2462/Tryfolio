package de.troetenrepublik.app.market

import de.troetenrepublik.app.engine.Quellenbuendel
import de.troetenrepublik.app.engine.Quellenbuendel.Ausgang
import de.troetenrepublik.app.engine.Quellenbuendel.Fehler
import de.troetenrepublik.app.engine.Quellenbuendel.Gesundheit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.Interceptor
import okhttp3.Response
import java.io.File
import java.io.IOException
import java.io.InterruptedIOException
import java.net.SocketTimeoutException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

// Antwort einer Quelle in unerwarteter Form (Feld fehlt, JSON kaputt). Leser werfen sie statt still leer zu bleiben;
// gezaehlt wird sie als DRIFT der Quelle.
class UnerwarteteAntwort(val quelle: String, val was: String) : IOException("unexpected response from $quelle: $was")

// Selbstreparatur der Datenquellen (seit 5.92), Laufzeitteil. Alle Abrufe ueber MarketApi laufen durch abfang: Abschalter,
// Umzug von Hosts und Pfaden, Kopfzeilen aus dem Buendel, Ersatzhosts bei Fehlern, Pause nach 403/429 und die
// Gesundheitszaehler je Quelle. Das Buendel kommt beim Start aus dem Zwischenspeicher und danach im Hintergrund
// von den Adressen in Quellenbuendel.ADRESSEN; ohne gueltiges Buendel gilt das Eingebaute.
object Quellen {
    // Oeffentlicher Ed25519-Schluessel des Buendels. Der private liegt nur beim Herausgeber (tools/quellen/signieren.py).
    const val SCHLUESSEL = "d343f2ac7b8b0cf943d15731ac429516317f6c83a48d478bcbe99a8111206582"

    // Offene Kennung beim Abruf des Buendels: nur App und Fassung.
    private val UA = Ersatzabrufe.KENNUNG

    // Stand des Buendels fuer die Seite Datenquellen.
    data class Stand(
        val version: Long = Quellenbuendel.EINGEBAUT.version,
        val geladen: Boolean = false,
        val geprueft: Long = 0,
        // Ausgang der letzten Suche: NETZ, HTTP (mit http), OHNE_SIGNATUR, SIGNATUR, FORM, HOST, AKTUELL, NEU
        val ergebnis: String = "",
        val http: Int = 0,
    )

    @Volatile var aktiv: Quellenbuendel.Buendel = Quellenbuendel.EINGEBAUT
        private set

    private val _stand = MutableStateFlow(Stand())
    val stand: StateFlow<Stand> = _stand

    private val _gesundheit = MutableStateFlow<Map<String, Gesundheit>>(emptyMap())
    val gesundheit: StateFlow<Map<String, Gesundheit>> = _gesundheit

    private val _gesehen = MutableStateFlow<Set<String>>(emptySet())
    val gesehen: StateFlow<Set<String>> = _gesehen

    private val gestartet = AtomicBoolean(false)
    @Volatile private var ordner: File? = null
    @Volatile private var geaendert = false
    private val pausiertBis = ConcurrentHashMap<String, Long>()
    private val naechsterAbruf = ConcurrentHashMap<String, Long>()

    private fun sha512(b: ByteArray): ByteArray = java.security.MessageDigest.getInstance("SHA-512").digest(b)

    private val schluessel by lazy { Quellenbuendel.hex(SCHLUESSEL) }

    // ------------------------------------------------------------ Start und Laden

    // Einmal je Prozess: gespeichertes Buendel und Zaehler laden, dann im Hintergrund nach einem neueren Buendel fragen
    // und die Zaehler regelmaessig sichern.
    fun starten(datenOrdner: File, scope: CoroutineScope) {
        if (!gestartet.compareAndSet(false, true)) return
        val o = File(datenOrdner, "quellen")
        ordner = o
        runCatching { zwischenspeicherLaden(o) }
        runCatching { _gesundheit.value = Quellenbuendel.gesundheitLesen(File(o, "gesundheit.json").readText()) }
        runCatching { _gesehen.value = File(o, "gesehen.txt").readLines().map { it.trim() }.filter { it.isNotEmpty() }.toSet() }
        scope.launch(Dispatchers.IO) {
            runCatching { aktualisieren(MarketApi()) }
            while (isActive) {
                delay(60_000)
                if (geaendert) runCatching { gesundheitSichern() }
            }
        }
    }

    private fun zwischenspeicherLaden(o: File) {
        val d = File(o, "quellen.json")
        val s = File(o, "quellen.json.sig")
        if (!d.exists() || !s.exists()) return
        // Gleiche Pruefung wie aus dem Netz: ein Buendel unter der eingebauten Fassung (nach einem Update) gilt nicht mehr
        val e = Quellenbuendel.pruefen(d.readBytes(), s.readText(), schluessel, Quellenbuendel.EINGEBAUT.version, ::sha512)
        if (e is Quellenbuendel.Ergebnis.Angenommen) {
            aktiv = e.buendel
            _stand.update { it.copy(version = e.buendel.version, geladen = true) }
        }
    }

    // Fragt die Adressen der Reihe nach (zuerst die vom aktiven Buendel genannten, dann die eingebauten). Die erste
    // gueltig signierte Antwort entscheidet; uebernommen wird sie nur mit hoeherer version. true, wenn neu.
    suspend fun aktualisieren(api: MarketApi): Boolean {
        val orte = (aktiv.adressen + Quellenbuendel.ADRESSEN).distinct()
        var ergebnis = "NETZ"
        var http = 0
        try {
            for (ort in orte) {
                val kopf = mapOf("Accept" to "application/json, text/plain", "User-Agent" to UA)
                val a = api.anfrage(ort, kopf, max = 512_000)
                if (!a.ok) { if (a.code != 0 && ergebnis == "NETZ") { ergebnis = "HTTP"; http = a.code }; continue }
                val s = api.anfrage("$ort.sig", kopf, max = 2_000)
                if (!s.ok) { ergebnis = "OHNE_SIGNATUR"; continue }
                val bytes = a.text.orEmpty().toByteArray(Charsets.UTF_8)
                when (val e = Quellenbuendel.pruefen(bytes, s.text.orEmpty(), schluessel, aktiv.version, ::sha512)) {
                    is Quellenbuendel.Ergebnis.Angenommen -> {
                        aktiv = e.buendel
                        ordner?.let { o ->
                            o.mkdirs()
                            de.troetenrepublik.app.plattform.SichereDatei(File(o, "quellen.json")).schreiben(bytes)
                            de.troetenrepublik.app.plattform.SichereDatei(File(o, "quellen.json.sig")).schreiben(s.text.orEmpty().toByteArray())
                        }
                        ergebnis = "NEU"
                        _stand.update { it.copy(version = e.buendel.version, geladen = true) }
                        return true
                    }
                    is Quellenbuendel.Ergebnis.Abgelehnt -> {
                        ergebnis = e.grund.name
                        // Gueltig, aber nicht neuer: der Stand ist aktuell, weitere Orte fragen lohnt nicht
                        if (e.grund == Quellenbuendel.Grund.VERSION) { ergebnis = "AKTUELL"; return false }
                    }
                }
            }
            return false
        } finally {
            _stand.update { it.copy(geprueft = System.currentTimeMillis(), ergebnis = ergebnis, http = http) }
        }
    }

    // ------------------------------------------------------------ Werte fuer Leser

    // Feldname fuer einen Leser; standard, wenn das Buendel keinen nennt.
    fun feld(quelle: String, name: String, standard: String): String =
        aktiv.quelle(quelle).felder[name]?.takeIf { it.isNotBlank() } ?: standard

    // Kommagetrennte Feldliste fuer einen Leser.
    fun felder(quelle: String, name: String, standard: List<String>): List<String> =
        aktiv.quelle(quelle).felder[name]?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }?.takeIf { it.isNotEmpty() } ?: standard

    // Einstellrad aus dem Buendel (tuning).
    fun einstellung(name: String, standard: Double): Double = aktiv.einstellungen[name] ?: standard

    // Eigene Projektadresse nach Namen (repo, releases, releasesApi, issues, spenden, lizenz).
    fun link(name: String, standard: String): String = aktiv.links[name] ?: standard

    // Hinweise des Buendels, die noch nicht weggeklickt sind.
    fun hinweise(): List<Quellenbuendel.Hinweis> = aktiv.hinweise.filter { it.id !in _gesehen.value }

    fun hinweisGesehen(id: String) {
        _gesehen.update { it + id }
        ordner?.let { o -> runCatching { o.mkdirs(); File(o, "gesehen.txt").writeText(_gesehen.value.joinToString("\n")) } }
    }

    // Nur fuer Tests: ein Buendel ohne Signatur setzen (null: eingebaut) und alle Zaehler leeren.
    internal fun fuerTest(b: Quellenbuendel.Buendel?) {
        aktiv = b ?: Quellenbuendel.EINGEBAUT
        _gesundheit.value = emptyMap()
        pausiertBis.clear()
        naechsterAbruf.clear()
    }

    fun zaehlerZuruecksetzen() {
        _gesundheit.value = emptyMap()
        pausiertBis.clear()
        geaendert = true
        runCatching { gesundheitSichern() }
    }

    // Unerwartete Form zaehlen (DRIFT), etwa wenn ein Leser die Antwort nicht versteht.
    fun drift(quelle: String) = zaehlen(quelle) { Quellenbuendel.fehler(it, System.currentTimeMillis(), Fehler.FORM) }

    // Zaehlen und werfen: fuer Leser, die eine unlesbare Antwort melden sollen.
    fun unerwartet(quelle: String, was: String): Nothing {
        drift(quelle)
        throw UnerwarteteAntwort(quelle, was)
    }

    private fun zaehlen(quelle: String, f: (Gesundheit) -> Gesundheit) {
        _gesundheit.update { m -> m + (quelle to f(m[quelle] ?: Gesundheit())) }
        geaendert = true
    }

    private fun gesundheitSichern() {
        val o = ordner ?: return
        geaendert = false
        o.mkdirs()
        de.troetenrepublik.app.plattform.SichereDatei(File(o, "gesundheit.json")).schreiben(Quellenbuendel.gesundheitText(_gesundheit.value).toByteArray())
    }

    // ------------------------------------------------------------ Abfang fuer OkHttp

    private fun istBuendel(url: String): Boolean {
        val u = url.substringBefore('?')
        return (aktiv.adressen + Quellenbuendel.ADRESSEN).any { u == it || u == "$it.sig" }
    }

    // Mindestabstand je Quelle: wartet hoechstens zehn Sekunden.
    private fun abstandHalten(id: String, ms: Int) {
        if (ms <= 0) return
        val jetzt = System.currentTimeMillis()
        var warten = 0L
        naechsterAbruf.compute(id) { _, n ->
            val start = maxOf(jetzt, n ?: 0L)
            warten = start - jetzt
            start + ms
        }
        if (warten > 0) Thread.sleep(minOf(warten, 10_000L))
    }

    // Anwendungs-Interceptor in MarketApi. Hosts ohne Quelle (DeepL, Feeds) laufen unveraendert durch.
    val abfang = Interceptor { chain ->
        val anfrage = chain.request()
        val id = Quellenbuendel.quelleZu(anfrage.url.host) ?: return@Interceptor chain.proceed(anfrage)
        val q = aktiv.quelle(id)
        val buendel = istBuendel(anfrage.url.toString())
        val jetzt = System.currentTimeMillis()
        if (q.aus && !buendel) {
            zaehlen(id) { Quellenbuendel.fehler(it, jetzt, Fehler.AUS) }
            throw IOException("source $id switched off")
        }
        // Nach 403/429 bleibt die Quelle eine Weile still.
        if ((pausiertBis[id] ?: 0L) > jetzt && !buendel) throw IOException("source $id paused")
        abstandHalten(id, q.abstandMs)

        val (host, pfad) = Quellenbuendel.umschreiben(q, anfrage.url.host, anfrage.url.encodedPath)
        val basis = anfrage.newBuilder()
        q.kopf.forEach { (k, v) -> if (anfrage.header(k) == null) basis.header(k, v) }
        val vorlage = basis.build()
        val kette = if (q.zeitlimitMs > 0) chain.withReadTimeout(q.zeitlimitMs, TimeUnit.MILLISECONDS).withConnectTimeout(minOf(q.zeitlimitMs, 15_000), TimeUnit.MILLISECONDS) else chain
        // Ersatzhosts nur fuer Abrufe ohne Koerper, damit nichts doppelt gesendet wird
        val hosts = if (anfrage.method == "GET" || anfrage.method == "HEAD") {
            Quellenbuendel.versuche(q, host, Quellenbuendel.umwegAktiv(_gesundheit.value[id], jetzt))
        } else listOf(host)

        var letzterFehler: IOException? = null
        for ((i, h) in hosts.withIndex()) {
            val url = anfrage.url.newBuilder().host(h).encodedPath(pfad).build()
            val antwort: Response = try {
                kette.proceed(vorlage.newBuilder().url(url).build())
            } catch (e: IOException) {
                // Abgebrochene Abrufe (Seite verlassen) zaehlen nicht
                if (chain.call().isCanceled()) throw e
                zaehlen(id) { Quellenbuendel.fehler(it, System.currentTimeMillis(), if (e is SocketTimeoutException || e is InterruptedIOException) Fehler.ZEIT else Fehler.NETZ) }
                letzterFehler = e
                continue
            }
            val leer = antwort.body?.contentLength() == 0L
            when (Quellenbuendel.einordnen(antwort.code, leer)) {
                Ausgang.OK -> {
                    zaehlen(id) { Quellenbuendel.erfolg(it, System.currentTimeMillis(), if (h != anfrage.url.host) h else "") }
                    return@Interceptor antwort
                }
                Ausgang.NEUTRAL -> return@Interceptor antwort
                Ausgang.GESPERRT -> {
                    zaehlen(id) { Quellenbuendel.fehler(it, System.currentTimeMillis(), Fehler.GESPERRT, antwort.code) }
                    val ms = Quellenbuendel.streuung(Quellenbuendel.pause(antwort.header("Retry-After"), q.pauseMs), Math.random())
                    pausiertBis[id] = System.currentTimeMillis() + ms
                    return@Interceptor antwort
                }
                Ausgang.LEER, Ausgang.SERVER -> {
                    zaehlen(id) { Quellenbuendel.fehler(it, System.currentTimeMillis(), if (antwort.code >= 500) Fehler.SERVER else Fehler.LEER, antwort.code) }
                    if (i == hosts.lastIndex) return@Interceptor antwort
                    antwort.close()
                }
            }
        }
        throw letzterFehler ?: IOException("no answer from $id")
    }
}
