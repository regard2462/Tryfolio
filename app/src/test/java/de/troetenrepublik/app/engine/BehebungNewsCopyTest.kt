package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.Lang
import de.troetenrepublik.app.i18n.I18n
import de.troetenrepublik.app.market.Kongress
import de.troetenrepublik.app.market.Portfolioverlauf
import de.troetenrepublik.app.market.Rss
import de.troetenrepublik.app.market.Strategien
import de.troetenrepublik.app.ui.Fmt
import de.troetenrepublik.app.ui.betragLesen
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.TreeMap

// Nachrichten, Mitteilungen, Rueckblick, Auswertungen und Eingaben des Copytradings: Zahlenbeispiele zu den Behebungen.
class BehebungNewsCopyTest {

    @After
    fun zurueck() { I18n.lang = Lang.DE }

    // ------------------------------------------------------------ Eingabe von Betraegen

    @Test(timeout = 20_000)
    fun betraegeNachSprache() {
        assertEquals(1000.0, betragLesen("1.000", ',')!!, 1e-9)
        assertEquals(1000.5, betragLesen("1.000,50", ',')!!, 1e-9)
        assertEquals(1_234_567.0, betragLesen("1.234.567", ',')!!, 1e-9)
        assertEquals(1000.0, betragLesen("1,000", '.')!!, 1e-9)
        assertEquals(1000.5, betragLesen("1,000.50", '.')!!, 1e-9)
        assertEquals(12.5, betragLesen("12,5", ',')!!, 1e-9)
        assertEquals(12.5, betragLesen("12.5", '.')!!, 1e-9)
        // Das andere Zeichen allein mit ein oder zwei Stellen dahinter ist ein Dezimalzeichen
        assertEquals(1.5, betragLesen("1.5", ',')!!, 1e-9)
        assertEquals(2.5, betragLesen("2,50", '.')!!, 1e-9)
        assertEquals(0.5, betragLesen("0.500", ',')!!, 1e-9)
        assertEquals(200.0, betragLesen("200", ',')!!, 1e-9)
        assertNull(betragLesen("", ','))
        assertNull(betragLesen(",", ','))
        assertNull(betragLesen("1,2,3", ','))
        assertNull(betragLesen("1,000,00", '.'))
        assertNull(betragLesen("12a", ','))
    }

    @Test(timeout = 20_000)
    fun keineNegativeNull() {
        I18n.lang = Lang.DE
        assertEquals("0,00", Fmt.num(-0.0001))
        assertFalse(Fmt.euro(-0.004).let { it.contains('-') || it.contains('−') })
        assertFalse(Fmt.pct(-0.001, 2).contains('-'))
        // Echte negative Werte behalten das Vorzeichen
        assertTrue(Fmt.euro(-0.006).contains('−'))
        assertEquals("-1,50", Fmt.num(-1.5))
        assertEquals("NaN", Fmt.num(Double.NaN))
    }

    // ------------------------------------------------------------ Mitteilungsnummern

    @Test(timeout = 20_000)
    fun mitteilungsnummernSteigenUeberProzesseHinweg() {
        val a = Mitteilungsnummer.naechste(0, 1_790_000_000L)
        assertEquals(1_790_000_000, a)
        // mehrere im selben Moment
        assertEquals(1_790_000_001, Mitteilungsnummer.naechste(a, 1_790_000_000L))
        // neuer Prozess zehn Minuten spaeter: keine Nummer des alten Prozesses (Zaehler beginnt wieder bei 0)
        val b = Mitteilungsnummer.naechste(0, 1_790_000_600L)
        assertTrue(b > 1_790_000_001 + 100)
        // Zaehlung laeuft weiter, auch wenn die Uhr stehen bleibt
        assertEquals(b + 1, Mitteilungsnummer.naechste(b, 1_790_000_600L))
    }

    // ------------------------------------------------------------ Tagesrueckblick

