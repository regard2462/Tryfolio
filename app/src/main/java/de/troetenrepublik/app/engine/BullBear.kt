package de.troetenrepublik.app.engine

// Bull- und Bear-Case einer Aktie (seit 5.7): was fuer und was gegen den Wert spricht, automatisch aus Schlagzeilen
// und Kennzahlen zusammengestellt. Schlagzeilen werden nach Themen erkannt (Klagen, Ermittlungen, Vorwuerfe zur
// Bilanz, Gewinnwarnungen, Herabstufungen gegenueber Hochstufungen, Rekordzahlen, Auftraegen usw.); Kennzahlen nach
// festen Schwellen. Jede Aussage nennt ihren Grund, Schlagzeilen bleiben als Beleg dabei. Keine Bewertung im Sinne
// einer Empfehlung.
object BullBear {
    enum class Seite { BULL, BEAR }

    // Themen der Schlagzeilen; Reihenfolge = Gewicht in der Anzeige (wichtigste zuerst je Seite).
    enum class Thema(val seite: Seite) {
        PLEITE(Seite.BEAR), BILANZ(Seite.BEAR), ERMITTLUNG(Seite.BEAR), KLAGE(Seite.BEAR), WARNUNG(Seite.BEAR),
        ABSTUFUNG(Seite.BEAR), RUECKRUF(Seite.BEAR), PERSONAL(Seite.BEAR),
        UEBERNAHME(Seite.BULL), PROGNOSE(Seite.BULL), ZAHLEN(Seite.BULL), AUFSTUFUNG(Seite.BULL), ZULASSUNG(Seite.BULL),
        AUFTRAG(Seite.BULL), RUECKKAUF(Seite.BULL), KI(Seite.BULL),
    }

    // Kennzahlen-Aussagen.
    enum class Kennzahl(val seite: Seite) {
        ANALYSTEN_POSITIV(Seite.BULL), WACHSTUM(Seite.BULL), GEWINNWACHSTUM(Seite.BULL), MARGE_HOCH(Seite.BULL),
        NETTOKASSE(Seite.BULL), CASHFLOW_POSITIV(Seite.BULL), UEBERTROFFEN(Seite.BULL), INSIDER_KAUFEN(Seite.BULL),
        GEWINNE_STEIGEN(Seite.BULL), FDA_ZULASSUNG(Seite.BULL),
        ANALYSTEN_SKEPTISCH(Seite.BEAR), SCHRUMPFT(Seite.BEAR), GEWINN_FAELLT(Seite.BEAR), VERLUST(Seite.BEAR),
        SCHULDEN(Seite.BEAR), CASHFLOW_NEGATIV(Seite.BEAR), VERFEHLT(Seite.BEAR), INSIDER_VERKAUFEN(Seite.BEAR),
        BEWERTUNG_HOCH(Seite.BEAR), SCHWANKUNG(Seite.BEAR), ZYKLISCH(Seite.BEAR), DIVIDENDE_UNGEDECKT(Seite.BEAR),
        LEERVERKAUF_HOCH(Seite.BEAR), FDA_RUECKRUF(Seite.BEAR),
    }

    data class Schlagzeile(val titel: String, val zeit: Long, val id: String)

    // Eine Aussage: Thema einer Schlagzeile (mit Beleg) oder eine Kennzahl (mit Werten fuer den Text).
    data class Punkt(val seite: Seite, val thema: Thema? = null, val kennzahl: Kennzahl? = null, val werte: List<Double> = emptyList(), val belege: List<Schlagzeile> = emptyList())

