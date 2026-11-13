package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.Lang
import de.troetenrepublik.app.data.WidgetKarte
import de.troetenrepublik.app.data.WidgetKarten
import de.troetenrepublik.app.data.WidgetKartenBauAbschnitte
import de.troetenrepublik.app.data.WidgetVorgabe
import de.troetenrepublik.app.data.WidgetZeile
import de.troetenrepublik.app.data.WidgetZiel
import de.troetenrepublik.app.i18n.I18n
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.market.AchtK
import de.troetenrepublik.app.market.Praesidialdokumente
import de.troetenrepublik.app.ui.Fmt
import de.troetenrepublik.app.widgetBranche
import de.troetenrepublik.app.widgetInsiderSim
import de.troetenrepublik.app.widgetKonvergenz
import de.troetenrepublik.app.widgetPraesidial
import de.troetenrepublik.app.widgetSelten
import de.troetenrepublik.app.widgetVorschlag
import de.troetenrepublik.app.widgetWarnzeichen
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

// Widgets zu den Abschnitten von 0.77 (Teil AD11): Konvergenz, seltene Insiderkaeufe, Insider-Simulator, Vorschlagsdepot, Branche,
// Warnzeichen, Praesidialdokumente. Aufbau der Karten aus festen Beispieldaten, Hinweise ohne Daten, Zuschnitt (langer Name
// faellt auf das Kuerzel oder weg, statt umzubrechen), Ziele und Vorgaben.
class Fassung077Ad11WidgetTest {

    @Before fun deutsch() { I18n.lang = Lang.DE }

    private fun tag(iso: String) = de.troetenrepublik.app.market.Politik.tag(iso)

    // ------------------------------------------------------------ Konvergenz

    private fun konvergenz() = listOf(
        Konvergenz.Zeile("NVDA", "Nvidia", insider = 3, kongress = 2, fonds = 900),
        Konvergenz.Zeile("MSFT", "Microsoft", insider = 0, kongress = 4, fonds = 700),
        Konvergenz.Zeile("BRK-B", "Berkshire Hathaway Class B", insider = 2, kongress = 0, fonds = 300),
    )

    @Test fun konvergenzKarteMitDaten() {
        val k = widgetKonvergenz(konvergenz(), "2026-11-10", 5L)
        assertEquals("Gleichzeitige Käufe", k.titel)
        assertEquals(3, k.zeilen.size)
        // Unter dem Namen die Zahlen je kaufender Gruppe, kein Balken; die Zahl der Gruppen rechts, weil die Liste auch zwei Gruppen kennt
        assertEquals("3/3", k.zeilen[0].wert)
        assertEquals("Insider 3 · Kongress 2 · Fonds 900", k.zeilen[0].unter)
        assertEquals("2/3", k.zeilen[1].wert)
        assertEquals("Kongress 4 · Fonds 700", k.zeilen[1].unter)
        assertEquals("Insider 2 · Fonds 300", k.zeilen[2].unter)
        assertTrue(k.zeilen.all { it.balken.isEmpty() })
        assertTrue(k.zeilen.all { it.einzeilig })
        // Kuerzel als Ersatz, wenn der Name nicht passt
        assertEquals(listOf("NVDA", "MSFT", "BRK-B"), k.zeilen.map { it.ersatz })
        assertEquals(Fmt.dayMonthNum(tag("2026-11-10")).let { "90 Tage bis $it" }, k.stand)
        assertEquals("copy-kv", k.ziel)
        assertEquals("copy-kv", WidgetZiel.pruefen(k.ziel))
        assertEquals(5L, k.am)
    }

    @Test fun konvergenzNimmtHoechstensAchtWerte() {
        val l = (1..12).map { Konvergenz.Zeile("S$it", "Firma $it", 1, 1, 0) }
        assertEquals(8, widgetKonvergenz(l, "2026-11-10", 1L).zeilen.size)
    }

