package de.troetenrepublik.app.engine

import de.troetenrepublik.app.market.Handelnde
import de.troetenrepublik.app.market.Investoren
import de.troetenrepublik.app.market.SecInsider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// Aktiensplits bei Meldungen mit Stueckzahl und Preis in der Einheit ihres Tages (13F, Form 4).
class CopySplitTest {
    private fun tag(iso: String) = CopyUebersicht.tag(iso)
    private val nvda = listOf(Splits.Split(tag("2024-06-10"), 10.0, 1.0))
    private fun faktor(s: String, nach: Long, bis: Long): Double =
        if (s == "NVDA") nvda.filter { it.t > nach && it.t <= bis }.fold(1.0) { a, x -> a * x.verhaeltnis } else 1.0

    private fun pos(k: String, stueck: Double, wert: Double) = Investoren.Position("N $k", k, "C$k", wert, stueck, "aktie")
    private fun investor(vor: Investoren.Position, jetzt: Investoren.Position) = Investoren.Investor(
        "x", "Xaver", "X Inc", "1", "", "",
        listOf(Investoren.Quartal("2024-06-30", "2024-08-14", 0.0, listOf(jetzt)), Investoren.Quartal("2024-03-31", "2024-05-15", 0.0, listOf(vor))),
    )

    @Test(timeout = 20_000)
    fun faktorZaehltSplitsErstNachDemTag() {
        SplitFaktoren.setzen("ZZSPLIT", nvda)
        assertEquals(10.0, SplitFaktoren.zwischen("ZZSPLIT", tag("2024-03-31")), 1e-9)
        assertEquals(1.0, SplitFaktoren.zwischen("ZZSPLIT", tag("2024-06-10")), 1e-9)
        assertEquals(10.0, SplitFaktoren.zwischen("ZZSPLIT", tag("2024-03-31"), tag("2024-06-30")), 1e-9)
        assertEquals(1.0, SplitFaktoren.zwischen("ZZSPLIT", tag("2024-03-31"), tag("2024-06-09")), 1e-9)
        assertEquals(1.0, SplitFaktoren.zwischen("ANDERE", 0L), 1e-9)
        SplitFaktoren.setzen("BRK-B", nvda)
        assertEquals(10.0, SplitFaktoren.zwischen("BRK.B", tag("2024-01-01")), 1e-9)
        SplitFaktoren.loeschen()
    }

    @Test(timeout = 20_000)
    fun splitSprungIstKeinKauf() {
        // 100 Stueck zu 903 (31.03.2024), 1000 Stueck zu 123,54 (30.06.2024): gleiche Menge nach dem Split
        val inv = investor(pos("NVDA", 100.0, 90_300.0), pos("NVDA", 1_000.0, 123_540.0))
        val ohne = CopyUebersicht.ausInvestoren(listOf(inv), { it }, { _, _, _ -> 1.0 }).first
        assertEquals(1, ohne.size)
        assertTrue(ohne.single().kauf)
        val mit = CopyUebersicht.ausInvestoren(listOf(inv), { it }, ::faktor).first
        assertTrue(mit.isEmpty())
        // Zusaetzlich 10 % gekauft: nur diese 100 Stueck zaehlen, zum Preis des Stichtags
        val mehr = investor(pos("NVDA", 100.0, 90_300.0), pos("NVDA", 1_100.0, 135_894.0))
        val t = CopyUebersicht.ausInvestoren(listOf(mehr), { it }, ::faktor).first.single()
        assertTrue(t.kauf)
        assertEquals(100 * 123.54, t.betrag, 1e-6)
    }

    @Test(timeout = 20_000)
    fun reverseSplitIstKeinVerkauf() {
        val rev = listOf(Splits.Split(tag("2024-05-01"), 1.0, 10.0))
        val f = { s: String, nach: Long, bis: Long -> rev.filter { it.t > nach && it.t <= bis }.fold(1.0) { a, x -> a * x.verhaeltnis } }
        val inv = investor(pos("ABC", 1_000.0, 1_000.0), pos("ABC", 100.0, 1_000.0))
        assertFalse(CopyUebersicht.ausInvestoren(listOf(inv), { it }, { _, _, _ -> 1.0 }).first.single().kauf)
        assertTrue(CopyUebersicht.ausInvestoren(listOf(inv), { it }, f).first.isEmpty())
    }

    @Test(timeout = 20_000)
    fun einstiegskursMitSplitBereinigt() {
        // Einstieg 903 (31.03.2024, Einheit damals), heute 140: bereinigt 90,3, also +55 %
        val kaeufe = CopyUebersicht.ausInvestoren(listOf(Investoren.Investor("x", "X", "X", "1", "", "", listOf(
            Investoren.Quartal("2024-03-31", "2024-05-15", 0.0, listOf(pos("NVDA", 200.0, 180_600.0))),
            Investoren.Quartal("2023-12-31", "2024-02-14", 0.0, listOf(pos("NVDA", 100.0, 50_000.0)))))), { it }, { _, _, _ -> 1.0 }).first
        val ohne = CopyUebersicht.einstaende(kaeufe, { _, _ -> null }, { 140.0 }, { _, _, _ -> 1.0 }).single()
        assertTrue(ohne.abstand < -0.8)
        val mit = CopyUebersicht.einstaende(kaeufe, { _, _ -> null }, { 140.0 }, ::faktor).single()
        assertEquals(90.3, mit.einstieg, 1e-9)
        assertEquals(140.0 / 90.3 - 1, mit.abstand, 1e-9)
        assertTrue(CopyUebersicht.unterEinstieg(listOf(mit)).isEmpty())
    }

    @Test(timeout = 20_000)
    fun insiderKaufVorSplit() {
        val l = listOf(SecInsider.Geschaeft("a1", "NVDA", "Nvidia", "1", "Meier", "S", "CEO", true, "2024-03-01", "2024-03-01", 10.0, 800.0, 8_000.0, 110.0))
        val (trades, _) = CopyUebersicht.ausInsidern(l)
        val e = CopyUebersicht.einstaende(trades, { _, _ -> null }, { 120.0 }, ::faktor).single()
        assertEquals(80.0, e.einstieg, 1e-9)
        assertEquals(0.5, e.abstand, 1e-9)
    }

    @Test(timeout = 20_000)
    fun quartalsvergleichInZeilenUndHandelnde() {
        val inv = investor(pos("NVDA", 100.0, 90_300.0), pos("NVDA", 1_000.0, 123_540.0))
        assertEquals(Investoren.Wandel.ERHOEHT, Investoren.zeilen(inv, { _, _, _ -> 1.0 }).first().wandel)
        val z = Investoren.zeilen(inv, { k, a, b -> faktor(k, a, b) }).first()
        assertEquals(Investoren.Wandel.GLEICH, z.wandel)
        assertEquals(1_000.0, z.stueckVorher, 1e-9)
        assertEquals(Investoren.Wandel.GLEICH, Handelnde.investoren(listOf(inv), ::faktor) { it == "NVDA" }.single().wandel)
        assertEquals(1_000.0, Handelnde.investoren(listOf(inv), ::faktor) { it == "NVDA" }.single().stueckVorher!!, 1e-9)
    }

    @Test(timeout = 20_000)
    fun kandidatenNurBeiSplitartigemSprung() {
        val nv = investor(pos("NVDA", 100.0, 90_300.0), pos("NVDA", 1_000.0, 123_540.0))
        val ruhig = investor(pos("AAA", 100.0, 1_000.0), pos("AAA", 130.0, 1_400.0))
        assertEquals(setOf("NVDA"), SplitFaktoren.kandidaten(listOf(nv, ruhig)))
    }
}
