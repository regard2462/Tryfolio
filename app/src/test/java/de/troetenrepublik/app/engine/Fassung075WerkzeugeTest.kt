package de.troetenrepublik.app.engine

import de.troetenrepublik.app.FinderAlarm
import de.troetenrepublik.app.FinderStand
import de.troetenrepublik.app.data.Candle
import de.troetenrepublik.app.engine.Abhaengigkeiten.Eingabe
import de.troetenrepublik.app.engine.Abhaengigkeiten.Zeilentyp
import de.troetenrepublik.app.engine.Aktienfinder.Daten
import de.troetenrepublik.app.engine.Einflussverzeichnis.Wertbild
import de.troetenrepublik.app.engine.FinderVorlagen.Feld
import de.troetenrepublik.app.engine.FinderVorlagen.Grenze
import de.troetenrepublik.app.finderAlarmAbgleich
import de.troetenrepublik.app.market.Praesident
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.math.sqrt

// Fassung 0.75: Werkzeuge fehlerfrei und zukunftsfest. Alle Kurse und Termine sind erfunden.
class Fassung075WerkzeugeTest {

    private val tag = 86_400_000L

    private fun ms(j: Int, m: Int, t: Int): Long = LocalDate.of(j, m, t).atTime(14, 0).toInstant(ZoneOffset.UTC).toEpochMilli()

    // Werktaegliche Kerzen von bis, Kurs steigt langsam (Wert egal, es zaehlt die Abdeckung).
    private fun reihe(von: Long, bis: Long): List<Candle> {
        val aus = ArrayList<Candle>()
        var t = von
        var c = 100.0
        while (t <= bis) {
            val wt = java.time.Instant.ofEpochMilli(t).atZone(ZoneOffset.UTC).dayOfWeek.value
            if (wt <= 5) { aus += Candle(t, c, c, c, c); c *= 1.0002 }
            t += tag
        }
        return aus
    }

    // ------------------------------------------------------------ A3 Crash-Test

    // Nachgestellt fuer 2035: Tagesreihe rollend zehn Jahre, fruehe Reihe ab Dezember 1999 bis fruehesEnde. Jede Krise
    // muss in einer der beiden ganz liegen, auch COVID, Zinswende und Zoelle.
    @Test fun crashTestDecktAlleKrisenAuch2035() {
        val jetzt = ms(2035, 6, 15)
        val taeglich = reihe(jetzt - 3652 * tag, jetzt)
        val frueh = reihe(ms(1999, 12, 1), PortfolioTools.fruehesEnde(jetzt))
        for (k in PortfolioTools.CRASHES) {
            val r = PortfolioTools.windowReturn(taeglich, k.from, k.to) ?: PortfolioTools.windowReturn(frueh, k.from, k.to)
            assertNotNull("Krise ${k.key} fehlt 2035", r)
        }
        // Mit dem alten festen Ende (31.12.2016) fehlten COVID, Zinswende und Zoelle
        val alt = reihe(ms(1999, 12, 1), 1_483_228_800_000L)
        val fehlend = PortfolioTools.CRASHES.filter { k ->
            PortfolioTools.windowReturn(taeglich, k.from, k.to) == null && PortfolioTools.windowReturn(alt, k.from, k.to) == null
        }.map { it.key }
        assertEquals(listOf("COVID", "RATES", "TARIFFS"), fehlend)
    }

    @Test fun crashTestJedesJahrBis2060() {
        for (j in 2026..2060) {
            val jetzt = ms(j, 1, 2)
            val taeglich = reihe(jetzt - 3652 * tag, jetzt)
            val frueh = reihe(ms(1999, 12, 1), PortfolioTools.fruehesEnde(jetzt))
            for (k in PortfolioTools.CRASHES) {
                assertNotNull("${k.key} $j", PortfolioTools.windowReturn(taeglich, k.from, k.to) ?: PortfolioTools.windowReturn(frueh, k.from, k.to))
            }
        }
        // Nie vor Ende 2016
        assertEquals(1_483_228_800_000L, PortfolioTools.fruehesEnde(ms(2020, 1, 1)))
    }

