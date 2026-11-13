package de.troetenrepublik.app.engine

import de.troetenrepublik.app.i18n.PrognoseQuellenTexte
import de.troetenrepublik.app.market.Prognose
import de.troetenrepublik.app.market.PrognoseWeitere
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Manifold: gekuerzte Antworten der Schnittstelle, Namen und Kennungen der Nutzer durch erfundene ersetzt.
// Metaculus: Beispiele nach der veroeffentlichten Beschreibung der Schnittstelle (ohne Konto kein Abruf).
class PrognoseWeitereTest {

    private fun text(name: String): String =
        javaClass.classLoader!!.getResourceAsStream("prognose/$name")!!.readBytes().toString(Charsets.UTF_8)

    private val jetzt = 1_794_500_000_000L

    @Test
    fun manifoldListe() {
        val l = PrognoseWeitere.manifoldListeLesen(text("manifold_liste.json"), "politics-default")
        assertEquals(3, l.size)
        val e = l.first()
        assertEquals(Prognose.Quelle.MANIFOLD, e.quelle)
        assertEquals("ZZLRuEZpZl", e.id)
        assertEquals("Will Venezuelans be better off at the end of 2026?", e.titel)
        assertTrue(e.binaer)
        assertEquals(0.1153, e.maerkte.single().chance, 0.0001)
        assertEquals(2, e.maerkte.single().seiten.size)
        assertEquals(1.0, e.maerkte.single().seiten.sumOf { it.preis }, 1e-9)
        assertEquals(271_284.9, e.volumen, 0.1)
        assertEquals(8_843.7, e.volumen24h, 0.1)
        assertEquals(438, e.teilnehmer)
        assertEquals(1_798_761_540_000L, e.ende)
        assertTrue(e.link.startsWith("https://manifold.markets/"))
        assertEquals(listOf("politics"), e.schlagworte)
        assertTrue(e.maerkte.single().offen)
        assertFalse(Prognose.Quelle.MANIFOLD.wettbar)
    }

    @Test
    fun manifoldMarktUndAufloesung() {
        val e = PrognoseWeitere.manifoldEreignisText(text("manifold_markt.json"))!!
        assertTrue("world" in e.schlagworte && "politics" in e.schlagworte)
        assertEquals("politics-default", e.kategorie)
        assertTrue(e.beschreibung.isNotBlank())
        assertEquals(e.maerkte.single().chance, PrognoseWeitere.manifoldMarktText(text("manifold_markt.json"))!!.chance, 0.0)
        val ja = PrognoseWeitere.manifoldMarktText("""{"id":"a","outcomeType":"BINARY","question":"q","probability":0.97,"isResolved":true,"resolution":"YES","volume":5}""")!!
        assertFalse(ja.offen)
        assertEquals(0, ja.gewinner)
        assertEquals(1.0, ja.auszahlung!!, 0.0)
        val teil = PrognoseWeitere.manifoldMarktText("""{"id":"a","outcomeType":"BINARY","question":"q","probability":0.4,"isResolved":true,"resolution":"MKT","resolutionProbability":0.4}""")!!
        assertNull(teil.gewinner)
        assertEquals(0.4, teil.auszahlung!!, 0.0)
        // Andere Arten und Unlesbares
        assertNull(PrognoseWeitere.manifoldMarktText("""{"id":"a","outcomeType":"MULTIPLE_CHOICE","question":"q"}"""))
        assertNull(PrognoseWeitere.manifoldMarktText("kein json"))
        assertTrue(PrognoseWeitere.manifoldListeLesen("""{"message":"An unexpected error occurred."}""").isEmpty())
        // Ein fremder Link wird nicht uebernommen
        val fremd = PrognoseWeitere.manifoldEreignisText("""{"id":"a","outcomeType":"BINARY","question":"q","probability":0.4,"url":"https://example.org/x"}""")!!
        assertEquals(PrognoseWeitere.MANIFOLD_SEITE, fremd.link)
    }

    @Test
    fun manifoldVerlauf() {
        val t = text("manifold_handel.json")
        val v = PrognoseWeitere.manifoldVerlaufLesen(t)
        // Zehn Geschaefte, eines davon eine Ruecknahme
        assertEquals(9, v.size)
        assertEquals(v.sortedBy { it.first }, v)
        assertEquals(1_794_521_078_120L, v.last().first)
        assertEquals(0.1153, v.last().second, 0.0001)
        assertTrue(v.all { it.second in 0.0..1.0 })
        assertNull(PrognoseWeitere.manifoldHandelWeiter(t))
        assertEquals("handel009", PrognoseWeitere.manifoldHandelWeiter(t, voll = 10))
        assertTrue(PrognoseWeitere.manifoldVerlaufLesen("[]").isEmpty())
    }

