package de.troetenrepublik.app.engine

import kotlin.math.roundToInt

// Werkzeug „Markt schlagen“ (0.78): fest hinterlegte Zahlen aus frei zugaenglichen Studien und Berichten, je Zahl mit
// Quelle, und die Rechnungen daraus. Kein Abruf zur Laufzeit. Jede Zahl steht woertlich in der genannten Quelle
// (Zusammenfassung der Studie oder Tabelle des Berichts); abgeleitete Werte sind Rechnungen dieses Objekts.
object MarktSchlagen {

    // Tag, an dem die Quellen zuletzt gelesen wurden
    const val STAND = "2026-11-12"

    // herausgeber, titel und jahr ergeben die Nennung im Quellenanhang; stand ist der Zeitraum oder Stichtag der Daten.
    data class Quelle(val herausgeber: String, val titel: String, val jahr: Int, val stand: String, val url: String) {
        val name: String get() = "$herausgeber: $titel ($jahr)"
    }

    data class Zahl(val schluessel: String, val wert: Double, val quelle: Quelle)

    val ESMA_KOSTEN = Quelle(
        "ESMA", "Costs and Performance of EU Retail Investment Products 2025", 2026, "2015 bis 2024",
        "https://www.esma.europa.eu/sites/default/files/2026-03/ESMA50-1949966494-4065_Market_Report_-_Costs_and_Performance_of_EU_Retail_Investment_Products.pdf",
    )
    val ESMA_CFD = Quelle(
        "ESMA", "ESMA agrees to prohibit binary options and restrict CFDs to protect retail investors", 2018, "27.03.2018",
        "https://www.esma.europa.eu/press-news/esma-news/esma-agrees-prohibit-binary-options-and-restrict-cfds-protect-retail-investors",
    )
    val BESSEMBINDER_2018 = Quelle(
        "Bessembinder", "Do stocks outperform Treasury bills? Journal of Financial Economics 129", 2018, "seit 1926",
        "https://ideas.repec.org/a/eee/jfinec/v129y2018i3p440-457.html",
    )
    val BESSEMBINDER_2023 = Quelle(
        "Bessembinder, Chen, Choi, Wei", "Long-Term Shareholder Returns: Evidence from 64,000 Global Stocks. Financial Analysts Journal 79", 2023,
        "1990 bis 2020",
        "https://rpc.cfainstitute.org/research/financial-analysts-journal/2023/long-term-shareholder-returns-evidence-from-64000-global-stocks",
    )
    val BARBER_ODEAN_2000 = Quelle(
        "Barber, Odean", "Trading Is Hazardous to Your Wealth. Journal of Finance 55", 2000, "1991 bis 1996",
        "https://faculty.haas.berkeley.edu/odean/papers%20current%20versions/individual_investor_performance_final.pdf",
    )
    val BARBER_2014 = Quelle(
        "Barber, Lee, Liu, Odean", "The cross-section of speculator skill: Evidence from day trading. Journal of Financial Markets 18", 2014,
        "1992 bis 2006",
        "https://ideas.repec.org/a/eee/finmar/v18y2014icp1-24.html",
    )
    val CHAGUE_2020 = Quelle(
        "Chague, De-Losso, Giovannetti", "Day trading for a living? FGV EESP, Textos para discussão 525", 2020, "Einstieg 2013 bis 2015",
        "https://econpapers.repec.org/RePEc:fgv:eesptd:525",
    )

    // Aussage ohne Zahl: Zusammenfassung des Aufsatzes (Crossref-Metadaten der DOI), Rechte beim Verlag, nur zitiert
    val FAMA_FRENCH_2010 = Quelle(
        "Fama, French", "Luck versus Skill in the Cross-Section of Mutual Fund Returns. Journal of Finance 65", 2010, "US-Aktienfonds",
        "https://doi.org/10.1111/j.1540-6261.2010.01598.x",
    )

    // Datensatz hinter dem Baustein ANOMALIEN (engine/Anomalien.kt), Lizenz CC BY 4.0
    val CHEN_ZIMMERMANN = Quelle(
        "Chen, Zimmermann", "Open Source Cross-Sectional Asset Pricing. Critical Finance Review 11, CC BY 4.0", 2022, "Datenstand 2026.10",
        "https://www.openassetpricing.com/",
    )
    val MCLEAN_2016 = Quelle(
        "McLean, Pontiff", "Does Academic Research Destroy Stock Return Predictability? Journal of Finance 71", 2016, "97 Signale",
        "https://doi.org/10.1111/jofi.12365",
    )

