package de.troetenrepublik.app.engine

import de.troetenrepublik.app.i18n.GeschichteTexte
import de.troetenrepublik.app.i18n.StandorteTexte
import de.troetenrepublik.app.market.FirmaGeschichte
import de.troetenrepublik.app.market.FirmaGeschichte.Art
import de.troetenrepublik.app.market.FirmaStandorte
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Standorte (Exhibit 21, Item 2, GLEIF) und Geschichte (Wikidata, SEC-Namen, Splits) einer Firma mit kurzen, selbst
// formulierten Auszuegen.
class FirmaStandorteGeschichteTest {

    private val a = FirmaStandorte.Aufloeser(
        listOf(
            FirmaStandorte.Land("US", "United States", "Vereinigte Staaten"), FirmaStandorte.Land("KY", "Cayman Islands", "Kaimaninseln"),
            FirmaStandorte.Land("GB", "United Kingdom", "Vereinigtes Königreich"), FirmaStandorte.Land("CH", "Switzerland", "Schweiz"),
            FirmaStandorte.Land("DE", "Germany", "Deutschland"), FirmaStandorte.Land("IE", "Ireland", "Irland"),
            FirmaStandorte.Land("BR", "Brazil", "Brasilien"), FirmaStandorte.Land("CN", "China", "China"),
            FirmaStandorte.Land("JP", "Japan", "Japan"), FirmaStandorte.Land("SG", "Singapore", "Singapur"),
        ),
    )

    @Test
    fun rechtsordnungenFindenIhrLand() {
        assertEquals("US", a.a2("Delaware"))
        assertEquals("US", a.a2("State of Delaware"))
        assertEquals("KY", a.a2("Cayman Islands"))
        assertEquals("GB", a.a2("England & Wales"))
        assertEquals("CH", a.a2("Zug, Switzerland"))
        assertEquals("DE", a.a2("Deutschland"))
        assertNull(a.a2("Apple Operations International"))
        assertNull(a.a2("Jurisdiction"))
    }

    @Test
    fun laenderImFliesstextNurMitVollemNamen() {
        val t = "We lease offices in Dublin, Ireland and Singapore. Our plants in China and Brazil are owned. Georgia and Jersey are states."
        assertEquals(setOf("IE", "SG", "CN", "BR"), a.genannt(t))
    }

    @Test
    fun tochterTabelleZaehltJeRechtsordnung() {
        val html = "<table><tr><td>Name</td><td>Jurisdiction</td></tr>" +
            "<tr><td>Alpha Operations International</td><td>Ireland</td></tr>" +
            "<tr><td>Alpha Cayman Ltd</td><td>Cayman Islands</td></tr>" +
            "<tr><td>Alpha Sales LLC</td><td>Delaware</td></tr>" +
            "<tr><td>Alpha Gamma Ltd</td><td>Ireland</td></tr></table>"
        val je = FirmaStandorte.tochterListe(html, a).groupingBy { it.a2 }.eachCount()
        assertEquals(mapOf("IE" to 2, "KY" to 1, "US" to 1), je)
    }

    @Test
    fun tochterListeMitAbschnittenUndLandInKlammern() {
        val html = "<table><tr><td>Brazil</td></tr><tr><td>Beta Ltda</td></tr><tr><td>Gamma SA</td></tr>" +
            "<tr><td>Germany</td></tr><tr><td>Delta GmbH</td></tr><tr><td>Epsilon Ltd (Japan)</td></tr></table>"
        val je = FirmaStandorte.tochterListe(html, a).groupingBy { it.a2 }.eachCount()
        assertEquals(2, je["BR"])
        assertEquals(1, je["DE"])
        assertEquals(1, je["JP"])
    }

    @Test
    fun tochterListeAusTextMitAbstaenden() {
        val text = "<pre>Alpha Ltd          Ireland\nBeta Pte Ltd         Singapore\n</pre>"
        val je = FirmaStandorte.tochterListe(text, a).groupingBy { it.a2 }.eachCount()
        assertEquals(mapOf("IE" to 1, "SG" to 1), je)
    }

    @Test
    fun listenDateiImVerzeichnis() {
        val index = "{\"directory\":{\"item\":[{\"name\":\"a10-k.htm\"},{\"name\":\"ex21.htm\"},{\"name\":\"ex231.htm\"}]}}"
        assertEquals("ex21.htm", FirmaStandorte.listenDatei(index, "10-K"))
        assertNull(FirmaStandorte.listenDatei("{\"directory\":{\"item\":[{\"name\":\"a10-k.htm\"}]}}", "10-K"))
        assertEquals("ex8.htm", FirmaStandorte.listenDatei("{\"directory\":{\"item\":[{\"name\":\"ex8.htm\"}]}}", "20-F"))
    }

