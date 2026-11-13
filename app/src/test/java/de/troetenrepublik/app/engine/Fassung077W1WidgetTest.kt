package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.Lang
import de.troetenrepublik.app.data.WidgetKarte
import de.troetenrepublik.app.data.WidgetKarten
import de.troetenrepublik.app.data.WidgetKartenBau
import de.troetenrepublik.app.i18n.I18n
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.ui.Fmt
import de.troetenrepublik.app.ui.blNameAlle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

// Widgets, Nachbesserung W1: feste Bezeichnungen und Zeilen brechen im Widget nie um, die Standardgroesse ist gefuellt, die
// Schlagzeilen im News-Widget stehen nur vollstaendig, Signale und Wertpapierseite rechnen dieselben Zeilen.
// Mass: drei Zellen breit sind auf dem Geraet (1080 px, Zelle rund 110 dp) gemessen etwa 254 dp, zwei Zellen hoch 222 dp, drei
// 342 dp, vier 462 dp. Abzueglich 28 dp Rand bleiben 226 dp Breite fuer Text und Wert.
class Fassung077W1WidgetTest {

    @Before fun deutsch() { I18n.lang = Lang.DE }

    private val breite = 254
    private fun hoehe(zellen: Int) = 120 * zellen - 18
    private val innen = breite - 28f

    // Platz fuer eine feste Bezeichnung neben dem breitesten ueblichen Wert (zum Beispiel +12,3 Pkt.)
    private val wertMax = WidgetKarten.textBreite("+12,3 Pkt.", 13f, fett = true) + 8f
    private val obergrenze = innen - wertMax

    private fun sprachen(block: (Lang) -> Unit) { for (l in listOf(Lang.DE, Lang.EN)) { I18n.lang = l; block(l) } }

    private fun passt(label: String, platz: Float = obergrenze) = WidgetKarten.textBreite(label, 13f) <= platz

    // ------------------------------------------------------------ Bezeichnungen

    @Test fun ausblickNamenSindKurz() = sprachen { l ->
        val namen = mutableListOf<Pair<String, String>>()
        namen += "zins" to S.zeTitel
        for (k in listOf("fed", "fed_jahr", "ezb", "rezession", "inflation", "oel", "hormus", "iran")) namen += k to S.meThema(k)
        for (k in listOf("rohoel", "benzin", "destillat", "cushing", "spr", "gas_us", "gas_eu", "erstantraege", "fluggaeste", "duerre")) namen += k to S.fiName(k)
        for (k in listOf("lieferketten", "fracht", "bahn", "halbleiter")) namen += k to S.ersName(k)
        for (k in listOf("wci", "aar", "wsts")) namen += k to S.wqName(k)
        // Das Wort rechts ist hoechstens dagegen (englisch against)
        val wort = maxOf(
            WidgetKarten.textBreite(S.widgetGrundDagegen, 13f, true), WidgetKarten.textBreite(S.widgetGrundDafuer, 13f, true),
            (0..4).maxOf { WidgetKarten.textBreite(S.agWort(it), 13f, true) },
        ) + 8f
        for ((k, n) in namen) {
            val kurz = S.widgetGrundName(k, n)
            assertTrue("$l $k: '$kurz' ist zu lang", passt(kurz, innen - wort))
        }
        I18n.lang = Lang.DE
        assertEquals("Arbeitslosenanträge USA", S.widgetGrundName("erstantraege", S.fiName("erstantraege")))
    }

    @Test fun lageNamenSindKurz() = sprachen { l ->
        val schluessel = listOf("WACHSTUM", "INFLATION", "ZINS_HEUTE", "ZINS_SCHRITTE", "KRIEGE", "SPANNUNG", "ENGPASS_GESTOERT", "ENSO_ZUSTAND", "ENSO_INDEX", "KATASTROPHEN", "DURCHBRUCH_NEU", "DURCHBRUCH_THEMEN")
        for (k in schluessel) {
            val n = S.widgetLageName(k, "2026")
            // Der Wert (6 von 28, El Nino laeuft) steht rechts; passt er nicht neben den Namen, rutscht er in die kleine Zeile,
            // der Name aber steht auch dann in einer Zeile
            assertTrue("$l $k: '$n' ist breiter als die Zeile", passt(n, innen))
        }
        assertTrue(passt(S.widgetLageName("ENGPASS_GESTOERT", "")))
    }

