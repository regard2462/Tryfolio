package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.Candle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.time.LocalDate
import kotlin.math.sin

// Fassung 5.92: „Im Preis?“. Schaetzung aus der Geschichte, Einordnung heute, Reihen fuer die Schaetzung, Terminkurve,
// datierte Signale und der Leser der Futures-Tabellen der EIA, ohne Netz mit kleinen Beispielen.
class Fassung592EingepreistTest {

    private fun tag(s: String) = LocalDate.parse(s).toEpochDay()

    // Deterministisches Rauschen zwischen -1 und 1.
    private fun rauschen(i: Int) = sin(i * 12.9898 + 78.233) * 43758.5453 % 1.0

    // Wochen ab 2010 mit y = Monatsmuster + steigung * x + staerke * Rauschen.
    private fun punkte(n: Int, steigung: Double, staerke: Double, saison: Boolean = true): List<Eingepreist.Punkt> {
        val start = tag("2010-01-01")
        return (0 until n).map { i ->
            val t = start + 7L * i
            val x = 10 * sin(i / 9.0)
            val monat = LocalDate.ofEpochDay(t).monthValue
            val muster = if (saison) (if (monat in 6..8) 20.0 else -5.0) else 0.0
            Eingepreist.Punkt(t, x, muster + steigung * x + staerke * rauschen(i))
        }
    }

    // ------------------------------------------------------------ Schaetzung

    @Test fun schaetzungFindetSteigungUndMonatsmuster() {
        val s = Eingepreist.schaetzen(punkte(600, -1.0, 2.0), saison = true, vorzeichen = -1)!!
        assertEquals(-1.0, s.steigung, 0.05)
        assertTrue(s.r2 > 0.8)
        assertEquals(Eingepreist.Staerke.DEUTLICH, s.staerke)
        // Sommer liegt 25 hoeher als Winter
        assertEquals(25.0, s.erwartet(0.0, 7) - s.erwartet(0.0, 1), 0.5)
        assertEquals(600, s.n)
    }

    @Test fun ohneSaisonWuerdeDasMusterRauschen() {
        // Ohne Monatsachsen erklaert x nur einen kleinen Teil, weil das Sommermuster als Rest bleibt
        val mit = Eingepreist.schaetzen(punkte(600, -1.0, 2.0), saison = true, vorzeichen = -1)!!
        val ohne = Eingepreist.schaetzen(punkte(600, -1.0, 2.0), saison = false, vorzeichen = -1)!!
        assertTrue(ohne.r2 < mit.r2)
    }

    @Test fun schwacherOderFalscherZusammenhang() {
        assertEquals(Eingepreist.Staerke.KEINE, Eingepreist.schaetzen(punkte(600, -0.1, 10.0), true, -1)!!.staerke)
        // Richtige Staerke, falsche Richtung: kein Zusammenhang
        assertEquals(Eingepreist.Staerke.KEINE, Eingepreist.schaetzen(punkte(600, 1.0, 2.0), true, -1)!!.staerke)
        assertEquals(Eingepreist.Staerke.DEUTLICH, Eingepreist.schaetzen(punkte(600, 1.0, 2.0), true, 0)!!.staerke)
        assertEquals(Eingepreist.Staerke.SCHWACH, Eingepreist.staerke(0.2, -1.0, -1))
        assertEquals(Eingepreist.Staerke.MITTEL, Eingepreist.staerke(0.3, -1.0, -1))
        // Zu wenige Punkte
        assertNull(Eingepreist.schaetzen(punkte(50, -1.0, 2.0), true, -1))
    }

    @Test fun ausreisserWerdenGekappt() {
        // Wenige extreme Wochen (wie der negative Oelpreis 2020) duerfen die Steigung nicht umdrehen
        val p = punkte(600, -1.0, 2.0, saison = false).mapIndexed { i, q -> if (i in 300..305) q.copy(x = 500.0, y = 900.0) else q }
        val s = Eingepreist.schaetzen(p, saison = false, vorzeichen = -1)!!
        assertTrue(s.steigung < -0.5)
        assertNotEquals(Eingepreist.Staerke.KEINE, s.staerke)
        assertTrue(s.xMax < 500)
    }

    private fun assertNotEquals(a: Any, b: Any) = assertFalse(a == b)