    data class Fakten(
        val kurs: Double = 0.0,
        val kursziel: Double = 0.0,
        // Mittel der Empfehlungen: 1 Kaufen bis 5 Verkaufen; 0 unbekannt.
        val empfehlung: Double = 0.0,
        val analysten: Int = 0,
        // Anteile als Dezimalzahl (0,12 = 12 %), wie Yahoo sie liefert.
        val umsatzWachstum: Double = 0.0,
        val gewinnWachstum: Double = 0.0,
        val gewinnMarge: Double = 0.0,
        val kasse: Double = 0.0,
        val schulden: Double = 0.0,
        val freierCashflow: Double = 0.0,
        val marktwert: Double = 0.0,
        val kgv: Double = 0.0,
        val kgvErwartet: Double = 0.0,
        val beta: Double = 0.0,
        val ausschuettung: Double = 0.0,
        val branche: String = "",
        val industrie: String = "",
        // Ergebnis je Aktie der letzten Quartale: tatsaechlich und erwartet.
        val ergebnisse: List<Pair<Double, Double>> = emptyList(),
        // Insider-Trend -100 bis +100, null ohne Daten.
        val insider: Int? = null,
        // Summe der veroeffentlichten Leerverkaufspositionen in Prozent des Grundkapitals, null ohne Liste.
        val leerverkauf: Double? = null,
        // FDA in zwoelf Monaten: Erstzulassungen und Rueckrufe der Klasse I (ernste Gefahr); null ohne Daten.
        val fdaZulassungen: Int? = null,
        val fdaRueckrufe: Int? = null,
    )

    private val THEMEN: Map<Thema, List<Regex>> = mapOf(
        Thema.PLEITE to listOf("bankrupt", "chapter 11", "going concern", "delist", "insolven", "zahlungsunfähig"),
        Thema.BILANZ to listOf("accounting", "restate", "fraud", "short.seller", "short report", "hindenburg", "auditor resign", "late filing",
            "delayed (annual|quarterly)? ?(report|filing)", "bilanz(skandal|fälschung|manipulation|betrug)", "betrug", "wirtschaftsprüfer", "geschönt"),
        Thema.ERMITTLUNG to listOf("investigation", "probe", "subpoena", "\\bsec\\b", "\\bdoj\\b", "justice department", "\\bftc\\b", "antitrust",
            "ermittl", "kartellamt", "durchsuch", "staatsanwalt", "bafin prüft"),
        Thema.KLAGE to listOf("lawsuit", "class action", "\\bsues\\b", "\\bsued\\b", "litigation", "verklagt", "sammelklage", "\\bklage"),
        Thema.WARNUNG to listOf("cuts (its |full.year )?(guidance|outlook|forecast)", "lowers (guidance|outlook|forecast)", "profit warning",
            "\\bmisses\\b", "gewinnwarnung", "senkt (die )?prognose", "verfehlt", "kappt (die )?prognose"),
        Thema.ABSTUFUNG to listOf("downgrade", "cuts price target", "lowers price target", "abgestuft", "senkt (das )?kursziel", "abstufung"),
        Thema.RUECKRUF to listOf("recall", "rückruf", "data breach", "\\bhack", "cyberangriff", "outage", "ausfall"),
        Thema.PERSONAL to listOf("(ceo|cfo|chief executive) (resigns|steps down|exits|departs|ousted)", "layoff", "job cuts", "stellenabbau",
            "tritt zurück", "entlassung"),
        Thema.UEBERNAHME to listOf("takeover", "to acquire", "acquisition offer", "buyout", "übernahmeangebot", "übernahme"),
        Thema.PROGNOSE to listOf("raises (its |full.year )?(guidance|outlook|forecast)", "lifts (guidance|outlook|forecast)", "hebt (die )?prognose",
            "erhöht (die )?prognose"),
        Thema.ZAHLEN to listOf("beats", "tops estimates", "record (revenue|quarter|sales|profit)", "rekord", "übertrifft"),
        Thema.AUFSTUFUNG to listOf("upgrade", "raises price target", "hochgestuft", "hebt (das )?kursziel", "kaufempfehlung"),
        Thema.ZULASSUNG to listOf("fda (approval|approves)", "wins approval", "zulassung"),
        Thema.AUFTRAG to listOf("contract", "partnership", "deal with", "order from", "großauftrag", "auftrag", "partnerschaft", "kooperation"),
        Thema.RUECKKAUF to listOf("buyback", "repurchase", "(raises|hikes|boosts) (its )?dividend", "rückkauf", "erhöht (die )?dividende"),
        Thema.KI to listOf("\\bai (demand|server|chip|boom|orders|growth)", "artificial intelligence", "ki.(nachfrage|boom|server|chip)"),
    ).mapValues { (_, l) -> l.map { Regex(it, RegexOption.IGNORE_CASE) } }