    @Test fun konvergenzOhneZweiGruppenZeigtRechtsNichts() {
        val l = listOf(Konvergenz.Zeile("NVDA", "Nvidia", 3, 2, 900), Konvergenz.Zeile("MSFT", "Microsoft", 1, 4, 700))
        val k = widgetKonvergenz(l, "2026-11-10", 1L)
        assertTrue(k.zeilen.all { it.wert.isEmpty() })
        assertEquals("Insider 3 · Kongress 2 · Fonds 900", k.zeilen[0].unter)
    }

    @Test fun konvergenzOhneDatenHatKeineZeilenUndEinenHinweis() {
        val k = widgetKonvergenz(emptyList(), "2026-11-10", 3L)
        assertTrue(k.zeilen.isEmpty())
        assertEquals("Gerade keine Aktie mit zwei Gruppen", k.hinweis)
        assertEquals("", k.stand)
    }

    @Test fun konvergenzAufEnglisch() {
        I18n.lang = Lang.EN
        val k = widgetKonvergenz(konvergenz(), "2026-11-10", 1L)
        assertEquals("Simultaneous buys", k.titel)
        assertEquals("Insiders 3 · Congress 2 · Funds 900", k.zeilen[0].unter)
        assertTrue(k.stand.startsWith("90 days to "))
    }

    @Test fun langerNameFaelltAufDasKuerzelStattUmzubrechen() {
        val k = widgetKonvergenz(konvergenz(), "2026-11-10", 1L)
        val schmal = WidgetKarten.zuschneiden(k, 140, 240)
        // "Berkshire Hathaway Class B" passt nicht in eine Zeile: das Kuerzel steht, nie ein Umbruch
        assertTrue(schmal.zeilen.any { it.text == "BRK-B" })
        assertTrue(schmal.zeilen.none { it.text.startsWith("Berkshire") })
        val breit = WidgetKarten.zuschneiden(k, 360, 300)
        assertTrue(breit.zeilen.any { it.text.startsWith("Berkshire Hathaway") })
    }

    @Test fun geklemmterTextBehaeltDenWertUndZeigtHoechstensDieZeilen() {
        val k = WidgetKarte("T", listOf(WidgetZeile("Ein sehr langer Name einer Firma mit vielen Woertern darin", "1,8 Mio. $", klemmen = 1)))
        val z = WidgetKarten.zuschneiden(k, 250, 200).zeilen
        assertEquals(1, z.size)
        assertEquals("1,8 Mio. $", z[0].wert)
    }

    @Test fun passtWederNameNochKuerzelFaelltDieZeileWeg() {
        val k = WidgetKarte("T", listOf(WidgetZeile("Berkshire Hathaway Class B", "3/3", einzeilig = true, ersatz = "BRK-B Berkshire Hathaway")))
        assertEquals(0, WidgetKarten.zuschneiden(k, 120, 200).zeilen.size)
    }

    // ------------------------------------------------------------ Seltene Insiderkaeufe

    private fun funde() = listOf(
        InsiderSeltenheit.Fund("INTC", "Intel", InsiderSeltenheit.Art.ERSTER, "2026-10-20", 1_800_000.0, "2025-03-12", false, 426),
        InsiderSeltenheit.Fund("NKE", "Nike", InsiderSeltenheit.Art.GROESSTER, "2026-11-05", 900_000.0, "2024-05-02", false, 550, faktor = 3.3),
        InsiderSeltenheit.Fund("DIS", "Disney", InsiderSeltenheit.Art.WOCHE, "2026-11-02", 600_000.0, "2024-06-20", false, 700, zahl = 5),
        InsiderSeltenheit.Fund("ZZZ", "Neu in den Daten", InsiderSeltenheit.Art.ERSTER, "2026-10-01", 600_000.0, "2025-06-01", true, 400),
    )

