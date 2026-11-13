package de.troetenrepublik.app.engine

import de.troetenrepublik.app.i18n.EnergieWeltTexte
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

// Oel und Erdgas je Land (EIA) und Gasfackeln (EOG): Datei lesen, Rangfolge, Ausfuhr und Einfuhr, Texte, Registrierung.
class Fassung094EnergieWeltTest {

    private fun datei(name: String) = listOf("src/main/assets/$name", "../app/src/main/assets/$name").map { File(it) }.first { it.exists() }
    private fun repoDatei(name: String) = listOf(name, "../$name").map { File(it) }.first { it.exists() }

    // Drei Jahre ab 2021, fuenf Laender: A groesster Foerderer, E verbraucht mehr als es foerdert
    private val text = """
        {"stand":"2026-07-22","quelle":"Test",
         "oel":{"ab":2021,"laender":[
           {"c":"AAA","en":"Alpha","de":"Alpha-Land","f":[8000,9000,10000],"v":[3000,3000,3000]},
           {"c":"BBB","en":"Beta","f":[4000,5000,null],"v":[6000,6000,6000]},
           {"c":"CCC","en":"Gamma","f":[1000,1000,1000],"v":[900,900,900]},
           {"c":"DDD","en":"Delta","f":[500,500,500],"v":[2500,2500,2500]},
           {"c":"EEE","en":"Epsilon","f":[null,null,null],"v":[100,100,100]}]},
         "gas":{"ab":2021,"laender":[{"c":"AAA","en":"Alpha","f":[100.5,110,120],"v":[90,95,100]}]},
         "fackel":{"ab":2022,"laender":[{"c":"AAA","en":"Alpha","f":[1.5,1.25]},{"c":"BBB","en":"Beta","f":[0.5,null]}]}}
    """.trimIndent()

    @Test fun dateiWirdGelesen() {
        val d = EnergieWelt.lesen(text)!!
        assertEquals(2021, d.oel!!.ab)
        assertEquals(2023, d.oel!!.bis)
        assertEquals(2023, d.fackel!!.bis)
        assertEquals("Alpha-Land", d.oel!!.laender[0].name(true))
        // ohne deutschen Namen steht der englische da
        assertEquals("Beta", d.oel!!.laender[1].name(true))
        assertEquals("Alpha", d.oel!!.laender[0].name(false))
    }

    @Test fun kaputteOderUnpassendeDateienWerdenAbgelehnt() {
        assertNull(EnergieWelt.lesen("kaputt"))
        assertNull(EnergieWelt.lesen(text.replace("\"stand\":\"2026-07-22\"", "\"stand\":\"gestern\"")))
        // Reihe mit anderer Laenge
        assertNull(EnergieWelt.lesen(text.replace("\"f\":[1000,1000,1000]", "\"f\":[1000,1000]")))
        // Verbrauch mit anderer Laenge als die Foerderung
        assertNull(EnergieWelt.lesen(text.replace("\"v\":[900,900,900]", "\"v\":[900]")))
        // unsinniges Anfangsjahr
        assertNull(EnergieWelt.lesen(text.replace("\"ab\":2021", "\"ab\":21")))
    }

    @Test fun dateiOhneLaenderIstGueltigUndZeigtNichts() {
        val d = EnergieWelt.lesen("{\"stand\":\"2026-11-14\",\"quelle\":\"x\"}")!!
        assertFalse(EnergieWelt.hat(d.oel))
        assertFalse(EnergieWelt.hat(d.gas))
        assertFalse(EnergieWelt.hat(d.fackel))
        assertTrue(EnergieWelt.hat(EnergieWelt.lesen(text)!!.oel))
        assertTrue(EnergieWelt.hat(EnergieWelt.lesen(text)!!.gas, verbrauch = true))
    }

    @Test fun rangfolgeMitAnteilenUndVorjahr() {
        val d = EnergieWelt.lesen(text)!!
        // Mindestens zwei Laender mit Wert: 2023 hat AAA, CCC, DDD (BBB fehlt), also gilt 2023
        val r = EnergieWelt.rang(d.oel!!, mindestens = 3)!!
        assertEquals(2023, r.jahr)
        assertEquals(11_500.0, r.summe, 1e-9)
        assertEquals(listOf("AAA", "CCC", "DDD"), r.eintraege.map { it.land.c })
        assertEquals(10_000.0 / 11_500 * 100, r.eintraege[0].anteil, 1e-9)
        assertEquals(11.11, r.eintraege[0].aenderung!!, 0.01)
        assertEquals(100.0, r.spitze(3), 1e-9)
        // Verlangt man mehr Laender, als 2023 meldet, gilt 2022 mit BBB
        val r2 = EnergieWelt.rang(d.oel!!, mindestens = 4)!!
        assertEquals(2022, r2.jahr)
        assertEquals(listOf("AAA", "BBB", "CCC", "DDD"), r2.eintraege.map { it.land.c })
        // Verbrauch: alle fuenf melden, EEE mit 100
        val v = EnergieWelt.rang(d.oel!!, verbrauch = true)!!
        assertEquals(2023, v.jahr)
        assertEquals("BBB", v.eintraege[0].land.c)
        assertEquals(5, v.eintraege.size)
        // Anteile ergeben zusammen 100
        assertEquals(100.0, v.eintraege.sumOf { it.anteil }, 1e-9)
    }

