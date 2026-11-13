package de.troetenrepublik.app.engine

import de.troetenrepublik.app.i18n.LangeLageTexte
import de.troetenrepublik.app.i18n.TonTexte
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

// Lange Lage: Geopolitical Risk Index, Economic Policy Uncertainty, Damodaran, Konflikte seit 1946 und der Ton der Berichte
// (GDELT). Parser an kurzen eigenen Auszuegen, dazu die mitgelieferten Dateien und die Texte in beiden Sprachen.
class Fassung079LangeLageTest {

    private fun asset(name: String) = listOf("src/main/assets/$name", "../app/src/main/assets/$name").map { File(it) }.first { it.exists() }

    private fun reihe(vararg w: Double?) = Monatsreihe(0, w.toList())

    // ---------------------------------------------------------------- Monatsreihe

    @Test fun monateZaehlenLueckenlos() {
        assertEquals(2026 * 12 + 9, Monatsreihe.monat(2026, 10))
        assertEquals(2026, Monatsreihe.jahr(Monatsreihe.monat(2026, 10)))
        assertEquals(10, Monatsreihe.monatImJahr(Monatsreihe.monat(2026, 10)))
        assertEquals("10/2026", Monatsreihe.marke(Monatsreihe.monat(2026, 10)))
        assertEquals(Monatsreihe.monat(1900, 1), Monatsreihe.lesenMonat("1900-01"))
        assertNull(Monatsreihe.lesenMonat("1900-13"))
        assertNull(Monatsreihe.lesenMonat("1900"))
    }

    @Test fun hoechsterStandSeit() {
        // Letzter Wert 4: zuletzt war es im Monat 0 hoeher (5)
        assertEquals(0, MonatsreiheRechnung.hoechsterSeit(reihe(5.0, 1.0, 3.0, 2.0, 4.0)))
        // Gleich hoch zaehlt als so hoch
        assertEquals(2, MonatsreiheRechnung.hoechsterSeit(reihe(1.0, 2.0, 4.0, 3.0, 4.0)))
        // Rekord: nie hoeher
        assertNull(MonatsreiheRechnung.hoechsterSeit(reihe(1.0, 2.0, 3.0, 4.0, 5.0)))
        // Luecken am Ende: der letzte Wert zaehlt
        assertEquals(1, MonatsreiheRechnung.hoechsterSeit(reihe(1.0, 9.0, 3.0, null, null)))
    }

    @Test fun rangGegenDieReihe() {
        val r = Monatsreihe(0, (1..20).map { it.toDouble() })
        assertEquals(70.0, MonatsreiheRechnung.perzentil(r, 15.0)!!, 1e-9)
        // Unter zwoelf Monaten gibt es keinen Rang
        assertNull(MonatsreiheRechnung.perzentil(Monatsreihe(0, listOf(1.0, 2.0, 3.0)), 2.0))
        assertEquals(MonatsreiheRechnung.Stufe.NIEDRIG, MonatsreiheRechnung.stufe(10.0))
        assertEquals(MonatsreiheRechnung.Stufe.NORMAL, MonatsreiheRechnung.stufe(50.0))
        assertEquals(MonatsreiheRechnung.Stufe.ERHOEHT, MonatsreiheRechnung.stufe(80.0))
        assertEquals(MonatsreiheRechnung.Stufe.HOCH, MonatsreiheRechnung.stufe(97.0))
    }

    @Test fun jahresmittelBrauchtSechsMonate() {
        // Zwei Jahre: das erste mit zwoelf Werten (Mittel 10), das zweite mit nur fuenf (zaehlt nicht)
        val r = Monatsreihe(Monatsreihe.monat(2020, 1), List(12) { 10.0 } + List(5) { 30.0 })
        val j = MonatsreiheRechnung.jahresmittel(r)
        assertEquals(listOf(2020), j.map { it.first })
        assertEquals(10.0, j.single().second, 1e-9)
        val sechs = Monatsreihe(Monatsreihe.monat(2020, 1), List(12) { 10.0 } + List(6) { 30.0 })
        assertEquals(30.0, MonatsreiheRechnung.jahresmittel(sechs).last().second, 1e-9)
    }

