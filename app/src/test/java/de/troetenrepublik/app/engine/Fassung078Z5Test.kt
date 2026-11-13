package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.Candle
import de.troetenrepublik.app.i18n.MarktLaenderTexte
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

class Fassung078Z5Test {
    private val tagMs = 86_400_000L

    // Tagesreihe aus Stufen: ab jedem genannten Tag gilt der Wert bis zur naechsten Stufe, Ende am letzten Tag.
    private fun stufen(bis: Long, vararg s: Pair<Long, Double>): List<Pair<Long, Double>> {
        val aus = ArrayList<Pair<Long, Double>>()
        for (t in s.first().first..bis) aus += t to s.last { it.first <= t }.second
        return aus
    }

    // ------------------------------------------------------------ Phasenzaehler

    @Test fun phasenLageNachAnteilen() {
        assertEquals(listOf(Phasenzaehler.Lage.NICHT, Phasenzaehler.Lage.NICHT, Phasenzaehler.Lage.NICHT, Phasenzaehler.Lage.ABWARTEND, Phasenzaehler.Lage.TEILWEISE, Phasenzaehler.Lage.VOLL, Phasenzaehler.Lage.VOLL, Phasenzaehler.Lage.VOLL), (0..7).map { Phasenzaehler.lage(it, 7) })
        assertEquals(listOf(Phasenzaehler.Lage.NICHT, Phasenzaehler.Lage.NICHT, Phasenzaehler.Lage.ABWARTEND, Phasenzaehler.Lage.TEILWEISE, Phasenzaehler.Lage.TEILWEISE, Phasenzaehler.Lage.VOLL, Phasenzaehler.Lage.VOLL), (0..6).map { Phasenzaehler.lage(it, 6) })
        assertEquals(listOf(Phasenzaehler.Lage.NICHT, Phasenzaehler.Lage.NICHT, Phasenzaehler.Lage.ABWARTEND, Phasenzaehler.Lage.TEILWEISE, Phasenzaehler.Lage.VOLL, Phasenzaehler.Lage.VOLL), (0..5).map { Phasenzaehler.lage(it, 5) })
    }

    @Test fun phasenSaisonUndMesspunkt() {
        assertEquals(listOf(11, 12, 1, 2, 3, 4), (1..12).filter { Phasenzaehler.saisonPositiv(it) }.sortedBy { (it + 1) % 12 })
        val r = listOf(100L to 1.0, 103L to 2.0, 120L to 3.0)
        assertEquals(103L to 2.0, Phasenzaehler.wertAm(r, 110))
        assertEquals(120L to 3.0, Phasenzaehler.wertAm(r, 115))
        assertEquals(100L to 1.0, Phasenzaehler.wertAm(r, 95))
        assertNull(Phasenzaehler.wertAm(r, 80))
        assertNull(Phasenzaehler.wertAm(r, 131))
        assertNull(Phasenzaehler.letzter(r, 120 + Phasenzaehler.FRISCH + 1))
        assertEquals(103L to 2.0, Phasenzaehler.letzter(r, 119))
    }