    @Test fun seltenKarteMitDaten() {
        val k = widgetSelten(funde(), "2026-11-10", 7L)
        assertEquals("Seltene Käufe", k.titel)
        // die jüngsten zuerst
        assertEquals(listOf("Nike", "Walt Disney", "Intel", "Neu in den Daten"), k.zeilen.map { it.text })
        assertEquals(Fmt.dollarKompakt(900_000.0), k.zeilen[0].wert)
        // dieselben kurzen Aussagen wie in der App
        assertEquals("Größter Kauf, ${Fmt.num(3.3, 1)}× der bisherige Rekord", k.zeilen[0].unter)
        assertEquals("5 Käufer in einer Woche", k.zeilen[1].unter)
        assertEquals("Erster Kauf seit 14 Monaten", k.zeilen[2].unter)
        assertEquals("Erster Kauf in 13 Monaten", k.zeilen[3].unter)
        // der Betrag steht immer rechts, ein langer Name wird in der Zeile abgeschnitten statt ersetzt oder umgebrochen
        assertTrue(k.zeilen.all { it.klemmen == 1 && it.wert.isNotBlank() && it.ersatz.isEmpty() })
        assertTrue(k.stand.startsWith("30 Tage bis "))
        assertEquals("copytrading/VORSTAENDE", k.ziel)
    }

    @Test fun seltenOhneDatenHatEinenHinweis() {
        val k = widgetSelten(emptyList(), "", 1L)
        assertTrue(k.zeilen.isEmpty())
        assertEquals("Keine seltenen Käufe", k.hinweis)
        assertEquals("", k.stand)
    }

    @Test fun seltenZeigtHoechstensAchtZeilen() {
        val l = (1..12).map { InsiderSeltenheit.Fund("S$it", "Firma $it", InsiderSeltenheit.Art.ERSTER, "2026-11-0${it % 9 + 1}", 600_000.0, "2025-01-01", false, 400) }
        assertEquals(8, widgetSelten(l, "2026-11-10", 1L).zeilen.size)
    }

    @Test fun langerNameLaeuftAusUndDerBetragBleibtStehen() {
        val lang = InsiderSeltenheit.Fund("X", "Bluerock Private Real Estate Fund Incorporated of America", InsiderSeltenheit.Art.ERSTER, "2026-11-05", 4_820_000.0, "2025-01-01", false, 400)
        val k = widgetSelten(listOf(lang) + funde(), "2026-11-10", 1L)
        val z = WidgetKarten.zuschneiden(k, 250, 340)
        assertEquals(k.zeilen.size, z.zeilen.size)
        assertTrue(z.zeilen.all { it.wert.isNotBlank() && !it.gestapelt })
        assertTrue(z.mitUnter)
    }

    @Test fun hoeheBestimmtDieZahlDerZeilen() {
        val k = widgetSelten(funde() + (1..6).map { InsiderSeltenheit.Fund("S$it", "Firma $it", InsiderSeltenheit.Art.ERSTER, "2026-10-1$it", 600_000.0, "2025-01-01", false, 400) }, "2026-11-10", 1L)
        val klein = WidgetKarten.zuschneiden(k, 250, 150).zeilen.size
        val gross = WidgetKarten.zuschneiden(k, 250, 480).zeilen.size
        assertTrue(klein < gross)
        assertEquals(8, gross)
    }

    // ------------------------------------------------------------ Insider-Simulator

    private fun cluster(rendite: Double = 0.079, index: Double = 0.023) = WirktDas.Cluster(
        stand = "2026-11-10", ab = "2020-01-02", halten = 60, fenster = 10, mindest = 3, cluster = 160, ausgewertet = 148,
        rendite = rendite, index = index, median = 0.03, indexMedian = 0.02, treffer = 0.61,
        schlechtester = WirktDas.Extrem("AAA", "2022-01-03", -0.4), bester = WirktDas.Extrem("BBB", "2021-01-04", 1.1),
        jahre = listOf(WirktDas.ClusterJahr(2020, 10, 0.1, 0.05, 0.6)), kurve = listOf(listOf(0.0, 0.0, 0.0), listOf(60.0, 0.079, 0.023)),
    )

