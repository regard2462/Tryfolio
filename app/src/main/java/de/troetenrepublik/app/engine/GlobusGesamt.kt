package de.troetenrepublik.app.engine

import kotlin.math.abs
import kotlin.math.ln

// [Globus Gesamtbild 0.73] Gesamtbild je Land: Punkte von 0 bis 100, gewichtetes Mittel aus fuenf Bereichen (je 0 bis 100).
// Wohlstand, Wachstum und Stabilitaet kommen aus den IWF-Reihen des gewaehlten Jahres (fuer Laender ohne IWF-Reihe aus der
// Rueckfallkette ImfErsatz), Regierungsfuehrung aus den Weltbank-Indikatoren, Sicherheit aus Konflikten, Sanktionen und
// Reisehinweisen (jeweils letzter Stand). Ein fehlender Bereich zaehlt mit dem Mittel der vorhandenen, hoechstens mit 70.
// Ein Land braucht mindestens drei Bereiche, darunter einen der ersten drei; mit genau drei Bereichen zaehlen hoechstens 70 Punkte.
object GlobusGesamt {
    enum class Bereich { WOHLSTAND, WACHSTUM, STABILITAET, REGIERUNG, SICHERHEIT }

    // Zutaten eines Bereichs: Wert siehe Zutat.wert
    enum class Art {
        BIP_KOPF, LEBENSERWARTUNG, WACHSTUM, INFLATION, SCHULDEN, HAUSHALT, ARBEITSLOS, LEISTUNG, WGI,
        KRIEG, KONFLIKT, SANKTION_EU, SANKTION_US, REISE, KEINE_ABZUEGE,
    }

    // wert: die Zahl des Landes (Wachstum: Mittel der Jahre von bis; Regierungsfuehrung: Perzentil; Reisehinweis: Stufe;
    // Abzuege: abgezogene Punkte). kennung: Dimension der Regierungsfuehrung (PV, GE, ...).
    // jahr und quelle (WB, UN, CIA): gesetzt, wenn die Zahl ein Ersatzwert ist und nicht vom IWF stammt
    class Zutat(
        val art: Art, val wert: Double, val kennung: String = "", val von: Int = 0, val bis: Int = 0,
        val jahr: Int = 0, val quelle: String = "",
    )

    // Sicherheitslage: schwerste Stufe der Konflikte im Land (0 keine, 1 bewaffnet, 2 Krieg), Zahl der EU-Regime und
    // US-Programme gegen das Land, Reisehinweis der USA (0 ohne Hinweis)
    class Sicherheit(val konflikt: Int, val sanktionenEu: Int, val sanktionenUs: Int, val reise: Int)

    // Zahlen je Land, die nicht aus dem IWF kommen (jeweils letzter Stand); fehlt eine Quelle, bleibt das Feld leer
    class Zusatz(val lebenserwartung: Double? = null, val wgi: Map<String, Double> = emptyMap(), val sicherheit: Sicherheit? = null)

    class BereichErgebnis(val bereich: Bereich, val punkte: Double, val zutaten: List<Zutat>)

    class Ergebnis(val jahr: Int, val punkte: Double, val bereiche: List<BereichErgebnis>) {
        fun bereich(b: Bereich): BereichErgebnis? = bereiche.firstOrNull { it.bereich == b }
    }

    // Gewichte in Prozent; fehlt ein Bereich, zaehlt er mit dem Mittel der vorhandenen, hoechstens mit DECKEL_FEHLEND Punkten
    val GEWICHT: Map<Bereich, Int> = mapOf(
        Bereich.WOHLSTAND to 25, Bereich.WACHSTUM to 10, Bereich.STABILITAET to 20, Bereich.REGIERUNG to 25, Bereich.SICHERHEIT to 20,
    )
    const val DECKEL_FEHLEND = 70.0

    // Schwellen: BIP je Kopf (Kaufkraft, Int.-$) logarithmisch von 1.500 (0) bis 100.000 (100); Lebenserwartung 55 bis 85 Jahre;
    // Wachstum -2 % bis +7 %; Inflation: Abstand zu 2 % von 0 (100) bis 13 Punkten (0); Schulden 30 % (100) bis 150 % (0);
    // Haushaltssaldo 0 (100) bis -8 % (0); Arbeitslosigkeit 3 % (100) bis 18 % (0); Leistungsbilanz 0 (100) bis -10 % (0).
    private const val BIP_UNTEN = 1_500.0
    private const val BIP_OBEN = 100_000.0
    const val FENSTER = 2