    @Test fun depotCheckNamenSindKurz() = sprachen { l ->
        val wert = WidgetKarten.textBreite(S.widgetStufeKritisch, 13f, true) + 8f
        for (r in DepotCheck.Regel.entries) {
            val n = S.widgetCheckName(r.name)
            assertTrue("$l ${r.name}: '$n' ist zu lang", passt(n, innen - wert))
        }
    }

    @Test fun festeNamenDerKategorieWidgetsPassenNebenDenWert() = sprachen { l ->
        val alle = ArrayList<Pair<String, String>>()
        for (k in listOf("zinskurve", "sahm", "vix", "vix_struktur", "trend_sp500", "trend_dax", "stress", "hochzins")) alle += "Fruehwarnung" to S.fwName(k)
        for (k in listOf("sp500", "nasdaq", "world", "dax", "stoxx", "nikkei", "bitcoin")) alle += "Marktphase" to S.mpIndex(k)
        for (i in 0..11) alle += "Themen" to S.korbName(i)
        for (b in Branchentrends.BRANCHEN) alle += "Sektoren" to (if (l == Lang.DE) b.de else b.en)
        for (k in listOf("DOTCOM", "FINANCIAL", "EURO", "COVID", "RATES", "TARIFFS")) alle += "Crash-Test" to S.ptCrashName(k)
        for (k in Blasen.DAFUER + Blasen.DAGEGEN) alle += "Blasen" to S.widgetBlasenName(k, blNameAlle(k))
        for (k in listOf("STOCKS", "ETFS", "CRYPTO", "INDEX", "DERIVATIVES")) alle += "Streuung" to S.assetClass(k)
        // Beim Sektor und beim Korb steht rechts die Aenderung, etwa +12,3 % oder -10,5 %
        val wert = WidgetKarten.textBreite("−12,3 %", 13f, true) + 8f
        val zuLang = alle.filter { !passt(it.second, innen - wert) }
        assertTrue("$l zu lang fuer die Zeile mit Wert: $zuLang", zuLang.isEmpty())
    }

    @Test fun titelPassenInDieZeile() = sprachen { l ->
        for (a in WidgetKarten.ARTEN) {
            val t = WidgetKarten.titel(a)
            assertTrue("$l $a: Titel '$t' ist zu lang", WidgetKarten.textBreite(t, 11f, fett = true) <= innen)
        }
    }

    // ------------------------------------------------------------ Zuschnitt: keine Umbrueche, Standardgroesse gefuellt

    // Jede Zeile steht mit ihrem Text in einer Zeile (die Ueberschriften mit klemmen ausgenommen); ein Wert, der neben dem
    // Text nicht passt, steht in der kleinen Zeile darunter
    private fun keinUmbruch(name: String, z: WidgetKarten.Zuschnitt) {
        for (e in z.zeilen) {
            if (e.klemmen > 0) continue
            val text = e.text
            val wert = if (e.wert.isBlank()) 0f else WidgetKarten.textBreite(e.wert, 13f, fett = true) + 8f
            val platz = if (e.gestapelt) innen else innen - wert
            assertEquals("$name: $text bricht um", 1, WidgetKarten.textZeilen(text, platz, 13f))
        }
    }

    private fun fuellung(z: WidgetKarten.Zuschnitt): Double = (z.genutzt / z.verfuegbar).toDouble()

    private fun pruefe(name: String, karte: WidgetKarte, zellenHoch: Int, mindestens: Double = 2.0 / 3) {
        val z = WidgetKarten.zuschneiden(karte, breite, hoehe(zellenHoch))
        assertTrue("$name: ohne Titel", z.titel)
        assertTrue("$name: Zeilen oder Hauptaussage fehlen", z.zeilen.isNotEmpty() || z.haupt)
        keinUmbruch(name, z)
        val prozent = (fuellung(z) * 100).toInt()
        assertTrue("$name: nur zu $prozent % gefuellt (${z.zeilen.size} Zeilen)", fuellung(z) >= mindestens)
        // Mehr Hoehe zeigt nie weniger Zeilen
        var vorher = 0
        for (h in 1..4) {
            val n = WidgetKarten.zuschneiden(karte, breite, hoehe(h)).zeilen.size
            assertTrue("$name: bei ${hoehe(h)} dp weniger Zeilen als bei weniger Hoehe", n >= vorher)
            assertTrue("$name: mehr als acht Zeilen", n <= 8)
            vorher = n
        }
    }

