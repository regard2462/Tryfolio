package de.troetenrepublik.app.engine

import de.troetenrepublik.app.engine.Einflussverzeichnis.Bestand
import de.troetenrepublik.app.engine.Einflussverzeichnis.Wertbild
import de.troetenrepublik.app.engine.Rohstoffprofil.Quelle
import de.troetenrepublik.app.engine.Rohstoffprofil.Weg
import de.troetenrepublik.app.market.SecBericht
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

// [0.76 q76j] Rohstoffe je Unternehmen statt je Branche (Rohstoffprofil) mit den echten Dateien aus assets/welt.
class Fassung076Q76jTest {
    private fun datei(name: String): String =
        listOf("src/main/assets/$name", "../app/src/main/assets/$name").map { File(it) }.first { it.exists() }.readText()

    private val index by lazy { Einflussverzeichnis.aufbauen(datei(WeltDaten.EINFLUSS), datei(WeltDaten.ROHSTOFFE)) }
    private val heute = LocalDate.of(2026, 11, 10).toEpochDay()

    private fun ids(w: Wertbild, sec: Rohstoffprofil.SecAngaben? = null) = Rohstoffprofil.fuer(index, w, sec).ids
    private fun treffer(w: Wertbild, id: String, sec: Rohstoffprofil.SecAngaben? = null) = Rohstoffprofil.fuer(index, w, sec).treffer.first { it.id == id }

    private val nvidia = Wertbild("NVDA", "HALBLEITER", "US", "US")

    @Test(timeout = 20_000)
    fun nvidiaFertigtNichtSelbst() {
        val e = Rohstoffprofil.fuer(index, nvidia)
        assertEquals(listOf("SILIZIUM"), e.ids)
        assertTrue(e.fabless)
        assertEquals(Weg.FERTIGER, e.treffer.single().weg)
        // Helium, Flussspat, Gallium und Germanium fallen beim Auftragsfertiger an, nicht bei NVIDIA
        assertTrue(listOf("HELIUM", "FLUSSSPAT", "GALLIUM", "GERMANIUM", "KUPFER").none { it in e.ids })
    }

    @Test(timeout = 20_000)
    fun fablessAusBeschreibungUndGalliumUeberDenFertiger() {
        val w = Wertbild("NAVI", "HALBLEITER", "US", "US", industrie = "Semiconductors",
            beschreibung = "Navitas is a fabless semiconductor company that designs gallium nitride power ICs for chargers.")
        val e = Rohstoffprofil.fuer(index, w)
        assertTrue(e.fabless)
        assertEquals(setOf("SILIZIUM", "GALLIUM"), e.ids.toSet())
        assertTrue(e.treffer.all { it.weg == Weg.FERTIGER && it.quelle == Quelle.BESCHREIBUNG })
        assertEquals("GAN", treffer(w, "GALLIUM").verwendung)
    }

    @Test(timeout = 20_000)
    fun eigeneFertigungZeigtWaferGaseUndAetzchemie() {
        for (s in listOf("INTC", "TSM", "MU")) {
            val e = Rohstoffprofil.fuer(index, Wertbild(s, "HALBLEITER", "US"))
            assertTrue(s, e.ids.containsAll(listOf("SILIZIUM", "HELIUM", "FLUSSSPAT")))
            assertFalse(s, "GALLIUM" in e.ids)
            assertEquals(s, Weg.EIGEN, e.treffer.first { it.id == "HELIUM" }.weg)
            assertFalse(s, e.fabless)
        }
    }

    @Test(timeout = 20_000)
    fun leistungshalbleiterBrauchenGallium() {
        val w = Wertbild("IFX.DE", "HALBLEITER", "DE")
        assertTrue("GALLIUM" in ids(w))
        assertEquals("GAN", treffer(w, "GALLIUM").verwendung)
        assertEquals(Weg.EIGEN, treffer(w, "GALLIUM").weg)
    }

