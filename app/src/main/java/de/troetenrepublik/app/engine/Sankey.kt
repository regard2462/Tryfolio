package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.Transaction

// Sankey-Diagramme (5.94) nach dem Vorbild von SankeyMATIC (ISC-Lizenz, nur als Vorbild, eigener Code): Knoten in
// Spalten, Hoehe nach Betrag, Baender zwischen ihnen so breit wie der Fluss. Hier die Anordnung und die Daten fuer
// Depot, Verkaeufe und Gewinnrechnung einer Aktie; gezeichnet wird in ui/SankeyDiagramm.kt.
object Sankey {

    enum class Art { NEUTRAL, PLUS, MINUS }

    data class Knoten(val id: String, val spalte: Int, val art: Art = Art.NEUTRAL)
    data class Fluss(val von: String, val nach: String, val wert: Double)

    data class Diagramm(val knoten: List<Knoten>, val fluesse: List<Fluss>) {
        val leer: Boolean get() = fluesse.none { it.wert > 0 }
    }

    // Lage eines Knotens: oben, Hoehe und Betrag; ein Band mit oberem und unterem Rand an beiden Enden.
    data class Platz(val knoten: Knoten, val x: Float, val y: Float, val h: Float, val wert: Double)
    data class Band(val fluss: Fluss, val von: Platz, val nach: Platz, val y0: Float, val y1: Float, val z0: Float, val z1: Float)
    data class Lage(val plaetze: List<Platz>, val baender: List<Band>)

    // Anordnung in breite mal hoehe: Spalten gleichmaessig verteilt, Knoten je Spalte in der gegebenen Reihenfolge
    // untereinander, mittig, mit luecke dazwischen. Ein Massstab fuer alle Spalten, damit gleiche Betraege gleich hoch
    // sind. Ohne positiven Fluss leer.
    fun anordnen(d: Diagramm, breite: Float, hoehe: Float, knotenBreite: Float, luecke: Float): Lage {
        val fluesse = d.fluesse.filter { it.wert > 0 && it.wert.isFinite() }
        if (fluesse.isEmpty()) return Lage(emptyList(), emptyList())
        val ein = HashMap<String, Double>()
        val aus = HashMap<String, Double>()
        for (f in fluesse) { aus[f.von] = (aus[f.von] ?: 0.0) + f.wert; ein[f.nach] = (ein[f.nach] ?: 0.0) + f.wert }
        val knoten = d.knoten.filter { (ein[it.id] ?: 0.0) > 0 || (aus[it.id] ?: 0.0) > 0 }
        val wert = knoten.associate { it.id to maxOf(ein[it.id] ?: 0.0, aus[it.id] ?: 0.0) }
        val spalten = knoten.groupBy { it.spalte }.toSortedMap()
        val n = spalten.size
        val massstab = spalten.values.minOf { s ->
            val frei = hoehe - luecke * (s.size - 1)
            if (frei <= 0) 0.0 else frei / s.sumOf { wert.getValue(it.id) }
        }.toFloat()
        val plaetze = LinkedHashMap<String, Platz>()
        spalten.entries.forEachIndexed { i, (_, s) ->
            val x = if (n <= 1) 0f else i * (breite - knotenBreite) / (n - 1)
            val belegt = s.sumOf { wert.getValue(it.id) }.toFloat() * massstab + luecke * (s.size - 1)
            var y = (hoehe - belegt) / 2
            for (k in s) {
                val h = wert.getValue(k.id).toFloat() * massstab
                plaetze[k.id] = Platz(k, x, y, h, wert.getValue(k.id))
                y += h + luecke
            }
        }
        // Baender: am Quellknoten nach der Lage des Ziels gestapelt, am Ziel nach der Lage der Quelle
        val obenAus = HashMap<String, Float>()
        val obenEin = HashMap<String, Float>()
        val roh = fluesse.mapNotNull { f -> val a = plaetze[f.von]; val b = plaetze[f.nach]; if (a == null || b == null) null else Triple(f, a, b) }
        val ausZuerst = roh.sortedWith(compareBy({ it.second.knoten.id }, { it.third.y }))
        val anfang = HashMap<Fluss, Pair<Float, Float>>()
        for ((f, a, _) in ausZuerst) {
            val y0 = obenAus[a.knoten.id] ?: a.y
            val dicke = f.wert.toFloat() * massstab
            anfang[f] = y0 to y0 + dicke
            obenAus[a.knoten.id] = y0 + dicke
        }
        val baender = roh.sortedWith(compareBy({ it.third.knoten.id }, { it.second.y })).map { (f, a, b) ->
            val z0 = obenEin[b.knoten.id] ?: b.y
            val dicke = f.wert.toFloat() * massstab
            obenEin[b.knoten.id] = z0 + dicke
            val (y0, y1) = anfang.getValue(f)
            Band(f, a, b, y0, y1, z0, z0 + dicke)
        }
        return Lage(plaetze.values.toList(), baender)
    }