    @Test
    fun ausduennen() {
        val p = (0 until 1000).map { it.toLong() to it / 1000.0 }
        val d = PrognoseWeitere.ausduennen(p, 400)
        assertEquals(400, d.size)
        assertEquals(p.first(), d.first())
        assertEquals(p.last(), d.last())
        assertEquals(p.take(5), PrognoseWeitere.ausduennen(p.take(5), 400))
    }

    @Test
    fun manifoldAdressen() {
        assertEquals(
            "https://api.manifold.markets/v0/search-markets?term=&sort=score&filter=open&contractType=BINARY&limit=15&offset=30&topicSlug=economics-default",
            PrognoseWeitere.manifoldListe("economics-default", 30),
        )
        assertFalse("topicSlug" in PrognoseWeitere.manifoldListe("", 0))
        assertTrue(PrognoseWeitere.manifoldSuche("fed & rates").contains("term=fed+%26+rates"))
        assertEquals("https://api.manifold.markets/v0/bets?contractId=abc&limit=1000&before=x1", PrognoseWeitere.manifoldHandel("abc", "x1"))
    }

    @Test
    fun metaculusListe() {
        val s = PrognoseWeitere.metaculusListeLesen(text("metaculus_liste.json"), 0, 20)!!
        assertEquals("20", s.weiter)
        // Eine Frage ohne ausgegebene Prognose, eine Zahlenfrage ohne Markt
        assertEquals(1, s.verborgen)
        assertEquals(listOf("90001", "90003", "90004"), s.ereignisse.map { it.id })
        val e = s.ereignisse.first()
        assertEquals(Prognose.Quelle.METACULUS, e.quelle)
        assertTrue(e.binaer)
        assertEquals(0.63, e.maerkte.single().chance, 1e-9)
        assertEquals("91001", e.maerkte.single().id)
        assertEquals("90001", e.maerkte.single().serie)
        assertEquals(312, e.teilnehmer)
        assertEquals("economy-business", e.kategorie)
        assertTrue("economy" in e.schlagworte && "business" in e.schlagworte)
        assertEquals("https://www.metaculus.com/questions/90001/", e.link)
        assertTrue(e.ende > 0)
        assertTrue(e.beschreibung.contains("Resolves Yes"))
        // Auswahl: ein Markt je Option, nach Wahrscheinlichkeit
        val wahl = s.ereignisse[1]
        assertEquals(listOf("Party B", "Party A", "Other"), wahl.maerkte.map { it.titel })
        assertEquals(0.6, wahl.maerkte.first().chance, 1e-9)
        // Gruppe: ein Markt je Teilfrage mit ihrer Bezeichnung
        val gruppe = s.ereignisse[2]
        assertEquals(listOf("Company A", "Company B"), gruppe.maerkte.map { it.titel })
        assertEquals(0.8, gruppe.maerkte.first().chance, 1e-9)
        assertFalse(Prognose.Quelle.METACULUS.wettbar)
        assertNull(PrognoseWeitere.metaculusListeLesen("""{"detail":"Authentication credentials were not provided."}"""))
    }

    @Test
    fun metaculusVerlaufUndToken() {
        val beitrag = text("metaculus_liste.json").let { t ->
            kotlinx.serialization.json.Json.parseToJsonElement(t).let { (it as kotlinx.serialization.json.JsonObject)["results"] as kotlinx.serialization.json.JsonArray }[0].toString()
        }
        assertNotNull(PrognoseWeitere.metaculusEreignisText(beitrag))
        val v = PrognoseWeitere.metaculusVerlaufLesen(beitrag, "91001")
        assertEquals(listOf(0.42, 0.58, 0.63), v.map { it.second })
        assertEquals(1_790_000_000_500L, v.first().first)
        assertTrue(PrognoseWeitere.metaculusVerlaufLesen(beitrag, "1").isEmpty())

        assertTrue(PrognoseWeitere.tokenGueltig("0123456789abcdef0123456789abcdef01234567"))
        assertFalse(PrognoseWeitere.tokenGueltig("kurz"))
        assertFalse(PrognoseWeitere.tokenGueltig("0123456789abcdef0123\r\nX-Kopf: 1"))
        assertNull(PrognoseWeitere.metaculusKopf(""))
        assertEquals("Token 0123456789abcdef0123", PrognoseWeitere.metaculusKopf(" 0123456789abcdef0123 ")!!["Authorization"])
        assertNull(PrognoseWeitere.metaculusBeitrag("12/../x"))
        assertEquals("https://www.metaculus.com/api/posts/90001/?with_cp=true&include_cp_history=true", PrognoseWeitere.metaculusBeitrag("90001"))
        val liste = PrognoseWeitere.metaculusListe("economy-business", 20, 20, "rate cut")
        assertTrue(liste.contains("statuses=open") && liste.contains("with_cp=true") && liste.contains("offset=20"))
        assertTrue(liste.contains("categories=economy-business") && liste.contains("search=rate+cut"))
    }