    @Test(timeout = 20_000)
    fun batterieautosUndRohstoffeJeAntrieb() {
        val tesla = ids(Wertbild("TSLA", "FAHRZEUGE", "US"))
        assertTrue(tesla.containsAll(listOf("LITHIUM", "NICKEL", "GRAPHIT")))
        // Kein Verbrenner, kein Abgaskatalysator
        assertFalse("PLATIN" in tesla)
        val byd = ids(Wertbild("1211.HK", "FAHRZEUGE", "CN"))
        assertTrue(byd.containsAll(listOf("LITHIUM", "GRAPHIT")))
        assertEquals(Weg.EIGEN, treffer(Wertbild("1211.HK", "FAHRZEUGE", "CN"), "LITHIUM").weg)
        // Hersteller mit Verbrennern behalten Platin im Katalysator
        assertTrue("PLATIN" in ids(Wertbild("VOW3.DE", "FAHRZEUGE", "DE", industrie = "Auto Manufacturers")))
    }

    @Test(timeout = 20_000)
    fun umicoreUndRheinmetall() {
        val umicore = ids(Wertbild("UMI.BR", "CHEMIE", "BE"))
        assertTrue(umicore.containsAll(listOf("KOBALT", "NICKEL", "LITHIUM", "PLATIN")))
        assertEquals("KATHODE", treffer(Wertbild("UMI.BR", "CHEMIE", "BE"), "KOBALT").verwendung)
        val rhm = ids(Wertbild("RHM.DE", "LUFTFAHRT", "DE"))
        assertTrue(rhm.containsAll(listOf("WOLFRAM", "KUPFER", "ANTIMON")))
        // Keine Triebwerkslegierungen der Luftfahrtbranche
        assertTrue(listOf("TITAN", "KOBALT").none { it in rhm })
    }

    @Test(timeout = 20_000)
    fun appleUndDieKonfliktmineralienDerSec() {
        val apple = Wertbild("AAPL", "COMPUTER", "US")
        val ohne = Rohstoffprofil.fuer(index, apple)
        assertTrue(ohne.ids.containsAll(listOf("ALUMINIUM", "LITHIUM", "SELTENE_ERDEN")))
        assertTrue(ohne.treffer.all { it.quelle == Quelle.FIRMA })
        val mit = Rohstoffprofil.fuer(index, apple, Rohstoffprofil.SecAngaben("2026-05-30"))
        assertTrue(mit.ids.containsAll(listOf("ZINN", "TANTAL", "WOLFRAM", "GOLD")))
        // Wolfram und Gold nennt nur die SEC; Zinn behaelt seine Verwendung aus dem Geschaeftsmodell
        assertEquals("KONFLIKT", treffer(apple, "GOLD", Rohstoffprofil.SecAngaben("2026-05-30")).verwendung)
        assertEquals("LOT", treffer(apple, "ZINN", Rohstoffprofil.SecAngaben("2026-05-30")).verwendung)
        assertEquals(Quelle.SEC, treffer(apple, "ZINN", Rohstoffprofil.SecAngaben("2026-05-30")).quelle)
        assertEquals(Rohstoffprofil.SEC_SD, treffer(apple, "ZINN", Rohstoffprofil.SecAngaben("2026-05-30")).sec)
        // Foerderer melden in der Form SD Zahlungen an Staaten, keine Konfliktmineralien im Produkt
        val miner = Wertbild("FCX", "BERGBAU", "US")
        assertEquals(listOf("KUPFER", "GOLD").toSet(), ids(miner, Rohstoffprofil.SecAngaben("2026-05-30")).toSet())
    }

    @Test(timeout = 20_000)
    fun bankenHabenKeineRohstoffAuchNichtUeberDieBeschreibung() {
        assertTrue(ids(Wertbild("JPM", "BANKEN", "US")).isEmpty())
        val goldhaendler = Wertbild("BANK", "BANKEN", "US", industrie = "Banks - Diversified",
            beschreibung = "The bank offers gold mining finance, precious metals production loans and sells jewelry vouchers.")
        assertTrue(ids(goldhaendler).isEmpty())
        // Auch mit Form SD bleibt eine Bank ohne Rohstoffe nur dann leer, wenn sie keine meldet; eine Meldung zaehlt
        assertTrue(ids(goldhaendler).isEmpty())
    }