    @Test fun erholungUeberBeideReihen() {
        // Krise 2020 in der fruehen Reihe (endet 2028), Erholung erst 2029 in der Tagesreihe (beginnt 2026)
        fun k(t: Long, c: Double) = Candle(t, c, c, c, c)
        val von = ms(2020, 2, 3)
        val bis = ms(2020, 6, 1)
        val frueh = listOf(k(ms(2019, 1, 7), 90.0), k(von, 100.0), k(bis, 60.0), k(ms(2026, 6, 1), 80.0), k(ms(2028, 1, 4), 95.0))
        val taeglich = listOf(k(ms(2026, 1, 5), 80.0), k(ms(2027, 1, 4), 90.0), k(ms(2029, 3, 1), 101.0))
        assertNull(PortfolioTools.recoveryDays(frueh, von, bis))
        val ganz = PortfolioTools.zusammengesetzt(frueh, taeglich)
        assertEquals(frueh.take(3) + taeglich, ganz)
        assertEquals((ms(2029, 3, 1) - bis) / tag, PortfolioTools.recoveryDays(ganz, von, bis))
        // Ohne Tagesreihe bleibt die fruehe
        assertEquals(frueh, PortfolioTools.zusammengesetzt(frueh, emptyList()))
    }

    // ------------------------------------------------------------ A6 Dividendenprognose

    private val jetzt = ms(2026, 11, 10)

    private fun quelle(vararg tageZurueck: Int, betrag: Double = 1.0) =
        PortfolioTools.DividendSource("A", "Alpha", 10.0, tageZurueck.map { jetzt - it * tag to betrag })

    @Test fun jahreszahlerMitSpaeteremExTagFehltNicht() {
        // Vorjahr am 370. Tag zurueck, dieses Jahr noch nicht gezahlt (Ex-Tag rutscht ein paar Tage spaeter)
        val e = PortfolioTools.expectedDividends(listOf(quelle(370, 735)), jetzt)
        assertEquals(1, e.size)
        assertEquals(jetzt + tag, e[0].date)
        assertEquals(10.0, e[0].amount, 1e-9)
    }

    @Test fun jahreszahlerSchonGezahltZaehltEinmal() {
        // Vorjahr vor 370 Tagen, dieses Jahr vor 3 Tagen: nur die Zahlung dieses Jahres fortschreiben
        val e = PortfolioTools.expectedDividends(listOf(quelle(370, 3)), jetzt)
        assertEquals(listOf(jetzt - 3 * tag + 365 * tag), e.map { it.date })
        // Frueher als im Vorjahr gezahlt: auch nur einmal
        val f = PortfolioTools.expectedDividends(listOf(quelle(360, 3)), jetzt)
        assertEquals(1, f.size)
    }

    @Test fun quartalsUndMonatszahlerOhneDoppelte() {
        val q = PortfolioTools.expectedDividends(listOf(quelle(380, 290, 200, 110, 20)), jetzt)
        assertEquals(4, q.size)
        val m = PortfolioTools.expectedDividends(listOf(quelle(*IntArray(14) { it * 30 })), jetzt)
        assertEquals(12, m.size)
        assertEquals(m.size, m.map { it.date }.distinct().size)
    }

    @Test fun toleranzNachZahlweise() {
        assertEquals(45 * tag, PortfolioTools.dividendenToleranz(listOf(jetzt - 100 * tag), jetzt))
        assertEquals(45 * tag, PortfolioTools.dividendenToleranz(listOf(jetzt - 400 * tag, jetzt - 35 * tag), jetzt))
        assertEquals(91 * tag / 4, PortfolioTools.dividendenToleranz(listOf(jetzt - 273 * tag, jetzt - 182 * tag, jetzt - 91 * tag, jetzt), jetzt))
        assertEquals(7 * tag + tag / 2, PortfolioTools.dividendenToleranz((0..12).map { jetzt - it * 30 * tag }, jetzt))
    }

