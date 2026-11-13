package de.troetenrepublik.app.engine

import de.troetenrepublik.app.engine.RohstoffWaage.Eingaben
import de.troetenrepublik.app.engine.RohstoffWaage.Richtung
import de.troetenrepublik.app.engine.RohstoffWaage.Treiber
import de.troetenrepublik.app.i18n.RohstoffLageTexte
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

// Rohstoffe: Fuer und Wider aus Messgroessen, Signale aus dem Kurs, ETF fuer den Optionshandel, Terminkurve und die Datei
// Oel weltweit.
class Fassung078Y3RohstoffTest {

    private fun asset(name: String) = listOf("src/main/assets/$name", "../app/src/main/assets/$name").map { File(it) }.first { it.exists() }

    // Tagesreihe: tage Werte, linear von a nach b, endet am Tag ende
    private fun reihe(tage: Int, a: Double, b: Double, ende: Long = 20_000L): List<Pair<Long, Double>> =
        (0 until tage).map { i -> ende - (tage - 1 - i) to a + (b - a) * i / (tage - 1) }

    private val alleSchluessel = listOf("GOLD", "SILBER", "PLATIN", "PALLADIUM", "OEL", "GAS", "KUPFER", "WEIZEN", "MAIS", "KAFFEE", "KAKAO", "ZUCKER", "KORB")

    @Test fun jederRohstoffDerAppHatGruppeUndTreiber() {
        alleSchluessel.forEach { k ->
            assertNotNull(k, RohstoffWaage.gruppe(k))
            assertTrue(k, RohstoffWaage.treiber(k).size >= 2)
            // Dollar und Trend gelten ueberall
            assertTrue(k, Treiber.DOLLAR in RohstoffWaage.treiber(k) && Treiber.TREND in RohstoffWaage.treiber(k))
        }
        // Fonds auf Minenaktien sind kein Rohstoff mit eigenem Preis
        assertNull(RohstoffWaage.gruppe("MINEN"))
        assertTrue(RohstoffWaage.treiber("MINEN").isEmpty())
        // Jeder Schluessel aus Rohstoffe ist abgedeckt
        listOf("GC=F", "SI=F", "PL=F", "PA=F", "BZ=F", "CL=F", "NG=F", "HG=F", "ZW=F", "ZC=F", "KC=F", "CC=F", "SB=F", "XDBC.DE").forEach {
            assertTrue(it, Rohstoffe.schluessel(it) in alleSchluessel)
        }
        assertEquals(3, RohstoffWaage.gewicht("GOLD", Treiber.REALZINS))
        assertEquals(2, RohstoffWaage.gewicht("SILBER", Treiber.REALZINS))
        assertEquals(3, RohstoffWaage.gewicht("OEL", Treiber.LAGER))
        // Jeder Markt der Positionierung, den die Waage nennt, steht im Archiv der CFTC
        alleSchluessel.mapNotNull { RohstoffWaage.cotMarkt(it) }.forEach { m -> assertTrue(m, Positionierung.MAERKTE.any { it.first == m }) }
    }

    @Test fun goldRealzinsDollarSpekulantenTrend() {
        val e = Eingaben(
            // Realzins von 2,10 auf 1,80: faellt um 0,30 Punkte
            realzins = reihe(100, 2.10, 1.80).let { r -> r.dropLast(64) + reihe(64, 2.10, 1.80) },
            // Dollar-Index von 100 auf 103: plus 3 %
            dollar = reihe(64, 100.0, 103.0),
            positionRang = 95, positionAnteil = 41.0, positionStand = 1_700_000_000_000L,
            kurse = reihe(300, 1000.0, 1300.0),
        )
        val p = RohstoffWaage.auswerten("GOLD", e).associateBy { it.treiber }
        assertEquals(Richtung.STUETZT, p.getValue(Treiber.REALZINS).richtung)
        assertEquals(-0.30, p.getValue(Treiber.REALZINS).vergleich, 1e-9)
        assertEquals(1.80, p.getValue(Treiber.REALZINS).wert, 1e-9)
        assertEquals(20_000L * 86_400_000L, p.getValue(Treiber.REALZINS).stand)
        assertEquals(Richtung.BELASTET, p.getValue(Treiber.DOLLAR).richtung)
        assertEquals(3.0, p.getValue(Treiber.DOLLAR).vergleich, 1e-9)
        assertEquals(Richtung.BELASTET, p.getValue(Treiber.POSITIONIERUNG).richtung)
        assertEquals(Richtung.STUETZT, p.getValue(Treiber.TREND).richtung)
        // Ohne Messung kein Punkt: Inflationserwartung fehlt, Saison braucht zehn Jahre
        assertFalse(Treiber.INFLATION in p)
        assertFalse(Treiber.SAISON in p)
        // Waage: dafuer Realzins 3 + Trend 2, dagegen Dollar 2 + Spekulanten 2
        val w = RohstoffWaage.waage(p.values.toList())!!
        assertEquals(5.0, w.dafuer, 1e-9)
        assertEquals(4.0, w.dagegen, 1e-9)
        assertEquals(11, w.wert)
        assertEquals(BullBear.Stufe.AUSGEWOGEN, w.stufe)
    }