    private val berlin = ZoneId.of("Europe/Berlin")
    private fun berlinMs(tag: LocalDate, h: Int, m: Int) = ZonedDateTime.of(tag.atTime(h, m), berlin).toInstant().toEpochMilli()
    private val mittwoch = LocalDate.of(2026, 11, 4)

    @Test(timeout = 20_000)
    fun rueckblickVorUsSchlussIstVorlaeufig() {
        val tag = mittwoch.toEpochDay()
        val us = listOf("SP500")
        // 19 Uhr Berlin: New York ist noch offen (Schluss 16 Uhr dort, 22 Uhr hier)
        assertTrue(Tagesrueckblick.vorlaeufigPlan(berlinMs(mittwoch, 19, 0), tag, us))
        assertFalse(Tagesrueckblick.vorlaeufigPlan(berlinMs(mittwoch, 22, 10), tag, us))
        // Ein reines DAX-Depot ist um 19 Uhr schon fertig
        assertFalse(Tagesrueckblick.vorlaeufigPlan(berlinMs(mittwoch, 19, 0), tag, listOf("DAX")))
        // Bitcoin hat keinen Schluss
        assertFalse(Tagesrueckblick.vorlaeufigPlan(berlinMs(mittwoch, 12, 0), tag, listOf("BTC")))
        assertEquals(listOf("SP500", "DAX"), Tagesrueckblick.maerkteDerPositionen(listOf("AAPL", "SAP.DE", "MSFT")))
        assertEquals(listOf("SP500"), Tagesrueckblick.maerkteDerPositionen(emptyList()))
    }

    @Test(timeout = 20_000)
    fun vorlaeufigerRueckblickWirdNachSchlussErsetztOhneZweiteMitteilung() {
        val tag = mittwoch.toEpochDay()
        val ab = 19 * 60
        val us = listOf("SP500")
        val neunzehn = berlinMs(mittwoch, 19, 0)
        // Noch nichts da: um 19 Uhr faellig
        assertEquals(tag, Tagesrueckblick.faellig(neunzehn, Tagesrueckblick.Archiv(), berlin, ab, us))
        // Um 19 Uhr erstellt, vorlaeufig: bis zum US-Schluss nicht noch einmal
        val archiv = Tagesrueckblick.Archiv(listOf(Tagesrueckblick.Rueckblick(tag, neunzehn, vorlaeufig = true)))
        assertNull(Tagesrueckblick.faellig(berlinMs(mittwoch, 19, 30), archiv, berlin, ab, us))
        assertNull(Tagesrueckblick.faellig(berlinMs(mittwoch, 22, 0), archiv, berlin, ab, us))
        // Nach Schluss (22:05) wird er ersetzt, auch am naechsten Morgen noch
        assertEquals(tag, Tagesrueckblick.faellig(berlinMs(mittwoch, 22, 10), archiv, berlin, ab, us))
        assertEquals(tag, Tagesrueckblick.faellig(berlinMs(mittwoch.plusDays(1), 7, 0), archiv, berlin, ab, us))
        // Der endgueltige beendet die Sache
        val fertig = Tagesrueckblick.Archiv(listOf(Tagesrueckblick.Rueckblick(tag, berlinMs(mittwoch, 22, 10), vorlaeufig = false)))
        assertNull(Tagesrueckblick.faellig(berlinMs(mittwoch, 23, 0), fertig, berlin, ab, us))
        // Ein von Hand vor der gewaehlten Uhrzeit erstellter vorlaeufiger wird zur Uhrzeit ersetzt (Mitteilung kommt dann)
        val vonHand = Tagesrueckblick.Archiv(listOf(Tagesrueckblick.Rueckblick(tag, berlinMs(mittwoch, 18, 0), vorlaeufig = true)))
        assertEquals(tag, Tagesrueckblick.faellig(neunzehn, vonHand, berlin, ab, us))
        assertTrue(berlinMs(mittwoch, 18, 0) < Tagesrueckblick.geplantAm(tag, ab, berlin))
        assertEquals(neunzehn, Tagesrueckblick.geplantAm(tag, ab, berlin))
    }

