package de.troetenrepublik.app.engine

import de.troetenrepublik.app.engine.Einflussverzeichnis.Art
import de.troetenrepublik.app.engine.Einflussverzeichnis.Bestand
import de.troetenrepublik.app.engine.Einflussverzeichnis.Wertbild
import de.troetenrepublik.app.engine.Einflussverzeichnis.ZielArt
import de.troetenrepublik.app.market.Industry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

// Einflussverzeichnis: die mitgelieferten Dateien (assets/welt) und die Umkehrabfragen.
class EinflussverzeichnisTest {
    private fun datei(name: String): String =
        listOf("src/main/assets/$name", "../app/src/main/assets/$name").map { File(it) }.first { it.exists() }.readText()

    private val heute = LocalDate.of(2026, 11, 10).toEpochDay()

    private val index by lazy { Einflussverzeichnis.aufbauen(datei(WeltDaten.EINFLUSS), datei(WeltDaten.ROHSTOFFE)) }

    private val nvidia = Wertbild("NVDA", "HALBLEITER", "US", "US")
    private val vw = Wertbild("VOW3.DE", "FAHRZEUGE", "DE", "DE")
    private val alphamin = Wertbild("AFM.V", "BERGBAU", "CD", "MU")

    @Test(timeout = 20_000)
    fun dateienSindVollstaendigUndStimmig() {
        val i = index
        assertTrue(i.groessen.size > 60)
        assertTrue(i.stand > 0 && i.rohstoffStand > 0)
        for (k in i.kanten) {
            if (k.ziel.art == ZielArt.BRANCHE) assertTrue("Branche ${k.ziel.schluessel} (${k.groesse})", k.ziel.schluessel in Industry.STOCK_KEYS)
            if (k.ziel.art == ZielArt.LAND) assertEquals("Land ${k.ziel.schluessel} (${k.groesse})", 2, k.ziel.schluessel.length)
            assertTrue("Beleg ${k.groesse}", k.beleg.isNotBlank())
            assertTrue("Stand ${k.groesse}", k.stand > 0)
            assertNotNull(i.groessen[k.groesse])
        }
        // Gepflegte Groessen tragen Stand und Ablauf, der Ablauf liegt nach dem Stand
        for (g in i.groessen.values.filter { it.typ !in listOf(Einflussverzeichnis.Typ.ROHSTOFF) && it.ablauf > 0 }) {
            assertTrue(g.id, g.ablauf > g.stand)
        }
        // Programme und Groessen mit Namen haben ihn auf Deutsch und Englisch
        for (g in i.groessen.values.filter { it.typ == Einflussverzeichnis.Typ.PROGRAMM || it.typ == Einflussverzeichnis.Typ.ZINS }) {
            assertTrue(g.id, g.de.isNotBlank() && g.en.isNotBlank() && g.kurzDe.isNotBlank() && g.kurzEn.isNotBlank())
        }
    }

    @Test(timeout = 20_000)
    fun rohstoffdatenSindPlausibel() {
        val i = index
        assertEquals(27, i.rohstoffe.size)
        for (r in i.rohstoffe.values) {
            assertTrue(r.id, r.laender.sumOf { it.prozent } <= 100)
            assertTrue(r.id, r.laender.all { it.land.length == 2 })
            assertTrue(r.id, r.kapitel.isNotBlank())
        }
        // Branchen verweisen nur auf vorhandene Rohstoffe
        for ((b, liste) in i.branchenRohstoffe) {
            assertTrue(b, b in Industry.STOCK_KEYS)
            for (id in liste) assertNotNull("$b: $id", i.rohstoffe[id])
        }
        assertEquals(100, i.rohstoffe.getValue("GALLIUM").konzentration)
        assertEquals(78, i.rohstoffe.getValue("LITHIUM").konzentration)
        assertEquals(100, i.rohstoffe.getValue("GERMANIUM").konzentration)
        assertEquals("CN", i.rohstoffe.getValue("SELTENE_ERDEN").laender.first().land)
        assertTrue(i.rohstoffe.getValue("KUPFER").kritisch)
        assertFalse(i.rohstoffe.getValue("GOLD").kritisch)
        assertEquals("KUPFER", Einflussverzeichnis.rohstoffFuerKurs(i, "HG=F")?.id)
        val chips = Einflussverzeichnis.rohstoffeFuerBranche(i, "HALBLEITER").map { it.id }
        assertTrue("SILIZIUM" in chips && "GALLIUM" in chips)
    }