    // ------------------------------------------------------------ Depot

    // Kennungen der Knoten im Depot-Diagramm; die Anzeige uebersetzt sie.
    const val ANFANG = "anfang"
    const val EINLAGEN = "einlagen"
    const val KURSGEWINNE = "kursgewinne"
    const val DIVIDENDEN = "dividenden"
    const val ZINSEN = "zinsen"
    const val DEPOT = "depot"
    const val ENDE = "ende"
    const val ENTNAHMEN = "entnahmen"
    const val KURSVERLUSTE = "kursverluste"
    const val GEBUEHREN = "gebuehren"
    const val STEUERN = "steuern"
    const val ERSTATTUNGEN = "erstattungen"
    const val KORREKTUREN = "korrekturen"

    // Geldfluss im Zeitraum aus der Kapitalbilanz: links Anfangswert, Einlagen, Kursgewinne, Dividenden und Zinsen,
    // rechts Endwert, Entnahmen, Kursverluste, Gebuehren und Steuern. Erstattete Steuern und Gebuehren stehen links,
    // zurueckgebuchte Dividenden und Zinsen rechts, so bleiben beide Seiten gleich gross.
    fun depot(b: Performanceanalyse.Bilanz): Diagramm {
        val links = listOf(
            ANFANG to b.anfang, EINLAGEN to b.einlagen, KURSGEWINNE to maxOf(b.kursgewinne, 0.0), DIVIDENDEN to maxOf(b.dividenden, 0.0),
            ZINSEN to maxOf(b.zinsen, 0.0), ERSTATTUNGEN to maxOf(-b.steuern, 0.0) + maxOf(-b.gebuehren, 0.0),
        )
        val rechts = listOf(
            ENDE to b.ende, ENTNAHMEN to b.entnahmen, KURSVERLUSTE to maxOf(-b.kursgewinne, 0.0), KORREKTUREN to maxOf(-b.dividenden, 0.0) + maxOf(-b.zinsen, 0.0),
            GEBUEHREN to maxOf(b.gebuehren, 0.0), STEUERN to maxOf(b.steuern, 0.0),
        )
        val knoten = links.map { (k, _) -> Knoten(k, 0, if (k == KURSGEWINNE || k == DIVIDENDEN || k == ZINSEN || k == ERSTATTUNGEN) Art.PLUS else Art.NEUTRAL) } +
            Knoten(DEPOT, 1) +
            rechts.map { (k, _) -> Knoten(k, 2, if (k == ENDE || k == ENTNAHMEN) Art.NEUTRAL else Art.MINUS) }
        val fluesse = links.map { (k, v) -> Fluss(k, DEPOT, v) } + rechts.map { (k, v) -> Fluss(DEPOT, k, v) }
        return Diagramm(knoten, fluesse)
    }

    // ------------------------------------------------------------ Verkaeufe

