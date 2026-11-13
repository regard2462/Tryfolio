package de.troetenrepublik.app.engine

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

// Bilanzqualitaet aus Jahresabschluessen: Piotroski-F-Score (neun Ja-Nein-Pruefungen zu Gewinn, Verschuldung und
// Effizienz, Joseph Piotroski 2000) und Altman-Z-Score (Insolvenznaehe aus Bilanzkennzahlen, Edward Altman: Fassung
// von 1968 fuer boersennotierte Hersteller, Z'' fuer andere Firmen). Fuer Banken, Versicherer und andere Finanzwerte
// passen beide nicht, weil deren Bilanzen anders aufgebaut sind, fuer Immobiliengesellschaften Altman nicht.
// Betraege in der Berichtswaehrung.
object Qualitaet {

    // Felder des Yahoo-Endpunkts ws/fundamentals-timeseries (Jahreswerte).
    val FELDER = listOf(
        "annualNetIncome", "annualTotalAssets", "annualOperatingCashFlow", "annualLongTermDebt", "annualCurrentAssets",
        "annualCurrentLiabilities", "annualOrdinarySharesNumber", "annualShareIssued", "annualGrossProfit", "annualTotalRevenue",
        "annualWorkingCapital", "annualRetainedEarnings", "annualEBIT", "annualOperatingIncome", "annualTotalLiabilitiesNetMinorityInterest",
        "annualInterestExpense", "annualTaxRateForCalcs", "annualInvestedCapital", "annualCapitalExpenditure", "annualFreeCashFlow",
        "trailingOperatingCashFlow", "trailingFreeCashFlow", "annualDilutedEPS",
        "annualNetPPE", "annualTotalDebt", "annualCashCashEquivalentsAndShortTermInvestments", "annualCashAndCashEquivalents",
        // [0.78] Beneish-M-Score (Beneish.kt)
        "annualAccountsReceivable", "annualReceivables", "annualDepreciationAndAmortization", "annualSellingGeneralAndAdministration",
        "annualCurrentDebt", "annualIncomeTaxPayable",
    )

    // Ein Geschaeftsjahr; null, wo die Angabe fehlt. Serialisierbar fuer den Zwischenspeicher des Aktienfinders.
    @Serializable
    data class Jahr(
        val ende: String,
        val waehrung: String = "",
        val nettogewinn: Double? = null,
        val bilanzsumme: Double? = null,
        val cashflow: Double? = null,
        val langfristSchulden: Double? = null,
        val umlaufvermoegen: Double? = null,
        val kurzfristSchulden: Double? = null,
        val aktien: Double? = null,
        val bruttogewinn: Double? = null,
        val umsatz: Double? = null,
        val betriebskapital: Double? = null,
        val gewinnruecklagen: Double? = null,
        val ebit: Double? = null,
        val verbindlichkeiten: Double? = null,
        val zinsaufwand: Double? = null,
        val steuersatz: Double? = null,
        val investiertesKapital: Double? = null,
        val capex: Double? = null,
        val freierCashflow: Double? = null,
        // Operativer Cashflow der letzten zwoelf Monate (Typ mit Praefix trailing), gehoert zum juengsten Jahr
        val cashflowLaufend: Double? = null,
        // Freier Cashflow der letzten zwoelf Monate (operativer Cashflow minus Investitionen, seit 5.94)
        val freierCashflowLaufend: Double? = null,
        // Ausgegebene Aktien samt eigenen Aktien im Bestand (0.77); aktien sind die Aktien im Umlauf
        val aktienAusgegeben: Double? = null,
        // Verwaesserter Gewinn je Aktie des Geschaeftsjahrs (Yahoo rechnet ihn meist auf spaetere Splits um)
        val gewinnJeAktie: Double? = null,
        // [0.78] Fuer die Auswahlraster im Aktienfinder: Sachanlagen netto, verzinsliche Schulden, Kasse samt kurzfristigen Anlagen
        val sachanlagen: Double? = null,
        val gesamtschulden: Double? = null,
        val kasse: Double? = null,
        // [0.78] Fuer den Beneish-M-Score: Forderungen aus Lieferungen und Leistungen, Abschreibungen (samt denen auf
        // immaterielle Werte), Vertriebs- und Verwaltungskosten, kurzfristige Finanzschulden, Steuerschulden
        val forderungen: Double? = null,
        val abschreibungen: Double? = null,
        val vertriebVerwaltung: Double? = null,
        val kurzfristFinanzschulden: Double? = null,
        val steuerschuld: Double? = null,
    )

