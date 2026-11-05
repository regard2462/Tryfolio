package de.troetenrepublik.app.market

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

// Quartalszahlen der Woche (5.94) mit erfundenen Zeilen im Format von Finnhub und Alpha Vantage.
class Fassung594QuartalszahlenTest {

    private val finnhub = """
        {"earningsCalendar":[
          {"date":"2026-11-03","epsActual":null,"epsEstimate":1.68,"hour":"amc","quarter":3,"revenueActual":null,"revenueEstimate":94000000000,"symbol":"AAA","year":2026},
          {"date":"2026-11-03","epsActual":null,"epsEstimate":-0.12,"hour":"bmo","quarter":3,"revenueActual":null,"revenueEstimate":null,"symbol":"BRK.B","year":2026},
          {"date":"2026-11-02","epsActual":1.89,"epsEstimate":2.01,"hour":"","quarter":3,"revenueActual":5000000,"revenueEstimate":4900000,"symbol":"CCC","year":2026}
        ]}
    """.trimIndent()

    @Test
    fun finnhubLesen() {
        val l = Quartalszahlen.finnhubLesen(finnhub)!!
        assertEquals(3, l.size)
        val a = l[0]
        assertEquals("AAA", a.symbol)
        assertEquals(Quartalszahlen.Zeit.NACH, a.zeit)
        assertEquals(LocalDate.of(2026, 11, 3).toEpochDay(), a.tag)
        assertEquals(94e9, a.umsatz!!, 1.0)
        assertEquals(1.68, a.epsErwartet!!, 1e-9)
        assertEquals("Q3 2026", a.quartal)
        val b = l[1]
        assertEquals("BRK-B", b.symbol)
        assertEquals(Quartalszahlen.Zeit.VOR, b.zeit)
        assertEquals(-0.12, b.epsErwartet!!, 1e-9)
        assertNull(b.umsatz)
        val c = l[2]
        assertEquals(Quartalszahlen.Zeit.OFFEN, c.zeit)
        assertEquals(1.89, c.eps!!, 1e-9)
        // Leerer Zeitraum ist eine leere Liste, Fehlermeldung oder kaputte Antwort null
        assertEquals(emptyList<Quartalszahlen.Termin>(), Quartalszahlen.finnhubLesen("""{"earningsCalendar":[]}"""))
        assertNull(Quartalszahlen.finnhubLesen("""{"error":"Invalid API key"}"""))
        assertNull(Quartalszahlen.finnhubLesen("<html>"))
    }

    @Test
    fun alphaLesen() {
        val csv = "symbol,name,reportDate,fiscalDateEnding,estimate,currency,timeOfTheDay\r\n" +
            "AAA,Alpha Devices Inc,2026-11-03,2026-09-30,1.68,USD,post-market\r\n" +
            "NOVO,\"Novo Nordisk, A/S\",2026-11-05,2026-09-30,4.1,DKK,pre-market\r\n" +
            "ZZZ,Zeta Corp,2026-11-06,2026-09-30,,USD,\r\n"
        val l = Quartalszahlen.alphaLesen(csv)!!
        assertEquals(3, l.size)
        assertEquals("Alpha Devices", l[0].name)
        assertEquals(Quartalszahlen.Zeit.NACH, l[0].zeit)
        assertEquals(1.68, l[0].epsErwartet!!, 1e-9)
        // Komma im Namen, Gewinn in Kronen bleibt leer
        assertEquals("Novo Nordisk", l[1].name)
        assertEquals(Quartalszahlen.Zeit.VOR, l[1].zeit)
        assertNull(l[1].epsErwartet)
        assertEquals(Quartalszahlen.Zeit.OFFEN, l[2].zeit)
        assertNull(Quartalszahlen.alphaLesen("""{"Information":"limit"}"""))
        assertEquals(Quartalszahlen.FINNHUB, Quartalszahlen.anbieter("x", "y"))
        assertEquals(Quartalszahlen.ALPHAVANTAGE, Quartalszahlen.anbieter(" ", "y"))
        assertNull(Quartalszahlen.anbieter("", ""))
    }

    @Test
    fun woche() {
        // Montag 2.11.2026: diese Woche 2. bis 6.11.
        assertEquals(LocalDate.of(2026, 11, 2), Quartalszahlen.woche(LocalDate.of(2026, 11, 4), 0).first())
        assertEquals(LocalDate.of(2026, 11, 6), Quartalszahlen.woche(LocalDate.of(2026, 11, 4), 0).last())
        // Am Wochenende zaehlt die kommende Woche
        assertEquals(LocalDate.of(2026, 11, 9), Quartalszahlen.woche(LocalDate.of(2026, 11, 7), 0).first())
        assertEquals(LocalDate.of(2026, 11, 9), Quartalszahlen.woche(LocalDate.of(2026, 11, 8), 0).first())
        assertEquals(LocalDate.of(2026, 10, 26), Quartalszahlen.woche(LocalDate.of(2026, 11, 2), -1).first())
    }

