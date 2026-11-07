package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.Candle
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.File
import java.time.LocalDate
import java.util.zip.ZipFile

// Rueckblick-Test des Aktienfinders (5.92): bewertet Aktien an frueheren Stichtagen nur mit dem, was damals bekannt
// war. Hier die Stichtag-Logik: Kurse bis zum Stichtag, Jahresabschluesse je Feld mit dem Tag, ab dem sie bekannt
// waren, Analystenurteile bis zum Stichtag. Daraus entstehen die Eingaben der echten Engine (Aktienfinder.bewerten).
// Abruf und Ablauf stehen im Werkzeug :desktop:finderRueckblick, die Messung in FinderRueckblickMessung.kt.
object FinderRueckblick {

    const val TAG_MS = 86_400_000L

    // Yahoo nennt kein Veroeffentlichungsdatum: ein Jahreswert gilt vorsichtig erst 90 Tage nach dem Periodenende.
    const val VERZUG_TAGE = 90L

    // Netto-Hochstufungen zaehlen aus den letzten 90 Tagen, eine Abdeckung durch Analysten aus dem letzten Jahr.
    const val ANALYSTEN_TAGE = 90L
    const val ABDECKUNG_TAGE = 365L

    // Ein Kurs gilt am Stichtag nur, wenn er hoechstens so alt ist (sonst wird der Wert nicht mehr gehandelt).
    const val KURS_ALTER_TAGE = 7L

    // Ein Abschluss gilt hoechstens so lange nach dem Periodenende (danach fehlen neuere Zahlen).
    const val ABSCHLUSS_ALTER_TAGE = 550L

    // Urteile, die aelter sind, zaehlen fuer die Trefferbilanz der Haeuser nicht mehr (spart Rechenzeit).
    const val URTEILE_JAHRE = 5L

    // Schluessel in Wert.rendite und Stichtag.msci/spx fuer die Rendite vom Stichtag bis heute (kaufen und halten).
    const val BIS_HEUTE = -1

    // Bereiche, die sich rueckwirkend pruefen lassen. Insider, Leerverkaeufe und Stimmung fehlen fuer fruehere Jahre.
    val IM_TEST = listOf(Aktienfinder.Bereich.BEWERTUNG, Aktienfinder.Bereich.QUALITAET, Aktienfinder.Bereich.ANALYSTEN, Aktienfinder.Bereich.TREND)

    // Standardgewichte im Test: die nicht pruefbaren Bereiche fallen weg, die Engine rechnet die uebrigen anteilig hoch.
    fun testGewichte(g: Map<Aktienfinder.Bereich, Double> = Aktienfinder.Bereich.entries.associateWith { it.gewicht.toDouble() }): Map<Aktienfinder.Bereich, Double> =
        Aktienfinder.Bereich.entries.associateWith { if (it in IM_TEST) g[it] ?: 0.0 else 0.0 }

    fun tag(ms: Long): Long = Math.floorDiv(ms, TAG_MS)

    fun tag(datum: String): Long? = runCatching { LocalDate.parse(datum.take(10)).toEpochDay() }.getOrNull()

    fun datum(tag: Long): String = LocalDate.ofEpochDay(tag).toString()

