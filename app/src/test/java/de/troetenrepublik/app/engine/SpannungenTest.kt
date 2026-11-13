package de.troetenrepublik.app.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Spannungsgebiete: Antwort der GDELT DOC API (timelinevolraw) und Berichtsdichte der letzten sieben Tage gegen davor.
class SpannungenTest {
    // Echte Antwort vom 7.11.2026 zur Abfrage „Taiwan“ (30 Tage; der Index hat Luecken)
    private val antwort = """{"query_details": {"title": "Taiwan", "date_resolution": "day"}, "timeline": [ { "series": "Article Count", "data": [ { "date": "20261010T000000Z", "value": 66, "norm": 1836},{ "date": "20261015T000000Z", "value": 921, "norm": 95273},{ "date": "20261016T000000Z", "value": 2860, "norm": 150288},{ "date": "20261017T000000Z", "value": 1478, "norm": 94046},{ "date": "20261018T000000Z", "value": 1243, "norm": 76163},{ "date": "20261019T000000Z", "value": 2200, "norm": 146057},{ "date": "20261020T000000Z", "value": 2672, "norm": 156425},{ "date": "20261021T000000Z", "value": 2770, "norm": 166970},{ "date": "20261022T000000Z", "value": 2722, "norm": 123805},{ "date": "20261023T000000Z", "value": 1789, "norm": 69302},{ "date": "20261024T000000Z", "value": 1353, "norm": 77623},{ "date": "20261025T000000Z", "value": 656, "norm": 31912},{ "date": "20261026T000000Z", "value": 148, "norm": 4483},{ "date": "20261027T000000Z", "value": 51, "norm": 2218},{ "date": "20261103T000000Z", "value": 132, "norm": 5672},{ "date": "20261104T000000Z", "value": 56, "norm": 2220},{ "date": "20261105T000000Z", "value": 120, "norm": 7479},{ "date": "20261106T000000Z", "value": 1624, "norm": 50779}] }] }"""

    @Test
    fun antwortLesen() {
        val t = Spannungen.lesen(antwort)!!
        assertEquals(18, t.size)
        assertEquals(java.time.LocalDate.of(2026, 10, 10).toEpochDay().toInt(), t.first().t)
        assertEquals(66, t.first().treffer)
        assertEquals(1836, t.first().gesamt)
        assertEquals(java.time.LocalDate.of(2026, 11, 6).toEpochDay().toInt(), t.last().t)
    }

    @Test
    fun leereUndAbgelehnteAntwortenSindNull() {
        assertNull(Spannungen.lesen("{}"))
        assertNull(Spannungen.lesen("Please limit requests to one every 5 seconds."))
    }

    @Test
    fun berichtsdichteGegenDavor() {
        val w = Spannungen.bewerten("TWN", Spannungen.lesen(antwort)!!, 7L)!!
        // letzte Woche (bis 6.11.): 4 Tageswerte mit zusammen 1932 Treffern, davor 14 Tageswerte
        assertEquals(1932, w.artikel)
        assertEquals(1.67, w.faktor, 0.01)
        assertEquals(java.time.LocalDate.of(2026, 11, 6).toEpochDay().toInt(), w.bis)
    }

    @Test
    fun zuWenigTageGebenKeinenVergleich() {
        val t = Spannungen.lesen(antwort)!!.takeLast(6)
        assertNull(Spannungen.bewerten("TWN", t, 7L))
    }

    @Test
    fun gebieteSindEindeutigUndAbfrageIstKodiert() {
        assertEquals(Spannungen.GEBIETE.size, Spannungen.GEBIETE.map { it.id }.distinct().size)
        val a = Spannungen.adresse(Spannungen.gebiet("TWN")!!)
        assertTrue(a.contains("mode=timelinevolraw"))
        assertTrue(a.contains("sourcelang%3Aeng"))
        assertTrue(a.contains("timespan=30d"))
        assertNotNull(Spannungen.gebiet("UKR"))
        val s = Spannungen.sortiert(listOf(Spannungen.Wert(1, "A", 5, 0.8, 1), Spannungen.Wert(1, "B", 5, 2.1, 1)))
        assertEquals("B", s.first().id)
    }
}