    @Test fun schwellenGenau() {
        fun realzins(delta: Double) = RohstoffWaage.auswerten("GOLD", Eingaben(realzins = reihe(64, 2.0, 2.0 + delta))).single { it.treiber == Treiber.REALZINS }.richtung
        assertEquals(Richtung.STUETZT, realzins(-0.15))
        assertEquals(Richtung.NEUTRAL, realzins(-0.14))
        assertEquals(Richtung.NEUTRAL, realzins(0.14))
        assertEquals(Richtung.BELASTET, realzins(0.15))
        // Zu kurze Reihe: kein Punkt
        assertTrue(RohstoffWaage.auswerten("GOLD", Eingaben(realzins = reihe(63, 2.0, 1.0))).isEmpty())
        fun rang(r: Int) = RohstoffWaage.auswerten("KUPFER", Eingaben(positionRang = r)).single().richtung
        assertEquals(Richtung.BELASTET, rang(90))
        assertEquals(Richtung.NEUTRAL, rang(89))
        assertEquals(Richtung.STUETZT, rang(10))
        fun lager(a: Double) = RohstoffWaage.auswerten("OEL", Eingaben(lagerAbweichung = a, lagerTag = 20_000L)).single().richtung
        assertEquals(Richtung.STUETZT, lager(-5.0))
        assertEquals(Richtung.NEUTRAL, lager(4.9))
        assertEquals(Richtung.BELASTET, lager(5.0))
        fun kurve(s: Double) = RohstoffWaage.auswerten("OEL", Eingaben(kurvenSpanne = s)).single().richtung
        assertEquals(Richtung.STUETZT, kurve(2.0))
        assertEquals(Richtung.NEUTRAL, kurve(-9.9))
        assertEquals(Richtung.BELASTET, kurve(-10.0))
        fun rezession(p: Double) = RohstoffWaage.auswerten("KUPFER", Eingaben(rezession = p)).single().richtung
        assertEquals(Richtung.BELASTET, rezession(30.0))
        assertEquals(Richtung.NEUTRAL, rezession(20.0))
        assertEquals(Richtung.STUETZT, rezession(10.0))
        fun opec(a: Double) = RohstoffWaage.auswerten("OEL", Eingaben(opecPlus = RohstoffWaage.Messung(36.0, a, 0L))).single().richtung
        assertEquals(Richtung.BELASTET, opec(2.0))
        assertEquals(Richtung.STUETZT, opec(-2.0))
        assertEquals(Richtung.NEUTRAL, opec(1.0))
        // Nur neutrale Punkte: keine Waage
        assertNull(RohstoffWaage.waage(RohstoffWaage.auswerten("OEL", Eingaben(opecPlus = RohstoffWaage.Messung(36.0, 1.0, 0L)))))
    }