    // ------------------------------------------------------------ Schlagzeilen

    private val t0 = 1_790_000_000_000L
    private fun e(titel: String, link: String, zeit: Long, kat: Set<Rss.Kategorie> = setOf(Rss.Kategorie.MAERKTE)) =
        Rss.Eintrag(titel, link, zeit, kategorien = kat)

    @Test(timeout = 20_000)
    fun spaetGelieferteMeldungWirdGemeldet() {
        val now = t0
        val schnell = e("Schnell", "s", now - 10 * 60_000L)
        val spaet = e("Spaet geliefert", "l", now - 3_600_000L)
        // Die Marke aus der Zeit der zuletzt gemeldeten Schlagzeile haette „spaet“ verworfen; nach Kennung bleibt sie melden
        assertEquals(listOf("l"), Rss.zuMelden(listOf(schnell, spaet), 0L, now, schon = setOf("s")).map { it.link })
        assertEquals(listOf("l", "s"), Rss.zuMelden(listOf(schnell, spaet), 0L, now).map { it.link })
        // Aelter als zwoelf Stunden nie
        assertTrue(Rss.zuMelden(listOf(e("Alt", "a", now - 13 * 3_600_000L)), 0L, now).isEmpty())
        // Was beim Oeffnen der Seite schon gelesen war, nicht
        assertTrue(Rss.zuMelden(listOf(spaet), now - 30 * 60_000L, now).isEmpty())
    }

    @Test(timeout = 20_000)
    fun eigeneFeedsBehaltenEintraegeLaenger() {
        val now = t0
        val tag = 86_400_000L
        val eigener = e("Newsletter vom Monatsanfang", "https://blog.test/a", now - 20 * tag, setOf(Rss.Kategorie.EIGENE))
        val markt = e("Alte Marktmeldung", "https://m.test/a", now - 20 * tag)
        val z = Rss.zusammen(listOf(listOf(eigener, markt)), now)
        assertEquals(listOf("https://blog.test/a"), z.map { it.link })
    }

    @Test(timeout = 20_000)
    fun gleicherTitelNurBinnenVierundzwanzigStunden() {
        val now = t0
        val tag = 86_400_000L
        val gestern = e("Marktbericht", "https://a.test/1", now - 3 * tag)
        val heute = e("Marktbericht", "https://b.test/2", now - 3_600_000L)
        assertEquals(2, Rss.zusammen(listOf(listOf(gestern), listOf(heute)), now).size)
        val gleichzeitig = e("Marktbericht", "https://c.test/3", now - 3_000_000L)
        val z = Rss.zusammen(listOf(listOf(heute), listOf(gleichzeitig)), now)
        assertEquals(1, z.size)
    }

    @Test(timeout = 20_000)
    fun zukunftszeitBleibtBeiMehrmaligemLadenGleich() {
        val now = t0
        val zukunft = e("Spaetere Zeit", "https://z.test/1", now + 30 * 60_000L)
        val erst = Rss.zusammen(listOf(listOf(zukunft)), now).single().zeit
        val spaeter = Rss.zusammen(listOf(listOf(zukunft)), now + 10 * 60_000L).single().zeit
        assertEquals(now, erst)
        assertEquals(erst, spaeter)
    }

    @Test(timeout = 20_000)
    fun zeitzonenUndZeitOhneZone() {
        val soll = Rss.zeit("Tue, 03 Nov 2026 14:00:00 GMT")
        assertTrue(soll > 0)
        assertEquals(soll, Rss.zeit("Tue, 03 Nov 2026 07:00:00 MST"))
        assertEquals(soll, Rss.zeit("Tue, 03 Nov 2026 08:00:00 MDT"))
        assertEquals(soll, Rss.zeit("Tue, 03 Nov 2026 15:00:00 CET"))
        assertEquals(soll, Rss.zeit("Tue, 03 Nov 2026 16:00:00 CEST"))
        assertEquals(soll, Rss.zeit("Tue, 03 Nov 2026 15:00:00 BST"))
        assertEquals(soll, Rss.zeit("Tue, 03 Nov 2026 14:00:00"))
        assertEquals(soll, Rss.zeit("2026-11-03T14:00:00"))
        assertEquals(soll, Rss.zeit("2026-11-03T15:00:00+01:00"))
    }

