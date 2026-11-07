package de.troetenrepublik.app.market

import de.troetenrepublik.app.engine.Investorenbriefe
import de.troetenrepublik.app.plattform.SichereDatei
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.time.LocalDate
import java.time.ZoneOffset

// Was Investoren schreiben (5.92), Abrufe (Rechenteil in engine/Investorenbriefe.kt): Briefe der Absender von ihren
// eigenen Seiten und, nur mit Kontaktadresse unter Zugaenge, Fondsberichte aus der Volltextsuche der SEC. Alles bleibt
// auf dem Geraet. Die Seiten der Absender fragen sich offen mit Name und Fassung der App ab, ohne etwas ueber die
// Person; die Kontaktadresse geht nur an sec.gov.
// Sparsam: Uebersicht je Absender hoechstens alle drei Tage, ein gelesener Brief wird nicht neu geholt (feste Adressen,
// die immer den neuesten Brief zeigen, alle sieben Tage). SEC je Aktie hoechstens alle sieben Tage, drei Berichte.
class Briefabrufe(private val api: MarketApi, ordner: File) {

    companion object {
        val KENNUNG = Ersatzabrufe.KENNUNG
        private const val TAG = 86_400_000L
        private const val MAX_TEXT = 400_000

        // Nur Adressen bei der SEC bekommen die Kontaktadresse.
        fun beiSec(url: String): Boolean {
            val host = Regex("^https://([^/:?#]+)").find(url)?.groupValues?.get(1)?.lowercase() ?: return false
            return host == "sec.gov" || host.endsWith(".sec.gov")
        }
    }

    @Serializable
    data class Stand(val briefe: List<Investorenbriefe.Brief> = emptyList(), val geprueft: Map<String, Long> = emptyMap())

    @Serializable
    data class SecEintrag(val abgerufen: Long = 0L, val fundstellen: List<Investorenbriefe.Fundstelle> = emptyList())

    @Serializable
    data class SecStand(val je: Map<String, SecEintrag> = emptyMap())

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    private val datei = SichereDatei(File(ordner, "investorenbriefe.json"))
    private val secDatei = SichereDatei(File(ordner, "investorenbriefe-sec.json"))
    private val sperre = Mutex()
    private val secSperre = Mutex()
    @Volatile private var stand: Stand? = null
    @Volatile private var secStand: SecStand? = null
    private val kopf = mapOf("User-Agent" to KENNUNG, "Accept" to "text/html,application/pdf;q=0.9,*/*;q=0.8")

    private fun geladen(): Stand = stand ?: (runCatching { json.decodeFromString(Stand.serializer(), datei.lesen().toString(Charsets.UTF_8)) }.getOrNull() ?: Stand()).also { stand = it }

    // Briefe auf dem Geraet, ohne Abruf.
    suspend fun gespeichert(): List<Investorenbriefe.Brief> = withContext(Dispatchers.IO) { sperre.withLock { geladen().briefe } }

    // Text eines Dokuments: PDF ueber den PDF-Leser, sonst die Seite als Text; mit pdf-Muster des Absenders das PDF,
    // auf das die Seite verweist.
    private suspend fun dokument(link: String, a: Investorenbriefe.Absender): String? {
        val roh = api.datei(link, 15_000_000, kopf) ?: return null
        if (istPdf(roh)) return Investorenbriefe.pdfText(PdfText.zeilen(roh))
        val html = roh.toString(Charsets.UTF_8)
        val pdf = Investorenbriefe.pdfLink(html, a)
        if (pdf != null) {
            delay(300)
            val b = api.datei(pdf, 15_000_000, kopf) ?: return null
            return if (istPdf(b)) Investorenbriefe.pdfText(PdfText.zeilen(b)) else null
        }
        return Investorenbriefe.htmlText(html)
    }

    private fun istPdf(b: ByteArray) = b.size > 4 && b[0] == '%'.code.toByte() && b[1] == 'P'.code.toByte() && b[2] == 'D'.code.toByte() && b[3] == 'F'.code.toByte()

