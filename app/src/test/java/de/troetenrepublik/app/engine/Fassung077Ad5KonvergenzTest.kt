package de.troetenrepublik.app.engine

import de.troetenrepublik.app.market.Gesamt13f
import de.troetenrepublik.app.market.HausDaten
import de.troetenrepublik.app.market.Politik
import de.troetenrepublik.app.market.SecInsider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

// [0.77 AD5] Konvergenz: Aktien, bei denen im selben Fenster mindestens zwei von drei Gruppen kaufen.
class Fassung077Ad5KonvergenzTest {

    private fun datei(name: String): String =
        listOf("src/main/assets/$name", "../app/src/main/assets/$name").map { File(it) }.first { it.exists() }.readText()

    private fun ms(tag: String) = LocalDate.parse(tag).toEpochDay() * 86_400_000L

    private fun kauf(k: String, person: String, tag: String, wert: Double = 50_000.0, kauf: Boolean = true) =
        SecInsider.Geschaeft(a = "$k-$person-$tag-$kauf", k = k, e = "$k Inc.", p = person, kauf = kauf, d = tag, f = tag, wert = wert)

    private fun kongress(symbol: String, person: String, gemeldet: String, kauf: Boolean = true) =
        KongressAuswertung.Handel(person, person, 'H', symbol, kauf, ms(gemeldet) - 20 * 86_400_000L, ms(gemeldet), 15_000.0)

    // Auszug aus assets/copytrading/gesamt13f.json (Quartal bis 30.06.2026), gekuerzt auf je einen groessten Halter:
    // A mit 136 Netto-Kaeufern und Zufluss, AAL mit 8, ABT mit Abfluss
    private val g13 = Gesamt13f.lesen(
        """{"stichtag":"2026-06-30","vorher":"2026-03-31","bis":"2026-08-31","melder":8827,"melderVorher":8839,"gruppe":8696,"aktien":{
        "A":{"h":1129,"hv":1075,"neu":132,"weg":78,"auf":421,"ab":339,"w":32950943882,"s":261625548,"sg":257453611,"sv":253074391,"f":581691796,"t":[["BlackRock, Inc.",25599001,3400315271,-1.2]],"n":"Agilent Technologies"},
        "AAL":{"h":577,"hv":570,"neu":88,"weg":84,"auf":177,"ab":173,"w":9152032116,"s":517915832,"sg":517306788,"sv":443493447,"f":1333807069,"t":[["Primecap Management Co/ca/",70732954,1278144480,44.4]],"n":"American Airlines Group Inc Com"},
        "ABT":{"h":2968,"hv":3115,"neu":148,"weg":277,"auf":972,"ab":1305,"w":120969294118,"s":1381230014,"sg":1348360557,"sv":1369298548,"f":-1899913309,"t":[["BlackRock, Inc.",152563476,13843609746,4.7]],"n":"Abbott Laboratories"}}}""",
    )!!

    private val fenster = Konvergenz.fenster(LocalDate.parse("2026-09-30"))

    private val insider = listOf(
        kauf("A", "Smith J", "2026-07-10"), kauf("A", "Lee K", "2026-08-01"), kauf("A", "Chen M", "2026-09-15"), kauf("A", "Chen M", "2026-09-16"),
        kauf("AAL", "Parker D", "2026-09-01"),
        kauf("XYZ", "A1", "2026-07-05"), kauf("XYZ", "A2", "2026-07-20"), kauf("XYZ", "A3", "2026-08-20"), kauf("XYZ", "A4", "2026-09-29"),
        kauf("ABT", "Ford R", "2026-08-02"), kauf("ABT", "Ford R", "2026-08-03"),
        // vor dem Fenster und Verkaeufe zaehlen nicht
        kauf("OLD", "Z1", "2026-05-01"), kauf("SELL", "S1", "2026-08-01", kauf = false),
    )

    private val handel = listOf(
        kongress("AAL", "k:Pelosi", "2026-08-10"), kongress("AAL", "k:Smith", "2026-09-02"), kongress("AAL", "k:Smith", "2026-09-03"),
        kongress("XYZ", "k:Jones", "2026-09-20"),
        kongress("OLD", "k:Jones", "2026-05-02"), kongress("SELL", "k:Jones", "2026-08-05", kauf = false),
        // gemeldet nach dem Fenster
        kongress("A", "k:Late", "2026-10-15"),
    )

    @Test(timeout = 20_000)
    fun fensterZaehltNeunzigTageBisZumEnde() {
        assertEquals("2026-07-02", fenster.ab)
        assertEquals("2026-09-30", fenster.bis)
    }

    @Test(timeout = 20_000)
    fun gruppenZaehlenVerschiedenePersonenImFenster() {
        val i = Konvergenz.insiderKaeufer(insider, fenster.ab, fenster.bis)
        assertEquals(3, i.getValue("A").first)
        assertEquals(4, i.getValue("XYZ").first)
        assertEquals("XYZ Inc.", i.getValue("XYZ").second)
        assertNull(i["OLD"])
        assertNull(i["SELL"])
        val k = Konvergenz.kongressKaeufer(handel, fenster.abMs, fenster.bisMs)
        assertEquals(2, k.getValue("AAL"))
        assertEquals(1, k.getValue("XYZ"))
        assertNull(k["OLD"])
        assertNull(k["SELL"])
        assertNull(k["A"])
        val f = Konvergenz.fondsKaeufer(g13)
        assertEquals(setOf("A", "AAL"), f.keys)
        assertEquals(136, f.getValue("A").netto)
    }

