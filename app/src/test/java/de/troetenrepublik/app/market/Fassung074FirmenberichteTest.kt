package de.troetenrepublik.app.market

import de.troetenrepublik.app.market.SecBericht.Status
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.InputStream

// Abruf, Speicher und Erneuerung der Firmenangaben (SEC-Jahresbericht, GLEIF-Konzernmutter) ohne Netz und ohne Wartezeit.
class Fassung074FirmenberichteTest {
    private fun resource(name: String): ByteArray = javaClass.classLoader!!.getResourceAsStream("sec/$name.xml")!!.readBytes()

    private class Netz : FirmenAbruf {
        val texte = HashMap<String, String>()
        var instanz: ByteArray = ByteArray(0)
        val abrufe = ArrayList<String>()
        var ausfall = false

        override suspend fun sec(url: String): String? { abrufe += url; return if (ausfall) null else texte[url] }

        override suspend fun secStrom(url: String, lesen: (InputStream) -> Xbrl.Gelesen): Xbrl.Gelesen? {
            abrufe += url
            return if (ausfall) null else lesen(instanz.inputStream())
        }

        override suspend fun gleif(url: String): String? { abrufe += url; return if (ausfall) null else texte[url] }
    }

    private class Platte : FirmenSpeicher {
        val dateien = HashMap<String, String>()
        override fun lesen(name: String): String? = dateien[name]
        override fun schreiben(name: String, text: String) { dateien[name] = text }
    }

    private val tag = 86_400_000L
    private var uhr = 1_000_000_000_000L
    private var pausen = 0

    private fun dienst(netz: Netz, platte: Platte = Platte()) = Firmenberichte(netz, platte, { uhr }, { pausen++ })

    private val kuerzel = """{"0":{"cik_str":1045810,"ticker":"NVDA","title":"NVIDIA CORP"}}"""
    private fun einreichungen(acc: String) = """{"filings":{"recent":{"form":["10-K"],"accessionNumber":["$acc"],"filingDate":["2026-02-25"],"reportDate":["2026-01-25"],"isXBRL":[1]}}}"""
    private fun verzeichnis(groesse: Long = 1_685_733L) = """{"directory":{"item":[{"name":"FilingSummary.xml","size":"52270"},{"name":"nvda-20260125_htm.xml","size":"$groesse"}]}}"""

    private fun nvidia(netz: Netz, acc: String = "0001045810-26-000021", groesse: Long = 1_685_733L) {
        netz.texte[SecFormen.KUERZEL] = kuerzel
        netz.texte[SecBericht.submissionsAdresse("1045810")] = einreichungen(acc)
        netz.texte[SecBericht.verzeichnisAdresse("1045810", acc)] = verzeichnis(groesse)
        netz.instanz = resource("nvidia")
    }

    @Test(timeout = 20_000)
    fun ersterAbrufLiestDenBerichtUndSpeichertIhn() = runBlocking {
        val netz = Netz(); nvidia(netz)
        val platte = Platte()
        val e = dienst(netz, platte).laden("NVDA", "NVIDIA", true)
        assertEquals(Status.OK, e.status)
        assertEquals(listOf("Compute And Networking", "Graphics"), e.bericht!!.segmente.map { it.n })
        // Kuerzelliste, Einreichungen, Verzeichnis, Instanz; zwischen den Abrufen steht eine Pause
        assertEquals(4, netz.abrufe.size)
        assertEquals(3, pausen)
        assertTrue(platte.dateien.containsKey(Firmenberichte.SEC_DATEI))
        // Ein neuer Dienst liest den Stand vom Geraet
        val spaeter = dienst(netz, platte).laden("NVDA", "NVIDIA", true)
        assertEquals(Status.OK, spaeter.status)
        assertEquals(4, netz.abrufe.size)
    }

    @Test(timeout = 20_000)
    fun innerhalbeinerWocheKeinAbruf() = runBlocking {
        val netz = Netz(); nvidia(netz)
        val d = dienst(netz)
        d.laden("NVDA", "NVIDIA", true)
        val n = netz.abrufe.size
        uhr += 6 * tag
        d.laden("NVDA", "NVIDIA", true)
        assertEquals(n, netz.abrufe.size)
        assertEquals(Status.OK, d.gespeichert("NVDA")!!.status)
        assertNull(d.gespeichert("AAPL"))
    }

