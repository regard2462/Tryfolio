package de.troetenrepublik.app.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

// Paket X4: Preisniveau und Kaufkraftparitaet aus den IWF-Reihen, Staerke der Waehrung und realer effektiver Wechselkurs
// (Weltbank), Big-Mac-Index (The Economist) und Ranglisten der Laenderindizes (UNDP, Weltbank). Rechenlogik mit festen
// Beispielen, die Buendeldateien gegen bekannte Eigenschaften der Quellen.
class Fassung078X4KaufkraftTest {
    private fun asset(name: String): File =
        listOf("src/main/assets/$name", "../app/src/main/assets/$name").map { File(it) }.first { it.exists() }

    private val waehrung by lazy { Waehrungsstaerke.lesen(asset(Waehrungsstaerke.DATEI).readText(Charsets.UTF_8))!! }
    private val bigmac by lazy { BigMac.lesen(asset(BigMac.DATEI).readText(Charsets.UTF_8))!! }
    private val rang by lazy { Ranglisten.lesen(asset(Ranglisten.DATEI).readText(Charsets.UTF_8))!! }

    // BIP je Kopf in Dollar und kaufkraftbereinigt: USA 80.000/80.000, Deutschland 55.000/70.000, Tuerkei 15.000/45.000
    private val imf = Imf.Datensatz(
        werte = mapOf(
            Imf.BIP_KOPF to mapOf("USA" to mapOf(2024 to 76000.0, 2025 to 80000.0), "DEU" to mapOf(2024 to 52000.0, 2025 to 55000.0), "TUR" to mapOf(2024 to 13000.0, 2025 to 15000.0)),
            Imf.BIP_KOPF_KKP to mapOf("USA" to mapOf(2024 to 76000.0, 2025 to 80000.0), "DEU" to mapOf(2024 to 65000.0, 2025 to 70000.0), "TUR" to mapOf(2024 to 40000.0, 2025 to 45000.0)),
            Imf.INFLATION to mapOf("DEU" to mapOf(2024 to 2.0, 2025 to 2.0), "TUR" to mapOf(2024 to 60.0, 2025 to 35.0)),
        ),
    )

    // ------------------------------------------------------------ Preisniveau

    @Test fun preisniveauGegenUsaUndGegenBasis() {
        assertEquals(1.0, Preisniveau.niveauUsa(imf, "USA", 2025)!!, 1e-12)
        assertEquals(55000.0 / 70000.0, Preisniveau.niveauUsa(imf, "DEU", 2025)!!, 1e-12)
        val tur = Preisniveau.preisniveau(imf, "TUR", "DEU", 2025)!!
        assertEquals((15000.0 / 45000.0) / (55000.0 / 70000.0) * 100.0, tur, 1e-9)
        assertEquals(42.4, tur, 0.05)
        assertEquals(100.0, Preisniveau.preisniveau(imf, "DEU", "DEU", 2025)!!, 1e-9)
        assertNull(Preisniveau.preisniveau(imf, "FRA", "DEU", 2025))
        assertNull(Preisniveau.preisniveau(imf, "TUR", "DEU", 2030))
    }

    @Test fun bewertungGegenKaufkraftparitaet() {
        // Tuerkei: Preisniveau ein Drittel der USA, die Lira liegt 66,7 % unter der Kaufkraftparitaet zum Dollar
        val n = Preisniveau.preisniveau(imf, "TUR", Preisniveau.USA, 2025)!!
        assertEquals(-66.67, Preisniveau.bewertung(n), 0.01)
        assertEquals(0.0, Preisniveau.bewertung(Preisniveau.preisniveau(imf, "USA", Preisniveau.USA, 2025)!!), 1e-9)
    }

    @Test fun warenkorbUndReisekasse() {
        assertEquals(580.0, Preisniveau.kostetDort(1000.0, 58.0), 1e-9)
        assertEquals(172.41, Preisniveau.reichtWie(100.0, 58.0), 0.01)
        // Hin und zurueck ergibt wieder den Betrag
        assertEquals(1000.0, Preisniveau.reichtWie(Preisniveau.kostetDort(1000.0, 58.0), 58.0), 1e-9)
    }