    @Test
    fun wichtigsteJeQuelle() {
        val manifold = PrognoseWeitere.manifoldListeLesen(text("manifold_liste.json"), "politics-default")
        // Bleibt: genug Umsatz in Mana. Raus: zu wenig Umsatz.
        assertNull(PrognoseAuswahl.grund(PrognoseAuswahl.merkmale(manifold[0], jetzt)))
        assertEquals(PrognoseAuswahl.Grund.VOLUMEN, PrognoseAuswahl.grund(PrognoseAuswahl.merkmale(manifold[1], jetzt)))
        // Sport ueber das Thema der Abfrage
        val sport = PrognoseWeitere.manifoldListeLesen(text("manifold_liste.json"), "sports-default")[2]
        assertEquals(PrognoseAuswahl.Grund.SPORT, PrognoseAuswahl.grund(PrognoseAuswahl.merkmale(sport, jetzt)))
        // Sammelkategorien ohne Trends und Sport
        val sammel = PrognoseAuswahl.sammelKategorien(PrognoseWeitere.MANIFOLD_KATEGORIEN)
        assertFalse("sports-default" in sammel || "" in sammel)
        assertTrue("economics-default" in sammel && "geopolitics" in sammel)
        // Metaculus: die Zahl der Prognostiker statt Umsatz
        val meta = PrognoseWeitere.metaculusListeLesen(text("metaculus_liste.json"))!!.ereignisse
        assertTrue(PrognoseAuswahl.wichtig(meta[0], jetzt))
        assertEquals(PrognoseAuswahl.Grund.VOLUMEN, PrognoseAuswahl.grund(PrognoseAuswahl.merkmale(meta[0].copy(teilnehmer = 5), jetzt)))
        // Jede Quelle bleibt fuer sich: gleiche Kennung, verschiedene Quellen, zwei Eintraege
        val gemischt = PrognoseAuswahl.ereignisse(listOf(manifold[0], meta[0].copy(id = manifold[0].id)), jetzt)
        assertEquals(2, gemischt.size)
        assertEquals(setOf(Prognose.Quelle.MANIFOLD, Prognose.Quelle.METACULUS), gemischt.map { it.quelle }.toSet())
    }

    @Test
    fun kategorienUndTexte() {
        Prognose.Quelle.entries.forEach { q -> assertTrue(q.name, Prognose.kategorien(q).isNotEmpty()) }
        assertEquals(PrognoseQuellenTexte.schluessel(true), PrognoseQuellenTexte.schluessel(false))
        for (k in PrognoseQuellenTexte.schluessel(true)) for (d in listOf(true, false)) {
            val t = PrognoseQuellenTexte.text(d, k, listOf("X"))
            // Manifold ohne Zusatz (0.79): der Kennzeichnungstext ist dort absichtlich leer
            if (!k.endsWith(".MANIFOLD")) assertTrue(k, t.isNotBlank())
            assertFalse(k, t.contains("{0}") || t.contains(" – ") || t.contains(" — ") || t.contains("?"))
        }
        // Kennzeichnung je Quelle ohne Echtgeld
        Prognose.Quelle.entries.filter { !it.wettbar }.forEach { q ->
            if (q == Prognose.Quelle.MANIFOLD) return@forEach
            assertTrue(PrognoseQuellenTexte.text(true, "art.${q.name}").isNotBlank())
            assertTrue(PrognoseQuellenTexte.text(true, "art.kurz.${q.name}").isNotBlank())
        }
        assertTrue(PrognoseQuellenTexte.text(true, "art.MANIFOLD").isBlank())
        assertTrue(PrognoseQuellenTexte.text(true, "art.METACULUS").contains("ohne Geldeinsatz"))
    }
}