    // Liest die Antwort von ws/fundamentals-timeseries: je Feld eine Liste von Jahreswerten mit asOfDate und
    // reportedValue.raw (einzelne Eintraege koennen null sein). Ergebnis nach Jahresende sortiert, aelteste zuerst.
    fun lesen(text: String): List<Jahr> {
        val wurzel = runCatching { Json.parseToJsonElement(text) as? JsonObject }.getOrNull() ?: return emptyList()
        val ergebnis = ((wurzel["timeseries"] as? JsonObject)?.get("result") as? JsonArray).orEmpty()
        val werte = HashMap<String, HashMap<String, Double>>()
        val waehrung = HashMap<String, String>()
        // Werte mit Praefix trailing (letzte zwoelf Monate) gehoeren zu keinem Stichtag: je Typ zaehlt der juengste Stand
        val laufend = HashMap<String, Pair<String, Double>>()
        for (e in ergebnis) {
            val o = e as? JsonObject ?: continue
            val typ = ((o["meta"] as? JsonObject)?.get("type") as? JsonArray)?.firstOrNull()?.let { (it as? JsonPrimitive)?.content } ?: continue
            for (p in (o[typ] as? JsonArray).orEmpty()) {
                val po = p as? JsonObject ?: continue
                val datum = (po["asOfDate"] as? JsonPrimitive)?.content ?: continue
                val roh = ((po["reportedValue"] as? JsonObject)?.get("raw") as? JsonPrimitive)?.content?.toDoubleOrNull() ?: continue
                if (!roh.isFinite()) continue
                if (typ.startsWith("trailing")) {
                    val alt = laufend[typ]
                    if (alt == null || datum > alt.first) laufend[typ] = datum to roh
                    continue
                }
                werte.getOrPut(datum) { HashMap() }[typ] = roh
                (po["currencyCode"] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() && typ != "annualOrdinarySharesNumber" && typ != "annualShareIssued" }
                    ?.let { waehrung[datum] = it }
            }
        }
        val jahre = werte.keys.sorted().map { d ->
            val f = werte.getValue(d)
            Jahr(
                ende = d, waehrung = waehrung[d].orEmpty(),
                nettogewinn = f["annualNetIncome"], bilanzsumme = f["annualTotalAssets"], cashflow = f["annualOperatingCashFlow"],
                langfristSchulden = f["annualLongTermDebt"], umlaufvermoegen = f["annualCurrentAssets"],
                kurzfristSchulden = f["annualCurrentLiabilities"], aktien = f["annualOrdinarySharesNumber"] ?: f["annualShareIssued"],
                bruttogewinn = f["annualGrossProfit"], umsatz = f["annualTotalRevenue"], betriebskapital = f["annualWorkingCapital"],
                gewinnruecklagen = f["annualRetainedEarnings"], ebit = f["annualEBIT"] ?: f["annualOperatingIncome"],
                verbindlichkeiten = f["annualTotalLiabilitiesNetMinorityInterest"], zinsaufwand = f["annualInterestExpense"],
                steuersatz = f["annualTaxRateForCalcs"], investiertesKapital = f["annualInvestedCapital"], capex = f["annualCapitalExpenditure"],
                freierCashflow = f["annualFreeCashFlow"], aktienAusgegeben = f["annualShareIssued"], gewinnJeAktie = f["annualDilutedEPS"],
                sachanlagen = f["annualNetPPE"], gesamtschulden = f["annualTotalDebt"],
                kasse = f["annualCashCashEquivalentsAndShortTermInvestments"] ?: f["annualCashAndCashEquivalents"],
                forderungen = f["annualAccountsReceivable"] ?: f["annualReceivables"], abschreibungen = f["annualDepreciationAndAmortization"],
                vertriebVerwaltung = f["annualSellingGeneralAndAdministration"], kurzfristFinanzschulden = f["annualCurrentDebt"],
                steuerschuld = f["annualIncomeTaxPayable"],
            )
        }
        // Laufender Cashflow gehoert zum juengsten Jahr, nicht zu einem eigenen Stichtag
        val cf = laufend["trailingOperatingCashFlow"]
        val fcf = laufend["trailingFreeCashFlow"]
        return if (jahre.isNotEmpty() && (cf != null || fcf != null))
            jahre.dropLast(1) + jahre.last().copy(cashflowLaufend = cf?.second, freierCashflowLaufend = fcf?.second) else jahre
    }

