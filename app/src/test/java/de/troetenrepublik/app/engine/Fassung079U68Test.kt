package de.troetenrepublik.app.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Durchbrueche: aeltere Treffer als „Zuletzt“ und Routine-Finanzmeldungen bei den Wirtschaftsthemen.
class Fassung079U68Test {

    private val jetzt = 1_794_000_000_000L
    private val tag = 86_400_000L

    private val t1 = "Longi sets new world record with 35.5%-efficient perovskite-silicon tandem cell"
    private val t2 = "Researchers set world record for flexible perovskite solar cell efficiency at 26%"
    private val t3 = "Scientists achieve first-ever stable all-perovskite tandem solar module in outdoor test"
    private val t4 = "Fraunhofer ISE hits world record 47.6% with four-junction photovoltaic cell"
    private val t5 = "Swiss team reaches world record for thin-film tandem photovoltaic output"

    private fun roh(thema: String, titel: String, alter: Long, i: Int = 0) =
        Durchbrueche.Roh(thema, titel, "https://example.org/$thema/$i", jetzt - alter * tag, "pv magazine")

    private fun solarAlt() = listOf(
        roh("solar", t1, 20, 1), roh("solar", t2, 60, 2), roh("solar", t3, 120, 3), roh("solar", t4, 200, 4), roh("solar", t5, 400, 5),
    )

    // 1. Zuletzt: bis 365 Tage, hoechstens drei je Thema, neueste zuerst

    @Test
    fun aeltereTrefferLandenInZuletzt() {
        // Die enge Auswahl behaelt davon nichts
        assertTrue(Durchbrueche.filtern(solarAlt(), jetzt).isEmpty())
        val z = Durchbrueche.aeltere(solarAlt(), jetzt, emptyList())
        assertEquals(listOf(t1, t2, t3), z.map { it.titel })
        assertTrue(z.all { it.thema == "solar" })
        assertTrue(z.all { jetzt - it.zeit <= Durchbrueche.ZULETZT_TAGE * tag && jetzt - it.zeit >= Durchbrueche.TAGE * tag })
        // Zwei aeltere ergeben zwei Zeilen, auch das Alter von einem Jahr zaehlt noch nicht
        assertEquals(2, Durchbrueche.aeltere(solarAlt().take(2), jetzt, emptyList()).size)
        assertTrue(Durchbrueche.aeltere(listOf(solarAlt().last()), jetzt, emptyList()).isEmpty())
    }

    @Test
    fun zuletztGiltNurFuerThemenOhneNeueMeldung() {
        val neu = Durchbrueche.filtern(listOf(roh("solar", t1, 2, 1), roh("krebs", "FDA approves amivantamab for recurrent or metastatic HNSCC", 3, 9)), jetzt)
        assertEquals(setOf("solar", "krebs"), neu.map { it.thema }.toSet())
        val ohne = Durchbrueche.ohneNeue(neu).map { it.id }
        assertFalse("solar" in ohne)
        assertFalse("krebs" in ohne)
        assertTrue("cyber" in ohne)
        assertEquals(Durchbrueche.THEMEN.size - 2, ohne.size)
    }

    @Test
    fun neueVerdraengenNichtsUndDublettenFallenWeg() {
        val neu = Durchbrueche.filtern(listOf(roh("solar", t1, 2, 1)), jetzt)
        assertEquals(1, neu.size)
        // Dieselbe Geschichte als alter Treffer steht nicht noch einmal unter Zuletzt
        val alt = Durchbrueche.aeltere(listOf(roh("solar", t1, 300, 7), roh("solar", t2, 60, 2), roh("solar", t3, 120, 3)), jetzt, neu)
        assertEquals(listOf(t2, t3), alt.map { it.titel })
        val alle = (neu + alt).sortedByDescending { it.zeit }
        val b = Durchbrueche.ansicht(null, alle, jetzt).first { it.thema.id == "solar" }
        assertEquals(listOf(t1), b.neu.map { it.titel })
        assertEquals(listOf(t2, t3), b.zuletzt.map { it.titel })
    }

    @Test
    fun zuletztBegrenztAufDreiUndEinJahr() {
        fun m(i: Int, alter: Long) = Durchbrueche.Meldung("solar", "Titel $i", "https://example.org/$i", jetzt - alter * tag, "Q")
        val liste = listOf(m(1, 20), m(2, 40), m(3, 60), m(4, 80), m(5, 400), m(6, 5))
        val z = Durchbrueche.zuletzt(liste, jetzt)["solar"]!!
        assertEquals(listOf("Titel 1", "Titel 2", "Titel 3"), z.map { it.titel })
        // Frische Meldungen gehoeren nicht dazu
        assertTrue(z.none { it.titel == "Titel 6" })
        assertEquals(listOf("Titel 6"), Durchbrueche.gruppieren(liste, jetzt).single().meldungen.map { it.titel })
    }

