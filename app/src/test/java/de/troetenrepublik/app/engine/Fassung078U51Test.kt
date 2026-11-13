package de.troetenrepublik.app.engine

import de.troetenrepublik.app.i18n.EntnahmeTexte
import de.troetenrepublik.app.i18n.FaktorTexte
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

// Entnahmeplan, Faktoren und Finanzwissen: gerundetes Zielvermoegen, Texte in beiden Sprachen, Themenliste ohne Fragen.
class Fassung078U51Test {
    private fun datei(vararg orte: String) = orte.map { File(it) }.first { it.exists() }
    private fun asset(name: String) = datei("src/main/assets/$name", "app/src/main/assets/$name", "../app/src/main/assets/$name").readText(Charsets.UTF_8)

    @Test fun zielvermoegenGerundet() {
        assertEquals(755_000.0, Entnahmeplan.zielAnzeige(754_881.0), 0.0)
        assertEquals(1_250_000.0, Entnahmeplan.zielAnzeige(1_249_600.0), 0.0)
        assertEquals(10_000.0, Entnahmeplan.zielAnzeige(10_499.0), 0.0)
        assertEquals(11_000.0, Entnahmeplan.zielAnzeige(10_501.0), 0.0)
        // unter 10.000 auf 100
        assertEquals(9_400.0, Entnahmeplan.zielAnzeige(9_449.0), 0.0)
        assertEquals(0.0, Entnahmeplan.zielAnzeige(0.0), 0.0)
    }

    @Test fun texteInBeidenSprachen() {
        assertEquals(EntnahmeTexte.schluessel(true), EntnahmeTexte.schluessel(false))
        assertEquals(FaktorTexte.schluessel(true), FaktorTexte.schluessel(false))
        for (k in listOf("start.wertpapiere", "start.gesamt", "start.ziel", "start.eigen", "ziel.gewinn", "ziel.gewinnHint")) {
            assertTrue(k, k in EntnahmeTexte.schluessel(true))
            for (d in listOf(true, false)) assertTrue("$k $d", EntnahmeTexte.text(d, k).isNotBlank())
        }
        assertEquals("Davon Kursgewinn", EntnahmeTexte.text(true, "ziel.gewinn"))
        assertEquals("Of which price gain", EntnahmeTexte.text(false, "ziel.gewinn"))
        for (k in listOf("alpha.titel", "alpha.ueber", "alpha.unter", "alpha.belegt", "alpha.offen", "erklaert", "beitrag.rest", "hinweis")) {
            for (d in listOf(true, false)) {
                val t = FaktorTexte.text(d, k)
                assertTrue("$k $d", t.isNotBlank())
                listOf("—", "–", "…").forEach { assertFalse("$k: $it", t.contains(it)) }
            }
        }
        // Fachbegriff nur hinter dem Info-Symbol
        assertFalse(FaktorTexte.text(true, "alpha.titel").contains("Alpha"))
        assertTrue(FaktorTexte.text(true, "alpha.info").contains("Alpha"))
    }

    private fun module(roh: String) = Json.parseToJsonElement(roh).jsonObject.getValue("module").jsonArray.map { it.jsonObject }

    // Die Themenliste und das Quiz benutzen dasselbe Verzeichnis: Titel je Thema in beiden Sprachen vorhanden, gleiche Kennungen
    @Test fun themenlisteDeEn() {
        val de = module(asset("finanzwissen/data/kurs.json"))
        val en = module(asset("finanzwissen/data/en/kurs.json"))
        assertEquals(de.map { it.getValue("id").jsonPrimitive.content }, en.map { it.getValue("id").jsonPrimitive.content })
        for (m in de + en) assertTrue(m.getValue("titel").jsonPrimitive.content.isNotBlank())
        assertEquals("Geld, Zins und Zeit", de.first().getValue("titel").jsonPrimitive.content)
    }

    // Finanzwissen kommt ohne Fragen aus: keine Fragendateien mehr im Paket
    @Test fun ohneFragen() {
        for (sprache in listOf("", "en/")) assertTrue(datei("src/main/assets/finanzwissen/data", "app/src/main/assets/finanzwissen/data", "../app/src/main/assets/finanzwissen/data").let { !java.io.File(it, "${sprache}fragen.json").exists() })
        assertFalse(asset("finanzwissen/js/ansichten.js").contains("K.t('Frage {0} von {1}'"))
    }
}
