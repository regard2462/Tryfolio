package de.troetenrepublik.app.engine

import de.troetenrepublik.app.engine.Suchgruppen.KryptoListe
import de.troetenrepublik.app.engine.Suchgruppen.KryptoZeile
import de.troetenrepublik.app.i18n.De
import de.troetenrepublik.app.i18n.En
import de.troetenrepublik.app.market.Catalog
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

// Suche: Einstiege Indizes, Rohstoffe, Waehrungen und die Listen der Krypto-Seite; erfundene Kurse, ohne Netz.
class Fassung078Y2SucheTest {

    private val katalog: Catalog.Data by lazy {
        val datei = listOf(File("src/main/assets/katalog.json"), File("app/src/main/assets/katalog.json"), File("../app/src/main/assets/katalog.json")).first { it.exists() }
        Json { ignoreUnknownKeys = true }.decodeFromString(Catalog.Data.serializer(), datei.readText())
    }

    private fun basiswerte(klasse: String) = katalog.basiswerte.filter { it.k == klasse }.map { it.s }

    // ------------------------------------------------------------ Rohstoffe

    @Test
    fun rohstoffGruppen() {
        assertEquals("EDELMETALLE", Suchgruppen.rohstoffGruppe("GC=F"))
        assertEquals("EDELMETALLE", Suchgruppen.rohstoffGruppe("PA=F"))
        assertEquals("ENERGIE", Suchgruppen.rohstoffGruppe("BZ=F"))
        assertEquals("ENERGIE", Suchgruppen.rohstoffGruppe("ng=f"))
        assertEquals("INDUSTRIEMETALLE", Suchgruppen.rohstoffGruppe("HG=F"))
        assertEquals("INDUSTRIEMETALLE", Suchgruppen.rohstoffGruppe("ALI=F"))
        assertEquals("AGRAR", Suchgruppen.rohstoffGruppe("KC=F"))
        assertEquals("AGRAR", Suchgruppen.rohstoffGruppe("LE=F"))
        assertEquals("", Suchgruppen.rohstoffGruppe("XX=F"))
        assertEquals("", Suchgruppen.rohstoffGruppe("AAPL"))
    }

    @Test
    fun jederRohstoffHatEineGruppe() {
        val alle = basiswerte("ROHSTOFF") + Suchgruppen.ROHSTOFFE.map { it.symbol }
        assertTrue(alle.size >= 20)
        for (s in alle) assertTrue(s, Suchgruppen.rohstoffGruppe(s) in Suchgruppen.ROHSTOFF_GRUPPEN)
        // Jede Gruppe ist besetzt
        for (g in Suchgruppen.ROHSTOFF_GRUPPEN) assertTrue(g, alle.any { Suchgruppen.rohstoffGruppe(it) == g })
    }

    // ------------------------------------------------------------ Waehrungen

    @Test
    fun waehrungsGruppen() {
        assertEquals("HAUPT", Suchgruppen.waehrungsGruppe("EURUSD=X"))
        assertEquals("HAUPT", Suchgruppen.waehrungsGruppe("EURJPY=X"))
        assertEquals("HAUPT", Suchgruppen.waehrungsGruppe("GBPEUR=X"))
        assertEquals("NEBEN", Suchgruppen.waehrungsGruppe("EURSEK=X"))
        assertEquals("NEBEN", Suchgruppen.waehrungsGruppe("EURHKD=X"))
        assertEquals("SCHWELLEN", Suchgruppen.waehrungsGruppe("EURTRY=X"))
        assertEquals("SCHWELLEN", Suchgruppen.waehrungsGruppe("EURPLN=X"))
        assertEquals("OHNE_EURO", Suchgruppen.waehrungsGruppe("USDJPY=X"))
        assertEquals("OHNE_EURO", Suchgruppen.waehrungsGruppe("AUDUSD=X"))
        assertEquals("", Suchgruppen.waehrungsGruppe("EURXXX=X"))
        assertEquals("", Suchgruppen.waehrungsGruppe("EUR=X"))
    }

