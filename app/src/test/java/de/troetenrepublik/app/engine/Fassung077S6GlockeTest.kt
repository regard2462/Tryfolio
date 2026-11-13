package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.AssetType
import de.troetenrepublik.app.data.PriceAlert
import de.troetenrepublik.app.market.Rss
import de.troetenrepublik.app.market.SecInsider
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Glocke je Wertpapier (0.77): welche Arten es fuer einen Wert gibt, wann eine Art meldet, Merker, Zusammenfassung.
class Fassung077S6GlockeTest {
    private val heute = 20_000L
    private val tag = 86_400_000L

    private fun alarm(art: String, merk: String = "", gemeldet: Long = 0L, tagesProzent: Double = 0.0, target: Double = 0.0, symbol: String = "AAPL") =
        PriceAlert("a-$art-$target-$tagesProzent", symbol, "Apple", target, above = true, createdAt = (heute - 3) * tag, art = art, merk = merk, gemeldetAm = gemeldet, tagesProzent = tagesProzent)

    private fun daten(
        kurs: Double = 100.0, aktuell: Boolean = true, hoch52: Double = 0.0, tief52: Double = 0.0, tagesHoch: Double = 0.0, tagesTief: Double = 0.0,
        insider: List<SecInsider.Geschaeft> = emptyList(), quartal: Long? = null, analyst: List<Analystenbilanz.Urteil>? = null,
        achtK: List<String>? = null, news: List<Alarmarten.NewsTreffer> = emptyList(),
    ) = Alarmarten.Daten(
        kurs, 1000.0, null, null, null, insider, heute, "US", aktuell, hoch52, tief52, tagesHoch, tagesTief, quartal, analyst, achtK, news,
    )

    private fun arten(typ: AssetType, us: Boolean, dividende: Boolean?) = Wertglocke.moeglich(typ, us, dividende).flatMap { it.arten }

    // ------------------------------------------------------------ welche Arten es gibt

    @Test fun usAktieMitDividendeHatAlleArten() {
        val a = arten(AssetType.STOCK, us = true, dividende = true)
        for (art in Alarmarten.ALLE) assertTrue(art, art in a)
        assertTrue(Wertglocke.MARKE in a && Wertglocke.BEWEGUNG in a)
        assertEquals(Wertglocke.Gruppe.values().toList(), Wertglocke.moeglich(AssetType.STOCK, true, true).map { it.gruppe })
    }

    @Test fun nichtMoeglicheArtenFehlen() {
        val de = arten(AssetType.STOCK, us = false, dividende = false)
        assertFalse(Alarmarten.INSIDER in de || Alarmarten.INSIDER_VK in de || Alarmarten.ACHTK in de || Alarmarten.EXDIV in de)
        assertTrue(Alarmarten.NEWS in de && Alarmarten.QUARTAL in de && Alarmarten.ANALYST in de)
        // Dividende unbekannt: ohne Ex-Tag
        assertFalse(Alarmarten.EXDIV in arten(AssetType.STOCK, true, null))
        val krypto = arten(AssetType.CRYPTO, us = true, dividende = null)
        assertEquals(
            listOf(Wertglocke.MARKE, Wertglocke.BEWEGUNG, Alarmarten.SCHNITT, Alarmarten.VOLUMEN, Alarmarten.WOCHEN52, Alarmarten.NEWS),
            krypto,
        )
        val etf = arten(AssetType.ETF, us = true, dividende = true)
        assertFalse(Alarmarten.ANALYST in etf || Alarmarten.QUARTAL in etf || Alarmarten.INSIDER in etf)
        val index = arten(AssetType.INDEX, false, null)
        assertFalse(Alarmarten.NEWS in index || Alarmarten.VOLUMEN in index)
        assertTrue(Alarmarten.WOCHEN52 in index)
    }

