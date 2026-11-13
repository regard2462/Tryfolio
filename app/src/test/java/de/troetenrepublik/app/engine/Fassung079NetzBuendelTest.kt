package de.troetenrepublik.app.engine

import de.troetenrepublik.app.engine.NetzTestHilfe.HEUTE
import de.troetenrepublik.app.i18n.NetzTexte
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Gepflegtes Buendel der Verflechtungen (assets/netz/kanten.json): lesen, Knoten und Kanten pruefen, Doppelte, Links, Guete.
class Fassung079NetzBuendelTest {

    private val b get() = NetzTestHilfe.buendel
    private val g get() = b.graph

    @Test fun buendelIstOhneProbleme() {
        assertEquals(emptyList<String>(), b.probleme)
        assertTrue("mindestens 40 belegte Kanten", g.kanten.size >= 40)
        assertTrue(b.stand.matches(Regex("\\d{4}-\\d{2}-\\d{2}")))
    }

    @Test fun jedeKanteHatLinkArtUndExistierendeKnoten() {
        for (k in g.kanten) {
            assertTrue(k.id, k.quelle.url.startsWith("https://"))
            assertTrue(k.id, k.quelle.name.isNotBlank())
            assertNotNull(k.id, g.knoten[k.von]); assertNotNull(k.id, g.knoten[k.nach])
            assertTrue(k.id, k.von != k.nach)
            k.weitere.forEach { assertTrue(k.id, it.url.startsWith("https://")) }
        }
    }

    @Test fun kennungenSindEindeutigUndLinksSindEinzeldokumente() {
        assertEquals(g.kanten.size, g.kanten.map { it.id }.toSet().size)
        for (k in g.kanten) {
            assertTrue(k.id + " " + k.quelle.url, Netz.einzeldokument(k.quelle.url))
            k.weitere.forEach { assertTrue(k.id + " " + it.url, Netz.einzeldokument(it.url)) }
        }
    }

    @Test fun nurDokumentierteKantenSindImBuendel() {
        // Wettbewerber, Mandate und Staatsgeld werden zur Laufzeit aus dem jeweiligen Dokument gelesen, nicht gepflegt
        assertTrue(g.kanten.none { it.art == Netz.Art.KONKURRIERT || it.art == Netz.Art.MANDAT })
        assertTrue(g.kanten.none { it.guete == Netz.Guete.C })
        // Stand ist das Datum des Dokuments, nie ein Abrufdatum nach den Dokumenten
        for (k in g.kanten) assertTrue(k.id, k.stand <= "2026-11-13")
        assertTrue(g.kanten.count { it.guete == Netz.Guete.A } >= 20)
    }

    @Test fun gueteAHatPrimaerquelleUndBCHabenWarnzeile() {
        val a = g.kanten.filter { it.guete == Netz.Guete.A }
        assertTrue(a.size >= 20)
        a.forEach { assertTrue(it.id, it.quelle.primaer) }
        for (gu in listOf("B", "C")) assertTrue(NetzTexte.text(true, "warn.$gu").isNotBlank() && NetzTexte.text(false, "warn.$gu").isNotBlank())
        assertEquals(0, g.kanten.count { it.guete == Netz.Guete.C })
    }

    @Test fun hinweiseSindZweisprachigUndOhneGedankenstrich() {
        for (k in g.kanten) {
            if (k.hinweis != null) assertFalse(k.id, k.hinweisEn.isNullOrBlank())
            for (t in listOfNotNull(k.hinweis, k.hinweisEn)) {
                assertFalse(k.id + " " + t, t.contains('—') || t.contains('–') || t.contains(';') || t.contains('?'))
            }
            if (k.programm != null) assertFalse(k.id, k.programmEn.isNullOrBlank())
        }
    }

    @Test fun umfangIstNurGenanntUndPlausibel() {
        for (k in g.kanten) {
            val u = k.umfang ?: continue
            assertTrue(k.id, u.wert > 0)
            if (u.einheit == Netz.Einheit.PCT) assertTrue(k.id, u.wert <= 100.0)
            if (u.bezug != null) assertEquals(k.id, Netz.Einheit.PCT, u.einheit)
        }
    }