    @Test fun ensoWirktJeRohstoffVerschieden() {
        // El Nino: Kaffee eher hoeher, Erdgas eher tiefer, Mais ohne bekannte Wirkung
        assertEquals(Richtung.STUETZT, RohstoffWaage.auswerten("KAFFEE", Eingaben(ensoPhase = 1, ensoIndex = 1.2)).single().richtung)
        assertEquals(Richtung.BELASTET, RohstoffWaage.auswerten("GAS", Eingaben(ensoPhase = 1, ensoIndex = 1.2)).single().richtung)
        assertTrue(RohstoffWaage.auswerten("MAIS", Eingaben(ensoPhase = 1, ensoIndex = 1.2)).isEmpty())
        // La Nina: Mais eher hoeher; neutral: kein Punkt
        assertEquals(Richtung.STUETZT, RohstoffWaage.auswerten("MAIS", Eingaben(ensoPhase = -1, ensoIndex = -0.9)).single().richtung)
        assertTrue(RohstoffWaage.auswerten("MAIS", Eingaben(ensoPhase = 0)).isEmpty())
        // Gold kennt den Treiber nicht
        assertTrue(RohstoffWaage.auswerten("GOLD", Eingaben(ensoPhase = 1)).isEmpty())
    }

    @Test fun saisonAusDerEigenenGeschichte() {
        // Zwoelf Jahre Monatsenden: jeder November plus 2 %, jeder andere Monat minus 0,1 %
        val tage = ArrayList<Pair<Long, Double>>()
        var kurs = 100.0
        var d = LocalDate.of(2013, 12, 31)
        tage += d.toEpochDay() to kurs
        while (d.isBefore(LocalDate.of(2026, 1, 31))) {
            d = d.plusDays(1).withDayOfMonth(1).plusMonths(1).minusDays(1)
            kurs *= if (d.monthValue == 11) 1.02 else 0.999
            // Zwei Kurse je Monat, damit die Reihe lang genug ist
            tage += d.minusDays(10).toEpochDay() to kurs
            tage += d.toEpochDay() to kurs
        }
        val nov = LocalDate.of(2026, 11, 12).toEpochDay()
        val w = RohstoffWaage.saison(tage, nov)!!
        assertEquals(12, w.anzahl)
        assertEquals(12, w.positiv)
        assertEquals(2.0, w.mittel, 0.01)
        val p = RohstoffWaage.auswerten("WEIZEN", Eingaben(kurse = tage, heute = nov)).single { it.treiber == Treiber.SAISON }
        assertEquals(Richtung.STUETZT, p.richtung)
        val mai = RohstoffWaage.auswerten("WEIZEN", Eingaben(kurse = tage, heute = LocalDate.of(2026, 5, 12).toEpochDay())).single { it.treiber == Treiber.SAISON }
        assertEquals(Richtung.BELASTET, mai.richtung)
        // Weniger als zehn Jahre: kein Punkt
        assertNull(RohstoffWaage.saison(tage.takeLast(150), nov))
    }

    @Test fun signaleAusDemKurs() {
        // Stetig steigend: am Jahreshoch, Durchbruch nach oben, ueber der 200-Tage-Linie
        val s = RohstoffWaage.kurssignale(reihe(300, 100.0, 160.0))!!
        assertEquals(1, s.durchbruch)
        assertEquals(0.0, s.hoch, 1e-9)
        assertTrue(s.tief > 0 && s.trend!! > 0)
        assertTrue(RohstoffWaage.amHoch(s) && !RohstoffWaage.amTief(s))
        assertNotNull(s.vola)
        assertNotNull(s.volaJahr)
        // Fallend: Durchbruch nach unten, am Tief
        val f = RohstoffWaage.kurssignale(reihe(300, 160.0, 100.0))!!
        assertEquals(-1, f.durchbruch)
        assertTrue(RohstoffWaage.amTief(f) && f.trend!! < 0)
        // Seitwaerts mit Ruecksetzer am Ende: kein Durchbruch
        val seit = reihe(60, 100.0, 100.0).mapIndexed { i, (t, v) -> t to v + (i % 5) }
        assertEquals(0, RohstoffWaage.kurssignale(seit)!!.durchbruch)
        // Kurze Reihe: Trend fehlt, zu kurz fuer alles: null
        assertNull(RohstoffWaage.kurssignale(reihe(50, 1.0, 2.0))!!.trend)
        assertNull(RohstoffWaage.kurssignale(reihe(10, 1.0, 2.0)))
        // Schwankung zuletzt viel hoeher als im Jahr
        val ruhig = reihe(280, 100.0, 100.5)
        val wild = (0 until 21).map { i -> 20_001L + i to if (i % 2 == 0) 100.0 else 110.0 }
        assertEquals(1, RohstoffWaage.volaLage(RohstoffWaage.kurssignale(ruhig + wild)!!))
    }