    // Briefe aller Absender, faellige nachgeholt. Scheitert eine Uebersicht, bleibt der alte Stand; neuer Versuch nach
    // sechs Stunden.
    suspend fun briefe(jetzt: Long = System.currentTimeMillis()): List<Investorenbriefe.Brief> = withContext(Dispatchers.IO) {
        sperre.withLock {
            val alt = geladen()
            val faellig = Investorenbriefe.ABSENDER.filter { jetzt - (alt.geprueft[it.id] ?: 0L) >= 3 * TAG }
            if (faellig.isEmpty()) return@withLock alt.briefe
            val briefe = alt.briefe.toMutableList()
            val geprueft = alt.geprueft.toMutableMap()
            for (a in faellig) {
                val links = if (a.seite != null) {
                    val html = api.anfrage(a.seite, kopf, max = 6_000_000).takeIf { it.ok }?.text
                    if (html == null) { geprueft[a.id] = jetzt - 3 * TAG + 6 * 3_600_000L; continue }
                    Investorenbriefe.links(html, a)
                } else a.fest
                if (links.isEmpty()) { geprueft[a.id] = jetzt - 3 * TAG + 6 * 3_600_000L; continue }
                val neu = ArrayList<Investorenbriefe.Brief>()
                for (link in links) {
                    val vorher = briefe.firstOrNull { it.absender == a.id && it.link == link }
                    if (vorher != null && (a.fest.isEmpty() || jetzt - vorher.abgerufen < 7 * TAG)) { neu += vorher; continue }
                    // Weitz verlangt in der robots.txt fuenf Sekunden Abstand
                    delay(if (a.id == "weitz") 5_000L else 300L)
                    val text = runCatching { dokument(link, a) }.getOrNull()?.takeIf { it.length >= 200 }
                    if (text == null) { vorher?.let { neu += it }; continue }
                    val q = Investorenbriefe.quartalBrief(link, text)
                    neu += Investorenbriefe.Brief(a.id, link, q?.text.orEmpty(), jetzt, text.take(MAX_TEXT))
                }
                briefe.removeAll { it.absender == a.id }
                briefe += neu
                geprueft[a.id] = jetzt
            }
            // Absender, die es nicht mehr gibt, fallen weg
            val ids = Investorenbriefe.ABSENDER.map { it.id }.toSet()
            val n = Stand(briefe.filter { it.absender in ids }, geprueft.filterKeys { it in ids })
            stand = n
            runCatching { datei.schreiben(json.encodeToString(Stand.serializer(), n).toByteArray(Charsets.UTF_8)) }
            n.briefe
        }
    }

    private fun secGeladen(): SecStand = secStand ?: (runCatching { json.decodeFromString(SecStand.serializer(), secDatei.lesen().toString(Charsets.UTF_8)) }.getOrNull() ?: SecStand()).also { secStand = it }

    // Gespeicherte SEC-Fundstellen einer Aktie, ohne Abruf.
    suspend fun secGespeichert(symbol: String): List<Investorenbriefe.Fundstelle> =
        withContext(Dispatchers.IO) { secSperre.withLock { secGeladen().je[symbol]?.fundstellen.orEmpty() } }

    // Fondsberichte der letzten 200 Tage, die die Aktie nahe bei „performance“ oder „position“ nennen; die ersten drei
    // gelesen. kennung: User-Agent mit der Kontaktadresse aus Zugaenge (SecInsider.kennung).
    suspend fun sec(symbol: String, name: String, kennung: String, jetzt: Long = System.currentTimeMillis()): List<Investorenbriefe.Fundstelle> =
        withContext(Dispatchers.IO) {
            secSperre.withLock {
                val alt = secGeladen()
                alt.je[symbol]?.takeIf { jetzt - it.abgerufen < 7 * TAG }?.let { return@withLock it.fundstellen }
                val suche = Investorenbriefe.Suche(name, symbol)
                if (suche.grund.length < 3) return@withLock emptyList()
                val secKopf = mapOf("User-Agent" to kennung, "Accept" to "*/*")
                suspend fun text(url: String, max: Int): String? {
                    if (!beiSec(url)) return null
                    delay(150)
                    return runCatching { api.anfrage(url, secKopf, max = max).takeIf { it.ok }?.text }.getOrNull()
                }
                val heute = LocalDate.now(ZoneOffset.UTC)
                val von = heute.minusDays(200).toString()
                val treffer = LinkedHashMap<String, Investorenbriefe.SecTreffer>()
                var antwort = false
                for (wort in listOf("performance", "position")) {
                    val t = text(Investorenbriefe.secAdresse(suche.grund, wort, von, heute.toString()), 2_000_000) ?: continue
                    antwort = true
                    Investorenbriefe.secTreffer(t).forEach { treffer.putIfAbsent(it.adsh, it) }
                }
                if (!antwort) return@withLock alt.je[symbol]?.fundstellen.orEmpty()
                val aus = ArrayList<Investorenbriefe.Fundstelle>()
                for (t in treffer.values.take(3)) {
                    val html = text(t.adresse, 12_000_000) ?: continue
                    val inhalt = withContext(Dispatchers.Default) { Investorenbriefe.htmlText(html) }
                    val q = runCatching { LocalDate.parse(t.stichtag.take(10)) }.getOrNull()?.let { Investorenbriefe.Quartal(it.year, (it.monthValue - 1) / 3 + 1) }
                    val brief = Investorenbriefe.Brief("sec", t.adresse, q?.text.orEmpty(), jetzt, inhalt, name = t.name, form = t.form)
                    withContext(Dispatchers.Default) { Investorenbriefe.fundstelle(brief, suche, t.name) }?.let { aus += it }
                }
                val n = SecStand(alt.je + (symbol to SecEintrag(jetzt, aus)))
                // Hoechstens 200 Aktien merken, die aeltesten fallen weg
                val gekuerzt = if (n.je.size > 200) SecStand(n.je.entries.sortedByDescending { it.value.abgerufen }.take(200).associate { it.key to it.value }) else n
                secStand = gekuerzt
                runCatching { secDatei.schreiben(json.encodeToString(SecStand.serializer(), gekuerzt).toByteArray(Charsets.UTF_8)) }
                aus
            }
        }
}