    // Banken, Versicherer und Finanzdienstleister (Sektor und Branche wie bei Yahoo).
    fun ausgenommen(sektor: String, branche: String): Boolean {
        val s = sektor.lowercase()
        val b = branche.lowercase()
        return s.contains("financial") || listOf("bank", "insurance", "financial", "capital markets", "asset management", "credit services", "mortgage")
            .any { b.contains(it) }
    }

    // Piotroski

    enum class Punkt { RENDITE, CASHFLOW, RENDITE_STEIGT, CASHFLOW_UEBER_GEWINN, SCHULDEN_SINKEN, LIQUIDITAET_STEIGT, KEINE_NEUEN_AKTIEN, MARGE_STEIGT, UMSCHLAG_STEIGT }

    // Eine Pruefung: erfuellt (null = nicht pruefbar), dazu der Wert im letzten und im Vorjahr fuer die Begruendung.
    data class Pruefung(val punkt: Punkt, val erfuellt: Boolean?, val jetzt: Double? = null, val vorher: Double? = null)

    data class Piotroski(val jahr: String, val vorjahr: String, val pruefungen: List<Pruefung>) {
        val score: Int get() = pruefungen.count { it.erfuellt == true }
        val geprueft: Int get() = pruefungen.count { it.erfuellt != null }
    }

    // Spielraum bei der Aktienzahl (0.77): bis zu einem halben Prozent mehr zaehlt als unveraendert. So viel kommt durch
    // Mitarbeiterprogramme und gerundete Angaben zusammen, ohne dass die Firma Kapital aufnimmt.
    const val AKTIEN_SPIELRAUM = 0.005

    private fun teil(a: Double?, b: Double?): Double? = if (a != null && b != null && b != 0.0) a / b else null

