package de.troetenrepublik.app.market

import de.troetenrepublik.app.market.AchtK.DealDoc
import de.troetenrepublik.app.market.AchtK.Detail
import de.troetenrepublik.app.market.AchtK.Rolle
import de.troetenrepublik.app.plattform.SichereDatei
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap

// Abruf der 8-K-Meldungen eines Emittenten bei der SEC [0.77]: eine Anfrage fuer die Liste, dazu je Aufruf hoechstens
// fuenf Dokumente mit Item 5.02, fuenf mit Item 2.01 oder 1.01 und fuenf weitere fuer die Angaben zu den Warnzeichen
// (150 ms Abstand, nacheinander, SEC-Kennung wie bei den uebrigen SEC-Abrufen). Was aus einer Meldung gelesen ist
// (Rolle, Vereinbarung, Angaben), aendert sich nie und bleibt dauerhaft in einer Datei; ein spaeterer Aufruf liest die
// naechsten. Antwortet die SEC mit 403 oder 429, bricht das Lesen ab. Das Ergebnis je Wert bleibt zwoelf Stunden im
// Speicher.
object AchtKAbruf {
    class Ergebnis(val cik: String, val warnzeichen: List<AchtK.Warnzeichen>, val vorgaenge: List<AchtK.Vorgang>) {
        // Warnzeichen ohne Boersenwechsel und Entwarnung (AchtK.zaehlt)
        val anzahl: Int get() = AchtK.anzahl(warnzeichen)
        val leer: Boolean get() = anzahl == 0 && vorgaenge.isEmpty()
    }

    @Serializable
    private class Gelesen(
        val rollen: Map<String, Rolle> = emptyMap(),
        val deals: Map<String, DealDoc> = emptyMap(),
        val details: Map<String, Detail> = emptyMap(),
        val stand: Int = 0,
    )

    // Stand der Leseregeln: Mit jeder Aenderung am Lesen der Meldungen hochzaehlen, dann wird neu gelesen.
    private const val LESE_STAND = 3

    private const val STUNDE = 3_600_000L
    private const val DOKUMENTE_JE_ART = 5

    private val api by lazy { MarketApi() }
    private val json = Json { ignoreUnknownKeys = true }
    private val sperre = Mutex()
    private val speicher = ConcurrentHashMap<String, Pair<Long, Ergebnis>>()
    private val rollen = ConcurrentHashMap<String, Rolle>()
    private val deals = ConcurrentHashMap<String, DealDoc>()
    private val details = ConcurrentHashMap<String, Detail>()
    private var kuerzelText: String? = null
    private var dateiGelesen = false

    private val _schwere = MutableStateFlow<Map<String, Int>>(emptyMap())

    // Zahl der schweren Warnzeichen (Korrektur, Delisting-Hinweis, Insolvenz; ohne Boersenwechsel und Entwarnung) je
    // Wert, sobald dessen Ergebnis geladen ist.
    val schwere: StateFlow<Map<String, Int>> = _schwere.asStateFlow()

    fun gemerkt(symbol: String): Ergebnis? = speicher[symbol]?.second

    private fun lesenVonDatei(datei: SichereDatei?) {
        if (dateiGelesen || datei == null) return
        dateiGelesen = true
        val g = runCatching { json.decodeFromString(Gelesen.serializer(), datei.lesen().decodeToString()) }.getOrNull() ?: return
        if (g.stand != LESE_STAND) return
        g.rollen.forEach { (k, v) -> rollen.putIfAbsent(k, v) }
        g.deals.forEach { (k, v) -> deals.putIfAbsent(k, v) }
        g.details.forEach { (k, v) -> details.putIfAbsent(k, v) }
    }

    private fun schreibenInDatei(datei: SichereDatei?) {
        if (datei == null) return
        val g = Gelesen(rollen.toMap(), deals.toMap(), details.toMap(), LESE_STAND)
        runCatching { datei.schreiben(json.encodeToString(Gelesen.serializer(), g).encodeToByteArray()) }
    }