    // Themen einer Schlagzeile.
    fun themen(titel: String): List<Thema> = THEMEN.filter { (_, r) -> r.any { it.containsMatchIn(titel) } }.keys.toList()

    // Schlagzeilen, die die Firma nennen: Kuerzel als Wort oder die ersten zwei Woerter des Namensstamms (mit oder ohne
    // Leerzeichen, „Super Micro“ und „Supermicro“). Yahoo ordnet einem Wert auch Meldungen ueber andere Firmen zu.
    // Bleibt nichts uebrig, zaehlen alle Schlagzeilen.
    fun zurFirma(schlagzeilen: List<Schlagzeile>, symbol: String, stamm: List<String>): List<Schlagzeile> {
        val kuerzel = symbol.substringBefore('.').lowercase()
        val worte = stamm.take(2)
        val muster = listOfNotNull(
            Regex("\\b" + Regex.escape(kuerzel) + "\\b", RegexOption.IGNORE_CASE).takeIf { kuerzel.length >= 2 },
            worte.takeIf { it.isNotEmpty() }?.let { w -> Regex(w.joinToString("\\s?") { Regex.escape(it) }, RegexOption.IGNORE_CASE) },
        )
        val passend = schlagzeilen.filter { s -> muster.any { it.containsMatchIn(s.titel) } }
        return passend.ifEmpty { schlagzeilen }
    }

    // Branchen, deren Gewinne stark mit der Konjunktur schwanken (Yahoo-Sektoren, dazu Halbleiter und Autos).
    private val ZYKLISCHE = setOf("industrials", "basic materials", "energy", "consumer cyclical", "real estate")
    private val ZYKLISCHE_INDUSTRIE = listOf("semiconductor", "auto", "airline", "steel", "chemical", "oil", "mining", "construction", "travel")

    fun zyklisch(branche: String, industrie: String): Boolean =
        branche.lowercase() in ZYKLISCHE || ZYKLISCHE_INDUSTRIE.any { industrie.lowercase().contains(it) }