    const val ERGEBNIS = "ergebnis"
    const val NETTO_PLUS = "netto+"
    const val NETTO_MINUS = "netto-"
    const val WEITERE_PLUS = "weitere+"
    const val WEITERE_MINUS = "weitere-"

    // Realisierte Ergebnisse je Wert im Zeitraum (von ausschliesslich, bis einschliesslich): links die Werte mit
    // Gewinn, rechts die mit Verlust, dazwischen das Ergebnis; der Saldo gleicht die Seiten aus. Je Seite die
    // groessten (hoechstens je), der Rest als „weitere“. Knoten der Werte heissen wie ihr Symbol.
    fun verkaeufe(txs: List<Transaction>, von: Long, bis: Long, je: Int = 6): Diagramm {
        val proWert = txs.filter { it.ts > von && it.ts <= bis && it.schliesst && it.symbol.isNotBlank() }
            .groupBy { it.symbol }.mapValues { (_, l) -> l.sumOf { it.realized } }.filterValues { it != 0.0 && it.isFinite() }
        if (proWert.isEmpty()) return Diagramm(emptyList(), emptyList())
        fun teil(l: List<Pair<String, Double>>, weitere: String): List<Pair<String, Double>> =
            if (l.size <= je) l else l.take(je - 1) + (weitere to l.drop(je - 1).sumOf { it.second })
        val gewinne = teil(proWert.filterValues { it > 0 }.toList().sortedByDescending { it.second }, WEITERE_PLUS)
        val verluste = teil(proWert.filterValues { it < 0 }.map { it.key to -it.value }.sortedByDescending { it.second }, WEITERE_MINUS)
        val saldo = gewinne.sumOf { it.second } - verluste.sumOf { it.second }
        val knoten = gewinne.map { Knoten(it.first, 0, Art.PLUS) } +
            (if (saldo < 0) listOf(Knoten(NETTO_MINUS, 0, Art.MINUS)) else emptyList()) +
            Knoten(ERGEBNIS, 1) +
            verluste.map { Knoten(it.first + "\u0000", 2, Art.MINUS) } +
            (if (saldo > 0) listOf(Knoten(NETTO_PLUS, 2, Art.PLUS)) else emptyList())
        val fluesse = gewinne.map { Fluss(it.first, ERGEBNIS, it.second) } +
            (if (saldo < 0) listOf(Fluss(NETTO_MINUS, ERGEBNIS, -saldo)) else emptyList()) +
            verluste.map { Fluss(ERGEBNIS, it.first + "\u0000", it.second) } +
            (if (saldo > 0) listOf(Fluss(ERGEBNIS, NETTO_PLUS, saldo)) else emptyList())
        return Diagramm(knoten, fluesse)
    }

    // Symbol eines Werteknotens (rechts mit Endzeichen, damit ein Wert mit Gewinn und Verlust nicht kollidiert).
    fun symbol(id: String): String = id.trimEnd('\u0000')

    // ------------------------------------------------------------ Gewinnrechnung einer Aktie

    // Felder von ws/fundamentals-timeseries fuer die Gewinnrechnung.
    val GUV_FELDER = listOf(
        "annualTotalRevenue", "annualCostOfRevenue", "annualGrossProfit", "annualResearchAndDevelopment",
        "annualSellingGeneralAndAdministration", "annualOperatingExpense", "annualOperatingIncome", "annualTaxProvision",
        "annualNetIncome",
    )

    const val UMSATZ = "umsatz"
    const val HERSTELLKOSTEN = "herstellkosten"
    const val BRUTTO = "brutto"
    const val FORSCHUNG = "forschung"
    const val VERWALTUNG = "verwaltung"
    const val SONSTIGE_KOSTEN = "sonstige"
    const val BETRIEB = "betrieb"
    const val BETRIEBSVERLUST = "betriebsverlust"
    const val STEUER = "steuer"
    const val ZINSEN_SONSTIGES = "zinsenSonstiges"
    const val FINANZERTRAG = "finanzertrag"
    const val NETTO = "netto"

