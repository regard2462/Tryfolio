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

    // Ersatz-Hauptschluessel: ein Buendel gilt mit dem Hauptschluessel oder mit diesem. Der private Teil liegt getrennt vom
    // Hauptschluessel (tools/quellen/signieren.py neu ersatz), damit der Verlust einer Schluesseldatei die Fernkorrektur
    // nicht beendet.
    const val ERSATZ_SCHLUESSEL = "36223a27ec3195c31a0734f69aa196034c30a9e76cc996d315db769ab4234f02"

    // Offene Kennung beim Abruf des Buendels: nur App und Fassung.
    private val UA: String get() = Ersatzabrufe.KENNUNG

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

    // Zuletzt gueltig gelesene Inhalte (Seite Datenquellen: "zuletzt gueltig geholt vor N Tagen")
    private val _inhalte = MutableStateFlow<Map<String, Quellenbuendel.InhaltStand>>(emptyMap())
    val inhalte: StateFlow<Map<String, Quellenbuendel.InhaltStand>> = _inhalte

    private val _gesehen = MutableStateFlow<Set<String>>(emptySet())
    val gesehen: StateFlow<Set<String>> = _gesehen

    private val gestartet = AtomicBoolean(false)
    @Volatile private var ordner: File? = null
    @Volatile private var geaendert = false
    private val naechsterAbruf = ConcurrentHashMap<String, Long>()

    private fun sha512(b: ByteArray): ByteArray = java.security.MessageDigest.getInstance("SHA-512").digest(b)

    // Beide Hauptschluessel; ein Buendel oder Zertifikat gilt mit einem von beiden.
    val schluessel: List<ByteArray> by lazy { listOf(Quellenbuendel.hex(SCHLUESSEL), Quellenbuendel.hex(ERSATZ_SCHLUESSEL)) }

    // ------------------------------------------------------------ Start und Laden

    // Einmal je Prozess: gespeichertes Buendel und Zaehler laden, dann im Hintergrund nach einem neueren Buendel fragen
    // und die Zaehler regelmaessig sichern.
    fun starten(datenOrdner: File, scope: CoroutineScope) {
        if (!gestartet.compareAndSet(false, true)) return
        val o = File(datenOrdner, "quellen")
        ordner = o
        AbrufAblage.starten(o)
        runCatching { zwischenspeicherLaden(o) }
        runCatching { _gesundheit.value = Quellenbuendel.gesundheitLesen(File(o, "gesundheit.json").readText()) }
        runCatching { _inhalte.value = Quellenbuendel.inhalteLesen(File(o, "inhalte.txt").readText()) }
        runCatching { _gesehen.value = File(o, "gesehen.txt").readLines().map { it.trim() }.filter { it.isNotEmpty() }.toSet() }
        val letzter = runCatching { File(o, "geprueft.txt").readText().trim().split(' ') }.getOrNull()
        var geprueft = letzter?.getOrNull(0)?.toLongOrNull() ?: 0L
        var ok = letzter?.getOrNull(1) == "1"
        scope.launch(Dispatchers.IO) {
            // Hoechstens einmal am Tag (nach einem Fehler nach einer Stunde), auch in einem Prozess, der Tage laeuft
            while (isActive) {
                val jetzt = de.troetenrepublik.app.engine.Uhr.jetzt()
                if (Aktualisierung.faellig(jetzt, geprueft, ok)) {
                    ok = runCatching { aktualisieren(MarketApi()); _stand.value.ergebnis !in setOf("NETZ", "HTTP", "OHNE_SIGNATUR") }.getOrDefault(false)
                    geprueft = jetzt
                    runCatching { o.mkdirs(); File(o, "geprueft.txt").writeText("$geprueft ${if (ok) 1 else 0}") }
                }
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
            _stand.update { it.copy(geprueft = de.troetenrepublik.app.engine.Uhr.jetzt(), ergebnis = ergebnis, http = http) }
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
    fun hinweise(): List<Quellenbuendel.Hinweis> = aktiv.hinweise.filter { it.gilt(Aktualisierung.FASSUNG) && it.id !in _gesehen.value }

    fun hinweisGesehen(id: String) {
        _gesehen.update { it + id }
        ordner?.let { o -> runCatching { o.mkdirs(); File(o, "gesehen.txt").writeText(_gesehen.value.joinToString("\n")) } }
    }

    // Nur fuer Tests: ein Buendel ohne Signatur setzen (null: eingebaut) und alle Zaehler leeren.
    internal fun fuerTest(b: Quellenbuendel.Buendel?) {
        aktiv = b ?: Quellenbuendel.EINGEBAUT
        _gesundheit.value = emptyMap()
        _inhalte.value = emptyMap()
        AbrufAblage.zuruecksetzen()
        naechsterAbruf.clear()
    }

    fun zaehlerZuruecksetzen() {
        _gesundheit.value = emptyMap()
        AbrufAblage.zuruecksetzen()
        geaendert = true
        runCatching { gesundheitSichern() }
    }

    // Ein Inhalt kam lesbar (ok) oder unlesbar an; die Seite Datenquellen zeigt, wann er zuletzt gueltig war.
    fun inhaltGelesen(inhalt: String, ok: Boolean) {
        _inhalte.update { m -> m + (inhalt to Quellenbuendel.inhaltGelesen(m[inhalt], ok, de.troetenrepublik.app.engine.Uhr.jetzt())) }
        geaendert = true
    }

    // Lesen mit Zaehler: liefert der Leser nichts oder nur eine leere Liste, obwohl eine Antwort kam, zaehlt das als DRIFT der
    // Quelle und der Inhalt gilt nicht als gueltig geholt; sonst wird der Zeitpunkt gemerkt.
    fun <T> lesen(quelle: String, inhalt: String, leser: () -> T?): T? {
        val r = runCatching(leser).getOrNull()
        val leer = r == null || (r is Collection<*> && r.isEmpty()) || (r is Map<*, *> && r.isEmpty())
        if (leer) drift(quelle)
        inhaltGelesen(inhalt, !leer)
        return r
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
        de.troetenrepublik.app.plattform.SichereDatei(File(o, "inhalte.txt")).schreiben(Quellenbuendel.inhalteText(_inhalte.value).toByteArray())
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

    // Hosts ohne Quelle im Buendel (Nachrichtenfeeds, Seiten der Behoerden und Boersen): 429 sperrt den Host, 403 und 503 mit
    // Retry-After die Adresse ohne Abfrageteil. Die Sperre steht auf dem Geraet; ein Erfolg hebt sie auf. Nur GET.
    val abfangSonstige = Interceptor { chain ->
        val anfrage = chain.request()
        val host = anfrage.url.host
        if (anfrage.method != "GET" || Quellenbuendel.quelleZu(host) != null || istBuendel(anfrage.url.toString())) return@Interceptor chain.proceed(anfrage)
        val hostSchluessel = "h:$host"
        val adressSchluessel = "u:$host${anfrage.url.encodedPath}"
        val jetzt = System.currentTimeMillis()
        if (AbrufAblage.gesperrtBis(hostSchluessel) > jetzt || AbrufAblage.gesperrtBis(adressSchluessel) > jetzt) throw IOException("host $host paused")
        val antwort = chain.proceed(anfrage)
        val warten = antwort.header("Retry-After")
        when {
            antwort.code == 429 -> AbrufAblage.ablehnung(hostSchluessel, 429, warten)
            antwort.code == 403 -> AbrufAblage.ablehnung(adressSchluessel, 403, warten)
            antwort.code == 503 && warten != null -> AbrufAblage.ablehnung(adressSchluessel, 503, warten)
            antwort.isSuccessful -> { AbrufAblage.erfolg(hostSchluessel); AbrufAblage.erfolg(adressSchluessel) }
        }
        antwort
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
        // Die Sperre steht auf dem Geraet (AbrufAblage) und gilt auch nach einem Neustart der App.
        if (!buendel && AbrufAblage.gesperrtBis("q:$id") > jetzt) throw IOException("source $id paused")
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
                    AbrufAblage.erfolg("q:$id")
                    zaehlen(id) { Quellenbuendel.erfolg(it, System.currentTimeMillis(), if (h != anfrage.url.host) h else "") }
                    return@Interceptor antwort
                }
                Ausgang.NEUTRAL -> return@Interceptor antwort
                Ausgang.GESPERRT -> {
                    zaehlen(id) { Quellenbuendel.fehler(it, System.currentTimeMillis(), Fehler.GESPERRT, antwort.code) }
                    // Retry-After, sonst gestaffelt (5 Minuten bis 12 Stunden); die Pause des Buendels gilt als Mindestwert
                    AbrufAblage.ablehnung("q:$id", antwort.code, antwort.header("Retry-After"), q.pauseMs.toLong())
                    return@Interceptor antwort
                }
                Ausgang.LEER, Ausgang.SERVER -> {
                    zaehlen(id) { Quellenbuendel.fehler(it, System.currentTimeMillis(), if (antwort.code >= 500) Fehler.SERVER else Fehler.LEER, antwort.code) }
                    // 503 mit Retry-After heisst: spaeter wiederkommen, auch ueber einen Neustart hinweg
                    if (antwort.code == 503 && antwort.header("Retry-After") != null) AbrufAblage.ablehnung("q:$id", 503, antwort.header("Retry-After"), 0L)
                    if (i == hosts.lastIndex) return@Interceptor antwort
                    antwort.close()
                }
            }
        }
        throw letzterFehler ?: IOException("no answer from $id")
    }
}