    @Test fun teilSchluesselHabenTexte() {
        for (k in g.kanten) if (k.teil != null) {
            assertTrue(k.id + " " + k.teil, NetzTexte.text(true, "teil." + k.teil).isNotBlank())
            assertTrue(k.id + " " + k.teil, NetzTexte.text(false, "teil." + k.teil).isNotBlank())
        }
    }

    @Test fun alleKnotenWerdenGenutztUndSymboleSindEindeutig() {
        val genutzt = g.kanten.flatMap { listOf(it.von, it.nach) }.toSet()
        assertEquals(g.knoten.keys, genutzt)
        val symbole = g.knoten.values.flatMap { k -> k.symbole.map { Netz.symbolSchluessel(it) to k.id } }.distinct()
        assertEquals(symbole.size, symbole.map { it.first }.toSet().size)
    }

    @Test fun knotenFuerSymbolFindetAliasUndKlassen() {
        assertEquals("NVDA", g.knotenFuerSymbol("NVDA")?.id)
        assertEquals("GOOGL", g.knotenFuerSymbol("GOOG")?.id)
        assertEquals("BRK-B", Netz.symbolSchluessel("BRK.B"))
        assertEquals("BRK-B", Netz.symbolSchluessel("brk-b"))
        assertEquals("TSM", g.knotenFuerSymbol("2330.TW")?.id)
        assertNull(g.knotenFuerSymbol("NICHTDA"))
        assertEquals("NVDA", g.knotenFuerName("NVIDIA Corporation")?.id)
    }

    @Test fun ausOptionenDerRechercheSindDaMitRichtigerRichtung() {
        // Nvidia an OpenAI (Beteiligung), OpenAI kauft bei Nvidia: Kreislauf
        val nvOa = g.kanten.filter { it.von == "NVDA" && it.nach == "OPENAI" }
        assertTrue(nvOa.any { it.art == Netz.Art.HAELT_ANTEIL })
        val absicht = nvOa.first { it.art == Netz.Art.FINANZIERT }
        assertEquals(Netz.Status.ABSICHT, absicht.status)
        assertTrue(absicht.umfang!!.hoechstens)
        assertTrue(g.kanten.any { it.von == "OPENAI" && it.nach == "NVDA" && it.art == Netz.Art.KAUFT })
        // Absichtserklaerungen sind als solche gekennzeichnet
        assertTrue(g.kanten.any { it.status == Netz.Status.ABSICHT })
        // Optionsscheine und Garantie
        assertEquals(2, g.kanten.count { it.art == Netz.Art.OPTIONSSCHEIN })
        assertTrue(g.kanten.any { it.art == Netz.Art.GARANTIE && it.von == "NVDA" })
    }

    @Test fun anthropicNachDemProspekt() {
        // S-1/A vom 10.11.2026: Anteile nach dem Boersengang und die sieben Kaeufer der Privatplatzierung
        val a = g.kanten.filter { it.nach == "ANTHROPIC" && it.guete == Netz.Guete.A }
        val google = a.single { it.von == "GOOGL" && it.art == Netz.Art.HAELT_ANTEIL }
        val amazon = a.single { it.von == "AMZN" && it.art == Netz.Art.HAELT_ANTEIL }
        assertEquals(13.7, google.umfang!!.wert, 1e-9)
        assertEquals(14.7, amazon.umfang!!.wert, 1e-9)
        assertEquals("2026-11-10", google.stand)
        assertTrue(google.quelle.url.contains("anthropicpbc-sx1ax2.htm") && google.quelle.primaer)
        // Zusagen vom November 2025 (Microsoft, Nvidia) bleiben als eigene Kanten mit eigenem Stand
        val platzierung = a.filter { it.art == Netz.Art.FINANZIERT && it.stand == "2026-11-10" }
        assertEquals(setOf("GOOGL", "NVDA", "AMD", "AVGO", "MU", "MSFT", "TSM"), platzierung.map { it.von }.toSet())
        assertEquals(26.0, platzierung.sumOf { it.umfang!!.wert } / 1e9, 1e-9)
        platzierung.forEach { assertTrue(it.von, it.bedingt) }
        // Keine Kante mit der alten Angabe "hoechstens 12 %"
        assertTrue(g.kanten.none { it.umfang?.hoechstens == true && it.umfang?.einheit == Netz.Einheit.PCT })
    }