    @Test(timeout = 20_000)
    fun versorgerBrauchenLeitungenKeineWafer() {
        val nee = Wertbild("ZZUTIL", "VERSORGER", "US", industrie = "Utilities - Regulated Electric")
        assertEquals(listOf("KUPFER", "ALUMINIUM"), ids(nee))
        assertTrue(Rohstoffprofil.fuer(index, nee).treffer.all { it.quelle == Quelle.INDUSTRIE })
        // Ohne feine Industrie: der Kern der Branche statt Silizium und Silber
        assertEquals(listOf("KUPFER", "ALUMINIUM"), ids(Wertbild("UNBEKANNT", "VERSORGER", "DE")))
        // Erneuerbare Versorger: Windmagnete und Solarmodule
        assertTrue("SELTENE_ERDEN" in ids(Wertbild("ORSTED.CO", "VERSORGER", "DK", industrie = "Utilities - Renewable")))
        // Gasnetze: nichts aus der Liste
        assertTrue(ids(Wertbild("SRG.MI", "VERSORGER", "IT", industrie = "Utilities - Regulated Gas")).isEmpty())
    }

    @Test(timeout = 20_000)
    fun fluggesellschaftenSindKeinFlugzeugbau() {
        assertTrue(ids(Wertbild("DAL", "VERKEHR", "US", industrie = "Airlines")).isEmpty())
        assertTrue(ids(Wertbild("BA", "LUFTFAHRT", "US")).containsAll(listOf("TITAN", "ALUMINIUM")))
        assertFalse("WOLFRAM" in ids(Wertbild("BA", "LUFTFAHRT", "US")))
    }

    @Test(timeout = 20_000)
    fun beschreibungLiefertVerwendungFuerUnbekannteFirmen() {
        val w = Wertbild("ZELLE", null, "DE", industrie = "Electrical Equipment & Parts",
            beschreibung = "The company manufactures lithium-ion battery cells and sells graphite anode materials and copper foil.")
        val e = Rohstoffprofil.fuer(index, w)
        assertEquals("AKKU", e.treffer.first { it.id == "LITHIUM" }.verwendung)
        assertEquals("ANODE", e.treffer.first { it.id == "GRAPHIT" }.verwendung)
        assertEquals(Quelle.BESCHREIBUNG, e.treffer.first { it.id == "LITHIUM" }.quelle)
        // Die Industrie liefert KUPFER, die Beschreibung verfeinert es
        assertEquals(Quelle.BESCHREIBUNG, e.treffer.first { it.id == "KUPFER" }.quelle)
        assertEquals("KABEL", e.treffer.first { it.id == "KUPFER" }.verwendung)
        // Ohne Fundstelle keine Zeile: "Goldman" und "Goldstandard" sind kein Gold
        val kein = Wertbild("X", null, "US", beschreibung = "Goldman style gold standard consulting for mining boards.")
        assertTrue(ids(kein).isEmpty())
    }

    @Test(timeout = 20_000)
    fun brancheBleibtRueckfallUndIstSoGekennzeichnet() {
        val maschinen = Rohstoffprofil.fuer(index, Wertbild("UNBEKANNT", "MASCHINEN", "DE"))
        assertEquals(Einflussverzeichnis.rohstoffeFuerBranche(index, "MASCHINEN").map { it.id }, maschinen.ids)
        assertTrue(maschinen.treffer.all { it.quelle == Quelle.BRANCHE && it.verwendung != null })
        assertFalse(maschinen.firmenbezogen)
        assertEquals(listOf("SILIZIUM"), ids(Wertbild("CHIP", "HALBLEITER", "US")))
        assertTrue(ids(Wertbild("KLINIK", "GESUNDHEIT", "DE")).isEmpty())
        assertTrue(ids(Wertbild("OHNE", null, "US")).isEmpty())
    }

    @Test(timeout = 20_000)
    fun rohstoffabschnittImJahresbericht() {
        val html = "<html><body><p>Item 1. Business</p><p>Raw materials</p><p>We purchase cobalt, nickel and <b>graphite</b> from suppliers " +
            "in Asia. Supplies of lithium carbonate are limited.</p><p>Seasonality</p><p>None.</p></body></html>"
        val roh = Rohstoffprofil.ausJahresbericht(html)
        assertTrue(roh.containsAll(listOf("KOBALT", "NICKEL", "GRAPHIT", "LITHIUM")))
        // Ausserhalb des Abschnitts genannte Rohstoffe zaehlen nicht
        val sonst = "<p>Our founder collected copper coins.</p>" + "<p>filler</p>".repeat(200) + "<p>Raw materials: steel.</p>"
        assertFalse("KUPFER" in Rohstoffprofil.ausJahresbericht(sonst))
        assertTrue(Rohstoffprofil.ausJahresbericht("<p>No such section, we buy cobalt.</p>").isEmpty())
    }