    // ------------------------------------------------------------ Einordnung

    @Test fun einordnungHeute() {
        val s = Eingepreist.schaetzen(punkte(600, -1.0, 2.0, saison = false), saison = false, vorzeichen = -1)!!
        val t = tag("2026-10-28")
        // Erwartet bei x = -5: etwa +5. Markt zeigt 5: passt, gruen
        val passt = Eingepreist.einordnen(s, -5.0, s.erwartet(-5.0, 10), t)
        assertEquals(Eingepreist.Urteil.PASST, passt.urteil)
        assertEquals(Fruehwarnung.Ampel.GRUEN, passt.ampel)
        assertEquals(50.0, passt.perzentil, 10.0)
        // Markt deutlich hoeher als ueblich: ungewoehnlich, rot
        val hoch = Eingepreist.einordnen(s, -5.0, s.erwartet(-5.0, 10) + 30, t)
        assertEquals(Eingepreist.Urteil.HOEHER, hoch.urteil)
        assertEquals(Fruehwarnung.Ampel.ROT, hoch.ampel)
        assertEquals(30.0, hoch.rest, 1e-9)
        // Etwas tiefer: gelb
        val tief = Eingepreist.einordnen(s, -5.0, s.erwartet(-5.0, 10) + s.reste[s.reste.size / 10], t)
        assertEquals(Eingepreist.Urteil.TIEFER, tief.urteil)
        assertEquals(Fruehwarnung.Ampel.GELB, tief.ampel)
        // Ohne Zusammenhang keine Ampel
        val keine = Eingepreist.einordnen(Eingepreist.schaetzen(punkte(600, -0.1, 10.0), true, -1)!!, 0.0, 0.0, t)
        assertEquals(Eingepreist.Urteil.UNKLAR, keine.urteil)
        assertNull(keine.ampel)
    }

    // ------------------------------------------------------------ Reihen

    // Wochenreihe ueber sieben Jahre: jedes Jahr 10 mehr.
    private fun lagerReihe(): List<Pair<Long, Double>> {
        val ende = LocalDate.parse("2026-10-23")
        return (0 until 7 * 52).map { w -> val d = ende.minusWeeks(w.toLong()); d.toEpochDay() to 400.0 + 10 * (d.year - 2019) }.reversed()
    }

    @Test fun abweichungenWieDasSignal() {
        val r = lagerReihe()
        val a = Eingepreist.abweichungen(r)
        assertEquals(Fruehindikatoren.lager(Fruehindikatoren.ROHOEL, r)!!.abweichung!!, a.last().second, 1e-9)
        // Die ersten Jahre haben keine drei Vorjahre
        assertTrue(a.first().first >= tag("2022-10-01"))
        val p = Eingepreist.abweichungen(r, punkte = true)
        assertEquals(30.0, p.last().second, 1e-9)
    }

    @Test fun anstiegeUndJahresvergleicheWieDieSignale() {
        val antraege = (0 until 120).map { i -> tag("2024-01-06") + 7L * i to 200_000.0 + 1_000.0 * (i % 30) }
        assertEquals(Fruehindikatoren.erstantraege(antraege)!!.abweichung!!, Eingepreist.anstiege(antraege).last().second, 1e-9)
        val tage = (0 until 800).map { i -> tag("2024-08-01") + i to 2_000_000.0 + 1_000.0 * i + 50_000.0 * ((i % 7) / 6) }
        assertEquals(Fruehindikatoren.fluggaeste(tage)!!.abweichung!!, Eingepreist.jahresvergleiche(tage).last().second, 1e-9)
    }

    @Test fun preisGegenSchnittHochUndRelativ() {
        val r = (0 until 120).map { i -> tag("2024-01-05") + 7L * i to if (i < 119) 100.0 else 110.0 }
        assertEquals((110.0 / (100.0 + 10.0 / 53) - 1) * 100, Eingepreist.gegenSchnitt(r).last().second, 1e-9)
        val zins = (0 until 120).map { i -> tag("2024-01-05") + 7L * i to if (i == 80) 5.0 else 4.0 }
        assertEquals(-1.0, Eingepreist.unterHoch(zins).last().second, 1e-9)
        val a = (0 until 60).map { i -> tag("2024-01-05") + 7L * i to 100.0 * (1 + 0.01 * i) }
        val b = (0 until 60).map { i -> tag("2024-01-05") + 7L * i to 100.0 }
        assertEquals((1.59 / 1.07 - 1) * 100, Eingepreist.relativ(a, b, 364).last().second, 1e-9)
    }