    @Test fun simulatorKarteMitDaten() {
        val k = widgetInsiderSim(cluster(), 9L)
        assertEquals("Insider-Cluster-Käufe", k.titel)
        assertEquals(Fmt.signedPct(7.9, 1), k.haupt)
        assertEquals("Ø nach 60 Handelstagen", k.hauptUnter)
        // die grosse Zahl bleibt neutral, der Vorsprung gegen den Index traegt die Farbe
        assertEquals(de.troetenrepublik.app.data.WidgetGrafik.NEUTRAL, k.hauptTon)
        assertEquals(6, k.zeilen.size)
        assertEquals("S&P 500", k.zeilen[0].text)
        assertEquals(Fmt.signedPct(2.3, 1), k.zeilen[0].wert)
        assertEquals("Vorsprung", k.zeilen[1].text)
        assertEquals("+5,6 Pkt.", k.zeilen[1].wert)
        assertEquals(true, k.zeilen[1].hoch)
        assertEquals(Fmt.pct(61.0, 0), k.zeilen[2].wert)
        assertEquals(listOf(0.61), k.zeilen[2].balken)
        assertEquals("148", k.zeilen[3].wert)
        assertEquals("seit 2020", k.zeilen[3].unter)
        assertEquals(Fmt.signedPct(3.0, 1), k.zeilen[4].wert)
        assertEquals("AAA", k.zeilen[5].unter)
        assertEquals(false, k.zeilen[5].hoch)
        assertEquals("148 Cluster seit 2020", k.stand)
        assertEquals("copy-wd", k.ziel)
        assertEquals("copy-wd", WidgetZiel.pruefen(k.ziel))
    }

    @Test fun hinterDemIndexIstDerVorsprungRotUndDieGrosseZahlNeutral() {
        val k = widgetInsiderSim(cluster(rendite = 0.035, index = 0.051), 1L)
        assertEquals(de.troetenrepublik.app.data.WidgetGrafik.NEUTRAL, k.hauptTon)
        assertEquals(Fmt.signedPct(3.5, 1), k.haupt)
        assertEquals("−1,6 Pkt.", k.zeilen[1].wert)
        assertEquals(false, k.zeilen[1].hoch)
    }

    @Test fun simulatorOhneDatenHatEinenHinweisOhneZahl() {
        val k = widgetInsiderSim(null, 4L)
        assertEquals("", k.haupt)
        assertTrue(k.zeilen.isEmpty())
        assertEquals("Keine Rückschau", k.hinweis)
    }

    @Test fun simulatorAufEnglisch() {
        I18n.lang = Lang.EN
        val k = widgetInsiderSim(cluster(), 1L)
        assertEquals("148 clusters since 2020", k.stand)
        assertEquals("Hit rate", k.zeilen[2].text)
        assertEquals("+5.6 pts", k.zeilen[1].wert)
    }

    @Test fun simulatorZeigtDieZahlAuchInSchmalenWidgets() {
        val k = widgetInsiderSim(cluster(), 1L)
        val z = WidgetKarten.zuschneiden(k, 250, 200)
        assertTrue(z.haupt)
        assertTrue(z.zeilen.isNotEmpty())
    }

    // ------------------------------------------------------------ Vorschlagsdepot und Branche

    private fun erg(symbol: String, name: String, branche: String, punkte: Double) = Aktienfinder.Ergebnis(
        Aktienfinder.Daten(symbol, name, land = "US", branche = branche), punkte, emptyMap(), Aktienfinder.Einordnung.FAIR, emptyList(), 1.0, emptyMap(),
    )

    private fun depot(): Vorschlagsdepot.Depot {
        val l = listOf(
            erg("MSFT", "Microsoft", "SOFTWARE", 90.0), erg("V", "Visa", "BANKEN", 80.0), erg("COST", "Costco", "EINZELHANDEL", 75.0),
            erg("BRK-B", "Berkshire Hathaway Class B", "HOLDINGS", 70.0), erg("LLY", "Eli Lilly", "PHARMA", 66.0), erg("PG", "Procter & Gamble", "NAHRUNG", 64.0),
        )
        val w = listOf(0.08, 0.075, 0.07, 0.065, 0.06, 0.055)
        return Vorschlagsdepot.Depot(l.mapIndexed { i, e -> Vorschlagsdepot.Eintrag(e, Vorschlagsdepot.sektor(e.daten.branche)!!, w[i], null) }, l.size)
    }