    @Test(timeout = 20_000)
    fun listeDerSecLiefertFormSdUndHauptdokument() {
        val json = """{"filings":{"recent":{"form":["10-K","SD","8-K","SD"],"accessionNumber":["0001-26-000010","0001-25-000020","0001-26-000030","0001-24-000040"],
            "filingDate":["2026-02-25","2025-05-30","2026-03-01","2024-05-31"],"primaryDocument":["nvda-20260125.htm","sd.htm","x.htm","y.htm"],"size":[7000000,1000,500,400]}}}"""
        assertEquals("2025-05-30", SecBericht.sdDatum(json, 2026))
        // Aelter als zwei Jahre zaehlt nicht
        assertEquals("", SecBericht.sdDatum(json, 2028))
        assertEquals("", SecBericht.sdDatum("{}", 2026))
        val d = SecBericht.hauptdokument(json, "0001-26-000010")
        assertNotNull(d)
        assertEquals("nvda-20260125.htm", d!!.datei)
        assertEquals(7_000_000L, d.groesse)
        assertNull(SecBericht.hauptdokument(json, "gibt-es-nicht"))
    }

    @Test(timeout = 20_000)
    fun embargoUndBindungNutzenDieUnternehmenszuordnung() {
        val bestand = listOf(
            Bestand(nvidia, "NVIDIA", 0.3),
            Bestand(Wertbild("JPM", "BANKEN", "US"), "JPMorgan", 0.3),
            Bestand(Wertbild("IFX.DE", "HALBLEITER", "DE"), "Infineon", 0.2),
            Bestand(Wertbild("ZZUTIL", "VERSORGER", "US"), "Versorger ohne Kernkraft", 0.2),
        )
        val z = Szenarien.ereignis(Szenarien.Ereignis.EMBARGO, index, bestand, heute)
        val je = z.betroffen.associateBy { it.symbol }
        // NVIDIA: nur Silizium (China 85 Prozent) ueber den Auftragsfertiger; Infineon zusaetzlich Gallium (China 99 Prozent)
        assertEquals(listOf("SILIZIUM"), je.getValue("NVDA").rohstoffe.map { it.id })
        assertTrue(je.getValue("IFX.DE").rohstoffe.map { it.id }.containsAll(listOf("SILIZIUM", "GALLIUM")))
        assertFalse("JPM" in je)
        // Versorger: Kupfer und Aluminium bleiben unter der Schwelle
        assertFalse("ZZUTIL" in je)
        assertEquals(87.5, Einflussverzeichnis.rohstoffBindung(index, nvidia)!!, 1e-9)
        assertNull(Einflussverzeichnis.rohstoffBindung(index, Wertbild("JPM", "BANKEN", "US")))
        // Branchenabfrage bleibt fuer Aufrufer ohne Unternehmen erhalten
        assertEquals(37.5, Einflussverzeichnis.rohstoffBindung(index, "HALBLEITER")!!, 1e-9)
    }

    @Test(timeout = 20_000)
    fun wirkungsnetzTrifftWerteNachProfilNichtNachBranche() {
        val nv = Einflussverzeichnis.fuerWert(index, nvidia, heute).map { it.groesse.id }
        assertTrue("rohstoff:SILIZIUM" in nv)
        assertFalse("rohstoff:GALLIUM" in nv)
        assertFalse("rohstoff:HELIUM" in nv)
        val ifx = Einflussverzeichnis.fuerWert(index, Wertbild("IFX.DE", "HALBLEITER", "DE"), heute).map { it.groesse.id }
        assertTrue("rohstoff:GALLIUM" in ifx)
        // Foerderer ohne Branchenliste: Kupfer trifft Freeport ueber das Profil
        val fcx = Einflussverzeichnis.fuerWert(index, Wertbild("FCX", "BERGBAU", "US"), heute).map { it.groesse.id }
        assertTrue("rohstoff:KUPFER" in fcx)
        val depot = listOf(Bestand(nvidia, "NVIDIA", 0.5), Bestand(Wertbild("IFX.DE", "HALBLEITER", "DE"), "Infineon", 0.5))
        assertEquals(listOf("IFX.DE"), Einflussverzeichnis.imDepot(index, "rohstoff:GALLIUM", depot, heute).map { it.bestand.wert.symbol })
        assertEquals(Einflussverzeichnis.ZielArt.PROFIL, Einflussverzeichnis.imDepot(index, "rohstoff:GALLIUM", depot, heute).first().kante.ziel.art)
    }

