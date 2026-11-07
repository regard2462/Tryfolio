package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.Transaction
import de.troetenrepublik.app.data.TxType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// Sankey-Diagramme (5.94): Anordnung, Depot, Verkaeufe und Gewinnrechnung mit erfundenen Werten.
class Fassung594SankeyTest {

    @Test
    fun anordnungMitGleichemMassstab() {
        val d = Sankey.Diagramm(
            listOf(Sankey.Knoten("a", 0), Sankey.Knoten("b", 0), Sankey.Knoten("m", 1), Sankey.Knoten("x", 2)),
            listOf(Sankey.Fluss("a", "m", 30.0), Sankey.Fluss("b", "m", 10.0), Sankey.Fluss("m", "x", 40.0)),
        )
        val l = Sankey.anordnen(d, 300f, 110f, 8f, 10f)
        val p = l.plaetze.associateBy { it.knoten.id }
        // Spalte 0: 100 px fuer 40, also 2,5 px je Einheit
        assertEquals(75f, p.getValue("a").h, 1e-3f)
        assertEquals(100f, p.getValue("m").h, 1e-3f)
        assertEquals(292f, p.getValue("x").x, 1e-3f)
        // Baender schliessen am Ziel lueckenlos aneinander
        val zu = l.baender.filter { it.nach.knoten.id == "m" }.sortedBy { it.z0 }
        assertEquals(zu[0].z1, zu[1].z0, 1e-3f)
    }

    @Test
    fun depotGleichtAus() {
        val b = Performanceanalyse.Bilanz(1000.0, 500.0, 100.0, -50.0, 20.0, 30.0, 5.0, 10.0, 15.0, 1360.0)
        val d = Sankey.depot(b)
        val ein = d.fluesse.filter { it.nach == Sankey.DEPOT }.sumOf { it.wert }
        val aus = d.fluesse.filter { it.von == Sankey.DEPOT }.sumOf { it.wert }
        assertEquals(ein, aus, 1e-9)
        assertEquals(50.0, d.fluesse.first { it.nach == Sankey.KURSVERLUSTE }.wert, 1e-9)
        // Steuererstattung 15 EUR und zurueckgebuchte Dividende 30 EUR: links bzw. rechts, beide Seiten gleich
        val e = Sankey.depot(b.copy(steuern = -15.0, dividenden = -30.0, ende = 1330.0))
        assertEquals(e.fluesse.filter { it.nach == Sankey.DEPOT }.sumOf { it.wert }, e.fluesse.filter { it.von == Sankey.DEPOT }.sumOf { it.wert }, 1e-9)
        assertEquals(15.0, e.fluesse.first { it.von == Sankey.ERSTATTUNGEN }.wert, 1e-9)
        assertEquals(30.0, e.fluesse.first { it.nach == Sankey.KORREKTUREN }.wert, 1e-9)
        assertTrue(e.fluesse.all { it.wert >= 0 })
    }

    @Test
    fun verkaeufe() {
        fun t(s: String, r: Double, ts: Long = 10) = Transaction(id = s + r, ts = ts, type = TxType.SELL, title = s, symbol = s, realized = r)
        val d = Sankey.verkaeufe(listOf(t("A", 100.0), t("B", -30.0), t("A", 20.0), t("C", 5.0, ts = 99)), 0, 50)
        assertEquals(120.0, d.fluesse.first { it.von == "A" }.wert, 1e-9)
        assertEquals(30.0, d.fluesse.first { Sankey.symbol(it.nach) == "B" }.wert, 1e-9)
        assertEquals(90.0, d.fluesse.first { it.nach == Sankey.NETTO_PLUS }.wert, 1e-9)
        assertTrue(Sankey.verkaeufe(emptyList(), 0, 50).leer)
    }

    @Test
    fun guvMitGewinnUndVerlust() {
        val g = Sankey.Guv("2025-12-31", "USD", 100.0, 40.0, 60.0, 10.0, 15.0, null, 30.0, 5.0, 20.0)
        val d = Sankey.guv(g)
        val f = d.fluesse.associate { (it.von + ">" + it.nach) to it.wert }
        assertEquals(5.0, f.getValue("brutto>sonstige"), 1e-9)
        assertEquals(5.0, f.getValue("betrieb>zinsenSonstiges"), 1e-9)
        assertEquals(20.0, f.getValue("betrieb>netto"), 1e-9)
        // SAP 2025 (Mrd. EUR): Betriebsergebnis 9,62, Steuern 2,944, Nettogewinn 7,161, also 0,485 Zins- und sonstiger Ertrag
        val sap = Sankey.guv(g.copy(umsatz = 36.8, herstellkosten = 9.99, brutto = 26.81, forschung = 6.63, verwaltung = 10.51, betrieb = 9.62, steuer = 2.944, netto = 7.161))
        val s = sap.fluesse.filter { it.wert > 0 }
        assertEquals(2.944, s.filter { it.nach == Sankey.STEUER }.sumOf { it.wert }, 1e-9)
        assertEquals(7.161, s.filter { it.nach == Sankey.NETTO }.sumOf { it.wert }, 1e-9)
        assertEquals(0.485, s.filter { it.von == Sankey.FINANZERTRAG }.sumOf { it.wert }, 1e-9)
        val verlust = Sankey.guv(g.copy(betrieb = -20.0, netto = -25.0))
        // Kosten 80 = Bruttogewinn 60 plus Verlust 20
        assertEquals(20.0, verlust.fluesse.filter { it.von == Sankey.BETRIEBSVERLUST }.sumOf { it.wert }, 1e-9)
    }
}