    // ------------------------------------------------------------ A4/B1 fremde Depots

    @Test fun schwankungDesBestands() {
        val start = ms(2024, 1, 1)
        // Woechentliche Kurse: A schwankt +-2 %, B gegenlaeufig +-1 %
        val a = ArrayList<Candle>()
        val b = ArrayList<Candle>()
        var ca = 100.0
        var cb = 100.0
        for (w in 0 until 80) {
            val t = start + w * 7 * tag
            a += Candle(t, ca, ca, ca, ca)
            b += Candle(t, cb, cb, cb, cb)
            ca *= if (w % 2 == 0) 1.02 else 0.98
            cb *= if (w % 2 == 0) 0.99 else 1.01
        }
        val einzeln = PortfolioTools.bestandsSchwankung(listOf(1.0), listOf(a), start)!!
        val r = Risikobeitrag.renditen(listOf(a), start)!!
        assertEquals(sqrt(Risikobeitrag.kovarianz(r)[0][0]), einzeln, 1e-12)
        val beide = PortfolioTools.bestandsSchwankung(listOf(500.0, 500.0), listOf(a, b), start)!!
        assertEquals(Risikobeitrag.berechnen(listOf(500.0, 500.0), listOf(a, b), start)!!.schwankung, beide, 1e-12)
        // Gegenlaeufige Werte gleichen sich aus
        assertTrue(beide < einzeln)
        // Zu wenig Wochen: keine Aussage
        assertNull(PortfolioTools.bestandsSchwankung(listOf(1.0), listOf(a.take(10)), start))
        assertNull(PortfolioTools.bestandsSchwankung(emptyList(), emptyList(), start))
    }

    // ------------------------------------------------------------ Entnahme und Umschichten

    @Test fun entnahmeFolgtDerBewertungOhneEigeneWahl() {
        // Beim ersten Oeffnen noch leer: Gesamtvermoegen, Rate aus 0
        assertEquals(1, PortfolioTools.entnahmeStart(-1, 0.0))
        assertEquals(0.0, PortfolioTools.entnahmeRate(-1.0, 0.0, 10.0), 0.0)
        // Bewertung kommt an: Wertpapiere und 4 % im Jahr
        assertEquals(0, PortfolioTools.entnahmeStart(-1, 120_000.0))
        assertEquals(400.0, PortfolioTools.entnahmeRate(-1.0, 120_000.0, 10.0), 1e-9)
        // Eigene Wahl bleibt
        assertEquals(1, PortfolioTools.entnahmeStart(1, 120_000.0))
        assertEquals(250.0, PortfolioTools.entnahmeRate(250.0, 120_000.0, 10.0), 0.0)
        assertEquals(0.0, PortfolioTools.entnahmeRate(0.0, 120_000.0, 10.0), 0.0)
    }

    @Test fun zielgewichteHaengenAnDerMengeNichtDerReihenfolge() {
        assertEquals(PortfolioTools.positionsMenge(listOf("B", "A", "C")), PortfolioTools.positionsMenge(listOf("C", "B", "A")))
        assertFalse(PortfolioTools.positionsMenge(listOf("A", "B")) == PortfolioTools.positionsMenge(listOf("A", "B", "C")))
    }

    // ------------------------------------------------------------ C3 Positionsgroesse

    @Test fun einStueckZuViel() {
        val r = Analysis.positionSize(1_000.0, 500.0, 1.0, 8.0, wholeUnits = true)!!
        assertEquals(0.0, r.units, 0.0)
        assertTrue(r.einStueckZuViel)
        assertEquals(40.0, r.lossPerUnit, 1e-9)
        assertEquals(10.0, r.maxLoss, 1e-9)
        assertFalse(Analysis.positionSize(100_000.0, 500.0, 1.0, 8.0, wholeUnits = true)!!.einStueckZuViel)
    }

    // ------------------------------------------------------------ Derivate: Boersenfeiertage

    private val berlin = ZoneId.of("Europe/Berlin")