    @Test fun einkommenInPreisenDerBasis() {
        val e = Preisniveau.einkommen(imf, "TUR", "DEU", 2025)!!
        assertEquals(15000.0, e.nominal, 1e-9)
        // Kaufkraftwert mal Preisniveau der Basis gegen die USA: 45.000 * 55/70
        assertEquals(45000.0 * 55000.0 / 70000.0, e.kaufkraft, 1e-6)
        val d = Preisniveau.einkommen(imf, "DEU", "DEU", 2025)!!
        assertEquals(d.nominal, d.kaufkraft, 1e-6)
    }

    @Test fun verlaufUndBasisland() {
        val v = Preisniveau.verlauf(imf, "TUR", "DEU", 2000, 2026)
        assertEquals(listOf(2024, 2025), v.map { it.first })
        assertEquals((13000.0 / 40000.0) / (52000.0 / 65000.0) * 100.0, v[0].second, 1e-9)
        assertEquals("TUR", Preisniveau.basis(imf, "TUR", 2025))
        assertEquals("DEU", Preisniveau.basis(imf, "FRA", 2025))
        assertEquals("DEU", Preisniveau.basis(imf, null, 2025))
    }

    @Test fun ersatzwerteAusVerschiedenenJahrenZaehlenNicht() {
        val jahr = java.time.LocalDate.now().year
        val d = Imf.Datensatz(
            werte = mapOf(Imf.WACHSTUM to mapOf("USA" to mapOf(jahr to 2.0))),
            ersatz = mapOf(
                Imf.BIP_KOPF to mapOf("CUB" to Imf.ErsatzWert(jahr - 1, 9000.0, "WB"), "PRK" to Imf.ErsatzWert(jahr - 1, 600.0, "UN")),
                Imf.BIP_KOPF_KKP to mapOf("CUB" to Imf.ErsatzWert(jahr - 1, 18000.0, "WB"), "PRK" to Imf.ErsatzWert(jahr - 3, 1700.0, "CIA")),
            ),
        )
        assertEquals(0.5, Preisniveau.niveauUsa(d, "CUB", jahr)!!, 1e-12)
        assertNull(Preisniveau.niveauUsa(d, "PRK", jahr))
    }

    // ------------------------------------------------------------ Staerke der Waehrung

    private val klein = Waehrungsstaerke.lesen(
        """{"stand":"2026-11-12","schema":1,"ab":2015,"bis":2025,"l":{
            "USA":{"f":[1,1,1,1,1,1,1,1,1,1,1]},
            "DEU":{"f":[0.9,0.9,0.89,0.85,0.89,0.88,0.85,0.95,0.92,0.92,0.9],"r":[95,96,97,98,97,98,99,96,98,99,104]},
            "TUR":{"f":[2.7,3.0,3.6,4.8,5.7,7.0,8.9,16.5,23.7,32.8,36]},
            "SAU":{"f":[3.75,3.75,3.75,3.75,3.75,3.75,3.75,3.75,3.75,3.75,3.75]},
            "ZWE":{"f":[null,null,null,null,null,51.3,88.6,375,3509,3266,26.6]},
            "FRA":{"f":[0.9,0.9,0.89,0.85,0.89,0.88,0.85,0.95,0.92,0.92,0.9],"r":[100,100,100,null,null,null,null,null,null,null,null]}
        }}""",
    )!!