    @Test fun vorschlagKarteMitDaten() {
        val d = depot()
        val k = widgetVorschlag(d, 3L)
        assertEquals("Vorschlagsdepot", k.titel)
        assertEquals(6, k.zeilen.size)
        assertEquals(listOf("Microsoft", "Visa", "Costco", "Berkshire Hathaway Class B", "Eli Lilly", "Procter & Gamble"), k.zeilen.map { it.text })
        assertEquals(Fmt.pct(d.eintraege[0].gewicht * 100, 1), k.zeilen[0].wert)
        // kein Balken, klein darunter der Sektor
        assertTrue(k.zeilen.all { it.balken.isEmpty() })
        assertEquals(S.vsdSektor("TECHNOLOGIE"), k.zeilen[0].unter)
        assertEquals(S.vsdSektor("FINANZEN"), k.zeilen[1].unter)
        // Standzeile mit den echten Zahlen: sechs Werte aus fuenf Sektoren
        assertEquals("6 Werte · 5 Sektoren", k.stand)
        assertEquals("BRK-B", k.zeilen[3].ersatz)
        assertEquals("aktienfinder", k.ziel)
        assertEquals("aktienfinder", WidgetZiel.pruefen(k.ziel))
    }

    @Test fun vorschlagOhneZwischenspeicherHatEinenHinweisStattLaedt() {
        val k = widgetVorschlag(null, 0L)
        assertTrue(k.zeilen.isEmpty())
        assertEquals("Aktienfinder nicht geladen", k.hinweis)
        assertEquals("", k.stand)
        assertEquals(0L, k.am)
    }

    @Test fun vorschlagMitGeladenemFinderOhnePositionenHatEinenAndernHinweis() {
        val k = widgetVorschlag(Vorschlagsdepot.Depot(emptyList(), 0), 2L)
        assertTrue(k.zeilen.isEmpty())
        assertEquals("Keine Aktie erfüllt die Bedingungen", k.hinweis)
    }

    private fun softwareWerte() = listOf(
        erg("MSFT", "Microsoft", "SOFTWARE", 84.0), erg("ADBE", "Adobe", "SOFTWARE", 78.0), erg("ORCL", "Oracle", "SOFTWARE", 74.0),
        erg("CRM", "Salesforce", "SOFTWARE", 70.0), erg("INTU", "Intuit", "SOFTWARE", 66.0), erg("NOW", "ServiceNow", "SOFTWARE", 60.0),
    ) + (1..5).map { erg("B$it", "Bank $it", "BANKEN", 50.0 + it) } + listOf(erg("X1", "Einzelfirma", "BERGBAU", 99.0))

    @Test fun brancheKarteZeigtDieBestenAktienDerGewaehltenBranche() {
        val k = widgetBranche(softwareWerte(), "SOFTWARE", 6L)
        assertEquals(S.industryName("SOFTWARE"), k.titel)
        assertEquals(listOf("Microsoft", "Adobe", "Oracle", "Salesforce", "Intuit", "ServiceNow"), k.zeilen.map { it.text })
        assertEquals("84", k.zeilen[0].wert)
        assertEquals(listOf(0.84), k.zeilen[0].balken)
        assertEquals("6 Werte", k.stand)
        assertTrue(k.zeilen.all { it.einzeilig })
        assertEquals("aktienfinder", k.ziel)
    }

    @Test fun brancheOhneWahlZeigtDieStaerksteBranche() {
        val k = widgetBranche(softwareWerte(), null, 1L)
        assertEquals(S.industryName("SOFTWARE"), k.titel)
        assertEquals("Microsoft", k.zeilen[0].text)
    }

    @Test fun brancheMitZuWenigenWertenOderOhneFinderHatEinenHinweis() {
        val klein = widgetBranche(softwareWerte(), "BERGBAU", 1L)
        assertTrue(klein.zeilen.isEmpty())
        assertEquals("Zu wenige Werte in der Branche", klein.hinweis)
        assertEquals(S.industryName("BERGBAU"), klein.titel)
        val ohne = widgetBranche(null, "SOFTWARE", 0L)
        assertTrue(ohne.zeilen.isEmpty())
        assertEquals("Aktienfinder nicht geladen", ohne.hinweis)
        assertEquals(0L, ohne.am)
    }