    // Ergebnis fuer das Symbol; null, wenn die Liste nicht abrufbar war (nichts gemerkt). cikHinweis: CIK aus den
    // Insiderdaten, sonst aus der Kuerzelliste der SEC. datei: dauerhafter Speicher der gelesenen Meldungen.
    suspend fun laden(symbol: String, kennung: String, cikHinweis: String?, heute: LocalDate, datei: SichereDatei? = null): Ergebnis? {
        frisch(symbol)?.let { return it }
        return sperre.withLock {
            frisch(symbol)?.let { return@withLock it }
            lesenVonDatei(datei)
            val kopf = mapOf("User-Agent" to kennung, "Accept" to "*/*")
            var gesperrt = false
            suspend fun holen(url: String): Pair<Int, String?> {
                // Die SEC hat abgelehnt (403/429, auch in einem frueheren Lauf): kein Abruf bis zum Ende der Sperre
                if (AbrufAblage.gesperrt("q:sec")) { gesperrt = true; return 0 to null }
                delay(150)
                val a = runCatching { api.anfrage(url, kopf, max = 3_000_000) }.getOrNull() ?: return 0 to null
                if (a.code == 403 || a.code == 429) gesperrt = true
                return a.code to (if (a.ok) a.text else null)
            }
            val cik = cikHinweis?.trimStart('0')?.takeIf { it.isNotBlank() }
                ?: (kuerzelText ?: holen(SecFormen.KUERZEL).second?.also { kuerzelText = it })?.let { SecFormen.cikAusKuerzeln(it, symbol) }
                ?: return@withLock null
            val text = holen(AchtK.submissionsAdresse(cik)).second ?: return@withLock null
            val liste = AchtK.meldungen(text)
            val emittent = AchtK.name(text).orEmpty()
            var geaendert = false

            // Dokumente mit Item 5.02: Rolle des Abgangs und Angaben dazu
            val lokal = HashMap<String, Rolle>()
            for (m in AchtK.fuehrungsKandidaten(liste, heute, rollen.keys, DOKUMENTE_JE_ART)) {
                if (gesperrt) break
                val html = holen(SecInsider.archivAdresse(cik, m.accession, m.datei)).second
                if (html == null) { lokal[m.accession] = Rolle.UNBEKANNT; continue }
                val r = AchtK.rolleLesen(html, emittent)
                rollen[m.accession] = r
                if (r != Rolle.KEINE && r != Rolle.UNBEKANNT) details[AchtK.detailKey(m.accession, AchtK.Art.FUEHRUNG)] = AchtKDetail.fuehrung(html, emittent)
                geaendert = true
            }
            // Dokumente mit Item 2.01 oder 1.01: Vollzug und Vereinbarung
            for (m in AchtK.dealKandidaten(liste, heute, deals.keys, DOKUMENTE_JE_ART)) {
                if (gesperrt) break
                val html = holen(SecInsider.archivAdresse(cik, m.accession, m.datei)).second ?: continue
                deals[m.accession] = AchtK.dealLesen(html, if ("2.01" in m.items) "2.01" else "1.01", emittent)
                geaendert = true
            }
            // Angaben zu den uebrigen Warnzeichen aus dem Text der Meldung, neueste zuerst, je Meldung einmal
            val alle = rollen + lokal
            val offen = AchtK.warnzeichen(liste, heute, alle, details).filter { it.detail == null && it.art != AchtK.Art.FUEHRUNG }
                .distinctBy { it.accession }.take(DOKUMENTE_JE_ART)
            for (w in offen) {
                if (gesperrt) break
                val html = holen(SecInsider.archivAdresse(cik, w.accession, w.datei)).second ?: continue
                // Alle Arten der Meldung auf einmal: ein Dokument, ein Abruf
                liste.firstOrNull { it.accession == w.accession }?.let { m ->
                    AchtK.arten(m).filter { it != AchtK.Art.FUEHRUNG }.forEach { art ->
                        AchtKDetail.lesen(art, html, emittent)?.let { details[AchtK.detailKey(w.accession, art)] = it }
                    }
                }
                geaendert = true
            }
            if (geaendert) schreibenInDatei(datei)
            val erg = Ergebnis(cik, AchtK.warnzeichen(liste, heute, alle, details), AchtK.vorgaenge(liste, deals, heute))
            speicher[symbol] = System.currentTimeMillis() to erg
            _schwere.value = _schwere.value + (symbol to AchtK.schwereAnzahl(erg.warnzeichen))
            erg
        }
    }

    private fun frisch(symbol: String): Ergebnis? =
        speicher[symbol]?.takeIf { System.currentTimeMillis() - it.first < 12 * STUNDE }?.second
}