    @Test fun bestehenderAlarmBleibtSichtbar() {
        val m = Wertglocke.moeglich(AssetType.STOCK, us = false, dividende = null)
        val sicht = Wertglocke.sichtbar(m, setOf(Alarmarten.INSIDER, Alarmarten.EXDIV))
        val a = sicht.flatMap { it.arten }
        assertTrue(Alarmarten.INSIDER in a && Alarmarten.EXDIV in a)
        assertEquals(Wertglocke.Gruppe.INSIDER, sicht.first { Alarmarten.INSIDER in it.arten }.gruppe)
        assertEquals(sicht.map { it.gruppe.ordinal }, sicht.map { it.gruppe.ordinal }.sorted())
        // ohne Besonderheit bleibt die Liste, wie sie ist
        assertTrue(Wertglocke.sichtbar(m, setOf(Alarmarten.NEWS)) === m)
    }

    // ------------------------------------------------------------ Alle, Umlegen

    @Test fun alleSchaltetAllesAusserKursmarken() {
        val m = Wertglocke.moeglich(AssetType.STOCK, true, true)
        val alle = Wertglocke.fuerAlle(m)
        assertFalse(Wertglocke.MARKE in alle)
        assertTrue(Wertglocke.BEWEGUNG in alle)
        assertFalse(Wertglocke.alleAn(m, setOf(Alarmarten.NEWS)))
        assertTrue(Wertglocke.alleAn(m, alle.toSet()))
        val neu = { art: String -> alarm(art.takeIf { it != Wertglocke.BEWEGUNG }.orEmpty(), tagesProzent = if (art == Wertglocke.BEWEGUNG) 5.0 else 0.0) }
        val markeDa = alarm("", target = 180.0)
        val an = Wertglocke.umlegen(listOf(markeDa), "AAPL", alle, true, neu)
        assertEquals(1 + alle.size, an.size)
        assertTrue(Wertglocke.alleAn(m, an.map { Wertglocke.artVon(it) }.toSet()))
        // aus: Kursmarke bleibt
        val aus = Wertglocke.umlegen(an, "AAPL", alle, false, neu)
        assertEquals(listOf(markeDa), aus)
    }

    @Test fun einschaltenErsetztNichtsVorhandenes() {
        val da = alarm(Alarmarten.NEWS, merk = "123")
        val l = Wertglocke.umlegen(listOf(da), "AAPL", listOf(Alarmarten.NEWS, Alarmarten.QUARTAL), true) { alarm(it) }
        assertEquals(2, l.size)
        assertEquals("123", l.first { it.art == Alarmarten.NEWS }.merk)
        // anderer Wert bleibt unberuehrt
        val fremd = alarm(Alarmarten.NEWS, symbol = "MSFT")
        assertEquals(listOf(fremd), Wertglocke.umlegen(listOf(fremd, da), "AAPL", listOf(Alarmarten.NEWS), false) { alarm(it) })
    }

    @Test fun bewegungSchwelleBehaeltKennungUndMeldetag() {
        val alt = alarm("", tagesProzent = 3.0, gemeldet = 19_999L)
        val l = Wertglocke.bewegungSetzen(listOf(alt, alarm("", target = 5.0)), "AAPL", 10.0) { alarm("", tagesProzent = 5.0) }
        val b = l.single { Wertglocke.artVon(it) == Wertglocke.BEWEGUNG }
        assertEquals(10.0, b.tagesProzent, 0.0)
        assertEquals(alt.id, b.id)
        assertEquals(19_999L, b.gemeldetAm)
        assertEquals(2, l.size)
        val neu = Wertglocke.bewegungSetzen(emptyList(), "AAPL", 2.0) { alarm("", tagesProzent = 5.0) }
        assertEquals(2.0, neu.single().tagesProzent, 0.0)
    }

    // ------------------------------------------------------------ wann eine Art meldet

    private fun geschaeft(kauf: Boolean, a: String, f: String = "2026-11-10", wert: Double = 250_000.0) =
        SecInsider.Geschaeft(a = a, k = "AAPL", e = "Apple", p = "Jemand", kauf = kauf, d = f, f = f, wert = wert)

