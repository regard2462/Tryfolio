package de.troetenrepublik.app.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.time.LocalDate

// Fassung 5.92: Fruehe Signale (Lager gegen den Fuenfjahresbereich, Erstantraege, Fluggaeste, Duerre, EU-Gas) und
// die Leser der Quellen, ohne Netz mit kurzen Beispielen.
class Fassung592FruehindikatorenTest {

    private fun tag(s: String) = LocalDate.parse(s).toEpochDay()

    // Wochenreihe ueber sechs Jahre bis ende; wert(Jahre zurueck) liefert den Stand der jeweiligen Woche.
    private fun wochen(ende: String, wert: (jahreZurueck: Int, woche: Int) -> Double): List<Pair<Long, Double>> {
        val e = LocalDate.parse(ende)
        return (0 until 6 * 53).map { w ->
            val d = e.minusWeeks(w.toLong())
            d.toEpochDay() to wert(((e.toEpochDay() - d.toEpochDay()).toInt() + 182) / 365, w)
        }.reversed()
    }

    // ------------------------------------------------------------ Einordnung gegen den Fuenfjahresbereich

    @Test fun bandNimmtDieselbeJahreszeit() {
        // Jedes Jahr ein anderer Stand: 400, 410, 420, 430, 440 vor 1 bis 5 Jahren
        val r = wochen("2026-10-23") { j, _ -> if (j == 0) 380.0 else 390.0 + 10 * j }
        val b = Fruehindikatoren.band(r)!!
        assertEquals(5, b.jahre)
        assertEquals(400.0, b.min, 1e-9)
        assertEquals(440.0, b.max, 1e-9)
        assertEquals(420.0, b.schnitt, 1e-9)
    }

    // Vorjahre 370 bis 470, Schnitt 420
    private fun vorjahr(j: Int) = listOf(0.0, 370.0, 400.0, 420.0, 440.0, 470.0)[j.coerceAtMost(5)]

    @Test fun lagerKnappUndDarunter() {
        // 395 liegt 6 % unter dem Schnitt von 420, aber im Bereich: gelb, knapp
        val knapp = Fruehindikatoren.lager(Fruehindikatoren.ROHOEL, wochen("2026-10-23") { j, _ -> if (j == 0) 395.0 else vorjahr(j) })!!
        assertEquals(Fruehindikatoren.Lage.KNAPP, knapp.lage)
        assertEquals(Fruehwarnung.Ampel.GELB, knapp.ampel)
        assertEquals(-5.95, knapp.abweichung!!, 0.01)
        // 360 liegt unter dem tiefsten Vorjahr: rot
        val darunter = Fruehindikatoren.lager(Fruehindikatoren.ROHOEL, wochen("2026-10-23") { j, _ -> if (j == 0) 360.0 else vorjahr(j) })!!
        assertEquals(Fruehindikatoren.Lage.DARUNTER, darunter.lage)
        assertEquals(Fruehwarnung.Ampel.ROT, darunter.ampel)
        // Im Schnitt: gruen
        val normal = Fruehindikatoren.lager(Fruehindikatoren.ROHOEL, wochen("2026-10-23") { j, _ -> if (j == 0) 418.0 else vorjahr(j) })!!
        assertEquals(Fruehindikatoren.Lage.UEBLICH, normal.lage)
        assertEquals(Fruehwarnung.Ampel.GRUEN, normal.ampel)
    }

    @Test fun wochenaenderung() {
        val r = wochen("2026-10-23") { j, w -> if (j == 0) 400.0 - w else 420.0 }
        val s = Fruehindikatoren.lager(Fruehindikatoren.BENZIN, r)!!
        assertEquals(1.0, s.aenderung!!, 1e-9)
        assertEquals(tag("2026-10-23"), s.stand)
    }

    @Test fun zuWenigJahre() {
        val r = (0 until 100).map { tag("2025-01-03") + it * 7L to 400.0 }
        assertNull(Fruehindikatoren.lager(Fruehindikatoren.ROHOEL, r))
    }