    private fun karten(): List<Triple<String, WidgetKarte, Int>> {
        val l = ArrayList<Triple<String, WidgetKarte, Int>>()
        // Ausblick 3x2: Wochen, Monate und vier Gruende
        l += Triple("Ausblick", WidgetKartenBau.ausblick(3, 3, listOf(
            S.widgetGrundName("erstantraege", S.fiName("erstantraege")) to true, S.widgetGrundName("zins", S.zeTitel) to false,
            S.widgetGrundName("rezession", S.meThema("rezession")) to true, S.widgetGrundName("lieferketten", S.ersName("lieferketten")) to false,
        ), "Stand 10:00", 1L), 2)
        // Lage 3x3
        val ereignisse = listOf(
            WidgetKartenBau.Ereignis(S.lbThema("ZINSEN"), S.widgetLageName("ZINS_SCHRITTE", ""), S.lbSchritte("+3,1"), true),
            WidgetKartenBau.Ereignis(S.lbThema("ROHSTOFFE"), S.widgetLageName("ENGPASS_GESTOERT", ""), S.lbVonGesamt("6", "28"), true),
            WidgetKartenBau.Ereignis(S.lbThema("KLIMA"), S.widgetLageName("ENSO_ZUSTAND", ""), S.ensoZustand("EL_NINO_ADVISORY"), true),
            WidgetKartenBau.Ereignis(S.lbThema("KONJUNKTUR"), S.widgetLageName("WACHSTUM", "2026"), "3,1 %", false),
            WidgetKartenBau.Ereignis(S.lbThema("KONJUNKTUR"), S.widgetLageName("INFLATION", "2026"), "4,1 %", false),
            WidgetKartenBau.Ereignis(S.lbThema("GEOPOLITIK"), S.widgetLageName("SPANNUNG", ""), S.lbVonGesamt("3", "30"), false),
            WidgetKartenBau.Ereignis(S.lbThema("KLIMA"), S.widgetLageName("KATASTROPHEN", ""), S.lbRot("12", "3"), false),
            WidgetKartenBau.Ereignis(S.lbThema("TECHNIK"), S.widgetLageName("DURCHBRUCH_NEU", ""), "14", false),
        )
        l += Triple("Lage", WidgetKartenBau.lage(ereignisse, "Stand 10:00", 1L), 3)
        // Blasen-Gefahr 3x3
        val blasen = (Blasen.DAFUER + Blasen.DAGEGEN).take(5).mapIndexed { i, k -> WidgetKartenBau.Anzeichen(S.widgetBlasenName(k, blNameAlle(k)), if (i == 1) "−0,9 " + S.fwPunkte else "228 %", i % 2 == 0) }
        l += Triple("Blasen", WidgetKartenBau.blasen(1, 5, 12, 0.4, blasen, Blasen.GRENZE_ERHOEHT, Blasen.GRENZE_HOCH, "Stand 10:00", 1L), 3)
        // Fruehwarnung 3x3: alle acht Zeichen
        val fw = listOf(
            "zinskurve" to "+0,25 ${S.fwPunkte}", "sahm" to "+0,30 ${S.fwPunkte}", "vix" to "18,3", "vix_struktur" to "0,92", "trend_sp500" to "+4,1 %",
            "trend_dax" to "−1,2 %", "stress" to "−0,45", "hochzins" to "3,10 ${S.fwPunkte}",
        )
        l += Triple("Fruehwarnung", WidgetKartenBau.fruehwarnung(fw.mapIndexed { i, (k, w) -> WidgetKartenBau.Warnzeichen(S.fwName(k), w, i % 3) }, "Stand 10:00", 1L), 3)
        // Marktphase 3x3
        val mp = listOf("sp500", "nasdaq", "world", "dax", "stoxx", "nikkei", "bitcoin")
        l += Triple("Marktphase", WidgetKartenBau.marktphase(mp.mapIndexed { i, k -> WidgetKartenBau.Phase(S.mpIndex(k), if (i == 5) "BAER" else "BULLE", i == 2, -3.4 * i) }, "Stand 10:00", 1L), 3)
        // Durchbrueche 3x2
        l += Triple("Durchbrueche", WidgetKartenBau.durchbrueche(listOf(
            WidgetKartenBau.Durchbruch("Erste Zulassung einer Gentherapie gegen Sichelzellanämie in Europa", "Medizin", "vor 2 Std."),
            WidgetKartenBau.Durchbruch("Fusionsreaktor hält Plasma erstmals über zehn Minuten", "Energie", "vor 5 Std."),
            WidgetKartenBau.Durchbruch("Neuer Batterietyp erreicht 500 Wattstunden je Kilogramm", "Batterien", "vor 1 Tag"),
            WidgetKartenBau.Durchbruch("Quantenrechner löst Aufgabe mit korrigierten Fehlern", "Technik", "vor 2 Tagen"),
        ), emptyList(), "Stand 10:00", 1L), 2)
        // Termine 3x2
        val firmen = listOf("Johnson & Johnson", "Taiwan Semiconductor Manufacturing", "Coca-Cola", "Procter & Gamble", "Verizon", "Apple")
        l += Triple("Termine", WidgetKartenBau.termine("dividenden", (1..6).map {
            WidgetKartenBau.Termin(firmen[it - 1], "1$it.11.", Fmt.price(0.45 * it, "USD"), it % 2 == 0, it.toLong())
        }, "Stand 10:00", 1L), 2)
        // Depot-Check 3x3: acht Regeln, drei mit Befund
        val dc = DepotCheck.Regel.entries.take(8).mapIndexed { i, r -> WidgetKartenBau.Befund(S.widgetCheckName(r.name), if (i < 3) i else 0) }
        l += Triple("Depotcheck", WidgetKartenBau.depotcheck(dc, 1L), 3)
        // Performance 3x2
        l += Triple("Performance", WidgetKartenBau.performance(12.3, 8.1, 7.4, -9.8, (0..30).map { 100.0 + it }, 1L), 2)
        // Streuung 3x2
        val klassen = listOf(WidgetKartenBau.Anteil(S.assetClass("STOCKS"), 0.7), WidgetKartenBau.Anteil(S.assetClass("ETFS"), 0.2), WidgetKartenBau.Anteil(S.assetClass("CRYPTO"), 0.1))
        l += Triple("Streuung", WidgetKartenBau.streuung("NVIDIA", 0.38, klassen, 1L), 2)
        // Umschichten 3x3
        l += Triple("Umschichten", WidgetKartenBau.umschichten(listOf(
            WidgetKartenBau.Abweichung("Aktien USA", 62.0, 50.0), WidgetKartenBau.Abweichung("Aktien Europa", 8.0, 20.0), WidgetKartenBau.Abweichung("Anleihen", 5.0, 15.0),
            WidgetKartenBau.Abweichung("Rohstoffe", 15.0, 10.0), WidgetKartenBau.Abweichung("Krypto", 10.0, 5.0),
        ), 1L), 3)
        // Crash-Test 3x2
        val krisen = listOf("DOTCOM", "FINANCIAL", "EURO", "COVID", "RATES", "TARIFFS").mapIndexed { i, k -> WidgetKartenBau.Krise(S.ptCrashName(k), -0.45 + i * 0.06) }
        l += Triple("Crashtest", WidgetKartenBau.crashtest(krisen, 1L), 2)
        // Themen 3x3
        l += Triple("Themen", WidgetKartenBau.themen((0..11).map { WidgetKartenBau.Korb(S.korbName(it), 3.0 - it * 0.4, 12 - it, 12) }, "Stand 10:00", 1L), 3)
        // Indexbeweger 3x3
        val hoch = listOf("NVDA", "AAPL", "MSFT", "AMZN", "GOOGL").mapIndexed { i, k -> WidgetKartenBau.Treiber(k, k, 0.3 - i * 0.05) }
        val runter = listOf("META", "TSLA", "AVGO", "NFLX", "ORCL").mapIndexed { i, k -> WidgetKartenBau.Treiber(k, k, -0.2 + i * 0.03) }
        l += Triple("Indexbeweger", WidgetKartenBau.indexBeweger("S&P 500", "SP500", 0.8, hoch, runter, "Stand 10:00", 1L), 3)
        // Sektoren 3x3
        l += Triple("Sektoren", WidgetKartenBau.sektoren(Branchentrends.BRANCHEN.mapIndexed { i, b -> WidgetKartenBau.Sektor(if (I18n.lang == Lang.DE) b.de else b.en, 2.0 - i * 0.25) }, "Stand 10:00", 1L), 3)
        // Kongress-Flow 3x3
        l += Triple("Kongressflow", WidgetKartenBau.kongressFlow(listOf("HALBLEITER", "SOFTWARE", "BANKEN", "PHARMA", "EINZELHANDEL", "VERSORGER").mapIndexed { i, k ->
            WidgetKartenBau.FlussSektor(S.industryShort(k), 2_000_000.0 - i * 200_000, 900_000.0 + i * 50_000, 12 - i, 5 + i)
        }, "Stand 10:00", 1L), 3)
        // Stillhalter 3x3
        val verfall = 1_800_000_000_000L
        l += Triple("Stillhalter", WidgetKartenBau.stillhalter(listOf(
            WidgetKartenBau.Stillhalt("Super Micro Computer", "SMCI", false, 31.0, 1.2, verfall, 0.31),
            WidgetKartenBau.Stillhalt("NVIDIA", "NVDA", false, 120.0, 3.1, verfall, 0.27),
            WidgetKartenBau.Stillhalt("Palantir Technologies", "PLTR", true, 80.0, 2.0, verfall, 0.22),
            WidgetKartenBau.Stillhalt("Apple", "AAPL", false, 200.0, 2.5, verfall, 0.18),
        ), "Stand 10:00", 1L), 3)
        // Volatilitaet 3x2
        l += Triple("Volatilitaet", WidgetKartenBau.volatilitaet("AAPL", 0.31, 0.24, 1.29, 0.55, "Stand 10:00", 1L, "Apple"), 2)
        // Signale 3x2
        l += Triple("Signale", WidgetKartenBau.signale("AAPL", listOf(
            WidgetKartenBau.Signal(S.sgInsider, S.sgInsiderWert(2, 5), 5.0, 2.0), WidgetKartenBau.Signal(S.sgKongress, S.sgKaeufeVerkaeufe(3, 1), 1.0, 3.0),
            WidgetKartenBau.Signal(S.sgStufungen, S.sgHochAb(4, 1), 1.0, 4.0), WidgetKartenBau.Signal(S.sgOptionen, S.sgOptionenWert(3, 1, 2), 2.0, 1.0),
        ), "Stand 10:00", 1L, "Apple"), 2)
        return l
    }