    @Test(timeout = 20_000)
    fun bestehendeListenSpeisenDenIndex() {
        val i = index
        val hormus = Engpaesse.STELLEN.first { it.id == "chokepoint6" }
        assertEquals(hormus.werte.toSet(), Einflussverzeichnis.werte(i, "engpass:chokepoint6", heute).toSet())
        assertTrue("IR" in Einflussverzeichnis.laender(i, "engpass:chokepoint6", heute))
        val taiwan = Spannungen.GEBIETE.first { it.id == "TWN" }
        assertTrue(Einflussverzeichnis.werte(i, "spannung:TWN", heute).containsAll(taiwan.werte))
        assertTrue("TW" in Einflussverzeichnis.laender(i, "spannung:TWN", heute))
        assertTrue(Einflussverzeichnis.werte(i, "klima:enso", heute).containsAll(Enso.WERTE))
        // Die Datei erweitert die Stellen um Branchen
        assertTrue("HALBLEITER" in Einflussverzeichnis.branchen(i, "engpass:chokepoint11", heute))
        // Alle Durchbruchthemen sind Groessen
        for (t in Durchbrueche.THEMEN) assertNotNull(t.id, i.groessen["durchbruch:" + t.id])
    }

    @Test(timeout = 20_000)
    fun halbleiterwertWirdVonDenRichtigenGroessenGetroffen() {
        val ids = Einflussverzeichnis.fuerWert(index, nvidia, heute).map { it.groesse.id }
        assertTrue("halbleiter" in ids)
        assertTrue("programm:chips-us" in ids)
        assertTrue("programm:exportkontrolle" in ids)
        assertTrue("engpass:chokepoint11" in ids)
        assertTrue("spannung:TWN" in ids)
        assertTrue("durchbruch:ki" in ids)
        // EU-Programm trifft einen US-Wert nicht, ein EU-Wert wird getroffen
        assertFalse("programm:chips-eu" in ids)
        assertTrue("programm:chips-eu" in Einflussverzeichnis.fuerWert(index, Wertbild("IFX.DE", "HALBLEITER", "DE", "DE"), heute).map { it.groesse.id })
        // Der genannte Wert (CHIPS-Zusage) hat Vorrang vor der Branche und ist gemeldet
        val chips = Einflussverzeichnis.fuerWert(index, Wertbild("INTC", "HALBLEITER", "US"), heute).first { it.groesse.id == "programm:chips-us" }
        assertEquals(ZielArt.WERT, chips.kante.ziel.art)
        assertEquals(Art.GEMELDET, chips.kante.art)
    }

    @Test(timeout = 20_000)
    fun autobauerUndFoerderer() {
        val a = Einflussverzeichnis.fuerWert(index, vw, heute).map { it.groesse.id }
        assertTrue("programm:co2-flotte" in a)
        assertTrue("programm:zoelle-232" in a)
        assertTrue("china" in a)
        assertFalse("programm:ira" in a)
        assertFalse("programm:chips-us" in a)
        val b = Einflussverzeichnis.fuerWert(index, alphamin, heute).map { it.groesse.id }
        assertTrue("dollar" in b && "china" in b)
        assertFalse("programm:co2-flotte" in b)
        // Ein Wert ohne Branche trifft nur genannte Werte
        val ohne = Einflussverzeichnis.fuerWert(index, Wertbild("XYZ", null, null), heute)
        assertTrue(ohne.isEmpty())
    }

    @Test(timeout = 20_000)
    fun abgelaufeneVerbindungenFallenHeraus() {
        val spaet = LocalDate.of(2030, 1, 1).toEpochDay()
        val ids = Einflussverzeichnis.fuerWert(index, nvidia, spaet).map { it.groesse.id }
        assertFalse("programm:chips-us" in ids)
        assertFalse("halbleiter" in ids)
        // Handlisten ohne Ablauf bleiben (Engpass Taiwanstrasse hat in der Datei einen Ablauf, die Werteliste nicht)
        assertTrue(Einflussverzeichnis.werte(index, "engpass:chokepoint11", spaet).isNotEmpty())
    }