    @Test fun nominaleVeraenderung() {
        // Lira in Euro: 2025 0,9/36, 2024 0,92/32,8
        val erwartet = ((0.9 / 36) / (0.92 / 32.8) - 1.0) * 100.0
        assertEquals(erwartet, Waehrungsstaerke.nominal(klein, "TUR", "DEU", 2025, 1)!!, 1e-9)
        assertEquals(((0.9 / 36) / (0.9 / 2.7) - 1.0) * 100.0, Waehrungsstaerke.nominal(klein, "TUR", "DEU", 2025, 10)!!, 1e-9)
        // Der Dollar in Euro steht, wenn der Euro zum Dollar steht
        assertEquals(0.0, Waehrungsstaerke.nominal(klein, "USA", "DEU", 2025, 10)!!, 1e-9)
        // Vor dem Beginn der Datei und fuer unbekannte Laender nichts
        assertNull(Waehrungsstaerke.nominal(klein, "TUR", "DEU", 2025, 11))
        assertNull(Waehrungsstaerke.nominal(klein, "XXX", "DEU", 2025, 1))
        assertEquals(2025, Waehrungsstaerke.letztesJahr(klein, "TUR", "DEU"))
    }

    @Test fun realNachInflationsunterschied() {
        // Kurs minus 50 %, Preise im Land verdoppelt, im Vergleichsland unveraendert: real unveraendert
        assertEquals(0.0, Waehrungsstaerke.real(-50.0, listOf(100.0), listOf(0.0)), 1e-9)
        // Kurs unveraendert, zwei Jahre 10 % gegen 2 %: real plus 16,3 %
        assertEquals((1.1 * 1.1) / (1.02 * 1.02) * 100.0 - 100.0, Waehrungsstaerke.real(0.0, listOf(10.0, 10.0), listOf(2.0, 2.0)), 1e-9)
        val z = Waehrungsstaerke.zeilen(klein, imf, "TUR", "DEU")
        assertEquals(listOf(1, 5, 10), z.map { it.jahre })
        val nominal = Waehrungsstaerke.nominal(klein, "TUR", "DEU", 2025, 1)!!
        assertEquals((1.0 + nominal / 100.0) * 1.35 / 1.02 * 100.0 - 100.0, z[0].real!!, 1e-9)
        // Fuer fuenf Jahre fehlen Inflationswerte: nominal ja, real nein
        assertNull(z[1].real)
        assertEquals(listOf(2.0, 2.0), Waehrungsstaerke.inflation(imf, "DEU", 2023, 2025))
        assertNull(Waehrungsstaerke.inflation(imf, "DEU", 2022, 2025))
    }

    @Test fun bruchDurchNeueWaehrungWirdNichtUeberbrueckt() {
        // 2025 steht eine neue Waehrung in der Reihe (3.266 zu 26,6): kein Wert ueber den Bruch, davor schon
        assertNull(Waehrungsstaerke.nominal(klein, "ZWE", "USA", 2025, 1))
        assertNull(Waehrungsstaerke.nominal(klein, "ZWE", "USA", 2025, 5))
        assertNotNull(Waehrungsstaerke.nominal(klein, "ZWE", "USA", 2024, 3))
        // Eine Abwertung, auch eine steile, ist kein Bruch
        assertNotNull(Waehrungsstaerke.nominal(klein, "TUR", "USA", 2025, 10))
    }

    @Test fun bindungAusDenKursen() {
        assertEquals("USA", Waehrungsstaerke.anker(klein, "SAU"))
        assertEquals("DEU", Waehrungsstaerke.anker(klein, "FRA"))
        assertNull(Waehrungsstaerke.anker(klein, "TUR"))
        assertNull(Waehrungsstaerke.anker(klein, "ZWE"))
        assertTrue(Waehrungsstaerke.fest(klein, "SAU", "USA", 2025))
        assertFalse(Waehrungsstaerke.fest(klein, "DEU", "USA", 2025))
    }

    @Test fun vergleichslandDerStaerke() {
        assertEquals("DEU", Waehrungsstaerke.gegen("TUR", "DEU", "TRY", "EUR"))
        // Gleiche Waehrung wie die Basis: gegen den Dollar; Dollarlaender und die USA gegen den Euro
        assertEquals("USA", Waehrungsstaerke.gegen("FRA", "DEU", "EUR", "EUR"))
        assertEquals("USA", Waehrungsstaerke.gegen("DEU", "DEU", "EUR", "EUR"))
        assertEquals("DEU", Waehrungsstaerke.gegen("ECU", "USA", "USD", "USD"))
        assertEquals("DEU", Waehrungsstaerke.gegen("USA", "USA", "USD", "USD"))
        assertEquals("CHE", Waehrungsstaerke.gegen("USA", "CHE", "USD", "CHF"))
    }