    @Test fun reiheLesenPruftForm() {
        val ok = kotlinx.serialization.json.Json.parseToJsonElement("""{"a":"2020-03","w":[1.5,null,2]}""") as kotlinx.serialization.json.JsonObject
        val r = Monatsreihe.lesen(ok)!!
        assertEquals(Monatsreihe.monat(2020, 3), r.von)
        assertEquals(Monatsreihe.monat(2020, 5), r.bis)
        assertNull(r.wert(Monatsreihe.monat(2020, 4)))
        val negativ = kotlinx.serialization.json.Json.parseToJsonElement("""{"a":"2020-03","w":[-1]}""") as kotlinx.serialization.json.JsonObject
        assertNull(Monatsreihe.lesen(negativ))
        assertNull(Monatsreihe.lesen(null))
    }

    // ---------------------------------------------------------------- Geopolitical Risk Index und EPU

    private fun reiheJson(von: String, n: Int, wert: (Int) -> Double) =
        """{"a":"$von","w":[${(0 until n).joinToString(",") { wert(it).toString() }}]}"""

    @Test fun gprLesenAuszug() {
        val json = """{"stand":"2026-11-14","schema":1,"quelle":{"zitat":"Caldara und Iacoviello"},
            "h":${reiheJson("1900-01", 130) { 100.0 + it }},"g":${reiheJson("1985-01", 130) { 100.0 }},
            "t":${reiheJson("1985-01", 130) { 90.0 }},"d":${reiheJson("1985-01", 130) { 110.0 }},
            "lm":{"USA":${reiheJson("1985-01", 130) { it.toDouble() }}},"lj":{"USA":{"a":1900,"w":[1.0,null,2.5]}}}"""
        val d = Gpr.lesen(json)!!
        assertEquals("Caldara und Iacoviello", d.zitat)
        assertEquals(130, d.h.werte.size)
        assertEquals(2, d.lj.getValue("USA").werte.size - 1)
        val lage = Gpr.lage(d.h)!!
        // Steigende Reihe: letzter Monat ist der hoechste
        assertNull(lage.hoechsterSeit)
        assertEquals(MonatsreiheRechnung.Stufe.HOCH, lage.stufe)
        assertEquals(1, Gpr.laenderLage(d, listOf("USA", "XXX")).size)
        // Zu kurze Reihe oder fehlendes Zitat lesen nicht
        assertNull(Gpr.lesen(json.replace("Caldara und Iacoviello", "")))
        assertNull(Gpr.lesen("{}"))
        assertNull(Gpr.lesen("kein json"))
    }

    @Test fun epuLesenAuszug() {
        val json = """{"stand":"2026-11-14","schema":1,
            "zitat":{"us":"Baker","gl":"Davis","emv":"Baker und Kost"},
            "us":${reiheJson("1980-01", 130) { 100.0 }},"gl":${reiheJson("1997-01", 70) { 120.0 }},"emv":${reiheJson("1985-01", 70) { 20.0 }}}"""
        val d = Epu.lesen(json)!!
        assertEquals(listOf("Baker", "Davis", "Baker und Kost"), d.zitat)
        assertEquals(Monatsreihe.monat(1985, 1), Epu.AB)
        // Rang ab 1985: eine gleichbleibende Reihe hat den Rang 0 (nichts liegt darunter)
        assertEquals(0.0, Epu.lage(d.us, Epu.AB)!!.perzentil, 1e-9)
        assertNull(Epu.lesen(json.replace("\"emv\":\"Baker und Kost\"", "\"x\":\"y\"")))
    }

