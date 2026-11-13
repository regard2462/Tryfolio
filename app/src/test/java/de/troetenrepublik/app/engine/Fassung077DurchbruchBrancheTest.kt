package de.troetenrepublik.app.engine

import de.troetenrepublik.app.market.Rss
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Fassung077DurchbruchBrancheTest {

    private val jetzt = Rss.zeit("Sat, 07 Nov 2026 00:00:00 GMT")
    private val symbole = Branchentrends.BRANCHEN.map { it.symbol }.toSet()

    private fun meldung(thema: String, titel: String, zeit: Long) = Durchbrueche.Meldung(thema, titel, "https://x/$titel", zeit, "Quelle")
    private fun erwartet(bis: String, art: String = "") = Durchbrueche.Eintrag(de = "Ereignis $bis", en = "Event $bis", bis = bis, art = art)

    private fun block(id: String, neu: List<Durchbrueche.Meldung> = emptyList(), e: List<Durchbrueche.Eintrag> = emptyList()) =
        Durchbrueche.Block(Durchbrueche.thema(id)!!, neu, Durchbrueche.ThemaInhalt(id, e = e), jetzt)

    @Test
    fun zuordnungNurMitExistierendenBranchen() {
        val ids = Durchbrueche.THEMEN.map { it.id }.toSet()
        Durchbrueche.THEMEN.forEach { t ->
            assertTrue(t.id in ids)
            Durchbrueche.branchen(t).forEach { assertTrue("${t.id}: $it", it in symbole) }
        }
        // Jede Branche mit Themen: Kuerzel steht in den Fonds des Themas
        Branchentrends.BRANCHEN.forEach { b ->
            Durchbrueche.THEMEN.filter { b.symbol in Durchbrueche.branchen(it) }.forEach { assertTrue(b.symbol in it.etfs) }
        }
    }

    @Test
    fun zuordnungKonkret() {
        assertEquals(listOf("SMH"), Durchbrueche.branchen(Durchbrueche.thema("halbleiter")!!))
        assertEquals(listOf("XBI"), Durchbrueche.branchen(Durchbrueche.thema("krebs")!!))
        assertEquals(listOf("ICLN"), Durchbrueche.branchen(Durchbrueche.thema("energie")!!))
        assertEquals(emptyList<String>(), Durchbrueche.branchen(Durchbrueche.thema("quanten")!!))
        assertEquals(emptyList<String>(), Durchbrueche.branchen(Durchbrueche.thema("raumfahrt")!!))
    }

    @Test
    fun meldungVorErwartetemEreignis() {
        val neu = listOf(meldung("halbleiter", "neu", jetzt - 1000), meldung("halbleiter", "alt", jetzt - 5000))
        val z = Durchbrueche.brancheZeilen("SMH", listOf(block("halbleiter", neu, listOf(erwartet("2027-03")))))
        assertEquals(1, z.size)
        assertEquals("neu", z[0].meldung?.titel)
        assertNull(z[0].erwartet)
        assertEquals(2, z[0].neu)
    }

    @Test
    fun ohneMeldungDasNaechsteErwartete() {
        val e = listOf(erwartet("2027-09"), erwartet("2027-03"), erwartet("2027-06", Durchbrueche.ART_ANGEKUENDIGT))
        val z = Durchbrueche.brancheZeilen("SMH", listOf(block("halbleiter", e = e)))
        assertEquals(1, z.size)
        assertNull(z[0].meldung)
        // Angekuendigte zuerst, danach nach Ablaufmonat
        assertEquals("2027-06", z[0].erwartet?.bis)
        assertEquals(0, z[0].neu)
    }

    @Test
    fun abgelaufenesZaehltNicht() {
        val z = Durchbrueche.brancheZeilen("SMH", listOf(block("halbleiter", e = listOf(erwartet("2026-01")))))
        assertTrue(z.isEmpty())
    }

    @Test
    fun leereFaelle() {
        assertTrue(Durchbrueche.brancheZeilen("SMH", emptyList()).isEmpty())
        // Branche ohne zugeordnetes Thema
        val b = block("halbleiter", listOf(meldung("halbleiter", "x", jetzt)))
        assertTrue(Durchbrueche.brancheZeilen("XLF", listOf(b)).isEmpty())
        assertTrue(Durchbrueche.brancheZeilen("GDX", listOf(b)).isEmpty())
        // Thema ohne Branche taucht nirgends auf
        val q = block("quanten", listOf(meldung("quanten", "q", jetzt)))
        Branchentrends.BRANCHEN.forEach { assertTrue(Durchbrueche.brancheZeilen(it.symbol, listOf(q)).isEmpty()) }
    }

    @Test
    fun mehrereThemenEinerBrancheBehaltenReihenfolge() {
        val a = block("krebs", listOf(meldung("krebs", "k", jetzt - 10)))
        val b = block("medizin", listOf(meldung("medizin", "m", jetzt - 5)))
        val z = Durchbrueche.brancheZeilen("XBI", listOf(b, a))
        assertEquals(listOf("medizin", "krebs"), z.map { it.thema.id })
        assertNotNull(z[0].meldung)
    }
}
