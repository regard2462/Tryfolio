package de.troetenrepublik.app.engine

import de.troetenrepublik.app.market.Kongress
import de.troetenrepublik.app.market.Politik
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Risiko-Kennzahlen sowie Senat und Regierung. Alle Daten sind erfunden.
class Fassung30Test {

    private val tag = 86_400_000L
    private val start = 1_700_000_000_000L

    private fun reihe(werte: List<Double>, abstand: Long = tag) = werte.mapIndexed { i, v -> start + i * abstand to v }

    @Test
    fun rueckgangUndUlcer() {
        // 100 bis 120 in 20 Tagen, Fall auf 90 in 10 Tagen, zurueck auf 120 in 30 Tagen, weiter auf 130
        val w = (0..20).map { 100.0 + it } + (1..10).map { 120.0 - 3 * it } + (1..30).map { 90.0 + it } + (1..10).map { 120.0 + it }
        val k = Risiko.kennzahlen(reihe(w))!!
        assertEquals(40L, k.rueckgangTage)
        assertFalse(k.laufend)
        assertTrue(k.ulcer > 0)
        assertNull("unter einem Jahr keine Calmar-Quote", k.calmar)
        // eine Reihe, die am Ende unter ihrem Hoch liegt
        val k2 = Risiko.kennzahlen(reihe((0..30).map { 100.0 + it } + (1..30).map { 130.0 - it }))!!
        assertTrue(k2.laufend)
        assertEquals(30L, k2.rueckgangTage)
    }

    @Test
    fun sharpeUndSortino() {
        // abwechselnd +2 % und -1 %: positive Rendite, Schwankung nach unten kleiner als insgesamt
        var v = 100.0
        val w = (0..400).map { i -> if (i > 0) v *= if (i % 2 == 1) 1.02 else 0.99; v }
        val k = Risiko.kennzahlen(reihe(w))!!
        assertTrue(k.sharpe!! > 0)
        assertTrue(k.sortino!! > k.sharpe!!)
        assertTrue(k.proJahr > 300)
        assertEquals(0.01, k.var95!!, 1e-9)
        assertEquals(0.01, k.es95!!, 1e-9)
        assertNotNull(k.calmar)
    }

    @Test
    fun betaAlphaKorrelation() {
        val markt = ArrayList<Double>()
        val doppelt = ArrayList<Double>()
        var m = 100.0
        var d = 100.0
        for (i in 0..120) {
            if (i > 0) {
                val r = if (i % 3 == 0) -0.01 else 0.006
                m *= 1 + r
                d *= 1 + 2 * r
            }
            markt += m
            doppelt += d
        }
        val v = Risiko.vergleich(reihe(doppelt), reihe(markt))!!
        assertEquals(2.0, v.beta, 1e-9)
        assertEquals(0.0, v.alphaProJahr, 1e-9)
        assertEquals(1.0, v.korrelation, 1e-9)
    }

    @Test
    fun verkaeufe() {
        val v = Risiko.verkaeufe(listOf(100.0, -50.0, 30.0, -10.0, 0.0))!!
        assertEquals(4, v.anzahl)
        assertEquals(2, v.gewinne)
        assertEquals(0.5, v.trefferquote, 1e-9)
        assertEquals(130.0 / 60.0, v.gewinnfaktor!!, 1e-9)
        assertEquals(65.0, v.mittelGewinn!!, 1e-9)
        assertEquals(-30.0, v.mittelVerlust!!, 1e-9)
        assertEquals(17.5, v.erwartung, 1e-9)
        assertNull(Risiko.verkaeufe(listOf(10.0, -5.0)))
    }

    private val senatJson = """
        {"stand":"2026-10-15","quelle":"q","von":"2023-10-16","meldungen":[
         {"id":"a1","vorname":"Jerry","nachname":"Moran,","amt":"Senator","eingang":"2026-09-20","titel":"Periodic Transaction Report for 09/20/2026","papier":false,
          "geschaefte":[{"d":"2026-09-01","i":"SP","k":"ACME","n":"Acme Corp","t":"Stock","a":"K","von":1001,"bis":15000,"c":null},
                        {"d":"2026-09-02","i":"","k":"--","n":"Example County GO Bonds","t":"Municipal Security","a":"V","von":15001,"bis":50000,"c":null},
                        {"d":"2026-09-03","i":"JT","k":"ACME","n":"Acme Corp Option Type: Call","t":"Stock Option","a":"T","von":50001,"bis":100000,"c":"x"}]},
         {"id":"a2","vorname":"Jerry","nachname":"Moran","amt":"Senator","eingang":"2026-06-01","titel":"Periodic Transaction Report for 06/01/2026","papier":true,"geschaefte":[]},
         {"id":"b1","vorname":"Ann M","nachname":"Example","amt":"Senator","eingang":"2026-10-01","titel":"t","papier":false,
          "geschaefte":[{"d":"2026-09-25","k":"WIDG","n":"Widget Inc","t":"Stock","a":"X","von":1001,"bis":15000}]}
        ]}
    """.trimIndent()