    @Test
    fun item2NimmtDieEchteUeberschriftNichtDasInhaltsverzeichnis() {
        val text = "Our headquarters are in Cupertino, California. We lease offices in Ireland and Singapore, and own plants in China and Brazil. " +
            "Further sites are leased in several other locations around the world."
        val html = "<table><tr><td>Item 2.</td><td>Properties</td></tr><tr><td>Item 3.</td><td>Legal Proceedings</td></tr></table>" +
            "<div>Risk text see Item 3. Legal Proceedings below</div>" +
            "<div>Item 2. Properties</div><div>$text</div><div>Item 3. Legal Proceedings</div><div>None.</div>"
        val ergebnis = FirmaStandorte.item2Text(html)
        assertNotNull(ergebnis)
        assertTrue(ergebnis!!.startsWith("Our headquarters"))
        assertEquals(setOf("IE", "SG", "CN", "BR"), a.genannt(ergebnis))
        assertNull(FirmaStandorte.item2Text("<table><tr><td>Item 2.</td><td>Properties</td></tr><tr><td>Item 3.</td><td>Legal Proceedings</td></tr></table>"))
    }

    @Test
    fun gleifKinderJeLand() {
        val text = "{\"meta\":{\"pagination\":{\"total\":3,\"lastPage\":2}},\"data\":[" +
            "{\"attributes\":{\"entity\":{\"legalAddress\":{\"country\":\"IE\"}}}}," +
            "{\"attributes\":{\"entity\":{\"legalAddress\":{\"country\":\"IE\"}}}}," +
            "{\"attributes\":{\"entity\":{\"legalAddress\":{\"country\":\"IN\"}}}}]}"
        val k = FirmaStandorte.kinderLesen(text)!!
        assertEquals(mapOf("IE" to 2, "IN" to 1), k.laender)
        assertEquals(3, k.gesamt)
        assertEquals(2, k.letzteSeite)
    }

    @Test
    fun quellenWerdenJeLandZusammengefuehrtOhneDoppelzaehlung() {
        val d = FirmaStandorte.Daten(
            ex21 = mapOf("IE" to 5, "US" to 20), gleif = mapOf("IE" to 3, "IN" to 2), wikidata = mapOf("IN" to 4),
            item2 = listOf("CN", "JP"), sitz = "US",
        )
        assertEquals(mapOf("IE" to 5, "US" to 20, "IN" to 4), FirmaStandorte.toechter(d))
        assertEquals(setOf("IE", "US", "IN", "CN", "JP"), FirmaStandorte.standorte(d))
        assertEquals(setOf("CN", "JP", "US"), FirmaStandorte.standortLaender(d))
        // Ein einziges Land aus Item 2 zaehlt nicht
        assertEquals(setOf("IE", "US", "IN"), FirmaStandorte.standorte(FirmaStandorte.Daten(ex21 = mapOf("IE" to 5, "US" to 20, "IN" to 4), item2 = listOf("CN"))))
        assertTrue(FirmaStandorte.leer(FirmaStandorte.Daten(item2 = listOf("AU"))))
        assertTrue(FirmaStandorte.leer(FirmaStandorte.Daten()))
    }

    @Test
    fun risikoUndSteuergebiete() {
        val t = mapOf("US" to 10, "RU" to 5, "PA" to 5, "VU" to 2, "ZZ" to 3, "IN" to 4)
        val hoch = mapOf("US" to false, "RU" to true, "PA" to false, "VU" to true, "IN" to false)
        val r = FirmaStandorte.risiko(t, { hoch[it] })
        // Indien hat mittlere Regierungsfuehrung und gilt nicht als klar riskant
        assertEquals(26, r.tochterGesamt)
        assertEquals(7, r.tochterHoch)
        assertEquals(3, r.tochterOhneWert)
        assertEquals(listOf("RU", "VU"), r.laenderHoch)
        assertEquals(7, FirmaStandorte.steuerToechter(t))
    }

    @Test
    fun zeilenZuerstToechterDannReineStandorte() {
        val d = FirmaStandorte.Daten(ex21 = mapOf("IE" to 5, "US" to 20), item2 = listOf("CN", "IE"), sitz = "US")
        val z = FirmaStandorte.zeilen(d, { it }, { if (it == "CN") true else false })
        assertEquals(listOf("US", "IE", "CN"), z.map { it.a2 })
        assertTrue(z[0].sitz)
        assertTrue(z[1].standort)
        assertTrue(z[2].hoch)
        assertEquals(0, z[2].toechter)
    }