    @Test fun duerreNurHochIstAuffaellig() {
        // Anteil in %: Vergleich in Punkten; wenig Duerre bleibt gruen
        val wenig = Fruehindikatoren.lager(Fruehindikatoren.DUERRE, wochen("2026-10-20") { j, _ -> if (j == 0) 10.0 else 30.0 + j }, punkte = true, nurHoch = true)!!
        assertEquals(Fruehwarnung.Ampel.GRUEN, wenig.ampel)
        assertEquals(-23.0, wenig.abweichung!!, 1e-9)
        val viel = Fruehindikatoren.lager(Fruehindikatoren.DUERRE, wochen("2026-10-20") { j, _ -> if (j == 0) 51.0 else 30.0 + j }, punkte = true, nurHoch = true)!!
        assertEquals(Fruehwarnung.Ampel.ROT, viel.ampel)
        assertEquals(18.0, viel.abweichung!!, 1e-9)
    }

    @Test fun gasEuMitWochenaenderung() {
        val start = LocalDate.parse("2020-10-01")
        val r = (0..(7 * 366)).map { i ->
            val d = start.plusDays(i.toLong())
            d.toEpochDay() to (if (d.year == 2026 && d.monthValue == 10) 70.0 + d.dayOfMonth * 0.1 else 85.0)
        }.filter { LocalDate.ofEpochDay(it.first) <= LocalDate.parse("2026-10-26") }
        val s = Fruehindikatoren.gasEu(r)!!
        assertEquals(72.6, s.wert, 1e-9)
        assertEquals(-12.4, s.abweichung!!, 1e-9)
        assertEquals(0.7, s.aenderung!!, 1e-9)
        assertEquals(Fruehwarnung.Ampel.ROT, s.ampel)
    }

    // ------------------------------------------------------------ Erstantraege und Fluggaeste

    @Test fun erstantraegeAnstiegUeberDasJahrestief() {
        val basis = (0 until 60).map { tag("2025-08-02") + it * 7L to 200_000.0 }
        assertEquals(Fruehwarnung.Ampel.GRUEN, Fruehindikatoren.erstantraege(basis)!!.ampel)
        // Die letzten vier Wochen bei 250.000: Vierwochenschnitt 25 % ueber dem Tief
        val hoch = basis.mapIndexed { i, p -> if (i >= 56) p.first to 250_000.0 else p }
        val s = Fruehindikatoren.erstantraege(hoch)!!
        assertEquals(25.0, s.abweichung!!, 1e-9)
        assertEquals(Fruehwarnung.Ampel.ROT, s.ampel)
        assertEquals(250_000.0, s.wert, 1e-9)
        assertNull(Fruehindikatoren.erstantraege(basis.take(40)))
    }

    @Test fun fluggaesteGegenDieselbenWochentage() {
        val ende = tag("2026-10-27")
        val tage = (0 until 400).map { i -> val t = ende - i; t to (if (i < 30) 2_060_000.0 else 2_000_000.0) }
        val s = Fruehindikatoren.fluggaeste(tage)!!
        assertEquals(3.0, s.abweichung!!, 1e-9)
        assertEquals(Fruehwarnung.Ampel.GRUEN, s.ampel)
        assertEquals(2_060_000.0, s.wert, 1e-9)
        val weniger = (0 until 400).map { i -> val t = ende - i; t to (if (i < 30) 1_920_000.0 else 2_000_000.0) }
        assertEquals(Fruehwarnung.Ampel.ROT, Fruehindikatoren.fluggaeste(weniger)!!.ampel)
        // Ohne Vorjahr kein Signal
        assertNull(Fruehindikatoren.fluggaeste(tage.take(100)))
    }

    // ------------------------------------------------------------ Leser

    @Test fun fredCsv() {
        val csv = "observation_date,ICSA\n2026-10-10,200000\n2026-10-17,194000\n2026-10-24,.\n"
        assertEquals(listOf(tag("2026-10-10") to 200_000.0, tag("2026-10-17") to 194_000.0), Fruehindikatoren.fredLesen(csv))
    }

    @Test fun tsaTabelle() {
        val html = """<table class="table"><thead><tr><th>Date</th><th>Numbers</th></tr></thead><tbody>""" +
            """<tr><td class="text-align-center">10/27/2026</td><td class="text-align-center">1,972,890</td></tr>""" +
            """<tr><td class="text-align-center">10/26/2026</td><td class="text-align-center">2,608,778</td></tr></tbody></table>"""
        assertEquals(listOf(tag("2026-10-26") to 2_608_778.0, tag("2026-10-27") to 1_972_890.0), Fruehindikatoren.tsaLesen(html))
    }