    @Test fun insiderVerkaufMeldetNurVerkaeufeEinmal() {
        val liste = listOf(geschaeft(kauf = true, a = "0001"), geschaeft(kauf = false, a = "0002"))
        val e = Alarmarten.pruefen(alarm(Alarmarten.INSIDER_VK), daten(insider = liste))
        assertTrue(e.gemeldet)
        assertEquals("ALERT_INSIDER_SELL", e.tag)
        assertEquals(250_000.0, e.extra, 1e-9)
        assertFalse(Alarmarten.pruefen(e.neu, daten(insider = liste)).gemeldet)
        // der Kaufalarm sieht den Verkauf nicht
        val kauf = Alarmarten.pruefen(alarm(Alarmarten.INSIDER), daten(insider = listOf(geschaeft(kauf = false, a = "0002"))))
        assertFalse(kauf.gemeldet)
        assertEquals("ALERT_INSIDER", Alarmarten.pruefen(alarm(Alarmarten.INSIDER), daten(insider = liste)).tag)
    }

    @Test fun wochenHochUndTiefMeldenEinmalJeRichtungUndTag() {
        val a = alarm(Alarmarten.WOCHEN52)
        assertFalse(Alarmarten.pruefen(a, daten(kurs = 90.0, hoch52 = 120.0, tief52 = 80.0, tagesHoch = 91.0, tagesTief = 89.0)).gemeldet)
        val hoch = Alarmarten.pruefen(a, daten(kurs = 119.0, hoch52 = 120.0, tief52 = 80.0, tagesHoch = 120.0, tagesTief = 118.0))
        assertTrue(hoch.gemeldet)
        assertEquals("ALERT_HIGH52", hoch.tag)
        assertEquals(120.0, hoch.extra, 1e-9)
        assertFalse(Alarmarten.pruefen(hoch.neu, daten(kurs = 120.0, hoch52 = 120.0, tagesHoch = 120.0)).gemeldet)
        // am naechsten Handelstag wieder
        assertTrue(Alarmarten.pruefen(hoch.neu, daten(kurs = 121.0, hoch52 = 121.0, tagesHoch = 121.0).copy(heute = heute + 1)).gemeldet)
        val tief = Alarmarten.pruefen(a, daten(kurs = 80.0, hoch52 = 120.0, tief52 = 80.0, tagesHoch = 82.0, tagesTief = 80.0))
        assertEquals("ALERT_LOW52", tief.tag)
        // ohne aktuellen Kurs oder ohne Spanne nichts
        assertFalse(Alarmarten.pruefen(a, daten(kurs = 120.0, hoch52 = 120.0, aktuell = false)).gemeldet)
        assertFalse(Alarmarten.pruefen(a, daten(kurs = 120.0)).gemeldet)
    }

    @Test fun meldungenMeldenJeGeschichteEinmalUndNurNeueres() {
        val a = alarm(Alarmarten.NEWS)
        val seit = (heute - 3) * tag
        val alt = Alarmarten.NewsTreffer(seit - 1000, "Apple alte Meldung")
        assertFalse(Alarmarten.pruefen(a, daten(news = listOf(alt))).gemeldet)
        val n1 = Alarmarten.NewsTreffer(seit + 1000, "Apple cuts forecast after weak iPhone sales")
        val e = Alarmarten.pruefen(a, daten(news = listOf(alt, n1)))
        assertTrue(e.gemeldet)
        assertEquals("ALERT_NEWS", e.tag)
        assertEquals(n1.titel, e.text)
        // dieselbe Meldung nicht noch einmal
        assertFalse(Alarmarten.pruefen(e.neu, daten(news = listOf(alt, n1))).gemeldet)
        // die neueste von mehreren
        val n2 = Alarmarten.NewsTreffer(seit + 5000, "Apple announces new chip")
        val zwei = Alarmarten.pruefen(e.neu, daten(news = listOf(n1, n2)))
        assertTrue(zwei.gemeldet)
        assertEquals(n2.titel, zwei.text)
        assertEquals(n2.zeit.toString(), zwei.neu.merk.substringBefore(9.toChar()))
    }