    private fun tage(now: Long) = Derivatives.expiries(now).map { java.time.Instant.ofEpochMilli(it).atZone(berlin).toLocalDate() }

    @Test fun verfaelleNurAnBoersentagen() {
        for (start in listOf(LocalDate.of(2026, 12, 10), LocalDate.of(2027, 3, 12), LocalDate.of(2027, 4, 20))) {
            val now = start.atTime(12, 0).atZone(berlin).toInstant().toEpochMilli()
            val t = tage(now)
            assertTrue(t.isNotEmpty())
            for (d in t) {
                assertTrue("$d", d.dayOfWeek.value <= 5)
                assertFalse("$d ist Feiertag", Boersenzeiten.feiertagDe(d))
            }
        }
        // Weihnachten 2026: Freitag 25.12. ist Feiertag, 24.12. auch; der Verfall rutscht auf Mittwoch 23.12.
        val dez = tage(LocalDate.of(2026, 12, 10).atTime(12, 0).atZone(berlin).toInstant().toEpochMilli())
        assertTrue(LocalDate.of(2026, 12, 23) in dez)
        assertFalse(LocalDate.of(2026, 12, 25) in dez)
        // Karfreitag 2027 (26.03.) ist kein Verfall, dafuer der Donnerstag davor
        val mrz = tage(LocalDate.of(2027, 3, 12).atTime(12, 0).atZone(berlin).toInstant().toEpochMilli())
        assertFalse(LocalDate.of(2027, 3, 26) in mrz)
        assertTrue(LocalDate.of(2027, 3, 25) in mrz)
    }

    // ------------------------------------------------------------ B8 Praesident

    @Test fun praesidentAbrufReichtBisHeute() {
        val stich = 20_696L
        fun tageVon(range: String) = when (range) {
            "3mo" -> 90; "6mo" -> 180; "1y" -> 365; "2y" -> 730; "5y" -> 1826; "10y" -> 3652; else -> Int.MAX_VALUE
        }
        for (abstand in listOf(10, 100, 400, 800, 1_000, 2_000, 4_000, 8_000)) {
            val r = Praesident.zeitraum(stich, stich + abstand)
            assertTrue("$abstand $r", tageVon(r) >= abstand)
        }
        assertEquals("2y", Praesident.zeitraum(stich, stich + 500))
        assertEquals("5y" to "1d", Praesident.zeitraum(stich, stich + 1_000) to Praesident.intervall(stich, stich + 1_000))
        assertEquals("10y" to "1wk", Praesident.zeitraum(stich, stich + 2_000) to Praesident.intervall(stich, stich + 2_000))
        assertEquals("max" to "1mo", Praesident.zeitraum(stich, stich + 4_000) to Praesident.intervall(stich, stich + 4_000))
        assertEquals("1d", Praesident.intervall(stich, stich + 300))
    }

    // ------------------------------------------------------------ B3 Depotfinder ohne Verbindung

    @Test fun finderAusfallUndStand() {
        assertTrue(FinderStand.ausfall(200, 0))
        assertFalse(FinderStand.ausfall(200, 1))
        assertFalse(FinderStand.ausfall(0, 0))
        assertEquals(5L, FinderStand.stand(listOf(9L, 5L, 7L), 100L))
        assertEquals(100L, FinderStand.stand(emptyList(), 100L))
    }

    @Test fun alarmMeldetNachAusfallNichtAllesNeu() {
        val v = FinderVorlagen.Vorlage("TEST", 1, listOf(Grenze(Feld.KGV, max = 15.0)))
        val a = FinderAlarm("US", "TEST", listOf(0), bekannt = listOf("A", "B"))
        // Ohne Antwort des Sammelabrufs: Werte bleiben offen, die gemerkten Treffer bleiben gemerkt
        val ohne = listOf(Daten("A", "A", land = "US", mitSammel = false), Daten("B", "B", land = "US", mitSammel = false))
        val (bekannt, neu) = finderAlarmAbgleich(ohne, v, a, 0L)
        assertEquals(setOf("A", "B"), bekannt.toSet())
        assertTrue(neu.isEmpty())
        // Wieder verbunden: nichts gilt als neu
        val mit = listOf(Daten("A", "A", land = "US", kgv = 10.0), Daten("B", "B", land = "US", kgv = 12.0))
        assertTrue(finderAlarmAbgleich(mit, v, a.copy(bekannt = bekannt), 0L).second.isEmpty())
        // Mit Antwort, aber ohne Wert: nicht erfuellt (wie bisher)
        assertTrue(finderAlarmAbgleich(listOf(Daten("A", "A", land = "US")), v, a, 0L).first.isEmpty())
    }