    @Test fun paarenNimmtDenLetztenWertDavor() {
        val x = listOf(tag("2024-01-05") to 1.0, tag("2024-01-12") to 2.0)
        val y = listOf(tag("2024-01-04") to 10.0, tag("2024-01-11") to 20.0)
        val p = Eingepreist.paaren(x, y, 3)
        assertEquals(listOf(10.0, 20.0), p.map { it.y })
        assertTrue(Eingepreist.paaren(x, y, 0).isEmpty())
    }

    // ------------------------------------------------------------ Terminkurve

    @Test fun kontrakteUndKurve() {
        val k = Eingepreist.kontrakte("CL", "NYM", 2026, 11, 4)
        assertEquals(listOf("CLX26.NYM", "CLZ26.NYM", "CLF27.NYM", "CLG27.NYM"), k.map { it.symbol })
        val jetzt = 1_793_160_000_000L
        fun c(v: Double, t: Long = jetzt) = listOf(Candle(t, v, v, v, v))
        // November abgelaufen (letzter Kurs eine Woche alt), dann vier frische Kontrakte
        val kerzen = mapOf(
            "CLX26.NYM" to c(99.0, jetzt - 7 * 86_400_000L), "CLZ26.NYM" to c(102.83), "CLF27.NYM" to c(101.06),
            "CLG27.NYM" to c(99.04), "CLH27.NYM" to c(96.83),
        )
        val kurve = Eingepreist.kurve(Eingepreist.kontrakte("CL", "NYM", 2026, 11, 5), kerzen)
        assertEquals(listOf("CLZ26.NYM", "CLF27.NYM", "CLG27.NYM", "CLH27.NYM"), kurve.map { it.first.symbol })
        // Backwardation: naher Kontrakt teurer, etwa 24 % im Jahr
        assertEquals(kotlin.math.ln(102.83 / 96.83) * 400, Eingepreist.kurvenSpanne(kurve)!!, 1e-9)
        assertNull(Eingepreist.kurvenSpanne(kurve.take(3)))
        // Contango beim Gas im Herbst: negativ
        assertTrue(Eingepreist.spanne(2.8, 3.5, 3)!! < 0)
        assertNull(Eingepreist.spanne(-37.6, 20.0, 3))
    }

    @Test fun lagerGegenKurveVonEndeZuEnde() {
        // Lager und Spanne ueber 20 Jahre: knappes Lager, steile Kurve
        val start = LocalDate.parse("2004-01-02")
        val lager = (0 until 20 * 52).map { w ->
            val d = start.plusWeeks(w.toLong())
            d.toEpochDay() to 400.0 + 30 * sin(w / 15.0)
        }
        val abw = Eingepreist.abweichungen(lager).toMap()
        val tabelle = lager.map { (t, _) ->
            val a = abw[t] ?: 0.0
            val spanne = -1.0 * a + 2 * rauschen(t.toInt())
            val f1 = 80.0
            t to doubleArrayOf(f1, f1, f1, f1 / kotlin.math.exp(spanne * 3 / 1200))
        }
        val heute = lager.last().first
        val kurve = (0 until 4).map { i -> Eingepreist.Kontrakt(2024, 1 + i, "CL") to 80.0 * (1 - 0.01 * i) }
        val ip = Fruehindikatoren.imPreisLager(Fruehindikatoren.ROHOEL, lager, tabelle, kurve, heute)!!
        assertEquals(Fruehindikatoren.Mass.KURVE, ip.mass)
        assertEquals(-1.0, ip.einordnung.schaetzung.steigung, 0.1)
        assertEquals(Eingepreist.Staerke.DEUTLICH, ip.einordnung.schaetzung.staerke)
        assertEquals("CL", ip.markt)
    }

    // ------------------------------------------------------------ Datierte Signale

    private fun kerzen(vararg werte: Double, ab: Long = tag("2026-01-05")): List<Candle> =
        werte.mapIndexed { i, v -> Candle((ab + i) * 86_400_000L, v, v, v, v) }

