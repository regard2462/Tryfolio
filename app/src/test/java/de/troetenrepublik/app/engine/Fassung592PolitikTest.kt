package de.troetenrepublik.app.engine

import de.troetenrepublik.app.market.Personenbilder
import de.troetenrepublik.app.market.Personeninfo
import de.troetenrepublik.app.market.Politik
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

// Fassung 5.92: Rangliste mit gespeichertem Stand, Partei und Ausschuesse der Abgeordneten, Vermoegen des
// Praesidenten, Bilder weiterer Regierungsmitglieder, Dateinamen fuer PDF-Downloads.
class Fassung592PolitikTest {
    private fun asset(name: String) = listOf("src/main/assets/$name", "../app/src/main/assets/$name").map { File(it) }.first { it.exists() }

    @Test fun ranglisteSpeichern() {
        val r = listOf(
            KongressAuswertung.Rang("k:Pelosi|Nancy|CA", "Nancy Pelosi", 'H', 7, 0.123, 0.71, 2_500_000.0),
            KongressAuswertung.Rang("n:Sanders|Bernard", "Bernard Sanders", 'S', 3, -0.04, 0.33, 45_000.0),
        )
        val text = RanglisteSpeicher.schreiben(1_800_000_000_000L, r)
        val (am, zurueck) = RanglisteSpeicher.lesen(text)!!
        assertEquals(1_800_000_000_000L, am)
        assertEquals(r, zurueck)
        assertNull(RanglisteSpeicher.lesen("kaputt"))
        assertNull(RanglisteSpeicher.lesen("""{"format":2,"am":1,"raenge":[]}"""))
        assertTrue(RanglisteSpeicher.veraltet(0L, 7 * 3_600_000L))
        assertTrue(!RanglisteSpeicher.veraltet(3_600_000L, 5 * 3_600_000L))
        // Uhr zurueckgestellt: lieber neu rechnen
        assertTrue(RanglisteSpeicher.veraltet(10 * 3_600_000L, 3_600_000L))
    }

    @Test fun personeninfo() {
        val t = """{"stand":"2026-10-28","p":{"P000197":{"pa":"D","k":"rep","st":"CA","d":11,"seit":"1987-01-06","a":[["Financial Services","Ranking Member"],["Budget",""]]}},"x":{"bessent-scott":{"pa":"R"}}}"""
        val d = Personeninfo.lesen(t)!!
        val i = d.p.getValue("P000197")
        assertEquals("D", i.pa)
        assertEquals(1987, i.seitJahr)
        assertEquals(listOf("Financial Services" to "Ranking Member", "Budget" to ""), i.ausschuesse)
        assertEquals("R", d.x.getValue("bessent-scott").pa)
        assertEquals("California", Personeninfo.STAATEN["CA"])
        // Mitgelieferte Datei: alle Abgeordneten und Senatoren mit Partei, Kammer und Staat
        val echt = Personeninfo.lesen(asset("copytrading/personen.json").readText())!!
        assertTrue(echt.p.size > 500)
        assertTrue(echt.p.values.all { it.pa in setOf("R", "D", "I", "L") && it.k in setOf("rep", "sen") && it.st.length == 2 })
        assertEquals("sen", echt.p.getValue("S000033").k)
    }

    @Test fun bioguideUeberBilder() {
        val b = Personenbilder(
            kongressFoto = "https://example.org/{id}.jpg",
            haus = mapOf("CA11|pelosi" to "P000197", "CA|pelosi" to "P000197"),
            senat = mapOf("sanders|bernard" to "S000033", "sanders" to "S000033"),
        )
        assertEquals("P000197", b.hausId("Pelosi", "CA11", "Nancy"))
        assertEquals("P000197", b.hausId("Pelosi", "CA", "Nancy"))
        assertEquals("S000033", b.senatorId("Sanders", "Bernie"))
        assertNull(b.hausId("Pelosi", "NY12"))
        assertEquals("https://example.org/P000197.jpg", b.haus("Pelosi", "CA11")!!.url)
    }

    @Test fun regierungMitVermoegenUndBildern() {
        val r = Politik.regierung(asset("copytrading/regierung.json").readText())!!
        val v = r.personen.single { it.id == "trump" }.vermoegen
        assertNotNull(v)
        v!!
        assertTrue(v.j >= 2025)
        assertTrue(v.av > 1_000_000_000L && v.ab >= v.av)
        assertTrue(v.nettoVon > 0 && v.nettoBis > v.nettoVon)
        assertNull(r.personen.single { it.id == "vance" }.vermoegen)
        // Bilder weiterer Regierungsmitglieder: freie Lizenz, Datei vorhanden
        val bilder = Personenbilder.lesen(asset("copytrading/bilder.json").readText())!!
        val regierung = bilder.personen.filterKeys { it.contains('-') }
        assertTrue(regierung.size >= 10)
        regierung.values.forEach { f ->
            assertTrue(f.lizenz, f.lizenz == "Public domain" || f.lizenz.startsWith("CC"))
            assertTrue(f.lokal, asset(f.lokal).length() in 2_000..60_000)
        }
    }

    @Test fun pdfDateiname() {
        assertEquals(
            "Donald-J-Trump.09.17.2026-278T.pdf",
            Politik.dateiname("https://extapps2.oge.gov/201/Presiden.nsf/PAS+Index/0BDCCCE7DE13AEFA85258E8A002DE2B5/\$FILE/Donald-J-Trump.09.17.2026-278T.pdf"),
        )
        assertEquals("Vance, JD 2025 Annual 278.pdf", Politik.dateiname("https://extapps2.oge.gov/x/\$FILE/Vance,%20JD%202025%20Annual%20278.pdf"))
        assertEquals("20012345.pdf", Politik.dateiname("https://disclosures-clerk.house.gov/public_disc/ptr-pdfs/2026/20012345.pdf"))
        assertEquals("a_b.pdf", Politik.dateiname("https://x.org/a:b?x=1"))
    }
}