    @Test fun standardgroesseIstGefuelltUndBrichtNichtUm() = sprachen { l ->
        for ((name, karte, zellen) in karten()) pruefe("$l $name", karte, zellen)
    }

    // Performance, Signale, Streuung und Crash-Test fuellen drei Zellen Hoehe nicht, zwei schon; die Blasen-Gefahr fuellt drei Zellen besser als vier
    @Test fun standardgroessenSindAnDenInhaltAngepasst() = sprachen { l ->
        val nach = karten().associate { it.first to it.second }.toMutableMap()
        // Signale haben oft nur drei Zeilen (Insider, Analysten, Optionen), so wie im Befund der Sichtpruefung
        nach["Signale"] = WidgetKartenBau.signale("AAPL", listOf(
            WidgetKartenBau.Signal(S.sgInsider, S.sgInsiderWert(2, 5), 5.0, 2.0),
            WidgetKartenBau.Signal(S.sgStufungen, S.sgHochAb(4, 1), 1.0, 4.0), WidgetKartenBau.Signal(S.sgOptionen, S.sgOptionenWert(3, 1, 2), 2.0, 1.0),
        ), "Stand 10:00", 1L, "Apple")
        for (n in listOf("Performance", "Signale", "Streuung", "Crashtest")) {
            val k = nach.getValue(n)
            val drei = fuellung(WidgetKarten.zuschneiden(k, breite, hoehe(3)))
            val zwei = fuellung(WidgetKarten.zuschneiden(k, breite, hoehe(2)))
            assertTrue("$l $n: bei drei Zellen schon zu ${(drei * 100).toInt()} % gefuellt", drei < 2.0 / 3)
            assertTrue("$l $n: bei zwei Zellen nur ${(zwei * 100).toInt()} %", zwei >= 2.0 / 3)
        }
        val blasen = nach.getValue("Blasen")
        val vier = fuellung(WidgetKarten.zuschneiden(blasen, breite, hoehe(4)))
        val drei = fuellung(WidgetKarten.zuschneiden(blasen, breite, hoehe(3)))
        assertTrue("$l Blasen bei drei Zellen nur ${(drei * 100).toInt()} % gefuellt", drei >= 0.8)
        assertTrue("$l Blasen: drei Zellen fuellen besser als vier", drei > vier)
    }