    @Test fun mitgelieferteGprDatei() {
        val d = Gpr.lesen(asset(Gpr.DATEI).readText())!!
        assertEquals(Monatsreihe.monat(1900, 1), d.h.von)
        assertEquals(Monatsreihe.monat(1985, 1), d.g.von)
        assertEquals(d.g.werte.size, d.t.werte.size)
        assertEquals(d.g.werte.size, d.d.werte.size)
        assertTrue(d.lm.size >= 40)
        assertTrue(d.lj.keys.containsAll(listOf("USA", "RUS", "UKR", "CHN", "ISR")))
        // Mittel 1985 bis 2019 ist 100 (Normierung der Quelle)
        assertEquals(100.0, MonatsreiheRechnung.mittel(d.g, Monatsreihe.monat(1985, 1), Monatsreihe.monat(2019, 12))!!, 0.5)
        // Der Rang jedes Landes liegt zwischen 0 und 100, mindestens zwei Laender eines Gebiets kommen vor
        val lagen = Gpr.laenderLage(d, listOf("RUS", "UKR", "BLR"))
        assertEquals(2, lagen.size)
        assertTrue(lagen.all { it.second.perzentil in 0.0..100.0 })
        assertTrue(lagen[0].second.perzentil >= lagen[1].second.perzentil)
        // Alle Laender der Spannungsgebiete, die der Index kennt, haben eine Reihe
        assertNotNull(Spannungen.GEBIETE.flatMap { it.laender }.firstOrNull { it in d.lm })
        assertTrue(Datenbuendel.SCHEMA.containsKey(Gpr.DATEI))
        assertTrue(Gpr.DATEI in Alter.dateien())
        assertTrue(Alter.profilFuer(Gpr.DATEI) === Alter.MONATSREIHE)
    }

    @Test fun mitgelieferteEpuDatei() {
        val d = Epu.lesen(asset(Epu.DATEI).readText())!!
        assertEquals(Monatsreihe.monat(1900, 1), d.us.von)
        assertEquals(Monatsreihe.monat(1997, 1), d.gl.von)
        assertEquals(Monatsreihe.monat(1985, 1), d.emv.von)
        // US-Reihe hat im Mittel 1985 bis 2009 rund 100
        assertEquals(100.0, MonatsreiheRechnung.mittel(d.us, Monatsreihe.monat(1985, 1), Monatsreihe.monat(2009, 12))!!, 5.0)
        assertNotNull(Epu.lage(d.us, Epu.AB))
        assertNotNull(Epu.lage(d.gl))
        assertNotNull(Epu.lage(d.emv, Epu.AB))
        assertTrue(Datenbuendel.SCHEMA.containsKey(Epu.DATEI))
        assertTrue(Epu.DATEI in Alter.dateien())
    }

    // ---------------------------------------------------------------- Damodaran

    private val damodaranJson = """{"stand":"2026-11-14","schema":1,"jahre":[2023,2024,2025],"gesamt":"Total Market",
        "erp":{"a":2023,"w":[4.6,4.33,4.23],"r":[3.9,4.6,4.2]},
        "b":{"Semiconductor":{"w":[20.0,25.0,30.0],"n":66},"Total Market":{"w":[20.0,21.0,22.0],"n":5994}},
        "z":{"Semiconductors":"Semiconductor"}}"""

    @Test fun damodaranLesenAuszug() {
        val d = Damodaran.lesen(damodaranJson)!!
        assertEquals(listOf(2023, 2024, 2025), d.jahre)
        assertEquals(4.23, d.praemie.letzte()!!.second, 1e-9)
        assertEquals(2025, d.praemie.letzte()!!.first)
        assertEquals(30.0, Damodaran.branche(d, " Semiconductors ")!!.werte.last()!!, 0.0)
        assertNull(Damodaran.branche(d, "Unbekannt"))
        // Weniger als fuenf Jahreswerte: kein Mittel, kein Rang
        assertNull(Damodaran.mittelBisher(d.branchen.getValue("Semiconductor")))
        assertNull(Damodaran.rang(d.branchen.getValue("Semiconductor")))
        assertNull(Damodaran.praemieRang(d.praemie))
        // Zuordnung auf eine Branche, die es nicht gibt, ist ungueltig
        assertNull(Damodaran.lesen(damodaranJson.replace("\"Semiconductors\":\"Semiconductor\"", "\"Semiconductors\":\"Nirgends\"")))
        // Jahre muessen lueckenlos sein
        assertNull(Damodaran.lesen(damodaranJson.replace("[2023,2024,2025]", "[2023,2025,2026]")))
    }

