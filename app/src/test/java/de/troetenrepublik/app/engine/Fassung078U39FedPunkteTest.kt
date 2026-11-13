package de.troetenrepublik.app.engine

import de.troetenrepublik.app.i18n.FedPunkteTexte
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

// Zinsprojektionen der Fed (Summary of Economic Projections, Figure 2): Leser der Seite der Federal Reserve an einem
// Ausschnitt der Seite vom 16.09.2026, Sitzungskalender, Median, Kernzeile und die Buendeldatei.
class Fassung078U39FedPunkteTest {
    private fun asset(name: String): File =
        listOf("src/main/assets/$name", "../app/src/main/assets/$name").map { File(it) }.first { it.exists() }

    private fun quelle(name: String): String = javaClass.classLoader!!.getResourceAsStream("fed/$name")!!.readBytes().decodeToString()

    private val tag = LocalDate.of(2026, 9, 16)
    private val seite by lazy { FedPunkte.seiteLesen(quelle("fomcprojtabl20260916_ausschnitt.htm"), tag)!! }
    private val datei by lazy { FedPunkte.lesen(asset(FedPunkte.DATEI).readText(Charsets.UTF_8))!! }

    @Test fun liestDieSeiteDerFed() {
        assertEquals(listOf("2026", "2027", "2028", "2029", FedPunkte.LANG), seite.spalten)
        // Leere Zeilen (4,500 und 4,250 ohne Stimme) fallen weg; hoechster Satz zuerst
        assertEquals(4.375, seite.zeilen.first().satz, 1e-9)
        assertEquals(listOf(4, 8, 0, 0, 0), seite.zeilen.first().stimmen)
        assertEquals(2.875, seite.zeilen.last().satz, 1e-9)
        assertEquals(listOf(4.375 to 4, 4.125 to 12, 3.875 to 2), FedPunkte.punkte(seite, "2026"))
        // Nicht jeder Teilnehmer nennt fuer jedes Jahr einen Satz
        assertEquals(listOf(18, 18, 17, 17, 18), seite.spalten.map { FedPunkte.teilnehmer(seite, it) })
        assertTrue(FedPunkte.punkte(seite, "2031").isEmpty())
    }

    @Test fun seiteStimmtMitDerDateiUeberein() {
        val s = datei.sitzungen.last { it.tag == tag }
        assertEquals(seite.spalten, s.spalten)
        assertEquals(seite.zeilen.map { it.satz to it.stimmen }, s.zeilen.map { it.satz to it.stimmen })
    }

    @Test fun median() {
        // 18 Teilnehmer: Mittel der 9. und 10. Angabe
        assertEquals(4.125, FedPunkte.median(seite, "2026")!!, 1e-9)
        assertEquals(4.125, FedPunkte.median(seite, "2027")!!, 1e-9)
        assertEquals(3.875, FedPunkte.median(seite, "2028")!!, 1e-9)
        assertEquals(3.625, FedPunkte.median(seite, "2029")!!, 1e-9)
        assertEquals(3.25, FedPunkte.median(seite, FedPunkte.LANG)!!, 1e-9)
        assertNull(FedPunkte.median(seite, "2031"))
        // Ungerade Zahl: die mittlere Angabe; gerade Zahl zwischen zwei Saetzen: das Mittel
        val s = FedPunkte.Sitzung(tag, listOf("2026", "2027"), listOf(FedPunkte.Zeile(4.0, listOf(1, 2)), FedPunkte.Zeile(3.5, listOf(1, 2)), FedPunkte.Zeile(3.0, listOf(1, 0))))
        assertEquals(3.5, FedPunkte.median(s, "2026")!!, 1e-9)
        assertEquals(3.75, FedPunkte.median(s, "2027")!!, 1e-9)
    }

    @Test fun kalenderUndDateiname() {
        val (neu, name) = FedPunkte.neuesteSeite(quelle("kalender_ausschnitt.htm"))!!
        assertEquals(tag, neu)
        assertEquals("fomcprojtabl20260916.htm", name)
        assertEquals(tag, FedPunkte.tagAus("fomcprojtabl20260916.htm"))
        assertNull(FedPunkte.neuesteSeite("<html>nichts</html>"))
        assertNull(FedPunkte.tagAus("fomcprojtabl20261340.htm"))
    }

    @Test fun andereSeitenWerdenAbgelehnt() {
        assertNull(FedPunkte.seiteLesen("", tag))
        assertNull(FedPunkte.seiteLesen("<html><table><tr><th>x</th></tr></table></html>", tag))
        // Tabelle mit unbekannter Spalte
        val fremd = "Midpoint of target range or target level for the federal funds rate <table><tr><th>M</th><th>Soon</th></tr><tr><th>4.0</th><td>3</td></tr></table>"
        assertNull(FedPunkte.seiteLesen(fremd, tag))
        // Nur leere Zellen
        val leer = "Midpoint of target range or target level for the federal funds rate <table><tr><th>M</th><th>2026</th></tr><tr><th>4.0</th><td>&nbsp;</td></tr></table>"
        assertNull(FedPunkte.seiteLesen(leer, tag))
    }