    // linear von unten (0) bis oben (100), ausserhalb gekappt; unten darf groesser sein als oben (fallende Skala)
    private fun linear(x: Double, unten: Double, oben: Double): Double = ((x - unten) / (oben - unten) * 100.0).coerceIn(0.0, 100.0)

    private fun einkommen(e: Double): Double =
        if (e <= 0.0) 0.0 else (ln(e / BIP_UNTEN) / ln(BIP_OBEN / BIP_UNTEN) * 100.0).coerceIn(0.0, 100.0)

    private fun lebenserwartung(l: Double) = linear(l, 55.0, 85.0)
    private fun wachstum(w: Double) = linear(w, -2.0, 7.0)
    private fun inflation(i: Double) = (100.0 * (1.0 - abs(i - 2.0) / 13.0)).coerceIn(0.0, 100.0)
    private fun schulden(s: Double) = linear(s, 150.0, 30.0)
    private fun haushalt(h: Double) = linear(h, -8.0, 0.0)
    private fun arbeitslos(a: Double) = linear(a, 18.0, 3.0)
    private fun leistung(l: Double) = linear(l, -10.0, 0.0)

    const val ABZUG_KRIEG = 60
    const val ABZUG_KONFLIKT = 30
    const val ABZUG_SANKTION = 15

    fun abzugReise(stufe: Int): Int = when (stufe) { 4 -> 30; 3 -> 15; 2 -> 5; else -> 0 }

    // Punkte der Sicherheit: 100 minus Abzuege, nicht unter 0
    private fun sicherheitBereich(s: Sicherheit): BereichErgebnis {
        val z = ArrayList<Zutat>()
        var abzug = 0
        fun ab(art: Art, punkte: Int) { abzug += punkte; z += Zutat(art, punkte.toDouble()) }
        when {
            s.konflikt >= 2 -> ab(Art.KRIEG, ABZUG_KRIEG)
            s.konflikt == 1 -> ab(Art.KONFLIKT, ABZUG_KONFLIKT)
        }
        if (s.sanktionenEu > 0) ab(Art.SANKTION_EU, ABZUG_SANKTION)
        if (s.sanktionenUs > 0) ab(Art.SANKTION_US, ABZUG_SANKTION)
        val r = abzugReise(s.reise)
        if (r > 0) z += Zutat(Art.REISE, s.reise.toDouble()).also { abzug += r }
        if (z.isEmpty()) z += Zutat(Art.KEINE_ABZUEGE, 0.0)
        return BereichErgebnis(Bereich.SICHERHEIT, (100 - abzug).coerceAtLeast(0).toDouble(), z)
    }

    // Abzug eines Reisehinweises in Punkten (fuer die Anzeige neben der Stufe)
    fun abzugVon(z: Zutat): Int = when (z.art) {
        Art.REISE -> abzugReise(z.wert.toInt())
        else -> z.wert.toInt()
    }

    // Wert einer IWF-Reihe mit dem Ersatzwert, falls er statt des IWF gilt
    private fun iwf(d: Imf.Datensatz, kennung: String, code: String, jahr: Int): Pair<Double, Imf.ErsatzWert?>? =
        Imf.wert(d, kennung, code, jahr)?.let { it to Imf.herkunft(d, kennung, code, jahr) }

    private fun zutat(art: Art, wert: Double, e: Imf.ErsatzWert?) = Zutat(art, wert, jahr = e?.jahr ?: 0, quelle = e?.quelle.orEmpty())

    // Gesamtbild eines Landes fuer das Jahr; null, wenn zu wenig Bereiche da sind.
    fun rechnen(d: Imf.Datensatz, code: String, jahr: Int, zusatz: Zusatz? = null): Ergebnis? {
        val liste = bereiche(d, code, jahr, zusatz)
        val ersten = liste.count { it.bereich == Bereich.WOHLSTAND || it.bereich == Bereich.WACHSTUM || it.bereich == Bereich.STABILITAET }
        if (liste.size < 3 || ersten < 1) return null
        return Ergebnis(jahr, gesamt(liste), liste)
    }