    @Test fun phasenZaehltSiebenGroessen() {
        val heute = LocalDate.of(2026, 11, 10).toEpochDay()
        val vor1 = heute - 365
        val vor2 = heute - 730
        val start = heute - 800
        val rohstoff = stufen(heute, start to 100.0, vor1 - 20 to 120.0, heute - 20 to 108.0)
        val e = Phasenzaehler.Eingaben(
            rendite10 = stufen(heute - 1, start to 4.5, heute - 20 to 4.0),
            rendite1 = stufen(heute - 1, start to 4.0, heute - 20 to 4.2),
            euroDollar = stufen(heute, start to 1.10, heute - 20 to 1.05),
            rohstoffe = List(4) { rohstoff },
            oel = stufen(heute, start to 80.0, heute - 20 to 88.0),
            heute = heute,
        )
        assertTrue(vor2 > start)
        val r = Phasenzaehler.auswerten(e)!!
        val z = r.zeilen.associateBy { it.groesse }
        assertEquals(Phasenzaehler.Groesse.entries.toSet(), z.keys)
        // Rohstoffe 108 gegen 120 vor einem Jahr; vor einem Jahr 120 gegen 100
        assertEquals(-10.0, z[Phasenzaehler.Groesse.ROHSTOFFE]!!.wert, 1e-9)
        assertTrue(z[Phasenzaehler.Groesse.ROHSTOFFE]!!.positiv)
        assertEquals(14.0, z[Phasenzaehler.Groesse.RELATIVZINS]!!.wert, 1e-9)
        assertEquals(-15.5, z[Phasenzaehler.Groesse.RELATIVZINS]!!.vorjahr!!, 1e-9)
        assertTrue(z[Phasenzaehler.Groesse.RELATIVZINS]!!.positiv)
        assertEquals(-0.2, z[Phasenzaehler.Groesse.ZINSSTRUKTUR]!!.wert, 1e-9)
        assertEquals(0.5, z[Phasenzaehler.Groesse.ZINSSTRUKTUR]!!.vorjahr!!, 1e-9)
        assertFalse(z[Phasenzaehler.Groesse.ZINSSTRUKTUR]!!.positiv)
        assertTrue(z[Phasenzaehler.Groesse.RENDITE]!!.positiv)
        assertEquals(heute - 1, z[Phasenzaehler.Groesse.RENDITE]!!.tag)
        assertEquals((1.10 / 1.05 - 1) * 100, z[Phasenzaehler.Groesse.DOLLAR]!!.wert, 1e-9)
        assertTrue(z[Phasenzaehler.Groesse.DOLLAR]!!.positiv)
        assertEquals(10.0, z[Phasenzaehler.Groesse.OEL]!!.wert, 1e-9)
        assertFalse(z[Phasenzaehler.Groesse.OEL]!!.positiv)
        assertTrue(z[Phasenzaehler.Groesse.SAISON]!!.positiv)
        assertEquals(5, r.positiv)
        assertEquals(Phasenzaehler.Lage.VOLL, r.lage)
    }

    @Test fun phasenOhneDatenOderMitAltenDaten() {
        val heute = LocalDate.of(2026, 6, 15).toEpochDay()
        val start = heute - 800
        val leer = emptyList<Pair<Long, Double>>()
        // Nur Renditen: Zinsstruktur, Rendite, Saison, also unter der Mindestzahl
        val nurZins = Phasenzaehler.Eingaben(stufen(heute, start to 4.0), stufen(heute, start to 3.0), leer, emptyList(), leer, heute)
        assertNull(Phasenzaehler.auswerten(nurZins))
        // Drei Rohstoffe reichen nicht fuer den Korb; mit Dollar und Oel sind es fuenf Groessen
        val rohstoff = stufen(heute, start to 100.0)
        val knapp = Phasenzaehler.Eingaben(
            stufen(heute, start to 4.0), stufen(heute, start to 3.0), stufen(heute, start to 1.1), List(3) { rohstoff }, stufen(heute, start to 80.0), heute,
        )
        val r = Phasenzaehler.auswerten(knapp)!!
        assertEquals(5, r.zeilen.size)
        assertTrue(r.zeilen.none { it.groesse == Phasenzaehler.Groesse.ROHSTOFFE || it.groesse == Phasenzaehler.Groesse.RELATIVZINS })
        // Juni: Saison negativ; unveraenderte Rendite, Dollar und Oel zaehlen nicht positiv; nur die Zinsstruktur
        assertEquals(1, r.positiv)
        assertEquals(Phasenzaehler.Lage.NICHT, r.lage)
        // Reihen, die vor zwei Monaten enden, zaehlen nicht mehr
        val alt = Phasenzaehler.Eingaben(
            stufen(heute - 60, start to 4.0), stufen(heute - 60, start to 3.0), stufen(heute - 60, start to 1.1), List(4) { stufen(heute - 60, start to 100.0) },
            stufen(heute - 60, start to 80.0), heute,
        )
        assertNull(Phasenzaehler.auswerten(alt))
    }

    // ------------------------------------------------------------ Kurs und Umsatz

    @Test fun kursUmsatzZonen() {
        assertEquals(KursUmsatz.Zone.KAUFZONE, KursUmsatz.zone(-8.0, -20.0, 25.0))
        assertEquals(KursUmsatz.Zone.FRUEHER_AUFSCHWUNG, KursUmsatz.zone(6.0, -10.0, -15.0))
        assertEquals(KursUmsatz.Zone.VERKAUFSZONE, KursUmsatz.zone(6.0, 30.0, 15.0))
        assertEquals(KursUmsatz.Zone.FRUEHER_ABSCHWUNG, KursUmsatz.zone(-4.0, 10.0, -12.0))
        // Anstieg mit hohem Umsatz, aber ueber zwoelf Monate im Minus: keine Zone
        assertEquals(KursUmsatz.Zone.KEINE, KursUmsatz.zone(6.0, -5.0, 15.0))
        assertEquals(KursUmsatz.Zone.KEINE, KursUmsatz.zone(1.0, 20.0, 30.0))
        assertEquals(KursUmsatz.Zone.KEINE, KursUmsatz.zone(6.0, 20.0, 5.0))
    }