    @Test(timeout = 20_000)
    fun depotWirdNachAnteilGeordnet() {
        val bestand = listOf(
            Bestand(vw, "Volkswagen", 0.30),
            Bestand(nvidia, "NVIDIA", 0.20),
            Bestand(Wertbild("TSM", "HALBLEITER", "TW", "TW"), "TSMC", 0.10),
            Bestand(Wertbild("MAERSK-B.CO", "VERKEHR", "DK", "DK"), "Maersk", 0.40),
        )
        val t = Einflussverzeichnis.imDepot(index, "engpass:chokepoint11", bestand, heute)
        // Taiwanstrasse: Maersk, NVIDIA und TSMC (genannt), VW ueber die Branche Fahrzeuge
        assertEquals(listOf("MAERSK-B.CO", "VOW3.DE", "NVDA", "TSM"), t.map { it.bestand.wert.symbol })
        // Genannte Werte vor Branchenverbindung
        assertEquals(ZielArt.WERT, t.first { it.bestand.wert.symbol == "TSM" }.kante.ziel.art)
        assertEquals(ZielArt.BRANCHE, t.first { it.bestand.wert.symbol == "VOW3.DE" }.kante.ziel.art)
        // Hormus trifft Maersk
        val h = Einflussverzeichnis.imDepot(index, "engpass:chokepoint6", bestand, heute).map { it.bestand.wert.symbol }
        assertTrue("MAERSK-B.CO" in h && "NVDA" !in h)
    }

    @Test(timeout = 20_000)
    fun alphaDreiZuAlphaZwei() {
        assertEquals("TW", Einflussverzeichnis.alpha2("TWN"))
        assertEquals("IR", Einflussverzeichnis.alpha2("IRN"))
        // Alle Anrainer und Laender der Listen sind zugeordnet
        for (s in Engpaesse.STELLEN) for (a in s.anrainer) assertNotNull(a, Einflussverzeichnis.alpha2(a))
        for (g in Spannungen.GEBIETE) for (a in g.laender) assertNotNull(a, Einflussverzeichnis.alpha2(a))
    }

    @Test(timeout = 20_000)
    fun fehlendeDateienGebenLeerenIndexMitListen() {
        val i = Einflussverzeichnis.aufbauen(null, null)
        assertTrue(i.rohstoffe.isEmpty())
        assertTrue(Einflussverzeichnis.werte(i, "engpass:chokepoint6", heute).isNotEmpty())
        val kaputt = Einflussverzeichnis.aufbauen("{kein json", "[]")
        assertTrue(kaputt.rohstoffe.isEmpty())
    }
    @Test(timeout = 20_000)
    fun rohstoffbindungDerBranche() {
        val i = index
        // Halbleiter: Gallium, Germanium, Silizium, Helium und Flussspat kommen zu mindestens 80 % aus drei Laendern
        assertEquals(37.5, Einflussverzeichnis.rohstoffBindung(i, "HALBLEITER")!!, 1e-9)
        assertNull(Einflussverzeichnis.rohstoffBindung(i, "BANKEN"))
        assertNull(Einflussverzeichnis.rohstoffBindung(i, null))
        assertNull(Einflussverzeichnis.rohstoffBindung(i, "GIBT_ES_NICHT"))
        // Bau: Eisenerz 66 %, Mangan 74 %, Kupfer 48 %, Aluminium 71 %, Zink 53 %: keine hohe Konzentration
        assertEquals(100.0, Einflussverzeichnis.rohstoffBindung(i, "BAU")!!, 1e-9)
    }

    @Test(timeout = 20_000)
    fun rohstoffbindungImFinderMitGewichtNull() {
        val ohne = Aktienfinder.bewerten(listOf(Aktienfinder.Daten("A", "A", kgv = 15.0)))
        val mit = Aktienfinder.bewerten(listOf(Aktienfinder.Daten("A", "A", kgv = 15.0, rohstoffPunkte = 10.0)))
        assertEquals(0, Aktienfinder.Bereich.WELT.gewicht)
        assertEquals(100, Aktienfinder.Bereich.entries.sumOf { it.gewicht })
        // Berechnet und gezeigt, aber ohne Einfluss auf die Gesamtpunktzahl
        assertEquals(10.0, mit[0].bereiche[Aktienfinder.Bereich.WELT]!!, 1e-9)
        assertEquals(ohne[0].gesamt, mit[0].gesamt, 1e-9)
        assertNull(ohne[0].bereiche[Aktienfinder.Bereich.WELT])
    }
}