    @Test(timeout = 20_000)
    fun atomNimmtVeroeffentlichungStattAenderung() {
        val xml = "<feed><entry><title>Alter Beitrag</title><link href=\"https://a.test/x\"/>" +
            "<updated>2026-11-05T10:00:00Z</updated><published>2026-11-01T10:00:00Z</published></entry></feed>"
        val l = Rss.lesen(xml, "Blog", Rss.Kategorie.EIGENE)
        assertEquals(Rss.zeit("2026-11-01T10:00:00Z"), l.single().zeit)
        // Ohne published gilt updated
        val ohne = Rss.lesen(xml.replace("<published>2026-11-01T10:00:00Z</published>", ""), "Blog", Rss.Kategorie.EIGENE)
        assertEquals(Rss.zeit("2026-11-05T10:00:00Z"), ohne.single().zeit)
    }

    @Test(timeout = 20_000)
    fun linkNurImPfadBereinigt() {
        assertEquals("https://a.test/p/q?next=//x&id=3#top", Rss.linkBereinigen("https://a.test//p//q?next=//x&utm_source=a&id=3#top"))
        assertEquals("https://a.test/a", Rss.linkBereinigen("https://a.test/a?utm_source=x&utm_medium=y"))
        assertEquals("https://beincrypto.com/x/", Rss.linkBereinigen("https://beincrypto.com/x/?.tsrc=rss"))
        assertEquals("https://a.test/a?id=1", Rss.linkBereinigen("https://a.test/a?id=1&utm_campaign=z"))
    }

    // ------------------------------------------------------------ Glocke, Summen, Sparplaene, Verlauf

    private fun meldung(id: String, tag: Int) = Kongress.Meldung(id, "Muster", "Max", "", "CA11", tag * 86_400_000L, 2026)

    @Test(timeout = 20_000)
    fun neueMeldungenNachReihenfolgeImVerzeichnis() {
        val l = Kongress.neuesteZuerst(listOf(meldung("20000001", 10), meldung("20000003", 12), meldung("20000002", 12), meldung("20000004", 12)))
        assertEquals(listOf("20000004", "20000003", "20000002", "20000001"), l.map { it.docId })
        // Alles vor der zuletzt gesehenen, auch am selben Tag; die gesehene selbst nicht
        assertEquals(listOf("20000004", "20000003"), Kongress.neueSeit(l, "20000002").map { it.docId })
        assertTrue(Kongress.neueSeit(l, "20000004").isEmpty())
        // Gesehene Meldung nicht mehr im Verzeichnis: nur die juengste, nicht jede der Person
        assertEquals(listOf("20000004"), Kongress.neueSeit(l, "19999999").map { it.docId })
    }

    @Test(timeout = 20_000)
    fun summeNenntWasOhneKursFehlt() {
        val b = listOf(10.0 to "EUR", 5.0 to "USD", 2.5 to "EUR")
        assertEquals(1, InsiderBetraege.ohneKurs(b, "EUR"))
        assertEquals(0, InsiderBetraege.ohneKurs(listOf(10.0 to "EUR"), "EUR"))
    }

    @Test(timeout = 20_000)
    fun sparplanBrauchtFuerJedenAnteilEinenEuro() {
        val allwetter = Strategien.alle.first { it.id == "allwetter" }.anteile
        val mindest = Strategien.mindestBetrag(allwetter)
        fun angelegt(monat: Double) = allwetter.count { (_, w) -> Math.round(monat * w * 100) / 100.0 >= 1.0 }
        assertEquals(allwetter.size, angelegt(mindest))
        assertTrue(angelegt(mindest - 0.01) < allwetter.size)
        // Das Beispiel des Berichts: 13 Euro im Monat reichen nicht fuer Gold und Rohstoffe (7,5 %)
        assertTrue(mindest > 13.0)
        assertEquals(0.0, Strategien.mindestBetrag(emptyList()), 0.0)
    }