    // Index der letzten Kerze am Tag tag oder davor, -1 ohne.
    fun bisTag(kurse: List<Candle>, tag: Long): Int {
        var lo = 0
        var hi = kurse.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (tag(kurse[mid].t) <= tag) lo = mid + 1 else hi = mid
        }
        return lo - 1
    }

    // Kurs am Stichtag (hoechstens KURS_ALTER_TAGE alt), sonst null.
    fun kursZum(kurse: List<Candle>, tag: Long): Double? {
        val i = bisTag(kurse, tag)
        if (i < 0 || tag - tag(kurse[i].t) > KURS_ALTER_TAGE) return null
        return kurse[i].close.takeIf { it > 0 && it.isFinite() }
    }

    // ------------------------------------------------------------ Abschluesse

    enum class Feld {
        NETTOGEWINN, BILANZSUMME, CASHFLOW, LANGFRIST_SCHULDEN, UMLAUF, KURZFRIST_SCHULDEN, AKTIEN, BRUTTOGEWINN, UMSATZ,
        BETRIEBSKAPITAL, RUECKLAGEN, EBIT, VERBINDLICHKEITEN, EPS, EIGENKAPITAL, SCHULDEN,
    }

    // Ein Wert mit dem Tag (seit 1970), ab dem er bekannt war.
    data class Wert(val bekannt: Long, val wert: Double)

    // Ein Geschaeftsjahr: Periodenende und je Feld der Wert. Je Aktie und Feld ist der Wert auf den heutigen
    // Aktienstand umgerechnet (Splits), wie die Kurse von Yahoo.
    data class Abschluss(val ende: String, val felder: Map<Feld, Wert>)

    // Was am Stichtag bekannt war: bis zu drei Jahre, aelteste zuerst, je Jahr nur die Felder, die bekannt waren.
    data class Stand(val enden: List<String>, val werte: List<Map<Feld, Double>>) {
        val letztes: Map<Feld, Double> get() = werte.last()
        val vorjahr: Map<Feld, Double>? get() = werte.getOrNull(werte.size - 2)

        // Die Jahre fuer Piotroski und Altman (Qualitaet).
        val jahre: List<Qualitaet.Jahr> get() = enden.indices.map { i ->
            val m = werte[i]
            Qualitaet.Jahr(
                ende = enden[i], nettogewinn = m[Feld.NETTOGEWINN], bilanzsumme = m[Feld.BILANZSUMME], cashflow = m[Feld.CASHFLOW],
                langfristSchulden = m[Feld.LANGFRIST_SCHULDEN], umlaufvermoegen = m[Feld.UMLAUF], kurzfristSchulden = m[Feld.KURZFRIST_SCHULDEN],
                aktien = m[Feld.AKTIEN], bruttogewinn = m[Feld.BRUTTOGEWINN], umsatz = m[Feld.UMSATZ], betriebskapital = m[Feld.BETRIEBSKAPITAL],
                gewinnruecklagen = m[Feld.RUECKLAGEN], ebit = m[Feld.EBIT], verbindlichkeiten = m[Feld.VERBINDLICHKEITEN],
            )
        }
    }

    // Stand zum Stichtag: nur Felder, die bis einschliesslich tag bekannt waren; ein Jahr zaehlt mit Gewinn oder Umsatz.
    // null ohne Jahr oder wenn das juengste Jahr zu alt ist.
    fun standZum(abschluesse: List<Abschluss>, tag: Long): Stand? {
        val l = abschluesse.sortedBy { it.ende }.mapNotNull { a ->
            val ende = tag(a.ende) ?: return@mapNotNull null
            if (ende > tag) return@mapNotNull null
            val m = HashMap<Feld, Double>()
            a.felder.forEach { (f, w) -> if (w.bekannt <= tag && w.wert.isFinite()) m[f] = w.wert }
            if (Feld.NETTOGEWINN in m || Feld.UMSATZ in m) a.ende to m else null
        }.takeLast(FinderBilanz.JAHRE)
        if (l.isEmpty()) return null
        if (tag - tag(l.last().first)!! > ABSCHLUSS_ALTER_TAGE) return null
        return Stand(l.map { it.first }, l.map { it.second })
    }

    // Aktienstand-Faktor: Produkt der Splits nach dem Tag (ein Wert von damals mal Faktor = heutige Stueckzahl).
    fun splitFaktor(splits: List<Splits.Split>, nachTag: Long): Double =
        splits.filter { tag(it.t) > nachTag && it.verhaeltnis > 0 }.fold(1.0) { f, s -> f * s.verhaeltnis }

    // Felder des Yahoo-Endpunkts ws/fundamentals-timeseries (Jahreswerte) mit ihrem Feld; spaetere sind Ersatz.
    val YAHOO_FELDER: List<Pair<String, Feld>> = listOf(
        "annualNetIncome" to Feld.NETTOGEWINN, "annualTotalAssets" to Feld.BILANZSUMME, "annualOperatingCashFlow" to Feld.CASHFLOW,
        "annualLongTermDebt" to Feld.LANGFRIST_SCHULDEN, "annualCurrentAssets" to Feld.UMLAUF, "annualCurrentLiabilities" to Feld.KURZFRIST_SCHULDEN,
        "annualOrdinarySharesNumber" to Feld.AKTIEN, "annualShareIssued" to Feld.AKTIEN, "annualGrossProfit" to Feld.BRUTTOGEWINN,
        "annualTotalRevenue" to Feld.UMSATZ, "annualWorkingCapital" to Feld.BETRIEBSKAPITAL, "annualRetainedEarnings" to Feld.RUECKLAGEN,
        "annualEBIT" to Feld.EBIT, "annualOperatingIncome" to Feld.EBIT, "annualTotalLiabilitiesNetMinorityInterest" to Feld.VERBINDLICHKEITEN,
        "annualDilutedEPS" to Feld.EPS, "annualBasicEPS" to Feld.EPS, "annualStockholdersEquity" to Feld.EIGENKAPITAL, "annualTotalDebt" to Feld.SCHULDEN,
    )

    // Liest die Antwort von ws/fundamentals-timeseries. Yahoo nennt Gewinn je Aktie und Aktienzahl schon auf dem
    // heutigen Aktienstand. Bekannt gilt ein Wert VERZUG_TAGE nach dem Periodenende.
    fun ausYahoo(text: String): List<Abschluss> {
        val wurzel = runCatching { Json.parseToJsonElement(text) as? JsonObject }.getOrNull() ?: return emptyList()
        val ergebnis = ((wurzel["timeseries"] as? JsonObject)?.get("result") as? JsonArray).orEmpty()
        val roh = HashMap<String, HashMap<String, Double>>()
        for (e in ergebnis) {
            val o = e as? JsonObject ?: continue
            val typ = ((o["meta"] as? JsonObject)?.get("type") as? JsonArray)?.firstOrNull()?.let { (it as? JsonPrimitive)?.content } ?: continue
            for (p in (o[typ] as? JsonArray).orEmpty()) {
                val po = p as? JsonObject ?: continue
                val d = (po["asOfDate"] as? JsonPrimitive)?.content ?: continue
                val v = ((po["reportedValue"] as? JsonObject)?.get("raw") as? JsonPrimitive)?.content?.toDoubleOrNull() ?: continue
                if (v.isFinite()) roh.getOrPut(d) { HashMap() }[typ] = v
            }
        }
        return roh.keys.sorted().mapNotNull { d ->
            val ende = tag(d) ?: return@mapNotNull null
            val f = roh.getValue(d)
            val m = HashMap<Feld, Wert>()
            for ((typ, feld) in YAHOO_FELDER) if (feld !in m) f[typ]?.let { m[feld] = Wert(ende + VERZUG_TAGE, it) }
            Abschluss(d, m)
        }
    }

    // Kennzahlen der SEC-Bulkdatei companyfacts (us-gaap, dei) je Feld, spaetere sind Ersatz.
    val FAKTEN: Map<Feld, List<String>> = mapOf(
        Feld.NETTOGEWINN to listOf("NetIncomeLoss", "ProfitLoss", "NetIncomeLossAvailableToCommonStockholdersBasic"),
        Feld.BILANZSUMME to listOf("Assets"),
        Feld.CASHFLOW to listOf("NetCashProvidedByUsedInOperatingActivities", "NetCashProvidedByUsedInOperatingActivitiesContinuingOperations"),
        Feld.LANGFRIST_SCHULDEN to listOf("LongTermDebtNoncurrent", "LongTermDebt"),
        Feld.UMLAUF to listOf("AssetsCurrent"),
        Feld.KURZFRIST_SCHULDEN to listOf("LiabilitiesCurrent"),
        Feld.AKTIEN to listOf("CommonStockSharesOutstanding", "WeightedAverageNumberOfDilutedSharesOutstanding"),
        Feld.BRUTTOGEWINN to listOf("GrossProfit"),
        Feld.UMSATZ to listOf("Revenues", "RevenueFromContractWithCustomerExcludingAssessedTax", "SalesRevenueNet", "RevenueFromContractWithCustomerIncludingAssessedTax"),
        Feld.RUECKLAGEN to listOf("RetainedEarningsAccumulatedDeficit"),
        Feld.EBIT to listOf("OperatingIncomeLoss"),
        Feld.VERBINDLICHKEITEN to listOf("Liabilities"),
        Feld.EPS to listOf("EarningsPerShareDiluted", "EarningsPerShareBasic"),
        Feld.EIGENKAPITAL to listOf("StockholdersEquity", "StockholdersEquityIncludingPortionAttributableToNoncontrollingInterest"),
        Feld.SCHULDEN to listOf("LongTermDebt", "LongTermDebtNoncurrent"),
    )

    // Ein Eintrag der companyfacts: Periode (start fehlt bei Bestandsgroessen), Wert, Formular und Einreichungstag.
    private data class Fakt(val start: Long?, val ende: Long, val wert: Double, val form: String, val fp: String, val filed: Long, val accn: String)

    private fun jahresFormular(form: String): Boolean = form.startsWith("10-K") || form.startsWith("20-F") || form.startsWith("40-F")

    // Liest eine companyfacts-Datei (JSON einer Firma): Name der Firma und Jahresabschluesse. Je Feld und Periodenende
    // gilt der zuerst eingereichte Wert mit seinem Einreichungstag (filed), spaetere Korrekturen bleiben aussen vor.
    // Gewinn je Aktie und Aktienzahl werden mit den Splits nach dem Einreichungstag auf den heutigen Stand gebracht.
    fun ausFakten(text: String, splits: List<Splits.Split> = emptyList()): Pair<String, List<Abschluss>>? {
        val wurzel = runCatching { Json.parseToJsonElement(text) as? JsonObject }.getOrNull() ?: return null
        val name = (wurzel["entityName"] as? JsonPrimitive)?.content.orEmpty()
        val facts = wurzel["facts"] as? JsonObject ?: return name to emptyList()
        val gaap = facts["us-gaap"] as? JsonObject
        val dei = facts["dei"] as? JsonObject
        fun eintraege(quelle: JsonObject?, tagName: String): List<Fakt> {
            val units = ((quelle?.get(tagName) as? JsonObject)?.get("units") as? JsonObject) ?: return emptyList()
            val aus = ArrayList<Fakt>()
            for ((einheit, liste) in units) {
                if (einheit != "USD" && einheit != "USD/shares" && einheit != "shares") continue
                for (e in (liste as? JsonArray).orEmpty()) {
                    val o = e as? JsonObject ?: continue
                    fun s(k: String) = (o[k] as? JsonPrimitive)?.content
                    val ende = s("end")?.let { tag(it) } ?: continue
                    val filed = s("filed")?.let { tag(it) } ?: continue
                    val wert = s("val")?.toDoubleOrNull()?.takeIf { it.isFinite() } ?: continue
                    aus += Fakt(s("start")?.let { tag(it) }, ende, wert, s("form").orEmpty(), s("fp").orEmpty(), filed, s("accn").orEmpty())
                }
            }
            return aus
        }
        // Jahreswerte: aus einem Jahresbericht; Zeitraumgroessen ueber etwa ein Jahr
        fun jahreswerte(tagName: String, quelle: JsonObject? = gaap): Map<Long, Fakt> {
            val aus = HashMap<Long, Fakt>()
            for (f in eintraege(quelle, tagName)) {
                if (!jahresFormular(f.form)) continue
                if (f.start != null && (f.ende - f.start) !in 300L..400L) continue
                val alt = aus[f.ende]
                if (alt == null || f.filed < alt.filed) aus[f.ende] = f
            }
            return aus
        }
        val je = FAKTEN.mapValues { (_, tags) -> tags.map { jahreswerte(it) } }
        // Geschaeftsjahre: Perioden mit Gewinn oder Umsatz ueber ein Jahr
        val enden = (je.getValue(Feld.NETTOGEWINN) + je.getValue(Feld.UMSATZ)).flatMap { it.keys }.toSortedSet()
        // Aktienzahl vom Deckblatt desselben Berichts, wenn sie zum Periodenende fehlt
        val deckblatt = eintraege(dei, "EntityCommonStockSharesOutstanding").filter { jahresFormular(it.form) }.groupBy { it.accn }
        val bericht = je.getValue(Feld.NETTOGEWINN).firstNotNullOfOrNull { it.takeIf { m -> m.isNotEmpty() } }.orEmpty()
        val abschluesse = enden.map { ende ->
            val m = HashMap<Feld, Wert>()
            for ((feld, listen) in je) {
                val f = listen.firstNotNullOfOrNull { it[ende] } ?: continue
                val faktor = when (feld) {
                    Feld.EPS -> 1.0 / splitFaktor(splits, f.filed)
                    Feld.AKTIEN -> splitFaktor(splits, f.filed)
                    else -> 1.0
                }
                m[feld] = Wert(f.filed, f.wert * faktor)
            }
            if (Feld.AKTIEN !in m) {
                bericht[ende]?.let { b -> deckblatt[b.accn]?.maxByOrNull { it.ende } }?.let { f ->
                    m[Feld.AKTIEN] = Wert(f.filed, f.wert * splitFaktor(splits, f.filed))
                }
            }
            Abschluss(datum(ende), m)
        }
        return name to abschluesse
    }

    private val NAME_REGEX = Regex("\"entityName\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"")
    private val CIK_REGEX = Regex("CIK0*(\\d+)\\.json$")

    // Liest aus der Bulkdatei companyfacts.zip die Firmen, die gesucht(cik, name) einem Symbol zuordnet. Vom Rest wird
    // nur der Anfang gelesen (Name). splits: Splits je Symbol fuer den Aktienstand.
    fun ausZip(datei: File, gesucht: (Long, String) -> String?, splits: (String) -> List<Splits.Split> = { emptyList() }): Map<String, List<Abschluss>> {
        val aus = HashMap<String, List<Abschluss>>()
        ZipFile(datei).use { zip ->
            for (eintrag in zip.entries()) {
                if (eintrag.isDirectory) continue
                val cik = CIK_REGEX.find(eintrag.name)?.groupValues?.get(1)?.toLongOrNull() ?: continue
                val anfang = zip.getInputStream(eintrag).use { s -> ByteArray(4096).let { b -> String(b, 0, maxOf(0, s.readNBytesSicher(b)), Charsets.UTF_8) } }
                val name = NAME_REGEX.find(anfang)?.groupValues?.get(1).orEmpty()
                val symbol = gesucht(cik, name) ?: continue
                if (symbol in aus) continue
                val text = zip.getInputStream(eintrag).use { it.readBytes().toString(Charsets.UTF_8) }
                ausFakten(text, splits(symbol))?.second?.takeIf { it.isNotEmpty() }?.let { aus[symbol] = it }
            }
        }
        return aus
    }

    // Liest bis zu b.size Bytes (readNBytes gibt es auf aelteren Android-Fassungen nicht).
    private fun java.io.InputStream.readNBytesSicher(b: ByteArray): Int {
        var n = 0
        while (n < b.size) {
            val r = read(b, n, b.size - n)
            if (r < 0) break
            n += r
        }
        return n
    }

    // ------------------------------------------------------------ Mitglieder des S&P 500

    // Eine Aenderung im Index: am Tag tag kam hinzu dazu und ging weg (je null, wenn nur eins geschah).
    data class Aenderung(val tag: Long, val hinzu: String?, val weg: String?)

    // Mitglieder zum Stichtag: von den heutigen aus die Aenderungen nach dem Stichtag rueckwaerts aufgeloest. Heutige
    // Mitglieder, die nie entfernt wurden, zaehlen erst ab ihrem Aufnahmetag (aktuell: Symbol zu Tag, null unbekannt).
    fun mitgliederZum(aktuell: Map<String, Long?>, aenderungen: List<Aenderung>, tag: Long): Set<String> {
        val s = aktuell.keys.toHashSet()
        for (a in aenderungen.sortedByDescending { it.tag }) {
            if (a.tag <= tag) break
            a.hinzu?.let { s.remove(it) }
            a.weg?.let { s.add(it) }
        }
        val einmalWeg = aenderungen.mapNotNullTo(HashSet()) { it.weg }
        aktuell.forEach { (sym, ab) -> if (ab != null && ab > tag && sym !in einmalWeg) s.remove(sym) }
        return s
    }

    // Kuerzel wie bei Yahoo: BRK.B wird BRK-B.
    fun yahooKuerzel(s: String): String = s.trim().uppercase().replace('.', '-')

    // ------------------------------------------------------------ Eingaben zum Stichtag

    // Alles, was zu einer Aktie vorliegt (ganze Geschichte). kurse und urteile nach Zeit aufsteigend.
    data class Eingaben(
        val symbol: String,
        val name: String,
        val branche: String,
        val kurse: List<Candle>,
        val abschluesse: List<Abschluss> = emptyList(),
        val urteile: List<Analystenbilanz.Urteil> = emptyList(),
        val splits: List<Splits.Split> = emptyList(),
    )

    // Ersatz fuer die Durchschnittsnote, die es rueckwirkend nicht gibt: Netto-Hochstufungen der letzten 90 Tage.
    // Jede Hochstufung mehr als Abstufungen verschiebt die Note um ein Viertel (keine Aenderung = 2,5, unauffaellig).
    fun noteErsatz(netto: Int): Double = (2.5 - netto.coerceIn(-6, 6) * 0.25).coerceIn(1.0, 5.0)

    // Erster Index mit zeit nach bis (Urteile aufsteigend).
    private fun urteileBis(urteile: List<Analystenbilanz.Urteil>, bis: Long): Int {
        var lo = 0
        var hi = urteile.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (urteile[mid].zeit < bis) lo = mid + 1 else hi = mid
        }
        return lo
    }

    // Eingaben der Engine zum Stichtag tag (Tag seit 1970, Schlusskurs dieses Tages). Nichts nach dem Stichtag fliesst
    // ein: Kurse bis zum Tag, Abschlussfelder mit bekannt <= tag, Urteile vor Ende des Tages. index: Vergleichsindex fuer
    // die Trefferbilanz der Analysten. null, wenn der Wert am Stichtag keinen Kurs hat.
    fun datenZum(e: Eingaben, tag: Long, index: List<Candle>?): Aktienfinder.Daten? {
        val i = bisTag(e.kurse, tag)
        if (i < 0 || tag - tag(e.kurse[i].t) > KURS_ALTER_TAGE) return null
        val kurs = e.kurse[i].close.takeIf { it > 0 && it.isFinite() } ?: return null
        val kurse = e.kurse.subList(0, i + 1)
        // Kurstrend: zwoelf Monate und Abstand zur 200-Tage-Linie in Prozent
        val jahr = kursZum(kurse, tag - 365)?.let { (kurs / it - 1.0) * 100.0 }
        val linie200 = if (kurse.size >= 200) (kurs / (kurse.subList(kurse.size - 200, kurse.size).sumOf { it.close } / 200.0) - 1.0) * 100.0 else null

        // Abschluesse: Bewertung und Qualitaet
        val stand = standZum(e.abschluesse, tag)
        val j = stand?.letztes.orEmpty()
        val eps = j[Feld.EPS]
        val aktien = j[Feld.AKTIEN]?.takeIf { it > 0 }
        val ek = j[Feld.EIGENKAPITAL]
        val umsatz = j[Feld.UMSATZ]
        val ni = j[Feld.NETTOGEWINN]
        val boersenwert = aktien?.let { it * kurs }
        val buchwert = if (ek != null && aktien != null) ek / aktien else null
        // Gewinnwachstum des letzten Jahres ersetzt das erwartete (rueckwirkend gibt es keine Schaetzungen)
        val epsVorjahr = stand?.vorjahr?.get(Feld.EPS)
        val gewinnErwartet = if (eps != null && eps > 0 && epsVorjahr != null && epsVorjahr > 0) eps * (eps / epsVorjahr) else null
        val bilanz = if (stand != null && Aktienfinder.mitBilanz(e.branche)) FinderBilanz.werte(stand.jahre, boersenwert, Qualitaet.fassungFinder(e.branche)) else null

        // Analysten: Urteile bis Ende des Stichtags, Kursziele auf den heutigen Aktienstand
        val bisMs = (tag + 1) * TAG_MS
        val ab = urteileBis(e.urteile, bisMs - URTEILE_JAHRE * 365 * TAG_MS)
        val urteile = e.urteile.subList(ab, urteileBis(e.urteile, bisMs)).let { l ->
            if (e.splits.isEmpty()) l else l.map { u ->
                val f = splitFaktor(e.splits, tag(u.zeit))
                if (f == 1.0) u else u.copy(ziel = u.ziel / f, zielVorher = u.zielVorher / f)
            }
        }
        val abgedeckt = urteile.any { bisMs - it.zeit <= ABDECKUNG_TAGE * TAG_MS }
        val netto = urteile.filter { bisMs - it.zeit <= ANALYSTEN_TAGE * TAG_MS }.sumOf { Analystenbilanz.richtung(it) ?: 0 }
        val auswertung = if (abgedeckt) {
            val idx = index?.let { x -> x.subList(0, bisTag(x, tag) + 1) }
            Analystenbilanz.auswerten(urteile, kurse, idx, kurs, bisMs)
        } else null
        val kursziel = auswertung?.zielRoh?.takeIf { it > 0 }?.let { (it / kurs - 1.0) * 100.0 }

        fun pos(v: Double?) = v?.takeIf { it > 0 && it.isFinite() }
        return Aktienfinder.Daten(
            symbol = e.symbol, name = e.name, land = "US", branche = e.branche,
            kgv = eps?.takeIf { it > 0 }?.let { pos(kurs / it) },
            kbv = buchwert?.takeIf { it > 0 }?.let { pos(kurs / it) },
            gewinn = eps, gewinnErwartet = gewinnErwartet, buchwert = buchwert,
            boersenwert = boersenwert,
            note = if (abgedeckt) noteErsatz(netto) else null,
            jahr = jahr, linie200 = linie200,
            kuv = if (boersenwert != null && umsatz != null && umsatz > 0) pos(boersenwert / umsatz) else null,
            marge = if (ni != null && umsatz != null && umsatz > 0) ni / umsatz * 100.0 else null,
            eigenkapitalRendite = if (ni != null && ek != null && ek > 0) ni / ek * 100.0 else null,
            verschuldung = j[Feld.SCHULDEN]?.let { s -> if (ek != null && ek > 0) s / ek else null },
            kursziel = kursziel,
            bilanz = bilanz?.bilanz?.ampel, fScore = bilanz?.fScore, zone = bilanz?.zone,
            bereinigt = auswertung?.takeIf { !it.wenigAussagekraft }?.bereinigt,
            mitDetails = true,
        )
    }

    // Rendite von Schluss am Stichtag bis zum letzten Kurs am Tag tag plus monate Monate. heute: letzter Tag mit Kursen
    // ueberhaupt; liegt das Ziel danach, ist die Zeit nicht verstrichen (null). Endet die Reihe vorher (Wert nicht mehr
    // gehandelt), gilt der letzte Kurs.
    fun rendite(kurse: List<Candle>, tag: Long, monate: Int, heute: Long): Double? {
        val ziel = LocalDate.ofEpochDay(tag).plusMonths(monate.toLong()).toEpochDay()
        if (ziel > heute) return null
        val a = kursZum(kurse, tag) ?: return null
        val j = bisTag(kurse, ziel)
        if (j < 0 || tag(kurse[j].t) <= tag) return null
        val b = kurse[j].close.takeIf { it > 0 && it.isFinite() } ?: return null
        return b / a - 1.0
    }

    // Rendite von Schluss am Stichtag bis zum letzten Kurs am Tag heute, ohne Zielmonat (5.93). Endet die Reihe vorher
    // (Wert nicht mehr gehandelt), gilt der letzte Kurs.
    fun renditeBis(kurse: List<Candle>, tag: Long, heute: Long): Double? {
        val a = kursZum(kurse, tag) ?: return null
        val j = bisTag(kurse, heute)
        if (j < 0 || tag(kurse[j].t) <= tag) return null
        val b = kurse[j].close.takeIf { it > 0 && it.isFinite() } ?: return null
        return b / a - 1.0
    }
}
