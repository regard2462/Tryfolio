package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.Candle
import de.troetenrepublik.app.i18n.IpoTexte
import de.troetenrepublik.app.market.IpoKalender
import de.troetenrepublik.app.market.IpoKalender.Status
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Detailblatt eines Boersengangs (0.78), Namen und Betraege erfunden.
class Fassung078U13IpoTest {

    private fun a(
        name: String, status: Status, volumen: Double? = null, preis: String? = null, tag: Long? = null, symbol: String? = null,
    ) = IpoKalender.Angebot(name.lowercase(), symbol, name, "NASDAQ", preis, null, volumen, tag, status)

    private val mio = 1_000_000.0

    @Test
    fun kopfwertJeZustand() {
        val spanne = a("Acme", Status.ANGEKUENDIGT, preis = "70.00-78.00")
        assertEquals(IpoBlatt.Kopfwert(IpoBlatt.WertArt.MITTE, 74.0), IpoBlatt.kopfwert(spanne, null))
        assertEquals(IpoBlatt.Kopfwert(IpoBlatt.WertArt.PREIS, 16.0), IpoBlatt.kopfwert(a("Birke", Status.ANGEKUENDIGT, preis = "16.00"), null))
        val fest = a("Cedro", Status.PREIS, preis = "20.00")
        assertEquals(IpoBlatt.Kopfwert(IpoBlatt.WertArt.AUSGABE, 20.0), IpoBlatt.kopfwert(fest, null))
        val k = IpoBlatt.kopfwert(fest, 25.0)!!
        assertEquals(IpoBlatt.WertArt.KURS, k.art)
        assertEquals(25.0, k.wert, 1e-9)
        assertEquals(25.0, k.seit!!, 1e-9)
        // Kurs ohne Ausgabepreis: Kurs ohne Veraenderung
        assertEquals(IpoBlatt.Kopfwert(IpoBlatt.WertArt.KURS, 12.0, null), IpoBlatt.kopfwert(a("Dorn", Status.PREIS), 12.0))
        // Der Kurs zaehlt erst nach der Erstnotiz
        assertEquals(IpoBlatt.WertArt.MITTE, IpoBlatt.kopfwert(spanne, 80.0)!!.art)
        assertNull(IpoBlatt.kopfwert(a("Erle", Status.EINGEREICHT, volumen = 100 * mio), null))
        assertNull(IpoBlatt.kopfwert(a("Farn", Status.PREIS), null))
    }

    @Test
    fun tageBisTermin() {
        assertEquals(7L, IpoBlatt.tageBis(107L, 100L))
        assertEquals(0L, IpoBlatt.tageBis(100L, 100L))
        assertEquals(-3L, IpoBlatt.tageBis(97L, 100L))
        assertNull(IpoBlatt.tageBis(null, 100L))
    }

    @Test
    fun groesseGegenAndere() {
        val ich = a("Acme", Status.ANGEKUENDIGT, volumen = 400 * mio, preis = "10.00-12.00")
        val andere = listOf(
            a("B", Status.ANGEKUENDIGT, 100 * mio, "5.00"), a("C", Status.PREIS, 200 * mio, "5.00"), a("D", Status.PREIS, 300 * mio, "5.00"),
            a("E", Status.EINGEREICHT, 800 * mio, "5.00"), a("F", Status.PREIS, 50 * mio, "5.00"),
        )
        val g = IpoBlatt.groesse(ich, andere + ich)!!
        assertEquals(200 * mio, g.median, 1.0)
        assertEquals(800 * mio, g.groesstes, 1.0)
        assertEquals("E", g.groesstesName)
        assertEquals(2, g.rang)
        assertEquals(6, g.anzahl)
        assertEquals(0.5, g.anteil, 1e-9)
        assertEquals(0.25, g.anteilMedian, 1e-9)
        assertEquals(1.0, g.anteilGroesstes, 1e-9)
        // Das groesste Angebot fuellt den Balken selbst
        val riese = ich.copy(volumen = 1600 * mio)
        val gr = IpoBlatt.groesse(riese, andere)!!
        assertEquals(1, gr.rang)
        assertEquals(1.0, gr.anteil, 1e-9)
        assertEquals(0.5, gr.anteilGroesstes, 1e-9)
    }