    @Test fun brancheAufEnglisch() {
        I18n.lang = Lang.EN
        val k = widgetBranche(softwareWerte(), "SOFTWARE", 1L)
        assertEquals(S.fbrWerte(6), k.stand)
        assertEquals("Internet & software", k.titel)
    }

    // ------------------------------------------------------------ Warnzeichen

    private fun warnungen() = listOf(
        AchtK.Warnzeichen(AchtK.Art.FUEHRUNG, "2026-09-14", AchtK.Rolle.CEO, "0001-26-000001", "a.htm"),
        AchtK.Warnzeichen(AchtK.Art.PRUEFER, "2026-03-02", AchtK.Rolle.KEINE, "0001-26-000002", "b.htm"),
    )

    @Test fun warnzeichenKarteMitDaten() {
        val k = widgetWarnzeichen("TSLA", warnungen(), "Stand 10:00", 8L)
        assertEquals("Warnzeichen · TSLA", k.titel)
        assertEquals("2", k.haupt)
        assertEquals("Warnzeichen, 24 Monate", k.hauptUnter)
        assertEquals(de.troetenrepublik.app.data.WidgetGrafik.GELB, k.hauptTon)
        // das juengste mit Datum
        assertEquals(1, k.zeilen.size)
        assertEquals("Abgang des CEO", k.zeilen[0].text)
        assertEquals(Fmt.date(tag("2026-09-14")), k.zeilen[0].wert)
        assertEquals("instrument/TSLA", k.ziel)
        assertEquals("instrument/TSLA", WidgetZiel.pruefen(k.ziel))
    }

    @Test fun schweresWarnzeichenFaerbtDieZahlRot() {
        val l = warnungen() + AchtK.Warnzeichen(AchtK.Art.KORREKTUR, "2026-01-05", AchtK.Rolle.KEINE, "0001-26-000003", "c.htm")
        val k = widgetWarnzeichen("TSLA", l, "", 1L)
        assertEquals("3", k.haupt)
        assertEquals(de.troetenrepublik.app.data.WidgetGrafik.ROT, k.hauptTon)
        // das juengste bleibt das vom September
        assertEquals("Abgang des CEO", k.zeilen[0].text)
        val juengstSchwer = widgetWarnzeichen("TSLA", listOf(AchtK.Warnzeichen(AchtK.Art.INSOLVENZ, "2026-10-01", AchtK.Rolle.KEINE, "x", "d.htm")), "", 1L)
        assertEquals(false, juengstSchwer.zeilen[0].hoch)
    }

    @Test fun ohneWarnzeichenStehtEineRuhigeZeile() {
        val k = widgetWarnzeichen("AAPL", emptyList(), "Stand 10:00", 2L)
        assertEquals("", k.haupt)
        assertTrue(k.zeilen.isEmpty())
        assertEquals("Keine Warnzeichen", k.hinweis)
        val z = WidgetKarten.zuschneiden(k, 200, 150)
        assertEquals("Keine Warnzeichen", z.hinweis)
    }

    @Test fun warnzeichenHinweiseOhneZahl() {
        val k = WidgetKartenBauAbschnitte.warnzeichenHinweis("SAP.DE", S.widgetWarnNurUs, 1L)
        assertEquals("Warnzeichen · SAP", k.titel)
        assertEquals("Nur US-Aktien", k.hinweis)
        assertEquals("", k.haupt)
        assertEquals("Kontaktadresse unter Zugänge fehlt", S.widgetWarnOhneKennung)
    }

    @Test fun warnzeichenAufEnglisch() {
        I18n.lang = Lang.EN
        val k = widgetWarnzeichen("TSLA", warnungen(), "", 1L)
        assertEquals("Warning signs · TSLA", k.titel)
        assertEquals("CEO leaves", k.zeilen[0].text)
        assertEquals("No warning signs", widgetWarnzeichen("TSLA", emptyList(), "", 1L).hinweis)
    }

