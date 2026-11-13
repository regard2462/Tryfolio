package de.troetenrepublik.app.market

import de.troetenrepublik.app.plattform.SichereDatei
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.InputStream
import java.util.zip.ZipInputStream

// Form AP des PCAOB: Fuer jeden Pruefvermerk bei einem Emittenten melden die Pruefgesellschaften die verantwortliche
// Partnerin oder den Partner und beteiligte andere Pruefer mit ihrem Anteil. Das PCAOB stellt den ganzen Bestand als
// Zip mit einer CSV-Datei zum Download bereit (taeglich erneuert, rund 13 MB). Die App liest die Datei einmal im Strom und
// behaelt nur einen kleinen Index (letzte vier Berichtsjahre je Emittent, ohne Fonds und Betriebsrentenplaene);
// Abruf nur auf Tipp der Nutzer.
object Pcaob {
    const val ADRESSE = "https://assets.pcaobus.org/firm-filings/FirmFilings.zip"
    const val QUELLE = "Public Company Accounting Oversight Board (pcaobus.org)"
    const val QUELLE_ADRESSE = "https://pcaobus.org/resources/auditorsearch"
    private const val JE_EMITTENT = 4

    // Anteil eines weiteren Pruefers an der Pruefung, Untergrenze in Prozent
    data class Anteil(val firma: String, val ab: Int)

    // ende: Ende des Geschaeftsjahrs (JJJJ-MM-TT)
    data class Eintrag(val ende: String, val firmaId: String, val firma: String, val partnerId: String, val partner: String, val andere: List<Anteil>)

    // neueste zuerst
    data class Ergebnis(val eintraege: List<Eintrag>) {
        val aktuell: Eintrag get() = eintraege.first()

        // Erstes Berichtsjahr der Reihe, in der Partner und Gesellschaft gleich bleiben (aelter als die Daten reicht Form AP nicht, ab 2017)
        val partnerSeit: Int
            get() {
                var seit = aktuell.ende.take(4).toIntOrNull() ?: 0
                for (e in eintraege.drop(1)) {
                    if (e.partnerId == aktuell.partnerId && e.firmaId == aktuell.firmaId) seit = e.ende.take(4).toIntOrNull() ?: seit else break
                }
                return seit
            }
    }

    class Index(val stand: String, val werte: Map<String, List<Eintrag>>) {
        fun fuer(cik: String): Ergebnis? = werte[cik.trim().trimStart('0')]?.takeIf { it.isNotEmpty() }?.let { Ergebnis(it) }
    }

    // ------------------------------------------------------------ CSV im Strom

    internal class CsvLeser(private val r: Utf8Strom) {
        private var vorgemerkt = -2

        private fun naechstes(): Int = r.lies()

        private fun lies(): Int = if (vorgemerkt != -2) vorgemerkt.also { vorgemerkt = -2 } else naechstes()

        // Ein Datensatz (Felder in Anfuehrungszeichen duerfen Zeilenumbrueche und Kommas enthalten); null am Ende
        fun satz(): List<String>? {
            val felder = ArrayList<String>(64)
            val sb = StringBuilder()
            var inAnf = false
            var gelesen = false
            while (true) {
                val c = lies()
                if (c < 0) {
                    if (!gelesen) return null
                    felder.add(sb.toString())
                    return felder
                }
                gelesen = true
                val ch = c.toChar()
                if (inAnf) {
                    if (ch == '"') {
                        val d = lies()
                        if (d == '"'.code) sb.append('"') else { inAnf = false; vorgemerkt = d }
                    } else sb.append(ch)
                } else when (ch) {
                    '"' -> inAnf = true
                    ',' -> { felder.add(sb.toString()); sb.setLength(0) }
                    '\n' -> { felder.add(sb.toString()); return felder }
                    '\r' -> {}
                    else -> sb.append(ch)
                }
            }
        }
    }

    private val json = Json { ignoreUnknownKeys = true }

    // "7/20/2017 12:00:00 AM" zu "2017-07-20"; "" bei unlesbarem Datum
    internal fun datum(s: String): String {
        val t = s.trim().substringBefore(' ').split('/')
        if (t.size != 3) return ""
        val m = t[0].toIntOrNull() ?: return ""
        val d = t[1].toIntOrNull() ?: return ""
        val j = t[2].toIntOrNull() ?: return ""
        return j.toString().padStart(4, '0') + "-" + m.toString().padStart(2, '0') + "-" + d.toString().padStart(2, '0')
    }