    // ------------------------------------------------------------ A7 einfluss.json nach Ablauf

    private fun datei(name: String): String =
        listOf("src/main/assets/$name", "../app/src/main/assets/$name").map { File(it) }.first { it.exists() }.readText()

    @Test(timeout = 20_000)
    fun abgelaufeneEinordnungenStehenAlsLuecke() {
        val index = Einflussverzeichnis.aufbauen(datei(WeltDaten.EINFLUSS), datei(WeltDaten.ROHSTOFFE))
        val vw = Wertbild("VOW3.DE", "FAHRZEUGE", "DE", "DE")
        val vorher = Abhaengigkeiten.karte(index, Eingabe(vw), LocalDate.of(2026, 11, 10).toEpochDay())
        assertTrue(vorher.any { it.typ == Zeilentyp.GROESSE })
        assertTrue(vorher.none { it.typ == Zeilentyp.LUECKE_ABGELAUFEN })
        // Ohne neue Datei nach dem 30.06.2028: Einordnungen weg, aber als Luecke genannt (Anzahl, spaetester Ablauf)
        val nachher = Abhaengigkeiten.karte(index, Eingabe(vw), LocalDate.of(2028, 7, 1).toEpochDay())
        val l = nachher.filter { it.typ == Zeilentyp.LUECKE_ABGELAUFEN }
        assertEquals(1, l.size)
        assertTrue((l[0].ganz ?: 0) >= 1)
        val bis = l[0].zahl!!.toLong()
        assertTrue(bis in LocalDate.of(2027, 6, 30).toEpochDay()..LocalDate.of(2028, 6, 30).toEpochDay())
        val einordnungen = Einflussverzeichnis.abgelaufen(index, listOf(vw), LocalDate.of(2028, 7, 1).toEpochDay())!!
        assertEquals(l[0].ganz, einordnungen.first)
        assertNull(Einflussverzeichnis.abgelaufen(index, listOf(vw), LocalDate.of(2026, 11, 10).toEpochDay()))
    }

    // A7 auch in Was waere wenn, Mitteilung und Wirkungsnetz: abgelaufene Einordnungen werden genannt, nicht still weggelassen
    private fun tagVon(j: Int, m: Int, t: Int) = LocalDate.of(j, m, t).toEpochDay()

    @Test(timeout = 20_000)
    fun szenarienNennenAbgelaufeneEinordnungen() {
        val index = Einflussverzeichnis.aufbauen(datei(WeltDaten.EINFLUSS), datei(WeltDaten.ROHSTOFFE))
        val bestand = listOf(
            Einflussverzeichnis.Bestand(Wertbild("HLX.TEST", "HALBLEITER", "US", "US"), "Halbleiter", 0.6),
            Einflussverzeichnis.Bestand(Wertbild("BKX.TEST", "BANKEN", "DE", "DE"), "Bank", 0.4),
        )
        val vorher = Szenarien.ereignis(Szenarien.Ereignis.TAIWAN, index, bestand, tagVon(2026, 11, 10))
        assertEquals(listOf("HLX.TEST"), vorher.betroffen.map { it.symbol })
        assertNull(vorher.abgelaufen)
        // Nach dem Ablauf: kein Treffer mehr, aber Anzahl und Ablauf der weggefallenen Einordnungen
        val nachher = Szenarien.ereignis(Szenarien.Ereignis.TAIWAN, index, bestand, tagVon(2028, 7, 1))
        assertTrue(nachher.betroffen.isEmpty())
        val (n, bis) = nachher.abgelaufen!!
        assertEquals(3, n)
        assertEquals(tagVon(2028, 6, 30), bis)
        // Rohstoff-Embargo haengt nicht an einfluss.json
        assertNull(Szenarien.ereignis(Szenarien.Ereignis.EMBARGO, index, bestand, tagVon(2028, 7, 1)).abgelaufen)
    }