    @Test fun seitDemSignalGegenDenIndex() {
        val aktie = kerzen(100.0, 105.0, 120.0)
        val index = kerzen(100.0, 100.0, 102.0)
        val (mehr, tage) = Eingepreist.seit(aktie, index, tag("2026-01-05") * 86_400_000L + 3_600_000L)!!
        assertEquals(1.2 / 1.02 - 1, mehr, 1e-12)
        assertEquals(2, tage)
        // Ohne Index die einfache Rendite
        assertEquals(0.2, Eingepreist.seit(aktie, null, tag("2026-01-05") * 86_400_000L)!!.first, 1e-12)
        assertNull(Eingepreist.seit(aktie, index, tag("2026-02-01") * 86_400_000L))
    }

    @Test fun laufUndRichtwert() {
        val zeit = tag("2026-01-05") * 86_400_000L
        val idx = kerzen(100.0, 100.0)
        // Ohne fruehere Faelle Richtwert 10 %: 18 % gelaufen ist ein Grossteil
        val gross = Eingepreist.gelaufen(kerzen(100.0, 118.0), idx, zeit, 1)!!
        assertEquals(Eingepreist.Lauf.GROSSTEIL, gross.lauf)
        assertEquals(Fruehwarnung.Ampel.GRUEN, gross.ampel)
        assertNull(gross.schnitt)
        assertEquals(Eingepreist.Lauf.TEIL, Eingepreist.gelaufen(kerzen(100.0, 105.0), idx, zeit, 1)!!.lauf)
        assertEquals(Eingepreist.Lauf.WENIG, Eingepreist.gelaufen(kerzen(100.0, 101.0), idx, zeit, 1)!!.lauf)
        assertEquals(Eingepreist.Lauf.GEGEN, Eingepreist.gelaufen(kerzen(100.0, 90.0), idx, zeit, 1)!!.lauf)
        // Abstufung: 9 % gefallen zaehlt in Signalrichtung
        assertEquals(Eingepreist.Lauf.GROSSTEIL, Eingepreist.gelaufen(kerzen(100.0, 91.0), idx, zeit, -1)!!.lauf)
        // Mit fuenf frueheren Faellen von im Schnitt 20 %: 10 % sind nur ein Teil
        val frueher = listOf(0.2, 0.25, 0.15, 0.2, 0.2)
        val teil = Eingepreist.gelaufen(kerzen(100.0, 110.0), idx, zeit, 1, frueher)!!
        assertEquals(Eingepreist.Lauf.TEIL, teil.lauf)
        assertEquals(0.2, teil.schnitt!!, 1e-12)
        assertEquals(5, teil.n)
        // Fruehere Faelle ohne Vorsprung
        val ohne = Eingepreist.gelaufen(kerzen(100.0, 104.0), idx, zeit, 1, listOf(0.01, -0.02, 0.0, -0.01, 0.01))!!
        assertEquals(Eingepreist.Lauf.OHNE_VORSPRUNG, ohne.lauf)
        assertNull(ohne.ampel)
    }

    @Test fun fruehereSignaleNachSechsMonaten() {
        val aktie = (0 until 300).map { i -> Candle((tag("2025-01-01") + i) * 86_400_000L, 0.0, 0.0, 0.0, 100.0 + i) }
        val zeiten = listOf(tag("2025-01-01") * 86_400_000L, tag("2025-01-01") * 86_400_000L + 3_600_000L, tag("2025-10-01") * 86_400_000L)
        // Gleicher Tag zaehlt einmal, das spaete Signal hat noch keine sechs Monate
        val f = Eingepreist.frueher(aktie, null, zeiten)
        assertEquals(1, f.size)
        assertEquals(226.0 / 100.0 - 1, f[0], 1e-12)
    }

    // ------------------------------------------------------------ Quellen und Leser