    private val ZAHL = Regex("\\d+")

    // Weitere Pruefer mit Anteil ab 5 Prozent aus dem Feld „Audit Not Divided Percent Information“ (JSON-Liste)
    internal fun andere(feld: String): List<Anteil> {
        if (feld.isBlank()) return emptyList()
        return runCatching {
            json.parseToJsonElement(feld).jsonArray.mapNotNull { e ->
                val o = e.jsonObject
                val name = o["FirmName"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty().replace(Regex("[\\t\\n,~^]"), " ")
                val ab = ZAHL.find(o["Percentage"]?.jsonPrimitive?.contentOrNull.orEmpty())?.value?.toIntOrNull()
                    ?: o["Range"]?.jsonPrimitive?.contentOrNull?.takeIf { !it.startsWith("Less", true) }?.let { ZAHL.find(it)?.value?.toIntOrNull() }
                if (name.isBlank() || ab == null || ab < 5) null else Anteil(name, ab)
            }.sortedByDescending { it.ab }.take(3)
        }.getOrDefault(emptyList())
    }

    // Index aus dem Zip-Strom: je Emittent (CIK) die letzten vier Berichtsjahre, nur gueltige (nicht ersetzte) Meldungen
    // von Emittenten ohne Fonds und Betriebsrentenplaene.
    fun bauen(zip: InputStream, stand: String): Index {
        val z = ZipInputStream(zip)
        var e = z.nextEntry
        while (e != null && !e.name.endsWith(".csv", true)) e = z.nextEntry
        if (e == null) return Index(stand, emptyMap())
        return aus(Utf8Strom(z), stand)
    }

    internal fun aus(leser: Utf8Strom, stand: String): Index {
        val csv = CsvLeser(leser)
        val kopf = csv.satz() ?: return Index(stand, emptyMap())
        fun sp(name: String) = kopf.indexOfFirst { it.trim() == name }
        val iNeu = sp("Latest Form AP Filing"); val iFirmaId = sp("Firm ID"); val iFirma = sp("Firm Name"); val iCik = sp("Issuer CIK")
        val iEnde = sp("Fiscal Period End Date"); val iNach = sp("Engagement Partner Last Name"); val iVor = sp("Engagement Partner First Name")
        val iMitte = sp("Engagement Partner Middle Name"); val iPid = sp("Engagement Partner ID"); val iArt = sp("Audit Report Type")
        val iAndere = sp("Audit Not Divided Percent Information")
        if (listOf(iNeu, iFirmaId, iFirma, iCik, iEnde, iNach, iVor, iPid, iArt).any { it < 0 }) return Index(stand, emptyMap())
        val werte = HashMap<String, MutableList<Eintrag>>()
        while (true) {
            val s = csv.satz() ?: break
            if (s.size != kopf.size || s[iNeu].trim() != "1") continue
            val art = s[iArt]
            // „Issuer, other than Employee Benefit Plan or Investment Company“; Fonds und Rentenplaene haben eigene Arten
            if (!art.startsWith("Issuer")) continue
            val cik = s[iCik].trim().trimStart('0')
            val ende = datum(s[iEnde])
            if (cik.isEmpty() || ende.isEmpty()) continue
            val partner = listOf(s[iVor], if (iMitte >= 0) s[iMitte] else "", s[iNach]).map { it.trim() }.filter { it.isNotEmpty() }.joinToString(" ")
            val liste = werte.getOrPut(cik) { ArrayList(JE_EMITTENT + 1) }
            liste.removeAll { it.ende == ende }
            liste.add(Eintrag(ende, s[iFirmaId].trim(), s[iFirma].trim(), s[iPid].trim(), partner, if (iAndere >= 0) andere(s[iAndere]) else emptyList()))
            if (liste.size > JE_EMITTENT) liste.remove(liste.minBy { it.ende })
        }
        return Index(stand, werte.mapValues { (_, l) -> l.sortedByDescending { it.ende } })
    }

    // ------------------------------------------------------------ Ablage (kleine Textdatei)

    private fun sauber(s: String) = s.replace(Regex("[\\t\\n\\r|;~^]"), " ")

    // Zeile je Emittent: cik TAB Eintrag TAB Eintrag ...; Eintrag: ende|firmaId|firma|partnerId|partner|firma~ab^firma~ab
    fun schreiben(i: Index): String {
        val sb = StringBuilder("v1\t").append(i.stand).append('\n')
        for ((cik, l) in i.werte) {
            sb.append(cik)
            for (e in l) {
                sb.append('\t').append(e.ende).append('|').append(sauber(e.firmaId)).append('|').append(sauber(e.firma)).append('|')
                    .append(sauber(e.partnerId)).append('|').append(sauber(e.partner)).append('|')
                    .append(e.andere.joinToString("^") { sauber(it.firma) + "~" + it.ab })
            }
            sb.append('\n')
        }
        return sb.toString()
    }

    fun lesen(text: String): Index? {
        val zeilen = text.lineSequence().iterator()
        if (!zeilen.hasNext()) return null
        val kopf = zeilen.next().split('\t')
        if (kopf.size < 2 || kopf[0] != "v1") return null
        val werte = HashMap<String, List<Eintrag>>()
        for (z in zeilen) {
            if (z.isBlank()) continue
            val t = z.split('\t')
            werte[t[0]] = t.drop(1).mapNotNull { x ->
                val f = x.split('|')
                if (f.size < 6) null else Eintrag(
                    f[0], f[1], f[2], f[3], f[4],
                    f[5].split('^').filter { it.contains('~') }.mapNotNull { a -> a.substringAfterLast('~').toIntOrNull()?.let { Anteil(a.substringBeforeLast('~'), it) } },
                )
            }
        }
        return Index(kopf[1], werte)
    }
}

// Abruf und Speicher des Form-AP-Index. Der Index liegt als kleine Datei auf dem Geraet und gilt, bis er aelter als
// 120 Tage ist; geladen wird nur auf ausdruecklichen Wunsch (ueber 13 MB, im Strom gelesen, nichts davon abgelegt).
object PcaobAbruf {
    private val api by lazy { MarketApi() }
    private val sperre = Mutex()
    @Volatile private var index: Pcaob.Index? = null
    private var gelesen = false
    const val ALT_TAGE = 120L