    @Test
    fun jedesPaarHatEineGruppe() {
        val alle = basiswerte("WAEHRUNG") + Suchgruppen.WAEHRUNGEN.map { it.symbol }
        for (s in alle) assertTrue(s, Suchgruppen.waehrungsGruppe(s) in Suchgruppen.WAEHRUNGS_GRUPPEN)
        for (g in Suchgruppen.WAEHRUNGS_GRUPPEN) assertTrue(g, alle.any { Suchgruppen.waehrungsGruppe(it) == g })
        // Die Zusatzliste nennt nur Paare zum Euro und kein Paar doppelt
        assertTrue(Suchgruppen.WAEHRUNGEN.all { it.symbol.startsWith("EUR") && it.symbol.endsWith("=X") && it.de == "EUR/" + it.waehrung })
        assertEquals(alle.size, alle.distinct().size)
    }

    // ------------------------------------------------------------ Indizes

    @Test
    fun indexRegionen() {
        assertEquals("DEUTSCHLAND", Suchgruppen.indexRegion("DE"))
        assertEquals("EUROPA", Suchgruppen.indexRegion("ES"))
        assertEquals("EUROPA", Suchgruppen.indexRegion("EU"))
        assertEquals("NORDAMERIKA", Suchgruppen.indexRegion("CA"))
        assertEquals("ASIEN_PAZIFIK", Suchgruppen.indexRegion("KR"))
        assertEquals("SCHWELLENLAENDER", Suchgruppen.indexRegion("BR"))
        // Die Region des Katalogs gilt, ausser sie ist nur „GLOBAL“
        assertEquals("EUROPA", Suchgruppen.indexRegion("", "EUROPA"))
        assertEquals("NORDAMERIKA", Suchgruppen.indexRegion("US", "GLOBAL"))
        assertEquals("GLOBAL", Suchgruppen.indexRegion(""))
    }

    @Test
    fun jederIndexHatEineRegionDerAuswahl() {
        for (u in katalog.basiswerte.filter { it.k == "INDEX" }) assertTrue(u.s, Suchgruppen.indexRegion(u.c, u.r) in Suchgruppen.REGIONEN)
        for (w in Suchgruppen.INDIZES) assertTrue(w.symbol, Suchgruppen.indexRegion(w.land) in Suchgruppen.REGIONEN)
        val alle = basiswerte("INDEX") + Suchgruppen.INDIZES.map { it.symbol }
        assertEquals(alle.size, alle.distinct().size)
        assertTrue(alle.size >= 40)
    }

    // ------------------------------------------------------------ Krypto

    @Test
    fun kryptoKategorien() {
        assertEquals("UNI", Suchgruppen.kryptoKuerzel("UNI7083-EUR"))
        assertEquals("BTC", Suchgruppen.kryptoKuerzel("btc-usd"))
        assertEquals("LAYER1", Suchgruppen.kryptoKategorie("BTC-EUR"))
        assertEquals("LAYER1", Suchgruppen.kryptoKategorie("ETH-USD"))
        assertEquals("ZAHLUNG", Suchgruppen.kryptoKategorie("XRP-EUR"))
        assertEquals("STABLECOIN", Suchgruppen.kryptoKategorie("USDT-EUR"))
        assertEquals("DEFI", Suchgruppen.kryptoKategorie("UNI7083-EUR"))
        assertEquals("INFRASTRUKTUR", Suchgruppen.kryptoKategorie("POL28321-EUR"))
        assertEquals("MEME", Suchgruppen.kryptoKategorie("DOGE-EUR"))
        assertEquals("", Suchgruppen.kryptoKategorie("ABC-EUR"))
        val alle = basiswerte("KRYPTO") + Suchgruppen.KRYPTO.map { it.symbol }
        assertEquals(alle.size, alle.distinct().size)
        for (s in alle) assertTrue(s, Suchgruppen.kryptoKategorie(s) in Suchgruppen.KRYPTO_KATEGORIEN)
        for (k in Suchgruppen.KRYPTO_KATEGORIEN) assertTrue(k, alle.any { Suchgruppen.kryptoKategorie(it) == k })
    }

    private val zeilen = listOf(
        KryptoZeile("BTC-EUR", "Bitcoin", kurs = 60_000.0, tag = 1.0, woche = -4.0, marktwert = 1_200e9, umsatz = 20e9, hoch52 = 90_000.0),
        KryptoZeile("ETH-EUR", "Ethereum", kurs = 2_000.0, tag = -2.0, woche = 6.0, marktwert = 240e9, umsatz = 12e9, hoch52 = 4_000.0),
        KryptoZeile("USDT-EUR", "Tether", kurs = 0.9, tag = 0.0, woche = 0.1, marktwert = 150e9, umsatz = 40e9, hoch52 = 0.95),
        KryptoZeile("DOGE-EUR", "Dogecoin", kurs = 0.1, tag = 8.0, woche = -1.0, marktwert = 15e9, umsatz = 1e9, hoch52 = 0.1),
        KryptoZeile("LINK-EUR", "Chainlink"),
    )