    @Test fun kursUmsatzAusKerzen() {
        val n = KursUmsatz.KURZ + KursUmsatz.LANG + 1
        // Kurs 100, in den letzten 63 Tagen auf 110; Umsatz davor 100, zuletzt 80
        val kerzen = (0 until n).map { i ->
            val jung = i >= n - KursUmsatz.KURZ
            val kurs = if (jung) 110.0 else 100.0
            Candle(i * tagMs, kurs, kurs, kurs, kurs, if (jung) 80.0 else 100.0)
        }
        val heute = n.toLong()
        val e = KursUmsatz.auswerten(kerzen, heute)!!
        assertEquals(10.0, e.kurs, 1e-9)
        assertEquals(10.0, e.kursLang, 1e-9)
        assertEquals(-20.0, e.umsatz, 1e-9)
        assertEquals(KursUmsatz.Zone.FRUEHER_AUFSCHWUNG, e.zone)
        assertEquals((n - 1) * tagMs, e.tag)
        // Der laufende Tag zaehlt nicht mit: mit ihm fehlt ein Tag
        assertNull(KursUmsatz.auswerten(kerzen, heute - 1))
        // Ohne Umsatz keine Aussage
        assertNull(KursUmsatz.auswerten(kerzen.map { it.copy(volume = 0.0) }, heute))
    }

    // ------------------------------------------------------------ 16-Wochen-Rhythmus

    @Test fun rhythmusAnker() {
        fun tag(j: Int, m: Int, t: Int) = LocalDate.of(j, m, t).toEpochDay()
        assertEquals(16, Rhythmus16.woche(tag(1987, 10, 19)))
        assertEquals(16, Rhythmus16.woche(tag(1987, 10, 25)))
        assertEquals(1, Rhythmus16.woche(tag(1987, 10, 26)))
        assertEquals(2, Rhythmus16.woche(tag(1987, 11, 2)))
        // 1.472 Wochen nach dem 19.10.1987
        assertEquals(16, Rhythmus16.woche(tag(2016, 1, 4)))
        assertEquals(1, Rhythmus16.woche(tag(2016, 1, 11)))
        assertEquals(15, Rhythmus16.woche(tag(1987, 10, 12)))
        assertEquals(java.time.DayOfWeek.MONDAY, LocalDate.ofEpochDay(Rhythmus16.ANKER).dayOfWeek)
    }

    @Test fun rhythmusRechnetNurInvestierteWochen() {
        val wochen = 640
        // Freitagsschluss je Woche: plus 2 % in den Wochen 13 bis 15, sonst minus 0,1 %
        val kurse = ArrayList<Pair<Long, Double>>()
        var k = 100.0
        kurse += Rhythmus16.ANKER - 3 to k
        for (w in 0 until wochen) {
            val freitag = Rhythmus16.ANKER + w * 7L + 4
            k *= if (Rhythmus16.woche(freitag) in Rhythmus16.INVESTIERT) 1.02 else 0.999
            kurse += freitag to k
        }
        val heute = Rhythmus16.ANKER + wochen * 7L
        val r = Rhythmus16.auswerten(kurse, heute)!!
        assertEquals(wochen, r.wochen)
        val investiert = wochen / 16 * 3
        assertEquals(Math.pow(1.02, investiert.toDouble()), r.strategie, 1e-6)
        assertEquals(k / 100.0, r.halten, 1e-9)
        assertTrue(r.strategie > r.halten)
        assertEquals(3.0 / 16, r.anteilZeit, 1e-9)
        assertEquals(1.0, r.imPlus, 1e-9)
        assertTrue(r.beste in Rhythmus16.INVESTIERT)
        assertFalse(r.schwaechste in Rhythmus16.INVESTIERT)
        assertEquals(Math.pow(0.999, (wochen / 16).toDouble()), r.jeWoche[0], 1e-9)
        assertTrue(r.strategieJahr > r.haltenJahr)
        // Die laufende Woche bleibt draussen
        assertEquals(wochen - 1, Rhythmus16.auswerten(kurse, heute - 1)!!.wochen)
        // Zu kurze Reihe
        assertNull(Rhythmus16.auswerten(kurse.take(Rhythmus16.MINDEST_WOCHEN), heute))
    }