    @Test fun analystenurteilMeldetHochUndHerabstufungEinmal() {
        val a = alarm(Alarmarten.ANALYST)
        val seit = (heute - 3) * tag
        val alt = Analystenbilanz.Urteil("Alt AG", seit - 5000, "Buy", "Hold", "up")
        val hoch = Analystenbilanz.Urteil("Haus A", seit + 1000, "Buy", "Hold", "up")
        val bestaetigt = Analystenbilanz.Urteil("Haus B", seit + 2000, "Buy", "Buy", "main")
        // nicht abgerufen: nichts, auch kein Pruefstempel
        assertEquals(a, Alarmarten.pruefen(a, daten()).neu)
        val e = Alarmarten.pruefen(a, daten(analyst = listOf(alt, hoch, bestaetigt)))
        assertTrue(e.gemeldet)
        assertEquals("ALERT_ANALYST_UP", e.tag)
        assertEquals("Haus A · Buy", e.text)
        assertEquals(heute, e.neu.gemeldetAm)
        val zweite = Alarmarten.pruefen(e.neu, daten(analyst = listOf(alt, hoch, bestaetigt)))
        assertFalse(zweite.gemeldet)
        val runter = Analystenbilanz.Urteil("Haus C", seit + 9000, "Sell", "Hold", "down")
        assertEquals("ALERT_ANALYST_DOWN", Alarmarten.pruefen(e.neu, daten(analyst = listOf(hoch, runter))).tag)
    }

    @Test fun quartalszahlenMeldenAmVortagEinmal() {
        // Termin Montag, der Handelstag davor ist der Freitag
        val montag = java.time.LocalDate.of(2026, 11, 30).toEpochDay()
        val freitag = java.time.LocalDate.of(2026, 11, 27).toEpochDay()
        val a = alarm(Alarmarten.QUARTAL)
        assertFalse(Alarmarten.pruefen(a, daten(quartal = montag).copy(heute = freitag - 1)).gemeldet)
        val e = Alarmarten.pruefen(a, daten(quartal = montag).copy(heute = freitag))
        assertTrue(e.gemeldet)
        assertEquals("ALERT_EARNINGS", e.tag)
        assertEquals(montag.toDouble(), e.extra, 0.0)
        assertFalse(Alarmarten.pruefen(e.neu, daten(quartal = montag).copy(heute = montag)).gemeldet)
        // schon vorbei oder unbekannt: nichts
        assertFalse(Alarmarten.pruefen(a, daten(quartal = montag).copy(heute = montag + 1)).gemeldet)
        assertFalse(Alarmarten.pruefen(a, daten(quartal = null)).gemeldet)
        // naechstes Quartal meldet wieder
        assertTrue(Alarmarten.pruefen(e.neu, daten(quartal = montag + 90).copy(heute = montag + 89)).gemeldet)
    }

    @Test fun achtKMeldetNeueWarnzeichenEinmalUndMerktDenPruefTag() {
        val a = alarm(Alarmarten.ACHTK)
        val seit = java.time.LocalDate.ofEpochDay(heute - 3).toString()
        val vorher = "2000-01-01|0000-1"
        assertEquals(a, Alarmarten.pruefen(a, daten()).neu)
        val leer = Alarmarten.pruefen(a, daten(achtK = listOf(vorher)))
        assertFalse(leer.gemeldet)
        assertEquals(heute, leer.neu.gemeldetAm)
        val schluessel = listOf(vorher, "$seit|0001-2", "$seit|0001-3")
        val e = Alarmarten.pruefen(a, daten(achtK = schluessel))
        assertTrue(e.gemeldet)
        assertEquals("ALERT_8K", e.tag)
        assertEquals(2.0, e.extra, 0.0)
        assertFalse(Alarmarten.pruefen(e.neu, daten(achtK = schluessel)).gemeldet)
        assertTrue(Alarmarten.pruefen(e.neu, daten(achtK = schluessel + "$seit|0001-4")).gemeldet)
    }

    // ------------------------------------------------------------ Schlagzeilen zu einem Wert

    @Test fun nurWichtigeSchlagzeilenDesWertsZaehlen() {
        val jetzt = 1_800_000_000_000L
        fun e(t: String, vor: Long) = Rss.Eintrag(t, "https://x/" + t.hashCode(), jetzt - vor)
        val apple = e("Apple raises dividend and announces buyback", 1000)
        val fremd = e("Microsoft raises dividend and announces buyback", 1000)
        val unwichtig = e("Apple opens new store", 1000)
        val alt = e("Apple raises guidance for the year", 20 * 3_600_000L)
        val liste = listOf(apple, fremd, unwichtig, alt)
        val wichtig = setOf(apple.id, fremd.id, alt.id)
        val t = Wertglocke.newsTreffer(liste, wichtig, listOf("Apple", "$" + "AAPL"), jetzt)
        assertEquals(listOf(apple.titel), t.map { it.titel })
        assertTrue(Wertglocke.newsTreffer(liste, wichtig, emptyList(), jetzt).isEmpty())
    }