    @Test fun jahrMitWenigenMeldungenZaehltNicht() {
        // 20 Laender melden 2024, im Jahr 2025 nur die ersten zwölf (wie der Verbrauch der EIA, zuerst nur OECD)
        val laender = (1..20).joinToString(",") { i ->
            val v2025 = if (i <= 12) "100" else "null"
            """{"c":"L$i","en":"Land $i","f":[1,1],"v":[${if (i == 20) 5000 else 100},$v2025]}"""
        }
        val d = EnergieWelt.lesen("""{"stand":"2026-11-01","quelle":"Test","oel":{"ab":2024,"laender":[$laender]}}""")!!
        val v = EnergieWelt.rang(d.oel!!, verbrauch = true)!!
        assertEquals(2024, v.jahr)
        assertEquals("L20", v.eintraege[0].land.c)
        assertEquals(20, v.eintraege.size)
    }

    @Test fun ausfuhrUndEinfuhr() {
        val d = EnergieWelt.lesen(text)!!
        val n = EnergieWelt.netto(d.oel!!, 2023)
        // BBB hat 2023 keine Foerderung, EEE keine Foerderung: beide fehlen
        assertEquals(listOf("AAA", "CCC", "DDD"), n.map { it.land.c })
        assertEquals(listOf("AAA", "CCC"), EnergieWelt.ausfuhr(n, 5).map { it.land.c })
        assertEquals(7000.0, EnergieWelt.ausfuhr(n, 1).single().saldo, 1e-9)
        assertEquals(listOf("DDD"), EnergieWelt.einfuhr(n, 5).map { it.land.c })
        assertEquals(-2000.0, EnergieWelt.einfuhr(n, 5).single().saldo, 1e-9)
        // 2022: BBB importiert 1000
        val n22 = EnergieWelt.netto(d.oel!!, 2022)
        assertEquals("DDD", EnergieWelt.einfuhr(n22, 1).single().land.c)
        assertEquals(-1000.0, EnergieWelt.einfuhr(n22, 5)[1].saldo, 1e-9)
    }

    @Test fun fackelnLaufenInEigenenJahren() {
        val d = EnergieWelt.lesen(text)!!
        val x = EnergieWelt.rang(d.fackel!!, mindestens = 1)!!
        assertEquals(2023, x.jahr)
        assertEquals(listOf("AAA"), x.eintraege.map { it.land.c })
    }

    @Test fun textePassenZusammen() {
        assertEquals(EnergieWeltTexte.schluessel(true), EnergieWeltTexte.schluessel(false))
        val k = EnergieWeltTexte.schluessel(true)
        listOf("OEL", "GAS").forEach { t -> listOf("titel", "kern", "f", "v", "info").forEach { assertTrue("$it.$t", "$it.$t" in k) } }
        listOf(true, false).forEach { de ->
            k.forEach { key ->
                val t = EnergieWeltTexte.text(de, key)
                assertTrue(key, t.isNotEmpty())
                assertFalse(key, t.contains("—") || t.contains(" – "))
            }
        }
        assertEquals("USA fördert am meisten Öl: 21,6 Mio. Barrel am Tag (21 % der Welt). Die zehn größten Förderer liefern 72 %.",
            EnergieWeltTexte.text(true, "kern.OEL", listOf("USA", "21,6", "21 %", "72 %")))
        assertTrue(EnergieWeltTexte.text(false, "fackel.unter").contains("not reported"))
    }

    @Test fun mitgelieferteDateiUndRegistrierung() {
        val s = datei("welt/energie.json").readText()
        assertNotNull(EnergieWelt.lesen(s))
        assertEquals(1, Datenbuendel.SCHEMA["welt/energie.json"])
        assertTrue(de.troetenrepublik.app.market.Daten.probeLesen("welt/energie.json", s))
        assertFalse(de.troetenrepublik.app.market.Daten.probeLesen("welt/energie.json", "{\"stand\":\"x\"}"))
        assertEquals(Alter.JAEHRLICH, Alter.profilFuer("welt/energie.json"))
        assertTrue(repoDatei("tools/daten/dateien.json").readText().contains("\"welt/energie.json\""))
        assertTrue(repoDatei("tools/daten/energie_welt.py").exists())
    }

    @Test fun readmeNenntOelUndErdgasJeLand() {
        val r = repoDatei("README.md").readText()
        assertTrue(r.contains("Oil and natural gas by country"))
    }
}