    // ------------------------------------------------------------ Praesidialdokumente

    private fun dokumente() = listOf(
        Praesidialdokumente.Dokument("2026-1", "2026-10-21", Praesidialdokumente.Art.MEMORANDUM, "Memorandum on Federal Hiring", "https://www.federalregister.gov/d/1"),
        Praesidialdokumente.Dokument("2026-4", "2026-11-04", Praesidialdokumente.Art.ERLASS, "Adjusting Imports of Steel into the United States", "https://www.federalregister.gov/d/4"),
        Praesidialdokumente.Dokument("2026-2", "2026-10-29", Praesidialdokumente.Art.PROKLAMATION, "National Day", "https://www.federalregister.gov/d/2"),
        Praesidialdokumente.Dokument("2026-0", "2026-09-01", Praesidialdokumente.Art.ERLASS, "Alt", "https://www.federalregister.gov/d/0"),
    )

    @Test fun praesidialKarteZeigtDieDreiJuengsten() {
        val k = widgetPraesidial(dokumente(), "Stand 10:00", 5L)
        assertEquals("Präsidialdokumente", k.titel)
        assertEquals(listOf("Adjusting Imports of Steel into the United States", "National Day", "Memorandum on Federal Hiring", "Alt"), k.zeilen.map { it.text })
        // Datum und Art klein darunter, rechts nichts
        assertEquals(Fmt.dayMonthNum(tag("2026-11-04")) + " · Executive Order", k.zeilen[0].unter)
        assertEquals(Fmt.dayMonthNum(tag("2026-10-29")) + " · Proklamation", k.zeilen[1].unter)
        assertTrue(k.zeilen.all { it.wert.isEmpty() && it.klemmen == 2 && it.ersatz.isEmpty() })
        assertEquals("Stand 10:00", k.stand)
        assertEquals("copytrading/POLITIK/REGIERUNG", k.ziel)
        assertEquals(k.ziel, WidgetZiel.pruefen(k.ziel))
    }

    @Test fun langerTitelBleibtStehenUndKeinErsatztextKommt() {
        val k = widgetPraesidial(dokumente(), "", 1L)
        val z = WidgetKarten.zuschneiden(k, 250, 340)
        assertEquals(4, z.zeilen.size)
        assertEquals("Adjusting Imports of Steel into the United States", z.zeilen[0].text)
        assertTrue(z.zeilen.none { it.text.contains("…") })
        assertTrue(z.mitUnter)
    }

    @Test fun hoeheBestimmtDieZahlDerDokumente() {
        val viele = (1..12).map { Praesidialdokumente.Dokument("n$it", "2026-10-${10 + it}", Praesidialdokumente.Art.PROKLAMATION, "Proclamation number $it of the year", "https://www.federalregister.gov/d/$it") }
        val k = widgetPraesidial(viele, "", 1L)
        assertEquals(8, k.zeilen.size)
        assertTrue(WidgetKarten.zuschneiden(k, 250, 160).zeilen.size < WidgetKarten.zuschneiden(k, 250, 480).zeilen.size)
    }

    @Test fun praesidialOhneDatenHatEinenHinweis() {
        val k = widgetPraesidial(emptyList(), "Stand 10:00", 1L)
        assertTrue(k.zeilen.isEmpty())
        assertEquals("Keine Dokumente", k.hinweis)
        assertEquals("", k.stand)
    }

    // ------------------------------------------------------------ Arten, Ziele, Vorgaben