    @Test
    fun senatLesen() {
        val d = Politik.senat(senatJson)!!
        val p = Politik.personen(d, Politik.tag("2026-10-15"))
        assertEquals(2, p.size)
        assertEquals("Moran|Jerry", p[0].schluessel)
        assertEquals("Jerry Moran", p[0].name)
        assertEquals(2, p[0].meldungen.size)
        assertEquals("Ann Example", p[1].name)
        val daten = Politik.daten(p[0].meldungen)
        assertEquals(2, daten.size)
        assertNull("Papiermeldung ohne Geschaefte", daten[1].second)
        val g = daten[0].second!!
        assertEquals(listOf("ST", "MB", "OP"), g.map { it.typ })
        assertEquals(listOf(Kongress.Art.KAUF, Kongress.Art.VERKAUF, Kongress.Art.TEILVERKAUF), g.map { it.art })
        assertNull(g[1].kuerzel)
        assertEquals("SP", g[0].inhaber)
        // Kongress.posten: Anleihe ohne Kuerzel faellt weg, Aktie und Option auf ACME zaehlen zusammen
        val posten = Kongress.posten(g)
        assertEquals(1, posten.size)
        assertEquals("ACME", posten[0].kuerzel)
        assertTrue(posten[0].optionen)
    }

    @Test
    fun regierungLesen() {
        val json = """
            {"stand":"2026-10-15","personen":[
             {"id":"trump","name":"Donald J. Trump","meldungen":[
               {"datei":"u1","unterschrieben":"2026-09-08","eingang":"2026-09-14","seiten":3,"zeilen":2,"ausgelassen":1,
                "geschaefte":[{"d":"2026-08-03","a":"K","n":"ACME CORP","k":"ACME","t":"ST","von":15001,"bis":50000,"s":true},
                              {"d":"2026-08-04","a":"K","n":"EXAMPLE ST GO 5% 2030","k":null,"t":"MB","von":250001,"bis":500000,"s":false}]}]},
             {"id":"vance","name":"JD Vance","bestand":{"stichtag":"2024-12-31","datei":"u2","positionen":[
               {"n":"Index Fund One","k":"AAA","t":"EF","von":1000001,"bis":5000000},
               {"n":"Index Fund Two","k":"BBB","t":"EF","von":1001,"bis":15000},
               {"n":"Private stake","k":null,"t":"OT","von":100001,"bis":250000}]}}
            ]}
        """.trimIndent()
        val d = Politik.regierung(json)!!
        val trump = d.personen.first { it.id == "trump" }
        val daten = Politik.daten(trump)
        assertEquals(1, daten.size)
        assertEquals(Politik.tag("2026-09-14"), daten[0].first.datum)
        assertEquals(2, daten[0].second!!.size)
        val vance = d.personen.first { it.id == "vance" }
        val w = Politik.gewichte(vance.bestand!!, 10)
        assertEquals(listOf("AAA", "BBB"), w.map { it.first })
        assertEquals(1.0, w.sumOf { it.second }, 1e-9)
        assertEquals(3_000_000.5 / (3_000_000.5 + 8_000.5), w[0].second, 1e-9)
    }

    @Test fun meldungenAufWhitehouseJePerson() {
        // Erfundene Liste im Aufbau der Seite: je Person Transaktionsmeldungen und Jahresberichte, doppelte Links einmal
        val html = """
            <a href="https://beispiel.test/uploads/2026/09/President-Donald-J.-Trump-Periodic-Transaction-Report-09.08.26.pdf">A</a>
            <a href="https://beispiel.test/uploads/2026/08/President-Donald-J.-Trump-Periodic-Transaction-Report-08.12.26.pdf">B</a>
            <a href="https://beispiel.test/uploads/2026/08/President-Donald-J.-Trump-Periodic-Transaction-Report-08.12.26.pdf">B</a>
            <a href="https://beispiel.test/uploads/2026/06/President-Donald-J.-Trump-2025-Annual-Report.pdf">C</a>
            <a href="https://beispiel.test/uploads/2026/05/Beispiel-Person-Periodic-Transaction-Report-05.01.26.pdf">D</a>
        """.trimIndent()
        assertEquals(3, de.troetenrepublik.app.market.Politik.weissesHausMeldungen(html, "Trump"))
        assertEquals(1, de.troetenrepublik.app.market.Politik.weissesHausMeldungen(html, "Person"))
        assertEquals(null, de.troetenrepublik.app.market.Politik.weissesHausMeldungen(html, "Vance"))
        assertEquals(null, de.troetenrepublik.app.market.Politik.weissesHausMeldungen("<html></html>", "Trump"))
    }
}
