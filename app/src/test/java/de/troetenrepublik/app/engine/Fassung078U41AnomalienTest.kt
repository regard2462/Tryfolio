package de.troetenrepublik.app.engine

import de.troetenrepublik.app.i18n.MarktTexte
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

// Renditemuster vor und nach ihrer Veroeffentlichung: Rechnung, mitgelieferte Datei, Texte.
class Fassung078U41AnomalienTest {
    private fun asset(name: String) = listOf("src/main/assets/$name", "../app/src/main/assets/$name").map { File(it) }.first { it.exists() }

    private fun s(k: String, studie: Double, danach: Double, zwischen: Double? = null) =
        Anomalien.Signal(k, k, "A", 2000, 1970, 1995, studie, danach, 300, 300, zwischen, if (zwischen == null) 0 else 60)

    @Test
    fun auswertung() {
        val l = listOf(s("a", 1.0, 0.5, 0.8), s("b", 0.6, 0.7), s("c", 0.8, -0.3, 0.2))
        val a = Anomalien.auswerten(l)!!
        assertEquals(3, a.anzahl)
        assertEquals(0.8, a.studie, 1e-9)
        assertEquals(0.3, a.danach, 1e-9)
        // Verhaeltnis der Mittelwerte: 1 - 0,3 / 0,8
        assertEquals(62.5, a.rueckgang, 1e-9)
        assertEquals(2, a.schwaecher)
        assertEquals(1, a.negativ)
        assertEquals(200.0 / 3, a.anteilSchwaecher, 1e-9)
        // Die Zeit bis zur Veroeffentlichung zaehlt nur ueber die Signale, die solche Monate haben: 0,5 gegen 0,9
        assertEquals(0.5, a.zwischen!!, 1e-9)
        assertEquals((1 - 0.5 / 0.9) * 100, a.rueckgangZwischen!!, 1e-9)
        val ohne = Anomalien.auswerten(listOf(s("b", 0.6, 0.7)))!!
        assertNull(ohne.zwischen)
        assertNull(ohne.rueckgangZwischen)
        assertTrue(ohne.rueckgang < 0)
        assertNull(Anomalien.auswerten(emptyList()))
        assertNull(Anomalien.auswerten(listOf(s("x", -0.2, 0.1))))
    }

    @Test
    fun streubild() {
        val l = listOf(s("a", 1.0, 0.5), s("b", 0.6, 2.0), s("c", 0.8, -0.3))
        assertEquals(-0.3 to 2.0, Anomalien.spanne(l))
        assertEquals(0.0 to 1.0, Anomalien.spanne(emptyList()))
        assertEquals("c", Anomalien.naechstes(l, 0.8, -0.2)!!.k)
        assertEquals("b", Anomalien.naechstes(l, 0.0, 2.0)!!.k)
        // Breite Flaeche: der waagerechte Abstand wiegt schwerer
        assertEquals("b", Anomalien.naechstes(l, 0.95, 1.3)!!.k)
        assertEquals("a", Anomalien.naechstes(l, 0.95, 1.3, breiteZuHoehe = 3.0)!!.k)
        assertNull(Anomalien.naechstes(emptyList(), 0.0, 0.0))
    }

    @Test
    fun mitgelieferteDatei() {
        val d = Anomalien.lesen(asset(Anomalien.DATEI).readText(Charsets.UTF_8))
        assertNotNull(d)
        d!!
        assertTrue(Regex("\\d{4}-\\d{2}").matches(d.stand) && Regex("\\d{4}-\\d{2}").matches(d.bis))
        assertTrue(d.signale.size >= 150)
        assertEquals(d.signale.size, d.signale.map { it.k }.toSet().size)
        d.signale.forEach { x ->
            assertTrue(x.k, x.name.isNotBlank() && x.von <= x.bis && x.bis <= x.jahr && x.jahr in 1960..2026)
            assertTrue(x.k, x.nStudie >= d.minMonate && x.nDanach >= d.minMonate && d.minMonate >= 12)
            assertTrue(x.k, (x.zwischen == null) == (x.nZwischen == 0))
        }
        val a = Anomalien.auswerten(d.signale)!!
        // Im Mittel bleibt nach der Veroeffentlichung weniger, aber mehr als null
        assertTrue(a.danach < a.studie && a.danach > 0)
        assertTrue(a.rueckgang > 0 && a.rueckgang < 100)
        assertTrue(a.schwaecher * 2 > a.anzahl)
        assertEquals(a.rueckgang, (1 - a.danach / a.studie) * 100, 1e-9)
        assertNull(Anomalien.lesen("{}"))
        assertNull(Anomalien.lesen("{\"stand\":\"2026-10\",\"signale\":[]}"))
    }

    @Test
    fun vergleichsstudieUndTexte() {
        val m = MarktSchlagen
        assertEquals(97.0, m.MUSTER_ANZAHL.wert, 0.0)
        assertEquals(26.0, m.MUSTER_DAVOR.wert, 0.0)
        assertEquals(58.0, m.MUSTER_DANACH.wert, 0.0)
        assertEquals(listOf(m.CHEN_ZIMMERMANN, m.MCLEAN_2016), m.quellen(MarktSchlagen.Baustein.ANOMALIEN))
        assertTrue(m.CHEN_ZIMMERMANN.name.contains("CC BY 4.0"))
        for (d in listOf(true, false)) {
            val t = MarktTexte.text(d, "anomalien.info", listOf("97", "58 %", "26 %"))
            assertTrue(t, t.contains("97") && t.contains("58 %") && t.contains("26 %"))
            assertTrue(MarktTexte.text(d, "ANOMALIEN.satz", listOf("204", "176")).contains("176"))
        }
    }
}