    // Reihenfolge wie im Quellenanhang
    val QUELLEN = listOf(
        ESMA_KOSTEN, BESSEMBINDER_2018, BESSEMBINDER_2023, CHEN_ZIMMERMANN, MCLEAN_2016, BARBER_ODEAN_2000, BARBER_2014, CHAGUE_2020, ESMA_CFD,
        FAMA_FRENCH_2010,
    )

    // Quellen, die eine Aussage ohne Zahl belegen
    val AUSSAGEQUELLEN = listOf(FAMA_FRENCH_2010)

    // Quellen, aus denen die App einen Datensatz auswertet statt einzelne Zahlen zu nennen
    val DATENQUELLEN = listOf(CHEN_ZIMMERMANN)

    // Aktienfonds der EU (UCITS), Rendite im Jahr nach laufenden Kosten ueber zehn Jahre, Prozent
    val FONDS_AKTIV = Zahl("fonds.aktiv", 7.6, ESMA_KOSTEN)
    val FONDS_PASSIV = Zahl("fonds.passiv", 9.3, ESMA_KOSTEN)
    val FONDS_ETF = Zahl("fonds.etf", 9.3, ESMA_KOSTEN)
    // Laufende Kosten im Jahr bei zehn Jahren Anlagedauer, Stand 2024, Prozent
    val KOSTEN_AKTIV = Zahl("kosten.aktiv", 1.39, ESMA_KOSTEN)
    val KOSTEN_ETF = Zahl("kosten.etf", 0.26, ESMA_KOSTEN)
    // Anleihefonds der EU im Jahr 2024, Rendite nach laufenden Kosten, Prozent
    val ANLEIHEN_AKTIV = Zahl("anleihen.aktiv", 7.2, ESMA_KOSTEN)
    val ANLEIHEN_PASSIV = Zahl("anleihen.passiv", 6.3, ESMA_KOSTEN)

    // Anteil der boersennotierten US-Unternehmen, der den gesamten Mehrertrag des Aktienmarkts gegenueber kurzfristigen
    // Staatsanleihen erklaert, Prozent
    val WENIGE_USA = Zahl("wenige.usa", 4.0, BESSEMBINDER_2018)
    // Anteil der Aktien mit weniger Ertrag als einmonatige US-Staatsanleihen, Prozent
    val SCHATZ_USA = Zahl("schatz.usa", 55.2, BESSEMBINDER_2023)
    val SCHATZ_WELT = Zahl("schatz.welt", 57.4, BESSEMBINDER_2023)

    // Vergleichsstudie zu den Renditemustern: Zahl der Signale, Rueckgang der Portfoliorendite ausserhalb der Stichprobe
    // vor der Veroeffentlichung und nach der Veroeffentlichung, Prozent
    val MUSTER_ANZAHL = Zahl("muster.anzahl", 97.0, MCLEAN_2016)
    val MUSTER_DAVOR = Zahl("muster.davor", 26.0, MCLEAN_2016)
    val MUSTER_DANACH = Zahl("muster.danach", 58.0, MCLEAN_2016)

    // Haushalte bei einem US-Discountbroker: Rendite im Jahr nach Kosten, Prozent
    val HAUSHALTE_ANZAHL = Zahl("haushalte.anzahl", 66465.0, BARBER_ODEAN_2000)
    val HAUSHALTE_MARKT = Zahl("haushalte.markt", 17.9, BARBER_ODEAN_2000)
    val HAUSHALTE_SCHNITT = Zahl("haushalte.schnitt", 16.4, BARBER_ODEAN_2000)
    val HAUSHALTE_VIEL = Zahl("haushalte.viel", 11.4, BARBER_ODEAN_2000)

    // Daytrader in Taiwan: weniger als dieser Anteil erzielt verlaesslich eine Ueberrendite nach Gebuehren, Prozent
    val TAIWAN_GRENZE = Zahl("taiwan.grenze", 1.0, BARBER_2014)
    // Die 500 besten des Vorjahrs: Rendite am Tag nach Gebuehren, Basispunkte
    val TAIWAN_BESTE = Zahl("taiwan.beste", 37.9, BARBER_2014)
    val TAIWAN_BESTE_ANZAHL = Zahl("taiwan.besteAnzahl", 500.0, BARBER_2014)
    // Daytrader in Brasilien, die mindestens 300 Tage dabeiblieben: Anteil mit Verlust, Prozent
    val BRASILIEN_VERLUST = Zahl("brasilien.verlust", 97.0, CHAGUE_2020)
    val BRASILIEN_TAGE = Zahl("brasilien.tage", 300.0, CHAGUE_2020)