    // Neun Pruefungen fuer das juengste Jahr gegen das Vorjahr. Renditen auf die Bilanzsumme zu Jahresbeginn (also
    // die des Vorjahres), wenn vorhanden, sonst zum Jahresende. null mit weniger als zwei Jahren.
    fun piotroski(jahre: List<Jahr>): Piotroski? {
        if (jahre.size < 2) return null
        val j = jahre[jahre.size - 1]
        val v = jahre[jahre.size - 2]
        val vv = jahre.getOrNull(jahre.size - 3)
        val basis = v.bilanzsumme ?: j.bilanzsumme
        val basisVorher = vv?.bilanzsumme ?: v.bilanzsumme
        val roa = teil(j.nettogewinn, basis)
        val roaVorher = teil(v.nettogewinn, basisVorher)
        val cfoQuote = teil(j.cashflow, basis)
        // Langfristige Schulden je durchschnittlicher Bilanzsumme (Piotroski), ohne Vorjahressumme zum Jahresende
        fun mittel(a: Double?, b: Double?) = if (a != null && b != null) (a + b) / 2 else a
        val hebel = (j.langfristSchulden ?: 0.0).takeIf { j.bilanzsumme != null }?.let { teil(it, mittel(j.bilanzsumme, v.bilanzsumme)) }
        val hebelVorher = (v.langfristSchulden ?: 0.0).takeIf { v.bilanzsumme != null }?.let { teil(it, mittel(v.bilanzsumme, vv?.bilanzsumme)) }
        // Beide ohne Angabe zu langfristigen Schulden: nicht pruefbar
        val hebelPruefbar = j.langfristSchulden != null || v.langfristSchulden != null
        val liq = teil(j.umlaufvermoegen, j.kurzfristSchulden)
        val liqVorher = teil(v.umlaufvermoegen, v.kurzfristSchulden)
        val marge = teil(j.bruttogewinn, j.umsatz)
        val margeVorher = teil(v.bruttogewinn, v.umsatz)
        val umschlag = teil(j.umsatz, basis)
        val umschlagVorher = teil(v.umsatz, basisVorher)
        fun groesser(a: Double?, b: Double?): Boolean? = if (a != null && b != null) a > b else null
        // Neue Aktien: gezaehlt werden die ausgegebenen Aktien. Die Aktien im Umlauf steigen auch, wenn die Firma eigene
        // Aktien aus dem Bestand abgibt (Mitarbeiterprogramme); das ist keine Ausgabe neuer Aktien. Ohne die Angabe in
        // beiden Jahren zaehlen die Aktien im Umlauf.
        val ausgegeben = j.aktienAusgegeben != null && v.aktienAusgegeben != null
        val stueck = if (ausgegeben) j.aktienAusgegeben else j.aktien
        val stueckVorher = if (ausgegeben) v.aktienAusgegeben else v.aktien
        return Piotroski(
            j.ende, v.ende,
            listOf(
                Pruefung(Punkt.RENDITE, roa?.let { it > 0 }, roa),
                Pruefung(Punkt.CASHFLOW, j.cashflow?.let { it > 0 }, j.cashflow),
                Pruefung(Punkt.RENDITE_STEIGT, groesser(roa, roaVorher), roa, roaVorher),
                Pruefung(Punkt.CASHFLOW_UEBER_GEWINN, groesser(cfoQuote, roa), cfoQuote, roa),
                // Schulden: gesunken (ohne Schulden in beiden Jahren gilt der Punkt als erfuellt)
                Pruefung(Punkt.SCHULDEN_SINKEN, if (hebelPruefbar && hebel != null && hebelVorher != null) hebel < hebelVorher || (hebel == 0.0 && hebelVorher == 0.0) else null, hebel, hebelVorher),
                Pruefung(Punkt.LIQUIDITAET_STEIGT, groesser(liq, liqVorher), liq, liqVorher),
                Pruefung(Punkt.KEINE_NEUEN_AKTIEN, if (stueck != null && stueckVorher != null) stueck <= stueckVorher * (1 + AKTIEN_SPIELRAUM) else null, stueck, stueckVorher),
                Pruefung(Punkt.MARGE_STEIGT, groesser(marge, margeVorher), marge, margeVorher),
                Pruefung(Punkt.UMSCHLAG_STEIGT, groesser(umschlag, umschlagVorher), umschlag, umschlagVorher),
            ),
        )
    }

    // Einordnung: 7 bis 9 stark, 4 bis 6 mittel, 0 bis 3 schwach (gemessen an den pruefbaren Punkten).
    enum class Stufe { STARK, MITTEL, SCHWACH }

    fun stufe(p: Piotroski): Stufe? {
        if (p.geprueft < 6) return null
        val skaliert = p.score * 9.0 / p.geprueft
        return when {
            skaliert >= 7.0 -> Stufe.STARK
            skaliert >= 4.0 -> Stufe.MITTEL
            else -> Stufe.SCHWACH
        }
    }

    // Altman

    enum class Zone { SICHER, GRAU, GEFAEHRDET }

    // HERSTELLER: Fassung von 1968 mit Umsatz und Boersenwert. ANDERE: Z'' fuer Nichtproduzenten (Altman, Hartzell,
    // Peck 1995; Altman u. a. 2017) mit Buchwert statt Boersenwert und ohne Umsatz, der anlagearme Firmen hebt.
    enum class Fassung { HERSTELLER, ANDERE }

    private val NICHT_HERSTELLER = Regex("retail|restaurant|software|services|internet|travel|lodging|leisure|gambling|resort|education|staffing|consulting|airline|rental|trucking|railroad|shipping|freight|logistics|biotechnology|health care plans|healthcare plans|medical care|medical distribution|diagnostics|utilit|telecom|entertainment|broadcasting|publishing|advertising|waste|security & protection|personal services")