    @Test fun etfUndTerminkontraktJeRohstoff() {
        assertEquals("GLD", RohstoffHandel.etf("GC=F")!!.kuerzel)
        assertEquals(RohstoffHandel.Art.PHYSISCH, RohstoffHandel.etf("GC=F")!!.art)
        assertEquals("GLD", RohstoffHandel.etf("4GLD.DE")!!.kuerzel)
        assertEquals("SLV", RohstoffHandel.etf("SI=F")!!.kuerzel)
        assertEquals("USO", RohstoffHandel.etf("CL=F")!!.kuerzel)
        assertEquals("BNO", RohstoffHandel.etf("BZ=F")!!.kuerzel)
        assertEquals("UNG", RohstoffHandel.etf("NG=F")!!.kuerzel)
        assertEquals("CPER", RohstoffHandel.etf("HG=F")!!.kuerzel)
        assertEquals(RohstoffHandel.Art.TERMIN, RohstoffHandel.etf("CL=F")!!.art)
        // Ohne ETF mit Optionen: kein Abschnitt
        assertNull(RohstoffHandel.etf("KC=F"))
        assertNull(RohstoffHandel.etf("CC=F"))
        assertNull(RohstoffHandel.etf("IS0E.DE"))
        assertNull(RohstoffHandel.etf("AAPL"))
        // Jedes ETF-Kuerzel ist eines, das die Optionsquelle direkt nimmt (Buchstaben ohne Boersenzusatz)
        listOf("GC=F", "SI=F", "PL=F", "PA=F", "CL=F", "BZ=F", "NG=F", "HG=F", "ZW=F", "ZC=F", "SB=F", "XDBC.DE").forEach {
            assertTrue(it, Regex("^[A-Z]{1,5}$").matches(RohstoffHandel.etf(it)!!.kuerzel))
        }
        // Terminkontrakte nur fuer Kurse, die selbst Terminkontrakte sind
        assertEquals("GCZ26.CMX", RohstoffHandel.termin("GC=F")!!.let { Eingepreist.kontrakte(it.wurzel, it.boerse, 2026, 12, 1).single().symbol })
        assertEquals("KCH27.NYB", RohstoffHandel.termin("KC=F")!!.let { Eingepreist.kontrakte(it.wurzel, it.boerse, 2027, 3, 1).single().symbol })
        assertNull(RohstoffHandel.termin("4GLD.DE"))
        listOf("GC=F", "SI=F", "PL=F", "PA=F", "CL=F", "BZ=F", "NG=F", "HG=F", "ZW=F", "ZC=F", "KC=F", "CC=F", "SB=F").forEach { assertNotNull(it, RohstoffHandel.termin(it)) }
        assertEquals("CL", RohstoffHandel.terminFuer("OEL")!!.wurzel)
        assertNull(RohstoffHandel.terminFuer("GOLD"))
    }

    @Test fun terminkurveMitAbstandZumKurs() {
        fun k(j: Int, m: Int) = Eingepreist.Kontrakt(j, m, "X")
        // Contango: spaetere Lieferung teurer
        val kurve = listOf(k(2026, 12) to 4000.0, k(2027, 2) to 4040.0, k(2027, 6) to 4120.0)
        val z = RohstoffHandel.zeilen(kurve, 4000.0)
        assertEquals(3, z.size)
        assertEquals(0.0, z[0].abstand, 1e-9)
        assertNull(z[0].aufsJahr)
        assertEquals(1.0, z[1].abstand, 1e-9)
        assertEquals(3.0, z[2].abstand, 1e-9)
        // Sechs Monate, 3 %: rund 5,9 % aufs Jahr (logarithmisch), positiv fuer Contango
        assertEquals(Math.log(1.03) * 200, z[2].aufsJahr!!, 1e-9)
        // Fuer die Waage zaehlt Backwardation positiv
        assertTrue(RohstoffHandel.spanne(kurve)!! < 0)
        assertTrue(RohstoffHandel.spanne(listOf(k(2026, 12) to 70.0, k(2027, 3) to 68.0))!! > 0)
        assertNull(RohstoffHandel.spanne(kurve.take(1)))
        assertTrue(RohstoffHandel.zeilen(kurve, 0.0).isEmpty())
    }