    // CFD-Konten von Kleinanlegern mit Verlust: Spanne der nationalen Aufsichtsbehoerden, Prozent
    val CFD_VON = Zahl("cfd.von", 74.0, ESMA_CFD)
    val CFD_BIS = Zahl("cfd.bis", 89.0, ESMA_CFD)

    val ZAHLEN = listOf(
        FONDS_AKTIV, FONDS_PASSIV, FONDS_ETF, KOSTEN_AKTIV, KOSTEN_ETF, ANLEIHEN_AKTIV, ANLEIHEN_PASSIV, WENIGE_USA, SCHATZ_USA, SCHATZ_WELT,
        MUSTER_ANZAHL, MUSTER_DAVOR, MUSTER_DANACH,
        HAUSHALTE_ANZAHL, HAUSHALTE_MARKT, HAUSHALTE_SCHNITT, HAUSHALTE_VIEL, TAIWAN_GRENZE, TAIWAN_BESTE, TAIWAN_BESTE_ANZAHL,
        BRASILIEN_VERLUST, BRASILIEN_TAGE, CFD_VON, CFD_BIS,
    )

    // Bausteine der Seite in ihrer Reihenfolge
    enum class Baustein { FONDS, KOSTEN, WENIGE, SCHATZ, ANOMALIEN, HAUSHALTE, TAIWAN, BRASILIEN, CFD }

    // Annahmen der Kostenrechnung (keine Quelle): einmalige Anlage, Wertzuwachs im Jahr vor Kosten, Dauer
    const val RECHNUNG_START = 10_000.0
    const val RECHNUNG_BRUTTO = 6.0
    const val RECHNUNG_JAHRE = 30

    // Punkte des Rasters
    const val RASTER = 100

    // Abstand in Prozentpunkten, auf eine Stelle gerundet (die Quellen nennen eine Stelle)
    private fun abstand(a: Double, b: Double): Double = ((a - b) * 10).roundToInt() / 10.0

    // Aktive Aktienfonds hinter Aktien-ETFs, Prozentpunkte im Jahr
    fun fondsAbstand(): Double = abstand(FONDS_ETF.wert, FONDS_AKTIV.wert)

    // Haushalte mit dem meisten Handel hinter dem Markt, Prozentpunkte im Jahr
    fun haushalteAbstand(): Double = abstand(HAUSHALTE_MARKT.wert, HAUSHALTE_VIEL.wert)

    // Wert der Anlage am Ende jedes Jahres, beginnend mit dem Start: Zuwachs vor Kosten, danach die laufenden Kosten
    // auf den Wert des Jahresendes.
    fun kostenVerlauf(
        kostenProzent: Double, start: Double = RECHNUNG_START, bruttoProzent: Double = RECHNUNG_BRUTTO, jahre: Int = RECHNUNG_JAHRE,
    ): List<Double> {
        val faktor = (1 + bruttoProzent / 100) * (1 - kostenProzent / 100)
        val werte = ArrayList<Double>(jahre + 1)
        var wert = start
        werte += wert
        repeat(jahre.coerceAtLeast(0)) { wert *= faktor; werte += wert }
        return werte
    }

    fun kostenEndwert(kostenProzent: Double): Double = kostenVerlauf(kostenProzent).last()

    // Endwert mit den Kosten des ETF minus Endwert mit den Kosten des aktiven Fonds
    fun kostenLuecke(): Double = kostenEndwert(KOSTEN_ETF.wert) - kostenEndwert(KOSTEN_AKTIV.wert)

    // Gefuellte Punkte eines Rasters aus 100 fuer einen Anteil in Prozent
    fun punkte(prozent: Double): Int = prozent.roundToInt().coerceIn(0, RASTER)

    // Quellen eines Bausteins
    fun quellen(b: Baustein): List<Quelle> = when (b) {
        Baustein.FONDS -> listOf(ESMA_KOSTEN, FAMA_FRENCH_2010)
        Baustein.KOSTEN -> listOf(ESMA_KOSTEN)
        Baustein.WENIGE -> listOf(BESSEMBINDER_2018)
        Baustein.SCHATZ -> listOf(BESSEMBINDER_2023)
        Baustein.ANOMALIEN -> listOf(CHEN_ZIMMERMANN, MCLEAN_2016)
        Baustein.HAUSHALTE -> listOf(BARBER_ODEAN_2000)
        Baustein.TAIWAN -> listOf(BARBER_2014)
        Baustein.BRASILIEN -> listOf(CHAGUE_2020)
        Baustein.CFD -> listOf(ESMA_CFD)
    }
}
