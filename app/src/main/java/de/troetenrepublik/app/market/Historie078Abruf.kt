package de.troetenrepublik.app.market

import de.troetenrepublik.app.engine.Historie078
import de.troetenrepublik.app.engine.Historie078.Fakt
import de.troetenrepublik.app.engine.Historie078.Punkt
import de.troetenrepublik.app.engine.Splits
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap

// Abrufe von Paket q78e (0.76), alle mit Zwischenspeicher im Speicher: Jahres- und Quartalszahlen von Yahoo
// (ws/fundamentals-timeseries), XBRL-Reihen der SEC (companyconcept, mit Kontaktkennung wie die uebrigen SEC-Abrufe),
// Leerverkaeufe der Nasdaq (nur an der Nasdaq notierte Werte), Fail-to-Deliver-Dateien der SEC und die
// Reg-SHO-Schwellenliste von Nasdaq Trader. Rechnen und Lesen steht in engine/Historie078.kt.
object Historie078Abruf {

    private val api by lazy { MarketApi() }
    private val nasdaq by lazy { NasdaqAbruf({ api.anfrage(it, NasdaqKalender.KOPF) }, sperre = AbrufAblage) }
    private val speicher = ConcurrentHashMap<String, Pair<Long, Any>>()
    private val secSperre = Mutex()
    private var kuerzelText: String? = null

    private fun frisch(schluessel: String, haltbarMs: Long): Any? =
        speicher[schluessel]?.takeIf { System.currentTimeMillis() - it.first < haltbarMs }?.second

    private fun merken(schluessel: String, wert: Any) { speicher[schluessel] = System.currentTimeMillis() to wert }

    private const val STUNDE = 3_600_000L

    // ------------------------------------------------------------ Yahoo

    // Stichtage mit TTM-Werten aus Yahoo; null ohne Antwort, leer ohne Daten.
    @Suppress("UNCHECKED_CAST")
    suspend fun yahooBasen(symbol: String): List<Historie078.Basis>? {
        (frisch("yb|$symbol", 12 * STUNDE) as? List<Historie078.Basis>)?.let { return it }
        val text = api.jahresabschluesse(symbol, Historie078.YAHOO_TYPEN) ?: return null
        val basen = Historie078.basenYahoo(Historie078.yahooReihen(text))
        if (basen.isNotEmpty()) merken("yb|$symbol", basen)
        return basen
    }

    // ------------------------------------------------------------ SEC XBRL

    class SecReihen(val basen: List<Historie078.Basis>, val umsatz: List<Punkt>)

    // Reihen aus den XBRL-Daten der SEC fuer US-Firmen (Berichte in US-Dollar); null ohne Treffer. kennung ist die Kennung
    // der App mit Kontakt (SecInsider.kennung), ohne sie ruft die SEC nicht ab.
    @Suppress("UNCHECKED_CAST")
    suspend fun secReihen(symbol: String, kennung: String, splits: List<Splits.Split>): SecReihen? {
        (frisch("sec|$symbol", 24 * STUNDE) as? SecReihen)?.let { return it }
        return secSperre.withLock {
            (frisch("sec|$symbol", 24 * STUNDE) as? SecReihen)?.let { return@withLock it }
            val kopf = mapOf("User-Agent" to kennung, "Accept" to "*/*")
            suspend fun text(url: String): String? {
                delay(150)
                return runCatching { api.anfrage(url, kopf).takeIf { it.ok }?.text }.getOrNull()
            }
            val liste = kuerzelText ?: text(SecFormen.KUERZEL)?.also { kuerzelText = it } ?: return@withLock null
            val cik = SecFormen.cikAusKuerzeln(liste, symbol) ?: return@withLock null
            suspend fun fakten(ns: String, begriff: String, einheit: String): List<Fakt> =
                text(Historie078.conceptAdresse(cik, ns, begriff))?.let { Historie078.faktenLesen(it, einheit) }.orEmpty()
            suspend fun ttm(begriff: String, einheit: String = "USD") = Historie078.ttm(fakten("us-gaap", begriff, einheit))

            val eps = Historie078.ttm(Historie078.splitAngepasst(fakten("us-gaap", "EarningsPerShareDiluted", "USD/shares"), splits, true))
            if (eps.isEmpty()) return@withLock null
            val umsatz = Historie078.zusammen(
                ttm("RevenueFromContractWithCustomerExcludingAssessedTax"), ttm("Revenues"), ttm("SalesRevenueNet"),
                ttm("RevenueFromContractWithCustomerIncludingAssessedTax"),
            )
            val cash = ttm("NetCashProvidedByUsedInOperatingActivities")
            val ebit = ttm("OperatingIncomeLoss")
            val abschreibung = ttm("DepreciationDepletionAndAmortization").ifEmpty { ttm("DepreciationAndAmortization") }
                .ifEmpty { ttm("DepreciationAmortizationAndAccretionNet") }
            val ebitda = Historie078.summe(ebit, abschreibung)
            var aktien = Historie078.bestaende(Historie078.splitAngepasst(fakten("dei", "EntityCommonStockSharesOutstanding", "shares"), splits, false))
            if (aktien.isEmpty()) {
                aktien = Historie078.splitAngepasst(fakten("us-gaap", "WeightedAverageNumberOfDilutedSharesOutstanding", "shares"), splits, false)
                    .map { Punkt(it.ende, it.wert) }.sortedBy { it.tag }
            }
            val schulden = Historie078.zusammen(
                Historie078.bestaende(fakten("us-gaap", "LongTermDebt", "USD")), Historie078.bestaende(fakten("us-gaap", "LongTermDebtNoncurrent", "USD")),
            )
            val kasse = Historie078.bestaende(fakten("us-gaap", "CashAndCashEquivalentsAtCarryingValue", "USD"))
            val r = SecReihen(Historie078.basen(eps, umsatz, cash, ebitda, aktien, schulden, kasse), umsatz)
            merken("sec|$symbol", r)
            r
        }
    }