    // Alle Aussagen: Schlagzeilen-Themen der letzten tage Tage (je Thema bis zu drei Belege), dann Kennzahlen.
    fun auswerten(f: Fakten, schlagzeilen: List<Schlagzeile>, jetzt: Long, tage: Int = 60): List<Punkt> {
        val ab = jetzt - tage * 86_400_000L
        val aus = ArrayList<Punkt>()
        val jeThema = LinkedHashMap<Thema, MutableList<Schlagzeile>>()
        schlagzeilen.filter { it.zeit == 0L || it.zeit >= ab }.sortedByDescending { it.zeit }.distinctBy { it.titel.trim().lowercase() }.forEach { s ->
            themen(s.titel).forEach { t -> jeThema.getOrPut(t) { mutableListOf() }.add(s) }
        }
        Thema.entries.forEach { t -> jeThema[t]?.let { aus += Punkt(t.seite, thema = t, belege = it.take(3)) } }

        fun k(kz: Kennzahl, vararg w: Double) { aus += Punkt(kz.seite, kennzahl = kz, werte = w.toList()) }
        if (f.kurs > 0 && f.kursziel > 0 && f.analysten >= 3) {
            val potenzial = (f.kursziel / f.kurs - 1) * 100
            if (potenzial >= 15 && (f.empfehlung == 0.0 || f.empfehlung <= 2.5)) k(Kennzahl.ANALYSTEN_POSITIV, f.kursziel, potenzial, f.analysten.toDouble())
            else if (potenzial <= -5 || f.empfehlung >= 3.0) k(Kennzahl.ANALYSTEN_SKEPTISCH, f.kursziel, potenzial, f.analysten.toDouble())
        }
        if (f.umsatzWachstum >= 0.10) k(Kennzahl.WACHSTUM, f.umsatzWachstum * 100) else if (f.umsatzWachstum <= -0.05) k(Kennzahl.SCHRUMPFT, f.umsatzWachstum * 100)
        if (f.gewinnWachstum >= 0.15) k(Kennzahl.GEWINNWACHSTUM, f.gewinnWachstum * 100) else if (f.gewinnWachstum <= -0.15) k(Kennzahl.GEWINN_FAELLT, f.gewinnWachstum * 100)
        if (f.gewinnMarge >= 0.20) k(Kennzahl.MARGE_HOCH, f.gewinnMarge * 100) else if (f.gewinnMarge < 0) k(Kennzahl.VERLUST, f.gewinnMarge * 100)
        if (f.kasse > 0 && f.kasse > f.schulden) k(Kennzahl.NETTOKASSE, f.kasse - f.schulden)
        else if (f.schulden > 0 && f.marktwert > 0 && f.schulden - f.kasse > f.marktwert * 0.5) k(Kennzahl.SCHULDEN, f.schulden - f.kasse, (f.schulden - f.kasse) / f.marktwert * 100)
        if (f.freierCashflow > 0 && f.marktwert > 0 && f.freierCashflow / f.marktwert >= 0.03) k(Kennzahl.CASHFLOW_POSITIV, f.freierCashflow, f.freierCashflow / f.marktwert * 100)
        else if (f.freierCashflow < 0) k(Kennzahl.CASHFLOW_NEGATIV, f.freierCashflow)
        val letzte = f.ergebnisse.takeLast(4).filter { it.second != 0.0 }
        if (letzte.size >= 3) {
            val drueber = letzte.count { it.first > it.second }
            val drunter = letzte.count { it.first < it.second }
            if (drueber >= 3) k(Kennzahl.UEBERTROFFEN, drueber.toDouble(), letzte.size.toDouble())
            else if (drunter >= 2) k(Kennzahl.VERFEHLT, drunter.toDouble(), letzte.size.toDouble())
        }
        if (f.kgv > 0 && f.kgvErwartet > 0 && f.kgvErwartet < f.kgv * 0.8) k(Kennzahl.GEWINNE_STEIGEN, f.kgv, f.kgvErwartet)
        if (f.kgv >= 50) k(Kennzahl.BEWERTUNG_HOCH, f.kgv)
        if (f.beta >= 1.5) k(Kennzahl.SCHWANKUNG, f.beta)
        if (zyklisch(f.branche, f.industrie)) k(Kennzahl.ZYKLISCH)
        if (f.ausschuettung > 1.0) k(Kennzahl.DIVIDENDE_UNGEDECKT, f.ausschuettung * 100)
        f.insider?.let { if (it >= 50) k(Kennzahl.INSIDER_KAUFEN, it.toDouble()) else if (it <= -50) k(Kennzahl.INSIDER_VERKAUFEN, it.toDouble()) }
        f.leerverkauf?.let { if (it >= 5.0) k(Kennzahl.LEERVERKAUF_HOCH, it) }
        f.fdaZulassungen?.let { if (it >= 1) k(Kennzahl.FDA_ZULASSUNG, it.toDouble()) }
        f.fdaRueckrufe?.let { if (it >= 1) k(Kennzahl.FDA_RUECKRUF, it.toDouble()) }
        return aus
    }
}