    @Test(timeout = 20_000)
    fun gleicheZeilenInEinerMeldungBleibenNeueinreichungenZaehlenEinmal() {
        fun h(tag: Long) = Portfolioverlauf.Handel(tag, "NVDA", "Nvidia", true, 15_000.0, false, false)
        val a = listOf(h(1), h(1), h(1))
        val neueinreichung = listOf(h(1), h(1), h(1), h(2))
        val z = Portfolioverlauf.ohneDoppelte(listOf(a, neueinreichung))
        assertEquals(4, z.size)
        assertEquals(3, z.count { it.t == 1L })
        assertEquals(3, Portfolioverlauf.ohneDoppelte(listOf(a)).size)
    }

    // ------------------------------------------------------------ Kongress: Tageskurse und Einstieg

    private fun kurse(f: (Int) -> Double, bis: Int = 400) =
        KongressAuswertung.Kurse.aus(TreeMap<Long, Double>().apply { for (d in 0..bis) put(d.toLong(), f(d)) })

    @Test(timeout = 20_000)
    fun ersterHandelstagNachDemTag() {
        val k = KongressAuswertung.Kurse.aus(TreeMap<Long, Double>(mapOf(100L to 1.0, 101L to 2.0, 103L to 3.0)))
        val t = KongressAuswertung.TAG
        assertEquals(2.0, k.nach(100 * t)!!, 0.0)
        assertEquals(3.0, k.nach(101 * t + 5)!!, 0.0)
        assertEquals(1.0, k.nach(99 * t)!!, 0.0)
        assertNull(k.nach(103 * t))
        assertEquals(2.0, k.am(101 * t)!!, 0.0)
    }

    @Test(timeout = 20_000)
    fun kongressKauftEinstiegErstNachDerMeldung() {
        val t = KongressAuswertung.TAG
        val handel = listOf(
            KongressAuswertung.Handel("k:A", "A", 'H', "S", true, 18 * t, 20 * t, 1_000.0),
            KongressAuswertung.Handel("k:A", "A", 'H', "S", true, 24 * t, 25 * t, 1_000.0),
        )
        // Am Tag 51 (erster Handelstag nach dem Stichtag 50) springt der Kurs, danach bleibt er stehen
        val k = mapOf("S" to kurse({ d -> if (d <= 50) 100.0 else 110.0 }))
        val s = KongressAuswertung.strategie(handel, k, null, 300 * t)
        assertNotNull(s)
        // Der Sprung am Tag nach dem Stichtag gehoert nicht dem Nachbauer: erst dann wird gekauft
        assertEquals(100.0, s!!.verlauf[1].second, 1e-9)
        assertEquals(100.0, s.verlauf.last().second, 1e-9)
    }

    @Test(timeout = 20_000)
    fun ranglisteMitTageskursenVomTagDesGeschaefts() {
        val t = KongressAuswertung.TAG
        val jetzt = 300 * t
        // Aktie verdoppelt sich am Tag 281 (der Kauf am Tag 280 liegt davor), Index bleibt gleich
        val aktie = kurse({ d -> if (d <= 280) 100.0 else 200.0 })
        val index = kurse({ 100.0 })
        val handel = (0 until 3).map { KongressAuswertung.Handel("k:A", "A", 'H', "S", true, (280 - it).toLong() * t, (281 - it).toLong() * t, 10_000.0) }
        val r = KongressAuswertung.rangliste(handel, mapOf("S" to aktie), index, jetzt)
        assertEquals(1, r.size)
        // Vom Schlusskurs am Tag des Geschaefts (100) bis zum letzten (200): +100 Prozent, Index 0
        assertEquals(1.0, r[0].vorsprung, 1e-9)
    }
}