    @Test fun ueberfaelligerPflegetermineIstKeinVertragsende() {
        val k = NetzTestHilfe.kante("A", Netz.Art.LIEFERT, "B", ablauf = "2026-01-01")
        assertEquals(Netz.Status.DEFINITIV, k.statusAm(HEUTE))
        assertTrue(k.aktiv(HEUTE))
        assertTrue(k.ungeprueft(HEUTE))
        assertFalse(k.ungeprueft("2025-12-31"))
        assertFalse(NetzTestHilfe.kante("A", Netz.Art.LIEFERT, "B", status = Netz.Status.BEENDET).aktiv(HEUTE))
        val z = NetzTestHilfe.kante("A", Netz.Art.STAAT_FINANZIERT, "B", status = Netz.Status.ZURUECKGEZOGEN)
        assertFalse(z.aktiv(HEUTE))
    }

    @Test fun lesenVerwirftFehlerhafteKanten() {
        val text = """{"schema":1,"stand":"2026-11-13","knoten":[{"id":"A","typ":"firma","name":"A"},{"id":"B","typ":"firma","name":"B"}],
            "kanten":[
             {"id":"1","von":"A","nach":"B","art":"liefert","status":"definitiv","guete":"A","stand":"2026-11-13","quelle":{"name":"q","url":"https://x.org/dok","primaer":true}},
             {"id":"2","von":"A","nach":"B","art":"liefert","status":"definitiv","guete":"A","stand":"2026-11-13","quelle":{"name":"q","url":"https://x.org/dok","primaer":true}},
             {"id":"3","von":"A","nach":"C","art":"liefert","status":"definitiv","guete":"A","stand":"2026-11-13","quelle":{"name":"q","url":"https://x.org/dok","primaer":true}},
             {"id":"4","von":"A","nach":"B","art":"kauft","status":"definitiv","guete":"A","stand":"2026-11-13","quelle":{"name":"q","url":"http://x.org/","primaer":true}},
             {"id":"5","von":"A","nach":"B","art":"kauft","status":"definitiv","guete":"A","stand":"2026-11-13","quelle":{"name":"q","url":"https://x.org/dok","primaer":false}},
             {"id":"6","von":"A","nach":"B","art":"unbekannt","status":"definitiv","guete":"B","stand":"2026-11-13","quelle":{"name":"q","url":"https://x.org/dok","primaer":true}},
             {"id":"7","von":"A","nach":"B","art":"konkurriert","status":"definitiv","guete":"B","stand":"2026-11-13","quelle":{"name":"q","url":"https://x.org/dok","primaer":true}},
             {"id":"8","von":"B","nach":"A","art":"konkurriert","status":"definitiv","guete":"B","stand":"2026-11-13","quelle":{"name":"q","url":"https://x.org/dok","primaer":true}}
            ]}"""
        val r = Netz.lesen(text)!!
        // Mehrere Kanten gleicher Art zwischen denselben Firmen sind erlaubt (zwei Runden, mehrere Vertraege)
        assertEquals(listOf("1", "2", "7", "8"), r.graph.kanten.map { it.id })
        assertEquals(4, r.probleme.size)
        assertNull(Netz.lesen("kein json"))
        assertNull(Netz.lesen("""{"knoten":[],"kanten":[]}"""))
    }

    @Test fun graphMitFuegtHinzuOhneDoppelte() {
        val basis = NetzTestHilfe.netz(NetzTestHilfe.kante("A", Netz.Art.HAELT_ANTEIL, "B"))
        val neu = NetzTestHilfe.kante("A", Netz.Art.HAELT_ANTEIL, "B", herkunft = Netz.Herkunft.GEMELDET)
        val andere = NetzTestHilfe.kante("A", Netz.Art.TOCHTER, "B", herkunft = Netz.Herkunft.GEMELDET)
        val g2 = basis.mit(listOf(NetzTestHilfe.knoten("C")), listOf(neu, andere, NetzTestHilfe.kante("C", Netz.Art.LIEFERT, "X", herkunft = Netz.Herkunft.GEMELDET)))
        assertEquals(2, g2.kanten.size)
        assertEquals(setOf("A", "B", "C"), g2.knoten.keys)
        assertEquals(1, basis.kanten.size)
    }
}
