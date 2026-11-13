package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.WidgetKarten
import de.troetenrepublik.app.i18n.WichtigTexte
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Widget-Reihenfolge, Durchbrueche zu jeder Zeile der Trendliste und Durchbrueche als Unterkategorie von „Wichtigste“.
class Fassung079U67Test {

    private val jetzt = 1_794_000_000_000L
    private val tag = 86_400_000L

    private fun meldung(thema: String, titel: String, zeit: Long, link: String = "https://x/$thema/$titel") =
        Durchbrueche.Meldung(thema, titel, link, zeit, "Quelle")

    // 1. Widgets: Durchbrueche gleich nach der Stimmung

    @Test
    fun durchbruecheStehenObenInDerWidgetListe() {
        val i = WidgetKarten.ANLEGBAR.indexOf(WidgetKarten.DURCHBRUECHE)
        assertTrue("Platz $i", i in 0..2)
        assertEquals(WidgetKarten.STIMMUNG, WidgetKarten.ANLEGBAR.first())
        assertEquals(1, WidgetKarten.ANLEGBAR.count { it == WidgetKarten.DURCHBRUECHE })
        assertEquals(WidgetKarten.ANLEGBAR.size, WidgetKarten.ANLEGBAR.toSet().size)
    }

    // 2. Jede Zeile der Trendliste hat Themen mit Quellen

    @Test
    fun jedeZeileHatEinThemaMitSuchbegriffenInBeidenSprachen() {
        val ids = Durchbrueche.THEMEN.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        TrendListe.EINTRAEGE.forEach { e ->
            assertTrue("${e.id} ohne Thema", e.durchbrueche.isNotEmpty())
            e.durchbrueche.forEach { assertNotNull("${e.id}: $it", Durchbrueche.thema(it)) }
        }
        Durchbrueche.THEMEN.forEach { t ->
            assertTrue("${t.id} Name", t.de.isNotBlank() && t.en.isNotBlank())
            assertTrue("${t.id} Stichwoerter", t.stichwoerter.isNotEmpty())
            assertTrue("${t.id} Fonds", t.etfs.isNotEmpty())
            assertTrue("${t.id} Suche en", Durchbrueche.suchbegriff(t, false).let { it.contains("breakthrough") && it.endsWith("when:14d") })
            assertTrue("${t.id} Suche de", Durchbrueche.suchbegriff(t, true).let { it.contains("Durchbruch") && it.endsWith("when:14d") })
        }
        // Jedes Thema der Liste steht in genau einer Zeile
        assertEquals(ids.sorted(), TrendListe.EINTRAEGE.flatMap { it.durchbrueche }.sorted())
    }

    @Test
    fun trendBloeckeGebenJedemThemaEinenBlock() {
        val leer = Durchbrueche.trendBloecke(null, emptyList(), jetzt)
        assertEquals(Durchbrueche.THEMEN.map { it.id }.toSet(), leer.map { it.thema.id }.toSet())
        assertTrue(leer.all { it.neu.isEmpty() })
        val z = TrendListe.zeilen(emptyMap(), emptyMap(), leer)
        assertEquals(TrendListe.EINTRAEGE.size, z.size)
        assertTrue(z.all { it.bloecke.isNotEmpty() })
        // Ein Thema mit Meldung steht nur einmal da, mit seiner Meldung
        val m = listOf(meldung("solar", "Solar zeigt Rekord", jetzt - 1000))
        val mit = Durchbrueche.trendBloecke(null, m, jetzt)
        assertEquals(1, mit.count { it.thema.id == "solar" })
        assertEquals(1, mit.first { it.thema.id == "solar" }.neu.size)
    }

    @Test
    fun feedmeldungenFindenDieNeuenThemen() {
        assertEquals("wasserstoff", Durchbrueche.zuordnen("New fuel cell design sets efficiency record")?.id)
        assertEquals("industrie", Durchbrueche.zuordnen("Scientists unveil a new 3D printing method for steel parts")?.id)
        assertEquals("krypto", Durchbrueche.zuordnen("Bitcoin network hash rate reaches record")?.id)
        // Bisherige Zuordnung bleibt
        assertEquals("raumfahrt", Durchbrueche.zuordnen("Starship reaches orbit again")?.id)
        assertNull(Durchbrueche.zuordnen("Don't miss: Meteor showers light up the night sky"))
    }