    @Test fun duerreCsv() {
        val csv = "MapDate,AreaOfInterest,None,D0,D1,D2,D3,D4,ValidStart,ValidEnd,StatisticFormatID\n" +
            "20261020,CONUS,26.82,73.18,50.67,28.68,9.37,1.19,2026-10-20,2026-10-26,1\n" +
            "20261013,CONUS,25.46,74.54,53.73,30.33,9.79,1.30,2026-10-13,2026-10-19,1\n"
        assertEquals(listOf(tag("2026-10-13") to 53.73, tag("2026-10-20") to 50.67), Fruehindikatoren.duerreLesen(csv))
        assertTrue(Fruehindikatoren.duerreLesen("a,b\n1,2").isEmpty())
    }

    @Test fun agsiJson() {
        val json = """{"last_page":193,"total":30,"dataset":"EU","gas_day":"2026-10-26","data":[""" +
            """{"name":"EU","code":"eu","gasDayStart":"2026-10-26","gasInStorage":"848.8181","full":"74.93","trend":"0.02"},""" +
            """{"name":"EU","code":"eu","gasDayStart":"2026-10-25","gasInStorage":"848.6","full":"74.91","trend":"0.05"}]}"""
        assertEquals(listOf(tag("2026-10-25") to 74.91, tag("2026-10-26") to 74.93), Fruehindikatoren.agsiLesen(json))
        assertTrue(Fruehindikatoren.agsiLesen("""{"error":"invalid key"}""").isEmpty())
    }

    @Test fun zuordnungZuWerten() {
        assertEquals(Fruehindikatoren.OEL, Fruehindikatoren.zuWert("OEL", ""))
        assertEquals(listOf(Fruehindikatoren.FLUGGAESTE), Fruehindikatoren.zuWert(null, "Airlines"))
        assertEquals(listOf(Fruehindikatoren.ROHOEL), Fruehindikatoren.zuWert(null, "Oil & Gas Refining & Marketing"))
        assertTrue(Fruehindikatoren.zuWert(null, "Semiconductors").isEmpty())
        assertTrue(Fruehindikatoren.zuWert("GOLD", "").isEmpty())
    }

    @Test fun adressen() {
        assertEquals("https://www.eia.gov/dnav/pet/hist_xls/WCESTUS1w.xls", Fruehindikatoren.eiaAdresse("WCESTUS1"))
        assertEquals("https://www.eia.gov/dnav/ng/hist_xls/NW2_EPG0_SWO_R48_BCFw.xls", Fruehindikatoren.eiaAdresse("NW2_EPG0_SWO_R48_BCF"))
        assertTrue(Fruehindikatoren.duerreAdresse(LocalDate.parse("2021-10-01"), LocalDate.parse("2026-10-28")).contains("startdate=10/1/2021&enddate=10/28/2026"))
        // Alle Hosts stehen in der festen Liste der Selbstreparatur
        for (u in listOf(
            Fruehindikatoren.eiaAdresse("WCESTUS1"), Fruehindikatoren.tsaAdresse(2025), Fruehindikatoren.agsiAdresse(),
            Fruehindikatoren.duerreAdresse(LocalDate.parse("2021-10-01"), LocalDate.parse("2026-10-28")),
        )) assertEquals("fruehsignale", Quellenbuendel.quelleZu(java.net.URI(u).host))
        assertEquals("uswirtschaft", Quellenbuendel.quelleZu(java.net.URI(Fruehindikatoren.fredAdresse(LocalDate.parse("2024-10-01"))).host))
    }

    // ------------------------------------------------------------ XLS

    // BIFF8-Satz: Typ, Laenge, Daten
    private fun satz(typ: Int, daten: ByteArray): ByteArray =
        ByteBuffer.allocate(4 + daten.size).order(ByteOrder.LITTLE_ENDIAN).putShort(typ.toShort()).putShort(daten.size.toShort()).put(daten).array()