    @Test fun artenSindAnlegbarMitTitelUndZiel() {
        for (a in WidgetKarten.ARTEN_ABSCHNITTE) {
            assertTrue(a in WidgetKarten.ARTEN)
            assertTrue(a in WidgetKarten.ANLEGBAR)
            assertTrue(WidgetKarten.titel(a), WidgetKarten.titel(a).isNotBlank() && WidgetKarten.titel(a) != a)
            assertNotNull(a, WidgetKarten.ziel(a).takeIf { it.isNotBlank() })
        }
        assertEquals(WidgetKarten.ARTEN.size, WidgetKarten.ARTEN.toSet().size)
        assertEquals("Gleichzeitige Käufe", WidgetKarten.titel(WidgetKarten.KONVERGENZ))
        assertEquals("Seltene Käufe", WidgetKarten.titel(WidgetKarten.INSIDERSELTEN))
        assertEquals("Vorschlagsdepot", WidgetKarten.titel(WidgetKarten.VORSCHLAG))
        assertEquals("Präsidialdokumente", WidgetKarten.titel(WidgetKarten.PRAESIDIAL))
        // Ziele, die die App von aussen annimmt
        assertEquals("copytrading/VORSTAENDE", WidgetKarten.ziel(WidgetKarten.INSIDERSELTEN))
        for (a in listOf(WidgetKarten.KONVERGENZ, WidgetKarten.INSIDERSELTEN, WidgetKarten.INSIDERSIM, WidgetKarten.VORSCHLAG, WidgetKarten.BRANCHE, WidgetKarten.PRAESIDIAL)) {
            assertEquals(a, WidgetKarten.ziel(a), WidgetZiel.pruefen(WidgetKarten.ziel(a)))
        }
    }

    @Test fun langdruckLegtDieWidgetsAnMitDerWahlDerStelle() {
        assertEquals(WidgetKarten.KONVERGENZ, WidgetVorgabe.konvergenz().art)
        assertEquals(WidgetKarten.INSIDERSELTEN, WidgetVorgabe.insiderSelten().art)
        assertEquals(WidgetKarten.INSIDERSIM, WidgetVorgabe.insiderSimulator().art)
        assertEquals(WidgetKarten.VORSCHLAG, WidgetVorgabe.vorschlagsdepot().art)
        assertEquals(WidgetKarten.PRAESIDIAL, WidgetVorgabe.praesidial().art)
        assertTrue(WidgetVorgabe.konvergenz().einstellung.isEmpty())
        // Branche und Warnzeichen tragen ihre Wahl als Variante
        val b = WidgetVorgabe.branche("OEL_GAS")!!
        assertEquals(WidgetKarten.BRANCHE, b.art)
        assertEquals("OEL_GAS", b.einstellung[WidgetVorgabe.VARIANTE])
        assertNull(WidgetVorgabe.branche(FinderBranchen.SAMMEL))
        assertNull(WidgetVorgabe.branche("kleinschrift"))
        val w = WidgetVorgabe.warnzeichen("BRK-B")!!
        assertEquals("BRK-B", w.einstellung[WidgetVorgabe.VARIANTE])
        assertNull(WidgetVorgabe.warnzeichen(""))
        assertNull(WidgetVorgabe.warnzeichen("a b"))
        assertEquals("branche:OEL_GAS", WidgetVorgabe.kartenSchluessel(WidgetKarten.BRANCHE, "OEL_GAS"))
        assertEquals("warnzeichen:BRK-B", WidgetVorgabe.kartenSchluessel(WidgetKarten.WARNZEICHEN, "BRK-B"))
        assertEquals("konvergenz", WidgetVorgabe.kartenSchluessel(WidgetKarten.KONVERGENZ, "x"))
        assertEquals("branche", WidgetVorgabe.art("branche:OEL_GAS"))
    }

    @Test fun karteUeberlebtSpeichernMitErsatz() {
        val json = Json { ignoreUnknownKeys = true }
        val k = widgetKonvergenz(konvergenz(), "2026-11-10", 1L)
        val zurueck = json.decodeFromString(WidgetKarte.serializer(), json.encodeToString(WidgetKarte.serializer(), k))
        assertEquals(k, zurueck)
        assertEquals("NVDA", zurueck.zeilen[0].ersatz)
        // eine gespeicherte Karte ohne das Feld bleibt lesbar
        val alt = json.decodeFromString(WidgetKarte.serializer(), """{"titel":"T","zeilen":[{"text":"A","wert":"1","einzeilig":true}]}""")
        assertFalse(alt.zeilen[0].ersatz.isNotEmpty())
    }
}