    @Test fun kgvGegenBranche() {
        val teurer = Damodaran.vergleich(40.0, 30.0)!!
        assertEquals(Damodaran.Lage.TEURER, teurer.lage)
        assertEquals(33.333, teurer.abstand, 0.01)
        assertEquals(Damodaran.Lage.VERGLEICHBAR, Damodaran.vergleich(31.5, 30.0)!!.lage)
        assertEquals(Damodaran.Lage.GUENSTIGER, Damodaran.vergleich(20.0, 30.0)!!.lage)
        assertNull(Damodaran.vergleich(0.0, 30.0))
        assertNull(Damodaran.vergleich(-5.0, 30.0))
        assertNull(Damodaran.vergleich(20.0, 0.0))
        assertNull(Damodaran.vergleich(Double.NaN, 30.0))
    }

    @Test fun mitgelieferteDamodaranDatei() {
        val d = Damodaran.lesen(asset(Damodaran.DATEI).readText())!!
        assertEquals((2015..2025).toList(), d.jahre)
        assertEquals(1961, d.praemie.von)
        assertEquals(2025, d.praemie.bis)
        // Risikopraemie in Prozent, plausibler Bereich
        assertTrue(d.praemie.erp.filterNotNull().all { it in 1.0..10.0 })
        assertNotNull(Damodaran.praemieRang(d.praemie))
        assertNotNull(Damodaran.praemieMittel(d.praemie))
        // Haeufige Branchen der Wertpapierseite sind zugeordnet und haben einen aktuellen Wert
        val haeufig = listOf(
            "Semiconductors", "Software - Application", "Software - Infrastructure", "Banks - Regional", "Banks - Diversified", "Biotechnology",
            "Drug Manufacturers - General", "Auto Manufacturers", "Aerospace & Defense", "Oil & Gas Integrated", "Oil & Gas E&P", "Gold",
            "Utilities - Regulated Electric", "REIT - Residential", "Insurance - Life", "Airlines", "Restaurants", "Consumer Electronics",
            "Internet Retail", "Specialty Chemicals", "Steel", "Railroads", "Telecom Services", "Packaged Foods", "Medical Devices",
        )
        for (b in haeufig) assertNotNull(b, Damodaran.branche(d, b))
        // Der Gesamtmarkt steht in allen Jahren da
        assertTrue(d.gesamt.werte.all { it != null })
        assertTrue(Datenbuendel.SCHEMA.containsKey(Damodaran.DATEI))
        assertTrue(Damodaran.DATEI in Alter.dateien())
    }

    // ---------------------------------------------------------------- Konflikte seit 1946

    private fun konfliktJson(s: String, n: String) =
        """{"stand":"2026-11-14","schema":1,"v":"26.1","a":2000,"bis":2009,"l":{"XXX":{"s":"$s","n":[$n]}}}"""

    @Test fun konfliktKennzahlen() {
        val aktiv = KonfliktReihe.lesen(konfliktJson("0112200111", "0,1,1,2,1,0,0,1,1,1"))!!
        val k = KonfliktReihe.kennzahlen(aktiv, aktiv.laender.getValue("XXX"))
        assertEquals(10, k.jahre)
        assertEquals(7, k.jahreMitKonflikt)
        assertEquals(2, k.kriegsjahre)
        assertEquals(2007, k.aktivSeit)
        assertEquals(Triple(4, 2001, 2004), k.laengste)
        assertNull(k.letztesJahr)

        val ruhig = KonfliktReihe.lesen(konfliktJson("0110000000", "0,1,1,0,0,0,0,0,0,0"))!!
        val r = KonfliktReihe.kennzahlen(ruhig, ruhig.laender.getValue("XXX"))
        assertNull(r.aktivSeit)
        assertEquals(2002, r.letztesJahr)
        assertEquals(Triple(2, 2001, 2002), r.laengste)
        assertEquals(0, r.kriegsjahre)
    }