    @Test
    fun listenReihenfolge() {
        fun kuerzel(l: KryptoListe, woche: Boolean = false, kategorie: String = "") = Suchgruppen.kryptoListe(zeilen, l, woche, kategorie).map { it.symbol.substringBefore('-') }
        assertEquals(listOf("BTC", "ETH", "USDT", "DOGE"), kuerzel(KryptoListe.MARKTWERT))
        assertEquals(listOf("USDT", "BTC", "ETH", "DOGE"), kuerzel(KryptoListe.UMSATZ))
        // Gewinner nur im Plus, Verlierer nur im Minus; unveraendert steht in keiner der beiden
        assertEquals(listOf("DOGE", "BTC"), kuerzel(KryptoListe.GEWINNER))
        assertEquals(listOf("ETH"), kuerzel(KryptoListe.VERLIERER))
        assertEquals(listOf("ETH", "USDT"), kuerzel(KryptoListe.GEWINNER, woche = true))
        assertEquals(listOf("BTC", "DOGE"), kuerzel(KryptoListe.VERLIERER, woche = true))
        // Am Hoch zuerst
        assertEquals(listOf("DOGE", "USDT", "BTC", "ETH"), kuerzel(KryptoListe.HOCH))
        assertEquals(listOf("BTC", "ETH"), kuerzel(KryptoListe.MARKTWERT, kategorie = "LAYER1"))
        assertEquals(listOf("DOGE"), kuerzel(KryptoListe.GEWINNER, kategorie = "MEME"))
        assertTrue(kuerzel(KryptoListe.MARKTWERT, kategorie = "DEFI").isEmpty())
    }

    @Test
    fun abstandZumHoch() {
        assertEquals(33.333, zeilen[0].unterHoch!!, 0.001)
        assertEquals(50.0, zeilen[1].unterHoch!!, 1e-9)
        assertEquals(0.0, zeilen[3].unterHoch!!, 1e-9)
        // Kurs ueber dem gemeldeten Hoch zaehlt als am Hoch, ohne Kurs oder Hoch gibt es keinen Abstand
        assertEquals(0.0, KryptoZeile("X-EUR", "X", kurs = 11.0, hoch52 = 10.0).unterHoch!!, 1e-9)
        assertNull(zeilen[4].unterHoch)
        assertNull(KryptoZeile("X-EUR", "X", kurs = 11.0).unterHoch)
    }

    @Test
    fun bitcoinAnteil() {
        val a = Suchgruppen.bitcoinAnteil(zeilen)!!
        assertEquals(1_200.0 / 1_605.0 * 100.0, a.first, 1e-9)
        assertEquals(4, a.second)
        // Ohne Bitcoin, ohne Marktwert von Bitcoin oder mit zu vielen Luecken keine Zahl
        assertNull(Suchgruppen.bitcoinAnteil(zeilen.drop(1)))
        assertNull(Suchgruppen.bitcoinAnteil(listOf(zeilen[0].copy(marktwert = 0.0), zeilen[1])))
        assertNull(Suchgruppen.bitcoinAnteil(zeilen + KryptoZeile("A-EUR", "A")))
        assertNull(Suchgruppen.bitcoinAnteil(emptyList()))
    }

    // ------------------------------------------------------------ Texte

    @Test
    fun jedeGruppeHatEinenNamenInBeidenSprachen() {
        val schluessel = Suchgruppen.ROHSTOFF_GRUPPEN + Suchgruppen.WAEHRUNGS_GRUPPEN + Suchgruppen.KRYPTO_KATEGORIEN
        assertEquals(schluessel.size, schluessel.distinct().size)
        for (k in schluessel) {
            assertNotEquals(k, De.suGruppe(k))
            assertNotEquals(k, En.suGruppe(k))
        }
        for (l in KryptoListe.entries) {
            assertNotEquals(l.name, De.kyListe(l.name)); assertNotEquals(l.name, En.kyListe(l.name))
            for (w in listOf(false, true)) { assertNotEquals(l.name, De.kyListeTitel(l.name, w)); assertNotEquals(l.name, En.kyListeTitel(l.name, w)) }
        }
    }
}