    private fun le(n: Int, f: ByteBuffer.() -> Unit): ByteArray = ByteBuffer.allocate(n).order(ByteOrder.LITTLE_ENDIAN).apply(f).array()

    private fun bof(art: Int) = satz(0x0809, le(16) { putShort(0x0600); putShort(art.toShort()) })
    private fun eof() = satz(0x000A, ByteArray(0))
    private fun zahl(z: Int, s: Int, v: Double) = satz(0x0203, le(14) { putShort(z.toShort()); putShort(s.toShort()); putShort(0); putDouble(v) })
    private fun rkSatz(z: Int, s: Int, rk: Int) = satz(0x027E, le(10) { putShort(z.toShort()); putShort(s.toShort()); putShort(0); putInt(rk) })

    // OLE2-Huelle mit einem Strom "Workbook" in normalen 512-Byte-Sektoren (Strom mindestens 4096 Bytes).
    private fun ole(strom: ByteArray): ByteArray {
        val daten = strom.copyOf(((strom.size + 511) / 512) * 512)
        val n = daten.size / 512
        // Sektor 0: FAT, Sektor 1: Verzeichnis, ab 2: Strom
        val kopf = le(512) {
            putInt(0xE011CFD0.toInt()); putInt(0xE11AB1A1.toInt())
            position(0x18); putShort(0x3E); putShort(3); putShort(0xFFFE.toShort()); putShort(9); putShort(6)
            position(0x2C); putInt(1); putInt(1); putInt(0); putInt(4096); putInt(-2); putInt(0); putInt(-2); putInt(0)
            putInt(0)
            for (k in 1 until 109) putInt(-1)
        }
        val fat = le(512) {
            putInt(-3); putInt(-2)
            for (k in 0 until n) putInt(if (k == n - 1) -2 else 3 + k)
            while (hasRemaining()) putInt(-1)
        }
        fun eintrag(name: String, typ: Int, start: Int, groesse: Int) = le(128) {
            val b = name.toByteArray(Charsets.UTF_16LE)
            put(b); position(64); putShort((b.size + 2).toShort()); put(typ.toByte())
            position(116); putInt(start); putInt(groesse)
        }
        val verzeichnis = eintrag("Root Entry", 5, -2, 0) + eintrag("Workbook", 2, 2, strom.size) + ByteArray(256)
        return kopf + fat + verzeichnis + daten
    }

    @Test fun xlsZahlenreihe() {
        // Globals, ein Inhaltsblatt mit einer Zahl, das Datenblatt mit Datum in Spalte 0 und Werten in Spalte 1
        val buch = bof(0x0005) + eof() +
            bof(0x0010) + zahl(3, 1, 7.0) + eof() +
            bof(0x0010) + zahl(3, 0, 46_297.0) + zahl(3, 1, 424_134.0) + zahl(4, 0, 46_304.0) +
            rkSatz(4, 1, (415_577 shl 2) or 2) + zahl(5, 0, 46_311.0) + rkSatz(5, 1, (41_251_700 shl 2) or 3) + eof()
        val datei = ole(buch + ByteArray(4_200 - buch.size))
        val r = XlsLeser.reihe(datei)
        assertEquals(3, r.size)
        assertEquals(tag("2026-10-02") to 424_134.0, r[0])
        assertEquals(415_577.0, r[1].second, 1e-9)
        assertEquals(412_517.0, r[2].second, 1e-9)
        assertEquals(tag("2026-10-16"), r[2].first)
    }

    @Test fun xlsRkGleitkomma() {
        // 1.5 als Gleitkomma: obere 32 Bit 0x3FF80000
        assertEquals(1.5, XlsLeser.rk(0x3FF80000), 1e-12)
        assertEquals(0.015, XlsLeser.rk(0x3FF80000 or 1), 1e-12)
        assertEquals(-3.0, XlsLeser.rk((-3 shl 2) or 2), 1e-12)
    }

    @Test fun keinXls() {
        assertTrue(XlsLeser.reihe("<html>Service unavailable</html>".toByteArray()).isEmpty())
        assertNull(XlsLeser.arbeitsmappe(ByteArray(600)))
        assertNotNull(XlsLeser.arbeitsmappe(ole(bof(0x0010) + eof() + ByteArray(4_100))))
    }
}