    // Der Abruf laeuft in einem eigenen Bereich, damit er nicht abbricht, wenn die Karte aus dem Bild scrollt
    private val bereich = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _laeuft = MutableStateFlow(false)
    private val _stand = MutableStateFlow<String?>(null)

    // Ob der Index gerade geladen wird
    val laeuft: StateFlow<Boolean> = _laeuft.asStateFlow()

    // Stand des Index (JJJJ-MM-TT), sobald einer da ist; aendert sich nach jedem Laden
    val stand: StateFlow<String?> = _stand.asStateFlow()

    // Der Index ohne Abruf; null, wenn es keinen gibt
    fun vorhanden(datei: SichereDatei?): Pcaob.Index? {
        index?.let { return it }
        if (gelesen || datei == null) return index
        gelesen = true
        index = runCatching { Pcaob.lesen(datei.lesen().decodeToString()) }.getOrNull()
        _stand.value = index?.stand
        return index
    }

    // Ob der Index fehlt oder zu alt ist
    fun veraltet(datei: SichereDatei?, heute: java.time.LocalDate): Boolean {
        val s = vorhanden(datei)?.stand ?: return true
        val d = runCatching { java.time.LocalDate.parse(s) }.getOrNull() ?: return true
        return java.time.temporal.ChronoUnit.DAYS.between(d, heute) > ALT_TAGE
    }

    // Laedt die Datei im Strom und baut den Index; null bei Netzfehler
    suspend fun laden(datei: SichereDatei?, heute: java.time.LocalDate): Pcaob.Index? = sperre.withLock {
        val neu = runCatching {
            api.strom(Pcaob.ADRESSE, mapOf("Accept" to "*/*")) { Pcaob.bauen(it, heute.toString()) }
        }.getOrNull() ?: return@withLock null
        if (neu.werte.isEmpty()) return@withLock null
        index = neu
        if (datei != null) runCatching { datei.schreiben(Pcaob.schreiben(neu).encodeToByteArray()) }
        _stand.value = neu.stand
        neu
    }

    // Startet das Laden im Hintergrund (nur auf Wunsch der Nutzer)
    fun starten(datei: SichereDatei?, heute: java.time.LocalDate) {
        if (!_laeuft.compareAndSet(false, true)) return
        bereich.launch {
            try { laden(datei, heute) } finally { _laeuft.value = false }
        }
    }
}
