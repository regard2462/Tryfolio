package de.troetenrepublik.app.market

import de.troetenrepublik.app.engine.StromMarkt
import de.troetenrepublik.app.engine.Uhr
import de.troetenrepublik.app.plattform.SichereDatei
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import java.io.File
import java.util.concurrent.ConcurrentHashMap

// Abrufe fuer Strom in Europa. Preise: ENTSO-E Transparency Platform, nur mit dem Token der Person, eine Anfrage je Land
// mit den letzten 30 Tagen. Erzeugung und Verbrauch: SMARD der Bundesnetzagentur ohne Schluessel, Stundenwerte der letzten
// Woche. Beides liegt zuletzt abgerufen auf dem Geraet und wird nur nach drei Stunden und nur bei Bedarf neu geholt.
// Eine Ablehnung (401, 403, 429) oder ein Netzfehler loescht oder ersetzt nie den gespeicherten Stand: die Anzeige bleibt
// bei den letzten Daten und nennt den Grund. Die Sperre nach 403 und 429 haelt die Quelle "strom" (Quellen.abfang).
class StromAbruf(
    private val api: MarketApi,
    private val ordner: File?,
    private val kontingent: Abrufkontingent? = null,
    private val jetzt: () -> Long = { Uhr.jetzt() },
) {
    enum class Meldung { SCHLUESSEL, ABGELEHNT, LIMIT, KEINE_DATEN, NETZ }

    class Preise(val tage: List<StromMarkt.Tag>, val stand: Long, val meldung: Meldung?)
    class Erzeugung(val reihen: Map<String, List<Pair<Long, Double>>>, val stand: Long, val meldung: Meldung?)

    companion object {
        const val QUELLE = "strom"
        const val FRISCH_MS = 3 * 3_600_000L
        // Nach einem Versuch, gleich ob geglueckt, vor Ablauf dieser Zeit kein zweiter
        const val PAUSE_MS = 10 * 60_000L
        // Letzte Tage der SMARD-Reihen, die abgelegt werden
        const val SMARD_TAGE = 8
        private val KOPF = mapOf("User-Agent" to Ersatzabrufe.KENNUNG)
    }

    private val sperren = ConcurrentHashMap<String, Mutex>()
    private val versuche = ConcurrentHashMap<String, Long>()
    private val preisSpeicher = ConcurrentHashMap<String, Pair<Long, List<StromMarkt.Tag>>>()
    @Volatile private var smardSpeicher: Pair<Long, Map<String, List<Pair<Long, Double>>>>? = null

    private fun lesen(f: File): String? = runCatching { if (f.exists()) SichereDatei(f).lesen().toString(Charsets.UTF_8) else null }.getOrNull()

    private fun schreiben(f: File, text: String) {
        runCatching { f.parentFile?.mkdirs(); SichereDatei(f).schreiben(text.toByteArray(Charsets.UTF_8)) }
    }

    private fun preisDatei(a2: String) = ordner?.let { File(it, "preise-${a2.lowercase()}.txt") }
    private fun smardDatei() = ordner?.let { File(it, "smard.txt") }

    // Erste Zeile "stand=<Millisekunden>", danach der Inhalt
    private fun mitStand(text: String?): Pair<Long, String>? {
        if (text == null || !text.startsWith("stand=")) return null
        val ende = text.indexOf('\n').let { if (it < 0) text.length else it }
        val stand = text.substring(6, ende).trim().toLongOrNull() ?: return null
        return stand to text.substring(minOf(ende + 1, text.length))
    }

    // Gespeicherter Stand eines Landes ohne Abruf (zum sofortigen Anzeigen)
    fun gespeichertePreise(a2: String): Preise? {
        val s = preisSpeicher[a2] ?: preisDatei(a2)?.let { lesen(it) }?.let { mitStand(it) }?.let { (t, x) -> t to StromMarkt.tageLaden(x) }?.takeIf { it.second.isNotEmpty() }
        return s?.let { Preise(it.second, it.first, null) }
    }

    fun gespeicherteErzeugung(): Erzeugung? {
        val s = smardSpeicher ?: smardDatei()?.let { lesen(it) }?.let { mitStand(it) }?.let { (t, x) -> t to StromMarkt.smardLaden(x) }?.takeIf { it.second.isNotEmpty() }
        return s?.let { Erzeugung(it.second, it.first, null) }
    }

    // Tagespreise eines Landes; null ohne Zone oder ohne gueltiges Token. Ohne frischen Stand ein Abruf; scheitert er, kommt
    // der gespeicherte Stand mit der Meldung zurueck, ohne gespeicherten Stand eine leere Liste mit der Meldung.
    suspend fun preise(a2: String, token: String, neu: Boolean = false): Preise? {
        val zone = StromMarkt.zone(a2) ?: return null
        if (!StromMarkt.tokenGueltig(token)) return null
        val schluessel = a2.uppercase()
        return sperren.getOrPut(schluessel) { Mutex() }.withLock {
            val bisher = gespeichertePreise(schluessel)
            val nun = jetzt()
            val heute = Math.floorDiv(nun, 86_400_000L)
            val frisch = bisher != null && nun - bisher.stand < FRISCH_MS && bisher.tage.any { it.tag >= heute }
            val zuletzt = versuche[schluessel]
            if (!neu && (frisch || (zuletzt != null && nun - zuletzt < PAUSE_MS))) return@withLock bisher
            versuche[schluessel] = nun
            val ergebnis = holePreise(zone, token.trim())
            val tage = ergebnis.first
            if (tage.isNotEmpty()) {
                val alle = StromMarkt.zusammenfuehren(bisher?.tage.orEmpty(), tage)
                preisSpeicher[schluessel] = nun to alle
                preisDatei(schluessel)?.let { schreiben(it, "stand=$nun\n" + StromMarkt.tageSpeichern(alle)) }
                Preise(alle, nun, null)
            } else Preise(bisher?.tage.orEmpty(), bisher?.stand ?: 0L, ergebnis.second ?: Meldung.KEINE_DATEN)
        }
    }

    private suspend fun holePreise(zone: StromMarkt.Zone, token: String): Pair<List<StromMarkt.Tag>, Meldung?> {
        val k = kontingent
        if (k != null) {
            if (!k.erlaubt(Abrufkontingent.ENTSOE)) return emptyList<StromMarkt.Tag>() to Meldung.LIMIT
            val warten = k.warten(Abrufkontingent.ENTSOE)
            if (warten > 0) delay(warten)
            k.buchen(Abrufkontingent.ENTSOE)
        }
        val heute = Uhr.heute()
        val url = StromMarkt.preisAdresse(zone, heute.minusDays(31), heute.plusDays(2), token)
        val a = api.anfrage(url, KOPF + ("Accept" to "application/xml"), max = 6_000_000)
        val text = a.text.orEmpty()
        return when {
            a.code == 401 -> emptyList<StromMarkt.Tag>() to Meldung.SCHLUESSEL
            a.code == 429 -> emptyList<StromMarkt.Tag>() to Meldung.LIMIT
            a.code == 403 -> emptyList<StromMarkt.Tag>() to Meldung.ABGELEHNT
            StromMarkt.absage(text) != null -> emptyList<StromMarkt.Tag>() to Meldung.KEINE_DATEN
            !a.ok -> emptyList<StromMarkt.Tag>() to Meldung.NETZ
            else -> {
                val tage = StromMarkt.preiseLesen(text)
                // Eine erfolgreiche Antwort ohne lesbare Preise zaehlt als unerwartete Form der Quelle
                if (tage.isEmpty()) runCatching { Quellen.unerwartet(QUELLE, "entsoe preise") }
                tage to if (tage.isEmpty()) Meldung.KEINE_DATEN else null
            }
        }
    }

    // Erzeugung und Verbrauch in Deutschland aus SMARD. Gleiches Verhalten wie bei den Preisen.
    suspend fun erzeugung(neu: Boolean = false): Erzeugung? = sperren.getOrPut("smard") { Mutex() }.withLock {
        val bisher = gespeicherteErzeugung()
        val nun = jetzt()
        val zuletzt = versuche["smard"]
        if (!neu && ((bisher != null && nun - bisher.stand < FRISCH_MS) || (zuletzt != null && nun - zuletzt < PAUSE_MS))) return@withLock bisher
        versuche["smard"] = nun
        val (reihen, meldung) = holeErzeugung()
        // Nur ein vollstaendiger Satz ersetzt den gespeicherten; sonst bleibt der alte stehen
        if (meldung == null && StromMarkt.mix(reihen) != null) {
            smardSpeicher = nun to reihen
            smardDatei()?.let { schreiben(it, "stand=$nun\n" + StromMarkt.smardSpeichern(reihen)) }
            Erzeugung(reihen, nun, null)
        } else Erzeugung(bisher?.reihen.orEmpty(), bisher?.stand ?: 0L, meldung ?: Meldung.KEINE_DATEN)
    }

    private fun meldungZu(code: Int): Meldung = when (code) {
        401, 403 -> Meldung.ABGELEHNT
        429 -> Meldung.LIMIT
        else -> Meldung.NETZ
    }

    private suspend fun holeErzeugung(): Pair<Map<String, List<Pair<Long, Double>>>, Meldung?> {
        val index = api.anfrage(StromMarkt.smardIndexAdresse(StromMarkt.ERZEUGER.first().filter), KOPF)
        if (!index.ok) return emptyMap<String, List<Pair<Long, Double>>>() to meldungZu(index.code)
        val wochen = StromMarkt.smardIndex(index.text.orEmpty())
        val letzte = wochen.lastOrNull() ?: return emptyMap<String, List<Pair<Long, Double>>>() to Meldung.KEINE_DATEN
        val filter = StromMarkt.ERZEUGER.map { it.schluessel to it.filter } + ("last" to StromMarkt.VERBRAUCH)
        val schranke = Semaphore(3)
        var abgelehnt: Meldung? = null

        suspend fun block(f: Int, start: Long): List<Pair<Long, Double>> = schranke.withPermit {
            val a = api.anfrage(StromMarkt.smardBlockAdresse(f, start), KOPF)
            // Eine Absage der Quelle vermerken; ein einzelner fehlender Block (404) laesst nur diese Reihe leer
            if (!a.ok && a.code != 404) abgelehnt = meldungZu(a.code)
            if (a.ok) StromMarkt.smardReihe(a.text.orEmpty()) else emptyList()
        }

        val neu = coroutineScope { filter.map { (k, f) -> async { k to block(f, letzte) } }.awaitAll() }.toMap()
        // Beginnt gerade eine neue Woche, reicht der juengste Block nicht fuer 24 Stunden: der davor kommt dazu
        val dazu = wochen.getOrNull(wochen.size - 2)
        val wenig = (neu["pv"]?.size ?: 0) < 48
        val voll = if (wenig && dazu != null) {
            val alt = coroutineScope { filter.map { (k, f) -> async { k to block(f, dazu) } }.awaitAll() }.toMap()
            neu.mapValues { (k, v) -> (alt[k].orEmpty() + v).distinctBy { it.first }.sortedBy { it.first } }
        } else neu
        val grenze = (voll.values.maxOfOrNull { r -> r.lastOrNull()?.first ?: 0L } ?: 0L) - SMARD_TAGE * 24L * 3_600_000L
        val gekuerzt = voll.mapValues { (_, v) -> v.filter { it.first > grenze } }
        return gekuerzt to abgelehnt
    }
}