    // Stichprobe vorher (Branche des Katalogs) und nachher (Unternehmenszuordnung ohne Beschreibung) fuer den Bericht
    @Test(timeout = 30_000)
    fun stichprobeVorherNachher() {
        class P(val s: String, val b: String, val l: String, val industrie: String? = null)
        val proben = listOf(
            P("NVDA", "HALBLEITER", "US"), P("TSM", "HALBLEITER", "TW"), P("INTC", "HALBLEITER", "US"), P("IFX.DE", "HALBLEITER", "DE"),
            P("ASML.AS", "HALBLEITER", "NL"), P("QCOM", "HALBLEITER", "US"), P("AAPL", "COMPUTER", "US"), P("TSLA", "FAHRZEUGE", "US"),
            P("1211.HK", "FAHRZEUGE", "CN"), P("VOW3.DE", "FAHRZEUGE", "DE", "Auto Manufacturers"), P("7203.T", "FAHRZEUGE", "JP"),
            P("UMI.BR", "CHEMIE", "BE"), P("RHM.DE", "LUFTFAHRT", "DE"), P("BA", "LUFTFAHRT", "US"), P("RR.L", "LUFTFAHRT", "GB"),
            P("JPM", "BANKEN", "US"), P("NEE", "VERSORGER", "US", "Utilities - Regulated Electric"), P("RWE.DE", "VERSORGER", "DE"),
            P("DAL", "VERKEHR", "US", "Airlines"), P("HEI.DE", "BAU", "DE"), P("BHP", "BERGBAU", "AU"), P("FCX", "BERGBAU", "US"),
            P("ALB", "CHEMIE", "US"), P("SIE.DE", "ELEKTRO", "DE"), P("ENR.DE", "ELEKTRO", "DE"), P("VWS.CO", "ELEKTRO", "DK"),
            P("SHL.DE", "GESUNDHEIT", "DE"), P("UNH", "GESUNDHEIT", "US"), P("DTE.DE", "TELEKOM", "DE"), P("3750.HK", "ELEKTRO", "CN"),
            P("MOS", "CHEMIE", "US"), P("LIN", "CHEMIE", "IE"), P("CAT", "MASCHINEN", "US"), P("SAND.ST", "MASCHINEN", "SE"),
        )
        val zeilen = ArrayList<String>()
        zeilen += "| Wert | Land | Branche | vorher (Branche) | nachher (Unternehmen) |"
        zeilen += "|---|---|---|---|---|"
        fun kurz(i: String) = i.lowercase().replaceFirstChar { it.uppercase() }
        for (p in proben) {
            val w = Wertbild(p.s, p.b, p.l, p.l, industrie = p.industrie)
            val vor = Einflussverzeichnis.rohstoffeFuerBranche(index, p.b).joinToString(", ") { kurz(it.id) }.ifEmpty { "keine" }
            val nach = Rohstoffprofil.fuer(index, w).treffer.joinToString(", ") { t ->
                kurz(t.id) + (t.weg?.let { " (" + when (it) { Weg.EIGEN -> "selbst"; Weg.ZULIEFERER -> "Zulieferer"; Weg.FERTIGER -> "Auftragsfertiger"; Weg.PRODUKT -> "Produkt" } + ")" } ?: "")
            }.ifEmpty { "keine" }
            zeilen += "| ${p.s} | ${p.l} | ${p.b} | $vor | $nach |"
            // Jede Zeile nennt nur Rohstoffe der Datei
            assertTrue(p.s, Rohstoffprofil.fuer(index, w).ids.all { it in index.rohstoffe })
        }
        assertTrue(proben.size >= 20)
        File("build").mkdirs()
        File("build/rohstoffprofil-vorher-nachher.md").writeText(zeilen.joinToString("\n"))
    }
}
