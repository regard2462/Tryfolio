package de.troetenrepublik.app.market

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Wikidata-Antworten (Auszuege im Format von wbsearchentities und wbgetentities) fuer das Firmenblatt.
class FirmaWikidataTest {
    private val suche = """{"searchinfo":{"search":"Lukoil"},"search":[{"id":"Q99","label":"Lukoil Person"},{"id":"Q329002","label":"Lukoil","description":"Russian oil company"}],"success":1}"""

    private fun anspruch(prop: String, wert: String, quali: String = "") =
        """"$prop":[{"mainsnak":{"snaktype":"value","property":"$prop","datavalue":{"value":$wert,"type":"x"}},"type":"statement"$quali}]"""

    private val eintraege = """{"entities":{
      "Q99":{"id":"Q99","claims":{${anspruch("P31", """{"entity-type":"item","id":"Q5"}""")}}},
      "Q329002":{"id":"Q329002","claims":{
        ${anspruch("P31", """{"entity-type":"item","id":"Q4830453"}""")},
        "P1128":[
          {"mainsnak":{"datavalue":{"value":{"amount":"+100000","unit":"1"}}},"qualifiers":{"P585":[{"datavalue":{"value":{"time":"+2019-01-01T00:00:00Z"}}}]}},
          {"mainsnak":{"datavalue":{"value":{"amount":"+104000","unit":"1"}}},"qualifiers":{"P585":[{"datavalue":{"value":{"time":"+2022-01-01T00:00:00Z"}}}]}}
        ],
        ${anspruch("P571", """{"time":"+1991-11-25T00:00:00Z","precision":11}""")},
        ${anspruch("P159", """{"entity-type":"item","id":"Q649"}""")},
        ${anspruch("P856", "\"https://lukoil.com\"")}
      }}}}"""

    @Test
    fun trefferInReihenfolge() {
        assertEquals(listOf("Q99", "Q329002"), FirmaWikidata.treffer(suche))
        assertTrue(FirmaWikidata.treffer("kein json").isEmpty())
    }

    @Test
    fun ersteFirmaUeberspringtMenschenUndNimmtDenJuengstenWert() {
        val r = FirmaWikidata.ersteFirma(eintraege, listOf("Q99", "Q329002"))
        assertNotNull(r)
        assertEquals("Q329002", r!!.kennung)
        assertEquals(104000L, r.mitarbeiter)
        assertEquals(2022, r.jahr)
        assertEquals(1991, r.gegruendet)
        assertEquals("Q649", r.sitz)
        assertEquals("https://lukoil.com", r.website)
        assertNull(FirmaWikidata.ersteFirma(eintraege, listOf("Q99")))
    }

    @Test
    fun beschriftungenInSpracheOderEnglisch() {
        val l = """{"entities":{"Q649":{"id":"Q649","labels":{"en":{"language":"en","value":"Moscow"},"de":{"language":"de","value":"Moskau"}}},"Q1":{"id":"Q1","labels":{"en":{"language":"en","value":"Universe"}}}}}"""
        assertEquals(mapOf("Q649" to "Moskau", "Q1" to "Universe"), FirmaWikidata.beschriftungen(l, "de"))
        val r = FirmaWikidata.ersteFirma(eintraege, listOf("Q329002"))!!
        val a = FirmaWikidata.zusammen(r, FirmaWikidata.beschriftungen(l, "de"))
        assertEquals("Moskau", a.sitz)
        assertTrue(!a.leer)
    }

    @Test
    fun ganzerWegMitAntwortenUndAusfall() = runBlocking {
        val beschriftung = """{"entities":{"Q649":{"id":"Q649","labels":{"de":{"language":"de","value":"Moskau"}}}}}"""
        val a = FirmaWikidata.laden("Lukoil", "de", { it }) { url ->
            when {
                "wbsearchentities" in url -> suche
                "props=labels" in url -> beschriftung
                "props=claims" in url -> eintraege
                else -> null
            }
        }
        assertEquals("Moskau", a!!.sitz)
        assertEquals(104000L, a.mitarbeiter)
        // Ohne Netz ist es ein Fehlschlag, kein leeres Ergebnis
        val fehl = runCatching { FirmaWikidata.laden("Lukoil", "de", { it }) { null } }
        assertTrue(fehl.isFailure)
        // Treffer ohne Firmenangaben: nichts
        assertNull(FirmaWikidata.laden("X", "de", { it }) { url -> if ("wbsearchentities" in url) """{"search":[]}""" else null })
    }
}