    @Test fun realerEffektiverKurs() {
        val r = Waehrungsstaerke.reer(klein, "DEU")!!
        assertEquals(2025, r.jahr)
        assertEquals(104.0, r.wert, 1e-9)
        val schnitt = listOf(96, 97, 98, 97, 98, 99, 96, 98, 99, 104).sum() / 10.0
        assertEquals(schnitt, r.schnitt, 1e-9)
        assertEquals((104.0 / schnitt - 1.0) * 100.0, r.abweichung, 1e-9)
        // Zu wenige Werte im Fenster
        assertNull(Waehrungsstaerke.reer(klein, "FRA"))
        assertNull(Waehrungsstaerke.reer(klein, "TUR"))
    }

    @Test fun waehrungLesenVerlangtPassendeLaengen() {
        assertNull(Waehrungsstaerke.lesen("""{"stand":"2026-11-12","ab":2024,"bis":2025,"l":{"DEU":{"f":[1,2,3]}}}"""))
        assertNull(Waehrungsstaerke.lesen("""{"stand":"heute","ab":2024,"bis":2025,"l":{"DEU":{"f":[1,2]}}}"""))
        assertNull(Waehrungsstaerke.lesen("""{"stand":"2026-11-12","ab":2024,"bis":2025,"l":{}}"""))
        assertNotNull(Waehrungsstaerke.lesen("""{"stand":"2026-11-12","ab":2024,"bis":2025,"l":{"DEU":{"f":[1,2]}}}"""))
    }

    // ------------------------------------------------------------ Big Mac

    @Test fun bigMacLesenUndEuroraum() {
        val d = BigMac.lesen(
            """{"stand":"2026-11-12","tage":["2026-01-01","2026-07-01"],"l":{
                "CHE":{"w":"CHF","t":1,"lp":7.3,"dp":9.042,"ur":[454,454],"er":277,"ua":485,"ea":150},
                "EUZ":{"w":"EUR","t":1,"lp":6.19,"dp":7.079,"ur":[100,138],"er":0,"ua":291,"ea":0},
                "VEN":{"w":"VES","t":0,"lp":10,"dp":2.0,"ur":[-600,null]}
            }}""",
        )!!
        val (che, euroraum) = BigMac.fuer(d, "CHE", "CHF")!!
        assertFalse(euroraum)
        assertEquals(27.7, che.rohEuro!!, 1e-9)
        assertEquals(15.0, che.bereinigtEuro!!, 1e-9)
        assertEquals("2026-07-01", che.tag)
        // Euro je Dollar am Stichtag aus dem Euroraum: 6,19 / 7,079
        assertEquals(9.042 * 6.19 / 7.079, BigMac.euroPreis(d, che)!!, 1e-9)
        // Deutschland steht in der Quelle als Euroraum, die Tuerkei hier gar nicht
        assertTrue(BigMac.fuer(d, "DEU", "EUR")!!.second)
        assertNull(BigMac.fuer(d, "TUR", "TRY"))
        // Anderer Stichtag als der Euroraum: kein Europreis
        assertNull(BigMac.euroPreis(d, d.laender.getValue("VEN")))
        assertEquals(listOf(2026.0 to -60.0), BigMac.verlauf(d, d.laender.getValue("VEN")))
        assertEquals(2026.5, BigMac.jahrBruch("2026-07-01"), 1e-9)
    }

    // ------------------------------------------------------------ Ranglisten

