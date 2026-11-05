package de.troetenrepublik.app.engine

import de.troetenrepublik.app.engine.VermoegenAnzeige.Netto
import de.troetenrepublik.app.market.Vermoegen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

// Fassung 5.96: Vermoegen laut Jahresmeldung mit getrennten Vermoegenswerten und Schulden, Leser mit Seitenumbruechen.
class Fassung596VermoegenTest {

    private val kopf = Vermoegen.Kopf("10000001", "Muster", "Max", "CA11", 2025, "05/15/2026")

    private fun e(av: Long, ab: Long, ao: Boolean = false, sv: Long = 0, sb: Long = 0, so: Boolean = false, z: Int = 0) =
        Vermoegen.E("10075701", "Pelosi", "Nancy", "CA11", 2025, av, ab, ao, sv, sb, so, z)

    // Tabellenkopf der Tabelle in Abschnitt A, in den Stuecken eines Seitenumbruchs.
    private val kopfA = listOf(
        "A", "sse", "t", "O", "w", "n", "e", "r", "V", "alu", "e", "o", "f A", "sse", "t", "I", "n", "c", "o", "me",
        "T", "y", "p", "e", "(s)", "I", "n", "c", "o", "me", "T", "x", ".", ">", "$", "1", ",", "0", "0", "0", "?",
    )

    // Tabellenkopf der Tabelle in Abschnitt D, in den Stuecken eines Seitenumbruchs.
    private val kopfD = listOf(
        "O", "w", "n", "e", "r", "Cre", "d", "i", "t", "o", "r", "D", "at", "e", "I", "n", "c", "u", "rre", "d",
        "T", "y", "p", "e", "A", "mo", "u", "n", "t", "o", "f", "L", "i", "ab", "i", "li", "t", "y",
    )

    @Test
    fun teilePelosi() {
        val t = VermoegenAnzeige.teile(e(av = 85_944_060, ab = 412_412_000, sv = 61_250_011, sb = 152_500_000, z = 68))
        assertEquals(Netto.OFFEN, t.fall)
        assertEquals(85_944_060L - 152_500_000L, t.nettoVon)
        assertEquals(412_412_000L - 61_250_011L, t.nettoBis)
        assertEquals(VermoegenAnzeige.Spanne(85_944_060, 412_412_000), t.werte)
        assertEquals(VermoegenAnzeige.Spanne(61_250_011, 152_500_000), t.schulden)
        assertEquals(68, t.posten)
    }

    @Test
    fun teileFaelle() {
        val plus = VermoegenAnzeige.teile(e(av = 2_000_000, ab = 5_000_000, sv = 100_000, sb = 500_000))
        assertEquals(Netto.PLUS, plus.fall)
        assertEquals(1_500_000L, plus.nettoVon)
        assertEquals(4_900_000L, plus.nettoBis)
        val minus = VermoegenAnzeige.teile(e(av = 15_001, ab = 50_000, sv = 100_001, sb = 250_000))
        assertEquals(Netto.MINUS, minus.fall)
        assertEquals(-234_999L, minus.nettoVon)
        assertEquals(-50_001L, minus.nettoBis)
        val ohne = VermoegenAnzeige.teile(e(av = 15_001, ab = 50_000))
        assertEquals(Netto.OHNE_SCHULDEN, ohne.fall)
        assertNull(ohne.schulden)
        val offeneWerte = VermoegenAnzeige.teile(e(av = 2_000_000, ab = 50_000_001, ao = true, sv = 100_000, sb = 500_000))
        assertEquals(Netto.PLUS, offeneWerte.fall)
        assertEquals(1_500_000L, offeneWerte.nettoVon)
        assertNull(offeneWerte.nettoBis)
        val offeneSchulden = VermoegenAnzeige.teile(e(av = 2_000_000, ab = 5_000_000, sv = 1_000_000, sb = 2_000_000, so = true))
        assertEquals(Netto.OFFEN, offeneSchulden.fall)
        assertNull(offeneSchulden.nettoVon)
        assertEquals(4_000_000L, offeneSchulden.nettoBis)
    }

    @Test
    fun mitte() {
        assertEquals((85_944_060.0 + 412_412_000) / 2.0 - (61_250_011.0 + 152_500_000) / 2.0, VermoegenAnzeige.mitte(e(av = 85_944_060, ab = 412_412_000, sv = 61_250_011, sb = 152_500_000)), 0.0)
        // offene Werte zaehlen mit der Untergrenze
        assertEquals(1_000_000.0, VermoegenAnzeige.mitte(e(av = 1_000_000, ab = 50_000_001, ao = true)), 0.0)
        // offene Schulden ebenso
        assertEquals(1_500_000.0, VermoegenAnzeige.mitte(e(av = 2_000_000, ab = 3_000_000, sv = 1_000_000, sb = 2_000_000, so = true)), 0.0)
    }

    @Test
    fun leserUndetermined() {
        val zeilen = listOf(
            "Filing Type:", "Annual Report", "S", "A", ":",
            "Verlag [BK]", "Undetermined", "Royalties", "$100,001 -", "$1,000,000",
            "S", "B", ":", "None disclosed.",
        )
        val e = Vermoegen.auswerten(kopf, zeilen)!!
        assertEquals(1, e.z)
        assertEquals(0L, e.av)
        assertEquals(0L, e.ab)
    }

    @Test
    fun leserSeitenumbruchOhneEinkommen() {
        val zeilen = listOf(
            "Filing Type:", "Annual Report", "S", "A", ":",
            "Leasing GmbH", "[OL]", "SP", "$100,001 -", "None",
        ) + kopfA + listOf(
            "$250,000", "S", "B", ":", "None disclosed.",
        )
        val e = Vermoegen.auswerten(kopf, zeilen)!!
        assertEquals(1, e.z)
        assertEquals(100_001L, e.av)
        assertEquals(250_000L, e.ab)
    }

    @Test
    fun leserSeitenumbruchMitEinkommen() {
        val zeilen = listOf(
            "Filing Type:", "Annual Report", "S", "A", ":",
            "Leasing GmbH", "[OL]", "SP", "$100,001 -", "Dividends", "$1,001 - $2,500",
        ) + kopfA + listOf(
            "$250,000", "S", "B", ":", "None disclosed.",
        )
        val e = Vermoegen.auswerten(kopf, zeilen)!!
        assertEquals(1, e.z)
        assertEquals(100_001L, e.av)
        assertEquals(250_000L, e.ab)
    }

    @Test
    fun leserSchuldenMitSeitenumbruch() {
        val zeilen = listOf(
            "Filing Type:", "Annual Report", "S", "A", ":",
            "Konto [BA]", "None",
            "S", "D", ":",
            "Kredit Linie", "$250,001 -",
        ) + kopfD + listOf(
            "$500,000", "SP", "Sparkasse",
            "Zweite Linie", "$15,001 - $50,000",
            "S", "E", ":", "Position",
        )
        val e = Vermoegen.auswerten(kopf, zeilen)!!
        assertEquals(1, e.z)
        assertEquals(250_001L + 15_001L, e.sv)
        assertEquals(500_000L + 50_000L, e.sb)
    }
}
