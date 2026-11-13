package de.troetenrepublik.app.engine

import de.troetenrepublik.app.engine.InsiderSeltenheit.Art
import de.troetenrepublik.app.market.SecInsider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

// [0.77 AD5] Seltene Insiderkaeufe: jeder juengere Kauf gegen die Kaeufe derselben Firma in den vorhandenen Daten.
class Fassung077Ad5SeltenTest {

    private val beginn = "2025-04-01"
    private val ende = "2026-09-30"

    private fun kauf(k: String, person: String, tag: String, wert: Double) =
        SecInsider.Geschaeft(a = "$k-$person-$tag", k = k, e = "$k Inc.", p = person, kauf = true, d = tag, f = tag, wert = wert)

    // Auszug aus dem mitgelieferten Stand (assets/copytrading/sec.json), gekuerzt
    private val auszug = """{"stand":"2026-09-30","geschaefte":[
        {"a":"0001628280-26-064003","k":"AFCG","e":"Advanced Flower Capital Inc.","c":"1822523","p":"Tannenbaum Leonard M","r":"A","kauf":true,"d":"2026-09-30","f":"2026-09-30","n":95397.0,"wert":344047.49,"nach":7109614.0,"preis":3.6065},
        {"a":"0001493152-26-045053","k":"ANIX","e":"Anixa Biosciences Inc","c":"715446","p":"Catelani Michael","r":"S","t":"President, COO, & CFO","kauf":true,"d":"2026-09-30","f":"2026-09-30","n":5500.0,"wert":15620.0,"nach":50000.0,"preis":2.84}]}"""

    private fun verkauf(k: String, person: String, tag: String) =
        SecInsider.Geschaeft(a = "$k-$person-$tag-v", k = k, e = "$k Inc.", p = person, kauf = false, d = tag, f = tag, wert = 80_000.0)

    @Test(timeout = 20_000)
    fun ersterKaufNenntDenLetztenKauf() {
        val l = listOf(kauf("XYZ", "A", "2025-06-02", 50_000.0), kauf("XYZ", "B", "2026-09-15", 800_000.0))
        val e = InsiderSeltenheit.fuerFirma(l, ende, beginn).single { it.art == Art.ERSTER }
        assertEquals("2025-06-02", e.seit)
        assertFalse(e.seitBeginn)
        assertEquals(800_000.0, e.betrag, 0.0)
        assertEquals(470, e.luecke)
        assertEquals(15, e.monate)
    }

    @Test(timeout = 20_000)
    fun ersterKaufBrauchtFruehereMeldungenDerFirma() {
        // ohne jede fruehere Meldung fehlt nur die Abdeckung
        assertTrue(InsiderSeltenheit.fuerFirma(listOf(kauf("NEU", "A", "2026-09-20", 900_000.0)), ende, beginn).isEmpty())
        // mit einem frueheren Verkauf gilt der Beginn der Daten, die Luecke zaehlt ab dort
        val g = InsiderSeltenheit.fuerFirma(listOf(verkauf("NEU", "B", "2025-08-01"), kauf("NEU", "A", "2026-09-20", 900_000.0)), ende, beginn).single()
        assertTrue(g.seitBeginn)
        assertEquals(LocalDate.parse("2026-09-20").toEpochDay() - LocalDate.parse(beginn).toEpochDay(), g.luecke.toLong())
    }

    @Test(timeout = 20_000)
    fun groessterKaufNurMitDreifachemAbstand() {
        val l = listOf(
            kauf("GRO", "A", "2025-05-05", 300_000.0), kauf("GRO", "B", "2025-12-01", 400_000.0), kauf("GRO", "C", "2026-07-01", 100_000.0),
            kauf("GRO", "D", "2026-09-10", 1_300_000.0),
        )
        val g = InsiderSeltenheit.fuerFirma(l, ende, beginn).single { it.art == Art.GROESSTER }
        assertEquals(1_300_000.0, g.betrag, 0.0)
        assertEquals(3.25, g.faktor, 1e-9)
        // nur das Doppelte reicht nicht
        val zwei = l.dropLast(1) + kauf("GRO", "D", "2026-09-10", 800_000.0)
        assertTrue(InsiderSeltenheit.fuerFirma(zwei, ende, beginn).none { it.art == Art.GROESSTER })
    }

    @Test(timeout = 20_000)
    fun kaeuferInEinerWocheBrauchenDrei() {
        val l = listOf(
            kauf("WOC", "A", "2025-10-01", 400_000.0), kauf("WOC", "B", "2025-10-03", 400_000.0),
            kauf("WOC", "C", "2026-09-22", 400_000.0), kauf("WOC", "D", "2026-09-24", 400_000.0), kauf("WOC", "E", "2026-09-28", 400_000.0),
        )
        val w = InsiderSeltenheit.fuerFirma(l, ende, beginn).single { it.art == Art.WOCHE }
        assertEquals(3, w.zahl)
        assertTrue(w.seitBeginn)
        assertEquals("2026-09-28", w.tag)
        // zwei Kaeufer reichen nicht
        assertTrue(InsiderSeltenheit.fuerFirma(l.dropLast(1), ende, beginn).none { it.art == Art.WOCHE })
        // gleiche Zahl schon im Februar: weniger als 365 Tage Abstand, keine Aussage
        val mit = l + listOf(kauf("WOC", "F", "2026-02-02", 400_000.0), kauf("WOC", "G", "2026-02-03", 400_000.0), kauf("WOC", "H", "2026-02-04", 400_000.0))
        assertTrue(InsiderSeltenheit.fuerFirma(mit, ende, beginn).none { it.art == Art.WOCHE })
    }