    @Test
    fun spieleDurfenDasWortSpielNennen() {
        val titel = "World's first fully playable video game runs on a quantum computer"
        // Ohne Ausnahme gilt „game“ als Produktmeldung
        assertFalse(Durchbrueche.durchbruch(titel, "Heise"))
        val r = Durchbrueche.Roh("spiele", titel, "https://x/spiel", jetzt - tag, "Heise")
        assertEquals(1, Durchbrueche.filtern(listOf(r), jetzt).size)
        // Sportmeldungen bleiben draussen
        val sport = Durchbrueche.Roh("spiele", "Team sets world record in esports league final", "https://x/sport", jetzt - tag, "Heise")
        assertTrue(Durchbrueche.filtern(listOf(sport), jetzt).isEmpty())
    }

    @Test
    fun dublettenZaehlenJeZeile() {
        val titel = "Researchers set world record for perovskite solar cell efficiency"
        val a = Durchbrueche.Roh("energie", titel, "https://x/1", jetzt - tag, "Phys.org")
        val b = Durchbrueche.Roh("solar", titel, "https://x/1", jetzt - tag, "Phys.org")
        val c = Durchbrueche.Roh("solar", titel.replace("Researchers", "Scientists"), "https://x/2", jetzt - tag + 1000, "Phys.org")
        val liste = Durchbrueche.filtern(listOf(a, b, c), jetzt)
        // Energie und Solar sind zwei Zeilen, in Solar bleibt die Geschichte einmal
        assertEquals(1, liste.count { it.thema == "energie" })
        assertEquals(1, liste.count { it.thema == "solar" })
    }

    // 5. Durchbrueche in Wichtigste

    @Test
    fun wichtigsteGruppiertNachThemenMitFrischestemZuerst() {
        val m = listOf(
            meldung("ki", "KI a", jetzt - 3 * tag),
            meldung("krebs", "Krebs a", jetzt - 1 * tag),
            meldung("ki", "KI b", jetzt - 2 * tag),
            meldung("fusion", "Fusion alt", jetzt - 20 * tag),
        ) + List(7) { meldung("solar", "Solar $it", jetzt - 5 * tag - it * 1000L) }
        val teil = WichtigThemen.durchbrueche(m, jetzt)!!
        assertEquals(listOf("krebs", "ki", "solar"), teil.gruppen.map { it.thema.id })
        assertEquals(3, teil.themen)
        // Hoechstens fuenf je Thema, aeltere als 14 Tage fehlen
        assertEquals(1 + 2 + Durchbrueche.JE_THEMA, teil.anzahl)
        assertEquals(listOf("KI b", "KI a"), teil.gruppen[1].meldungen.map { it.titel })
        assertTrue(teil.gruppen.none { it.thema.id == "fusion" })
    }

    @Test
    fun wichtigsteOhneMeldungenHatKeineUnterkategorie() {
        assertNull(WichtigThemen.durchbrueche(null, jetzt))
        assertNull(WichtigThemen.durchbrueche(emptyList(), jetzt))
        assertNull(WichtigThemen.durchbrueche(listOf(meldung("ki", "alt", jetzt - 30 * tag)), jetzt))
    }

    @Test
    fun tippOeffnetArtikelOderThema() {
        val m = meldung("quanten", "Quanten Rekord", jetzt - tag, link = "https://x/quanten-rekord")
        val e = WichtigThemen.alsEintrag(m)
        assertEquals("https://x/quanten-rekord", e.link)
        assertEquals("https://x/quanten-rekord", e.id)
        assertEquals("Quanten Rekord", e.titel)
        assertEquals(m.zeit, e.zeit)
        assertEquals("maerkte/trends/durchbrueche:quanten", WichtigThemen.wegZumThema("quanten"))
        // Das Thema steht in der Trendliste, der Weg fuehrt dorthin
        assertNotNull(TrendListe.zuDurchbruch("quanten"))
    }

    @Test
    fun zusammenfassungszeile() {
        assertEquals("12 neu in 5 Themen", WichtigTexte.durchbrueche(true, 12, 5))
        assertEquals("1 neu in 1 Thema", WichtigTexte.durchbrueche(true, 1, 1))
        assertEquals("12 new in 5 topics", WichtigTexte.durchbrueche(false, 12, 5))
        assertEquals("3 new in 1 topic", WichtigTexte.durchbrueche(false, 3, 1))
        assertEquals("Zum Thema", WichtigTexte.zumThema(true))
        assertEquals("Open topic", WichtigTexte.zumThema(false))
    }
}