    // Der Tacho nutzt die Breite: bei drei Zellen ist er deutlich hoeher als die frueheren 52 dp
    @Test fun tachoNutztDieFlaeche() {
        val blasen = karten().first { it.first == "Blasen" }.second
        val z = WidgetKarten.zuschneiden(blasen, breite, hoehe(3))
        assertTrue(z.grafikHoehe >= 90)
    }

    // ------------------------------------------------------------ Hoehe bestimmt die Zahl der Zeilen

    @Test fun mehrHoeheZeigtMehrZeilenBisAcht() {
        val krisen = WidgetKartenBau.crashtest((0 until 9).map { WidgetKartenBau.Krise("Krise $it", -0.1 * (it + 1)) }, 1L)
        val n = (1..4).map { WidgetKarten.zuschneiden(krisen, breite, hoehe(it)).zeilen.size }
        assertEquals(n.sorted(), n)
        assertTrue(n.last() > n.first())
        assertTrue(n.all { it <= 8 })
        // Hoch genug fuer alle Zeilen: nie mehr als sieben (die erste Krise steht gross)
        assertEquals(7, WidgetKarten.zuschneiden(krisen, breite, 900).zeilen.size)
    }

    @Test fun zeileMitLangemNamenBrichtNichtUm() {
        val k = WidgetKartenBau.crashtest(listOf(
            WidgetKartenBau.Krise("Corona-Crash", -0.6), WidgetKartenBau.Krise("Ein sehr langer Name einer Krise der Weltwirtschaft", -0.5),
            WidgetKartenBau.Krise("Zollschock", -0.1),
        ), 1L)
        val z = WidgetKarten.zuschneiden(k, breite, hoehe(3))
        keinUmbruch("lang", z)
    }