    // Ein Geschaeftsjahr der Gewinnrechnung, Betraege in der Berichtswaehrung.
    data class Guv(
        val ende: String, val waehrung: String, val umsatz: Double?, val herstellkosten: Double?, val brutto: Double?, val forschung: Double?,
        val verwaltung: Double?, val betriebskosten: Double?, val betrieb: Double?, val steuer: Double?, val netto: Double?,
    )

    // Juengstes Geschaeftsjahr mit Umsatz aus der Antwort (Aufbau wie bei Qualitaet.lesen).
    fun guvLesen(text: String): Guv? = runCatching {
        val wurzel = kotlinx.serialization.json.Json.parseToJsonElement(text) as? kotlinx.serialization.json.JsonObject ?: return null
        val ergebnis = ((wurzel["timeseries"] as? kotlinx.serialization.json.JsonObject)?.get("result") as? kotlinx.serialization.json.JsonArray).orEmpty()
        val werte = HashMap<String, HashMap<String, Double>>()
        val waehrung = HashMap<String, String>()
        for (e in ergebnis) {
            val o = e as? kotlinx.serialization.json.JsonObject ?: continue
            val typ = ((o["meta"] as? kotlinx.serialization.json.JsonObject)?.get("type") as? kotlinx.serialization.json.JsonArray)
                ?.firstOrNull()?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content } ?: continue
            for (p in (o[typ] as? kotlinx.serialization.json.JsonArray).orEmpty()) {
                val po = p as? kotlinx.serialization.json.JsonObject ?: continue
                val datum = (po["asOfDate"] as? kotlinx.serialization.json.JsonPrimitive)?.content ?: continue
                val roh = ((po["reportedValue"] as? kotlinx.serialization.json.JsonObject)?.get("raw") as? kotlinx.serialization.json.JsonPrimitive)
                    ?.content?.toDoubleOrNull()?.takeIf { it.isFinite() } ?: continue
                werte.getOrPut(datum) { HashMap() }[typ] = roh
                (po["currencyCode"] as? kotlinx.serialization.json.JsonPrimitive)?.content?.takeIf { it.isNotBlank() }?.let { waehrung[datum] = it }
            }
        }
        val d = werte.keys.filter { (werte.getValue(it)["annualTotalRevenue"] ?: 0.0) > 0 }.maxOrNull() ?: return null
        val f = werte.getValue(d)
        Guv(
            d, waehrung[d].orEmpty(), f["annualTotalRevenue"], f["annualCostOfRevenue"], f["annualGrossProfit"], f["annualResearchAndDevelopment"],
            f["annualSellingGeneralAndAdministration"], f["annualOperatingExpense"], f["annualOperatingIncome"], f["annualTaxProvision"], f["annualNetIncome"],
        )
    }.getOrNull()

    // Vom Umsatz zum Nettogewinn: Umsatz teilt sich in Herstellkosten und Bruttogewinn, der Bruttogewinn in Forschung,
    // Vertrieb und Verwaltung, sonstige Betriebskosten und Betriebsergebnis, das Betriebsergebnis in Steuern, Zinsen und
    // Sonstiges und Nettogewinn. Bleibt nach den Steuern mehr als das Betriebsergebnis hergibt (Zins- und sonstige
    // Ertraege), kommt das Plus als eigener Zufluss dazu, damit Steuern und Nettogewinn ihre echten Werte behalten.
    // Fehlende Posten ergeben sich als Rest; ein Verlust endet die Kette an dieser Stelle.
    fun guv(g: Guv): Diagramm {
        val umsatz = g.umsatz?.takeIf { it > 0 } ?: return Diagramm(emptyList(), emptyList())
        val brutto = (g.brutto ?: g.herstellkosten?.let { umsatz - it })?.coerceIn(0.0, umsatz) ?: umsatz
        val herstell = umsatz - brutto
        val knoten = mutableListOf(Knoten(UMSATZ, 0), Knoten(HERSTELLKOSTEN, 1, Art.MINUS), Knoten(BRUTTO, 1, Art.PLUS))
        val fluesse = mutableListOf(Fluss(UMSATZ, HERSTELLKOSTEN, herstell), Fluss(UMSATZ, BRUTTO, brutto))
        val betrieb = g.betrieb ?: return Diagramm(knoten, fluesse)
        val fe = (g.forschung ?: 0.0).coerceAtLeast(0.0)
        val vv = (g.verwaltung ?: 0.0).coerceAtLeast(0.0)
        if (betrieb > 0) {
            val sonst = (brutto - betrieb - fe - vv).coerceAtLeast(0.0)
            knoten += listOf(Knoten(FORSCHUNG, 2, Art.MINUS), Knoten(VERWALTUNG, 2, Art.MINUS), Knoten(SONSTIGE_KOSTEN, 2, Art.MINUS), Knoten(BETRIEB, 2, Art.PLUS))
            fluesse += listOf(Fluss(BRUTTO, FORSCHUNG, fe), Fluss(BRUTTO, VERWALTUNG, vv), Fluss(BRUTTO, SONSTIGE_KOSTEN, sonst), Fluss(BRUTTO, BETRIEB, minOf(betrieb, brutto)))
            val netto = g.netto
            if (netto != null && netto > 0) {
                val steuer = (g.steuer ?: 0.0).coerceAtLeast(0.0)
                val rest = betrieb - netto - steuer
                if (rest >= 0) {
                    knoten += listOf(Knoten(STEUER, 3, Art.MINUS), Knoten(ZINSEN_SONSTIGES, 3, Art.MINUS), Knoten(NETTO, 3, Art.PLUS))
                    fluesse += listOf(Fluss(BETRIEB, STEUER, steuer), Fluss(BETRIEB, ZINSEN_SONSTIGES, rest), Fluss(BETRIEB, NETTO, netto))
                } else {
                    // Das Betriebsergebnis deckt zuerst die Steuern, der Zufluss den Rest
                    val ausBetrieb = minOf(steuer, betrieb)
                    knoten += listOf(Knoten(FINANZERTRAG, 2, Art.PLUS), Knoten(STEUER, 3, Art.MINUS), Knoten(NETTO, 3, Art.PLUS))
                    fluesse += listOf(
                        Fluss(BETRIEB, STEUER, ausBetrieb), Fluss(BETRIEB, NETTO, betrieb - ausBetrieb),
                        Fluss(FINANZERTRAG, STEUER, steuer - ausBetrieb), Fluss(FINANZERTRAG, NETTO, netto - (betrieb - ausBetrieb)),
                    )
                }
            }
        } else {
            // Betriebsverlust: die Kosten uebersteigen den Bruttogewinn, der Verlust kommt als eigener Zufluss dazu
            knoten.add(0, Knoten(BETRIEBSVERLUST, 1, Art.MINUS))
            knoten += listOf(Knoten(FORSCHUNG, 2, Art.MINUS), Knoten(VERWALTUNG, 2, Art.MINUS), Knoten(SONSTIGE_KOSTEN, 2, Art.MINUS))
            val kosten = brutto - betrieb
            val sonst = (kosten - fe - vv).coerceAtLeast(0.0)
            // Verlust und Bruttogewinn speisen die Kosten anteilig
            val anteil = if (kosten > 0) brutto / kosten else 0.0
            for ((k, v) in listOf(FORSCHUNG to fe, VERWALTUNG to vv, SONSTIGE_KOSTEN to sonst)) {
                fluesse += Fluss(BRUTTO, k, v * anteil)
                fluesse += Fluss(BETRIEBSVERLUST, k, v * (1 - anteil))
            }
        }
        return Diagramm(knoten, fluesse)
    }
}