    @Test(timeout = 20_000)
    fun nachEinerWocheNurDieListeWennKeinNeuerBericht() = runBlocking {
        val netz = Netz(); nvidia(netz)
        val d = dienst(netz)
        d.laden("NVDA", "NVIDIA", true)
        netz.abrufe.clear()
        uhr += 8 * tag
        val e = d.laden("NVDA", "NVIDIA", true)
        assertEquals(Status.OK, e.status)
        assertEquals(listOf(SecBericht.submissionsAdresse("1045810")), netz.abrufe)
        // Die Nachfrage ist vermerkt: eine Woche Ruhe
        netz.abrufe.clear()
        uhr += 3 * tag
        d.laden("NVDA", "NVIDIA", true)
        assertTrue(netz.abrufe.isEmpty())
    }

    @Test(timeout = 20_000)
    fun neuerBerichtWirdGelesen() = runBlocking {
        val netz = Netz(); nvidia(netz)
        val d = dienst(netz)
        d.laden("NVDA", "NVIDIA", true)
        netz.abrufe.clear()
        uhr += 8 * tag
        nvidia(netz, acc = "0001045810-27-000009")
        val e = d.laden("NVDA", "NVIDIA", true)
        assertEquals(Status.OK, e.status)
        assertEquals(3, netz.abrufe.size)
        assertTrue(netz.abrufe.last().contains("000104581027000009"))
    }

    @Test(timeout = 20_000)
    fun zuGrosserBerichtWirdNichtGeladen() = runBlocking {
        val netz = Netz(); nvidia(netz, groesse = SecBericht.MAX_BYTES + 1)
        val e = dienst(netz).laden("NVDA", "NVIDIA", true)
        assertEquals(Status.ZU_GROSS, e.status)
        assertNull(e.bericht)
        assertTrue(netz.abrufe.none { it.endsWith("_htm.xml") })
    }

    @Test(timeout = 20_000)
    fun ueberschreitungWaehrendDesLesensGiltAlsZuGross() = runBlocking {
        val netz = Netz(); nvidia(netz)
        // Verzeichnis nennt eine kleine Datei, der Strom ist aber groesser als die Grenze
        netz.instanz = ByteArray((SecBericht.MAX_BYTES + 10).toInt()) { 'a'.code.toByte() }
        val e = dienst(netz).laden("NVDA", "NVIDIA", true)
        assertEquals(Status.ZU_GROSS, e.status)
    }

    @Test(timeout = 20_000)
    fun firmaOhneSecBerichtBleibtEineWocheStehen() = runBlocking {
        val netz = Netz(); nvidia(netz)
        val d = dienst(netz)
        val e = d.laden("SIE.DE", "Siemens", true)
        assertEquals(Status.KEIN_SEC, e.status)
        val n = netz.abrufe.size
        d.laden("SIE.DE", "Siemens", true)
        assertEquals(n, netz.abrufe.size)
        // Die Kuerzelliste wird fuer weitere Werte nicht noch einmal geholt
        d.laden("BMW.DE", "BMW", true)
        assertEquals(1, netz.abrufe.count { it == SecFormen.KUERZEL })
    }

    @Test(timeout = 20_000)
    fun netzfehlerSpeichertNichts() = runBlocking {
        val netz = Netz(); nvidia(netz)
        val platte = Platte()
        val d = dienst(netz, platte)
        netz.ausfall = true
        assertEquals(Status.FEHLER, d.laden("NVDA", "NVIDIA", true).status)
        assertTrue(platte.dateien.isEmpty())
        netz.ausfall = false
        assertEquals(Status.OK, d.laden("NVDA", "NVIDIA", true).status)
    }

    @Test(timeout = 20_000)
    fun ohneKontaktNurDerGespeicherteStand() = runBlocking {
        val netz = Netz(); nvidia(netz)
        val d = dienst(netz)
        assertEquals(Status.OHNE_KONTAKT, d.laden("NVDA", "NVIDIA", false).status)
        assertTrue(netz.abrufe.isEmpty())
        d.laden("NVDA", "NVIDIA", true)
        uhr += 30 * tag
        netz.abrufe.clear()
        assertEquals(Status.OK, d.laden("NVDA", "NVIDIA", false).status)
        assertTrue(netz.abrufe.isEmpty())
    }

    @Test(timeout = 20_000)
    fun ohneJahresberichtMitXbrl() = runBlocking {
        val netz = Netz(); nvidia(netz)
        netz.texte[SecBericht.submissionsAdresse("1045810")] = """{"filings":{"recent":{"form":["8-K"],"accessionNumber":["x"],"filingDate":["2026-01-01"]}}}"""
        assertEquals(Status.KEIN_BERICHT, dienst(netz).laden("NVDA", "NVIDIA", true).status)
    }

    // ------------------------------------------------------------ Konzernmutter