    // ------------------------------------------------------------ Zeile der Liste

    @Test fun zusammenfassungNenntAktiveArtenEinzeilig() {
        val l = listOf(alarm(Alarmarten.NEWS), alarm("", target = 180.0), alarm(Alarmarten.INSIDER))
        val z = Wertglocke.zusammenfassung(l, { "<$it>" }, { m -> if (m.size == 1) "Marke " + m[0] else m.size.toString() + " Marken" }, { "Bewegung $it" })
        assertEquals("Marke 180.0 · <INSIDER> · <NEWS>", z)
        assertFalse(z.contains(10.toChar()))
        val zwei = Wertglocke.zusammenfassung(l + alarm("", target = 190.0) + alarm("", tagesProzent = 5.0), { "<$it>" }, { m -> m.size.toString() + " Marken" }, { "Bewegung $it" })
        assertEquals("2 Marken · Bewegung 5.0 · <INSIDER> · <NEWS>", zwei)
        assertEquals("", Wertglocke.zusammenfassung(emptyList(), { it }, { "" }, { "" }))
    }

    @Test fun werteInReihenfolgeDesErstenAlarms() {
        val l = listOf(alarm(Alarmarten.NEWS, symbol = "MSFT"), alarm(Alarmarten.NEWS), alarm("", target = 1.0, symbol = "MSFT"))
        assertEquals(listOf("MSFT", "AAPL"), Wertglocke.werte(l).map { it.first })
    }

    // ------------------------------------------------------------ alte Staende

    @Test fun alteGespeicherteAlarmeBleibenLadbar() {
        val json = Json { ignoreUnknownKeys = true }
        val alteMarke = json.decodeFromString(PriceAlert.serializer(), "{" + q("id", "x1") + "," + q("symbol", "AAPL") + "," + q("name", "Apple") + ",\"target\":180.0,\"above\":true}")
        assertEquals(Wertglocke.MARKE, Wertglocke.artVon(alteMarke))
        assertEquals("", alteMarke.art)
        val alteBewegung = json.decodeFromString(
            PriceAlert.serializer(),
            "{" + q("id", "x2") + "," + q("symbol", "AAPL") + "," + q("name", "Apple") + ",\"target\":0.0,\"above\":true,\"tagesProzent\":3.5,\"gemeldetAm\":19000}",
        )
        assertEquals(Wertglocke.BEWEGUNG, Wertglocke.artVon(alteBewegung))
        assertEquals(19_000L, alteBewegung.gemeldetAm)
        val alteArt = json.decodeFromString(
            PriceAlert.serializer(),
            "{" + q("id", "x3") + "," + q("symbol", "AAPL") + "," + q("name", "Apple") + ",\"target\":0.0,\"above\":true," + q("art", "INSIDER") + "," + q("merk", "2026-10-01|0001") + "}",
        )
        assertEquals(Alarmarten.INSIDER, Wertglocke.artVon(alteArt))
        assertTrue(Alarmarten.istArt(alteArt))
        assertEquals("2026-10-01|0001", alteArt.merk)
        // die alten Arten gibt es weiter, neue sind dazugekommen, keine doppelt
        for (art in listOf("SMA50", "VOL2", "EXDIV", "INSIDER")) assertTrue(art in Alarmarten.ALLE)
        assertEquals(Alarmarten.ALLE.size, Alarmarten.ALLE.toSet().size)
        // neu geschriebene Alarme lesen sich wieder ein
        val neu = alarm(Alarmarten.NEWS, merk = "5")
        assertEquals(neu, json.decodeFromString(PriceAlert.serializer(), json.encodeToString(PriceAlert.serializer(), neu)))
    }

    private fun q(k: String, v: String) = "\"" + k + "\":\"" + v + "\""
}