    @Test fun rangZaehltBessereWerte() {
        val d = Ranglisten.lesen(
            """{"stand":"2026-11-12","i":{
                "hdi":{"AAA":[2023,0.9],"BBB":[2023,0.9],"CCC":[2023,0.8],"DDD":[2023,0.95]},
                "gini":{"AAA":[2023,25.0],"BBB":[2020,40.0],"CCC":[2012,30.0]}
            },"hdiAb":2021,"hdi":{"AAA":[880,null,900]}}""",
        )!!
        // Gleiche Werte teilen den Platz
        assertEquals(2, Ranglisten.platz(d, Ranglisten.Index.HDI, "AAA")!!.rang)
        assertEquals(2, Ranglisten.platz(d, Ranglisten.Index.HDI, "BBB")!!.rang)
        assertEquals(4, Ranglisten.platz(d, Ranglisten.Index.HDI, "CCC")!!.rang)
        assertEquals(4, Ranglisten.platz(d, Ranglisten.Index.HDI, "CCC")!!.von)
        // Gini: der niedrigste Wert hat Platz 1; die Erhebung von 2012 ist aelter als zehn Jahre und zaehlt nicht
        assertEquals(1, Ranglisten.platz(d, Ranglisten.Index.GINI, "AAA")!!.rang)
        assertEquals(2, Ranglisten.platz(d, Ranglisten.Index.GINI, "BBB")!!.von)
        assertNull(Ranglisten.platz(d, Ranglisten.Index.GINI, "CCC"))
        assertNull(Ranglisten.wert(d, Ranglisten.Index.GINI, "CCC"))
        assertEquals(listOf(Ranglisten.Index.HDI, Ranglisten.Index.GINI), Ranglisten.plaetze(d, "AAA").map { it.index })
        assertEquals(listOf(2021 to 0.88, 2023 to 0.9), Ranglisten.hdiVerlauf(d, "AAA"))
        assertEquals(1f, Ranglisten.lage(Ranglisten.platz(d, Ranglisten.Index.HDI, "DDD")!!), 1e-6f)
        assertEquals(0f, Ranglisten.lage(Ranglisten.platz(d, Ranglisten.Index.HDI, "CCC")!!), 1e-6f)
    }

    // ------------------------------------------------------------ Buendeldateien

    @Test fun waehrungsdateiDecktDieWeltAb() {
        assertTrue(waehrung.laender.size >= 200)
        assertTrue(waehrung.bis >= 2025)
        assertEquals(1.0, Waehrungsstaerke.kurs(waehrung, "USA", 2020)!!, 1e-9)
        // Der Franken hat in zehn Jahren zum Euro gewonnen, die Lira verloren
        val bis = Waehrungsstaerke.letztesJahr(waehrung, "CHE", "DEU")!!
        assertTrue(Waehrungsstaerke.nominal(waehrung, "CHE", "DEU", bis, 10)!! > 5.0)
        assertTrue(Waehrungsstaerke.nominal(waehrung, "TUR", "DEU", bis, 10)!! < -80.0)
        // Bindungen: Riyal und Hongkong-Dollar am Dollar, Krone und CFA-Franc am Euro
        assertEquals("USA", Waehrungsstaerke.anker(waehrung, "SAU"))
        assertEquals("USA", Waehrungsstaerke.anker(waehrung, "HKG"))
        assertEquals("DEU", Waehrungsstaerke.anker(waehrung, "DNK"))
        assertEquals("DEU", Waehrungsstaerke.anker(waehrung, "SEN"))
        assertNull(Waehrungsstaerke.anker(waehrung, "CHE"))
        assertNull(Waehrungsstaerke.anker(waehrung, "JPN"))
    }