    // ------------------------------------------------------------ Schuldentragfaehigkeit

    // Ausschnitt der gebuendelten Datei (Weltbank, Stand 12.11.2026)
    private val ausschnitt = """{"stand":"2026-11-12","schema":1,"l":{"ARG":{"e":[2024,16.86],"z":[2024,8.462],"k":[2024,164.4],"w":[2024,203.5],"r":[2025,6.002]},"DEU":{"e":[2024,29.37],"z":[2024,2.48],"w":[2024,2.599],"r":[2025,11.33]},"JPN":{"w":[2025,4.656],"r":[2025,30.92]},"TUR":{"e":[2024,31.46],"z":[2024,11.66],"k":[2024,114.5],"w":[2024,64.58],"r":[2025,11.62]},"USA":{"e":[2024,17.78],"z":[2024,20.29],"w":[2024,5.345],"r":[2025,4.503]}}}"""

    @Test fun schuldenLesenUndRechnen() {
        val d = Schuldenlast.lesen(ausschnitt)!!
        assertEquals("2026-11-12", d.stand)
        assertEquals(5, d.laender.size)
        assertNull(d.laender["JPN"]!!.einnahmen)
        val usa = Schuldenlast.rechnen(d.laender["USA"]!!) { 120.0 }.associateBy { it.art }
        assertEquals(120.0 / 17.78 * 100, usa[Schuldenlast.Art.SCHULDEN_EINNAHMEN]!!.wert, 1e-9)
        assertEquals(20.29, usa[Schuldenlast.Art.ZINS_EINNAHMEN]!!.wert, 1e-9)
        // Zins auf die Schulden: 20,29 % von 17,78 % des BIP durch 120 % des BIP
        val zins = 20.29 * 17.78 / 100 / 120.0 * 100
        assertEquals(zins, usa[Schuldenlast.Art.ZINS_WACHSTUM]!!.a!!, 1e-9)
        assertEquals(zins - 5.345, usa[Schuldenlast.Art.ZINS_WACHSTUM]!!.wert, 1e-9)
        assertEquals(Fruehwarnung.Ampel.GRUEN, usa[Schuldenlast.Art.ZINS_WACHSTUM]!!.ampel)
        assertEquals(4.503 / 120.0 * 100, usa[Schuldenlast.Art.RESERVEN]!!.wert, 1e-9)
        assertNull(usa[Schuldenlast.Art.KURZFRISTIG])
        assertEquals(2024, usa[Schuldenlast.Art.SCHULDEN_EINNAHMEN]!!.jahr)
        // Kurzfristige Auslandsschulden ueber den Reserven
        val arg = Schuldenlast.rechnen(d.laender["ARG"]!!) { 85.0 }.associateBy { it.art }
        assertEquals(Fruehwarnung.Ampel.ROT, arg[Schuldenlast.Art.KURZFRISTIG]!!.ampel)
        // Ohne Schuldenquote bleiben nur die Kennzahlen ohne Schulden
        val ohne = Schuldenlast.rechnen(d.laender["TUR"]!!) { null }.map { it.art }
        assertEquals(listOf(Schuldenlast.Art.ZINS_EINNAHMEN, Schuldenlast.Art.KURZFRISTIG), ohne)
        // Zins ueber dem Wachstum: rot
        val teuer = Schuldenlast.Land(Schuldenlast.Wert(2024, 20.0), Schuldenlast.Wert(2024, 30.0), Schuldenlast.Wert(2024, 2.0), null, null)
        assertEquals(Fruehwarnung.Ampel.ROT, Schuldenlast.rechnen(teuer) { 100.0 }.first { it.art == Schuldenlast.Art.ZINS_WACHSTUM }.ampel)
        assertNull(Schuldenlast.lesen("{}"))
        assertNull(Schuldenlast.lesen("""{"stand":"heute","l":{"DEU":{}}}"""))
    }