    @Test fun schreibenUndLesen() {
        val text = FedPunkte.schreiben("2026-11-12", listOf(seite))
        val d = FedPunkte.lesen(text)!!
        assertEquals(1, d.sitzungen.size)
        assertEquals(tag, d.sitzungen[0].tag)
        assertEquals(seite.zeilen.map { it.satz to it.stimmen }, d.sitzungen[0].zeilen.map { it.satz to it.stimmen })
        assertNull(FedPunkte.lesen("{}"))
        assertNull(FedPunkte.lesen("""{"stand":"2026-11-12","sitzungen":[{"tag":"2026-09-16","j":["Bald"],"p":[[4000,1]]}]}"""))
    }

    @Test fun kernzeileUndMarkt() {
        val k = FedPunkte.kern(datei)!!
        assertEquals(tag, k.sitzung.tag)
        assertEquals(2026, k.jahr)
        assertEquals(4.125, k.median, 1e-9)
        assertEquals(18, k.teilnehmer)
        // Die Sitzung davor (17.06.2026) fuehrt 2026 ebenfalls
        assertEquals(LocalDate.of(2026, 6, 17), k.vorherTag)
        assertEquals(FedPunkte.median(datei.sitzungen[datei.sitzungen.size - 2], "2026")!!, k.vorher!!, 1e-9)
        // Neue Sitzung im Dezember: sie wird zur juengsten, eine Sitzung desselben Tags ersetzt die alte
        val dez = FedPunkte.Sitzung(LocalDate.of(2026, 12, 9), listOf("2026", "2027", FedPunkte.LANG), listOf(FedPunkte.Zeile(4.125, listOf(19, 10, 0)), FedPunkte.Zeile(3.0, listOf(0, 9, 19))))
        val mit = FedPunkte.mit(datei, dez)
        assertEquals(datei.sitzungen.size + 1, mit.sitzungen.size)
        assertEquals(tag, FedPunkte.kern(mit)!!.vorherTag)
        assertEquals(mit.sitzungen.size, FedPunkte.mit(mit, dez).sitzungen.size)
        // Markt: Kontrakt fuer Dezember des Jahres
        val pfad = listOf(Zinserwartung.Monat(2026, 11, 3.9), Zinserwartung.Monat(2026, 12, 3.85), Zinserwartung.Monat(2027, 1, 3.8))
        assertEquals(3.85, FedPunkte.marktEnde(pfad, 2026)!!, 1e-9)
        assertNull(FedPunkte.marktEnde(pfad, 2027))
    }

    @Test fun ganzeDateiIstGepflegt() {
        val n = FedPunkte.DATEI
        val text = asset(n).readText(Charsets.UTF_8)
        assertTrue(n in Alter.dateien())
        assertEquals(1, Datenbuendel.SCHEMA[n])
        assertTrue(asset(n).length() < 30_000)
        assertTrue(datei.sitzungen.size >= 22)
        assertEquals(LocalDate.of(2021, 3, 17), datei.sitzungen.first().tag)
        // Je Sitzung 15 bis 19 Teilnehmer; fuer spaetere Jahre und den laengerfristigen Satz fehlen manchmal einzelne Angaben
        for (s in datei.sitzungen) {
            val erste = FedPunkte.teilnehmer(s, s.spalten.first())
            assertTrue(s.tag.toString(), erste in 15..19)
            for (sp in s.spalten) assertTrue(s.tag.toString() + " " + sp, FedPunkte.teilnehmer(s, sp) in (erste - 2)..erste)
        }
        val im = listOf(File("../daten/$n"), File("daten/$n")).first { it.exists() }
        assertEquals(text, im.readText(Charsets.UTF_8))
        assertTrue(de.troetenrepublik.app.market.Daten.probeLesen(n, text))
    }

    @Test fun texteUndNennung() {
        assertEquals(FedPunkteTexte.schluessel(true), FedPunkteTexte.schluessel(false))
        for (de in listOf(true, false)) for (k in FedPunkteTexte.schluessel(de)) {
            val t = FedPunkteTexte.text(de, k, listOf("a", "b"))
            assertTrue(k, t.isNotBlank() && !t.contains("{") && !t.contains("–") && !t.contains("—"))
        }
        val notice = listOf("NOTICE", "../NOTICE").map { File(it) }.first { it.exists() }.readText(Charsets.UTF_8).replace(Regex("\\s+"), " ")
        assertTrue(notice.contains("indikatoren/fed_punkte.json: Board of Governors of the Federal Reserve System, Summary of Economic Projections"))
    }
}
