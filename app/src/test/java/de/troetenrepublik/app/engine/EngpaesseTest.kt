package de.troetenrepublik.app.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Engpaesse: Antwort des Dienstes Daily_Chokepoints_Data (IWF PortWatch), Lage gegen das Vorjahr, Verlauf.
class EngpaesseTest {
    // Gekuerzte echte Antwort vom 7.11.2026 (drei von 588 Zeilen)
    private val antwort = """{"objectIdFieldName":"ObjectId","fields":[{"name":"portid","type":"esriFieldTypeString"},{"name":"date","type":"esriFieldTypeDateOnly"}],"features":[
        {"attributes":{"portid":"chokepoint1","date":"2026-10-05","n_total":36,"n_tanker":17,"n_container":11}},
        {"attributes":{"portid":"chokepoint1","date":"2026-10-06","n_total":48,"n_tanker":18,"n_container":12}},
        {"attributes":{"portid":"chokepoint1","date":"2026-10-07","n_total":32,"n_tanker":15,"n_container":14}}]}"""

    private val fehler = """{"error":{"code":429,"message":"Unable to perform query. Too many requests.","details":["API calls quota exceeded (6328 request units)! maximum allowed request units (6000) per Minute. Retry after 60 sec."]}}"""

    @Test
    fun antwortLesen() {
        val z = Engpaesse.lesen(antwort)!!
        assertEquals(3, z.size)
        assertEquals("chokepoint1", z[0].id)
        assertEquals(java.time.LocalDate.of(2026, 10, 5).toEpochDay().toInt(), z[0].tag.t)
        assertEquals(36, z[0].tag.n)
        assertEquals(17, z[0].tag.k)
        assertEquals(11, z[0].tag.c)
    }

    @Test
    fun fehlerAntwortIstNull() {
        assertNull(Engpaesse.lesen(fehler))
        assertNull(Engpaesse.lesen("kein json"))
        assertNull(Engpaesse.lesen("""{"features":[]}"""))
    }

    @Test
    fun adressenEnthaltenFensterUndCacheHinweis() {
        val a = Engpaesse.adresseVorjahr(java.time.LocalDate.of(2026, 10, 25).toEpochDay().toInt())
        // 25.10.2025 plus minus 14 Tage
        assertTrue(a.contains("2025-10-11"))
        assertTrue(a.contains("2025-11-08"))
        assertTrue(a.contains("cacheHint=true"))
        assertTrue(Engpaesse.adresseVerlauf("chokepoint6", java.time.LocalDate.of(2026, 11, 7)).contains("chokepoint6"))
    }

    // Straße von Hormus: letzte sieben Tage bis 25.10.2026 und das Vorjahresfenster 11.10. bis 8.11.2025 (echte Werte)
    private val hormusNeu = listOf(4, 3, 3, 4, 5, 2, 2)
    private val hormusVorjahr = listOf(69, 55, 71, 120, 62, 51, 58, 72, 82, 82, 103, 116, 109, 104, 101, 99, 106, 103, 114, 95, 80, 74, 60, 51, 74, 78, 69, 77, 107)

    private fun zeilen(id: String, ab: Int, werte: List<Int>) = werte.mapIndexed { i, n -> Engpaesse.Zeile(id, Engpaesse.Tag(ab + i, n, n / 2, n / 4)) }

    @Test
    fun hormusAmBodenGegenVorjahr() {
        val letzter = java.time.LocalDate.of(2026, 10, 25).toEpochDay().toInt()
        val neu = zeilen("chokepoint6", letzter - 6, hormusNeu) + zeilen("chokepoint1", letzter - 6, List(7) { 41 })
        val vj = zeilen("chokepoint6", letzter - 365 - 14, hormusVorjahr) + zeilen("chokepoint1", letzter - 365 - 14, List(29) { 42 })
        val u = Engpaesse.uebersicht(neu, vj, 1L)!!
        assertEquals(letzter, u.tag)
        val h = u.lage["chokepoint6"]!!
        assertEquals(23.0 / 7.0, h.n, 1e-9)
        assertEquals(hormusVorjahr.sum() / 29.0, h.vn!!, 1e-9)
        assertTrue(h.abw!! < -95.0)
        assertEquals(-2.4, u.lage["chokepoint1"]!!.abw!!, 0.1)
        // Rangfolge: Hormus zuerst, und als schwaechster Engpass mit genug Vorjahresverkehr
        assertEquals("chokepoint6", Engpaesse.sortiert(u).first().first.id)
        assertEquals("chokepoint6", Engpaesse.schlechtester(u)!!.first.id)
    }

    @Test
    fun ohneVorjahrKeineAbweichung() {
        val letzter = 20751
        val u = Engpaesse.uebersicht(zeilen("chokepoint6", letzter - 6, hormusNeu), null, 1L)!!
        assertNull(u.lage["chokepoint6"]!!.abw)
        assertNull(Engpaesse.schlechtester(u))
    }

    @Test
    fun zuWenigeTageSindKeineLage() {
        assertNull(Engpaesse.lage(listOf(Engpaesse.Tag(100, 5, 1, 1), Engpaesse.Tag(101, 5, 1, 1)), 101))
    }

    @Test
    fun verlaufUndGleitenderSchnitt() {
        val tage = (1..60).map { Engpaesse.Tag(1000 + it, if (it <= 30) 10 else 20, 0, 0) }
        val z = tage.map { Engpaesse.Zeile("chokepoint6", it) } + listOf(Engpaesse.Zeile("chokepoint1", Engpaesse.Tag(1001, 1, 0, 0)))
        val v = Engpaesse.verlauf(z, "chokepoint6", 5L)!!
        assertEquals(60, v.tage.size)
        assertNull(Engpaesse.verlauf(z, "chokepoint1", 5L))
        val g = Engpaesse.glatt(v.tage)
        assertEquals(60 - 6, g.size)
        assertEquals(10.0, g.first().second, 1e-9)
        assertEquals(20.0, g.last().second, 1e-9)
        assertNotNull(Engpaesse.stelle("chokepoint11"))
        assertEquals(28, Engpaesse.STELLEN.map { it.id }.distinct().size)
    }

    @Test
    fun farbeNachAbweichung() {
        val up = 0xFF00FF00.toInt(); val down = 0xFFFF0000.toInt()
        assertEquals(GlobusFarbe.PUNKT_OHNE, GlobusFarbe.engpass(null, up, down))
        // bei null grau, bei minus 40 Prozent und weniger voll rot
        assertEquals(GlobusFarbe.mischen(GlobusFarbe.PUNKT_OHNE, down, 1f), GlobusFarbe.engpass(-96.0, up, down))
        assertEquals(GlobusFarbe.mischen(GlobusFarbe.PUNKT_OHNE, up, 1f), GlobusFarbe.engpass(40.0, up, down))
    }
}