    @Test
    fun datenRundlauf() {
        val d = FirmaStandorte.Daten(5L, "US", mapOf("IE" to 1), emptyMap(), emptyMap(), listOf("CN"), "10-K", "2026-02-01", 7)
        val l = FirmaStandorte.lesen(FirmaStandorte.schreiben(d))!!
        assertEquals(mapOf("IE" to 1), l.ex21)
        assertEquals("10-K", l.bericht)
        assertEquals(7, l.gleifGesamt)
    }

    // ------------------------------------------------------------ Geschichte

    private val anspruch = Json.parseToJsonElement(
        "{\"P571\":[{\"mainsnak\":{\"datavalue\":{\"value\":{\"time\":\"+1976-04-01T00:00:00Z\",\"precision\":11}}}}]," +
            "\"P414\":[{\"mainsnak\":{\"datavalue\":{\"value\":{\"id\":\"Q82059\"}}},\"qualifiers\":{\"P580\":[{\"datavalue\":{\"value\":{\"time\":\"+1980-12-00T00:00:00Z\",\"precision\":10}}}]}}]," +
            "\"P169\":[{\"mainsnak\":{\"datavalue\":{\"value\":{\"id\":\"Q926972\"}}},\"qualifiers\":{\"P580\":[{\"datavalue\":{\"value\":{\"time\":\"+1977-01-01T00:00:00Z\",\"precision\":9}}}]}}," +
            "{\"mainsnak\":{\"datavalue\":{\"value\":{\"id\":\"Q999\"}}}}]," +
            "\"P159\":[{\"mainsnak\":{\"datavalue\":{\"value\":{\"id\":\"Q100\"}}},\"qualifiers\":{\"P580\":[{\"datavalue\":{\"value\":{\"time\":\"+1976-04-01T00:00:00Z\",\"precision\":11}}}]}},{\"mainsnak\":{\"datavalue\":{\"value\":{\"id\":\"Q189471\"}}},\"qualifiers\":{\"P580\":[{\"datavalue\":{\"value\":{\"time\":\"+1993-00-00T00:00:00Z\",\"precision\":9}}}]}}]," +
            "\"P355\":[{\"mainsnak\":{\"datavalue\":{\"value\":{\"id\":\"Q42\"}}},\"qualifiers\":{\"P580\":[{\"datavalue\":{\"value\":{\"time\":\"+2014-05-28T00:00:00Z\",\"precision\":11}}}]}}]}",
    ).jsonObject

    private val namen = mapOf("Q82059" to "Nasdaq", "Q926972" to "Mike Scott", "Q189471" to "Cupertino", "Q42" to "Beats", "Q100" to "Los Altos", "Q999" to "Ohne Datum")

    @Test
    fun zeitMitGenauigkeit() {
        val e = FirmaGeschichte.ausWikidata(anspruch, "Q312") { namen[it] }
        assertEquals("1976-04-01", e.first { it.art == Art.GRUENDUNG }.tag)
        assertEquals("1980-12", e.first { it.art == Art.BOERSENGANG }.tag)
        assertEquals("Nasdaq", e.first { it.art == Art.BOERSENGANG }.a)
        assertEquals("1977", e.first { it.art == Art.CEO }.tag)
        assertEquals("1993", e.first { it.art == Art.SITZ }.tag)
        assertEquals("Cupertino", e.first { it.art == Art.SITZ }.a)
        // Der erste Sitz hat zwar ein Startdatum, ist aber kein Umzug
        assertEquals(1, e.count { it.art == Art.SITZ })
        assertEquals("Beats", e.first { it.art == Art.NEUE_TOCHTER }.a)
        // Ohne Zeitangabe kein Eintrag
        assertEquals(1, e.count { it.art == Art.CEO })
    }

    @Test
    fun einzelnerSitzMitStartdatumIstKeinUmzug() {
        val nur = Json.parseToJsonElement(
            "{\"P159\":[{\"mainsnak\":{\"datavalue\":{\"value\":{\"id\":\"Q189471\"}}},\"qualifiers\":{\"P580\":[{\"datavalue\":{\"value\":{\"time\":\"+2018-00-00T00:00:00Z\",\"precision\":9}}}]}}]}",
        ).jsonObject
        assertEquals(0, FirmaGeschichte.ausWikidata(nur, "Q312") { namen[it] }.count { it.art == Art.SITZ })
        // Derselbe Ort zweimal ist ebenfalls keiner
        val doppelt = Json.parseToJsonElement(
            "{\"P159\":[{\"mainsnak\":{\"datavalue\":{\"value\":{\"id\":\"Q189471\"}}},\"qualifiers\":{\"P580\":[{\"datavalue\":{\"value\":{\"time\":\"+1993-00-00T00:00:00Z\",\"precision\":9}}}]}}," +
                "{\"mainsnak\":{\"datavalue\":{\"value\":{\"id\":\"Q189471\"}}},\"qualifiers\":{\"P580\":[{\"datavalue\":{\"value\":{\"time\":\"+2018-00-00T00:00:00Z\",\"precision\":9}}}]}}]}",
        ).jsonObject
        assertEquals(0, FirmaGeschichte.ausWikidata(doppelt, "Q312") { namen[it] }.count { it.art == Art.SITZ })
    }