    @Test fun oelWeltDateiLesenUndRechnen() {
        val d = OelWelt.lesen(asset(OelWelt.DATEI).readText(Charsets.UTF_8))!!
        assertEquals(d.istMonat - d.abMonat + 1, d.monate)
        assertTrue(d.monate >= 36)
        assertEquals(7, d.foerderung.gruppen.size)
        // Die Regionen ergeben in jedem Monat die Welt
        for (i in 0 until d.monate) {
            val summe = d.foerderung.gruppen.sumOf { it.w[i]!! }
            assertEquals("Monat $i", d.foerderung.welt[i]!!, summe, 0.02)
        }
        // Jede Region traegt mindestens ihre aufgefuehrten Laender
        d.foerderung.gruppen.forEach { g -> assertTrue(g.id, OelWelt.rest(g, d.monate - 1)!! >= 0.0) }
        val laender = d.foerderung.gruppen.flatMap { it.laender }
        assertTrue(laender.size >= 35)
        assertEquals(laender.size, laender.map { it.a2 }.distinct().size)
        listOf("US", "SA", "RU", "CN", "BR", "NO", "NG", "IR").forEach { a2 -> assertTrue(a2, laender.any { it.a2 == a2 }) }
        laender.forEach { assertTrue(it.a2, it.de.isNotBlank() && it.en.isNotBlank() && it.a2.length == 2) }
        // Welt in plausibler Groesse (Millionen Barrel am Tag), USA groesster Foerderer der Datei
        val welt = OelWelt.wert(d.foerderung.welt)!!
        assertTrue(welt.jetzt in 90.0..120.0)
        assertNotNull(welt.aenderung)
        assertEquals("US", laender.maxByOrNull { OelWelt.wert(it.w)!!.jetzt }!!.a2)
        // OPEC+: Rohoel je Land, die Summe der Laender ergibt die Summe der Datei
        val plus = d.rohoel.laender.filter { it.opecplus }
        assertTrue(plus.size >= 15)
        assertEquals(d.rohoel.opecplus.last()!!, plus.sumOf { it.w.last()!! }, 0.05)
        assertTrue(d.rohoel.laender.any { it.a2 == "IR" && !it.opecplus && it.opec })
        // Verbrauch und Lager
        assertTrue(OelWelt.wert(d.verbrauch.welt)!!.jetzt in 90.0..120.0)
        assertTrue(OelWelt.wert(d.lager.oecd)!!.jetzt in 2_000.0..3_500.0)
        // Prognose: ein volles Kalenderjahr
        assertTrue(OelWelt.jahresmittel(d.prognose!!.foerderung, d.istMonat + 1).isNotEmpty())
        assertEquals(1, Datenbuendel.SCHEMA[OelWelt.DATEI])
        assertTrue(OelWelt.DATEI in Alter.dateien())
        assertEquals(Alter.MONATLICH, Alter.profilFuer(OelWelt.DATEI))
    }