    @Test fun konfliktLesenPruftForm() {
        assertNull(KonfliktReihe.lesen(konfliktJson("011220011", "0,1,1,2,1,0,0,1,1")))
        assertNull(KonfliktReihe.lesen(konfliktJson("0112200113", "0,1,1,2,1,0,0,1,1,1")))
        assertNull(KonfliktReihe.lesen(konfliktJson("0112200111", "0,1,1,2,1,0,0,1,1")))
        assertNull(KonfliktReihe.lesen("{}"))
    }

    @Test fun mitgelieferteKonfliktDatei() {
        val d = KonfliktReihe.lesen(asset(KonfliktReihe.DATEI).readText())!!
        assertEquals(1946, d.von)
        assertEquals(2025, d.bis)
        assertEquals("26.1", d.version)
        // Das letzte Jahr stimmt mit konflikte.json ueberein: gleiche Laender, gleiche Stufe, gleiche Zahl
        val heute = Konflikte.lesen(asset(Konflikte.DATEI).readText())!!
        assertEquals(heute.jahr, d.bis)
        assertEquals(heute.nachLand.keys, d.laender.filterValues { it.stufen.last() > 0 }.keys)
        for ((iso, liste) in heute.nachLand) {
            val l = d.laender.getValue(iso)
            assertEquals(iso, liste.maxOf { it.stufe }, l.stufen.last())
            assertEquals(iso, liste.size, l.anzahl.last())
        }
        // Wo eine Stufe steht, gibt es mindestens einen Konflikt, und umgekehrt
        for (l in d.laender.values) for (i in l.stufen.indices) assertEquals(l.stufen[i] > 0, l.anzahl[i] > 0)
        // Stichprobe: Ukraine seit 2014 ohne Pause
        val ukr = d.laender.getValue("UKR")
        assertTrue(KonfliktReihe.kennzahlen(d, ukr).aktivSeit!! <= 2014)
        // Laender ohne Konflikt seit 1946 stehen nicht in der Datei
        assertFalse("CHE" in d.laender)
        assertTrue(Datenbuendel.SCHEMA.containsKey(KonfliktReihe.DATEI))
        assertTrue(KonfliktReihe.DATEI in Alter.dateien())
    }

    // ---------------------------------------------------------------- Ton der Berichte (GDELT timelinetone)

    private fun tag(s: String) = LocalDate.parse(s).toEpochDay().toInt()

    @Test fun tonAntwortLesen() {
        val antwort = """{"timeline":[{"series":"Average Tone","data":[
            {"date":"20261010T000000Z","value":-1.52},{"date":"20261009T000000Z","value":-2.1},{"date":"20261011T000000Z","value":99.0}]}]}"""
        val t = Ton.lesen(antwort)!!
        // Sortiert nach Tag, ein unmoeglicher Wert (ueber 30) entfaellt
        assertEquals(listOf(tag("2026-10-09"), tag("2026-10-10")), t.map { it.t })
        assertEquals(-2.1, t.first().ton, 1e-9)
        // Leere Antwort und die Bitte um Abstand ergeben null
        assertNull(Ton.lesen("{}"))
        assertNull(Ton.lesen("Please limit requests to one every 5 seconds."))
        assertNull(Ton.lesen("""{"timeline":[{"series":"x","data":[]}]}"""))
    }

    // 92 Tage bis 31.10.2026: die ersten 62 Tage mit Ton davor, die letzten 30 mit Ton neu; kleine regelmaessige Schwankung
    private fun tage(davor: Double, neu: Double): List<Ton.Tag> {
        val ende = tag("2026-10-31")
        return (0 until 92).map { i ->
            val t = ende - 91 + i
            Ton.Tag(t, (if (t > ende - Ton.NEU_TAGE) neu else davor) + 0.1 * (i % 3 - 1))
        }
    }