    @Test
    fun namenUndZeit() {
        assertEquals("Novo Nordisk", Quartalszahlen.name("Novo Nordisk A/S"))
        assertEquals("Taiwan Semiconductor Manufacturing", Quartalszahlen.name("Taiwan Semiconductor Manufacturing Company Ltd."))
        assertEquals("Booking Holdings", Quartalszahlen.name("Booking Holdings Inc."))
        assertEquals("Merck & Co.", Quartalszahlen.name("Merck & Co., Inc."))
        assertEquals("Coca Cola Femsa", Quartalszahlen.name("Coca Cola Femsa S.A.B. de C.V."))
        assertEquals("Coca Cola Femsa", Quartalszahlen.name("Coca Cola Femsa S.A.B."))
        assertEquals("Eli Lilly and Company", Quartalszahlen.name("Eli Lilly and Company"))
        // Zeitpunkt gegen die Sitzung des Heimatplatzes: USA 9:30 bis 16, Tokio 9 bis 15:30
        fun z(symbol: String, h: Int, m: Int): Quartalszahlen.Zeit {
            val p = Quartalszahlen.platz(symbol)!!
            return Quartalszahlen.zeitAus(java.time.LocalTime.of(h, m), p.oeffnet, p.schliesst)
        }
        assertEquals(Quartalszahlen.Zeit.NACH, z("AAPL", 16, 5))
        assertEquals(Quartalszahlen.Zeit.VOR, z("AAPL", 7, 0))
        assertEquals(Quartalszahlen.Zeit.NACH, z("7203.T", 15, 30))
        assertEquals(Quartalszahlen.Zeit.HANDEL, z("SAP.DE", 12, 0))
        assertEquals(Quartalszahlen.Zeit.OFFEN, z("SAP.DE", 0, 0))
        assertEquals(null, Quartalszahlen.platz("ABC.XYZ"))
    }

    @Test
    fun gattungen() {
        fun t(s: String, name: String, wert: Double?, n: Int? = null) = Quartalszahlen.Termin(s, name, 1L, Quartalszahlen.Zeit.VOR, wert = wert, schaetzungen = n)
        val l = Quartalszahlen.ohneDoppelteGattungen(listOf(
            t("FOXA", "Fox", 21.16e9, 9), t("FOX", "Fox", 21.16e9, 4),
            // gleicher Name, anderer Boersenwert: zwei Firmen
            t("ABC", "Acme", 5e9), t("ABD", "Acme", 9e9),
            t("X1", "Ohne", null), t("X2", "Ohne", null),
        ))
        assertEquals(listOf("FOXA", "ABC", "ABD", "X1", "X2"), l.map { it.symbol })
        // Die Gattung aus dem Depot schlaegt die mit mehr Schaetzungen
        val mein = Quartalszahlen.ohneDoppelteGattungen(listOf(t("FOXA", "Fox", 21.16e9, 9), t("FOX", "Fox", 21.16e9, 4).copy(eigen = true)))
        assertEquals(listOf("FOX"), mein.map { it.symbol })
        assertEquals("Fox", Quartalszahlen.ohneGattung("Fox (B)"))
        assertEquals("Alphabet", Quartalszahlen.ohneGattung("Alphabet (Class A)"))
    }

    @Test
    fun bekannte() {
        fun t(s: String, wert: Double, eigen: Boolean = false) = Quartalszahlen.Termin(s, s, 1L, Quartalszahlen.Zeit.NACH, wert = wert, eigen = eigen)
        val viele = (1..12).map { t("G$it", it * 20e9) } + t("KLEIN", 1e9) + t("MEIN", 5e8, eigen = true)
        // Finnhub: ohne Boersenwert entscheidet der erwartete Quartalsumsatz
        val u = { s: String, umsatz: Double -> Quartalszahlen.Termin(s, s, 1L, Quartalszahlen.Zeit.VOR, umsatz = umsatz) }
        assertEquals(listOf("U2"), Quartalszahlen.bekannte(listOf(u("U1", 4e8), u("U2", 3e9))).map { it.symbol })
        val b = Quartalszahlen.bekannte(viele)
        assertEquals(Quartalszahlen.JE_GRUPPE, b.size)
        assertEquals("MEIN", b.first().symbol)
        assertTrue(b.none { it.symbol == "KLEIN" })
        assertEquals("G12", b[1].symbol)
    }
}
