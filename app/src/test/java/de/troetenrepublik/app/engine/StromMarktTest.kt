package de.troetenrepublik.app.engine

import de.troetenrepublik.app.i18n.StromTexte
import de.troetenrepublik.app.market.Abrufkontingent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

// Strom in Europa: Leser der ENTSO-E-Antworten (XML) und der SMARD-Antworten (JSON), Tageswerte, Einordnung und Ablage,
// ohne Netz mit kleinen selbst geschriebenen Beispielen.
class StromMarktTest {

    private fun tag(s: String) = LocalDate.parse(s).toEpochDay()
    private fun ms(s: String) = tag(s.substring(0, 10)) * 86_400_000L + s.substring(11, 13).toLong() * 3_600_000L + s.substring(14, 16).toLong() * 60_000L

    // Publication_MarketDocument mit einer Zeitreihe je Eintrag: (Beginn, Ende, Aufloesung, Punkte als Position zu Preis)
    private class Reihe(val start: String, val ende: String, val aufloesung: String, val punkte: Map<Int, Double>)

    private fun dokument(vararg reihen: Reihe): String {
        val sb = StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<Publication_MarketDocument xmlns=\"urn:iec62325.351:tc57wg16:451-3:publicationdocument:7:3\">\n<mRID>x</mRID>\n")
        for (r in reihen) {
            sb.append("<TimeSeries>\n<mRID>1</mRID>\n<curveType>A01</curveType>\n<Period>\n")
            sb.append("<timeInterval>\n<start>${r.start}</start>\n<end>${r.ende}</end>\n</timeInterval>\n<resolution>${r.aufloesung}</resolution>\n")
            for ((p, v) in r.punkte) sb.append("<Point>\n<position>$p</position>\n<price.amount>$v</price.amount>\n</Point>\n")
            sb.append("</Period>\n</TimeSeries>\n")
        }
        return sb.append("</Publication_MarketDocument>").toString()
    }

    private fun stunden(werte: List<Double>) = werte.mapIndexed { i, v -> i + 1 to v }.toMap()

    // ------------------------------------------------------------ ENTSO-E

    @Test fun stuendlicherTagGibtMittelTiefUndHoch() {
        val preise = (0 until 24).map { if (it < 12) 50.0 else 100.0 }
        val xml = dokument(Reihe("2026-11-12T23:00Z", "2026-11-13T23:00Z", "PT60M", stunden(preise)))
        val tage = StromMarkt.preiseLesen(xml)
        assertEquals(1, tage.size)
        // Beginn 23:00 UTC ist Mitternacht in Mitteleuropa: Liefertag ist der 13.
        assertEquals(tag("2026-11-13"), tage[0].tag)
        assertEquals(75.0, tage[0].mittel, 1e-9)
        assertEquals(50.0, tage[0].min, 1e-9)
        assertEquals(100.0, tage[0].max, 1e-9)
    }

    @Test fun viertelstundenWerdenZumTagesmittel() {
        val preise = (0 until 96).map { if (it < 48) 20.5 else -10.5 }
        val xml = dokument(Reihe("2026-06-14T22:00Z", "2026-06-15T22:00Z", "PT15M", stunden(preise)))
        val t = StromMarkt.preiseLesen(xml).single()
        assertEquals(tag("2026-06-15"), t.tag)
        assertEquals(5.0, t.mittel, 1e-9)
        assertEquals(-10.5, t.min, 1e-9)
    }

    @Test fun fehlendePositionenWiederholenDenVorigenPreis() {
        // Kurventyp A03: Position 2 und 3 fehlen und gelten wie Position 1, Position 5 bis 24 wie 4
        val xml = dokument(Reihe("2026-11-12T23:00Z", "2026-11-13T23:00Z", "PT60M", mapOf(1 to 10.0, 4 to 30.0)))
        val t = StromMarkt.preiseLesen(xml).single()
        assertEquals((3 * 10.0 + 21 * 30.0) / 24, t.mittel, 1e-9)
    }

    @Test fun zeitumstellungTagHatNurEinenLiefertag() {
        // 25 Stunden am 26.10.2025 (Mitternacht MESZ = 22:00 UTC am 25.)
        val xml = dokument(Reihe("2025-10-25T22:00Z", "2025-10-26T23:00Z", "PT60M", stunden(List(25) { 40.0 })))
        val tage = StromMarkt.preiseLesen(xml)
        assertEquals(1, tage.size)
        assertEquals(tag("2025-10-26"), tage[0].tag)
    }

    @Test fun mehrtaegigeZeitreiheWirdInTageGeteilt() {
        val werte = (0 until 72).map { if (it < 24) 10.0 else if (it < 48) 20.0 else 30.0 }
        val xml = dokument(Reihe("2026-11-09T23:00Z", "2026-11-12T23:00Z", "PT60M", stunden(werte)))
        val tage = StromMarkt.preiseLesen(xml)
        assertEquals(listOf(10.0, 20.0, 30.0), tage.map { it.mittel })
        assertEquals(listOf(tag("2026-11-10"), tag("2026-11-11"), tag("2026-11-12")), tage.map { it.tag })
    }

    @Test fun mehrereTageUndFeinereAufloesungGewinnt() {
        val a = Reihe("2026-11-11T23:00Z", "2026-11-12T23:00Z", "PT60M", stunden(List(24) { 10.0 }))
        val b = Reihe("2026-11-12T23:00Z", "2026-11-13T23:00Z", "PT60M", stunden(List(24) { 20.0 }))
        // Derselbe Tag nochmals viertelstuendlich mit anderem Mittel: die feinere Reihe gilt
        val c = Reihe("2026-11-12T23:00Z", "2026-11-13T23:00Z", "PT15M", stunden(List(96) { 25.0 }))
        val tage = StromMarkt.preiseLesen(dokument(a, b, c))
        assertEquals(2, tage.size)
        assertEquals(10.0, tage[0].mittel, 1e-9)
        assertEquals(25.0, tage[1].mittel, 1e-9)
    }

    @Test fun absageUndUnlesbaresGebenKeineTage() {
        val ack = "<Acknowledgement_MarketDocument><Reason><code>999</code><text>No matching data found for Data item Day-ahead Prices</text></Reason></Acknowledgement_MarketDocument>"
        assertTrue(StromMarkt.preiseLesen(ack).isEmpty())
        val a = StromMarkt.absage(ack)
        assertNotNull(a)
        assertEquals(999, a!!.first)
        assertTrue(a.second.startsWith("No matching data"))
        assertNull(StromMarkt.absage(dokument(Reihe("2026-11-12T23:00Z", "2026-11-13T23:00Z", "PT60M", stunden(List(24) { 1.0 })))))
        assertTrue(StromMarkt.preiseLesen("<html>Bot</html>").isEmpty())
        assertTrue(StromMarkt.preiseLesen("").isEmpty())
        // Zeitreihe ohne Aufloesung oder ohne Punkte
        assertTrue(StromMarkt.preiseLesen(dokument(Reihe("2026-11-12T23:00Z", "2026-11-13T23:00Z", "P1Y", stunden(List(24) { 1.0 })))).isEmpty())
        assertTrue(StromMarkt.preiseLesen(dokument(Reihe("2026-11-12T23:00Z", "2026-11-13T23:00Z", "PT60M", emptyMap()))).isEmpty())
    }

    @Test fun zeitUndDauerWerdenGelesen() {
        assertEquals(ms("2026-11-12T23:00"), StromMarkt.zeitLesen("2026-11-12T23:00Z"))
        assertEquals(ms("2026-11-12T23:30"), StromMarkt.zeitLesen("2026-11-12T23:30:00Z"))
        assertNull(StromMarkt.zeitLesen("12.11.2026"))
        assertEquals(3_600_000L, StromMarkt.dauerLesen("PT60M"))
        assertEquals(900_000L, StromMarkt.dauerLesen("PT15M"))
        assertEquals(3_600_000L, StromMarkt.dauerLesen("PT1H"))
        assertNull(StromMarkt.dauerLesen("P1D"))
    }

    @Test fun adresseTraegtZoneZeitraumUndToken() {
        val z = StromMarkt.zone("de")!!
        val url = StromMarkt.preisAdresse(z, LocalDate.of(2026, 10, 12), LocalDate.of(2026, 11, 15), " abc-DEF_123456 ")
        assertEquals(
            "https://web-api.tp.entsoe.eu/api?securityToken=abc-DEF_123456&documentType=A44&in_Domain=10Y1001A1001A82H" +
                "&out_Domain=10Y1001A1001A82H&periodStart=202610120000&periodEnd=202611150000",
            url,
        )
        assertEquals("strom", Quellenbuendel.quelleZu(java.net.URI(url).host))
        assertEquals("strom", Quellenbuendel.quelleZu("www.smard.de"))
    }

    @Test fun tokenNurAusErlaubtenZeichen() {
        assertTrue(StromMarkt.tokenGueltig("beispiel-token-0123456789"))
        assertFalse(StromMarkt.tokenGueltig("kurz"))
        assertFalse(StromMarkt.tokenGueltig("abc def ghi jkl"))
        assertFalse(StromMarkt.tokenGueltig("abcdefgh&documentType=X"))
        assertFalse(StromMarkt.tokenGueltig(""))
    }

    @Test fun zonenSindEindeutigUndVollstaendig() {
        assertEquals(StromMarkt.ZONEN.size, StromMarkt.ZONEN.map { it.a2 }.toSet().size)
        assertEquals(StromMarkt.ZONEN.size, StromMarkt.ZONEN.map { it.eic }.toSet().size)
        assertTrue(StromMarkt.ZONEN.all { it.eic.length == 16 && it.eic.startsWith("10Y") })
        for (a2 in listOf("DE", "FR", "NL", "ES", "IT", "PL", "NO", "SE")) assertNotNull(a2, StromMarkt.zone(a2))
        assertNull(StromMarkt.zone("US"))
    }

    // ------------------------------------------------------------ Einordnung und Ablage

    private fun t(tag: Long, mittel: Double) = StromMarkt.Tag(tag, mittel, mittel - 5, mittel + 5)

    @Test fun lageVergleichtMitVortagUndSchnittUndKenntMorgen() {
        val heute = tag("2026-11-13")
        val tage = (1..10).map { t(heute - 10 + it - 1, 100.0) } + t(heute, 120.0) + t(heute + 1, 90.0)
        val l = StromMarkt.lage(tage, heute)!!
        assertEquals(heute, l.tag.tag)
        assertEquals(20.0, l.gegenVortag!!, 1e-9)
        assertEquals(20.0, l.gegenSchnitt!!, 1e-9)
        assertEquals(90.0, StromMarkt.morgen(tage, heute)!!.mittel, 1e-9)
    }

    @Test fun lageOhneVortagUndMitWenigenTagenLiefertNurDenWert() {
        val heute = tag("2026-11-13")
        val l = StromMarkt.lage(listOf(t(heute - 3, 80.0), t(heute, 100.0)), heute)!!
        assertNull(l.gegenVortag)
        assertNull(l.gegenSchnitt)
        assertNull(StromMarkt.lage(listOf(t(heute + 1, 80.0)), heute))
        assertNull(StromMarkt.lage(emptyList(), heute))
    }

    @Test fun lageUeberNullPreisGibtKeinProzent() {
        val heute = tag("2026-11-13")
        val tage = (1..8).map { t(heute - 9 + it, 0.2) } + t(heute, 50.0)
        val l = StromMarkt.lage(tage, heute)!!
        assertNull(l.gegenVortag)
        assertNull(l.gegenSchnitt)
    }

    @Test fun tageUeberlebenDasAblegenUndNeueErsetzenAlte() {
        val alt = listOf(t(100, 10.0), t(101, 11.0))
        val gelesen = StromMarkt.tageLaden(StromMarkt.tageSpeichern(alt))
        assertEquals(alt.map { it.mittel }, gelesen.map { it.mittel })
        assertEquals(alt.map { it.tag }, gelesen.map { it.tag })
        val neu = StromMarkt.zusammenfuehren(gelesen, listOf(t(101, 99.0), t(102, 12.0)))
        assertEquals(listOf(100L, 101L, 102L), neu.map { it.tag })
        assertEquals(99.0, neu[1].mittel, 1e-9)
        assertEquals(3, StromMarkt.zusammenfuehren(neu, emptyList(), behalten = 3).size)
        assertEquals(listOf(101L, 102L), StromMarkt.zusammenfuehren(neu, emptyList(), behalten = 2).map { it.tag })
        // Beschaedigte Zeilen fallen allein weg
        assertEquals(1, StromMarkt.tageLaden("kaputt\n5;1.0;0.0;2.0\n6;x;1;2").size)
    }

    // ------------------------------------------------------------ SMARD

    @Test fun smardIndexUndReiheMitLuecken() {
        assertEquals(listOf(1000L, 2000L), StromMarkt.smardIndex("""{"timestamps":[2000,1000]}"""))
        assertTrue(StromMarkt.smardIndex("<html>").isEmpty())
        val r = StromMarkt.smardReihe("""{"meta_data":{"version":1,"created":5},"series": [[3600000,10.5],[7200000,null],[10800000,12.0],[14400000]]}""")
        assertEquals(listOf(3_600_000L to 10.5, 10_800_000L to 12.0), r)
        assertTrue(StromMarkt.smardReihe("").isEmpty())
        assertTrue(StromMarkt.smardReihe("""{"series":"x"}""").isEmpty())
    }

    @Test fun smardAdressen() {
        assertEquals("https://www.smard.de/app/chart_data/4068/DE/index_hour.json", StromMarkt.smardIndexAdresse(4068))
        assertEquals("https://www.smard.de/app/chart_data/410/DE/410_DE_hour_1794178800000.json", StromMarkt.smardBlockAdresse(410, 1794178800000L))
    }

    // 48 Stunden; jede Quelle liefert einen festen Wert
    private fun reihen(werte: Map<String, Double>, stunden: Int = 48, bis: Int = stunden): Map<String, List<Pair<Long, Double>>> =
        werte.mapValues { (_, w) -> (0 until bis).map { it * 3_600_000L to w } }

    private val BASIS = mapOf(
        "pv" to 10_000.0, "windOn" to 10_000.0, "windOff" to 0.0, "wasser" to 0.0, "biomasse" to 0.0, "sonstErneuerbar" to 0.0,
        "gas" to 10_000.0, "braunkohle" to 10_000.0, "steinkohle" to 0.0, "pumpspeicher" to 0.0, "sonstKonventionell" to 0.0,
    )

    @Test fun mixZeigtAnteileUndErneuerbareDerLetzten24Stunden() {
        val m = StromMarkt.mix(reihen(BASIS))!!
        assertEquals(50.0, m.erneuerbar, 1e-9)
        assertEquals(40_000.0, m.gesamtMw, 1e-9)
        assertEquals(47 * 3_600_000L, m.bis)
        assertEquals(25.0, m.anteile.first { it.schluessel == "pv" }.prozent, 1e-9)
        assertEquals(100.0, m.anteile.sumOf { it.prozent }, 1e-9)
        assertTrue(m.anteile.first().mw >= m.anteile.last().mw)
    }

    @Test fun mixEndetBeiDerJuengstenStundeMitAllenQuellen() {
        // Eine Quelle ist zwei Stunden weiter als die anderen: das Fenster endet bei der gemeinsamen Stunde
        val r = reihen(BASIS).toMutableMap()
        r["pv"] = r.getValue("pv") + listOf(48 * 3_600_000L to 10_000.0, 49 * 3_600_000L to 10_000.0)
        assertEquals(47 * 3_600_000L, StromMarkt.mix(r)!!.bis)
    }

    @Test fun mixBrauchtGenugQuellenUndStunden() {
        assertNull(StromMarkt.mix(emptyMap()))
        assertNull(StromMarkt.mix(reihen(BASIS.filterKeys { it in setOf("pv", "gas") })))
        assertNull(StromMarkt.mix(reihen(BASIS, stunden = 6)))
        assertNull(StromMarkt.mix(reihen(BASIS.mapValues { 0.0 })))
    }

    @Test fun erneuerbarVerlaufJeStunde() {
        val v = StromMarkt.erneuerbarVerlauf(reihen(BASIS), tage = 1)
        assertEquals(24, v.size)
        assertTrue(v.all { kotlin.math.abs(it.second - 50.0) < 1e-9 })
        assertTrue(StromMarkt.erneuerbarVerlauf(emptyMap()).isEmpty())
    }

    @Test fun verbrauchJetztUndMittel() {
        val r = (0 until 48).map { it * 3_600_000L to (if (it >= 24) 60_000.0 else 40_000.0) }
        val l = StromMarkt.last(r)!!
        assertEquals(60_000.0, l.jetzt, 1e-9)
        assertEquals(60_000.0, l.mittel24, 1e-9)
        assertNull(StromMarkt.last(r.take(5)))
        assertNull(StromMarkt.last(emptyList()))
    }

    @Test fun smardAblageUeberlebtDasSpeichern() {
        val r = mapOf("pv" to listOf(1L to 2.5, 2L to 3.5), "last" to listOf(1L to 50_000.0))
        assertEquals(r, StromMarkt.smardLaden(StromMarkt.smardSpeichern(r)))
        assertTrue(StromMarkt.smardLaden("").isEmpty())
    }

    // ------------------------------------------------------------ Texte und Kontingent

    @Test fun beideSprachenHabenDieselbenSchluessel() {
        assertEquals(StromTexte.schluessel(true), StromTexte.schluessel(false))
        for (k in StromTexte.schluessel(true)) {
            assertTrue(k, StromTexte.text(true, k).isNotBlank())
            assertTrue(k, StromTexte.text(false, k).isNotBlank())
        }
        for (e in StromMarkt.ERZEUGER) assertTrue(e.schluessel, StromTexte.text(true, "q." + e.schluessel).isNotBlank())
    }

    @Test fun texteOhneGedankenstrich() {
        for (de in listOf(true, false)) for (k in StromTexte.schluessel(de)) {
            val x = StromTexte.text(de, k)
            assertFalse(k, x.contains('—') || x.contains('–'))
        }
    }

    @Test fun kontingentFuerEntsoeLiegtUnterDerGrenzeDerPlattform() {
        val r = Abrufkontingent.REGELN.getValue(Abrufkontingent.ENTSOE)
        assertTrue(r.grenzen.any { it.fensterMs == 60_000L && it.menge < 400 })
    }
}