    @Test(timeout = 20_000)
    fun wirkungsnetzNenntAusgeblendeteGroessen() {
        val index = Einflussverzeichnis.aufbauen(datei(WeltDaten.EINFLUSS), datei(WeltDaten.ROHSTOFFE))
        assertNull(Wirkungsnetz.ausgeblendet(index, tagVon(2026, 11, 10)))
        val sichtbar = Wirkungsnetz.uebersicht(index, emptyList(), emptyList(), emptyMap(), tagVon(2026, 11, 10)).size
        val (n, bis) = Wirkungsnetz.ausgeblendet(index, tagVon(2028, 7, 1))!!
        val danach = Wirkungsnetz.uebersicht(index, emptyList(), emptyList(), emptyMap(), tagVon(2028, 7, 1)).size
        // Genau die ausgeblendeten fehlen in der Uebersicht
        assertEquals(sichtbar - danach, n)
        assertEquals(tagVon(2028, 6, 30), bis)
        assertNull(Wirkungsnetz.eintrag(index, "spannung:TWN", emptyList(), emptyList(), emptyMap(), tagVon(2028, 7, 1)))
        assertEquals(tagVon(2028, 6, 30), Wirkungsnetz.abgelaufen(index, "spannung:TWN", tagVon(2028, 7, 1)))
        assertNull(Wirkungsnetz.abgelaufen(index, "spannung:TWN", tagVon(2026, 11, 10)))
    }

    @Test fun mitteilungNenntAbgelaufeneWerte() {
        // Erfundene Groesse: ein Engpass, der einen Wert namentlich nennt, gueltig bis 30.06.2027
        val einfluss = """{"v":1,"stand":"2026-06-01","groessen":[{"id":"engpass:test","typ":"ENGPASS","de":"Test","en":"Test",""" +
            """"stand":"2026-06-01","ablauf":"2027-06-30","gruppen":[{"werte":["AAA.TEST"]}]}]}"""
        val index = Einflussverzeichnis.aufbauen(einfluss, null, emptyList(), emptyList(), emptyList(), emptyList())
        val depot = listOf(Einflussverzeichnis.Bestand(Wertbild("AAA.TEST", null, "US"), "Alpha", 0.5))
        val ereignis = WeltMeldung.Ereignis("engpass:test", Einflussverzeichnis.Typ.ENGPASS, -30.0, 2.0)
        val vorher = tagVon(2027, 6, 1)
        val nachher = tagVon(2027, 7, 1)
        assertNotNull(WeltMeldung.waehlen(index, listOf(ereignis), depot, WeltMeldung.Gemerkt(), vorher))
        assertNull(WeltMeldung.ausgeblendet(index, depot, vorher))
        // Nach dem Ablauf kommt keine Mitteilung mehr; das wird genannt
        assertNull(WeltMeldung.waehlen(index, listOf(ereignis), depot, WeltMeldung.Gemerkt(), nachher))
        assertEquals(1 to tagVon(2027, 6, 30), WeltMeldung.ausgeblendet(index, depot, nachher))
        // Nur auf der Watchlist (Anteil 0): keine Mitteilung, also auch kein Hinweis
        assertNull(WeltMeldung.ausgeblendet(index, depot.map { Einflussverzeichnis.Bestand(it.wert, it.name, 0.0) }, nachher))
        // Wirkungsnetz derselben Groesse
        assertEquals(1 to tagVon(2027, 6, 30), Wirkungsnetz.ausgeblendet(index, nachher))
    }
}