    @Test(timeout = 20_000)
    fun kleineUndAlteKaeufeZaehlenNicht() {
        val frueher = verkauf("KLE", "B", "2025-06-01")
        assertTrue(InsiderSeltenheit.fuerFirma(listOf(frueher, kauf("KLE", "A", "2026-09-20", 99_000.0)), ende, beginn).isEmpty())
        assertTrue(InsiderSeltenheit.fuerFirma(listOf(verkauf("ALT", "B", "2025-06-01"), kauf("ALT", "A", "2026-08-01", 900_000.0)), ende, beginn).isEmpty())
        assertTrue(InsiderSeltenheit.fuerFirma(listOf(verkauf("VER", "A", "2026-09-20")), ende, beginn).isEmpty())
        val l = listOf(kauf("OFT", "A", "2026-03-20", 800_000.0), kauf("OFT", "B", "2026-09-20", 800_000.0))
        assertTrue(InsiderSeltenheit.fuerFirma(l, ende, beginn).none { it.art == Art.ERSTER })
    }

    @Test(timeout = 20_000)
    fun listeSortiertNachSeltenheitMitEinerAussageJeFirma() {
        val l = listOf(
            kauf("AAA", "A", "2025-06-02", 600_000.0), kauf("AAA", "B", "2026-09-15", 900_000.0),
            verkauf("BBB", "A", "2025-05-01"), kauf("BBB", "A", "2026-09-20", 700_000.0),
            kauf("CCC", "A", "2025-09-10", 600_000.0), kauf("CCC", "B", "2026-09-20", 700_000.0),
        )
        val liste = InsiderSeltenheit.besteJeFirma(InsiderSeltenheit.funde(l, ende, beginn))
        assertEquals(listOf("BBB", "AAA", "CCC"), liste.map { it.symbol })
        assertEquals(liste.map { it.luecke }, liste.map { it.luecke }.sortedDescending())
    }

    @Test(timeout = 20_000)
    fun fondsSindKeineFirmen() {
        // echte Namen aus dem Bestand
        val fonds = listOf(
            "BPRE" to "Bluerock Private Real Estate Fund", "PMPEX" to "Primark Meketa Private Equity Investments Fund",
            "TPYTX" to "TCW Private Asset Income Fund", "GDV" to "Gabelli Dividend & Income Trust", "BFZ" to "Blackrock California Municipal Income Trust",
            "ACP" to "abrdn Income Credit Strategies Fund", "CCLFX" to "Cliffwater Corporate Lending Fund", "NFRX" to "2023 ETF Series Trust",
            "RMT" to "Royce Micro-Cap Trust, Inc.", "USO" to "United States Oil Fund, LP",
        )
        for ((k, n) in fonds) assertTrue("$k $n", InsiderSeltenheit.istFonds(k, n))
        val firmen = listOf(
            "ARCC" to "Ares Capital Corp", "BXSL" to "Blackstone Secured Lending Fund", "BBDC" to "Barings BDC, Inc.", "GBDC" to "GOLUB CAPITAL BDC, Inc.",
            "HR" to "Healthcare Realty Trust Inc", "ARR" to "Arbor Realty Trust Inc", "NTRS" to "Northern Trust Corp", "UHT" to "Universal Health Realty Income Trust",
            "GME" to "GameStop Corp.", "PINE" to "Alpine Income Property Trust, Inc.", "PBT" to "Permian Basin Royalty Trust", "AAPL" to "Apple Inc.",
        )
        for ((k, n) in firmen) assertFalse("$k $n", InsiderSeltenheit.istFonds(k, n))
        // ein Fondskauf erzeugt keine Aussage
        val f = SecInsider.Geschaeft(a = "f", k = "BPRE", e = "Bluerock Private Real Estate Fund", p = "A", kauf = true, d = "2026-09-20", f = "2026-09-20", wert = 9_000_000.0)
        assertTrue(InsiderSeltenheit.fuerFirma(listOf(f, verkauf("BPRE", "B", "2025-06-01")), ende, beginn).isEmpty())
    }

    @Test(timeout = 20_000)
    fun liestAuszugAusDemStand() {
        val d = SecInsider.lesen(auszug)
        assertNotNull(d)
        assertEquals(2, d!!.geschaefte.size)
        assertEquals("2026-09-30", InsiderSeltenheit.beginn(d.geschaefte))
    }

    @Test(timeout = 60_000)
    fun gebuendelteDatenGebenBelegbareAussagen() {
        val text = listOf("src/main/assets/copytrading/sec.json", "../app/src/main/assets/copytrading/sec.json").map { File(it) }.first { it.exists() }.readText()
        val d = SecInsider.lesen(text)!!
        val b = InsiderSeltenheit.beginn(d.geschaefte)!!
        val e = d.stand
        val funde = InsiderSeltenheit.besteJeFirma(InsiderSeltenheit.funde(d.geschaefte, e, b))
        assertTrue(funde.size in 20..60)
        val untergrenze = LocalDate.parse(e).minusDays(InsiderSeltenheit.NEU_TAGE.toLong()).toString()
        for (f in funde) {
            assertTrue(f.luecke >= InsiderSeltenheit.LUECKE_TAGE)
            assertTrue(f.betrag >= InsiderSeltenheit.MIN_BETRAG)
            assertTrue(f.tag > untergrenze && f.tag <= e)
            assertTrue(f.seit >= b && f.seit < f.tag)
            assertFalse(InsiderSeltenheit.istFonds(f.symbol, f.name))
        }
        println("Seltene Kaeufe: Daten ab $b, Stand $e, ${funde.size} Werte")
        funde.take(3).forEach { println("  ${it.symbol} ${it.art} seit ${it.seit} (Beginn ${it.seitBeginn}) Luecke ${it.luecke} Tage, ${it.betrag}, ${it.tag}") }
    }
}