    // ------------------------------------------------------------ News: nur ganze Meldungen

    @Test fun nachrichtenNurGanzeEintraege() {
        val titel = listOf(
            "EQS-Adhoc: Allianz SE: Allianz erzielt Rekordergebnis von 14,1 Milliarden", "Megaspeed und Chinas Kampf um den Zugang zu High-End-Chips von Nvidia",
            "Neue Chancen: DAX auf Rekordhoch: Wall Street erholt", "DAX vor Rekordhoch: Siemens Energy und Infineon treiben den Markt",
        )
        // 3x2 (222 dp): drei Meldungen mit Quellzeile, die vierte wuerde ohne sie dastehen
        assertEquals(3, WidgetKarten.nachrichtenAnzahl(titel, breite, hoehe(2)))
        // 3x3: alle vier
        assertEquals(4, WidgetKarten.nachrichtenAnzahl(titel, breite, hoehe(3)))
        // Nie weniger als eine, auch wenn das Widget sehr niedrig ist
        assertEquals(1, WidgetKarten.nachrichtenAnzahl(titel, breite, 40))
        assertEquals(0, WidgetKarten.nachrichtenAnzahl(emptyList(), breite, hoehe(2)))
        // Mit grosser Schrift passt weniger hinein
        assertTrue(WidgetKarten.nachrichtenAnzahl(titel, breite, hoehe(3), 1.5f) < 4)
    }

    // ------------------------------------------------------------ Hinweise statt Laedt

    @Test fun ohneDatenStehtEinKurzerHinweis() = sprachen { _ ->
        assertTrue(S.widgetFinderLeer.length < 40)
        assertFalse(S.widgetFinderLeer.contains("…"))
        assertTrue(S.widgetKeineDaten.isNotBlank() && S.widgetZuWenigPositionen.isNotBlank())
        // Eine Karte ohne Zeilen zeigt ihren Hinweis, keinen leeren Kasten
        val karte = WidgetKartenBau.aktienfinder("", emptyList(), "", 1L)
        val z = WidgetKarten.zuschneiden(karte, breite, hoehe(2))
        assertEquals(S.widgetFinderLeer, z.hinweis)
        assertTrue(z.zeilen.isEmpty())
    }

    // ------------------------------------------------------------ Signale: dieselbe Rechnung wie die Wertpapierseite

    @Test fun signaleZeilenSindFuerSeiteUndWidgetGleich() {
        val zeilen = de.troetenrepublik.app.widgetSignaleZeilen(null, null, null, Signale.Optionen(3, 1, 2, 1.0e6))
        assertEquals(1, zeilen.size)
        assertEquals(S.sgOptionenWert(3, 1, 2), zeilen[0].wert)
        // Anderer Stand der Seite, andere Zeile: das Widget uebernimmt sie
        val neu = de.troetenrepublik.app.widgetSignaleZeilen(null, null, null, Signale.Optionen(3, 0, 3, 1.0e6))
        assertEquals(S.sgOptionenWert(3, 0, 3), neu[0].wert)
    }
}