    @Test fun tonRichtungGegenDieMonateDavor() {
        assertEquals(Ton.Richtung.DEUTLICH_NEGATIVER, Ton.bewerten(tage(-1.0, -2.0))!!.richtung)
        assertEquals(Ton.Richtung.NEGATIVER, Ton.bewerten(tage(-1.0, -1.5))!!.richtung)
        assertEquals(Ton.Richtung.UNVERAENDERT, Ton.bewerten(tage(-1.0, -1.1))!!.richtung)
        assertEquals(Ton.Richtung.UNVERAENDERT, Ton.bewerten(tage(-1.0, -1.0))!!.richtung)
        assertEquals(Ton.Richtung.POSITIVER, Ton.bewerten(tage(-1.0, -0.5))!!.richtung)
        assertEquals(Ton.Richtung.DEUTLICH_POSITIVER, Ton.bewerten(tage(-1.0, 0.0))!!.richtung)
        val e = Ton.bewerten(tage(-1.0, -2.0))!!
        assertEquals(-2.0, e.neu, 0.05)
        assertEquals(-1.0, e.davor, 0.05)
        assertEquals(-1.0, e.abstand, 0.1)
        assertEquals(tag("2026-10-31"), e.bis)
    }

    @Test fun tonBraucktGenugTageWerte() {
        // Nur 20 Tage insgesamt: zu wenig davor
        assertNull(Ton.bewerten(tage(-1.0, -2.0).takeLast(20)))
        // 40 Tage: nur 10 davor
        assertNull(Ton.bewerten(tage(-1.0, -2.0).take(72).takeLast(40)))
        assertNull(Ton.bewerten(emptyList()))
    }

    @Test fun tonStarkesRauschenIstKeineAenderung() {
        // Mittel -1,0 gegen -1,4, aber die Tageswerte schwanken um mehrere Punkte: der Abstand hebt sich nicht ab
        val ende = tag("2026-10-31")
        val laut = (0 until 92).map { i ->
            val t = ende - 91 + i
            val grund = if (t > ende - Ton.NEU_TAGE) -1.4 else -1.0
            Ton.Tag(t, grund + if (i % 2 == 0) 4.0 else -4.0)
        }
        assertEquals(Ton.Richtung.UNVERAENDERT, Ton.bewerten(laut)!!.richtung)
    }

    @Test fun tonWochenmittel() {
        val w = Ton.wochen(tage(-1.0, -2.0))
        assertTrue(w.size in 12..14)
        assertTrue(w.zipWithNext().all { (a, b) -> b.first - a.first == 7 })
        assertEquals(tag("2026-10-31") - 3, w.last().first)
        assertEquals(-2.0, w.last().second, 0.2)
        assertEquals(-1.0, w.first().second, 0.2)
        assertTrue(Ton.wochen(emptyList()).isEmpty())
        // Wochen mit weniger als drei Tageswerten entfallen
        val duenn = listOf(Ton.Tag(tag("2026-10-30"), -1.0), Ton.Tag(tag("2026-10-31"), -1.0))
        assertTrue(Ton.wochen(duenn).isEmpty())
    }

    @Test fun tonSucheAusNamenDerSeite() {
        assertEquals("\"apple\" (shares OR stock OR earnings OR \"stock price\" OR CEO OR investors)", Ton.suche(listOf("Apple Inc.")))
        // Kurzer Name faellt auf den naechsten zurueck, gar kein langer Name ergibt keine Suche
        assertEquals("\"home depot\" (shares OR stock OR earnings OR \"stock price\" OR CEO OR investors)", Ton.suche(listOf("3M", "Home Depot")))
        assertNull(Ton.suche(listOf("3M", "HP")))
        assertNull(Ton.suche(emptyList()))
        val u = Ton.adresse(Ton.suche(listOf("Apple Inc."))!!)
        assertTrue(u.startsWith(Spannungen.ADRESSE + "?query="))
        assertTrue(u.contains("mode=timelinetone"))
        assertTrue(u.contains("timespan=3months"))
        assertTrue(u.contains("sourcelang%3Aeng"))
        // Keine Kennung des Nutzers in der Anfrage
        assertFalse(u.contains("@"))
    }

    @Test fun tonSpeichernUndLesen() {
        val t = tage(-1.0, -2.0)
        val w = Ton.wert(t, 123L)
        val zurueck = Ton.tage(w)
        assertEquals(t.size, zurueck.size)
        assertEquals(t.first().t, zurueck.first().t)
        assertEquals(t.last().ton, zurueck.last().ton, 0.0)
        val text = Laenderrisiken.schreiben(Ton.Wert.serializer(), w)
        val gelesen = Laenderrisiken.lesen(Ton.Wert.serializer(), text)!!
        assertEquals(123L, gelesen.abgerufen)
        assertEquals(t.size, gelesen.tage.size)
    }