    @Test
    fun benoetigteKennungenNennenNurDatierteAussagen() {
        val ids = FirmaGeschichte.benoetigteKennungen(anspruch, "Q312")
        assertTrue("Q82059" in ids && "Q42" in ids && "Q926972" in ids)
        assertTrue("Q999" !in ids)
    }

    @Test
    fun fruehereNamenAusDerSecListe() {
        val subs = "{\"name\":\"Alpha Inc.\",\"formerNames\":[{\"name\":\"Alpha Computer Inc\",\"from\":\"1990-01-01T00:00:00.000Z\",\"to\":\"2007-01-09T00:00:00.000Z\"}," +
            "{\"name\":\"Beta Corp\",\"from\":\"1980-01-01T00:00:00.000Z\",\"to\":\"1989-12-31T00:00:00.000Z\"}]}"
        val e = FirmaGeschichte.secName(subs, "0000320193")
        assertEquals(2, e.size)
        assertEquals("1989-12-31", e[0].tag)
        assertEquals("Beta Corp", e[0].a)
        assertEquals("Alpha Computer Inc", e[0].b)
        assertEquals("2007-01-09", e[1].tag)
        assertEquals("Alpha Inc.", e[1].b)
    }

    @Test
    fun wikidataNameEntfaelltNebenSecName() {
        val sec = listOf(FirmaGeschichte.Ereignis(Art.NAME, "2007-01-09", "A", "B"))
        val wd = listOf(
            FirmaGeschichte.Ereignis(Art.NAME, "2007", "A", "B"), FirmaGeschichte.Ereignis(Art.GRUENDUNG, "1976-04-01"),
            FirmaGeschichte.Ereignis(Art.BOERSENGANG, "1980-12", "Nasdaq"),
        )
        val z = FirmaGeschichte.zusammenfuehren(wd, sec)
        assertEquals(listOf(Art.NAME, Art.BOERSENGANG, Art.GRUENDUNG), z.map { it.art })
        assertEquals("2007-01-09", z[0].tag)
        val k = FirmaGeschichte.kern(z)
        assertEquals(1976, k.gegruendet)
        assertEquals(1980, k.boersengang)
        assertEquals(0, k.uebernahmen)
    }

    @Test
    fun splitsAlsEreignisse() {
        val e = FirmaGeschichte.ausSplits(
            listOf(
                de.troetenrepublik.app.engine.Splits.Split(18_500L * 86_400_000L, 4.0, 1.0),
                de.troetenrepublik.app.engine.Splits.Split(19_000L * 86_400_000L, 1.0, 10.0),
            ),
        )
        assertEquals("4:1", e[0].a)
        assertEquals(10, e[0].tag.length)
        assertEquals("", e[0].b)
        assertEquals("REVERSE", e[1].b)
    }

    @Test
    fun geschichteRundlauf() {
        val d = FirmaGeschichte.Daten(3L, "Q1", listOf(FirmaGeschichte.Ereignis(Art.SITZ, "1993", "Cupertino", link = "https://www.wikidata.org/wiki/Q1")))
        val l = FirmaGeschichte.lesen(FirmaGeschichte.schreiben(d))!!
        assertEquals(Art.SITZ, l.ereignisse[0].art)
        assertEquals("Cupertino", l.ereignisse[0].a)
    }

    // ------------------------------------------------------------ Texte

    @Test
    fun textehabenInBeidenSprachenDieselbenSchluessel() {
        assertEquals(StandorteTexte.schluessel(true), StandorteTexte.schluessel(false))
        assertEquals(GeschichteTexte.schluessel(true), GeschichteTexte.schluessel(false))
        for (de in listOf(true, false)) {
            for (k in StandorteTexte.schluessel(de)) assertTrue(k, StandorteTexte.text(de, k).isNotBlank() && !StandorteTexte.text(de, k).contains("—"))
            for (k in GeschichteTexte.schluessel(de)) assertTrue(k, GeschichteTexte.text(de, k).isNotBlank() && !GeschichteTexte.text(de, k).contains("—"))
        }
    }

    @Test
    fun jedeArtHatEinenText() {
        for (art in Art.entries) {
            if (art == Art.FUEHRUNG) continue
            assertTrue(art.name, GeschichteTexte.schluessel(true).contains(art.name))
        }
    }
}