    @Test(timeout = 20_000)
    fun listeNimmtZweiVonDreiUndOrdnetDreiVonDreiZuerst() {
        val l = Konvergenz.liste(
            Konvergenz.insiderKaeufer(insider, fenster.ab, fenster.bis), Konvergenz.kongressKaeufer(handel, fenster.abMs, fenster.bisMs), Konvergenz.fondsKaeufer(g13),
        )
        assertEquals(listOf("AAL", "XYZ", "A"), l.map { it.symbol })
        val aal = l.first()
        assertEquals(3, aal.gruppen)
        assertEquals(1, aal.insider)
        assertEquals(2, aal.kongress)
        assertEquals(8, aal.fonds)
        // Name aus den Meldungen, sonst aus dem 13F-Bild
        assertEquals("AAL Inc.", aal.name)
        // ABT: nur Insider, Fonds verkaufen netto; kein Eintrag
        assertTrue(l.none { it.symbol == "ABT" })
    }

    @Test(timeout = 20_000)
    fun einWertAufDerListeFuerDieWertpapierseite() {
        val z = Konvergenz.fuerWert("AAL", insider, 2, g13.werte["AAL"], fenster)
        assertNotNull(z)
        assertEquals(3, z!!.gruppen)
        // nur Insider: nicht auf der Liste
        assertNull(Konvergenz.fuerWert("ABT", insider, 0, g13.werte["ABT"], fenster))
        // Insider und Fonds ohne Kongress
        assertEquals(2, Konvergenz.fuerWert("A", insider, 0, g13.werte["A"], fenster)!!.gruppen)
    }

    @Test(timeout = 20_000)
    fun aktienklassenWerdenZusammengefuehrt() {
        assertEquals("BRK-B", Konvergenz.kuerzel("BRK.B"))
        assertEquals("BRK-B", Konvergenz.kuerzel(" brk/b "))
        assertEquals("AAPL", Konvergenz.kuerzel("AAPL"))
    }

    @Test(timeout = 90_000)
    fun gebuendelteDatenGebenEineListe() {
        val sec = SecInsider.lesen(datei("copytrading/sec.json"))!!
        val f = Konvergenz.fenster(LocalDate.parse(sec.stand))
        // Kongress wie AppState.kongressHandelLaden: Haus (mitgeliefert) und Senat, nur Aktien mit Kuerzel
        val handel = ArrayList<KongressAuswertung.Handel>()
        val haus = HausDaten.lesen(datei("copytrading/haus.json"))!!
        for (m in haus.meldungen) haus.geschaefte[m.docId]?.forEach { g ->
            val k = g.kuerzel ?: return@forEach
            if (g.typ == "ST" && (g.kauf || g.verkauf) && g.mitte > 0) handel += KongressAuswertung.Handel("k:" + m.person, m.name, 'H', Konvergenz.kuerzel(k), g.kauf, g.datum, m.datum, g.mitte, g.inhaber)
        }
        val senat = Politik.senat(datei("copytrading/senat.json"))!!
        for (sm in senat.meldungen) if (!sm.papier) {
            val t = Politik.tag(sm.eingang)
            sm.geschaefte.forEach { x ->
                val g = Politik.geschaeft(x, t)
                val k = g.kuerzel ?: return@forEach
                if (g.typ == "ST" && (g.kauf || g.verkauf) && g.mitte > 0) handel += KongressAuswertung.Handel("n:" + sm.person, sm.name, 'S', Konvergenz.kuerzel(k), g.kauf, g.datum, t, g.mitte, g.inhaber)
            }
        }
        val g13 = Gesamt13f.lesen(datei("copytrading/gesamt13f.json"))!!
        val l = Konvergenz.liste(Konvergenz.insiderKaeufer(sec.geschaefte, f.ab, f.bis), Konvergenz.kongressKaeufer(handel, f.abMs, f.bisMs), Konvergenz.fondsKaeufer(g13))
        assertTrue(l.isNotEmpty())
        assertTrue(l.all { it.gruppen >= 2 })
        assertEquals(l.map { it.gruppen }, l.map { it.gruppen }.sortedDescending())
        val drei = l.count { it.gruppen == 3 }
        println("Konvergenz: Fenster ${f.ab} bis ${f.bis}, ${l.size} Werte, davon $drei mit drei Gruppen (Kongress-Kaeufe im Fenster: ${handel.count { it.kauf && it.gemeldet in f.abMs..f.bisMs }})")
        l.take(3).forEach { println("  ${it.symbol} (${it.name}): ${it.insider} Insider, ${it.kongress} Abgeordnete, ${it.fonds} Netto-Kaeufer") }
        if (drei == 0) l.take(3).forEach { println("  zwei Gruppen: ${it.symbol} ${it.insider}/${it.kongress}/${it.fonds}") }
    }
}