    @Test fun adressenUndHosts() {
        for (u in listOf(Fruehindikatoren.EIA_FUTURES_OEL, Fruehindikatoren.EIA_FUTURES_GAS)) {
            assertEquals("fruehsignale", Quellenbuendel.quelleZu(java.net.URI(u).host))
        }
        assertEquals("fruehsignale", Quellenbuendel.quelleZu(java.net.URI(Fruehindikatoren.agsiZeitraum(LocalDate.parse("2016-01-01"), LocalDate.parse("2026-10-28"), 2)).host))
        assertEquals("https://fred.stlouisfed.org/graph/fredgraph.csv?id=DGS2&cosd=1976-01-01", Fruehindikatoren.fredAdresse(LocalDate.parse("1976-01-01"), "DGS2"))
        assertEquals(3, Fruehindikatoren.agsiSeiten("""{"last_page":3,"data":[]}"""))
        assertEquals(1, Fruehindikatoren.agsiSeiten("kein json"))
        assertEquals("ZS=F", Fruehindikatoren.getreide("zs=f"))
        assertEquals("ZC=F", Fruehindikatoren.getreide("CL=F"))
        assertEquals(listOf(Fruehindikatoren.DUERRE), Fruehindikatoren.zuWert(null, "", "ZS=F"))
    }

    // BIFF8-Satz: Typ, Laenge, Daten
    private fun satz(typ: Int, daten: ByteArray): ByteArray =
        ByteBuffer.allocate(4 + daten.size).order(ByteOrder.LITTLE_ENDIAN).putShort(typ.toShort()).putShort(daten.size.toShort()).put(daten).array()

    private fun le(n: Int, f: ByteBuffer.() -> Unit): ByteArray = ByteBuffer.allocate(n).order(ByteOrder.LITTLE_ENDIAN).apply(f).array()
    private fun bof(art: Int) = satz(0x0809, le(16) { putShort(0x0600); putShort(art.toShort()) })
    private fun eof() = satz(0x000A, ByteArray(0))
    private fun zahl(z: Int, s: Int, v: Double) = satz(0x0203, le(14) { putShort(z.toShort()); putShort(s.toShort()); putShort(0); putDouble(v) })

    // OLE2-Huelle mit einem Strom "Workbook" in normalen 512-Byte-Sektoren (Strom mindestens 4096 Bytes).
    private fun ole(strom: ByteArray): ByteArray {
        val daten = strom.copyOf(((strom.size + 511) / 512) * 512)
        val n = daten.size / 512
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

    @Test fun futuresTabelleDerEia() {
        // Wie PET_PRI_FUT_S1_W.xls: Inhaltsblatt, dann Rohoel mit vier Kontrakten (der dritte fehlt in einer Zeile)
        val buch = bof(0x0005) + eof() +
            bof(0x0010) + zahl(6, 4, 4.0) + eof() +
            bof(0x0010) + zahl(3, 0, 45_380.0) + zahl(3, 1, 82.02) + zahl(3, 2, 81.41) + zahl(3, 3, 80.77) + zahl(3, 4, 80.05) +
            zahl(4, 0, 45_387.0) + zahl(4, 1, 85.56) + zahl(4, 2, 84.71) + zahl(4, 4, 82.92) + eof() +
            bof(0x0010) + zahl(3, 0, 45_387.0) + zahl(3, 1, 2.763) + eof()
        val datei = ole(buch + ByteArray(4_300 - buch.size))
        val t = XlsLeser.tabelle(datei, 1)
        assertEquals(2, t.size)
        assertEquals(tag("2024-04-05"), t[1].first)
        assertEquals(85.56, t[1].second[0], 1e-12)
        assertTrue(t[1].second[2].isNaN())
        assertEquals(82.92, t[1].second[3], 1e-12)
        assertEquals(2, Eingepreist.spannen(t).size)
        assertEquals(2.763, XlsLeser.tabelle(datei, 2)[0].second[0], 1e-12)
        assertTrue(XlsLeser.tabelle(datei, 9).isEmpty())
        // Zu wenige Zeilen fuer die Plausibilitaet
        assertFalse(Fruehindikatoren.terminPlausibel(Fruehindikatoren.TERMIN.getValue(Fruehindikatoren.ROHOEL), t))
        val lang = (0 until 200).map { i -> (19_000L + 7 * i) to doubleArrayOf(80.0, 79.0, 78.0, 77.0) }
        assertTrue(Fruehindikatoren.terminPlausibel(Fruehindikatoren.TERMIN.getValue(Fruehindikatoren.ROHOEL), lang))
        assertFalse(Fruehindikatoren.terminPlausibel(Fruehindikatoren.TERMIN.getValue(Fruehindikatoren.BENZIN), lang))
    }
}