    @Test
    fun blockOhneNeuesMitAelteremZaehltKeineNeuen() {
        val liste = Durchbrueche.aeltere(solarAlt(), jetzt, emptyList())
        val bloecke = Durchbrueche.trendBloecke(null, liste, jetzt)
        val solar = bloecke.first { it.thema.id == "solar" }
        assertTrue(solar.neu.isEmpty())
        assertEquals(3, solar.zuletzt.size)
        assertEquals(Durchbrueche.THEMEN.map { it.id }.toSet(), bloecke.map { it.thema.id }.toSet())
        val zeilen = TrendListe.zeilen(emptyMap(), emptyMap(), bloecke)
        assertEquals(0, zeilen.sumOf { it.neu })
        // Ohne aeltere Treffer bleibt der Block leer
        assertTrue(bloecke.first { it.thema.id == "cyber" }.zuletzt.isEmpty())
    }

    @Test
    fun speicherLiestAlteDateienOhneNeueEintraege() {
        val alt = """{"abgerufen":5,"liste":[{"thema":"solar","titel":"t","link":"https://x","zeit":1,"quelle":"q"}]}"""
        val s = Durchbrueche.lesenGespeichert(alt)
        assertNotNull(s)
        assertEquals(1, s!!.liste.size)
        val liste = Durchbrueche.aeltere(solarAlt(), jetzt, emptyList())
        val zurueck = Durchbrueche.lesenGespeichert(Durchbrueche.schreiben(Durchbrueche.Stand(jetzt, liste)))!!
        assertEquals(liste.map { it.titel }, Durchbrueche.zuletzt(zurueck.liste, jetzt)["solar"]!!.map { it.titel })
    }

    @Test
    fun ausfallBehaeltAelterenStand() {
        val alt = Durchbrueche.aeltere(solarAlt(), jetzt, emptyList())
        val vereint = Durchbrueche.zusammenfuehren(emptyList(), alt, setOf("solar"), jetzt)
        assertEquals(alt.map { it.titel }, vereint.map { it.titel })
    }

    @Test
    fun datumAusZeit() {
        val d = Durchbrueche.datum(20_000L * tag)
        assertEquals(listOf(2024, 10, 4), listOf(d.jahr, d.monat, d.tag))
        val h = Durchbrueche.datum(jetzt)
        assertEquals(listOf(2026, 11, 6), listOf(h.jahr, h.monat, h.tag))
        val schalt = Durchbrueche.datum(20_512L * tag)
        assertEquals(listOf(2026, 2, 28), listOf(schalt.jahr, schalt.monat, schalt.tag))
        // Gleiches Kalenderjahr mit Tag, aelteres Jahr nur Monat und Jahr
        assertTrue(Durchbrueche.datumMitTag(jetzt, jetzt))
        assertTrue(Durchbrueche.datumMitTag(jetzt - 30 * tag, jetzt) == (Durchbrueche.datum(jetzt - 30 * tag).jahr == Durchbrueche.datum(jetzt).jahr))
        assertFalse(Durchbrueche.datumMitTag(jetzt - 400 * tag, jetzt))
    }

    // 2. Banken: nur Neuerungen, Routine-Finanzmeldungen fallen bei Wirtschaftsthemen heraus

    private fun bank(titel: String, quelle: String = "Reuters") = Durchbrueche.Roh("banken", titel, "https://example.org/b", jetzt - tag, quelle)

    @Test
    fun routineMeldungenDerBankenFallenWeg() {
        val routine = listOf(
            "Al Salam Bank's net profit rises 20.4% to \$185.8m in first nine months",
            "Lucid Stake at UBS Hits a Record 3.7% as Bank's Puts Rise Nearly 40-Fold",
            "First Abu Dhabi Bank approves \$4bn strategic rights issue",
            "Bank of England erhöht Leitzins erstmals seit über drei Jahren",
        )
        routine.forEach { assertTrue(it, Durchbrueche.filtern(listOf(bank(it)), jetzt).isEmpty()) }
        val echt = Durchbrueche.filtern(listOf(bank("ECB approves launch of digital euro pilot")), jetzt)
        assertEquals(listOf("banken"), echt.map { it.thema })
        // Das blanke Wort Bank gehoert nicht mehr zum Thema
        val t = Durchbrueche.thema("banken")!!
        assertFalse(t.muster.containsMatchIn("Bank of England"))
        assertFalse(Durchbrueche.suchbegriff(t, false).contains(" OR bank"))
        assertFalse(Durchbrueche.suchbegriff(t, true).contains("OR Bank"))
    }

    @Test
    fun routineAusschlussGiltNurFuerWirtschaftsthemen() {
        val titel = "FDA approves first drug to treat rare disease in a quarter of patients"
        val medizin = Durchbrueche.Roh("medizin", titel, "https://example.org/m", jetzt - tag, "Reuters")
        assertEquals(listOf("medizin"), Durchbrueche.filtern(listOf(medizin), jetzt).map { it.thema })
        // Dieselbe Formulierung bei einem Wirtschaftsthema faellt heraus
        val industrie = Durchbrueche.Roh("industrie", "World's first 3D printing plant doubles output in a quarter", "https://example.org/i", jetzt - tag, "Reuters")
        assertTrue(Durchbrueche.filtern(listOf(industrie), jetzt).isEmpty())
    }
}
