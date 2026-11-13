package de.troetenrepublik.app.engine

import de.troetenrepublik.app.engine.FinderVorlagen.Feld
import de.troetenrepublik.app.engine.FinderVorlagen.Grenze
import de.troetenrepublik.app.engine.FinderVorlagen.Pruefung
import de.troetenrepublik.app.sammelWerte
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

// Vorlagen und harte Grenzen im Aktienfinder (5.94) mit erfundenen Werten.
class Fassung594VorlagenTest {

    private fun daten(symbol: String, mitDetails: Boolean = false, leer: Double? = null, kurs: Double = 10.0) =
        Aktienfinder.Daten(symbol, symbol, "US", kgv = 12.0, boersenwert = 1e9, leer = leer, mitDetails = mitDetails, werte = mapOf(Feld.KURS to kurs))

    @Test
    fun ungeprueftStattAusgeschieden() {
        val g = listOf(Grenze(Feld.KURS, 5.0), Grenze(Feld.LEER, 20.0))
        assertEquals(Pruefung.OFFEN, FinderVorlagen.pruefen(daten("A"), g))
        assertEquals(Pruefung.NEIN, FinderVorlagen.pruefen(daten("A", mitDetails = true), g))
        assertEquals(Pruefung.JA, FinderVorlagen.pruefen(daten("A", mitDetails = true, leer = 25.0), g))
        // Eine Grenze des Sammelabrufs scheidet sofort aus, auch wenn anderes noch fehlt
        assertEquals(Pruefung.NEIN, FinderVorlagen.pruefen(daten("A", kurs = 3.0), g))
    }

    @Test
    fun auswahlStelltUngeprueftesNachHinten() {
        val l = Aktienfinder.bewerten(listOf(
            daten("OFFEN", kurs = 50.0),
            daten("JA", mitDetails = true, leer = 22.0, kurs = 8.0),
            daten("NEIN", mitDetails = true, leer = 5.0),
        ))
        val f = Aktienfinder.Filter(grenzen = listOf(Grenze(Feld.KURS, 5.0), Grenze(Feld.LEER, 20.0)))
        val a = Aktienfinder.auswahl(l, f, Aktienfinder.Sortierung.NachFeld(Feld.KURS))
        assertEquals(listOf("JA", "OFFEN"), a.map { it.symbol })
    }

    @Test
    fun relativesVolumenUndFuenfJahresWachstum() {
        val d = Aktienfinder.Daten("A", "A", kgv = 20.0, peg = 2.0, werte = mapOf(Feld.REL_VOLUMEN to 3.0, Feld.VOLUMEN_SCHNITT to 1e6))
        assertEquals(3.0, FinderVorlagen.wert(d, Feld.REL_VOLUMEN)!!, 1e-9)
        // Ohne Angabe von Yahoo: KGV durch PEG
        assertEquals(10.0, FinderVorlagen.wert(d, Feld.GEWINN_FUENF)!!, 1e-9)
    }

    @Test
    fun kerzenwerte() {
        val start = LocalDate.of(2025, 9, 1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val t = List(200) { start + it * 86_400_000L }
        // Steigt gleichmaessig: neues Hoch, Kurs ueber der 20-Tage-Linie, RSI hoch
        val c = List(200) { 100.0 + it }
        val w = FinderVorlagen.kerzenWerte(t, c)
        assertEquals(0.0, w.getValue(Feld.HOCH20), 1e-9)
        assertTrue(w.getValue(Feld.LINIE20) > 0)
        assertTrue(w.getValue(Feld.RSI) > 90)
        assertEquals((299.0 / 294.0 - 1) * 100, w.getValue(Feld.WOCHE), 1e-9)
        // Letzter Schluss 2025 ist Tag 121 (31.12.)
        assertEquals((299.0 / 221.0 - 1) * 100, w.getValue(Feld.JAHRESANFANG), 1e-9)
        assertTrue(FinderVorlagen.kerzenWerte(t.take(40), c.take(40)).isEmpty())
    }

    @Test
    fun kreuzungNurFrisch() {
        // Faellt 80 Tage, dann steil hoch: die 20-Tage-Linie kreuzt die 50-Tage-Linie nach oben
        val c = List(80) { 200.0 - it } + List(60) { 120.0 + 4.0 * it }
        val t = List(c.size) { it * 86_400_000L }
        val s20 = Indikatoren.sma(c, 20)
        val s50 = Indikatoren.sma(c, 50)
        val i = (81 until c.size).first { s20[it - 1]!! <= s50[it - 1]!! && s20[it]!! > s50[it]!! }
        assertEquals(1.0, FinderVorlagen.kerzenWerte(t.take(i + 3), c.take(i + 3)).getValue(Feld.KREUZUNG), 0.0)
        assertEquals(0.0, FinderVorlagen.kerzenWerte(t.take(i + 12), c.take(i + 12)).getValue(Feld.KREUZUNG), 0.0)
    }

    @Test
    fun sammelabruf() {
        val jetzt = 1_800_000_000_000L
        val k = mapOf(
            "regularMarketPrice" to 250.0, "regularMarketOpen" to 102.0, "regularMarketPreviousClose" to 100.0,
            "fiftyTwoWeekHigh" to 500.0, "fiftyDayAverage" to 110.0, "twoHundredDayAverage" to 100.0,
            // Ein Termin liegt eine Woche zurueck, der andere in drei Tagen
            "earningsTimestamp" to (jetzt / 1000 - 7 * 86_400).toDouble(), "earningsTimestampStart" to (jetzt / 1000 + 3 * 86_400).toDouble(),
        )
        val (w, termin) = sammelWerte(k, 0.5, "GBp", jetzt)
        // 250 Pence = 2,50 Pfund, bei 0,5 Pfund je Euro 5 Euro
        assertEquals(5.0, w.getValue(Feld.KURS), 1e-9)
        assertEquals(2.0, w.getValue(Feld.LUECKE), 1e-9)
        assertEquals(-50.0, w.getValue(Feld.HOCH52), 1e-9)
        assertEquals(10.0, w.getValue(Feld.SMA50_200), 1e-9)
        assertEquals(jetzt + 3 * 86_400_000L, termin)
        assertNull(sammelWerte(emptyMap(), 1.0, "USD", jetzt).second)
    }

    @Test
    fun vorlagenEindeutig() {
        val v = FinderVorlagen.VORLAGEN
        assertEquals(42, v.size)
        // Gap-Scanner: Luecke nach oben absteigend, nach unten aufsteigend sortiert
        assertEquals(Feld.LUECKE, FinderVorlagen.vorlage("LUECKE_AUF")!!.sortierung)
        assertTrue(FinderVorlagen.vorlage("LUECKE_AB")!!.aufsteigend)
        assertEquals(v.size, v.map { it.id }.toSet().size)
        assertEquals(v.size, v.map { it.nummer }.toSet().size)
        // Vorlage 20 ist dieselbe wie 10 und steht nicht doppelt
        assertTrue(v.none { it.nummer == 20 })
        assertTrue(v.all { it.grenzen.isNotEmpty() })
    }
}