    // Fassung nach Sektor und Branche wie bei Yahoo; null fuer Immobilien (und Finanzwerte, siehe ausgenommen).
    fun fassung(sektor: String, branche: String): Fassung? {
        val s = sektor.lowercase()
        val b = branche.lowercase()
        if (s.contains("real estate") || b.contains("reit") || ausgenommen(sektor, branche)) return null
        val herstellerSektor = listOf("industrials", "basic materials", "energy", "consumer cyclical", "consumer defensive", "technology", "healthcare")
            .any { s.contains(it) }
        return if (herstellerSektor && !NICHT_HERSTELLER.containsMatchIn(b)) Fassung.HERSTELLER else Fassung.ANDERE
    }

    private val HERSTELLER_BRANCHEN = setOf(
        "ALKOHOL_TABAK", "BAU", "BERGBAU", "CHEMIE", "COMPUTER", "ELEKTRO", "HALBLEITER", "FAHRZEUGE", "LUFTFAHRT", "FORST",
        "KONSUM", "MASCHINEN", "NAHRUNG", "PHARMA", "ROHSTOFFE", "TEXTIL", "OEL_GAS",
    )

    // Fassung nach den Branchen des Aktienfinders; null fuer Immobilien, Banken und Versicherer.
    fun fassungFinder(branche: String): Fassung? = when (branche) {
        "IMMOBILIEN", "BANKEN", "VERSICHERUNG" -> null
        in HERSTELLER_BRANCHEN, "" -> Fassung.HERSTELLER
        else -> Fassung.ANDERE
    }

    // Hersteller: ueber 2,99 sicher, unter 1,81 gefaehrdet. Andere (Z''): ueber 2,6 sicher, unter 1,1 gefaehrdet.
    fun zone(z: Double, fassung: Fassung = Fassung.HERSTELLER): Zone = when (fassung) {
        Fassung.HERSTELLER -> when {
            z > 2.99 -> Zone.SICHER
            z >= 1.81 -> Zone.GRAU
            else -> Zone.GEFAEHRDET
        }
        Fassung.ANDERE -> when {
            z > 2.6 -> Zone.SICHER
            z >= 1.1 -> Zone.GRAU
            else -> Zone.GEFAEHRDET
        }
    }

    // Die Bausteine mit ihrem Gewicht: Betriebskapital, Gewinnruecklagen, EBIT und Umsatz je Bilanzsumme, Boersenwert
    // (bei ANDERE Buchwert des Eigenkapitals) je Verbindlichkeiten; umsatz ist bei ANDERE 0.
    data class Altman(val z: Double, val zone: Zone, val betriebskapital: Double, val ruecklagen: Double, val ebit: Double, val boersenwert: Double, val umsatz: Double, val fassung: Fassung = Fassung.HERSTELLER)

    // boersenwert in der Berichtswaehrung (nur bei HERSTELLER noetig). Fehlt das Betriebskapital, gilt Umlaufvermoegen
    // minus kurzfristige Schulden. null, wenn ein Baustein fehlt.
    fun altman(j: Jahr, boersenwert: Double?, fassung: Fassung = Fassung.HERSTELLER): Altman? {
        val ta = j.bilanzsumme?.takeIf { it > 0 } ?: return null
        val tl = j.verbindlichkeiten?.takeIf { it > 0 } ?: return null
        val wc = j.betriebskapital ?: j.umlaufvermoegen?.let { a -> j.kurzfristSchulden?.let { a - it } } ?: return null
        val re = j.gewinnruecklagen ?: return null
        val ebit = j.ebit ?: return null
        if (fassung == Fassung.ANDERE) {
            val a = 6.56 * wc / ta
            val b = 3.26 * re / ta
            val c = 6.72 * ebit / ta
            val d = 1.05 * (ta - tl) / tl
            val z = a + b + c + d
            return if (z.isFinite()) Altman(z, zone(z, fassung), a, b, c, d, 0.0, fassung) else null
        }
        val mv = boersenwert?.takeIf { it > 0 && it.isFinite() } ?: return null
        val umsatz = j.umsatz ?: return null
        val a = 1.2 * wc / ta
        val b = 1.4 * re / ta
        val c = 3.3 * ebit / ta
        val d = 0.6 * mv / tl
        val e = 1.0 * umsatz / ta
        val z = a + b + c + d + e
        return if (z.isFinite()) Altman(z, zone(z), a, b, c, d, e) else null
    }
}