    @Test
    fun groesseNurMitGenugEchtenWerten() {
        val ich = a("Acme", Status.ANGEKUENDIGT, volumen = 400 * mio, preis = "10.00")
        val vier = (1..4).map { a("N$it", Status.PREIS, it * 100 * mio, "5.00") }
        val fuenfter = a("N5", Status.PREIS, 900 * mio, "5.00")
        assertNull(IpoBlatt.groesse(ich, vier))
        // Zurueckgezogene, Platzhalter des Prospekts, Mantelgesellschaften und Angebote ohne Volumen zaehlen nicht
        val unecht = listOf(
            a("Weg", Status.ZURUECK, 500 * mio, "5.00"), a("Platz", Status.EINGEREICHT, 500 * mio),
            a("Mantel Acquisition Corp", Status.PREIS, 500 * mio, "10.00"), a("Leer", Status.PREIS, null, "5.00"),
        )
        assertNull(IpoBlatt.groesse(ich, vier + unecht))
        assertEquals(6, IpoBlatt.groesse(ich, vier + unecht + fuenfter)!!.anzahl)
        // Eigener Platzhalterbetrag: kein Vergleich
        assertNull(IpoBlatt.groesse(a("Acme", Status.EINGEREICHT, volumen = 400 * mio), vier + fuenfter))
        // Mantelgesellschaft nur gegen Mantelgesellschaften
        val maentel = (1..5).map { a("Huelle $it Acquisition Corp", Status.PREIS, it * 50 * mio, "10.00") }
        assertNull(IpoBlatt.groesse(maentel[0], vier + fuenfter))
        assertEquals(6, IpoBlatt.groesse(a("Neue Acquisition Corp", Status.ANGEKUENDIGT, 100 * mio, "10.00"), maentel + vier)!!.anzahl)
    }

    @Test
    fun median() {
        assertEquals(2.0, IpoBlatt.median(listOf(3.0, 1.0, 2.0)), 1e-9)
        assertEquals(2.5, IpoBlatt.median(listOf(4.0, 1.0, 2.0, 3.0)), 1e-9)
        assertTrue(IpoBlatt.median(emptyList()).isNaN())
    }

    @Test
    fun ersteTageVerteilung() {
        val liste = listOf("A" to 12.0, "B" to -4.0, "C" to 0.0, "D" to 30.0, "E" to -10.0).map { a(it.first, Status.PREIS, preis = "10.00") to it.second }
        val alle = liste.map { it.first } + a("Mantel Acquisition Corp", Status.PREIS, preis = "10.00") + a("Offen", Status.ANGEKUENDIGT, preis = "10.00")
        val karte = liste.associate { it.first.deal to it.second } + ("mantel acquisition corp" to 0.1) + ("offen" to 99.0)
        val e = IpoBlatt.ersteTage(a("Neu", Status.ANGEKUENDIGT, preis = "10.00"), alle, karte)!!
        assertEquals(listOf(-10.0, -4.0, 0.0, 12.0, 30.0), e.werte)
        assertEquals(2, e.imPlus)
        assertEquals(0.0, e.median, 1e-9)
        assertEquals(-1, e.eigen)
        assertEquals(-10.0, e.schwaechster, 1e-9)
        assertEquals(30.0, e.staerkster, 1e-9)
        // Das gestartete Angebot selbst steht an seiner Stelle
        assertEquals(3, IpoBlatt.ersteTage(liste[0].first, alle, karte)!!.eigen)
        // Zu wenige Werte
        assertNull(IpoBlatt.ersteTage(liste[0].first, alle, karte - "e"))
        assertNull(IpoBlatt.ersteTage(liste[0].first, alle, emptyMap()))
    }

    @Test
    fun kerzenAbErstnotiz() {
        val tag = 86_400_000L
        fun k(t: Long, c: Double) = Candle(t, c, c, c, c)
        val kerzen = listOf(k(102 * tag, 14.0), k(99 * tag, 9.0), k(100 * tag + 5, 10.0), k(101 * tag, Double.NaN), k(103 * tag, 0.0))
        assertEquals(listOf(10.0, 14.0), IpoBlatt.kerzenAb(kerzen, 100L).map { it.close })
        assertTrue(IpoBlatt.kerzenAb(kerzen, null).isEmpty())
    }

    @Test
    fun texteInBeidenSprachen() {
        assertEquals(IpoTexte.schluessel(true), IpoTexte.schluessel(false))
        for (k in IpoTexte.schluessel(true)) for (d in listOf(true, false)) {
            val t = IpoTexte.text(d, k, listOf("1", "2"))
            assertTrue(k, t.isNotBlank())
            assertFalse(k, t.contains('{'))
            assertFalse(k, t.contains('–') || t.contains('—') || t.contains('…'))
        }
        for (art in IpoBlatt.WertArt.entries) assertTrue(art.name, "wert." + art.name in IpoTexte.schluessel(true))
        assertEquals("in 7 Tagen", IpoTexte.text(true, "tage.in", listOf("7")))
        assertEquals("3 of 12", IpoTexte.text(false, "von", listOf("3", "12")))
    }
}