    // ---------------------------------------------------------------- Texte

    private fun platzhalter(t: String) = Regex("\\{\\d\\}").findAll(t).map { it.value }.sorted().toList()

    @Test fun langeLageTexteInBeidenSprachen() {
        val de = LangeLageTexte.schluessel(true)
        assertEquals(de, LangeLageTexte.schluessel(false))
        for (k in de) {
            val d = LangeLageTexte.text(true, k, emptyList())
            val e = LangeLageTexte.text(false, k, emptyList())
            assertTrue(k, d.isNotBlank() && e.isNotBlank())
            assertEquals(k, platzhalter(d), platzhalter(e))
            for (t in listOf(d, e)) {
                assertFalse("Geviertstrich in $k", t.contains("—"))
                assertFalse("Gedankenstrich in $k", t.contains("–"))
                assertFalse("Auslassungspunkte in $k", t.contains("…"))
            }
        }
        for (s in MonatsreiheRechnung.Stufe.entries) assertTrue(LangeLageTexte.text(true, "u.stufe." + s.name).isNotBlank())
        for (l in Damodaran.Lage.entries) assertTrue(LangeLageTexte.text(true, "b.lage." + l.name).isNotBlank())
        assertEquals("Branche 22,3×", LangeLageTexte.text(true, "b.kern", listOf("22,3×")))
        assertEquals("Industry 22.3×", LangeLageTexte.text(false, "b.kern", listOf("22.3×")))
    }

    @Test fun tonTexteInBeidenSprachen() {
        val de = TonTexte.schluessel(true)
        assertEquals(de, TonTexte.schluessel(false))
        for (k in de) {
            val d = TonTexte.text(true, k, emptyList())
            val e = TonTexte.text(false, k, emptyList())
            assertTrue(k, d.isNotBlank() && e.isNotBlank())
            assertEquals(k, platzhalter(d), platzhalter(e))
            for (t in listOf(d, e)) {
                assertFalse("Geviertstrich in $k", t.contains("—"))
                assertFalse("Gedankenstrich in $k", t.contains("–"))
            }
        }
        // Jede Richtung hat eine Kernaussage
        for (r in Ton.Richtung.entries) assertTrue(TonTexte.text(true, "kern." + r.name).isNotBlank())
    }

    // ---------------------------------------------------------------- Einbindung

    @Test fun neueDateienSindImManifestUndImLeser() {
        val neu = listOf(Gpr.DATEI, Epu.DATEI, Damodaran.DATEI, KonfliktReihe.DATEI)
        val m = Datenbuendel.manifestLesen(asset("daten/manifest.json").readText())!!
        for (n in neu) {
            assertTrue(n, n in m.dateien)
            assertTrue(n, Datenbuendel.SCHEMA.containsKey(n))
            // Der Pruefer der Ferndatei versteht die eingebaute Datei
            assertTrue(n, de.troetenrepublik.app.market.Daten.PRUEFER.getValue(n)(asset(n).readText()))
        }
        // Das Quellenverzeichnis kennt GDELT
        assertNotNull(Quellenbuendel.ARTEN.firstOrNull { it.id == "gdelt" })
        // Quellen und Zitate stehen in NOTICE, die Funktionen in der README
        val notice = listOf("NOTICE", "../NOTICE").map { File(it) }.first { it.exists() }.readText(Charsets.UTF_8).replace(Regex("\\s+"), " ")
        for (n in neu) assertTrue(n, notice.contains(n))
        assertTrue(notice.contains("Caldara") && notice.contains("Baker, Bloom") && notice.contains("Damodaran") && notice.contains("GDELT"))
        val readme = listOf("README.md", "../README.md").map { File(it) }.first { it.exists() }.readText(Charsets.UTF_8)
        assertTrue(readme.contains("tone of news coverage") && readme.contains("implied equity risk premium") && readme.contains("Conflicts since 1946"))
    }
}
