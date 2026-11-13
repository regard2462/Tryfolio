package de.troetenrepublik.app.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// [0.77] Beschaeftigten-Reihe aus Wikidata: nur Eintraege, deren Boersenkuerzel zum Symbol passt.
class Fassung077MitarbeiterTest {

    private fun ma(menge: Long, zeit: String) =
        """{"mainsnak":{"datavalue":{"value":{"amount":"+$menge","unit":"1"}}},"qualifiers":{"P585":[{"datavalue":{"value":{"time":"$zeit"}}}]}}"""

    private fun eintrag(kuerzel: String?, vararg punkte: String): String {
        val boerse = if (kuerzel == null) "" else """"P414":[{"mainsnak":{},"qualifiers":{"P249":[{"datavalue":{"value":"$kuerzel"}}]}}],"""
        return """{"claims":{$boerse"P1128":[${punkte.joinToString(",")}]}}"""
    }

    private fun antwort(vararg e: Pair<String, String>) = """{"entities":{${e.joinToString(",") { "\"${it.first}\":${it.second}" }}}}"""

    @Test fun reiheEinesPassendenEintrags() {
        val a = antwort(
            "Q1" to eintrag("AAPL", ma(147000, "+2017-09-30T00:00:00Z"), ma(164000, "+2022-00-00T00:00:00Z"), ma(132000, "+2018-00-00T00:00:00Z")),
        )
        val r = MitarbeiterReihe.lesen(a, listOf("Q1"), "AAPL")!!
        assertEquals(listOf(2017, 2018, 2022), r.map { it.jahr })
        assertEquals(164000L, r.last().anzahl)
    }

    @Test fun gleichnamigeFirmaMitAnderemKuerzelZaehltNicht() {
        val a = antwort(
            "Q1" to eintrag("XYZ", ma(10, "+2020-00-00T00:00:00Z"), ma(20, "+2021-00-00T00:00:00Z")),
            "Q2" to eintrag("AAPL", ma(1, "+2019-00-00T00:00:00Z"), ma(2, "+2020-00-00T00:00:00Z")),
        )
        assertEquals(2L, MitarbeiterReihe.lesen(a, listOf("Q1", "Q2"), "AAPL")!!.last().anzahl)
        assertNull(MitarbeiterReihe.lesen(a, listOf("Q1"), "AAPL"))
    }

    @Test fun ohneJahrOderMitNurEinemJahrKeineReihe() {
        val ohneJahr = """{"mainsnak":{"datavalue":{"value":{"amount":"+5"}}}}"""
        assertNull(MitarbeiterReihe.lesen(antwort("Q1" to eintrag("KO", ohneJahr, ma(9, "+2020-00-00T00:00:00Z"))), listOf("Q1"), "KO"))
    }

    @Test fun kuerzelOhneHandelsplatz() {
        assertTrue("SAP" in MitarbeiterReihe.kuerzel("SAP.DE"))
        assertTrue("BRKB" in MitarbeiterReihe.kuerzel("BRK-B"))
        assertTrue("BRKB" in MitarbeiterReihe.kuerzel("BRK.B"))
        val a = antwort("Q1" to eintrag("SAP", ma(100, "+2020-00-00T00:00:00Z"), ma(110, "+2021-00-00T00:00:00Z")))
        assertEquals(110L, MitarbeiterReihe.lesen(a, listOf("Q1"), "SAP.DE")!!.last().anzahl)
    }
}