    // ------------------------------------------------------------ Wikidata: Beschaeftigte

    // Zahl der Beschaeftigten je Jahr (Wikidata, CC0). Gesucht wird mit den Namen der Reihe nach; der Eintrag muss zum
    // Symbol passen (MitarbeiterReihe). Leer, wenn es keine Reihe gibt; null ohne Antwort (nicht gemerkt).
    @Suppress("UNCHECKED_CAST")
    suspend fun mitarbeiter(namen: List<String>, symbol: String): List<de.troetenrepublik.app.engine.MitarbeiterReihe.Punkt>? {
        (frisch("ma|$symbol", 7 * 24 * STUNDE) as? List<de.troetenrepublik.app.engine.MitarbeiterReihe.Punkt>)?.let { return it }
        var antwortete = false
        for (n in namen.map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(2)) {
            val such = api.anfrage(FirmaWikidata.suchAdresse(n, "en") { java.net.URLEncoder.encode(it, "UTF-8") }, NasdaqKalender.KOPF)
            val ids = such.takeIf { it.ok }?.text?.let { FirmaWikidata.treffer(it) }?.take(5) ?: continue
            antwortete = true
            if (ids.isEmpty()) continue
            val e = api.anfrage(FirmaWikidata.eintraegeAdresse(ids, "claims"), NasdaqKalender.KOPF)
            val reihe = e.takeIf { it.ok }?.text?.let { de.troetenrepublik.app.engine.MitarbeiterReihe.lesen(it, ids, symbol) } ?: continue
            merken("ma|$symbol", reihe)
            return reihe
        }
        if (!antwortete) return null
        merken("ma|$symbol", emptyList<de.troetenrepublik.app.engine.MitarbeiterReihe.Punkt>())
        return emptyList()
    }

    // ------------------------------------------------------------ Nasdaq: Leerverkauf

    // Meldungen zum Leerverkaufsbestand (zweimal im Monat, etwa ein Jahr); nur fuer an der Nasdaq notierte Werte,
    // sonst leer. null bei Fehler oder in der Pause nach Fehlern.
    suspend fun leerverkauf(symbol: String): List<Historie078.Leer>? =
        nasdaq.holen(Historie078.leerAdresse(symbol.replace('-', '.')), { _ -> 6 * STUNDE }, Historie078::leerLesen)

    // ------------------------------------------------------------ SEC: Fail-to-Deliver

    // Fail-to-Deliver der letzten Halbmonate (Menge je Tag) aus den Dateien der SEC; null, wenn keine Datei lesbar war.
    @Suppress("UNCHECKED_CAST")
    suspend fun ftd(symbol: String, kennung: String, heute: LocalDate, dateien: Int = 6): List<Historie078.Ftd>? {
        (frisch("ftd|$symbol", 12 * STUNDE) as? List<Historie078.Ftd>)?.let { return it }
        val kuerzel = Historie078.ftdKuerzel(symbol)
        val kopf = mapOf("User-Agent" to kennung, "Accept" to "*/*")
        val aus = ArrayList<Historie078.Ftd>()
        var gelesen = 0
        for (url in Historie078.ftdAdressen(heute, dateien)) {
            delay(150)
            val zip = runCatching { api.datei(url, 8_000_000, kopf) }.getOrNull() ?: continue
            val text = runCatching {
                java.util.zip.ZipInputStream(zip.inputStream()).use { z ->
                    z.nextEntry ?: return@use null
                    z.readBytes().decodeToString()
                }
            }.getOrNull() ?: continue
            gelesen++
            aus += Historie078.ftdLesen(text, kuerzel)
        }
        if (gelesen == 0) return null
        val r = aus.sortedBy { it.tag }.distinctBy { it.tag }
        merken("ftd|$symbol", r)
        return r
    }

    // ------------------------------------------------------------ Nasdaq Trader: Reg SHO

    // Schwellenliste der Nasdaq (Schwellenwerte nach Regulation SHO) des juengsten verfuegbaren Handelstags.
    // Nur Nasdaq-Werte stehen darin. null ohne lesbare Liste.
    @Suppress("UNCHECKED_CAST")
    suspend fun regShoListe(heute: LocalDate): Set<String>? {
        (frisch("regsho", 6 * STUNDE) as? Set<String>)?.let { return it }
        for (tag in Historie078.regShoTage(heute, 4)) {
            val a = runCatching { api.anfrage(Historie078.regShoAdresse(tag), NasdaqKalender.KOPF) }.getOrNull() ?: continue
            val liste = a.takeIf { it.ok }?.text?.let { Historie078.regShoLesen(it) } ?: continue
            merken("regsho", liste)
            return liste
        }
        return null
    }
}