    private val eigen = """{"data":[{"type":"lei-records","id":"529900QBVWXMWANH7H45","attributes":{"lei":"529900QBVWXMWANH7H45","entity":{"legalName":{"name":"Siemens Healthineers AG","language":"de"},"legalAddress":{"country":"DE"}}},
        "relationships":{"direct-parent":{"links":{"lei-record":"https://api.gleif.org/api/v1/lei-records/529900QBVWXMWANH7H45/direct-parent"}},
        "ultimate-parent":{"links":{"lei-record":"https://api.gleif.org/api/v1/lei-records/529900QBVWXMWANH7H45/ultimate-parent"}}}}]}"""
    private val mutter = """{"data":{"type":"lei-records","id":"W38RGI023J3WT1HWRP32","attributes":{"lei":"W38RGI023J3WT1HWRP32","entity":{"legalName":{"name":"Siemens Aktiengesellschaft","language":"de"},"legalAddress":{"country":"DE"}}}}}"""
    private val ohneMutter = """{"data":[{"type":"lei-records","id":"549300S4KLFTLO7GSQ80","attributes":{"lei":"549300S4KLFTLO7GSQ80","entity":{"legalName":{"name":"NVIDIA CORPORATION","language":"en"},"legalAddress":{"country":"US"}}},
        "relationships":{"direct-parent":{"links":{"reporting-exception":"https://api.gleif.org/api/v1/lei-records/549300S4KLFTLO7GSQ80/direct-parent-reporting-exception"}},
        "ultimate-parent":{"links":{"reporting-exception":"https://api.gleif.org/api/v1/lei-records/549300S4KLFTLO7GSQ80/ultimate-parent-reporting-exception"}}}}]}"""
    private val up = "https://api.gleif.org/api/v1/lei-records/529900QBVWXMWANH7H45/ultimate-parent"

    @Test(timeout = 20_000)
    fun konzernmutterUeberDieObersteMutter() = runBlocking {
        val netz = Netz()
        netz.texte[Gleif.leiSuche("DE000SHL1006")] = eigen
        netz.texte[up] = mutter
        val platte = Platte()
        val m = dienst(netz, platte).mutter("SHL.DE", "DE000SHL1006")!!
        assertEquals("Siemens Aktiengesellschaft", m.name)
        assertEquals("DE", m.land)
        assertEquals(listOf(Gleif.leiSuche("DE000SHL1006"), up), netz.abrufe)
        // Einen Monat lang aus dem Speicher, danach neu
        netz.abrufe.clear()
        uhr += 29 * tag
        assertNotNull(dienst(netz, platte).mutter("SHL.DE", "DE000SHL1006"))
        assertTrue(netz.abrufe.isEmpty())
        uhr += 2 * tag
        dienst(netz, platte).mutter("SHL.DE", "DE000SHL1006")
        assertEquals(2, netz.abrufe.size)
    }

    @Test(timeout = 20_000)
    fun ohneGemeldeteMutterNichtsZeigen() = runBlocking {
        val netz = Netz()
        netz.texte[Gleif.leiSuche("US67066G1040")] = ohneMutter
        val d = dienst(netz)
        assertNull(d.mutter("NVDA", "US67066G1040"))
        assertEquals(1, netz.abrufe.size)
        assertNull(d.mutter("NVDA", "US67066G1040"))
        assertEquals(1, netz.abrufe.size)
        // Ohne ISIN gibt es keinen Abruf
        assertNull(d.mutter("XYZ", ""))
        assertEquals(1, netz.abrufe.size)
    }

    @Test(timeout = 20_000)
    fun mutterGleichNamensWirdNichtGezeigt() {
        val a = Gleif.satzLesen(eigen)!!
        assertNull(Gleif.mutterVon(a, a))
        val gleichName = Gleif.satzLesen(mutter.replace("Siemens Aktiengesellschaft", "SIEMENS HEALTHINEERS AG"))!!
        assertNull(Gleif.mutterVon(a, gleichName))
        assertNull(Gleif.mutterVon(a, null))
        assertNull(Gleif.satzLesen("kein json"))
        assertNull(Gleif.satzLesen("""{"data":[]}"""))
    }

    @Test(timeout = 20_000)
    fun gleifFehlerLaesstDenStandStehen() = runBlocking {
        val netz = Netz()
        netz.texte[Gleif.leiSuche("DE000SHL1006")] = eigen
        netz.texte[up] = mutter
        val platte = Platte()
        dienst(netz, platte).mutter("SHL.DE", "DE000SHL1006")
        uhr += 40 * tag
        netz.ausfall = true
        val m = dienst(netz, platte).mutter("SHL.DE", "DE000SHL1006")
        assertEquals("Siemens Aktiengesellschaft", m!!.name)
        // Der Stand gilt nur fuer dieselbe ISIN
        assertNull(dienst(netz, platte).mutter("SHL.DE", "DE000SHL1007"))
    }
}
