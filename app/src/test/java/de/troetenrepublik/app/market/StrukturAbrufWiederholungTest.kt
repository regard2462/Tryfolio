package de.troetenrepublik.app.market

import de.troetenrepublik.app.engine.Struktur
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// Die Suche der SEC (13G und 13D) wird nach einem Fehler einmal nach der Pause wiederholt, andere Abrufe nicht.
class StrukturAbrufWiederholungTest {

    private class Netz(private val ausfaelle: Map<String, Int>) : StrukturNetz {
        val aufrufe = ArrayList<String>()
        override suspend fun sec(url: String): String? {
            aufrufe += url
            val bisher = aufrufe.count { it == url }
            if (bisher <= (ausfaelle.entries.firstOrNull { url.contains(it.key) }?.value ?: 0)) return null
            return when {
                url.contains("submissions") -> "{\"name\":\"Beispiel AG\",\"filings\":{\"recent\":{\"form\":[],\"accessionNumber\":[],\"primaryDocument\":[],\"filingDate\":[],\"items\":[]}}}"
                url.contains("search-index") -> "{\"hits\":{\"hits\":[]}}"
                else -> null
            }
        }
        override suspend fun secBis(url: String, max: Long, genug: (String) -> Boolean): String? = null
        override suspend fun wiki(url: String): String? = null
    }

    private class Speicher : FirmenSpeicher {
        private val dateien = HashMap<String, String>()
        init { dateien[StrukturAbruf.DATEI] = Struktur.dateiSchreiben(Struktur.Datei(mapOf("XYZ" to Struktur.Eintrag(0L, "1234", null)))) }
        override fun lesen(name: String): String? = dateien[name]
        override fun schreiben(name: String, text: String) { dateien[name] = text }
    }

    private fun lauf(netz: Netz, pausen: MutableList<Long>) = runBlocking {
        StrukturAbruf(netz, Speicher(), { 1_800_000_000_000L }, { pausen += it }).laden("XYZ", "Beispiel AG", "", true, "de")
    }

    @Test
    fun einFehlerBeiDerSucheWirdWiederholt() {
        val netz = Netz(mapOf("13G" to 1, "13D" to 1))
        val pausen = ArrayList<Long>()
        val e = lauf(netz, pausen)!!
        val suchen = netz.aufrufe.filter { it.contains("search-index") }
        // Je Suche zwei Anfragen: der Fehler und die Wiederholung
        assertEquals(4, suchen.size)
        assertEquals(suchen[0], suchen[1])
        assertEquals(suchen[2], suchen[3])
        assertTrue("13G" in e.geprueft)
        assertTrue("13D" in e.geprueft)
        assertTrue(pausen.all { it == StrukturAbruf.PAUSE_MS })
    }

    @Test
    fun nurEineWiederholung() {
        val netz = Netz(mapOf("search-index" to 5))
        val pausen = ArrayList<Long>()
        val e = lauf(netz, pausen)
        // Auch die zweite Antwort fehlt: genau zwei Anfragen je Suche, nichts als geprueft gemeldet
        assertEquals(4, netz.aufrufe.count { it.contains("search-index") })
        assertFalse(e != null && "13G" in e.geprueft)
        assertFalse(e != null && "13D" in e.geprueft)
    }

    @Test
    fun ohneFehlerEineAnfrageJeSuche() {
        val netz = Netz(emptyMap())
        lauf(netz, ArrayList())
        assertEquals(2, netz.aufrufe.count { it.contains("search-index") })
    }
}