    // Gewichtetes Mittel; fehlende Bereiche zaehlen mit dem gewichteten Mittel der vorhandenen, hoechstens mit DECKEL_FEHLEND
    fun gesamt(liste: List<BereichErgebnis>): Double {
        val gewichte = liste.sumOf { GEWICHT.getValue(it.bereich) }
        val summe = liste.sumOf { GEWICHT.getValue(it.bereich) * it.punkte }
        val fuellung = minOf(summe / gewichte, DECKEL_FEHLEND)
        val fehlend = Bereich.entries.filter { b -> liste.none { it.bereich == b } }.sumOf { GEWICHT.getValue(it) }
        val punkte = (summe + fehlend * fuellung) / 100.0
        // Mit nur drei Bereichen ist die Einstufung duenn: hoechstens DECKEL_FEHLEND Punkte
        return if (liste.size <= 3) minOf(punkte, DECKEL_FEHLEND) else punkte
    }

    // Die vorhandenen Bereiche eines Landes, ohne die Mindestregel fuer das Gesamtbild
    fun bereiche(d: Imf.Datensatz, code: String, jahr: Int, zusatz: Zusatz? = null): List<BereichErgebnis> {
        val liste = ArrayList<BereichErgebnis>(5)

        // Wohlstand
        run {
            val teile = ArrayList<Double>(); val z = ArrayList<Zutat>()
            iwf(d, Imf.BIP_KOPF_KKP, code, jahr)?.let { (v, e) -> teile += einkommen(v); z += zutat(Art.BIP_KOPF, v, e) }
            zusatz?.lebenserwartung?.let { teile += lebenserwartung(it); z += Zutat(Art.LEBENSERWARTUNG, it) }
            if (teile.isNotEmpty()) liste += BereichErgebnis(Bereich.WOHLSTAND, teile.average(), z)
        }
        // Wachstum: Mittel der Jahre von jahr-2 bis jahr+2, mindestens drei davon vorhanden
        run {
            val von = jahr - FENSTER; val bis = jahr + FENSTER
            // nur Werte genau dieser Jahre: ein Ersatzwert wird nicht ueber mehrere Jahre fortgeschrieben
            val reihe = (von..bis).mapNotNull { Imf.wertGenau(d, Imf.WACHSTUM, code, it) }
            if (reihe.size >= 3) {
                val m = reihe.average()
                liste += BereichErgebnis(Bereich.WACHSTUM, wachstum(m), listOf(Zutat(Art.WACHSTUM, m, von = von, bis = bis)))
            }
        }
        // Stabilitaet: mindestens zwei Werte
        run {
            val teile = ArrayList<Double>(); val z = ArrayList<Zutat>()
            iwf(d, Imf.INFLATION, code, jahr)?.let { (v, e) -> teile += inflation(v); z += zutat(Art.INFLATION, v, e) }
            iwf(d, Imf.SCHULDEN, code, jahr)?.let { (v, e) -> teile += schulden(v); z += zutat(Art.SCHULDEN, v, e) }
            iwf(d, Imf.HAUSHALT, code, jahr)?.let { (v, e) -> teile += haushalt(v); z += zutat(Art.HAUSHALT, v, e) }
            iwf(d, Imf.ARBEITSLOS, code, jahr)?.let { (v, e) -> teile += arbeitslos(v); z += zutat(Art.ARBEITSLOS, v, e) }
            iwf(d, Imf.LEISTUNG, code, jahr)?.let { (v, e) -> teile += leistung(v); z += zutat(Art.LEISTUNG, v, e) }
            if (teile.size >= 2) liste += BereichErgebnis(Bereich.STABILITAET, teile.average(), z)
        }
        // Regierungsfuehrung: Mittel der Weltbank-Perzentile, mindestens drei der sechs
        zusatz?.wgi?.takeIf { it.size >= 3 }?.let { w ->
            liste += BereichErgebnis(Bereich.REGIERUNG, w.values.average(), w.map { (k, v) -> Zutat(Art.WGI, v, kennung = k) })
        }
        zusatz?.sicherheit?.let { liste += sicherheitBereich(it) }
        return liste
    }