    @Test fun euroBeitrittOhneSprung() {
        // Kroatien fuehrt seit 2023 den Euro: Die Jahre davor stehen umgerechnet in Euro, der Kurs zum Euro bleibt nahe 1
        for (j in 2005..2025) {
            val w = Waehrungsstaerke.wert(waehrung, "HRV", "DEU", j)!!
            assertTrue("HRV $j: $w", w in 0.9..1.1)
        }
        assertEquals(1.0, Waehrungsstaerke.wert(waehrung, "HRV", "DEU", 2024)!!, 1e-3)
        assertEquals(1.0, Waehrungsstaerke.wert(waehrung, "LTU", "DEU", 2010)!!, 0.01)
        // Simbabwe: neue Waehrung 2024, kein Wert ueber den Bruch
        assertNull(Waehrungsstaerke.nominal(waehrung, "ZWE", "USA", 2025, 5))
    }

    @Test fun realerEffektiverKursInDerDatei() {
        val r = Waehrungsstaerke.reer(waehrung, "CHE")!!
        assertTrue(r.jahr >= 2025)
        assertTrue(r.wert in 50.0..200.0 && r.schnitt in 50.0..200.0)
        assertEquals(100.0, Waehrungsstaerke.reerVerlauf(waehrung, "DEU").first { it.first == 2010 }.second, 1e-6)
        assertTrue(waehrung.laender.values.count { l -> l.reer.any { it != null } } >= 80)
    }

    @Test fun bigMacDatei() {
        assertTrue(bigmac.laender.size >= 50)
        assertTrue(bigmac.tage.first().startsWith("2000") && bigmac.tage.last() >= "2026-07-01")
        val usa = bigmac.laender.getValue("USA")
        assertEquals(0.0, usa.rohDollar.last()!!, 1e-9)
        val euz = bigmac.laender.getValue(BigMac.EURORAUM)
        assertEquals(0.0, euz.rohEuro!!, 1e-9)
        val che = BigMac.fuer(bigmac, "CHE", "CHF")!!.first
        // Roh gegen den Euro: Dollarpreise im Verhaeltnis (auf Promille gerundet in der Datei)
        assertEquals((che.dollarPreis / euz.dollarPreis - 1.0) * 100.0, che.rohEuro!!, 0.2)
        assertEquals((che.dollarPreis / usa.dollarPreis - 1.0) * 100.0, che.rohDollar.last()!!, 0.2)
        assertTrue(BigMac.euroPreis(bigmac, che)!! > BigMac.euroPreis(bigmac, euz)!!)
        assertEquals(euz.preis, BigMac.euroPreis(bigmac, euz)!!, 1e-9)
        // Nur Preise und Indexwerte von The Economist, keine Wechselkurse und kein BIP Dritter
        val roh = asset(BigMac.DATEI).readText(Charsets.UTF_8)
        assertFalse(roh.contains("dollar_ex") || roh.contains("GDP"))
        assertTrue(roh.contains("The Economist") && roh.contains("CC BY 4.0"))
    }

    @Test fun ranglistenDatei() {
        val hdi = Ranglisten.platz(rang, Ranglisten.Index.HDI, "DEU")!!
        assertTrue(hdi.von >= 190)
        assertTrue(hdi.rang in 1..15)
        assertTrue(hdi.wert.wert in 0.9..1.0)
        // Platz 1 hat den hoechsten Wert, beim Gini den niedrigsten
        val alleHdi = rang.indizes.getValue(Ranglisten.Index.HDI)
        val bestes = alleHdi.maxByOrNull { it.value.wert }!!.key
        assertEquals(1, Ranglisten.platz(rang, Ranglisten.Index.HDI, bestes)!!.rang)
        val gini = rang.indizes.getValue(Ranglisten.Index.GINI).filterKeys { Ranglisten.platz(rang, Ranglisten.Index.GINI, it) != null }
        assertEquals(1, Ranglisten.platz(rang, Ranglisten.Index.GINI, gini.minByOrNull { it.value.wert }!!.key)!!.rang)
        assertTrue(gini.size >= 140)
        for (x in Ranglisten.Index.entries) assertTrue(x.name, (rang.indizes[x]?.size ?: 0) >= 150)
        val v = Ranglisten.hdiVerlauf(rang, "DEU")
        assertEquals(1990, v.first().first)
        assertEquals(hdi.wert.wert, v.last().second, 1e-9)
        assertTrue(Ranglisten.plaetze(rang, "DEU").size == Ranglisten.Index.entries.size)
    }