    @Test fun schuldenAmpelNachDritteln() {
        val alle = (1..30).map { it.toDouble() }
        assertEquals(Fruehwarnung.Ampel.GRUEN, Schuldenlast.drittel(5.0, alle, true))
        assertEquals(Fruehwarnung.Ampel.GELB, Schuldenlast.drittel(15.0, alle, true))
        assertEquals(Fruehwarnung.Ampel.ROT, Schuldenlast.drittel(28.0, alle, true))
        assertEquals(Fruehwarnung.Ampel.ROT, Schuldenlast.drittel(5.0, alle, false))
        assertEquals(Fruehwarnung.Ampel.GRUEN, Schuldenlast.drittel(28.0, alle, false))
        assertNull(Schuldenlast.drittel(5.0, alle.take(29), true))
        // Tabelle: 30 Laender mit steigenden Zinsausgaben, gleiche Einnahmen und Schulden
        val l = (1..30).joinToString(",") { i -> "\"L%02d\":{\"e\":[2024,20],\"z\":[2024,$i],\"w\":[2024,50],\"r\":[2024,$i]}".format(i) }
        val t = Schuldenlast.tabelle(Schuldenlast.lesen("""{"stand":"2026-11-12","l":{$l}}""")!!) { _, _ -> 60.0 }
        fun ampel(land: String, art: Schuldenlast.Art) = t[land]!!.first { it.art == art }.ampel
        assertEquals(Fruehwarnung.Ampel.GRUEN, ampel("L01", Schuldenlast.Art.ZINS_EINNAHMEN))
        assertEquals(Fruehwarnung.Ampel.ROT, ampel("L30", Schuldenlast.Art.ZINS_EINNAHMEN))
        // Viele Reserven sind die kleinere Last
        assertEquals(Fruehwarnung.Ampel.ROT, ampel("L01", Schuldenlast.Art.RESERVEN))
        assertEquals(Fruehwarnung.Ampel.GRUEN, ampel("L30", Schuldenlast.Art.RESERVEN))
        // Gleiche Werte bei allen: niemand ist leichter, also gruen
        assertEquals(Fruehwarnung.Ampel.GRUEN, ampel("L15", Schuldenlast.Art.SCHULDEN_EINNAHMEN))
        assertEquals(Fruehwarnung.Ampel.GRUEN, ampel("L15", Schuldenlast.Art.ZINS_WACHSTUM))
    }

    private fun datei(vararg orte: String): File = orte.map { File(it) }.first { it.exists() }

    @Test fun schuldenDateiIstGepflegt() {
        val n = Schuldenlast.DATEI
        val asset = datei("src/main/assets/$n", "../app/src/main/assets/$n")
        assertTrue(asset.length() < 300_000)
        val text = asset.readText(Charsets.UTF_8)
        val d = Schuldenlast.lesen(text)!!
        assertTrue(d.laender.size > 150)
        assertNotNull(d.laender["DEU"]!!.einnahmen)
        assertTrue(d.laender.values.count { it.zinsen != null } > 100)
        assertTrue(n in Alter.dateien())
        assertEquals(Alter.STANDARD, Alter.profilFuer(n))
        assertEquals(1, Datenbuendel.SCHEMA[n])
        assertTrue(de.troetenrepublik.app.market.Daten.probeLesen(n, text))
        assertFalse(de.troetenrepublik.app.market.Daten.probeLesen(n, "{}"))
        assertTrue(datei("tools/daten/dateien.json", "../tools/daten/dateien.json").readText(Charsets.UTF_8).contains("\"name\": \"$n\""))
        val notice = datei("NOTICE", "../NOTICE").readText(Charsets.UTF_8).replace(Regex("\\s+"), " ")
        assertTrue(notice.contains("globus/schulden.json: World Bank, World Development Indicators (CC BY 4.0"))
    }

    // ------------------------------------------------------------ Texte

    @Test fun texteInBeidenSprachen() {
        val de = MarktLaenderTexte.schluessel(true)
        assertEquals(de, MarktLaenderTexte.schluessel(false))
        val noetig = Phasenzaehler.Lage.entries.map { "pz.lage." + it.name } +
            Phasenzaehler.Groesse.entries.flatMap { listOf("pz.${it.name}.0", "pz.${it.name}.1") } +
            KursUmsatz.Zone.entries.map { "ku.zone." + it.name } +
            Schuldenlast.Art.entries.flatMap { listOf("sl." + it.name, "sl.info." + it.name) }
        for (k in noetig) assertTrue(k, k in de)
        for (sprache in listOf(true, false)) for (k in de) {
            val t = MarktLaenderTexte.text(sprache, k, emptyList())
            assertTrue(k, t.isNotBlank())
            assertFalse(k, t.contains('—') || t.contains('–') || t.contains('?'))
        }
        assertEquals("5 von 7 positiv", MarktLaenderTexte.text(true, "pz.zahl", listOf("5", "7")))
        assertEquals("", MarktLaenderTexte.text(true, "gibt.es.nicht", emptyList()))
    }
}