    // Kuerzel aller Laender mit irgendeiner IWF-Zahl (ohne die Welt)
    fun laender(d: Imf.Datensatz): Set<String> =
        d.werte.values.flatMapTo(HashSet()) { it.keys } + d.ersatz.values.flatMap { it.keys } - Imf.WELT

    // Staaten der Rangliste: 193 Mitglieder der Vereinten Nationen plus Taiwan, Kosovo, Palaestina und Vatikan (alpha-3 wie die
    // Karte). Alle uebrigen Eintraege (Hongkong, Macau, Bermuda, Puerto Rico, Groenland, ...) gelten als Gebiete.
    val STAATEN: Set<String> = (
        "AFG ALB DZA AND AGO ATG ARG ARM AUS AUT AZE BHS BHR BGD BRB BLR BEL BLZ BEN BTN BOL BIH BWA BRA BRN BGR BFA BDI CPV KHM CMR " +
            "CAN CAF TCD CHL CHN COL COM COG COD CRI CIV HRV CUB CYP CZE DNK DJI DMA DOM ECU EGY SLV GNQ ERI EST SWZ ETH FJI FIN FRA " +
            "GAB GMB GEO DEU GHA GRC GRD GTM GIN GNB GUY HTI HND HUN ISL IND IDN IRN IRQ IRL ISR ITA JAM JPN JOR KAZ KEN KIR PRK KOR " +
            "KWT KGZ LAO LVA LBN LSO LBR LBY LIE LTU LUX MDG MWI MYS MDV MLI MLT MHL MRT MUS MEX FSM MDA MCO MNG MNE MAR MOZ MMR NAM " +
            "NRU NPL NLD NZL NIC NER NGA MKD NOR OMN PAK PLW PAN PNG PRY PER PHL POL PRT QAT ROU RUS RWA KNA LCA VCT WSM SMR STP SAU " +
            "SEN SRB SYC SLE SGP SVK SVN SLB SOM ZAF SSD ESP LKA SDN SUR SWE CHE SYR TJK TZA THA TLS TGO TON TTO TUN TUR TKM TUV UGA " +
            "UKR ARE GBR USA URY UZB VUT VEN VNM YEM ZMB ZWE TWN KOS PSE VAT"
        ).split(" ").toSet()

    // IWF-Kuerzel von Kosovo und Westjordanland/Gaza
    private val STAAT_IWF = setOf("UVK", "WBG")

    fun istStaat(code: String): Boolean = code in STAATEN || code in STAAT_IWF

    fun nurStaaten(rangliste: List<GlobusEbenen.Rang>): List<GlobusEbenen.Rang> = rangliste.filter { istStaat(it.code) }

    // Alle Laender mit Ergebnis, bestes zuerst (bei Gleichstand nach Kuerzel, damit die Folge stabil bleibt).
    fun rangliste(d: Imf.Datensatz, codes: Collection<String>, jahr: Int, zusatz: Map<String, Zusatz>): List<GlobusEbenen.Rang> =
        codes.distinct().mapNotNull { c -> rechnen(d, c, jahr, zusatz[c])?.let { GlobusEbenen.Rang(c, it.punkte) } }
            .sortedWith(compareByDescending<GlobusEbenen.Rang> { it.wert }.thenBy { it.code })

    // Platz (ab 1) eines Landes in der Rangliste; null, wenn es keine Punkte hat
    fun platz(rangliste: List<GlobusEbenen.Rang>, code: String): Int? =
        rangliste.indexOfFirst { it.code == code }.takeIf { it >= 0 }?.plus(1)

    // Zusatzzahlen aus den geladenen Quellen. lebenserwartung und wgi je Land (alpha-3); sicherheit nur mit geladenen
    // Konflikt- und Sanktionsdaten.
    fun zusatz(
        lebenserwartung: Double?, wgi: Map<String, Double>, konflikt: Int?, sanktionenEu: Int?, sanktionenUs: Int?, reise: Int?,
    ): Zusatz = Zusatz(
        lebenserwartung, wgi,
        if (konflikt != null && sanktionenEu != null && sanktionenUs != null) Sicherheit(konflikt, sanktionenEu, sanktionenUs, reise ?: 0) else null,
    )
}