    // ------------------------------------------------------------ Globus

    @Test fun ebenenDesGlobus() {
        val up = 0xFF00FF00.toInt(); val down = 0xFFFF0000.toInt(); val n = 0xFFCC9900.toInt()
        assertEquals(0f, GlobusFarbe.staerke(GlobusModus.PREISNIVEAU, 100.0), 1e-6f)
        assertEquals(-0.5f, GlobusFarbe.staerke(GlobusModus.PREISNIVEAU, 70.0), 1e-6f)
        assertEquals(1f, GlobusFarbe.staerke(GlobusModus.KKP, 80.0), 1e-6f)
        assertEquals(0f, GlobusFarbe.staerke(GlobusModus.HDI, 0.7), 1e-6f)
        // Ohne Urteil: darueber neutral, darunter blau; der HDI in Gruen und Rot
        assertEquals(n, GlobusFarbe.farbe(GlobusModus.PREISNIVEAU, 200.0, up, down, n))
        assertEquals(GlobusFarbe.ERHOLUNG, GlobusFarbe.farbe(GlobusModus.KKP, -90.0, up, down, n))
        assertEquals(n, GlobusFarbe.farbe(GlobusModus.GINI, 60.0, up, down, n))
        assertEquals(up, GlobusFarbe.farbe(GlobusModus.HDI, 1.0, up, down, n))
        assertEquals(down, GlobusFarbe.farbe(GlobusModus.HDI, 0.4, up, down, n))
        assertEquals(GlobusFarbe.OHNE_DATEN, GlobusFarbe.farbe(GlobusModus.HDI, null, up, down, n))
        assertTrue(GlobusModus.PREISNIVEAU.mitJahr && GlobusModus.KKP.mitJahr && !GlobusModus.HDI.mitJahr)
        // Neue Ebenen stehen am Ende: gespeicherte Nummern der alten Ebenen bleiben gueltig
        assertEquals(GlobusModus.AKTIEN1950.ordinal + 1, GlobusModus.PREISNIVEAU.ordinal)
    }

    // ------------------------------------------------------------ Pflege und Nennung

    @Test fun dateienSindGepflegtUndAlsGueltigErkannt() {
        for (n in listOf(Waehrungsstaerke.DATEI, BigMac.DATEI, Ranglisten.DATEI)) {
            assertTrue(n in Alter.dateien())
            assertEquals(1, Datenbuendel.SCHEMA[n])
            assertTrue(asset(n).length() < 300_000)
            val im = listOf(File("../daten/$n"), File("daten/$n")).first { it.exists() }
            assertEquals(asset(n).readText(Charsets.UTF_8), im.readText(Charsets.UTF_8))
            assertTrue(de.troetenrepublik.app.market.Daten.probeLesen(n, asset(n).readText(Charsets.UTF_8)))
        }
        assertEquals(Alter.HALBJAHR, Alter.profilFuer(BigMac.DATEI))
        assertEquals(Alter.STANDARD, Alter.profilFuer(Waehrungsstaerke.DATEI))
    }

    @Test fun nennungInDerQuellenliste() {
        val notice = listOf("NOTICE", "../NOTICE").map { File(it) }.first { it.exists() }.readText(Charsets.UTF_8).replace(Regex("\\s+"), " ")
        assertTrue(notice.contains("globus/bigmac.json: The Economist, The Big Mac index"))
        assertTrue(notice.contains("Creative Commons Attribution 4.0 International License (https://creativecommons.org/licenses/by/4.0/)"))
        assertTrue(notice.contains("globus/ranglisten.json: UNDP, Human Development Report"))
        assertTrue(notice.contains("Creative Commons Attribution 3.0 IGO license (https://creativecommons.org/licenses/by/3.0/igo/)"))
        assertTrue(notice.contains("globus/waehrung.json: World Bank, World Development Indicators (CC BY 4.0"))
    }
}