    @Test fun oelWeltKleineRechnungen() {
        val w = OelWelt.wert(listOf(10.0, 11.0, 12.0, 13.0, 14.0, 15.0, 16.0, 17.0, 18.0, 19.0, 20.0, 21.0, 11.0, null))!!
        assertEquals(11.0, w.jetzt, 1e-9)
        assertEquals(12, w.platz)
        assertEquals(10.0, w.aenderung!!, 1e-9)
        assertNull(OelWelt.wert(listOf(null, null)))
        assertNull(OelWelt.wert(listOf(1.0, 2.0))!!.aenderung)
        assertEquals(25.0, OelWelt.anteil(2.5, 10.0)!!, 1e-9)
        assertNull(OelWelt.anteil(1.0, 0.0))
        assertEquals(OelWelt.monat("2026-10"), 2026 * 12 + 9)
        assertNull(OelWelt.monat("2026-13"))
        assertEquals(LocalDate.of(2026, 10, 1).toEpochDay() * 86_400_000L, OelWelt.ms(2026 * 12 + 9))
        // Jahresmittel nur aus vollen Jahren: Reihe ab November 2026, 14 Monate
        val j = OelWelt.jahresmittel(listOf(1.0, 1.0) + List(12) { 3.0 }, 2026 * 12 + 10)
        assertEquals(listOf(2027 to 3.0), j)
        // Vorgaben der OPEC+: Abweichung in Tausend Barrel am Tag
        val text = """{"stand":"2026-11-05","ab":"2025-10","ist":"2026-10",
            "foerderung":{"welt":[1,1,1,1,1,1,1,1,1,1,1,1,1],"gruppen":[{"id":"NAHOST","w":[1,1,1,1,1,1,1,1,1,1,1,1,1]}]},
            "rohoel":{"welt":[1,1,1,1,1,1,1,1,1,1,1,1,1],"opecplus":[1,1,1,1,1,1,1,1,1,1,1,1,1],"laender":[
              {"a2":"SA","de":"Saudi-Arabien","en":"Saudi Arabia","opec":true,"opecplus":true,"w":[9,9,9,9,9,9,9,9,9,9,9,9,10.57]},
              {"a2":"IR","de":"Iran","en":"Iran","opec":true,"w":[3,3,3,3,3,3,3,3,3,3,3,3,3]}]},
            "verbrauch":{"welt":[1,1,1,1,1,1,1,1,1,1,1,1,1],"gruppen":[]},
            "lager":{"oecd":[1,1,1,1,1,1,1,1,1,1,1,1,1]},
            "quoten":{"monat":"2026-11","laender":{"SA":10478}}}"""
        val d = OelWelt.lesen(text)!!
        val e = OelWelt.erfuellung(d).single()
        assertEquals("SA", e.land.a2)
        assertEquals(92.0, e.abweichung, 1e-6)
        // Kaputte oder unpassende Dateien
        assertNull(OelWelt.lesen("kaputt"))
        assertNull(OelWelt.lesen(text.replace("\"ist\":\"2026-10\"", "\"ist\":\"2026-09\"")))
        // Boersenlager gegen den Vormonat: Vergleichstag 25 bis 45 Tage zurueck
        assertEquals(-20.0, OelWelt.gegenVormonat(listOf(100L to 500.0, 130L to 400.0))!!, 1e-9)
        assertNull(OelWelt.gegenVormonat(listOf(100L to 500.0, 110L to 400.0)))
        assertNull(OelWelt.gegenVormonat(emptyList()))
    }

    @Test fun texteInBeidenSprachenVollstaendig() {
        assertEquals(RohstoffLageTexte.schluessel(true), RohstoffLageTexte.schluessel(false))
        val k = RohstoffLageTexte.schluessel(true)
        Treiber.entries.forEach { t ->
            Richtung.entries.forEach { r ->
                // ENSO hat keine neutrale Aussage: ohne Wirkung gibt es keinen Punkt
                if (!(t == Treiber.ENSO && r == Richtung.NEUTRAL)) assertTrue("p.${t.name}.${r.ordinal}", "p.${t.name}.${r.ordinal}" in k)
            }
            assertTrue("m.${t.name}", "m.${t.name}" in k)
            assertTrue("w.${t.name}", "w.${t.name}" in k)
        }
        listOf("NORDAMERIKA", "SUEDAMERIKA", "EUROPA", "EURASIEN", "NAHOST", "AFRIKA", "ASIEN").forEach { assertTrue(it, "ow.r.$it" in k) }
        // Name fuer jeden Rohstoff mit ETF
        listOf("GC=F", "SI=F", "PL=F", "PA=F", "CL=F", "BZ=F", "NG=F", "HG=F", "ZW=F", "ZC=F", "SB=F", "XDBC.DE").forEach { s ->
            assertTrue(s, "n." + Rohstoffe.schluessel(s) in k)
        }
        listOf("PHYSISCH", "TERMIN").forEach { assertTrue(it, "o.auf.$it" in k) }
        // Platzhalter werden ersetzt, keine Gedankenstriche in den Texten
        assertEquals("3 dafür", RohstoffLageTexte.text(true, "k.dafuer", listOf("3")))
        assertEquals("Index 99,1 · −2,3 % in 3 Monaten", RohstoffLageTexte.text(true, "m.DOLLAR", listOf("99,1", "−2,3 %")))
        assertEquals("", RohstoffLageTexte.text(true, "gibt.es.nicht", emptyList()))
        listOf(true, false).forEach { de -> k.forEach { key -> assertFalse(key, RohstoffLageTexte.text(de, key, emptyList()).contains(" – ") || RohstoffLageTexte.text(de, key, emptyList()).contains("—")) } }
    }
}
